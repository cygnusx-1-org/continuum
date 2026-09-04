package ml.docilealligator.infinityforreddit.managers

import android.content.SharedPreferences
import ml.docilealligator.infinityforreddit.utils.SharedPreferencesUtils

/**
 * Holds the preferences rather than values read out of them once: these settings are per-account, so
 * a value cached at construction would keep the previous account's choice for the life of the
 * process.
 *
 * [isMuted] is also written at runtime by the player's mute button, which is why it is stored back --
 * but only when [rememberMuteOption] says the choice is meant to outlive the video.
 */
class VideoMuteManager(private val sharedPreferences: SharedPreferences) {

    var isMuted: Boolean
        get() = sharedPreferences.getBoolean(SharedPreferencesUtils.MUTE_AUTOPLAYING_VIDEOS, true)
        set(value) {
            // The post detail's mute button calls this on every tap, unlike the feed, which asks
            // first. Writing regardless would let unmuting one video clear Settings > Video >
            // "Mute autoplaying videos" for good. Nothing is lost by skipping it: every read of
            // isMuted is already behind the same condition, so with the option off the stored
            // value is never consulted anyway.
            if (!rememberMuteOption) {
                return
            }
            sharedPreferences.edit()
                .putBoolean(SharedPreferencesUtils.MUTE_AUTOPLAYING_VIDEOS, value)
                .apply()
        }

    var rememberMuteOption: Boolean
        get() = sharedPreferences.getBoolean(
            SharedPreferencesUtils.REMEMBER_MUTING_OPTION_IN_POST_FEED, false)
        set(value) {
            sharedPreferences.edit()
                .putBoolean(SharedPreferencesUtils.REMEMBER_MUTING_OPTION_IN_POST_FEED, value)
                .apply()
        }

    fun getMasterMutingOption(): Boolean? = if (rememberMuteOption) isMuted else null
}
