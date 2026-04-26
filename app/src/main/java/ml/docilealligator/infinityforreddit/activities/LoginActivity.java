package ml.docilealligator.infinityforreddit.activities;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.InflateException;
import android.view.MenuItem;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.graphics.Insets;
import androidx.core.view.OnApplyWindowInsetsListener;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;


import org.greenrobot.eventbus.EventBus;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;

import javax.inject.Inject;
import javax.inject.Named;

import ml.docilealligator.infinityforreddit.account.FetchMyInfo;
import ml.docilealligator.infinityforreddit.Infinity;
import ml.docilealligator.infinityforreddit.R;
import ml.docilealligator.infinityforreddit.RedditDataRoomDatabase;
import ml.docilealligator.infinityforreddit.account.FetchMyInfo;
import ml.docilealligator.infinityforreddit.apis.RedditAPI;
import ml.docilealligator.infinityforreddit.asynctasks.ParseAndInsertNewAccount;
import ml.docilealligator.infinityforreddit.customtheme.CustomThemeWrapper;
import ml.docilealligator.infinityforreddit.databinding.ActivityLoginBinding;
import ml.docilealligator.infinityforreddit.events.NewUserLoggedInEvent;
import ml.docilealligator.infinityforreddit.utils.APIUtils;
import ml.docilealligator.infinityforreddit.utils.SharedPreferencesUtils;
import ml.docilealligator.infinityforreddit.utils.Utils;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;

public class LoginActivity extends BaseActivity {

    @Inject
    @Named("no_oauth")
    Retrofit mRetrofit;
    @Inject
    @Named("oauth")
    Retrofit mOauthRetrofit;
    @Inject
    RedditDataRoomDatabase mRedditDataRoomDatabase;
    @Inject
    @Named("default")
    SharedPreferences mSharedPreferences;
    @Inject
    @Named("current_account")
    SharedPreferences mCurrentAccountSharedPreferences;
    @Inject
    CustomThemeWrapper mCustomThemeWrapper;
    @Inject
    Executor mExecutor;
    private String authCode;
    private ActivityLoginBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ((Infinity) getApplication()).getAppComponent().inject(this);

        setImmersiveModeNotApplicableBelowAndroid16();

        super.onCreate(savedInstanceState);
        binding = ActivityLoginBinding.inflate(getLayoutInflater());

        try {
            setContentView(binding.getRoot());
        } catch (InflateException ie) {
            Log.e("LoginActivity", "Failed to inflate LoginActivity: " + ie.getMessage());
            Toast.makeText(LoginActivity.this, R.string.no_system_webview_error, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        applyCustomTheme();

        if (isImmersiveInterfaceRespectForcedEdgeToEdge()) {
            if (isChangeStatusBarIconColor()) {
                addOnOffsetChangedListener(binding.appbarLayoutLoginActivity);
            }

            ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), new OnApplyWindowInsetsListener() {
                @NonNull
                @Override
                public WindowInsetsCompat onApplyWindowInsets(@NonNull View v, @NonNull WindowInsetsCompat insets) {
                    Insets allInsets = Utils.getInsets(insets, true, isForcedImmersiveInterface());

                    setMargins(binding.toolbarLoginActivity,
                            allInsets.left,
                            allInsets.top,
                            allInsets.right,
                            BaseActivity.IGNORE_MARGIN);

                    binding.linearLayoutLoginActivity.setPadding(
                            allInsets.left,
                            0,
                            allInsets.right,
                            allInsets.bottom
                    );

                    setMargins(binding.fabLoginActivity,
                            BaseActivity.IGNORE_MARGIN,
                            BaseActivity.IGNORE_MARGIN,
                            (int) Utils.convertDpToPixel(16, LoginActivity.this) + allInsets.right,
                            (int) Utils.convertDpToPixel(16, LoginActivity.this) + allInsets.bottom);

                    return WindowInsetsCompat.CONSUMED;
                }
            });
        }

        setSupportActionBar(binding.toolbarLoginActivity);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        binding.webviewLoginActivity.getSettings().setJavaScriptEnabled(true);

        String userAgent = binding.webviewLoginActivity.getSettings().getUserAgentString();
        Log.d("LoginActivity", "Default WebView user agent: " + userAgent);
        String chromeUserAgent = userAgent
                .replace("; wv)", ")")
                .replace("Version/4.0 ", "");
        binding.webviewLoginActivity.getSettings().setUserAgentString(chromeUserAgent);
        Log.d("LoginActivity", "Overridden WebView user agent: " + chromeUserAgent);
        Log.d("LoginActivity", "App user agent (APIUtils.USER_AGENT, unused by WebView): " + APIUtils.USER_AGENT);

        Log.d("LoginActivity", "OAuth redirect URI: " + APIUtils.REDIRECT_URI);

        Uri baseUri = Uri.parse(APIUtils.OAUTH_URL);
        Uri.Builder uriBuilder = baseUri.buildUpon();
        uriBuilder.appendQueryParameter(APIUtils.CLIENT_ID_KEY, APIUtils.getClientId(getApplicationContext()));
        uriBuilder.appendQueryParameter(APIUtils.RESPONSE_TYPE_KEY, APIUtils.RESPONSE_TYPE);
        uriBuilder.appendQueryParameter(APIUtils.STATE_KEY, APIUtils.STATE);
        uriBuilder.appendQueryParameter(APIUtils.REDIRECT_URI_KEY, APIUtils.REDIRECT_URI);
        uriBuilder.appendQueryParameter(APIUtils.DURATION_KEY, APIUtils.DURATION);
        uriBuilder.appendQueryParameter(APIUtils.SCOPE_KEY, APIUtils.SCOPE);

        String url = uriBuilder.toString();

        binding.internetDisconnectedErrorRetryButtonLoginActivity.setOnClickListener(view -> {
            recreate();
        });

        binding.fabLoginActivity.setOnClickListener(view -> {
            Intent intent = new Intent(this, LoginChromeCustomTabActivity.class);
            startActivity(intent);
            finish();
        });

        CookieManager.getInstance().removeAllCookies(aBoolean -> {
        });

        binding.webviewLoginActivity.addJavascriptInterface(new JsRequestLogger(), "AndroidLogger");
        binding.webviewLoginActivity.loadUrl(url);
        binding.webviewLoginActivity.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (url.contains("&code=") || url.contains("?code=")) {
                    Uri uri = Uri.parse(url);
                    String state = uri.getQueryParameter("state");
                    if (state.equals(APIUtils.STATE)) {
                        authCode = uri.getQueryParameter("code");

                        Map<String, String> params = new HashMap<>();
                        params.put(APIUtils.GRANT_TYPE_KEY, "authorization_code");
                        params.put("code", authCode);
                        params.put(APIUtils.REDIRECT_URI_KEY, APIUtils.REDIRECT_URI);

                        RedditAPI api = mRetrofit.create(RedditAPI.class);
                        Call<String> accessTokenCall = api.getAccessToken(APIUtils.getHttpBasicAuthHeader(getApplicationContext()), params);
                        accessTokenCall.enqueue(new Callback<>() {
                            @Override
                            public void onResponse(@NonNull Call<String> call, @NonNull Response<String> response) {
                                if (response.isSuccessful()) {
                                    try {
                                        String accountResponse = response.body();
                                        if (accountResponse == null) {
                                            //Handle error
                                            return;
                                        }

                                        JSONObject responseJSON = new JSONObject(accountResponse);
                                        if (!responseJSON.has(APIUtils.ACCESS_TOKEN_KEY) || !responseJSON.has(APIUtils.REFRESH_TOKEN_KEY)) {
                                            Toast.makeText(LoginActivity.this, R.string.invalid_client_id_error, Toast.LENGTH_LONG).show();
                                            finish();
                                            return;
                                        }
                                        String accessToken = responseJSON.getString(APIUtils.ACCESS_TOKEN_KEY);
                                        String refreshToken = responseJSON.getString(APIUtils.REFRESH_TOKEN_KEY);

                                        FetchMyInfo.fetchAccountInfo(mExecutor, mHandler, mOauthRetrofit,
                                                mRedditDataRoomDatabase, accessToken,
                                                new FetchMyInfo.FetchMyInfoListener() {
                                                    @Override
                                                    public void onFetchMyInfoSuccess(String name, String profileImageUrl, String bannerImageUrl, int karma, boolean isMod) {
                                                        mCurrentAccountSharedPreferences.edit().putString(SharedPreferencesUtils.ACCESS_TOKEN, accessToken)
                                                            .putString(SharedPreferencesUtils.ACCOUNT_NAME, name)
                                                            .putString(SharedPreferencesUtils.ACCOUNT_IMAGE_URL, profileImageUrl).apply();
                                                        mCurrentAccountSharedPreferences.edit().remove(SharedPreferencesUtils.SUBSCRIBED_THINGS_SYNC_TIME).apply();
                                                        ParseAndInsertNewAccount.parseAndInsertNewAccount(mExecutor, new Handler(), name, accessToken, refreshToken, profileImageUrl, bannerImageUrl,
                                                                karma, isMod, authCode, mRedditDataRoomDatabase.accountDao(),
                                                                () -> {
                                                                    EventBus.getDefault().post(new NewUserLoggedInEvent());
                                                                    finish();
                                                                });
                                                    }

                                                    @Override
                                                    public void onFetchMyInfoFailed(boolean parseFailed) {
                                                        if (parseFailed) {
                                                            Toast.makeText(LoginActivity.this, R.string.parse_user_info_error, Toast.LENGTH_SHORT).show();
                                                        } else {
                                                            Toast.makeText(LoginActivity.this, R.string.cannot_fetch_user_info, Toast.LENGTH_SHORT).show();
                                                        }

                                                        finish();
                                                    }
                                        });
                                    } catch (JSONException e) {
                                        e.printStackTrace();
                                        Toast.makeText(LoginActivity.this, R.string.parse_json_response_error, Toast.LENGTH_SHORT).show();
                                        finish();
                                    }
                                } else {
                                    Toast.makeText(LoginActivity.this, R.string.retrieve_token_error, Toast.LENGTH_SHORT).show();
                                    finish();
                                }
                            }

                            @Override
                            public void onFailure(@NonNull Call<String> call, @NonNull Throwable t) {
                                Toast.makeText(LoginActivity.this, R.string.retrieve_token_error, Toast.LENGTH_SHORT).show();
                                t.printStackTrace();
                                finish();
                            }
                        });
                    } else {
                        Toast.makeText(LoginActivity.this, R.string.something_went_wrong, Toast.LENGTH_SHORT).show();
                        finish();
                    }

                } else if (url.contains("error=access_denied")) {
                    Toast.makeText(LoginActivity.this, R.string.access_denied, Toast.LENGTH_SHORT).show();
                    finish();
                } else {
                    view.loadUrl(url);
                }

                return true;
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                view.evaluateJavascript(
                    "(function() {" +
                    "  document.addEventListener('submit', function(e) {" +
                    "    if (e.submitter && e.submitter.name === 'authorize') {" +
                    "      AndroidLogger.log('rewriting authorize from [' + e.submitter.value + '] to [Allow]');" +
                    "      e.submitter.value = 'Allow';" +
                    "    }" +
                    "  }, true);" +
                    "})();",
                    null
                );
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame() && !Utils.isConnectedToInternet(LoginActivity.this)) {
                    binding.internetDisconnectedErrorLinearLayoutLoginActivity.setVisibility(View.VISIBLE);
                } else {
                    super.onReceivedError(view, request, error);
                }
            }
        });
    }

    @Override
    public SharedPreferences getDefaultSharedPreferences() {
        return mSharedPreferences;
    }

    @Override
    public SharedPreferences getCurrentAccountSharedPreferences() {
        return mCurrentAccountSharedPreferences;
    }

    @Override
    public CustomThemeWrapper getCustomThemeWrapper() {
        return mCustomThemeWrapper;
    }

    @Override
    protected void applyCustomTheme() {
        int backgroundColor = mCustomThemeWrapper.getBackgroundColor();
        binding.getRoot().setBackgroundColor(backgroundColor);
        applyAppBarLayoutAndCollapsingToolbarLayoutAndToolbarTheme(binding.appbarLayoutLoginActivity, null, binding.toolbarLoginActivity);
        int primaryTextColor = mCustomThemeWrapper.getPrimaryTextColor();
        binding.twoFaInfOTextViewLoginActivity.setTextColor(primaryTextColor);
        Drawable infoDrawable = Utils.getTintedDrawable(this, R.drawable.ic_info_preference_day_night_24dp, mCustomThemeWrapper.getPrimaryIconColor());
        binding.twoFaInfOTextViewLoginActivity.setCompoundDrawablesWithIntrinsicBounds(infoDrawable, null, null, null);
        binding.internetDisconnectedErrorLinearLayoutLoginActivity.setBackgroundColor(backgroundColor);
        binding.internetDisconnectedErrorTextViewLoginActivity.setTextColor(primaryTextColor);
        binding.internetDisconnectedErrorRetryButtonLoginActivity.setTextColor(mCustomThemeWrapper.getButtonTextColor());
        binding.internetDisconnectedErrorRetryButtonLoginActivity.setBackgroundColor(mCustomThemeWrapper.getColorPrimaryLightTheme());
        applyFABTheme(binding.fabLoginActivity);

        if (typeface != null) {
            binding.twoFaInfOTextViewLoginActivity.setTypeface(typeface);
            binding.internetDisconnectedErrorTextViewLoginActivity.setTypeface(typeface);
            binding.internetDisconnectedErrorRetryButtonLoginActivity.setTypeface(typeface);
        }
    }

    private static class JsRequestLogger {
        @JavascriptInterface
        public void log(String message) {
            Log.d("LoginActivity", "[JS] " + message);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();

            return true;
        }

        return false;
    }
}
