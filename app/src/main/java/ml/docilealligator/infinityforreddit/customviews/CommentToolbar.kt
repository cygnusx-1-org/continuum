package ml.docilealligator.infinityforreddit.customviews

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import com.google.android.material.button.MaterialButton
import ml.docilealligator.infinityforreddit.R

/**
 * The bottom toolbar of a comment row.
 *
 * The reply button is never dropped, whatever the width. When the row cannot fit, this shrinks in
 * stages: first the options that are also reachable from the overflow sheet (save, then the expand
 * chevron), then the icon metrics of everything that is left. Deciding this from the measure pass
 * rather than from a dp threshold means it stays correct for any combination of window width,
 * indentation depth, font scale and display size, and it re-evaluates when any of those change.
 */
class CommentToolbar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {

    private companion object {
        const val LEVEL_FULL = 0
        const val LEVEL_NO_SAVE = 1
        const val LEVEL_NO_EXPAND = 2
        const val LEVEL_SMALL_ICONS = 3
        const val LEVEL_TINY_ICONS = 4
        const val LEVEL_MAX = LEVEL_TINY_ICONS
    }

    private val density = context.resources.displayMetrics.density

    // What the adapter asked for, as opposed to what currently fits.
    private var saveRequestedVisible = true
    private var expandRequestedVisible = false

    private var appliedLevel = -1

    // Available width the current appliedLevel was searched against. The search only has to run
    // again when this changes or when a rebind invalidates appliedLevel, which is what stops a
    // second measure pass over unchanged content from walking the levels — and flipping the save
    // and expand buttons — all over again.
    private var searchedAvailable = Int.MIN_VALUE

    private var replyButton: View? = null
    private var saveButton: View? = null
    private var expandButton: View? = null
    private var placeholder: View? = null

    // Captured from the inflated layout so a compacted row can be restored when it is recycled into
    // a wider one. Every metric compaction touches has to be recorded here: restoring only some of
    // them leaves recycled rows measuring differently from rows that were never compacted.
    private class ChildMetrics(
        val paddingStart: Int,
        val paddingTop: Int,
        val paddingEnd: Int,
        val paddingBottom: Int,
        val iconSize: Int,
        val minWidth: Int,
        val minimumWidth: Int,
        val insetTop: Int,
        val insetBottom: Int
    )

    private val originals = HashMap<View, ChildMetrics>()
    private var capturedOriginals = false

    override fun onFinishInflate() {
        super.onFinishInflate()
        replyButton = findViewById(R.id.reply_button_item_post_comment)
        saveButton = findViewById(R.id.save_button_item_post_comment)
        expandButton = findViewById(R.id.expand_button_item_post_comment)
        placeholder = findViewById(R.id.placeholder_item_post_comment)
    }

    /**
     * Records which optional items the row wants, before any space-driven trimming. Call this
     * instead of setting visibility on the save and expand views directly, so that a row recycled
     * into a wider window can get them back.
     */
    fun setOptionalVisibility(saveVisible: Boolean, expandVisible: Boolean) {
        val changed =
            saveRequestedVisible != saveVisible || expandRequestedVisible != expandVisible
        saveRequestedVisible = saveVisible
        expandRequestedVisible = expandVisible
        // Always force the next measure to re-apply, even when the intent is unchanged: the adapter
        // resets the expand button to GONE in onViewRecycled, so a row rebound with the same intent
        // still needs its visibility written back.
        appliedLevel = -1
        if (changed) {
            requestLayout()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        captureOriginals()

        val available = MeasureSpec.getSize(widthMeasureSpec) - paddingStart - paddingEnd
        if (appliedLevel >= 0 && available == searchedAvailable) {
            // Same content at the same width: the level already applied is still the answer.
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            return
        }

        var level = LEVEL_FULL
        while (true) {
            applyLevel(level)
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            if (level >= LEVEL_MAX || requiredWidth() <= available) {
                break
            }
            level++
        }
        searchedAvailable = available
    }

    /** Width the row needs: every visible child except the spacer, which may collapse to nothing. */
    private fun requiredWidth(): Int {
        var total = 0
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.visibility == View.GONE || child === placeholder) {
                continue
            }
            val lp = child.layoutParams as? ViewGroup.MarginLayoutParams
            total += child.measuredWidth + (lp?.marginStart ?: 0) + (lp?.marginEnd ?: 0)
        }
        return total
    }

    private fun captureOriginals() {
        if (capturedOriginals) {
            return
        }
        capturedOriginals = true
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            originals[child] = ChildMetrics(
                child.paddingStart, child.paddingTop, child.paddingEnd, child.paddingBottom,
                (child as? MaterialButton)?.iconSize ?: 0,
                (child as? MaterialButton)?.minWidth ?: 0,
                child.minimumWidth,
                (child as? MaterialButton)?.insetTop ?: 0,
                (child as? MaterialButton)?.insetBottom ?: 0
            )
        }
    }

    private fun applyLevel(level: Int) {
        if (level == appliedLevel) {
            return
        }
        appliedLevel = level

        saveButton?.visibility =
            if (saveRequestedVisible && level < LEVEL_NO_SAVE) View.VISIBLE else View.GONE
        expandButton?.visibility =
            if (expandRequestedVisible && level < LEVEL_NO_EXPAND) View.VISIBLE else View.GONE

        val compact = level >= LEVEL_SMALL_ICONS
        val iconSize = when {
            level >= LEVEL_TINY_ICONS -> (16 * density).toInt()
            level >= LEVEL_SMALL_ICONS -> (20 * density).toInt()
            else -> 0 // restore whatever the layout declared
        }
        val horizontalPadding = when {
            level >= LEVEL_TINY_ICONS -> 0
            level >= LEVEL_SMALL_ICONS -> (2 * density).toInt()
            else -> -1 // restore whatever the layout declared
        }

        for (i in 0 until childCount) {
            val child = getChildAt(i)
            val original = originals[child] ?: continue
            if (child is MaterialButton) {
                child.iconSize = if (compact) iconSize else original.iconSize
                child.minWidth = if (compact) 0 else original.minWidth
                child.minimumWidth = if (compact) 0 else original.minimumWidth
                child.insetTop = if (compact) 0 else original.insetTop
                child.insetBottom = if (compact) 0 else original.insetBottom
            }
            if (child is MaterialButton || child is TextView) {
                if (horizontalPadding < 0) {
                    child.setPaddingRelative(
                        original.paddingStart, original.paddingTop,
                        original.paddingEnd, original.paddingBottom
                    )
                } else {
                    child.setPaddingRelative(
                        horizontalPadding, original.paddingTop,
                        horizontalPadding, original.paddingBottom
                    )
                }
            }
        }
    }
}
