package ml.docilealligator.infinityforreddit.resume

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.Trace
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.core.net.toUri
import androidx.preference.PreferenceManager
import java.io.File
import java.io.IOException
import java.lang.ref.WeakReference
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import ml.docilealligator.infinityforreddit.BuildConfig
import ml.docilealligator.infinityforreddit.account.Account
import ml.docilealligator.infinityforreddit.account.AccountScope
import ml.docilealligator.infinityforreddit.account.AccountScopedSharedPreferences
import ml.docilealligator.infinityforreddit.activities.MainActivity
import ml.docilealligator.infinityforreddit.post.FeedCache
import ml.docilealligator.infinityforreddit.utils.SharedPreferencesUtils
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * The screen stack the user left, so the next launch can put it back.
 *
 * Holds a snapshot of the live activity stack -- each screen's class, the extras it was launched
 * with, and whatever that screen chose to record through [Restorable] -- as JSON under
 * `filesDir/resume_state.json`. `filesDir` and not `cacheDir`: this backs a setting the user turned
 * on, and a cache the OS is free to reclaim would turn it off again at random.
 *
 * Two launch routes have to be handled, and the difference between them is the whole design:
 *
 * - **From the launcher.** The process is new and Android starts [MainActivity] alone.
 *   [buildRestoreIntents] hands it the screens that were above it so it can replay them.
 * - **From recents, with the process dead.** [MainActivity] never runs; the system recreates only
 *   the activity that was on top, and the ones below it come back lazily on Back. [seedFromSnapshot]
 *   rebuilds the live stack underneath that top screen, without which the next [capture] would
 *   refuse the snapshot for not being rooted at [MainActivity] and the user would lose it.
 *
 * Nothing here runs unless the setting is on: every entry point checks first, so a user with it off
 * pays one preference read per activity creation and one per transition, and nothing else. In
 * particular no screen is ever asked to describe itself or to name itself, which is what keeps the
 * three screens that serialize an object graph from costing anything to someone who never turned
 * this on.
 */
object ResumeState {

    private const val TAG = "ResumeState"

    /** Trace section names, matched by scripts/measure-resume-frames.sh and by a Perfetto capture. */
    private const val TRACE_CAPTURE = "ResumeState.capture"
    private const val TRACE_DESCRIBE = "ResumeState.describe"

    /**
     * How long a capture may take before a debug build says so. A 60Hz frame is 16.67ms and the
     * transition this runs on is already spending most of it, so a quarter of a frame is the point
     * at which this stops being free.
     */
    private const val SLOW_CAPTURE_MS = 4.0

    /**
     * Where a screen's description is worked out, off the main thread.
     *
     * Describing means asking the screen for its relaunch extras, and three screens answer that by
     * running a whole `Post`, `PostFilter` or `MultiReddit` through Gson. Traced on a device, the
     * first such call cost 192.9 ms -- 175.7 ms of it contending on the JIT code cache while Gson's
     * writer path was compiled -- which is eleven frames on the transition away from the first
     * gallery a user opens. The work itself is unavoidable; paying for it on the main thread is not.
     *
     * Single-threaded and low priority: there is no ordering requirement between screens, and this
     * must never compete with the UI it exists to keep out of the way of.
     */
    private val defaultDescribeExecutor: Executor =
        Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "resume-describe").apply { priority = Thread.MIN_PRIORITY + 2 }
        }

    @VisibleForTesting
    var describeExecutor: Executor = defaultDescribeExecutor

    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

    /**
     * How work gets onto the main thread's queue. Two uses: bringing a finished description back to
     * be applied -- [live] and every [Entry] on it are only ever touched there, which is what makes
     * the whole thing lock-free -- and deferring the start of a describe to the message after the
     * one that created the screen, so it cannot read the intent while `onCreate` is reading it.
     */
    private val defaultPublisher: (Runnable) -> Unit = { mainHandler.post(it) }

    @VisibleForTesting
    var publishToMainThread: (Runnable) -> Unit = defaultPublisher

    private const val VERSION = 1
    private const val FILE_NAME = "resume_state.json"

    /**
     * Where a replayed screen finds its own recorded state, carried on the intent that launched it.
     *
     * The snapshot is a single document that a new one replaces outright, so nothing may be left
     * waiting in memory to be picked up later. A replayed screen therefore does not go looking: the
     * replay hands it its state directly, and by the time the first new snapshot is written there is
     * nothing outstanding to lose. Stripped from the recorded extras in [launchExtrasOf], because it
     * describes the screen's position rather than which screen it is.
     */
    private const val EXTRA_REPLAY_STATE = "ml.docilealligator.infinityforreddit.resume.STATE"

    private const val KEY_VERSION = "version"
    private const val KEY_SAVED_AT = "savedAt"
    private const val KEY_ACCOUNT = "account"
    private const val KEY_STACK = "stack"
    private const val KEY_CLASS = "cls"
    private const val KEY_EXTRAS = "extras"
    private const val KEY_DATA = "data"
    private const val KEY_STATE = "state"

    /**
     * The screen's own cheap identifier, so [claim] can match a snapshot entry to a screen without
     * the comparison that serializes an object graph. Optional: absent -- which every snapshot
     * written before this existed is -- falls back to comparing extras, so no version bump.
     */
    private const val KEY_IDENTITY = "identity"

    /**
     * Screens that must never be recorded, by simple class name.
     *
     * A composer or a login flow reopened on launch is at best confusing and at worst destructive:
     * a half-written comment restored into a screen the user did not ask for invites them to post
     * it. Hitting one of these truncates the snapshot at that point rather than discarding it, so
     * the feed underneath still comes back.
     */
    private val NEVER_RECORD =
        setOf(
            "LoginActivity",
            "LoginChromeCustomTabActivity",
            "LockScreenActivity",
            "CommentActivity",
            "EditCommentActivity",
            "EditPostActivity",
            "EditMultiRedditActivity",
            "EditProfileActivity",
            "PostTextActivity",
            "PostLinkActivity",
            "PostImageActivity",
            "PostVideoActivity",
            "PostGalleryActivity",
            "PostPollActivity",
            "SubmitCrosspostActivity",
            "SendPrivateMessageActivity",
            "ReportActivity",
            "ShareDataResolverActivity",
            "LinkResolverActivity",
            "QRCodeScannerActivity",
        )

    /**
     * One screen on the stack.
     *
     * Everything but the class name, the reference and [identity] is filled in by [scheduleDescribe]
     * on the describe thread, started as the screen is created and applied when it lands -- or by
     * [describe] on the main thread, if a capture gets here first.
     *
     * None of it happens at all while the setting is off, which is what keeps the feature free for
     * the people who never turn it on. Describing a screen means asking it for its launch extras,
     * and three screens answer that by serializing a whole post, filter or multireddit through Gson:
     * 192.9 ms on a device, the first time, which is why it does not happen on the main thread.
     */
    private class Entry(val cls: String, var activity: WeakReference<Activity>?) {
        /** Whether the fields below have been worked out yet. */
        var described = false
        var extras: Bundle? = null
        /** The intent's data URI, which is not an extra and would otherwise be lost on replay. */
        var data: String? = null
        var state: Bundle? = null
        /** False once an unrecordable screen is seen: it and everything above it cannot come back. */
        var recordable = true

        /**
         * What [ResumeLaunchExtras.resumeIdentity] said when this screen was created, or null if it
         * had no cheap answer. Recorded up front, before anything is described, because its only
         * use is deciding whether a rebuilt screen is this one -- and that decision has to be made
         * synchronously, before the description exists.
         */
        var identity: String? = null

        /** For an entry read back from the snapshot, which is described by construction. */
        constructor(
            cls: String,
            extras: Bundle?,
            data: String?,
            state: Bundle?,
        ) : this(cls, null) {
            this.described = true
            this.extras = extras
            this.data = data
            this.state = state
        }
    }

    /** The stack as it stands right now, bottom first. */
    private val live = mutableListOf<Entry>()

    private var startedCount = 0

    /** The last JSON written, so an unchanged stack costs no file write. */
    private var lastWritten: String? = null

    /** The snapshot read from disk, entries handed out one at a time by [claim]. */
    private var restoring: MutableList<Entry>? = null

    private var loaded = false

    /**
     * The account the loaded snapshot was read for. An account switch happens inside a live process
     * -- the activities are recreated, not the app -- so a snapshot already in memory has to be
     * re-checked rather than trusted, or the new account claims the old one's screens.
     */
    private var loadedAccount: String? = null

    /**
     * Whether a recreated top activity may still seed the stack beneath itself. True only for the
     * first screen of a process: after that, the stack under a new activity is real.
     */
    private var canSeed = true

    /**
     * Whether the stack above MainActivity has already been replayed in this process. A theme or
     * account change relaunches MainActivity from inside the app, and replaying a second time would
     * stack the user's history on top of itself.
     */
    private var replayed = false

    /**
     * The key is scoped by hand here because this reads the raw default preferences rather than the
     * injected [AccountScopedSharedPreferences] wrapper -- there is no Dagger graph at an activity
     * lifecycle callback. The setting sits on a "This account" screen, so one account resuming does
     * not commit the others to it.
     */
    @JvmStatic
    fun isEnabled(context: Context): Boolean =
        PreferenceManager.getDefaultSharedPreferences(context)
            .getBoolean(
                AccountScope.key(
                    currentAccount(context),
                    SharedPreferencesUtils.RESUME_WHERE_I_LEFT_OFF,
                ),
                false,
            )

    private fun currentAccount(context: Context): String =
        context
            .getSharedPreferences(
                SharedPreferencesUtils.CURRENT_ACCOUNT_SHARED_PREFERENCES_FILE,
                Context.MODE_PRIVATE,
            )
            .getString(SharedPreferencesUtils.ACCOUNT_NAME, Account.ANONYMOUS_ACCOUNT)
            ?: Account.ANONYMOUS_ACCOUNT

    // ---------------------------------------------------------------- lifecycle

    /**
     * Put [activity] on the live stack. Safe to call twice for the same activity, and it is called
     * twice: from `onActivityPreCreated` where the platform has it, and from `onActivityCreated`
     * everywhere else. Only the first call records anything, so where both fire the earlier one
     * wins and the ordering is the one the replay depends on.
     */
    @JvmStatic
    fun recordCreated(activity: Activity) {
        val cls = activity.javaClass.name
        if (live.any { it.activity?.get() === activity }) {
            return
        }
        // A configuration change destroys and rebuilds the screen in place. recordDestroyed leaves
        // the entry behind with a dead reference precisely so the replacement can adopt it, rather
        // than the stack losing its root on every rotation -- and adopting keeps whatever describe()
        // already worked out, since a rebuilt screen carries the same intent.
        for (i in live.indices.reversed()) {
            val entry = live[i]
            if (entry.cls != cls || entry.activity?.get() != null) {
                continue
            }
            // Same class is not the same screen. With "Don't keep activities" on, opening r/aww
            // finds the destroyed r/pics entry sitting there and would adopt it, inheriting its
            // recorded extras -- so the feed the user is actually reading gets recorded under the
            // other subreddit's name, and the resume reopens the wrong one. An entry nothing has
            // described yet has no identity to inherit wrongly; a described one is only this screen
            // if it was launched the same way.
            //
            // isSameScreen answers that from the cheap identity where the screen offers one, and
            // only falls back to comparing rewritten extras -- a Gson call, on the main thread,
            // during a rotation -- where it does not. That fallback is why the three screens that
            // serialize an object graph implement resumeIdentity.
            if (!entry.described || isSameScreen(entry, activity)) {
                entry.activity = WeakReference(activity)
                // An adopted entry that nobody has described yet still needs one, and the rebuilt
                // screen is the one to ask. An already-described one keeps what it had: a rebuilt
                // screen carries the same intent, so the answer cannot have changed.
                //
                // This is also where an entry created while the setting was off picks up its
                // identity. Without that it would keep a null one for the rest of the session, and
                // the first rotation after the screen was described would drop back to comparing
                // extras -- the main-thread Gson call, on the one path that cannot afford it.
                if (!entry.described && isEnabled(activity)) {
                    startDescribing(entry, activity)
                }
                return
            }
        }
        val entry = Entry(cls, WeakReference(activity))
        live.add(entry)
        // Both of the calls below ask the screen a question, so both are behind the setting and the
        // setting is read once. resumeIdentity is cheap next to a describe but it is not free --
        // the gallery's answer unparcels a whole Post to read one field off it -- and charging that
        // to someone who never turned the feature on is the regression this gate exists to stop.
        //
        // Scheduling here rather than at the first capture gives the expensive screens -- gallery,
        // filtered posts, search, which each run an object graph through Gson -- the whole time the
        // user spends on the screen to finish, instead of spending it on the transition away.
        if (isEnabled(activity)) {
            startDescribing(entry, activity)
        }
    }

    /**
     * Ask [activity] who it is, and start working out how to rebuild it.
     *
     * The two go together: the identity is what a later rotation compares against instead of
     * serializing the screen again, so an entry that gets a description without one has only the
     * expensive comparison left to fall back on. Both callers are already behind the setting.
     */
    private fun startDescribing(entry: Entry, activity: Activity) {
        // The identity is read here, on the main thread, while the screen is being created.
        entry.identity = identityOf(activity)
        // The describe is queued for after this message instead of started now. It runs off the
        // main thread and reads the activity's intent, and this is called from
        // onActivityCreated -- before the screen's own onCreate has read those same extras. Two
        // threads reading one Bundle is not a spectator sport: reading a Parcelable extra unparcels
        // it lazily and writes the result back into the map. Posting puts the work after onCreate
        // has finished, so the read this could have collided with has already happened.
        publishToMainThread { scheduleDescribe(entry, activity) }
    }

    /** What [describe] works out, as a value, so it can be computed away from the entry it fills. */
    private class Description(val extras: Bundle?, val data: String?, val recordable: Boolean)

    /**
     * Work out what it would take to rebuild this screen. Pure: it reads the activity's intent and
     * returns, touching nothing shared, which is what lets [scheduleDescribe] run it off the main
     * thread.
     *
     * Its own trace section. It is no longer nested inside capture's on the common path -- it
     * happens earlier, on the describe thread -- so in a trace look for it on `resume-describe`
     * rather than on the main thread, and finding it on the main thread means a capture beat the
     * background work to this screen.
     */
    private fun computeDescription(cls: String, activity: Activity): Description {
        Trace.beginSection(TRACE_DESCRIBE)
        try {
            val extras = launchExtrasOf(activity)
            return Description(
                extras = extras,
                data = activity.intent?.dataString,
                // An entry whose extras cannot be encoded cannot be relaunched, so it is recorded
                // as unrecordable rather than dropped: capture() needs to know where to truncate.
                recordable =
                    cls.substringAfterLast('.') !in NEVER_RECORD &&
                        (activity !is ResumeLaunchExtras || extras != null) &&
                        (extras == null || BundleJson.toJson(extras, lenient = false) != null),
            )
        } finally {
            Trace.endSection()
        }
    }

    /**
     * Fill [entry] in from a finished [Description]. Main thread only, like everything that touches
     * [live].
     *
     * Marked described whatever the description says, so a screen that cannot answer is asked once
     * and not again on every screen transition for the rest of its life.
     */
    private fun applyDescription(entry: Entry, description: Description) {
        entry.described = true
        entry.extras = description.extras
        entry.data = description.data
        entry.recordable = description.recordable
    }

    /**
     * Describe [activity] on the main thread, because something needs the answer now.
     *
     * The fallback for when a capture reaches a screen before [scheduleDescribe]'s result has come
     * back -- the user opening a screen and leaving it again within a frame or two. Correct, just
     * not free, and it is the path this whole arrangement exists to keep off the common route.
     */
    private fun describe(entry: Entry, activity: Activity) {
        // Double-checked against the background task: it may have computed this already and be
        // waiting to publish, in which case there is nothing to do and nothing to race over.
        val description =
            synchronized(entry) {
                if (entry.described) return
                computeDescription(entry.cls, activity)
            }
        applyDescription(entry, description)
    }

    /**
     * Start working out [entry]'s description on [describeExecutor], to be applied when it lands.
     *
     * Called as the screen is created, which buys the whole time the user spends looking at it --
     * against a cost measured at 15.9 ms in one process and 192.9 ms in another, both on the same
     * device, the difference being how much of Gson's writer path was already compiled.
     *
     * The result is discarded rather than forced if anything moved meanwhile: a capture may have
     * described the entry inline already, or the entry may have been adopted by a different screen
     * of the same class. Both are checked on the main thread, at the moment of applying, so the
     * background thread never observes [live] at all.
     */
    private fun scheduleDescribe(entry: Entry, activity: Activity) {
        // A composer or a login screen is unrecordable by class alone, so there is nothing to work
        // out and no reason to touch it.
        if (entry.cls.substringAfterLast('.') in NEVER_RECORD) {
            return
        }
        val ref = WeakReference(activity)
        describeExecutor.execute {
            val screen = ref.get() ?: return@execute
            val description =
                try {
                    // The lock covers the computation and nothing else. What it protects is not the
                    // entry's fields -- those are only ever written on the main thread, below --
                    // but the activity's Intent: reading a Parcelable extra unparcels the Bundle
                    // lazily and mutates it in place, so an inline describe racing this one on the
                    // same screen would be two threads unparcelling the same Bundle at once.
                    synchronized(entry) {
                        if (entry.described) return@execute
                        computeDescription(entry.cls, screen)
                    }
                } catch (e: RuntimeException) {
                    // launchExtrasOf already contains what a screen's own code can throw; this is
                    // the backstop that keeps an unexpected one from killing the describe thread
                    // and silently disabling the optimisation for the rest of the session. The
                    // entry stays undescribed and capture handles it inline, exactly as before.
                    return@execute
                }
            publishToMainThread {
                if (!entry.described && entry.activity?.get() === screen) {
                    applyDescription(entry, description)
                }
            }
        }
    }

    @JvmStatic
    fun recordDestroyed(activity: Activity) {
        for (i in live.indices.reversed()) {
            if (live[i].activity?.get() !== activity) {
                continue
            }
            if (activity.isFinishing) {
                live.removeAt(i)
            } else {
                // Destroyed for a configuration change, not dismissed: the screen is still on the
                // stack and is about to be rebuilt. Keeping the entry is what stops a rotation from
                // dropping screens out of the middle of the stack -- and a stack that has lost
                // MainActivity from the bottom is one capture() refuses outright.
                live[i].activity = null
            }
            return
        }
    }

    @JvmStatic
    fun onActivityStarted() {
        startedCount++
        canSeed = false
    }

    /**
     * The app went to the background. This, not `onSaveInstanceState`, is where the snapshot is
     * written: dismissing from recents delivers pause, stop and destroy in one short burst and then
     * kills the process, and `onSaveInstanceState` is not called on that path at all.
     */
    @JvmStatic
    fun onActivityStopped(context: Context) {
        startedCount--
        if (startedCount <= 0) {
            startedCount = 0
            capture(context)
        }
    }

    /** The earliest warning that the app may be going away. */
    @JvmStatic
    fun onActivityPaused(context: Context) {
        capture(context)
    }

    // ---------------------------------------------------------------- capture

    /**
     * Ask every live screen where it is and write the snapshot.
     *
     * The write is synchronous, on the calling thread, and that is deliberate: a background write
     * loses the race against process death, which is exactly the case this exists to survive. The
     * cost is bounded because only extras and small state bundles are written here -- the posts
     * themselves live in [FeedCache].
     */
    @JvmStatic
    fun capture(context: Context) {
        if (!isEnabled(context)) {
            return
        }
        // Traced and timed because this is the whole main-thread cost of the feature being on, and
        // it lands on a screen transition, where an animation is already using the frame. The
        // section shows up on the main-thread track in a Perfetto capture; the log line below is
        // for when a trace would be more setup than the question deserves. Both are off in a
        // release build -- Trace is a no-op when nothing is tracing, and the timing is behind
        // BuildConfig.DEBUG.
        // Read the clock BEFORE opening the section, so nothing sits between beginSection and the
        // try that guarantees its endSection. Anything in that gap that threw would leak the
        // section and corrupt every enclosing one in the trace, not just this one.
        val startedAt = if (BuildConfig.DEBUG) SystemClock.elapsedRealtimeNanos() else 0L
        Trace.beginSection(TRACE_CAPTURE)
        try {
            captureEnabled(context)
        } finally {
            Trace.endSection()
            if (BuildConfig.DEBUG) {
                val elapsedMs = (SystemClock.elapsedRealtimeNanos() - startedAt) / 1_000_000.0
                if (elapsedMs > SLOW_CAPTURE_MS) {
                    Log.w(
                        TAG,
                        "capture took ${elapsedMs}ms on the main thread, " +
                            "over the ${SLOW_CAPTURE_MS}ms budget",
                    )
                }
            }
        }
    }

    private fun captureEnabled(context: Context) {
        val snapshot = mutableListOf<Entry>()
        for (entry in live) {
            val activity = entry.activity?.get()
            if (!entry.described) {
                if (activity == null) {
                    // Never described, and gone before anything could ask it: there is no way to
                    // rebuild it, so the snapshot stops here exactly as it would for a screen that
                    // said it could not be relaunched.
                    break
                }
                describe(entry, activity)
            }
            if (!entry.recordable) {
                break
            }
            if (activity is Restorable) {
                val out = Bundle()
                try {
                    activity.saveResumeState(out)
                } catch (e: RuntimeException) {
                    // A screen that cannot describe itself truncates the snapshot rather than
                    // failing the whole thing: what is below it is still worth coming back to.
                    break
                }
                // An empty bundle means the screen had nothing to say right now, not that it has
                // nothing worth remembering. Overwriting on that would let a capture during startup
                // erase the position the user actually left.
                if (!out.isEmpty) {
                    entry.state = out
                }
            }
            snapshot.add(entry)
        }

        if (snapshot.isEmpty() || snapshot[0].cls != MainActivity::class.java.name) {
            // Without MainActivity underneath, backing out of a restored screen would leave the user
            // nowhere. Better to launch normally than to restore half a stack.
            return
        }

        val document = toJson(snapshot, currentAccount(context)) ?: return
        // Compared without the timestamp, which is added below. With it inside the comparison the
        // document differed on every capture -- System.currentTimeMillis() moves whether or not the
        // stack did -- so this check never fired and every screen transition wrote the file
        // synchronously on the main thread. That is the cost this check exists to avoid, and the
        // timestamp is a field nothing reads.
        val fingerprint = document.toString()
        if (fingerprint == lastWritten) {
            return
        }
        // One snapshot, replaced outright: the document just written is the whole truth, so anything
        // still unclaimed from the one it replaces is stale and goes with it. Nothing is lost by
        // this, because every screen that had state coming to it has already been given it -- the
        // launcher path hands each replayed screen its state on the intent that starts it, and the
        // recents path claims before anything has had a chance to pause. What this does prevent is
        // an old entry surviving to be claimed by a screen the user opens themselves much later,
        // which would drop them somewhere they had been in a previous session with no way to tell
        // why.
        restoring = null
        try {
            // Stamped here rather than in toJson, so the time recorded is when the stack last
            // changed rather than when it was last looked at. Nothing reads it yet; it is written
            // so a TTL could be added without a version bump.
            document.put(KEY_SAVED_AT, System.currentTimeMillis())
            File(context.filesDir, FILE_NAME).writeText(document.toString())
            lastWritten = fingerprint
        } catch (e: JSONException) {
            // Nothing to do but leave the previous snapshot in place.
        } catch (e: IOException) {
            // Nothing to do but leave the previous snapshot in place.
        }
    }

    // ---------------------------------------------------------------- restore

    /**
     * The state recorded for [activity], or null. Each entry is handed out once: a second screen of
     * the same class is a screen the user opened themselves, not the one being restored.
     */
    @JvmStatic
    fun claim(activity: Activity): Bundle? {
        if (!isEnabled(activity)) {
            return null
        }
        // A replayed screen was handed its state on its own intent, so it never consults the pool.
        // Removed as it is read: one screen, one restore.
        val intent = activity.intent
        val carried = intent?.getBundleExtra(EXTRA_REPLAY_STATE)
        if (carried != null) {
            intent.removeExtra(EXTRA_REPLAY_STATE)
            return carried
        }
        load(activity)
        val pending = restoring ?: return null
        val name = activity.javaClass.name
        val iterator = pending.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.cls != name) {
                continue
            }
            // MainActivity is matched on class alone. Its intent is rewritten in place by theme and
            // account changes, so its extras are not a stable identity.
            //
            // Everything else goes through isSameScreen, which prefers the recorded identifier. It
            // matters here for the same reason it does on rotation: this runs in onCreate, and a
            // screen the user opens themselves during the window before the first capture clears
            // the pool would otherwise pay the serializing comparison on the launch path.
            if (name == MainActivity::class.java.name || isSameScreen(entry, activity)) {
                iterator.remove()
                return entry.state
            }
        }
        return null
    }

    /**
     * The screens that sat above [MainActivity], as intents to replay, or null when there is
     * nothing to replay.
     *
     * Returns null rather than a partial stack if any class has gone: a renamed activity would
     * otherwise leave the launcher icon dropping the user somewhere arbitrary forever.
     */
    @JvmStatic
    fun buildRestoreIntents(context: Context): Array<Intent>? {
        if (!isEnabled(context) || replayed) {
            return null
        }
        replayed = true
        load(context)
        val pending = restoring ?: return null
        // Filtered by class rather than sliced from index 1: MainActivity claims its own state
        // before it asks for this, and claiming removes the entry, so "everything after the first"
        // is not the same set it was a moment ago.
        val above = pending.filter { it.cls != MainActivity::class.java.name }
        if (above.isEmpty()) {
            return null
        }
        val intents = ArrayList<Intent>(above.size)
        for (entry in above) {
            val cls =
                try {
                    Class.forName(entry.cls)
                } catch (e: ClassNotFoundException) {
                    return null
                }
            intents.add(
                Intent(context, cls).apply {
                    entry.extras?.let { putExtras(it) }
                    entry.data?.let { data = it.toUri() }
                    // The state travels with the launch rather than waiting in memory to be
                    // claimed. These screens are created after this one has already paused, and
                    // pausing writes a new snapshot -- which replaces the old one outright, pool
                    // included. Handing the state over here is what lets that stay true.
                    entry.state?.let { putExtra(EXTRA_REPLAY_STATE, it) }
                }
            )
        }
        // Handed off, so nothing is left for them to claim and no screen can take one twice.
        pending.removeAll(above)
        return intents.toTypedArray()
    }

    /**
     * Rebuild the live stack under [activity] from the snapshot, for the recents path where the
     * system recreates only the top screen.
     *
     * Only ever the first screen of a process, and never matched against the snapshot's index 0: a
     * deep link opening a post must not conjure a feed underneath itself.
     */
    @JvmStatic
    fun seedFromSnapshot(activity: Activity) {
        // canSeed first, and isEnabled last: this runs from BaseActivity.onCreate, so it is on the
        // path of every screen the user opens. canSeed is false from the first screen's onStart
        // onwards, which makes it a field read that settles the question for the whole session,
        // where isEnabled is two SharedPreferences lookups and a key to build.
        if (!canSeed || live.size > 1 || !isEnabled(activity)) {
            return
        }
        load(activity)
        val pending = restoring ?: return
        val name = activity.javaClass.name
        val index = (1 until pending.size).firstOrNull { pending[it].cls == name } ?: return
        val below =
            pending.subList(0, index).map { recorded ->
                // The identity travels with the copy. Without it a seeded entry has none, and the
                // first rotation of one of these screens falls back to the comparison that
                // serializes -- on the recents path, where the whole stack is already being rebuilt.
                //
                // The source is named rather than left as `it`: inside `apply` the receiver is
                // `this`, so `it` would still be the source here, but a later edit that turned the
                // block into one taking `it` would silently make this a self-assignment that
                // compiles and quietly drops the identity again.
                Entry(recorded.cls, recorded.extras, recorded.data, recorded.state)
                    .apply { identity = recorded.identity }
            }
        live.addAll(0, below)
    }

    // ---------------------------------------------------------------- clearing

    /**
     * Forget the current account's snapshot and the posts it points at, leaving every other
     * account's alone.
     */
    @JvmStatic
    fun clear(context: Context) {
        clearAccount(context, currentAccount(context))
    }

    /**
     * Forget one named account's snapshot and the posts it points at, leaving every other account's
     * alone.
     *
     * The setting is per-account, so this has to be too: one account turning it off used to throw
     * away another account's cached feeds, which cost them a full refetch for a choice they never
     * made. The snapshot file is shared, so it is deleted only when it is theirs -- and if it is
     * somebody else's it was already unreadable for this account, since [fromJson] rejects a
     * snapshot stamped with a different one.
     *
     * Called when an account turns the setting off, deletes its resume data from Account Settings
     * Management, is logged out of, or is removed. Not on a plain account switch: the incoming
     * account cannot read the outgoing one's snapshot, and the outgoing one's cached feeds are
     * theirs to come back to.
     */
    @JvmStatic
    fun clearAccount(context: Context, accountName: String?) {
        val account = AccountScope.namespace(accountName)
        if (account == AccountScope.namespace(currentAccount(context))) {
            // This process may already be holding the snapshot in memory, and everything it says is
            // about to stop being true.
            restoring = null
            loaded = true
            loadedAccount = null
            canSeed = false
            lastWritten = null
        }
        FeedCache.clearAccount(account)
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) {
            return
        }
        val owner =
            try {
                AccountScope.namespace(JSONObject(file.readText()).optString(KEY_ACCOUNT))
            } catch (e: JSONException) {
                account // unreadable: it can do nobody any good, so let it go
            } catch (e: IOException) {
                null
            }
        if (owner == account) {
            file.delete()
        }
    }

    // ---------------------------------------------------------------- codec

    private fun load(context: Context) {
        val account = currentAccount(context)
        if (loaded && loadedAccount == account) {
            return
        }
        loaded = true
        loadedAccount = account
        restoring = null
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) {
            return
        }
        restoring =
            try {
                fromJson(file.readText(), account)
            } catch (e: IOException) {
                null
            }
    }

    /**
     * The snapshot as a document, without its timestamp.
     *
     * The timestamp is added by the caller, immediately before writing, so that this string can be
     * compared against the last one written to decide whether writing is needed at all. Built into
     * the document here it would change on every call and defeat that comparison entirely.
     */
    private fun toJson(snapshot: List<Entry>, account: String): JSONObject? {
        return try {
            val stack = JSONArray()
            for (entry in snapshot) {
                val obj = JSONObject()
                obj.put(KEY_CLASS, entry.cls)
                entry.data?.let { obj.put(KEY_DATA, it) }
                entry.identity?.let { obj.put(KEY_IDENTITY, it) }
                entry.extras?.let { extras ->
                    obj.put(KEY_EXTRAS, BundleJson.toJson(extras, lenient = false) ?: return null)
                }
                entry.state?.let { state -> obj.put(KEY_STATE, BundleJson.toJson(state, lenient = true)) }
                stack.put(obj)
            }
            JSONObject()
                .put(KEY_VERSION, VERSION)
                .put(KEY_ACCOUNT, account)
                .put(KEY_STACK, stack)
        } catch (e: JSONException) {
            null
        }
    }

    private fun fromJson(json: String, account: String): MutableList<Entry>? {
        val root =
            try {
                JSONObject(json)
            } catch (e: JSONException) {
                return null
            }
        // Discarded, never migrated: an older document describes a stack this build may not have.
        if (root.optInt(KEY_VERSION, 0) != VERSION) {
            return null
        }
        // A snapshot belongs to the account that made it. Restoring one user's feed and inbox into
        // another user's session would show them somebody else's content.
        if (root.optString(KEY_ACCOUNT) != account) {
            return null
        }
        val stack = root.optJSONArray(KEY_STACK) ?: return null
        val entries = mutableListOf<Entry>()
        for (i in 0 until stack.length()) {
            val obj = stack.optJSONObject(i) ?: return null
            val cls = obj.optString(KEY_CLASS).ifEmpty { return null }
            entries.add(
                Entry(
                    cls,
                    obj.optJSONObject(KEY_EXTRAS)?.let { BundleJson.toBundle(it) },
                    obj.optString(KEY_DATA).ifEmpty { null },
                    obj.optJSONObject(KEY_STATE)?.let { BundleJson.toBundle(it) },
                ).apply { identity = obj.optString(KEY_IDENTITY).ifEmpty { null } }
            )
        }
        if (entries.isEmpty() || entries[0].cls != MainActivity::class.java.name) {
            return null
        }
        return entries
    }

    /**
     * The extras this screen would be recorded and relaunched with.
     *
     * A screen carrying a Parcelable it can rebuild from an identifier says so through
     * [ResumeLaunchExtras]; everything else is recorded with the extras it was launched with.
     */
    private fun launchExtrasOf(activity: Activity): Bundle? {
        val extras =
            try {
                if (activity is ResumeLaunchExtras) {
                    activity.resumeLaunchExtras()
                } else {
                    activity.intent?.extras
                }
            } catch (e: RuntimeException) {
                // resumeLaunchExtras() is the screen's own code, and three screens answer it by
                // running a post, a filter or a multireddit through Gson. Contained here rather
                // than at each call site because the callers cannot afford a throw and do not
                // agree on where they run: describing happens on the describe executor, where an
                // escaping exception would kill the task; matching a snapshot entry and adopting
                // one across a rebuild both happen on a lifecycle callback, where it would take the
                // app down mid-transition. Null is already the answer meaning "cannot be rebuilt",
                // so every caller degrades correctly: the snapshot truncates here, and nothing
                // matches or is adopted.
                null
            }
        if (extras == null || !extras.containsKey(EXTRA_REPLAY_STATE)) {
            return extras
        }
        // A replay carries the screen's recorded state on the same intent. It is not part of what
        // identifies the screen, and it is a nested Bundle, which the codec refuses -- left in, it
        // would make every replayed screen unrecordable and truncate the next snapshot there.
        return Bundle(extras).apply { remove(EXTRA_REPLAY_STATE) }
    }

    /** [ResumeLaunchExtras.resumeIdentity], or null for a screen that does not offer one. */
    private fun identityOf(activity: Activity): String? {
        if (activity !is ResumeLaunchExtras) {
            return null
        }
        val key =
            try {
                activity.resumeIdentity()
            } catch (e: RuntimeException) {
                // A screen's own code, on a lifecycle callback, where a throw takes the app down
                // mid-transition. Null degrades to the full comparison, which is correct either way.
                null
            } ?: return null
        // The screen names only what the codec cannot see -- the field inside the Parcelable it was
        // launched with. Everything else that distinguishes two of these screens is a primitive
        // extra, and those are added here, encoded leniently so the Parcelable itself is dropped
        // rather than serialized.
        //
        // Combining them centrally rather than asking each screen for the whole answer is what
        // makes this at least as discriminating as the comparison it replaces. Enumerating extras
        // per screen was weaker: the search screen's identity left out the initial sort type, so
        // two searches for the same query in the same multireddit, ordered differently, looked
        // identical and one would have adopted the other's entry.
        val primitives =
            activity.intent?.extras?.let { BundleJson.toJson(it, lenient = true)?.toString() }
        // NUL as the separator, written as an escape: it cannot occur in a post id, a filter
        // name or a JSON document, so the two halves can never run together into a string
        // that also spells some other screen's identity.
        return key + '\u0000' + primitives
    }

    /**
     * Whether [activity] is the screen [entry] was recorded for.
     *
     * Prefers the cheap identity: on a configuration change the rebuilt screen carries the same
     * intent, so the same identifier comes back out of it, and no `Parcelable` has to be serialized
     * to find that out. Both sides must have one -- an entry recorded before this existed, or a
     * screen with no cheap answer, falls through to the full comparison rather than guessing.
     */
    private fun isSameScreen(entry: Entry, activity: Activity): Boolean {
        val recorded = entry.identity
        if (recorded != null) {
            val current = identityOf(activity)
            if (current != null) {
                return recorded == current
            }
        }
        return extrasMatch(entry.extras, activity)
    }

    /**
     * Whether [extras] describe the same launch as [activity] was started with.
     *
     * Compared through [launchExtrasOf] rather than against the raw intent, because that is what
     * was recorded. A [ResumeLaunchExtras] screen rewrites its own extras -- swapping a Parcelable
     * for something storable -- so comparing the rewritten record against the raw intent made those
     * screens fail to recognise themselves. That never showed on the launcher path, where the
     * replay hands them the rewritten extras to begin with, only on the recents path, where the
     * system recreates the top screen with the intent it originally had.
     */
    private fun extrasMatch(extras: Bundle?, activity: Activity): Boolean {
        val actual = launchExtrasOf(activity)
        if (extras == null) {
            return actual == null || actual.isEmpty
        }
        if (actual == null) {
            return extras.isEmpty
        }
        return BundleJson.sameContents(extras, actual)
    }

    /**
     * Forget everything this process is holding, so one test's snapshot cannot reach the next.
     *
     * The state here is process-global on purpose -- there is one activity stack -- which in a test
     * runner means one shared object across every test in the class.
     */
    @VisibleForTesting
    @JvmStatic
    fun resetForTests() {
        live.clear()
        startedCount = 0
        lastWritten = null
        restoring = null
        loaded = false
        loadedAccount = null
        canSeed = true
        replayed = false
        // Restored too: these are process-global, so a class that swaps in a hand-driven executor
        // would otherwise hand it on to whichever class ran next, along with a queue nothing drains
        // any more.
        describeExecutor = defaultDescribeExecutor
        publishToMainThread = defaultPublisher
    }
}
