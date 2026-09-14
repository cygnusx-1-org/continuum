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
 *    an image the body already shows (issue #317);
 *  * a text post that embeds an image but has no Reddit-generated `preview` (Reddit builds one
 *    from the first link in a body, not from uploaded images) gets that image as its preview, so
 *    the feed shows it instead of the bare URL.
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

    private fun mediaMetadataBlock(id: String, url: String, downscaledUrl: String? = null) = JSONObject().apply {
        put(id, JSONObject().apply {
            put("status", "valid")
            put("e", "Image")
            put("id", id)
            put("s", JSONObject().apply { put("x", 1200); put("y", 800); put("u", url) })
            if (downscaledUrl != null) {
                put("p", JSONArray().put(JSONObject().apply {
                    put("x", 320); put("y", 213); put("u", downscaledUrl)
                }))
            }
        })
    }

    /**
     * A self post's own permalink, which is what Reddit puts in its `url` -- how [ParsePost] tells a
     * text post from a link post.
     */
    private val selfUrl = "https://www.reddit.com/r/bestof/comments/abc123/a_title/"

    /** A listing entry with every field [ParsePost.parseBasicData] reads unconditionally. */
    private fun postJson(
        id: String = "abc123",
        permalink: String = "/r/bestof/comments/abc123/a_title/",
        url: String = "https://www.reddit.com/r/MadeMeSmile/comments/xyz987/original/",
        domain: String = "reddit.com",
        thumbnail: String = "self",
        preview: JSONObject? = null,
        mediaMetadata: JSONObject? = null,
        crosspostParent: JSONObject? = null,
        selftext: String = "",
        selftextHtml: String? = null
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
        put("selftext", selftext)
        put("selftext_html", selftextHtml ?: JSONObject.NULL)
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

    @Test
    fun `a text post without a preview shows the first image its body embeds`() {
        val full = "https://preview.redd.it/img1.png?width=576&format=png&auto=webp&s=abc"
        val small = "https://preview.redd.it/img1.png?width=320&crop=smart&auto=webp&s=def"
        val post = ParsePost.parseBasicData(
            postJson(
                url = selfUrl,
                domain = "self.bestof",
                thumbnail = "https://external-preview.redd.it/og140.png?width=140&height=70",
                mediaMetadata = mediaMetadataBlock("img1", full, downscaledUrl = small),
                selftext = "$full\n\nSome words about it."
            )
        )

        assertEquals(Post.TEXT_TYPE, post.postType)
        assertTrue("the body still renders the image itself", post.embedsInlineBodyMedia())
        assertEquals(listOf(full, small), post.previews.map { it.previewUrl })
        assertEquals(1200, post.previews[0].previewWidth)
        assertEquals(800, post.previews[0].previewHeight)
        assertTrue(post.isInlineBodyImagePreview)
    }

    @Test
    fun `the snippet is split around the image the card shows`() {
        val full = "https://preview.redd.it/img1.png?width=576&s=x"
        val post = ParsePost.parseBasicData(
            postJson(
                url = selfUrl,
                domain = "self.bestof",
                mediaMetadata = mediaMetadataBlock("img1", full),
                selftext = "Here is what I mean:\n\n$full\n\nAnd that is that.",
                selftextHtml = "<!-- SC_OFF --><div class=\"md\"><p>Here is what I mean:</p>\n\n" +
                        "<p>$full</p>\n\n<p>And that is that.</p>\n</div><!-- SC_ON -->"
            )
        )

        assertTrue(post.isInlineBodyImagePreview)
        assertTrue(post.hasInlineImageSnippetSplit())
        assertEquals("Here is what I mean:", post.selfTextPlainTrimmedBeforeInlineImage)
        assertEquals("And that is that.", post.selfTextPlainTrimmedAfterInlineImage)
        assertEquals(
            "the whole snippet is still there for the cards with one slot",
            "Here is what I mean:\n\nAnd that is that.",
            post.selfTextPlainTrimmed,
        )
    }

    @Test
    fun `a body whose image is written with a caption leaves the snippet unsplit`() {
        // Reddit renders [caption](url) as an anchor, so the URL never appears in the plain text
        // the snippet is built from and there is nothing to split on.
        val full = "https://preview.redd.it/img1.png?width=576&s=x"
        val post = ParsePost.parseBasicData(
            postJson(
                url = selfUrl,
                domain = "self.bestof",
                mediaMetadata = mediaMetadataBlock("img1", full),
                selftext = "[a lime]($full)\n\nWords.",
                selftextHtml = "<!-- SC_OFF --><div class=\"md\"><p><a href=\"$full\">a lime</a></p>\n\n" +
                        "<p>Words.</p>\n</div><!-- SC_ON -->"
            )
        )

        assertTrue(post.isInlineBodyImagePreview)
        assertFalse(post.hasInlineImageSnippetSplit())
        assertEquals("a lime\n\nWords.", post.selfTextPlainTrimmed)
    }

    @Test
    fun `a reddit-generated preview is not an inline body image`() {
        val post = ParsePost.parseBasicData(
            postJson(
                url = selfUrl,
                domain = "self.bestof",
                preview = previewBlock(
                    "https://preview.redd.it/ogfull.jpg",
                    "https://preview.redd.it/ogsmall.jpg"
                ),
                selftext = "A link to somewhere else."
            )
        )

        assertEquals("https://preview.redd.it/ogfull.jpg", post.previews[0].previewUrl)
        assertFalse(post.isInlineBodyImagePreview)
    }

    @Test
    fun `the snippet drops the URL of an image the body renders`() {
        val full = "https://preview.redd.it/img1.png?width=576&format=png&auto=webp&s=abc"
        val other = "https://example.com/img1.png"
        val post = ParsePost.parseBasicData(
            postJson(
                url = selfUrl,
                domain = "self.bestof",
                mediaMetadata = mediaMetadataBlock("img1", full),
                selftext = "$full\n\nWords about it. Also $other",
                selftextHtml = "<!-- SC_OFF --><div class=\"md\"><p>$full</p>\n\n" +
                        "<p>Words about it. Also $other</p>\n</div><!-- SC_ON -->"
            )
        )

        assertEquals("Words about it. Also $other", post.selfTextPlainTrimmed)
        assertTrue("the full body keeps it", post.selfTextPlain!!.contains(full))
    }

    @Test
    fun `the embedded image is taken in body order, not media_metadata order`() {
        val first = "https://i.redd.it/first1.jpg"
        val second = "https://preview.redd.it/second2.png?width=576&s=x"
        val metadata = mediaMetadataBlock("second2", second)
        mediaMetadataBlock("first1", first).let { metadata.put("first1", it.getJSONObject("first1")) }
        val post = ParsePost.parseBasicData(
            postJson(
                url = selfUrl,
                domain = "self.bestof",
                mediaMetadata = metadata,
                selftext = "![img]($first)\n\n![img]($second)"
            )
        )

        assertEquals(first, post.previews[0].previewUrl)
    }

    @Test
    fun `a text post with a reddit-generated preview keeps it over the embedded image`() {
        val embedded = "https://preview.redd.it/img1.png?width=576&s=x"
        val post = ParsePost.parseBasicData(
            postJson(
                url = selfUrl,
                domain = "self.bestof",
                preview = previewBlock(
                    "https://preview.redd.it/ogfull.jpg",
                    "https://preview.redd.it/ogsmall.jpg"
                ),
                mediaMetadata = mediaMetadataBlock("img1", embedded),
                selftext = embedded
            )
        )

        assertEquals("https://preview.redd.it/ogfull.jpg", post.previews[0].previewUrl)
    }

    @Test
    fun `an embedded gif or a URL media_metadata does not describe yields no preview`() {
        val gif = "https://i.redd.it/anim1.gif"
        val metadata = JSONObject().put("anim1", JSONObject().apply {
            put("status", "valid")
            put("e", "AnimatedImage")
            put("id", "anim1")
            put("s", JSONObject().apply {
                put("x", 480); put("y", 270); put("gif", gif); put("mp4", "https://i.redd.it/anim1.mp4")
            })
        })
        val post = ParsePost.parseBasicData(
            postJson(
                url = selfUrl,
                domain = "self.bestof",
                mediaMetadata = metadata,
                selftext = "![gif]($gif)\n\nhttps://preview.redd.it/unknown9.png?width=1&s=y"
            )
        )

        assertTrue("the gif is inline media", post.embedsInlineBodyMedia())
        assertTrue(post.previews.isEmpty())
    }

    @Test
    fun `a text post that embeds nothing has no preview`() {
        val post = ParsePost.parseBasicData(
            postJson(url = selfUrl, domain = "self.bestof", selftext = "Just words.")
        )

        assertFalse(post.embedsInlineBodyMedia())
        assertTrue(post.previews.isEmpty())
    }
}
