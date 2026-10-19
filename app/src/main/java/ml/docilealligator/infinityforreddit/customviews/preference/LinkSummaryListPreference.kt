package ml.docilealligator.infinityforreddit.customviews.preference

import android.content.Context
import android.text.Spanned
import android.text.style.ClickableSpan
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.TextView
import androidx.preference.PreferenceViewHolder

/**
 * A [CustomFontListPreference] whose summary can carry links: a tap on a [ClickableSpan] in it
 * runs the span, and a tap anywhere else opens the list as usual.
 *
 * The summary view is not given a movement method, which would make the whole of it clickable and
 * swallow the row's own tap; only a touch that lands on a link is taken.
 */
class LinkSummaryListPreference(context: Context, attrs: AttributeSet?) :
    CustomFontListPreference(context, attrs) {

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        val summary = holder.findViewById(android.R.id.summary) as? TextView ?: return
        summary.setOnTouchListener { view, event ->
            val action = event.actionMasked
            val link = if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_UP) {
                linkAt(view as TextView, event)
            } else {
                null
            }
            if (link != null && action == MotionEvent.ACTION_UP) {
                view.performClick()
                link.onClick(view)
            }
            link != null
        }
    }

    /** The link under [event] in [summary], or null when the touch is not on one. */
    private fun linkAt(summary: TextView, event: MotionEvent): ClickableSpan? {
        val text = summary.text as? Spanned ?: return null
        val layout = summary.layout ?: return null
        val x = event.x - summary.totalPaddingLeft + summary.scrollX
        val y = event.y.toInt() - summary.totalPaddingTop + summary.scrollY
        if (y < 0 || y > layout.height) {
            return null
        }
        val line = layout.getLineForVertical(y)
        if (x < layout.getLineLeft(line) || x > layout.getLineRight(line)) {
            return null
        }
        val offset = layout.getOffsetForHorizontal(line, x)
        return text.getSpans(offset, offset, ClickableSpan::class.java).firstOrNull()
    }
}
