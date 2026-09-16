package ml.docilealligator.infinityforreddit.markdown.spoiler

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.text.Spannable
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ReplacementSpan
import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.view.View
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * What a hidden spoiler actually renders as, read back off the bitmap.
 *
 * [SpoilerSpan] hides text by painting the glyphs in the block's own colour, which is why issue #416
 * exists: a colour emoji is a bitmap glyph and a [ReplacementSpan] — a Reddit emote, an inline image
 * — does its own drawing, so neither takes the paint's colour and both stay legible through a
 * spoiler nobody has tapped. [LeakingSpan] below is that failure in its smallest form: something
 * inside the spoiler that draws in a colour of its own no matter what the `TextPaint` says.
 *
 * Asserting on pixels rather than on the mask's geometry is deliberate. The fix hangs on a view's
 * overlay being drawn after its text, and only a real draw can say whether it is.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SpoilerMaskDrawableTest {

    private val context: Application = ApplicationProvider.getApplicationContext()

    @Test
    fun `a hidden spoiler covers content that draws in its own colours`() {
        val rendered = render(spoileredLeak())

        assertEquals(
            "the leaking span shows through an untapped spoiler",
            0,
            rendered.bitmap.count(LEAK),
        )
        assertTrue("the spoiler block was not drawn at all", rendered.bitmap.count(BLOCK) > 0)
    }

    @Test
    fun `revealing a spoiler stops covering it`() {
        val rendered = render(spoileredLeak())

        rendered.reveal()

        assertTrue(
            "the leaking span stayed hidden after the spoiler was tapped",
            rendered.redraw().count(LEAK) > 0,
        )
    }

    @Test
    fun `the mask leaves the text around the spoiler alone`() {
        val rendered = render(spoileredLeak())

        // "before" and "after" sit outside the spoiler, so something has to be neither the page nor
        // the block. Counting those rather than exact blue keeps the assertion off the mercy of
        // whichever pixels the glyph antialiasing happens to land on.
        assertTrue(
            "the mask swallowed the surrounding text",
            rendered.bitmap.count { it != PAGE && it != BLOCK } > 0,
        )
    }

    /**
     * A spoiler broken over two lines is covered on both, and no further: the row it starts on is
     * masked only as far as its own text reaches.
     *
     * [android.text.Layout.getSelectionPath] pads every row but the last out to the view's full
     * width when it is handed a range spanning lines — that is the selection highlight's shape, not
     * the block's, and it would paint over half the view. [SpoilerMaskDrawable] walks the lines
     * itself to avoid it, and this is what would catch that walk being dropped.
     */
    @Test
    fun `a wrapped spoiler is masked line by line, not out to the view's edge`() {
        // "lead " leaves the first line well short of WIDTH, so a row padded out to the view's edge
        // is unmistakable. A leak on each side of the break is what makes "covered" checkable: the
        // block alone proves nothing here, since a hidden spoiler's own glyphs are painted in it.
        val text = SpannableString("lead A\nB trail")
        val spoilerStart = text.indexOf('A')
        val spoilerEnd = text.indexOf('B') + 1
        text.setSpan(LeakingSpan(), spoilerStart, spoilerStart + 1, SPAN_FLAGS)
        text.setSpan(LeakingSpan(), spoilerEnd - 1, spoilerEnd, SPAN_FLAGS)
        text.setSpan(SpoilerSpan(BLOCK, BLOCK), spoilerStart, spoilerEnd, SPAN_FLAGS)

        val rendered = render(text)
        val layout = rendered.textView.layout
        val firstLineMiddle = (layout.getLineTop(0) + layout.getLineBottom(0)) / 2 +
            rendered.textView.totalPaddingTop

        assertEquals(
            "a leak shows through on one of the spoiler's two lines",
            0,
            rendered.bitmap.count(LEAK),
        )
        assertNotEquals(
            "the mask ran past the end of the line's text",
            BLOCK,
            rendered.bitmap.getPixel(rendered.bitmap.width - 1, firstLineMiddle),
        )
    }

    /** `"before ? after"`, with the `?` a [LeakingSpan] and the middle word a hidden spoiler. */
    private fun spoileredLeak(): Spannable {
        val text = SpannableString("before ? after")
        val leak = text.indexOf('?')
        text.setSpan(LeakingSpan(), leak, leak + 1, SPAN_FLAGS)
        // The spoiler takes the spaces either side too, the way `>! ? !<` would.
        text.setSpan(SpoilerSpan(BLOCK, BLOCK), leak - 1, leak + 2, SPAN_FLAGS)
        return text
    }

    private fun render(text: Spannable): Rendered {
        // A platform theme rather than the app's: nothing here turns on how Continuum styles a
        // TextView, and a themed context keeps TextView's own attribute lookups off the app theme.
        val textView = TextView(ContextThemeWrapper(context, android.R.style.Theme_Material_Light))
        textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, TEXT_SIZE_PX)
        textView.setTextColor(TEXT)
        textView.setText(text, TextView.BufferType.SPANNABLE)
        SpoilerMaskDrawable.attachTo(textView)
        textView.measure(
            View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        textView.layout(0, 0, textView.measuredWidth, textView.measuredHeight)
        return Rendered(textView)
    }

    private class Rendered(val textView: TextView) {
        var bitmap: Bitmap = draw()
            private set

        fun redraw(): Bitmap = draw().also { bitmap = it }

        /** Taps every spoiler in the text, as [SpoilerAwareMovementMethod] would. */
        fun reveal() {
            val text = textView.text as Spanned
            text.getSpans(0, text.length, SpoilerSpan::class.java).forEach { it.onClick(textView) }
        }

        private fun draw(): Bitmap {
            val bitmap = Bitmap.createBitmap(textView.width, textView.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(PAGE)
            textView.draw(canvas)
            return bitmap
        }
    }

    /**
     * Stands in for a colour emoji or a Reddit emote: it fills its own box in [LEAK] and never looks
     * at the paint, so colouring the text to match the block does nothing to it.
     */
    private class LeakingSpan : ReplacementSpan() {
        private val ownPaint = Paint().apply { color = LEAK }

        override fun getSize(
            paint: Paint,
            text: CharSequence?,
            start: Int,
            end: Int,
            fm: Paint.FontMetricsInt?,
        ): Int {
            fm?.let { paint.getFontMetricsInt(it) }
            return LEAK_WIDTH
        }

        override fun draw(
            canvas: Canvas,
            text: CharSequence?,
            start: Int,
            end: Int,
            x: Float,
            top: Int,
            y: Int,
            bottom: Int,
            paint: Paint,
        ) {
            canvas.drawRect(x, top.toFloat(), x + LEAK_WIDTH, bottom.toFloat(), ownPaint)
        }
    }

    private fun Bitmap.count(color: Int): Int = count { it == color }

    private fun Bitmap.count(matches: (Int) -> Boolean): Int {
        var found = 0
        for (y in 0 until height) {
            for (x in 0 until width) {
                if (matches(getPixel(x, y))) {
                    found++
                }
            }
        }
        return found
    }

    private companion object {
        val SPAN_FLAGS = Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        val WIDTH = 400
        val TEXT_SIZE_PX = 24f
        val LEAK_WIDTH = 30

        val PAGE = Color.WHITE
        val TEXT = Color.BLUE
        val BLOCK = Color.BLACK
        val LEAK = Color.RED
    }
}
