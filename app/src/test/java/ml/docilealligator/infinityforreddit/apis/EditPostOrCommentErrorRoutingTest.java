package ml.docilealligator.infinityforreddit.apis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.ResponseBody;
import org.junit.Test;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.scalars.ScalarsConverterFactory;

/**
 * {@code EditPostActivity.editPost} reports success from Retrofit's {@code onResponse}. That is only
 * safe because it now checks {@link Response#isSuccessful()} first — an HTTP error reaches
 * {@code onResponse}, not {@code onFailure}, so without the check a 403 would toast "Edit
 * successful", set {@code RESULT_OK} and finish, and the caller's refresh would show the post
 * unchanged.
 *
 * <p>These tests pin that routing against the real {@link RedditAPI} declaration and the OkHttp and
 * Retrofit versions the app ships, so the check cannot be dropped as redundant later.
 */
public class EditPostOrCommentErrorRoutingTest {

    /** Serves {@code code} without a network, so the exchange completes normally at the HTTP layer. */
    private static RedditAPI apiRespondingWith(int code, String body) {
        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(chain -> new okhttp3.Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(code)
                        .message("")
                        .body(ResponseBody.create(body, MediaType.parse("application/json")))
                        .build())
                .build();

        return new Retrofit.Builder()
                .baseUrl("https://oauth.reddit.com/")
                .client(client)
                .addConverterFactory(ScalarsConverterFactory.create())
                .build()
                .create(RedditAPI.class);
    }

    private static Response<String> enqueueAndAwait(RedditAPI api, AtomicBoolean onFailureCalled)
            throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Response<String>> received = new AtomicReference<>();

        api.editPostOrComment(new HashMap<>(), new HashMap<>()).enqueue(new Callback<>() {
            @Override
            public void onResponse(Call<String> call, Response<String> response) {
                received.set(response);
                latch.countDown();
            }

            @Override
            public void onFailure(Call<String> call, Throwable t) {
                onFailureCalled.set(true);
                latch.countDown();
            }
        });

        assertTrue("callback did not fire", latch.await(10, TimeUnit.SECONDS));
        return received.get();
    }

    @Test
    public void httpErrorReachesOnResponseAndIsNotSuccessful() throws InterruptedException {
        AtomicBoolean onFailureCalled = new AtomicBoolean();
        Response<String> response = enqueueAndAwait(apiRespondingWith(403, "{\"error\": 403}"), onFailureCalled);

        // The premise EditPostActivity's guard rests on: HTTP errors are not routed to onFailure.
        assertFalse("a 403 must not be routed to onFailure", onFailureCalled.get());
        assertFalse("isSuccessful() must be false for a 403", response.isSuccessful());
        assertEquals(403, response.code());
        // ...so the pre-fix code, which only read body(), would have taken the success path.
        assertNull(response.body());
    }

    @Test
    public void serverErrorAlsoReachesOnResponse() throws InterruptedException {
        AtomicBoolean onFailureCalled = new AtomicBoolean();
        Response<String> response = enqueueAndAwait(apiRespondingWith(500, ""), onFailureCalled);

        assertFalse("a 500 must not be routed to onFailure", onFailureCalled.get());
        assertFalse(response.isSuccessful());
        assertEquals(500, response.code());
    }

    @Test
    public void successStillReachesOnResponseAsSuccessful() throws InterruptedException {
        AtomicBoolean onFailureCalled = new AtomicBoolean();
        Response<String> response = enqueueAndAwait(apiRespondingWith(200, "{}"), onFailureCalled);

        // The guard must not swallow the happy path.
        assertFalse(onFailureCalled.get());
        assertTrue(response.isSuccessful());
        assertEquals("{}", response.body());
    }
}
