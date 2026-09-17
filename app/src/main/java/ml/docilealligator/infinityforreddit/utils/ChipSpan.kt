package ml.docilealligator.infinityforreddit.utils

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.text.style.ReplacementSpan
import android.util.TypedValue

/**
 * Draws its text on a rounded, filled rectangle, so the span reads as the same kind of chip a
 * post's flair row shows rather than as a coloured run of text.
 *
 * Shared by the "Recovered" marker and the user tag (issue #413), which sit in the same flair
 * line and so have to be the same shape: same padding, same corner, same growth of the line.
 *
 * [growLine] decides what happens to the line the chip sits on. In a flair line it is true: the
 * line makes room for the chip's vertical padding, or the rounded edges are clipped by the rows
 * above and below. In a single-line author view — a feed row, an inbox row, a collapsed comment,
 * the profile header — it is false: the line's metrics are left exactly as they were, and the chip
 * is drawn within the text's own ascent and descent, so a tagged author's row lands on the same
 * pixels as an untagged one.
 */
class ChipSpan(
    private val backgroundColor: Int,
    private val textColor: Int,
    private val horizontalPadding: Float,
    private val verticalPadding: Float,
    private val cornerRadius: Float,
    private val growLine: Boolean,
) : ReplacementSpan() {

    companion object {
        private const val HORIZONTAL_PADDING_DP = 6f
        private const val VERTICAL_PADDING_DP = 2f
        private const val CORNER_RADIUS_DP = 6f

        /** A chip in the one size every chip in the app is drawn at. */
        @JvmStatic
        fun standard(
            context: Context,
            backgroundColor: Int,
            textColor: Int,
            growLine: Boolean,
        ): ChipSpan = ChipSpan(
            backgroundColor,
            textColor,
            dp(context, HORIZONTAL_PADDING_DP),
            dp(context, VERTICAL_PADDING_DP),
            dp(context, CORNER_RADIUS_DP),
            growLine,
        )

        private fun dp(context: Context, value: Float): Float = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, value, context.resources.displayMetrics
        )
    }

    override fun getSize(
        paint: Paint,
        text: CharSequence,
        start: Int,
        end: Int,
        fm: Paint.FontMetricsInt?
    ): Int {
        // The metrics are always written, and are the paint's own: a span that leaves them alone
        // inherits whatever the previous run left in the object, which is only right by accident.
        fm?.let {
            paint.getFontMetricsInt(it)
            if (growLine) {
                it.ascent -= verticalPadding.toInt()
                it.top -= verticalPadding.toInt()
                it.descent += verticalPadding.toInt()
                it.bottom += verticalPadding.toInt()
            }
        }
        return (paint.measureText(text, start, end) + horizontalPadding * 2).toInt()
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
        val width = paint.measureText(text, start, end) + horizontalPadding * 2
        val metrics = paint.fontMetrics
        // Without the line's growth there is no room outside the glyph box, so the chip is the
        // glyph box; with it, the padding the line was asked for is drawn.
        val padding = if (growLine) verticalPadding else 0f
        val rect = RectF(
            x,
            y + metrics.ascent - padding,
            x + width,
            y + metrics.descent + padding
        )

        val originalColor = paint.color
        paint.color = backgroundColor
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint)
        paint.color = textColor
        canvas.drawText(text, start, end, x + horizontalPadding, y.toFloat(), paint)
        paint.color = originalColor
    }
}
