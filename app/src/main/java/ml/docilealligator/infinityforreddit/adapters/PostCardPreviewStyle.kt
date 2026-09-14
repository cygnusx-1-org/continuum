package ml.docilealligator.infinityforreddit.adapters

import android.widget.ImageView
import ml.docilealligator.infinityforreddit.customviews.AspectRatioGifImageView
import ml.docilealligator.infinityforreddit.post.Post

/**
 * How a feed card draws a post's preview: how the selftext snippet is divided around the image,
 * whether the image is squared, and whether a squared image is fitted inside the square or cropped
 * to it.
 *
 * Pure policy, kept out of [PostRecyclerViewAdapter] for the same reason `PreloadWindow` is kept
 * out of `CompactThumbnailPreloader`: these are the decisions worth asserting, and a test -- or a
 * layout golden -- that re-derived them would go on passing while the app shipped the opposite.
 * The adapter still owns the views and the settings; this owns only the rules.
 */
object PostCardPreviewStyle {

    /**
     * Height-to-width ratio of a "Fixed Height in Card" preview: square, so every preview in the
     * feed is the same height as every other one.
     */
    const val SQUARE_PREVIEW_RATIO = 1f

    /**
     * The selftext snippet that goes above the preview, or `null` for none.
     *
     * A card reads in the same order the post detail does, because the two are the same post:
     *
     *  * an image out of the post's own body sits where the body puts it, so the words before it
     *    go above and the words after it go below -- the split
     *    [Post.hasInlineImageSnippetSplit] records;
     *  * a preview Reddit built from a link in the body is not part of the body at all. The detail
     *    draws it above the selftext, the way it draws a link post's preview, so the card puts
     *    nothing above it and the whole snippet below.
     *
     * That second case is every text post that carries a link -- the feed used to open such a card
     * with the body's first 250 characters and then show the image underneath, while tapping
     * through to the same post showed the image first.
     *
     * [hasBelowSlot] is false for the cards that have only one slot -- cards 2 and 3 draw the image
     * at the top, above the title, so their single slot already follows the image and takes the
     * whole snippet. A body image that could not be split (see [Post.hasInlineImageSnippetSplit])
     * keeps the whole snippet above the image, which is where the card has always put it: the body
     * renders that image somewhere the card cannot locate, so there is no order to follow.
     */
    @JvmStatic
    fun snippetAbovePreview(post: Post, hasBelowSlot: Boolean): String? = when {
        !hasBelowSlot -> post.selfTextPlainTrimmed
        isSplit(post) -> post.selfTextPlainTrimmedBeforeInlineImage
        post.isInlineBodyImagePreview -> post.selfTextPlainTrimmed
        else -> null
    }

    /** The selftext snippet that goes below the preview, or `null` for none. See [snippetAbovePreview]. */
    @JvmStatic
    fun snippetBelowPreview(post: Post, hasBelowSlot: Boolean): String? = when {
        !hasBelowSlot -> null
        isSplit(post) -> post.selfTextPlainTrimmedAfterInlineImage
        post.isInlineBodyImagePreview -> null
        else -> post.selfTextPlainTrimmed
    }

    private fun isSplit(post: Post): Boolean =
        post.isInlineBodyImagePreview && post.hasInlineImageSnippetSplit()

    /**
     * Whether [preview] is drawn as the fixed square rather than at its own aspect ratio:
     * "Fixed Height in Card" is on, or the preview carries no dimensions to size a ratio from.
     */
    @JvmStatic
    fun squarePreview(preview: Post.Preview, fixedHeightInCard: Boolean): Boolean =
        fixedHeightInCard || preview.previewWidth <= 0 || preview.previewHeight <= 0

    /**
     * Whether a squared card letterboxes its media rather than cropping it: a gif post that
     * autoplays, or a text post whose preview is an image out of its own body. With autoplay off a
     * gif card is a still with a play badge, exactly like a video card is then, and crops like one.
     *
     * A body image is part of what the post says -- a screenshot, a chart, a meme whose words run
     * to the edges -- and the square's centre crop cuts those edges off. Reddit's own preview of a
     * link is a picture of somewhere else, which survives cropping, so it still crops.
     */
    @JvmStatic
    fun letterboxSquare(post: Post, autoplay: Boolean): Boolean =
        letterboxWithVideoBars(post, autoplay) || post.isInlineBodyImagePreview

    /**
     * Whether a letterboxed square is filled with the black an autoplaying video card shows around
     * its clip, rather than with the card colour. A gif stands in for a video and gets the video's
     * bars; a still out of a post's body is a picture on a card, and sits on the card.
     */
    @JvmStatic
    fun letterboxWithVideoBars(post: Post, autoplay: Boolean): Boolean =
        post.postType == Post.GIF_TYPE && autoplay

    /**
     * Shapes a card's preview image view for [post]: squared or at its own aspect ratio, fitted or
     * cropped. The whole of what a with-preview card does to its image slot, in one call, so the
     * adapter, a test and a layout golden cannot each take a different path through it.
     *
     * [maxPreviewHeight] is the ceiling a squared preview is clamped to -- half the feed's visible
     * height, so a square preview in a wide column cannot come out taller than the screen.
     */
    @JvmStatic
    fun applyPreviewShape(
        imageView: AspectRatioGifImageView,
        preview: Post.Preview,
        post: Post,
        fixedHeightInCard: Boolean,
        autoplay: Boolean,
        maxPreviewHeight: Int,
    ) {
        if (squarePreview(preview, fixedHeightInCard)) {
            squareShape(imageView, letterboxSquare(post, autoplay), maxPreviewHeight)
        } else {
            ratioShape(imageView, preview)
        }
    }

    /**
     * Sizes a preview to a square, so that it is as tall as the column it sits in is wide.
     *
     * This is what Settings -> Interface -> Post -> "Fixed Height in Card" gives you: every preview
     * in the feed ends up the same height as every other one, because every column is the same
     * width. It is also the fallback for a post whose preview metadata carries no usable dimensions
     * to size from.
     *
     * It has to be expressed as a ratio rather than as a layout height.
     * `AspectRatioGifImageView.onMeasure` replaces the measured height with `width * ratio` for any
     * positive ratio, so a height written to the layout params is silently discarded -- which is
     * what the flat 400dp height that used to be here always was (#373).
     *
     * A still fills the square, cropped to its centre. A [letterbox]ed preview is fitted inside it
     * instead, the way an autoplaying video card shows its clip: the `PlayerView` in that card
     * keeps media3's default fit mode, so a 16:9 video in a square is shown whole between black
     * bars, while the same clip posted as a gif went through the still's centre crop and lost its
     * sides.
     */
    @JvmStatic
    fun squareShape(imageView: AspectRatioGifImageView, letterbox: Boolean, maxPreviewHeight: Int) {
        imageView.scaleType =
            if (letterbox) ImageView.ScaleType.FIT_CENTER else ImageView.ScaleType.CENTER_CROP
        imageView.setRatioMaxHeight(maxPreviewHeight)
        imageView.setRatio(SQUARE_PREVIEW_RATIO)
    }

    /**
     * Sizes a preview from the dimensions Reddit reported for it, the behaviour when "Fixed Height
     * in Card" is off. Clears the square preview's height cap, which would otherwise crop whatever
     * post this recycled view holds next.
     */
    @JvmStatic
    fun ratioShape(imageView: AspectRatioGifImageView, preview: Post.Preview) {
        imageView.setRatioMaxHeight(0)
        imageView.setRatio(preview.previewHeight.toFloat() / preview.previewWidth)
    }
}
