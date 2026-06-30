package ml.docilealligator.infinityforreddit.asynctasks;

import androidx.annotation.NonNull;
import java.util.HashSet;
import java.util.Set;
import ml.docilealligator.infinityforreddit.apis.RedditAPI;
import ml.docilealligator.infinityforreddit.utils.APIUtils;
import org.json.JSONArray;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;

public class FetchCrosspostableSubreddits {

    public static void fetch(Retrofit oauthRetrofit, String accessToken, String sourceSubredditName,
                             FetchCrosspostableSubredditsListener listener) {
        oauthRetrofit.create(RedditAPI.class)
                .getCrosspostableSubreddits(APIUtils.getOAuthHeader(accessToken), sourceSubredditName)
                .enqueue(new Callback<>() {
                    @Override
                    public void onResponse(@NonNull Call<String> call, @NonNull Response<String> response) {
                        if (!response.isSuccessful() || response.body() == null) {
                            listener.onFail();
                            return;
                        }
                        try {
                            JSONArray arr = new JSONArray(response.body());
                            Set<String> result = new HashSet<>(arr.length());
                            for (int i = 0; i < arr.length(); i++) {
                                result.add(arr.getString(i).toLowerCase());
                            }
                            listener.onSuccess(result);
                        } catch (Exception e) {
                            listener.onFail();
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<String> call, @NonNull Throwable t) {
                        listener.onFail();
                    }
                });
    }

    public interface FetchCrosspostableSubredditsListener {
        void onSuccess(Set<String> allowedSubreddits);
        void onFail();
    }
}
