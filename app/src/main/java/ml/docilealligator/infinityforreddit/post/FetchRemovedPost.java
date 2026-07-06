package ml.docilealligator.infinityforreddit.post;

import androidx.annotation.NonNull;
import ml.docilealligator.infinityforreddit.apis.ArcticShiftAPI;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;

public class FetchRemovedPost {

    public static void fetchRemovedPost(Retrofit arcticShiftRetrofit, Post post, FetchRemovedPostListener listener) {
        arcticShiftRetrofit.create(ArcticShiftAPI.class).getRemovedPost(post.getId())
                .enqueue(new Callback<String>() {
                    @Override
                    public void onResponse(@NonNull Call<String> call, @NonNull Response<String> response) {
                        if (!response.isSuccessful() || response.body() == null) {
                            listener.fetchFailed();
                            return;
                        }

                        Result result = parseResponse(response.body());
                        if (result == null) {
                            listener.fetchFailed();
                            return;
                        }

                        if (result.title != null) {
                            post.setTitle(result.title);
                        }
                        if (result.body != null) {
                            post.setSelfText(result.body);
                            post.setSelfTextPlain("");
                            post.setSelfTextPlainTrimmed("");
                        }
                        listener.fetchSuccess(post);
                    }

                    @Override
                    public void onFailure(@NonNull Call<String> call, @NonNull Throwable t) {
                        listener.fetchFailed();
                    }
                });
    }

    /**
     * Returns null (rather than an empty {@link Result}) for any unexpected payload shape so a
     * schema drift or error envelope from the archive can't be mistaken for "found but empty".
     */
    private static Result parseResponse(String responseBody) {
        try {
            JSONObject obj = new JSONObject(responseBody);
            JSONArray data = obj.optJSONArray("data");
            if (data == null || data.length() == 0) {
                return null;
            }
            JSONObject post = data.optJSONObject(0);
            if (post == null) {
                return null;
            }

            String title = readString(post, "title");
            String body = readString(post, "selftext");
            if (body != null && (body.equals("[removed]") || body.equals("[deleted]"))) {
                body = null;
            }

            if (title == null && body == null) {
                return null;
            }

            return new Result(title, body);
        } catch (JSONException e) {
            return null;
        }
    }

    private static String readString(JSONObject obj, String key) {
        String value = obj.optString(key, null);
        return value == null || value.trim().isEmpty() ? null : value;
    }

    private static final class Result {
        final String title;
        final String body;

        Result(String title, String body) {
            this.title = title;
            this.body = body;
        }
    }

    public interface FetchRemovedPostListener {
        void fetchSuccess(Post post);

        void fetchFailed();
    }
}
