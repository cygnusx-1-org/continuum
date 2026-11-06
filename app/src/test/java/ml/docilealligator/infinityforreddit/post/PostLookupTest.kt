package ml.docilealligator.infinityforreddit.post

import android.app.Application
import kotlinx.coroutines.runBlocking
import ml.docilealligator.infinityforreddit.thing.InfoUse
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Response
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Pins [PostLookup] against what Reddit returned while `/api/info` was blocked (issue #476): a 403
 * HTML page from `/api/info`, the post as the only child of the first listing
 * from `/comments/<id>`, and a 404 for a post that does not exist.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class PostLookupTest {
    private val infoRequests = ArrayList<String>()
    private val postRequests = ArrayList<String>()
    private val infoBlocked = AtomicBoolean(false)

    private fun lookup(
        info: (String) -> Response<String> = { blocked() },
        post: (String) -> Response<String> = { ok(commentsListing(it)) },
        infoUse: InfoUse = InfoUse.First(infoBlocked)
    ) = PostLookup(
        { fullnames -> synchronized(infoRequests) { infoRequests.add(fullnames) }; info(fullnames) },
        { postId -> synchronized(postRequests) { postRequests.add(postId) }; post(postId) },
        infoUse
    )

    @Test
    fun withoutTheOldWayInfoIsNeverAsked() = runBlocking {
        val response = lookup(info = { throw AssertionError("asked /api/info") }, infoUse = InfoUse.Never).load(listOf("b", "a"))

        assertTrue(response.isSuccessful)
        assertEquals(listOf("b", "a"), postIds(response))
        assertTrue(infoRequests.isEmpty())
    }

    @Test
    fun aWorkingInfoResponseIsReturnedAsItIs() = runBlocking {
        val infoBody = """{"kind":"Listing","data":{"children":[],"after":null}}"""

        val response = lookup(info = { ok(infoBody) }).load(listOf("a", "b"))

        assertEquals(infoBody, response.body())
        assertEquals(listOf("t3_a,t3_b"), infoRequests)
        assertTrue(postRequests.isEmpty())
        assertFalse(infoBlocked.get())
    }

    @Test
    fun aBlockedInfoFallsBackToEachPostInOrder() = runBlocking {
        val response = lookup().load(listOf("c", "a", "b"))

        assertTrue(response.isSuccessful)
        assertEquals(listOf("c", "a", "b"), postIds(response))
        assertEquals(setOf("c", "a", "b"), postRequests.toSet())
        assertTrue(infoBlocked.get())
    }

    @Test
    fun infoIsNotAskedAgainOnceItHasRefused() = runBlocking {
        lookup().load(listOf("a"))
        lookup().load(listOf("b"))

        assertEquals(1, infoRequests.size)
        assertEquals(listOf("a", "b"), postRequests)
    }

    @Test
    fun noIdsMeansAnEmptyListingWithNoRequests() = runBlocking {
        val response = lookup().load(emptyList())

        assertTrue(response.isSuccessful)
        assertEquals(emptyList<String>(), postIds(response))
        assertTrue(infoRequests.isEmpty())
        assertTrue(postRequests.isEmpty())
    }

    @Test
    fun aPostThatIsGoneOrPrivateIsLeftOut() = runBlocking {
        val response = lookup(post = {
            when (it) {
                "gone" -> error(404, """{"message": "Not Found", "error": 404}""")
                "private" -> error(403, """{"reason": "private", "message": "Forbidden", "error": 403}""")
                else -> ok(commentsListing(it))
            }
        }).load(listOf("a", "gone", "private", "b"))

        assertTrue(response.isSuccessful)
        assertEquals(listOf("a", "b"), postIds(response))
    }

    @Test
    fun onlyGonePostsMakeAnEmptyListingRatherThanAnError() = runBlocking {
        val response = lookup(post = { error(404, """{"message": "Not Found", "error": 404}""") })
            .load(listOf("gone"))

        assertTrue(response.isSuccessful)
        assertEquals(emptyList<String>(), postIds(response))
    }

    @Test
    fun aBlockOnEveryPostIsReportedAsTheBlock() = runBlocking {
        val response = lookup(post = { blocked() }).load(listOf("a", "b"))

        assertEquals(403, response.code())
    }

    @Test
    fun aRateLimitFailsThePageSoTheRetryAsksAgain() = runBlocking {
        val response = lookup(post = {
            if (it == "b") error(429, "") else ok(commentsListing(it))
        }).load(listOf("a", "b", "c"))

        assertEquals(429, response.code())
    }

    private fun postIds(response: Response<String>): List<String> {
        val children = JSONObject(response.body()!!).getJSONObject("data").getJSONArray("children")
        return (0 until children.length()).map {
            val child = children.getJSONObject(it)
            assertEquals("t3", child.getString("kind"))
            child.getJSONObject("data").getString("id")
        }
    }

    private fun commentsListing(postId: String): String = JSONArray()
        .put(listing(JSONObject().put("kind", "t3").put("data", JSONObject().put("id", postId).put("name", "t3_$postId"))))
        .put(listing(JSONObject().put("kind", "t1").put("data", JSONObject().put("id", "c_$postId"))))
        .toString()

    private fun listing(child: JSONObject) = JSONObject()
        .put("kind", "Listing")
        .put("data", JSONObject().put("children", JSONArray().put(child)).put("after", JSONObject.NULL))

    private fun ok(body: String): Response<String> = Response.success(body)

    private fun blocked(): Response<String> =
        Response.error(403, "<body class=theme-beta>blocked by network security</body>".toResponseBody("text/html".toMediaType()))

    private fun error(code: Int, body: String): Response<String> =
        Response.error(code, body.toResponseBody("application/json".toMediaType()))
}
