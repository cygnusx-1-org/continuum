package ml.docilealligator.infinityforreddit.settings;

import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.preference.ListPreference;
import androidx.preference.SwitchPreference;
import ml.docilealligator.infinityforreddit.R;
import ml.docilealligator.infinityforreddit.customviews.preference.CustomFontPreferenceFragmentCompat;
import ml.docilealligator.infinityforreddit.events.ChangeDisableSwipingBetweenTabsEvent;
import ml.docilealligator.infinityforreddit.events.ChangeSwipeActionThresholdEvent;
import ml.docilealligator.infinityforreddit.events.ChangeVibrateWhenActionTriggeredEvent;
import ml.docilealligator.infinityforreddit.utils.SharedPreferencesUtils;
import org.greenrobot.eventbus.EventBus;

/**
 * What posts and comments share: how far a swipe has to travel, whether it buzzes, and the tab
 * gesture it competes with. The actions themselves are on the two screens this links to, which
 * have a different list each.
 */
public class SwipeActionPreferenceFragment extends CustomFontPreferenceFragmentCompat {

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        setPreferencesFromResource(R.xml.swipe_action_preferences, rootKey);

        SwitchPreference vibrateWhenActionTriggeredSwitch = findPreference(SharedPreferencesUtils.VIBRATE_WHEN_ACTION_TRIGGERED);
        SwitchPreference disableSwipingBetweenTabsSwitch = findPreference(SharedPreferencesUtils.DISABLE_SWIPING_BETWEEN_TABS);
        ListPreference swipeActionThresholdListPreference = findPreference(SharedPreferencesUtils.SWIPE_ACTION_THRESHOLD);

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
            moveOntoTheList(swipeActionThresholdListPreference);
            swipeActionThresholdListPreference.setOnPreferenceChangeListener((preference, newValue) -> {
                EventBus.getDefault().post(new ChangeSwipeActionThresholdEvent(Float.parseFloat((String) newValue)));
                return true;
            });
        }
    }

    /**
     * Pull a stored threshold the list no longer offers onto the nearest one it does.
     *
     * The offered range has narrowed, so an account that chose one of the values dropped from it
     * has a setting this screen cannot show: {@code getEntry()} is null, and the summary that
     * folds the current value in renders that as a blank line where the percentage belongs.
     * Moving it is also the only way the user can see what their swipe is actually doing.
     */
    private void moveOntoTheList(ListPreference threshold) {
        if (threshold.getEntry() != null) {
            return;
        }
        CharSequence[] offered = threshold.getEntryValues();
        if (offered == null || offered.length == 0) {
            return;
        }
        float stored;
        try {
            stored = Float.parseFloat(String.valueOf(threshold.getValue()));
        } catch (NumberFormatException | NullPointerException e) {
            stored = Float.parseFloat(offered[0].toString());
        }
        String nearest = offered[0].toString();
        float smallestGap = Float.MAX_VALUE;
        for (CharSequence candidate : offered) {
            float gap = Math.abs(Float.parseFloat(candidate.toString()) - stored);
            if (gap < smallestGap) {
                smallestGap = gap;
                nearest = candidate.toString();
            }
        }
        threshold.setValue(nearest);
        // setValue does not call the change listener, and a feed left running underneath this
        // screen would otherwise keep swiping at the distance that is no longer on offer.
        EventBus.getDefault().post(new ChangeSwipeActionThresholdEvent(Float.parseFloat(nearest)));
    }
}
