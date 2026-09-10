package ml.docilealligator.infinityforreddit.post

import ml.docilealligator.infinityforreddit.TestInfinity
import ml.docilealligator.infinityforreddit.utils.ShortClipHostUtils
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [ParsePost] decides which link posts are really videos. That decision used to be five copies of
 * the same host chain, one per parse path, and they had drifted: only one carried the tumblr arm,
 * only one was guarded against a malformed URL, and one set the post type redundantly. They are one
 * helper now, and nothing named that behaviour before -- no test asserted that even a
 * `streamable.com` link becomes a video.
 *
 * So these pin the whole chain, for the new clip hosts and for the two that were already there, on
 * each shape a post can arrive in. A listing that lands on a different path than the one a host was
 * added to is the bug this is here to catch, and it is a miserable one to chase from a bug report
 * because it depends on which feed the reader happened to open.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestInfinity::class)
class ParsePostVideoHostTest {

    @After
    fun restoreInlinePlayback() {
        ShortClipHostUtils.inlinePlaybackEnabled = true
    }

    private fun previewBlock() = JSONObject().apply {
        put("images", JSONArray().put(JSONObject().apply {
            put("source", JSONObject().apply {
                put("url", "https://external-preview.redd.it/full.jpg"); put("width", 1200); put("height", 800)
            })
            put("resolutions", JSONArray().put(JSONObject().apply {
                put("url", "https://external-preview.redd.it/small.jpg"); put("width", 108); put("height", 72)
            }))
        }))
    }

    /** A listing entry with every field [ParsePost.parseBasicData] reads unconditionally. */
    private fun postJson(
        url: String,
        domain: String,
        preview: JSONObject? = null,
        crosspostParent: JSONObject? = null,
        id: String = "abc123"
    ) = JSONObject().apply {
        put("id", id)
        put("name", "t3_$id")
        put("subreddit", "soccer")
        put("subreddit_name_prefixed", "r/soccer")
        put("author", "someone")
        put("distinguished", JSONObject.NULL)
        put("created_utc", 1789017851L)
        put("title", "A goal")
        put("score", 10)
        put("num_comments", 3)
        put("upvote_ratio", 0.95)
        put("hidden", false)
        put("spoiler", false)
        put("over_18", false)
        put("stickied", false)
        put("archived", false)
        put("locked", false)
        put("saved", false)
        put("send_replies", true)
        put("can_mod_post", false)
        put("likes", JSONObject.NULL)
        put("permalink", "/r/soccer/comments/$id/a_goal/")
        put("thumbnail", "")
        put("domain", domain)
        put("is_video", false)
        put("url", url)
        put("selftext", "")
        put("selftext_html", JSONObject.NULL)
        if (preview != null) put("preview", preview)
        if (crosspostParent != null) put("crosspost_parent_list", JSONArray().put(crosspostParent))
    }

    @Test
    fun `a clip host link with no preview becomes a video`() {
        // r/soccer's moderators have thumbnails switched off, so its clip posts arrive on the
        // no-preview path -- the busiest subreddit for this feature takes the branch that is
        // easiest to forget.
        val post = ParsePost.parseBasicData(
            postJson(url = "https://streamain.com/en/7OWwVWjPv6GNI4W/watch", domain = "streamain.com")
        )

        assertEquals(Post.VIDEO_TYPE, post.postType)
        assertTrue(post.isShortClip)
        assertEquals(ShortClipHostUtils.Host.STREAMAIN, post.shortClipHost)
        assertEquals("7OWwVWjPv6GNI4W", post.shortClipId)
    }

    @Test
    fun `a clip host link with a preview becomes a video`() {
        val post = ParsePost.parseBasicData(
            postJson(
                url = "https://streamin.link/v/dfc4c91e",
                domain = "streamin.link",
                preview = previewBlock()
            )
        )

        assertEquals(Post.VIDEO_TYPE, post.postType)
        assertEquals(ShortClipHostUtils.Host.STREAMIN, post.shortClipHost)
        assertEquals("dfc4c91e", post.shortClipId)
    }

    @Test
    fun `a crossposted clip host link becomes a video`() {
        val parent = postJson(
            url = "https://dubz.link/c/29640a",
            domain = "dubz.link",
            preview = previewBlock(),
            id = "parent1"
        )
        val post = ParsePost.parseBasicData(
            postJson(url = "https://dubz.link/c/29640a", domain = "dubz.link", crosspostParent = parent)
        )

        assertEquals(Post.VIDEO_TYPE, post.postType)
        assertEquals(ShortClipHostUtils.Host.DUBZ, post.shortClipHost)
        assertEquals("29640a", post.shortClipId)
    }

    @Test
    fun `the video url stays on the share page for the resolver to overwrite`() {
        // The MP4 is not known at parse time. Handing the player the share page would make it try
        // to decode HTML as video, so the contract is that videoUrl holds the page until
        // FetchShortClipVideo replaces it -- and that the post's own url is never touched, so copy
        // link and open in browser keep naming what the poster linked.
        val url = "https://dropr.co/v/1363fa0c"
        val post = ParsePost.parseBasicData(postJson(url = url, domain = "dropr.co"))

        assertEquals(url, post.videoUrl)
        assertEquals(url, post.url)
        assertFalse(post.isLoadedStreamableVideoAlready)
    }

    @Test
    fun `a clip host post is not a normal video`() {
        // isNormalVideo gates the ExoPlayer quality selector, which only means anything for a
        // Reddit HLS stream. These are single-track progressive MP4s.
        val post = ParsePost.parseBasicData(
            postJson(url = "https://streamff.pro/v/3cdd35fe", domain = "streamff.pro")
        )

        assertEquals(Post.VIDEO_TYPE, post.postType)
        assertFalse(post.isNormalVideo)
    }

    @Test
    fun `the setting switched off leaves clip hosts as link posts`() {
        ShortClipHostUtils.inlinePlaybackEnabled = false

        val post = ParsePost.parseBasicData(
            postJson(url = "https://streamain.com/en/7OWwVWjPv6GNI4W/watch", domain = "streamain.com")
        )

        assertEquals(Post.NO_PREVIEW_LINK_TYPE, post.postType)
        assertFalse(post.isShortClip)
        assertNull(post.shortClipId)
    }

    @Test
    fun `a streamable link still becomes a video on every path`() {
        // Nothing asserted this before the refactor, which put it at risk.
        val noPreview = ParsePost.parseBasicData(
            postJson(url = "https://streamable.com/abc123", domain = "streamable.com")
        )
        assertEquals(Post.VIDEO_TYPE, noPreview.postType)
        assertTrue(noPreview.isStreamable)
        assertEquals("abc123", noPreview.streamableShortCode)

        val withPreview = ParsePost.parseBasicData(
            postJson(url = "https://streamable.com/abc123", domain = "streamable.com", preview = previewBlock())
        )
        assertEquals(Post.VIDEO_TYPE, withPreview.postType)
        assertTrue(withPreview.isStreamable)
    }

    @Test
    fun `the tumblr arm still runs only on the no-preview path`() {
        // It was only ever on that one copy. A tumblr mp4 that has a preview is caught earlier by
        // the generic mp4 branch, so running the arm everywhere would change how those render.
        val noPreview = ParsePost.parseBasicData(
            postJson(url = "https://example.tumblr.com/clip.mp4", domain = "tumblr.com")
        )
        assertEquals(Post.VIDEO_TYPE, noPreview.postType)
        assertTrue(noPreview.isTumblr)

        val withPreview = ParsePost.parseBasicData(
            postJson(url = "https://example.tumblr.com/clip.mp4", domain = "tumblr.com", preview = previewBlock())
        )
        assertEquals(Post.VIDEO_TYPE, withPreview.postType)
        assertFalse(withPreview.isTumblr)
    }

    @Test
    fun `an mlb highlight with no preview becomes a video`() {
        // Reddit generates no preview for these links, and the generic mp4 promotion only runs on
        // the branch for posts that have one, so r/baseball highlights arrived as link cards.
        val url = "https://mlb-cuts-diamond.mlb.com/FORGE/2026/2026-09/09/" +
            "4b7a1a43-10dff22b-30483077-csvm-diamondgcp-asset_1280x720_59_16000K.mp4"
        val post = ParsePost.parseBasicData(postJson(url = url, domain = "mlb-cuts-diamond.mlb.com"))

        assertEquals(Post.VIDEO_TYPE, post.postType)
        assertEquals(url, post.videoUrl)
        assertEquals(url, post.videoDownloadUrl)
        assertFalse(post.isShortClip)
    }

    @Test
    fun `an ordinary link post is left alone`() {
        val post = ParsePost.parseBasicData(
            postJson(url = "https://www.bbc.co.uk/sport/football/12345", domain = "bbc.co.uk")
        )

        assertEquals(Post.NO_PREVIEW_LINK_TYPE, post.postType)
        assertFalse(post.isShortClip)
        assertFalse(post.isStreamable)
    }
}
