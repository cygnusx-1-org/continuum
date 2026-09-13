/*
 * Copyright (c) 2017 Nam Nguyen, nam@ene.im
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ml.docilealligator.infinityforreddit.videoautoplay;

import android.graphics.Point;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import androidx.annotation.FloatRange;
import androidx.annotation.NonNull;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import ml.docilealligator.infinityforreddit.videoautoplay.widget.Container;

/**
 * @author eneim | 5/31/17.
 */

public final class ToroUtil {

    @SuppressWarnings("unused")
    private static final String TAG = "ToroLib:Util";

    private ToroUtil() {
        throw new RuntimeException("Meh!");
    }

    /**
     * Get the ratio in range of 0.0 ~ 1.0 the visible area of a {@link ToroPlayer}'s playerView.
     *
     * @param player    the {@link ToroPlayer} need to investigate.
     * @param container the {@link ViewParent} that holds the {@link ToroPlayer}. If {@code null}
     *                  then this method must returns 0.0f;
     * @return the value in range of 0.0 ~ 1.0 of the visible area.
     */
    @FloatRange(from = 0.0, to = 1.0) //
    public static float visibleAreaOffset(@NonNull ToroPlayer player, ViewParent container) {
        if (container == null) return 0.0f;
        return visibleAreaOffset(player.getPlayerView(), container);
    }

    /**
     * Get the ratio in range of 0.0 ~ 1.0 of {@code view} that is visible inside {@code container}.
     *
     * <p>Measured against the part of the container the reader can actually see, not the window.
     * {@link View#getGlobalVisibleRect} clips only to the window, so on its own it counts pixels
     * that are inside the window but covered: a feed drawn with {@code clipToPadding="false"}
     * spills into its own inset padding, and its host may paint a bar over the result. Both are
     * taken off here -- the padding directly, and whatever covers the list through
     * {@link Container#clipToViewport}. Without that the visible-area setting meant "visible in the
     * window" rather than "visible in the feed", and a card whose last stretch sat behind a bar
     * reported itself fully visible.
     *
     * @param view      the view to measure.
     * @param container the {@link ViewParent} that holds it; a null one, or one that is not a
     *                  {@link View}, gives 0.0f since there is no viewport to measure against.
     */
    @FloatRange(from = 0.0, to = 1.0) //
    public static float visibleAreaOffset(@NonNull View view, ViewParent container) {
        if (!(container instanceof View)) return 0.0f;

        Rect drawRect = new Rect();
        view.getDrawingRect(drawRect);
        int drawArea = drawRect.width() * drawRect.height();
        if (drawArea <= 0) return 0.0f;

        Rect viewRect = new Rect();
        if (!view.getGlobalVisibleRect(viewRect, new Point())) return 0.0f;

        View containerView = (View) container;
        Rect contentRect = new Rect();
        if (!containerView.getGlobalVisibleRect(contentRect, new Point())) return 0.0f;

        // Take the tighter of what the window shows of the container and the container's own
        // content box. Padding is in the container's coordinates, so it is applied to the
        // container's real edges -- deriving it from the visible rect would over-inset a container
        // that is itself partly outside the window.
        int[] location = new int[2];
        containerView.getLocationInWindow(location);
        contentRect.left = Math.max(contentRect.left, location[0] + containerView.getPaddingLeft());
        contentRect.top = Math.max(contentRect.top, location[1] + containerView.getPaddingTop());
        contentRect.right = Math.min(contentRect.right,
                location[0] + containerView.getWidth() - containerView.getPaddingRight());
        contentRect.bottom = Math.min(contentRect.bottom,
                location[1] + containerView.getHeight() - containerView.getPaddingBottom());

        // Finally let the container remove whatever its host paints over it.
        if (containerView instanceof Container) {
            ((Container) containerView).clipToViewport(contentRect);
        }

        if (!viewRect.intersect(contentRect)) return 0.0f;
        return (viewRect.width() * viewRect.height()) / (float) drawArea;
    }

    /**
     * Ensures that an object reference passed as a parameter to the calling
     * method is not null.
     *
     * @param reference an object reference
     * @return the non-null reference that was validated
     * @throws NullPointerException if {@code reference} is null
     */
    public static @NonNull
    <T> T checkNotNull(final T reference) {
        if (reference == null) {
            throw new NullPointerException();
        }
        return reference;
    }

    /**
     * Ensures that an object reference passed as a parameter to the calling
     * method is not null.
     *
     * @param reference    an object reference
     * @param errorMessage the exception message to use if the check fails; will
     *                     be converted to a string using {@link String#valueOf(Object)}
     * @return the non-null reference that was validated
     * @throws NullPointerException if {@code reference} is null
     */
    public static @NonNull
    <T> T checkNotNull(final T reference, final Object errorMessage) {
        if (reference == null) {
            throw new NullPointerException(String.valueOf(errorMessage));
        }
        return reference;
    }

    @SuppressWarnings("unchecked")  //
    public static void wrapParamBehavior(@NonNull final Container container,
                                         final Container.BehaviorCallback callback) {
        container.setBehaviorCallback(callback);
        ViewGroup.LayoutParams params = container.getLayoutParams();
        if (params instanceof CoordinatorLayout.LayoutParams) {
            CoordinatorLayout.Behavior temp = ((CoordinatorLayout.LayoutParams) params).getBehavior();
            if (temp != null) {
                ((CoordinatorLayout.LayoutParams) params).setBehavior(new Container.Behavior(temp));
            }
        }
    }
}
