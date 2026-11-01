package ml.docilealligator.infinityforreddit.comment

import android.app.Application
import kotlinx.coroutines.runBlocking
import ml.docilealligator.infinityforreddit.account.Account
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Response
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Pins [AnonymousCommentLookup] against what Reddit returns while anonymous `/api/info` is blocked
 * (issue #476): a 403 HTML page from `/api/info`, and from a focused thread the post in the first
 * listing and the comment as the only child of the second, without the post's title.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class AnonymousCommentLookupTest {
    private val infoRequests = ArrayList<String>()
    private val commentRequests = ArrayList<Pair<String, String>>()
    private val infoBlocked = AtomicBoolean(false)

    private fun lookup(
        info: (String) -> Response<String> = { blocked() },
        comment: (String, String) -> Response<String> = { postId, commentId -> ok(thread(postId, commentId)) }
    ) = AnonymousCommentLookup(
        { fullnames -> synchronized(infoRequests) { infoRequests.add(fullnames) }; info(fullnames) },
        { postId, commentId ->
            synchronized(commentRequests) { commentRequests.add(postId to commentId) }
            comment(postId, commentId)
        },
        infoBlocked
    )

    private fun saved(commentId: String, postId: String = "p_$commentId") =
        AnonymousSavedComment(Account.ANONYMOUS_ACCOUNT, commentId, postId, 0L)

    @Test
    fun aWorkingInfoResponseIsReturnedAsItIs() = runBlocking {
        val infoBody = """{"kind":"Listing","data":{"children":[],"after":null}}"""

        val response = lookup(info = { ok(infoBody) }).load(listOf(saved("a"), saved("b")))

        assertEquals(infoBody, response.body())
        assertEquals(listOf("t1_a,t1_b"), infoRequests)
        assertTrue(commentRequests.isEmpty())
    }

    @Test
    fun aBlockedInfoFallsBackToEachCommentsThreadInOrder() = runBlocking {
        val response = lookup().load(listOf(saved("c", "x"), saved("a", "y"), saved("b", "z")))

        assertTrue(response.isSuccessful)
        assertEquals(listOf("c", "a", "b"), commentIds(response))
        assertEquals(setOf("x" to "c", "y" to "a", "z" to "b"), commentRequests.toSet())
        assertTrue(infoBlocked.get())
    }

    @Test
    fun theFallbackLooksAsInfoWould() = runBlocking {
        val response = lookup().load(listOf(saved("a", "post")))

        val data = children(response).getJSONObject(0).getJSONObject("data")
        assertEquals("Title of post", data.getString("link_title"))
        assertEquals("", data.getString("replies"))
    }

    @Test
    fun aCommentThatIsGoneIsLeftOut() = runBlocking {
        val response = lookup(comment = { postId, commentId ->
            if (commentId == "gone") error(404, """{"message": "Not Found", "error": 404}""")
            else ok(thread(postId, commentId))
        }).load(listOf(saved("a"), saved("gone"), saved("b")))

        assertTrue(response.isSuccessful)
        assertEquals(listOf("a", "b"), commentIds(response))
    }

    @Test
    fun aBlockOnEveryCommentIsReportedAsTheBlock() = runBlocking {
        val response = lookup(comment = { _, _ -> blocked() }).load(listOf(saved("a"), saved("b")))

        assertEquals(403, response.code())
    }

    private fun children(response: Response<String>): JSONArray =
        JSONObject(response.body()!!).getJSONObject("data").getJSONArray("children")

    private fun commentIds(response: Response<String>): List<String> {
        val children = children(response)
        return (0 until children.length()).map {
            val child = children.getJSONObject(it)
            assertEquals("t1", child.getString("kind"))
            child.getJSONObject("data").getString("id")
        }
    }

    /** A focused thread as Reddit sends it: the reply below the comment included, no link_title. */
    private fun thread(postId: String, commentId: String): String {
        val reply = JSONObject().put("kind", "t1").put("data", JSONObject().put("id", "r_$commentId"))
        val comment = JSONObject().put("kind", "t1").put(
            "data", JSONObject().put("id", commentId).put("name", "t1_$commentId")
                .put("replies", listing(reply))
        )
        val post = JSONObject().put("kind", "t3").put(
            "data", JSONObject().put("id", postId).put("name", "t3_$postId").put("title", "Title of $postId")
        )
        return JSONArray().put(listing(post)).put(listing(comment)).toString()
    }

    private fun listing(child: JSONObject) = JSONObject()
        .put("kind", "Listing")
        .put("data", JSONObject().put("children", JSONArray().put(child)).put("after", JSONObject.NULL))

    private fun ok(body: String): Response<String> = Response.success(body)

    private fun blocked(): Response<String> =
        Response.error(403, "<body class=theme-beta>blocked by network security</body>".toResponseBody("text/html".toMediaType()))

    private fun error(code: Int, body: String): Response<String> =
        Response.error(code, body.toResponseBody("application/json".toMediaType()))
}
