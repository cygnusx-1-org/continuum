package ml.docilealligator.infinityforreddit.adapters;

import android.content.res.ColorStateList;
import androidx.annotation.NonNull;
import ml.docilealligator.infinityforreddit.databinding.ItemPostCard3WithPreviewBinding;

public class PostMaterial3CardWithPreviewViewHolder extends PostWithPreviewTypeViewHolder {
    PostMaterial3CardWithPreviewViewHolder(@NonNull ItemPostCard3WithPreviewBinding binding, PostPreviewHost host) {
        super(binding.getRoot(), host);
        setBaseView(binding.iconGifImageViewItemPostCard3WithPreview,
                binding.subredditNameTextViewItemPostCard3WithPreview,
                binding.userTextViewItemPostCard3WithPreview,
                binding.stickiedPostImageViewItemPostCard3WithPreview,
                binding.postTimeTextViewItemPostCard3WithPreview,
                binding.titleTextViewItemPostCard3WithPreview,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                binding.bottomConstraintLayoutItemPostCard3WithPreview,
                binding.upvoteButtonItemPostCard3WithPreview,
                binding.scoreTextViewItemPostCard3WithPreview,
                binding.downvoteButtonItemPostCard3WithPreview,
                binding.commentsCountButtonItemPostCard3WithPreview,
                binding.saveButtonItemPostCard3WithPreview,
                binding.shareButtonItemPostCard3WithPreview,
                binding.linkTextViewItemPostCard3WithPreview,
                binding.imageViewNoPreviewGalleryItemPostCard3WithPreview,
                binding.progressBarItemPostCard3WithPreview,
                binding.videoOrGifIndicatorImageViewItemPostCard3WithPreview,
                binding.loadImageErrorTextViewItemPostCard3WithPreview,
                binding.imageWrapperRelativeLayoutItemPostCard3WithPreview,
                binding.imageViewItemPostCard3WithPreview);
        contentTextView = binding.contentTextViewItemPostCard3WithPreview;
    }

    @Override
    void setItemViewBackgroundColor(boolean isReadPost) {
        itemView.setBackgroundTintList(ColorStateList.valueOf(isReadPost ? rowHost().readPostFilledCardViewBackgroundColor() : rowHost().filledCardViewBackgroundColor()));
    }
}
