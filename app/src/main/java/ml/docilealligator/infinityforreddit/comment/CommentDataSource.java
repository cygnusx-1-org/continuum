package ml.docilealligator.infinityforreddit.comment;

import android.os.Handler;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.lifecycle.MutableLiveData;
import androidx.paging.PageKeyedDataSource;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import ml.docilealligator.infinityforreddit.NetworkState;
import ml.docilealligator.infinityforreddit.RedditDataRoomDatabase;
import ml.docilealligator.infinityforreddit.account.Account;
import ml.docilealligator.infinityforreddit.apis.RedditAPI;
import ml.docilealligator.infinityforreddit.localsaved.LocalSavedThing;
import ml.docilealligator.infinityforreddit.post.PostPagingSource;
import ml.docilealligator.infinityforreddit.thing.SortType;
import ml.docilealligator.infinityforreddit.utils.APIUtils;
import ml.docilealligator.infinityforreddit.utils.JSONUtils;
import ml.docilealligator.infinityforreddit.utils.SavedSearchCache;
import ml.docilealligator.infinityforreddit.utils.SavedThingSearchFilter;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;

public class CommentDataSource extends PageKeyedDataSource<String, Comment> {

    // When a search query is active, the remote initial load follows the listing cursor to the end,
    // collecting every match, so results are presented at once rather than trickling in. Bounded by
    // this page cap: 40 * 100 comfortably covers Reddit's ~1000-item saved cap. (The local path walks
    // its finite DB listing to exhaustion instead.)
    private static final int SEARCH_MAX_PAGES = 40;

    private final Executor executor;
    private final Handler handler;
    private final Retrofit retrofit;
    @Nullable
    private final String accessToken;
    @NonNull
    private final String accountName;
    private final String username;
    private final SortType sortType;
    private final boolean areSavedComments;
    private final boolean areLocalSavedComments;
    @Nullable
    private final String searchQuery;
    // Full unfiltered saved-comments listing, shared with the ViewModel so a refined query filters it
    // in memory instead of re-walking the listing (null when not searching).
    @Nullable
    private final SavedSearchCache<Comment> savedSearchCache;
    private final RedditDataRoomDatabase redditDataRoomDatabase;

    private final MutableLiveData<NetworkState> paginationNetworkStateLiveData;
    private final MutableLiveData<NetworkState> initialLoadStateLiveData;
    private final MutableLiveData<Boolean> hasPostLiveData;

    @Nullable
    private LoadParams<String> params;
    @Nullable
    private LoadCallback<String, Comment> callback;

    CommentDataSource(Executor executor, Handler handler, Retrofit retrofit, @Nullable String accessToken,
                      @NonNull String accountName, String username, SortType sortType,
                      boolean areSavedComments, boolean areLocalSavedComments,
                      @Nullable String searchQuery, @Nullable SavedSearchCache<Comment> savedSearchCache,
                      RedditDataRoomDatabase redditDataRoomDatabase) {
        this.executor = executor;
        this.handler = handler;
        this.retrofit = retrofit;
        this.accessToken = accessToken;
        this.accountName = accountName;
        this.username = username;
        this.sortType = sortType;
        this.areSavedComments = areSavedComments;
        this.areLocalSavedComments = areLocalSavedComments;
        this.searchQuery = searchQuery;
        this.savedSearchCache = savedSearchCache;
        this.redditDataRoomDatabase = redditDataRoomDatabase;
        paginationNetworkStateLiveData = new MutableLiveData<>();
        initialLoadStateLiveData = new MutableLiveData<>();
        hasPostLiveData = new MutableLiveData<>();
    }

    private boolean isSearchActive() {
        return searchQuery != null && !searchQuery.trim().isEmpty();
    }

    MutableLiveData<NetworkState> getPaginationNetworkStateLiveData() {
        return paginationNetworkStateLiveData;
    }

    MutableLiveData<NetworkState> getInitialLoadStateLiveData() {
        return initialLoadStateLiveData;
    }

    MutableLiveData<Boolean> hasPostLiveData() {
        return hasPostLiveData;
    }

    void retryLoadingMore() {
        if (params != null && callback != null) {
            loadAfter(params, callback);
        }
    }

    @Override
    public void loadInitial(@NonNull LoadInitialParams<String> params, @NonNull LoadInitialCallback<String, Comment> callback) {
        initialLoadStateLiveData.postValue(NetworkState.LOADING);

        if (areLocalSavedComments) {
            executor.execute(() -> {
                try {
                    LocalSavedResult result = fetchLocalSavedComments(null);
                    handler.post(() -> {
                        hasPostLiveData.postValue(!result.comments.isEmpty());
                        callback.onResult(result.comments, null, result.nextKey);
                        initialLoadStateLiveData.postValue(NetworkState.LOADED);
                    });
                } catch (Exception e) {
                    handler.post(() -> initialLoadStateLiveData.postValue(
                            new NetworkState(NetworkState.Status.FAILED, "Error parsing data")));
                }
            });
            return;
        }

        if (areSavedComments && isSearchActive()) {
            executor.execute(() -> {
                try {
                    LocalSavedResult result = fetchFilteredSavedComments(null);
                    handler.post(() -> {
                        hasPostLiveData.postValue(!result.comments.isEmpty());
                        callback.onResult(result.comments, null, result.nextKey);
                        initialLoadStateLiveData.postValue(NetworkState.LOADED);
                    });
                } catch (Exception e) {
                    handler.post(() -> initialLoadStateLiveData.postValue(
                            new NetworkState(NetworkState.Status.FAILED, "Error parsing data")));
                }
            });
            return;
        }

        RedditAPI api = retrofit.create(RedditAPI.class);
        Call<String> commentsCall;
        if (areSavedComments) {
            commentsCall = api.getUserSavedCommentsOauth(username, PostPagingSource.USER_WHERE_SAVED,
                    null, sortType.getType(), sortType.getTime(),
                    APIUtils.getOAuthHeader(accessToken));
        } else {
            if (accountName.equals(Account.ANONYMOUS_ACCOUNT)) {
                commentsCall = api.getUserComments(username, null, sortType.getType(),
                        sortType.getTime());
            } else {
                commentsCall = api.getUserCommentsOauth(APIUtils.getOAuthHeader(accessToken), username,
                        null, sortType.getType(), sortType.getTime());
            }
        }
        commentsCall.enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<String> call, @NonNull Response<String> response) {
                if (response.isSuccessful()) {
                    executor.execute(() -> {
                        parseComments(response.body(), new ParseCommentsAsyncTaskListener() {
                            @Override
                            public void parseSuccessful(ArrayList<Comment> comments, String after) {
                                handler.post(() -> {
                                    if (comments.isEmpty()) {
                                        hasPostLiveData.postValue(false);
                                    } else {
                                        hasPostLiveData.postValue(true);
                                    }

                                    if (after == null || after.isEmpty() || after.equals("null")) {
                                        callback.onResult(comments, null, null);
                                    } else {
                                        callback.onResult(comments, null, after);
                                    }
                                    initialLoadStateLiveData.postValue(NetworkState.LOADED);
                                });
                            }

                            @Override
                            public void parseFailed() {
                                handler.post(() -> {
                                    initialLoadStateLiveData.postValue(new NetworkState(NetworkState.Status.FAILED, "Error parsing data"));
                                });
                            }
                        });
                    });
                } else {
                    initialLoadStateLiveData.postValue(new NetworkState(NetworkState.Status.FAILED, "Error parsing data"));
                }
            }

            @Override
            public void onFailure(@NonNull Call<String> call, @NonNull Throwable t) {
                initialLoadStateLiveData.postValue(new NetworkState(NetworkState.Status.FAILED, "Error parsing data"));
            }
        });
    }

    @Override
    public void loadBefore(@NonNull LoadParams<String> params, @NonNull LoadCallback<String, Comment> callback) {

    }

    @Override
    public void loadAfter(@NonNull LoadParams<String> params, @NonNull LoadCallback<String, Comment> callback) {
        this.params = params;
        this.callback = callback;

        paginationNetworkStateLiveData.postValue(NetworkState.LOADING);

        if (areLocalSavedComments) {
            executor.execute(() -> {
                try {
                    LocalSavedResult result = fetchLocalSavedComments(params.key);
                    handler.post(() -> {
                        callback.onResult(result.comments, result.nextKey);
                        paginationNetworkStateLiveData.postValue(NetworkState.LOADED);
                    });
                } catch (Exception e) {
                    handler.post(() -> paginationNetworkStateLiveData.postValue(
                            new NetworkState(NetworkState.Status.FAILED, "Error fetching data")));
                }
            });
            return;
        }

        if (areSavedComments && isSearchActive()) {
            executor.execute(() -> {
                try {
                    LocalSavedResult result = fetchFilteredSavedComments(params.key);
                    handler.post(() -> {
                        callback.onResult(result.comments, result.nextKey);
                        paginationNetworkStateLiveData.postValue(NetworkState.LOADED);
                    });
                } catch (Exception e) {
                    handler.post(() -> paginationNetworkStateLiveData.postValue(
                            new NetworkState(NetworkState.Status.FAILED, "Error fetching data")));
                }
            });
            return;
        }

        RedditAPI api = retrofit.create(RedditAPI.class);
        Call<String> commentsCall;
        if (areSavedComments) {
            commentsCall = api.getUserSavedCommentsOauth(username, PostPagingSource.USER_WHERE_SAVED, params.key,
                    sortType.getType(), sortType.getTime(), APIUtils.getOAuthHeader(accessToken));
        } else {
            if (accountName.equals(Account.ANONYMOUS_ACCOUNT)) {
                commentsCall = api.getUserComments(username, params.key, sortType.getType(),
                        sortType.getTime());
            } else {
                commentsCall = api.getUserCommentsOauth(APIUtils.getOAuthHeader(accessToken),
                        username, params.key, sortType.getType(), sortType.getTime());
            }
        }
        commentsCall.enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<String> call, @NonNull Response<String> response) {
                if (response.isSuccessful()) {
                    executor.execute(() -> {
                        parseComments(response.body(), new ParseCommentsAsyncTaskListener() {
                            @Override
                            public void parseSuccessful(ArrayList<Comment> comments, String after) {
                                handler.post(() -> {
                                    if (after == null || after.isEmpty() || after.equals("null")) {
                                        callback.onResult(comments, null);
                                    } else {
                                        callback.onResult(comments, after);
                                    }
                                    paginationNetworkStateLiveData.postValue(NetworkState.LOADED);
                                });
                            }

                            @Override
                            public void parseFailed() {
                                handler.post(() -> paginationNetworkStateLiveData.postValue(new NetworkState(NetworkState.Status.FAILED, "Error parsing data")));
                            }
                        });
                    });
                } else {
                    paginationNetworkStateLiveData.postValue(new NetworkState(NetworkState.Status.FAILED, "Error fetching data"));
                }
            }

            @Override
            public void onFailure(@NonNull Call<String> call, @NonNull Throwable t) {
                paginationNetworkStateLiveData.postValue(new NetworkState(NetworkState.Status.FAILED, "Error fetching data"));
            }
        });
    }

    /**
     * Loads a page of promoted local-saved comments (t1_) from Room and hydrates them via
     * /api/info. Runs on {@code executor}. Throws on any failure so the caller can post a
     * FAILED state without mutating anything.
     *
     * <p>When a search query is active it walks the whole local_saved comments listing (following the
     * time cursor to DB exhaustion), hydrates it, caches the full unfiltered result, and returns every
     * match as a single terminal page (next key null). Walking to exhaustion rather than stopping at a
     * page cap keeps the cache complete for refinements and avoids a PageKeyedDataSource stall when the
     * first pages hold no match. Without a query it loads exactly one page, as before.
     */
    @WorkerThread
    private LocalSavedResult fetchLocalSavedComments(@Nullable String beforeKey) throws Exception {
        if (isSearchActive()) {
            // A refined query reuses the listing already hydrated for this search session.
            List<Comment> cached = savedSearchCache != null ? savedSearchCache.snapshot() : null;
            if (cached != null) {
                return filterComments(cached, null);
            }

            ArrayList<Comment> all = new ArrayList<>();
            Long before = null;
            while (true) {
                List<LocalSavedThing> promoted =
                        redditDataRoomDatabase.localSavedThingDao().getPromotedCommentsSync(accountName, before);
                if (promoted.isEmpty()) {
                    break;
                }
                long lastItem = hydrateLocalComments(promoted, all);
                // Page by DB rows so removed/un-hydratable items don't stop pagination early.
                if (promoted.size() < 25) {
                    break;
                }
                before = lastItem;
            }
            if (savedSearchCache != null) {
                savedSearchCache.set(all);
            }
            return filterComments(all, null);
        }

        Long before = beforeKey != null ? Long.parseLong(beforeKey) : null;
        List<LocalSavedThing> promoted =
                redditDataRoomDatabase.localSavedThingDao().getPromotedCommentsSync(accountName, before);
        if (promoted.isEmpty()) {
            return new LocalSavedResult(new ArrayList<>(), null);
        }
        ArrayList<Comment> comments = new ArrayList<>();
        long lastItem = hydrateLocalComments(promoted, comments);
        // Page by DB rows so removed/un-hydratable items don't stop pagination early.
        String nextKey = promoted.size() < 25 ? null : Long.toString(lastItem);
        return new LocalSavedResult(comments, nextKey);
    }

    /**
     * Hydrates one page of promoted local-saved comment rows via /api/info, appending the parsed
     * (unfiltered) comments to {@code out}. Returns the time of the last row for cursor pagination.
     */
    @WorkerThread
    private long hydrateLocalComments(List<LocalSavedThing> promoted, List<Comment> out) throws Exception {
        StringBuilder ids = new StringBuilder();
        long lastItem = 0;
        for (LocalSavedThing t : promoted) {
            ids.append(t.getFullName()).append(",");
            lastItem = t.getTime();
        }
        ids.deleteCharAt(ids.length() - 1);

        Response<String> response = retrofit.create(RedditAPI.class)
                .getInfoOauth(ids.toString(), APIUtils.getOAuthHeader(accessToken)).execute();
        if (!response.isSuccessful() || response.body() == null) {
            throw new java.io.IOException("Response failed");
        }

        JSONObject data = new JSONObject(response.body()).getJSONObject(JSONUtils.DATA_KEY);
        JSONArray children = data.getJSONArray(JSONUtils.CHILDREN_KEY);
        for (int i = 0; i < children.length(); i++) {
            try {
                JSONObject commentJSON = children.getJSONObject(i).getJSONObject(JSONUtils.DATA_KEY);
                Comment comment = ParseComment.parseSingleComment(commentJSON, 0);
                comment.setSaved(true);
                out.add(comment);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        return lastItem;
    }

    /**
     * Follows the remote /saved comments listing from {@code startAfter} (null = first page),
     * keeping only comments that match the active search query, until it exhausts the listing or hits
     * {@link #SEARCH_MAX_PAGES}, so every match is returned at once. Runs on {@code executor}; throws
     * on any failure. Reddit caps a user's saved listing at ~1000 items, which SEARCH_MAX_PAGES * 100
     * covers.
     */
    @WorkerThread
    private LocalSavedResult fetchFilteredSavedComments(@Nullable String startAfter) throws Exception {
        // A refined query reuses the listing already walked for this search session.
        if (savedSearchCache != null && startAfter == null) {
            List<Comment> cached = savedSearchCache.snapshot();
            if (cached != null) {
                return filterComments(cached, null);
            }
        }

        RedditAPI api = retrofit.create(RedditAPI.class);
        ArrayList<Comment> all = new ArrayList<>();
        String cursor = normalizeAfter(startAfter);
        int pages = 0;

        while (true) {
            Response<String> response = api.getUserSavedCommentsOauth(username,
                    PostPagingSource.USER_WHERE_SAVED, cursor, sortType.getType(), sortType.getTime(),
                    APIUtils.getOAuthHeader(accessToken)).execute();
            if (!response.isSuccessful() || response.body() == null) {
                throw new java.io.IOException("Response failed");
            }
            ParsedComments parsed = parseCommentsSync(response.body());
            all.addAll(parsed.comments);
            cursor = normalizeAfter(parsed.after);
            pages++;
            if (cursor == null || pages >= SEARCH_MAX_PAGES) {
                break;
            }
        }

        // Cache only a complete walk (from the start, listing exhausted) so refinements filter the
        // whole listing rather than a truncated prefix.
        if (savedSearchCache != null && startAfter == null && cursor == null) {
            savedSearchCache.set(all);
        }
        return filterComments(all, cursor);
    }

    private LocalSavedResult filterComments(List<Comment> unfiltered, @Nullable String nextKey) {
        ArrayList<Comment> matches = new ArrayList<>();
        for (Comment comment : unfiltered) {
            if (SavedThingSearchFilter.matches(comment, searchQuery)) {
                matches.add(comment);
            }
        }
        return new LocalSavedResult(matches, nextKey);
    }

    @Nullable
    private static String normalizeAfter(@Nullable String after) {
        if (after == null || after.isEmpty() || after.equals("null")) {
            return null;
        }
        return after;
    }

    private static class LocalSavedResult {
        final ArrayList<Comment> comments;
        @Nullable
        final String nextKey;

        LocalSavedResult(ArrayList<Comment> comments, @Nullable String nextKey) {
            this.comments = comments;
            this.nextKey = nextKey;
        }
    }

    @WorkerThread
    private static ParsedComments parseCommentsSync(String response) throws JSONException {
        JSONObject data = new JSONObject(response).getJSONObject(JSONUtils.DATA_KEY);
        JSONArray commentsJSONArray = data.getJSONArray(JSONUtils.CHILDREN_KEY);
        String after = data.getString(JSONUtils.AFTER_KEY);
        ArrayList<Comment> comments = new ArrayList<>();
        for (int i = 0; i < commentsJSONArray.length(); i++) {
            try {
                JSONObject commentJSON = commentsJSONArray.getJSONObject(i).getJSONObject(JSONUtils.DATA_KEY);
                comments.add(ParseComment.parseSingleComment(commentJSON, 0));
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        return new ParsedComments(comments, after);
    }

    private static class ParsedComments {
        final ArrayList<Comment> comments;
        final String after;

        ParsedComments(ArrayList<Comment> comments, String after) {
            this.comments = comments;
            this.after = after;
        }
    }

    @WorkerThread
    private static void parseComments(@Nullable String response, ParseCommentsAsyncTaskListener parseCommentsAsyncTaskListener) {
        try {
            JSONObject data = new JSONObject(response).getJSONObject(JSONUtils.DATA_KEY);
            JSONArray commentsJSONArray = data.getJSONArray(JSONUtils.CHILDREN_KEY);
            String after = data.getString(JSONUtils.AFTER_KEY);
            ArrayList<Comment> comments = new ArrayList<>();
            for (int i = 0; i < commentsJSONArray.length(); i++) {
                try {
                    JSONObject commentJSON = commentsJSONArray.getJSONObject(i).getJSONObject(JSONUtils.DATA_KEY);
                    comments.add(ParseComment.parseSingleComment(commentJSON, 0));
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
            parseCommentsAsyncTaskListener.parseSuccessful(comments, after);
        } catch (JSONException e) {
            e.printStackTrace();
            parseCommentsAsyncTaskListener.parseFailed();
        }
    }

    interface ParseCommentsAsyncTaskListener {
        void parseSuccessful(ArrayList<Comment> comments, String after);

        void parseFailed();
    }
}
