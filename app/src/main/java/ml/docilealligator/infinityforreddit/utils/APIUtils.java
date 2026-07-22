package ml.docilealligator.infinityforreddit.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.util.Base64;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import ml.docilealligator.infinityforreddit.R;
import ml.docilealligator.infinityforreddit.account.Account;
import okhttp3.MediaType;
import okhttp3.RequestBody;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Created by alex on 2/23/18.
 */

public class APIUtils {
    public static final String OAUTH_URL = "https://www.reddit.com/api/v1/authorize.compact";
    public static final String OAUTH_API_BASE_URI = "https://oauth.reddit.com";
    public static final String API_BASE_URI = "https://www.reddit.com";
    public static final String API_UPLOAD_MEDIA_URI = "https://reddit-uploaded-media.s3-accelerate.amazonaws.com";
    public static final String API_UPLOAD_VIDEO_URI = "https://reddit-uploaded-video.s3-accelerate.amazonaws.com";
    public static final String REDGIFS_API_BASE_URI = "https://api.redgifs.com";
    public static final String OH_MY_DL_BASE_URI = "https://ohmydl.com";
    public static final String IMGUR_API_BASE_URI = "https://api.imgur.com/3/";
    public static final String STREAMABLE_API_BASE_URI = "https://api.streamable.com";
    public static final String SERVER_API_BASE_URI = "http://127.0.0.1";
    public static final String ARCTIC_SHIFT_API_BASE_URI = "https://arctic-shift.photon-reddit.com/";

    public static final String CLIENT_ID_KEY = "client_id";
    public static final String CLIENT_SECRET_KEY = "client_secret";
    public static final String IMGUR_CLIENT_ID = "Client-ID cc671794e0ab397";
    public static final String REDGIFS_CLIENT_ID = "1828d0bcc93-15ac-bde6-0005-d2ecbe8daab3";
    public static final String REDGIFS_CLIENT_SECRET = "TJBlw7jRXW65NAGgFBtgZHu97WlzRXHYybK81sZ9dLM=";
    public static final String RESPONSE_TYPE_KEY = "response_type";
    public static final String RESPONSE_TYPE = "code";
    public static final String STATE_KEY = "state";
    public static final String STATE = "23ro8xlxvzp4asqd";
    public static final String REDIRECT_URI_KEY = "redirect_uri";
    public static final String DEFAULT_REDIRECT_URI = "redreader://rr_oauth_redir";
    public static String REDIRECT_URI = DEFAULT_REDIRECT_URI;
    public static final String DURATION_KEY = "duration";
    public static final String DURATION = "permanent";
    public static final String SCOPE_KEY = "scope";

    public static final String[] SCOPE_LIST = {
        "account", "creddits", "edit", "flair", "history", "identity", "livemanage", "modconfig", "modcontributors",
        "modflair", "modlog", "modmail", "modothers", "modposts", "modwiki", "modself", "mysubreddits", "privatemessages",
        "read", "report", "save", "submit", "subscribe", "vote", "wikiedit", "wikiread"
    };

    public static final String SCOPE = String.join(" ", SCOPE_LIST);

    public static final String ACCESS_TOKEN_KEY = "access_token";
    public static final String ERROR_KEY = "error";

    public static final String AUTHORIZATION_KEY = "Authorization";
    public static final String AUTHORIZATION_BASE = "bearer ";
    public static final String USER_AGENT_KEY = "User-Agent";
    public static String ANONYMOUS_USER_AGENT = "org.quantumbadger.redreader/1.25.2";
    public static final String DEFAULT_USER_AGENT = "org.quantumbadger.redreader/1.25.2";
    public static String USER_AGENT = DEFAULT_USER_AGENT;
    public static final String USERNAME_KEY = "username";

    public static final String GRANT_TYPE_KEY = "grant_type";
    public static final String GRANT_TYPE_REFRESH_TOKEN = "refresh_token";
    public static final String GRANT_TYPE_CLIENT_CREDENTIALS = "client_credentials";
    // Application-only ("userless") OAuth grant for installed apps. Used for anonymous browsing
    // now that Reddit has shut down the unauthenticated www.reddit.com/*.json endpoints.
    public static final String GRANT_TYPE_INSTALLED_CLIENT = "https://oauth.reddit.com/grants/installed_client";
    public static final String REFRESH_TOKEN_KEY = "refresh_token";

    public static final String DEVICE_ID_KEY = "device_id";
    // Privacy-respecting sentinel accepted by Reddit in place of a unique per-device id.
    public static final String DEVICE_ID_DO_NOT_TRACK = "DO_NOT_TRACK_THIS_DEVICE";

    public static final String DIR_KEY = "dir";
    public static final String ID_KEY = "id";
    public static final String RANK_KEY = "rank";
    public static final String DIR_UPVOTE = "1";
    public static final String DIR_UNVOTE = "0";
    public static final String DIR_DOWNVOTE = "-1";
    public static final String RANK = "10";

    public static final String ACTION_KEY = "action";
    public static final String SR_NAME_KEY = "sr_name";

    public static final String API_TYPE_KEY = "api_type";
    public static final String API_TYPE_JSON = "json";
    public static final String RETURN_RTJSON_KEY = "return_rtjson";
    public static final String TEXT_KEY = "text";
    public static final String URL_KEY = "url";
    public static final String VIDEO_POSTER_URL_KEY = "video_poster_url";
    public static final String THING_ID_KEY = "thing_id";

    public static final String SR_KEY = "sr";
    public static final String TITLE_KEY = "title";
    public static final String FLAIR_TEXT_KEY = "flair_text";
    public static final String SPOILER_KEY = "spoiler";
    public static final String NSFW_KEY = "nsfw";
    public static final String CROSSPOST_FULLNAME_KEY = "crosspost_fullname";
    public static final String SEND_REPLIES_KEY = "sendreplies";
    public static final String KIND_KEY = "kind";
    public static final String KIND_SELF = "self";
    public static final String KIND_LINK = "link";
    public static final String KIND_IMAGE = "image";
    public static final String KIND_VIDEO = "video";
    public static final String KIND_VIDEOGIF = "videogif";
    public static final String KIND_CROSSPOST = "crosspost";
    public static final String RICHTEXT_JSON_KEY = "richtext_json";

    public static final String FILEPATH_KEY = "filepath";
    public static final String MIMETYPE_KEY = "mimetype";

    public static final String LINK_KEY = "link";
    public static final String FLAIR_TEMPLATE_ID_KEY = "flair_template_id";
    public static final String FLAIR_ID_KEY = "flair_id";

    public static final String MAKE_FAVORITE_KEY = "make_favorite";

    public static final String MULTIPATH_KEY = "multipath";
    public static final String MODEL_KEY = "model";
    public static final String FROM_KEY = "from";
    public static final String DISPLAY_NAME_KEY = "display_name";
    public static final String DESCRIPTION_MD_KEY = "description_md";

    public static final String REASON_KEY = "reason";

    public static final String SUBJECT_KEY = "subject";
    public static final String TO_KEY = "to";

    public static final String NAME_KEY = "name";

    public static final String ORIGIN_KEY = "Origin";
    public static final String REVEDDIT_ORIGIN = "https://www.reveddit.com";
    public static final String REFERER_KEY = "Referer";
    public static final String REVEDDIT_REFERER = "https://www.reveddit.com/";

    // Whether the user has opted in to using their custom Client ID / User Agent / Redirect URI.
    // Defaults to false, so the built-in defaults are used unless overrides are explicitly enabled.
    private static boolean areOverridesEnabled(SharedPreferences sharedPreferences) {
        return sharedPreferences.getBoolean(SharedPreferencesUtils.ENABLE_API_KEY_OVERRIDES_PREF_KEY, false);
    }

    // Method to retrieve Client ID from SharedPreferences
    public static String getClientId(Context context) {
        // Explicitly get SharedPreferences by file name to ensure consistency with the PreferenceFragment
        SharedPreferences sharedPreferences = context.getSharedPreferences(SharedPreferencesUtils.DEFAULT_PREFERENCES_FILE, Context.MODE_PRIVATE);

        String defaultClientId = context.getString(R.string.default_client_id);
        if (!areOverridesEnabled(sharedPreferences)) {
            return defaultClientId;
        }
        return Objects.requireNonNull(sharedPreferences.getString(SharedPreferencesUtils.CLIENT_ID_PREF_KEY, defaultClientId));
    }

    // Method to retrieve User Agent from SharedPreferences
    public static String getUserAgent(Context context) {
        SharedPreferences sharedPreferences = context.getSharedPreferences(SharedPreferencesUtils.DEFAULT_PREFERENCES_FILE, Context.MODE_PRIVATE);
        if (!areOverridesEnabled(sharedPreferences)) {
            return DEFAULT_USER_AGENT;
        }
        String userAgent = sharedPreferences.getString(SharedPreferencesUtils.USER_AGENT_PREF_KEY, DEFAULT_USER_AGENT);
        if (userAgent == null || userAgent.isEmpty()) {
            return DEFAULT_USER_AGENT;
        }
        return userAgent;
    }

    // Method to retrieve Redirect URI from SharedPreferences
    public static String getRedirectUri(Context context) {
        SharedPreferences sharedPreferences = context.getSharedPreferences(SharedPreferencesUtils.DEFAULT_PREFERENCES_FILE, Context.MODE_PRIVATE);
        String defaultRedirectUri = context.getString(R.string.default_redirect_uri);
        if (!areOverridesEnabled(sharedPreferences)) {
            return defaultRedirectUri;
        }
        String redirectUri = sharedPreferences.getString(SharedPreferencesUtils.REDIRECT_URI_PREF_KEY, defaultRedirectUri);
        if (redirectUri == null || redirectUri.isEmpty()) {
            return defaultRedirectUri;
        }
        return redirectUri;
    }

    // Initialize mutable configurable fields from SharedPreferences at app startup
    public static void initConfigurableFields(Context context) {
        USER_AGENT = getUserAgent(context);
        REDIRECT_URI = getRedirectUri(context);
    }

    // Method to retrieve Giphy API Key from SharedPreferences
    public static String getGiphyApiKey(Context context) {
        // Explicitly get SharedPreferences by file name to ensure consistency
        SharedPreferences sharedPreferences = context.getSharedPreferences(SharedPreferencesUtils.DEFAULT_PREFERENCES_FILE, Context.MODE_PRIVATE);
        String customKey = sharedPreferences.getString(SharedPreferencesUtils.GIPHY_API_KEY_PREF_KEY, null);

        // Return custom key if set and not empty, otherwise return the default from strings.xml
        if (customKey != null && !customKey.isEmpty()) {
            return customKey;
        } else {
            return context.getString(R.string.default_giphy_api_key);
        }
    }

    public static final String SPAM_KEY = "spam";
    public static final String HOW_KEY = "how";
    public static final String HOW_YES = "yes";
    public static final String HOW_NO = "no";

    public static final String PLATFORM_KEY = "platform";

    public static Map<String, String> getHttpBasicAuthHeader(Context context) {
        // Ensure we use the application context to avoid potential lifecycle issues with the passed context
        Context appContext = context.getApplicationContext();

        Map<String, String> params = new HashMap<>();
        String clientId = getClientId(appContext);
        String credentials = String.format("%s:%s", clientId, "");
        String auth = "Basic " + Base64.encodeToString(credentials.getBytes(), Base64.NO_WRAP);
        params.put(APIUtils.AUTHORIZATION_KEY, auth);

        return params;
    }

    public static Map<String, String> getOAuthHeader(@Nullable String accessToken) {
        Map<String, String> params = new HashMap<>();
        params.put(APIUtils.AUTHORIZATION_KEY, APIUtils.AUTHORIZATION_BASE + accessToken);
        params.put(APIUtils.USER_AGENT_KEY, APIUtils.USER_AGENT);

        return params;
    }

    public static Map<String, String> getServerHeader(String serverAccessToken, String accountName, boolean anonymous) {
        if (accountName.equals(Account.ANONYMOUS_ACCOUNT) || anonymous) {
            return new HashMap<>();
        }

        Map<String, String> params = new HashMap<>();
        params.put(APIUtils.AUTHORIZATION_KEY, APIUtils.AUTHORIZATION_BASE + serverAccessToken);
        params.put(APIUtils.USERNAME_KEY, accountName);

        return params;
    }

    public static Map<String, String> getRedgifsOAuthHeader(@Nullable String redgifsAccessToken) {
        Map<String, String> params = new HashMap<>();
        params.put(APIUtils.AUTHORIZATION_KEY, APIUtils.AUTHORIZATION_BASE + redgifsAccessToken);

        return params;
    }

    public static RequestBody getRequestBody(String s) {
        return RequestBody.create(s, MediaType.parse("text/plain"));
    }

    public static Map<String, String> getRevedditHeader() {
        Map<String, String> params = new HashMap<>();
        params.put(APIUtils.ORIGIN_KEY, APIUtils.REVEDDIT_ORIGIN);
        params.put(APIUtils.REFERER_KEY, APIUtils.REVEDDIT_REFERER);
        params.put(APIUtils.USER_AGENT_KEY, APIUtils.USER_AGENT);

        return params;
    }

    // Concatenated subreddit name works too
    public static int subredditAPICallLimit(@Nullable String subredditName) {
        return 100;
    }

    /**
     * Extracts the human-readable error from a Reddit {@code api_type=json} response envelope
     * ({@code {"json":{"errors":[[code,message,field],...]}}}). Returns the last error's message
     * (falling back to its code) with the first letter capitalized, or {@code null} when the body
     * carries no error — an empty {@code errors} array, no {@code json} envelope, or an unparseable
     * body. Reddit reports API-level failures (archived thing, rate limit, …) inside an otherwise
     * HTTP-200 body, so callers of endpoints sent with {@code api_type=json} must consult this even
     * when {@code response.isSuccessful()} is true. Same extraction as
     * {@link ml.docilealligator.infinityforreddit.user.SelectUserFlair} and
     * {@code ParseComment.parseSentCommentErrorMessage}.
     */
    @Nullable
    public static String parseApiErrorMessage(@Nullable String responseBody) {
        if (responseBody == null) {
            return null;
        }
        try {
            JSONArray errors = new JSONObject(responseBody).getJSONObject(JSONUtils.JSON_KEY)
                    .getJSONArray(JSONUtils.ERRORS_KEY);
            if (errors.length() == 0) {
                return null;
            }
            JSONArray error = errors.getJSONArray(errors.length() - 1);
            if (error.length() == 0) {
                return null;
            }
            String errorString = error.length() >= 2 ? error.getString(1) : error.getString(0);
            if (errorString.isEmpty()) {
                return null;
            }
            return errorString.substring(0, 1).toUpperCase() + errorString.substring(1);
        } catch (JSONException e) {
            return null;
        }
    }

    // Application-only (anonymous/userless) Reddit OAuth token management
    public static final AtomicReference<ApplicationOnlyToken> ANONYMOUS_TOKEN = new AtomicReference<>(new ApplicationOnlyToken("", 0));

    public static class ApplicationOnlyToken {
        @NonNull public final String token;
        private final long expireAt;

        private ApplicationOnlyToken(@NonNull String token, final long expireAt) {
            this.token = token;
            this.expireAt = expireAt;
        }

        // Reddit application-only tokens are valid for expiresInSeconds (typically 3600s / 1 hour).
        // Expire a few minutes early to give a safety margin before the real expiry.
        public static ApplicationOnlyToken expireIn(@NonNull String token, long expiresInSeconds) {
            long leewayMillis = 5 * 60 * 1000;
            long lifetimeMillis = Math.max(0, expiresInSeconds * 1000 - leewayMillis);
            return new ApplicationOnlyToken(token, SystemClock.uptimeMillis() + lifetimeMillis);
        }

        public boolean isValid() {
            return !token.isEmpty() && expireAt > SystemClock.uptimeMillis();
        }
    }

    // RedGifs token management
    public static final AtomicReference<RedgifsAuthToken> REDGIFS_TOKEN = new AtomicReference<>(new RedgifsAuthToken("", 0));

    public static class RedgifsAuthToken {
        @NonNull public final String token;
        private final long expireAt;

        private RedgifsAuthToken(@NonNull String token, final long expireAt) {
            this.token = token;
            this.expireAt = expireAt;
        }

        public static RedgifsAuthToken expireIn1day(@NonNull String token) {
            // 23 not 24 to give an hour leeway
            long expireTime = 1000 * 60 * 60 * 23;
            return new RedgifsAuthToken(token, SystemClock.uptimeMillis() + expireTime);
        }

        public boolean isValid() {
            return !token.isEmpty() && expireAt > SystemClock.uptimeMillis();
        }
    }
}
