package ml.docilealligator.infinityforreddit.adapters;

import android.content.res.ColorStateList;
import android.graphics.PorterDuff;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.button.MaterialButton;
import com.libRG.CustomTextView;
import ml.docilealligator.infinityforreddit.activities.BaseActivity;
import ml.docilealligator.infinityforreddit.bottomsheetfragments.PostOptionsBottomSheetFragment;
import ml.docilealligator.infinityforreddit.customviews.AspectRatioGifImageView;
import ml.docilealligator.infinityforreddit.customviews.LinearLayoutManagerBugFixed;
import ml.docilealligator.infinityforreddit.post.MarkPostAsReadInterface;
import ml.docilealligator.infinityforreddit.post.Post;
import ml.docilealligator.infinityforreddit.utils.SharedPreferencesUtils;

// Fields such as post are assigned at bind time, not by the constructor.
@SuppressWarnings("NullAway.Init")
public abstract class PostBaseViewHolder extends PostViewHolder {
    TextView subredditTextView;
    TextView userTextView;
    @Nullable
    Post.Preview preview;

    private final PostRowHost host;
    private final BaseActivity mActivity;

    PostBaseViewHolder(@NonNull View itemView, PostRowHost host) {
        super(itemView, host);
        this.host = host;
        this.mActivity = host.activity();
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
                    MaterialButton shareButton) {
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
                upvoteButton,
                scoreTextView,
                downvoteButton,
                commentsCountButton,
                saveButton,
                shareButton);

        this.subredditTextView = subredditTextView;
        this.userTextView = userTextView;

        if (host.voteButtonsOnTheRight() && saveButton != null && shareButton != null && commentsCountButton != null) {
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

        setItemViewBackgroundColor(false);

        if (mActivity.typeface != null) {
            subredditTextView.setTypeface(mActivity.typeface);
            userTextView.setTypeface(mActivity.typeface);
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
            upvoteButton.setTypeface(mActivity.typeface);
            commentsCountButton.setTypeface(mActivity.typeface);
        }
        if (mActivity.titleTypeface != null) {
            titleTextView.setTypeface(mActivity.titleTypeface);
        }

        subredditTextView.setTextColor(host.subredditColor());
        userTextView.setTextColor(host.usernameColor());
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

        upvoteButton.setIconTint(ColorStateList.valueOf(host.postIconAndInfoColor()));
        scoreTextView.setTextColor(host.postIconAndInfoColor());
        downvoteButton.setIconTint(ColorStateList.valueOf(host.postIconAndInfoColor()));
        commentsCountButton.setTextColor(host.postIconAndInfoColor());
        commentsCountButton.setIconTint(ColorStateList.valueOf(host.postIconAndInfoColor()));
        saveButton.setIconTint(ColorStateList.valueOf(host.postIconAndInfoColor()));
        shareButton.setIconTint(ColorStateList.valueOf(host.postIconAndInfoColor()));

        itemView.setOnLongClickListener(v -> {
            Post post = host.getItem(getBindingAdapterPosition());
            if (post == null || host.longPressPostNonMediaAreaAction().equals(SharedPreferencesUtils.LONG_PRESS_POST_VALUE_NONE)) {
                return false;
            }

            if (host.longPressPostNonMediaAreaAction().equals(SharedPreferencesUtils.LONG_PRESS_POST_VALUE_SHOW_POST_OPTIONS)) {
                if (post.getPostType() == Post.GALLERY_TYPE && this instanceof PostBaseGalleryTypeViewHolder) {
                    RecyclerView.LayoutManager layoutManager = ((PostBaseGalleryTypeViewHolder) this).galleryRecyclerView.getLayoutManager();
                    if (layoutManager instanceof LinearLayoutManagerBugFixed) {
                        showPostOptions(((LinearLayoutManagerBugFixed) layoutManager).findFirstVisibleItemPosition());
                    } else {
                        showPostOptions(-1);
                    }
                } else {
                    showPostOptions(-1);
                }
            } else if (host.longPressPostNonMediaAreaAction().equals(SharedPreferencesUtils.LONG_PRESS_POST_VALUE_PREVIEW_IN_FULLSCREEN)) {
                markPostRead(post, true);
                host.openMedia(post, true);
            }
            return true;
        });
    }

    void showPostOptions(int currentGalleryItemPosition) {
        PostOptionsBottomSheetFragment postOptionsBottomSheetFragment;
        if (post.getPostType() == Post.GALLERY_TYPE && this instanceof PostBaseGalleryTypeViewHolder && currentGalleryItemPosition >= 0) {
            postOptionsBottomSheetFragment = PostOptionsBottomSheetFragment.newInstance(post,
                    getBindingAdapterPosition(), currentGalleryItemPosition, true);
        } else {
            postOptionsBottomSheetFragment = PostOptionsBottomSheetFragment.newInstance(post, getBindingAdapterPosition(), true);
        }
        postOptionsBottomSheetFragment.show(host.childFragmentManager(), postOptionsBottomSheetFragment.getTag());
    }

    @Override
    void markPostRead(Post post, boolean changePostItemColor) {
        if (!host.handleReadPost()) {
            return;
        }

        if (!post.isRead() && host.markPostsAsRead()) {
            post.markAsRead();
            if (changePostItemColor) {
                setItemViewBackgroundColor(true);
                titleTextView.setTextColor(host.readPostTitleColor());
                if (this instanceof PostTextTypeViewHolder) {
                    ((PostTextTypeViewHolder) this).contentTextView.setTextColor(host.readPostContentColor());
                }
            }
            if (mActivity != null && mActivity instanceof MarkPostAsReadInterface) {
                ((MarkPostAsReadInterface) mActivity).markPostAsRead(post);
            }
        }
    }
}
