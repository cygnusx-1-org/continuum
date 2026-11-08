package ml.docilealligator.infinityforreddit;

import org.jspecify.annotations.Nullable;

public class Constants {
    public static final int DEFAULT_TAB_COUNT = 3;
    public static final long VIDEO_SEEK_BACK_INCREMENT_MS = 10000;
    public static final long VIDEO_SEEK_FORWARD_INCREMENT_MS = 10000;
    public static final long VIDEO_SHORT_DURATION_THRESHOLD_MS = 19000;
    /**
     * How long a video's on-screen controls stay up after playback starts or the last touch, before
     * they go. The same 5 s media3 gives a PlayerView by default, so every player in the app hides
     * its controls on one clock.
     */
    public static final int VIDEO_CONTROLS_SHOW_TIMEOUT_MS = 5000;

    /**
     * In-app name of the virtual subreddit that shows r/all's listing while carrying an identity of
     * its own for post filtering. Wildcard "Exclude subreddits" terms only ever run here, so a broad
     * term like {@code *irl*} cannot reach Home, Search, a multireddit or a real subreddit page.
     *
     * <p>Not a subreddit that exists on Reddit, so its About tab and subscribe button have nothing
     * to show; only the post listing is redirected.
     */
    public static final String REDDITJR_ALL_SUBREDDIT = "RedditJrAll";

    /**
     * What this feed was called before the app was renamed. Saved post filters were migrated off
     * it, but a restored old backup, a saved tab or a stored sort preference can still carry it,
     * so it still counts as the same feed.
     */
    public static final String LEGACY_ALL_SUBREDDIT = "ContinuumAll";

    /** The subreddit whose post listing {@link #REDDITJR_ALL_SUBREDDIT} actually fetches. */
    public static final String REDDITJR_ALL_SOURCE_SUBREDDIT = "all";

    private static final String POPULAR_SUBREDDIT = "popular";

    /** True when {@code subredditName} is {@link #REDDITJR_ALL_SUBREDDIT}, in any casing. */
    public static boolean isRedditJrAll(@Nullable String subredditName) {
        return REDDITJR_ALL_SUBREDDIT.equalsIgnoreCase(subredditName)
                || LEGACY_ALL_SUBREDDIT.equalsIgnoreCase(subredditName);
    }

    /**
     * True for the undifferentiated firehose feeds — r/all, r/popular and
     * {@link #REDDITJR_ALL_SUBREDDIT}. These carry posts from subreddits the user never chose, so
     * they default to a different sort, always display the source subreddit on each post, and hide
     * read posts without needing the per-subreddit preference.
     */
    public static boolean isFirehoseSubreddit(@Nullable String subredditName) {
        return REDDITJR_ALL_SOURCE_SUBREDDIT.equalsIgnoreCase(subredditName)
                || POPULAR_SUBREDDIT.equalsIgnoreCase(subredditName)
                || isRedditJrAll(subredditName);
    }

    /**
     * The name to put in the API path for {@code subredditName}. Only
     * {@link #REDDITJR_ALL_SUBREDDIT} differs from its in-app name: it fetches r/all. Everything
     * else in the app — the toolbar, the post filter's "applies to", the sort preference key —
     * keeps its own name, which is what lets a filter target it separately from r/all.
     */
    public static @Nullable String apiSubredditName(@Nullable String subredditName) {
        return isRedditJrAll(subredditName) ? REDDITJR_ALL_SOURCE_SUBREDDIT : subredditName;
    }
}
