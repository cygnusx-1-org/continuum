package ml.docilealligator.infinityforreddit.customviews

import android.content.Context
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.TextUtils
import android.util.AttributeSet
import android.view.View
import androidx.appcompat.widget.AppCompatTextView
import java.util.function.Supplier

/**
 * A name with badges after it — the followed/saved marker (issue #415) and the user tag chip
 * (#413) — that drops the badges onto a second line rather than letting them be ellipsised away.
 *
 * A compact row gives the name only the width left between the subreddit icon and the timestamp,
 * with `ellipsize="end"`, and the badges sit at the end of that text. A name long enough to fill
 * the row therefore took the badges with it: `u/The_GoldenGreyWarden ♥` rendered as
 * `u/The_GoldenGreyW…`, and a followed user looked like anyone else. The badges are the one part
 * of the line that cannot be inferred from what is left, so they are what survives.
 *
 * Which of the two forms is used is decided in [onMeasure], because only there is the width the
 * row actually allows known:
 *
 * - **Both fit**: one line, `name` then badges, exactly as a plain `TextView` drew it. This is
 *   every short name, and the row is the height it always was.
 * - **They do not**: two lines — the name ellipsised to the full width on the first, the badges
 *   alone on the second. The row grows by one line, which is the cost of showing them at all.
 *
 * The badges come in two forms: the one passed outright carries the 4dp lead-in that separates
 * them from the name, and the one behind the supplier does not, so a second line starts flush
 * under the name rather than 4dp inside it. The second form is built only by the rows that turn
 * out to need it -- every row builds its badges on every bind, and the drawable behind the marker
 * is not free.
 */
class BadgedNameTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.textViewStyle,
) : AppCompatTextView(context, attrs, defStyleAttr) {

    private var name: CharSequence = ""
    private var inlineBadges: CharSequence? = null
    private var standaloneBadges: Supplier<CharSequence>? = null

    /** The width [applyForWidth] last laid the text out for; -1 forces the next measure to redo it. */
    private var appliedWidth = -1

    /** Whether the badges are currently on a line of their own. */
    private var isTwoLine = false

    /**
     * The name and the badges to put after it, or nulls for a name with none — which is also how
     * this view is used for text that can never carry a badge, such as a subreddit's name.
     */
    fun setNameAndBadges(
        name: CharSequence?,
        inlineBadges: CharSequence?,
        standaloneBadges: Supplier<CharSequence>?,
    ) {
        this.name = name ?: ""
        this.inlineBadges = inlineBadges
        this.standaloneBadges = standaloneBadges
        // The recycled view is showing the previous post's author until the measure below runs, so
        // the one-line form goes on now. It is the right answer for most names, and the wrong one
        // is corrected before the frame is drawn.
        appliedWidth = -1
        applyOneLine()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val available = View.MeasureSpec.getSize(widthMeasureSpec) - compoundPaddingLeft - compoundPaddingRight
        // An unspecified width is a measurement pass with no row to fit into -- a scroll-state
        // save, say -- and answering it would cache a width the row never has.
        if (View.MeasureSpec.getMode(widthMeasureSpec) != View.MeasureSpec.UNSPECIFIED &&
            available > 0 && available != appliedWidth
        ) {
            appliedWidth = available
            applyForWidth(available)
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    private fun applyOneLine() {
        val badges = inlineBadges
        maxLines = 1
        isTwoLine = false
        text = if (badges.isNullOrEmpty()) name else SpannableStringBuilder(name).append(badges)
    }

    private fun applyForWidth(available: Int) {
        val inline = inlineBadges
        if (inline.isNullOrEmpty()) {
            // Nothing to move, and setNameAndBadges already wrote the name.
            if (isTwoLine) {
                applyOneLine()
            }
            return
        }
        // getDesiredWidth measures the spans too, so the marker's own width is part of the answer
        // rather than something added to it afterwards.
        if (Layout.getDesiredWidth(SpannableStringBuilder(name).append(inline), paint) <= available) {
            // setNameAndBadges already wrote this form, and writing it again is another setText and
            // another layout request on the busiest path there is -- a compact feed being flung.
            if (isTwoLine) {
                applyOneLine()
            }
            return
        }
        // Ellipsised here rather than left to the view: with two lines allowed, the line breaker
        // would spill the name onto the second and truncate there, putting the badges back inside
        // what gets cut.
        val ellipsisedName = TextUtils.ellipsize(name, paint, available.toFloat(), TextUtils.TruncateAt.END)
        maxLines = 2
        isTwoLine = true
        text = SpannableStringBuilder(ellipsisedName)
            .append('\n')
            .append(standaloneBadges?.get() ?: inline)
    }
}
