package ml.docilealligator.infinityforreddit.utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import ml.docilealligator.infinityforreddit.comment.Comment;

/**
 * In-memory cache for storing comment data and scroll positions per post.
 * Data is not persisted to disk and is cleared when the app is restarted.
 */
public class CommentScrollPositionCache {
    private static final CommentScrollPositionCache INSTANCE = new CommentScrollPositionCache();

    private final Map<String, CachedPostComments> cache = new HashMap<>();

    private CommentScrollPositionCache() {
        // Private constructor for singleton
    }

    public static CommentScrollPositionCache getInstance() {
        return INSTANCE;
    }

    /**
     * Saves the comment data and scroll position for a post.
     * @param postId The post ID
     * @param comments The list of comments
     * @param children The list of child IDs for loading more
     * @param hasMoreChildren Whether there are more children to load
     * @param scrollPosition The scroll position (first visible item position)
     */
    public void save(String postId, ArrayList<Comment> comments, ArrayList<String> children,
                     boolean hasMoreChildren, int scrollPosition) {
        if (postId != null && comments != null) {
            cache.put(postId, new CachedPostComments(comments, children, hasMoreChildren, scrollPosition));
        }
    }

    /**
     * Gets the cached data for a post.
     * @param postId The post ID
     * @return The cached data, or null if not found
     */
    public CachedPostComments get(String postId) {
        if (postId != null) {
            return cache.get(postId);
        }
        return null;
    }

    /**
     * Checks if there is cached data for a post.
     * @param postId The post ID
     * @return true if cached data exists
     */
    public boolean has(String postId) {
        return postId != null && cache.containsKey(postId);
    }

    /**
     * Removes the cached data for a post.
     * @param postId The post ID
     */
    public void remove(String postId) {
        if (postId != null) {
            cache.remove(postId);
        }
    }

    /**
     * Clears all cached data.
     */
    public void clearAll() {
        cache.clear();
    }

    /**
     * Holder class for cached post comment data.
     */
    public static class CachedPostComments {
        public final ArrayList<Comment> comments;
        public final ArrayList<String> children;
        public final boolean hasMoreChildren;
        public final int scrollPosition;

        public CachedPostComments(ArrayList<Comment> comments, ArrayList<String> children,
                                   boolean hasMoreChildren, int scrollPosition) {
            this.comments = comments;
            this.children = children;
            this.hasMoreChildren = hasMoreChildren;
            this.scrollPosition = scrollPosition;
        }
    }
}
