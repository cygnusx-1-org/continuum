package ml.docilealligator.infinityforreddit.settings;

import android.content.SharedPreferences;
import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.preference.Preference;
import androidx.preference.SwitchPreference;
import javax.inject.Inject;
import javax.inject.Named;
import ml.docilealligator.infinityforreddit.Infinity;
import ml.docilealligator.infinityforreddit.R;
import ml.docilealligator.infinityforreddit.customviews.preference.CustomFontPreferenceFragmentCompat;
import ml.docilealligator.infinityforreddit.events.ChangeLockBottomAppBarEvent;
import ml.docilealligator.infinityforreddit.events.ChangePullToRefreshEvent;
import ml.docilealligator.infinityforreddit.utils.SharedPreferencesUtils;
import org.greenrobot.eventbus.EventBus;

/**
 * A simple {@link Fragment} subclass.
 */
public class GesturesAndButtonsPreferenceFragment extends CustomFontPreferenceFragmentCompat {

    @Inject
    @Named("default")
    SharedPreferences sharedPreferences;

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        setPreferencesFromResource(R.xml.gestures_and_buttons_preferences, rootKey);
        ((Infinity) mActivity.getApplication()).getAppComponent().inject(this);

        SwitchPreference lockJumpToNextTopLevelCommentButtonSwitch =
                findPreference(SharedPreferencesUtils.LOCK_JUMP_TO_NEXT_TOP_LEVEL_COMMENT_BUTTON);
        SwitchPreference lockBottomAppBarSwitch = findPreference(SharedPreferencesUtils.LOCK_BOTTOM_APP_BAR);
        SwitchPreference swipeUpToHideJumpToNextTopLevelCommentButtonSwitch =
                findPreference(SharedPreferencesUtils.SWIPE_UP_TO_HIDE_JUMP_TO_NEXT_TOP_LEVEL_COMMENT_BUTTON);
        SwitchPreference pullToRefreshSwitch = findPreference(SharedPreferencesUtils.PULL_TO_REFRESH);

        if (lockJumpToNextTopLevelCommentButtonSwitch != null && lockBottomAppBarSwitch != null &&
                swipeUpToHideJumpToNextTopLevelCommentButtonSwitch != null) {
            lockJumpToNextTopLevelCommentButtonSwitch.setOnPreferenceChangeListener((preference, newValue) -> {
                swipeUpToHideJumpToNextTopLevelCommentButtonSwitch.setVisible(!((Boolean) newValue));
                return true;
            });

            lockBottomAppBarSwitch.setOnPreferenceChangeListener((preference, newValue) -> {
                EventBus.getDefault().post(new ChangeLockBottomAppBarEvent((Boolean) newValue));
                return true;
            });

            if (!sharedPreferences.getBoolean(SharedPreferencesUtils.LOCK_JUMP_TO_NEXT_TOP_LEVEL_COMMENT_BUTTON, false)) {
                swipeUpToHideJumpToNextTopLevelCommentButtonSwitch.setVisible(true);
            }
        }

        if (pullToRefreshSwitch != null) {
            pullToRefreshSwitch.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    EventBus.getDefault().post(new ChangePullToRefreshEvent((Boolean) newValue));
                    return true;
                }
            });
        }

        SwitchPreference swipeBetweenPostsSwitch = findPreference(SharedPreferencesUtils.SWIPE_BETWEEN_POSTS);
        if (swipeBetweenPostsSwitch != null) {
            // Swipe Between Posts and comment Swipe Action both consume horizontal swipes and
            // cannot coexist; the former takes precedence. Warn here when it is enabled.
            updateSwipeBetweenPostsSummary(swipeBetweenPostsSwitch, swipeBetweenPostsSwitch.isChecked());
            swipeBetweenPostsSwitch.setOnPreferenceChangeListener((preference, newValue) -> {
                updateSwipeBetweenPostsSummary(swipeBetweenPostsSwitch, (Boolean) newValue);
                return true;
            });
        }
    }

    private void updateSwipeBetweenPostsSummary(SwitchPreference swipeBetweenPostsSwitch, boolean enabled) {
        swipeBetweenPostsSwitch.setSummary(enabled
                ? getString(R.string.settings_swipe_between_posts_disables_comment_swipe_summary)
                : null);
    }
}
