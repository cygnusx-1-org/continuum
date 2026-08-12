package ml.docilealligator.infinityforreddit.settings;

import android.content.SharedPreferences;
import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.preference.ListPreference;
import androidx.preference.SwitchPreference;
import ml.docilealligator.infinityforreddit.R;
import ml.docilealligator.infinityforreddit.customviews.preference.CustomFontPreferenceFragmentCompat;
import ml.docilealligator.infinityforreddit.events.ChangeDisableSwipingBetweenTabsEvent;
import ml.docilealligator.infinityforreddit.events.ChangeEnableSwipeActionSwitchEvent;
import ml.docilealligator.infinityforreddit.events.ChangeSwipeActionEvent;
import ml.docilealligator.infinityforreddit.events.ChangeSwipeActionThresholdEvent;
import ml.docilealligator.infinityforreddit.events.ChangeVibrateWhenActionTriggeredEvent;
import ml.docilealligator.infinityforreddit.utils.SharedPreferencesUtils;
import org.greenrobot.eventbus.EventBus;

public class SwipeActionPreferenceFragment extends CustomFontPreferenceFragmentCompat {

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        setPreferencesFromResource(R.xml.swipe_action_preferences, rootKey);

        SwitchPreference enableSwipeActionSwitch = findPreference(SharedPreferencesUtils.ENABLE_SWIPE_ACTION);
        ListPreference swipeLeftActionListPreference = findPreference(SharedPreferencesUtils.SWIPE_LEFT_ACTION);
        ListPreference swipeRightActionListPreference = findPreference(SharedPreferencesUtils.SWIPE_RIGHT_ACTION);
        SwitchPreference vibrateWhenActionTriggeredSwitch = findPreference(SharedPreferencesUtils.VIBRATE_WHEN_ACTION_TRIGGERED);
        SwitchPreference disableSwipingBetweenTabsSwitch = findPreference(SharedPreferencesUtils.DISABLE_SWIPING_BETWEEN_TABS);
        ListPreference swipeActionThresholdListPreference = findPreference(SharedPreferencesUtils.SWIPE_ACTION_THRESHOLD);

        if (enableSwipeActionSwitch != null) {
            // Comment swipe actions cannot coexist with Swipe Between Posts (both consume
            // horizontal swipes); Swipe Between Posts wins, so disable this when it is on.
            SharedPreferences preferenceManagerSharedPreferences = getPreferenceManager().getSharedPreferences();
            boolean swipeBetweenPostsEnabled = preferenceManagerSharedPreferences != null
                    && preferenceManagerSharedPreferences.getBoolean(SharedPreferencesUtils.SWIPE_BETWEEN_POSTS, false);
            if (swipeBetweenPostsEnabled) {
                enableSwipeActionSwitch.setEnabled(false);
                enableSwipeActionSwitch.setSummary(R.string.settings_enable_swipe_action_disabled_by_swipe_between_posts_summary);
            }
            enableSwipeActionSwitch.setOnPreferenceChangeListener((preference, newValue) -> {
                EventBus.getDefault().post(new ChangeEnableSwipeActionSwitchEvent((Boolean) newValue));
                return true;
            });
        }

        if (swipeLeftActionListPreference != null) {
            swipeLeftActionListPreference.setOnPreferenceChangeListener((preference, newValue) -> {
                if (swipeRightActionListPreference != null) {
                    EventBus.getDefault().post(new ChangeSwipeActionEvent(Integer.parseInt((String) newValue), Integer.parseInt(swipeRightActionListPreference.getValue())));
                } else {
                    EventBus.getDefault().post(new ChangeSwipeActionEvent(Integer.parseInt((String) newValue), -1));
                }
                return true;
            });
        }

        if (swipeRightActionListPreference != null) {
            swipeRightActionListPreference.setOnPreferenceChangeListener((preference, newValue) -> {
                if (swipeLeftActionListPreference != null) {
                    EventBus.getDefault().post(new ChangeSwipeActionEvent(Integer.parseInt(swipeLeftActionListPreference.getValue()), Integer.parseInt((String) newValue)));
                } else {
                    EventBus.getDefault().post(new ChangeSwipeActionEvent(-1, Integer.parseInt((String) newValue)));
                }
                return true;
            });
        }

        if (vibrateWhenActionTriggeredSwitch != null) {
            vibrateWhenActionTriggeredSwitch.setOnPreferenceChangeListener((preference, newValue) -> {
                EventBus.getDefault().post(new ChangeVibrateWhenActionTriggeredEvent((Boolean) newValue));
                return true;
            });
        }

        if (disableSwipingBetweenTabsSwitch != null) {
            disableSwipingBetweenTabsSwitch.setOnPreferenceChangeListener((preference, newValue) -> {
                EventBus.getDefault().post(new ChangeDisableSwipingBetweenTabsEvent((Boolean) newValue));
                return true;
            });
        }

        if (swipeActionThresholdListPreference != null) {
            swipeActionThresholdListPreference.setOnPreferenceChangeListener((preference, newValue) -> {
                EventBus.getDefault().post(new ChangeSwipeActionThresholdEvent(Float.parseFloat((String) newValue)));
                return true;
            });
        }
    }
}