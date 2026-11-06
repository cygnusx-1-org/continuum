package ml.docilealligator.infinityforreddit.adapters.navigationdrawer

import android.content.SharedPreferences
import ml.docilealligator.infinityforreddit.utils.SharedPreferencesUtils

/** How a navigation drawer section is drawn. */
enum class DrawerSectionState(val value: String) {
    SHOWN("shown"),
    COLLAPSED("collapsed"),
    HIDDEN("hidden");

    companion object {
        @JvmStatic
        fun of(value: String?): DrawerSectionState = values().firstOrNull { it.value == value } ?: SHOWN
    }
}

/**
 * The navigation drawer sections that can be hidden as well as collapsed, each kept as one choice.
 *
 * Older versions kept two switches per section, a collapse and a hide, with hide winning. A section
 * with no choice of its own yet takes its state from those once, writes it as a choice, and drops
 * the switches, so reading is the only step a caller has to know about.
 */
enum class DrawerSection(
    val key: String,
    private val legacyCollapseKey: String,
    private val legacyHideKey: String,
) {
    RECENTS(
        SharedPreferencesUtils.RECENT_SUBREDDITS_SECTION,
        SharedPreferencesUtils.COLLAPSE_RECENT_SUBREDDITS_SECTION,
        SharedPreferencesUtils.HIDE_RECENT_SUBREDDITS_SECTION,
    ),
    FAVORITES(
        SharedPreferencesUtils.FAVORITE_SUBREDDITS_SECTION,
        SharedPreferencesUtils.COLLAPSE_FAVORITE_SUBREDDITS_SECTION,
        SharedPreferencesUtils.HIDE_FAVORITE_SUBREDDITS_SECTION,
    ),
    SUBSCRIBED(
        SharedPreferencesUtils.SUBSCRIBED_SUBREDDITS_SECTION,
        SharedPreferencesUtils.COLLAPSE_SUBSCRIBED_SUBREDDITS_SECTION,
        SharedPreferencesUtils.HIDE_SUBSCRIBED_SUBREDDITS_SECTIONS,
    );

    fun read(preferences: SharedPreferences): DrawerSectionState {
        if (preferences.contains(key)) {
            return DrawerSectionState.of(preferences.getString(key, null))
        }
        val state = when {
            preferences.getBoolean(legacyHideKey, false) -> DrawerSectionState.HIDDEN
            preferences.getBoolean(legacyCollapseKey, false) -> DrawerSectionState.COLLAPSED
            else -> DrawerSectionState.SHOWN
        }
        if (preferences.contains(legacyHideKey) || preferences.contains(legacyCollapseKey)) {
            preferences.edit()
                .putString(key, state.value)
                .remove(legacyHideKey)
                .remove(legacyCollapseKey)
                .apply()
        }
        return state
    }

    fun write(preferences: SharedPreferences, state: DrawerSectionState) {
        preferences.edit().putString(key, state.value).apply()
    }
}
