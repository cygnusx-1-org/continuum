package ml.docilealligator.infinityforreddit.utils

import ml.docilealligator.infinityforreddit.post.ImgurMedia
import ml.docilealligator.infinityforreddit.post.Post
import ml.docilealligator.infinityforreddit.services.DownloadMediaService
import org.apache.commons.io.FilenameUtils

/**
 * Single source of truth for media filenames, so that the "save" (download) and "share" actions
 * produce identical filenames.
 *
 * The scheme mirrors the download path:
 * sanitized title + "_" + post id (+ "_" + 1-based gallery/album index) + extension.
 */
object MediaFileNameUtils {

    private const val MAX_TITLE_LENGTH = 100

    /**
     * Sanitizes an arbitrary title into a filesystem-safe filename component.
     */
    @JvmStatic
    fun sanitizeFilename(inputName: String?): String {
        if (inputName.isNullOrEmpty()) {
            return "reddit_media" // Default name if title is missing
        }

        // Remove characters that are invalid in filenames on most systems, collapse runs of
        // whitespace/underscores, and trim leading/trailing underscores.
        var sanitized = inputName
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .replace(Regex("[\\s_]+"), "_")
            .replace(Regex("^_+|_+$"), "")

        // Limit length to avoid issues with max path length.
        if (sanitized.length > MAX_TITLE_LENGTH) {
            sanitized = sanitized.substring(0, MAX_TITLE_LENGTH).replace(Regex("_+$"), "")
        }

        // Handle the case where sanitization results in an empty string.
        if (sanitized.isEmpty()) {
            return "reddit_media_" + System.currentTimeMillis()
        }
        return sanitized
    }

    /**
     * Builds the filename for a post, or for a single item of a gallery post.
     *
     * @param post the post
     * @param galleryIndex the 0-based gallery index for gallery posts; ignored for other post types
     */
    @JvmStatic
    fun getDownloadFileName(post: Post, galleryIndex: Int): String {
        var sanitizedTitle = sanitizeFilename(post.title)
        // Sanitize the id too. A Reddit id is base36 and comes from the parsed API response, so in
        // practice this is a no-op, but appending it raw after the title has already been sanitized
        // would let anything unexpected in it straight into a path.
        val postId = post.id
        if (!postId.isNullOrEmpty()) {
            sanitizedTitle = sanitizedTitle + "_" + sanitizeFilename(postId)
        }

        var url = ""
        var mediaType = -1

        when (post.postType) {
            Post.IMAGE_TYPE -> {
                url = post.url ?: ""
                mediaType = DownloadMediaService.EXTRA_MEDIA_TYPE_IMAGE
            }
            Post.GIF_TYPE -> {
                url = post.videoUrl ?: "" // GIFs are often served as videos (mp4)
                mediaType = DownloadMediaService.EXTRA_MEDIA_TYPE_GIF
            }
            Post.VIDEO_TYPE -> {
                mediaType = DownloadMediaService.EXTRA_MEDIA_TYPE_VIDEO
                // Streamable/Redgifs URLs are fetched later, so fall back to the media-type extension.
                if (!post.isStreamable) {
                    url = post.videoUrl ?: ""
                }
            }
            Post.GALLERY_TYPE -> {
                val media = post.gallery[galleryIndex]
                if (media.mediaType == Post.Gallery.TYPE_VIDEO) {
                    url = media.url
                    mediaType = DownloadMediaService.EXTRA_MEDIA_TYPE_VIDEO
                } else {
                    // Retrieve the original instead of the one additionally compressed by Reddit.
                    url = if (media.hasFallback()) media.fallbackUrl else media.url
                    mediaType = if (media.mediaType == Post.Gallery.TYPE_GIF) {
                        DownloadMediaService.EXTRA_MEDIA_TYPE_GIF
                    } else {
                        DownloadMediaService.EXTRA_MEDIA_TYPE_IMAGE
                    }
                }
            }
        }

        val indexSuffix =
            if (post.postType == Post.GALLERY_TYPE && galleryIndex >= 0) "_" + (galleryIndex + 1) else ""
        return sanitizedTitle + indexSuffix + getExtension(url, mediaType)
    }

    /**
     * Builds the filename for an Imgur media item.
     *
     * @param imgurMedia the Imgur media
     * @param title the post/album title; falls back to the media id when null/blank
     * @param index the 0-based index within an album, or a negative value for a standalone item
     */
    @JvmStatic
    @JvmOverloads
    fun getDownloadFileName(imgurMedia: ImgurMedia, title: String?, index: Int = -1): String {
        // The Reddit paths put the post id (and the comment id) in the name, which is what makes
        // two different posts unable to collide. Imgur names carried only the album title, so two
        // different albums sharing a title collided; the media id closes that.
        //
        // Guard on the resolved name rather than on the title, because an untitled media falls back
        // to its own id — here, and again in the callers, which substitute the id before calling.
        // Appending it in that case would name the file "id_id".
        //
        // Sanitize the id once and reuse that value. It has to be sanitized because, unlike a
        // Reddit post id, an Imgur id can come straight from a user-supplied URL path segment via
        // LinkResolverActivity, so it can carry characters that are illegal in a filename. And it
        // has to be a single evaluation because sanitizeFilename is not idempotent for input that
        // sanitizes away to nothing — it appends a timestamp — so sanitizing twice and comparing
        // the two results would never match.
        val mediaId = imgurMedia.id
        val sanitizedId = if (mediaId.isNullOrEmpty()) null else sanitizeFilename(mediaId)
        var name = if (title.isNullOrBlank()) sanitizedId ?: sanitizeFilename(null) else sanitizeFilename(title)
        if (sanitizedId != null && name != sanitizedId) {
            name = name + "_" + sanitizedId
        }
        val indexSuffix = if (index >= 0) "_" + (index + 1) else ""
        return name + indexSuffix + getExtension(imgurMedia)
    }

    private fun getExtension(url: String?, mediaType: Int): String {
        val extension = FilenameUtils.getExtension(url)
        if (!extension.isNullOrEmpty() &&
            extension.matches(Regex("(?i)(jpg|jpeg|png|gif|mp4|webm|mov|avi)"))
        ) {
            // Limit extension length to prevent abuse.
            return "." + extension.lowercase().substring(0, minOf(extension.length, 5))
        }
        return when (mediaType) {
            DownloadMediaService.EXTRA_MEDIA_TYPE_IMAGE -> ".jpg"
            DownloadMediaService.EXTRA_MEDIA_TYPE_GIF -> ".gif"
            DownloadMediaService.EXTRA_MEDIA_TYPE_VIDEO -> ".mp4"
            else -> ".unknown"
        }
    }

    private fun getExtension(imgurMedia: ImgurMedia): String {
        // ImgurMedia already exposes a reasonable filename with extension.
        val extension = FilenameUtils.getExtension(imgurMedia.fileName)
        if (!extension.isNullOrEmpty()) {
            return "." + extension.lowercase().substring(0, minOf(extension.length, 5))
        }
        // Fallback based on type if the filename lacks an extension.
        return getExtension(
            imgurMedia.link,
            if (imgurMedia.type == ImgurMedia.TYPE_VIDEO) {
                DownloadMediaService.EXTRA_MEDIA_TYPE_VIDEO
            } else {
                DownloadMediaService.EXTRA_MEDIA_TYPE_IMAGE
            }
        )
    }
}
