package ml.docilealligator.infinityforreddit.network

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [RedditMediaExtensionInterceptor] against a fake origin that serves each image under one extension
 * only, the way i.redd.it does: the right one answers 200 and every other one 404.
 */
class RedditMediaExtensionInterceptorTest {

    private val requested = mutableListOf<String>()

    private fun client(existing: Set<String>) = OkHttpClient.Builder()
        .addInterceptor(RedditMediaExtensionInterceptor)
        .addInterceptor(Interceptor { chain ->
            val url = chain.request().url.toString()
            requested += url
            val found = url in existing
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(if (found) 200 else 404)
                .message(if (found) "OK" else "Not Found")
                .body((if (found) "image" else "placeholder").toResponseBody())
                .build()
        })
        .build()

    private fun get(client: OkHttpClient, url: String): Response =
        client.newCall(Request.Builder().url(url).build()).execute()

    @Test
    fun `a wrong extension is answered from the one the image exists under`() {
        val response = get(client(setOf("https://i.redd.it/abc123.png")), "https://i.redd.it/abc123.jpeg")

        assertEquals(200, response.code)
        assertEquals("https://i.redd.it/abc123.png", response.request.url.toString())
        assertEquals("image", response.body.string())
        assertEquals(listOf("https://i.redd.it/abc123.jpeg", "https://i.redd.it/abc123.png"), requested)
    }

    @Test
    fun `a right extension costs no retries`() {
        val response = get(client(setOf("https://i.redd.it/abc123.jpeg")), "https://i.redd.it/abc123.jpeg")

        assertEquals(200, response.code)
        assertEquals(listOf("https://i.redd.it/abc123.jpeg"), requested)
    }

    @Test
    fun `an image missing under every extension stays the original 404`() {
        val response = get(client(emptySet()), "https://i.redd.it/abc123.jpeg")

        assertEquals(404, response.code)
        assertEquals("https://i.redd.it/abc123.jpeg", response.request.url.toString())
        assertEquals(RedditMediaExtensionInterceptor.EXTENSIONS.map { "https://i.redd.it/abc123.$it" }, requested)
    }

    @Test
    fun `a 404 from any other host passes through`() {
        val response = get(client(setOf("https://preview.redd.it/abc123.png")), "https://preview.redd.it/abc123.jpeg")

        assertEquals(404, response.code)
        assertEquals(listOf("https://preview.redd.it/abc123.jpeg"), requested)
    }

    @Test
    fun `a download learns the extension it was actually served under`() {
        val served = get(client(setOf("https://i.redd.it/abc123.png")), "https://i.redd.it/abc123.jpeg").request.url

        assertEquals("png", RedditMediaExtensionInterceptor.substitutedExtension("https://i.redd.it/abc123.jpeg", served))
    }

    @Test
    fun `a file served as asked, or not an i-redd-it image, reports no substitution`() {
        assertEquals(null, RedditMediaExtensionInterceptor.substitutedExtension(
            "https://i.redd.it/abc123.png", "https://i.redd.it/abc123.png".toHttpUrl()))
        assertEquals(null, RedditMediaExtensionInterceptor.substitutedExtension(
            "https://i.redd.it/abc123.PNG", "https://i.redd.it/abc123.png".toHttpUrl()))
        assertEquals(null, RedditMediaExtensionInterceptor.substitutedExtension(
            "https://v.redd.it/abc123.jpeg", "https://v.redd.it/abc123.png".toHttpUrl()))
        assertEquals(null, RedditMediaExtensionInterceptor.substitutedExtension(
            "https://i.redd.it/abc123.jpeg", "https://i.redd.it/xyz789.png".toHttpUrl()))
        assertEquals(null, RedditMediaExtensionInterceptor.substitutedExtension(
            null, "https://i.redd.it/abc123.png".toHttpUrl()))
    }

    @Test
    fun `a path that is not a bare media file passes through`() {
        get(client(setOf("https://i.redd.it/dir/abc123.png")), "https://i.redd.it/dir/abc123.jpeg")
        get(client(setOf("https://i.redd.it/abc123.png")), "https://i.redd.it/abc123.mp4")

        assertEquals(listOf("https://i.redd.it/dir/abc123.jpeg", "https://i.redd.it/abc123.mp4"), requested)
    }
}
