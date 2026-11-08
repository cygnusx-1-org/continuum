package ml.docilealligator.infinityforreddit.customviews

import android.content.Context
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.widget.NestedScrollView
import androidx.test.core.app.ApplicationProvider
import androidx.viewpager.widget.PagerAdapter
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The gallery's pager sits inside the same wrapper as on screen -- a NestedScrollView that fills the
 * viewport -- because that wrapper is what once swallowed every up-and-down drag before the pager
 * saw it, so "Swipe Up and Down Through a Gallery" paged nothing.
 */
@RunWith(RobolectricTestRunner::class)
class ViewPagerBugFixedTest {

    private lateinit var context: Context
    private lateinit var pager: ViewPagerBugFixed
    private lateinit var root: FrameLayout

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        pager = ViewPagerBugFixed(context)
        pager.adapter = object : PagerAdapter() {
            override fun getCount() = 3
            override fun isViewFromObject(view: View, `object`: Any) = view === `object`
            override fun instantiateItem(container: ViewGroup, position: Int): Any {
                val page = View(context)
                container.addView(page)
                return page
            }

            override fun destroyItem(container: ViewGroup, position: Int, `object`: Any) {
                container.removeView(`object` as View)
            }
        }
        val frame = FrameLayout(context)
        frame.addView(pager, FrameLayout.LayoutParams(MATCH, MATCH))
        val scroller = NestedScrollView(context)
        scroller.isFillViewport = true
        scroller.addView(frame, ViewGroup.LayoutParams(MATCH, MATCH))
        root = FrameLayout(context)
        root.addView(scroller, FrameLayout.LayoutParams(MATCH, MATCH))
        layout()
    }

    private fun layout() {
        root.measure(
            View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(HEIGHT, View.MeasureSpec.EXACTLY)
        )
        root.layout(0, 0, WIDTH, HEIGHT)
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
        root.dispatchTouchEvent(event)
        event.recycle()
    }

    private fun swipeUp() = drag(WIDTH / 2f, HEIGHT * 0.8f, WIDTH / 2f, HEIGHT * 0.2f)
    private fun swipeDown() = drag(WIDTH / 2f, HEIGHT * 0.2f, WIDTH / 2f, HEIGHT * 0.8f)
    private fun swipeLeft() = drag(WIDTH * 0.8f, HEIGHT / 2f, WIDTH * 0.2f, HEIGHT / 2f)
    private fun swipeRight() = drag(WIDTH * 0.2f, HEIGHT / 2f, WIDTH * 0.8f, HEIGHT / 2f)

    private fun startOn(page: Int) {
        pager.setCurrentItem(page, false)
        layout()
    }

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

    private companion object {
        const val WIDTH = 1080
        const val HEIGHT = 1920
        const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    }
}
