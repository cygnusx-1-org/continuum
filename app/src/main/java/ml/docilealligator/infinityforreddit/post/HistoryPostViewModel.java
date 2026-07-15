package ml.docilealligator.infinityforreddit.post;

import android.content.SharedPreferences;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelKt;
import androidx.lifecycle.ViewModelProvider;
import androidx.paging.Pager;
import androidx.paging.PagingConfig;
import androidx.paging.PagingData;
import androidx.paging.PagingLiveData;
import androidx.paging.PagingSource;
import java.util.concurrent.Executor;
import ml.docilealligator.infinityforreddit.RedditDataRoomDatabase;
import ml.docilealligator.infinityforreddit.postfilter.PostFilter;
import ml.docilealligator.infinityforreddit.readpost.ReadPostType;
import ml.docilealligator.infinityforreddit.utils.SavedSearchCache;
import retrofit2.Retrofit;

public class HistoryPostViewModel extends ViewModel {
    private final Executor executor;
    private final Retrofit retrofit;
    private final RedditDataRoomDatabase redditDataRoomDatabase;
    @Nullable
    private final String accessToken;
    private final String accountName;
    private final SharedPreferences sharedPreferences;
    private final int readPostType;
    private final PostFilter postFilter;

    private final LiveData<PagingData<Post>> posts;

    private final MutableLiveData<PostFilter> postFilterLiveData;
    // Saved-screen search for the Local Saved posts tab. When non-empty the paging source loads the
    // whole local_saved list, filters, and returns every match at once. Read on the paging executor
    // when a source is built, written from the main thread, so it is volatile.
    @Nullable
    private volatile String searchQuery;
    // Full unfiltered local_saved posts listing shared with the current source so refining the query
    // filters in memory instead of re-hydrating. Invalidated on filter change, refresh, search cleared.
    private final SavedSearchCache<Post> savedSearchCache = new SavedSearchCache<>();
    // Most recently created source, refreshed in place via invalidate() on a query change instead of
    // reposting a LiveData (which would tear down and rebuild the pager mid-load).
    @Nullable
    private volatile PagingSource<String, Post> pagingSource;

    public HistoryPostViewModel(Executor executor, Retrofit retrofit, RedditDataRoomDatabase redditDataRoomDatabase,
                                @Nullable String accessToken, @NonNull String accountName, SharedPreferences sharedPreferences,
                                int readPostType, PostFilter postFilter) {
        this.executor = executor;
        this.retrofit = retrofit;
        this.redditDataRoomDatabase = redditDataRoomDatabase;
        this.accessToken = accessToken;
        this.accountName = accountName;
        this.sharedPreferences = sharedPreferences;
        this.readPostType = readPostType;
        this.postFilter = postFilter;

        postFilterLiveData = new MutableLiveData<>(postFilter);

        Pager<String, Post> pager = new Pager<>(new PagingConfig(25, 4, false, 10), this::returnPagingSource);

        posts = Transformations.switchMap(postFilterLiveData, postFilterValue -> PagingLiveData.cachedIn(PagingLiveData.getLiveData(pager), ViewModelKt.getViewModelScope(this)));
    }

    public LiveData<PagingData<Post>> getPosts() {
        return posts;
    }

    // Refreshes the current source in place (invalidate() rebuilds it from the factory with the new
    // query) rather than reposting a LiveData, so the pager is not torn down and rebuilt while a slow
    // load-all is still collecting. A non-empty query loads the whole local_saved list and returns
    // every match at once; an empty query restores the normal paginated listing and drops the cache.
    public void searchSaved(String query) {
        String normalized = query == null ? "" : query;
        if (!normalized.equals(searchQuery == null ? "" : searchQuery)) {
            searchQuery = normalized;
            if (normalized.isEmpty()) {
                savedSearchCache.invalidate();
            }
            PagingSource<String, Post> currentSource = pagingSource;
            if (currentSource != null) {
                currentSource.invalidate();
            }
        }
    }

    public PagingSource<String, Post> returnPagingSource() {
        PagingSource<String, Post> source;
        if (readPostType == ReadPostType.LOCAL_SAVED_POSTS) {
            source = new LocalSavedPostPagingSource(retrofit, executor, redditDataRoomDatabase, accessToken,
                    accountName, postFilter, searchQuery, savedSearchCache);
        } else {
            source = new HistoryPostPagingSource(retrofit, executor, redditDataRoomDatabase, accessToken, accountName,
                    sharedPreferences, accountName, readPostType, postFilter);
        }
        pagingSource = source;
        return source;
    }

    // Called by the Saved screen's pull-to-refresh so a refresh while a search is active refetches
    // the listing instead of serving the cached copy.
    public void invalidateSavedSearchCache() {
        savedSearchCache.invalidate();
    }

    public void changePostFilter(PostFilter postFilter) {
        // A different filter changes which items pass, so the cached search copy is stale.
        savedSearchCache.invalidate();
        postFilterLiveData.postValue(postFilter);
    }

    public static class Factory extends ViewModelProvider.NewInstanceFactory {
        private final Executor executor;
        private final Retrofit retrofit;
        private final RedditDataRoomDatabase redditDataRoomDatabase;
        @Nullable
    private final String accessToken;
        private final String accountName;
        private final SharedPreferences sharedPreferences;
        private final int readPostType;
        private final PostFilter postFilter;

        public Factory(Executor executor, Retrofit retrofit, RedditDataRoomDatabase redditDataRoomDatabase,
                       @Nullable String accessToken, @NonNull String accountName, SharedPreferences sharedPreferences, int readPostType,
                       PostFilter postFilter) {
            this.executor = executor;
            this.retrofit = retrofit;
            this.redditDataRoomDatabase = redditDataRoomDatabase;
            this.accessToken = accessToken;
            this.accountName = accountName;
            this.sharedPreferences = sharedPreferences;
            this.readPostType = readPostType;
            this.postFilter = postFilter;
        }

        @NonNull
        @Override
        public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
            return (T) new HistoryPostViewModel(executor, retrofit, redditDataRoomDatabase, accessToken, accountName, sharedPreferences,
                    readPostType, postFilter);
        }
    }
}
