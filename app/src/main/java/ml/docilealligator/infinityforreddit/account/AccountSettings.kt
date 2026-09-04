package ml.docilealligator.infinityforreddit.account

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import ml.docilealligator.infinityforreddit.RedditDataRoomDatabase
import ml.docilealligator.infinityforreddit.utils.SharedPreferencesUtils

/**
 * One account's stored settings, moved or removed wholesale.
 *
 * Backs the two things a static classification makes possible: copying another account's settings
 * onto this one, and putting this one back where a newly added account starts. Both are exact
 * because [AccountScopedKeys] already answers, for every key, whether it belongs to an account.
 *
 * Like [AccountSettingsMigration] this opens the preference files itself rather than taking them
 * injected: the injected ones are [AccountScopedSharedPreferences], which show only the current
 * account's view of a file, and both operations here have to reach a second account's keys.
 *
 * Both write with `commit()`. The caller restarts the app immediately afterwards, and `apply()`
 * only promises the write eventually reaches disk — the process is gone before then.
 */
object AccountSettings {

    /**
     * A preferences file that per-account keys can be in, and which of its keys those are.
     *
     * Every namespaced key is per-account in all of them but the default file, which mixes both and
     * so carries a base list.
     */
    private class ScopedFile(
        val name: String,
        /** False for a file a copy leaves alone; see [copyBetweenAccounts]. */
        val copied: Boolean = true,
        val holdsBase: (String) -> Boolean = { true },
    )

    /**
     * The default file's per-account bases: the ones [AccountScopedKeys] routes, plus the link
     * handler and browser pickers, which build their own [AccountScope] keys. Those three are
     * deliberately absent from [AccountScopedKeys] — listing them there would scope them twice —
     * so this is the one place that has to name them.
     */
    private val DEFAULT_FILE_BASES: Set<String> =
        AccountScopedKeys.scopedDefaultKeys() + setOf(
            SharedPreferencesUtils.LINK_HANDLER_BASE,
            SharedPreferencesUtils.EPHEMERAL_CUSTOM_TAB_PACKAGE_BASE,
            SharedPreferencesUtils.SPECIFIC_BROWSER_PACKAGE_BASE,
        )

    /**
     * Every file a per-account key can be in: the five the façade routes, the four that scope their
     * own keys, and the default file.
     */
    private val SCOPED_FILES: List<ScopedFile> = listOf(
        ScopedFile(AccountScopedKeys.DEFAULT_PREFERENCES) { it in DEFAULT_FILE_BASES },
        ScopedFile(SharedPreferencesUtils.POST_LAYOUT_SHARED_PREFERENCES_FILE),
        ScopedFile(SharedPreferencesUtils.BOTTOM_APP_BAR_SHARED_PREFERENCES_FILE),
        ScopedFile(SharedPreferencesUtils.SORT_TYPE_SHARED_PREFERENCES_FILE),
        ScopedFile(SharedPreferencesUtils.NAVIGATION_DRAWER_SHARED_PREFERENCES_FILE),
        ScopedFile(SharedPreferencesUtils.POST_DETAILS_SHARED_PREFERENCES_FILE),
        ScopedFile(SharedPreferencesUtils.NSFW_AND_SPOILER_SHARED_PREFERENCES_FILE),
        ScopedFile(SharedPreferencesUtils.POST_HISTORY_SHARED_PREFERENCES_FILE),
        ScopedFile(SharedPreferencesUtils.RECENTLY_VISITED_SHARED_PREFERENCES_FILE),
        // Tabs name subreddits and multireddits the destination account may not have, and a tab
        // pointing at something it cannot load is worse than the tabs it already had. A reset does
        // clear them: there the account is going back to defaults, which always load.
        ScopedFile(SharedPreferencesUtils.MAIN_PAGE_TABS_SHARED_PREFERENCES_FILE, copied = false),
    )

    /**
     * Gives [destinationAccountName] the settings [sourceAccountName] has, main page tabs aside.
     *
     * A mirror, not a merge: a per-account key the destination has and the source does not is
     * removed, so the two accounts end up agreeing rather than the destination keeping a setting
     * the source never had. Runs one way — the source file entries are only ever read.
     *
     * Anonymous can be a destination but is never offered as a source, which is a rule of the
     * picker rather than of this code; passing it as one would work.
     *
     * Blocking. Call it off the main thread.
     */
    @SuppressLint("ApplySharedPref")
    @JvmStatic
    fun copyBetweenAccounts(
        context: Context,
        redditDataRoomDatabase: RedditDataRoomDatabase,
        sourceAccountName: String,
        destinationAccountName: String?,
    ) {
        if (AccountScope.namespace(sourceAccountName) ==
            AccountScope.namespace(destinationAccountName)
        ) {
            return
        }

        for (file in SCOPED_FILES) {
            if (file.copied) {
                mirror(open(context, file.name), file, sourceAccountName, destinationAccountName)
            }
        }

        copyFilters(redditDataRoomDatabase, sourceAccountName,
            destinationAccountName ?: Account.ANONYMOUS_ACCOUNT)
    }

    /**
     * The post and comment filters, which are rows rather than keys but are per-account settings all
     * the same. A mirror like the files: the destination's own filters go first.
     *
     * The blocked-subreddit rows are deliberately left behind. They are what a rule has been
     * observed hiding while *that* account browsed, not something the user wrote, and the
     * destination's own rows go with its old filters through the foreign key.
     */
    private fun copyFilters(
        redditDataRoomDatabase: RedditDataRoomDatabase,
        source: String,
        destination: String,
    ) {
        redditDataRoomDatabase.runInTransaction {
            redditDataRoomDatabase.postFilterDao().deleteAllPostFilters(destination)
            redditDataRoomDatabase.commentFilterDao().deleteAllCommentFilters(destination)

            val postFilters = redditDataRoomDatabase.postFilterDao().getAllPostFilters(source)
            postFilters.forEach { it.username = destination }
            redditDataRoomDatabase.postFilterDao().insertAll(postFilters)

            val postUsages = redditDataRoomDatabase.postFilterUsageDao()
                .getAllPostFilterUsageForAccount(source)
            postUsages.forEach { it.username = destination }
            redditDataRoomDatabase.postFilterUsageDao().insertAll(postUsages)

            val commentFilters = redditDataRoomDatabase.commentFilterDao().getAllCommentFilters(source)
            commentFilters.forEach { it.username = destination }
            redditDataRoomDatabase.commentFilterDao().insertAll(commentFilters)

            val commentUsages = redditDataRoomDatabase.commentFilterUsageDao()
                .getAllCommentFilterUsageForAccount(source)
            commentUsages.forEach { it.username = destination }
            redditDataRoomDatabase.commentFilterUsageDao().insertAll(commentUsages)
        }
    }

    /**
     * Removes every setting [accountName] owns, main page tabs included, which leaves it reading the
     * code defaults — the state an account added after the migration starts in.
     *
     * Blocking. Call it off the main thread.
     */
    @SuppressLint("ApplySharedPref")
    @JvmStatic
    fun reset(
        context: Context,
        redditDataRoomDatabase: RedditDataRoomDatabase,
        accountName: String?,
    ) {
        // The usages and the blocked-subreddit rows cascade off the filters.
        val account = accountName ?: Account.ANONYMOUS_ACCOUNT
        redditDataRoomDatabase.runInTransaction {
            redditDataRoomDatabase.postFilterDao().deleteAllPostFilters(account)
            redditDataRoomDatabase.commentFilterDao().deleteAllCommentFilters(account)
        }

        val namespace = AccountScope.namespace(accountName)

        for (file in SCOPED_FILES) {
            val preferences = open(context, file.name)
            val editor = preferences.edit()
            var changed = false

            for (key in preferences.all.keys) {
                if (belongsTo(namespace, key, file)) {
                    editor.remove(key)
                    changed = true
                }
            }

            if (changed) {
                editor.commit()
            }
        }
    }

    @SuppressLint("ApplySharedPref")
    private fun mirror(
        preferences: SharedPreferences,
        file: ScopedFile,
        sourceAccountName: String,
        destinationAccountName: String?,
    ) {
        val source = AccountScope.namespace(sourceAccountName)
        val destination = AccountScope.namespace(destinationAccountName)
        val stored = preferences.all
        val editor = preferences.edit()
        var changed = false

        for (key in stored.keys) {
            if (belongsTo(destination, key, file)) {
                editor.remove(key)
                changed = true
            }
        }

        for ((key, value) in stored) {
            if (!belongsTo(source, key, file)) {
                continue
            }
            val base = AccountScope.baseOf(key) ?: continue
            if (put(editor, AccountScope.key(destinationAccountName, base), value)) {
                changed = true
            }
        }

        if (changed) {
            editor.commit()
        }
    }

    /** Whether [key] is one of [namespace]'s, and one this file counts as belonging to an account. */
    private fun belongsTo(namespace: String, key: String, file: ScopedFile): Boolean {
        if (AccountScope.namespaceOf(key) != namespace) {
            return false
        }
        val base = AccountScope.baseOf(key) ?: return false
        return file.holdsBase(base)
    }

    /**
     * Opens a file by name, except the default one: [AccountScopedKeys.DEFAULT_PREFERENCES] is an
     * identifier, and the file `PreferenceManager` uses is not the one
     * [SharedPreferencesUtils.DEFAULT_PREFERENCES_FILE] names.
     */
    private fun open(context: Context, fileName: String): SharedPreferences =
        if (fileName == AccountScopedKeys.DEFAULT_PREFERENCES) {
            PreferenceManager.getDefaultSharedPreferences(context)
        } else {
            context.getSharedPreferences(fileName, Context.MODE_PRIVATE)
        }

    private fun put(editor: SharedPreferences.Editor, target: String, value: Any?): Boolean {
        when (value) {
            is Boolean -> editor.putBoolean(target, value)
            is Int -> editor.putInt(target, value)
            is Long -> editor.putLong(target, value)
            is Float -> editor.putFloat(target, value)
            is String -> editor.putString(target, value)
            // Nothing stores one today, but a dropped value would look like a setting that refused
            // to copy rather than like a gap here.
            is Set<*> -> editor.putStringSet(target, value.filterIsInstance<String>().toMutableSet())
            else -> return false
        }
        return true
    }
}
