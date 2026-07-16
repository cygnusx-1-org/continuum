package ml.docilealligator.infinityforreddit;

import android.os.Handler;
import androidx.annotation.Nullable;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Executor;
import ml.docilealligator.infinityforreddit.account.Account;
import ml.docilealligator.infinityforreddit.multireddit.AnonymousMultiredditSubreddit;
import ml.docilealligator.infinityforreddit.postfilter.PostFilter;
import ml.docilealligator.infinityforreddit.subscribedsubreddit.SubscribedSubredditData;
import ml.docilealligator.infinityforreddit.subscribeduser.SubscribedUserData;

public class FetchPostFilterAndConcatenatedSubredditNames {
    /**
     * Refresh {@link PostFilter#neverHideSubredditsLowerCase} with the current account's subscribed
     * subreddits and the {@code u_username} profile subreddits of the users it follows, so that
     * prefix/suffix matching never hides them. When neither toggle is on it just clears the set and
     * skips the database queries, so the common case pays nothing. Runs on the caller's worker
     * thread (this class is always invoked from an executor).
     */
    private static void refreshNeverHideSubreddits(RedditDataRoomDatabase redditDataRoomDatabase) {
        if (neverHideMatchingDisabled()) {
            PostFilter.neverHideSubredditsLowerCase = Collections.emptySet();
            return;
        }
        Account currentAccount = redditDataRoomDatabase.accountDao().getCurrentAccount();
        String accountName = currentAccount != null ? currentAccount.getAccountName() : Account.ANONYMOUS_ACCOUNT;
        List<SubscribedSubredditData> subscribedSubreddits = redditDataRoomDatabase.subscribedSubredditDao().getAllSubscribedSubredditsList(accountName);
        updateNeverHideSubreddits(redditDataRoomDatabase, accountName, subscribedSubreddits);
    }

    /**
     * Variant that reuses an already-fetched subscribed-subreddit list, so callers that have it in
     * hand (e.g. the anonymous front page) don't query it a second time.
     */
    private static void refreshNeverHideSubreddits(RedditDataRoomDatabase redditDataRoomDatabase, String accountName,
                                                   List<SubscribedSubredditData> subscribedSubreddits) {
        if (neverHideMatchingDisabled()) {
            PostFilter.neverHideSubredditsLowerCase = Collections.emptySet();
            return;
        }
        updateNeverHideSubreddits(redditDataRoomDatabase, accountName, subscribedSubreddits);
    }

    private static void updateNeverHideSubreddits(RedditDataRoomDatabase redditDataRoomDatabase, String accountName,
                                                  List<SubscribedSubredditData> subscribedSubreddits) {
        Set<String> names = new HashSet<>();
        if (subscribedSubreddits != null) {
            for (SubscribedSubredditData s : subscribedSubreddits) {
                if (s.getName() != null) {
                    names.add(s.getName().toLowerCase(Locale.ENGLISH));
                }
            }
        }
        List<SubscribedUserData> subscribedUsers = redditDataRoomDatabase.subscribedUserDao().getAllSubscribedUsersList(accountName);
        if (subscribedUsers != null) {
            for (SubscribedUserData u : subscribedUsers) {
                if (u.getName() != null) {
                    // Posts from a followed user's profile appear under the "u_username" subreddit.
                    names.add(("u_" + u.getName()).toLowerCase(Locale.ENGLISH));
                }
            }
        }
        PostFilter.neverHideSubredditsLowerCase = names;
    }

    private static boolean neverHideMatchingDisabled() {
        return !PostFilter.subredditFilterPrefixMatching && !PostFilter.subredditFilterSuffixMatching;
    }

    public static void fetchPostFilter(RedditDataRoomDatabase redditDataRoomDatabase, Executor executor,
                                                   Handler handler, int postFilterUsage,
                                                   @Nullable String nameOfUsage, FetchPostFilterListerner fetchPostFilterListerner) {
        executor.execute(() -> {
            refreshNeverHideSubreddits(redditDataRoomDatabase);
            List<PostFilter> postFilters = redditDataRoomDatabase.postFilterDao().getValidPostFilters(postFilterUsage, nameOfUsage);
            PostFilter mergedPostFilter = PostFilter.mergePostFilter(postFilters);
            handler.post(() -> fetchPostFilterListerner.success(mergedPostFilter));
        });
    }

    public static void fetchPostFilterAndConcatenatedSubredditNames(RedditDataRoomDatabase redditDataRoomDatabase, Executor executor,
                                                   Handler handler, int postFilterUsage, @Nullable String nameOfUsage,
                                                   FetchPostFilterAndConcatenatecSubredditNamesListener fetchPostFilterAndConcatenatecSubredditNamesListener) {
        executor.execute(() -> {
            List<PostFilter> postFilters = redditDataRoomDatabase.postFilterDao().getValidPostFilters(postFilterUsage, nameOfUsage);
            PostFilter mergedPostFilter = PostFilter.mergePostFilter(postFilters);
            List<SubscribedSubredditData> anonymousSubscribedSubreddits = redditDataRoomDatabase.subscribedSubredditDao().getAllSubscribedSubredditsList(Account.ANONYMOUS_ACCOUNT);
            refreshNeverHideSubreddits(redditDataRoomDatabase, Account.ANONYMOUS_ACCOUNT, anonymousSubscribedSubreddits);
            if (anonymousSubscribedSubreddits != null && !anonymousSubscribedSubreddits.isEmpty()) {
                StringBuilder stringBuilder = new StringBuilder();
                for (SubscribedSubredditData s : anonymousSubscribedSubreddits) {
                    stringBuilder.append(s.getName()).append("+");
                }
                if (stringBuilder.length() > 0) {
                    stringBuilder.deleteCharAt(stringBuilder.length() - 1);
                }
                handler.post(() -> fetchPostFilterAndConcatenatecSubredditNamesListener.success(mergedPostFilter, stringBuilder.toString()));
            } else {
                handler.post(() -> fetchPostFilterAndConcatenatecSubredditNamesListener.success(mergedPostFilter, null));
            }
        });
    }

    public static void fetchPostFilterAndConcatenatedSubredditNames(RedditDataRoomDatabase redditDataRoomDatabase, Executor executor,
                                                                    Handler handler, @Nullable String multipath, int postFilterUsage, @Nullable String nameOfUsage,
                                                                    FetchPostFilterAndConcatenatecSubredditNamesListener fetchPostFilterAndConcatenatecSubredditNamesListener) {
        executor.execute(() -> {
            refreshNeverHideSubreddits(redditDataRoomDatabase);
            List<PostFilter> postFilters = redditDataRoomDatabase.postFilterDao().getValidPostFilters(postFilterUsage, nameOfUsage);
            PostFilter mergedPostFilter = PostFilter.mergePostFilter(postFilters);
            List<AnonymousMultiredditSubreddit> anonymousMultiredditSubreddits = redditDataRoomDatabase.anonymousMultiredditSubredditDao().getAllAnonymousMultiRedditSubreddits(multipath);
            if (anonymousMultiredditSubreddits != null && !anonymousMultiredditSubreddits.isEmpty()) {
                StringBuilder stringBuilder = new StringBuilder();
                for (AnonymousMultiredditSubreddit s : anonymousMultiredditSubreddits) {
                    stringBuilder.append(s.getSubredditName()).append("+");
                }
                if (stringBuilder.length() > 0) {
                    stringBuilder.deleteCharAt(stringBuilder.length() - 1);
                }
                handler.post(() -> fetchPostFilterAndConcatenatecSubredditNamesListener.success(mergedPostFilter, stringBuilder.toString()));
            } else {
                handler.post(() -> fetchPostFilterAndConcatenatecSubredditNamesListener.success(mergedPostFilter, null));
            }
        });
    }

    public interface FetchPostFilterListerner {
        void success(PostFilter postFilter);
    }

    public interface FetchPostFilterAndConcatenatecSubredditNamesListener {
        void success(PostFilter postFilter, @Nullable String concatenatedSubredditNames);
    }
}
