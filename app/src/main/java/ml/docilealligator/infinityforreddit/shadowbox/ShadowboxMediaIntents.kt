package ml.docilealligator.infinityforreddit.shadowbox

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import ml.docilealligator.infinityforreddit.activities.LinkResolverActivity
import ml.docilealligator.infinityforreddit.activities.ViewRedditGalleryActivity
import ml.docilealligator.infinityforreddit.activities.ViewVideoActivity
import ml.docilealligator.infinityforreddit.post.Post

/**
 * The intents that open a post's existing full-screen viewer from a Shadowbox page. Each one is
 * the matching branch of PostRecyclerViewAdapter.openMedia, so the viewer gets exactly what it gets
 * when the post is tapped in the feed.
 */
@OptIn(UnstableApi::class)
object ShadowboxMediaIntents {

    /**
     * The VIDEO_TYPE branch: [progressMs] > 0 makes the player pick up where the page was.
     *
     * The video URL is set only when the post has one. Streamable and short-clip posts reach this
     * screen before their resolver has run -- that is exactly why they get a preview page rather
     * than an inline player -- and the viewer resolves those from the short code itself.
     */
    fun openVideo(context: Context, post: Post, progressMs: Long) {
        val intent = Intent(context, ViewVideoActivity::class.java)
        val videoUrl = post.videoUrl
        when {
            post.isImgur -> {
                videoUrl?.let { intent.data = Uri.parse(it) }
                intent.putExtra(ViewVideoActivity.EXTRA_VIDEO_TYPE, ViewVideoActivity.VIDEO_TYPE_IMGUR)
            }
            post.isRedgifs -> {
                intent.putExtra(ViewVideoActivity.EXTRA_VIDEO_TYPE, ViewVideoActivity.VIDEO_TYPE_REDGIFS)
                intent.putExtra(ViewVideoActivity.EXTRA_REDGIFS_ID, post.redgifsId)
                videoUrl?.let { intent.data = Uri.parse(it) }
                intent.putExtra(ViewVideoActivity.EXTRA_VIDEO_DOWNLOAD_URL, post.videoDownloadUrl)
            }
            post.isStreamable -> {
                intent.putExtra(ViewVideoActivity.EXTRA_VIDEO_TYPE, ViewVideoActivity.VIDEO_TYPE_STREAMABLE)
                intent.putExtra(ViewVideoActivity.EXTRA_STREAMABLE_SHORT_CODE, post.streamableShortCode)
                if (post.isLoadedStreamableVideoAlready && videoUrl != null) {
                    intent.data = Uri.parse(videoUrl)
                    intent.putExtra(ViewVideoActivity.EXTRA_VIDEO_DOWNLOAD_URL, post.videoDownloadUrl)
                }
            }
            post.isShortClip -> {
                intent.putExtra(ViewVideoActivity.EXTRA_VIDEO_TYPE, ViewVideoActivity.VIDEO_TYPE_SHORT_CLIP)
                intent.putExtra(ViewVideoActivity.EXTRA_SHORT_CLIP_HOST, post.shortClipHost?.name)
                intent.putExtra(ViewVideoActivity.EXTRA_SHORT_CLIP_ID, post.shortClipId)
                if (post.isLoadedStreamableVideoAlready && videoUrl != null) {
                    intent.data = Uri.parse(videoUrl)
                    intent.putExtra(ViewVideoActivity.EXTRA_VIDEO_DOWNLOAD_URL, post.videoDownloadUrl)
                }
            }
            post.isMlbClip -> {
                videoUrl?.let { intent.data = Uri.parse(it) }
                intent.putExtra(ViewVideoActivity.EXTRA_VIDEO_TYPE, ViewVideoActivity.VIDEO_TYPE_DIRECT)
                intent.putExtra(ViewVideoActivity.EXTRA_VIDEO_DOWNLOAD_URL, post.videoDownloadUrl)
            }
            else -> {
                videoUrl?.let { intent.data = Uri.parse(it) }
                intent.putExtra(ViewVideoActivity.EXTRA_SUBREDDIT, post.subredditName)
                intent.putExtra(ViewVideoActivity.EXTRA_ID, post.id)
                intent.putExtra(ViewVideoActivity.EXTRA_VIDEO_DOWNLOAD_URL, post.videoDownloadUrl)
            }
        }
        intent.putExtra(ViewVideoActivity.EXTRA_POST, post)
        if (progressMs > 0) {
            intent.putExtra(ViewVideoActivity.EXTRA_PROGRESS_SECONDS, progressMs)
        }
        intent.putExtra(ViewVideoActivity.EXTRA_IS_NSFW, post.isNSFW)
        context.startActivity(intent)
    }

    /** The GIF_TYPE branch for a GIF that has an mp4 variant. */
    fun openGifMp4(context: Context, post: Post, mp4Url: String, progressMs: Long) {
        val intent = Intent(context, ViewVideoActivity::class.java)
        intent.data = Uri.parse(mp4Url)
        intent.putExtra(ViewVideoActivity.EXTRA_VIDEO_TYPE, ViewVideoActivity.VIDEO_TYPE_DIRECT)
        intent.putExtra(ViewVideoActivity.EXTRA_SUBREDDIT, post.subredditName)
        intent.putExtra(ViewVideoActivity.EXTRA_ID, post.id)
        intent.putExtra(ViewVideoActivity.EXTRA_VIDEO_DOWNLOAD_URL, mp4Url)
        intent.putExtra(ViewVideoActivity.EXTRA_POST, post)
        if (progressMs > 0) {
            intent.putExtra(ViewVideoActivity.EXTRA_PROGRESS_SECONDS, progressMs)
        }
        intent.putExtra(ViewVideoActivity.EXTRA_IS_NSFW, post.isNSFW)
        context.startActivity(intent)
    }

    /** The LINK_TYPE / NO_PREVIEW_LINK_TYPE branch. Nothing to open without a URL. */
    fun openLink(context: Context, post: Post) {
        val url = post.url ?: return
        val intent = Intent(context, LinkResolverActivity::class.java)
        intent.data = Uri.parse(url)
        intent.putExtra(LinkResolverActivity.EXTRA_IS_NSFW, post.isNSFW)
        intent.putExtra(LinkResolverActivity.EXTRA_SUBREDDIT_NAME, post.subredditName)
        intent.putExtra(LinkResolverActivity.EXTRA_POST_TITLE_KEY, post.title)
        context.startActivity(intent)
    }

    /** The GALLERY_TYPE branch, opened on [galleryItemIndex]. */
    fun openGallery(context: Context, post: Post, galleryItemIndex: Int) {
        val intent = Intent(context, ViewRedditGalleryActivity::class.java)
        intent.putExtra(ViewRedditGalleryActivity.EXTRA_POST, post)
        intent.putExtra(ViewRedditGalleryActivity.EXTRA_GALLERY_ITEM_INDEX, galleryItemIndex)
        context.startActivity(intent)
    }
}
