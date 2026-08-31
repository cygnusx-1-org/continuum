package ml.docilealligator.infinityforreddit.post;

import android.content.SharedPreferences;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.paging.ListenableFuturePagingSource;
import androidx.paging.PagingState;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.gson.Gson;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;
import ml.docilealligator.infinityforreddit.Constants;
import ml.docilealligator.infinityforreddit.RedditDataRoomDatabase;
import ml.docilealligator.infinityforreddit.RedditError;
import ml.docilealligator.infinityforreddit.account.Account;
import ml.docilealligator.infinityforreddit.apis.RedditAPI;
import ml.docilealligator.infinityforreddit.postfilter.PostFilter;
import ml.docilealligator.infinityforreddit.readpost.ReadPost;
import ml.docilealligator.infinityforreddit.readpost.ReadPostType;
import ml.docilealligator.infinityforreddit.readpost.ReadPostsListInterface;
import ml.docilealligator.infinityforreddit.thing.SortType;
import ml.docilealligator.infinityforreddit.utils.APIUtils;
import ml.docilealligator.infinityforreddit.utils.JSONUtils;
import ml.docilealligator.infinityforreddit.utils.SavedPostCache;
import ml.docilealligator.infinityforreddit.utils.SavedSearchCache;
import ml.docilealligator.infinityforreddit.utils.SavedThingSearchFilter;
import ml.docilealligator.infinityforreddit.utils.SharedPreferencesUtils;
import okhttp3.ResponseBody;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import retrofit2.HttpException;
import retrofit2.Response;
import retrofit2.Retrofit;

public class PostPagingSource extends ListenableFuturePagingSource<String, Post> {
    /*public static final int TYPE_FRONT_PAGE = 0;
    public static final int TYPE_SUBREDDIT = 1;
    public static final int TYPE_USER = 2;
    public static final int TYPE_SEARCH = 3;
    public static final int TYPE_MULTI_REDDIT = 4;
    public static final int TYPE_ANONYMOUS_FRONT_PAGE = 5;
    public static final int TYPE_ANONYMOUS_MULTIREDDIT = 6;*/

    public static final String USER_WHERE_SUBMITTED = "submitted";
    public static final String USER_WHERE_UPVOTED = "upvoted";
    public static final String USER_WHERE_DOWNVOTED = "downvoted";
    public static final String USER_WHERE_HIDDEN = "hidden";
    public static final String USER_WHERE_SAVED = "saved";

    private static final int HTTP_INTERNAL_SERVER_ERROR = 500;
    // Reddit caps a user's saved listing at ~1000 items (100/page), so this bounds the load-all
    // saved search while comfortably covering the whole listing.
    private static final int SAVED_SEARCH_MAX_PAGES = 15;

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
    private String subredditOrUserName;
    @Nullable
    private String query;
    @Nullable
    private String trendingSource;
    @PostType
    private final int postType;
    private final SortType sortType;
    private final PostFilter postFilter;
    private final ReadPostsListInterface readPostsList;
    @Nullable
    private String userWhere;
    @Nullable
    private String searchQuery;
    // Full unfiltered saved listing, shared with the ViewModel so a refined query filters the
    // already-loaded items in memory instead of re-walking the whole listing (null when not the
    // saved-search path).
    @Nullable
    private SavedSearchCache<Post> savedSearchCache;
    // Accumulated raw t3 listing children for the persistent SavedPostCache (saved feed only), built
    // as pages are walked so a later Saved tab open can be served from disk with no network call.
    private final JSONArray savedCacheChildren = new JSONArray();
    private final Set<String> savedCacheSeenFullnames = new HashSet<>();
    @Nullable
    private String multiRedditPath;
    private final List<Post> posts;
    private final Set<String> existingPostIds = new HashSet<>();
    @Nullable
    private String previousLastItem;
    @Nullable
    private List<String> multiRedditUsernames;
    private boolean multiRedditUsernamesFetched = false;
    @Nullable
    private String subredditOnlyName;

    /**
     * Whether the feed reading this source is showing media posts only, so that a page carrying no
     * media is a page it will display nothing from (issue #377).
     *
     * <p>Only pagination consults this. The posts themselves are never dropped here -- the feed
     * filters them on the way out of the view model, which is what lets the setting be turned off
     * without refetching -- so all this changes is when a load is allowed to stop.
     *
     * <p>Written from the main thread when the setting or the post layout changes, read on the
     * paging executor while a load runs, hence volatile. A change does not invalidate the source:
     * pages already loaded stay, and only the next load sees the new value.
     */
    private volatile boolean mediaOnly;

    /**
     * How many further pages one load may pull while every page so far has come back with nothing
     * the feed can show, and how large those extra pages are.
     *
     * <p>The cap is what stops a listing with no media in it at all (r/AskReddit, say) from walking
     * the whole subreddit. The hop size is deliberately not {@code loadParams.getLoadSize()}: an
     * initial load asks for 10 posts, and giving up after 60 would call a subreddit media-less on
     * far too little evidence.
     */
    private static final int MAX_BARREN_PAGE_HOPS = 5;
    private static final int BARREN_PAGE_HOP_LOAD_SIZE = 100;

    PostPagingSource(Executor executor, Retrofit retrofit, RedditDataRoomDatabase redditDataRoomDatabase,
                     @Nullable String accessToken, @NonNull String accountName, SharedPreferences sharedPreferences,
                     @Nullable SharedPreferences postFeedScrolledPositionSharedPreferences, @PostType int postType,
                     SortType sortType, PostFilter postFilter, ReadPostsListInterface readPostsList) {
        this.executor = executor;
        this.retrofit = retrofit;
        this.redditDataRoomDatabase = redditDataRoomDatabase;
        this.accessToken = accessToken;
        this.accountName = accountName;
        this.sharedPreferences = sharedPreferences;
        this.postFeedScrolledPositionSharedPreferences = postFeedScrolledPositionSharedPreferences;
        this.postType = postType;
        this.sortType = sortType == null ? new SortType(SortType.Type.BEST) : sortType;
        this.postFilter = postFilter;
        this.readPostsList = readPostsList;
        posts = new ArrayList<>();
    }

    // PostPagingSource.TYPE_SUBREDDIT || PostPagingSource.TYPE_ANONYMOUS_FRONT_PAGE || PostPagingSource.TYPE_ANONYMOUS_MULTIREDDIT:
    PostPagingSource(Executor executor, Retrofit retrofit, RedditDataRoomDatabase redditDataRoomDatabase,
                     @Nullable String accessToken, @NonNull String accountName,
                     SharedPreferences sharedPreferences, @Nullable SharedPreferences postFeedScrolledPositionSharedPreferences,
                     @Nullable String name, @PostType int postType, SortType sortType, PostFilter postFilter,
                     ReadPostsListInterface readPostsList) {
        this.executor = executor;
        this.retrofit = retrofit;
        this.redditDataRoomDatabase = redditDataRoomDatabase;
        this.accessToken = accessToken;
        this.accountName = accountName;
        this.sharedPreferences = sharedPreferences;
        this.postFeedScrolledPositionSharedPreferences = postFeedScrolledPositionSharedPreferences;
        this.subredditOrUserName = name;
        if (subredditOrUserName == null) {
            subredditOrUserName = "popular";
        }
        this.postType = postType;
        if (sortType == null) {
            if (Constants.isFirehoseSubreddit(name)) {
                this.sortType = new SortType(SortType.Type.HOT);
            } else {
                this.sortType = new SortType(SortType.Type.BEST);
            }
        } else {
            this.sortType = sortType;
        }
        this.postFilter = postFilter;
        this.readPostsList = readPostsList;
        posts = new ArrayList<>();
    }

    // PostPagingSource.TYPE_MULTI_REDDIT
    PostPagingSource(Executor executor, Retrofit retrofit, RedditDataRoomDatabase redditDataRoomDatabase,
                     @Nullable String accessToken, @NonNull String accountName,
                     SharedPreferences sharedPreferences, @Nullable SharedPreferences postFeedScrolledPositionSharedPreferences,
                     @Nullable String path, @Nullable String query, @PostType int postType, SortType sortType, PostFilter postFilter,
                     ReadPostsListInterface readPostsList) {
        this.executor = executor;
        this.retrofit = retrofit;
        this.redditDataRoomDatabase = redditDataRoomDatabase;
        this.accessToken = accessToken;
        this.accountName = accountName;
        this.sharedPreferences = sharedPreferences;
        this.postFeedScrolledPositionSharedPreferences = postFeedScrolledPositionSharedPreferences;
        if (path != null && path.endsWith("/")) {
            multiRedditPath = path.substring(0, path.length() - 1);
        } else {
            multiRedditPath = path == null ? "" : path;
        }
        this.query = query;
        this.postType = postType;
        if (sortType == null) {
            this.sortType = new SortType(SortType.Type.HOT);
        } else {
            this.sortType = sortType;
        }
        this.postFilter = postFilter;
        this.readPostsList = readPostsList;
        posts = new ArrayList<>();
    }

    PostPagingSource(Executor executor, Retrofit retrofit, RedditDataRoomDatabase redditDataRoomDatabase,
                     @Nullable String accessToken, @NonNull String accountName,
                     SharedPreferences sharedPreferences, @Nullable SharedPreferences postFeedScrolledPositionSharedPreferences,
                     @Nullable String subredditOrUserName, @PostType int postType, SortType sortType, PostFilter postFilter,
                     @Nullable String where, @Nullable String searchQuery, @Nullable SavedSearchCache<Post> savedSearchCache,
                     ReadPostsListInterface readPostsList) {
        this.executor = executor;
        this.retrofit = retrofit;
        this.redditDataRoomDatabase = redditDataRoomDatabase;
        this.accessToken = accessToken;
        this.accountName = accountName;
        this.sharedPreferences = sharedPreferences;
        this.postFeedScrolledPositionSharedPreferences = postFeedScrolledPositionSharedPreferences;
        this.subredditOrUserName = subredditOrUserName;
        this.postType = postType;
        this.sortType = sortType == null ? new SortType(SortType.Type.NEW) : sortType;
        this.postFilter = postFilter;
        userWhere = where;
        this.searchQuery = searchQuery;
        this.savedSearchCache = savedSearchCache;
        this.readPostsList = readPostsList;
        posts = new ArrayList<>();
    }

    PostPagingSource(Executor executor, Retrofit retrofit, RedditDataRoomDatabase redditDataRoomDatabase,
                     @Nullable String accessToken, @NonNull String accountName,
                     SharedPreferences sharedPreferences, @Nullable SharedPreferences postFeedScrolledPositionSharedPreferences,
                     @Nullable String subredditOrUserName, @Nullable String query, @Nullable String trendingSource, @PostType int postType,
                     SortType sortType, PostFilter postFilter, ReadPostsListInterface readPostsList) {
        this.executor = executor;
        this.retrofit = retrofit;
        this.redditDataRoomDatabase = redditDataRoomDatabase;
        this.accessToken = accessToken;
        this.accountName = accountName;
        this.sharedPreferences = sharedPreferences;
        this.postFeedScrolledPositionSharedPreferences = postFeedScrolledPositionSharedPreferences;
        this.subredditOrUserName = subredditOrUserName;
        this.query = query;
        this.trendingSource = trendingSource;
        this.postType = postType;
        this.sortType = sortType == null ? new SortType(SortType.Type.RELEVANCE) : sortType;
        this.postFilter = postFilter;
        this.readPostsList = readPostsList;
        posts = new ArrayList<>();
    }

    public static class PostPagingSourceError extends Exception {
        public final int code;
        @Nullable
        public final String message;

        PostPagingSourceError(int code, @Nullable String message) {
            super(message);
            this.code = code;
            this.message = message;
        }
    }

    @Nullable
    @Override
    public String getRefreshKey(@NonNull PagingState<String, Post> pagingState) {
        return null;
    }

    public void setMediaOnly(boolean mediaOnly) {
        this.mediaOnly = mediaOnly;
    }

    /**
     * Load a page the feed can actually show something from.
     *
     * <p>Paging appends more data when the presenter reaches the end of what is on screen, and the
     * presenter asks by way of the item it is binding. A page every item of which is filtered out
     * downstream therefore ends the feed for good: nothing is presented, so nothing is bound, so no
     * hint is raised and no further page is ever requested. What the user sees is a blank feed with
     * no posts and no "no posts found" -- the latter is gated on end-of-pagination, which never
     * arrives either.
     *
     * <p>Three things empty a page out from under the feed: a post filter that excludes everything
     * on it, "Media Posts Only" on a page of text and link posts, and Reddit repeating a cursor so
     * that {@link #transformData} discards the lot as duplicates. All three land here as a page with
     * nothing to present and a next key still in hand, and the answer to all three is the same one
     * {@link #loadUserPostsWithKey} already applies on the user listing: keep following the next key
     * until a page yields something, and hand back everything gathered on the way as one page.
     *
     * <p>Nothing is discarded while hopping. The non-media and filtered-out posts are still returned
     * and still cached, so turning the setting back off shows them without a refetch.
     *
     * <p>If {@link #MAX_BARREN_PAGE_HOPS} pages go by with nothing to show, the page comes back with
     * a null next key. That is a deliberate lie about the listing being over, and the honest option
     * -- keeping the key -- is worse: it restores exactly the blank screen this exists to remove,
     * whereas ending pagination lets the feed say "no posts found", which is what a listing with no
     * media in its first several hundred posts effectively means.
     */
    @NonNull
    @Override
    public ListenableFuture<LoadResult<String, Post>> loadFuture(@NonNull LoadParams<String> loadParams) {
        return loadPastBarrenPages(loadParams, 0);
    }

    private ListenableFuture<LoadResult<String, Post>> loadPastBarrenPages(@NonNull LoadParams<String> loadParams,
                                                                          int hopsSoFar) {
        return Futures.transformAsync(loadOnce(loadParams), result -> {
            if (!(result instanceof LoadResult.Page)) {
                return Futures.immediateFuture(result);
            }

            @SuppressWarnings("unchecked")
            LoadResult.Page<String, Post> page = (LoadResult.Page<String, Post>) result;
            String nextKey = page.getNextKey();
            if (nextKey == null || presentsSomething(page.getData())) {
                return Futures.immediateFuture(result);
            }

            if (hopsSoFar >= MAX_BARREN_PAGE_HOPS) {
                return Futures.immediateFuture(
                        new LoadResult.Page<>(page.getData(), page.getPrevKey(), null));
            }

            LoadParams<String> nextParams = new LoadParams.Append<>(
                    nextKey,
                    Math.max(loadParams.getLoadSize(), BARREN_PAGE_HOP_LOAD_SIZE),
                    loadParams.getPlaceholdersEnabled());
            return Futures.transform(loadPastBarrenPages(nextParams, hopsSoFar + 1),
                    tail -> concatenatePages(page, tail), executor);
        }, executor);
    }

    /**
     * Whether the feed will render at least one of these posts. Read posts are not considered: a
     * feed hiding those can still empty a page out, but that filter lives in the view model and the
     * source is not told about it.
     */
    private boolean presentsSomething(List<Post> pagePosts) {
        if (pagePosts.isEmpty()) {
            return false;
        }
        if (!mediaOnly) {
            return true;
        }
        for (Post post : pagePosts) {
            if (post.isMediaPost()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Joins a barren page to whatever the hop after it returned. The prev key stays the first page's
     * and the next key becomes the last one's, so the merged page keys as though it had been loaded
     * in one go. An error from the hop is returned as the error -- the head presents nothing on its
     * own, so surfacing it with a retry beats showing the blank feed.
     */
    private static LoadResult<String, Post> concatenatePages(LoadResult.Page<String, Post> head,
                                                             LoadResult<String, Post> tail) {
        if (!(tail instanceof LoadResult.Page)) {
            return tail;
        }
        @SuppressWarnings("unchecked")
        LoadResult.Page<String, Post> tailPage = (LoadResult.Page<String, Post>) tail;
        List<Post> merged = new ArrayList<>(head.getData().size() + tailPage.getData().size());
        merged.addAll(head.getData());
        merged.addAll(tailPage.getData());
        return new LoadResult.Page<>(merged, head.getPrevKey(), tailPage.getNextKey());
    }

    private ListenableFuture<LoadResult<String, Post>> loadOnce(@NonNull LoadParams<String> loadParams) {
        RedditAPI api = retrofit.create(RedditAPI.class);
        switch (postType) {
            case PostType.FRONT_PAGE:
                return loadHomePosts(loadParams, api);
            case PostType.SUBREDDIT:
                return loadSubredditPosts(loadParams, api);
            case PostType.USER:
                return loadUserPosts(loadParams, api);
            case PostType.SEARCH:
                return loadSearchPosts(loadParams, api);
            case PostType.DUPLICATES:
                return loadDuplicatesPosts(loadParams, api);
            case PostType.MULTIREDDIT:
                return loadMultiRedditPosts(loadParams, api);
            default:
                return loadAnonymousFrontPageOrMultiredditPosts(loadParams, api);
        }
    }

    public LoadResult<String, Post> transformData(Response<String> response) {
        if (!response.isSuccessful()) {
            //{"reason": "banned", "message": "Not Found", "error": 404}
            try (ResponseBody errorBody = response.errorBody()) {
                if (errorBody != null) {
                    // Gson returns null for an empty or literal-null body. Reading the reason
                    // off it threw an NPE that the catch below swallowed, silently discarding the
                    // specific reason instead of reporting it.
                    RedditError redditError = new Gson().fromJson(errorBody.string(), RedditError.class);
                    if (redditError != null) {
                        return new LoadResult.Error<>(new PostPagingSourceError(response.code(), redditError.getReason()));
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return new LoadResult.Error<>(new PostPagingSourceError(response.code(), null));
        }
        return transformData(parseListing(response.body()), response.code());
    }

    // Parses the response body once and shares it (versus re-parsing for the posts and the `after`
    // separately, plus again for the Saved cache). Returns null on a null/malformed body.
    @Nullable
    private static JSONObject parseListing(@Nullable String body) {
        if (body == null) {
            return null;
        }
        try {
            return new JSONObject(body);
        } catch (JSONException e) {
            return null;
        }
    }

    private LoadResult<String, Post> transformData(@Nullable JSONObject json, int responseCode) {
        LinkedHashSet<Post> newPosts = ParsePost.parsePostsSync(json, -1, postFilter, readPostsList);
        if (newPosts == null) {
            return new LoadResult.Error<>(new PostPagingSourceError(responseCode, "Error parsing posts"));
        }
        String lastItem = ParsePost.getLastItem(json);
        int currentPostsSize = posts.size();
        if (lastItem != null && lastItem.equals(previousLastItem)) {
            lastItem = null;
        }
        previousLastItem = lastItem;

        if (Account.ANONYMOUS_ACCOUNT.equals(accountName)) {
            setMetadataToAnonymousPosts(newPosts);
        }

        for (Post p : newPosts) {
            if (existingPostIds.contains(p.getId())) {
                continue;
            }

            existingPostIds.add(p.getId());
            posts.add(p);
        }

        if (currentPostsSize == posts.size()) {
            return new LoadResult.Page<>(new ArrayList<>(), null, lastItem);
        } else {
            // Copy the slice: subList() is a live view of posts, which a later load()
            // structurally modifies via add(), invalidating the view (CME on iteration).
            return new LoadResult.Page<>(new ArrayList<>(posts.subList(currentPostsSize, posts.size())), null, lastItem);
        }
    }

    private void setMetadataToAnonymousPosts(LinkedHashSet<Post> posts) {
        List<ReadPost> readPostsInDatabase = redditDataRoomDatabase.readPostDao().getAllReadPostsForMetadata(
                accountName, posts.stream().map(Post::getId).collect(Collectors.toList()));
        Map<String, Post> existingPostsMap = posts.stream().collect(Collectors.toMap(Post::getId, post -> post));
        for (ReadPost r : readPostsInDatabase) {
            Post existingPost = existingPostsMap.get(r.getId());
            if (existingPost != null) {
                switch (r.getReadPostType()) {
                    case ReadPostType.ANONYMOUS_UPVOTED_POSTS:
                        existingPost.setVoteType(1);
                        break;
                    case ReadPostType.ANONYMOUS_DOWNVOTED_POSTS:
                        existingPost.setVoteType(-1);
                        break;
                    case ReadPostType.ANONYMOUS_HIDDEN_POSTS:
                        existingPost.setHidden(true);
                        break;
                    case ReadPostType.ANONYMOUS_SAVED_POSTS:
                        existingPost.setSaved(true);
                        break;
                }
            }
        }
    }

    private ListenableFuture<LoadResult<String, Post>> loadHomePosts(@NonNull LoadParams<String> loadParams, RedditAPI api) {
        ListenableFuture<Response<String>> bestPost;
        String afterKey;
        if (loadParams.getKey() == null) {
            boolean savePostFeedScrolledPosition = sortType != null && sortType.getType() == SortType.Type.BEST && sharedPreferences.getBoolean(SharedPreferencesUtils.SAVE_FRONT_PAGE_SCROLLED_POSITION, false);
            if (savePostFeedScrolledPosition && postFeedScrolledPositionSharedPreferences != null) {
                String accountNameForCache = accountName.equals(Account.ANONYMOUS_ACCOUNT) ? SharedPreferencesUtils.FRONT_PAGE_SCROLLED_POSITION_ANONYMOUS : accountName;
                afterKey = postFeedScrolledPositionSharedPreferences.getString(accountNameForCache + SharedPreferencesUtils.FRONT_PAGE_SCROLLED_POSITION_FRONT_PAGE_BASE, null);
            } else {
                afterKey = null;
            }
        } else {
            afterKey = loadParams.getKey();
        }
        bestPost = api.getBestPostsListenableFuture(sortType.getType(), sortType.getTime(), afterKey,
                APIUtils.getOAuthHeader(accessToken));

        ListenableFuture<LoadResult<String, Post>> pageFuture = Futures.transform(bestPost, this::transformData, executor);

        ListenableFuture<LoadResult<String, Post>> partialLoadResultFuture =
                Futures.catching(pageFuture, HttpException.class,
                        LoadResult.Error::new, executor);

        return Futures.catching(partialLoadResultFuture,
                IOException.class, LoadResult.Error::new, executor);
    }

    private ListenableFuture<Response<String>> fetchSubredditPosts(LoadParams<String> loadParams, RedditAPI api, int limit) {
        // r/ContinuumAll is a name the app gives r/all's listing so a post filter can target it on
        // its own. Only the API path is rewritten; everything else keeps the ContinuumAll identity.
        String apiName = Constants.apiSubredditName(subredditOrUserName);
        if (accountName.equals(Account.ANONYMOUS_ACCOUNT)) {
            return api.getSubredditBestPostsListenableFuture(apiName, sortType.getType(),
                    sortType.getTime(), loadParams.getKey(), limit);
        } else {
            return api.getSubredditBestPostsOauthListenableFuture(apiName, sortType.getType(),
                    sortType.getTime(), loadParams.getKey(), limit,
                    APIUtils.getOAuthHeader(accessToken));
        }
    }

    private ListenableFuture<LoadResult<String, Post>> loadSubredditPosts(@NonNull LoadParams<String> loadParams, RedditAPI api) {
        int[] limit = {APIUtils.subredditAPICallLimit(subredditOrUserName)};
        ListenableFuture<Response<String>> subredditPost = fetchSubredditPosts(loadParams, api, limit[0]);

        // Retry with halved limit on HTTP 500
        ListenableFuture<Response<String>> retryOnce = Futures.transformAsync(subredditPost, response -> {
            if (response.code() == HTTP_INTERNAL_SERVER_ERROR) {
                limit[0] /= 2;
                return fetchSubredditPosts(loadParams, api, limit[0]);
            }
            return Futures.immediateFuture(response);
        }, executor);

        // Retry with halved limit again on HTTP 500
        ListenableFuture<Response<String>> retryTwice = Futures.transformAsync(retryOnce, response -> {
            if (response.code() == HTTP_INTERNAL_SERVER_ERROR) {
                limit[0] /= 2;
                return fetchSubredditPosts(loadParams, api, limit[0]);
            }
            return Futures.immediateFuture(response);
        }, executor);

        ListenableFuture<LoadResult<String, Post>> pageFuture = Futures.transform(retryTwice, this::transformData, executor);

        ListenableFuture<LoadResult<String, Post>> partialLoadResultFuture =
                Futures.catching(pageFuture, HttpException.class,
                        LoadResult.Error::new, executor);

        return Futures.catching(partialLoadResultFuture,
                IOException.class, LoadResult.Error::new, executor);
    }

    private ListenableFuture<Response<String>> fetchUserPosts(RedditAPI api, @Nullable String afterKey) {
        if (accountName.equals(Account.ANONYMOUS_ACCOUNT)) {
            return api.getUserPostsListenableFuture(subredditOrUserName, afterKey, sortType.getType(),
                    sortType.getTime(), 100);
        } else {
            return api.getUserPostsOauthListenableFuture(APIUtils.AUTHORIZATION_BASE + accessToken,
                    subredditOrUserName, userWhere, afterKey, USER_WHERE_SUBMITTED.equals(userWhere) ? sortType.getType() : null, USER_WHERE_SUBMITTED.equals(userWhere) ? sortType.getTime() : null, 100);
        }
    }

    private boolean isSavedSearchActive() {
        return searchQuery != null && !searchQuery.trim().isEmpty();
    }

    // The server-side Saved posts tab, the only user feed the hard-TTL SavedPostCache applies to.
    private boolean isSavedFeed() {
        return postType == PostType.USER && USER_WHERE_SAVED.equals(userWhere);
    }

    private ListenableFuture<LoadResult<String, Post>> loadUserPosts(@NonNull LoadParams<String> loadParams, RedditAPI api) {
        boolean reset = loadParams.getKey() == null;

        if (isSavedSearchActive()) {
            // A refined query reuses the listing already walked for this search session (see
            // SavedSearchCache), so only the first query of a session hits the network.
            List<Post> cached = savedSearchCache != null ? savedSearchCache.snapshot() : null;
            if (cached != null) {
                return Futures.immediateFuture(filterToPage(cached));
            }
            // First search after a tab reopen: if the persistent cache holds the whole saved list and
            // is still fresh, filter it instead of re-walking the network. The cache read touches Room
            // and disk, so it MUST run on the executor: loadFuture() is invoked on the main thread (the
            // feed is a PagingLiveData driven from a CoroutineLiveData on Dispatchers.Main.immediate).
            if (reset && isSavedFeed() && SavedPostCache.isFresh(accountName, userWhere)) {
                return Futures.submitAsync(() -> {
                    SavedPostCache.Cached hit = SavedPostCache.load(accountName, userWhere, postFilter, readPostsList);
                    if (hit != null && hit.complete) {
                        if (savedSearchCache != null) {
                            savedSearchCache.set(hit.posts);
                        }
                        return Futures.immediateFuture(filterToPage(hit.posts));
                    }
                    // Cache miss (file cleared / incomplete): walk the listing over the network.
                    return catchErrors(loadAllUserPostsFiltered(api, loadParams.getKey(), 0));
                }, executor);
            }
            // Load the entire saved listing up front, then filter, so every match is presented at
            // once (with the normal loading indicator) rather than trickling in as pages arrive.
            return catchErrors(loadAllUserPostsFiltered(api, loadParams.getKey(), 0));
        }

        // Tab open (no search): serve the whole saved list from the fresh persistent cache with no
        // network call. The cache read touches Room and disk, so it MUST run on the executor (see
        // above); loadFuture() runs on the main thread. nextKey null == "no more".
        if (reset && isSavedFeed() && SavedPostCache.isFresh(accountName, userWhere)) {
            return Futures.submitAsync(() -> {
                SavedPostCache.Cached hit = SavedPostCache.load(accountName, userWhere, postFilter, readPostsList);
                if (hit != null && hit.complete) {
                    return Futures.<LoadResult<String, Post>>immediateFuture(new LoadResult.Page<>(hit.posts, null, null));
                }
                // Cache miss after a fresh check (file cleared / incomplete): fall back to the network.
                return catchErrors(loadUserPostsWithKey(api, loadParams.getKey()));
            }, executor);
        }
        return catchErrors(loadUserPostsWithKey(api, loadParams.getKey()));
    }

    private LoadResult<String, Post> filterToPage(List<Post> unfiltered) {
        List<Post> filtered = new ArrayList<>();
        for (Post p : unfiltered) {
            if (SavedThingSearchFilter.matches(p, searchQuery)) {
                filtered.add(p);
            }
        }
        return new LoadResult.Page<>(filtered, null, null);
    }

    // Collect this page's raw t3 children (deduped, in order) for the persistent SavedPostCache. The
    // whole unfiltered t3 listing is stored -- the current postFilter is re-applied on load -- so a
    // filter change needs no invalidation. Takes the already-parsed listing to avoid re-parsing.
    private void accumulateSavedCacheChildren(@Nullable JSONObject json) {
        if (json == null) {
            return;
        }
        try {
            JSONArray children = json.getJSONObject(JSONUtils.DATA_KEY).getJSONArray(JSONUtils.CHILDREN_KEY);
            for (int i = 0; i < children.length(); i++) {
                JSONObject child = children.getJSONObject(i);
                // Saved listings interleave t1 comments; the posts tab keeps only submissions.
                if (!"t3".equals(child.optString(JSONUtils.KIND_KEY))) {
                    continue;
                }
                String fullname = child.getJSONObject(JSONUtils.DATA_KEY).optString(JSONUtils.NAME_KEY);
                if (fullname.isEmpty() || !savedCacheSeenFullnames.add(fullname)) {
                    continue;
                }
                savedCacheChildren.put(child);
            }
        } catch (JSONException e) {
            // A malformed page just means a smaller cache; never fail the load over it.
        }
    }

    // Walks the saved/user listing from afterKey to the end (accumulating into `posts` with dedup),
    // then returns every post that matches the search query as a single terminal page (next key
    // null). Bounded by SAVED_SEARCH_MAX_PAGES; transformData's previousLastItem guard and the finite
    // listing also terminate it.
    private ListenableFuture<LoadResult<String, Post>> loadAllUserPostsFiltered(RedditAPI api, @Nullable String afterKey, int pagesLoaded) {
        ListenableFuture<Response<String>> userPosts = fetchUserPosts(api, afterKey);
        return Futures.transformAsync(userPosts, response -> {
            if (!response.isSuccessful()) {
                LoadResult<String, Post> error = new LoadResult.Error<>(new Exception("Error getting response"));
                return Futures.immediateFuture(error);
            }
            JSONObject json = parseListing(response.body());
            LinkedHashSet<Post> newPosts = ParsePost.parsePostsSync(json, -1, postFilter, readPostsList);
            if (newPosts == null) {
                LoadResult<String, Post> error = new LoadResult.Error<>(new Exception("Error parsing posts"));
                return Futures.immediateFuture(error);
            }
            String lastItem = ParsePost.getLastItem(json);
            boolean repeated = lastItem != null && lastItem.equals(previousLastItem);
            previousLastItem = lastItem;

            for (Post p : newPosts) {
                if (!existingPostIds.contains(p.getId())) {
                    existingPostIds.add(p.getId());
                    posts.add(p);
                }
            }

            // Keep the raw t3 children so a complete walk can be persisted for the Saved tab cache.
            if (isSavedFeed()) {
                accumulateSavedCacheChildren(json);
            }

            int nextPagesLoaded = pagesLoaded + 1;
            // Genuine end = Reddit gave a null/empty `after`. A repeated cursor also stops the walk
            // (defensive), but it may mean we stopped short, so it does NOT count the list as complete.
            boolean genuineEnd = lastItem == null || lastItem.isEmpty();
            boolean atEnd = genuineEnd || repeated || nextPagesLoaded >= SAVED_SEARCH_MAX_PAGES;
            if (!atEnd) {
                return loadAllUserPostsFiltered(api, lastItem, nextPagesLoaded);
            }

            // Cache the full unfiltered walk so refining the query filters it in memory instead of
            // re-walking the listing over the network.
            if (savedSearchCache != null) {
                savedSearchCache.set(posts);
            }
            // Persist to disk so a later tab open is instant -- only when we truly reached the end
            // (a capped or repeated-cursor walk is not known to be the complete list).
            if (isSavedFeed() && genuineEnd) {
                SavedPostCache.store(accountName, userWhere, savedCacheChildren, true);
            }
            return Futures.immediateFuture(filterToPage(posts));
        }, executor);
    }

    // Saved/upvoted/downvoted/hidden listings interleave posts (t3) and comments (t1). A page that
    // contains only comments parses to zero posts, which would make us hand Paging an empty page with
    // a non-null next key — Paging 3 does not reliably continue from an empty page, so pagination
    // would stall wherever a run of saved comments begins. When that happens, keep following the next
    // key until a page yields at least one post or the listing ends (next key becomes null). The
    // listing is finite and transformData's previousLastItem guard nulls a repeated key, so this
    // terminates.
    private ListenableFuture<LoadResult<String, Post>> loadUserPostsWithKey(RedditAPI api, @Nullable String afterKey) {
        ListenableFuture<Response<String>> userPosts = fetchUserPosts(api, afterKey);
        return Futures.transformAsync(userPosts, response -> {
            if (!response.isSuccessful()) {
                return Futures.immediateFuture(new LoadResult.Error<>(new Exception("Error getting response")));
            }
            // Parse once and share the listing between transformData and the Saved-cache accumulation.
            JSONObject json = parseListing(response.body());
            LoadResult<String, Post> result = transformData(json, 200);
            // Accumulate raw children as the user pages so that reaching the end via normal browsing
            // (not just search) warms the persistent Saved cache too.
            if (isSavedFeed()) {
                accumulateSavedCacheChildren(json);
            }
            if (result instanceof LoadResult.Page) {
                LoadResult.Page<String, Post> page = (LoadResult.Page<String, Post>) result;
                if (page.getData().isEmpty() && page.getNextKey() != null) {
                    return loadUserPostsWithKey(api, page.getNextKey());
                }
                // Persist as complete only when Reddit genuinely ended the listing (null/empty
                // `after`). A null next key can also come from the repeated-cursor guard, which may
                // mean we stopped short -- getLastItem gives the raw `after` to tell them apart.
                if (isSavedFeed() && page.getNextKey() == null) {
                    String after = ParsePost.getLastItem(json);
                    if (after == null || after.isEmpty()) {
                        SavedPostCache.store(accountName, userWhere, savedCacheChildren, true);
                    }
                }
            }
            return Futures.immediateFuture(result);
        }, executor);
    }

    private ListenableFuture<LoadResult<String, Post>> loadSearchPosts(@NonNull LoadParams<String> loadParams, RedditAPI api) {
        ListenableFuture<Response<String>> searchPosts;
        if (subredditOrUserName == null) {
            if (Account.ANONYMOUS_ACCOUNT.equals(accountName)) {
                searchPosts = api.searchPostsListenableFuture(query, loadParams.getKey(), sortType.getType(), sortType.getTime(),
                        trendingSource);
            } else {
                searchPosts = api.searchPostsOauthListenableFuture(query, loadParams.getKey(), sortType.getType(),
                        sortType.getTime(), trendingSource, APIUtils.getOAuthHeader(accessToken));
            }
        } else {
            if (Account.ANONYMOUS_ACCOUNT.equals(accountName)) {
                searchPosts = api.searchPostsInSpecificSubredditListenableFuture(subredditOrUserName, query,
                        sortType.getType(), sortType.getTime(), loadParams.getKey());
            } else {
                searchPosts = api.searchPostsInSpecificSubredditOauthListenableFuture(subredditOrUserName, query,
                        sortType.getType(), sortType.getTime(), loadParams.getKey(),
                        APIUtils.getOAuthHeader(accessToken));
            }
        }

        ListenableFuture<LoadResult<String, Post>> pageFuture = Futures.transform(searchPosts, this::transformData, executor);

        ListenableFuture<LoadResult<String, Post>> partialLoadResultFuture =
                Futures.catching(pageFuture, HttpException.class,
                        LoadResult.Error::new, executor);

        return Futures.catching(partialLoadResultFuture,
                IOException.class, LoadResult.Error::new, executor);
    }

    private ListenableFuture<LoadResult<String, Post>> loadDuplicatesPosts(@NonNull LoadParams<String> loadParams, RedditAPI api) {
        ListenableFuture<Response<String>> duplicatesPosts;
        if (accountName.equals(Account.ANONYMOUS_ACCOUNT)) {
            duplicatesPosts = api.getDuplicatesListenableFuture(subredditOrUserName, loadParams.getKey(),
                    APIUtils.ANONYMOUS_USER_AGENT);
        } else {
            duplicatesPosts = api.getDuplicatesOauthListenableFuture(subredditOrUserName, loadParams.getKey(),
                    APIUtils.getOAuthHeader(accessToken));
        }

        ListenableFuture<LoadResult<String, Post>> pageFuture = Futures.transform(duplicatesPosts, this::transformDuplicatesData, executor);

        ListenableFuture<LoadResult<String, Post>> partialLoadResultFuture =
                Futures.catching(pageFuture, HttpException.class,
                        LoadResult.Error::new, executor);

        return Futures.catching(partialLoadResultFuture,
                IOException.class, LoadResult.Error::new, executor);
    }

    // Mirrors transformData() but reads the duplicates listing (element 1 of the response array)
    // rather than a top-level listing object.
    public LoadResult<String, Post> transformDuplicatesData(Response<String> response) {
        if (response.isSuccessful()) {
            String responseString = response.body();
            LinkedHashSet<Post> newPosts = ParsePost.parseDuplicatePostsSync(responseString, postFilter, readPostsList);
            String lastItem = ParsePost.getDuplicatesLastItem(responseString);
            if (newPosts == null) {
                return new LoadResult.Error<>(new PostPagingSourceError(response.code(), "Error parsing posts"));
            } else {
                int currentPostsSize = posts.size();
                if (lastItem != null && lastItem.equals(previousLastItem)) {
                    lastItem = null;
                }
                previousLastItem = lastItem;

                if (Account.ANONYMOUS_ACCOUNT.equals(accountName)) {
                    setMetadataToAnonymousPosts(newPosts);
                }

                for (Post p : newPosts) {
                    if (existingPostIds.contains(p.getId())) {
                        continue;
                    }

                    existingPostIds.add(p.getId());
                    posts.add(p);
                }

                if (currentPostsSize == posts.size()) {
                    return new LoadResult.Page<>(new ArrayList<>(), null, lastItem);
                } else {
                    // Copy the slice: subList() is a live view of posts, which a later load()
                    // structurally modifies via add(), invalidating the view (CME on iteration).
                    return new LoadResult.Page<>(new ArrayList<>(posts.subList(currentPostsSize, posts.size())), null, lastItem);
                }
            }
        } else {
            return new LoadResult.Error<>(new PostPagingSourceError(response.code(), null));
        }
    }

    private ListenableFuture<LoadResult<String, Post>> loadMultiRedditPosts(@NonNull LoadParams<String> loadParams, RedditAPI api) {
        // When searching within multi-reddit, keep original behavior (no user post merging)
        if (query != null && !query.isEmpty()) {
            ListenableFuture<Response<String>> multiRedditPosts;
            if (accountName.equals(Account.ANONYMOUS_ACCOUNT)) {
                multiRedditPosts = api.searchMultiRedditPostsListenableFuture(multiRedditPath, query, loadParams.getKey(),
                        sortType.getType(), sortType.getTime());
            } else {
                multiRedditPosts = api.searchMultiRedditPostsOauthListenableFuture(multiRedditPath, query, loadParams.getKey(),
                        sortType.getType(), sortType.getTime(), APIUtils.getOAuthHeader(accessToken));
            }

            ListenableFuture<LoadResult<String, Post>> pageFuture = Futures.transform(multiRedditPosts, this::transformData, executor);
            return catchErrors(pageFuture);
        }

        // Parse composite after key
        String multiAfterKey = getMainAfterKey(loadParams.getKey());
        Map<String, String> currentUserAfterKeys = parseUserAfterKeys(loadParams.getKey());
        final boolean isInitialLoad = loadParams.getKey() == null;

        // Determine if we have users to merge (or might have on first load)
        boolean hasUsers = multiRedditUsernames != null && !multiRedditUsernames.isEmpty();
        boolean mightHaveUsers = !multiRedditUsernamesFetched && !accountName.equals(Account.ANONYMOUS_ACCOUNT);

        // Fetch multi-reddit posts (use reduced limit when merging with user posts)
        ListenableFuture<Response<String>> multiRedditPosts;
        if (accountName.equals(Account.ANONYMOUS_ACCOUNT)) {
            multiRedditPosts = api.getMultiRedditPostsListenableFuture(multiRedditPath, sortType.getType(), multiAfterKey, sortType.getTime());
        } else if (hasUsers || mightHaveUsers) {
            multiRedditPosts = api.getMultiRedditPostsOauthListenableFuture(multiRedditPath, sortType.getType(), multiAfterKey,
                    sortType.getTime(), APIUtils.getOAuthHeader(accessToken), 75);
        } else {
            multiRedditPosts = api.getMultiRedditPostsOauthListenableFuture(multiRedditPath, sortType.getType(), multiAfterKey,
                    sortType.getTime(), APIUtils.getOAuthHeader(accessToken), 100);
        }

        // On first load, fetch multi-reddit info to discover user entries, then fire user
        // post requests as soon as usernames are known (without waiting for multi-reddit posts)
        if (mightHaveUsers) {
            multiRedditUsernamesFetched = true;
            ListenableFuture<Response<String>> multiInfoFuture = api.getMultiRedditInfoListenableFuture(
                    APIUtils.getOAuthHeader(accessToken), multiRedditPath);

            // As soon as info returns, launch user post fetches immediately
            ListenableFuture<List<Response<String>>> userPostsFuture = Futures.transformAsync(multiInfoFuture, infoResponse -> {
                parseMultiRedditInfoResponse(infoResponse);
                if (multiRedditUsernames == null || multiRedditUsernames.isEmpty()) {
                    return Futures.immediateFuture(new ArrayList<>());
                }
                List<ListenableFuture<Response<String>>> userFutures = launchUserPostFetches(api, currentUserAfterKeys, isInitialLoad);
                return Futures.successfulAsList(userFutures);
            }, executor);

            // Wait for multi-reddit posts AND user posts (both in flight simultaneously)
            ListenableFuture<LoadResult<String, Post>> pageFuture = Futures.whenAllSucceed(multiRedditPosts, userPostsFuture)
                    .call(() -> {
                        Response<String> mainResponse = Futures.getDone(multiRedditPosts);
                        List<Response<String>> userResponses = Futures.getDone(userPostsFuture);
                        return mergeResponses(mainResponse, userResponses, getUsersToFetch(currentUserAfterKeys, isInitialLoad));
                    }, executor);

            return catchErrors(pageFuture);
        }

        // Subsequent loads: fetch multi-reddit posts and user posts all in parallel
        if (multiRedditUsernames != null && !multiRedditUsernames.isEmpty()) {
            List<ListenableFuture<Response<String>>> userFutures = launchUserPostFetches(api, currentUserAfterKeys, isInitialLoad);
            ListenableFuture<List<Response<String>>> allUserPosts = Futures.successfulAsList(userFutures);

            ListenableFuture<LoadResult<String, Post>> pageFuture = Futures.whenAllSucceed(multiRedditPosts, allUserPosts)
                    .call(() -> {
                        Response<String> mainResponse = Futures.getDone(multiRedditPosts);
                        List<Response<String>> userResponses = Futures.getDone(allUserPosts);
                        return mergeResponses(mainResponse, userResponses, getUsersToFetch(currentUserAfterKeys, isInitialLoad));
                    }, executor);

            return catchErrors(pageFuture);
        }

        // No users, just transform multi-reddit posts
        ListenableFuture<LoadResult<String, Post>> pageFuture = Futures.transform(multiRedditPosts, this::transformData, executor);
        return catchErrors(pageFuture);
    }

    private List<String> getUsersToFetch(@Nullable Map<String, String> currentUserAfterKeys, boolean isInitialLoad) {
        List<String> users = new ArrayList<>();
        if (multiRedditUsernames == null) return users;
        for (String username : multiRedditUsernames) {
            if (!isInitialLoad && currentUserAfterKeys != null && !currentUserAfterKeys.containsKey(username)) {
                continue;
            }
            users.add(username);
        }
        return users;
    }

    private List<ListenableFuture<Response<String>>> launchUserPostFetches(
            RedditAPI api, @Nullable Map<String, String> currentUserAfterKeys, boolean isInitialLoad) {
        List<ListenableFuture<Response<String>>> futures = new ArrayList<>();
        if (multiRedditUsernames == null) {
            return futures;
        }
        for (String username : multiRedditUsernames) {
            if (!isInitialLoad && currentUserAfterKeys != null && !currentUserAfterKeys.containsKey(username)) {
                continue;
            }
            String userAfter = (currentUserAfterKeys != null) ? currentUserAfterKeys.get(username) : null;
            if (accountName.equals(Account.ANONYMOUS_ACCOUNT)) {
                futures.add(api.getUserPostsListenableFuture(username, userAfter, sortType.getType(), sortType.getTime(), 25));
            } else {
                futures.add(api.getUserPostsOauthListenableFuture(APIUtils.AUTHORIZATION_BASE + accessToken, username, "submitted",
                        userAfter, sortType.getType(), sortType.getTime(), 25));
            }
        }
        return futures;
    }

    @Nullable
    private String getMainAfterKey(@Nullable String compositeKey) {
        if (compositeKey == null || !compositeKey.startsWith("{")) return compositeKey;
        try {
            String m = new JSONObject(compositeKey).optString("m");
            return m.isEmpty() ? null : m;
        } catch (JSONException e) {
            return compositeKey;
        }
    }

    @Nullable
    private Map<String, String> parseUserAfterKeys(@Nullable String compositeKey) {
        if (compositeKey == null || !compositeKey.startsWith("{")) return null;
        try {
            JSONObject users = new JSONObject(compositeKey).optJSONObject("u");
            if (users == null) return null;
            Map<String, String> result = new HashMap<>();
            Iterator<String> keys = users.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                String val = users.getString(key);
                if (!val.isEmpty()) result.put(key, val);
            }
            return result;
        } catch (JSONException e) {
            return null;
        }
    }

    private void parseMultiRedditInfoResponse(Response<String> response) {
        if (response == null || !response.isSuccessful() || response.body() == null) return;
        try {
            JSONObject data = new JSONObject(response.body()).getJSONObject("data");
            JSONArray subreddits = data.getJSONArray("subreddits");
            multiRedditUsernames = new ArrayList<>();
            for (int i = 0; i < subreddits.length(); i++) {
                String name = subreddits.getJSONObject(i).getString("name");
                if (name.startsWith("u_")) {
                    multiRedditUsernames.add(name.substring(2));
                }
            }
        } catch (JSONException e) {
            // Failed to parse multi-reddit info
        }
    }

    private LoadResult<String, Post> mergeResponses(
            @Nullable Response<String> mainResponse, List<Response<String>> userResponses,
            List<String> usersToFetch) {
        int currentPostsSize = posts.size();

        // Parse main multi-reddit response
        String mainLastItem = null;
        if (mainResponse != null && mainResponse.isSuccessful()) {
            String responseString = mainResponse.body();
            LinkedHashSet<Post> newPosts = ParsePost.parsePostsSync(responseString, -1, postFilter, readPostsList);
            mainLastItem = ParsePost.getLastItem(responseString);
            if (newPosts != null) {
                addNewPosts(newPosts);
            }
        }

        // If no user responses, return main response only
        if (userResponses == null || userResponses.isEmpty()) {
            if (mainLastItem != null && mainLastItem.equals(previousLastItem)) {
                mainLastItem = null;
            }
            previousLastItem = mainLastItem;
            int newSize = posts.size();
            if (newSize == currentPostsSize) {
                return new LoadResult.Page<>(new ArrayList<>(), null, mainLastItem);
            }
            return new LoadResult.Page<>(new ArrayList<>(posts.subList(currentPostsSize, newSize)), null, mainLastItem);
        }

        try {
            boolean hasMore = false;
            JSONObject compositeAfter = new JSONObject();

            if (mainLastItem != null && !mainLastItem.isEmpty()) {
                compositeAfter.put("m", mainLastItem);
                hasMore = true;
            }

            // Parse user post responses
            JSONObject userAfters = new JSONObject();
            for (int i = 0; i < usersToFetch.size(); i++) {
                String username = usersToFetch.get(i);
                Response<String> userResponse = (i < userResponses.size()) ? userResponses.get(i) : null;
                if (userResponse != null && userResponse.isSuccessful()) {
                    String responseString = userResponse.body();
                    LinkedHashSet<Post> userPosts = ParsePost.parsePostsSync(responseString, -1, postFilter, readPostsList);
                    String userLastItem = ParsePost.getLastItem(responseString);
                    if (userPosts != null) {
                        addNewPosts(userPosts);
                    }
                    if (userLastItem != null && !userLastItem.isEmpty()) {
                        userAfters.put(username, userLastItem);
                        hasMore = true;
                    }
                }
            }

            if (userAfters.length() > 0) {
                compositeAfter.put("u", userAfters);
            }

            String nextKey = hasMore ? compositeAfter.toString() : null;
            return buildSortedPage(currentPostsSize, nextKey);
        } catch (JSONException e) {
            return new LoadResult.Error<>(e);
        }
    }

    private LoadResult<String, Post> buildSortedPage(int currentPostsSize, @Nullable String nextKey) {
        int newSize = posts.size();
        if (newSize == currentPostsSize) {
            return new LoadResult.Page<>(new ArrayList<>(), null, nextKey);
        }
        List<Post> resultPosts = new ArrayList<>(posts.subList(currentPostsSize, newSize));
        resultPosts.sort((a, b) -> Long.compare(b.getPostTimeMillis(), a.getPostTimeMillis()));
        return new LoadResult.Page<>(resultPosts, null, nextKey);
    }

    private void addNewPosts(LinkedHashSet<Post> newPosts) {
        for (Post p : newPosts) {
            if (existingPostIds.contains(p.getId())) {
                continue;
            }
            existingPostIds.add(p.getId());
            posts.add(p);
        }
    }

    // A future that yields a null "no main response" sentinel; mergeResponses handles the null.
    // Isolated here because Futures.immediateFuture's value parameter is modeled @NonNull.
    @SuppressWarnings("NullAway")
    private static ListenableFuture<Response<String>> immediateNullResponse() {
        return Futures.immediateFuture(null);
    }

    private ListenableFuture<LoadResult<String, Post>> catchErrors(ListenableFuture<LoadResult<String, Post>> future) {
        ListenableFuture<LoadResult<String, Post>> partial =
                Futures.catching(future, HttpException.class, LoadResult.Error::new, executor);
        return Futures.catching(partial, IOException.class, LoadResult.Error::new, executor);
    }

    private ListenableFuture<LoadResult<String, Post>> loadAnonymousFrontPageOrMultiredditPosts(@NonNull LoadParams<String> loadParams, RedditAPI api) {
        // For anonymous multireddit, extract user entries from concatenated name on first call
        if (postType == PostType.ANONYMOUS_MULTIREDDIT && !multiRedditUsernamesFetched) {
            multiRedditUsernamesFetched = true;
            @SuppressWarnings("StringSplitter") // String.split drops trailing empty fields, which is the behavior relied on here.
            String[] parts = (subredditOrUserName == null ? "" : subredditOrUserName).split("\\+");
            List<String> subreddits = new ArrayList<>();
            multiRedditUsernames = new ArrayList<>();
            for (String part : parts) {
                if (part.startsWith("u_")) {
                    multiRedditUsernames.add(part.substring(2));
                } else {
                    subreddits.add(part);
                }
            }
            if (!multiRedditUsernames.isEmpty() && !subreddits.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < subreddits.size(); i++) {
                    if (i > 0) sb.append("+");
                    sb.append(subreddits.get(i));
                }
                subredditOnlyName = sb.toString();
            } else if (multiRedditUsernames.isEmpty()) {
                subredditOnlyName = null;
            } else {
                // Only users, no subreddits
                subredditOnlyName = "";
            }
        }

        // If no users to merge, use original behavior
        if (multiRedditUsernames == null || multiRedditUsernames.isEmpty()) {
            ListenableFuture<Response<String>> anonymousHomePosts = api.getAnonymousFrontPageOrMultiredditPostsListenableFuture(
                    subredditOrUserName, sortType.getType(), sortType.getTime(), loadParams.getKey(),
                    APIUtils.subredditAPICallLimit(subredditOrUserName), APIUtils.ANONYMOUS_USER_AGENT);

            ListenableFuture<LoadResult<String, Post>> pageFuture = Futures.transform(anonymousHomePosts, this::transformData, executor);
            ListenableFuture<LoadResult<String, Post>> partialLoadResultFuture =
                    Futures.catching(pageFuture, HttpException.class, LoadResult.Error::new, executor);
            return Futures.catching(partialLoadResultFuture, IOException.class, LoadResult.Error::new, executor);
        }

        // Parse composite after key
        String mainAfterKey = getMainAfterKey(loadParams.getKey());
        Map<String, String> currentUserAfterKeys = parseUserAfterKeys(loadParams.getKey());
        final boolean isInitialLoad = loadParams.getKey() == null;

        boolean hasSubreddits = subredditOnlyName != null && !subredditOnlyName.isEmpty();

        // Launch subreddit posts fetch (reduced limit since we're merging with user posts)
        ListenableFuture<Response<String>> mainFuture;
        if (hasSubreddits) {
            mainFuture = api.getAnonymousFrontPageOrMultiredditPostsListenableFuture(
                    subredditOnlyName, sortType.getType(), sortType.getTime(), mainAfterKey,
                    75, APIUtils.ANONYMOUS_USER_AGENT);
        } else {
            mainFuture = immediateNullResponse();
        }

        // Launch user post fetches in parallel
        List<ListenableFuture<Response<String>>> userFutures = launchUserPostFetches(api, currentUserAfterKeys, isInitialLoad);
        ListenableFuture<List<Response<String>>> allUserPosts = Futures.successfulAsList(userFutures);

        ListenableFuture<LoadResult<String, Post>> pageFuture = Futures.whenAllSucceed(mainFuture, allUserPosts)
                .call(() -> {
                    Response<String> mainResponse = Futures.getDone(mainFuture);
                    List<Response<String>> userResponses = Futures.getDone(allUserPosts);
                    return mergeResponses(mainResponse, userResponses, getUsersToFetch(currentUserAfterKeys, isInitialLoad));
                }, executor);
        return catchErrors(pageFuture);
    }
}
