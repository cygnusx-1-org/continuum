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
import ml.docilealligator.infinityforreddit.events.ChangeEnableCommentSwipeActionSwitchEvent;
import ml.docilealligator.infinityforreddit.events.ChangeEnableSwipeActionSwitchEvent;
import ml.docilealligator.infinityforreddit.events.ChangeLockBottomAppBarEvent;
import ml.docilealligator.infinityforreddit.events.ChangePullToRefreshEvent;
import ml.docilealligator.infinityforreddit.utils.SharedPreferencesUtils;
import ml.docilealligator.infinityforreddit.utils.SwipeActionPreferences;
import org.greenrobot.eventbus.EventBus;

/**
 * A simple {@link Fragment} subclass.
 */
public class GesturesAndButtonsPreferenceFragment extends CustomFontPreferenceFragmentCompat {

    @Inject
    @Named("default")
    SharedPreferences sharedPreferences;

    @Nullable
    private SwitchPreference swipeBetweenPostsSwitch;

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

        swipeBetweenPostsSwitch = findPreference(SharedPreferencesUtils.SWIPE_BETWEEN_POSTS);
        if (swipeBetweenPostsSwitch != null) {
            swipeBetweenPostsSwitch.setOnPreferenceChangeListener((preference, newValue) -> {
                // Swipe Between Posts and the swipe actions both consume a horizontal swipe on a
                // post, so only one of them can be on. Each side switches the other off rather
                // than silently overriding it, which is what left a screen of settings that
                // looked available and did nothing.
                if ((Boolean) newValue) {
                    SwipeActionPreferences.turnOffSwipeActions(sharedPreferences);
                    EventBus.getDefault().post(new ChangeEnableSwipeActionSwitchEvent(false));
                    EventBus.getDefault().post(new ChangeEnableCommentSwipeActionSwitchEvent(false));
                }
                return true;
            });
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        // The swipe action screens can have switched this off while they were on top, and coming
        // back does not re-inflate the preference, so its checkbox would still say what it said
        // when this screen was built.
        if (swipeBetweenPostsSwitch != null) {
            swipeBetweenPostsSwitch.setChecked(
                    sharedPreferences.getBoolean(SharedPreferencesUtils.SWIPE_BETWEEN_POSTS, false));
        }
    }
}
