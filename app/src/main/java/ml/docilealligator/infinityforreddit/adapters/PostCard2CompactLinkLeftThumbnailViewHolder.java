package ml.docilealligator.infinityforreddit.adapters;

import androidx.annotation.NonNull;
import ml.docilealligator.infinityforreddit.databinding.ItemPostCard2CompactLinkBinding;

class PostCard2CompactLinkLeftThumbnailViewHolder extends PostCompactBaseViewHolder {
    PostCard2CompactLinkLeftThumbnailViewHolder(@NonNull ItemPostCard2CompactLinkBinding binding, PostCompactHost host) {
        super(binding.getRoot(), host);
        postTypeIndicatorView = binding.postTypeIndicatorViewItemPostCard2CompactLink;
        noPreviewPostTypeIndicatorView = binding.postTypeIndicatorViewNoPreviewItemPostCard2CompactLink;

        setBaseView(binding.iconGifImageViewItemPostCard2CompactLink,
                binding.nameTextViewItemPostCard2CompactLink,
                binding.stickiedPostImageViewItemPostCard2CompactLink,
                binding.postTimeTextViewItemPostCard2CompactLink,
                binding.titleTextViewItemPostCard2CompactLink,
                binding.typeTextViewItemPostCard2CompactLink,
                binding.archivedImageViewItemPostCard2CompactLink,
                binding.lockedImageViewItemPostCard2CompactLink,
                binding.crosspostImageViewItemPostCard2CompactLink,
                binding.nsfwTextViewItemPostCard2CompactLink,
                binding.spoilerCustomTextViewItemPostCard2CompactLink,
                binding.flairCustomTextViewItemPostCard2CompactLink,
                binding.linkTextViewItemPostCard2CompactLink,
                binding.imageViewWrapperItemPostCard2CompactLink,
                binding.progressBarItemPostCard2CompactLink,
                binding.imageViewItemPostCard2CompactLink,
                binding.playButtonImageViewItemPostCard2CompactLink,
                binding.frameLayoutImageViewNoPreviewLinkItemPostCard2CompactLink,
                binding.imageViewNoPreviewLinkItemPostCard2CompactLink,
                binding.bottomConstraintLayoutItemPostCard2CompactLink,
                binding.upvoteButtonItemPostCard2CompactLink,
                binding.scoreTextViewItemPostCard2CompactLink,
                binding.downvoteButtonItemPostCard2CompactLink,
                binding.commentsCountButtonItemPostCard2CompactLink,
                binding.saveButtonItemPostCard2CompactLink,
                binding.shareButtonItemPostCard2CompactLink,
                binding.dividerItemPostCard2CompactLink);
        setupUsernameView(binding.usernameTextViewItemPostCard2CompactLink);
    }
}
