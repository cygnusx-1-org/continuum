package ml.docilealligator.infinityforreddit.shadowbox

import ml.docilealligator.infinityforreddit.post.Post

/**
 * Picks the preview a Shadowbox page shows for a post, the way the feed does in
 * PostRecyclerViewAdapter.getSuitablePreviewWithThumbnailFallback: the largest preview that fits
 * under the resolution cap (or the middle one in data-saving mode), falling back to the thumbnail
 * when Reddit sent no previews at all.
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
