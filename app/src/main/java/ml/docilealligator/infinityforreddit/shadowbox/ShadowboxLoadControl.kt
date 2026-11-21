package ml.docilealligator.infinityforreddit.shadowbox

import androidx.annotation.OptIn
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.LoadControl
import androidx.media3.exoplayer.analytics.PlayerId
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.TrackGroupArray
import androidx.media3.exoplayer.trackselection.ExoTrackSelection
import androidx.media3.exoplayer.upstream.Allocator

/**
 * How far ahead a Shadowbox page's player loads: [DefaultLoadControl] with a byte cap, and held to a
 * few seconds on any page that is not playing.
 *
 * A page prepares its player as soon as it is built -- a page before it is swiped to -- and a
 * paused ExoPlayer goes on loading until its buffer is full. At the defaults, 50 s of media with a
 * byte ceiling over 100 MB, every video page near the one in front filled itself, and ExoPlayer
 * keeps what it has loaded in byte arrays on the Java heap: a run of video posts ran a 256 MB heap
 * out within a few swipes (issue #502), and the pages that were only waiting took bandwidth from
 * the one being watched.
 *
 * A page that is not playing -- a neighbour, or the page in front with the user's pause on it --
 * loads [WAITING_BUFFER_MS] ahead, which is enough to come ready, so a swipe still starts the video
 * at once. The page that plays loads up to [MAX_BUFFER_MS] or [TARGET_BUFFER_BYTES], whichever it
 * reaches first.
 */
@OptIn(UnstableApi::class)
class ShadowboxLoadControl : LoadControl {

    private val delegate: DefaultLoadControl = DefaultLoadControl.Builder()
        .setBufferDurationsMs(
            MIN_BUFFER_MS, MAX_BUFFER_MS, BUFFER_FOR_PLAYBACK_MS, BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS
        )
        .setTargetBufferBytes(TARGET_BUFFER_BYTES)
        // The byte cap holds below the minimum duration too, so a high-bitrate file cannot get past
        // it on the way to MIN_BUFFER_MS.
        .setPrioritizeTimeOverSizeThresholds(false)
        .build()

    override fun shouldContinueLoading(parameters: LoadControl.Parameters): Boolean {
        // The delegate is asked every time, even when the answer here is no, so the loading state it
        // keeps per player follows the buffer instead of going stale while the page waits.
        val delegateWantsMore = delegate.shouldContinueLoading(parameters)
        return delegateWantsMore &&
            (parameters.playWhenReady || parameters.bufferedDurationUs < WAITING_BUFFER_US)
    }

    // Everything else is the delegate's. These are the overloads ExoPlayer calls, and the same set
    // DefaultLoadControl itself overrides.

    override fun onPrepared(playerId: PlayerId) = delegate.onPrepared(playerId)

    override fun onTracksSelected(
        parameters: LoadControl.Parameters,
        trackGroups: TrackGroupArray,
        trackSelections: Array<ExoTrackSelection?>
    ) = delegate.onTracksSelected(parameters, trackGroups, trackSelections)

    override fun onStopped(playerId: PlayerId) = delegate.onStopped(playerId)

    override fun onReleased(playerId: PlayerId) = delegate.onReleased(playerId)

    override fun getAllocator(playerId: PlayerId): Allocator = delegate.getAllocator(playerId)

    override fun getBackBufferDurationUs(playerId: PlayerId): Long =
        delegate.getBackBufferDurationUs(playerId)

    override fun retainBackBufferFromKeyframe(playerId: PlayerId): Boolean =
        delegate.retainBackBufferFromKeyframe(playerId)

    override fun shouldStartPlayback(parameters: LoadControl.Parameters): Boolean =
        delegate.shouldStartPlayback(parameters)

    override fun shouldContinuePreloading(
        playerId: PlayerId,
        timeline: Timeline,
        mediaPeriodId: MediaSource.MediaPeriodId,
        bufferedDurationUs: Long
    ): Boolean = delegate.shouldContinuePreloading(playerId, timeline, mediaPeriodId, bufferedDurationUs)

    companion object {
        private const val MIN_BUFFER_MS = 15_000
        private const val MAX_BUFFER_MS = 30_000
        private const val BUFFER_FOR_PLAYBACK_MS = DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS
        private const val BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS =
            DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS

        /** About 25 s of a 1080p Reddit or Redgifs video, per player. */
        private const val TARGET_BUFFER_BYTES = 24 * 1024 * 1024

        /**
         * What a page that is not playing loads ahead. Kept a second past what the player needs to
         * come ready after a rebuffer, so a waiting page always gets there and never sits buffering
         * behind its poster.
         */
        private const val WAITING_BUFFER_MS = BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS + 1_000
        private const val WAITING_BUFFER_US = WAITING_BUFFER_MS * 1_000L
    }
}
