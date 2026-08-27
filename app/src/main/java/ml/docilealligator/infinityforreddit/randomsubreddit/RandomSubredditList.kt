package ml.docilealligator.infinityforreddit.randomsubreddit

/**
 * The two name lists a random pick is drawn from.
 *
 * Reddit resolves `r/random` and `r/randnsfw` as ordinary *banned* subreddits now -- both answer
 * 404 `{"reason": "banned"}` under every auth mode -- so there is no server-side randomness left to
 * call and the candidate names have to come from a list we host.
 *
 * Neither file promises a name is still good. They are derived from an archive, and archived
 * liveness decays fast: 16.1% of the subreddits in the source archive were already banned by the
 * time they were checked. So a list is a pool of *plausible* candidates, and the batch validation
 * in [RandomSubredditRepository] is what makes a pick true at the moment it is used.
 */
enum class RandomSubredditList(
    val remoteUrl: String,
    /**
     * Shared by all three copies of a list: the asset shipped in the APK, the downloaded copy
     * that supersedes it, and the file in the `subreddit-lists` repo the asset is symlinked to.
     */
    val fileName: String,
    private val stateKeyPrefix: String
) {
    /**
     * Names Reddit has not flagged `over18`, which is not the same as safe -- the flag is set by
     * each subreddit's moderators, so one that was never flagged sits in here. See the
     * `random_subreddit_sfw_not_guaranteed` note shown wherever the feature is offered.
     */
    SFW(
        "https://raw.githubusercontent.com/cygnusx-1-org/subreddit-lists/master/subreddits-sfw.txt",
        "subreddits-sfw.txt",
        "sfw"
    ),

    NSFW(
        "https://raw.githubusercontent.com/cygnusx-1-org/subreddit-lists/master/subreddits-nsfw.txt",
        "subreddits-nsfw.txt",
        "nsfw"
    );

    /**
     * The ETag from the last 200, sent straight back as `If-None-Match`. It is opaque: raw
     * githubusercontent returns 64 hex characters that look like a SHA-256 and are neither the
     * content's nor the git blob's, so it is stored and replayed verbatim and never computed here.
     */
    val etagKey: String get() = stateKeyPrefix + "_etag"

    /** Last check that completed, whether 304 or 200. Drives the 24h cadence. */
    val lastSuccessKey: String get() = stateKeyPrefix + "_last_success_utc"

    /** Last check attempted, including failures. Drives the 1h retry. */
    val lastAttemptKey: String get() = stateKeyPrefix + "_last_attempt_utc"
}
