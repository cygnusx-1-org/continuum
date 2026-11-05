package ml.docilealligator.infinityforreddit.adapters;

import android.graphics.drawable.GradientDrawable;

import androidx.fragment.app.FragmentManager;

import ml.docilealligator.infinityforreddit.post.Post;

/**
 * What the compact rows need on top of {@link PostRowHost}. Like it, every value is read when asked
 * for: the compact thumbnail size, the layout and the long-press actions change while a feed is open.
 */
interface PostCompactHost extends PostRowHost {
    int archivedIconTint();

    int compactThumbnailBoxSizePx();

    int crosspostIconTint();

    int dividerColor();

    int filledCardViewBackgroundColor();

    int flairBackgroundColor();

    int flairTextColor();

    int lockedIconTint();

    int nsfwBackgroundColor();

    int nsfwTextColor();

    int postLayout();

    int postTypeTextColor();

    int readPostFilledCardViewBackgroundColor();

    int secondaryTextColor();

    int spoilerBackgroundColor();

    int spoilerTextColor();

    int stickiedPostIconTint();

    boolean longPressToHideToolbarInCompactLayout();

    boolean voteButtonsOnTheRight();

    String longPressPostMediaAction();

    String longPressPostNonMediaAreaAction();

    float scale();

    FragmentManager childFragmentManager();

    GradientDrawable createRoundedBackground(int color, float cornerRadiusDp);

    void openMedia(Post post, boolean peekMedia);
}
