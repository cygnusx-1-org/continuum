package ml.docilealligator.infinityforreddit.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.io.EOFException;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

import ml.docilealligator.infinityforreddit.utils.OAuthLoginHelper.FailureType;
import ml.docilealligator.infinityforreddit.utils.OAuthLoginHelper.RedirectAction;
import ml.docilealligator.infinityforreddit.utils.OAuthLoginHelper.RedirectResult;
import ml.docilealligator.infinityforreddit.utils.OAuthLoginHelper.TokenResult;
import okhttp3.MediaType;
import okhttp3.ResponseBody;
import retrofit2.Response;

/**
 * Pure (Context-free) tests for the parts of {@link OAuthLoginHelper} that don't parse JSON: HTTP
 * status classification, throwable classification, and the redirect decision. These run on plain
 * JUnit 5 with no Robolectric.
 *
 * <p>The JSON-body classification deliberately lives in {@code OAuthLoginHelperJsonTest} (Robolectric)
 * instead — it must run against Android's {@code org.json}, which coerces JSON null differently from
 * a desktop org.json. Testing it here would exercise the wrong parser and miss real bugs.
 */
class OAuthLoginHelperTest {

    private static Response<String> error(int code) {
        return Response.error(code, ResponseBody.create("error body " + code, (MediaType) null));
    }

    // ---- Non-2xx responses map to the right HTTP failure type, carrying the status code ----

    @Test
    void http401IsUnauthorizedWithCode() {
        TokenResult result = OAuthLoginHelper.classifyTokenResponse(error(401));
        assertFalse(result.isSuccess());
        assertEquals(FailureType.UNAUTHORIZED, result.failureType);
        assertEquals(401, result.httpCode);
        assertNull(result.accessToken);
    }

    @Test
    void http403IsUnauthorized() {
        TokenResult result = OAuthLoginHelper.classifyTokenResponse(error(403));
        assertEquals(FailureType.UNAUTHORIZED, result.failureType);
        assertEquals(403, result.httpCode);
    }

    @Test
    void http429IsRateLimited() {
        TokenResult result = OAuthLoginHelper.classifyTokenResponse(error(429));
        assertEquals(FailureType.RATE_LIMITED, result.failureType);
        assertEquals(429, result.httpCode);
    }

    @Test
    void http500IsServerError() {
        TokenResult result = OAuthLoginHelper.classifyTokenResponse(error(500));
        assertEquals(FailureType.SERVER_ERROR, result.failureType);
        assertEquals(500, result.httpCode);
    }

    @Test
    void http503IsServerError() {
        assertEquals(FailureType.SERVER_ERROR, OAuthLoginHelper.classifyTokenResponse(error(503)).failureType);
    }

    @Test
    void http400IsHttpOther() {
        TokenResult result = OAuthLoginHelper.classifyTokenResponse(error(400));
        assertEquals(FailureType.HTTP_OTHER, result.failureType);
        assertEquals(400, result.httpCode);
    }

    @Test
    void http404IsHttpOther() {
        assertEquals(FailureType.HTTP_OTHER, OAuthLoginHelper.classifyTokenResponse(error(404)).failureType);
    }

    // ---- Empty bodies are detected before any JSON parsing, so they're safe to test purely ----

    @Test
    void nullBodyIsEmptyResponse() {
        assertEquals(FailureType.EMPTY_RESPONSE,
                OAuthLoginHelper.classifyTokenResponse(Response.success(null)).failureType);
    }

    @Test
    void blankBodyIsEmptyResponse() {
        assertEquals(FailureType.EMPTY_RESPONSE,
                OAuthLoginHelper.classifyTokenResponse(Response.success("")).failureType);
    }

    // ---- onFailure throwables ----

    @Test
    void ioExceptionIsNetworkFailure() {
        assertEquals(FailureType.NETWORK, OAuthLoginHelper.classifyThrowable(new IOException("reset")).failureType);
    }

    @Test
    void socketTimeoutIsNetworkFailure() {
        assertEquals(FailureType.NETWORK, OAuthLoginHelper.classifyThrowable(new SocketTimeoutException()).failureType);
    }

    @Test
    void unknownHostIsNetworkFailure() {
        assertEquals(FailureType.NETWORK, OAuthLoginHelper.classifyThrowable(new UnknownHostException()).failureType);
    }

    @Test
    void eofIsNetworkFailure() {
        assertEquals(FailureType.NETWORK, OAuthLoginHelper.classifyThrowable(new EOFException()).failureType);
    }

    @Test
    void nonIoThrowableIsUnknown() {
        assertEquals(FailureType.UNKNOWN, OAuthLoginHelper.classifyThrowable(new IllegalStateException()).failureType);
    }

    @Test
    void nullThrowableIsUnknown() {
        assertEquals(FailureType.UNKNOWN, OAuthLoginHelper.classifyThrowable(null).failureType);
    }

    // ---- Redirect decision ----

    @Test
    void codeWithMatchingStateExchanges() {
        RedirectResult result = OAuthLoginHelper.classifyRedirect("abc123", APIUtils.STATE, null);
        assertEquals(RedirectAction.EXCHANGE_CODE, result.action);
        assertEquals("abc123", result.authCode);
    }

    @Test
    void codeWithWrongStateIsMismatch() {
        RedirectResult result = OAuthLoginHelper.classifyRedirect("abc123", "not-the-state", null);
        assertEquals(RedirectAction.STATE_MISMATCH, result.action);
        assertNull(result.authCode);
    }

    @Test
    void codeWithNullStateIsMismatchNotCrash() {
        // The original WebView code did state.equals(STATE) and could NPE on a missing state.
        RedirectResult result = OAuthLoginHelper.classifyRedirect("abc123", null, null);
        assertEquals(RedirectAction.STATE_MISMATCH, result.action);
    }

    @Test
    void emptyCodeIsTreatedAsAbsent() {
        RedirectResult result = OAuthLoginHelper.classifyRedirect("", APIUtils.STATE, "access_denied");
        assertEquals(RedirectAction.ACCESS_DENIED, result.action);
    }

    @Test
    void accessDeniedError() {
        RedirectResult result = OAuthLoginHelper.classifyRedirect(null, null, "access_denied");
        assertEquals(RedirectAction.ACCESS_DENIED, result.action);
    }

    @Test
    void otherErrorIsOauthError() {
        RedirectResult result = OAuthLoginHelper.classifyRedirect(null, null, "invalid_request");
        assertEquals(RedirectAction.OAUTH_ERROR, result.action);
        assertEquals("invalid_request", result.errorValue);
    }

    @Test
    void noCodeNoErrorIsNone() {
        assertEquals(RedirectAction.NONE, OAuthLoginHelper.classifyRedirect(null, null, null).action);
    }

    @Test
    void codeTakesPrecedenceOverError() {
        RedirectResult result = OAuthLoginHelper.classifyRedirect("abc123", APIUtils.STATE, "access_denied");
        assertEquals(RedirectAction.EXCHANGE_CODE, result.action);
        assertEquals("abc123", result.authCode);
    }

    // ---- Error-page detection (the WebView "{}" dead-end) ----

    @Test
    void emptyJsonObjectLooksLikeErrorPage() {
        assertTrue(OAuthLoginHelper.looksLikeJsonErrorPage("{}"));
    }

    @Test
    void jsonErrorBodyLooksLikeErrorPage() {
        assertTrue(OAuthLoginHelper.looksLikeJsonErrorPage("  {\"error\":\"invalid_grant\"}\n"));
    }

    @Test
    void htmlPageIsNotErrorPage() {
        assertFalse(OAuthLoginHelper.looksLikeJsonErrorPage("Hey, user! would like to connect Accept Decline"));
    }

    @Test
    void emptyOrNullIsNotErrorPage() {
        assertFalse(OAuthLoginHelper.looksLikeJsonErrorPage(""));
        assertFalse(OAuthLoginHelper.looksLikeJsonErrorPage("   "));
        assertFalse(OAuthLoginHelper.looksLikeJsonErrorPage(null));
    }

    @Test
    void braceWithoutClosingIsNotErrorPage() {
        assertFalse(OAuthLoginHelper.looksLikeJsonErrorPage("{ this is just prose that opens a brace"));
    }
}
