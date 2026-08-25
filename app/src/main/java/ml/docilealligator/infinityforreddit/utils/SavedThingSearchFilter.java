package ml.docilealligator.infinityforreddit.utils;

import androidx.annotation.Nullable;
import java.util.Locale;
import ml.docilealligator.infinityforreddit.comment.Comment;
import ml.docilealligator.infinityforreddit.post.Post;

/**
 * Client-side matcher used by the Saved screen's search. Reddit exposes no server-side search over
 * a user's /saved listing, so the Saved tabs filter the items they have already loaded (and keep
 * loading further pages while a query is active) against this matcher.
 *
 * <p>Matching mirrors the behaviour users expect from the sibling app: the query is lower-cased and
 * split into whitespace-separated terms, and every term must match somewhere (AND logic). A term of
 * the form {@code /r/subreddit} matches the item's subreddit exactly (as well as any literal
 * occurrence in the text). Post text covers title + subreddit + author; comment text covers body +
 * subreddit + author.
 */
public final class SavedThingSearchFilter {

    private SavedThingSearchFilter() {
    }

    public static boolean matches(Post post, @Nullable String query) {
        if (isBlank(query)) {
            return true;
        }
        if (post == null) {
            return false;
        }
        return matchesTerms(buildPostText(post), post.getSubredditName(), splitTerms(query));
    }

    public static boolean matches(Comment comment, @Nullable String query) {
        if (isBlank(query)) {
            return true;
        }
        if (comment == null) {
            return false;
        }
        return matchesTerms(buildCommentText(comment), comment.getSubredditName(), splitTerms(query));
    }

    private static boolean isBlank(@Nullable String s) {
        return s == null || s.trim().isEmpty();
    }

    private static String[] splitTerms(@Nullable String query) {
        if (query == null) {
            return new String[0];
        }
        return query.trim().toLowerCase(Locale.ROOT).split("\\s+");
    }

    // Locale.ROOT, not the device locale: the query and the text being searched differ in case by
    // construction, and a Turkish/Azeri device lower-cases "I" to dotless "i" on one side only, so
    // "PICS" would stop matching r/pics. This folds Reddit content, never text shown to the user.
    private static boolean matchesTerms(String searchableText, @Nullable String subredditName, String[] terms) {
        String text = searchableText == null ? "" : searchableText.toLowerCase(Locale.ROOT);
        String subreddit = subredditName == null ? "" : subredditName.toLowerCase(Locale.ROOT);
        for (String term : terms) {
            if (term.isEmpty()) {
                continue;
            }
            boolean matched;
            if (term.startsWith("/r/") && term.length() > 3) {
                String wantedSubreddit = term.substring(3);
                matched = subreddit.equals(wantedSubreddit) || text.contains(term);
            } else {
                matched = text.contains(term);
            }
            if (!matched) {
                return false;
            }
        }
        return true;
    }

    private static String buildPostText(Post post) {
        StringBuilder builder = new StringBuilder();
        append(builder, post.getTitle());
        append(builder, post.getSubredditName());
        append(builder, post.getAuthor());
        return builder.toString();
    }

    private static String buildCommentText(Comment comment) {
        StringBuilder builder = new StringBuilder();
        String body = comment.getCommentRawText();
        if (body == null || body.isEmpty()) {
            body = comment.getCommentMarkdown();
        }
        append(builder, body);
        append(builder, comment.getSubredditName());
        append(builder, comment.getAuthor());
        return builder.toString();
    }

    private static void append(StringBuilder builder, @Nullable String value) {
        if (value != null) {
            builder.append(value).append(' ');
        }
    }
}
