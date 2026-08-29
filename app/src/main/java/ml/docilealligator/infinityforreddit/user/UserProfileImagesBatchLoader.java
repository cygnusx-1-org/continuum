package ml.docilealligator.infinityforreddit.user;

import android.os.Handler;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.Executor;
import ml.docilealligator.infinityforreddit.RedditDataRoomDatabase;
import ml.docilealligator.infinityforreddit.apis.RedditAPI;
import ml.docilealligator.infinityforreddit.comment.Comment;
import ml.docilealligator.infinityforreddit.post.Post;
import ml.docilealligator.infinityforreddit.utils.APIUtils;
import ml.docilealligator.infinityforreddit.utils.JSONUtils;
import org.json.JSONException;
import org.json.JSONObject;
import retrofit2.Response;
import retrofit2.Retrofit;

public class UserProfileImagesBatchLoader {
    public static final int BATCH_SIZE = 100;

    private final Executor mExecutor;
    private final Handler mHandler;
    private final RedditDataRoomDatabase mRedditDataRoomDatabase;
    private final Retrofit mRetrofit;
    private final Retrofit mOauthRetrofit;
    private final Map<String, String> mUserFullNameToImageMap;
    private final Queue<String> mUserFullnameQueue;
    private final Map<String, LoadIconListener> mUserFullNameToListenerMap;
    private final List<String> mCallingUserFullnames;
    private final Set<String> mLoadingUserFullNames;
    // Reddit's local user cache is keyed on the username, not on the t2_ fullname the queue
    // carries, so the fullname alone cannot hit it. The callers below know both, so they hand the
    // pairing in and the batch loop resolves the username from here; without it every avatar goes
    // to the network even when it is already stored.
    private final Map<String, String> mUserFullnameToUsernameMap;
    private final Object mUsernameMapLock = new Object();
    private final Object mImageMapLock = new Object();
    private final Object mUserFullnameQueueLock = new Object();
    private final Object mListenerMapLock = new Object();
    private final Object mCallingUserFullnamesLock = new Object();
    private final Object mLoadingSetLock = new Object();
    private boolean mIsLoadingBatch = false;

    public UserProfileImagesBatchLoader(Executor executor, Handler handler, RedditDataRoomDatabase redditDataRoomDatabase,
                                           Retrofit retrofit, Retrofit oauthRetrofit) {
        mExecutor = executor;
        mHandler = handler;
        mRedditDataRoomDatabase = redditDataRoomDatabase;
        mRetrofit = retrofit;
        mOauthRetrofit = oauthRetrofit;
        mUserFullNameToImageMap = new HashMap<>();
        mUserFullnameQueue = new ArrayDeque<>();
        mUserFullNameToListenerMap = new HashMap<>();
        mCallingUserFullnames = new ArrayList<>();
        mLoadingUserFullNames = new HashSet<>();
        mUserFullnameToUsernameMap = new HashMap<>();
    }

    public void loadAuthorImagesInPosts(@Nullable String accessToken, List<Post> posts,
                                        @NonNull LoadIconListener loadIconListener) {
        List<String> userFullnames = new ArrayList<>();
        Map<String, String> userFullnameToUsername = new HashMap<>();
        for (Post post : posts) {
            String authorFullname = post.getAuthorFullname();
            if (authorFullname == null || authorFullname.isEmpty()) {
                continue;
            }
            userFullnames.add(authorFullname);
            userFullnameToUsername.put(authorFullname, post.getAuthor());
        }
        loadAuthorImages(accessToken, userFullnames, userFullnameToUsername, loadIconListener);
    }

    public void loadAuthorImagesInComments(@Nullable String accessToken, List<Comment> comments,
                                           @NonNull LoadIconListener loadIconListener) {
        List<String> userFullnames = new ArrayList<>();
        Map<String, String> userFullnameToUsername = new HashMap<>();
        for (Comment comment : comments) {
            String authorFullName = comment.getAuthorFullName();
            if (authorFullName == null || authorFullName.isEmpty()) {
                continue;
            }
            userFullnames.add(authorFullName);
            String author = comment.getAuthor();
            if (author != null) {
                userFullnameToUsername.put(authorFullName, author);
            }
        }
        loadAuthorImages(accessToken, userFullnames, userFullnameToUsername, loadIconListener);
    }

    private void loadAuthorImages(@Nullable String accessToken, List<String> userFullnames,
                                  Map<String, String> userFullnameToUsername,
                                  @NonNull LoadIconListener loadIconListener) {
        if (userFullnames.isEmpty()) {
            // Nothing carries an author full name, so there is no icon to resolve and nothing to
            // report: LoadIconListener only has a success callback keyed on one. Returning here
            // also keeps the get(0) below in bounds.
            return;
        }

        synchronized (mUsernameMapLock) {
            mUserFullnameToUsernameMap.putAll(userFullnameToUsername);
        }

        String authorFullName = userFullnames.get(0);
        synchronized (mImageMapLock) {
            if (mUserFullNameToImageMap.containsKey(authorFullName)) {
                loadIconListener.loadIconSuccess(authorFullName, mUserFullNameToImageMap.get(authorFullName));
                return;
            }
        }

        synchronized (mListenerMapLock) {
            mUserFullNameToListenerMap.put(authorFullName, loadIconListener);
        }

        synchronized (mUserFullnameQueueLock) {
            mUserFullnameQueue.addAll(userFullnames);
        }

        synchronized (mCallingUserFullnamesLock) {
            mCallingUserFullnames.add(authorFullName);
        }

        if (!mIsLoadingBatch) {
            loadNextBatch(accessToken);
        }
    }

    private void loadNextBatch(@Nullable String accessToken) {
        synchronized (mUserFullnameQueueLock) {
            if (mUserFullnameQueue.isEmpty()) {
                return;
            }
        }

        mIsLoadingBatch = true;

        mExecutor.execute(() -> {
            synchronized (mCallingUserFullnamesLock) {
                Iterator<String> iterator = mCallingUserFullnames.iterator();
                while (iterator.hasNext()) {
                    String userFullname = iterator.next();
                    LoadIconListener loadIconListener;
                    synchronized (mListenerMapLock) {
                        loadIconListener = mUserFullNameToListenerMap.get(userFullname);
                    }

                    if (loadIconListener != null) {
                        synchronized (mImageMapLock) {
                            if (mUserFullNameToImageMap.containsKey(userFullname)) {
                                String url = mUserFullNameToImageMap.get(userFullname);
                                mHandler.post(() -> loadIconListener.loadIconSuccess(userFullname, url));
                                iterator.remove();
                                continue;
                            }
                        }

                        String username;
                        synchronized (mUsernameMapLock) {
                            username = mUserFullnameToUsernameMap.get(userFullname);
                        }
                        UserData userData = username != null
                                ? mRedditDataRoomDatabase.userDao().getUserData(username) : null;
                        if (userData != null) {
                            String iconImageUrl = userData.getIconUrl();
                            synchronized (mImageMapLock) {
                                mUserFullNameToImageMap.put(userFullname, iconImageUrl == null ? "" : iconImageUrl);
                            }
                            mHandler.post(() -> loadIconListener.loadIconSuccess(userFullname, iconImageUrl));
                            synchronized (mListenerMapLock) {
                                mUserFullNameToListenerMap.remove(userFullname);
                            }
                            iterator.remove();
                        }
                    } else {
                        iterator.remove();
                    }
                }
            }

            StringBuilder stringBuilder = new StringBuilder();

            synchronized (mUserFullnameQueueLock) {
                for (int i = 0; i < BATCH_SIZE && !mUserFullnameQueue.isEmpty(); i++) {
                    String userFullname = mUserFullnameQueue.poll();
                    if (userFullname == null || userFullname.isEmpty()) {
                        i--;
                        continue;
                    }

                    boolean alreadyCached;
                    synchronized (mImageMapLock) {
                        alreadyCached = mUserFullNameToImageMap.containsKey(userFullname);
                    }
                    if (!alreadyCached) {
                        stringBuilder.append(userFullname).append(",");
                        synchronized (mLoadingSetLock) {
                            mLoadingUserFullNames.add(userFullname);
                        }
                    } else if (i == 0) {
                        LoadIconListener loadIconListener;
                        synchronized (mListenerMapLock) {
                            loadIconListener = mUserFullNameToListenerMap.get(userFullname);
                        }
                        if (loadIconListener != null) {
                            String url;
                            synchronized (mImageMapLock) {
                                url = mUserFullNameToImageMap.get(userFullname);
                            }
                            mHandler.post(() -> {
                                loadIconListener.loadIconSuccess(userFullname, url);
                            });
                            synchronized (mListenerMapLock) {
                                mUserFullNameToListenerMap.remove(userFullname);
                            }
                        }
                        for (int j = 0; j < BATCH_SIZE - 1 && !mUserFullnameQueue.isEmpty(); j++) {
                            mUserFullnameQueue.poll();
                        }
                        break;
                    }
                }
            }

            if (stringBuilder.length() > 0) {
                stringBuilder.deleteCharAt(stringBuilder.length() - 1);
                try {
                    Response<String> response;
                    if (accessToken != null) {
                        response = mOauthRetrofit.create(RedditAPI.class).loadPartialUserDataOauth(
                                stringBuilder.toString(), APIUtils.getOAuthHeader(accessToken)).execute();
                    } else {
                        response = mRetrofit.create(RedditAPI.class).loadPartialUserData(stringBuilder.toString()).execute();
                    }
                    String responseBody = response.body();
                    if (response.isSuccessful() && responseBody != null) {
                        parseUserProfileImages(responseBody);
                        callListenerAndLoadNextBatch(accessToken, true);
                    } else {
                        // A successful-but-empty body used to reach new JSONObject(null), which
                        // throws NPE rather than JSONException and so escaped onto the executor
                        // thread instead of falling through to the failure path here.
                        callListenerAndLoadNextBatch(accessToken, false);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    callListenerAndLoadNextBatch(accessToken, false);
                }
            } else {
                mIsLoadingBatch = false;
                loadNextBatch(accessToken);
            }
        });
    }

    @WorkerThread
    private void parseUserProfileImages(String response) {
        try {
            JSONObject jsonResponse = new JSONObject(response);
            synchronized (mLoadingSetLock) {
                for (String s : mLoadingUserFullNames) {
                    try {
                        String imageUrl = jsonResponse.getJSONObject(s).getString(JSONUtils.PROFILE_IMG_KEY).replace("&amp;","&");
                        synchronized (mImageMapLock) {
                            mUserFullNameToImageMap.put(s, imageUrl);
                        }
                    } catch (JSONException e) {
                        Log.e("UserProfileImagesBatchLoader", "parseUserProfileImages failed", e);
                    }
                }
            }
        } catch (JSONException e) {
            Log.e("UserProfileImagesBatchLoader", "parseUserProfileImages failed", e);
        }
    }

    private void callListenerAndLoadNextBatch(@Nullable String accessToken, boolean loadSuccessful) {
        synchronized (mLoadingSetLock) {
            for (String s : mLoadingUserFullNames) {
                LoadIconListener loadIconListener;
                synchronized (mListenerMapLock) {
                    loadIconListener = mUserFullNameToListenerMap.get(s);
                }
                if (loadIconListener != null) {
                    String imageUrl;
                    synchronized (mImageMapLock) {
                        imageUrl = mUserFullNameToImageMap.get(s);
                    }
                    mHandler.post(() -> {
                        loadIconListener.loadIconSuccess(s, imageUrl);
                    });
                    synchronized (mListenerMapLock) {
                        mUserFullNameToListenerMap.remove(s);
                    }
                }
                if (!loadSuccessful) {
                    synchronized (mImageMapLock) {
                        if (!mUserFullNameToImageMap.containsKey(s)) {
                            mUserFullNameToImageMap.put(s, "");
                        }
                    }
                }
            }

            mLoadingUserFullNames.clear();
        }

        mIsLoadingBatch = false;
        loadNextBatch(accessToken);
    }

    public interface LoadIconListener {
        void loadIconSuccess(String subredditOrUserName, @Nullable String iconUrl);
    }
}
