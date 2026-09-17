package ml.docilealligator.infinityforreddit.utils

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.text.style.ReplacementSpan
import kotlin.math.min

/**
 * Draws a square icon inline, in place of the one character it spans.
 *
 * `ImageSpan` would be the obvious thing, but it makes the line tall enough for the drawable, and
 * a 14dp glyph beside 14sp text is taller than the text's own box — so an author with a marker
 * would sit a pixel or two lower than one without, in the same list. This leaves the line's
 * metrics exactly as the text left them and centres the icon inside them instead, which is also
 * what [ChipSpan] does with `growLine = false` and why the two agree on a row's height.
 *
 * Because the line is never grown, [maxSize] is a ceiling rather than the size: the icon is drawn
 * at whatever the text's own ascent-to-descent box allows, so it still fits at the app's smaller
 * font sizes — `font_default` is 10sp, 12sp or 14sp by setting — instead of being clipped by the
 * view's bounds.
 *
 * The caller passes a drawable it owns: the bounds set here belong to this span.
 */
class IconSpan(
    private val drawable: Drawable,
    private val maxSize: Int,
    private val gap: Int,
) : ReplacementSpan() {

    override fun getSize(
        paint: Paint,
        text: CharSequence,
        start: Int,
        end: Int,
        fm: Paint.FontMetricsInt?
    ): Int {
        // Written from the paint, never left alone: a span that does not fill these inherits
        // whatever the previous run put in the object, which is only right by accident.
        fm?.let { paint.getFontMetricsInt(it) }
        // Whole pixels, so the width measured here is exactly the width drawn: a fractional gap
        // truncated into this total leaves the glyph's right edge past what the run was given.
        return gap + sizeFor(paint)
    }

    override fun draw(
        canvas: Canvas,
        text: CharSequence,
        start: Int,
        end: Int,
        x: Float,
        top: Int,
        y: Int,
        bottom: Int,
        paint: Paint
    ) {
        // Centred on the text's own vertical middle rather than sat on the baseline, so the glyph
        // lines up with the capitals the way a compound drawable does.
        val size = sizeFor(paint)
        val centerY = y + (paint.ascent() + paint.descent()) / 2f
        drawable.setBounds(0, 0, size, size)
        canvas.save()
        canvas.translate(x + gap, centerY - size / 2f)
        drawable.draw(canvas)
        canvas.restore()
    }

    /**
     * The side of the square, in pixels: [maxSize] unless the text is small enough that the icon
     * would not fit between its ascent and descent. Both callers take it from the same paint, so
     * the width measured is the width drawn.
     */
    private fun sizeFor(paint: Paint): Int =
        min(maxSize, (paint.descent() - paint.ascent()).toInt())
}
