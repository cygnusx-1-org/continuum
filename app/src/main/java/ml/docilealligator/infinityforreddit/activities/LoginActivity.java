package ml.docilealligator.infinityforreddit.activities;

import static ml.docilealligator.infinityforreddit.utils.UtilsKt.getChromeCustomTabPackageName;

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
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.OnApplyWindowInsetsListener;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;
import javax.inject.Inject;
import javax.inject.Named;
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
import ml.docilealligator.infinityforreddit.utils.OAuthLoginHelper;
import ml.docilealligator.infinityforreddit.utils.SharedPreferencesUtils;
import ml.docilealligator.infinityforreddit.utils.Utils;
import org.greenrobot.eventbus.EventBus;
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
    @SuppressWarnings("NullAway.Init")
    private String authCode;
    private ActivityLoginBinding binding;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
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

        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);

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
            if (getChromeCustomTabPackageName(this) == null) {
                Toast.makeText(this, R.string.login_chrome_required, Toast.LENGTH_SHORT).show();
            } else {
                Intent intent = new Intent(this, LoginChromeCustomTabActivity.class);
                startActivity(intent);
                finish();
            }
        });

        CookieManager.getInstance().removeAllCookies(aBoolean -> {
        });

        binding.webviewLoginActivity.addJavascriptInterface(new JsRequestLogger(), "AndroidLogger");

        // Hide Reddit's cookie consent wrapper before any page script runs. It can appear
        // behind the login form and steal focus from inputs. Inject at document-start so the
        // CSS rule + MutationObserver land before Reddit's own scripts mount the element.
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            WebViewCompat.addDocumentStartJavaScript(
                    binding.webviewLoginActivity,
                    "(function(){"
                            + "var ID='data-protection-consent-wrapper';"
                            + "var addStyle=function(){"
                            + "if(document.getElementById('_dpcStyle'))return;"
                            + "var s=document.createElement('style');"
                            + "s.id='_dpcStyle';"
                            + "s.textContent='#'+ID+'{display:none!important}';"
                            + "(document.head||document.documentElement||document)"
                            + ".appendChild(s);"
                            + "};"
                            + "var kill=function(){"
                            + "var el=document.getElementById(ID);"
                            + "if(el)el.remove();"
                            + "};"
                            + "addStyle();kill();"
                            + "new MutationObserver(function(){addStyle();kill();})"
                            + ".observe(document.documentElement||document,"
                            + "{childList:true,subtree:true});"
                            + "})()",
                    Collections.singleton("https://*.reddit.com"));
        }

        binding.webviewLoginActivity.loadUrl(url);
        binding.webviewLoginActivity.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                // Only the OAuth redirect URI is ours to handle; everything else keeps loading.
                if (url == null || !url.startsWith(APIUtils.REDIRECT_URI)) {
                    view.loadUrl(url);
                    return true;
                }

                Uri uri = Uri.parse(url);
                OAuthLoginHelper.RedirectResult redirect = OAuthLoginHelper.classifyRedirect(
                        uri.getQueryParameter("code"), uri.getQueryParameter("state"), uri.getQueryParameter("error"));
                switch (redirect.action) {
                    case EXCHANGE_CODE: {
                        authCode = Objects.requireNonNull(redirect.authCode);

                        Map<String, String> params = new HashMap<>();
                        params.put(APIUtils.GRANT_TYPE_KEY, "authorization_code");
                        params.put("code", authCode);
                        params.put(APIUtils.REDIRECT_URI_KEY, APIUtils.REDIRECT_URI);

                        RedditAPI api = mRetrofit.create(RedditAPI.class);
                        Call<String> accessTokenCall = api.getAccessToken(APIUtils.getHttpBasicAuthHeader(getApplicationContext()), params);
                        accessTokenCall.enqueue(new Callback<>() {
                            @Override
                            public void onResponse(@NonNull Call<String> call, @NonNull Response<String> response) {
                                        OAuthLoginHelper.TokenResult result =
                                                OAuthLoginHelper.classifyTokenResponse(response);
                                        if (!result.isSuccess()) {
                                            Toast.makeText(LoginActivity.this, OAuthLoginHelper.describeFailure(getApplicationContext(), result), Toast.LENGTH_LONG).show();
                                            finish();
                                            return;
                                        }
                                        String accessToken = Objects.requireNonNull(result.accessToken);
                                        String refreshToken = Objects.requireNonNull(result.refreshToken);

                                        FetchMyInfo.fetchAccountInfo(mExecutor, mHandler, mOauthRetrofit,
                                                mRedditDataRoomDatabase, accessToken,
                                                new FetchMyInfo.FetchMyInfoListener() {
                                                    @Override
                                                    public void onFetchMyInfoSuccess(String name, String profileImageUrl, @Nullable String bannerImageUrl, int karma, boolean isMod) {
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
                            }

                            @Override
                            public void onFailure(@NonNull Call<String> call, @NonNull Throwable t) {
                                OAuthLoginHelper.TokenResult result = OAuthLoginHelper.classifyThrowable(t);
                                Toast.makeText(LoginActivity.this, OAuthLoginHelper.describeFailure(getApplicationContext(), result), Toast.LENGTH_LONG).show();
                                finish();
                            }
                        });
                        break;
                    }
                    case ACCESS_DENIED:
                        Toast.makeText(LoginActivity.this, R.string.access_denied, Toast.LENGTH_SHORT).show();
                        finish();
                        break;
                    case OAUTH_ERROR:
                        Toast.makeText(LoginActivity.this, getString(R.string.oauth_error_reddit_error, redirect.errorValue), Toast.LENGTH_LONG).show();
                        finish();
                        break;
                    case STATE_MISMATCH:
                    case NONE:
                    default:
                        Toast.makeText(LoginActivity.this, R.string.something_went_wrong, Toast.LENGTH_SHORT).show();
                        finish();
                        break;
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
                // Detect the OAuth flow dead-ending on a bare-JSON error page (e.g. "{}") rendered in
                // the WebView when the redirect never fires — otherwise the user is stuck with no feedback.
                view.evaluateJavascript(
                    "(function() {" +
                    "  try {" +
                    "    if (location.host.indexOf('reddit.com') === -1) return;" +
                    "    var t = (document.body ? document.body.innerText : '').trim();" +
                    "    if (t.length > 0 && t.charAt(0) === '{') {" +
                    "      AndroidLogger.onPossibleErrorPage(t);" +
                    "    }" +
                    "  } catch (e) {}" +
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

    // Non-static so onPossibleErrorPage can finish the activity / show a toast on the UI thread.
    private class JsRequestLogger {
        @JavascriptInterface
        public void log(String message) {
            Log.d("LoginActivity", "[JS] " + message);
        }

        // Called from the WebView when a loaded reddit page's text starts with '{'. Confirm it's a
        // bare-JSON error page and, if so, surface a clear message instead of leaving "{}" on screen.
        @JavascriptInterface
        public void onPossibleErrorPage(String body) {
            if (!OAuthLoginHelper.looksLikeJsonErrorPage(body)) {
                return;
            }
            Log.e(OAuthLoginHelper.TAG, "OAuth flow dead-ended on an error page: " + body);
            runOnUiThread(() -> showLoginFailedDialog(R.string.oauth_error_login_page));
        }
    }

    /**
     * Shows a dismiss-to-close dialog for a terminal login error. Unlike a toast (capped at ~3.5s),
     * this stays on screen until the user acknowledges it, then finishes the activity.
     */
    private void showLoginFailedDialog(int messageResId) {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        CharSequence message = OAuthLoginHelper.warningText(
                mCustomThemeWrapper.getColorAccent(), getString(messageResId));
        new MaterialAlertDialogBuilder(this, R.style.MaterialAlertDialogTheme)
                .setTitle(R.string.oauth_login_failed_title)
                .setMessage(message)
                .setCancelable(false)
                .setPositiveButton(R.string.ok, null)
                .setOnDismissListener(dialog -> finish())
                .show();
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
