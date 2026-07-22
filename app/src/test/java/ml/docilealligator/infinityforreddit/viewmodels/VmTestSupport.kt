package ml.docilealligator.infinityforreddit.viewmodels

import android.os.Looper
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.robolectric.Shadows.shadowOf
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory

/**
 * Serves [code]/[body] without a network via an OkHttp interceptor, so a Retrofit `Call<String>`
 * completes normally at the HTTP layer (same technique as EditPostOrCommentErrorRoutingTest). The
 * ViewModel-under-test calls `.create(RedditAPI)` on this instance, so its real request-building and
 * response-routing run unchanged against a canned response.
 */
fun retrofitRespondingWith(code: Int, body: String): Retrofit {
    val client = OkHttpClient.Builder()
        .addInterceptor { chain ->
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message("")
                .body(body.toResponseBody("application/json".toMediaType()))
                .build()
        }
        .build()
    return Retrofit.Builder()
        .baseUrl("https://oauth.reddit.com/")
        .client(client)
        .addConverterFactory(ScalarsConverterFactory.create())
        .build()
}

/**
 * Flushes the (Robolectric-paused) main looper repeatedly until [cond] holds, or fails after
 * [timeoutMs]. Needed because these ViewModels deliver their result via `Handler(mainLooper).post`
 * (and Retrofit's enqueue hops through a background thread first).
 */
fun awaitMainUntil(timeoutMs: Long = 5000, cond: () -> Boolean) {
    val deadline = System.currentTimeMillis() + timeoutMs
    while (System.currentTimeMillis() < deadline) {
        shadowOf(Looper.getMainLooper()).idle()
        if (cond()) return
        Thread.sleep(5)
    }
    shadowOf(Looper.getMainLooper()).idle()
    if (!cond()) throw AssertionError("condition not met within ${timeoutMs}ms")
}
