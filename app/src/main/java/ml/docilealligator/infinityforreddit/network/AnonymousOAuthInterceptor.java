package ml.docilealligator.infinityforreddit.network;

import android.content.SharedPreferences;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.NonNull;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import ml.docilealligator.infinityforreddit.apis.RedditAPI;
import ml.docilealligator.infinityforreddit.utils.APIUtils;
import ml.docilealligator.infinityforreddit.utils.SharedPreferencesUtils;
import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import retrofit2.Retrofit;
import retrofit2.converter.scalars.ScalarsConverterFactory;

/**
 * Transparently authenticates anonymous Reddit requests with an application-only ("userless")
 * OAuth token. Reddit shut down the unauthenticated www.reddit.com/*.json endpoints, so anonymous
 * browsing must now go through oauth.reddit.com with a token obtained via the installed_client
 * grant. This interceptor:
 *     rewrites the host of anonymous data requests from www.reddit.com to oauth.reddit.com,
 *     injects a cached application-only bearer token (fetching one lazily when needed), and
 *     refreshes the token and retries once on a 401.
 *
 * The token-exchange and authorize endpoints are left untouched so the normal login flow, which
 * shares the same Retrofit instance, keeps working.
 */
public class AnonymousOAuthInterceptor implements Interceptor {
    private static final String TAG = "AnonymousOAuth";

    private final SharedPreferences currentAccountSharedPreferences;
    private final String basicAuthHeader;
    private volatile OkHttpClient tokenClient;

    public AnonymousOAuthInterceptor(@NonNull String clientId, @NonNull SharedPreferences currentAccountSharedPreferences) {
        this.currentAccountSharedPreferences = currentAccountSharedPreferences;
        String credentials = clientId + ":";
        this.basicAuthHeader = "Basic " + Base64.encodeToString(credentials.getBytes(), Base64.NO_WRAP);
    }

    @NonNull
    @Override
    public Response intercept(@NonNull Chain chain) throws IOException {
        Request original = chain.request();
        if (!shouldAuthenticate(original)) {
            return chain.proceed(original);
        }

        String token = currentToken();
        if (token.isEmpty()) {
            token = refreshToken("");
        }

        Response response = chain.proceed(authenticate(original, token));
        if (response.code() != 401) {
            return response;
        }

        // Token was rejected (likely expired or revoked). Refresh once and retry.
        String newToken = refreshToken(token);
        if (newToken.isEmpty() || newToken.equals(token)) {
            return response;
        }
        response.close();
        return chain.proceed(authenticate(original, newToken));
    }

    /**
     * Only Reddit data requests should be redirected and authenticated. The token-exchange and
     * authorize endpoints must stay on www.reddit.com and must not carry an application-only token.
     */
    private boolean shouldAuthenticate(Request request) {
        HttpUrl url = request.url();
        String host = url.host();
        if (!host.equals("www.reddit.com") && !host.equals("reddit.com")) {
            return false;
        }
        String path = url.encodedPath();
        return !path.contains("/api/v1/access_token") && !path.contains("/api/v1/authorize");
    }

    private Request authenticate(Request request, String token) {
        HttpUrl newUrl = request.url().newBuilder()
                .host("oauth.reddit.com")
                .build();
        return request.newBuilder()
                .url(newUrl)
                .header(APIUtils.AUTHORIZATION_KEY, APIUtils.AUTHORIZATION_BASE + token)
                .header(APIUtils.USER_AGENT_KEY, APIUtils.ANONYMOUS_USER_AGENT)
                .build();
    }

    private String currentToken() {
        APIUtils.ApplicationOnlyToken cached = APIUtils.ANONYMOUS_TOKEN.get();
        if (cached.isValid()) {
            return cached.token;
        }
        // Fall back to the last-known persisted token. It may be stale, in which case the 401
        // path will refresh it, but trying it first avoids an extra token fetch on cold start.
        return currentAccountSharedPreferences.getString(SharedPreferencesUtils.ANONYMOUS_ACCESS_TOKEN, "");
    }

    /**
     * Fetches and caches a new application-only token. If another thread already refreshed to a
     * token different from {@code rejectedToken}, that one is reused instead of fetching again.
     */
    private synchronized String refreshToken(String rejectedToken) {
        APIUtils.ApplicationOnlyToken cached = APIUtils.ANONYMOUS_TOKEN.get();
        if (cached.isValid() && !cached.token.equals(rejectedToken)) {
            return cached.token;
        }

        Map<String, String> headers = new HashMap<>();
        headers.put(APIUtils.AUTHORIZATION_KEY, basicAuthHeader);
        headers.put(APIUtils.USER_AGENT_KEY, APIUtils.ANONYMOUS_USER_AGENT);

        Map<String, String> params = new HashMap<>();
        params.put(APIUtils.GRANT_TYPE_KEY, APIUtils.GRANT_TYPE_INSTALLED_CLIENT);
        params.put(APIUtils.DEVICE_ID_KEY, APIUtils.DEVICE_ID_DO_NOT_TRACK);

        try {
            retrofit2.Response<String> response = getTokenClient().getAccessToken(headers, params).execute();
            if (response.isSuccessful() && response.body() != null) {
                JSONObject json = new JSONObject(response.body());
                String newToken = json.optString(APIUtils.ACCESS_TOKEN_KEY, "");
                long expiresIn = json.optLong("expires_in", 3600);
                if (!newToken.isEmpty()) {
                    APIUtils.ANONYMOUS_TOKEN.set(APIUtils.ApplicationOnlyToken.expireIn(newToken, expiresIn));
                    currentAccountSharedPreferences.edit()
                            .putString(SharedPreferencesUtils.ANONYMOUS_ACCESS_TOKEN, newToken).apply();
                    return newToken;
                }
            } else {
                Log.e(TAG, "Failed to fetch application-only token: HTTP " + response.code());
            }
        } catch (IOException | JSONException e) {
            Log.e(TAG, "Error fetching application-only token", e);
        }

        return "";
    }

    private RedditAPI getTokenClient() {
        // Built lazily with a bare client so the token request never re-enters this interceptor.
        if (tokenClient == null) {
            synchronized (this) {
                if (tokenClient == null) {
                    tokenClient = new OkHttpClient.Builder().build();
                }
            }
        }
        return new Retrofit.Builder()
                .baseUrl(APIUtils.API_BASE_URI)
                .client(tokenClient)
                .addConverterFactory(ScalarsConverterFactory.create())
                .build()
                .create(RedditAPI.class);
    }
}
