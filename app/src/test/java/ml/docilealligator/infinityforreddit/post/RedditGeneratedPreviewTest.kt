package ml.docilealligator.infinityforreddit.post

import ml.docilealligator.infinityforreddit.TestInfinity
import ml.docilealligator.infinityforreddit.adapters.PostCardPreviewStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A text post whose image came out of a link in its body rather than out of the body itself.
 *
 * Reddit builds a self post's `preview` from the first link it finds, so any text post that
 * mentions a video or an article arrives with an `external-preview.redd.it` image that the body
 * does not render. The post detail draws that image above the selftext -- `getItemViewType` gives
 * such a post the link holder, whose layout is title, image, `content_markdown_view` -- so the feed
 * card has to as well, or the same post reads in one order in the feed and the other order a tap
 * later. It read the other way for three r/copypasta posts, which are the fixtures here.
 *
 * The six fixtures in [InlineBodyImagePreviewTest] are all the opposite case (an image the body
 * embeds, which keeps its place in the body), so nothing there could catch this.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestInfinity::class)
class RedditGeneratedPreviewTest {

    /** What each fixture's body opens with, once the markdown is gone. */
    private val bodyOpeners = mapOf(
        "text_link_end" to "Ggmonnnn. Fucking COME ON MAN! Breif crying CMON.",
        "text_link_middle" to "The girl in this comic is struggling to work through and express",
        "text_link_labelled" to "WIR SCHNEIDEN HEUTE EINEN ECHTEN LABUBU AUF",
    )

    @Test
    fun `the image is Reddit's own preview, not one made out of the body`() {
        forEachFixture { name, post ->
            assertEquals(name, Post.TEXT_TYPE, post.postType)
            assertFalse("$name: the body embeds nothing itself", post.embedsInlineBodyMedia())
            assertFalse("$name: so the preview is not a body image", post.isInlineBodyImagePreview)

            val source = RedditPreviewFixtures.json(name)
                .getJSONObject("preview").getJSONArray("images").getJSONObject(0)
                .getJSONObject("source")
            assertEquals(name, source.getString("url"), post.previews[0].previewUrl)
            assertEquals(name, source.getInt("width"), post.previews[0].previewWidth)
            assertEquals(name, source.getInt("height"), post.previews[0].previewHeight)
        }
    }

    @Test
    fun `Reddit says the preview is disabled on a self post, and the card shows it anyway`() {
        forEachFixture { name, post ->
            assertFalse(
                "$name: what Reddit sends for an external preview on a self post",
                RedditPreviewFixtures.json(name).getJSONObject("preview").getBoolean("enabled"),
            )
            assertFalse("$name: the card still has an image to draw", post.previews.isEmpty())
        }
    }

    @Test
    fun `the snippet is the body's opening, whole and unsplit`() {
        forEachFixture { name, post ->
            assertFalse("$name: nothing to split around", post.hasInlineImageSnippetSplit())
            assertNull(name, post.selfTextPlainTrimmedBeforeInlineImage)
            assertNull(name, post.selfTextPlainTrimmedAfterInlineImage)

            val snippet = post.selfTextPlainTrimmed!!
            assertTrue("$name: $snippet", snippet.startsWith(bodyOpeners.getValue(name)))
            // Every one of these bodies runs past what a card has room for.
            assertEquals("$name: cut to the card's length", 250, snippet.length)
        }
    }

    @Test
    fun `the card puts the image first, the way the post detail does`() {
        forEachFixture { name, post ->
            assertNull(
                "$name: the image leads, so nothing goes above it",
                PostCardPreviewStyle.snippetAbovePreview(post, hasBelowSlot = true),
            )
            assertEquals(
                "$name: and the body follows it",
                post.selfTextPlainTrimmed,
                PostCardPreviewStyle.snippetBelowPreview(post, hasBelowSlot = true),
            )
        }
    }

    @Test
    fun `a one-slot card still shows the whole snippet`() {
        // Cards 2 and 3 draw the image above the title, so their single slot already follows it.
        forEachFixture { name, post ->
            assertEquals(
                name,
                post.selfTextPlainTrimmed,
                PostCardPreviewStyle.snippetAbovePreview(post, hasBelowSlot = false),
            )
            assertNull(name, PostCardPreviewStyle.snippetBelowPreview(post, hasBelowSlot = false))
        }
    }

    @Test
    fun `someone else's preview is still cropped to the square`() {
        // The letterbox is for an image the post itself uploaded, whose edges carry its meaning.
        forEachFixture { name, post ->
            assertTrue(name, PostCardPreviewStyle.squarePreview(post.previews[0], fixedHeightInCard = true))
            assertFalse(name, PostCardPreviewStyle.letterboxSquare(post, autoplay = false))
            assertFalse(name, PostCardPreviewStyle.letterboxWithVideoBars(post, autoplay = false))
        }
    }

    private fun forEachFixture(assertions: (String, Post) -> Unit) {
        RedditPreviewFixtures.NAMES.forEach { name ->
            assertions(name, RedditPreviewFixtures.post(name))
        }
    }
}
