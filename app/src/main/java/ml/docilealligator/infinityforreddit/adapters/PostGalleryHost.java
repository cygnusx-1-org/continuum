package ml.docilealligator.infinityforreddit.adapters;

import android.content.SharedPreferences;

import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.RequestManager;

import ml.docilealligator.infinityforreddit.post.Post;
import ml.docilealligator.infinityforreddit.SaveMemoryCenterInisdeDownsampleStrategy;

/**
 * What the gallery rows need on top of {@link PostRowHost}. Like it, every value is read when asked
 * for: the autoplay offset, whether video may play and the image count setting change while a feed
 * is open.
 */
interface PostGalleryHost extends PostRowHost {
    SharedPreferences sharedPreferences();

    RequestManager glide();

    SaveMemoryCenterInisdeDownsampleStrategy saveMemoryDownsampleStrategy();

    boolean hideImageCountInGallery();

    RecyclerView.RecycledViewPool galleryRecycledViewPool();

    boolean swipingBetweenTabsDisabled();

    void openMedia(Post post, int galleryItemIndex, boolean peekMedia);
}
