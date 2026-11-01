package ml.docilealligator.infinityforreddit.adapters

import ml.docilealligator.infinityforreddit.post.Post

/**
 * Which picture an inline gallery tile shows when it is not animating.
 *
 * Reddit publishes downscales of every gallery item up to 1080px wide. The tile used to take the
 * largest of them whatever its own width, which on a 1440px-wide screen is stretched by a third.
 * It now takes the smallest downscale at least as wide as the tile, so a 1080px tile still gets
 * the light download the quick swipe was built on, and only a tile wider than every downscale goes
 * to the source -- the original upload, the one picture that covers it.
 *
 * The source stands in for a downscale only for a still image whose size is known and within the
 * feed's maximum resolution, the same ceiling an image post's preview is held to. A gif or video
 * source is the animation itself, often tens of MB, so its tile keeps the widest downscale; so does
 * a source too large for the setting, which the decoder would only halve back down again.
 *
 * One rule for the tile's own load, for the swipe neighbour it warms, and for the feed's preloader,
 * because Glide's memory cache is keyed on the url: a warmed picture is only found by a load that
 * picked the same one.
 */
object GalleryTileStill {

    /**
     * The url of [item]'s still in a tile [tileWidthPx] wide, or `null` when its only picture is
     * the animation -- a gif or video with no downscale -- which the tile loads as the source.
     *
     * [tileWidthPx] at or below zero means the width is not known, and gives the widest downscale,
     * which is what every tile showed before tiles were sized ahead of time. [dataSaving] gives the
     * same whatever the width, so data saving never sends a tile to the original upload.
     */
    @JvmStatic
    fun url(item: Post.Gallery, tileWidthPx: Int, maxResolution: Int, dataSaving: Boolean): String? {
        val downscales = item.downscales.orEmpty()
            .filter { it.previewWidth > 0 }
            .sortedBy { it.previewWidth }
        val still = when {
            downscales.isEmpty() -> item.feedPreviewUrl
            tileWidthPx <= 0 || dataSaving -> downscales.last().previewUrl
            else -> downscales.firstOrNull { it.previewWidth >= tileWidthPx }?.previewUrl
                ?: item.url.takeIf { sourceFits(item, maxResolution) }
                ?: downscales.last().previewUrl
        }
        // A still image with no downscale at all -- another host's album, or one too small for
        // Reddit to have made any -- is its own still.
        return still ?: item.url.takeIf { item.mediaType == Post.Gallery.TYPE_IMAGE }
    }

    private fun sourceFits(item: Post.Gallery, maxResolution: Int): Boolean =
        item.mediaType == Post.Gallery.TYPE_IMAGE
            && item.sourceWidth > 0 && item.sourceHeight > 0
            && item.sourceWidth.toLong() * item.sourceHeight <= maxResolution
}
