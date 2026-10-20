package ml.docilealligator.infinityforreddit.user;

import android.os.Handler;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import java.util.ArrayList;
import java.util.concurrent.Executor;
import ml.docilealligator.infinityforreddit.apis.RedditAPI;
import ml.docilealligator.infinityforreddit.utils.APIUtils;
import ml.docilealligator.infinityforreddit.utils.FlairUtils;
import ml.docilealligator.infinityforreddit.utils.JSONUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;

public class FetchUserFlairs {
    public static void fetchUserFlairsInSubreddit(Executor executor, Handler handler, Retrofit oauthRetrofit, @Nullable String accessToken, String subredditName, FetchUserFlairsInSubredditListener fetchUserFlairsInSubredditListener) {
        oauthRetrofit.create(RedditAPI.class).getUserFlairs(APIUtils.getOAuthHeader(accessToken), subredditName)
                .enqueue(new Callback<>() {
                    @Override
                    public void onResponse(@NonNull Call<String> call, @NonNull Response<String> response) {
                        if (response.isSuccessful()) {
                            executor.execute(() -> {
                                ArrayList<UserFlair> userFlairs = parseUserFlairs(response.body());
                                if (userFlairs == null) {
                                    handler.post(fetchUserFlairsInSubredditListener::fetchFailed);
                                } else {
                                    handler.post(() -> fetchUserFlairsInSubredditListener.fetchSuccessful(userFlairs));
                                }
                            });
                        } else if (response.code() == 403) {
                            //No flairs
                            fetchUserFlairsInSubredditListener.fetchSuccessful(null);
                        } else {
                            fetchUserFlairsInSubredditListener.fetchFailed();
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<String> call, @NonNull Throwable throwable) {
                        fetchUserFlairsInSubredditListener.fetchFailed();
                    }
                });
    }

    @WorkerThread
    @Nullable
    private static ArrayList<UserFlair> parseUserFlairs(@Nullable String response) {
        try {
            JSONArray jsonArray = new JSONArray(response);
            ArrayList<UserFlair> userFlairs = new ArrayList<>();
            for (int i = 0; i < jsonArray.length(); i++) {
                try {
                    JSONObject userFlairObject = jsonArray.getJSONObject(i);
                    String id = userFlairObject.getString(JSONUtils.ID_KEY);
                    String text = userFlairObject.getString(JSONUtils.TEXT_KEY);
                    boolean editable = userFlairObject.getBoolean(JSONUtils.TEXT_EDITABLE_KEY);
                    int maxEmojis = userFlairObject.getInt(JSONUtils.MAX_EMOJIS_KEY);

                    String htmlText = FlairUtils.richtextToHtml(userFlairObject.optJSONArray(JSONUtils.RICHTEXT_KEY));

                    userFlairs.add(new UserFlair(id, text, htmlText, editable, maxEmojis));
                } catch (JSONException e) {
                    Log.e("FetchUserFlairs", "parseUserFlairs failed", e);
                }
            }
            return userFlairs;
        } catch (JSONException e) {
            Log.e("FetchUserFlairs", "parseUserFlairs failed", e);
        }
        return null;
    }

    public interface FetchUserFlairsInSubredditListener {
        void fetchSuccessful(@Nullable ArrayList<UserFlair> userFlairs);

        void fetchFailed();
    }
}
