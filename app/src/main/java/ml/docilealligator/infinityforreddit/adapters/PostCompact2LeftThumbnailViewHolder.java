package ml.docilealligator.infinityforreddit.adapters;

import androidx.annotation.NonNull;
import ml.docilealligator.infinityforreddit.databinding.ItemPostCompact2Binding;

class PostCompact2LeftThumbnailViewHolder extends PostCompactBaseViewHolder {
    PostCompact2LeftThumbnailViewHolder(@NonNull ItemPostCompact2Binding binding, PostCompactHost host) {
        super(binding.getRoot(), host);
        postTypeIndicatorView = binding.postTypeIndicatorViewItemPostCompact2;
        noPreviewPostTypeIndicatorView = binding.postTypeIndicatorViewNoPreviewItemPostCompact2;

        setBaseView(binding.iconGifImageViewItemPostCompact2,
                binding.nameTextViewItemPostCompact2,
                binding.stickiedPostImageViewItemPostCompact2,
                binding.postTimeTextViewItemPostCompact2,
                binding.titleTextViewItemPostCompact2,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                binding.imageViewWrapperItemPostCompact2,
                binding.progressBarItemPostCompact2,
                binding.imageViewItemPostCompact2,
                binding.playButtonImageViewItemPostCompact2,
                binding.frameLayoutImageViewNoPreviewLinkItemPostCompact2,
                binding.imageViewNoPreviewLinkItemPostCompact2,
                null,
                binding.upvoteButtonItemPostCompact2,
                binding.scoreTextViewItemPostCompact2,
                binding.downvoteButtonItemPostCompact2,
                null,
                null,
                null,
                binding.dividerItemPostCompact2);
        setupUsernameView(binding.usernameTextViewItemPostCompact2);
    }
}
