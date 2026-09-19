package ml.docilealligator.infinityforreddit.settings

import android.os.Bundle
import androidx.preference.ListPreference
import androidx.preference.SwitchPreference
import ml.docilealligator.infinityforreddit.R
import ml.docilealligator.infinityforreddit.customviews.preference.CustomFontPreferenceFragmentCompat
import ml.docilealligator.infinityforreddit.events.ChangeEnableSwipeActionSwitchEvent
import ml.docilealligator.infinityforreddit.events.ChangeSwipeActionLevelsEvent
import ml.docilealligator.infinityforreddit.utils.SharedPreferencesUtils
import ml.docilealligator.infinityforreddit.utils.SwipeActionPreferences
import org.greenrobot.eventbus.EventBus

/**
 * What a swipe does to a post: up to four actions on each side of the row, reached by swiping
 * further. The sides are the ones the actions show on -- the left side is what a swipe to the
 * right uncovers -- which is why the headings say "Left Side" rather than "Swipe Left".
 *
 * The pickers hang off the switch above them, so a screen that does nothing looks like it does
 * nothing. Every level past the first starts empty, which is what makes a plain one-step swipe
 * the default without a mode to turn on first.
 */
class PostSwipeActionPreferenceFragment : CustomFontPreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.post_swipe_action_preferences, rootKey)

        findPreference<SwitchPreference>(SharedPreferencesUtils.ENABLE_SWIPE_ACTION)
            ?.setOnPreferenceChangeListener { _, newValue ->
                if (newValue == true) {
                    // Not getPreferenceManager().getSharedPreferences(): that is null once a
                    // screen is on a PreferenceDataStore, and this key is per-account.
                    SwipeActionPreferences.turnOffSwipeBetweenPosts(mActivity.defaultSharedPreferences)
                }
                EventBus.getDefault().post(ChangeEnableSwipeActionSwitchEvent(newValue as Boolean))
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
            SharedPreferencesUtils.SWIPE_LEFT_ACTION,
            SharedPreferencesUtils.SWIPE_LEFT_ACTION_LEVEL_2,
            SharedPreferencesUtils.SWIPE_LEFT_ACTION_LEVEL_3,
            SharedPreferencesUtils.SWIPE_LEFT_ACTION_LEVEL_4,
            SharedPreferencesUtils.SWIPE_RIGHT_ACTION,
            SharedPreferencesUtils.SWIPE_RIGHT_ACTION_LEVEL_2,
            SharedPreferencesUtils.SWIPE_RIGHT_ACTION_LEVEL_3,
            SharedPreferencesUtils.SWIPE_RIGHT_ACTION_LEVEL_4,
        )
    }
}
