package ml.docilealligator.infinityforreddit.recentlyvisited

import android.content.SharedPreferences
import java.util.Locale
import java.util.concurrent.Executor
import ml.docilealligator.infinityforreddit.RedditDataRoomDatabase
import ml.docilealligator.infinityforreddit.account.Account
import ml.docilealligator.infinityforreddit.account.AccountScope
import ml.docilealligator.infinityforreddit.utils.SharedPreferencesUtils

/**
 * Writes visits to [RecentlyVisited]. Called once a subreddit's or user's data has finished
 * loading, so the name and icon are inserted together and a page that never loaded is never
 * recorded.
 *
 * The subscribe/follow exclusion happens here rather than at display time: anything already in
 * `subscribed_subreddits` / `subscribed_users` is simply not written. Things subscribed to *from*
 * the Recently Visited list therefore keep their row -- the list shows their state instead.
 */
object RecordRecentlyVisited {

    /** Rows kept per account, per type. Oldest visits beyond this are trimmed on insert. */
    const val LIMIT_PER_TYPE = 50

    /**
     * Reddit's own listing endpoints dressed up as subreddits. Visiting one is not visiting a
     * community, so none of them are recorded.
     */
    private val PSEUDO_SUBREDDITS = setOf("popular", "all", "random", "randnsfw", "mod", "friends")

    @JvmStatic
    fun isEnabled(accountName: String, recentlyVisitedSharedPreferences: SharedPreferences): Boolean =
        recentlyVisitedSharedPreferences.getBoolean(
            AccountScope.key(accountName, SharedPreferencesUtils.RECENTLY_VISITED_ENABLED_BASE), false
        )

    @JvmStatic
    fun recordSubreddit(
        executor: Executor,
        redditDataRoomDatabase: RedditDataRoomDatabase,
        recentlyVisitedSharedPreferences: SharedPreferences,
        accountName: String,
        subredditName: String,
        iconUrl: String?
    ) {
        if (!isEnabled(accountName, recentlyVisitedSharedPreferences)) {
            return
        }
        if (subredditName.isEmpty() || PSEUDO_SUBREDDITS.contains(subredditName.lowercase(Locale.US))) {
            return
        }
        // A u_ subreddit is a user's profile feed, so following or saving that user excludes it
        // too -- otherwise the same person shows up twice, once past the exclusion.
        val profileOwner = if (subredditName.length > 2 && subredditName.startsWith("u_")) {
            subredditName.substring(2)
        } else {
            null
        }
        if (profileOwner != null && profileOwner.equals(accountName, ignoreCase = true)) {
            return
        }
        executor.execute {
            if (redditDataRoomDatabase.subscribedSubredditDao()
                    .getSubscribedSubreddit(subredditName, accountName) != null
            ) {
                return@execute
            }
            if (profileOwner != null && redditDataRoomDatabase.subscribedUserDao()
                    .getSubscribedUser(profileOwner, accountName) != null
            ) {
                return@execute
            }
            insert(redditDataRoomDatabase, accountName, subredditName, RecentlyVisitedType.SUBREDDIT, iconUrl)
        }
    }

    @JvmStatic
    fun recordUser(
        executor: Executor,
        redditDataRoomDatabase: RedditDataRoomDatabase,
        recentlyVisitedSharedPreferences: SharedPreferences,
        accountName: String,
        username: String,
        iconUrl: String?
    ) {
        if (!isEnabled(accountName, recentlyVisitedSharedPreferences)) {
            return
        }
        if (username.isEmpty() || username.equals(accountName, ignoreCase = true)) {
            return
        }
        executor.execute {
            if (redditDataRoomDatabase.subscribedUserDao().getSubscribedUser(username, accountName) != null) {
                return@execute
            }
            insert(redditDataRoomDatabase, accountName, username, RecentlyVisitedType.USER, iconUrl)
        }
    }

    /** Removes every row for an account. Used when the setting is switched off. */
    @JvmStatic
    fun purge(executor: Executor, redditDataRoomDatabase: RedditDataRoomDatabase, accountName: String) {
        executor.execute { redditDataRoomDatabase.recentlyVisitedDao().deleteAllForAccount(accountName) }
    }

    private fun insert(
        redditDataRoomDatabase: RedditDataRoomDatabase,
        accountName: String,
        name: String,
        @RecentlyVisitedType type: Int,
        iconUrl: String?
    ) {
        // The anonymous account row is created lazily elsewhere too; the foreign key needs it.
        if (accountName == Account.ANONYMOUS_ACCOUNT) {
            redditDataRoomDatabase.accountDao().insertIfNotExists(Account.getAnonymousAccount())
        }
        val dao = redditDataRoomDatabase.recentlyVisitedDao()
        dao.insert(RecentlyVisited(accountName, name, type, iconUrl, System.currentTimeMillis()))
        dao.trimToLimit(accountName, type, LIMIT_PER_TYPE)
    }
}
