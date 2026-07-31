package ml.docilealligator.infinityforreddit.customtheme

import android.content.Context
import android.content.SharedPreferences
import java.util.concurrent.Executor
import ml.docilealligator.infinityforreddit.RedditDataRoomDatabase
import ml.docilealligator.infinityforreddit.utils.CustomThemeSharedPreferencesUtils
import ml.docilealligator.infinityforreddit.utils.SharedPreferencesUtils

/**
 * Continuum ships with Solarized Amoled as its default theme, applied on the first launch of an
 * install that has nothing to preserve. It is applied by writing the theme out rather than by
 * changing the fallbacks in [CustomThemeWrapper], so that upgrading users keep whatever they are
 * already looking at.
 */
object DefaultTheme {
    @JvmStatic
    fun applyOnFirstLaunch(
        context: Context,
        defaultSharedPreferences: SharedPreferences,
        amoledThemeSharedPreferences: SharedPreferences,
        internalSharedPreferences: SharedPreferences,
        executor: Executor,
        redditDataRoomDatabase: RedditDataRoomDatabase,
    ) {
        if (internalSharedPreferences.getBoolean(SharedPreferencesUtils.DEFAULT_THEME_APPLIED, false)) {
            return
        }

        // Left behind by a run that wrote the theme but was killed before recording the theme row.
        val interrupted = internalSharedPreferences.getBoolean(
            SharedPreferencesUtils.DEFAULT_THEME_IN_PROGRESS, false)
        val unusedInstall = isUnusedInstall(context, defaultSharedPreferences, amoledThemeSharedPreferences)
        if (!unusedInstall && !interrupted) {
            // An install with something of its own to keep. Leave it be, and stop looking.
            markApplied(internalSharedPreferences)
            return
        }

        val solarizedAmoled = CustomThemeWrapper.getSolarizedAmoled(context)
        if (unusedInstall) {
            CustomThemeSharedPreferencesUtils.insertThemeToSharedPreferences(solarizedAmoled, amoledThemeSharedPreferences)
            defaultSharedPreferences.edit()
                .putString(SharedPreferencesUtils.THEME_KEY, SharedPreferencesUtils.THEME_DARK)
                .putBoolean(SharedPreferencesUtils.AMOLED_DARK_KEY, true)
                .apply()
            internalSharedPreferences.edit()
                .putBoolean(SharedPreferencesUtils.DEFAULT_THEME_IN_PROGRESS, true)
                .apply()
        }

        // The theme row backs the Customize Theme and Manage Themes screens, which is also how
        // applying a predefined theme by hand records it. It is written last, and the theme counts
        // as applied only once it is in place, so that a process death in between is repaired on
        // the next launch rather than leaving the theme half applied for good.
        executor.execute {
            val customThemeDao = redditDataRoomDatabase.customThemeDao()
            if (customThemeDao.getAmoledCustomTheme() == null) {
                customThemeDao.insert(solarizedAmoled)
            }
            markApplied(internalSharedPreferences)
        }
    }

    private fun markApplied(internalSharedPreferences: SharedPreferences) {
        internalSharedPreferences.edit()
            .putBoolean(SharedPreferencesUtils.DEFAULT_THEME_APPLIED, true)
            .remove(SharedPreferencesUtils.DEFAULT_THEME_IN_PROGRESS)
            .apply()
    }

    /**
     * An install carrying no data of its own: a first launch, or one after the user cleared storage,
     * both of which should land on the default theme. Anyone who has used an earlier version has
     * settings or a database to keep, and is left alone. The database is checked by file rather than
     * by query so that this stays synchronous — the theme has to be settled before the first
     * activity reads it.
     */
    private fun isUnusedInstall(
        context: Context,
        defaultSharedPreferences: SharedPreferences,
        amoledThemeSharedPreferences: SharedPreferences,
    ): Boolean {
        return defaultSharedPreferences.all.isEmpty() &&
                amoledThemeSharedPreferences.all.isEmpty() &&
                !context.getDatabasePath(RedditDataRoomDatabase.DATABASE_NAME).exists()
    }
}
