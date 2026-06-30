package ml.docilealligator.infinityforreddit.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import ml.docilealligator.infinityforreddit.utils.OAuthLoginHelper.FailureType;
import ml.docilealligator.infinityforreddit.utils.OAuthLoginHelper.TokenResult;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import retrofit2.Response;

/**
 * JSON-body classification tests that MUST run against Android's {@code org.json} to reflect
 * production. Robolectric supplies the real Android JSON implementation; a plain JUnit test would
 * use the stub (returns defaults) or a desktop org.json (coerces JSON null to ""), and would not
 * catch the {@code optString}-coerces-null-to-"null" bug.
 *
 * <p>The stock {@link Application} is used instead of the app's Infinity Application — these tests
 * only need the JSON runtime, not the Dagger graph or the global EventBus it installs.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, application = Application.class)
public class OAuthLoginHelperJsonTest {

    private static Response<String> success(String body) {
        return Response.success(body);
    }

    private static TokenResult classify(String body) {
        return OAuthLoginHelper.classifyTokenResponse(success(body));
    }

    @Test
    public void emptyJsonObjectIsMissingTokens() {
        assertEquals(FailureType.MISSING_TOKENS, classify("{}").failureType);
    }

    @Test
    public void accessTokenOnlyIsMissingTokens() {
        assertEquals(FailureType.MISSING_TOKENS, classify("{\"access_token\":\"at\"}").failureType);
    }

    @Test
    public void refreshTokenOnlyIsMissingTokens() {
        assertEquals(FailureType.MISSING_TOKENS, classify("{\"refresh_token\":\"rt\"}").failureType);
    }

    @Test
    public void blankTokenStringIsMissingTokens() {
        assertEquals(FailureType.MISSING_TOKENS,
                classify("{\"access_token\":\"\",\"refresh_token\":\"rt\"}").failureType);
    }

    @Test
    public void explicitNullTokensAreMissingTokens() {
        // The headline bug: Android's optString returns the string "null" for a JSON null, so without
        // the isNull guard this would be accepted as a valid token "null". Only the real Android
        // parser exposes it — hence Robolectric.
        TokenResult result = classify("{\"access_token\":null,\"refresh_token\":null}");
        assertEquals(FailureType.MISSING_TOKENS, result.failureType);
        assertNull(result.accessToken);
    }

    @Test
    public void nullRefreshTokenIsMissingTokens() {
        assertEquals(FailureType.MISSING_TOKENS,
                classify("{\"access_token\":\"at\",\"refresh_token\":null}").failureType);
    }

    @Test
    public void fullValidResponseIsSuccess() {
        TokenResult result = classify(
                "{\"access_token\":\"at\",\"token_type\":\"bearer\",\"expires_in\":3600,"
                        + "\"scope\":\"*\",\"refresh_token\":\"rt\"}");
        assertTrue(result.isSuccess());
        assertEquals("at", result.accessToken);
        assertEquals("rt", result.refreshToken);
    }

    @Test
    public void stringErrorFieldIsRedditError() {
        TokenResult result = classify("{\"error\":\"invalid_grant\"}");
        assertEquals(FailureType.REDDIT_ERROR, result.failureType);
        assertEquals("invalid_grant", result.redditError);
    }

    @Test
    public void numericErrorFieldIsRedditError() {
        // Reddit sometimes returns {"message":"Unauthorized","error":401}.
        assertEquals(FailureType.REDDIT_ERROR, classify("{\"message\":\"Unauthorized\",\"error\":401}").failureType);
    }

    @Test
    public void explicitNullErrorIsNotARedditError() {
        // {"error":null} is not a real error; it should fall through to the (missing) token check.
        assertEquals(FailureType.MISSING_TOKENS, classify("{\"error\":null}").failureType);
    }

    @Test
    public void nonJsonBodyIsMalformed() {
        assertEquals(FailureType.MALFORMED_JSON, classify("not json at all").failureType);
    }

    @Test
    public void whitespaceBodyIsMalformed() {
        // Non-empty but not valid JSON -> JSONException -> malformed.
        assertEquals(FailureType.MALFORMED_JSON, classify("   ").failureType);
    }
}
