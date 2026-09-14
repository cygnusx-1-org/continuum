package ml.docilealligator.infinityforreddit.post

import ml.docilealligator.infinityforreddit.TestInfinity
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Reddit derives a text post's `preview` from the first link in its body, not from the images
 * uploaded into it, so a post that embeds its own images arrives with `media_metadata` and no
 * `preview` at all. The feed used to draw such a post as plain text with the bare
 * `preview.redd.it/...?s=<signature>` URL sitting in the snippet, while reddit.com showed the image;
 * `ParsePost.applyInlineBodyImagePreview` now gives it the first image the body embeds.
 *
 * Driven by [InlineBodyImageFixtures] -- the real listing payloads of six r/test posts, one per
 * arrangement of text and images in the body.
 *
 * `image_image_text` is the one that pins body order: its `media_metadata` lists the wide jpg first
 * (and the parsed map is a HashMap, so its order is arbitrary anyway) while the body opens with the
 * small png. The png is the image a reader sees first, so it is the one the card must show.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestInfinity::class)
class InlineBodyImagePreviewTest {

    /**
     * What one fixture's card is expected to show: the `media_metadata` entry the body opens with
     * (or leads up to), and which side of the image the selftext snippet belongs on.
     */
    private data class Expected(
        val fixture: String,
        val imageId: String,
        val width: Int,
        val height: Int,
        val downscaledWidth: Int,
        val downscaledHeight: Int,
        val snippet: String,
        val snippetBefore: String,
        val snippetAfter: String,
    )

    private val cases = listOf(
        Expected(
            "text_image", "of804o39afph1", 1344, 2992, 640, 1280,
            snippet = "This is text.", snippetBefore = "This is text.", snippetAfter = "",
        ),
        Expected(
            "image_text", "n9adtvxlafph1", 2948, 2020, 640, 438,
            snippet = "This is text.", snippetBefore = "", snippetAfter = "This is text.",
        ),
        Expected(
            "text_image_text", "6r72e2gvafph1", 2948, 2020, 640, 438,
            snippet = "This is text.\n\nThis is text.",
            snippetBefore = "This is text.", snippetAfter = "This is text.",
        ),
        Expected(
            "text_image_image", "3ttqo837bfph1", 2948, 2020, 640, 438,
            snippet = "This is text.", snippetBefore = "This is text.", snippetAfter = "",
        ),
        Expected(
            "image_image_text", "g7lc0ajdbfph1", 640, 657, 640, 657,
            snippet = "This is text.", snippetBefore = "", snippetAfter = "This is text.",
        ),
        Expected(
            "image_text_image", "odcdtgjmbfph1", 2948, 2020, 640, 438,
            snippet = "This is text.", snippetBefore = "", snippetAfter = "This is text.",
        ),
    )

    @Test
    fun `every arrangement of text and images is a text post that embeds its own media`() {
        forEachCase { expected, _, post ->
            assertEquals(expected.fixture, Post.TEXT_TYPE, post.postType)
            assertTrue(
                "${expected.fixture}: the body renders the image itself",
                post.embedsInlineBodyMedia(),
            )
        }
    }

    @Test
    fun `the card shows the first image the body embeds, at its own size`() {
        forEachCase { expected, json, post ->
            val media = json.getJSONObject("media_metadata").getJSONObject(expected.imageId)
            val sourceUrl = media.getJSONObject("s").getString("u")

            assertEquals(
                "${expected.fixture}: the image the body opens with",
                sourceUrl,
                post.previews[0].previewUrl,
            )
            assertEquals(expected.fixture, expected.width, post.previews[0].previewWidth)
            assertEquals(expected.fixture, expected.height, post.previews[0].previewHeight)
            assertTrue(expected.fixture, post.isInlineBodyImagePreview)
        }
    }

    @Test
    fun `a downscaled rung follows the original, so the feed has a size to choose from`() {
        forEachCase { expected, _, post ->
            assertEquals("${expected.fixture}: original then downscaled", 2, post.previews.size)
            assertEquals(expected.fixture, expected.downscaledWidth, post.previews[1].previewWidth)
            assertEquals(expected.fixture, expected.downscaledHeight, post.previews[1].previewHeight)
        }
    }

    @Test
    fun `the snippet is split around the image, so the card reads in the body's order`() {
        forEachCase { expected, _, post ->
            assertTrue("${expected.fixture}: split", post.hasInlineImageSnippetSplit())
            assertEquals(
                "${expected.fixture}: the words before the image",
                expected.snippetBefore,
                post.selfTextPlainTrimmedBeforeInlineImage,
            )
            assertEquals(
                "${expected.fixture}: the words after it",
                expected.snippetAfter,
                post.selfTextPlainTrimmedAfterInlineImage,
            )
        }
    }

    @Test
    fun `the snippet drops the URLs of the images the body renders`() {
        forEachCase { expected, _, post ->
            assertEquals(expected.fixture, expected.snippet, post.selfTextPlainTrimmed)
            assertFalse(
                "${expected.fixture}: nor do its halves carry one",
                (post.selfTextPlainTrimmedBeforeInlineImage!! +
                    post.selfTextPlainTrimmedAfterInlineImage!!).contains("redd.it"),
            )
            assertFalse(
                "${expected.fixture}: no bare URL left in the snippet",
                post.selfTextPlainTrimmed!!.contains("redd.it"),
            )
            assertTrue(
                "${expected.fixture}: the body itself still has it to render from",
                post.selfTextPlain!!.contains("redd.it"),
            )
        }
    }

    @Test
    fun `body order decides, not the order media_metadata happens to list`() {
        val json = fixture("image_image_text")
        val media = json.getJSONObject("media_metadata")
        val png = media.getJSONObject("g7lc0ajdbfph1").getJSONObject("s").getString("u")
        val jpg = media.getJSONObject("u80t999ebfph1").getJSONObject("s").getString("u")
        val body = json.getString("selftext")

        // media_metadata lists the wide jpg first; the body opens with the small png. Asserted off
        // the body text rather than off the map, whose order nothing guarantees -- which is the
        // whole reason the parser reads the body instead of the map.
        assertTrue("the body opens with the png", body.indexOf(png) < body.indexOf(jpg))

        val post = ParsePost.parseBasicData(json)
        assertEquals("so the png is the card's image", png, post.previews[0].previewUrl)
    }

    private fun forEachCase(assertions: (Expected, JSONObject, Post) -> Unit) {
        cases.forEach { expected ->
            val json = fixture(expected.fixture)
            assertions(expected, json, ParsePost.parseBasicData(json))
        }
    }

    private fun fixture(name: String) = InlineBodyImageFixtures.json(name)
}
