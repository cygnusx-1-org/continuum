package ml.docilealligator.infinityforreddit.asynctasks;

import androidx.annotation.NonNull;
import ml.docilealligator.infinityforreddit.apis.RedditAPI;
import ml.docilealligator.infinityforreddit.utils.APIUtils;
import org.json.JSONObject;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;

public class FetchPostRequirements {

    public static void fetch(Retrofit oauthRetrofit, String accessToken, String subredditName,
                             FetchPostRequirementsListener listener) {
        oauthRetrofit.create(RedditAPI.class)
                .getPostRequirements(APIUtils.getOAuthHeader(accessToken), subredditName)
                .enqueue(new Callback<>() {
                    @Override
                    public void onResponse(@NonNull Call<String> call, @NonNull Response<String> response) {
                        if (!response.isSuccessful() || response.body() == null) {
                            listener.onFail();
                            return;
                        }
                        try {
                            JSONObject json = new JSONObject(response.body());
                            boolean isFlairRequired = !json.isNull("is_flair_required")
                                    && json.getBoolean("is_flair_required");
                            listener.onSuccess(isFlairRequired);
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

    public interface FetchPostRequirementsListener {
        void onSuccess(boolean isFlairRequired);
        void onFail();
    }
}
