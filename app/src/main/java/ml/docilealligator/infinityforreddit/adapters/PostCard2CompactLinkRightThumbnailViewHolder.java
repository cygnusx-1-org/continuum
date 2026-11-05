package ml.docilealligator.infinityforreddit.adapters;

import androidx.annotation.NonNull;
import ml.docilealligator.infinityforreddit.databinding.ItemPostCard2CompactLinkRightThumbnailBinding;

class PostCard2CompactLinkRightThumbnailViewHolder extends PostCompactBaseViewHolder {
    PostCard2CompactLinkRightThumbnailViewHolder(@NonNull ItemPostCard2CompactLinkRightThumbnailBinding binding, PostCompactHost host) {
        super(binding.getRoot(), host);
        postTypeIndicatorView = binding.postTypeIndicatorViewItemPostCard2CompactLinkRightThumbnail;
        noPreviewPostTypeIndicatorView = binding.postTypeIndicatorViewNoPreviewItemPostCard2CompactLinkRightThumbnail;

        setBaseView(binding.iconGifImageViewItemPostCard2CompactLinkRightThumbnail,
                binding.nameTextViewItemPostCard2CompactLinkRightThumbnail,
                binding.stickiedPostImageViewItemPostCard2CompactLinkRightThumbnail,
                binding.postTimeTextViewItemPostCard2CompactLinkRightThumbnail,
                binding.titleTextViewItemPostCard2CompactLinkRightThumbnail,
                binding.typeTextViewItemPostCard2CompactLinkRightThumbnail,
                binding.archivedImageViewItemPostCard2CompactLinkRightThumbnail,
                binding.lockedImageViewItemPostCard2CompactLinkRightThumbnail,
                binding.crosspostImageViewItemPostCard2CompactLinkRightThumbnail,
                binding.nsfwTextViewItemPostCard2CompactLinkRightThumbnail,
                binding.spoilerCustomTextViewItemPostCard2CompactLinkRightThumbnail,
                binding.flairCustomTextViewItemPostCard2CompactLinkRightThumbnail,
                binding.linkTextViewItemPostCard2CompactLinkRightThumbnail,
                binding.imageViewWrapperItemPostCard2CompactLinkRightThumbnail,
                binding.progressBarItemPostCard2CompactLinkRightThumbnail,
                binding.imageViewItemPostCard2CompactLinkRightThumbnail,
                binding.playButtonImageViewItemPostCard2CompactLinkRightThumbnail,
                binding.frameLayoutImageViewNoPreviewLinkItemPostCard2CompactLinkRightThumbnail,
                binding.imageViewNoPreviewLinkItemPostCard2CompactLinkRightThumbnail,
                binding.bottomConstraintLayoutItemPostCard2CompactLinkRightThumbnail,
                binding.upvoteButtonItemPostCard2CompactLinkRightThumbnail,
                binding.scoreTextViewItemPostCard2CompactLinkRightThumbnail,
                binding.downvoteButtonItemPostCard2CompactLinkRightThumbnail,
                binding.commentsCountButtonItemPostCard2CompactLinkRightThumbnail,
                binding.saveButtonItemPostCard2CompactLinkRightThumbnail,
                binding.shareButtonItemPostCard2CompactLinkRightThumbnail,
                binding.dividerItemPostCard2CompactLinkRightThumbnail);
        setupUsernameView(binding.usernameTextViewItemPostCard2CompactLinkRightThumbnail);
    }
}
