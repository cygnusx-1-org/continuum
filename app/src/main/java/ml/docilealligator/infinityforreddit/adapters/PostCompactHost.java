package ml.docilealligator.infinityforreddit.adapters;

import android.graphics.drawable.GradientDrawable;

import ml.docilealligator.infinityforreddit.post.Post;

/**
 * What the compact rows need on top of {@link PostRowHost}. Like it, every value is read when asked
 * for: the compact thumbnail size, the layout and the long-press actions change while a feed is open.
 */
interface PostCompactHost extends PostRowHost {
    int compactThumbnailBoxSizePx();

    int dividerColor();

    int postLayout();

    boolean longPressToHideToolbarInCompactLayout();

    float scale();

    GradientDrawable createRoundedBackground(int color, float cornerRadiusDp);

}
