package ml.docilealligator.infinityforreddit.adapters;

import androidx.annotation.NonNull;
import androidx.media3.common.util.UnstableApi;
import ml.docilealligator.infinityforreddit.databinding.ItemPostGalleryBinding;
import ml.docilealligator.infinityforreddit.databinding.ItemPostGalleryVideoPlayerBinding;
import ml.docilealligator.infinityforreddit.post.Post;

/**
 * A gallery tile whose video autoplays, muted, over the tile's own still. Taking part in the feed's
 * autoplay the way a card does -- the same player, the same visible-area rule, the same limit on
 * how many play at once -- but with no controls, so the tile stays a tile.
 */
@UnstableApi
class PostGalleryVideoAutoplayViewHolder extends PostGalleryViewHolder implements VideoAutoplayRow {
    private final VideoAutoplayImpl toroPlayer;

    <H extends PostGalleryHost & PostVideoHost> PostGalleryVideoAutoplayViewHolder(@NonNull ItemPostGalleryBinding binding, H host) {
        super(binding, host);
        ItemPostGalleryVideoPlayerBinding player = ItemPostGalleryVideoPlayerBinding.bind(binding.videoPlayerStubItemPostGallery.inflate());
        toroPlayer = new VideoAutoplayImpl(itemView, binding.imageViewItemPostGallery,
                player.errorLoadingVideoImageViewItemPostGalleryVideoPlayer, player.playerViewItemPostGalleryVideoPlayer,
                null, host) {
            @Override
            public int getPlayerOrder() {
                return getBindingAdapterPosition();
            }

            @Override
            int getAdapterPosition() {
                return getBindingAdapterPosition();
            }

            @Override
            Post getPost() {
                return post;
            }

            @Override
            void markPostRead(Post post, boolean changePostItemColor) {
                PostGalleryVideoAutoplayViewHolder.this.markPostRead(post, changePostItemColor);
            }
        };
    }

    @Override
    public VideoAutoplayImpl toroPlayer() {
        return toroPlayer;
    }
}
