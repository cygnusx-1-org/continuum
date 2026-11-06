package ml.docilealligator.infinityforreddit.settings;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceManager;
import androidx.preference.SwitchPreference;
import ml.docilealligator.infinityforreddit.R;
import ml.docilealligator.infinityforreddit.adapters.navigationdrawer.DrawerSection;
import ml.docilealligator.infinityforreddit.customviews.preference.CustomFontPreferenceFragmentCompat;
import ml.docilealligator.infinityforreddit.events.ChangeHideKarmaEvent;
import ml.docilealligator.infinityforreddit.events.ChangeNavigationDrawerSectionsEvent;
import ml.docilealligator.infinityforreddit.events.ChangeShowAvatarOnTheRightInTheNavigationDrawerEvent;
import ml.docilealligator.infinityforreddit.utils.SharedPreferencesUtils;
import org.greenrobot.eventbus.EventBus;

public class NavigationDrawerPreferenceFragment extends CustomFontPreferenceFragmentCompat {

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        PreferenceManager preferenceManager = getPreferenceManager();
        preferenceManager.setSharedPreferencesName(SharedPreferencesUtils.NAVIGATION_DRAWER_SHARED_PREFERENCES_FILE);
        // Older versions kept a collapse switch and a hide switch per section; turn them into the
        // single choice before the screen shows it.
        SharedPreferences drawerPreferences = mActivity.accountScopedPreferencesFor(
                SharedPreferencesUtils.NAVIGATION_DRAWER_SHARED_PREFERENCES_FILE);
        if (drawerPreferences != null) {
            for (DrawerSection section : DrawerSection.values()) {
                section.read(drawerPreferences);
            }
        }
        setPreferencesFromResource(R.xml.navigation_drawer_preferences, rootKey);

        SwitchPreference showAvatarOnTheRightSwitch = findPreference(SharedPreferencesUtils.SHOW_AVATAR_ON_THE_RIGHT);
        SwitchPreference hideKarmaSwitch = findPreference(SharedPreferencesUtils.HIDE_ACCOUNT_KARMA_NAV_BAR);

        if (showAvatarOnTheRightSwitch != null) {
            showAvatarOnTheRightSwitch.setOnPreferenceChangeListener((preference, newValue) -> {
                EventBus.getDefault().post(new ChangeShowAvatarOnTheRightInTheNavigationDrawerEvent((Boolean) newValue));
                return true;
            });
        }

        if (hideKarmaSwitch != null) {
            hideKarmaSwitch.setOnPreferenceChangeListener((preference, newValue) -> {
                EventBus.getDefault().post(new ChangeHideKarmaEvent((Boolean) newValue));
                return true;
            });
        }

        // Apply the collapse/hide section toggles live to the navigation drawer instead of
        // requiring a restart.
        // Posted via Handler so it runs AFTER the framework persists the new value (the change
        // listener fires before persistence), otherwise the drawer would re-read the old values.
        Preference.OnPreferenceChangeListener sectionListener = (preference, newValue) -> {
            new Handler(Looper.getMainLooper()).post(() -> EventBus.getDefault().post(new ChangeNavigationDrawerSectionsEvent()));
            return true;
        };
        androidx.preference.ListPreference recentLimit = findPreference(SharedPreferencesUtils.RECENT_SUBREDDITS_LIMIT);
        if (recentLimit != null) {
            recentLimit.setOnPreferenceChangeListener(sectionListener);
        }
        for (String key : NAVIGATION_DRAWER_SECTION_KEYS) {
            Preference sectionPreference = findPreference(key);
            if (sectionPreference != null) {
                sectionPreference.setOnPreferenceChangeListener(sectionListener);
            }
        }
    }

    private static final String[] NAVIGATION_DRAWER_SECTION_KEYS = {
            SharedPreferencesUtils.COLLAPSE_ACCOUNT_SECTION,
            SharedPreferencesUtils.COLLAPSE_REDDIT_SECTION,
            SharedPreferencesUtils.COLLAPSE_POST_SECTION,
            SharedPreferencesUtils.COLLAPSE_PREFERENCES_SECTION,
            SharedPreferencesUtils.RECENT_SUBREDDITS_SECTION,
            SharedPreferencesUtils.FAVORITE_SUBREDDITS_SECTION,
            SharedPreferencesUtils.SUBSCRIBED_SUBREDDITS_SECTION,
            SharedPreferencesUtils.SHOW_THEME_TOGGLE_IN_NAVIGATION_DRAWER,
            SharedPreferencesUtils.SHOW_NSFW_TOGGLE_IN_NAVIGATION_DRAWER,
            SharedPreferencesUtils.SHOW_THUMBNAIL_ON_THE_LEFT_TOGGLE_IN_NAVIGATION_DRAWER,
            SharedPreferencesUtils.SHOW_RESUME_TOGGLE_IN_NAVIGATION_DRAWER,
    };
}