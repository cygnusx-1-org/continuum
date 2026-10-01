package ml.docilealligator.infinityforreddit.settings

import android.os.Bundle
import androidx.preference.ListPreference
import androidx.preference.SwitchPreference
import ml.docilealligator.infinityforreddit.R
import ml.docilealligator.infinityforreddit.customviews.preference.CustomFontPreferenceFragmentCompat
import ml.docilealligator.infinityforreddit.events.ChangeEnableCommentSwipeActionSwitchEvent
import ml.docilealligator.infinityforreddit.events.ChangeSwipeActionLevelsEvent
import ml.docilealligator.infinityforreddit.utils.SharedPreferencesUtils
import ml.docilealligator.infinityforreddit.utils.SwipeActionPreferences
import org.greenrobot.eventbus.EventBus

/**
 * What a swipe does to a comment. Its switch and actions are its own and start on this screen's
 * defaults, which are also what [SwipeActionPreferences] reads while they are unset -- nothing here
 * follows the post screen.
 */
class CommentSwipeActionPreferenceFragment : CustomFontPreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.comment_swipe_action_preferences, rootKey)

        findPreference<SwitchPreference>(SharedPreferencesUtils.ENABLE_COMMENT_SWIPE_ACTION)
            ?.setOnPreferenceChangeListener { _, newValue ->
                if (newValue == true) {
                    SwipeActionPreferences.turnOffSwipeBetweenPosts(mActivity.defaultSharedPreferences)
                }
                EventBus.getDefault().post(ChangeEnableCommentSwipeActionSwitchEvent(newValue as Boolean))
                true
            }

        for (key in LEVEL_KEYS) {
            findPreference<ListPreference>(key)?.setOnPreferenceChangeListener { _, _ ->
                ChangeSwipeActionLevelsEvent.postAfterStoring()
                true
            }
        }
    }

    companion object {
        private val LEVEL_KEYS = arrayOf(
            SharedPreferencesUtils.COMMENT_SWIPE_LEFT_ACTION,
            SharedPreferencesUtils.COMMENT_SWIPE_LEFT_ACTION_LEVEL_2,
            SharedPreferencesUtils.COMMENT_SWIPE_LEFT_ACTION_LEVEL_3,
            SharedPreferencesUtils.COMMENT_SWIPE_RIGHT_ACTION,
            SharedPreferencesUtils.COMMENT_SWIPE_RIGHT_ACTION_LEVEL_2,
            SharedPreferencesUtils.COMMENT_SWIPE_RIGHT_ACTION_LEVEL_3,
        )
    }
}
