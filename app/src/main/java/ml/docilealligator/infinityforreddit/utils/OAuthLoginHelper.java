package ml.docilealligator.infinityforreddit.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.util.Base64;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.Objects;
import ml.docilealligator.infinityforreddit.R;
import org.json.JSONException;
import org.json.JSONObject;
import retrofit2.Response;

/**
 * Shared, stateless error handling for the OAuth token-exchange step, used by both the WebView
 * ({@code LoginActivity}) and Custom Tab ({@code LoginChromeCustomTabActivity}) login paths so the
 * two stay in lockstep.
 *
 * <p>The branching logic lives in {@link #classifyTokenResponse} / {@link #classifyThrowable},
 * which are Context-free and unit-testable. {@link #describeFailure} maps a classified result to a
 * friendly, localized message that includes a diagnostic code (HTTP status or Reddit error) when
 * one is available. The raw response body / throwable is always logged to logcat under {@link #TAG}.
 *
 * <p>It also owns the one piece of login state: the random {@code state} of the attempt in flight
 * ({@link #newPendingState} / {@link #consumePendingState}), which every authorize URL carries and
 * every redirect is checked against.
 */
public final class OAuthLoginHelper {

    public static final String TAG = "OAuthLogin";

    /** Random bytes in a login attempt's {@code state}: 128 bits, 22 URL-safe characters. */
    private static final int STATE_BYTES = 16;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private OAuthLoginHelper() {
    }

    /** The kind of failure that occurred during the token exchange. */
    public enum FailureType {
        /** Non-2xx with HTTP 401/403 — usually a bad/unknown API Client ID or bad Basic auth. */
        UNAUTHORIZED,
        /** Non-2xx with HTTP 429 — Reddit is rate-limiting login attempts. */
        RATE_LIMITED,
        /** Non-2xx with HTTP >= 500 — a Reddit server-side error. */
        SERVER_ERROR,
        /** Any other non-2xx response. */
        HTTP_OTHER,
        /** A 2xx response with a null or empty body. */
        EMPTY_RESPONSE,
        /** A 2xx response whose JSON carried an {@code "error"} field. */
        REDDIT_ERROR,
        /** A 2xx response whose JSON parsed but lacked an access or refresh token (the #246 case). */
        MISSING_TOKENS,
        /** A 2xx response whose body was not valid JSON. */
        MALFORMED_JSON,
        /** The call itself failed with an {@link IOException} — typically no connectivity. */
        NETWORK,
        /** The call failed with a non-IO throwable. */
        UNKNOWN
    }

    /**
     * Result of classifying a token exchange. Exactly one of (access+refresh token) or
     * {@link #failureType} is populated; check {@link #isSuccess()}.
     */
    public static final class TokenResult {
        @Nullable
        public final String accessToken;
        @Nullable
        public final String refreshToken;
        @Nullable
        public final FailureType failureType;
        /** HTTP status for the HTTP_* failures, otherwise -1. */
        public final int httpCode;
        /** The raw Reddit {@code error} value for {@link FailureType#REDDIT_ERROR}, otherwise null. */
        @Nullable
        public final String redditError;

        private TokenResult(@Nullable String accessToken, @Nullable String refreshToken, @Nullable FailureType failureType,
                            int httpCode, @Nullable String redditError) {
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
            this.failureType = failureType;
            this.httpCode = httpCode;
            this.redditError = redditError;
        }

        static TokenResult success(String accessToken, String refreshToken) {
            return new TokenResult(accessToken, refreshToken, null, -1, null);
        }

        static TokenResult failure(FailureType type) {
            return new TokenResult(null, null, type, -1, null);
        }

        static TokenResult httpFailure(FailureType type, int httpCode) {
            return new TokenResult(null, null, type, httpCode, null);
        }

        static TokenResult redditError(String redditError) {
            return new TokenResult(null, null, FailureType.REDDIT_ERROR, -1, redditError);
        }

        public boolean isSuccess() {
            return failureType == null;
        }
    }

    /**
     * Classifies a token-endpoint response into either both tokens or a {@link FailureType}. Covers
     * non-2xx responses (with HTTP code), empty bodies, Reddit {@code {"error":...}} bodies, missing
     * tokens, and malformed JSON. Context-free so it can be unit-tested directly; raw detail is
     * logged here.
     */
    @NonNull
    public static TokenResult classifyTokenResponse(@NonNull Response<String> response) {
        if (!response.isSuccessful()) {
            int code = response.code();
            Log.e(TAG, "Token exchange failed: HTTP " + code + " — " + readErrorBody(response));
            if (code == 401 || code == 403) {
                return TokenResult.httpFailure(FailureType.UNAUTHORIZED, code);
            } else if (code == 429) {
                return TokenResult.httpFailure(FailureType.RATE_LIMITED, code);
            } else if (code >= 500) {
                return TokenResult.httpFailure(FailureType.SERVER_ERROR, code);
            } else {
                return TokenResult.httpFailure(FailureType.HTTP_OTHER, code);
            }
        }

        String body = response.body();
        if (body == null || body.isEmpty()) {
            Log.e(TAG, "Token exchange returned an empty body");
            return TokenResult.failure(FailureType.EMPTY_RESPONSE);
        }

        try {
            JSONObject responseJSON = new JSONObject(body);

            // !isNull guards against an explicit JSON null error (e.g. {"error":null}), which
            // is not a real failure and should fall through to the token check below.
            if (responseJSON.has(APIUtils.ERROR_KEY) && !responseJSON.isNull(APIUtils.ERROR_KEY)) {
                String redditError = responseJSON.optString(APIUtils.ERROR_KEY);
                Log.e(TAG, "Token exchange returned an error field: " + body);
                return TokenResult.redditError(redditError);
            }

            String accessToken = responseJSON.optString(APIUtils.ACCESS_TOKEN_KEY);
            String refreshToken = responseJSON.optString(APIUtils.REFRESH_TOKEN_KEY);
            // isNull is essential: Android's org.json coerces a JSON null to the string "null"
            // (non-empty), so optString alone would accept {"access_token":null} as a valid token.
            if (responseJSON.isNull(APIUtils.ACCESS_TOKEN_KEY) || responseJSON.isNull(APIUtils.REFRESH_TOKEN_KEY)
                    || accessToken.isEmpty() || refreshToken.isEmpty()) {
                Log.e(TAG, "Token exchange response was missing tokens: " + body);
                return TokenResult.failure(FailureType.MISSING_TOKENS);
            }

            return TokenResult.success(accessToken, refreshToken);
        } catch (JSONException e) {
            Log.e(TAG, "Failed to parse token exchange response: " + body, e);
            return TokenResult.failure(FailureType.MALFORMED_JSON);
        }
    }

    /**
     * Classifies an {@code onFailure} throwable from the token-exchange call. Network failures
     * (IOException) map to {@link FailureType#NETWORK}; anything else to {@link FailureType#UNKNOWN}.
     */
    @NonNull
    public static TokenResult classifyThrowable(@Nullable Throwable t) {
        Log.e(TAG, "Token exchange call failed", t);
        if (t instanceof IOException) {
            return TokenResult.failure(FailureType.NETWORK);
        }
        return TokenResult.failure(FailureType.UNKNOWN);
    }

    /**
     * Maps a failed {@link TokenResult} to a friendly, localized message that includes the HTTP
     * status or Reddit error code when available.
     */
    @NonNull
    public static String describeFailure(@NonNull Context context, @NonNull TokenResult result) {
        switch (Objects.requireNonNull(result.failureType)) {
            case UNAUTHORIZED:
                return context.getString(R.string.oauth_error_invalid_credentials, result.httpCode);
            case RATE_LIMITED:
                return context.getString(R.string.oauth_error_rate_limited);
            case SERVER_ERROR:
                return context.getString(R.string.oauth_error_server, result.httpCode);
            case HTTP_OTHER:
                return context.getString(R.string.oauth_error_http_generic, result.httpCode);
            case EMPTY_RESPONSE:
                return context.getString(R.string.oauth_error_empty_response);
            case REDDIT_ERROR:
                return context.getString(R.string.oauth_error_reddit_error, result.redditError);
            case MISSING_TOKENS:
                return context.getString(R.string.oauth_error_missing_tokens);
            case MALFORMED_JSON:
                return context.getString(R.string.parse_json_response_error);
            case NETWORK:
                return context.getString(R.string.oauth_error_network);
            case UNKNOWN:
            default:
                return context.getString(R.string.retrieve_token_error);
        }
    }

    /** What the OAuth redirect URI tells us to do. */
    public enum RedirectAction {
        /** A valid {@code code} with the pending attempt's {@code state} — proceed to the token exchange. */
        EXCHANGE_CODE,
        /**
         * A {@code code} arrived but its {@code state} was missing or not the pending attempt's — a
         * forged redirect (login CSRF), or one from an attempt that has since been replaced.
         */
        STATE_MISMATCH,
        /** {@code error=access_denied} — the user declined, or Reddit auto-denied. */
        ACCESS_DENIED,
        /** Some other {@code error=...} value. */
        OAUTH_ERROR,
        /** No {@code code} and no {@code error} — not a redirect we act on (keep loading). */
        NONE
    }

    /** Result of {@link #classifyRedirect}. */
    public static final class RedirectResult {
        public final RedirectAction action;
        /** The auth code for {@link RedirectAction#EXCHANGE_CODE}, otherwise null. */
        @Nullable
        public final String authCode;
        /** The raw error value for {@link RedirectAction#OAUTH_ERROR}, otherwise null. */
        @Nullable
        public final String errorValue;

        private RedirectResult(RedirectAction action, @Nullable String authCode, @Nullable String errorValue) {
            this.action = action;
            this.authCode = authCode;
            this.errorValue = errorValue;
        }
    }

    /**
     * Starts a login attempt: makes a fresh, unguessable {@code state} for its authorize URL and
     * remembers it, so {@link #consumePendingState} can tell the redirect this attempt causes from
     * one another app or a web page forges. The Custom Tab activity is exported for every supported
     * redirect URI, so with a fixed value anyone could hand the app an auth code for their own
     * account and switch it to that account (OAuth login CSRF). Kept in the internal preferences, not
     * in memory, because the process can die while the user is in the browser. A new attempt
     * replaces any earlier one.
     */
    @NonNull
    public static String newPendingState(@NonNull Context context) {
        byte[] bytes = new byte[STATE_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        String state = Base64.encodeToString(bytes, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
        internalPreferences(context).edit().putString(SharedPreferencesUtils.OAUTH_PENDING_STATE, state).apply();
        return state;
    }

    /**
     * Whether a redirect's {@code state} is the one {@link #newPendingState} handed out for the login
     * attempt in flight. A match ends that attempt — the state is cleared, so the same redirect cannot
     * be replayed — while a mismatch leaves it in place, so a forged redirect cannot cancel a real
     * login either.
     */
    public static boolean consumePendingState(@NonNull Context context, @Nullable String state) {
        SharedPreferences preferences = internalPreferences(context);
        if (!stateMatches(preferences.getString(SharedPreferencesUtils.OAUTH_PENDING_STATE, null), state)) {
            return false;
        }
        preferences.edit().remove(SharedPreferencesUtils.OAUTH_PENDING_STATE).apply();
        return true;
    }

    /** Null-safe: no attempt in flight, or a redirect without a {@code state}, never matches. */
    static boolean stateMatches(@Nullable String pendingState, @Nullable String state) {
        return pendingState != null && !pendingState.isEmpty() && pendingState.equals(state);
    }

    @NonNull
    private static SharedPreferences internalPreferences(@NonNull Context context) {
        return context.getSharedPreferences(SharedPreferencesUtils.INTERNAL_SHARED_PREFERENCES_FILE, Context.MODE_PRIVATE);
    }

    /**
     * Decides what to do with an OAuth redirect from its {@code code} and {@code error} query
     * parameters and whether its {@code state} passed {@link #consumePendingState}. Context-/Uri-free
     * so both login activities can share it and it can be unit-tested directly. A present
     * {@code code} takes precedence, and is only exchanged when the state matched.
     */
    @NonNull
    public static RedirectResult classifyRedirect(@Nullable String code, boolean stateMatches,
                                                  @Nullable String error) {
        if (code != null && !code.isEmpty()) {
            if (stateMatches) {
                return new RedirectResult(RedirectAction.EXCHANGE_CODE, code, null);
            }
            return new RedirectResult(RedirectAction.STATE_MISMATCH, null, null);
        }
        if ("access_denied".equals(error)) {
            return new RedirectResult(RedirectAction.ACCESS_DENIED, null, null);
        }
        if (error != null && !error.isEmpty()) {
            return new RedirectResult(RedirectAction.OAUTH_ERROR, null, error);
        }
        return new RedirectResult(RedirectAction.NONE, null, null);
    }

    /**
     * Heuristic: does this WebView page text look like a bare JSON body (e.g. {@code {}} or
     * {@code {"error":...}}) rather than an HTML page? Reddit renders such a body in the WebView when
     * the OAuth flow dead-ends instead of redirecting to our scheme — typically a misconfigured API
     * Client ID or Redirect URI. A normal consent/login page's text does not start with '{'.
     */
    public static boolean looksLikeJsonErrorPage(@Nullable String pageText) {
        if (pageText == null) {
            return false;
        }
        String trimmed = pageText.trim();
        return trimmed.length() >= 2 && trimmed.length() < 2000
                && trimmed.charAt(0) == '{' && trimmed.charAt(trimmed.length() - 1) == '}';
    }

    /**
     * Builds the API-Keys-style alert text: a "⚠" glyph enlarged 2x and tinted with {@code
     * accentColor}, two spaces, then {@code text}. Matches {@code APIKeysPreferenceFragment}'s
     * restart-warning banner so login alerts read consistently.
     */
    @NonNull
    public static CharSequence warningText(int accentColor, @NonNull CharSequence text) {
        String symbol = "⚠";
        SpannableString spanned = new SpannableString(symbol + "  " + text);
        spanned.setSpan(new RelativeSizeSpan(2f), 0, symbol.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        spanned.setSpan(new ForegroundColorSpan(accentColor), 0, symbol.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        return spanned;
    }

    @NonNull
    private static String readErrorBody(@NonNull Response<String> response) {
        if (response.errorBody() == null) {
            return "<no error body>";
        }
        try {
            return response.errorBody().string();
        } catch (IOException e) {
            return "<unreadable error body: " + e.getMessage() + ">";
        }
    }
}
