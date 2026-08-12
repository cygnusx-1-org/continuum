package ml.docilealligator.infinityforreddit.post;

import android.net.Uri;
import android.text.Html;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
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

                        Result result = parseResponse(response.body(), post.getId());
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
                        if (result.redgifsId != null) {
                            // The original was a redgifs post; rebuild it as the app renders a live
                            // one — a redgifs video (VIDEO_TYPE + isRedgifs, media.redgifs.com mp4) —
                            // plus the recovered Reddit preview frame, so it plays (or at least shows
                            // the poster when the redgifs video was itself deleted) instead of a link.
                            post.setPostType(Post.VIDEO_TYPE);
                            post.setIsRedgifs(true);
                            post.setRedgifsId(result.redgifsId);
                            post.setVideoUrl(result.redgifsVideoUrl);
                            post.setVideoDownloadUrl(result.redgifsVideoUrl);
                            // Restore the redgifs watch url too, so the card's domain/share match a
                            // live redgifs post (the removal had rewritten url to the self permalink).
                            if (result.redgifsUrl != null) {
                                post.setUrl(result.redgifsUrl);
                            }
                            if (result.mediaPreviews != null && post.getPreviews().isEmpty()) {
                                post.setPreviews(result.mediaPreviews);
                            }
                            applyRecoveredBody(post, result.body);
                        } else if (result.link != null) {
                            // The original post was a link post; rebuild it as one so the recovered
                            // destination renders as a tappable link card rather than as body text.
                            post.setUrl(result.link);
                            post.setPostType(Post.NO_PREVIEW_LINK_TYPE);
                            applyRecoveredBody(post, result.body);
                        } else if (result.mediaUrl != null) {
                            // The original post was an image/gif whose url the removal rewrote to the
                            // self permalink; rebuild it as media so the picture renders rather than a
                            // bare link. Only restore the preview the removal stripped — never clobber
                            // one the live post somehow still has.
                            post.setUrl(result.mediaUrl);
                            post.setPostType(result.mediaPostType);
                            if (result.mediaPostType == Post.GIF_TYPE) {
                                post.setVideoUrl(result.mediaUrl);
                            }
                            if (result.mediaPreviews != null && post.getPreviews().isEmpty()) {
                                post.setPreviews(result.mediaPreviews);
                            }
                            if (result.mediaThumbnail != null) {
                                post.setThumbnailUrl(result.mediaThumbnail);
                            }
                            applyRecoveredBody(post, result.body);
                        } else if (result.body != null) {
                            // The original was a self/text post; restore its body and re-type it as
                            // text. A removed self post arrives typed NO_PREVIEW_LINK_TYPE — its live
                            // url is the self permalink and its body is gone — which renders an empty
                            // "LINK" card; setting TEXT_TYPE drops the card so it reads as plain text.
                            // (getItemViewType still upgrades to the link holder if a body-link preview
                            // survives, so a legitimate self-post preview is not lost.)
                            if (result.isSelf) {
                                post.setPostType(Post.TEXT_TYPE);
                            }
                            applyRecoveredBody(post, result.body);
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
    private static Result parseResponse(String responseBody, String postId) {
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

            // A media/link post loses its destination on removal — Reddit rewrites the url to the
            // self permalink — so recover the original url. Rebuild a redgifs post as its video and
            // an image/gif as real media (below) so it renders; recover any other destination as a
            // tappable link. Recover this even when a caption (body) survived: the media is the point
            // of the post, and the caption is kept and shown alongside it (previously a surviving
            // body short-circuited this block, dropping the image/video). Skip self-posts and a url
            // that merely points back at *this* post's own comments permalink, but keep a url that
            // points at a *different* thread (a genuine link post).
            String redgifsId = null;
            String redgifsVideoUrl = null;
            String redgifsUrl = null;
            String mediaUrl = null;
            int mediaPostType = -1;
            ArrayList<Post.Preview> mediaPreviews = null;
            String mediaThumbnail = null;
            if (link == null && !isSelf) {
                String url = readString(post, "url");
                // Skip a url that only points back at this post itself — its own comments permalink
                // (Reddit's post-removal rewrite) or its own /gallery/<id> page — since recovering
                // that as a link yields a dead self-link.
                if (url != null && !pointsBackAtSamePost(url, postId)) {
                    String recoveredRedgifsId = isRedgifs(url) ? ParsePost.getRedgifsId(post) : null;
                    if (recoveredRedgifsId != null) {
                        // Rebuild redgifs exactly as ParsePost does for a live post: keep its watch
                        // url and restore the Reddit-hosted preview frame (external-preview.redd.it),
                        // which Reddit keeps alive after removal — even when the redgifs video itself
                        // was later deleted — so the card still shows a poster image.
                        redgifsId = recoveredRedgifsId;
                        redgifsVideoUrl = ParsePost.getRedgifsVideoUrl(recoveredRedgifsId);
                        redgifsUrl = url;
                        mediaPreviews = buildPreviews(post);
                    } else {
                        int recoveredType = classifyMedia(post, url);
                        if (recoveredType == Post.IMAGE_TYPE || recoveredType == Post.GIF_TYPE) {
                            // Prefer the post's own direct url as the preview: the archive
                            // preview.images[].url points at the legacy i.redditmedia.com host, which
                            // is frequently dead for old removed posts. Reuse only its width/height.
                            mediaUrl = url;
                            mediaPostType = recoveredType;
                            mediaPreviews = buildMediaPreviews(post, url);
                            String thumbnail = readString(post, "thumbnail");
                            if (thumbnail != null && thumbnail.startsWith("http")) {
                                mediaThumbnail = thumbnail;
                            }
                        } else {
                            // Not a recognized image — recover as a tappable link (see onResponse).
                            link = url;
                        }
                    }
                }
            }

            if (title == null && body == null && link == null && mediaUrl == null
                    && redgifsId == null) {
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

            return new Result(title, body, link, author, authorFlair, authorFlairHTML, flair,
                    mediaUrl, mediaPostType, mediaPreviews, mediaThumbnail, isSelf,
                    redgifsId, redgifsVideoUrl, redgifsUrl);
        } catch (JSONException e) {
            return null;
        }
    }

    /**
     * Classifies a recovered non-self post's url into a continuum media {@link Post} type, using the
     * archive's {@code post_hint}/{@code domain} plus the url's file extension. Returns
     * {@link Post#GIF_TYPE} for gifs, {@link Post#IMAGE_TYPE} for still images, or {@code -1} when the
     * url is not a recognized image (a real external/reddit link, recovered as a plain link instead).
     */
    private static int classifyMedia(JSONObject post, String url) {
        // Compare on the path alone: a query string (?s=…) or fragment must not defeat the extension.
        String path = url;
        int query = path.indexOf('?');
        if (query >= 0) {
            path = path.substring(0, query);
        }
        int fragment = path.indexOf('#');
        if (fragment >= 0) {
            path = path.substring(0, fragment);
        }
        path = path.toLowerCase(Locale.ENGLISH);

        // A hosted/rich video or a video-container url (imgur .gifv/.mp4, .webm) is not displayable
        // as an image or gif — the image loader can't decode it — so let it fall through to link
        // recovery rather than rebuild a broken media post. (Rebuilding it as a playable video is out
        // of scope; the tappable link is the safe fallback, matching the old url-into-body behavior.)
        if (readBoolean(post, "is_video")
                || path.endsWith(".gifv") || path.endsWith(".mp4") || path.endsWith(".webm")) {
            return -1;
        }
        // A .gif takes the gif path (the loader animates it), not the image path where an image
        // holder may show only a static frame; check it before the image-domain heuristic below so a
        // .gif on an image host (i.redd.it/i.imgur.com) is still classified as a gif.
        if (path.endsWith(".gif")) {
            return Post.GIF_TYPE;
        }
        String postHint = readString(post, "post_hint");
        String domain = readString(post, "domain");
        if ("image".equals(postHint)
                || path.endsWith(".jpg") || path.endsWith(".jpeg")
                || path.endsWith(".png") || path.endsWith(".webp")
                || "i.redd.it".equalsIgnoreCase(domain) || "i.imgur.com".equalsIgnoreCase(domain)) {
            return Post.IMAGE_TYPE;
        }
        return -1;
    }

    /**
     * Builds a one-element preview list pointing at the post's own direct media {@code url} — the
     * reliable image even for old posts, whereas the archive's {@code preview} urls point at the
     * legacy {@code i.redditmedia.com} host that is frequently dead. The archive preview's
     * {@code width}/{@code height} are reused for aspect ratio when present, else 0/0 (the loader
     * falls back to a fixed-height layout).
     */
    private static ArrayList<Post.Preview> buildMediaPreviews(JSONObject post, String url) {
        int width = 0;
        int height = 0;
        JSONObject preview = post.optJSONObject("preview");
        if (preview != null) {
            JSONArray images = preview.optJSONArray("images");
            JSONObject image = images == null || images.length() == 0 ? null : images.optJSONObject(0);
            JSONObject source = image == null ? null : image.optJSONObject("source");
            if (source != null) {
                width = source.optInt("width", 0);
                height = source.optInt("height", 0);
            }
        }
        ArrayList<Post.Preview> previews = new ArrayList<>();
        previews.add(new Post.Preview(url, width, height, "", ""));
        return previews;
    }

    /**
     * Rebuilds the preview list from the archive's Reddit-hosted preview
     * (external-preview.redd.it / preview.redd.it). Used for a recovered redgifs post: Reddit keeps
     * that preview frame alive after removal — even when the redgifs video itself was later deleted —
     * so the card still renders a poster image. Returns null when the archive has no usable preview.
     */
    @Nullable
    private static ArrayList<Post.Preview> buildPreviews(JSONObject post) {
        JSONObject preview = post.optJSONObject("preview");
        if (preview == null) {
            return null;
        }
        JSONArray images = preview.optJSONArray("images");
        JSONObject image = images == null || images.length() == 0 ? null : images.optJSONObject(0);
        JSONObject source = image == null ? null : image.optJSONObject("source");
        if (source == null) {
            return null;
        }
        String url = source.optString("url", null);
        if (url == null || !url.startsWith("http")) {
            return null;
        }
        // The archive stores the url html-escaped (&amp;); the image loader needs the raw url.
        url = Html.fromHtml(url, Html.FROM_HTML_MODE_LEGACY).toString();
        int width = source.optInt("width", 0);
        int height = source.optInt("height", 0);
        ArrayList<Post.Preview> previews = new ArrayList<>();
        previews.add(new Post.Preview(url, width, height, "", ""));
        return previews;
    }

    /** Whether a recovered url is a redgifs watch/embed link, mirroring ParsePost's authority check. */
    private static boolean isRedgifs(@NonNull String url) {
        String host = Uri.parse(url).getHost();
        return host != null && host.contains("redgifs.com");
    }

    /**
     * Whether the recovered url only points back at this same removed post — Reddit's post-removal
     * rewrite to the post's own comments permalink ({@code …/comments/<id>/…}), or the post's own
     * {@code /gallery/<id>} page. Both are the removed post itself, so recovering either as a link
     * yields a dead self-link. Matches on exact path segments (not a bare substring) so a longer id
     * that merely starts with this one — or another thread's permalink (a genuine crosspost link) —
     * can't match.
     */
    private static boolean pointsBackAtSamePost(@NonNull String url, String postId) {
        List<String> segments = Uri.parse(url).getPathSegments();
        for (int i = 0; i + 1 < segments.size(); i++) {
            String segment = segments.get(i);
            if (("comments".equals(segment) || "gallery".equals(segment))
                    && segments.get(i + 1).equals(postId)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Applies a recovered self-text/caption: the body when one survived (shown alongside a recovered
     * image/video/link), otherwise "" to clear the removal placeholder. selfTextPlain/Trimmed are
     * reset to "" as every recovery branch did before.
     */
    private static void applyRecoveredBody(Post post, @Nullable String body) {
        post.setSelfText(body != null ? body : "");
        post.setSelfTextPlain("");
        post.setSelfTextPlainTrimmed("");
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
        // Recovered image/gif: mediaUrl non-null iff the post is rebuilt as media (see onResponse);
        // mediaPostType is then Post.IMAGE_TYPE or Post.GIF_TYPE, else -1.
        @Nullable
        final String mediaUrl;
        final int mediaPostType;
        @Nullable
        final ArrayList<Post.Preview> mediaPreviews;
        @Nullable
        final String mediaThumbnail;
        // The archive's is_self: when true the original was a text post, so a recovered body is
        // re-typed TEXT_TYPE (see onResponse) rather than left as the removed post's link type.
        final boolean isSelf;
        // Recovered redgifs: redgifsId non-null iff the post is rebuilt as a redgifs video (see
        // onResponse), with redgifsVideoUrl the media.redgifs.com mp4 the app plays and redgifsUrl
        // the watch page restored as the post's url.
        @Nullable
        final String redgifsId;
        @Nullable
        final String redgifsVideoUrl;
        @Nullable
        final String redgifsUrl;

        Result(@Nullable String title, @Nullable String body, @Nullable String link, @Nullable String author,
               String authorFlair, String authorFlairHTML, String flair,
               @Nullable String mediaUrl, int mediaPostType,
               @Nullable ArrayList<Post.Preview> mediaPreviews, @Nullable String mediaThumbnail, boolean isSelf,
               @Nullable String redgifsId, @Nullable String redgifsVideoUrl, @Nullable String redgifsUrl) {
            this.title = title;
            this.body = body;
            this.link = link;
            this.author = author;
            this.authorFlair = authorFlair;
            this.authorFlairHTML = authorFlairHTML;
            this.flair = flair;
            this.mediaUrl = mediaUrl;
            this.mediaPostType = mediaPostType;
            this.mediaPreviews = mediaPreviews;
            this.mediaThumbnail = mediaThumbnail;
            this.isSelf = isSelf;
            this.redgifsId = redgifsId;
            this.redgifsVideoUrl = redgifsVideoUrl;
            this.redgifsUrl = redgifsUrl;
        }
    }

    public interface FetchRemovedPostListener {
        void fetchSuccess(Post post);

        void fetchFailed();
    }
}
