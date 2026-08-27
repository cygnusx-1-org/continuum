package ml.docilealligator.infinityforreddit.randomsubreddit

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The refresh cadence, which is the one piece of this feature with no visible symptom when it is
 * wrong: check too eagerly and every app open pays for a multi-megabyte conditional request; check
 * too rarely and a failed download is never retried. Neither shows up on screen, because a stale
 * list still picks -- validation at pick time is what keeps a pick correct.
 *
 * [RandomSubredditListRefreshPolicy] takes the clock as an argument precisely so this can be
 * asserted outright rather than inferred from behaviour.
 */
class RandomSubredditListRefreshPolicyTest {

    private companion object {
        /** An arbitrary "now" far enough from zero that hours can be subtracted from it. */
        const val NOW = 1_800_000_000_000L

        const val HOUR = 60L * 60L * 1000L
        const val MINUTE = 60L * 1000L
    }

    private fun isDue(lastSuccess: Long, lastAttempt: Long) =
        RandomSubredditListRefreshPolicy.isCheckDue(lastSuccess, lastAttempt, NOW)

    @Test
    fun neverCheckedIsDue() {
        assertTrue(isDue(lastSuccess = 0L, lastAttempt = 0L))
    }

    @Test
    fun succeeded23HoursAgoIsNotDue() {
        val when23HoursAgo = NOW - 23 * HOUR
        assertFalse(isDue(lastSuccess = when23HoursAgo, lastAttempt = when23HoursAgo))
    }

    @Test
    fun succeeded25HoursAgoIsDue() {
        val when25HoursAgo = NOW - 25 * HOUR
        assertTrue(isDue(lastSuccess = when25HoursAgo, lastAttempt = when25HoursAgo))
    }

    @Test
    fun failed30MinutesAgoIsNotDue() {
        // Nothing has ever succeeded, so only the attempt is stamped.
        assertFalse(isDue(lastSuccess = 0L, lastAttempt = NOW - 30 * MINUTE))
    }

    @Test
    fun failed2HoursAgoIsDue() {
        assertTrue(isDue(lastSuccess = 0L, lastAttempt = NOW - 2 * HOUR))
    }

    @Test
    fun failureAfterAnOldSuccessRetriesHourlyRatherThanDaily() {
        // The stale success must not hold the retry back, and must not force one either.
        val successDaysAgo = NOW - 3 * 24 * HOUR
        assertFalse(isDue(lastSuccess = successDaysAgo, lastAttempt = NOW - 30 * MINUTE))
        assertTrue(isDue(lastSuccess = successDaysAgo, lastAttempt = NOW - 2 * HOUR))
    }

    @Test
    fun successAfterFailuresReturnsToTheDailyCadence() {
        // A success stamps both timestamps, which is what puts the 24h interval back in charge.
        val failedThenSucceeded = NOW - 2 * HOUR
        assertFalse(isDue(lastSuccess = failedThenSucceeded, lastAttempt = failedThenSucceeded))
        assertTrue(
            isDue(lastSuccess = NOW - 25 * HOUR, lastAttempt = NOW - 25 * HOUR)
        )
    }

    @Test
    fun aTimestampInTheFutureIsCheckedNowRatherThanWaitedOut() {
        // A device clock that was wrong when the stamp was written would otherwise strand the
        // check until real time caught up, which can be years.
        assertTrue(isDue(lastSuccess = NOW + 400 * 24 * HOUR, lastAttempt = 0L))
        assertTrue(isDue(lastSuccess = 0L, lastAttempt = NOW + 400 * 24 * HOUR))
    }
}
