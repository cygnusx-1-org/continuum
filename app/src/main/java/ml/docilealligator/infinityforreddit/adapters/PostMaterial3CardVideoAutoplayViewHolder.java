package ml.docilealligator.infinityforreddit.adapters;

import android.content.res.ColorStateList;
import androidx.media3.common.util.UnstableApi;
import ml.docilealligator.infinityforreddit.R;
import ml.docilealligator.infinityforreddit.databinding.ItemPostCard3VideoTypeAutoplayBinding;

@UnstableApi
public class PostMaterial3CardVideoAutoplayViewHolder extends PostBaseVideoAutoplayViewHolder {
    PostMaterial3CardVideoAutoplayViewHolder(ItemPostCard3VideoTypeAutoplayBinding binding, PostVideoHost host) {
        super(binding.getRoot(),
                host,
                binding.iconGifImageViewItemPostCard3VideoTypeAutoplay,
                binding.subredditNameTextViewItemPostCard3VideoTypeAutoplay,
                binding.userTextViewItemPostCard3VideoTypeAutoplay,
                binding.stickiedPostImageViewItemPostCard3VideoTypeAutoplay,
                binding.postTimeTextViewItemPostCard3VideoTypeAutoplay,
                binding.titleTextViewItemPostCard3VideoTypeAutoplay,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                binding.previewFrameLayoutItemPostCard3VideoTypeAutoplay,
                binding.aspectRatioFrameLayoutItemPostCard3VideoTypeAutoplay,
                binding.previewImageViewItemPostCard3VideoTypeAutoplay,
                binding.errorLoadingVideoImageViewItemPostCard3VideoTypeAutoplay,
                binding.playerViewItemPostCard3VideoTypeAutoplay,
                binding.getRoot().findViewById(R.id.video_quality_exo_playback_control_view),
                binding.getRoot().findViewById(R.id.mute_exo_playback_control_view),
                binding.getRoot().findViewById(R.id.fullscreen_exo_playback_control_view),
                binding.getRoot().findViewById(R.id.exo_play),
                binding.getRoot().findViewById(R.id.exo_progress),
                binding.bottomConstraintLayoutItemPostCard3VideoTypeAutoplay,
                binding.upvoteButtonItemPostCard3VideoTypeAutoplay,
                binding.scoreTextViewItemPostCard3VideoTypeAutoplay,
                binding.downvoteButtonItemPostCard3VideoTypeAutoplay,
                binding.commentsCountButtonItemPostCard3VideoTypeAutoplay,
                binding.saveButtonItemPostCard3VideoTypeAutoplay,
                binding.shareButtonItemPostCard3VideoTypeAutoplay);
    }

    @Override
    void setItemViewBackgroundColor(boolean isReadPost) {
        itemView.setBackgroundTintList(ColorStateList.valueOf(isReadPost ? rowHost().readPostFilledCardViewBackgroundColor() : rowHost().filledCardViewBackgroundColor()));
    }
}
