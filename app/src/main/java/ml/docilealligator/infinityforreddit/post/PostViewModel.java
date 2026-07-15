package ml.docilealligator.infinityforreddit.post;

import android.content.SharedPreferences;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Pair;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelKt;
import androidx.lifecycle.ViewModelProvider;
import androidx.paging.Pager;
import androidx.paging.PagingConfig;
import androidx.paging.PagingData;
import androidx.paging.PagingDataTransforms;
import androidx.paging.PagingLiveData;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;
import ml.docilealligator.infinityforreddit.RedditDataRoomDatabase;
import ml.docilealligator.infinityforreddit.SingleLiveEvent;
import ml.docilealligator.infinityforreddit.account.Account;
import ml.docilealligator.infinityforreddit.apis.RedditAPI;
import ml.docilealligator.infinityforreddit.moderation.PostModerationEvent;
import ml.docilealligator.infinityforreddit.postfilter.PostFilter;
import ml.docilealligator.infinityforreddit.readpost.ReadPostsListInterface;
import ml.docilealligator.infinityforreddit.thing.SortType;
import ml.docilealligator.infinityforreddit.utils.APIUtils;
import ml.docilealligator.infinityforreddit.utils.SavedPostCache;
import ml.docilealligator.infinityforreddit.utils.SavedSearchCache;
import ml.docilealligator.infinityforreddit.utils.SharedPreferencesUtils;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;

public class PostViewModel extends ViewModel {
    private final Executor executor;
    private final Retrofit retrofit;
    private final RedditDataRoomDatabase redditDataRoomDatabase;
    @Nullable
    private final String accessToken;
    private final String accountName;
    private final SharedPreferences sharedPreferences;
    @Nullable
    private final SharedPreferences postFeedScrolledPositionSharedPreferences;
    @Nullable
    private String name;
    @Nullable
    private String query;
    @Nullable
    private String trendingSource;
    @PostType
    private final int postType;
    private SortType sortType;
    private PostFilter postFilter;
    @Nullable
    private String userWhere;
    private ReadPostsListInterface readPostsList;
    private final MutableLiveData<Boolean> hideReadPostsValue = new MutableLiveData<>();

    private final LiveData<PagingData<Post>> posts;
    private final LiveData<PagingData<Post>> postsWithReadPostsHidden;
    // Saved-screen search. Only the Saved tab sets this; when non-empty the paging source loads the
    // whole saved listing, filters by the query, and returns every match at once (see
    // PostPagingSource#loadAllUserPostsFiltered). Read on the paging executor when a source is built,
    // written from the main thread, so it is volatile.
    @Nullable
    private volatile String searchQuery;
    // Full unfiltered saved listing shared with the current PostPagingSource so refining the query
    // filters in memory instead of re-walking the listing. Invalidated whenever the listing must be
    // refetched (sort/filter change, refresh, search cleared).
    private final SavedSearchCache<Post> savedSearchCache = new SavedSearchCache<>();
    // The most recently created source, kept so a query change can refresh it in place via
    // invalidate() instead of reposting a LiveData (which would tear down and rebuild the whole
    // pipeline while a slow load-all is still collecting — the rebuild race behind the paging CMEs).
    @Nullable
    private volatile PostPagingSource pagingSource;

    private final MutableLiveData<SortType> sortTypeLiveData;
    private final MutableLiveData<PostFilter> postFilterLiveData;
    private final SortTypeAndPostFilterLiveData sortTypeAndPostFilterLiveData;

    public final SingleLiveEvent<PostModerationEvent> moderationEventLiveData = new SingleLiveEvent<>();

    // PostType.FRONT_PAGE
    public PostViewModel(Executor executor, Retrofit retrofit, RedditDataRoomDatabase redditDataRoomDatabase,
                         @Nullable String accessToken, @NonNull String accountName,
                         SharedPreferences sharedPreferences, @Nullable SharedPreferences postFeedScrolledPositionSharedPreferences,
                         @Nullable SharedPreferences postHistorySharedPreferences, @PostType int postType,
                         SortType sortType, PostFilter postFilter, ReadPostsListInterface readPostsList) {
        this.executor = executor;
        this.retrofit = retrofit;
        this.redditDataRoomDatabase = redditDataRoomDatabase;
        this.accessToken = accessToken;
        this.accountName = accountName;
        this.sharedPreferences = sharedPreferences;
        this.postFeedScrolledPositionSharedPreferences = postFeedScrolledPositionSharedPreferences;
        this.postType = postType;
        this.sortType = sortType;
        this.postFilter = postFilter;
        this.readPostsList = readPostsList;

        sortTypeLiveData = new MutableLiveData<>(sortType);
        postFilterLiveData = new MutableLiveData<>(postFilter);

        sortTypeAndPostFilterLiveData = new SortTypeAndPostFilterLiveData(sortTypeLiveData, postFilterLiveData);

        Pager<String, Post> pager = new Pager<>(new PagingConfig(100, 4, false, 10), this::returnPagingSoruce);

        posts = Transformations.switchMap(sortTypeAndPostFilterLiveData, sortAndPostFilter -> {
            changeSortTypeAndPostFilter(
                    Objects.requireNonNull(sortTypeLiveData.getValue()), Objects.requireNonNull(postFilterLiveData.getValue()));
            return PagingLiveData.cachedIn(PagingLiveData.getLiveData(pager), ViewModelKt.getViewModelScope(this));
        });

        postsWithReadPostsHidden = PagingLiveData.cachedIn(Transformations.switchMap(hideReadPostsValue,
                currentlyReadPostIds -> Transformations.map(
                        posts,
                        postPagingData -> PagingDataTransforms.filter(
                                postPagingData, executor,
                                post -> !post.isRead() || !Boolean.TRUE.equals(hideReadPostsValue.getValue())))), ViewModelKt.getViewModelScope(this));

        hideReadPostsValue.setValue(postHistorySharedPreferences != null
                && postHistorySharedPreferences.getBoolean((accountName.equals(Account.ANONYMOUS_ACCOUNT) ? "" : accountName) + SharedPreferencesUtils.HIDE_READ_POSTS_AUTOMATICALLY_BASE, false));
    }

    // PostType.SUBREDDIT || PostType.ANONYMOUS_FRONT_PAGE || PostType.ANONYMOUS_MULTIREDDIT
    public PostViewModel(Executor executor, Retrofit retrofit, RedditDataRoomDatabase redditDataRoomDatabase,
                         @Nullable String accessToken, @NonNull String accountName,
                         SharedPreferences sharedPreferences, @Nullable SharedPreferences postFeedScrolledPositionSharedPreferences,
                         @Nullable SharedPreferences postHistorySharedPreferences, @Nullable String subredditName, @PostType int postType,
                         SortType sortType, PostFilter postFilter, ReadPostsListInterface readPostsList) {
        this.executor = executor;
        this.retrofit = retrofit;
        this.redditDataRoomDatabase = redditDataRoomDatabase;
        this.accessToken = accessToken;
        this.accountName = accountName;
        this.sharedPreferences = sharedPreferences;
        this.postFeedScrolledPositionSharedPreferences = postFeedScrolledPositionSharedPreferences;
        this.postType = postType;
        this.sortType = sortType;
        this.postFilter = postFilter;
        this.readPostsList = readPostsList;
        this.name = subredditName;

        sortTypeLiveData = new MutableLiveData<>(sortType);
        postFilterLiveData = new MutableLiveData<>(postFilter);

        sortTypeAndPostFilterLiveData = new SortTypeAndPostFilterLiveData(sortTypeLiveData, postFilterLiveData);

        Pager<String, Post> pager = new Pager<>(new PagingConfig(100, 4, false, 10), this::returnPagingSoruce);

        posts = Transformations.switchMap(sortTypeAndPostFilterLiveData, sortAndPostFilter -> {
            changeSortTypeAndPostFilter(
                    Objects.requireNonNull(sortTypeLiveData.getValue()), Objects.requireNonNull(postFilterLiveData.getValue()));
            return PagingLiveData.cachedIn(PagingLiveData.getLiveData(pager), ViewModelKt.getViewModelScope(this));
        });

        postsWithReadPostsHidden = PagingLiveData.cachedIn(Transformations.switchMap(hideReadPostsValue,
                currentlyReadPostIds -> Transformations.map(
                        posts,
                        postPagingData -> PagingDataTransforms.filter(
                                postPagingData, executor,
                                post -> !post.isRead() || !Boolean.TRUE.equals(hideReadPostsValue.getValue())))), ViewModelKt.getViewModelScope(this));

        hideReadPostsValue.setValue(postHistorySharedPreferences != null
                && postHistorySharedPreferences.getBoolean((accountName.equals(Account.ANONYMOUS_ACCOUNT) ? "" : accountName) + SharedPreferencesUtils.HIDE_READ_POSTS_AUTOMATICALLY_BASE, false)
                && ((postType != PostType.SUBREDDIT || "all".equals(subredditName) || "popular".equals(subredditName)) || postHistorySharedPreferences.getBoolean((accountName.equals(Account.ANONYMOUS_ACCOUNT) ? "" : accountName) + SharedPreferencesUtils.HIDE_READ_POSTS_AUTOMATICALLY_IN_SUBREDDITS_BASE, false)));
    }

    // PostType.MULTIREDDIT
    public PostViewModel(Executor executor, Retrofit retrofit, RedditDataRoomDatabase redditDataRoomDatabase,
                         @Nullable String accessToken, @NonNull String accountName,
                         SharedPreferences sharedPreferences, @Nullable SharedPreferences postFeedScrolledPositionSharedPreferences,
                         @Nullable SharedPreferences postHistorySharedPreferences, @Nullable String multiredditPath, @Nullable String query,
                         @PostType int postType, SortType sortType, PostFilter postFilter, ReadPostsListInterface readPostsList) {
        this.executor = executor;
        this.retrofit = retrofit;
        this.redditDataRoomDatabase = redditDataRoomDatabase;
        this.accessToken = accessToken;
        this.accountName = accountName;
        this.sharedPreferences = sharedPreferences;
        this.postFeedScrolledPositionSharedPreferences = postFeedScrolledPositionSharedPreferences;
        this.postType = postType;
        this.sortType = sortType;
        this.postFilter = postFilter;
        this.readPostsList = readPostsList;
        this.name = multiredditPath;
        this.query = query;

        sortTypeLiveData = new MutableLiveData<>(sortType);
        postFilterLiveData = new MutableLiveData<>(postFilter);

        sortTypeAndPostFilterLiveData = new SortTypeAndPostFilterLiveData(sortTypeLiveData, postFilterLiveData);

        Pager<String, Post> pager = new Pager<>(new PagingConfig(100, 4, false, 10), this::returnPagingSoruce);

        posts = Transformations.switchMap(sortTypeAndPostFilterLiveData, sortAndPostFilter -> {
            changeSortTypeAndPostFilter(
                    Objects.requireNonNull(sortTypeLiveData.getValue()), Objects.requireNonNull(postFilterLiveData.getValue()));
            return PagingLiveData.cachedIn(PagingLiveData.getLiveData(pager), ViewModelKt.getViewModelScope(this));
        });

        postsWithReadPostsHidden = PagingLiveData.cachedIn(Transformations.switchMap(hideReadPostsValue,
                currentlyReadPostIds -> Transformations.map(
                        posts,
                        postPagingData -> PagingDataTransforms.filter(
                                postPagingData, executor,
                                post -> !post.isRead() || !Boolean.TRUE.equals(hideReadPostsValue.getValue())))), ViewModelKt.getViewModelScope(this));

        hideReadPostsValue.setValue(postHistorySharedPreferences != null
                && postHistorySharedPreferences.getBoolean((accountName.equals(Account.ANONYMOUS_ACCOUNT) ? "" : accountName) + SharedPreferencesUtils.HIDE_READ_POSTS_AUTOMATICALLY_BASE, false));
    }

    // PostPagingSource.TYPE_USER
    public PostViewModel(Executor executor, Retrofit retrofit, RedditDataRoomDatabase redditDataRoomDatabase,
                         @Nullable String accessToken, @NonNull String accountName,
                         SharedPreferences sharedPreferences,
                         @Nullable SharedPreferences postFeedScrolledPositionSharedPreferences,
                         @Nullable SharedPreferences postHistorySharedPreferences, @Nullable String username,
                         @PostType int postType, SortType sortType, PostFilter postFilter, @Nullable String userWhere,
                         ReadPostsListInterface readPostsList) {
        this.executor = executor;
        this.retrofit = retrofit;
        this.redditDataRoomDatabase = redditDataRoomDatabase;
        this.accessToken = accessToken;
        this.accountName = accountName;
        this.sharedPreferences = sharedPreferences;
        this.postFeedScrolledPositionSharedPreferences = postFeedScrolledPositionSharedPreferences;
        this.postType = postType;
        this.sortType = sortType;
        this.postFilter = postFilter;
        this.readPostsList = readPostsList;
        this.name = username;
        this.userWhere = userWhere;

        sortTypeLiveData = new MutableLiveData<>(sortType);
        postFilterLiveData = new MutableLiveData<>(postFilter);

        sortTypeAndPostFilterLiveData = new SortTypeAndPostFilterLiveData(sortTypeLiveData, postFilterLiveData);

        Pager<String, Post> pager = new Pager<>(new PagingConfig(100, 4, false, 10), this::returnPagingSoruce);

        posts = Transformations.switchMap(sortTypeAndPostFilterLiveData, sortAndPostFilter -> {
            changeSortTypeAndPostFilter(
                    Objects.requireNonNull(sortTypeLiveData.getValue()), Objects.requireNonNull(postFilterLiveData.getValue()));
            return PagingLiveData.cachedIn(PagingLiveData.getLiveData(pager), ViewModelKt.getViewModelScope(this));
        });

        postsWithReadPostsHidden = PagingLiveData.cachedIn(Transformations.switchMap(hideReadPostsValue,
                currentlyReadPostIds -> Transformations.map(
                        posts,
                        postPagingData -> PagingDataTransforms.filter(
                                postPagingData, executor,
                                post -> !post.isRead() || !Boolean.TRUE.equals(hideReadPostsValue.getValue())))), ViewModelKt.getViewModelScope(this));

        hideReadPostsValue.setValue(postHistorySharedPreferences != null
                && postHistorySharedPreferences.getBoolean((accountName.equals(Account.ANONYMOUS_ACCOUNT) ? "" : accountName) + SharedPreferencesUtils.HIDE_READ_POSTS_AUTOMATICALLY_BASE, false)
                && postHistorySharedPreferences.getBoolean((accountName.equals(Account.ANONYMOUS_ACCOUNT) ? "" : accountName) + SharedPreferencesUtils.HIDE_READ_POSTS_AUTOMATICALLY_IN_USERS_BASE, false));
    }

    // postType == PostType.SEARCH
    public PostViewModel(Executor executor, Retrofit retrofit, RedditDataRoomDatabase redditDataRoomDatabase,
                         @Nullable String accessToken, @NonNull String accountName,
                         SharedPreferences sharedPreferences, @Nullable SharedPreferences postFeedScrolledPositionSharedPreferences,
                         @Nullable SharedPreferences postHistorySharedPreferences, @Nullable String subredditName, @Nullable String query,
                         @Nullable String trendingSource, @PostType int postType, SortType sortType, PostFilter postFilter,
                         ReadPostsListInterface readPostsList) {
        this.executor = executor;
        this.retrofit = retrofit;
        this.redditDataRoomDatabase = redditDataRoomDatabase;
        this.accessToken = accessToken;
        this.accountName = accountName;
        this.sharedPreferences = sharedPreferences;
        this.postFeedScrolledPositionSharedPreferences = postFeedScrolledPositionSharedPreferences;
        this.postType = postType;
        this.sortType = sortType;
        this.postFilter = postFilter;
        this.readPostsList = readPostsList;
        this.name = subredditName;
        this.query = query;
        this.trendingSource = trendingSource;

        sortTypeLiveData = new MutableLiveData<>(sortType);
        postFilterLiveData = new MutableLiveData<>(postFilter);

        sortTypeAndPostFilterLiveData = new SortTypeAndPostFilterLiveData(sortTypeLiveData, postFilterLiveData);

        Pager<String, Post> pager = new Pager<>(new PagingConfig(100, 4, false, 10), this::returnPagingSoruce);

        posts = Transformations.switchMap(sortTypeAndPostFilterLiveData, sortAndPostFilter -> {
            changeSortTypeAndPostFilter(
                    Objects.requireNonNull(sortTypeLiveData.getValue()), Objects.requireNonNull(postFilterLiveData.getValue()));
            return PagingLiveData.cachedIn(PagingLiveData.getLiveData(pager), ViewModelKt.getViewModelScope(this));
        });

        postsWithReadPostsHidden = PagingLiveData.cachedIn(Transformations.switchMap(hideReadPostsValue,
                currentlyReadPostIds -> Transformations.map(
                        posts,
                        postPagingData -> PagingDataTransforms.filter(
                                postPagingData, executor,
                                post -> !post.isRead() || !Boolean.TRUE.equals(hideReadPostsValue.getValue())))), ViewModelKt.getViewModelScope(this));

        hideReadPostsValue.setValue(postHistorySharedPreferences != null
                && postHistorySharedPreferences.getBoolean((accountName.equals(Account.ANONYMOUS_ACCOUNT) ? "" : accountName) + SharedPreferencesUtils.HIDE_READ_POSTS_AUTOMATICALLY_BASE, false)
                && postHistorySharedPreferences.getBoolean((accountName.equals(Account.ANONYMOUS_ACCOUNT) ? "" : accountName) + SharedPreferencesUtils.HIDE_READ_POSTS_AUTOMATICALLY_IN_SEARCH_BASE, false));
    }

    public LiveData<PagingData<Post>> getPosts() {
        return postsWithReadPostsHidden;
    }

    @Nullable
    public String getSearchQuery() {
        return searchQuery;
    }

    // Refreshes the current PostPagingSource in place (invalidate() rebuilds it from the factory with
    // the new query) rather than reposting a LiveData, so the LiveData/cachedIn/read-hidden-filter
    // pipeline is not torn down and rebuilt while a slow load-all is still collecting. A non-empty
    // query makes the source load the whole saved listing, filter it, and return every match at once;
    // an empty query restores the normal paginated listing. Clearing the query drops the cached
    // listing so reopening search refetches.
    public void searchSaved(String query) {
        String normalized = query == null ? "" : query;
        if (!normalized.equals(searchQuery == null ? "" : searchQuery)) {
            searchQuery = normalized;
            if (normalized.isEmpty()) {
                savedSearchCache.invalidate();
            }
            PostPagingSource currentSource = pagingSource;
            if (currentSource != null) {
                currentSource.invalidate();
            }
        }
    }

    public void hideReadPosts() {
        // Guard against re-firing when read posts are already hidden. Re-setting the same value makes
        // the switchMap tear down and rebuild the filter pipeline while the previous collection is still
        // cancelling; the two collectors then race over the cached post stream and crash paging with a
        // ConcurrentModificationException (issue #321). Only the initial false->true transition rebuilds,
        // which is the safe single-rebuild case.
        if (!Boolean.TRUE.equals(hideReadPostsValue.getValue())) {
            hideReadPostsValue.setValue(true);
        }
    }

    public PostPagingSource returnPagingSoruce() {
        PostPagingSource paging3PagingSource;
        switch (postType) {
            case PostType.FRONT_PAGE:
                paging3PagingSource = new PostPagingSource(executor, retrofit, redditDataRoomDatabase,
                        accessToken, accountName, sharedPreferences, postFeedScrolledPositionSharedPreferences,
                        postType, sortType, postFilter, readPostsList);
                break;
            case PostType.SUBREDDIT:
            case PostType.ANONYMOUS_FRONT_PAGE:
            case PostType.ANONYMOUS_MULTIREDDIT:
            case PostType.DUPLICATES:
                paging3PagingSource = new PostPagingSource(executor, retrofit, redditDataRoomDatabase,
                        accessToken, accountName, sharedPreferences, postFeedScrolledPositionSharedPreferences,
                        name, postType, sortType, postFilter, readPostsList);
                break;
            case PostType.MULTIREDDIT:
                paging3PagingSource = new PostPagingSource(executor, retrofit, redditDataRoomDatabase,
                        accessToken, accountName, sharedPreferences, postFeedScrolledPositionSharedPreferences,
                        name, query, postType, sortType, postFilter, readPostsList);
                break;
            case PostType.SEARCH:
                paging3PagingSource = new PostPagingSource(executor, retrofit, redditDataRoomDatabase,
                        accessToken, accountName, sharedPreferences, postFeedScrolledPositionSharedPreferences,
                        name, query, trendingSource, postType, sortType, postFilter, readPostsList);
                break;
            default:
                //User
                paging3PagingSource = new PostPagingSource(executor, retrofit, redditDataRoomDatabase,
                        accessToken, accountName, sharedPreferences, postFeedScrolledPositionSharedPreferences,
                        name, postType, sortType, postFilter, userWhere, searchQuery, savedSearchCache, readPostsList);
                break;
        }
        pagingSource = paging3PagingSource;
        return paging3PagingSource;
    }

    // Called by the Saved screen's pull-to-refresh so a refresh while a search is active refetches
    // the listing instead of serving the cached copy. Also drops the persistent hard-TTL cache so a
    // later tab reopen won't serve the pre-refresh list.
    public void invalidateSavedSearchCache() {
        savedSearchCache.invalidate();
        if (PostPagingSource.USER_WHERE_SAVED.equals(userWhere)) {
            SavedPostCache.invalidate();
        }
    }

    // Drops only the in-memory Saved search cache. Used after an in-app save/unsave (the persistent
    // SavedPostCache is already invalidated at the SaveThing funnel), so a search in progress on the
    // Saved tab refetches instead of re-filtering a list that still holds the just-unsaved item.
    public void invalidateInMemorySavedSearchCache() {
        savedSearchCache.invalidate();
    }

    // The "Bypass cache (fetch fresh)" toggle. Drops the persistent and in-memory Saved caches and
    // rebuilds the source, so the reload re-walks the network for the whole history and rewrites the
    // cache, instead of serving the copy still within its TTL.
    public void forceFreshSavedLoad() {
        SavedPostCache.invalidate();
        savedSearchCache.invalidate();
        PostPagingSource currentSource = pagingSource;
        if (currentSource != null) {
            currentSource.invalidate();
        }
    }

    private void changeSortTypeAndPostFilter(SortType sortType, PostFilter postFilter) {
        this.sortType = sortType;
        this.postFilter = postFilter;
    }

    public void changeSortType(SortType sortType) {
        // A different sort reorders the saved listing, so the cached search copy is stale.
        savedSearchCache.invalidate();
        sortTypeLiveData.postValue(sortType);
    }

    public void changePostFilter(PostFilter postFilter) {
        // A different filter changes which items pass, so the cached search copy is stale.
        savedSearchCache.invalidate();
        postFilterLiveData.postValue(postFilter);
    }

    // Used by the anonymous home/multireddit feed, whose subreddit list is assembled locally and can
    // change while this ViewModel is alive (e.g. subscribing from another screen). Updates the names
    // read by returnPagingSoruce() and re-triggers the paging pipeline so a fresh PostPagingSource is
    // built with them. Re-posting the current sort type rebuilds the feed exactly as changeSortType
    // does; without it the cached PagingData (and its stale names) would stick around.
    public void changeSubredditName(String name) {
        this.name = name;
        sortTypeLiveData.postValue(sortTypeLiveData.getValue());
    }

    public static class Factory extends ViewModelProvider.NewInstanceFactory {
        private final Executor executor;
        private final Retrofit retrofit;
        private final RedditDataRoomDatabase redditDataRoomDatabase;
        @Nullable
        private String accessToken;
        private String accountName;
        private final SharedPreferences sharedPreferences;
        @Nullable
        private SharedPreferences postFeedScrolledPositionSharedPreferences;
        @Nullable
        private SharedPreferences postHistorySharedPreferences;
        @Nullable
        private String name;
        @Nullable
        private String query;
        @Nullable
        private String trendingSource;
        @PostType
        private final int postType;
        private final SortType sortType;
        private final PostFilter postFilter;
        @Nullable
        private String userWhere;
        private final ReadPostsListInterface readPostsList;

        // Front page
        public Factory(Executor executor, Retrofit retrofit, RedditDataRoomDatabase redditDataRoomDatabase,
                       @Nullable String accessToken, @NonNull String accountName,
                       SharedPreferences sharedPreferences, @Nullable SharedPreferences postFeedScrolledPositionSharedPreferences,
                       SharedPreferences postHistorySharedPreferences, @PostType int postType, SortType sortType,
                       PostFilter postFilter, ReadPostsListInterface readPostsList) {
            this.executor = executor;
            this.retrofit = retrofit;
            this.redditDataRoomDatabase = redditDataRoomDatabase;
            this.accessToken = accessToken;
            this.accountName = accountName;
            this.sharedPreferences = sharedPreferences;
            this.postFeedScrolledPositionSharedPreferences = postFeedScrolledPositionSharedPreferences;
            this.postHistorySharedPreferences = postHistorySharedPreferences;
            this.postType = postType;
            this.sortType = sortType;
            this.postFilter = postFilter;
            this.readPostsList = readPostsList;
        }

        // PostType.SUBREDDIT
        public Factory(Executor executor, Retrofit retrofit, RedditDataRoomDatabase redditDataRoomDatabase,
                       @Nullable String accessToken, @NonNull String accountName,
                       SharedPreferences sharedPreferences, @Nullable SharedPreferences postFeedScrolledPositionSharedPreferences,
                       SharedPreferences postHistorySharedPreferences, @Nullable String name, @PostType int postType, SortType sortType,
                       PostFilter postFilter, ReadPostsListInterface readPostsList) {
            this.executor = executor;
            this.retrofit = retrofit;
            this.redditDataRoomDatabase = redditDataRoomDatabase;
            this.accessToken = accessToken;
            this.accountName = accountName;
            this.sharedPreferences = sharedPreferences;
            this.postFeedScrolledPositionSharedPreferences = postFeedScrolledPositionSharedPreferences;
            this.postHistorySharedPreferences = postHistorySharedPreferences;
            this.name = name;
            this.postType = postType;
            this.sortType = sortType;
            this.postFilter = postFilter;
            this.readPostsList = readPostsList;
        }

        // PostType.MULTIREDDIT
        public Factory(Executor executor, Retrofit retrofit, RedditDataRoomDatabase redditDataRoomDatabase,
                       @Nullable String accessToken, @NonNull String accountName,
                       SharedPreferences sharedPreferences, @Nullable SharedPreferences postFeedScrolledPositionSharedPreferences,
                       SharedPreferences postHistorySharedPreferences, @Nullable String name, @Nullable String query, @PostType int postType, SortType sortType,
                       PostFilter postFilter, ReadPostsListInterface readPostsList) {
            this.executor = executor;
            this.retrofit = retrofit;
            this.redditDataRoomDatabase = redditDataRoomDatabase;
            this.accessToken = accessToken;
            this.accountName = accountName;
            this.sharedPreferences = sharedPreferences;
            this.postFeedScrolledPositionSharedPreferences = postFeedScrolledPositionSharedPreferences;
            this.postHistorySharedPreferences = postHistorySharedPreferences;
            this.name = name;
            this.query = query;
            this.postType = postType;
            this.sortType = sortType;
            this.postFilter = postFilter;
            this.readPostsList = readPostsList;
        }

        //User posts
        public Factory(Executor executor, Retrofit retrofit, RedditDataRoomDatabase redditDataRoomDatabase,
                       @Nullable String accessToken, @NonNull String accountName,
                       SharedPreferences sharedPreferences, @Nullable SharedPreferences postFeedScrolledPositionSharedPreferences,
                       SharedPreferences postHistorySharedPreferences, String username, @PostType int postType,
                       SortType sortType, PostFilter postFilter, String where, ReadPostsListInterface readPostsList) {
            this.executor = executor;
            this.retrofit = retrofit;
            this.redditDataRoomDatabase = redditDataRoomDatabase;
            this.accessToken = accessToken;
            this.accountName = accountName;
            this.sharedPreferences = sharedPreferences;
            this.postFeedScrolledPositionSharedPreferences = postFeedScrolledPositionSharedPreferences;
            this.postHistorySharedPreferences = postHistorySharedPreferences;
            this.name = username;
            this.postType = postType;
            this.sortType = sortType;
            this.postFilter = postFilter;
            userWhere = where;
            this.readPostsList = readPostsList;
        }

        // PostType.SEARCH
        public Factory(Executor executor, Retrofit retrofit, RedditDataRoomDatabase redditDataRoomDatabase,
                       @Nullable String accessToken, @NonNull String accountName,
                       SharedPreferences sharedPreferences, @Nullable SharedPreferences postFeedScrolledPositionSharedPreferences,
                       SharedPreferences postHistorySharedPreferences, @Nullable String name, @Nullable String query, @Nullable String trendingSource,
                       @PostType int postType, SortType sortType, PostFilter postFilter, ReadPostsListInterface readPostsList) {
            this.executor = executor;
            this.retrofit = retrofit;
            this.redditDataRoomDatabase = redditDataRoomDatabase;
            this.accessToken = accessToken;
            this.accountName = accountName;
            this.sharedPreferences = sharedPreferences;
            this.postFeedScrolledPositionSharedPreferences = postFeedScrolledPositionSharedPreferences;
            this.postHistorySharedPreferences = postHistorySharedPreferences;
            this.name = name;
            this.query = query;
            this.trendingSource = trendingSource;
            this.postType = postType;
            this.sortType = sortType;
            this.postFilter = postFilter;
            this.readPostsList = readPostsList;
        }

        //Anonymous Front Page
        public Factory(Executor executor, Retrofit retrofit, RedditDataRoomDatabase redditDataRoomDatabase,
                       SharedPreferences sharedPreferences, String concatenatedSubredditNames,
                       @PostType int postType, SortType sortType, PostFilter postFilter,
                       ReadPostsListInterface readPostsList) {
            this.executor = executor;
            this.retrofit = retrofit;
            this.redditDataRoomDatabase = redditDataRoomDatabase;
            // Anonymous browsing has no account, so this factory previously left accountName null and
            // create() forwarded that null to the view model / paging source. PostPagingSource then
            // both dereferences accountName (e.g. accountName.equals(ANONYMOUS_ACCOUNT) when merging
            // followed-user posts in an anonymous multireddit -> NPE) and, via the null-safe reversed
            // check ANONYMOUS_ACCOUNT.equals(accountName), silently skipped applying the local
            // anonymous vote/hide/save metadata. Using the real anonymous sentinel fixes both.
            this.accountName = Account.ANONYMOUS_ACCOUNT;
            this.sharedPreferences = sharedPreferences;
            this.name = concatenatedSubredditNames;
            this.postType = postType;
            this.sortType = sortType;
            this.postFilter = postFilter;
            this.readPostsList = readPostsList;
        }

        @NonNull
        @Override
        public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
            if (postType == PostType.FRONT_PAGE) {
                return (T) new PostViewModel(executor, retrofit, redditDataRoomDatabase, accessToken,
                        accountName, sharedPreferences, postFeedScrolledPositionSharedPreferences,
                        postHistorySharedPreferences, postType, sortType, postFilter, readPostsList);
            } else if (postType == PostType.SEARCH) {
                return (T) new PostViewModel(executor, retrofit, redditDataRoomDatabase, accessToken,
                        accountName, sharedPreferences, postFeedScrolledPositionSharedPreferences,
                        postHistorySharedPreferences, name, query, trendingSource, postType, sortType, postFilter, readPostsList);
            } else if (postType == PostType.SUBREDDIT || postType == PostType.DUPLICATES) {
                return (T) new PostViewModel(executor, retrofit, redditDataRoomDatabase, accessToken,
                        accountName, sharedPreferences, postFeedScrolledPositionSharedPreferences,
                        postHistorySharedPreferences, name, postType, sortType, postFilter, readPostsList);
            } else if (postType == PostType.MULTIREDDIT) {
                return (T) new PostViewModel(executor, retrofit, redditDataRoomDatabase, accessToken,
                        accountName, sharedPreferences, postFeedScrolledPositionSharedPreferences,
                        postHistorySharedPreferences, name, query, postType, sortType, postFilter, readPostsList);
            } else if (postType == PostType.ANONYMOUS_FRONT_PAGE || postType == PostType.ANONYMOUS_MULTIREDDIT) {
                return (T) new PostViewModel(executor, retrofit, redditDataRoomDatabase, accessToken,
                        accountName, sharedPreferences, postFeedScrolledPositionSharedPreferences,
                        postHistorySharedPreferences, name, postType, sortType, postFilter, readPostsList);
            } else {
                return (T) new PostViewModel(executor, retrofit, redditDataRoomDatabase, accessToken,
                        accountName, sharedPreferences, postFeedScrolledPositionSharedPreferences,
                        postHistorySharedPreferences, name, postType, sortType, postFilter, userWhere, readPostsList);
            }
        }
    }

    private static class SortTypeAndPostFilterLiveData extends MediatorLiveData<Pair<PostFilter, SortType>> {
        public SortTypeAndPostFilterLiveData(LiveData<SortType> sortTypeLiveData, LiveData<PostFilter> postFilterLiveData) {
            addSource(sortTypeLiveData, sortType -> setValue(Pair.create(postFilterLiveData.getValue(), sortType)));
            addSource(postFilterLiveData, postFilter -> setValue(Pair.create(postFilter, sortTypeLiveData.getValue())));
        }
    }

    public void approvePost(@NonNull Post post, int position) {
        Map<String, String> params = new HashMap<>();
        params.put(APIUtils.ID_KEY, post.getFullName());
        retrofit.create(RedditAPI.class).approveThing(APIUtils.getOAuthHeader(accessToken), params).enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<String> call, @NonNull Response<String> response) {
                if (response.isSuccessful()) {
                    post.setApproved(true);
                    post.setApprovedBy(accountName);
                    post.setApprovedAtUTC(System.currentTimeMillis());
                    post.setRemoved(false, false);
                    moderationEventLiveData.postValue(new PostModerationEvent.Approved(post, position));
                } else {
                    moderationEventLiveData.postValue(new PostModerationEvent.ApproveFailed(post, position));
                }
            }

            @Override
            public void onFailure(@NonNull Call<String> call, @NonNull Throwable throwable) {
                moderationEventLiveData.postValue(new PostModerationEvent.ApproveFailed(post, position));
            }
        });
    }

    public void removePost(@NonNull Post post, int position, boolean isSpam) {
        Map<String, String> params = new HashMap<>();
        params.put(APIUtils.ID_KEY, post.getFullName());
        params.put(APIUtils.SPAM_KEY, Boolean.toString(isSpam));
        retrofit.create(RedditAPI.class).removeThing(APIUtils.getOAuthHeader(accessToken), params).enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<String> call, @NonNull Response<String> response) {
                if (response.isSuccessful()) {
                    post.setApproved(false);
                    post.setApprovedBy(null);
                    post.setApprovedAtUTC(0);
                    post.setRemoved(true, isSpam);
                    moderationEventLiveData.postValue(isSpam ? new PostModerationEvent.MarkedAsSpam(post, position): new PostModerationEvent.Removed(post, position));
                } else {
                    moderationEventLiveData.postValue(isSpam ? new PostModerationEvent.MarkAsSpamFailed(post, position) : new PostModerationEvent.RemoveFailed(post, position));
                }
            }

            @Override
            public void onFailure(@NonNull Call<String> call, @NonNull Throwable throwable) {
                moderationEventLiveData.postValue(isSpam ? new PostModerationEvent.MarkAsSpamFailed(post, position) : new PostModerationEvent.RemoveFailed(post, position));
            }
        });
    }

    public void toggleSticky(@NonNull Post post, int position) {
        Map<String, String> params = new HashMap<>();
        params.put(APIUtils.ID_KEY, post.getFullName());
        params.put(APIUtils.STATE_KEY, Boolean.toString(!post.isStickied()));
        params.put(APIUtils.API_TYPE_KEY, APIUtils.API_TYPE_JSON);
        retrofit.create(RedditAPI.class).toggleStickyPost(APIUtils.getOAuthHeader(accessToken), params).enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<String> call, @NonNull Response<String> response) {
                if (response.isSuccessful()) {
                    post.setIsStickied(!post.isStickied());
                    moderationEventLiveData.postValue(post.isStickied() ? new PostModerationEvent.SetStickyPost(post, position): new PostModerationEvent.UnsetStickyPost(post, position));
                } else {
                    moderationEventLiveData.postValue(post.isStickied() ? new PostModerationEvent.UnsetStickyPostFailed(post, position) : new PostModerationEvent.SetStickyPostFailed(post, position));
                }
            }

            @Override
            public void onFailure(@NonNull Call<String> call, @NonNull Throwable throwable) {
                moderationEventLiveData.postValue(post.isStickied() ? new PostModerationEvent.UnsetStickyPostFailed(post, position) : new PostModerationEvent.SetStickyPostFailed(post, position));
            }
        });
    }

    public void toggleLock(@NonNull Post post, int position) {
        Map<String, String> params = new HashMap<>();
        params.put(APIUtils.ID_KEY, post.getFullName());
        Call<String> call = post.isLocked() ? retrofit.create(RedditAPI.class).unLockThing(APIUtils.getOAuthHeader(accessToken), params) : retrofit.create(RedditAPI.class).lockThing(APIUtils.getOAuthHeader(accessToken), params);
        call.enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<String> call, @NonNull Response<String> response) {
                if (response.isSuccessful()) {
                    post.setIsLocked(!post.isLocked());
                    moderationEventLiveData.postValue(post.isLocked() ? new PostModerationEvent.Locked(post, position): new PostModerationEvent.Unlocked(post, position));
                } else {
                    moderationEventLiveData.postValue(post.isLocked() ? new PostModerationEvent.UnlockFailed(post, position) : new PostModerationEvent.LockFailed(post, position));
                }
            }

            @Override
            public void onFailure(@NonNull Call<String> call, @NonNull Throwable throwable) {
                moderationEventLiveData.postValue(post.isLocked() ? new PostModerationEvent.UnlockFailed(post, position) : new PostModerationEvent.LockFailed(post, position));
            }
        });
    }

    public void toggleNSFW(@NonNull Post post, int position) {
        Map<String, String> params = new HashMap<>();
        params.put(APIUtils.ID_KEY, post.getFullName());
        Call<String> call = post.isNSFW() ? retrofit.create(RedditAPI.class).unmarkNSFW(APIUtils.getOAuthHeader(accessToken), params) : retrofit.create(RedditAPI.class).markNSFW(APIUtils.getOAuthHeader(accessToken), params);
        call.enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<String> call, @NonNull Response<String> response) {
                if (response.isSuccessful()) {
                    post.setNSFW(!post.isNSFW());
                    moderationEventLiveData.postValue(post.isNSFW() ? new PostModerationEvent.MarkedNSFW(post, position): new PostModerationEvent.UnmarkedNSFW(post, position));
                } else {
                    moderationEventLiveData.postValue(post.isNSFW() ? new PostModerationEvent.UnmarkNSFWFailed(post, position) : new PostModerationEvent.MarkNSFWFailed(post, position));
                }
            }

            @Override
            public void onFailure(@NonNull Call<String> call, @NonNull Throwable throwable) {
                moderationEventLiveData.postValue(post.isNSFW() ? new PostModerationEvent.UnmarkNSFWFailed(post, position) : new PostModerationEvent.MarkNSFWFailed(post, position));
            }
        });
    }

    public void toggleSpoiler(@NonNull Post post, int position) {
        Map<String, String> params = new HashMap<>();
        params.put(APIUtils.ID_KEY, post.getFullName());
        Call<String> call = post.isSpoiler() ? retrofit.create(RedditAPI.class).unmarkSpoiler(APIUtils.getOAuthHeader(accessToken), params) : retrofit.create(RedditAPI.class).markSpoiler(APIUtils.getOAuthHeader(accessToken), params);
        call.enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<String> call, @NonNull Response<String> response) {
                if (response.isSuccessful()) {
                    post.setSpoiler(!post.isSpoiler());
                    moderationEventLiveData.postValue(post.isSpoiler() ? new PostModerationEvent.MarkedSpoiler(post, position): new PostModerationEvent.UnmarkedSpoiler(post, position));
                } else {
                    moderationEventLiveData.postValue(post.isSpoiler() ? new PostModerationEvent.UnmarkSpoilerFailed(post, position) : new PostModerationEvent.MarkSpoilerFailed(post, position));
                }
            }

            @Override
            public void onFailure(@NonNull Call<String> call, @NonNull Throwable throwable) {
                moderationEventLiveData.postValue(post.isSpoiler() ? new PostModerationEvent.UnmarkSpoilerFailed(post, position) : new PostModerationEvent.MarkSpoilerFailed(post, position));
            }
        });
    }

    public void toggleMod(@NonNull Post post, int position) {
        Map<String, String> params = new HashMap<>();
        params.put(APIUtils.ID_KEY, post.getFullName());
        params.put(APIUtils.HOW_KEY, post.isModerator() ? APIUtils.HOW_NO : APIUtils.HOW_YES);
        retrofit.create(RedditAPI.class).toggleDistinguishedThing(APIUtils.getOAuthHeader(accessToken), params).enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<String> call, @NonNull Response<String> response) {
                if (response.isSuccessful()) {
                    post.setIsModerator(!post.isModerator());
                    moderationEventLiveData.postValue(post.isModerator() ? new PostModerationEvent.DistinguishedAsMod(post, position): new PostModerationEvent.UndistinguishedAsMod(post, position));
                } else {
                    moderationEventLiveData.postValue(post.isModerator() ? new PostModerationEvent.UndistinguishAsModFailed(post, position) : new PostModerationEvent.DistinguishAsModFailed(post, position));
                }
            }

            @Override
            public void onFailure(@NonNull Call<String> call, @NonNull Throwable throwable) {
                moderationEventLiveData.postValue(post.isModerator() ? new PostModerationEvent.UndistinguishAsModFailed(post, position) : new PostModerationEvent.DistinguishAsModFailed(post, position));
            }
        });
    }

    public void toggleNotification(@NonNull Post post, int position) {
        Map<String, String> params = new HashMap<>();
        params.put(APIUtils.ID_KEY, post.getFullName());
        params.put(APIUtils.STATE_KEY, String.valueOf(!post.isSendReplies()));
        retrofit.create(RedditAPI.class).toggleRepliesNotification(APIUtils.getOAuthHeader(accessToken), params).enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<String> call, @NonNull Response<String> response) {
                if (response.isSuccessful()) {
                    post.setSendReplies(!post.isSendReplies());
                    moderationEventLiveData.postValue(post.isSendReplies() ? new PostModerationEvent.SetReceiveNotification(post, position): new PostModerationEvent.UnsetReceiveNotification(post, position));
                } else {
                    moderationEventLiveData.postValue(post.isSendReplies() ? new PostModerationEvent.UnsetReceiveNotificationFailed(post, position) : new PostModerationEvent.SetReceiveNotificationFailed(post, position));
                }
            }

            @Override
            public void onFailure(@NonNull Call<String> call, @NonNull Throwable throwable) {
                moderationEventLiveData.postValue(post.isSendReplies() ? new PostModerationEvent.UnsetReceiveNotificationFailed(post, position) : new PostModerationEvent.SetReceiveNotificationFailed(post, position));
            }
        });
    }
}
