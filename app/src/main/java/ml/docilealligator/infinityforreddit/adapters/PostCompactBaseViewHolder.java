package ml.docilealligator.infinityforreddit.adapters;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.loadingindicator.LoadingIndicator;
import com.libRG.CustomTextView;
import ml.docilealligator.infinityforreddit.R;
import ml.docilealligator.infinityforreddit.activities.BaseActivity;
import ml.docilealligator.infinityforreddit.activities.ViewUserDetailActivity;
import ml.docilealligator.infinityforreddit.bottomsheetfragments.PostOptionsBottomSheetFragment;
import ml.docilealligator.infinityforreddit.customviews.AspectRatioGifImageView;
import ml.docilealligator.infinityforreddit.customviews.BadgedNameTextView;
import ml.docilealligator.infinityforreddit.customviews.PostTypeIndicatorView;
import ml.docilealligator.infinityforreddit.post.MarkPostAsReadInterface;
import ml.docilealligator.infinityforreddit.post.Post;
import ml.docilealligator.infinityforreddit.utils.SharedPreferencesUtils;
import ml.docilealligator.infinityforreddit.utils.Utils;

// Views are assigned by setBaseView, not by the constructor.
@SuppressWarnings("NullAway.Init")
public class PostCompactBaseViewHolder extends PostViewHolder {
    BadgedNameTextView nameTextView;
    BadgedNameTextView usernameTextView;
    @Nullable TextView linkTextView;
    RelativeLayout relativeLayout;
    LoadingIndicator loadingIndicator;
    ImageView imageView;
    ImageView playButtonImageView;
    FrameLayout noPreviewPostImageFrameLayout;
    ImageView noPreviewPostImageView;
    @Nullable ConstraintLayout bottomConstraintLayout;
    @Nullable PostTypeIndicatorView postTypeIndicatorView;
    @Nullable PostTypeIndicatorView noPreviewPostTypeIndicatorView;
    View divider;
    RequestListener<Drawable> requestListener;
    GradientDrawable itemViewBackground;
    Drawable thumbnailRoundedEdgeBackground;

    private final PostCompactHost host;
    private final BaseActivity mActivity;

    PostCompactBaseViewHolder(View itemView, PostCompactHost host) {
        super(itemView, host);
        this.host = host;
        this.mActivity = host.activity();
    }

    void setupUsernameView(BadgedNameTextView usernameTextView) {
        this.usernameTextView = usernameTextView;

        if (mActivity.typeface != null) {
            usernameTextView.setTypeface(mActivity.typeface);
        }

        usernameTextView.setOnClickListener(view -> {
            if (!host.canStartActivity()) {
                return;
            }
            int position = getBindingAdapterPosition();
            if (position < 0) {
                return;
            }
            Post post = host.getItem(position);
            if (post == null || post.isAuthorDeleted()) {
                return;
            }
            host.setCanStartActivity(false);
            Intent intent = new Intent(mActivity, ViewUserDetailActivity.class);
            intent.putExtra(ViewUserDetailActivity.EXTRA_USER_NAME_KEY, post.getAuthor());
            mActivity.startActivity(intent);
        });
    }

    void setBaseView(AspectRatioGifImageView iconGifImageView,
                     BadgedNameTextView nameTextView,
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
                     @Nullable TextView linkTextView,
                     RelativeLayout relativeLayout,
                     LoadingIndicator loadingIndicator,
                     ImageView imageView,
                     ImageView playButtonImageView,
                     FrameLayout noPreviewLinkImageFrameLayout,
                     ImageView noPreviewLinkImageView,
                     @Nullable ConstraintLayout bottomConstraintLayout,
                     MaterialButton upvoteButton,
                     TextView scoreTextView,
                     MaterialButton downvoteButton,
                     @Nullable MaterialButton commentsCountButton,
                     @Nullable MaterialButton saveButton,
                     @Nullable MaterialButton shareButton,
                     View divider) {
        super.setBaseView(iconGifImageView,
                nameTextView,
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
                upvoteButton,
                scoreTextView,
                downvoteButton,
                commentsCountButton,
                saveButton,
                shareButton);

        this.nameTextView = nameTextView;
        this.linkTextView = linkTextView;
        this.relativeLayout = relativeLayout;
        this.loadingIndicator = loadingIndicator;
        this.imageView = imageView;
        this.playButtonImageView = playButtonImageView;
        this.noPreviewPostImageFrameLayout = noPreviewLinkImageFrameLayout;
        this.noPreviewPostImageView = noPreviewLinkImageView;
        this.bottomConstraintLayout = bottomConstraintLayout;
        this.divider = divider;

        // The layouts inflate at @dimen/post_compact_thumbnail_size; the preference overrides it
        // here so all six compact variants follow one setting. Both boxes are sized: the preview
        // wrapper and the no-preview link fallback, exactly one of which is ever shown.
        ViewGroup.LayoutParams imageParams = relativeLayout.getLayoutParams();
        imageParams.width = host.compactThumbnailBoxSizePx();
        imageParams.height = host.compactThumbnailBoxSizePx();
        relativeLayout.setLayoutParams(imageParams);
        ViewGroup.LayoutParams noPreviewParams = noPreviewLinkImageView.getLayoutParams();
        noPreviewParams.width = host.compactThumbnailBoxSizePx();
        noPreviewParams.height = host.compactThumbnailBoxSizePx();
        noPreviewLinkImageView.setLayoutParams(noPreviewParams);

        if (host.voteButtonsOnTheRight() && saveButton != null && shareButton != null && commentsCountButton != null) {
            if (bottomConstraintLayout != null) {
                ConstraintSet constraintSet = new ConstraintSet();
                constraintSet.clone(bottomConstraintLayout);
                constraintSet.clear(upvoteButton.getId(), ConstraintSet.START);
                constraintSet.clear(scoreTextView.getId(), ConstraintSet.START);
                constraintSet.clear(downvoteButton.getId(), ConstraintSet.START);
                constraintSet.clear(saveButton.getId(), ConstraintSet.END);
                constraintSet.clear(shareButton.getId(), ConstraintSet.END);
                constraintSet.connect(upvoteButton.getId(), ConstraintSet.END, scoreTextView.getId(), ConstraintSet.START);
                constraintSet.connect(scoreTextView.getId(), ConstraintSet.END, downvoteButton.getId(), ConstraintSet.START);
                constraintSet.connect(downvoteButton.getId(), ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END);
                constraintSet.connect(commentsCountButton.getId(), ConstraintSet.START, saveButton.getId(), ConstraintSet.END);
                constraintSet.connect(commentsCountButton.getId(), ConstraintSet.END, upvoteButton.getId(), ConstraintSet.START);
                constraintSet.connect(saveButton.getId(), ConstraintSet.START, shareButton.getId(), ConstraintSet.END);
                constraintSet.connect(shareButton.getId(), ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START);
                constraintSet.setHorizontalBias(commentsCountButton.getId(), 0);
                constraintSet.applyTo(bottomConstraintLayout);
            }
        }

        if (((ViewGroup) itemView).getLayoutTransition() != null) {
            ((ViewGroup) itemView).getLayoutTransition().setAnimateParentHierarchy(false);
        }

        if (mActivity.typeface != null) {
            nameTextView.setTypeface(mActivity.typeface);
            postTimeTextView.setTypeface(mActivity.typeface);
            if (typeTextView != null) {
                typeTextView.setTypeface(mActivity.typeface);
            }
            if (spoilerTextView != null) {
                spoilerTextView.setTypeface(mActivity.typeface);
            }
            if (nsfwTextView != null) {
                nsfwTextView.setTypeface(mActivity.typeface);
            }
            if (flairTextView != null) {
                flairTextView.setTypeface(mActivity.typeface);
            }
            if (linkTextView != null) {
                linkTextView.setTypeface(mActivity.typeface);
            }
            upvoteButton.setTypeface(mActivity.typeface);
            if (commentsCountButton != null) {
                commentsCountButton.setTypeface(mActivity.typeface);
            }
        }
        if (mActivity.titleTypeface != null) {
            titleTextView.setTypeface(mActivity.titleTypeface);
        }

        applyCompactItemLayoutParams();
        applyCompactItemBackground(false);
        postTimeTextView.setTextColor(host.secondaryTextColor());
        titleTextView.setTextColor(host.postTitleColor());
        stickiedPostImageView.setColorFilter(host.stickiedPostIconTint(), PorterDuff.Mode.SRC_IN);
        if (typeTextView != null) {
            typeTextView.setTextColor(host.postTypeTextColor());
        }
        if (spoilerTextView != null) {
            spoilerTextView.setBackgroundColor(host.spoilerBackgroundColor());
            spoilerTextView.setBorderColor(host.spoilerBackgroundColor());
            spoilerTextView.setTextColor(host.spoilerTextColor());
        }
        if (nsfwTextView != null) {
            nsfwTextView.setBackgroundColor(host.nsfwBackgroundColor());
            nsfwTextView.setBorderColor(host.nsfwBackgroundColor());
            nsfwTextView.setTextColor(host.nsfwTextColor());
        }
        if (flairTextView != null) {
            flairTextView.setBackgroundColor(host.flairBackgroundColor());
            flairTextView.setBorderColor(host.flairBackgroundColor());
            flairTextView.setTextColor(host.flairTextColor());
        }
        if (archivedImageView != null) {
            archivedImageView.setColorFilter(host.archivedIconTint(), PorterDuff.Mode.SRC_IN);
        }
        if (lockedImageView != null) {
            lockedImageView.setColorFilter(host.lockedIconTint(), PorterDuff.Mode.SRC_IN);
        }
        if (crosspostImageView != null) {
            crosspostImageView.setColorFilter(host.crosspostIconTint(), PorterDuff.Mode.SRC_IN);
        }
        if (linkTextView != null) {
            linkTextView.setTextColor(host.secondaryTextColor());
        }
        playButtonImageView.setColorFilter(host.mediaIndicatorIconTint(), PorterDuff.Mode.SRC_IN);
        playButtonImageView.setBackgroundTintList(ColorStateList.valueOf(host.mediaIndicatorBackgroundColor()));
        loadingIndicator.setIndicatorColor(host.colorAccent());
        loadingIndicator.setVisibility(View.GONE);
        noPreviewLinkImageFrameLayout.setBackground(host.createRoundedBackground(
                host.noPreviewPostTypeBackgroundColor(),
                host.postLayout() == SharedPreferencesUtils.POST_LAYOUT_CARD ? 16 :
                        SharedPreferencesUtils.isCardTop(host.postLayout()) ? 12 : 8));
        noPreviewLinkImageView.setBackground(null);
        noPreviewLinkImageView.setColorFilter(host.noPreviewPostTypeIconTint(), android.graphics.PorterDuff.Mode.SRC_IN);
        upvoteButton.setIconTint(ColorStateList.valueOf(host.postIconAndInfoColor()));
        scoreTextView.setTextColor(host.postIconAndInfoColor());
        downvoteButton.setIconTint(ColorStateList.valueOf(host.postIconAndInfoColor()));
        if (commentsCountButton != null) {
            commentsCountButton.setTextColor(host.postIconAndInfoColor());
            commentsCountButton.setIconTint(ColorStateList.valueOf(host.postIconAndInfoColor()));
        }
        if (saveButton != null) {
            saveButton.setIconTint(ColorStateList.valueOf(host.postIconAndInfoColor()));
        }
        if (shareButton != null) {
            shareButton.setIconTint(ColorStateList.valueOf(host.postIconAndInfoColor()));
        }
        divider.setBackgroundColor(host.dividerColor());

        imageView.setClipToOutline(true);
        noPreviewLinkImageFrameLayout.setClipToOutline(true);

        itemView.setOnLongClickListener(view -> {
            if (bottomConstraintLayout != null && host.longPressToHideToolbarInCompactLayout()) {
                if (bottomConstraintLayout.getLayoutParams().height == 0) {
                    ViewGroup.LayoutParams params = bottomConstraintLayout.getLayoutParams();
                    params.height = LinearLayout.LayoutParams.WRAP_CONTENT;
                    bottomConstraintLayout.setLayoutParams(params);
                    host.callback().delayTransition();
                } else {
                    host.callback().delayTransition();
                    ViewGroup.LayoutParams params = bottomConstraintLayout.getLayoutParams();
                    params.height = 0;
                    bottomConstraintLayout.setLayoutParams(params);
                }
                return true;
            } else if (host.longPressPostNonMediaAreaAction().equals(SharedPreferencesUtils.LONG_PRESS_POST_VALUE_SHOW_POST_OPTIONS)) {
                showPostOptions();
                return true;
            } else if (host.longPressPostNonMediaAreaAction().equals(SharedPreferencesUtils.LONG_PRESS_POST_VALUE_PREVIEW_IN_FULLSCREEN)) {
                markPostRead(post, true);
                host.openMedia(post, true);
                return true;
            }
            return false;
        });

        imageView.setOnClickListener(view -> {
            int position = getBindingAdapterPosition();
            if (position < 0) {
                return;
            }
            Post post = host.getItem(position);
            if (post != null) {
                markPostRead(post, true);
                if (post.getPostType() == Post.TEXT_TYPE) {
                    // Preview thumbnail on a self/text post; open the post like tapping the row.
                    host.openViewPostDetailActivity(post, position);
                } else {
                    host.openMedia(post);
                }
            }
        });

        imageView.setOnLongClickListener(v -> {
            if (host.longPressPostMediaAction().equals(SharedPreferencesUtils.LONG_PRESS_POST_VALUE_SHOW_POST_OPTIONS)) {
                showPostOptions();
                return true;
            } else if (host.longPressPostMediaAction().equals(SharedPreferencesUtils.LONG_PRESS_POST_VALUE_PREVIEW_IN_FULLSCREEN)) {
                markPostRead(post, true);
                host.openMedia(post, true);
                return true;
            }
            return false;
        });

        noPreviewLinkImageFrameLayout.setOnClickListener(view -> {
            imageView.performClick();
        });

        noPreviewLinkImageFrameLayout.setOnLongClickListener(view -> imageView.performLongClick());

        requestListener = new RequestListener<>() {
            @Override
            public boolean onLoadFailed(@Nullable GlideException e, Object model, @NonNull Target<Drawable> target, boolean isFirstResource) {
                loadingIndicator.setVisibility(View.GONE);
                return false;
            }

            @Override
            public boolean onResourceReady(@NonNull Drawable resource, @NonNull Object model, Target<Drawable> target, @NonNull DataSource dataSource, boolean isFirstResource) {
                loadingIndicator.setVisibility(View.GONE);
                if (Utils.previewLikelyHasTransparentBackground(resource)) {
                    imageView.setBackgroundResource(R.drawable.transparent_image_backdrop);
                }
                return false;
            }
        };
    }

    void applyCompactItemLayoutParams() {
        ViewGroup.LayoutParams params = itemView.getLayoutParams();
        if (!(params instanceof ViewGroup.MarginLayoutParams)) {
            return;
        }

        int horizontalMargin = SharedPreferencesUtils.isCardTop(host.postLayout()) ? (int) (16 * host.scale()) : 0;
        int verticalMargin = SharedPreferencesUtils.isCardTop(host.postLayout()) ? (int) (8 * host.scale()) : 0;
        ViewGroup.MarginLayoutParams marginLayoutParams = (ViewGroup.MarginLayoutParams) params;

        if (marginLayoutParams.leftMargin == horizontalMargin
                && marginLayoutParams.rightMargin == horizontalMargin
                && marginLayoutParams.topMargin == verticalMargin
                && marginLayoutParams.bottomMargin == verticalMargin) {
            return;
        }

        marginLayoutParams.setMargins(horizontalMargin, verticalMargin, horizontalMargin, verticalMargin);
        itemView.setLayoutParams(marginLayoutParams);
    }

    void applyCompactItemBackground(boolean isReadPost) {
        if (host.postLayout() == SharedPreferencesUtils.POST_LAYOUT_CARD) {
            int backgroundColor = isReadPost ? host.readPostCardViewBackgroundColor() : host.cardViewBackgroundColor();
            setRoundedItemViewBackground(backgroundColor, 16);
        } else if (SharedPreferencesUtils.isCardTop(host.postLayout())) {
            int backgroundColor = isReadPost ? host.readPostFilledCardViewBackgroundColor() : host.filledCardViewBackgroundColor();
            setRoundedItemViewBackground(backgroundColor, 12);
        } else {
            itemView.setClipToOutline(false);
            int backgroundColor = isReadPost ? host.readPostCardViewBackgroundColor() : host.cardViewBackgroundColor();
            itemView.setBackgroundColor(backgroundColor);
        }
    }

    private void setRoundedItemViewBackground(int color, float cornerRadiusDp) {
        // Reuse a single GradientDrawable per holder and only swap its color, so we don't
        // allocate a new drawable on every bind/recycle while scrolling. The corner radius is
        // fixed for the lifetime of the holder because host.postLayout() never changes.
        if (itemViewBackground == null) {
            itemViewBackground = host.createRoundedBackground(color, cornerRadiusDp);
            itemView.setBackground(itemViewBackground);
            itemView.setClipToOutline(true);
        } else {
            itemViewBackground.setColor(color);
        }
    }

    void showPostOptions() {
        Post post = host.getItem(getBindingAdapterPosition());
        if (post == null) {
            return;
        }

        PostOptionsBottomSheetFragment postOptionsBottomSheetFragment;
        postOptionsBottomSheetFragment = PostOptionsBottomSheetFragment.newInstance(post, getBindingAdapterPosition(), true);
        postOptionsBottomSheetFragment.show(host.childFragmentManager(), postOptionsBottomSheetFragment.getTag());
    }

    @Override
    void setItemViewBackgroundColor(boolean isReadPost) {
        applyCompactItemBackground(isReadPost);
    }

    @Override
    void markPostRead(Post post, boolean changePostItemColor) {
        if (!host.handleReadPost()) {
            return;
        }

        if (!post.isRead() && host.markPostsAsRead()) {
            post.markAsRead();
            if (changePostItemColor) {
                applyCompactItemBackground(true);
                titleTextView.setTextColor(host.readPostTitleColor());
            }
            if (mActivity != null && mActivity instanceof MarkPostAsReadInterface) {
                ((MarkPostAsReadInterface) mActivity).markPostAsRead(post);
            }
        }
    }
}
