package ml.docilealligator.infinityforreddit.utils

import ml.docilealligator.infinityforreddit.comment.Comment
import ml.docilealligator.infinityforreddit.post.ImgurMedia
import ml.docilealligator.infinityforreddit.post.Post
import ml.docilealligator.infinityforreddit.services.DownloadMediaService
import java.text.Normalizer
import org.apache.commons.io.FilenameUtils

/**
 * Single source of truth for media filenames, so that the "save" (download) and "share" actions
 * produce identical filenames.
 *
 * The scheme mirrors the download path:
 * sanitized title + "_" + post id (+ "_" + 1-based gallery/album index) + extension.
 */
object MediaFileNameUtils {

    /**
     * Byte budget for the title part of a name, measured as UTF-8 rather than as UTF-16 units.
     *
     * Every filesystem Android exposes to an app caps one path component at 255 *bytes* -- measured
     * on ext4, on FUSE-backed emulated storage, and on a real vfat SD card, all of which reject 256
     * ASCII bytes and equally reject 86 CJK characters (258 bytes). A UTF-16 count could not express
     * that: 100 units is 100 bytes of ASCII but 300 bytes of CJK, which overflows once the ids and
     * extension are appended.
     *
     * 100 bytes keeps an ASCII title byte-for-byte identical to what this produced before, and
     * leaves ample room under 255 for `_<postId>_<commentId><ext>`.
     */
    private const val MAX_TITLE_BYTES = 100

    /**
     * Every quotation mark, single and double, not just the two ASCII ones. Only `"` is illegal in
     * a filename, but Reddit titles carry the typographic forms constantly and they are just as
     * unwanted in a name.
     *
     * Deliberately excludes the primes `\u2032` and `\u2033`, which look like quotes but measure
     * feet and inches -- stripping them would turn `24\u2033 monitor` into `24 monitor`.
     */
    private val QUOTE_MARKS = Regex(
        "[\"\u201C\u201D\u201E\u201F\u00AB\u00BB\u301D\u301E\u301F\uFF02" +
            "'\u2018\u2019\u201A\u201B\u2039\u203A\uFF07]"
    )

    /**
     * Sanitizes an arbitrary title into a filesystem-safe filename component.
     */
    @JvmStatic
    fun sanitizeFilename(inputName: String?): String {
        if (inputName.isNullOrEmpty()) {
            return "reddit_media" // Default name if title is missing
        }

        // Quotes are stripped outright rather than mapped to a separator like the illegal
        // characters below, because they sit *inside* words as often as around them. An apostrophe
        // is the common case -- "it\u2019s my birthday" has to become "its", not "it_s" -- and the
        // same holds for a title wrapped in quotes, where substituting a separator only to collapse
        // and trim it again is a longer route to the same name.
        //
        // Remove characters that are invalid in filenames on most systems, collapse runs of
        // whitespace/underscores, and trim leading/trailing underscores.
        var sanitized = inputName
            .replace(QUOTE_MARKS, "")
            .replace(Regex("[\\\\/:*?<>|]"), "_")
            .replace(Regex("[\\s_]+"), "_")
            .replace(Regex("^_+|_+$"), "")

        // Limit length to avoid issues with max path length.
        if (sanitized.utf8Size() > MAX_TITLE_BYTES) {
            sanitized = sanitized.truncateToUtf8Bytes(MAX_TITLE_BYTES).replace(Regex("_+$"), "")
        }

        // Handle the case where sanitization results in an empty string.
        if (sanitized.isEmpty()) {
            return "reddit_media_" + System.currentTimeMillis()
        }
        return sanitized
    }

    private fun String.utf8Size(): Int = toByteArray(Charsets.UTF_8).size

    /**
     * Cuts this string to at most [maxBytes] of UTF-8, always on a code-point boundary.
     *
     * Advancing by code point rather than by `char` is what keeps a surrogate pair intact. Half a
     * pair is not merely cosmetic: encoding a lone surrogate to UTF-8 substitutes `?`, which is one
     * of the characters [sanitizeFilename] strips precisely because it is illegal on FAT, so a
     * split emoji could put an illegal character back into a name that had just been cleaned.
     */
    private fun String.truncateToUtf8Bytes(maxBytes: Int): String {
        var end = 0
        var used = 0
        while (end < length) {
            val codePoint = codePointAt(end)
            val width = when {
                codePoint < 0x80 -> 1
                codePoint < 0x800 -> 2
                codePoint < 0x10000 -> 3
                else -> 4
            }
            if (used + width > maxBytes) {
                break
            }
            used += width
            end += Character.charCount(codePoint)
        }
        return substring(0, end)
    }

    /**
     * Rewrites [fileName] using ASCII only, keeping its extension.
     *
     * Every destination reachable on Android today stores the full range -- emoji, CJK, Cyrillic and
     * accented Latin all round-trip intact on ext4, on FUSE-backed emulated storage and on a vfat SD
     * card, and AOSP's own `isValidFatFilenameChar` treats them as valid. So this is not applied up
     * front; it is the retry for a destination that turns out to disagree, which is the only way to
     * tell one apart from another through SAF or MediaStore.
     */
    @JvmStatic
    fun toAsciiFilename(fileName: String): String {
        val dot = fileName.lastIndexOf('.')
        val base = if (dot > 0) fileName.substring(0, dot) else fileName
        val extension = if (dot > 0) fileName.substring(dot) else ""

        // Decompose first so accented Latin folds to its base letter -- "Caf\u00E9" becomes "Cafe"
        // rather than "Caf_". Scripts with no ASCII equivalent, and emoji, have nothing to fold to
        // and fall through to the separator below.
        val folded = Normalizer.normalize(base, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")

        val asciiBase = folded
            .replace(Regex("[^\\x20-\\x7E]+"), "_")
            .replace(Regex("[\\s_]+"), "_")
            .replace(Regex("^_+|_+$"), "")

        // A title written entirely in a non-Latin script sanitizes away to nothing here, so it needs
        // the same fallback sanitizeFilename uses rather than an empty name.
        val safeBase = asciiBase.ifEmpty { "reddit_media_" + System.currentTimeMillis() }

        // No length budget here. [fileName] arrives already bounded -- its title went through
        // sanitizeFilename and the ids were appended after -- and folding only ever shrinks it, so
        // there is nothing left to cut. Applying the *title* budget to a *whole* name would truncate
        // the ids off the end, which are what stop two posts colliding, and would make an
        // already-ASCII name compare unequal to its input so the caller retried with the worse name.
        return safeBase + extension
    }

    /**
     * Builds the filename for a post, or for a single item of a gallery post.
     *
     * @param post the post
     * @param galleryIndex the 0-based gallery index for gallery posts; ignored for other post types
     */
    @JvmStatic
    fun getDownloadFileName(post: Post, galleryIndex: Int): String {
        val sanitizedTitle = joinTitleAndIds(post.title, post.id, null)

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
     * Builds the filename for a media item embedded in a post body or a comment, rather than for
     * the post's own media.
     *
     * The host post's type says nothing about an embedded item — a video embedded in a self post
     * sits on a [Post.TEXT_TYPE] post — so the extension has to come from the media URL here
     * instead of from [getDownloadFileName]'s post-type switch, which would yield ".unknown".
     *
     * @param postTitle title of the host post
     * @param postId id of the host post
     * @param commentId id of the comment the media is embedded in, or null for a post body
     * @param mediaUrl the URL actually being downloaded, used for the extension
     * @param mediaType a [DownloadMediaService] media type, used when the URL carries no extension
     */
    @JvmStatic
    fun getEmbeddedMediaFileName(
        postTitle: String?,
        postId: String?,
        commentId: String?,
        mediaUrl: String?,
        mediaType: Int,
    ): String {
        return joinTitleAndIds(postTitle, postId, commentId) + getExtension(mediaUrl, mediaType)
    }

    /**
     * Builds the filename for the image or GIF the viewer currently has open.
     *
     * Separate from [getEmbeddedMediaFileName] on the two counts only the viewer knows about: an
     * APNG is an extension of its own, which no [DownloadMediaService] media type can express, and
     * a missing title falls back to a name that says which of the two kinds it was.
     *
     * @param postTitle title of the host post, or null when the caller has none
     * @param postId id of the host post
     * @param commentId id of the comment the media is embedded in, or null for a post's own image
     *     or one embedded in a post body
     * @param imageUrl the URL actually being downloaded, used for the extension
     * @param isGif whether the viewer resolved the media to a GIF
     * @param isApng whether the viewer resolved the media to an APNG
     */
    @JvmStatic
    fun getViewedImageFileName(
        postTitle: String?,
        postId: String?,
        commentId: String?,
        imageUrl: String?,
        isGif: Boolean,
        isApng: Boolean,
    ): String {
        val title = if (postTitle.isNullOrEmpty()) {
            if (isGif || isApng) "reddit_gif" else "reddit_image"
        } else {
            postTitle
        }
        return joinTitleAndIds(title, postId, commentId) +
            getViewedImageExtension(imageUrl, isGif, isApng)
    }

    /**
     * Names the screenshot shared from a post card, so a receiving app shows something meaningful
     * rather than a generic name. A screenshot is always a PNG render of the card.
     */
    @JvmStatic
    fun getScreenshotFileName(post: Post, withComments: Boolean): String {
        val base = joinTitleAndIds(post.title, post.id, null)
        return if (withComments) base + "_comments.png" else "$base.png"
    }

    /** Names the screenshot shared from a single comment. */
    @JvmStatic
    fun getScreenshotFileName(comment: Comment): String =
        "comment_" + joinTitleAndIds(comment.author, comment.id, null) + ".png"

    /**
     * Joins a title and the ids that disambiguate it into the stem of a filename.
     *
     * The order is the reason this lives in one place. Each part is sanitized on its own and joined
     * afterwards, never the other way round: sanitizing the joined string would put the ids inside
     * [MAX_TITLE_BYTES], so a title long enough to reach the cap -- routine once the budget counts
     * bytes and the title is CJK -- truncates the ids away, and the ids are what stop two posts
     * from colliding. Nor is it equivalent for a degenerate title, because [sanitizeFilename] is
     * not idempotent for input that sanitizes away to nothing.
     *
     * An id is sanitized rather than appended raw. A Reddit id is base36 and comes from the parsed
     * API response, so in practice that is a no-op, but the title has already been cleaned by this
     * point and nothing else would stop an unexpected id from reaching a path.
     */
    private fun joinTitleAndIds(title: String?, postId: String?, commentId: String?): String {
        var name = sanitizeFilename(title)
        if (!postId.isNullOrEmpty()) {
            name = name + "_" + sanitizeFilename(postId)
        }
        if (!commentId.isNullOrEmpty()) {
            name = name + "_" + sanitizeFilename(commentId)
        }
        return name
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

    /**
     * The extension for the image or GIF on screen: the URL's own when it is one this viewer could
     * be showing, otherwise whatever the viewer resolved the media to.
     *
     * Wider than [getExtension]'s list by "apng", which the viewer tells apart from a plain PNG and
     * which no [DownloadMediaService] media type carries.
     */
    private fun getViewedImageExtension(imageUrl: String?, isGif: Boolean, isApng: Boolean): String {
        val extension = FilenameUtils.getExtension(imageUrl)
        if (!extension.isNullOrEmpty() &&
            extension.matches(Regex("(?i)(jpg|jpeg|png|apng|gif|mp4|webm|mov|avi)"))
        ) {
            // Limit extension length to prevent abuse.
            return "." + extension.lowercase().substring(0, minOf(extension.length, 5))
        }
        return when {
            isApng -> ".apng"
            isGif -> ".gif"
            else -> ".jpg"
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
