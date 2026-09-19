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
 * What a swipe does to a comment. Comments used to read the post screen's settings outright, so
 * the switch and the first level of each direction start on whatever that screen said rather than
 * on this one's defaults -- turning a shared setting into two must not look like a reset.
 */
class CommentSwipeActionPreferenceFragment : CustomFontPreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.comment_swipe_action_preferences, rootKey)

        seedEnabledFromPostSwipe()
        seedFromPostAction(SharedPreferencesUtils.COMMENT_SWIPE_LEFT_ACTION,
            SharedPreferencesUtils.SWIPE_LEFT_ACTION, "0")
        seedFromPostAction(SharedPreferencesUtils.COMMENT_SWIPE_RIGHT_ACTION,
            SharedPreferencesUtils.SWIPE_RIGHT_ACTION, "1")

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

    /**
     * Opens on whatever the one shared switch said, since it used to turn comments on too.
     * Writing it is also what ends the fallback [SwipeActionPreferences.commentSwipeEnabled]
     * applies while the key is missing.
     */
    private fun seedEnabledFromPostSwipe() {
        val preferences = mActivity.defaultSharedPreferences
        if (preferences.contains(SharedPreferencesUtils.ENABLE_COMMENT_SWIPE_ACTION)) return
        findPreference<SwitchPreference>(SharedPreferencesUtils.ENABLE_COMMENT_SWIPE_ACTION)
            ?.isChecked = preferences.getBoolean(SharedPreferencesUtils.ENABLE_SWIPE_ACTION, false)
    }

    /**
     * Writes the comment key once, so the picker opens on the action comments were already doing
     * rather than on this screen's own default.
     *
     * Not `getPreferenceManager().getSharedPreferences()`: that is null once a screen is on a
     * PreferenceDataStore, and this key is per-account.
     */
    private fun seedFromPostAction(commentKey: String, postKey: String, fallback: String) {
        val preferences = mActivity.defaultSharedPreferences
        if (preferences.contains(commentKey)) return
        findPreference<ListPreference>(commentKey)?.value =
            SwipeActionPreferences.seededLevel1(preferences, postKey, fallback)
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
