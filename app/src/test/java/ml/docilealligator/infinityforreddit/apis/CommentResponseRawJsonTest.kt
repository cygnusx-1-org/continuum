package ml.docilealligator.infinityforreddit.apis

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory

/**
 * Reddit html-escapes '>', '<' and '&' in every JSON string unless the request carries
 * `raw_json=1`. The comment that `api/comment` and `api/editusertext` send back is parsed straight
 * into the local Comment, whose markdown pre-fills Edit, so without the flag a quoted line came back
 * as "&gt;" and was re-sent that way on save (issue #497). These pin the flag on the URL Retrofit
 * actually builds for both calls, and that the endpoint path itself is unchanged.
 */
class CommentResponseRawJsonTest {

    private var captured: Request? = null

    private val api: RedditAPI = Retrofit.Builder()
        .baseUrl("https://oauth.reddit.com/")
        .client(OkHttpClient.Builder().addInterceptor { chain ->
            captured = chain.request()
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("")
                .body("{}".toResponseBody("application/json".toMediaType()))
                .build()
        }.build())
        .addConverterFactory(ScalarsConverterFactory.create())
        .build()
        .create(RedditAPI::class.java)

    private val params = mapOf("api_type" to "json", "thing_id" to "t1_abc", "text" to "> quoted")

    @Test
    fun sendCommentAsksForRawJson() {
        api.sendCommentOrReplyToMessage(emptyMap(), params).execute()
        assertRawJson("/api/comment")
    }

    @Test
    fun editCommentAsksForRawJson() {
        api.editPostOrComment(emptyMap(), params).execute()
        assertRawJson("/api/editusertext")
    }

    private fun assertRawJson(expectedPath: String) {
        val url = checkNotNull(captured) { "no request was made" }.url
        assertEquals(expectedPath, url.encodedPath)
        assertEquals("1", url.queryParameter("raw_json"))
    }
}
