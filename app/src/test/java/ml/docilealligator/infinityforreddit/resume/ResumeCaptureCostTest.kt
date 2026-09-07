package ml.docilealligator.infinityforreddit.resume

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.google.gson.Gson
import java.io.File
import ml.docilealligator.infinityforreddit.TestInfinity
import ml.docilealligator.infinityforreddit.account.AccountScope
import ml.docilealligator.infinityforreddit.activities.MainActivity
import ml.docilealligator.infinityforreddit.post.ParsePost
import ml.docilealligator.infinityforreddit.post.Post
import ml.docilealligator.infinityforreddit.postfilter.PostFilter
import ml.docilealligator.infinityforreddit.utils.SharedPreferencesUtils
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * What a capture costs on the main thread once the setting is on.
 *
 * [ResumeStateCostTest] covers the other half -- that a user who never turns the feature on is not
 * charged for it -- and stops deliberately short of the expensive part. Its own comment says why:
 * a stack that is not rooted at [MainActivity] is refused at the root check before the encode and
 * the file write are reached, and standing up a real [MainActivity] would have measured Dagger and
 * view inflation instead of anything to do with this feature.
 *
 * This file gets past that without a real [MainActivity]. `seedFromSnapshot` builds its entries from
 * the JSON document rather than from live activities, which is exactly what the recents path needs
 * it to do -- so seeding a snapshot rooted at [MainActivity] under one real screen produces a live
 * stack that passes the root check, and `capture` runs all the way through `toJson` and
 * `writeText`. Every timing below is therefore of the whole main-thread path, not a prefix of it.
 *
 * **What these numbers are and are not.** This is a desktop JVM under Robolectric writing to a
 * desktop filesystem. A millisecond here is not a millisecond on a phone, and nothing in this file
 * can tell you whether a frame was dropped -- that needs `dumpsys gfxinfo framestats` on a device,
 * driving the same journey with the setting off and on. What these do catch, deterministically and
 * without flaking, is the shape of the work: that the write is skipped when nothing changed, that
 * the snapshot stays kilobytes rather than megabytes, and that the cost tracks stack depth rather
 * than what any screen is showing. Bounds are set far above anything real so they fail on an
 * algorithmic mistake and never on a slow machine.
 *
 * Measurements are appended to `app/build/reports/resume-cost.txt`, because a bound that passes
 * tells you nothing about the size of the number underneath it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestInfinity::class)
class ResumeCaptureCostTest {

    /** A screen recorded in the snapshot but never rebuilt, as the seeded stack below the top is. */
    class SeededScreenActivity : Activity()

    /** A top screen with nothing of its own to record, so two captures produce the same document. */
    class StaticScreenActivity : Activity()

    /** A screen whose recorded position moves, so no two captures write the same document. */
    class ScrollingScreenActivity : Activity(), Restorable {
        private var position = 0

        override fun saveResumeState(out: Bundle) {
            out.putInt("anchorPosition", position++)
            out.putString("anchorFullname", "t3_abc123")
            out.putInt("anchorOffset", -48)
        }

        override fun restoreResumeState(state: Bundle) = Unit
    }

    /**
     * The gallery screen's describe, copied rather than referenced.
     *
     * `ViewRedditGalleryActivity.resumeLaunchExtras` swaps the `Parcelable` post it was launched
     * with for the post's JSON, because a marshalled `Parcel` must never be written to disk. That
     * `new Gson().toJson(post)` is the single most expensive thing this feature does on the main
     * thread, and it is what this stand-in reproduces. Using the real activity would drag in its
     * whole `onCreate`; the operation being measured is identical.
     */
    class GalleryLikeActivity : Activity(), ResumeLaunchExtras {
        // The same deprecated overload the real screen uses: matching it is the point.
        @Suppress("DEPRECATION")
        override fun resumeLaunchExtras(): Bundle? {
            val extras = intent.extras ?: return null
            val post = intent.getParcelableExtra<Post>(EXTRA_POST) ?: return extras
            val out = Bundle(extras)
            out.remove(EXTRA_POST)
            out.putString(EXTRA_POST_JSON, Gson().toJson(post))
            return out
        }

        companion object {
            const val EXTRA_POST = "EP"
            const val EXTRA_POST_JSON = "EPJ"
        }
    }

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val snapshot = File(context.filesDir, "resume_state.json")

    /** Held strongly: the live stack keeps only weak references, and a cleared one ends a capture. */
    private val alive = mutableListOf<Activity>()

    @Before
    fun setUp() {
        ResumeState.resetForTests()
        alive.clear()
        snapshot.delete()
        currentAccountFile().edit()
            .putString(SharedPreferencesUtils.ACCOUNT_NAME, "alice").commit()
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putBoolean(
                AccountScope.key("alice", SharedPreferencesUtils.RESUME_WHERE_I_LEFT_OFF), true)
            .commit()
    }

    @After
    fun tearDown() {
        ResumeState.resetForTests()
        alive.clear()
        snapshot.delete()
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        currentAccountFile().edit().clear().commit()
    }

    // ------------------------------------------------------------------ fixtures

    private fun currentAccountFile() =
        context.getSharedPreferences(
            SharedPreferencesUtils.CURRENT_ACCOUNT_SHARED_PREFERENCES_FILE, Context.MODE_PRIVATE)

    /** One recorded screen, in the shape the codec writes and reads. */
    private fun entry(cls: String, extras: Map<String, String>, state: Map<String, Int>) =
        JSONObject().apply {
            put("cls", cls)
            put("extras", JSONObject().apply {
                extras.forEach { (k, v) -> put(k, JSONObject().put("t", "s").put("v", v)) }
            })
            put("state", JSONObject().apply {
                state.forEach { (k, v) -> put(k, JSONObject().put("t", "i").put("v", v)) }
            })
        }

    /** A screen of the size the real ones record: a handful of extras and a scroll position. */
    private fun typicalEntry(cls: String, index: Int) =
        entry(
            cls,
            extras = mapOf(
                "EN" to "subredditName$index",
                "ESN" to "AskReddit",
                "ESI" to "https://styles.redditmedia.com/t5_2qh1i/styles/communityIcon.png",
            ),
            state = mapOf("page" to index, "anchorPosition" to index * 7, "anchorOffset" to -32),
        )

    private fun writeSnapshot(vararg stack: JSONObject) {
        snapshot.writeText(
            JSONObject()
                .put("version", 1)
                .put("savedAt", System.currentTimeMillis())
                .put("account", "alice")
                .put("stack", JSONArray().apply { stack.forEach { put(it) } })
                .toString())
    }

    /**
     * Put [top] on the live stack with [depth] recorded screens beneath it, the lowest of them
     * [MainActivity], and return it.
     *
     * This is the recents path: the system rebuilds only the top screen, and `seedFromSnapshot`
     * restores what was under it from the document. It is the only way to reach the encode and the
     * write without constructing a real [MainActivity].
     */
    private fun <T : Activity> stackOf(top: Class<T>, depth: Int, extras: Bundle = Bundle()): T {
        // seedFromSnapshot matches the top screen against the FIRST entry of its class, so a top
        // that shares a class with the filler beneath it matches at index 1 and seeds one screen
        // instead of depth-1. The stack would come out shallower than asked for, and the test
        // measuring it would say nothing it claimed to.
        require(top != SeededScreenActivity::class.java) {
            "SeededScreenActivity is the filler class; a top screen needs its own"
        }
        val below = buildList {
            add(typicalEntry(MainActivity::class.java.name, 0))
            repeat(depth - 2) { add(typicalEntry(SeededScreenActivity::class.java.name, it + 1)) }
        }
        writeSnapshot(*(below + entry(top.name, emptyMap(), emptyMap())).toTypedArray())

        val intent = Intent(context, top).putExtras(extras)
        val activity = Robolectric.buildActivity(top, intent).get()
        alive.add(activity)
        ResumeState.recordCreated(activity)
        ResumeState.seedFromSnapshot(activity)
        return activity
    }

    /** A gallery post of [images] items, parsed from listing JSON exactly as the feed parses one. */
    private fun galleryPost(images: Int): Post {
        val id = "abc123"
        val mediaIds = (0 until images).map { "media$it" }
        val data = JSONObject().apply {
            put("id", id)
            put("name", "t3_$id")
            put("subreddit", "pics")
            put("subreddit_name_prefixed", "r/pics")
            put("author", "someone")
            put("distinguished", JSONObject.NULL)
            put("created_utc", 1700000000L)
            put("title", "A gallery of $images things")
            put("score", 4211)
            put("num_comments", 318)
            put("upvote_ratio", 0.95)
            put("hidden", false)
            put("spoiler", false)
            put("over_18", false)
            put("stickied", false)
            put("archived", false)
            put("locked", false)
            put("saved", false)
            put("send_replies", true)
            put("can_mod_post", false)
            put("likes", JSONObject.NULL)
            put("permalink", "/r/pics/comments/$id/a_gallery/")
            put("thumbnail", "https://b.thumbs.redditmedia.com/thumb.jpg")
            put("domain", "reddit.com")
            put("is_video", false)
            // A real gallery's url points at /gallery/<id>, which is what routes it through the
            // no-preview-link branch and then into the gallery branch that builds the item list.
            put("url", "https://www.reddit.com/gallery/$id")
            put("selftext", "")
            put("selftext_html", JSONObject.NULL)
            put("gallery_data", JSONObject().put("items", JSONArray().apply {
                mediaIds.forEach { put(JSONObject().put("media_id", it)) }
            }))
            put("media_metadata", JSONObject().apply {
                mediaIds.forEach { mediaId ->
                    put(mediaId, JSONObject().apply {
                        put("status", "valid")
                        put("e", "Image")
                        put("m", "image/jpg")
                        put("id", mediaId)
                        put("s", JSONObject()
                            .put("x", 3024)
                            .put("y", 4032)
                            .put("u", "https://preview.redd.it/$mediaId.jpg?width=3024&format=pjpg&auto=webp&s=0123456789abcdef0123456789abcdef01234567"))
                        put("p", JSONArray().apply {
                            listOf(108, 216, 320, 640, 960, 1080).forEach { width ->
                                put(JSONObject()
                                    .put("x", width)
                                    .put("y", width * 4 / 3)
                                    .put("u", "https://preview.redd.it/$mediaId.jpg?width=$width&crop=smart&auto=webp&s=0123456789abcdef0123456789abcdef01234567"))
                            }
                        })
                    })
                }
            })
        }
        val listing = JSONObject().put("data", JSONObject()
            .put("children", JSONArray().put(JSONObject().put("kind", "t3").put("data", data)))
            .put("after", JSONObject.NULL))
        val parsed = ParsePost.parsePostsSync(
            listing, -1, PostFilter().apply { allowNSFW = true }, null)
        assertNotNull("the gallery fixture must parse", parsed)
        return parsed!!.first()
    }

    /** Median of [runs] samples in milliseconds, after [warmup] discarded ones to let the JIT settle. */
    private fun medianMs(warmup: Int = 20, runs: Int = 50, block: () -> Unit): Double {
        repeat(warmup) { block() }
        val samples = (0 until runs)
            .map {
                val start = System.nanoTime()
                block()
                (System.nanoTime() - start) / 1_000_000.0
            }
            .sorted()
        return samples[samples.size / 2]
    }

    // ------------------------------------------------------------------ the path is actually reached

    @Test
    fun `a stack rooted at the feed is encoded and written, not refused`() {
        // Everything else in this file is a timing, and a timing of a refusal is a timing of
        // nothing. This is the test that makes the rest mean something: it proves capture ran past
        // the root check at ResumeState.kt:348 and reached the write.
        val top = stackOf(ScrollingScreenActivity::class.java, depth = 4)

        ResumeState.capture(context)

        assertTrue("capture wrote no snapshot", snapshot.exists())
        val written = snapshot.readText()
        assertTrue("the feed must be the root", written.contains(MainActivity::class.java.name))
        assertTrue("the top screen must be recorded", written.contains(top.javaClass.name))
        assertTrue("the screen's own state must be recorded", written.contains("anchorFullname"))
        assertEquals(4, JSONObject(written).getJSONArray("stack").length())
    }

    @Test
    fun `an unchanged stack costs no write at all`() {
        // The lastWritten comparison at ResumeState.kt:355 is what makes most transitions free: the
        // user moves between screens far more often than the recorded position actually changes.
        // Deleting the file and capturing again is exact -- if the write were still happening, the
        // file would come back. A top screen with no state of its own, so nothing about the stack
        // moves between the two captures.
        stackOf(StaticScreenActivity::class.java, depth = 3)
        ResumeState.capture(context)
        assertTrue(snapshot.exists())
        snapshot.delete()

        ResumeState.capture(context)

        assertFalse("an unchanged stack was written again", snapshot.exists())
    }

    @Test
    fun `a stack that moved is written`() {
        // The other half of the rule, without which the short circuit above would be a bug rather
        // than an optimisation.
        stackOf(ScrollingScreenActivity::class.java, depth = 3)
        ResumeState.capture(context)
        snapshot.delete()

        ResumeState.capture(context)

        assertTrue("a moved stack was not written", snapshot.exists())
    }

    // ------------------------------------------------------------------ how big the write is

    @Test
    fun `the snapshot stays kilobytes however deep the stack`() {
        // Size is what the write costs, and it is exact rather than timed. Eight screens is deeper
        // than any stack a user builds by hand; the posts themselves are not here at all, which is
        // the whole reason this stays small -- they live in FeedCache, written on its own executor.
        stackOf(ScrollingScreenActivity::class.java, depth = 8)
        ResumeState.capture(context)
        val deep = snapshot.length()

        ResumeState.resetForTests()
        snapshot.delete()
        stackOf(ScrollingScreenActivity::class.java, depth = 2)
        ResumeState.capture(context)
        val shallow = snapshot.length()

        record("snapshot bytes, 2-deep stack: $shallow")
        record("snapshot bytes, 8-deep stack: $deep")
        assertTrue("an 8-deep snapshot was $deep bytes", deep < 16 * 1024)
    }

    // ------------------------------------------------------------------ how long the write takes

    @Test
    fun `capturing a deep stack, encode and write included, costs a fraction of a frame`() {
        // capture runs on every onActivityPaused -- every screen transition -- and writes
        // synchronously on the main thread, deliberately, because a background write loses the race
        // against process death. This is that whole path: walk, ask each screen, encode, write.
        // The screen's position moves on every call, so the lastWritten short circuit never fires
        // and each sample includes a real file write.
        stackOf(ScrollingScreenActivity::class.java, depth = 8)

        val median = medianMs { ResumeState.capture(context) }

        record("capture median ms, 8-deep stack, encode and write: $median")
        assertTrue("median capture of an 8-deep stack was ${median}ms", median < 8.0)
    }

    @Test
    fun `the shallowest stack there can be is bounded too`() {
        // Two screens is the floor -- the snapshot has to be rooted at MainActivity, so a capture
        // never walks fewer than that. Recorded next to the 8-deep figure above so the pair says
        // how the cost moves with depth. Deliberately not asserted as a ratio: both numbers are
        // fractions of a millisecond on this machine, and an ordering assertion between two
        // quantities that small measures the scheduler rather than the code.
        stackOf(ScrollingScreenActivity::class.java, depth = 2)

        val median = medianMs { ResumeState.capture(context) }

        record("capture median ms, 2-deep stack, encode and write: $median")
        assertTrue("median capture of a 2-deep stack was ${median}ms", median < 8.0)
    }

    // ------------------------------------------------------------------ the describe that is not free

    @Test
    fun `serializing a gallery post is the most expensive thing a describe does`() {
        // The gallery, filtered-posts and search screens answer resumeLaunchExtras by running a
        // whole Post, PostFilter or MultiReddit through Gson. The gallery's is the largest of the
        // three by a wide margin, so it is the one measured. Once per screen, on the main thread,
        // on the first capture after it opens -- which is a transition, with an animation running.
        val post = galleryPost(images = 20)
        assertEquals("the fixture must be a gallery", Post.GALLERY_TYPE, post.postType)
        assertEquals("the fixture must carry its items", 20, post.gallery.size)

        val json = Gson().toJson(post)
        // Accumulated rather than discarded: a timing loop whose output nothing reads is one
        // something is entitled to optimise away, and it is what lint's CheckResult is asking for.
        var sink = 0L
        val median = medianMs { sink += Gson().toJson(post).length }
        assertTrue("the timed calls produced nothing", sink > 0)

        record("gallery post JSON bytes, 20 images: ${json.length}")
        record("Gson toJson median ms, 20-image gallery post: $median")
        assertTrue("a 20-image gallery post serialized to ${json.length} bytes", json.length < 128 * 1024)
        assertTrue("serializing a 20-image gallery post took ${median}ms", median < 20.0)
    }

    @Test
    fun `the capture that describes a gallery is the expensive one, and only the first`() {
        // What the deferral bought: the Gson call lands on one transition rather than on every one.
        // Both numbers are recorded because the ratio between them is the point -- the first
        // capture pays for the describe, and every capture after it does not.
        val extras = Bundle().apply {
            putParcelable(GalleryLikeActivity.EXTRA_POST, galleryPost(images = 20))
            putInt("EGII", 3)
        }
        stackOf(GalleryLikeActivity::class.java, depth = 3, extras = extras)

        val start = System.nanoTime()
        ResumeState.capture(context)
        val first = (System.nanoTime() - start) / 1_000_000.0

        // Nothing moved, so these are the walk and the short circuit with no describe and no write.
        val steady = medianMs { ResumeState.capture(context) }

        record("capture ms, first sight of a gallery screen (describes, single sample): $first")
        record("capture median ms, same screen afterwards (no describe): $steady")
        assertTrue(
            "the gallery screen was recorded with its post as JSON",
            snapshot.readText().contains(GalleryLikeActivity.EXTRA_POST_JSON))
        assertTrue("a steady-state capture took ${steady}ms", steady < 8.0)
    }

    // ------------------------------------------------------------------ report

    companion object {
        private val report = StringBuilder()

        private fun record(line: String) {
            report.append(line).append('\n')
        }

        @JvmStatic
        @BeforeClass
        fun startReport() {
            report.setLength(0)
        }

        /**
         * A bound that passes says nothing about the size of the number under it, and Gradle does
         * not show a unit test's stdout. The measurements go to a file so they can be read after a
         * run and compared against the next one.
         */
        @JvmStatic
        @AfterClass
        fun writeReport() {
            val out = File(System.getProperty("user.dir") ?: ".", "build/reports/resume-cost.txt")
            out.parentFile?.mkdirs()
            out.writeText(
                "ResumeState main-thread cost, desktop JVM under Robolectric.\n" +
                    "Not phone milliseconds: use scripts/measure-resume-frames.sh for that.\n\n" +
                    report)
            print(out.readText())
        }
    }
}
