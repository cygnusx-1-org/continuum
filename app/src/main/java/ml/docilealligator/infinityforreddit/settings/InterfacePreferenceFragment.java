package ml.docilealligator.infinityforreddit.settings;


import android.os.Build;
import android.os.Bundle;

import androidx.preference.Preference;
import androidx.preference.SwitchPreference;

import org.greenrobot.eventbus.EventBus;

import ml.docilealligator.infinityforreddit.R;
import ml.docilealligator.infinityforreddit.customviews.preference.CustomFontPreferenceFragmentCompat;
import ml.docilealligator.infinityforreddit.events.ChangeHideFabInPostFeedEvent;
import ml.docilealligator.infinityforreddit.events.ChangeVoteButtonsPositionEvent;
import ml.docilealligator.infinityforreddit.events.RecreateActivityEvent;
import ml.docilealligator.infinityforreddit.utils.SharedPreferencesUtils;

public class InterfacePreferenceFragment extends CustomFontPreferenceFragmentCompat {
    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.interface_preferences, rootKey);

        Preference immersiveInterfaceEntryPreference = findPreference(SharedPreferencesUtils.IMMERSIVE_INTERFACE_ENTRY_KEY);
        SwitchPreference hideFabInPostFeedSwitchPreference = findPreference(SharedPreferencesUtils.HIDE_FAB_IN_POST_FEED);
        SwitchPreference bottomAppBarSwitch = findPreference(SharedPreferencesUtils.BOTTOM_APP_BAR_KEY);
        SwitchPreference voteButtonsOnTheRightSwitch = findPreference(SharedPreferencesUtils.VOTE_BUTTONS_ON_THE_RIGHT_KEY);
        Preference displayCategory = findPreference("display_category");
        SwitchPreference forceMaxRefreshRateSwitch = findPreference(SharedPreferencesUtils.FORCE_MAX_REFRESH_RATE_KEY);

        if (immersiveInterfaceEntryPreference != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            immersiveInterfaceEntryPreference.setVisible(true);
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

        if (bottomAppBarSwitch != null) {
            bottomAppBarSwitch.setOnPreferenceChangeListener((preference, newValue) -> {
                EventBus.getDefault().post(new RecreateActivityEvent());
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
