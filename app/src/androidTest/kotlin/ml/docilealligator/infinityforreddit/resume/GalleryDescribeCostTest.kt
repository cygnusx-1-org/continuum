package ml.docilealligator.infinityforreddit.resume

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.gson.Gson
import ml.docilealligator.infinityforreddit.post.ParsePost
import ml.docilealligator.infinityforreddit.post.Post
import ml.docilealligator.infinityforreddit.postfilter.PostFilter
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * What the gallery screen's describe costs on a real device, on ART.
 *
 * `ViewRedditGalleryActivity.resumeLaunchExtras()` answers by running the whole `Post` through Gson,
 * because a marshalled `Parcel` must never be written to disk. That happens on the main thread, once
 * per screen, on the first `ResumeState.capture` after the gallery opens -- which is a screen
 * transition with an animation already running. It is the single most expensive thing the resume
 * feature does, and it is the one cost that had never been measured anywhere but a desktop JVM.
 *
 * The real figure came from tracing the real screen: opening a 20-image r/pics gallery and then
 * backgrounding it produced a 209.9 ms `ResumeState.capture`, of which 192.9 ms was this describe,
 * and 175.7 ms of that was "Lock contention on Jit code cache" -- Gson's reflective machinery being
 * loaded and JIT-compiled on first use. The next describe in the same process was 0.24 ms.
 *
 * This exists so the cost can be re-measured without first having to find a gallery on the front
 * page. Driving the real screen does work -- a live r/pics listing carried 14 galleries in 100
 * posts -- but it needs the media view itself to be tapped (`gallery_recycler_view_…`, not the
 * header block), and getting that wrong opens a user profile instead.
 *
 * It is a measurement harness, not a gate. The only assertions are that the fixture really is a
 * gallery of the size asked for; the timings are logged rather than bounded, because the number
 * that matters depends on whether Gson happened to be cold (see [measure]), so no threshold here
 * would mean the same thing twice. `check` compiles this file -- `deviceTests` depends on
 * `assembleDebugAndroidTest`, so a break here fails the gate -- but it does not run it: the script
 * runs `UserProfileTest` alone unless given `--class`.
 *
 * Run with: scripts/run-device-tests.sh --class
 *   ml.docilealligator.infinityforreddit.resume.GalleryDescribeCostTest
 */
@RunWith(AndroidJUnit4::class)
class GalleryDescribeCostTest {

    private companion object {
        const val TAG = "GalleryDescribeCost"
        /** A 60Hz frame. The transition this runs on is already spending most of one. */
        const val FRAME_MS = 1000.0 / 60.0
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
                        put("s", JSONObject().put("x", 3024).put("y", 4032).put(
                            "u",
                            "https://preview.redd.it/$mediaId.jpg?width=3024&format=pjpg&auto=webp&s=0123456789abcdef0123456789abcdef01234567"))
                        put("p", JSONArray().apply {
                            listOf(108, 216, 320, 640, 960, 1080).forEach { w ->
                                put(JSONObject().put("x", w).put("y", w * 4 / 3).put(
                                    "u",
                                    "https://preview.redd.it/$mediaId.jpg?width=$w&crop=smart&auto=webp&s=0123456789abcdef0123456789abcdef01234567"))
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

    private fun measure(label: String, post: Post, firstOfRun: Boolean) {
        // The cold call is the measurement that matters, and a warm-up would destroy it: averaging
        // over warm iterations reports the 0.24 ms and hides the 192.
        //
        // What this CANNOT promise is that the cold call is cold. It measures the first call of
        // this test, not the first of the process, and the two are only the same if nothing else
        // has already exercised Gson's writer path -- which the app does in MainPageTabsUtils,
        // Account and a dozen other places, none of them under this test's control. Read the two
        // numbers together: a coldCall close to warmMedian means Gson was already warm and this run
        // says nothing about the first-open cost, while a coldCall orders of magnitude above it is
        // the real thing. The traced device run that found this recorded 192.9 ms against a 0.24 ms
        // second describe, so the gap to look for is large and unmistakable.
        val t0 = System.nanoTime()
        val json = Gson().toJson(post)
        val coldMs = (System.nanoTime() - t0) / 1_000_000.0

        // Every result is accumulated rather than discarded. Partly so lint's CheckResult is
        // satisfied honestly instead of suppressed, and partly because a timing loop whose output
        // nothing reads is a timing loop something is entitled to optimise away.
        var sink = 0L
        repeat(20) { sink += Gson().toJson(post).length }
        val samples = (0 until 50)
            .map {
                val t = System.nanoTime()
                sink += Gson().toJson(post).length
                (System.nanoTime() - t) / 1_000_000.0
            }
            .sorted()
        val warmMedian = samples[samples.size / 2]
        check(sink > 0) { "the timed calls produced nothing" }
        Log.i(TAG, "$label: bytes=${json.length} coldCall=${coldMs}ms " +
            (if (firstOfRun) "(first of this test run) " else "(Gson already warmed by an earlier size) ") +
            "warmMedian=${warmMedian}ms warmMax=${samples.last()}ms " +
            "coldOverWarm=${if (warmMedian > 0) coldMs / warmMedian else Double.NaN}x " +
            "frameBudget=${FRAME_MS}ms coldOverBudget=${coldMs > FRAME_MS}")
    }

    @Test
    fun galleryDescribeCostOnDevice() {
        // Largest first, deliberately. Only the first call of the run can be cold, and the cold
        // cost is the whole point -- measuring 3 images cold and 20 warm would put the expensive
        // measurement on the cheapest fixture and report the size that matters as 0.2 ms.
        var firstOfRun = true
        for (n in listOf(20, 7, 3)) {
            val post = galleryPost(n)
            assertEquals("fixture must be a gallery", Post.GALLERY_TYPE, post.postType)
            assertEquals("fixture must carry its items", n, post.gallery.size)
            measure("gallery_${n}_images", post, firstOfRun)
            firstOfRun = false
        }
    }
}
