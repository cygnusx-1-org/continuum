package ml.docilealligator.infinityforreddit.markdown.imageandgif

import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.test.core.app.ApplicationProvider
import ml.docilealligator.infinityforreddit.TestInfinity
import ml.docilealligator.infinityforreddit.customviews.AspectRatioGifImageView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * How an image in a post body or a comment is sized. The assertions are on the height the block
 * actually measures to at a known width, not on the fields behind it: the height is what a reader
 * sees, and it is what the "Fixed Height in Card" setting is about.
 *
 * The three shapes are the ones the r/test fixtures carry -- a tall portrait screenshot, a wide
 * landscape one, and a roughly square one -- because those are the shapes that come out differently.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestInfinity::class)
class MarkdownMediaSizeTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    /** A block's width on a phone, and a cap well under what a square at that width would be. */
    private val blockWidth = 1080
    private val maxHeight = 600

    @Test
    fun `an image is as tall as its own shape makes it`() {
        assertEquals("tall 1344x2992", 2404, heightOf(1344, 2992, fixedHeight = false))
        assertEquals("wide 2948x2020", 740, heightOf(2948, 2020, fixedHeight = false))
        assertEquals("square-ish 640x657", 1108, heightOf(640, 657, fixedHeight = false))
    }

    @Test
    fun `the ceiling is not applied to an image sized by its own shape`() {
        // Every one of those is taller than maxHeight, and none of them is clamped to it.
        assertTrue(heightOf(1344, 2992, fixedHeight = false) > maxHeight)
        assertTrue(heightOf(2948, 2020, fixedHeight = false) > maxHeight)
    }

    @Test
    fun `fixed height gives every shape the same square, capped at the ceiling`() {
        assertEquals(maxHeight, heightOf(1344, 2992, fixedHeight = true))
        assertEquals(maxHeight, heightOf(2948, 2020, fixedHeight = true))
        assertEquals(maxHeight, heightOf(640, 657, fixedHeight = true))
    }

    @Test
    fun `a square narrower than the ceiling is as tall as the block is wide`() {
        assertEquals(blockWidth, heightOf(2948, 2020, fixedHeight = true, maxHeight = 4000))
    }

    @Test
    fun `dimensions media_metadata did not give are drawn as the same square`() {
        assertEquals(maxHeight, heightOf(0, 0, fixedHeight = false))
        assertEquals(maxHeight, heightOf(-1, 480, fixedHeight = false))
    }

    @Test
    fun `the ceiling of a fixed-height image does not follow the view to the next one`() {
        val imageView = blockImageView()

        MarkdownMediaSize.applyTo(imageView, 2948, 2020, fixedHeight = true, maxHeight = maxHeight)
        assertEquals(maxHeight, measuredHeight(imageView))

        // The same view, rebound to a tall image with the setting off: it must grow past the cap
        // the last image left on it, not stay clamped.
        MarkdownMediaSize.applyTo(imageView, 1344, 2992, fixedHeight = false, maxHeight = maxHeight)
        assertEquals(2404, measuredHeight(imageView))
    }

    @Test
    fun `the image fills the width of its block and is fitted, not cropped`() {
        val imageView = blockImageView()

        MarkdownMediaSize.applyTo(imageView, 2948, 2020, fixedHeight = false, maxHeight = maxHeight)

        val params = imageView.layoutParams as FrameLayout.LayoutParams
        assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, params.width)
        assertEquals(ViewGroup.LayoutParams.WRAP_CONTENT, params.height)
        assertEquals(Gravity.NO_GRAVITY, params.gravity)
        assertEquals(ImageView.ScaleType.FIT_CENTER, imageView.scaleType)
    }

    private fun heightOf(
        srcWidth: Int,
        srcHeight: Int,
        fixedHeight: Boolean,
        maxHeight: Int = this.maxHeight,
    ): Int {
        val imageView = blockImageView()
        MarkdownMediaSize.applyTo(imageView, srcWidth, srcHeight, fixedHeight, maxHeight)
        return measuredHeight(imageView)
    }

    /** The block's image slot: an [AspectRatioGifImageView] filling a [FrameLayout], as in the XML. */
    private fun blockImageView(): AspectRatioGifImageView {
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
            View.MeasureSpec.makeMeasureSpec(blockWidth, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        return imageView.measuredHeight
    }
}
