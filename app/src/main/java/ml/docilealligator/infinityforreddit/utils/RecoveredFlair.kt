package ml.docilealligator.infinityforreddit.utils

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ReplacementSpan
import android.util.TypedValue
import androidx.core.content.ContextCompat
import ml.docilealligator.infinityforreddit.R

/**
 * The "Recovered" marker for content that came from the Arctic Shift archive rather than from
 * Reddit.
 *
 * A post shows it as a chip in the flair row it already has. A comment has no chip row, and its
 * byline is a tightly constrained layout where an added view would move its neighbours, so there it
 * is drawn as a span inside the author-flair line instead: nothing is added to the layout, and a
 * recovered comment lands on the same pixels as an ordinary one.
 */
object RecoveredFlair {

    private const val HORIZONTAL_PADDING_DP = 6f
    private const val VERTICAL_PADDING_DP = 2f
    private const val CORNER_RADIUS_DP = 6f

    /**
     * The label on its own, for a comment with no flair of its own.
     */
    @JvmStatic
    fun label(context: Context): CharSequence = prependTo(context, null)

    /**
     * The label followed by [flair], so a recovered comment keeps whatever flair its author had.
     */
    @JvmStatic
    fun prependTo(context: Context, flair: CharSequence?): CharSequence {
        val text = context.getString(R.string.recovered)
        val builder = SpannableStringBuilder(text)
        builder.setSpan(
            ChipSpan(
                ContextCompat.getColor(context, R.color.recoveredChipBackground),
                ContextCompat.getColor(context, R.color.recoveredChipText),
                dp(context, HORIZONTAL_PADDING_DP),
                dp(context, VERTICAL_PADDING_DP),
                dp(context, CORNER_RADIUS_DP)
            ),
            0, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        if (!flair.isNullOrEmpty()) {
            builder.append("  ").append(flair)
        }
        return builder
    }

    private fun dp(context: Context, value: Float): Float = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, value, context.resources.displayMetrics
    )

    /**
     * Draws its text on a rounded, filled rectangle, so the span reads as the same kind of chip a
     * post's flair row shows rather than as a coloured run of text.
     */
    private class ChipSpan(
        private val backgroundColor: Int,
        private val textColor: Int,
        private val horizontalPadding: Float,
        private val verticalPadding: Float,
        private val cornerRadius: Float
    ) : ReplacementSpan() {

        override fun getSize(
            paint: Paint,
            text: CharSequence,
            start: Int,
            end: Int,
            fm: Paint.FontMetricsInt?
        ): Int {
            // The line has to make room for the chip's padding, or the rounded edges are clipped by
            // the rows above and below.
            fm?.let {
                paint.getFontMetricsInt(it)
                it.ascent -= verticalPadding.toInt()
                it.top -= verticalPadding.toInt()
                it.descent += verticalPadding.toInt()
                it.bottom += verticalPadding.toInt()
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
            val rect = RectF(
                x,
                y + metrics.ascent - verticalPadding,
                x + width,
                y + metrics.descent + verticalPadding
            )

            val originalColor = paint.color
            paint.color = backgroundColor
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint)
            paint.color = textColor
            canvas.drawText(text, start, end, x + horizontalPadding, y.toFloat(), paint)
            paint.color = originalColor
        }
    }
}
