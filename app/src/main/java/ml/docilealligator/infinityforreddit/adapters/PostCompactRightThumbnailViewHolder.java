package ml.docilealligator.infinityforreddit.adapters;

import androidx.annotation.NonNull;
import ml.docilealligator.infinityforreddit.databinding.ItemPostCompactRightThumbnailBinding;

class PostCompactRightThumbnailViewHolder extends PostCompactBaseViewHolder {
    PostCompactRightThumbnailViewHolder(@NonNull ItemPostCompactRightThumbnailBinding binding, PostCompactHost host) {
        super(binding.getRoot(), host);
        postTypeIndicatorView = binding.postTypeIndicatorViewItemPostCompactRightThumbnail;
        noPreviewPostTypeIndicatorView = binding.postTypeIndicatorViewNoPreviewItemPostCompactRightThumbnail;

        setBaseView(binding.iconGifImageViewItemPostCompactRightThumbnail,
                binding.nameTextViewItemPostCompactRightThumbnail,
                binding.stickiedPostImageViewItemPostCompactRightThumbnail,
                binding.postTimeTextViewItemPostCompactRightThumbnail,
                binding.titleTextViewItemPostCompactRightThumbnail,
                binding.typeTextViewItemPostCompactRightThumbnail,
                binding.archivedImageViewItemPostCompactRightThumbnail,
                binding.lockedImageViewItemPostCompactRightThumbnail,
                binding.crosspostImageViewItemPostCompactRightThumbnail,
                binding.nsfwTextViewItemPostCompactRightThumbnail,
                binding.spoilerCustomTextViewItemPostCompactRightThumbnail,
                binding.flairCustomTextViewItemPostCompactRightThumbnail,
                binding.linkTextViewItemPostCompactRightThumbnail,
                binding.imageViewWrapperItemPostCompactRightThumbnail,
                binding.progressBarItemPostCompactRightThumbnail,
                binding.imageViewItemPostCompactRightThumbnail,
                binding.playButtonImageViewItemPostCompactRightThumbnail,
                binding.frameLayoutImageViewNoPreviewLinkItemPostCompactRightThumbnail,
                binding.imageViewNoPreviewLinkItemPostCompactRightThumbnail,
                binding.bottomConstraintLayoutItemPostCompactRightThumbnail,
                binding.upvoteButtonItemPostCompactRightThumbnail,
                binding.scoreTextViewItemPostCompactRightThumbnail,
                binding.downvoteButtonItemPostCompactRightThumbnail,
                binding.commentsCountButtonItemPostCompactRightThumbnail,
                binding.saveButtonItemPostCompactRightThumbnail,
                binding.shareButtonItemPostCompactRightThumbnail,
                binding.dividerItemPostCompactRightThumbnail);
        setupUsernameView(binding.usernameTextViewItemPostCompactRightThumbnail);
    }
}
