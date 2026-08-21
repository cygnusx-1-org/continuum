package ml.docilealligator.infinityforreddit.thing;

import android.os.Handler;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import ml.docilealligator.infinityforreddit.RedditDataRoomDatabase;
import ml.docilealligator.infinityforreddit.account.Account;
import ml.docilealligator.infinityforreddit.apis.RedditAPI;
import ml.docilealligator.infinityforreddit.asynctasks.InsertSubscribedThings;
import ml.docilealligator.infinityforreddit.subscribedsubreddit.SubscribedSubredditData;
import ml.docilealligator.infinityforreddit.subscribeduser.SubscribedUserData;
import ml.docilealligator.infinityforreddit.utils.APIUtils;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;

public class FavoriteThing {
    public static void favoriteSubreddit(Executor executor, Handler handler, Retrofit oauthRetrofit,
                                         RedditDataRoomDatabase redditDataRoomDatabase,
                                         @Nullable String accessToken, @NonNull String accountName,
                                         SubscribedSubredditData subscribedSubredditData,
                                         FavoriteThingListener favoriteThingListener) {
        if (accountName.equals(Account.ANONYMOUS_ACCOUNT)) {
            InsertSubscribedThings.insertSubscribedThings(executor, handler, redditDataRoomDatabase, subscribedSubredditData,
                    favoriteThingListener::success);
        } else {
            Map<String, String> params = new HashMap<>();
            params.put(APIUtils.SR_NAME_KEY, subscribedSubredditData.getName());
            params.put(APIUtils.MAKE_FAVORITE_KEY, "true");
            oauthRetrofit.create(RedditAPI.class).favoriteThing(APIUtils.getOAuthHeader(accessToken), params).enqueue(new Callback<String>() {
                @Override
                public void onResponse(@NonNull Call<String> call, @NonNull Response<String> response) {
                    if (response.isSuccessful()) {
                        InsertSubscribedThings.insertSubscribedThings(executor, handler, redditDataRoomDatabase, subscribedSubredditData,
                                favoriteThingListener::success);
                    } else {
                        favoriteThingListener.failed();
                    }
                }

                @Override
                public void onFailure(@NonNull Call<String> call, @NonNull Throwable t) {
                    favoriteThingListener.failed();
                }
            });
        }
    }

    public static void unfavoriteSubreddit(Executor executor, Handler handler, Retrofit oauthRetrofit,
                                           RedditDataRoomDatabase redditDataRoomDatabase,
                                           @Nullable String accessToken, @NonNull String accountName,
                                           SubscribedSubredditData subscribedSubredditData,
                                           FavoriteThingListener favoriteThingListener) {
        if (accountName.equals(Account.ANONYMOUS_ACCOUNT)) {
            InsertSubscribedThings.insertSubscribedThings(executor, handler, redditDataRoomDatabase,
                    subscribedSubredditData, favoriteThingListener::success);
        } else {
            Map<String, String> params = new HashMap<>();
            params.put(APIUtils.SR_NAME_KEY, subscribedSubredditData.getName());
            params.put(APIUtils.MAKE_FAVORITE_KEY, "false");
            oauthRetrofit.create(RedditAPI.class).favoriteThing(APIUtils.getOAuthHeader(accessToken), params).enqueue(new Callback<String>() {
                @Override
                public void onResponse(@NonNull Call<String> call, @NonNull Response<String> response) {
                    if (response.isSuccessful()) {
                        InsertSubscribedThings.insertSubscribedThings(executor, handler, redditDataRoomDatabase,
                                subscribedSubredditData, favoriteThingListener::success);
                    } else {
                        favoriteThingListener.failed();
                    }
                }

                @Override
                public void onFailure(@NonNull Call<String> call, @NonNull Throwable t) {
                    favoriteThingListener.failed();
                }
            });
        }
    }

    public static void favoriteUser(Executor executor, Handler handler, Retrofit oauthRetrofit,
                                    RedditDataRoomDatabase redditDataRoomDatabase,
                                    @Nullable String accessToken, @NonNull String accountName,
                                    SubscribedUserData subscribedUserData,
                                    FavoriteThingListener favoriteThingListener) {
        if (isLocalOnly(accountName, subscribedUserData)) {
            updateUserFavorite(executor, handler, redditDataRoomDatabase, accountName,
                    subscribedUserData.getName(), true, favoriteThingListener);
        } else {
            Map<String, String> params = new HashMap<>();
            params.put(APIUtils.SR_NAME_KEY, "u_" + subscribedUserData.getName());
            params.put(APIUtils.MAKE_FAVORITE_KEY, "true");
            oauthRetrofit.create(RedditAPI.class).favoriteThing(APIUtils.getOAuthHeader(accessToken), params).enqueue(new Callback<String>() {
                @Override
                public void onResponse(@NonNull Call<String> call, @NonNull Response<String> response) {
                    if (response.isSuccessful()) {
                        updateUserFavorite(executor, handler, redditDataRoomDatabase, accountName,
                                subscribedUserData.getName(), true, favoriteThingListener);
                    } else {
                        favoriteThingListener.failed();
                    }
                }

                @Override
                public void onFailure(@NonNull Call<String> call, @NonNull Throwable t) {
                    favoriteThingListener.failed();
                }
            });
        }
    }

    public static void unfavoriteUser(Executor executor, Handler handler, Retrofit oauthRetrofit,
                                      RedditDataRoomDatabase redditDataRoomDatabase,
                                      @Nullable String accessToken, @NonNull String accountName,
                                      SubscribedUserData subscribedUserData,
                                      FavoriteThingListener favoriteThingListener) {
        if (isLocalOnly(accountName, subscribedUserData)) {
            updateUserFavorite(executor, handler, redditDataRoomDatabase, accountName,
                    subscribedUserData.getName(), false, favoriteThingListener);
        } else {
            Map<String, String> params = new HashMap<>();
            params.put(APIUtils.SR_NAME_KEY, "u_" + subscribedUserData.getName());
            params.put(APIUtils.MAKE_FAVORITE_KEY, "false");
            oauthRetrofit.create(RedditAPI.class).favoriteThing(APIUtils.getOAuthHeader(accessToken), params).enqueue(new Callback<String>() {
                @Override
                public void onResponse(@NonNull Call<String> call, @NonNull Response<String> response) {
                    if (response.isSuccessful()) {
                        updateUserFavorite(executor, handler, redditDataRoomDatabase, accountName,
                                subscribedUserData.getName(), false, favoriteThingListener);
                    } else {
                        favoriteThingListener.failed();
                    }
                }

                @Override
                public void onFailure(@NonNull Call<String> call, @NonNull Throwable t) {
                    favoriteThingListener.failed();
                }
            });
        }
    }

    /**
     * Whether the favourite is ours alone to record. Reddit's favourite flag applies to things the
     * account follows, so a user who is only saved locally -- like anything under the anonymous
     * account -- has nothing there to favourite, and asking would just fail.
     */
    private static boolean isLocalOnly(@NonNull String accountName, SubscribedUserData subscribedUserData) {
        return accountName.equals(Account.ANONYMOUS_ACCOUNT) || !subscribedUserData.isFollowed();
    }

    /**
     * Writes only the favourite column. Replacing the whole row here would write back the caller's
     * snapshot of {@code is_saved}, which a tap on the row's save ribbon may have changed while the
     * favourite request was in flight.
     */
    private static void updateUserFavorite(Executor executor, Handler handler,
                                           RedditDataRoomDatabase redditDataRoomDatabase,
                                           @NonNull String accountName, String username, boolean favorite,
                                           FavoriteThingListener favoriteThingListener) {
        executor.execute(() -> {
            redditDataRoomDatabase.subscribedUserDao().updateFavorite(username, accountName, favorite);
            handler.post(favoriteThingListener::success);
        });
    }

    public interface FavoriteThingListener {
        void success();

        void failed();
    }
}
