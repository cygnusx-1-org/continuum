package ml.docilealligator.infinityforreddit.customviews

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import kotlin.math.min

/**
 * A frame that sizes its single child to a square, bounded by [maxHeight].
 *
 * This exists for the video autoplay previews, which sit in an
 * `androidx.media3.ui.AspectRatioFrameLayout`. That class is `final`, so its height cannot be
 * capped from a subclass the way [AspectRatioGifImageView] caps an image preview. Wrapping it puts
 * the decision in a parent that knows its own width at measure time.
 *
 * When [squarePreview] is on, the child is measured to exactly `min(width, maxHeight)` and the
 * inner view is expected to have had its aspect ratio cleared (`setAspectRatio(0)`), which makes it
 * honour that measurement instead of imposing a shape of its own. When it is off, this is an
 * ordinary [FrameLayout] and the inner view sizes itself from the video's real aspect ratio exactly
 * as it always has.
 */
class MaxHeightSquareFrameLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    /** Height ceiling in pixels, or `0` to let the square run to the full width. */
    var maxHeight: Int = 0
        set(value) {
            if (field != value) {
                field = value
                requestLayout()
            }
        }

    /** Whether the child is a square "Fixed Height in Card" preview rather than a real video shape. */
    var squarePreview: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                requestLayout()
            }
        }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // getChildAt returns null for an out-of-range index, so this covers the empty case.
        val child = getChildAt(0)
        val width = MeasureSpec.getSize(widthMeasureSpec)
        if (!squarePreview || child == null || width <= 0) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            return
        }

        val cap = maxHeight
        val height = if (cap > 0) min(width, cap) else width
        child.measure(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY),
        )
        setMeasuredDimension(width, height)
    }
}
