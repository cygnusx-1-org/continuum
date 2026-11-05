package ml.docilealligator.infinityforreddit.adapters;

import androidx.annotation.NonNull;
import ml.docilealligator.infinityforreddit.databinding.ItemPostCompactBinding;

class PostCompactLeftThumbnailViewHolder extends PostCompactBaseViewHolder {
    PostCompactLeftThumbnailViewHolder(@NonNull ItemPostCompactBinding binding, PostCompactHost host) {
        super(binding.getRoot(), host);
        postTypeIndicatorView = binding.postTypeIndicatorViewItemPostCompact;
        noPreviewPostTypeIndicatorView = binding.postTypeIndicatorViewNoPreviewItemPostCompact;

        setBaseView(binding.iconGifImageViewItemPostCompact,
                binding.nameTextViewItemPostCompact,
                binding.stickiedPostImageViewItemPostCompact,
                binding.postTimeTextViewItemPostCompact,
                binding.titleTextViewItemPostCompact,
                binding.typeTextViewItemPostCompact,
                binding.archivedImageViewItemPostCompact,
                binding.lockedImageViewItemPostCompact,
                binding.crosspostImageViewItemPostCompact,
                binding.nsfwTextViewItemPostCompact,
                binding.spoilerCustomTextViewItemPostCompact,
                binding.flairCustomTextViewItemPostCompact,
                binding.linkTextViewItemPostCompact,
                binding.imageViewWrapperItemPostCompact,
                binding.progressBarItemPostCompact,
                binding.imageViewItemPostCompact,
                binding.playButtonImageViewItemPostCompact,
                binding.frameLayoutImageViewNoPreviewLinkItemPostCompact,
                binding.imageViewNoPreviewLinkItemPostCompact,
                binding.bottomConstraintLayoutItemPostCompact,
                binding.upvoteButtonItemPostCompact,
                binding.scoreTextViewItemPostCompact,
                binding.downvoteButtonItemPostCompact,
                binding.commentsCountButtonItemPostCompact,
                binding.saveButtonItemPostCompact,
                binding.shareButtonItemPostCompact,
                binding.dividerItemPostCompact);
        setupUsernameView(binding.usernameTextViewItemPostCompact);
    }
}
