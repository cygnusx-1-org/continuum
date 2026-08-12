package ml.docilealligator.infinityforreddit.comment;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ml.docilealligator.infinityforreddit.apis.ArcticShiftAPI;
import ml.docilealligator.infinityforreddit.post.FetchRemovedPost;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;

/**
 * Recovers the body of a removed/deleted comment from the Arctic Shift archive, mirroring
 * {@link FetchRemovedPost}. Comments carry no title and no link/url of their own, so this is a
 * strict simplification: the archive record is pure body text.
 */
public class FetchRemovedComment {

    public static void fetchRemovedComment(Retrofit arcticShiftRetrofit, Comment comment, FetchRemovedCommentListener listener) {
        String id = comment.getId();
        if (id == null) {
            listener.fetchFailed();
            return;
        }
        arcticShiftRetrofit.create(ArcticShiftAPI.class).getRemovedComment(id)
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
                        listener.fetchSuccess(result.body, result.author, result.authorFlair, result.authorFlairHTML);
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
    @Nullable
    private static Result parseResponse(String responseBody) {
        try {
            JSONObject obj = new JSONObject(responseBody);
            JSONArray data = obj.optJSONArray("data");
            if (data == null || data.length() == 0) {
                return null;
            }
            JSONObject comment = data.optJSONObject(0);
            if (comment == null) {
                return null;
            }

            // Reject a body that is itself a removal placeholder: when the archive only ever ingested
            // the comment after Reddit scrubbed it, there is no original to recover, and handing back
            // the placeholder would masquerade as a successful recovery.
            String body = readString(comment, "body");
            if (body == null || FetchRemovedPost.isRemovalPlaceholder(body)) {
                return null;
            }

            // Keep the archived author only when it is a real username: a deleted-account comment can
            // be archived with a "[deleted]" author too, and restoring that over the visible
            // "[deleted]" would be a no-op dressed up as a recovery. Flair is only meaningful
            // alongside a recovered author, so it is parsed only in that case.
            String author = readString(comment, "author");
            if (FetchRemovedPost.isRemovalPlaceholder(author)) {
                author = null;
            }
            String authorFlair = author == null ? null : FetchRemovedPost.parseAuthorFlairText(comment);
            String authorFlairHTML = author == null ? null : FetchRemovedPost.parseAuthorFlairHtml(comment);

            return new Result(body, author, authorFlair, authorFlairHTML);
        } catch (JSONException e) {
            return null;
        }
    }

    @Nullable
    private static String readString(JSONObject obj, String key) {
        // isNull() is true for both an absent key and a JSON null; optString(key, null) alone would
        // return the literal string "null" for the latter (org.json quirk).
        if (obj.isNull(key)) {
            return null;
        }
        String value = obj.optString(key, null);
        return value == null || value.trim().isEmpty() ? null : value;
    }

    private static final class Result {
        final String body;
        @Nullable
        final String author;
        @Nullable
        final String authorFlair;
        @Nullable
        final String authorFlairHTML;

        Result(String body, @Nullable String author, @Nullable String authorFlair, @Nullable String authorFlairHTML) {
            this.body = body;
            this.author = author;
            this.authorFlair = authorFlair;
            this.authorFlairHTML = authorFlairHTML;
        }
    }

    public interface FetchRemovedCommentListener {
        void fetchSuccess(String recoveredMarkdown, @Nullable String recoveredAuthor,
                          @Nullable String recoveredAuthorFlair, @Nullable String recoveredAuthorFlairHTML);

        void fetchFailed();
    }
}
