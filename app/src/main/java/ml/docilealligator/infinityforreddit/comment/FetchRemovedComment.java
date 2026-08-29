package ml.docilealligator.infinityforreddit.comment;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
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
 * <p>
 * This is the single-comment path behind the per-comment "Recover comment" action; whole-thread
 * recovery batches the same endpoint through {@code RecoverRemovedComments} and shares the parsing
 * below.
 */
public class FetchRemovedComment {

    public static void fetchRemovedComment(Retrofit arcticShiftRetrofit, Comment comment, FetchRemovedCommentListener listener) {
        String id = comment.getId();
        if (id == null) {
            listener.fetchFailed();
            return;
        }
        arcticShiftRetrofit.create(ArcticShiftAPI.class).getRemovedComments(id)
                .enqueue(new Callback<String>() {
                    @Override
                    public void onResponse(@NonNull Call<String> call, @NonNull Response<String> response) {
                        if (!response.isSuccessful() || response.body() == null) {
                            listener.fetchFailed();
                            return;
                        }

                        Result result = parseResults(response.body()).get(id);
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
     * Parses an {@code api/comments/ids} response body into recovered comments keyed by comment id.
     * Returns an empty map for any unexpected payload shape, so a schema drift or an error envelope
     * from the archive can't be mistaken for "found but empty".
     */
    @NonNull
    public static Map<String, Result> parseResults(String responseBody) {
        try {
            return parseResults(new JSONObject(responseBody));
        } catch (JSONException e) {
            return Collections.emptyMap();
        }
    }

    /**
     * As {@link #parseResults(String)}, for a payload the caller has already parsed — bulk recovery
     * reads {@code error} off the envelope first (the archive signals its own throttle there, with
     * an HTTP 200), and re-parsing a batch of several hundred comments to get at the data would be
     * wasteful.
     * <p>
     * A comment the archive has nothing usable for is absent from the map rather than present with
     * an empty {@link Result}, keeping "not archived" and "recovered" distinguishable.
     */
    @NonNull
    public static Map<String, Result> parseResults(@NonNull JSONObject payload) {
        JSONArray data = payload.optJSONArray("data");
        if (data == null) {
            return Collections.emptyMap();
        }

        Map<String, Result> results = new HashMap<>();
        for (int i = 0; i < data.length(); i++) {
            JSONObject comment = data.optJSONObject(i);
            if (comment == null) {
                continue;
            }
            String id = readString(comment, "id");
            if (id == null) {
                continue;
            }
            Result result = parseComment(comment);
            if (result != null) {
                results.put(id, result);
            }
        }
        return results;
    }

    /**
     * Returns null (rather than an empty {@link Result}) for an archive record with no original
     * left to give back.
     */
    @Nullable
    private static Result parseComment(JSONObject comment) {
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
    }

    @Nullable
    private static String readString(JSONObject obj, String key) {
        // isNull() is true for both an absent key and a JSON null; optString() alone would
        // return the literal string "null" for the latter (org.json quirk).
        if (obj.isNull(key)) {
            return null;
        }
        String value = obj.optString(key);
        return value.trim().isEmpty() ? null : value;
    }

    public static final class Result {
        public final String body;
        @Nullable
        public final String author;
        @Nullable
        public final String authorFlair;
        @Nullable
        public final String authorFlairHTML;

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
