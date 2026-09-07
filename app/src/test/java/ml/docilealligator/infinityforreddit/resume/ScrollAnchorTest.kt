package ml.docilealligator.infinityforreddit.resume

import android.content.Context
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import ml.docilealligator.infinityforreddit.TestInfinity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The anchor has to survive a round trip to the pixel, and the reason it is easy to get wrong is
 * that the offset `scrollToPositionWithOffset` restores against is not the one the obvious
 * measurement gives.
 *
 * That offset is measured to the item's decorated START -- top decoration inset AND top margin
 * removed -- relative to the list's `paddingTop`. Capture plain `child.getTop()` instead and every
 * capture/apply cycle drifts by the inset, which is what a list creeping a few pixels further down
 * on each rotation actually is. The tests below give the list both an inset and a margin so a
 * capture that ignored either would land somewhere else.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestInfinity::class)
class ScrollAnchorTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private companion object {
        const val ITEM_HEIGHT = 100
        const val TOP_MARGIN = 7
        const val DECORATION_INSET = 13
        const val LIST_PADDING_TOP = 23
        const val VIEWPORT_HEIGHT = 500
        const val ITEM_COUNT = 40
    }

    private class FixedHeightAdapter : RecyclerView.Adapter<FixedHeightAdapter.Holder>() {
        class Holder(view: View) : RecyclerView.ViewHolder(view)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val view = View(parent.context)
            view.layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ITEM_HEIGHT).apply {
                topMargin = TOP_MARGIN
            }
            return Holder(view)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) = Unit

        override fun getItemCount() = ITEM_COUNT
    }

    /** A laid-out list, with a top inset and a top margin on every row. */
    private fun list(): RecyclerView {
        val recyclerView = RecyclerView(context)
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = FixedHeightAdapter()
        recyclerView.setPadding(0, LIST_PADDING_TOP, 0, 0)
        recyclerView.clipToPadding = false
        recyclerView.addItemDecoration(object : RecyclerView.ItemDecoration() {
            override fun getItemOffsets(
                outRect: Rect,
                view: View,
                parent: RecyclerView,
                state: RecyclerView.State,
            ) {
                outRect.top = DECORATION_INSET
            }
        })
        // Robolectric draws nothing, so the layout pass has to be driven by hand.
        val host = FrameLayout(context)
        host.addView(recyclerView, VIEWPORT_HEIGHT, VIEWPORT_HEIGHT)
        layOut(recyclerView)
        return recyclerView
    }

    private fun layOut(recyclerView: RecyclerView) {
        recyclerView.measure(
            View.MeasureSpec.makeMeasureSpec(VIEWPORT_HEIGHT, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(VIEWPORT_HEIGHT, View.MeasureSpec.EXACTLY))
        recyclerView.layout(0, 0, VIEWPORT_HEIGHT, VIEWPORT_HEIGHT)
    }

    @Test
    fun `capture and apply land on the same pixels`() {
        val recyclerView = list()
        ScrollAnchor.scrollTo(recyclerView, 12, -37)
        layOut(recyclerView)

        val captured = ScrollAnchor.captureTopmost(recyclerView)

        // Somewhere else entirely, then back.
        ScrollAnchor.scrollTo(recyclerView, 0, 0)
        layOut(recyclerView)
        ScrollAnchor.scrollTo(recyclerView, captured.position, captured.offset)
        layOut(recyclerView)

        val again = ScrollAnchor.captureTopmost(recyclerView)
        assertEquals(captured.position, again.position)
        assertEquals(captured.offset, again.offset)
    }

    @Test
    fun `repeated round trips do not drift`() {
        // The regression this is really about: an offset measured to the wrong edge loses the
        // decoration inset and the margin on every cycle, so the list settles a little further down
        // each time rather than failing outright.
        val recyclerView = list()
        ScrollAnchor.scrollTo(recyclerView, 9, -21)
        layOut(recyclerView)

        var anchor = ScrollAnchor.captureTopmost(recyclerView)
        val first = anchor
        repeat(5) {
            ScrollAnchor.scrollTo(recyclerView, anchor.position, anchor.offset)
            layOut(recyclerView)
            anchor = ScrollAnchor.captureTopmost(recyclerView)
        }

        assertEquals(first.position, anchor.position)
        assertEquals("drifted by ${anchor.offset - first.offset}px", first.offset, anchor.offset)
    }

    @Test
    fun `the offset is measured past the decoration and the margin`() {
        // Pinning the actual number, not just its stability: at the very top the first row's
        // decorated start sits exactly paddingTop below the list's top edge, so the offset is zero
        // even though the row's own top is an inset and a margin further down.
        val recyclerView = list()
        ScrollAnchor.scrollTo(recyclerView, 0, 0)
        layOut(recyclerView)

        val anchor = ScrollAnchor.captureTopmost(recyclerView)

        assertEquals(0, anchor.position)
        assertEquals(0, anchor.offset)
        val firstRow = recyclerView.getChildAt(0)
        assertEquals("the row itself starts below its own inset and margin",
            LIST_PADDING_TOP + DECORATION_INSET + TOP_MARGIN, firstRow.top)
    }

    @Test
    fun `captureTopmost takes the row at the top edge, not the biggest one`() {
        // The two captures answer different questions. A cold restore has no idea how tall the rows
        // above the anchor will turn out to be -- their images have not loaded -- so it records the
        // row at the viewport edge, whose offset is bounded by its own height, rather than the one
        // filling most of the screen.
        val recyclerView = list()
        // Land so that row 6 is barely on screen at the top and row 7 fills most of the viewport.
        ScrollAnchor.scrollTo(recyclerView, 6, -90)
        layOut(recyclerView)

        val topmost = ScrollAnchor.captureTopmost(recyclerView)
        val mostVisible = ScrollAnchor.capture(recyclerView, ScrollAnchor.NO_POSITION, 0)

        assertEquals(6, topmost.position)
        assertTrue("the most-visible row is further down the list",
            mostVisible.position > topmost.position)
    }

    @Test
    fun `an empty list captures no anchor`() {
        val recyclerView = RecyclerView(context)
        recyclerView.layoutManager = LinearLayoutManager(context)
        FrameLayout(context).addView(recyclerView, VIEWPORT_HEIGHT, VIEWPORT_HEIGHT)
        layOut(recyclerView)

        assertFalse(ScrollAnchor.captureTopmost(recyclerView).isValid)
    }

    @Test
    fun `capture keeps the sticky anchor while it is still on screen`() {
        // A multi-column round trip goes through an intermediate where the layout manager snaps the
        // anchor to the top. Re-measuring there would overwrite the good offset with the snapped
        // one, so an anchor that is still rendered keeps the offset it was captured with.
        val recyclerView = list()
        ScrollAnchor.scrollTo(recyclerView, 10, -40)
        layOut(recyclerView)

        val sticky = ScrollAnchor.capture(recyclerView, 11, -1234)

        assertEquals(11, sticky.position)
        assertEquals(-1234, sticky.offset)
    }

    @Test
    fun `an empty list carries the sticky anchor through untouched`() {
        // A capture against a list with nothing rendered says nothing about where the user was, so
        // it must not reset what the last one recorded.
        val recyclerView = RecyclerView(context)
        recyclerView.layoutManager = LinearLayoutManager(context)
        FrameLayout(context).addView(recyclerView, VIEWPORT_HEIGHT, VIEWPORT_HEIGHT)
        layOut(recyclerView)

        val anchor = ScrollAnchor.capture(recyclerView, 5, -60)

        assertFalse(anchor.isValid)
        assertEquals(5, anchor.stickyPosition)
        assertEquals(-60, anchor.stickyOffset)
    }
}
