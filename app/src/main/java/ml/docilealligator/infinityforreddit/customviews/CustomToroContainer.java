package ml.docilealligator.infinityforreddit.customviews;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ml.docilealligator.infinityforreddit.videoautoplay.widget.Container;

public class CustomToroContainer extends Container {
    private final Rect occluderRect = new Rect();

    @Nullable
    private OnWindowFocusChangedListener listener;

    public CustomToroContainer(Context context) {
        super(context);
    }

    public CustomToroContainer(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public CustomToroContainer(Context context, @Nullable AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    public void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
    }

    @Override
    public void onWindowFocusChanged(boolean hasWindowFocus) {
        super.onWindowFocusChanged(hasWindowFocus);
        if (listener != null) {
            listener.onWindowFocusChanged(hasWindowFocus);
        }
    }

    public void addOnWindowFocusChangedListener(@Nullable OnWindowFocusChangedListener onWindowFocusChangedListener) {
        this.listener = onWindowFocusChangedListener;
    }

    /**
     * Removes the part of the feed that the activity paints over it.
     *
     * <p>Every activity hosting one of these lays the list out to the full height of a
     * CoordinatorLayout and then draws the bottom app bar on top of its lower edge, so the rows
     * behind that bar are on screen in the window's sense but not in the reader's. Counting them
     * made a card report itself fully visible while its last stretch was covered, which is the
     * difference between the autoplay visible-area setting meaning "in the window" and meaning "in
     * the feed".
     *
     * <p>Only bars spanning the full width are subtracted, because anything narrower -- the
     * floating action button, say -- takes a bite out of the middle of an edge, and a rectangle
     * cannot describe what is left. A bar that is hidden, fully transparent, or scrolled away
     * covers nothing and is skipped on its own, so this follows a bar that hides on scroll without
     * being told about it.
     */
    @Override
    public void clipToViewport(@NonNull Rect contentRect) {
        // Every level up to the activity's content view, not just the nearest CoordinatorLayout:
        // the feed fragments are themselves rooted in one, so stopping at the first would only ever
        // find the fragment's own children and never the activity's bar.
        View branch = this;
        ViewParent parent = getParent();
        while (parent instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) parent;
            for (int i = 0; i < group.getChildCount(); i++) {
                View child = group.getChildAt(i);
                // Skip the branch this container sits in; it is the thing being covered.
                if (child == branch) {
                    continue;
                }
                clipByOccluder(child, contentRect);
            }
            if (group.getId() == android.R.id.content) {
                break;
            }
            branch = group;
            parent = group.getParent();
        }
    }

    /** Takes {@code child} off {@code contentRect} if it currently covers a full-width band of it. */
    private void clipByOccluder(View child, Rect contentRect) {
        if (child.getVisibility() != View.VISIBLE || child.getAlpha() == 0f) {
            return;
        }
        if (!child.getGlobalVisibleRect(occluderRect)) {
            return;
        }
        if (occluderRect.left > contentRect.left || occluderRect.right < contentRect.right) {
            return;
        }
        if (occluderRect.top <= contentRect.top) {
            contentRect.top = Math.max(contentRect.top, occluderRect.bottom);
        } else {
            contentRect.bottom = Math.min(contentRect.bottom, occluderRect.top);
        }
    }

    public interface OnWindowFocusChangedListener {
        void onWindowFocusChanged(boolean hasWindowsFocus);
    }
}
