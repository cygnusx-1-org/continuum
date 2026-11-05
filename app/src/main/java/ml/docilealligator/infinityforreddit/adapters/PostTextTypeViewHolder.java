package ml.docilealligator.infinityforreddit.adapters;

import android.content.res.ColorStateList;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import com.google.android.material.button.MaterialButton;
import com.libRG.CustomTextView;
import ml.docilealligator.infinityforreddit.activities.BaseActivity;
import ml.docilealligator.infinityforreddit.customviews.AspectRatioGifImageView;
import ml.docilealligator.infinityforreddit.databinding.ItemPostTextBinding;

// contentTextView is assigned by setBaseView, not by the constructor.
@SuppressWarnings("NullAway.Init")
class PostTextTypeViewHolder extends PostBaseViewHolder {
    TextView contentTextView;

    private final PostRowHost host;
    private final BaseActivity mActivity;

    PostTextTypeViewHolder(@NonNull View itemView, PostRowHost host) {
        super(itemView, host);
        this.host = host;
        this.mActivity = host.activity();
    }

    PostTextTypeViewHolder(@NonNull ItemPostTextBinding binding, PostRowHost host) {
        super(binding.getRoot(), host);
        this.host = host;
        this.mActivity = host.activity();
        setBaseView(
                binding.iconGifImageViewItemPostTextType,
                binding.subredditNameTextViewItemPostTextType,
                binding.userTextViewItemPostTextType,
                binding.stickiedPostImageViewItemPostTextType,
                binding.postTimeTextViewItemPostTextType,
                binding.titleTextViewItemPostTextType,
                binding.typeTextViewItemPostTextType,
                binding.archivedImageViewItemPostTextType,
                binding.lockedImageViewItemPostTextType,
                binding.crosspostImageViewItemPostTextType,
                binding.nsfwTextViewItemPostTextType,
                binding.spoilerCustomTextViewItemPostTextType,
                binding.flairCustomTextViewItemPostTextType,
                binding.bottomConstraintLayoutItemPostTextType,
                binding.upvoteButtonItemPostTextType,
                binding.scoreTextViewItemPostTextType,
                binding.downvoteButtonItemPostTextType,
                binding.commentsCountButtonItemPostTextType,
                binding.saveButtonItemPostTextType,
                binding.shareButtonItemPostTextType,
                binding.contentTextViewItemPostTextType);
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
                    TextView contentTextView) {
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

        this.contentTextView = contentTextView;

        if (mActivity.contentTypeface != null) {
            contentTextView.setTypeface(mActivity.titleTypeface);
        }
        contentTextView.setTextColor(host.postContentColor());
    }

    @Override
    void setItemViewBackgroundColor(boolean isReadPost) {
        itemView.setBackgroundTintList(ColorStateList.valueOf(isReadPost ? host.readPostCardViewBackgroundColor() : host.cardViewBackgroundColor()));
    }
}
