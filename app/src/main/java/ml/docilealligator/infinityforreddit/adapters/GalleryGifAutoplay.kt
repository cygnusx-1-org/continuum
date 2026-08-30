package ml.docilealligator.infinityforreddit.adapters

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import ml.docilealligator.infinityforreddit.videoautoplay.PlayerSelector
import ml.docilealligator.infinityforreddit.videoautoplay.ToroPlayer
import ml.docilealligator.infinityforreddit.videoautoplay.ToroUtil
import ml.docilealligator.infinityforreddit.videoautoplay.media.PlaybackInfo
import ml.docilealligator.infinityforreddit.videoautoplay.widget.Container

/**
 * Autoplay for the gifs inside a gallery post's pager (issue #382).
 *
 * Those gifs are Glide drawables rather than players, but they take part in the same coordination
 * as autoplaying videos — the visible-area threshold and Settings -> Video -> "Simultaneous
 * autoplay limit" — by presenting the gallery itself as a [ToroPlayer], so a gif and a video can
 * never both play when the limit is one. Nothing here touches volume: gifs carry no audio.
 *
 * A host ViewHolder owns one of these and delegates its own [ToroPlayer] methods to it, supplying
 * [getPlayerOrder] (its adapter position) and the adapter-level state below.
 */
abstract class GalleryGifAutoplay(
    private val itemView: View,
    private val galleryRecyclerView: RecyclerView,
    private val adapter: PostGalleryTypeImageRecyclerViewAdapter,
) : ToroPlayer {
    private var container: Container? = null
    private var playing = false

    /** The host adapter's gate on playback, e.g. false while the window has lost focus. */
    protected abstract fun canPlay(): Boolean

    /** Fraction of the gallery that must be on screen, from the "start autoplay" sliders. */
    protected abstract fun visibleAreaThreshold(): Double

    /**
     * The selector that enforces the simultaneous-autoplay limit, or null to leave the Container's
     * own (the post detail screen holds a single post, so the limit has nothing to bound there).
     */
    protected open fun playerSelector(): PlayerSelector? = null

    override fun getPlayerView(): View = galleryRecyclerView

    /** A gif has no resume position to carry across a recycle: it restarts from frame one. */
    override fun getCurrentPlaybackInfo(): PlaybackInfo = PlaybackInfo()

    override fun initialize(container: Container, playbackInfo: PlaybackInfo) {
        if (this.container == null) {
            this.container = container
            // A feed with no autoplaying video never initializes a video player, and the
            // Container's default selector plays a single item whatever the limit is set to.
            playerSelector()?.let { container.playerSelector = it }
        }
    }

    override fun play() {
        playing = true
        adapter.setPlaying(true)
    }

    override fun pause() {
        playing = false
        adapter.setPlaying(false)
    }

    override fun isPlaying(): Boolean = playing

    override fun release() = pause()

    override fun wantsToPlay(): Boolean =
        canPlay() &&
            adapter.canAnimateCurrentTile() &&
            ToroUtil.visibleAreaOffset(this, itemView.parent) >= visibleAreaThreshold()

    /**
     * Swiping the pager changes the answer [wantsToPlay] gives — a still page wants nothing, a gif
     * page wants a slot — and the Container only asks again when the feed itself scrolls, so have
     * it re-select now.
     */
    fun onGalleryPageSettled(position: Int) {
        if (!adapter.setCurrentPosition(position)) {
            // Settling back onto the same page — nothing to reconsider.
            return
        }
        val container = this.container ?: return
        if (container.scrollState == RecyclerView.SCROLL_STATE_IDLE) {
            container.onScrollStateChanged(RecyclerView.SCROLL_STATE_IDLE)
        }
    }
}
