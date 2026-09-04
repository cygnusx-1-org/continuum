package ml.docilealligator.infinityforreddit;

import android.os.Handler;
import androidx.annotation.Nullable;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Executor;
import ml.docilealligator.infinityforreddit.account.Account;
import ml.docilealligator.infinityforreddit.multireddit.AnonymousMultiredditSubreddit;
import ml.docilealligator.infinityforreddit.postfilter.PostFilter;
import ml.docilealligator.infinityforreddit.postfilter.PostFilterBlockedSubreddit;
import ml.docilealligator.infinityforreddit.postfilter.PostFilterUsage;
import ml.docilealligator.infinityforreddit.subscribedsubreddit.SubscribedSubredditData;
import ml.docilealligator.infinityforreddit.subscribeduser.SubscribedUserData;

public class FetchPostFilterAndConcatenatedSubredditNames {
    /**
     * Resolve the filters that apply to a feed and prepare the static state
     * {@link PostFilter#isPostAllowed} reads for them.
     *
     * <p>Wildcard "Exclude subreddits" terms only run on
     * {@link Constants#CONTINUUM_ALL_SUBREDDIT}, so the flag that enables them is set here, where
     * the feed is still known — {@code isPostAllowed} itself is static and sees only a post and a
     * filter. When the resolved filter has no wildcard term (the overwhelmingly common case) the
     * supporting lookups are skipped entirely, so nothing pays for a feature it is not using.
     *
     * <p>The two static sets are left alone on that path rather than cleared. They are read only
     * while a wildcard term is matching — which happens on that one feed, which refreshes them as it
     * loads — and every feed resolves its filter on a shared executor: clearing them here would
     * strip a ContinuumAll feed that is still paging of its subscribed-subreddit protection the
     * moment any other feed loaded.
     */
    private static PostFilter resolvePostFilter(RedditDataRoomDatabase redditDataRoomDatabase,
                                                int postFilterUsage, @Nullable String nameOfUsage) {
        // Read before the filters, not after: a filter belongs to an account now, so the account is
        // part of the question rather than something only the wildcard path needs.
        Account currentAccount = redditDataRoomDatabase.accountDao().getCurrentAccount();
        String accountName = currentAccount != null ? currentAccount.getAccountName() : Account.ANONYMOUS_ACCOUNT;
        PostFilter mergedPostFilter = mergeValidPostFilters(redditDataRoomDatabase, postFilterUsage, nameOfUsage, accountName);
        if (!mergedPostFilter.wildcardSubredditMatchingEnabled || mergedPostFilter.subredditTermOwners.isEmpty()) {
            return mergedPostFilter;
        }
        List<SubscribedSubredditData> subscribedSubreddits = redditDataRoomDatabase.subscribedSubredditDao().getAllSubscribedSubredditsList(accountName);
        updateNeverHideSubreddits(redditDataRoomDatabase, accountName, subscribedSubreddits);
        refreshWildcardExceptions(redditDataRoomDatabase, accountName);
        return mergedPostFilter;
    }

    /**
     * The account's filters for this feed, merged into one. The merged filter carries the account
     * too, so the blocked-subreddit rows a wildcard term records land under the right owner.
     */
    private static PostFilter mergeValidPostFilters(RedditDataRoomDatabase redditDataRoomDatabase,
                                                    int postFilterUsage, @Nullable String nameOfUsage,
                                                    String accountName) {
        List<PostFilter> postFilters = redditDataRoomDatabase.postFilterDao()
                .getValidPostFilters(postFilterUsage, nameOfUsage, accountName);
        PostFilter mergedPostFilter = PostFilter.mergePostFilter(postFilters);
        mergedPostFilter.username = accountName;
        mergedPostFilter.wildcardSubredditMatchingEnabled =
                postFilterUsage == PostFilterUsage.SUBREDDIT_TYPE && Constants.isContinuumAll(nameOfUsage);
        return mergedPostFilter;
    }

    /**
     * Variant for callers that already hold the subscribed-subreddit list (the anonymous front page)
     * so it isn't queried a second time.
     */
    private static PostFilter resolvePostFilter(RedditDataRoomDatabase redditDataRoomDatabase,
                                                int postFilterUsage, @Nullable String nameOfUsage,
                                                String accountName,
                                                @Nullable List<SubscribedSubredditData> subscribedSubreddits) {
        PostFilter mergedPostFilter = mergeValidPostFilters(redditDataRoomDatabase, postFilterUsage, nameOfUsage, accountName);
        if (!mergedPostFilter.wildcardSubredditMatchingEnabled || mergedPostFilter.subredditTermOwners.isEmpty()) {
            return mergedPostFilter;
        }
        updateNeverHideSubreddits(redditDataRoomDatabase, accountName, subscribedSubreddits);
        refreshWildcardExceptions(redditDataRoomDatabase, accountName);
        return mergedPostFilter;
    }

    /**
     * Refresh {@link PostFilter#wildcardExceptionKeys} from the subreddits the user has marked as
     * exceptions in a rule's blocked list.
     */
    private static void refreshWildcardExceptions(RedditDataRoomDatabase redditDataRoomDatabase,
                                                 String accountName) {
        List<PostFilterBlockedSubreddit> exceptions =
                redditDataRoomDatabase.postFilterBlockedSubredditDao().getAllExceptions(accountName);
        Set<String> keys = new HashSet<>();
        for (PostFilterBlockedSubreddit e : exceptions) {
            keys.add(PostFilter.exceptionKey(e.getFilterName(), e.getRuleValue(), e.getSubredditName()));
        }
        PostFilter.wildcardExceptionKeys = keys;
    }

    private static void updateNeverHideSubreddits(RedditDataRoomDatabase redditDataRoomDatabase, String accountName,
                                                  @Nullable List<SubscribedSubredditData> subscribedSubreddits) {
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

    public static void fetchPostFilter(RedditDataRoomDatabase redditDataRoomDatabase, Executor executor,
                                                   Handler handler, int postFilterUsage,
                                                   @Nullable String nameOfUsage, FetchPostFilterListerner fetchPostFilterListerner) {
        executor.execute(() -> {
            PostFilter mergedPostFilter = resolvePostFilter(redditDataRoomDatabase, postFilterUsage, nameOfUsage);
            handler.post(() -> fetchPostFilterListerner.success(mergedPostFilter));
        });
    }

    public static void fetchPostFilterAndConcatenatedSubredditNames(RedditDataRoomDatabase redditDataRoomDatabase, Executor executor,
                                                   Handler handler, int postFilterUsage, @Nullable String nameOfUsage,
                                                   FetchPostFilterAndConcatenatecSubredditNamesListener fetchPostFilterAndConcatenatecSubredditNamesListener) {
        executor.execute(() -> {
            List<SubscribedSubredditData> anonymousSubscribedSubreddits = redditDataRoomDatabase.subscribedSubredditDao().getAllSubscribedSubredditsList(Account.ANONYMOUS_ACCOUNT);
            PostFilter mergedPostFilter = resolvePostFilter(redditDataRoomDatabase, postFilterUsage, nameOfUsage,
                    Account.ANONYMOUS_ACCOUNT, anonymousSubscribedSubreddits);
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
            PostFilter mergedPostFilter = resolvePostFilter(redditDataRoomDatabase, postFilterUsage, nameOfUsage);
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
