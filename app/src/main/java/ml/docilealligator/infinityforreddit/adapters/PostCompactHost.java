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

    int cardViewBackgroundColor();

    int colorAccent();

    int compactThumbnailBoxSizePx();

    int crosspostIconTint();

    int dividerColor();

    int filledCardViewBackgroundColor();

    int flairBackgroundColor();

    int flairTextColor();

    int lockedIconTint();

    int mediaIndicatorBackgroundColor();

    int mediaIndicatorIconTint();

    int nsfwBackgroundColor();

    int nsfwTextColor();

    int noPreviewPostTypeBackgroundColor();

    int noPreviewPostTypeIconTint();

    int postLayout();

    int postTypeTextColor();

    int readPostCardViewBackgroundColor();

    int readPostFilledCardViewBackgroundColor();

    int secondaryTextColor();

    int spoilerBackgroundColor();

    int spoilerTextColor();

    int stickiedPostIconTint();

    boolean longPressToHideToolbarInCompactLayout();

    boolean markPostsAsRead();

    boolean voteButtonsOnTheRight();

    String longPressPostMediaAction();

    String longPressPostNonMediaAreaAction();

    float scale();

    FragmentManager childFragmentManager();

    GradientDrawable createRoundedBackground(int color, float cornerRadiusDp);

    void openMedia(Post post);

    void openMedia(Post post, boolean peekMedia);
}
