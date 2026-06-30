package ml.docilealligator.infinityforreddit.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import ml.docilealligator.infinityforreddit.utils.OAuthLoginHelper.TokenResult;
import okhttp3.MediaType;
import okhttp3.ResponseBody;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import retrofit2.Response;

/**
 * Robolectric tests that verify the user-facing messages — including the diagnostic HTTP/Reddit
 * codes — resolve from the real string resources. The classification branching itself is covered by
 * the pure {@code OAuthLoginHelperTest}.
 */
@RunWith(RobolectricTestRunner.class)
// Use the stock Application (not Infinity) — we only need a Context for getString, and the real
// Application's onCreate installs a global EventBus that throws when reused across test methods.
@Config(sdk = 33, application = Application.class)
public class OAuthLoginHelperMessageTest {

    private final Context context = RuntimeEnvironment.getApplication();

    private static Response<String> success(String body) {
        return Response.success(body);
    }

    private static Response<String> error(int code, String body) {
        return Response.error(code, ResponseBody.create(body, (MediaType) null));
    }

    private String messageFor(Response<String> response) {
        TokenResult result = OAuthLoginHelper.classifyTokenResponse(response);
        return OAuthLoginHelper.describeFailure(context, result);
    }

    @Test
    public void unauthorizedMessageIncludesHttpCode() {
        String message = messageFor(error(401, "Unauthorized"));
        assertTrue(message, message.contains("Invalid API Client ID"));
        assertTrue(message, message.contains("401"));
    }

    @Test
    public void rateLimitedMessage() {
        assertTrue(messageFor(error(429, "slow down")).contains("rate-limiting"));
    }

    @Test
    public void serverErrorMessageIncludesHttpCode() {
        String message = messageFor(error(500, "boom"));
        assertTrue(message, message.contains("server error"));
        assertTrue(message, message.contains("500"));
    }

    @Test
    public void genericHttpMessageIncludesHttpCode() {
        String message = messageFor(error(418, "teapot"));
        assertTrue(message, message.contains("Could not retrieve login token"));
        assertTrue(message, message.contains("418"));
    }

    @Test
    public void emptyResponseMessage() {
        assertTrue(messageFor(success("")).contains("empty login response"));
    }

    @Test
    public void redditErrorMessageIncludesRawError() {
        assertTrue(messageFor(success("{\"error\":\"invalid_grant\"}")).contains("invalid_grant"));
    }

    @Test
    public void missingTokensMessage() {
        // The issue #246 / "{}" case now gets a correct, specific message.
        assertTrue(messageFor(success("{}")).contains("did not contain an access token"));
    }

    @Test
    public void malformedJsonMessage() {
        assertEquals("Error occurred when parsing the JSON response", messageFor(success("nope")));
    }

    @Test
    public void networkMessage() {
        TokenResult result = OAuthLoginHelper.classifyThrowable(new java.io.IOException("reset"));
        assertTrue(OAuthLoginHelper.describeFailure(context, result).contains("Network error"));
    }

    @Test
    public void unknownThrowableMessage() {
        TokenResult result = OAuthLoginHelper.classifyThrowable(new IllegalStateException());
        assertEquals("Error Retrieving the token", OAuthLoginHelper.describeFailure(context, result));
    }
}
