package ml.docilealligator.infinityforreddit.adapters;

import android.content.res.ColorStateList;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.loadingindicator.LoadingIndicator;
import com.libRG.CustomTextView;
import ml.docilealligator.infinityforreddit.R;
import ml.docilealligator.infinityforreddit.activities.BaseActivity;
import ml.docilealligator.infinityforreddit.customviews.AspectRatioGifImageView;
import ml.docilealligator.infinityforreddit.databinding.ItemPostWithPreviewBinding;
import ml.docilealligator.infinityforreddit.post.Post;
import ml.docilealligator.infinityforreddit.utils.SharedPreferencesUtils;
import ml.docilealligator.infinityforreddit.utils.Utils;

// Fields such as the views are assigned by setBaseView, not by the constructor itself.
@SuppressWarnings("NullAway.Init")
class PostWithPreviewTypeViewHolder extends PostBaseViewHolder {
    TextView linkTextView;
    ImageView imageViewNoPreviewGallery;
    LoadingIndicator loadingIndicator;
    ImageView videoOrGifIndicator;
    TextView loadImageErrorTextView;
    @Nullable
    FrameLayout imageWrapperFrameLayout;
    AspectRatioGifImageView imageView;
    // Selftext snippet, shown only for text posts that also carry a preview image: the words
    // before the image, with contentTextViewBelowPreview holding the ones after it. A preview
    // Reddit made out of a link in the body has no words before it -- it is not in the body --
    // so that snippet goes in the slot below and this one stays hidden.
    @Nullable
    TextView contentTextView;
    // The words that follow the image, below the preview. Card 1 only: cards 2 and 3 draw the
    // preview at the top of the card, above the title, so there is nothing "before the image"
    // for them to hold and their one slot takes the whole snippet.
    @Nullable
    TextView contentTextViewBelowPreview;
    RequestListener<Drawable> glideRequestListener;
    /** Whether this card is currently showing the animation rather than the still. */
    boolean animatingGif;

    private final PostPreviewHost host;
    private final BaseActivity mActivity;

    PostWithPreviewTypeViewHolder(@NonNull View itemView, PostPreviewHost host) {
        super(itemView, host);
        this.host = host;
        this.mActivity = host.activity();
    }

    PostWithPreviewTypeViewHolder(@NonNull ItemPostWithPreviewBinding binding, PostPreviewHost host) {
        super(binding.getRoot(), host);
        this.host = host;
        this.mActivity = host.activity();
        setBaseView(
                binding.iconGifImageViewItemPostWithPreview,
                binding.subredditNameTextViewItemPostWithPreview,
                binding.userTextViewItemPostWithPreview,
                binding.stickiedPostImageViewItemPostWithPreview,
                binding.postTimeTextViewItemPostWithPreview,
                binding.titleTextViewItemPostWithPreview,
                binding.typeTextViewItemPostWithPreview,
                binding.archivedImageViewItemPostWithPreview,
                binding.lockedImageViewItemPostWithPreview,
                binding.crosspostImageViewItemPostWithPreview,
                binding.nsfwTextViewItemPostWithPreview,
                binding.spoilerCustomTextViewItemPostWithPreview,
                binding.flairCustomTextViewItemPostWithPreview,
                binding.bottomConstraintLayoutItemPostWithPreview,
                binding.upvoteButtonItemPostWithPreview,
                binding.scoreTextViewItemPostWithPreview,
                binding.downvoteButtonItemPostWithPreview,
                binding.commentsCountButtonItemPostWithPreview,
                binding.saveButtonItemPostWithPreview,
                binding.shareButtonItemPostWithPreview,
                binding.linkTextViewItemPostWithPreview,
                binding.imageViewNoPreviewGalleryItemPostWithPreview,
                binding.progressBarItemPostWithPreview,
                binding.videoOrGifIndicatorImageViewItemPostWithPreview,
                binding.loadImageErrorTextViewItemPostWithPreview,
                binding.imageWrapperRelativeLayoutItemPostWithPreview,
                binding.imageViewItemPostWithPreview);
        contentTextView = binding.contentTextViewItemPostWithPreview;
        contentTextViewBelowPreview = binding.contentTextViewBelowPreviewItemPostWithPreview;
    }

    void setBaseView(AspectRatioGifImageView iconGifImageView,
                     TextView subredditTextView,
                     TextView userTextView,
                     ImageView stickiedPostImageView,
                     TextView postTimeTextView,
                     TextView titleTextView,
                     @Nullable CustomTextView typeTextView,
                     @Nullable ImageView archivedImageView,
                     @Nullable ImageView lockedImageView,
                     @Nullable ImageView crosspostImageView,
                     @Nullable CustomTextView nsfwTextView,
                     @Nullable CustomTextView spoilerTextView,
                     @Nullable CustomTextView flairTextView,
                     ConstraintLayout bottomConstraintLayout,
                     MaterialButton upvoteButton,
                     TextView scoreTextView,
                     MaterialButton downvoteButton,
                     MaterialButton commentsCountButton,
                     MaterialButton saveButton,
                     MaterialButton shareButton,
                     TextView linkTextView,
                     ImageView imageViewNoPreviewGallery,
                     LoadingIndicator loadingIndicator,
                     ImageView videoOrGifIndicator,
                     TextView loadImageErrorTextView,
                     @Nullable FrameLayout imageWrapperFrameLayout,
                     AspectRatioGifImageView imageView) {
        super.setBaseView(
                iconGifImageView,
                subredditTextView,
                userTextView,
                stickiedPostImageView,
                postTimeTextView,
                titleTextView,
                typeTextView,
                archivedImageView,
                lockedImageView,
                crosspostImageView,
                nsfwTextView,
                spoilerTextView,
                flairTextView,
                bottomConstraintLayout,
                upvoteButton,
                scoreTextView,
                downvoteButton,
                commentsCountButton,
                saveButton,
                shareButton);

        this.linkTextView = linkTextView;
        this.imageViewNoPreviewGallery = imageViewNoPreviewGallery;
        this.loadingIndicator = loadingIndicator;
        this.videoOrGifIndicator = videoOrGifIndicator;
        this.loadImageErrorTextView = loadImageErrorTextView;
        this.imageWrapperFrameLayout = imageWrapperFrameLayout;
        this.imageView = imageView;

        if (mActivity.typeface != null) {
            linkTextView.setTypeface(mActivity.typeface);
            loadImageErrorTextView.setTypeface(mActivity.typeface);
        }
        linkTextView.setTextColor(host.secondaryTextColor());
        imageViewNoPreviewGallery.setBackgroundColor(host.noPreviewPostTypeBackgroundColor());
        imageViewNoPreviewGallery.setColorFilter(host.noPreviewPostTypeIconTint(), android.graphics.PorterDuff.Mode.SRC_IN);
        loadingIndicator.setIndicatorColor(host.colorAccent());
        videoOrGifIndicator.setColorFilter(host.mediaIndicatorIconTint(), PorterDuff.Mode.SRC_IN);
        videoOrGifIndicator.setBackgroundTintList(ColorStateList.valueOf(host.mediaIndicatorBackgroundColor()));
        loadImageErrorTextView.setTextColor(host.primaryTextColor());

        imageView.setOnClickListener(view -> {
            int position = getBindingAdapterPosition();
            if (position < 0) {
                return;
            }
            Post post = host.getItem(position);
            if (post != null) {
                markPostRead(post, true);
                if (post.getPostType() == Post.TEXT_TYPE) {
                    // Preview image on a self/text post; open the post like tapping the card.
                    host.openViewPostDetailActivity(post, position);
                } else {
                    host.openMedia(post);
                }
            }
        });

        imageView.setOnLongClickListener(view -> {
            if (host.longPressPostMediaAction().equals(SharedPreferencesUtils.LONG_PRESS_POST_VALUE_SHOW_POST_OPTIONS)) {
                showPostOptions(-1);
                return true;
            } else if (host.longPressPostMediaAction().equals(SharedPreferencesUtils.LONG_PRESS_POST_VALUE_PREVIEW_IN_FULLSCREEN)) {
                markPostRead(post, true);
                host.openMedia(post, true);
                return true;
            }
            return false;
        });

        imageViewNoPreviewGallery.setOnClickListener(view -> {
            imageView.performClick();
        });

        imageViewNoPreviewGallery.setOnLongClickListener(view -> imageView.performLongClick());

        glideRequestListener = new RequestListener<>() {
            @Override
            public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
                loadingIndicator.setVisibility(View.GONE);
                // A link post's preview is supplementary, and reddit-link previews can be
                // unavailable. When one fails, collapse the oversized ratio-reserved box to the
                // compact no-preview link tile instead of leaving a giant black rectangle. Media
                // posts keep the box (the preview is the content, and failures there are usually
                // transient), preserving the previous behaviour for them.
                int position = getBindingAdapterPosition();
                Post post = position >= 0 ? host.getItem(position) : null;
                if (post != null && (post.getPostType() == Post.LINK_TYPE || post.getPostType() == Post.NO_PREVIEW_LINK_TYPE)) {
                    if (imageWrapperFrameLayout != null) {
                        imageWrapperFrameLayout.setVisibility(View.GONE);
                    }
                    imageView.setVisibility(View.GONE);
                    imageViewNoPreviewGallery.setImageResource(R.drawable.ic_link_day_night_24dp);
                    imageViewNoPreviewGallery.setVisibility(View.VISIBLE);
                }
                return false;
            }

            @Override
            public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
                loadImageErrorTextView.setVisibility(View.GONE);
                loadingIndicator.setVisibility(View.GONE);
                // Re-correct the reserved aspect ratio from the actual drawable: the preview
                // metadata ratio can differ from the bitmap Reddit actually serves, which would
                // otherwise letterbox the image with black against the card background. Only do
                // this when the preview was sized from its own dimensions; a square
                // fixed-height preview is deliberately not the drawable's shape, and
                // center-crops to fill instead (or, for an autoplaying gif, sits letterboxed in
                // it -- see setSquarePreview).
                boolean ratioMode = !host.fixedHeightPreviewInCard() && preview != null
                        && preview.getPreviewWidth() > 0 && preview.getPreviewHeight() > 0;
                if (ratioMode && resource.getIntrinsicWidth() > 0 && resource.getIntrinsicHeight() > 0) {
                    imageView.setRatio((float) resource.getIntrinsicHeight() / resource.getIntrinsicWidth());
                }
                if (Utils.previewLikelyHasTransparentBackground(resource)) {
                    imageView.setBackgroundResource(R.drawable.transparent_image_backdrop);
                }
                return false;
            }
        };
    }

    @Override
    void setItemViewBackgroundColor(boolean isReadPost) {
        itemView.setBackgroundTintList(ColorStateList.valueOf(isReadPost ? rowHost().readPostCardViewBackgroundColor() : rowHost().cardViewBackgroundColor()));
    }
}
