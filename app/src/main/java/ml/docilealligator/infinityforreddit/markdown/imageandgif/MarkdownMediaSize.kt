package ml.docilealligator.infinityforreddit.markdown.imageandgif

import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import ml.docilealligator.infinityforreddit.customviews.AspectRatioGifImageView

/**
 * How an image or gif embedded in a post body or a comment is sized. Pure view work, kept out of
 * [ImageAndGifEntry] so the same shaping can be asserted in a unit test and captured in a layout
 * golden -- a golden that re-derived the rule would go on passing while the app shipped the
 * opposite.
 */
object MarkdownMediaSize {

    /** The square a fixed-height image is drawn in: as tall as the block is wide. */
    const val SQUARE_MEDIA_RATIO = 1f

    /**
     * Sizes an embedded image or gif to the full width of its block, as tall as its own aspect
     * ratio makes it. Body media is part of what a post or comment says -- a screenshot, a chart, a
     * diagram someone drew -- so it is shown at the size the text around it is, rather than shrunk
     * to a thumbnail.
     *
     * Settings -> Interface -> Post -> "Fixed Height in Card" ([fixedHeight]) instead gives every
     * image the same square box the feed's cards use under that setting, so a column of comments
     * does not change rhythm with every image's shape. The square is capped at [maxHeight], the
     * same half-viewport ceiling the feed applies, so one image cannot take a whole screen; the
     * image is fitted inside it rather than cropped to it, because an image in a body is usually
     * one whose edges carry the meaning.
     *
     * Dimensions of zero mean `media_metadata` did not say, which is also what the feed treats as
     * square: it is a placeholder either way, because `onResourceReady` re-sizes from the real
     * drawable as soon as one arrives.
     */
    @JvmStatic
    fun applyTo(
        imageView: AspectRatioGifImageView,
        srcWidth: Int,
        srcHeight: Int,
        fixedHeight: Boolean,
        maxHeight: Int,
    ) {
        imageView.scaleType = ImageView.ScaleType.FIT_CENTER
        val params = imageView.layoutParams as FrameLayout.LayoutParams
        if (params.width != ViewGroup.LayoutParams.MATCH_PARENT ||
            params.height != ViewGroup.LayoutParams.WRAP_CONTENT ||
            params.gravity != Gravity.NO_GRAVITY
        ) {
            params.width = ViewGroup.LayoutParams.MATCH_PARENT
            params.height = ViewGroup.LayoutParams.WRAP_CONTENT
            params.gravity = Gravity.NO_GRAVITY
            imageView.layoutParams = params
        }
        if (fixedHeight || srcWidth <= 0 || srcHeight <= 0) {
            imageView.setRatioMaxHeight(maxHeight)
            imageView.setRatio(SQUARE_MEDIA_RATIO)
        } else {
            // Cleared, or the square's cap would still be clamping this image's own height.
            imageView.setRatioMaxHeight(0)
            imageView.setRatio(srcHeight.toFloat() / srcWidth)
        }
    }
}
