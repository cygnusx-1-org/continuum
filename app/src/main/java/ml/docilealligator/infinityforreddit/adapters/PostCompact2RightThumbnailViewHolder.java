package ml.docilealligator.infinityforreddit.adapters;

import androidx.annotation.NonNull;
import ml.docilealligator.infinityforreddit.databinding.ItemPostCompact2RightThumbnailBinding;

class PostCompact2RightThumbnailViewHolder extends PostCompactBaseViewHolder {
    PostCompact2RightThumbnailViewHolder(@NonNull ItemPostCompact2RightThumbnailBinding binding, PostCompactHost host) {
        super(binding.getRoot(), host);
        postTypeIndicatorView = binding.postTypeIndicatorViewItemPostCompact2RightThumbnail;
        noPreviewPostTypeIndicatorView = binding.postTypeIndicatorViewNoPreviewItemPostCompact2RightThumbnail;

        setBaseView(binding.iconGifImageViewItemPostCompact2RightThumbnail,
                binding.nameTextViewItemPostCompact2RightThumbnail,
                binding.stickiedPostImageViewItemPostCompact2RightThumbnail,
                binding.postTimeTextViewItemPostCompact2RightThumbnail,
                binding.titleTextViewItemPostCompact2RightThumbnail,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                binding.imageViewWrapperItemPostCompact2RightThumbnail,
                binding.progressBarItemPostCompact2RightThumbnail,
                binding.imageViewItemPostCompact2RightThumbnail,
                binding.playButtonImageViewItemPostCompact2RightThumbnail,
                binding.frameLayoutImageViewNoPreviewLinkItemPostCompact2RightThumbnail,
                binding.imageViewNoPreviewLinkItemPostCompact2RightThumbnail,
                null,
                binding.upvoteButtonItemPostCompact2RightThumbnail,
                binding.scoreTextViewItemPostCompact2RightThumbnail,
                binding.downvoteButtonItemPostCompact2RightThumbnail,
                null,
                null,
                null,
                binding.dividerItemPostCompact2RightThumbnail);
        setupUsernameView(binding.usernameTextViewItemPostCompact2RightThumbnail);
    }
}
