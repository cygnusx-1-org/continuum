package ml.docilealligator.infinityforreddit.resume

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Looper
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.time.Duration
import ml.docilealligator.infinityforreddit.TestInfinity
import ml.docilealligator.infinityforreddit.account.Account
import ml.docilealligator.infinityforreddit.account.AccountScope
import ml.docilealligator.infinityforreddit.activities.MainActivity
import ml.docilealligator.infinityforreddit.utils.SharedPreferencesUtils
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * What the snapshot refuses is as much of the feature as what it restores.
 *
 * A snapshot that is read too freely does not fail visibly -- it drops the user into somebody
 * else's session, or onto a stack with nothing underneath it, or replays their history on top of
 * itself. Each rejection below is one of those, so each is tested by writing a document that should
 * be turned down and checking that nothing comes back rather than something plausible.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestInfinity::class)
class ResumeStateTest {

    /** A stand-in for any screen the user could have had open above the feed. */
    class UpperScreenActivity : Activity()

    /** A second one, for a stack that is three deep. */
    class TopScreenActivity : Activity()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val snapshot = File(context.filesDir, "resume_state.json")

    @Before
    fun setUp() {
        ResumeState.resetForTests()
        snapshot.delete()
        signInAs("alice")
        setEnabled("alice", true)
    }

    @After
    fun tearDown() {
        ResumeState.resetForTests()
        snapshot.delete()
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        currentAccountFile().edit().clear().commit()
    }

    // ------------------------------------------------------------------ fixtures

    private fun currentAccountFile() =
        context.getSharedPreferences(
            SharedPreferencesUtils.CURRENT_ACCOUNT_SHARED_PREFERENCES_FILE, Context.MODE_PRIVATE)

    private fun signInAs(accountName: String) {
        currentAccountFile().edit()
            .putString(SharedPreferencesUtils.ACCOUNT_NAME, accountName).commit()
    }

    private fun setEnabled(accountName: String, enabled: Boolean) {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putBoolean(
                AccountScope.key(accountName, SharedPreferencesUtils.RESUME_WHERE_I_LEFT_OFF),
                enabled)
            .commit()
    }

    /** One stack entry, as the codec writes it. [state] is the screen's own note to itself. */
    private fun entry(
        cls: String,
        extras: Map<String, String> = emptyMap(),
        data: String? = null,
        state: Map<String, Int>? = null,
    ) =
        JSONObject().apply {
            put("cls", cls)
            data?.let { put("data", it) }
            if (extras.isNotEmpty()) {
                put("extras", JSONObject().apply {
                    extras.forEach { (key, value) ->
                        put(key, JSONObject().put("t", "s").put("v", value))
                    }
                })
            }
            state?.let { recorded ->
                put("state", JSONObject().apply {
                    recorded.forEach { (key, value) ->
                        put(key, JSONObject().put("t", "i").put("v", value))
                    }
                })
            }
        }

    private fun writeSnapshot(
        account: String = "alice",
        version: Int = 1,
        vararg stack: JSONObject,
    ) {
        snapshot.writeText(
            JSONObject()
                .put("version", version)
                .put("savedAt", System.currentTimeMillis())
                .put("account", account)
                .put("stack", JSONArray().apply { stack.forEach { put(it) } })
                .toString())
    }

    private val mainActivity = MainActivity::class.java.name
    private val upperScreen = UpperScreenActivity::class.java.name
    private val topScreen = TopScreenActivity::class.java.name

    private fun stackOnDisk() = JSONObject(snapshot.readText()).getJSONArray("stack")

    /**
     * A live stack of the feed and one screen above it, the feed seeded from the snapshot.
     *
     * The only way to a live stack rooted at [MainActivity] without standing a real one up, which
     * would drag the whole Dagger graph into a test about bookkeeping. [seedFromSnapshot] builds
     * its entries from the document, so what comes back is exactly what a launch would hold part
     * way through rebuilding one.
     */
    private fun seedFeedUnder(screen: UpperScreenActivity) {
        ResumeState.recordCreated(screen)
        ResumeState.seedFromSnapshot(screen)
    }

    /**
     * A live stack holding the feed and nothing else, as a launcher launch holds it at the moment
     * it asks for the screens that were above it.
     *
     * Seeded under a screen that is then dismissed, for the same reason [seedFeedUnder] exists at
     * all: standing up a real [MainActivity] would drag the whole Dagger graph into a test about
     * bookkeeping.
     */
    private fun feedAlone() {
        val placeholder = upperScreenShowing("pics")
        seedFeedUnder(placeholder)
        placeholder.finish()
        ResumeState.recordDestroyed(placeholder)
    }

    private fun upperScreenShowing(subreddit: String) =
        Robolectric.buildActivity(
            UpperScreenActivity::class.java,
            Intent(context, UpperScreenActivity::class.java).putExtra("EN", subreddit)).get()

    // ------------------------------------------------------------------ replay

    @Test
    fun `the screens above the feed come back in order`() {
        writeSnapshot(
            stack = arrayOf(
                entry(mainActivity),
                entry(upperScreen, mapOf("EN" to "pics")),
                entry(upperScreen, mapOf("EN" to "aww"))))

        val intents = ResumeState.buildRestoreIntents(context)

        assertNotNull(intents)
        assertEquals(2, intents!!.size)
        assertEquals("pics", intents[0].getStringExtra("EN"))
        assertEquals("aww", intents[1].getStringExtra("EN"))
    }

    @Test
    fun `a replay records the stack it asked for, not the order the screens arrive in`() {
        // startActivities starts only the topmost intent; the screens beneath it are added to the
        // task unstarted and created afterwards, when the visibility pass finds them showing
        // through the translucent screen above. Recording the order they arrived in wrote the stack
        // upside down -- the next launch replayed it inverted and recorded it the right way up
        // again, so the app alternated between the top two screens on every restart, forever.
        writeSnapshot(
            stack = arrayOf(
                entry(mainActivity),
                entry(upperScreen, mapOf("EN" to "pics")),
                entry(topScreen)))
        feedAlone()
        val intents = ResumeState.buildRestoreIntents(context)!!

        // Top first and the one under it second, which is the order the platform creates them in.
        ResumeState.recordCreated(
            Robolectric.buildActivity(TopScreenActivity::class.java, intents[1]).get())
        ResumeState.recordCreated(
            Robolectric.buildActivity(UpperScreenActivity::class.java, intents[0]).get())
        ResumeState.capture(context)

        val stack = stackOnDisk()
        assertEquals(3, stack.length())
        assertEquals(mainActivity, stack.getJSONObject(0).getString("cls"))
        assertEquals(upperScreen, stack.getJSONObject(1).getString("cls"))
        assertEquals(topScreen, stack.getJSONObject(2).getString("cls"))
    }

    @Test
    fun `the position a replay carries is not recorded as part of the screen`() {
        // It says where the screen goes, not which screen it is. Recorded, it would be compared
        // against extras that never had it -- so a rotation would not recognise the screen it had
        // just rebuilt, and the next launch would replay a position on top of a position.
        writeSnapshot(
            stack = arrayOf(entry(mainActivity), entry(upperScreen, mapOf("EN" to "pics"))))
        feedAlone()
        val intents = ResumeState.buildRestoreIntents(context)!!

        ResumeState.recordCreated(
            Robolectric.buildActivity(UpperScreenActivity::class.java, intents[0]).get())
        ResumeState.capture(context)

        val extras = stackOnDisk().getJSONObject(1).getJSONObject("extras")
        assertEquals(1, extras.length())
        assertEquals("pics", extras.getJSONObject("EN").getString("v"))
    }

    @Test
    fun `an intent data uri is replayed alongside the extras`() {
        // Not an extra, and lost entirely if it is not carried separately: a screen opened from a
        // link would come back with no idea what it was showing.
        writeSnapshot(
            stack = arrayOf(
                entry(mainActivity),
                entry(upperScreen, data = "https://www.reddit.com/r/pics/comments/abc/")))

        val intents = ResumeState.buildRestoreIntents(context)!!

        assertEquals("https://www.reddit.com/r/pics/comments/abc/", intents[0].data.toString())
    }

    @Test
    fun `a snapshot with only the feed replays nothing`() {
        writeSnapshot(stack = arrayOf(entry(mainActivity)))

        assertNull(ResumeState.buildRestoreIntents(context))
    }

    @Test
    fun `the stack is replayed at most once per process`() {
        // A theme or account change relaunches the feed from inside the app. Replaying again would
        // stack the user's history on top of itself.
        writeSnapshot(stack = arrayOf(entry(mainActivity), entry(upperScreen)))

        assertNotNull(ResumeState.buildRestoreIntents(context))
        assertNull(ResumeState.buildRestoreIntents(context))
    }

    @Test
    fun `a snapshot from an older version is discarded`() {
        // Discarded, never migrated: an older document describes a stack this build may not have.
        writeSnapshot(version = 0, stack = arrayOf(entry(mainActivity), entry(upperScreen)))

        assertNull(ResumeState.buildRestoreIntents(context))
    }

    @Test
    fun `another account's snapshot is discarded`() {
        // The one that would actually show somebody the wrong content: restoring one user's feed
        // and inbox into another user's session.
        writeSnapshot(account = "bob", stack = arrayOf(entry(mainActivity), entry(upperScreen)))

        assertNull(ResumeState.buildRestoreIntents(context))
    }

    @Test
    fun `a snapshot not rooted at the feed is discarded`() {
        // Backing out of a restored screen would otherwise leave the user nowhere.
        writeSnapshot(stack = arrayOf(entry(upperScreen), entry(upperScreen)))

        assertNull(ResumeState.buildRestoreIntents(context))
    }

    @Test
    fun `a renamed screen discards the whole replay rather than half of it`() {
        // A partial stack would drop the user somewhere arbitrary every launch, with no way to tell
        // why, until they cleared the data.
        writeSnapshot(
            stack = arrayOf(
                entry(mainActivity),
                entry(upperScreen),
                entry("ml.docilealligator.infinityforreddit.activities.RemovedInSomeFutureBuild")))

        assertNull(ResumeState.buildRestoreIntents(context))
    }

    @Test
    fun `nothing is replayed while the setting is off`() {
        setEnabled("alice", false)
        writeSnapshot(stack = arrayOf(entry(mainActivity), entry(upperScreen)))

        assertNull(ResumeState.buildRestoreIntents(context))
    }

    @Test
    fun `the setting is read per account`() {
        // It sits on a "This account" screen, so one account turning it on must not commit the
        // others to it.
        setEnabled("bob", true)
        signInAs("bob")
        assertTrue(ResumeState.isEnabled(context))

        setEnabled("bob", false)
        signInAs("alice")
        assertTrue("alice still has it on", ResumeState.isEnabled(context))
    }

    @Test
    fun `a half-built replay does not overwrite the snapshot it came from`() {
        // A replay launches its screens bottom first and each pauses as the next covers it, and
        // pausing captures. Writing there would replace the three screens being restored with the
        // two that exist so far -- and a process killed before the top screen ever pauses (an app
        // restart from Settings, a force-stop, a crash) would then resume one screen back, with
        // every restart after that peeling off another.
        writeSnapshot(
            stack = arrayOf(entry(mainActivity), entry(upperScreen), entry(topScreen)))
        seedFeedUnder(upperScreenShowing("pics"))
        assertNotNull(ResumeState.buildRestoreIntents(context))

        ResumeState.capture(context)

        assertEquals(3, stackOnDisk().length())
    }

    @Test
    fun `the stack is recorded again once the last replayed screen arrives`() {
        // The other half of the rule above: suppressing captures for the rest of the session would
        // freeze the snapshot at the session before it.
        writeSnapshot(
            stack = arrayOf(entry(mainActivity), entry(upperScreen), entry(topScreen)))
        seedFeedUnder(upperScreenShowing("pics"))
        assertNotNull(ResumeState.buildRestoreIntents(context))
        val top = Robolectric.buildActivity(
            TopScreenActivity::class.java, Intent(context, TopScreenActivity::class.java)).get()

        ResumeState.recordCreated(top)
        ResumeState.capture(context)

        // The recorded upper screen had no extras; the live one is showing r/pics, so an entry that
        // says so is a write that happened rather than the document that was already there.
        val stack = stackOnDisk()
        assertEquals(3, stack.length())
        assertEquals("pics",
            stack.getJSONObject(1).getJSONObject("extras").getJSONObject("EN").getString("v"))
    }

    // ------------------------------------------------------------------ launch hold

    /**
     * A replay in flight, with the launch frame held exactly as MainActivity holds it.
     *
     * @return the intents the replay would start, so a test can build the screens they describe.
     */
    private fun replayInFlight(): Array<Intent> {
        writeSnapshot(
            stack = arrayOf(
                entry(mainActivity),
                entry(upperScreen, mapOf("EN" to "pics")),
                entry(topScreen)))
        feedAlone()
        val intents = ResumeState.buildRestoreIntents(context)!!
        ResumeState.holdLaunchFrame()
        return intents
    }

    @Test
    fun `nothing is held back when no replay was started`() {
        writeSnapshot(stack = arrayOf(entry(mainActivity)))
        feedAlone()

        assertNull(ResumeState.buildRestoreIntents(context))

        // The feed is the whole snapshot, so MainActivity never asks for the hold and never has to
        // wait for a screen that is not coming.
        assertFalse(ResumeState.isHoldingLaunchFrame())
    }

    @Test
    fun `the launch frame is held while the replayed stack is on its way up`() {
        replayInFlight()

        assertTrue(ResumeState.isHoldingLaunchFrame())
    }

    @Test
    fun `a screen with no replay position does not release the launch frame`() {
        // The feed itself, and anything the user opens, arrive without a recorded position. Neither
        // is the frame the launch is waiting for -- releasing on the feed's own creation would put
        // it back on screen ahead of the screen it is being held back for, which is the flash this
        // exists to remove.
        replayInFlight()
        val drawn = mutableListOf<Activity>()
        ResumeState.releaseWhenDrawn = { drawn.add(it) }
        val ordinary = upperScreenShowing("aww")

        ResumeState.recordCreated(ordinary)
        ResumeState.noteContentCreated(ordinary)

        assertTrue(drawn.isEmpty())
        assertTrue(ResumeState.isHoldingLaunchFrame())
    }

    @Test
    fun `the first replayed screen to arrive releases the launch frame once it has drawn`() {
        val intents = replayInFlight()
        val drawn = mutableListOf<Activity>()
        ResumeState.releaseWhenDrawn = { drawn.add(it) }
        val top = Robolectric.buildActivity(TopScreenActivity::class.java, intents[1]).get()

        ResumeState.recordCreated(top)
        ResumeState.noteContentCreated(top)

        // startActivities resumes only the topmost intent, so the first replayed screen to be
        // created is the one the user ends up looking at. Its frame is the one worth waiting for.
        assertEquals(listOf<Activity>(top), drawn)
        // Still held: what lowers it is the draw, not the creation.
        assertTrue(ResumeState.isHoldingLaunchFrame())

        ResumeState.releaseLaunchFrame()

        assertFalse(ResumeState.isHoldingLaunchFrame())
    }

    @Test
    fun `only the first replayed screen lines up a release`() {
        // The screens beneath the top one are created afterwards, from the visibility pass. Each
        // lining up its own release would leave the hold at the mercy of whichever drew last.
        val intents = replayInFlight()
        val drawn = mutableListOf<Activity>()
        ResumeState.releaseWhenDrawn = { drawn.add(it) }
        val top = Robolectric.buildActivity(TopScreenActivity::class.java, intents[1]).get()
        val under = Robolectric.buildActivity(UpperScreenActivity::class.java, intents[0]).get()

        ResumeState.recordCreated(top)
        ResumeState.noteContentCreated(top)
        ResumeState.recordCreated(under)
        ResumeState.noteContentCreated(under)

        assertEquals(listOf<Activity>(top), drawn)
    }

    @Test
    fun `the launch frame is let through even if no replayed screen ever draws`() {
        // A screen that finishes itself on the way up, or a replay the platform drops. Without the
        // backstop the launcher's splash would sit there for the rest of the launch.
        replayInFlight()
        ResumeState.releaseWhenDrawn = { }

        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(6))

        assertFalse(ResumeState.isHoldingLaunchFrame())
    }

    @Test
    fun `a held launch frame does not outlive the process state it belongs to`() {
        replayInFlight()

        ResumeState.resetForTests()

        assertFalse(ResumeState.isHoldingLaunchFrame())
    }

    // ------------------------------------------------------------------ claim

    @Test
    fun `a screen claims the state recorded for it`() {
        writeSnapshot(
            stack = arrayOf(
                entry(mainActivity),
                entry(upperScreen, mapOf("EN" to "pics"), state = mapOf("RP" to 2))))

        val activity = Robolectric.buildActivity(
            UpperScreenActivity::class.java,
            Intent(context, UpperScreenActivity::class.java).putExtra("EN", "pics")).get()

        val state = ResumeState.claim(activity)

        assertNotNull(state)
        assertEquals(2, state!!.getInt("RP"))
    }

    @Test
    fun `an entry is handed out only once`() {
        // A second screen of the same class is one the user opened themselves, not the one being
        // restored, and giving it the same state would scroll it somewhere they never were.
        writeSnapshot(
            stack = arrayOf(
                entry(mainActivity),
                entry(upperScreen, mapOf("EN" to "pics"), state = mapOf("RP" to 2))))
        val intent = Intent(context, UpperScreenActivity::class.java).putExtra("EN", "pics")

        assertNotNull(
            ResumeState.claim(Robolectric.buildActivity(UpperScreenActivity::class.java, intent).get()))
        assertNull(
            ResumeState.claim(Robolectric.buildActivity(UpperScreenActivity::class.java, intent).get()))
    }

    @Test
    fun `a screen showing something else does not claim the entry`() {
        writeSnapshot(
            stack = arrayOf(
                entry(mainActivity),
                entry(upperScreen, mapOf("EN" to "pics"), state = mapOf("RP" to 2))))

        val elsewhere = Robolectric.buildActivity(
            UpperScreenActivity::class.java,
            Intent(context, UpperScreenActivity::class.java).putExtra("EN", "aww")).get()

        assertNull(ResumeState.claim(elsewhere))

        // And the entry it did not match is still there for the screen it belongs to.
        val itsOwn = Robolectric.buildActivity(
            UpperScreenActivity::class.java,
            Intent(context, UpperScreenActivity::class.java).putExtra("EN", "pics")).get()
        assertNotNull(ResumeState.claim(itsOwn))
    }

    @Test
    fun `a relaunch the app asked for itself claims nothing`() {
        // Changing an API key or restoring a backup restarts the app. The user was in Settings, not
        // reopening the app, so putting them back where the restart interrupted them answers a
        // question they did not ask.
        writeSnapshot(
            stack = arrayOf(entry(mainActivity), entry(upperScreen, state = mapOf("RP" to 2))))

        val restarted = Robolectric.buildActivity(
            UpperScreenActivity::class.java,
            Intent(context, UpperScreenActivity::class.java)
                .putExtra(ResumeState.EXTRA_SKIP_RESUME, true)).get()

        assertNull(ResumeState.claim(restarted))
    }

    @Test
    fun `an ordinary launch of the same screen still claims its state`() {
        // The control for the test above: without the flag the entry matches and is handed over, so
        // it is the flag doing the work and not the comparison failing.
        writeSnapshot(
            stack = arrayOf(entry(mainActivity), entry(upperScreen, state = mapOf("RP" to 2))))

        val ordinary = Robolectric.buildActivity(
            UpperScreenActivity::class.java,
            Intent(context, UpperScreenActivity::class.java)).get()

        assertNotNull(ResumeState.claim(ordinary))
    }

    // ------------------------------------------------------------------ clearing

    @Test
    fun `clearing an account removes its snapshot`() {
        writeSnapshot(stack = arrayOf(entry(mainActivity), entry(upperScreen)))

        ResumeState.clearAccount(context, "alice")

        assertFalse(snapshot.exists())
    }

    @Test
    fun `clearing one account leaves another's snapshot alone`() {
        // The file is shared between accounts, so the deletion has to check whose it is. Turning the
        // setting off for one account used to throw away every other account's cached feeds too.
        writeSnapshot(account = "bob", stack = arrayOf(entry(mainActivity), entry(upperScreen)))

        ResumeState.clearAccount(context, "alice")

        assertTrue(snapshot.exists())
    }

    @Test
    fun `clearing the anonymous account matches however it is spelled`() {
        writeSnapshot(account = Account.ANONYMOUS_ACCOUNT, stack = arrayOf(entry(mainActivity)))

        ResumeState.clearAccount(context, "")

        assertFalse(snapshot.exists())
    }

    @Test
    fun `an unreadable snapshot is thrown away rather than kept`() {
        snapshot.writeText("{ this is not json")

        ResumeState.clearAccount(context, "alice")

        assertFalse(snapshot.exists())
    }
}
