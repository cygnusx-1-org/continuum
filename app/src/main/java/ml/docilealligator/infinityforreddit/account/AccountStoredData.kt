package ml.docilealligator.infinityforreddit.account

import android.annotation.SuppressLint
import android.content.Context
import ml.docilealligator.infinityforreddit.RedditDataRoomDatabase
import ml.docilealligator.infinityforreddit.readpost.ReadPostType
import ml.docilealligator.infinityforreddit.utils.SharedPreferencesUtils

/**
 * One account's accumulated data, thrown away a kind at a time.
 *
 * These sit beside the whole-account operations in [AccountSettings] on the Account Settings
 * Management screen, and they are what used to be the Advanced screen's "Delete All" actions. Each
 * is deliberately narrow: this account's subscription list, its followed and saved users, the sort
 * order a feed remembers, the layout a feed remembers, where Home had reached, and which posts have
 * been read. None of them is a setting anyone chose on a settings screen, which is why they are
 * worth clearing without resetting the settings that were.
 *
 * Every function is scoped to one account and touches no other's, which is what separates them from
 * the actions left on Global Settings Management — the theme library and the legacy keys have no
 * account to belong to.
 *
 * All of them block. Call them off the main thread.
 */
object AccountStoredData {

    /**
     * This account's subreddit subscriptions, favourites included.
     *
     * The `subscribed_subreddits` rows only, not the `subreddits` metadata cache: that one is keyed
     * by subreddit id with no account column, because an icon and a sidebar are the same bytes
     * whoever asked for them.
     *
     * A signed-in account's list is a copy of what Reddit holds, so it refills on the next
     * subscription sync. Anonymous browsing is the exception both ways: it can subscribe
     * (`SubredditSubscription#insertSubscription` writes the row with no server call) and
     * `MainActivity#loadSubscriptions` never syncs it, so for anonymous this is the only copy.
     */
    @JvmStatic
    fun deleteSubscribedSubreddits(
        redditDataRoomDatabase: RedditDataRoomDatabase,
        accountName: String?,
    ) {
        redditDataRoomDatabase.subscribedSubredditDao()
            .deleteAllSubscribedSubreddits(accountName ?: Account.ANONYMOUS_ACCOUNT)
    }

    /**
     * The users this account follows or has saved.
     *
     * One `subscribed_users` row covers both, so both go. A signed-in account's follows are
     * Reddit's and come back on the next sync. Saving a user is purely local
     * ([ml.docilealligator.infinityforreddit.user.UserSaving]), and so is following one while
     * logged out (`UserFollowing#anonymousFollowUser`), so neither of those comes back.
     *
     * As above, the `users` metadata cache is left alone — it has no account column either.
     */
    @JvmStatic
    fun deleteSubscribedUsers(
        redditDataRoomDatabase: RedditDataRoomDatabase,
        accountName: String?,
    ) {
        redditDataRoomDatabase.subscribedUserDao()
            .deleteAllSubscribedUsers(accountName ?: Account.ANONYMOUS_ACCOUNT)
    }

    /** The sort order remembered for each feed this account has changed it on. */
    @JvmStatic
    fun deleteSortTypes(context: Context, accountName: String?) {
        AccountSettings.resetFile(
            context, SharedPreferencesUtils.SORT_TYPE_SHARED_PREFERENCES_FILE, accountName)
    }

    /** The layout remembered for each feed this account has changed it on. */
    @JvmStatic
    fun deletePostLayouts(context: Context, accountName: String?) {
        AccountSettings.resetFile(
            context, SharedPreferencesUtils.POST_LAYOUT_SHARED_PREFERENCES_FILE, accountName)
    }

    /**
     * Where this account had reached in Home, which the feed picks up again on the next launch.
     *
     * This file predates [AccountScope] and does not use its separator: the key is the account name
     * followed directly by [SharedPreferencesUtils.FRONT_PAGE_SCROLLED_POSITION_FRONT_PAGE_BASE],
     * which is why it is read here rather than through [AccountSettings]. It needs no anonymous
     * special case — this is the file whose `.anonymous` spelling every account name now shares.
     * One key per account, so there is nothing to scan for.
     */
    @SuppressLint("ApplySharedPref")
    @JvmStatic
    fun deleteFrontPageScrolledPosition(context: Context, accountName: String?) {
        context.getSharedPreferences(
            SharedPreferencesUtils.FRONT_PAGE_SCROLLED_POSITION_SHARED_PREFERENCES_FILE,
            Context.MODE_PRIVATE)
            .edit()
            .remove(AccountScope.namespace(accountName) +
                SharedPreferencesUtils.FRONT_PAGE_SCROLLED_POSITION_FRONT_PAGE_BASE)
            .commit()
    }

    /**
     * The posts this account has read.
     *
     * `read_posts` is not only read posts: anonymous browsing keeps its upvoted, downvoted, hidden
     * and saved lists in the same table under other [ReadPostType]s, and those are things the user
     * put there rather than a history that accumulated. Only [ReadPostType.READ_POSTS] goes, which
     * is also exactly what the row's summary counts.
     */
    @JvmStatic
    fun deleteReadPosts(redditDataRoomDatabase: RedditDataRoomDatabase, accountName: String?) {
        redditDataRoomDatabase.readPostDao().deleteAllReadPosts(
            accountName ?: Account.ANONYMOUS_ACCOUNT, ReadPostType.READ_POSTS)
    }
}
