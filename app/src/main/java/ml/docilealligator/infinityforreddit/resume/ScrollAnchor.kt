package ml.docilealligator.infinityforreddit.resume

import android.view.View
import android.view.ViewGroup
import androidx.core.view.OneShotPreDrawListener
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Captures and re-applies "which row is the user looking at, and where on screen does it sit".
 *
 * The offset `scrollToPositionWithOffset` restores against is measured to the item's decorated
 * START -- top decoration inset AND top margin removed -- relative to the RecyclerView's
 * paddingTop. Capturing anything else (plain `child.getTop()`, say) makes every capture/apply
 * round trip drift by the decoration/margin inset, which shows up as a list that creeps a few
 * pixels further down on each rotation.
 *
 * The capture logic here is the one that lived inline in `PostFragment.onSaveInstanceState`; it is
 * unchanged, only moved, so a resume restore and a rotation restore measure the same thing.
 */
object ScrollAnchor {

    const val NO_POSITION: Int = RecyclerView.NO_POSITION

    /** How long the list may stay hidden waiting for a scroll that never lands. */
    private const val REVEAL_TIMEOUT_MS = 1000L

    /**
     * A captured anchor, plus the sticky anchor to carry into the next capture.
     *
     * [stickyPosition] / [stickyOffset] are not always the same as [position] / [offset]: when the
     * previous anchor is still on screen its ORIGINAL offset is kept rather than re-measured. A
     * multi-column round trip goes through an intermediate where
     * StaggeredGridLayoutManager's gap handling snaps the anchor to the top, and re-measuring
     * there would overwrite the good offset with the snapped one.
     */
    class Anchor(
        @JvmField val position: Int,
        @JvmField val offset: Int,
        @JvmField val stickyPosition: Int,
        @JvmField val stickyOffset: Int,
    ) {
        val isValid: Boolean
            get() = position != NO_POSITION
    }

    /**
     * Pick the row the user perceives as their "main content" and measure where it sits.
     *
     * Preference order:
     * 1. [stickyPosition] from a previous capture, if it is still rendered -- preserves the user's
     *    intended item and its offset across lossy intermediates (see [Anchor]).
     * 2. The child with the largest visible height in the viewport. Ties go to the smaller adapter
     *    position, which preserves the "top of the row" feel in multi-column layouts.
     */
    @JvmStatic
    fun capture(rv: RecyclerView, stickyPosition: Int, stickyOffset: Int): Anchor {
        val paddingTop = rv.paddingTop
        val lm = rv.layoutManager
        val viewportBottom = rv.height - rv.paddingBottom

        var bestAnchorPos = NO_POSITION
        var bestAnchorOffset = 0
        var bestVisible = -1
        var stickyVisible = false

        for (i in 0 until rv.childCount) {
            val child = rv.getChildAt(i) ?: continue
            val childTop = child.top
            val childBottom = child.bottom
            if (childBottom <= paddingTop) continue
            val pos = rv.getChildAdapterPosition(child)
            if (pos == NO_POSITION) continue

            val topMargin = (child.layoutParams as ViewGroup.MarginLayoutParams).topMargin
            val decoratedStart = (lm?.getDecoratedTop(child) ?: childTop) - topMargin
            val childOffset = decoratedStart - paddingTop

            val visibleBottom = minOf(childBottom, viewportBottom)
            val visible = maxOf(0, visibleBottom - maxOf(childTop, paddingTop))
            if (visible <= 0) continue

            if (visible > bestVisible ||
                (visible == bestVisible && (bestAnchorPos == NO_POSITION || pos < bestAnchorPos))
            ) {
                bestAnchorPos = pos
                bestAnchorOffset = childOffset
                bestVisible = visible
            }
            if (pos == stickyPosition) {
                stickyVisible = true
            }
        }

        return when {
            // Anchor unchanged: keep the sticky offset (the original, un-snapped one).
            stickyVisible -> Anchor(stickyPosition, stickyOffset, stickyPosition, stickyOffset)
            // Anchor changed (the user scrolled away): adopt the most-visible item, measure fresh.
            bestAnchorPos != NO_POSITION ->
                Anchor(bestAnchorPos, bestAnchorOffset, bestAnchorPos, bestAnchorOffset)
            // Nothing rendered. The sticky anchor is carried through untouched rather than reset:
            // a capture against an empty list says nothing about where the user was.
            else -> Anchor(NO_POSITION, 0, stickyPosition, stickyOffset)
        }
    }

    /**
     * The row at the top edge of the viewport, and how far above that edge it starts.
     *
     * This, not [capture], is what a resume records. The two answer different questions and both
     * answers are right for their own question. [capture] picks the item occupying the most of the
     * viewport, which is the one the user would say they were reading, and reproduces it exactly
     * across a rotation because every row above it has the height it had a moment ago. A cold
     * restore has no such guarantee: images have not loaded, so rows above the anchor measure
     * differently, and an offset of -1065 or +1838 -- which is what "most visible" routinely
     * produces -- lands the user somewhere they have never been. The topmost row's offset is
     * bounded by its own height and says something a rebuilt layout can still honour: this row
     * starts here.
     */
    @JvmStatic
    fun captureTopmost(rv: RecyclerView): Anchor {
        val paddingTop = rv.paddingTop
        val lm = rv.layoutManager

        var bestPos = NO_POSITION
        var bestOffset = 0
        var bestTop = Int.MAX_VALUE

        for (i in 0 until rv.childCount) {
            val child = rv.getChildAt(i) ?: continue
            if (child.bottom <= paddingTop) continue
            val pos = rv.getChildAdapterPosition(child)
            if (pos == NO_POSITION) continue

            val topMargin = (child.layoutParams as ViewGroup.MarginLayoutParams).topMargin
            val decoratedStart = (lm?.getDecoratedTop(child) ?: child.top) - topMargin
            // Topmost by screen position, not by adapter position: a staggered grid lays its
            // columns out independently, so the lowest-indexed child is not always the highest one.
            if (decoratedStart < bestTop) {
                bestTop = decoratedStart
                bestPos = pos
                bestOffset = decoratedStart - paddingTop
            }
        }
        return Anchor(bestPos, bestOffset, bestPos, bestOffset)
    }

    /**
     * Scroll [rv] so [position] sits at [offset], branching on whatever layout manager it has.
     * Falls back to `scrollToPosition` for a layout manager with no offset-aware scroll, which
     * loses the offset but never the row.
     */
    @JvmStatic
    fun scrollTo(rv: RecyclerView, position: Int, offset: Int) {
        when (val lm = rv.layoutManager) {
            is StaggeredGridLayoutManager -> lm.scrollToPositionWithOffset(position, offset)
            is LinearLayoutManager -> lm.scrollToPositionWithOffset(position, offset)
            else -> rv.scrollToPosition(position)
        }
    }

    /**
     * Hide [rv] until an [applyHidden] reveals it, for the window between a restore being decided
     * on and the data it needs arriving.
     *
     * [timeoutMs] is the backstop: a load that errors or never returns reveals the list anyway,
     * because an empty feed the user can pull to refresh beats a blank screen with no explanation.
     */
    @JvmStatic
    fun hideUntilRestored(rv: RecyclerView, timeoutMs: Long) {
        rv.visibility = View.INVISIBLE
        rv.postDelayed({ rv.visibility = View.VISIBLE }, timeoutMs)
    }

    /**
     * Jump to [position]/[offset] with the list hidden, then reveal it and run [onDone].
     *
     * A restore that scrolls a visible list shows the top of the feed first and snaps away from it
     * a frame later. Hiding the list until the jump has landed removes that flash. One layout pass
     * AND one posted tick are waited out: the pass gives the adapter its children, the tick lets
     * the layout manager settle before the offset is applied against it.
     *
     * [REVEAL_TIMEOUT_MS] is the backstop. If the list never lays out -- a load that stalls or
     * errors -- the reveal happens anyway, because a feed at the wrong offset beats a blank screen.
     */
    @JvmStatic
    @JvmOverloads
    fun applyHidden(rv: RecyclerView, position: Int, offset: Int, onDone: Runnable? = null) {
        if (position == NO_POSITION) {
            // Nowhere to jump to. Undo any earlier hideUntilRestored rather than leaving a list
            // that is loaded, correct, and invisible.
            rv.visibility = View.VISIBLE
            onDone?.run()
            return
        }

        rv.visibility = View.INVISIBLE
        val revealed = AtomicBoolean(false)
        val reveal = Runnable {
            if (revealed.compareAndSet(false, true)) {
                rv.visibility = View.VISIBLE
                onDone?.run()
            }
        }
        rv.postDelayed(reveal, REVEAL_TIMEOUT_MS)

        OneShotPreDrawListener.add(rv) {
            rv.post {
                if (!revealed.get()) {
                    scrollTo(rv, position, offset)
                    // Posted so the reveal lands after the jump has been laid out, not with it.
                    rv.post {
                        rv.removeCallbacks(reveal)
                        reveal.run()
                    }
                }
            }
        }
    }
}
