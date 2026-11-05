package ml.docilealligator.infinityforreddit.adapters;

import androidx.annotation.NonNull;
import ml.docilealligator.infinityforreddit.databinding.ItemPostGalleryGalleryTypeBinding;

class PostGalleryGalleryTypeViewHolder extends PostGalleryBaseGalleryTypeViewHolder {

    public PostGalleryGalleryTypeViewHolder(@NonNull ItemPostGalleryGalleryTypeBinding binding, PostGalleryHost host) {
        super(binding.getRoot(), binding.galleryFrameLayoutItemPostGalleryGalleryType,
                binding.galleryRecyclerViewItemPostGalleryGalleryType, binding.imageIndexTextViewItemPostGalleryGalleryType,
                binding.imageViewNoPreviewItemPostGalleryGalleryType,
                host);
    }
}
