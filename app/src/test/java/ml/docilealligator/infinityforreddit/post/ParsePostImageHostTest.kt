package ml.docilealligator.infinityforreddit.post

import android.os.Parcel
import ml.docilealligator.infinityforreddit.TestInfinity
import ml.docilealligator.infinityforreddit.utils.ImageHostUtils
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Reddit types `imgchest.com/p/<id>` and `imgbb.com/<id>` as `post_hint: link`, correctly -- the URL
 * is an HTML page and the picture is on a different host -- so without this promotion they render as
 * link cards that open a browser, which is what r/anime's Megami Magazine threads did.
 *
 * They are promoted to gallery posts rather than image posts, because the card that swipes through
 * several pictures already exists and is the gallery one. Only the cover is known at parse time, so
 * the gallery starts as that one tile and a bound row fills in the rest.
 *
 * The promotion sits on one arm of `ParsePost`, immediately after the video-host chain, and that
 * placement is the whole design: it runs late enough that a video host wins, and only on the arm
 * where Reddit gave the post a preview for the card to draw. Both of those are silent when they
 * break -- a post simply renders as the wrong kind of card -- so they are pinned here.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestInfinity::class)
class ParsePostImageHostTest {

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
        id: String = "1j1t9v4"
    ) = JSONObject().apply {
        put("id", id)
        put("name", "t3_$id")
        put("subreddit", "anime")
        put("subreddit_name_prefixed", "r/anime")
        put("author", "someone")
        put("distinguished", JSONObject.NULL)
        put("created_utc", 1789017851L)
        put("title", "Megami Magazine - April 2025")
        put("score", 477)
        put("num_comments", 25)
        put("upvote_ratio", 0.95)
        put("hidden", false)
        put("spoiler", false)
        put("over_18", true)
        put("stickied", false)
        put("archived", false)
        put("locked", false)
        put("saved", false)
        put("send_replies", true)
        put("can_mod_post", false)
        put("likes", JSONObject.NULL)
        put("permalink", "/r/anime/comments/$id/megami_magazine_april_2025/")
        put("thumbnail", "")
        put("domain", domain)
        put("is_video", false)
        put("url", url)
        put("selftext", "")
        put("selftext_html", JSONObject.NULL)
        if (preview != null) put("preview", preview)
    }

    @Test
    fun `an imgchest album link becomes a gallery post`() {
        val post = ParsePost.parseBasicData(
            postJson(
                url = "https://imgchest.com/p/n87wl2angyx",
                domain = "imgchest.com",
                preview = previewBlock()
            )
        )

        // A gallery post, because the card that swipes horizontally is the gallery card. What the
        // album actually holds is not known here -- the URL names it, not its contents -- so the
        // gallery is seeded with the cover Reddit previewed and a bound row fills in the rest.
        assertEquals(Post.GALLERY_TYPE, post.postType)
        assertTrue(post.isImageHostAlbum)
        assertFalse(post.isImageHostGalleryResolved)
        assertEquals(ImageHostUtils.Host.IMGCHEST, post.imageHost)
        assertEquals("n87wl2angyx", post.imageHostId)
        assertEquals(1, post.gallery.size)
        assertEquals("https://external-preview.redd.it/full.jpg", post.gallery[0].url)
    }

    @Test
    fun `an imgbb link becomes a gallery post`() {
        val post = ParsePost.parseBasicData(
            postJson(url = "https://ibb.co/zNBxjX8", domain = "ibb.co", preview = previewBlock())
        )

        assertEquals(Post.GALLERY_TYPE, post.postType)
        assertEquals(ImageHostUtils.Host.IMGBB, post.imageHost)
        assertEquals("zNBxjX8", post.imageHostId)
    }

    @Test
    fun `the url is not rewritten to point at the album`() {
        // Copy link, open in browser and share all read this, and all three should keep naming the
        // page the poster linked rather than a CDN file the app resolved for itself.
        val url = "https://imgchest.com/p/9rydn3x8d4k"
        val post = ParsePost.parseBasicData(
            postJson(url = url, domain = "imgchest.com", preview = previewBlock())
        )

        assertEquals(url, post.url)
    }

    @Test
    fun `an album with no preview stays a link`() {
        // The card has nothing of its own to draw until the album is read -- the seeded tile is
        // Reddit's preview. Promoting a post that has none would leave an empty carousel, so the
        // promotion deliberately runs only on the arm where a preview exists.
        val post = ParsePost.parseBasicData(
            postJson(url = "https://imgchest.com/p/n87wl2angyx", domain = "imgchest.com")
        )

        assertEquals(Post.NO_PREVIEW_LINK_TYPE, post.postType)
        assertFalse(post.isImageHostAlbum)
        assertNull(post.imageHostId)
    }

    @Test
    fun `a direct cdn file is an ordinary image post and not an album`() {
        // The r/Deltarune post that prompted this feature. It is already handled by the extension
        // branches, and treating it as an album would send the viewer to scrape a PNG as HTML.
        val url = "https://cdn.imgchest.com/files/95805b54f207.png"
        val post = ParsePost.parseBasicData(
            postJson(url = url, domain = "cdn.imgchest.com", preview = previewBlock())
        )

        assertEquals(Post.IMAGE_TYPE, post.postType)
        assertFalse(post.isImageHostAlbum)
        assertNull(post.imageHost)
        assertEquals(url, post.url)
    }

    @Test
    fun `a direct imgbb cdn file is not an album either`() {
        val post = ParsePost.parseBasicData(
            postJson(url = "https://i.ibb.co/abc123/name.jpg", domain = "i.ibb.co", preview = previewBlock())
        )

        assertEquals(Post.IMAGE_TYPE, post.postType)
        assertFalse(post.isImageHostAlbum)
    }

    @Test
    fun `a video host is still a video and never an album`() {
        // The image arm was added to the same branch the video chain runs on, so the risk is that a
        // host the video chain claims comes out as an image card that opens an album viewer.
        //
        // This pins the outcome, not the mechanism: `applyExternalImageHost` is guarded on the post
        // still being a link, but no URL currently satisfies both chains -- the video hosts are
        // named domains and extensions, and an `.mp4` path fails the alphanumeric id check -- so
        // that guard is defensive and mutating it away does not fail anything here. Removing it is
        // still wrong the day a host appears on both lists, which is why it stays.
        val post = ParsePost.parseBasicData(
            postJson(url = "https://streamable.com/abc123", domain = "streamable.com", preview = previewBlock())
        )

        assertEquals(Post.VIDEO_TYPE, post.postType)
        assertFalse(post.isImageHostAlbum)
    }

    @Test
    fun `an ordinary link post is left alone`() {
        val post = ParsePost.parseBasicData(
            postJson(url = "https://www.bbc.co.uk/news/12345", domain = "bbc.co.uk", preview = previewBlock())
        )

        assertEquals(Post.LINK_TYPE, post.postType)
        assertFalse(post.isImageHostAlbum)
    }

    @Test
    fun `the album survives being parcelled`() {
        // Every hop between the feed and a viewer parcels the post, and the two fields are written
        // and read positionally. A field added to one side only shifts everything after it, which
        // corrupts unrelated flags rather than failing where the mistake is.
        val original = ParsePost.parseBasicData(
            postJson(url = "https://imgchest.com/p/n87wl2angyx", domain = "imgchest.com", preview = previewBlock())
        )

        val parcel = Parcel.obtain()
        try {
            original.writeToParcel(parcel, 0)
            parcel.setDataPosition(0)
            val restored = Post.CREATOR.createFromParcel(parcel)

            assertEquals(ImageHostUtils.Host.IMGCHEST, restored.imageHost)
            assertEquals("n87wl2angyx", restored.imageHostId)
            assertTrue(restored.isImageHostAlbum)
            // Witnesses for a shift, and they have to be fields written *after* the two new ones
            // with values distinctive enough to be wrong visibly. The booleans that immediately
            // follow are all false here, so reading one field late still yields false and proves
            // nothing; these are the first that cannot.
            assertEquals(original.permalink, restored.permalink)
            assertEquals(original.postTimeMillis, restored.postTimeMillis)
            assertEquals(original.score.toLong(), restored.score.toLong())
            assertEquals(original.postType, restored.postType)
            // Written before them, so this is a plain sanity check rather than a shift witness.
            assertEquals(original.url, restored.url)
        } finally {
            parcel.recycle()
        }
    }

    @Test
    fun `demoting to a link clears the album`() {
        // Without this the post keeps a host and an id while claiming to be a link, and the next
        // thing to read isImageHostAlbum would route a link card at the album viewer.
        val post = ParsePost.parseBasicData(
            postJson(url = "https://imgchest.com/p/n87wl2angyx", domain = "imgchest.com", preview = previewBlock())
        )
        post.demoteToLinkPost()

        assertEquals(Post.LINK_TYPE, post.postType)
        assertFalse(post.isImageHostAlbum)
        assertNull(post.imageHost)
        assertNull(post.imageHostId)
    }
}
