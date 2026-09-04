package ml.docilealligator.infinityforreddit.account

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import java.util.concurrent.Executor
import ml.docilealligator.infinityforreddit.RedditDataRoomDatabase
import ml.docilealligator.infinityforreddit.utils.DisableNsfwForeverMigration
import ml.docilealligator.infinityforreddit.utils.SharedPreferencesUtils

/**
 * Moves every per-account setting written before [AccountScope] onto the one key scheme.
 *
 * Five conventions had grown up independently, and three of them disagreed about how to spell the
 * anonymous account: the NSFW and tab screens used an empty prefix, post history and the link
 * handler used `"-"`, and the link handler was *read* back with neither, which is why changing it
 * while logged out never took effect. Each old spelling is looked for in turn and the first one
 * found wins, so a setting someone actually chose is preferred over the one that was silently in
 * force.
 *
 * Runs once. Afterwards the old keys are gone, which makes a second run a no-op even if the flag
 * that records it is lost.
 */
object AccountSettingsMigration {

    /**
     * How many rounds of seeding this build expects to have happened.
     *
     * Seeding is what stops a setting that has just become per-account from reading as reset, so
     * every time [AccountScopedKeys] grows this has to go up — the flag alone cannot tell "seeded"
     * from "seeded before that key was on the list". Version 1 was the original scoping; version 2
     * added the sort-type defaults; version 3 added the bottom app bar toggle.
     */
    private const val SEED_VERSION = 3

    /** Bases that live in a file, plus any pre-per-account key that held the same setting. */
    private class Scoped(val base: String, val legacyUnscoped: String? = null)

    private val NSFW_AND_SPOILER = listOf(
        Scoped(SharedPreferencesUtils.NSFW_BASE),
        Scoped(SharedPreferencesUtils.BLUR_NSFW_BASE),
        Scoped(SharedPreferencesUtils.DO_NOT_BLUR_NSFW_IN_NSFW_SUBREDDITS),
        Scoped(SharedPreferencesUtils.BLUR_SPOILER_BASE),
    )

    private val POST_HISTORY = listOf(
        Scoped(SharedPreferencesUtils.MARK_POSTS_AS_READ_BASE),
        Scoped(SharedPreferencesUtils.READ_POSTS_LIMIT_ENABLED),
        Scoped(SharedPreferencesUtils.READ_POSTS_LIMIT),
        Scoped(SharedPreferencesUtils.MARK_POSTS_AS_READ_AFTER_VOTING_BASE),
        Scoped(SharedPreferencesUtils.MARK_POSTS_AS_READ_ON_SCROLL_BASE),
        Scoped(SharedPreferencesUtils.HIDE_READ_POSTS_AUTOMATICALLY_BASE),
        Scoped(SharedPreferencesUtils.HIDE_READ_POSTS_AUTOMATICALLY_IN_SUBREDDITS_BASE),
        Scoped(SharedPreferencesUtils.HIDE_READ_POSTS_AUTOMATICALLY_IN_USERS_BASE),
        Scoped(SharedPreferencesUtils.HIDE_READ_POSTS_AUTOMATICALLY_IN_SEARCH_BASE),
    )

    private val RECENTLY_VISITED = listOf(Scoped(SharedPreferencesUtils.RECENTLY_VISITED_ENABLED_BASE))

    private val MAIN_PAGE_TABS = listOf(
        Scoped(SharedPreferencesUtils.MAIN_PAGE_TABS_ORDER),
        Scoped(SharedPreferencesUtils.MAIN_PAGE_SHOW_TAB_NAMES),
        Scoped(SharedPreferencesUtils.MAIN_PAGE_SHOW_MULTIREDDITS),
        Scoped(SharedPreferencesUtils.MAIN_PAGE_SHOW_FAVORITE_MULTIREDDITS),
        Scoped(SharedPreferencesUtils.MAIN_PAGE_SHOW_USERS_MULTIREDDITS),
        Scoped(SharedPreferencesUtils.MAIN_PAGE_SHOW_FAVORITE_USERS_MULTIREDDITS),
        Scoped(SharedPreferencesUtils.MAIN_PAGE_SHOW_SUBSCRIBED_SUBREDDITS),
        Scoped(SharedPreferencesUtils.MAIN_PAGE_SHOW_FAVORITE_SUBSCRIBED_SUBREDDITS),
    )

    /**
     * The bottom app bar's twelve keys. Its own convention was the fifth and last: one bar written
     * under the bare key and shared by every signed-in account, and a second under `"-"` for
     * anonymous — which [SearchResultActivity] then read back without the prefix, so its FAB was the
     * signed-in one even when logged out.
     */
    private val BOTTOM_APP_BAR = listOf(
        SharedPreferencesUtils.MAIN_ACTIVITY_BOTTOM_APP_BAR_OPTION_COUNT,
        SharedPreferencesUtils.MAIN_ACTIVITY_BOTTOM_APP_BAR_OPTION_1,
        SharedPreferencesUtils.MAIN_ACTIVITY_BOTTOM_APP_BAR_OPTION_2,
        SharedPreferencesUtils.MAIN_ACTIVITY_BOTTOM_APP_BAR_OPTION_3,
        SharedPreferencesUtils.MAIN_ACTIVITY_BOTTOM_APP_BAR_OPTION_4,
        SharedPreferencesUtils.MAIN_ACTIVITY_BOTTOM_APP_BAR_FAB,
        SharedPreferencesUtils.OTHER_ACTIVITIES_BOTTOM_APP_BAR_OPTION_COUNT,
        SharedPreferencesUtils.OTHER_ACTIVITIES_BOTTOM_APP_BAR_OPTION_1,
        SharedPreferencesUtils.OTHER_ACTIVITIES_BOTTOM_APP_BAR_OPTION_2,
        SharedPreferencesUtils.OTHER_ACTIVITIES_BOTTOM_APP_BAR_OPTION_3,
        SharedPreferencesUtils.OTHER_ACTIVITIES_BOTTOM_APP_BAR_OPTION_4,
        SharedPreferencesUtils.OTHER_ACTIVITIES_BOTTOM_APP_BAR_FAB,
    )

    /**
     * The link settings are the ones the old code read back from an unprefixed key when browsing
     * anonymously, so that key is what an anonymous user was actually getting.
     */
    private val DEFAULT_FILE = listOf(
        Scoped(SharedPreferencesUtils.LINK_HANDLER_BASE, SharedPreferencesUtils.LINK_HANDLER),
        Scoped(SharedPreferencesUtils.EPHEMERAL_CUSTOM_TAB_PACKAGE_BASE,
            SharedPreferencesUtils.EPHEMERAL_CUSTOM_TAB_PACKAGE),
        Scoped(SharedPreferencesUtils.SPECIFIC_BROWSER_PACKAGE_BASE,
            SharedPreferencesUtils.SPECIFIC_BROWSER_PACKAGE),
    )

    /**
     * Opens every preferences file itself rather than taking them injected: the injected ones are
     * [AccountScopedSharedPreferences], and a migration that reads through the façade would see one
     * account's filtered view and write keys that are scoped twice.
     */
    @JvmStatic
    fun migrate(
        context: Context,
        internalSharedPreferences: SharedPreferences,
        executor: Executor,
        redditDataRoomDatabase: RedditDataRoomDatabase,
    ) {
        if (internalSharedPreferences.getBoolean(SharedPreferencesUtils.ACCOUNT_SCOPE_MIGRATED, false) &&
            seedVersionOf(internalSharedPreferences) >= SEED_VERSION &&
            internalSharedPreferences.getBoolean(SharedPreferencesUtils.BOTTOM_APP_BAR_SCOPE_MIGRATED, false)
        ) {
            return
        }

        executor.execute {
            val defaultSharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
            val nsfwAndSpoilerSharedPreferences = file(context, SharedPreferencesUtils.NSFW_AND_SPOILER_SHARED_PREFERENCES_FILE)
            val postHistorySharedPreferences = file(context, SharedPreferencesUtils.POST_HISTORY_SHARED_PREFERENCES_FILE)
            val recentlyVisitedSharedPreferences = file(context, SharedPreferencesUtils.RECENTLY_VISITED_SHARED_PREFERENCES_FILE)
            val mainPageTabsSharedPreferences = file(context, SharedPreferencesUtils.MAIN_PAGE_TABS_SHARED_PREFERENCES_FILE)

            // Anonymous first: it is the namespace every old spelling disagreed about.
            val namespaces = mutableListOf<String?>(null)
            redditDataRoomDatabase.accountDao().allAccounts.mapTo(namespaces) { it.accountName }

            if (!internalSharedPreferences.getBoolean(SharedPreferencesUtils.ACCOUNT_SCOPE_MIGRATED, false)) {
                rescope(nsfwAndSpoilerSharedPreferences, NSFW_AND_SPOILER, namespaces)
                rescope(postHistorySharedPreferences, POST_HISTORY, namespaces)
                rescope(recentlyVisitedSharedPreferences, RECENTLY_VISITED, namespaces)
                rescope(mainPageTabsSharedPreferences, MAIN_PAGE_TABS, namespaces)
                rescope(defaultSharedPreferences, DEFAULT_FILE, namespaces)

                internalSharedPreferences.edit()
                    .putBoolean(SharedPreferencesUtils.ACCOUNT_SCOPE_MIGRATED, true)
                    .apply()

                // Ordered after the rescope so it writes onto keys that are already canonical.
                DisableNsfwForeverMigration.turnOffNsfwForAffectedAccounts(
                    defaultSharedPreferences, nsfwAndSpoilerSharedPreferences, redditDataRoomDatabase)
            }

            seedIfBehind(internalSharedPreferences, defaultSharedPreferences, namespaces, listOf(
                AccountScopedKeys.DEFAULT_PREFERENCES to defaultSharedPreferences,
                SharedPreferencesUtils.POST_LAYOUT_SHARED_PREFERENCES_FILE to
                    file(context, SharedPreferencesUtils.POST_LAYOUT_SHARED_PREFERENCES_FILE),
                SharedPreferencesUtils.SORT_TYPE_SHARED_PREFERENCES_FILE to
                    file(context, SharedPreferencesUtils.SORT_TYPE_SHARED_PREFERENCES_FILE),
                SharedPreferencesUtils.NAVIGATION_DRAWER_SHARED_PREFERENCES_FILE to
                    file(context, SharedPreferencesUtils.NAVIGATION_DRAWER_SHARED_PREFERENCES_FILE),
                SharedPreferencesUtils.POST_DETAILS_SHARED_PREFERENCES_FILE to
                    file(context, SharedPreferencesUtils.POST_DETAILS_SHARED_PREFERENCES_FILE),
            ))

            // Its own flag, because it was scoped a release after the rest and the two above are
            // already set on every device that has run this once.
            if (!internalSharedPreferences.getBoolean(SharedPreferencesUtils.BOTTOM_APP_BAR_SCOPE_MIGRATED, false)) {
                scopeBottomAppBar(
                    file(context, SharedPreferencesUtils.BOTTOM_APP_BAR_SHARED_PREFERENCES_FILE),
                    namespaces)

                internalSharedPreferences.edit()
                    .putBoolean(SharedPreferencesUtils.BOTTOM_APP_BAR_SCOPE_MIGRATED, true)
                    .apply()
            }
        }
    }

    /**
     * Gives every account its own bottom app bar.
     *
     * Anonymous keeps the bar it had under `"-"`; every account that exists now starts from the one
     * bar they were all sharing, so nobody's changes on the upgrade. Unlike [seed] the old keys go:
     * the whole file is per-account by construction, so there is no key here that could turn out to
     * have been misclassified, and taking them away leaves a second run nothing to do.
     */
    private fun scopeBottomAppBar(preferences: SharedPreferences, namespaces: List<String?>) {
        val existing = preferences.all
        val editor = preferences.edit()
        var changed = false

        for (base in BOTTOM_APP_BAR) {
            for (accountName in namespaces) {
                val target = AccountScope.key(accountName, base)
                if (existing.containsKey(target)) {
                    // Already canonical: leave it, and take no old key it might disagree with.
                    continue
                }
                val old = if (accountName.isNullOrEmpty() || accountName == Account.ANONYMOUS_ACCOUNT) {
                    Account.ANONYMOUS_ACCOUNT + base
                } else {
                    base
                }
                if (copy(editor, target, existing[old] ?: continue)) {
                    changed = true
                }
            }

            // After every account has taken its copy, not inside the loop above.
            if (existing.containsKey(base)) {
                editor.remove(base)
                changed = true
            }
            if (existing.containsKey(Account.ANONYMOUS_ACCOUNT + base)) {
                editor.remove(Account.ANONYMOUS_ACCOUNT + base)
                changed = true
            }
        }

        if (changed) {
            editor.apply()
        }
    }

    /**
     * Arranges for [migrate] to run again on the next start when a restore has just put back a
     * backup that predates [AccountScope]. [restoredDefaultPreferences] is the default preferences
     * file as the backup held it, or null when the restore could not read one.
     *
     * Such a backup carries every per-account setting under its old global spelling, and nothing
     * reads those once the façade is in: without this, restoring one reads as every per-account
     * setting having gone back to its default. A second migration over the restored files gives the
     * user what upgrading rather than restoring would have.
     *
     * Running again when it was not needed is harmless, which is the direction to be wrong in here.
     * Rescoping leaves a canonical key that is already there, and seeding only fills a key an
     * account does not have; a backup holding no per-account keys at all reaches the second run with
     * nothing to do.
     */
    @SuppressLint("ApplySharedPref")
    @JvmStatic
    fun rerunIfBackupPredatesAccountScope(
        internalSharedPreferences: SharedPreferences,
        restoredDefaultPreferences: Map<String, *>?,
    ) {
        if (restoredDefaultPreferences == null || !predatesAccountScope(restoredDefaultPreferences)) {
            return
        }

        // commit(), not apply(): a restore ends by killing the process.
        internalSharedPreferences.edit()
            .putBoolean(SharedPreferencesUtils.ACCOUNT_SCOPE_MIGRATED, false)
            .putBoolean(SharedPreferencesUtils.ACCOUNT_SCOPE_SEEDED, false)
            .putInt(SharedPreferencesUtils.ACCOUNT_SCOPE_SEED_VERSION, 0)
            .commit()
    }

    /** Whether the file holds not one key [AccountScope] built, which is what an older backup is. */
    private fun predatesAccountScope(defaultPreferences: Map<String, *>): Boolean =
        defaultPreferences.keys.none { key ->
            val base = AccountScope.baseOf(key)
            base != null && AccountScopedKeys.isScoped(AccountScopedKeys.DEFAULT_PREFERENCES, base)
        }

    private fun file(context: Context, name: String): SharedPreferences =
        context.getSharedPreferences(name, Context.MODE_PRIVATE)

    /**
     * Gives every existing account its own copy of the settings that have just become per-account,
     * so nobody's configuration appears to reset on the upgrade. Accounts added afterwards start at
     * the code defaults instead, which is why this runs once and only for accounts that already
     * exist.
     *
     * The original global value is left where it is. Nothing reads it once the façade is in place,
     * and keeping it costs a few bytes against the risk of deleting a key that turns out to be
     * misclassified.
     */
    /**
     * Seeds when this device is behind [SEED_VERSION], and records that it has caught up.
     *
     * Re-running is safe by construction: [seed] fills only a key an account does not already have,
     * so a later round writes nothing but the keys the list has since gained.
     */
    private fun seedIfBehind(
        internalSharedPreferences: SharedPreferences,
        defaultSharedPreferences: SharedPreferences,
        namespaces: List<String?>,
        files: List<Pair<String, SharedPreferences>>,
    ) {
        if (seedVersionOf(internalSharedPreferences) >= SEED_VERSION) {
            return
        }

        seed(namespaces, files)
        splitSaveSortType(defaultSharedPreferences, namespaces)

        internalSharedPreferences.edit()
            .putInt(SharedPreferencesUtils.ACCOUNT_SCOPE_SEED_VERSION, SEED_VERSION)
            // Still written, so a build without the version reads this device as seeded.
            .putBoolean(SharedPreferencesUtils.ACCOUNT_SCOPE_SEEDED, true)
            .apply()
    }

    /** Rounds of seeding already done. A device seeded before the version existed did round 1. */
    private fun seedVersionOf(internalSharedPreferences: SharedPreferences): Int = when {
        internalSharedPreferences.contains(SharedPreferencesUtils.ACCOUNT_SCOPE_SEED_VERSION) ->
            internalSharedPreferences.getInt(SharedPreferencesUtils.ACCOUNT_SCOPE_SEED_VERSION, 0)
        internalSharedPreferences.getBoolean(SharedPreferencesUtils.ACCOUNT_SCOPE_SEEDED, false) -> 1
        else -> 0
    }

    /**
     * The combined "Save Sort Type" toggle, split into a post-feed and a comment one.
     *
     * Both halves are per-account now, so every account takes the old combined choice. It runs from
     * here, after [seed], rather than from the application: on the main thread it raced the seeding
     * on the executor, and whichever landed first decided whether an account kept the choice it had
     * or the older one underneath it.
     */
    private fun splitSaveSortType(
        defaultSharedPreferences: SharedPreferences,
        namespaces: List<String?>,
    ) {
        if (!defaultSharedPreferences.contains(SharedPreferencesUtils.SAVE_SORT_TYPE)) {
            return
        }

        val saveSortType =
            defaultSharedPreferences.getBoolean(SharedPreferencesUtils.SAVE_SORT_TYPE, true)
        val editor = defaultSharedPreferences.edit()
        var changed = false

        for (accountName in namespaces) {
            for (base in listOf(SharedPreferencesUtils.SAVE_POST_SORT,
                SharedPreferencesUtils.SAVE_COMMENT_SORT)) {
                val target = AccountScope.key(accountName, base)
                if (!defaultSharedPreferences.contains(target)) {
                    editor.putBoolean(target, saveSortType)
                    changed = true
                }
            }
        }

        if (changed) {
            editor.apply()
        }
    }

    private fun seed(
        namespaces: List<String?>,
        files: List<Pair<String, SharedPreferences>>,
    ) {
        for ((fileName, preferences) in files) {
            val existing = preferences.all
            // Only keys that are not already namespaced: seeding a scoped key would scope it twice.
            val unscoped = existing.keys.filter { AccountScope.baseOf(it) == null }.toSet()
            val toSeed = AccountScopedKeys.scopedKeysIn(fileName, unscoped)
            if (toSeed.isEmpty()) {
                continue
            }

            val editor = preferences.edit()
            var changed = false
            for (accountName in namespaces) {
                for (key in toSeed) {
                    val target = AccountScope.key(accountName, key)
                    // Asked of the file rather than of the snapshot above: this runs on a background
                    // executor while the first activity is coming up, so a value written in between
                    // would otherwise be overwritten by the seeded global one.
                    if (preferences.contains(target)) {
                        continue
                    }
                    if (copy(editor, target, existing[key])) {
                        changed = true
                    }
                }
            }
            if (changed) {
                editor.apply()
            }
        }
    }

    private fun rescope(
        preferences: SharedPreferences,
        scoped: List<Scoped>,
        namespaces: List<String?>,
    ) {
        val existing = preferences.all
        val editor = preferences.edit()
        var changed = false

        for (accountName in namespaces) {
            for (entry in scoped) {
                val target = AccountScope.key(accountName, entry.base)
                if (existing.containsKey(target)) {
                    // Already canonical. Leave it, and take no old key it might disagree with.
                    continue
                }

                val candidates = oldKeysFor(accountName, entry)
                val found = candidates.firstOrNull { existing.containsKey(it) } ?: continue
                if (!copy(editor, target, existing[found])) {
                    continue
                }
                candidates.forEach(editor::remove)
                changed = true
            }
        }

        if (changed) {
            editor.apply()
        }
    }

    /** Every spelling this setting could have been stored under, best first. */
    private fun oldKeysFor(accountName: String?, entry: Scoped): List<String> {
        if (!accountName.isNullOrEmpty() && accountName != Account.ANONYMOUS_ACCOUNT) {
            return listOf(accountName + entry.base)
        }
        // What the user chose outranks what was silently in force underneath it.
        return listOfNotNull(
            Account.ANONYMOUS_ACCOUNT + entry.base,
            entry.base,
            entry.legacyUnscoped,
        )
    }

    private fun copy(editor: SharedPreferences.Editor, target: String, value: Any?): Boolean {
        when (value) {
            is Boolean -> editor.putBoolean(target, value)
            is Int -> editor.putInt(target, value)
            is Long -> editor.putLong(target, value)
            is Float -> editor.putFloat(target, value)
            is String -> editor.putString(target, value)
            else -> return false
        }
        return true
    }
}
