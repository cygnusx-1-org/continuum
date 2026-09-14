package ml.docilealligator.infinityforreddit.adapters

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.test.core.app.ApplicationProvider
import ml.docilealligator.infinityforreddit.TestInfinity
import ml.docilealligator.infinityforreddit.customviews.AspectRatioGifImageView
import ml.docilealligator.infinityforreddit.post.InlineBodyImageFixtures
import ml.docilealligator.infinityforreddit.post.Post
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The three decisions a feed card makes about a preview, held against the six real r/test posts in
 * [InlineBodyImageFixtures] rather than against hand-set flags -- the question is what the card does
 * with what Reddit actually sends.
 *
 * The line these draw is between an image out of the post's own body and a preview Reddit built from
 * a link in it. The first is part of what the post says, so it keeps its whole frame and it follows
 * the body's own order; the second is a picture of somewhere else, which survives a centre crop and
 * has no place in the body to speak of.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestInfinity::class)
class PostCardPreviewStyleTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    /** What each fixture's card shows above its image and below it. */
    private val snippetHalves = mapOf(
        "text_image" to ("This is text." to ""),
        "image_text" to ("" to "This is text."),
        "text_image_text" to ("This is text." to "This is text."),
        "text_image_image" to ("This is text." to ""),
        "image_image_text" to ("" to "This is text."),
        "image_text_image" to ("" to "This is text."),
    )

    @Test
    fun `a card with two slots reads in the body's own order`() {
        InlineBodyImageFixtures.NAMES.forEach { name ->
            val post = InlineBodyImageFixtures.post(name)
            val (above, below) = snippetHalves.getValue(name)

            assertEquals(name, above, PostCardPreviewStyle.snippetAbovePreview(post, hasBelowSlot = true))
            assertEquals(name, below, PostCardPreviewStyle.snippetBelowPreview(post, hasBelowSlot = true))
        }
    }

    @Test
    fun `a card with one slot takes the whole snippet`() {
        // Cards 2 and 3 draw the image above the title, so there is no "before the image" for them.
        val post = InlineBodyImageFixtures.post("text_image_text")

        assertEquals(
            "This is text.\n\nThis is text.",
            PostCardPreviewStyle.snippetAbovePreview(post, hasBelowSlot = false),
        )
        assertNull(PostCardPreviewStyle.snippetBelowPreview(post, hasBelowSlot = false))
    }

    @Test
    fun `a preview that is not out of the body leads the card`() {
        // Reddit's own preview of a link is not part of the body, and the post detail draws it
        // above the selftext. The card follows, or the same post reads in two different orders.
        val linkPost = post(Post.LINK_TYPE)
        linkPost.selfTextPlainTrimmed = "Some words."

        assertNull(PostCardPreviewStyle.snippetAbovePreview(linkPost, hasBelowSlot = true))
        assertEquals("Some words.", PostCardPreviewStyle.snippetBelowPreview(linkPost, hasBelowSlot = true))
    }

    @Test
    fun `a body image the card cannot place keeps the snippet above it`() {
        // A body that wrote its image as a caption carries no URL in its plain text, so there is
        // no split to follow (see ParsePost.applyInlineImageSnippetSplit) and no order to read.
        val textPost = post(Post.TEXT_TYPE)
        textPost.isInlineBodyImagePreview = true
        textPost.selfTextPlainTrimmed = "Some words."

        assertFalse(textPost.hasInlineImageSnippetSplit())
        assertEquals("Some words.", PostCardPreviewStyle.snippetAbovePreview(textPost, hasBelowSlot = true))
        assertNull(PostCardPreviewStyle.snippetBelowPreview(textPost, hasBelowSlot = true))
    }

    @Test
    fun `a preview is squared only by the setting, or by having no size of its own`() {
        InlineBodyImageFixtures.NAMES.forEach { name ->
            val preview = InlineBodyImageFixtures.post(name).previews[0]

            assertFalse(name, PostCardPreviewStyle.squarePreview(preview, fixedHeightInCard = false))
            assertTrue(name, PostCardPreviewStyle.squarePreview(preview, fixedHeightInCard = true))
        }

        // What a thumbnail fallback is: a URL with no dimensions behind it.
        val sizeless = Post.Preview("https://b.thumbs.redditmedia.com/t140.jpg", 0, 0, "", "")
        assertTrue(PostCardPreviewStyle.squarePreview(sizeless, fixedHeightInCard = false))
    }

    @Test
    fun `a body image is fitted inside the square, never cropped to it`() {
        InlineBodyImageFixtures.NAMES.forEach { name ->
            val post = InlineBodyImageFixtures.post(name)

            assertTrue(name, PostCardPreviewStyle.letterboxSquare(post, autoplay = false))
            assertTrue(name, PostCardPreviewStyle.letterboxSquare(post, autoplay = true))
        }
    }

    @Test
    fun `a body image sits on the card, without a video's black bars`() {
        InlineBodyImageFixtures.NAMES.forEach { name ->
            val post = InlineBodyImageFixtures.post(name)

            assertFalse(name, PostCardPreviewStyle.letterboxWithVideoBars(post, autoplay = false))
            assertFalse(name, PostCardPreviewStyle.letterboxWithVideoBars(post, autoplay = true))
        }
    }

    @Test
    fun `a reddit-generated preview still crops`() {
        // A text post whose preview came from a link in the body, not from an upload: the flag
        // ParsePost sets for a body image is what separates the two, and it is off here.
        val textPost = post(Post.TEXT_TYPE)
        textPost.previews = arrayListOf(Post.Preview("https://preview.redd.it/og.jpg", 1200, 630, "", ""))

        assertFalse(PostCardPreviewStyle.letterboxSquare(textPost, autoplay = false))
        assertFalse(PostCardPreviewStyle.letterboxSquare(textPost, autoplay = true))
    }

    @Test
    fun `an autoplaying gif keeps the video bars a still gif does not get`() {
        val gif = post(Post.GIF_TYPE)

        assertTrue(PostCardPreviewStyle.letterboxSquare(gif, autoplay = true))
        assertTrue(PostCardPreviewStyle.letterboxWithVideoBars(gif, autoplay = true))
        // Autoplay off makes it a still with a play badge, exactly like a video card is then.
        assertFalse(PostCardPreviewStyle.letterboxSquare(gif, autoplay = false))
        assertFalse(PostCardPreviewStyle.letterboxWithVideoBars(gif, autoplay = false))
    }

    @Test
    fun `with the setting off a card is as tall as the image's own shape`() {
        val imageView = cardImageView()
        val post = InlineBodyImageFixtures.post("text_image")

        shape(imageView, post, fixedHeightInCard = false)

        // 1344x2992 at a 1080px column.
        assertEquals(2404, measuredHeight(imageView))
    }

    @Test
    fun `with the setting on every card is the same square, fitted and capped`() {
        val tall = cardImageView()
        val wide = cardImageView()

        shape(tall, InlineBodyImageFixtures.post("text_image"), fixedHeightInCard = true)
        shape(wide, InlineBodyImageFixtures.post("image_text"), fixedHeightInCard = true)

        assertEquals(MAX_PREVIEW_HEIGHT, measuredHeight(tall))
        assertEquals(MAX_PREVIEW_HEIGHT, measuredHeight(wide))
        assertEquals(ImageView.ScaleType.FIT_CENTER, tall.scaleType)
        assertEquals(ImageView.ScaleType.FIT_CENTER, wide.scaleType)
    }

    @Test
    fun `a reddit-generated preview is cropped to the square it is given`() {
        val imageView = cardImageView()
        val textPost = post(Post.TEXT_TYPE)
        textPost.previews = arrayListOf(Post.Preview("https://preview.redd.it/og.jpg", 1200, 630, "", ""))

        shape(imageView, textPost, fixedHeightInCard = true)

        assertEquals(ImageView.ScaleType.CENTER_CROP, imageView.scaleType)
    }

    @Test
    fun `the square's cap does not follow a recycled view to the next post`() {
        val imageView = cardImageView()
        val post = InlineBodyImageFixtures.post("text_image")

        shape(imageView, post, fixedHeightInCard = true)
        assertEquals(MAX_PREVIEW_HEIGHT, measuredHeight(imageView))

        shape(imageView, post, fixedHeightInCard = false)
        assertEquals(2404, measuredHeight(imageView))
    }

    private fun shape(imageView: AspectRatioGifImageView, post: Post, fixedHeightInCard: Boolean) {
        PostCardPreviewStyle.applyPreviewShape(
            imageView, post.previews[0], post, fixedHeightInCard,
            autoplay = false, maxPreviewHeight = MAX_PREVIEW_HEIGHT,
        )
    }

    /** A card's image slot: the view inside the wrapper the layout puts around it. */
    private fun cardImageView(): AspectRatioGifImageView {
        val imageView = AspectRatioGifImageView(context)
        FrameLayout(context).addView(
            imageView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
        return imageView
    }

    private fun measuredHeight(imageView: AspectRatioGifImageView): Int {
        imageView.measure(
            View.MeasureSpec.makeMeasureSpec(COLUMN_WIDTH, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        return imageView.measuredHeight
    }

    private fun post(postType: Int) = Post(
        "abc123", "t3_abc123", "test", "r/test",
        "someone", "t2_someone", "", "", 0L,
        "A title", "/r/test/comments/abc123/a_title/", 0, postType, 0, 0,
        0, "", false, false,
        false, false, false, false, false, false,
        false, false, false, 0L, null,
        false, false, "", null
    )

    companion object {
        /** One column of a phone feed, and the half-viewport ceiling that goes with it. */
        private const val COLUMN_WIDTH = 1080
        private const val MAX_PREVIEW_HEIGHT = 960
    }
}
