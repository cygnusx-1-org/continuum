package ml.docilealligator.infinityforreddit.customviews;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.viewpager.widget.ViewPager;

public class ViewPagerBugFixed extends ViewPager {
    private boolean vertical;

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
            return super.onTouchEvent(vertical ? swapAxes(ev) : ev);
        } catch (IllegalArgumentException ignore) {
            Log.d("ViewPagerBugFixed", "onTouchEvent: ignoring IllegalArgumentException", ignore);
        }

        return false;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
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
