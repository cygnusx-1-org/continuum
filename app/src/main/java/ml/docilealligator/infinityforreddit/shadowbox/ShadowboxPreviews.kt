package ml.docilealligator.infinityforreddit.shadowbox

import android.content.Context
import android.content.SharedPreferences
import android.graphics.drawable.Drawable
import com.bumptech.glide.RequestBuilder
import com.bumptech.glide.RequestManager
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import ml.docilealligator.infinityforreddit.post.Post
import ml.docilealligator.infinityforreddit.utils.SharedPreferencesUtils
import ml.docilealligator.infinityforreddit.utils.Utils

/**
 * Picks the preview a Shadowbox page shows for a post, the way the feed does in
 * PostRecyclerViewAdapter.getSuitablePreviewWithThumbnailFallback: the largest preview that fits
 * under the resolution cap (or the middle one in data-saving mode), falling back to the thumbnail
 * when Reddit sent no previews at all.
 *
 * It also owns the request the pages make for that preview, and the two settings that decide which
 * one they get, so every page asks for the same picture in the same way.
 */
object ShadowboxPreviews {
    /**
     * What the "hide text posts and posts with no preview" setting keeps: a post that is not a
     * text post and has a preview image to show.
     *
     * A text post is out whether or not Reddit gave it a preview -- the page renders its words,
     * not the picture -- and everything else is out without a preview, media post or otherwise,
     * since the page would come up blank or on a placeholder.
     */
    fun hasPreviewToShow(post: Post): Boolean =
        post.postType != Post.TEXT_TYPE && bestPreview(post, Int.MAX_VALUE, false) != null

    /**
     * How long a preview takes to fade in when it was not already in Glide's memory. A cached
     * image is set without any animation at all, so this is only ever the cost of one that had to
     * be fetched -- and a picture that fades is a picture that does not appear from nowhere.
     */
    const val CROSS_FADE_MS = 200

    /**
     * The one request every page makes for a preview: fitted to the view it goes in, and faded in
     * when it was not already in Glide's memory.
     */
    fun previewRequest(glide: RequestManager, url: String): RequestBuilder<Drawable> =
        glide.load(url)
            .fitCenter()
            .transition(DrawableTransitionOptions.withCrossFade(CROSS_FADE_MS))

    /**
     * Height-to-width ratio a gallery's tiles are measured at. The items carry no dimensions of
     * their own, so the post's preview stands in for all of them, exactly as the feed's inline
     * gallery does; square when there is none.
     */
    fun galleryTileRatio(preview: Post.Preview?): Float =
        if (preview != null && preview.previewWidth > 0 && preview.previewHeight > 0) {
            preview.previewHeight.toFloat() / preview.previewWidth
        } else {
            1f
        }

    /**
     * The height a gallery tile is measured to, which is what AspectRatioGifImageView.onMeasure
     * works out from the ratio and the cap the page puts on it.
     *
     * Worked out here rather than left to the view so that a tile's request can name its size:
     * one that does is answered during the bind, while one that does not waits for the list to be
     * laid out before Glide has a size to decode to.
     */
    fun galleryTileHeight(ratio: Float, width: Int, maxHeight: Int): Int =
        // At least one pixel: a preview wide enough to round the height to nothing would
        // otherwise ask Glide for a zero-sized image, which it rejects outright.
        minOf((width * ratio).toInt(), maxHeight).coerceAtLeast(1)

    /** "Post Feed Max Resolution": the pixel count a preview is picked to fit under. */
    @JvmStatic
    fun maxResolution(sharedPreferences: SharedPreferences): Int =
        SharedPreferencesUtils.getInt(
            sharedPreferences, SharedPreferencesUtils.POST_FEED_MAX_RESOLUTION, "5000000"
        )

    /** Whether "Data Saving Mode" applies on the connection this device is on right now. */
    @JvmStatic
    fun dataSavingMode(context: Context, sharedPreferences: SharedPreferences): Boolean =
        when (sharedPreferences.getString(
            SharedPreferencesUtils.DATA_SAVING_MODE, SharedPreferencesUtils.DATA_SAVING_MODE_OFF
        )) {
            SharedPreferencesUtils.DATA_SAVING_MODE_ALWAYS -> true
            SharedPreferencesUtils.DATA_SAVING_MODE_ONLY_ON_CELLULAR_DATA ->
                Utils.getConnectedNetwork(context) == Utils.NETWORK_TYPE_CELLULAR
            else -> false
        }

    @JvmStatic
    fun bestPreview(post: Post, maxResolution: Int, dataSavingMode: Boolean): Post.Preview? {
        val previews = post.previews
        if (previews != null && previews.isNotEmpty()) {
            val previewIndex = if (dataSavingMode && previews.size > 2) previews.size / 2 else 0
            var preview = previews[previewIndex]
            if (preview.previewWidth * preview.previewHeight > maxResolution) {
                for (i in previews.size - 1 downTo 1) {
                    preview = previews[i]
                    if (preview.previewWidth * preview.previewHeight <= maxResolution) {
                        return preview
                    }
                }
            }
            return preview
        }
        val thumbnailUrl = post.thumbnailUrl
        if (hasValidThumbnail(thumbnailUrl)) {
            return Post.Preview(thumbnailUrl, 0, 0, "", "")
        }
        return null
    }

    private fun hasValidThumbnail(thumbnailUrl: String?): Boolean {
        return !thumbnailUrl.isNullOrEmpty()
                && thumbnailUrl != "self"
                && thumbnailUrl != "default"
                && thumbnailUrl != "nsfw"
                && thumbnailUrl != "spoiler"
                && thumbnailUrl != "image"
                && thumbnailUrl.startsWith("http")
    }
}
