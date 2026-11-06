package ml.docilealligator.infinityforreddit.settings;

import android.os.Build;
import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.SwitchPreference;
import ml.docilealligator.infinityforreddit.R;
import ml.docilealligator.infinityforreddit.customviews.preference.CustomFontPreferenceFragmentCompat;
import ml.docilealligator.infinityforreddit.events.ChangeHideFabInPostFeedEvent;
import ml.docilealligator.infinityforreddit.events.ChangeShowElapsedTimeEvent;
import ml.docilealligator.infinityforreddit.events.ChangeTimeFormatEvent;
import ml.docilealligator.infinityforreddit.events.ChangeVoteButtonsPositionEvent;
import ml.docilealligator.infinityforreddit.events.RecreateActivityEvent;
import ml.docilealligator.infinityforreddit.utils.SharedPreferencesUtils;
import org.greenrobot.eventbus.EventBus;

public class InterfacePreferenceFragment extends CustomFontPreferenceFragmentCompat {
    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        setPreferencesFromResource(R.xml.interface_preferences, rootKey);

        SwitchPreference immersiveInterfaceSwitch = findPreference(SharedPreferencesUtils.IMMERSIVE_INTERFACE_KEY);
        SwitchPreference disableImmersiveInterfaceInLandscapeModeSwitch = findPreference(SharedPreferencesUtils.DISABLE_IMMERSIVE_INTERFACE_IN_LANDSCAPE_MODE);
        SwitchPreference showElapsedTimeSwitch = findPreference(SharedPreferencesUtils.SHOW_ELAPSED_TIME_KEY);
        ListPreference timeFormatList = findPreference(SharedPreferencesUtils.TIME_FORMAT_KEY);
        SwitchPreference hideFabInPostFeedSwitchPreference = findPreference(SharedPreferencesUtils.HIDE_FAB_IN_POST_FEED);
        SwitchPreference voteButtonsOnTheRightSwitch = findPreference(SharedPreferencesUtils.VOTE_BUTTONS_ON_THE_RIGHT_KEY);
        Preference displayCategory = findPreference("display_category");
        SwitchPreference forceMaxRefreshRateSwitch = findPreference(SharedPreferencesUtils.FORCE_MAX_REFRESH_RATE_KEY);

        // Immersive mode needs API 26; below it the switches would do nothing.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            if (immersiveInterfaceSwitch != null) {
                immersiveInterfaceSwitch.setVisible(false);
            }
            if (disableImmersiveInterfaceInLandscapeModeSwitch != null) {
                disableImmersiveInterfaceInLandscapeModeSwitch.setVisible(false);
            }
        }

        if (immersiveInterfaceSwitch != null) {
            immersiveInterfaceSwitch.setOnPreferenceChangeListener((preference, newValue) -> {
                EventBus.getDefault().post(new RecreateActivityEvent());
                return true;
            });
        }
        if (disableImmersiveInterfaceInLandscapeModeSwitch != null) {
            disableImmersiveInterfaceInLandscapeModeSwitch.setOnPreferenceChangeListener((preference, newValue) -> {
                EventBus.getDefault().post(new RecreateActivityEvent());
                return true;
            });
        }

        if (showElapsedTimeSwitch != null) {
            showElapsedTimeSwitch.setOnPreferenceChangeListener((preference, newValue) -> {
                EventBus.getDefault().post(new ChangeShowElapsedTimeEvent((Boolean) newValue));
                return true;
            });
        }
        if (timeFormatList != null) {
            timeFormatList.setOnPreferenceChangeListener((preference, newValue) -> {
                EventBus.getDefault().post(new ChangeTimeFormatEvent((String) newValue));
                return true;
            });
        }

        // Display.getSupportedModes() is API 23+, and below it no device offers more than 60Hz,
        // so the Force Maximum Refresh Rate option is hidden where it would be a no-op.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            if (displayCategory != null) {
                displayCategory.setVisible(false);
            }
            if (forceMaxRefreshRateSwitch != null) {
                forceMaxRefreshRateSwitch.setVisible(false);
            }
        }

        if (forceMaxRefreshRateSwitch != null) {
            forceMaxRefreshRateSwitch.setOnPreferenceChangeListener((preference, newValue) -> {
                EventBus.getDefault().post(new RecreateActivityEvent());
                return true;
            });
        }

        if (hideFabInPostFeedSwitchPreference != null) {
            hideFabInPostFeedSwitchPreference.setOnPreferenceChangeListener((preference, newValue) -> {
                EventBus.getDefault().post(new ChangeHideFabInPostFeedEvent((Boolean) newValue));
                return true;
            });
        }

        if (voteButtonsOnTheRightSwitch != null) {
            voteButtonsOnTheRightSwitch.setOnPreferenceChangeListener((preference, newValue) -> {
                EventBus.getDefault().post(new ChangeVoteButtonsPositionEvent((Boolean) newValue));
                return true;
            });
        }
    }
}
