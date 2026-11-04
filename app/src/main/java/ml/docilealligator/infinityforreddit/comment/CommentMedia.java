package ml.docilealligator.infinityforreddit.comment;

import android.net.Uri;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ml.docilealligator.infinityforreddit.post.Post;
import ml.docilealligator.infinityforreddit.thing.MediaMetadata;

/**
 * Every image and GIF in a list of comments, as the items of a gallery: the ones Reddit attached to
 * the comment, and the ones linked in its text. Each carries the commenter as its caption, so the
 * vertical list says whose picture it is.
 */
public final class CommentMedia {
    // A link that ends in an image extension, or one on Reddit's own image hosts, which often have
    // none. Stops at whitespace and the characters that close a Markdown link.
    private static final Pattern IMAGE_URL = Pattern.compile(
            "https?://[^\\s)\\]>\"]+?\\.(?:jpe?g|png|webp|gif)(?:\\?[^\\s)\\]>\"]*)?"
                    + "|https?://(?:i|preview)\\.redd\\.it/[^\\s)\\]>\"]+",
            Pattern.CASE_INSENSITIVE);

    private CommentMedia() {
        // Prevent instantiation
    }

    public static ArrayList<Post.Gallery> collect(@Nullable List<Comment> comments) {
        ArrayList<Post.Gallery> media = new ArrayList<>();
        if (comments == null) {
            return media;
        }
        // The same picture is often attached and linked, or quoted in a reply.
        Set<String> seen = new LinkedHashSet<>();
        for (Comment comment : comments) {
            if (comment == null) {
                continue;
            }
            String caption = comment.getAuthor() == null ? "" : "u/" + comment.getAuthor();

            Map<String, MediaMetadata> metadata = comment.getMediaMetadataMap();
            if (metadata != null) {
                for (MediaMetadata item : metadata.values()) {
                    if (item == null || item.original == null || "video".equalsIgnoreCase(item.e)) {
                        continue;
                    }
                    String url = clean(item.original.url);
                    if (!seen.add(url)) {
                        continue;
                    }
                    Post.Gallery gallery = new Post.Gallery(item.isGIF ? "image/gif" : mimeFor(url),
                            url, url, item.fileName, caption, "");
                    gallery.sourceWidth = item.original.x;
                    gallery.sourceHeight = item.original.y;
                    if (item.downscaled != null && item.downscaled.url != null) {
                        gallery.feedPreviewUrl = clean(item.downscaled.url);
                    }
                    media.add(gallery);
                }
            }

            String text = comment.getCommentMarkdown();
            if (text != null) {
                Matcher matcher = IMAGE_URL.matcher(text);
                while (matcher.find()) {
                    String url = clean(matcher.group());
                    if (!seen.add(url)) {
                        continue;
                    }
                    String path = Uri.parse(url).getLastPathSegment();
                    media.add(new Post.Gallery(mimeFor(url), url, url,
                            path == null ? "Image.jpg" : path, caption, ""));
                }
            }
        }
        return media;
    }

    // Reddit escapes the ampersands in the links it hands out.
    private static String clean(String url) {
        return url.replace("&amp;", "&");
    }

    private static String mimeFor(String url) {
        String path = Uri.parse(url).getPath();
        String lower = path == null ? "" : path.toLowerCase(Locale.US);
        if (lower.endsWith(".gif")) {
            return "image/gif";
        }
        if (lower.endsWith(".png")) {
            return "image/png";
        }
        if (lower.endsWith(".webp")) {
            return "image/webp";
        }
        return "image/jpeg";
    }
}
