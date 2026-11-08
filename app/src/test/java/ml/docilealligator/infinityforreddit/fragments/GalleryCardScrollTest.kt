package ml.docilealligator.infinityforreddit.fragments

import android.app.Activity
import android.os.Looper
import android.os.SystemClock
import android.view.ContextThemeWrapper
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.PagerSnapHelper
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.AppBarLayout
import ml.docilealligator.infinityforreddit.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.xmlpull.v1.XmlPullParser

/**
 * A gallery card holds a horizontal carousel. When the finger lands on it, the carousel asks its
 * ancestors to start a nested scroll along its own axis, and the CoordinatorLayout above the feed
 * answers by recording that the collapsing app bar did not accept it -- on the same flag the feed's
 * own vertical scroll had just set. The app bar then sat out the rest of the drag, so the feed
 * scrolled under it first and the header only collapsed afterwards. A carousel that takes no part
 * in nested scrolling leaves the flag alone.
 */
@RunWith(RobolectricTestRunner::class)
class GalleryCardScrollTest {

    private lateinit var activity: Activity
    private lateinit var coordinator: CoordinatorLayout
    private lateinit var appBar: AppBarLayout
    private lateinit var feed: RecyclerView
    private lateinit var title: View
    private lateinit var carousel: RecyclerView

    @Before
    fun setUp() {
        activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val ctx = ContextThemeWrapper(activity, com.google.android.material.R.style.Theme_Material3_Light_NoActionBar)
        coordinator = CoordinatorLayout(ctx)
        appBar = AppBarLayout(ctx)
        for (height in intArrayOf(150, 50)) {
            appBar.addView(View(ctx), AppBarLayout.LayoutParams(MATCH, height).apply {
                scrollFlags = AppBarLayout.LayoutParams.SCROLL_FLAG_SCROLL or AppBarLayout.LayoutParams.SCROLL_FLAG_ENTER_ALWAYS
            })
        }
        coordinator.addView(appBar, CoordinatorLayout.LayoutParams(MATCH, ViewGroup.LayoutParams.WRAP_CONTENT))
        feed = RecyclerView(ctx)
        feed.layoutManager = LinearLayoutManager(ctx)
        feed.adapter = FeedAdapter()
        coordinator.addView(feed, CoordinatorLayout.LayoutParams(MATCH, MATCH).apply {
            behavior = AppBarLayout.ScrollingViewBehavior()
        })
        activity.setContentView(coordinator)
        settle()
        val card = feed.getChildAt(0) as ViewGroup
        title = card.getChildAt(0)
        carousel = card.getChildAt(1) as RecyclerView
    }

    private fun settle() = shadowOf(Looper.getMainLooper()).idle()

    private fun centreOf(view: View): Pair<Float, Float> {
        val at = IntArray(2)
        val origin = IntArray(2)
        view.getLocationInWindow(at)
        coordinator.getLocationInWindow(origin)
        return Pair(at[0] - origin[0] + view.width / 2f, at[1] - origin[1] + view.height / 2f)
    }

    private fun dragUpOn(view: View, distance: Float) {
        val (x, y) = centreOf(view)
        val down = SystemClock.uptimeMillis()
        var time = down
        send(MotionEvent.obtain(down, time, MotionEvent.ACTION_DOWN, x, y, 0))
        val steps = 20
        for (i in 1..steps) {
            time += 16
            send(MotionEvent.obtain(down, time, MotionEvent.ACTION_MOVE, x, y - distance * i / steps, 0))
        }
        time += 16
        send(MotionEvent.obtain(down, time, MotionEvent.ACTION_UP, x, y - distance, 0))
        settle()
    }

    private fun send(event: MotionEvent) {
        coordinator.dispatchTouchEvent(event)
        event.recycle()
    }

    /** What a drag left behind: how far the app bar went and how far the feed itself scrolled. */
    private fun state() = "appBar=${appBar.top} feedScroll=${feed.computeVerticalScrollOffset()}"

    @Test
    fun `a drag on a carousel that sits out nested scrolling does what a drag on the title does`() {
        dragUpOn(title, 100f)
        val onTitle = state()
        setUp()
        carousel.isNestedScrollingEnabled = false
        dragUpOn(carousel, 100f)
        assertEquals(onTitle, state())
    }

    @Test
    fun `every gallery carousel layout sits out nested scrolling`() {
        for ((layout, id) in listOf(
            R.layout.item_post_gallery_type to R.id.gallery_recycler_view_item_post_gallery_type,
            R.layout.item_post_card_3_gallery_type to R.id.gallery_recycler_view_item_post_card_3_gallery_type,
            R.layout.item_post_detail_gallery to R.id.gallery_recycler_view_item_post_detail_gallery,
        )) {
            assertTrue(activity.resources.getResourceEntryName(layout), sitsOutNestedScrolling(layout, id))
        }
    }

    private fun sitsOutNestedScrolling(layout: Int, id: Int): Boolean {
        val parser = activity.resources.getLayout(layout)
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG && parser.getIdAttributeResourceValue(0) == id) {
                return !parser.getAttributeBooleanValue(ANDROID_NS, "nestedScrollingEnabled", true)
            }
        }
        error("no view with that id in the layout")
    }

    private inner class FeedAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun getItemCount() = 20
        override fun getItemViewType(position: Int) = if (position == 0) 0 else 1

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val context = parent.context
            if (viewType == 1) {
                val plain = View(context)
                plain.layoutParams = RecyclerView.LayoutParams(MATCH, 250)
                return object : RecyclerView.ViewHolder(plain) {}
            }
            val card = LinearLayout(context)
            card.orientation = LinearLayout.VERTICAL
            card.layoutParams = RecyclerView.LayoutParams(MATCH, ViewGroup.LayoutParams.WRAP_CONTENT)
            card.addView(View(context), LinearLayout.LayoutParams(MATCH, 60))
            val inner = RecyclerView(context)
            inner.layoutManager = LinearLayoutManager(context, RecyclerView.HORIZONTAL, false)
            inner.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
                override fun getItemCount() = 3
                override fun onCreateViewHolder(p: ViewGroup, t: Int): RecyclerView.ViewHolder {
                    val image = View(p.context)
                    image.layoutParams = RecyclerView.LayoutParams(MATCH, MATCH)
                    return object : RecyclerView.ViewHolder(image) {}
                }

                override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {}
            }
            PagerSnapHelper().attachToRecyclerView(inner)
            card.addView(inner, LinearLayout.LayoutParams(MATCH, 250))
            return object : RecyclerView.ViewHolder(card) {}
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {}
    }

    private companion object {
        const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
    }
}
