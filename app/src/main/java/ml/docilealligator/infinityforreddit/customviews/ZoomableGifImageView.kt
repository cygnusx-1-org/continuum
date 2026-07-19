package ml.docilealligator.infinityforreddit.customviews

import android.annotation.SuppressLint
import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
import com.otaliastudios.zoom.ZoomImageView

/**
 * A [ZoomImageView] for displaying animated GIFs with pinch-to-zoom and pan.
 *
 * The bare ZoomImageView swallows touch events in its zoom engine, which breaks two things we
 * rely on, so this subclass restores them:
 *  - Single taps are forwarded to the view's OnClickListener. BigImageView sets that listener on
 *    the GIF view to toggle the toolbar, so without this a tap would do nothing.
 *  - While the user is pinching (two fingers) or panning a zoomed-in GIF, parent views (the
 *    swipe-to-dismiss HaulerView, the gallery ViewPager and the NestedScrollView) are asked to
 *    stop intercepting touches, mirroring what SubsamplingScaleImageView already does for static
 *    images. At rest (zoom == 1, one finger) the parents keep their gestures, so swipe-to-dismiss
 *    and paging between gallery items still work.
 */
class ZoomableGifImageView(context: Context) : ZoomImageView(context) {

    private val tapDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            performClick()
            return true
        }
    })

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        tapDetector.onTouchEvent(ev)

        if (ev.pointerCount >= 2 || zoom > 1.00001f) {
            parent?.requestDisallowInterceptTouchEvent(true)
        }

        return super.onTouchEvent(ev)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
