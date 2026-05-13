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
import android.widget.Toast;

import androidx.browser.customtabs.CustomTabsClient;
import androidx.browser.customtabs.CustomTabsService;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.SwitchPreference;

import org.greenrobot.eventbus.EventBus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Named;

import ml.docilealligator.infinityforreddit.Infinity;
import ml.docilealligator.infinityforreddit.R;
import ml.docilealligator.infinityforreddit.customviews.preference.CustomFontPreferenceFragmentCompat;
import ml.docilealligator.infinityforreddit.events.ChangePostFeedMaxResolutionEvent;
import ml.docilealligator.infinityforreddit.events.ChangeSavePostFeedScrolledPositionEvent;
import ml.docilealligator.infinityforreddit.events.RecreateActivityEvent;
import ml.docilealligator.infinityforreddit.utils.SharedPreferencesUtils;

public class MiscellaneousPreferenceFragment extends CustomFontPreferenceFragmentCompat {

    @Inject
    @Named("default")
    SharedPreferences mSharedPreferences;
    @Inject
    @Named("post_feed_scrolled_position_cache")
    SharedPreferences cache;

    public MiscellaneousPreferenceFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.miscellaneous_preferences, rootKey);

        ((Infinity) mActivity.getApplication()).getAppComponent().inject(this);

        ListPreference linkHandlerListPreference = findPreference(SharedPreferencesUtils.LINK_HANDLER);
        ListPreference ephemeralBrowserListPreference = findPreference(SharedPreferencesUtils.EPHEMERAL_CUSTOM_TAB_PACKAGE);
        ListPreference specificBrowserListPreference = findPreference(SharedPreferencesUtils.SPECIFIC_BROWSER_PACKAGE);
        ListPreference mainPageBackButtonActionListPreference = findPreference(SharedPreferencesUtils.MAIN_PAGE_BACK_BUTTON_ACTION);
        SwitchPreference savePostFeedScrolledPositionSwitch = findPreference(SharedPreferencesUtils.SAVE_FRONT_PAGE_SCROLLED_POSITION);
        ListPreference languageListPreference = findPreference(SharedPreferencesUtils.LANGUAGE);
        EditTextPreference postFeedMaxResolution = findPreference(SharedPreferencesUtils.POST_FEED_MAX_RESOLUTION);

        List<String[]> ephemeralBrowsers = findEphemeralBrowsers(mActivity);
        boolean hasEphemeralBrowser = !ephemeralBrowsers.isEmpty();

        List<String[]> installedBrowsers = findInstalledBrowsers(mActivity);

        String linkHandlerKey = mActivity.accountName + SharedPreferencesUtils.LINK_HANDLER_BASE;
        String ephemeralPkgKey = mActivity.accountName + SharedPreferencesUtils.EPHEMERAL_CUSTOM_TAB_PACKAGE_BASE;
        String specificPkgKey = mActivity.accountName + SharedPreferencesUtils.SPECIFIC_BROWSER_PACKAGE_BASE;
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

        if (languageListPreference != null) {
            languageListPreference.setOnPreferenceChangeListener((preference, newValue) -> {
                EventBus.getDefault().post(new RecreateActivityEvent());
                return true;
            });
        }

        if (postFeedMaxResolution != null) {
            postFeedMaxResolution.setOnPreferenceChangeListener((preference, newValue) -> {
                try {
                    int resolution = Integer.parseInt((String) newValue);
                    if (resolution <= 0) {
                        Toast.makeText(mActivity, R.string.not_a_valid_number, Toast.LENGTH_SHORT).show();
                        return false;
                    }
                    EventBus.getDefault().post(new ChangePostFeedMaxResolutionEvent(resolution));
                } catch (NumberFormatException e) {
                    Toast.makeText(mActivity, R.string.not_a_valid_number, Toast.LENGTH_SHORT).show();
                    return false;
                }
                return true;
            });
        }
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
        Collections.sort(browsers, Comparator.comparing(b -> b[0].toLowerCase()));
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
            }
        }
        Collections.sort(browsers, Comparator.comparing(b -> b[0].toLowerCase()));
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
        if ((current == null || !values.contains(current)) && !values.isEmpty()) {
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