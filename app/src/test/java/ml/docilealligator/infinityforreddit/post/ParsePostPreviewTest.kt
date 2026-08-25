package ml.docilealligator.infinityforreddit.post

import ml.docilealligator.infinityforreddit.TestInfinity
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [ParsePost] turns Reddit's listing JSON into [Post]. Two behaviours here were shipped as fixes and
 * had nothing naming them afterwards:
 *
 *  * a link post that points at another Reddit post carries the linked post's image on the
 *    `external-preview.redd.it` host, which does not load reliably; it is rewritten to the canonical
 *    `preview.redd.it`. Only reddit links are rewritten, so a news site's genuine external preview
 *    keeps its own host;
 *  * a crosspost of a post that embeds its images inline in the body must not also surface a
 *    Reddit-generated preview or the parent's 140px thumbnail — both render as a blurry duplicate of
 *    an image the body already shows (issue #317).
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestInfinity::class)
class ParsePostPreviewTest {

    private fun previewBlock(sourceUrl: String, resolutionUrl: String) = JSONObject().apply {
        put("images", JSONArray().put(JSONObject().apply {
            put("source", JSONObject().apply {
                put("url", sourceUrl); put("width", 1200); put("height", 800)
            })
            put("resolutions", JSONArray().put(JSONObject().apply {
                put("url", resolutionUrl); put("width", 108); put("height", 72)
            }))
        }))
    }

    private fun mediaMetadataBlock(id: String, url: String) = JSONObject().apply {
        put(id, JSONObject().apply {
            put("status", "valid")
            put("e", "Image")
            put("id", id)
            put("s", JSONObject().apply { put("x", 1200); put("y", 800); put("u", url) })
        })
    }

    /** A listing entry with every field [ParsePost.parseBasicData] reads unconditionally. */
    private fun postJson(
        id: String = "abc123",
        permalink: String = "/r/bestof/comments/abc123/a_title/",
        url: String = "https://www.reddit.com/r/MadeMeSmile/comments/xyz987/original/",
        domain: String = "reddit.com",
        thumbnail: String = "self",
        preview: JSONObject? = null,
        mediaMetadata: JSONObject? = null,
        crosspostParent: JSONObject? = null
    ) = JSONObject().apply {
        put("id", id)
        put("name", "t3_$id")
        put("subreddit", "bestof")
        put("subreddit_name_prefixed", "r/bestof")
        put("author", "someone")
        put("distinguished", JSONObject.NULL)
        put("created_utc", 1700000000L)
        put("title", "A title")
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
        put("permalink", permalink)
        put("thumbnail", thumbnail)
        put("domain", domain)
        put("is_video", false)
        put("url", url)
        put("selftext", "")
        put("selftext_html", JSONObject.NULL)
        if (preview != null) put("preview", preview)
        if (mediaMetadata != null) put("media_metadata", mediaMetadata)
        if (crosspostParent != null) put("crosspost_parent_list", JSONArray().put(crosspostParent))
    }

    @Test
    fun `a link to another reddit post has its preview moved to the host that serves it`() {
        val post = ParsePost.parseBasicData(
            postJson(
                domain = "reddit.com",
                preview = previewBlock(
                    "https://external-preview.redd.it/full.jpg?width=1200&s=sig",
                    "https://external-preview.redd.it/small.jpg?width=108&s=sig"
                )
            )
        )

        assertEquals(
            listOf(
                "https://preview.redd.it/full.jpg?width=1200&s=sig",
                "https://preview.redd.it/small.jpg?width=108&s=sig"
            ),
            post.previews.map { it.previewUrl }
        )
    }

    @Test
    fun `a reddit subdomain is a reddit link too`() {
        val post = ParsePost.parseBasicData(
            postJson(
                domain = "old.reddit.com",
                preview = previewBlock(
                    "https://external-preview.redd.it/full.jpg",
                    "https://external-preview.redd.it/small.jpg"
                )
            )
        )

        assertEquals("https://preview.redd.it/full.jpg", post.previews[0].previewUrl)
    }

    @Test
    fun `a link to somewhere other than reddit keeps its own preview host`() {
        val post = ParsePost.parseBasicData(
            postJson(
                url = "https://www.bbc.co.uk/news/a-story",
                domain = "bbc.co.uk",
                preview = previewBlock(
                    "https://external-preview.redd.it/full.jpg?s=sig",
                    "https://external-preview.redd.it/small.jpg?s=sig"
                )
            )
        )

        assertEquals(
            listOf(
                "https://external-preview.redd.it/full.jpg?s=sig",
                "https://external-preview.redd.it/small.jpg?s=sig"
            ),
            post.previews.map { it.previewUrl }
        )
    }

    @Test
    fun `a domain that merely ends in reddit-dot-com is not reddit`() {
        val post = ParsePost.parseBasicData(
            postJson(
                url = "https://notreddit.com/a-story",
                domain = "notreddit.com",
                preview = previewBlock(
                    "https://external-preview.redd.it/full.jpg",
                    "https://external-preview.redd.it/small.jpg"
                )
            )
        )

        assertEquals("https://external-preview.redd.it/full.jpg", post.previews[0].previewUrl)
    }

    @Test
    fun `a crosspost of a body-embedded-media post shows the body, not a duplicate preview`() {
        val parent = postJson(
            id = "parent1",
            permalink = "/r/original/comments/parent1/original/",
            url = "https://www.reddit.com/r/original/comments/parent1/original/",
            thumbnail = "https://b.thumbs.redditmedia.com/parent140.jpg",
            preview = previewBlock(
                "https://preview.redd.it/parentfull.jpg",
                "https://preview.redd.it/parentsmall.jpg"
            ),
            mediaMetadata = mediaMetadataBlock("img1", "https://preview.redd.it/img1.jpg")
        )
        val post = ParsePost.parseBasicData(
            postJson(
                preview = previewBlock(
                    "https://preview.redd.it/xfull.jpg",
                    "https://preview.redd.it/xsmall.jpg"
                ),
                crosspostParent = parent
            )
        )

        assertTrue("the body renders the media itself", post.embedsInlineBodyMedia())
        assertTrue("no duplicate preview above the body", post.previews.isEmpty())
        assertEquals("the parent's 140px thumbnail is not upscaled above the body", "self", post.thumbnailUrl)
    }

    @Test
    fun `a crosspost of a post without inline media still inherits the parent's preview`() {
        val parent = postJson(
            id = "parent2",
            permalink = "/r/original/comments/parent2/original/",
            url = "https://i.redd.it/parent.jpg",
            domain = "i.redd.it",
            thumbnail = "https://b.thumbs.redditmedia.com/parent140.jpg",
            preview = previewBlock(
                "https://preview.redd.it/parentfull.jpg",
                "https://preview.redd.it/parentsmall.jpg"
            )
        )
        val post = ParsePost.parseBasicData(postJson(crosspostParent = parent))

        assertFalse(post.embedsInlineBodyMedia())
        assertEquals("https://preview.redd.it/parentfull.jpg", post.previews[0].previewUrl)
        assertEquals("https://b.thumbs.redditmedia.com/parent140.jpg", post.thumbnailUrl)
    }
}
