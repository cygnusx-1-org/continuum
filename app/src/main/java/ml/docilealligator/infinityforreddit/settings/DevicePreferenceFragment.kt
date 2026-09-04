package ml.docilealligator.infinityforreddit.settings

import android.os.Bundle
import android.widget.Toast
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import ml.docilealligator.infinityforreddit.R
import ml.docilealligator.infinityforreddit.customviews.preference.CustomFontPreferenceFragmentCompat
import ml.docilealligator.infinityforreddit.events.ChangePostFeedMaxResolutionEvent
import ml.docilealligator.infinityforreddit.utils.SharedPreferencesUtils
import org.greenrobot.eventbus.EventBus

/**
 * Settings that describe the device rather than the person using it, so they stay the same whichever
 * account is signed in: whether this hardware folds, and how large an image the feed will decode.
 */
class DevicePreferenceFragment : CustomFontPreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.device_preferences, rootKey)

        val postFeedMaxResolution =
            findPreference<EditTextPreference>(SharedPreferencesUtils.POST_FEED_MAX_RESOLUTION)

        postFeedMaxResolution?.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { _, newValue ->
                val resolution = (newValue as? String)?.toIntOrNull()
                if (resolution == null || resolution <= 0) {
                    Toast.makeText(mActivity, R.string.not_a_valid_number, Toast.LENGTH_SHORT).show()
                    false
                } else {
                    EventBus.getDefault().post(ChangePostFeedMaxResolutionEvent(resolution))
                    true
                }
            }
    }
}
