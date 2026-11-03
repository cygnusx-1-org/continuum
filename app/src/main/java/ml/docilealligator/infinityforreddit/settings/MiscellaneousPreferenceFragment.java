package ml.docilealligator.infinityforreddit.settings;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.style.ClickableSpan;
import android.text.style.URLSpan;
import android.util.Log;
import android.view.View;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.browser.customtabs.CustomTabsClient;
import androidx.browser.customtabs.CustomTabsService;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.SwitchPreference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executor;
import javax.inject.Inject;
import javax.inject.Named;
import ml.docilealligator.infinityforreddit.Infinity;
import ml.docilealligator.infinityforreddit.R;
import ml.docilealligator.infinityforreddit.account.AccountScope;
import ml.docilealligator.infinityforreddit.activities.LinkResolverActivity;
import ml.docilealligator.infinityforreddit.customviews.preference.CustomFontPreferenceFragmentCompat;
import ml.docilealligator.infinityforreddit.events.ChangeSavePostFeedScrolledPositionEvent;
import ml.docilealligator.infinityforreddit.events.RecreateActivityEvent;
import ml.docilealligator.infinityforreddit.resume.ResumeState;
import ml.docilealligator.infinityforreddit.utils.RedditLinkUtils;
import ml.docilealligator.infinityforreddit.utils.SharedPreferencesUtils;
import org.greenrobot.eventbus.EventBus;

public class MiscellaneousPreferenceFragment extends CustomFontPreferenceFragmentCompat {

    @Inject
    @Named("default")
    SharedPreferences mSharedPreferences;
    @Inject
    @Named("post_feed_scrolled_position_cache")
    SharedPreferences cache;
    @Inject
    Executor executor;

    public MiscellaneousPreferenceFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        setPreferencesFromResource(R.xml.miscellaneous_preferences, rootKey);

        ((Infinity) mActivity.getApplication()).getAppComponent().inject(this);

        ListPreference linkHandlerListPreference = findPreference(SharedPreferencesUtils.LINK_HANDLER);
        ListPreference ephemeralBrowserListPreference = findPreference(SharedPreferencesUtils.EPHEMERAL_CUSTOM_TAB_PACKAGE);
        ListPreference specificBrowserListPreference = findPreference(SharedPreferencesUtils.SPECIFIC_BROWSER_PACKAGE);
        ListPreference mainPageBackButtonActionListPreference = findPreference(SharedPreferencesUtils.MAIN_PAGE_BACK_BUTTON_ACTION);
        SwitchPreference savePostFeedScrolledPositionSwitch = findPreference(SharedPreferencesUtils.SAVE_FRONT_PAGE_SCROLLED_POSITION);
        SwitchPreference resumeWhereILeftOffSwitch = findPreference(SharedPreferencesUtils.RESUME_WHERE_I_LEFT_OFF);
        ListPreference languageListPreference = findPreference(SharedPreferencesUtils.LANGUAGE);
        ListPreference sharedLinkDomainListPreference = findPreference(SharedPreferencesUtils.SHARED_LINK_DOMAIN);
        EditTextPreference sharedLinkCustomDomainPreference = findPreference(SharedPreferencesUtils.SHARED_LINK_CUSTOM_DOMAIN);

        List<String[]> ephemeralBrowsers = findEphemeralBrowsers(mActivity);
        boolean hasEphemeralBrowser = !ephemeralBrowsers.isEmpty();

        List<String[]> installedBrowsers = findInstalledBrowsers(mActivity);

        String linkHandlerKey = AccountScope.key(mActivity.accountName, SharedPreferencesUtils.LINK_HANDLER_BASE);
        String ephemeralPkgKey = AccountScope.key(mActivity.accountName, SharedPreferencesUtils.EPHEMERAL_CUSTOM_TAB_PACKAGE_BASE);
        String specificPkgKey = AccountScope.key(mActivity.accountName, SharedPreferencesUtils.SPECIFIC_BROWSER_PACKAGE_BASE);
        String currentLinkHandler = mSharedPreferences.getString(linkHandlerKey, "0");

        if (linkHandlerListPreference != null) {
            linkHandlerListPreference.setPersistent(false);
            filterLinkHandlerEntries(linkHandlerListPreference, hasEphemeralBrowser);
            linkHandlerListPreference.setValue(currentLinkHandler);
            linkHandlerListPreference.setOnPreferenceChangeListener((preference, newValue) -> {
                mSharedPreferences.edit().putString(linkHandlerKey, (String) newValue).apply();
                if (ephemeralBrowserListPreference != null) {
                    ephemeralBrowserListPreference.setVisible("3".equals(newValue));
                }
                if (specificBrowserListPreference != null) {
                    specificBrowserListPreference.setVisible("4".equals(newValue));
                }
                return true;
            });
        }

        if (ephemeralBrowserListPreference != null) {
            if (hasEphemeralBrowser) {
                populateEphemeralBrowserEntries(ephemeralBrowserListPreference, ephemeralBrowsers);
                ephemeralBrowserListPreference.setPersistent(false);
                String savedEphemeralPkg = mSharedPreferences.getString(ephemeralPkgKey, "");
                if (savedEphemeralPkg != null && !savedEphemeralPkg.isEmpty()) {
                    ephemeralBrowserListPreference.setValue(savedEphemeralPkg);
                }
                ephemeralBrowserListPreference.setVisible("3".equals(currentLinkHandler));
                ephemeralBrowserListPreference.setOnPreferenceChangeListener((preference, newValue) -> {
                    mSharedPreferences.edit().putString(ephemeralPkgKey, (String) newValue).apply();
                    return true;
                });
            } else {
                ephemeralBrowserListPreference.setVisible(false);
            }
        }

        if (specificBrowserListPreference != null) {
            if (!installedBrowsers.isEmpty()) {
                populateBrowserEntries(specificBrowserListPreference, installedBrowsers);
                specificBrowserListPreference.setPersistent(false);
                String savedPkg = mSharedPreferences.getString(specificPkgKey, "");
                if (savedPkg != null && !savedPkg.isEmpty()) {
                    specificBrowserListPreference.setValue(savedPkg);
                } else {
                    String firstPkg = installedBrowsers.get(0)[1];
                    specificBrowserListPreference.setValue(firstPkg);
                    mSharedPreferences.edit().putString(specificPkgKey, firstPkg).apply();
                }
                specificBrowserListPreference.setVisible("4".equals(currentLinkHandler));
                specificBrowserListPreference.setOnPreferenceChangeListener((preference, newValue) -> {
                    mSharedPreferences.edit().putString(specificPkgKey, (String) newValue).apply();
                    return true;
                });
            } else {
                specificBrowserListPreference.setVisible(false);
            }
        }

        if (sharedLinkDomainListPreference != null) {
            // The XML has no default for this list; without a value, it would open with nothing
            // selected rather than on the domain links already go out on.
            String sharedLinkDomain = RedditLinkUtils.sharedLinkDomainChoice(mSharedPreferences);
            sharedLinkDomainListPreference.setValue(sharedLinkDomain);
            // The chosen domain, as the other lists here show theirs, with the description under it
            // after a blank line, the way the swipe threshold's summary sets its value apart.
            CharSequence description = withLinksOpenedInApp(getText(R.string.settings_shared_link_domain_summary));
            sharedLinkDomainListPreference.setSummaryProvider(preference -> {
                CharSequence entry = ((ListPreference) preference).getEntry();
                return entry == null ? description : TextUtils.concat(entry, "\n\n", description);
            });
            if (sharedLinkCustomDomainPreference != null) {
                // Disabled rather than hidden, so picking Custom does not shift the rows below.
                sharedLinkCustomDomainPreference.setEnabled(
                        RedditLinkUtils.CUSTOM_SHARED_LINK_DOMAIN.equals(sharedLinkDomain));
            }
            sharedLinkDomainListPreference.setOnPreferenceChangeListener((preference, newValue) -> {
                if (sharedLinkCustomDomainPreference != null) {
                    sharedLinkCustomDomainPreference.setEnabled(
                            RedditLinkUtils.CUSTOM_SHARED_LINK_DOMAIN.equals(newValue));
                }
                return true;
            });
        }

        if (sharedLinkCustomDomainPreference != null) {
            sharedLinkCustomDomainPreference.setOnBindEditTextListener(editText -> {
                editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
                editText.setSingleLine(true);
                // Never an empty box: with nothing typed in yet, links go out on www.reddit.com.
                if (editText.getText().toString().trim().isEmpty()) {
                    editText.setText(RedditLinkUtils.WWW_REDDIT_HOST);
                    editText.setSelection(editText.length());
                }
            });
            // Never "Not set" either, even for an empty value an older build let through: the
            // row names the domain links actually go out on.
            sharedLinkCustomDomainPreference.setSummaryProvider(preference -> {
                String domain = RedditLinkUtils.normalizeDomain(((EditTextPreference) preference).getText());
                return domain != null ? domain : RedditLinkUtils.WWW_REDDIT_HOST;
            });
            sharedLinkCustomDomainPreference.setOnPreferenceChangeListener((preference, newValue) -> {
                String typed = (String) newValue;
                // An empty field is refused like any other non-domain, so the row always names
                // the domain links go out on.
                String domain = RedditLinkUtils.normalizeDomain(typed);
                if (domain == null) {
                    Toast.makeText(mActivity, R.string.settings_shared_link_custom_domain_invalid, Toast.LENGTH_SHORT).show();
                    return false;
                }
                if (!domain.equals(typed)) {
                    // Stored as the bare host it was cut down to, so the summary shows what links
                    // will actually go out on.
                    ((EditTextPreference) preference).setText(domain);
                    return false;
                }
                return true;
            });
        }

        if (mainPageBackButtonActionListPreference != null) {
            mainPageBackButtonActionListPreference.setOnPreferenceChangeListener((preference, newValue) -> {
                EventBus.getDefault().post(new RecreateActivityEvent());
                return true;
            });
        }

        if (savePostFeedScrolledPositionSwitch != null) {
            savePostFeedScrolledPositionSwitch.setOnPreferenceChangeListener((preference, newValue) -> {
                if (!(Boolean) newValue) {
                    cache.edit().clear().apply();
                }
                EventBus.getDefault().post(new ChangeSavePostFeedScrolledPositionEvent((Boolean) newValue));
                return true;
            });
        }

        if (resumeWhereILeftOffSwitch != null) {
            resumeWhereILeftOffSwitch.setOnPreferenceChangeListener((preference, newValue) -> {
                if (!(Boolean) newValue) {
                    // Turning it off forgets the recorded stack and the posts it points at. Leaving
                    // them would resume a session the user asked the app to stop remembering, if
                    // the setting were ever turned back on.
                    //
                    // Off the main thread, because it reads and unlinks the snapshot file, and on
                    // the application context, because backing out of Settings while it runs must
                    // not leave it holding a destroyed activity. Nothing races the in-memory half:
                    // every entry point into ResumeState checks the setting first, and the
                    // preference is written the moment this listener returns true.
                    Context context = mActivity.getApplicationContext();
                    executor.execute(() -> ResumeState.clear(context));
                }
                return true;
            });
        }

        if (languageListPreference != null) {
            languageListPreference.setOnPreferenceChangeListener((preference, newValue) -> {
                EventBus.getDefault().post(new RecreateActivityEvent());
                return true;
            });
        }

    }

    /**
     * {@code text} with each of its links opened through LinkResolverActivity, as every other link
     * in the app is, rather than straight in a browser, and drawn in the theme's link color.
     */
    private CharSequence withLinksOpenedInApp(CharSequence text) {
        SpannableStringBuilder linked = new SpannableStringBuilder(text);
        for (URLSpan urlSpan : linked.getSpans(0, linked.length(), URLSpan.class)) {
            int start = linked.getSpanStart(urlSpan);
            int end = linked.getSpanEnd(urlSpan);
            String url = urlSpan.getURL();
            linked.removeSpan(urlSpan);
            linked.setSpan(new ClickableSpan() {
                @Override
                public void onClick(@NonNull View widget) {
                    Intent intent = new Intent(mActivity, LinkResolverActivity.class);
                    intent.setData(Uri.parse(url));
                    mActivity.startActivity(intent);
                }

                @Override
                public void updateDrawState(@NonNull TextPaint textPaint) {
                    super.updateDrawState(textPaint);
                    textPaint.setColor(mActivity.customThemeWrapper.getLinkColor());
                }
            }, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return linked;
    }

    private static List<String[]> findInstalledBrowsers(Context context) {
        PackageManager pm = context.getPackageManager();
        int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                ? PackageManager.MATCH_ALL
                : PackageManager.GET_DISABLED_COMPONENTS;
        List<ResolveInfo> resolved = pm.queryIntentActivities(
                new Intent(Intent.ACTION_VIEW, Uri.parse("http://www.example.com")), flags);
        List<String[]> browsers = new ArrayList<>();
        for (ResolveInfo info : resolved) {
            if (info.activityInfo == null || !info.activityInfo.enabled) continue;
            String pkg = info.activityInfo.applicationInfo.packageName;
            String label = pm.getApplicationLabel(info.activityInfo.applicationInfo).toString();
            browsers.add(new String[]{label, pkg});
        }
        Collections.sort(browsers, Comparator.comparing(b -> b[0].toLowerCase(Locale.getDefault())));
        return browsers;
    }

    private static void populateBrowserEntries(ListPreference pref, List<String[]> browsers) {
        List<CharSequence> entries = new ArrayList<>();
        List<CharSequence> values = new ArrayList<>();
        for (String[] b : browsers) {
            entries.add(b[0]);
            values.add(b[1]);
        }
        pref.setEntries(entries.toArray(new CharSequence[0]));
        pref.setEntryValues(values.toArray(new CharSequence[0]));
    }

    private static List<String[]> findEphemeralBrowsers(Context context) {
        PackageManager pm = context.getPackageManager();
        Intent svcQuery = new Intent(CustomTabsService.ACTION_CUSTOM_TABS_CONNECTION);
        List<String[]> browsers = new ArrayList<>();
        for (ResolveInfo info : pm.queryIntentServices(svcQuery, 0)) {
            ServiceInfo si = info.serviceInfo;
            if (si == null) continue;
            if (!CustomTabsClient.isEphemeralBrowsingSupported(context, si.packageName)) continue;
            try {
                String label = pm.getApplicationLabel(pm.getApplicationInfo(si.packageName, 0)).toString();
                browsers.add(new String[]{label, si.packageName});
            } catch (PackageManager.NameNotFoundException ignored) {
                Log.d("MiscellaneousPreferenceFragment", "findEphemeralBrowsers: ignoring PackageManager.NameNotFoundException", ignored);
            }
        }
        Collections.sort(browsers, Comparator.comparing(b -> b[0].toLowerCase(Locale.getDefault())));
        return browsers;
    }

    private void populateEphemeralBrowserEntries(ListPreference pref, List<String[]> browsers) {
        List<CharSequence> entries = new ArrayList<>();
        List<CharSequence> values = new ArrayList<>();
        for (String[] b : browsers) {
            entries.add(b[0]);
            values.add(b[1]);
        }
        pref.setEntries(entries.toArray(new CharSequence[0]));
        pref.setEntryValues(values.toArray(new CharSequence[0]));
        String current = pref.getValue();
        boolean currentIsKnown = false;
        for (CharSequence v : values) {
            if (v.toString().equals(current)) {
                currentIsKnown = true;
                break;
            }
        }
        if (!currentIsKnown && !values.isEmpty()) {
            pref.setValue(values.get(0).toString());
        }
    }

    private void filterLinkHandlerEntries(ListPreference pref, boolean allowEphemeral) {
        if (allowEphemeral) return;
        CharSequence[] origEntries = pref.getEntries();
        CharSequence[] origValues = pref.getEntryValues();
        List<CharSequence> entries = new ArrayList<>();
        List<CharSequence> values = new ArrayList<>();
        for (int i = 0; i < origValues.length; i++) {
            if ("3".equals(origValues[i].toString())) continue;
            entries.add(origEntries[i]);
            values.add(origValues[i]);
        }
        pref.setEntries(entries.toArray(new CharSequence[0]));
        pref.setEntryValues(values.toArray(new CharSequence[0]));
        if ("3".equals(pref.getValue())) {
            pref.setValue("0");
        }
    }
}