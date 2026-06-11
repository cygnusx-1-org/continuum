package ml.docilealligator.infinityforreddit.customviews

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import kotlin.math.max

/**
 * A small, dependency-free vertical bar chart that draws one labeled bar per data point with its
 * numeric value above the bar and the window label below it. Bars are scaled to the largest value
 * in the set, so a tall left-most bar (e.g. the last 15 minutes) against shorter ones immediately
 * shows that things are slower right now than the longer-term baseline.
 */
class BarChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    data class Bar(
        val label: String,
        val value: Double,
        val valueText: String,
        val available: Boolean,
        val color: Int
    )

    private var bars: List<Bar> = emptyList()
    private var scaleMax: Double = 0.0

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val valueTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = sp(11f)
    }
    private val labelTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = sp(11f)
    }

    private val barRect = RectF()
    private val cornerRadius = dp(3f)

    /**
     * @param scaleMax the value mapped to full bar height. Pass a value shared across all charts so
     * bar heights are directly comparable between rows; pass 0 to scale each chart to its own max.
     */
    fun setBars(bars: List<Bar>, scaleMax: Double, valueTextColor: Int, labelTextColor: Int) {
        this.bars = bars
        this.scaleMax = scaleMax
        valueTextPaint.color = valueTextColor
        labelTextPaint.color = labelTextColor
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val n = bars.size
        if (n == 0) {
            return
        }

        val maxValue = if (scaleMax > 0.0) {
            scaleMax
        } else {
            bars.filter { it.available }.maxOfOrNull { it.value }?.takeIf { it > 0.0 } ?: 0.0
        }

        val valueTextHeight = valueTextPaint.fontMetrics.let { it.descent - it.ascent }
        val labelTextHeight = labelTextPaint.fontMetrics.let { it.descent - it.ascent }
        val topPadding = valueTextHeight + dp(2f)
        val bottomPadding = labelTextHeight + dp(2f)

        val chartTop = paddingTop + topPadding
        val chartBottom = height - paddingBottom - bottomPadding
        val chartHeight = max(0f, chartBottom - chartTop)

        val usableWidth = (width - paddingLeft - paddingRight).toFloat()
        val slot = usableWidth / n
        val barWidth = slot * 0.5f

        for (i in bars.indices) {
            val bar = bars[i]
            val centerX = paddingLeft + slot * i + slot / 2f

            if (bar.available && maxValue > 0.0) {
                val fraction = (bar.value / maxValue).toFloat().coerceIn(0f, 1f)
                val barHeight = max(dp(2f), fraction * chartHeight)
                val top = chartBottom - barHeight
                barRect.set(centerX - barWidth / 2f, top, centerX + barWidth / 2f, chartBottom)
                barPaint.color = bar.color
                canvas.drawRoundRect(barRect, cornerRadius, cornerRadius, barPaint)
                canvas.drawText(
                    bar.valueText,
                    centerX,
                    top - valueTextPaint.fontMetrics.descent - dp(1f),
                    valueTextPaint
                )
            } else {
                canvas.drawText(
                    EMPTY_VALUE,
                    centerX,
                    chartBottom - dp(1f),
                    valueTextPaint
                )
            }

            canvas.drawText(
                bar.label,
                centerX,
                chartBottom + labelTextHeight,
                labelTextPaint
            )
        }
    }

    private fun dp(value: Float): Float = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics
    )

    private fun sp(value: Float): Float = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_SP, value, resources.displayMetrics
    )

    companion object {
        private const val EMPTY_VALUE = "—"
    }

    init {
        // Avoid an entirely invisible view in edit mode previews.
        if (isInEditMode) {
            barPaint.color = Color.GRAY
        }
    }
}
