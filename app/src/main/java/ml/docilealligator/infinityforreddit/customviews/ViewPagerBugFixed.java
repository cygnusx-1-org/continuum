package ml.docilealligator.infinityforreddit.customviews;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.viewpager.widget.ViewPager;
import java.util.function.BooleanSupplier;

public class ViewPagerBugFixed extends ViewPager {
    private boolean vertical;
    @Nullable
    private BooleanSupplier keepsDrag;

    public ViewPagerBugFixed(@NonNull Context context) {
        super(context);
    }

    public ViewPagerBugFixed(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    /**
     * Pages up and down instead of sideways. A ViewPager only knows one axis, so this lays the pages
     * out along the other and hands the pager its touches with the two axes swapped; the children
     * still get their touches as they were.
     */
    public void setVertical(boolean vertical) {
        this.vertical = vertical;
        setPageTransformer(true, vertical ? (page, position) -> {
            page.setTranslationX(page.getWidth() * -position);
            page.setTranslationY(position * page.getHeight());
        } : null);
        setOverScrollMode(vertical ? OVER_SCROLL_NEVER : OVER_SCROLL_ALWAYS);
    }

    /**
     * While this says yes, a vertical pager leaves an up or down drag to the page under the finger
     * instead of turning it into a page change: a zoomed picture is panned, not paged away from.
     * (A sideways pager asks the page itself, which a vertical one cannot, its axes being swapped.)
     */
    public void setKeepsDrag(@Nullable BooleanSupplier keepsDrag) {
        this.keepsDrag = keepsDrag;
    }

    /**
     * A page that takes every touch -- the gallery's image view does, to pan and zoom -- asks its
     * parents to leave it alone the moment the finger lands, and lets go only for a sideways drag.
     * That is the wrong axis for a vertical pager, which would then never see an up or down drag, so
     * it does not honour the request; {@link #setKeepsDrag} is how a page keeps a drag it needs.
     */
    @Override
    public void requestDisallowInterceptTouchEvent(boolean disallowIntercept) {
        if (!vertical) {
            super.requestDisallowInterceptTouchEvent(disallowIntercept);
        }
    }

    @Override
    protected boolean canScroll(View v, boolean checkV, int dx, int x, int y) {
        if (vertical) {
            return keepsDrag != null && keepsDrag.getAsBoolean();
        }
        return super.canScroll(v, checkV, dx, x, y);
    }

    /** {@code ev} with its x and y exchanged, in the pager's own proportions; applying it twice undoes it. */
    private MotionEvent swapAxes(MotionEvent ev) {
        float width = getWidth();
        float height = getHeight();
        ev.setLocation(ev.getY() / height * width, ev.getX() / width * height);
        return ev;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        try {
            if (!vertical) {
                return super.onTouchEvent(ev);
            }
            // Handed back as it came, since whatever looks at the event next expects the screen's axes.
            boolean handled = super.onTouchEvent(swapAxes(ev));
            swapAxes(ev);
            return handled;
        } catch (IllegalArgumentException ignore) {
            Log.d("ViewPagerBugFixed", "onTouchEvent: ignoring IllegalArgumentException", ignore);
        }

        return false;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (vertical && ev.getActionMasked() == MotionEvent.ACTION_DOWN && getParent() != null) {
            // The scrolling wrappers above this take any up or down drag for their own before the
            // pager can claim it; their say ends when they are asked to let go.
            getParent().requestDisallowInterceptTouchEvent(true);
        }
        try {
            boolean intercepted = super.onInterceptTouchEvent(vertical ? swapAxes(ev) : ev);
            if (vertical) {
                swapAxes(ev);
            }
            return intercepted;
        } catch (IllegalArgumentException ignore) {
            Log.d("ViewPagerBugFixed", "onInterceptTouchEvent: ignoring IllegalArgumentException", ignore);
        }

        return false;
    }
}
