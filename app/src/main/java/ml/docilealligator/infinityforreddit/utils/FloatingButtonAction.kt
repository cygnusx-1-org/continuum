package ml.docilealligator.infinityforreddit.utils

import android.content.SharedPreferences

/**
 * What the floating button does, read from the setting on the Interface screen.
 *
 * The choice used to be made on the bottom app bar's Customize screen and kept in that bar's own
 * file, so a choice made there is still honoured until one is made on the Interface screen: the
 * new key is looked at first and the old file only when it has none.
 */
object FloatingButtonAction {

    /** One of the `MAIN_ACTIVITY_BOTTOM_APP_BAR_FAB_*` values. */
    @JvmStatic
    fun mainActivity(current: SharedPreferences, legacy: SharedPreferences): Int =
        read(current, legacy, SharedPreferencesUtils.MAIN_ACTIVITY_FAB_ACTION, SharedPreferencesUtils.MAIN_ACTIVITY_BOTTOM_APP_BAR_FAB)

    /** One of the `OTHER_ACTIVITIES_BOTTOM_APP_BAR_FAB_*` values. */
    @JvmStatic
    fun otherActivities(current: SharedPreferences, legacy: SharedPreferences): Int =
        read(current, legacy, SharedPreferencesUtils.OTHER_ACTIVITIES_FAB_ACTION, SharedPreferencesUtils.OTHER_ACTIVITIES_BOTTOM_APP_BAR_FAB)

    private fun read(current: SharedPreferences, legacy: SharedPreferences, key: String, legacyKey: String): Int =
        current.getString(key, null)?.toIntOrNull()
            ?: legacy.getInt(legacyKey, SUBMIT_POSTS)

    /** Submit post: the first choice of both lists, and what the button did before it could be changed. */
    private const val SUBMIT_POSTS = 0
}
