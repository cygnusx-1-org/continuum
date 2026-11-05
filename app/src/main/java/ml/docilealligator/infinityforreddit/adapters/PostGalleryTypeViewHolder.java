package ml.docilealligator.infinityforreddit.adapters;

import android.content.res.ColorStateList;
import ml.docilealligator.infinityforreddit.databinding.ItemPostGalleryTypeBinding;

public class PostGalleryTypeViewHolder extends PostBaseGalleryTypeViewHolder {

    PostGalleryTypeViewHolder(ItemPostGalleryTypeBinding binding, PostGalleryHost host) {
        super(binding.getRoot(),
                binding.iconGifImageViewItemPostGalleryType,
                binding.subredditNameTextViewItemPostGalleryType,
                binding.userTextViewItemPostGalleryType,
                binding.stickiedPostImageViewItemPostGalleryType,
                binding.postTimeTextViewItemPostGalleryType,
                binding.titleTextViewItemPostGalleryType,
                binding.typeTextViewItemPostGalleryType,
                binding.archivedImageViewItemPostGalleryType,
                binding.lockedImageViewItemPostGalleryType,
                binding.crosspostImageViewItemPostGalleryType,
                binding.nsfwTextViewItemPostGalleryType,
                binding.spoilerTextViewItemPostGalleryType,
                binding.flairTextViewItemPostGalleryType,
                binding.galleryFrameLayoutItemPostGalleryType,
                binding.galleryRecyclerViewItemPostGalleryType,
                binding.imageIndexTextViewItemPostGalleryType,
                binding.noPreviewImageViewItemPostGalleryType,
                binding.bottomConstraintLayoutItemPostGalleryType,
                binding.upvoteButtonItemPostGalleryType,
                binding.scoreTextViewItemPostGalleryType,
                binding.downvoteButtonItemPostGalleryType,
                binding.commentsCountButtonItemPostGalleryType,
                binding.saveButtonItemPostGalleryType,
                binding.shareButtonItemPostGalleryType,
                host);
    }

    @Override
    void setItemViewBackgroundColor(boolean isReadPost) {
        itemView.setBackgroundTintList(ColorStateList.valueOf(isReadPost ? rowHost().readPostCardViewBackgroundColor() : rowHost().cardViewBackgroundColor()));
    }
}
