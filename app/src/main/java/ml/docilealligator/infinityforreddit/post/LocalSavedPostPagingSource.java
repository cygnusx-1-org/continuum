package ml.docilealligator.infinityforreddit.post;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.paging.ListenableFuturePagingSource;
import androidx.paging.PagingState;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.Executor;
import ml.docilealligator.infinityforreddit.RedditDataRoomDatabase;
import ml.docilealligator.infinityforreddit.apis.RedditAPI;
import ml.docilealligator.infinityforreddit.localsaved.LocalSavedThing;
import ml.docilealligator.infinityforreddit.postfilter.PostFilter;
import ml.docilealligator.infinityforreddit.readpost.NullReadPostsList;
import ml.docilealligator.infinityforreddit.utils.APIUtils;
import ml.docilealligator.infinityforreddit.utils.SavedSearchCache;
import ml.docilealligator.infinityforreddit.utils.SavedThingSearchFilter;
import retrofit2.Call;
import retrofit2.HttpException;
import retrofit2.Response;
import retrofit2.Retrofit;

/**
 * Paging source for the Local Saved "posts" tab. Reads promoted post fullnames (t3_) from the
 * local_saved table and hydrates them live via /api/info. Mirrors
 * {@link HistoryPostPagingSource} but is sourced from the local_saved table (logged-in only).
 */
public class LocalSavedPostPagingSource extends ListenableFuturePagingSource<String, Post> {
    private final Retrofit oauthRetrofit;
    private final Executor executor;
    private final RedditDataRoomDatabase redditDataRoomDatabase;
    private final String accessToken;
    private final String accountName;
    private final PostFilter postFilter;
    @Nullable
    private final String searchQuery;
    // Full unfiltered local_saved posts listing, shared with the ViewModel so a refined query filters
    // it in memory instead of re-hydrating every post again (null when not searching).
    @Nullable
    private final SavedSearchCache<Post> savedSearchCache;

    // /api/info accepts up to 100 fullnames per call; the load-all search path hydrates in batches
    // of this size.
    private static final int INFO_BATCH_SIZE = 100;

    public LocalSavedPostPagingSource(Retrofit oauthRetrofit, Executor executor,
                                      RedditDataRoomDatabase redditDataRoomDatabase,
                                      @Nullable String accessToken, @NonNull String accountName,
                                      PostFilter postFilter, @Nullable String searchQuery,
                                      @Nullable SavedSearchCache<Post> savedSearchCache) {
        this.oauthRetrofit = oauthRetrofit;
        this.executor = executor;
        this.redditDataRoomDatabase = redditDataRoomDatabase;
        this.accessToken = accessToken;
        this.accountName = accountName;
        this.postFilter = postFilter;
        this.searchQuery = searchQuery;
        this.savedSearchCache = savedSearchCache;
    }

    private boolean isSearchActive() {
        return searchQuery != null && !searchQuery.trim().isEmpty();
    }

    @Nullable
    @Override
    public String getRefreshKey(@NonNull PagingState<String, Post> pagingState) {
        return null;
    }

    @NonNull
    @Override
    public ListenableFuture<LoadResult<String, Post>> loadFuture(@NonNull LoadParams<String> loadParams) {
        if (isSearchActive()) {
            // Load the whole local_saved posts list, hydrate, filter, and return every match at once
            // (with the normal loading indicator) rather than trickling in as pages arrive.
            ListenableFuture<LoadResult<String, Post>> searchFuture =
                    Futures.submit(this::loadAllFiltered, executor);
            ListenableFuture<LoadResult<String, Post>> partial =
                    Futures.catching(searchFuture, HttpException.class, LoadResult.Error::new, executor);
            return Futures.catching(partial, IOException.class, LoadResult.Error::new, executor);
        }

        Long before = loadParams.getKey() != null ? Long.parseLong(loadParams.getKey()) : null;
        ListenableFuture<List<LocalSavedThing>> promoted =
                redditDataRoomDatabase.localSavedThingDao().getPromotedPosts(accountName, before);

        ListenableFuture<LoadResult<String, Post>> pageFuture =
                Futures.transform(promoted, this::transformData, executor);

        ListenableFuture<LoadResult<String, Post>> partialLoadResultFuture =
                Futures.catching(pageFuture, HttpException.class, LoadResult.Error::new, executor);

        return Futures.catching(partialLoadResultFuture, IOException.class, LoadResult.Error::new, executor);
    }

    private LoadResult<String, Post> loadAllFiltered() throws IOException {
        // A refined query reuses the listing already hydrated for this search session.
        List<Post> cached = savedSearchCache != null ? savedSearchCache.snapshot() : null;
        if (cached != null) {
            return filterToPage(cached);
        }

        List<LocalSavedThing> promotedPosts =
                redditDataRoomDatabase.localSavedThingDao().getAllPromotedPostsSync(accountName);
        List<Post> allPosts = new ArrayList<>();

        for (int start = 0; start < promotedPosts.size(); start += INFO_BATCH_SIZE) {
            int end = Math.min(start + INFO_BATCH_SIZE, promotedPosts.size());
            StringBuilder ids = new StringBuilder();
            for (int i = start; i < end; i++) {
                ids.append(promotedPosts.get(i).getFullName()).append(",");
            }
            if (ids.length() > 0) {
                ids.deleteCharAt(ids.length() - 1);
            }

            Response<String> response = oauthRetrofit.create(RedditAPI.class)
                    .getInfoOauth(ids.toString(), APIUtils.getOAuthHeader(accessToken)).execute();
            if (!response.isSuccessful()) {
                return new LoadResult.Error<>(new PostPagingSource.PostPagingSourceError(response.code(), "Response failed"));
            }
            LinkedHashSet<Post> newPosts = ParsePost.parsePostsSync(response.body(), -1, postFilter,
                    NullReadPostsList.getInstance());
            if (newPosts == null) {
                return new LoadResult.Error<>(new Exception("Error parsing posts"));
            }
            for (Post p : newPosts) {
                p.setSaved(true);
                allPosts.add(p);
            }
        }

        // Cache the full unfiltered hydrate so refining the query filters it in memory.
        if (savedSearchCache != null) {
            savedSearchCache.set(allPosts);
        }
        return filterToPage(allPosts);
    }

    private LoadResult<String, Post> filterToPage(List<Post> unfiltered) {
        List<Post> matches = new ArrayList<>();
        for (Post p : unfiltered) {
            if (SavedThingSearchFilter.matches(p, searchQuery)) {
                matches.add(p);
            }
        }
        return new LoadResult.Page<>(matches, null, null);
    }

    private LoadResult<String, Post> transformData(List<LocalSavedThing> promotedPosts) {
        StringBuilder ids = new StringBuilder();
        long lastItem = 0;
        for (LocalSavedThing t : promotedPosts) {
            // Stored value is already a full name (t3_...).
            ids.append(t.getFullName()).append(",");
            lastItem = t.getTime();
        }
        if (ids.length() > 0) {
            ids.deleteCharAt(ids.length() - 1);
        }

        if (ids.length() == 0) {
            return new LoadResult.Page<>(new ArrayList<>(), null, null);
        }

        Call<String> call = oauthRetrofit.create(RedditAPI.class)
                .getInfoOauth(ids.toString(), APIUtils.getOAuthHeader(accessToken));

        try {
            Response<String> response = call.execute();
            if (response.isSuccessful()) {
                LinkedHashSet<Post> newPosts = ParsePost.parsePostsSync(response.body(), -1, postFilter,
                        NullReadPostsList.getInstance());
                if (newPosts == null) {
                    return new LoadResult.Error<>(new Exception("Error parsing posts"));
                }
                for (Post p : newPosts) {
                    p.setSaved(true);
                }
                if (promotedPosts.size() < 25) {
                    return new LoadResult.Page<>(new ArrayList<>(newPosts), null, null);
                }
                return new LoadResult.Page<>(new ArrayList<>(newPosts), null, Long.toString(lastItem));
            } else {
                return new LoadResult.Error<>(new PostPagingSource.PostPagingSourceError(response.code(), "Response failed"));
            }
        } catch (IOException e) {
            e.printStackTrace();
            return new LoadResult.Error<>(new Exception("Response failed"));
        }
    }
}
