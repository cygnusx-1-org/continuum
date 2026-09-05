package ml.docilealligator.infinityforreddit.account

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.VisibleForTesting
import ml.docilealligator.infinityforreddit.utils.SharedPreferencesUtils

/**
 * The preferences half of renaming the anonymous account from `"-"` to `".anonymous"`.
 *
 * The rows are handled by `RedditDataRoomDatabase.MIGRATION_41_42`; two things outside the database
 * also stored the old spelling and are corrected here.
 *
 * Both steps are idempotent and cost two preference lookups on a launch that has nothing to do, so
 * there is no "already done" flag to keep in step with them.
 *
 * Must run before anything reads the current account: every settings read resolves through the
 * account name, so a background fix-up could lose a race with the first activity and route a whole
 * screen to the wrong namespace.
 */
object AnonymousAccountRename {

    /** How the anonymous account was spelled before the rename. */
    private const val LEGACY_ANONYMOUS_ACCOUNT = "-"

    /**
     * Both steps, on the calling thread.
     *
     * Neither is deferred. The account name cannot be, because every settings read resolves through
     * it and a background fix-up would lose the race with the first activity. Home's sort key is
     * cheap enough to follow it rather than be raced too: handing it to a background thread meant an
     * anonymous user who reached Home first saw the default sort for that launch. Between them this
     * is two lookups in files the launch opens anyway, and a write only on the one launch that
     * finds anything to move.
     */
    @JvmStatic
    fun migrate(context: Context) {
        renameCurrentAccount(context.getSharedPreferences(
            SharedPreferencesUtils.CURRENT_ACCOUNT_SHARED_PREFERENCES_FILE, Context.MODE_PRIVATE))
        moveAnonymousHomeSortKeys(context)
    }

    /**
     * Rewrites the stored current-account name, which is read with
     * [Account.ANONYMOUS_ACCOUNT] as its default and so would otherwise come back as a literal
     * `"-"` — an account name matching no row, with no access token, that the app would treat as
     * signed in.
     *
     * Cheap, synchronous and on the main thread by design; see the class comment.
     */
    @SuppressLint("ApplySharedPref")
    @VisibleForTesting
    @JvmStatic
    fun renameCurrentAccount(currentAccountSharedPreferences: SharedPreferences) {
        val stored = currentAccountSharedPreferences.getString(
            SharedPreferencesUtils.ACCOUNT_NAME, Account.ANONYMOUS_ACCOUNT)
        if (LEGACY_ANONYMOUS_ACCOUNT == stored) {
            currentAccountSharedPreferences.edit()
                .putString(SharedPreferencesUtils.ACCOUNT_NAME, Account.ANONYMOUS_ACCOUNT)
                .commit()
        }
    }

    /**
     * Moves Home's remembered sort for anonymous browsing onto a key of its own.
     *
     * It was stored as `sort_type_subreddit_post_` + the account name — anonymous encoded as a
     * subreddit named `"-"`. Once the account name contains a `.`, [AccountScope.baseOf] splits the
     * key at that one instead, so the key stops being recognised as the anonymous account's: it
     * would read as the default and never be cleared by a reset.
     *
     * Opens the file directly rather than through the injected instance, which would resolve these
     * keys into the current account's namespace a second time.
     */
    @VisibleForTesting
    @JvmStatic
    fun moveAnonymousHomeSortKeys(context: Context) {
        val preferences = context.getSharedPreferences(
            SharedPreferencesUtils.SORT_TYPE_SHARED_PREFERENCES_FILE, Context.MODE_PRIVATE)
        val editor = preferences.edit()
        var changed = false

        for ((legacyBase, base) in listOf(
            SharedPreferencesUtils.SORT_TYPE_SUBREDDIT_POST_BASE to
                SharedPreferencesUtils.SORT_TYPE_ANONYMOUS_FRONT_PAGE_POST,
            SharedPreferencesUtils.SORT_TIME_SUBREDDIT_POST_BASE to
                SharedPreferencesUtils.SORT_TIME_ANONYMOUS_FRONT_PAGE_POST,
        )) {
            val from = AccountScope.key(
                Account.ANONYMOUS_ACCOUNT, legacyBase + LEGACY_ANONYMOUS_ACCOUNT)
            val value = preferences.getString(from, null) ?: continue
            editor.putString(AccountScope.key(Account.ANONYMOUS_ACCOUNT, base), value)
            editor.remove(from)
            changed = true
        }

        if (changed) {
            editor.apply()
        }
    }
}
