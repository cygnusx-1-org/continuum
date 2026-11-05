package ml.docilealligator.infinityforreddit.adapters;

import android.content.res.ColorStateList;
import androidx.annotation.NonNull;
import ml.docilealligator.infinityforreddit.databinding.ItemPostCard3TextBinding;

public class PostMaterial3CardTextTypeViewHolder extends PostTextTypeViewHolder {
    private final PostRowHost host;

    PostMaterial3CardTextTypeViewHolder(@NonNull ItemPostCard3TextBinding binding, PostRowHost host) {
        super(binding.getRoot(), host);
        this.host = host;
        setBaseView(
                binding.iconGifImageViewItemPostCard3TextType,
                binding.subredditNameTextViewItemPostCard3TextType,
                binding.userTextViewItemPostCard3TextType,
                binding.stickiedPostImageViewItemPostCard3TextType,
                binding.postTimeTextViewItemPostCard3TextType,
                binding.titleTextViewItemPostCard3TextType,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                binding.bottomConstraintLayoutItemPostCard3TextType,
                binding.upvoteButtonItemPostCard3TextType,
                binding.scoreTextViewItemPostCard3TextType,
                binding.downvoteButtonItemPostCard3TextType,
                binding.commentsCountButtonItemPostCard3TextType,
                binding.saveButtonItemPostCard3TextType,
                binding.shareButtonItemPostCard3TextType,
                binding.contentTextViewItemPostCard3TextType);
    }

    @Override
    void setItemViewBackgroundColor(boolean isReadPost) {
        itemView.setBackgroundTintList(ColorStateList.valueOf(isReadPost ? host.readPostFilledCardViewBackgroundColor() : host.filledCardViewBackgroundColor()));
    }
}
