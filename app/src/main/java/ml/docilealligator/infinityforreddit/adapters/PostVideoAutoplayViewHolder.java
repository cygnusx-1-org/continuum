package ml.docilealligator.infinityforreddit.adapters;

import android.content.res.ColorStateList;
import androidx.media3.common.util.UnstableApi;
import ml.docilealligator.infinityforreddit.R;
import ml.docilealligator.infinityforreddit.databinding.ItemPostVideoTypeAutoplayBinding;

@UnstableApi
class PostVideoAutoplayViewHolder extends PostBaseVideoAutoplayViewHolder {
    PostVideoAutoplayViewHolder(ItemPostVideoTypeAutoplayBinding binding, PostVideoHost host) {
        super(binding.getRoot(),
                host,
                binding.iconGifImageViewItemPostVideoTypeAutoplay,
                binding.subredditNameTextViewItemPostVideoTypeAutoplay,
                binding.userTextViewItemPostVideoTypeAutoplay,
                binding.stickiedPostImageViewItemPostVideoTypeAutoplay,
                binding.postTimeTextViewItemPostVideoTypeAutoplay,
                binding.titleTextViewItemPostVideoTypeAutoplay,
                binding.typeTextViewItemPostVideoTypeAutoplay,
                binding.crosspostImageViewItemPostVideoTypeAutoplay,
                binding.archivedImageViewItemPostVideoTypeAutoplay,
                binding.lockedImageViewItemPostVideoTypeAutoplay,
                binding.nsfwTextViewItemPostVideoTypeAutoplay,
                binding.spoilerCustomTextViewItemPostVideoTypeAutoplay,
                binding.flairCustomTextViewItemPostVideoTypeAutoplay,
                binding.previewFrameLayoutItemPostVideoTypeAutoplay,
                binding.aspectRatioFrameLayoutItemPostVideoTypeAutoplay,
                binding.previewImageViewItemPostVideoTypeAutoplay,
                binding.errorLoadingVideoImageViewItemPostVideoTypeAutoplay,
                binding.playerViewItemPostVideoTypeAutoplay,
                binding.getRoot().findViewById(R.id.video_quality_exo_playback_control_view),
                binding.getRoot().findViewById(R.id.mute_exo_playback_control_view),
                binding.getRoot().findViewById(R.id.fullscreen_exo_playback_control_view),
                binding.getRoot().findViewById(R.id.exo_play),
                binding.getRoot().findViewById(R.id.exo_progress),
                binding.bottomConstraintLayoutItemPostVideoTypeAutoplay,
                binding.upvoteButtonItemPostVideoTypeAutoplay,
                binding.scoreTextViewItemPostVideoTypeAutoplay,
                binding.downvoteButtonItemPostVideoTypeAutoplay,
                binding.commentsCountButtonItemPostVideoTypeAutoplay,
                binding.saveButtonItemPostVideoTypeAutoplay,
                binding.shareButtonItemPostVideoTypeAutoplay);
    }

    @Override
    void setItemViewBackgroundColor(boolean isReadPost) {
        itemView.setBackgroundTintList(ColorStateList.valueOf(isReadPost ? rowHost().readPostCardViewBackgroundColor() : rowHost().cardViewBackgroundColor()));
    }
}
