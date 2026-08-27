package ml.docilealligator.infinityforreddit.randomsubreddit

/**
 * When a cached [RandomSubredditList] is due another look at the remote copy.
 *
 * Checks are opportunistic, on app open -- there is deliberately no WorkManager job and no alarm
 * behind this. A list a few days stale costs nothing, because every pick is validated against
 * `/api/info` at the moment it is made; the refresh only keeps the candidate pool broad.
 *
 * Kept free of Android and of the clock so the cadence can be tested outright.
 */
object RandomSubredditListRefreshPolicy {

    /** After a check that completed -- 304 or 200 alike. */
    const val SUCCESS_INTERVAL_MILLIS = 24L * 60L * 60L * 1000L

    /** After a check that failed, retried hourly until one succeeds. */
    const val FAILURE_RETRY_INTERVAL_MILLIS = 60L * 60L * 1000L

    @JvmStatic
    fun isCheckDue(lastSuccessUtcMillis: Long, lastAttemptUtcMillis: Long, nowUtcMillis: Long): Boolean {
        if (lastSuccessUtcMillis <= 0L && lastAttemptUtcMillis <= 0L) {
            // Never checked. Picks run off the copy shipped in the APK until this lands.
            return true
        }

        // A check that completed stamps both timestamps and one that failed stamps only the
        // attempt, so whichever is later says how the last check ended.
        val lastCheckSucceeded = lastSuccessUtcMillis >= lastAttemptUtcMillis
        val lastCheckUtcMillis = if (lastCheckSucceeded) lastSuccessUtcMillis else lastAttemptUtcMillis
        val elapsedMillis = nowUtcMillis - lastCheckUtcMillis
        if (elapsedMillis < 0L) {
            // The stored stamp is in the future -- the device clock moved backwards, or was wrong
            // when it was written. Check now rather than sit out the difference.
            return true
        }

        return elapsedMillis >= if (lastCheckSucceeded) SUCCESS_INTERVAL_MILLIS else FAILURE_RETRY_INTERVAL_MILLIS
    }
}
