package ml.docilealligator.infinityforreddit.post;

import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.paging.ListenableFuturePagingSource;
import androidx.paging.PagingState;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

import ml.docilealligator.infinityforreddit.readpost.ReadPostsListInterface;
import ml.docilealligator.infinityforreddit.thing.SortType;
import ml.docilealligator.infinityforreddit.account.Account;
import ml.docilealligator.infinityforreddit.apis.RedditAPI;
import ml.docilealligator.infinityforreddit.postfilter.PostFilter;
import ml.docilealligator.infinityforreddit.utils.APIUtils;
import ml.docilealligator.infinityforreddit.utils.SharedPreferencesUtils;
import retrofit2.HttpException;
import retrofit2.Response;
import retrofit2.Retrofit;

public class PostPagingSource extends ListenableFuturePagingSource<String, Post> {
    public static final int TYPE_FRONT_PAGE = 0;
    public static final int TYPE_SUBREDDIT = 1;
    public static final int TYPE_USER = 2;
    public static final int TYPE_SEARCH = 3;
    public static final int TYPE_MULTI_REDDIT = 4;
    public static final int TYPE_ANONYMOUS_FRONT_PAGE = 5;
    public static final int TYPE_ANONYMOUS_MULTIREDDIT = 6;

    public static final String USER_WHERE_SUBMITTED = "submitted";
    public static final String USER_WHERE_UPVOTED = "upvoted";
    public static final String USER_WHERE_DOWNVOTED = "downvoted";
    public static final String USER_WHERE_HIDDEN = "hidden";
    public static final String USER_WHERE_SAVED = "saved";

    private static final int HTTP_INTERNAL_SERVER_ERROR = 500;

    private final Executor executor;
    private final Retrofit retrofit;
    private final String accessToken;
    private final String accountName;
    private final SharedPreferences sharedPreferences;
    private final SharedPreferences postFeedScrolledPositionSharedPreferences;
    private String subredditOrUserName;
    private String query;
    private String trendingSource;
    private final int postType;
    private final SortType sortType;
    private final PostFilter postFilter;
    private final ReadPostsListInterface readPostsList;
    private String userWhere;
    private String multiRedditPath;
    private final LinkedHashSet<Post> postLinkedHashSet;
    private String previousLastItem;
    private List<String> multiRedditUsernames;
    private boolean multiRedditUsernamesFetched = false;
    private String subredditOnlyName;

    PostPagingSource(Executor executor, Retrofit retrofit, @Nullable String accessToken, @NonNull String accountName,
                     SharedPreferences sharedPreferences,
                     SharedPreferences postFeedScrolledPositionSharedPreferences, int postType,
                     SortType sortType, PostFilter postFilter, ReadPostsListInterface readPostsList) {
        this.executor = executor;
        this.retrofit = retrofit;
        this.accessToken = accessToken;
        this.accountName = accountName;
        this.sharedPreferences = sharedPreferences;
        this.postFeedScrolledPositionSharedPreferences = postFeedScrolledPositionSharedPreferences;
        this.postType = postType;
        this.sortType = sortType == null ? new SortType(SortType.Type.BEST) : sortType;
        this.postFilter = postFilter;
        this.readPostsList = readPostsList;
        postLinkedHashSet = new LinkedHashSet<>();
    }

    // PostPagingSource.TYPE_SUBREDDIT || PostPagingSource.TYPE_ANONYMOUS_FRONT_PAGE || PostPagingSource.TYPE_ANONYMOUS_MULTIREDDIT:
    PostPagingSource(Executor executor, Retrofit retrofit, @Nullable String accessToken, @NonNull String accountName,
                     SharedPreferences sharedPreferences, SharedPreferences postFeedScrolledPositionSharedPreferences,
                     String name, int postType, SortType sortType, PostFilter postFilter,
                     ReadPostsListInterface readPostsList) {
        this.executor = executor;
        this.retrofit = retrofit;
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
            if ("popular".equals(name) || "all".equals(name)) {
                this.sortType = new SortType(SortType.Type.HOT);
            } else {
                this.sortType = new SortType(SortType.Type.BEST);
            }
        } else {
            this.sortType = sortType;
        }
        this.postFilter = postFilter;
        this.readPostsList = readPostsList;
        postLinkedHashSet = new LinkedHashSet<>();
    }

    // PostPagingSource.TYPE_MULTI_REDDIT
    PostPagingSource(Executor executor, Retrofit retrofit, @Nullable String accessToken, @NonNull String accountName,
                     SharedPreferences sharedPreferences, SharedPreferences postFeedScrolledPositionSharedPreferences,
                     String path, String query, int postType, SortType sortType, PostFilter postFilter,
                     ReadPostsListInterface readPostsList) {
        this.executor = executor;
        this.retrofit = retrofit;
        this.accessToken = accessToken;
        this.accountName = accountName;
        this.sharedPreferences = sharedPreferences;
        this.postFeedScrolledPositionSharedPreferences = postFeedScrolledPositionSharedPreferences;
        if (path.endsWith("/")) {
            multiRedditPath = path.substring(0, path.length() - 1);
        } else {
            multiRedditPath = path;
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
        postLinkedHashSet = new LinkedHashSet<>();
    }

    PostPagingSource(Executor executor, Retrofit retrofit, @Nullable String accessToken, @NonNull String accountName,
                     SharedPreferences sharedPreferences, SharedPreferences postFeedScrolledPositionSharedPreferences,
                     String subredditOrUserName, int postType, SortType sortType, PostFilter postFilter,
                     String where, ReadPostsListInterface readPostsList) {
        this.executor = executor;
        this.retrofit = retrofit;
        this.accessToken = accessToken;
        this.accountName = accountName;
        this.sharedPreferences = sharedPreferences;
        this.postFeedScrolledPositionSharedPreferences = postFeedScrolledPositionSharedPreferences;
        this.subredditOrUserName = subredditOrUserName;
        this.postType = postType;
        this.sortType = sortType == null ? new SortType(SortType.Type.NEW) : sortType;
        this.postFilter = postFilter;
        userWhere = where;
        this.readPostsList = readPostsList;
        postLinkedHashSet = new LinkedHashSet<>();
    }

    PostPagingSource(Executor executor, Retrofit retrofit, @Nullable String accessToken, @NonNull String accountName,
                     SharedPreferences sharedPreferences, SharedPreferences postFeedScrolledPositionSharedPreferences,
                     String subredditOrUserName, String query, String trendingSource, int postType,
                     SortType sortType, PostFilter postFilter, ReadPostsListInterface readPostsList) {
        this.executor = executor;
        this.retrofit = retrofit;
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
        postLinkedHashSet = new LinkedHashSet<>();
        this.readPostsList = readPostsList;
    }

    @Nullable
    @Override
    public String getRefreshKey(@NonNull PagingState<String, Post> pagingState) {
        return null;
    }

    @NonNull
    @Override
    public ListenableFuture<LoadResult<String, Post>> loadFuture(@NonNull LoadParams<String> loadParams) {
        RedditAPI api = retrofit.create(RedditAPI.class);
        switch (postType) {
            case TYPE_FRONT_PAGE:
                return loadHomePosts(loadParams, api);
            case TYPE_SUBREDDIT:
                return loadSubredditPosts(loadParams, api);
            case TYPE_USER:
                return loadUserPosts(loadParams, api);
            case TYPE_SEARCH:
                return loadSearchPosts(loadParams, api);
            case TYPE_MULTI_REDDIT:
                return loadMultiRedditPosts(loadParams, api);
            default:
                return loadAnonymousFrontPageOrMultiredditPosts(loadParams, api);
        }
    }

    public LoadResult<String, Post> transformData(Response<String> response) {
        if (response.isSuccessful()) {
            String responseString = response.body();
            LinkedHashSet<Post> newPosts = ParsePost.parsePostsSync(responseString, -1, postFilter, readPostsList);
            String lastItem = ParsePost.getLastItem(responseString);
            if (newPosts == null) {
                return new LoadResult.Error<>(new Exception("Error parsing posts"));
            } else {
                int currentPostsSize = postLinkedHashSet.size();
                if (lastItem != null && lastItem.equals(previousLastItem)) {
                    lastItem = null;
                }
                previousLastItem = lastItem;

                postLinkedHashSet.addAll(newPosts);
                if (currentPostsSize == postLinkedHashSet.size()) {
                    return new LoadResult.Page<>(new ArrayList<>(), null, lastItem);
                } else {
                    return new LoadResult.Page<>(new ArrayList<>(postLinkedHashSet).subList(currentPostsSize, postLinkedHashSet.size()), null, lastItem);
                }
            }
        } else {
            return new LoadResult.Error<>(new Exception("Error getting response"));
        }
    }

    private ListenableFuture<LoadResult<String, Post>> loadHomePosts(@NonNull LoadParams<String> loadParams, RedditAPI api) {
        ListenableFuture<Response<String>> bestPost;
        String afterKey;
        if (loadParams.getKey() == null) {
            boolean savePostFeedScrolledPosition = sortType != null && sortType.getType() == SortType.Type.BEST && sharedPreferences.getBoolean(SharedPreferencesUtils.SAVE_FRONT_PAGE_SCROLLED_POSITION, false);
            if (savePostFeedScrolledPosition) {
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
        if (accountName.equals(Account.ANONYMOUS_ACCOUNT)) {
            return api.getSubredditBestPostsListenableFuture(subredditOrUserName, sortType.getType(),
                    sortType.getTime(), loadParams.getKey(), limit);
        } else {
            return api.getSubredditBestPostsOauthListenableFuture(subredditOrUserName, sortType.getType(),
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

    private ListenableFuture<LoadResult<String, Post>> loadUserPosts(@NonNull LoadParams<String> loadParams, RedditAPI api) {
        ListenableFuture<Response<String>> userPosts;
        if (accountName.equals(Account.ANONYMOUS_ACCOUNT)) {
            userPosts = api.getUserPostsListenableFuture(subredditOrUserName, loadParams.getKey(), sortType.getType(),
                    sortType.getTime());
        } else {
            userPosts = api.getUserPostsOauthListenableFuture(APIUtils.AUTHORIZATION_BASE + accessToken,
                    subredditOrUserName, userWhere, loadParams.getKey(), USER_WHERE_SUBMITTED.equals(userWhere) ? sortType.getType() : null, USER_WHERE_SUBMITTED.equals(userWhere) ? sortType.getTime() : null);
        }

        ListenableFuture<LoadResult<String, Post>> pageFuture = Futures.transform(userPosts, this::transformData, executor);

        ListenableFuture<LoadResult<String, Post>> partialLoadResultFuture =
                Futures.catching(pageFuture, HttpException.class,
                        LoadResult.Error::new, executor);

        return Futures.catching(partialLoadResultFuture,
                IOException.class, LoadResult.Error::new, executor);
    }

    private ListenableFuture<LoadResult<String, Post>> loadSearchPosts(@NonNull LoadParams<String> loadParams, RedditAPI api) {
        ListenableFuture<Response<String>> searchPosts;
        if (subredditOrUserName == null) {
            if (accountName.equals(Account.ANONYMOUS_ACCOUNT)) {
                searchPosts = api.searchPostsListenableFuture(query, loadParams.getKey(), sortType.getType(), sortType.getTime(),
                        trendingSource);
            } else {
                searchPosts = api.searchPostsOauthListenableFuture(query, loadParams.getKey(), sortType.getType(),
                        sortType.getTime(), trendingSource, APIUtils.getOAuthHeader(accessToken));
            }
        } else {
            if (accountName.equals(Account.ANONYMOUS_ACCOUNT)) {
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
                    sortType.getTime(), APIUtils.getOAuthHeader(accessToken));
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

    private List<String> getUsersToFetch(Map<String, String> currentUserAfterKeys, boolean isInitialLoad) {
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
            RedditAPI api, Map<String, String> currentUserAfterKeys, boolean isInitialLoad) {
        List<ListenableFuture<Response<String>>> futures = new ArrayList<>();
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

    private String getMainAfterKey(String compositeKey) {
        if (compositeKey == null || !compositeKey.startsWith("{")) return compositeKey;
        try {
            String m = new JSONObject(compositeKey).optString("m", null);
            return (m != null && !m.isEmpty()) ? m : null;
        } catch (JSONException e) {
            return compositeKey;
        }
    }

    private Map<String, String> parseUserAfterKeys(String compositeKey) {
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
            Response<String> mainResponse, List<Response<String>> userResponses,
            List<String> usersToFetch) {
        int currentPostsSize = postLinkedHashSet.size();

        // Parse main multi-reddit response
        String mainLastItem = null;
        if (mainResponse != null && mainResponse.isSuccessful()) {
            String responseString = mainResponse.body();
            LinkedHashSet<Post> newPosts = ParsePost.parsePostsSync(responseString, -1, postFilter, readPostsList);
            mainLastItem = ParsePost.getLastItem(responseString);
            if (newPosts != null) {
                postLinkedHashSet.addAll(newPosts);
            }
        }

        // If no user responses, return main response only
        if (userResponses == null || userResponses.isEmpty()) {
            if (mainLastItem != null && mainLastItem.equals(previousLastItem)) {
                mainLastItem = null;
            }
            previousLastItem = mainLastItem;
            int newSize = postLinkedHashSet.size();
            if (newSize == currentPostsSize) {
                return new LoadResult.Page<>(new ArrayList<>(), null, mainLastItem);
            }
            return new LoadResult.Page<>(new ArrayList<>(postLinkedHashSet).subList(currentPostsSize, newSize), null, mainLastItem);
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
                        postLinkedHashSet.addAll(userPosts);
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

    private LoadResult<String, Post> buildSortedPage(int currentPostsSize, String nextKey) {
        int newSize = postLinkedHashSet.size();
        if (newSize == currentPostsSize) {
            return new LoadResult.Page<>(new ArrayList<>(), null, nextKey);
        }
        List<Post> resultPosts = new ArrayList<>(postLinkedHashSet).subList(currentPostsSize, newSize);
        resultPosts = new ArrayList<>(resultPosts);
        resultPosts.sort((a, b) -> Long.compare(b.getPostTimeMillis(), a.getPostTimeMillis()));
        return new LoadResult.Page<>(resultPosts, null, nextKey);
    }

    private ListenableFuture<LoadResult<String, Post>> catchErrors(ListenableFuture<LoadResult<String, Post>> future) {
        ListenableFuture<LoadResult<String, Post>> partial =
                Futures.catching(future, HttpException.class, LoadResult.Error::new, executor);
        return Futures.catching(partial, IOException.class, LoadResult.Error::new, executor);
    }

    private ListenableFuture<LoadResult<String, Post>> loadAnonymousFrontPageOrMultiredditPosts(@NonNull LoadParams<String> loadParams, RedditAPI api) {
        // For anonymous multireddit, extract user entries from concatenated name on first call
        if (postType == TYPE_ANONYMOUS_MULTIREDDIT && !multiRedditUsernamesFetched) {
            multiRedditUsernamesFetched = true;
            String[] parts = subredditOrUserName.split("\\+");
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
            mainFuture = Futures.immediateFuture(null);
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
