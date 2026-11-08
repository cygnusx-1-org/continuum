package ml.docilealligator.infinityforreddit.customviews

import android.app.Activity
import android.content.Context
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.widget.NestedScrollView
import androidx.viewpager.widget.PagerAdapter
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * The gallery's pager sits inside the same wrapper as on screen -- a NestedScrollView that fills the
 * viewport -- because that wrapper is what once swallowed every up-and-down drag before the pager
 * saw it, so "Swipe Up and Down Through a Gallery" paged nothing. It has to be on a window: a pager
 * that is not attached never lays out its pages.
 */
@RunWith(RobolectricTestRunner::class)
class ViewPagerBugFixedTest {

    private lateinit var pager: ViewPagerBugFixed

    private val width get() = pager.width.toFloat()
    private val height get() = pager.height.toFloat()

    @Before
    fun setUp() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        pager = ViewPagerBugFixed(activity)
        pager.adapter = object : PagerAdapter() {
            override fun getCount() = 3
            override fun isViewFromObject(view: View, `object`: Any) = view === `object`
            override fun instantiateItem(container: ViewGroup, position: Int): Any {
                val page = GrabbyPage(activity)
                container.addView(page)
                return page
            }

            override fun destroyItem(container: ViewGroup, position: Int, `object`: Any) {
                container.removeView(`object` as View)
            }
        }
        val frame = FrameLayout(activity)
        frame.addView(pager, FrameLayout.LayoutParams(MATCH, MATCH))
        val scroller = NestedScrollView(activity)
        scroller.isFillViewport = true
        scroller.addView(frame, ViewGroup.LayoutParams(MATCH, MATCH))
        activity.setContentView(scroller)
        settle()
    }

    private fun settle() = shadowOf(Looper.getMainLooper()).idle()

    private fun startOn(page: Int) {
        pager.setCurrentItem(page, false)
        settle()
    }

    /** A finger going from ([fromX], [fromY]) to ([toX], [toY]) in small steps, then lifting. */
    private fun drag(fromX: Float, fromY: Float, toX: Float, toY: Float) {
        val down = SystemClock.uptimeMillis()
        var time = down
        send(MotionEvent.obtain(down, time, MotionEvent.ACTION_DOWN, fromX, fromY, 0))
        val steps = 12
        for (i in 1..steps) {
            time += 16
            val x = fromX + (toX - fromX) * i / steps
            val y = fromY + (toY - fromY) * i / steps
            send(MotionEvent.obtain(down, time, MotionEvent.ACTION_MOVE, x, y, 0))
        }
        time += 16
        send(MotionEvent.obtain(down, time, MotionEvent.ACTION_UP, toX, toY, 0))
    }

    private fun send(event: MotionEvent) {
        (pager.parent.parent as View).dispatchTouchEvent(event)
        event.recycle()
    }

    private fun swipeUp() = drag(width / 2, height * 0.8f, width / 2, height * 0.2f)
    private fun swipeDown() = drag(width / 2, height * 0.2f, width / 2, height * 0.8f)
    private fun swipeLeft() = drag(width * 0.8f, height / 2, width * 0.2f, height / 2)
    private fun swipeRight() = drag(width * 0.2f, height / 2, width * 0.8f, height / 2)

    @Test
    fun `the standard gallery pages left`() {
        swipeLeft()
        assertEquals(1, pager.currentItem)
    }

    @Test
    fun `the standard gallery pages right`() {
        startOn(1)
        swipeRight()
        assertEquals(0, pager.currentItem)
    }

    @Test
    fun `the standard gallery ignores an up and down drag`() {
        swipeUp()
        assertEquals(0, pager.currentItem)
    }

    @Test
    fun `a vertical gallery pages up`() {
        pager.setVertical(true)
        swipeUp()
        assertEquals(1, pager.currentItem)
    }

    @Test
    fun `a vertical gallery pages down`() {
        pager.setVertical(true)
        startOn(1)
        swipeDown()
        assertEquals(0, pager.currentItem)
    }

    @Test
    fun `a vertical gallery ignores a sideways drag`() {
        pager.setVertical(true)
        swipeLeft()
        assertEquals(0, pager.currentItem)
    }

    /**
     * What the gallery's real page does: SubsamplingScaleImageView takes every touch, asks its parents
     * to leave it alone as soon as the finger lands, and lets go only when the finger moves sideways.
     * Nothing but that sideways release ever reaches a pager that has been told to keep out.
     */
    private class GrabbyPage(context: Context) : View(context) {
        private var startX = 0f

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.x
                    parent.requestDisallowInterceptTouchEvent(true)
                }
                MotionEvent.ACTION_MOVE ->
                    if (Math.abs(event.x - startX) > 20) parent.requestDisallowInterceptTouchEvent(false)
            }
            return true
        }
    }

    private companion object {
        const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    }
}
