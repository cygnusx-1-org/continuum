package ml.docilealligator.infinityforreddit.post

import ml.docilealligator.infinityforreddit.TestInfinity
import ml.docilealligator.infinityforreddit.utils.JSONUtils
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Body images Reddit's listing reports as failed (see [FailedRedditMedia]). The gallery side is in
 * [ParsePostGalleryTest].
 *
 * The listing gives such an image as `{"status": "failed"}` in `media_metadata` and writes
 * `*Processing img <id>...*` in the body where the image goes. Here that shape is laid over
 * `text_image` from [InlineBodyImageFixtures], which is the same post with the image healthy.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestInfinity::class)
class FailedRedditMediaTest {

    private val id = "of804o39afph1"

    /** `text_image` as the listing gives it when its image is reported failed. */
    private fun failedTextImage(): JSONObject {
        val json = InlineBodyImageFixtures.json("text_image")
        json.put("selftext", "This is text.\n\n*Processing img $id...*")
        json.put(
            "selftext_html",
            "<!-- SC_OFF --><div class=\"md\"><p>This is text.</p>\n\n<p><em>Processing img $id...</em></p>\n</div><!-- SC_ON -->"
        )
        json.put("media_metadata", JSONObject().put(id, JSONObject().put("status", "failed")))
        return json
    }

    @Test
    fun `a failed entry is kept as an image at its i-redd-it url`() {
        val data = JSONObject().put("media_metadata", JSONObject().put("abc123", JSONObject().put("status", "failed")))
        val media = checkNotNull(JSONUtils.parseMediaMetadata(data)?.get("abc123"))

        assertEquals("Image", media.e)
        assertEquals("https://i.redd.it/abc123.jpeg", media.original.url)
        assertEquals(0, media.original.x)
        assertEquals(0, media.original.y)
    }

    @Test
    fun `a failed entry takes its extension from a thumbnail that names it`() {
        val data = JSONObject()
            .put("thumbnail", "https://preview.redd.it/abc123.png?width=140&height=140&crop=1:1,smart&auto=webp&s=0")
            .put("media_metadata", JSONObject().put("abc123", JSONObject().put("status", "failed")))

        assertEquals("https://i.redd.it/abc123.png", JSONUtils.parseMediaMetadata(data)?.get("abc123")?.original?.url)
    }

    @Test
    fun `the processing placeholder in a body becomes the image`() {
        val selfText = ParsePost.parseBasicData(failedTextImage()).selfText.orEmpty()

        // text_image's thumbnail is a png crop of this image, so the extension is .png.
        assertTrue(selfText, selfText.contains("![](https://i.redd.it/$id.png)"))
        assertTrue(selfText, !selfText.contains("Processing img"))
    }

    @Test
    fun `a body led to by a failed image gets it as the card preview, size unknown`() {
        val post = ParsePost.parseBasicData(failedTextImage())

        assertTrue(post.isInlineBodyImagePreview)
        assertEquals("https://i.redd.it/$id.png", post.previews[0].previewUrl)
        // The thumbnail is a 1:1 crop, so it says nothing about the image's shape.
        assertEquals(0, post.previews[0].previewWidth)
        assertEquals(0, post.previews[0].previewHeight)
    }

    @Test
    fun `the placeholder is not left in the feed snippet`() {
        val post = ParsePost.parseBasicData(failedTextImage())

        assertEquals("This is text.", post.selfTextPlainTrimmed)
        assertEquals("This is text.", post.selfTextPlainTrimmedBeforeInlineImage)
        assertEquals("", post.selfTextPlainTrimmedAfterInlineImage)
    }

    @Test
    fun `a placeholder for an image the listing does not report failed stays as text`() {
        // Still processing: no entry at all, so there is nothing to show but Reddit's words.
        val json = failedTextImage()
        json.put("media_metadata", JSONObject())
        val selfText = ParsePost.parseBasicData(json).selfText.orEmpty()

        assertTrue(selfText, selfText.contains("Processing img $id..."))
    }
}
