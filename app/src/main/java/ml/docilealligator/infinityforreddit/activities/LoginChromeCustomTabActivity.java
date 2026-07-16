package ml.docilealligator.infinityforreddit.activities;


import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.browser.customtabs.CustomTabColorSchemeParams;
import androidx.browser.customtabs.CustomTabsIntent;
import androidx.browser.customtabs.CustomTabsService;
import androidx.core.graphics.Insets;
import androidx.core.view.OnApplyWindowInsetsListener;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
import ml.docilealligator.infinityforreddit.databinding.ActivityLoginChromeCustomTabBinding;
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

public class LoginChromeCustomTabActivity extends BaseActivity {

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
    private ActivityLoginChromeCustomTabBinding binding;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        ((Infinity) getApplication()).getAppComponent().inject(this);

        setImmersiveModeNotApplicableBelowAndroid16();

        super.onCreate(savedInstanceState);
        binding = ActivityLoginChromeCustomTabBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        applyCustomTheme();

        if (isImmersiveInterfaceRespectForcedEdgeToEdge()) {
            ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), new OnApplyWindowInsetsListener() {
                @NonNull
                @Override
                public WindowInsetsCompat onApplyWindowInsets(@NonNull View v, @NonNull WindowInsetsCompat insets) {
                    Insets allInsets = Utils.getInsets(insets, false, isForcedImmersiveInterface());

                    setMargins(binding.toolbarLoginChromeCustomTabActivity,
                            allInsets.left,
                            allInsets.top,
                            allInsets.right,
                            BaseActivity.IGNORE_MARGIN);

                    return WindowInsetsCompat.CONSUMED;
                }
            });
        }

        setSupportActionBar(binding.toolbarLoginChromeCustomTabActivity);
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);

        Intent intent = getIntent();
        if (intent != null && intent.getData() != null) {
            handleRedirectUri(intent.getData());
        } else {
            checkAndOpenLoginPage();
        }

        binding.openWebpageButtonLoginChromeCustomTabActivity.setOnClickListener(view -> {
            checkAndOpenLoginPage();
        });
    }

    @Override
    protected void onNewIntent(@NonNull Intent intent) {
        super.onNewIntent(intent);

        Uri uri = intent.getData();

        if (uri == null) {
            binding.openWebpageButtonLoginChromeCustomTabActivity.setVisibility(View.VISIBLE);
            return;
        }

        handleRedirectUri(uri);
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
        binding.getRoot().setBackgroundColor(mCustomThemeWrapper.getBackgroundColor());
        applyAppBarLayoutAndCollapsingToolbarLayoutAndToolbarTheme(binding.appbarLayoutLoginChromeCustomTabActivity, null, binding.toolbarLoginChromeCustomTabActivity);
        binding.openWebpageButtonLoginChromeCustomTabActivity.setTextColor(mCustomThemeWrapper.getButtonTextColor());
        binding.openWebpageButtonLoginChromeCustomTabActivity.setBackgroundColor(mCustomThemeWrapper.getColorPrimaryLightTheme());
        if (typeface != null) {
            binding.openWebpageButtonLoginChromeCustomTabActivity.setTypeface(typeface);
        }
    }

    private void handleRedirectUri(@NonNull Uri uri) {
        binding.openWebpageButtonLoginChromeCustomTabActivity.setVisibility(View.GONE);

        OAuthLoginHelper.RedirectResult redirect = OAuthLoginHelper.classifyRedirect(
                uri.getQueryParameter("code"), uri.getQueryParameter("state"), uri.getQueryParameter("error"));
        switch (redirect.action) {
            case EXCHANGE_CODE:
                exchangeCodeForToken(redirect.authCode);
                break;
            case ACCESS_DENIED:
                Toast.makeText(this, R.string.access_denied, Toast.LENGTH_SHORT).show();
                finish();
                break;
            case OAUTH_ERROR:
                Toast.makeText(this, getString(R.string.oauth_error_reddit_error, redirect.errorValue), Toast.LENGTH_LONG).show();
                finish();
                break;
            case STATE_MISMATCH:
            case NONE:
            default:
                Toast.makeText(this, R.string.something_went_wrong, Toast.LENGTH_SHORT).show();
                finish();
                break;
        }
    }

    private void exchangeCodeForToken(String authCode) {
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
                    Toast.makeText(LoginChromeCustomTabActivity.this, OAuthLoginHelper.describeFailure(getApplicationContext(), result), Toast.LENGTH_LONG).show();
                    finish();
                    return;
                }
                String accessToken = result.accessToken;
                String refreshToken = result.refreshToken;

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
                                    Toast.makeText(LoginChromeCustomTabActivity.this, R.string.parse_user_info_error, Toast.LENGTH_SHORT).show();
                                } else {
                                    Toast.makeText(LoginChromeCustomTabActivity.this, R.string.cannot_fetch_user_info, Toast.LENGTH_SHORT).show();
                                }

                                finish();
                            }
                        });
            }

            @Override
            public void onFailure(@NonNull Call<String> call, @NonNull Throwable t) {
                OAuthLoginHelper.TokenResult result = OAuthLoginHelper.classifyThrowable(t);
                Toast.makeText(LoginChromeCustomTabActivity.this, OAuthLoginHelper.describeFailure(getApplicationContext(), result), Toast.LENGTH_LONG).show();
                finish();
            }
        });
    }

    private void checkAndOpenLoginPage() {
        // The browser/Custom Tab path relies on a static manifest intent filter to relaunch the app
        // when Reddit redirects to the configured Redirect URI. If no filter matches it, the redirect
        // would silently strand the browser, so warn up front instead of launching.
        if (!isRedirectUriRegistered()) {
            showUnsupportedRedirectUriDialog();
            return;
        }

        Uri.Builder uriBuilder = Uri.parse(APIUtils.OAUTH_URL).buildUpon();
        uriBuilder.appendQueryParameter(APIUtils.CLIENT_ID_KEY, APIUtils.getClientId(getApplicationContext()));
        uriBuilder.appendQueryParameter(APIUtils.RESPONSE_TYPE_KEY, APIUtils.RESPONSE_TYPE);
        uriBuilder.appendQueryParameter(APIUtils.STATE_KEY, APIUtils.STATE);
        uriBuilder.appendQueryParameter(APIUtils.REDIRECT_URI_KEY, APIUtils.REDIRECT_URI);
        uriBuilder.appendQueryParameter(APIUtils.DURATION_KEY, APIUtils.DURATION);
        uriBuilder.appendQueryParameter(APIUtils.SCOPE_KEY, APIUtils.SCOPE);
        Uri loginUri = uriBuilder.build();

        ArrayList<ResolveInfo> resolveInfos = getCustomTabsPackages(getPackageManager());

        if (!resolveInfos.isEmpty()) {
            String packageName = resolveInfos.get(0).activityInfo.packageName;

            if (isFirefoxBrowser(packageName)) {
                // Firefox Custom Tabs don't handle custom scheme redirects properly.
                // Use a regular browser intent instead — the full browser will
                // dispatch redreader://rr_oauth_redir via standard Android intent resolution.
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, loginUri);
                browserIntent.setPackage(packageName);
                try {
                    startActivity(browserIntent);
                } catch (ActivityNotFoundException e) {
                    Snackbar.make(binding.getRoot(), R.string.custom_tab_not_available, Snackbar.LENGTH_LONG).show();
                }
            } else {
                CustomTabsIntent.Builder builder = new CustomTabsIntent.Builder();
                // add share action to menu list
                builder.setShareState(CustomTabsIntent.SHARE_STATE_ON);
                builder.setDefaultColorSchemeParams(
                    new CustomTabColorSchemeParams.Builder()
                        .setToolbarColor(mCustomThemeWrapper.getColorPrimary())
                        .build());
                CustomTabsIntent customTabsIntent = builder.build();
                customTabsIntent.intent.setPackage(packageName);
                customTabsIntent.intent.putExtra("com.google.android.apps.chrome.EXTRA_OPEN_NEW_INCOGNITO_TAB", true);

                try {
                    customTabsIntent.launchUrl(this, loginUri);
                } catch (ActivityNotFoundException e) {
                    Snackbar.make(binding.getRoot(), R.string.custom_tab_not_available, Snackbar.LENGTH_LONG).show();
                }
            }
        } else {
            // No Custom Tabs browsers found — try opening in any available browser.
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, loginUri);
            try {
                startActivity(browserIntent);
            } catch (ActivityNotFoundException e) {
                Snackbar.make(binding.getRoot(), R.string.custom_tab_not_available, Snackbar.LENGTH_LONG).show();
            }
        }
    }

    // Resolves the configured Redirect URI against this app's own manifest intent filters. Using the
    // package manager (rather than a hardcoded string list) respects Android's exact scheme/host/
    // port/path matching and can't drift from the manifest.
    private boolean isRedirectUriRegistered() {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(APIUtils.REDIRECT_URI));
        intent.addCategory(Intent.CATEGORY_BROWSABLE);
        List<ResolveInfo> resolveInfos = getPackageManager().queryIntentActivities(intent, 0);
        for (ResolveInfo info : resolveInfos) {
            if (info.activityInfo != null && getPackageName().equals(info.activityInfo.packageName)) {
                return true;
            }
        }
        return false;
    }

    private void showUnsupportedRedirectUriDialog() {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        CharSequence message = OAuthLoginHelper.warningText(mCustomThemeWrapper.getColorAccent(),
                getString(R.string.oauth_error_redirect_uri_unregistered, APIUtils.REDIRECT_URI));
        new MaterialAlertDialogBuilder(this, R.style.MaterialAlertDialogTheme)
                .setTitle(R.string.oauth_login_failed_title)
                .setMessage(message)
                .setCancelable(false)
                .setPositiveButton(R.string.ok, null)
                .setOnDismissListener(dialog -> finish())
                .show();
    }

    private boolean isFirefoxBrowser(String packageName) {
        return packageName != null && (
            packageName.startsWith("org.mozilla.firefox") ||
            packageName.startsWith("org.mozilla.fenix") ||
            packageName.equals("org.mozilla.focus") ||
            packageName.equals("org.mozilla.klar")
        );
    }

    private ArrayList<ResolveInfo> getCustomTabsPackages(PackageManager pm) {
        // Get default VIEW intent handler.
        Intent activityIntent = new Intent()
            .setAction(Intent.ACTION_VIEW)
            .addCategory(Intent.CATEGORY_BROWSABLE)
            .setData(Uri.fromParts("http", "", null));

        // Get all apps that can handle VIEW intents.
        List<ResolveInfo> resolvedActivityList = pm.queryIntentActivities(activityIntent, 0);
        ArrayList<ResolveInfo> packagesSupportingCustomTabs = new ArrayList<>();
        for (ResolveInfo info : resolvedActivityList) {
            Intent serviceIntent = new Intent();
            serviceIntent.setAction(CustomTabsService.ACTION_CUSTOM_TABS_CONNECTION);
            serviceIntent.setPackage(info.activityInfo.packageName);
            // Check if this package also resolves the Custom Tabs service.
            if (pm.resolveService(serviceIntent, 0) != null) {
                packagesSupportingCustomTabs.add(info);
            }
        }

        return packagesSupportingCustomTabs;
    }
}
