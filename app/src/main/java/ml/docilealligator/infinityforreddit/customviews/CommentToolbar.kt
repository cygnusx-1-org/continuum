package ml.docilealligator.infinityforreddit.customviews

import android.content.Context
import android.text.Layout
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import androidx.constraintlayout.widget.ConstraintLayout
import com.google.android.material.button.MaterialButton
import ml.docilealligator.infinityforreddit.R
import kotlin.math.ceil

/**
 * The bottom toolbar of a comment row.
 *
 * The reply button is never dropped, whatever the width. When the row cannot fit, this shrinks in
 * stages: first the options that are also reachable from the overflow sheet (save, then the expand
 * chevron); then the icon metrics of everything that is left. Deciding this from the measure pass
 * rather than from a dp threshold means it stays correct for any combination of window width,
 * indentation depth, font scale and display size, and it re-evaluates when any of those change.
 *
 * The vote group - upvote, score, downvote - is not decided from this row's own content (issue
 * #494). The score column is either wide or narrow, and which one is chosen from the width the row
 * has and nothing else: a reference row carrying every optional item a comment can have, the
 * chevron and the child-count badge included, gets the wide column only if it fits with it. Every
 * row at the same width and depth then gets the same column whether it has replies or not. Deciding
 * it per row put a leaf comment's arrows 45dp further apart than those of the comment with replies
 * beside it on a 360dp phone, because only the second one had a chevron and a badge to fit.
 *
 * Both columns are fixed widths rather than the score's own width, so "4" and "11409" leave the
 * arrows in the same place too. The wide one is the layout's android:minWidth (upstream Infinity's
 * 64dp); the narrow one is the widest string the score ordinarily shows, measured in the score's own
 * paint so it follows the font, font scale and display size. A score wider than its column still
 * widens it - nothing is clipped - so only a six-digit score, or a translation of "Hidden" too long
 * for even the wide column, moves the arrows.
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
        // Only reached by a row whose own content is wider than the reference row allowed for - a
        // badge wider than BADGE_RESERVE_TEXT, say. Narrowing that one row's score is still cheaper
        // than shrinking every icon on it. (A score wider than the wide column gains nothing here:
        // its text already sets the column's width, whatever the minimum.)
        const val LEVEL_NARROW_SCORE = 3
        const val LEVEL_SMALL_ICONS = 4
        const val LEVEL_TINY_ICONS = 5
        const val LEVEL_MAX = LEVEL_TINY_ICONS

        // The child-count badge is reserved at this text's width in the reference row: a comment
        // with up to 99 replies under it. A wider badge is left to the row's own stages, which drop
        // the save button before they touch the vote group.
        const val BADGE_RESERVE_TEXT = "+88"

        // What the score shows short of a six-digit count: absolute scores up to 99999 and down to
        // -9999, and the abbreviated form. Digits are tabular in every bundled font, so eights
        // stand for any digits. The translated "Hidden" is added in onFinishInflate. "Vote" is not:
        // it replaces every score at once when vote counts are hidden, so no row ever has to line
        // up with a number, and a long translation of it would only widen the narrow column.
        val SCORE_TEMPLATES = listOf("88888", "-8888", "88.8K")
    }

    private val density = context.resources.displayMetrics.density

    // What the adapter asked for, as opposed to what currently fits.
    private var saveRequestedVisible = true
    private var expandRequestedVisible = false

    private var appliedLevel = -1

    // Whether the reference row needs the narrow score column at the current width. A function of
    // the width alone, never of this row's content, which is what keeps neighbouring rows' vote
    // groups on the same pixels.
    private var narrowScore = false
    private var narrowScoreWidth = 0

    // Available width the current appliedLevel was searched against. The search only has to run
    // again when this changes or when a rebind invalidates appliedLevel, which is what stops a
    // second measure pass over unchanged content from walking the levels — and flipping the save
    // and expand buttons — all over again.
    private var searchedAvailable = Int.MIN_VALUE

    private var replyButton: View? = null
    private var saveButton: View? = null
    private var expandButton: View? = null
    private var placeholder: View? = null
    private var scoreTextView: TextView? = null
    private var childCountBadge: TextView? = null

    private var scoreTemplates: List<String> = SCORE_TEMPLATES

    // The chevron the adapter puts on the expand button. A row without replies never gets one, so
    // the reference row cannot read it off the view.
    private var chevronWidth = 0

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
        scoreTextView = findViewById(R.id.score_text_view_item_post_comment)
        childCountBadge = findViewById(R.id.childCountTextViewItemPostComment)
        scoreTemplates = SCORE_TEMPLATES + context.getString(R.string.hidden)
        chevronWidth = AppCompatResources.getDrawable(context, R.drawable.ic_expand_more_grey_24dp)
            ?.intrinsicWidth ?: 0
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

        // The narrow column follows the score's paint, which the adapter may have changed since the
        // last search, so re-measure it and force the level below to be written back.
        narrowScoreWidth = measureNarrowScoreWidth()
        appliedLevel = -1

        var level = LEVEL_FULL
        applyLevel(level)
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)

        // Every child the reference row reads now has its full-metrics width; the score, the only
        // one the column decides, is not among them. The answer only changes with the width, so a
        // rebind at the same width keeps it and skips the second pass.
        val narrow = referenceWidth() > available
        if (narrow != narrowScore) {
            narrowScore = narrow
            appliedLevel = -1
            applyLevel(level)
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }

        while (level < LEVEL_MAX && requiredWidth() > available) {
            level++
            applyLevel(level)
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
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
            total += child.measuredWidth + horizontalMargins(child)
        }
        return total
    }

    /**
     * Width a row would need at full metrics with the wide score column if it had every optional
     * item: this row's own buttons as measured, plus the chevron and the badge at fixed reserved
     * widths whether this row shows them or not. Nothing in it depends on this comment's content.
     */
    private fun referenceWidth(): Int {
        var total = 0
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.visibility == View.GONE || child === placeholder || child === scoreTextView ||
                child === expandButton || child === childCountBadge
            ) {
                continue
            }
            total += child.measuredWidth + horizontalMargins(child)
        }
        scoreTextView?.let { score ->
            total += (originals[score]?.minWidth ?: 0) + horizontalMargins(score)
        }
        expandButton?.let { expand ->
            val original = originals[expand]
            total += (original?.paddingStart ?: 0) + (original?.paddingEnd ?: 0) + chevronWidth +
                horizontalMargins(expand)
        }
        childCountBadge?.let { badge ->
            val original = originals[badge]
            total += (original?.paddingStart ?: 0) + (original?.paddingEnd ?: 0) +
                textWidth(badge, BADGE_RESERVE_TEXT) + horizontalMargins(badge)
        }
        return total
    }

    /**
     * The narrow score column: the widest template that fits in the wide column, plus the score's
     * own padding. A template wider than the wide column lines up in neither, so counting it would
     * only stretch the narrow column to the wide one and leave the row nothing to give up - which
     * is what German's "Versteckt" for "Hidden" did.
     */
    private fun measureNarrowScoreWidth(): Int {
        val score = scoreTextView ?: return 0
        val original = originals[score] ?: return 0
        val padding = original.paddingStart + original.paddingEnd
        return scoreTemplates.map { textWidth(score, it) + padding }
            .filter { it <= original.minWidth }
            .maxOrNull() ?: original.minWidth
    }

    private fun textWidth(view: TextView, text: String): Int =
        ceil(Layout.getDesiredWidth(text, view.paint)).toInt()

    private fun horizontalMargins(child: View): Int {
        val lp = child.layoutParams as? ViewGroup.MarginLayoutParams ?: return 0
        return lp.marginStart + lp.marginEnd
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
                // Any TextView, not just the buttons: the score declares its wide column as
                // android:minWidth, and a row recycled after being compacted has no other record
                // of what the layout asked for.
                (child as? TextView)?.minWidth ?: 0,
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
            if (child is TextView && child === scoreTextView) {
                // MaterialButton owns its own minWidth above; the score is a plain TextView, so
                // nothing else here would restore it.
                //
                // Both minimums have to be written. A single android:minWidth in the layout lands in
                // two independent fields - TextView's, which its onMeasure applies, and View's, which
                // reaches the same measure through getSuggestedMinimumWidth() - because View and
                // TextView each declare that attribute and each parse it. Setting only TextView's
                // leaves the column stuck at the wider of the two.
                val narrow = narrowScore || level >= LEVEL_NARROW_SCORE
                child.minWidth = if (narrow) narrowScoreWidth else original.minWidth
                child.minimumWidth = if (narrow) narrowScoreWidth else original.minimumWidth
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
