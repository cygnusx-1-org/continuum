package ml.docilealligator.infinityforreddit.managers

import android.content.SharedPreferences
import ml.docilealligator.infinityforreddit.utils.SharedPreferencesUtils

/**
 * Decides whether an inline (feed or post detail) video starts silent. Three settings say so, and
 * they rank:
 *
 * 1. **Mute Videos**, and **Mute NSFW Videos** on an NSFW post, always win ([forcesMute]). Nothing
 *    the reader remembers lifts them; they can still unmute that one video with its own button.
 * 2. **Remember Mute**, when on, then lets the reader's last tap on any mute button decide.
 * 3. Otherwise **Mute Autoplaying Videos** is the starting state.
 *
 * Holds the preferences rather than values read out of them once: these settings are per-account, so
 * a value cached at construction would keep the previous account's choice for the life of the
 * process.
 */
class VideoMuteManager(private val sharedPreferences: SharedPreferences) {

    /** The "Mute Autoplaying Videos" setting: where an unremembered video starts. */
    private val mutesAutoplayByDefault: Boolean
        get() = sharedPreferences.getBoolean(SharedPreferencesUtils.MUTE_AUTOPLAYING_VIDEOS, true)

    /**
     * The reader's last mute choice. Kept under a key of its own: it used to be written over the
     * "Mute Autoplaying Videos" setting, so a tap on a mute button silently flipped a switch in
     * Settings > Video. Until there has been a tap it is that setting.
     */
    var isMuted: Boolean
        get() = sharedPreferences.getBoolean(
            SharedPreferencesUtils.REMEMBERED_MUTE_STATE, mutesAutoplayByDefault)
        set(value) {
            // The post detail's mute button calls this on every tap, unlike the feed, which asks
            // first. Nothing is lost by skipping it with the option off: every read of isMuted is
            // already behind the same condition.
            if (!rememberMuteOption) {
                return
            }
            sharedPreferences.edit()
                .putBoolean(SharedPreferencesUtils.REMEMBERED_MUTE_STATE, value)
                .apply()
        }

    var rememberMuteOption: Boolean
        get() = sharedPreferences.getBoolean(
            SharedPreferencesUtils.REMEMBER_MUTING_OPTION_IN_POST_FEED, false)
        set(value) {
            // Turning it on starts from the setting, not from whatever was tapped long ago.
            sharedPreferences.edit()
                .putBoolean(SharedPreferencesUtils.REMEMBER_MUTING_OPTION_IN_POST_FEED, value)
                .remove(SharedPreferencesUtils.REMEMBERED_MUTE_STATE)
                .apply()
        }

    /** Drops the remembered choice, so [isMuted] follows "Mute Autoplaying Videos" again. */
    fun forgetRememberedMute() {
        sharedPreferences.edit().remove(SharedPreferencesUtils.REMEMBERED_MUTE_STATE).apply()
    }

    fun getMasterMutingOption(): Boolean? = if (rememberMuteOption) isMuted else null

    fun forcesMute(isNsfw: Boolean): Boolean =
        sharedPreferences.getBoolean(SharedPreferencesUtils.MUTE_VIDEO, false) ||
            (isNsfw && sharedPreferences.getBoolean(SharedPreferencesUtils.MUTE_NSFW_VIDEO, false))

    fun startsMuted(isNsfw: Boolean): Boolean =
        forcesMute(isNsfw) || (getMasterMutingOption() ?: mutesAutoplayByDefault)
}
