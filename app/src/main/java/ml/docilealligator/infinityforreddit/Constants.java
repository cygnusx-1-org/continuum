package ml.docilealligator.infinityforreddit;

import org.jspecify.annotations.Nullable;

public class Constants {
    public static final int DEFAULT_TAB_COUNT = 3;
    public static final int MAX_TAB_COUNT = 6;
    public static final long VIDEO_SEEK_BACK_INCREMENT_MS = 10000;
    public static final long VIDEO_SEEK_FORWARD_INCREMENT_MS = 10000;
    public static final long VIDEO_SHORT_DURATION_THRESHOLD_MS = 19000;

    /**
     * In-app name of the virtual subreddit that shows r/all's listing while carrying an identity of
     * its own for post filtering. Wildcard "Exclude subreddits" terms only ever run here, so a broad
     * term like {@code *irl*} cannot reach Home, Search, a multireddit or a real subreddit page.
     *
     * <p>Registered on Reddit as a placeholder subreddit, so its About tab and the subscribe button
     * resolve like any other subreddit; only the post listing is redirected.
     */
    public static final String CONTINUUM_ALL_SUBREDDIT = "ContinuumAll";

    /** The subreddit whose post listing {@link #CONTINUUM_ALL_SUBREDDIT} actually fetches. */
    public static final String CONTINUUM_ALL_SOURCE_SUBREDDIT = "all";

    private static final String POPULAR_SUBREDDIT = "popular";

    /** True when {@code subredditName} is {@link #CONTINUUM_ALL_SUBREDDIT}, in any casing. */
    public static boolean isContinuumAll(@Nullable String subredditName) {
        return CONTINUUM_ALL_SUBREDDIT.equalsIgnoreCase(subredditName);
    }

    /**
     * True for the undifferentiated firehose feeds — r/all, r/popular and
     * {@link #CONTINUUM_ALL_SUBREDDIT}. These carry posts from subreddits the user never chose, so
     * they default to a different sort, always display the source subreddit on each post, and hide
     * read posts without needing the per-subreddit preference.
     */
    public static boolean isFirehoseSubreddit(@Nullable String subredditName) {
        return CONTINUUM_ALL_SOURCE_SUBREDDIT.equalsIgnoreCase(subredditName)
                || POPULAR_SUBREDDIT.equalsIgnoreCase(subredditName)
                || isContinuumAll(subredditName);
    }

    /**
     * The name to put in the API path for {@code subredditName}. Only
     * {@link #CONTINUUM_ALL_SUBREDDIT} differs from its in-app name: it fetches r/all. Everything
     * else in the app — the toolbar, the post filter's "applies to", the sort preference key —
     * keeps the ContinuumAll name, which is what lets a filter target it separately from r/all.
     */
    public static @Nullable String apiSubredditName(@Nullable String subredditName) {
        return isContinuumAll(subredditName) ? CONTINUUM_ALL_SOURCE_SUBREDDIT : subredditName;
    }
}
