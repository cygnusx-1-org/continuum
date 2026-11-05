package ml.docilealligator.infinityforreddit.adapters;

import android.content.res.ColorStateList;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import ml.docilealligator.infinityforreddit.R;
import ml.docilealligator.infinityforreddit.activities.BaseActivity;
import ml.docilealligator.infinityforreddit.databinding.ItemPostGalleryBinding;
import ml.docilealligator.infinityforreddit.post.MarkPostAsReadInterface;
import ml.docilealligator.infinityforreddit.post.Post;
import ml.docilealligator.infinityforreddit.utils.SharedPreferencesUtils;
import ml.docilealligator.infinityforreddit.utils.Utils;

// Fields such as post are assigned at bind time, not by the constructor.
@SuppressWarnings("NullAway.Init")
class PostGalleryViewHolder extends RecyclerView.ViewHolder {
    ItemPostGalleryBinding binding;
    RequestListener<Drawable> requestListener;
    Post post;
    @Nullable
    Post.Preview preview;
    /** Whether this card is currently showing the animation rather than the still. */
    boolean animatingGif;

    private final PostGalleryHost host;
    private final BaseActivity mActivity;

    public PostGalleryViewHolder(@NonNull ItemPostGalleryBinding binding, PostGalleryHost host) {
        super(binding.getRoot());
        this.binding = binding;
        this.host = host;
        this.mActivity = host.activity();

        if (mActivity.typeface != null) {
            binding.loadImageErrorTextViewItemGallery.setTypeface(mActivity.typeface);
        }
        if (mActivity.titleTypeface != null) {
            binding.titleTextViewItemPostGallery.setTypeface(mActivity.titleTypeface);
        }
        itemView.setBackgroundTintList(ColorStateList.valueOf(host.cardViewBackgroundColor()));
        binding.titleTextViewItemPostGallery.setTextColor(host.postTitleColor());
        binding.progressBarItemPostGallery.setIndicatorColor(host.colorAccent());
        binding.progressBarItemPostGallery.setVisibility(View.GONE);
        binding.imageViewNoPreviewItemPostGallery.setBackgroundColor(host.noPreviewPostTypeBackgroundColor());
        binding.imageViewNoPreviewItemPostGallery.setColorFilter(host.noPreviewPostTypeIconTint(), android.graphics.PorterDuff.Mode.SRC_IN);
        binding.videoOrGifIndicatorImageViewItemPostGallery.setColorFilter(host.mediaIndicatorIconTint(), PorterDuff.Mode.SRC_IN);
        binding.videoOrGifIndicatorImageViewItemPostGallery.setBackgroundTintList(ColorStateList.valueOf(host.mediaIndicatorBackgroundColor()));
        binding.loadImageErrorTextViewItemGallery.setTextColor(host.primaryTextColor());

        itemView.setOnClickListener(view -> {
            int position = getBindingAdapterPosition();
            if (position >= 0 && host.canStartActivity()) {
                Post post = host.getItem(position);
                if (post != null) {
                    markPostRead(post, true);

                    if (post.getPostType() == Post.TEXT_TYPE || !host.sharedPreferences().getBoolean(SharedPreferencesUtils.CLICK_TO_SHOW_MEDIA_IN_GALLERY_LAYOUT, true)) {
                        host.openViewPostDetailActivity(post, getBindingAdapterPosition());
                    } else {
                        host.openMedia(post);
                    }
                }
            }
        });

        itemView.setOnLongClickListener(view -> {
            int position = getBindingAdapterPosition();
            if (position >= 0 && host.canStartActivity()) {
                Post post = host.getItem(position);
                if (post != null) {
                    markPostRead(post, true);

                    if (post.getPostType() == Post.TEXT_TYPE || host.sharedPreferences().getBoolean(SharedPreferencesUtils.CLICK_TO_SHOW_MEDIA_IN_GALLERY_LAYOUT, true)) {
                        host.openViewPostDetailActivity(post, getBindingAdapterPosition());
                    } else {
                        host.openMedia(post);
                    }
                }
            }

            return true;
        });

        binding.imageViewNoPreviewItemPostGallery.setOnClickListener(view -> {
            itemView.performClick();
        });

        requestListener = new RequestListener<>() {
            @Override
            public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
                binding.progressBarItemPostGallery.setVisibility(View.GONE);
                return false;
            }

            @Override
            public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
                binding.loadImageErrorTextViewItemGallery.setVisibility(View.GONE);
                binding.progressBarItemPostGallery.setVisibility(View.GONE);
                if (Utils.previewLikelyHasTransparentBackground(resource)) {
                    binding.imageViewItemPostGallery.setBackgroundResource(R.drawable.transparent_image_backdrop);
                }
                return false;
            }
        };
    }

    void markPostRead(Post post, boolean changePostItemColor) {
        if (!host.handleReadPost()) {
            return;
        }

        if (!post.isRead() && host.markPostsAsRead()) {
            post.markAsRead();
            if (changePostItemColor) {
                itemView.setBackgroundTintList(ColorStateList.valueOf(host.readPostCardViewBackgroundColor()));
                binding.titleTextViewItemPostGallery.setTextColor(host.readPostTitleColor());
            }
            if (mActivity != null && mActivity instanceof MarkPostAsReadInterface) {
                ((MarkPostAsReadInterface) mActivity).markPostAsRead(post);
            }
        }
    }
}
