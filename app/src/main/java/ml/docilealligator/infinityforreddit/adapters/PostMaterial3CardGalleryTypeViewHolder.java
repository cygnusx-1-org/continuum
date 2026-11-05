package ml.docilealligator.infinityforreddit.adapters;

import android.content.res.ColorStateList;
import ml.docilealligator.infinityforreddit.databinding.ItemPostCard3GalleryTypeBinding;

public class PostMaterial3CardGalleryTypeViewHolder extends PostBaseGalleryTypeViewHolder {
    PostMaterial3CardGalleryTypeViewHolder(ItemPostCard3GalleryTypeBinding binding, PostGalleryHost host) {
        super(binding.getRoot(),
                binding.iconGifImageViewItemPostCard3GalleryType,
                binding.subredditNameTextViewItemPostCard3GalleryType,
                binding.userTextViewItemPostCard3GalleryType,
                binding.stickiedPostImageViewItemPostCard3GalleryType,
                binding.postTimeTextViewItemPostCard3GalleryType,
                binding.titleTextViewItemPostCard3GalleryType,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                binding.galleryFrameLayoutItemPostCard3GalleryType,
                binding.galleryRecyclerViewItemPostCard3GalleryType,
                binding.imageIndexTextViewItemPostCard3GalleryType,
                binding.noPreviewImageViewItemPostCard3GalleryType,
                binding.bottomConstraintLayoutItemPostCard3GalleryType,
                binding.upvoteButtonItemPostCard3GalleryType,
                binding.scoreTextViewItemPostCard3GalleryType,
                binding.downvoteButtonItemPostCard3GalleryType,
                binding.commentsCountButtonItemPostCard3GalleryType,
                binding.saveButtonItemPostCard3GalleryType,
                binding.shareButtonItemPostCard3GalleryType,
                host);
    }

    @Override
    void setItemViewBackgroundColor(boolean isReadPost) {
        itemView.setBackgroundTintList(ColorStateList.valueOf(isReadPost ? rowHost().readPostFilledCardViewBackgroundColor() : rowHost().filledCardViewBackgroundColor()));
    }
}
