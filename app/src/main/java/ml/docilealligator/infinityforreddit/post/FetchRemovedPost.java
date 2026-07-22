package ml.docilealligator.infinityforreddit.post;

import android.text.Html;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import ml.docilealligator.infinityforreddit.apis.ArcticShiftAPI;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;

public class FetchRemovedPost {

    /** A body that is exactly a [text](url) autolink — Reddit's rendering of a lone bare URL. */
    private static final Pattern SOLE_MARKDOWN_LINK =
            Pattern.compile("\\[[^\\]]*]\\((https?://[^)\\s]+)\\)");
    /** A body that is exactly one bare URL. */
    private static final Pattern SOLE_URL = Pattern.compile("https?://\\S+");

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
                        if (result.author != null) {
                            post.setAuthor(result.author);
                            post.setAuthorFlair(result.authorFlair);
                            post.setAuthorFlairHTML(result.authorFlairHTML);
                        }
                        // Only restore link flair the removal stripped: fill it in when the post
                        // currently has none, but never overwrite a flair that is still present (it
                        // may be the surviving original or a mod's post-removal reason flair).
                        if (!result.flair.isEmpty() && post.getFlair().isEmpty()) {
                            post.setFlair(result.flair);
                        }
                        if (result.link != null) {
                            // The original post was a link post; rebuild it as one so the recovered
                            // destination renders as a tappable link card rather than as body text.
                            post.setUrl(result.link);
                            post.setPostType(Post.NO_PREVIEW_LINK_TYPE);
                            post.setSelfText("");
                            post.setSelfTextPlain("");
                            post.setSelfTextPlainTrimmed("");
                        } else if (result.body != null) {
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
    @Nullable
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

            // Reject values that are themselves a removal placeholder: when the archive only ever
            // ingested the post after Reddit scrubbed it, there is no original to recover, and
            // handing back the placeholder would masquerade as a successful recovery.
            String title = readString(post, "title");
            if (isRemovalPlaceholder(title)) {
                title = null;
            }
            String body = readString(post, "selftext");
            if (isRemovalPlaceholder(body)) {
                body = null;
            }
            boolean isSelf = readBoolean(post, "is_self");

            // When a link post's recovered body is nothing but a link (Reddit stores a bare URL the
            // author wrote as a [url](url) autolink), surface it as a link post rather than burying
            // the destination in the selftext. Only for non-self posts — a self-post whose body
            // happens to be a single link is still a text post, so leave it in the body.
            String link = isSelf ? null : extractSoleLink(body);
            if (link != null) {
                body = null;
            }

            // A media post with no selftext still loses its destination on removal — Reddit rewrites
            // the url to the self permalink — so recover the original url into the body. A bare URL
            // is valid markdown and renders as a tappable link. Skip self-posts and any url that
            // merely points back at the comments permalink.
            if (body == null && link == null && !isSelf) {
                String url = readString(post, "url");
                if (url != null && !url.contains("/comments/")) {
                    body = url;
                }
            }

            if (title == null && body == null && link == null) {
                return null;
            }

            // Restore the original author when the poster's account was deleted (post shows
            // "[deleted]"). Keep it only when the archive holds a real username, so we never swap one
            // "[deleted]"/"[removed]" placeholder for another. Flair is only meaningful alongside a
            // recovered author, so it is parsed only in that case.
            String author = readString(post, "author");
            if (isRemovalPlaceholder(author)) {
                author = null;
            }
            // Always non-null (""); only applied when the author is recovered (see onResponse).
            String authorFlair = parseAuthorFlairText(post);
            String authorFlairHTML = parseAuthorFlairHtml(post);
            // Post's own link flair (independent of the author); "" when absent.
            String flair = parseLinkFlair(post);

            return new Result(title, body, link, author, authorFlair, authorFlairHTML, flair);
        } catch (JSONException e) {
            return null;
        }
    }

    /**
     * Recognizes the placeholder text Reddit substitutes for removed content: the bare
     * {@code [removed]} / {@code [deleted]} left by an ordinary moderator removal or author
     * deletion, the "[ Removed by moderator ]" sentence a subreddit moderator leaves in the title
     * when the removal carries a reason, and the "[ Removed by Reddit ... ]" sentence its admins
     * leave (in both the title and the body) on a legal / content-policy takedown. Used both to
     * decide a post is removed and to reject archive copies that are themselves post-takedown
     * snapshots rather than the original.
     */
    public static boolean isRemovalPlaceholder(@Nullable String text) {
        if (text == null) {
            return false;
        }
        String normalized = text.trim().toLowerCase(Locale.ENGLISH);
        return normalized.equals("[removed]")
                || normalized.equals("[deleted]")
                || normalized.startsWith("[ removed by reddit")
                || normalized.startsWith("[ removed by moderator");
    }

    /**
     * If {@code body} consists solely of a single link — either a bare URL or the [url](url)
     * autolink Reddit generates for one — returns that URL; otherwise null.
     */
    @Nullable
    private static String extractSoleLink(@Nullable String body) {
        if (body == null) {
            return null;
        }
        String trimmed = body.trim();
        Matcher markdownLink = SOLE_MARKDOWN_LINK.matcher(trimmed);
        if (markdownLink.matches()) {
            return markdownLink.group(1);
        }
        if (SOLE_URL.matcher(trimmed).matches()) {
            return trimmed;
        }
        return null;
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

    private static boolean readBoolean(JSONObject obj, String key) {
        return obj.optBoolean(key, false);
    }

    /**
     * Rebuilds an author's flair HTML from an archive object's {@code author_flair_richtext} array,
     * mirroring {@code ParseComment}/{@code ParsePost}. Returns "" when there is no flair. Shared with
     * {@code FetchRemovedComment}.
     */
    public static String parseAuthorFlairHtml(JSONObject obj) {
        StringBuilder builder = new StringBuilder();
        JSONArray flairArray = obj.optJSONArray("author_flair_richtext");
        if (flairArray != null) {
            for (int i = 0; i < flairArray.length(); i++) {
                JSONObject flairObject = flairArray.optJSONObject(i);
                if (flairObject == null) {
                    continue;
                }
                String e = flairObject.optString("e", "");
                if (e.equals("text")) {
                    builder.append(Html.escapeHtml(flairObject.optString("t", "")));
                } else if (e.equals("emoji")) {
                    builder.append("<img src=\"").append(Html.escapeHtml(flairObject.optString("u", ""))).append("\">");
                }
            }
        }
        return builder.toString();
    }

    /** The plain-text author flair from an archive object, or "" when absent. */
    public static String parseAuthorFlairText(JSONObject obj) {
        return obj.isNull("author_flair_text") ? "" : obj.optString("author_flair_text", "");
    }

    /**
     * Rebuilds a post's link flair from an archive object, mirroring {@code ParsePost}: the
     * {@code link_flair_richtext} array rendered to HTML when present, otherwise the plain
     * {@code link_flair_text}. Returns "" when the post has no flair.
     */
    private static String parseLinkFlair(JSONObject obj) {
        StringBuilder builder = new StringBuilder();
        JSONArray flairArray = obj.optJSONArray("link_flair_richtext");
        if (flairArray != null) {
            for (int i = 0; i < flairArray.length(); i++) {
                JSONObject flairObject = flairArray.optJSONObject(i);
                if (flairObject == null) {
                    continue;
                }
                String e = flairObject.optString("e", "");
                if (e.equals("text")) {
                    builder.append(Html.escapeHtml(flairObject.optString("t", "")));
                } else if (e.equals("emoji")) {
                    builder.append("<img src=\"").append(Html.escapeHtml(flairObject.optString("u", ""))).append("\">");
                }
            }
        }
        String flair = builder.toString();
        if (flair.isEmpty() && !obj.isNull("link_flair_text")) {
            flair = obj.optString("link_flair_text", "");
        }
        return flair;
    }

    private static final class Result {
        @Nullable
        final String title;
        @Nullable
        final String body;
        @Nullable
        final String link;
        @Nullable
        final String author;
        final String authorFlair;
        final String authorFlairHTML;
        final String flair;

        Result(@Nullable String title, @Nullable String body, @Nullable String link, @Nullable String author,
               String authorFlair, String authorFlairHTML, String flair) {
            this.title = title;
            this.body = body;
            this.link = link;
            this.author = author;
            this.authorFlair = authorFlair;
            this.authorFlairHTML = authorFlairHTML;
            this.flair = flair;
        }
    }

    public interface FetchRemovedPostListener {
        void fetchSuccess(Post post);

        void fetchFailed();
    }
}
