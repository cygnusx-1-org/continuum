package ml.docilealligator.infinityforreddit.customviews

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import ml.docilealligator.infinityforreddit.R

/**
 * Header row of a fully collapsed comment.
 *
 * The username takes up the slack here (weight 1, ellipsized), so a narrow or deeply indented row
 * squeezes it rather than overflowing. This drops the trailing metadata in priority order — child
 * count, then score, then time — but only once the username has actually been squeezed below a
 * readable width. Deciding that from the measure pass rather than from a dp threshold on the item
 * width keeps it correct for any font scale, display size and indentation depth.
 */
class CollapsedCommentHeader @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private companion object {
        const val LEVEL_FULL = 0
        const val LEVEL_NO_CHILD_COUNT = 1
        const val LEVEL_NO_SCORE = 2
        const val LEVEL_NO_TIME = 3
        const val LEVEL_MAX = LEVEL_NO_TIME
        const val MIN_USER_NAME_DP = 96
    }

    private val minUserNameWidth =
        (MIN_USER_NAME_DP * context.resources.displayMetrics.density).toInt()

    // What the adapter asked for, as opposed to what currently fits.
    private var childCountRequestedVisible = true

    private var appliedLevel = -1

    private var userName: View? = null
    private var childCount: View? = null
    private var score: View? = null
    private var time: View? = null

    override fun onFinishInflate() {
        super.onFinishInflate()
        userName = findViewById(R.id.user_name_text_view_item_comment_fully_collapsed)
        childCount = findViewById(R.id.child_count_text_view_item_comment_fully_collapsed)
        score = findViewById(R.id.score_text_view_item_comment_fully_collapsed)
        time = findViewById(R.id.time_text_view_item_comment_fully_collapsed)
    }

    /** Records whether this comment has a child count to show, before any space-driven trimming. */
    fun setOptionalVisibility(childCountVisible: Boolean) {
        val changed = childCountRequestedVisible != childCountVisible
        childCountRequestedVisible = childCountVisible
        // Force the next measure to re-apply even when unchanged, so a recycled row cannot inherit
        // the trimming decided for the row it was last bound to.
        appliedLevel = -1
        if (changed) {
            requestLayout()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        var level = LEVEL_FULL
        while (true) {
            applyLevel(level)
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            if (level >= LEVEL_MAX || (userName?.measuredWidth ?: 0) >= minUserNameWidth) {
                break
            }
            level++
        }
    }

    private fun applyLevel(level: Int) {
        if (level == appliedLevel) {
            return
        }
        appliedLevel = level

        childCount?.visibility =
            if (childCountRequestedVisible && level < LEVEL_NO_CHILD_COUNT) View.VISIBLE else View.GONE
        score?.visibility = if (level < LEVEL_NO_SCORE) View.VISIBLE else View.GONE
        time?.visibility = if (level < LEVEL_NO_TIME) View.VISIBLE else View.GONE
    }
}
