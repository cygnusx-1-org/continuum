package ml.docilealligator.infinityforreddit.adapters;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.RequestManager;
import com.bumptech.glide.request.RequestOptions;
import java.util.ArrayList;
import jp.wasabeef.glide.transformations.RoundedCornersTransformation;
import ml.docilealligator.infinityforreddit.R;
import ml.docilealligator.infinityforreddit.activities.BaseActivity;
import ml.docilealligator.infinityforreddit.customtheme.CustomThemeWrapper;
import ml.docilealligator.infinityforreddit.databinding.ItemSelectedSubredditBinding;
import ml.docilealligator.infinityforreddit.multireddit.ExpandedSubredditInMultiReddit;

public class SelectedSubredditsRecyclerViewAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private final BaseActivity activity;
    private final CustomThemeWrapper customThemeWrapper;
    private final RequestManager glide;
    private final ArrayList<ExpandedSubredditInMultiReddit> subreddits;

    public SelectedSubredditsRecyclerViewAdapter(BaseActivity activity, CustomThemeWrapper customThemeWrapper,
                                                 RequestManager glide,
                                                 ArrayList<ExpandedSubredditInMultiReddit> subreddits) {
        this.activity = activity;
        this.customThemeWrapper = customThemeWrapper;
        this.glide = glide;
        this.subreddits = subreddits;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new SubredditViewHolder(ItemSelectedSubredditBinding
                .inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof SubredditViewHolder) {
            ExpandedSubredditInMultiReddit subreddit = subreddits.get(position);
            SubredditViewHolder subredditViewHolder = (SubredditViewHolder) holder;

            glide.load(subreddit.getIconUrl())
                    .apply(RequestOptions.bitmapTransform(new RoundedCornersTransformation(72, 0)))
                    .error(glide.load(R.drawable.subreddit_default_icon)
                            .apply(RequestOptions.bitmapTransform(new RoundedCornersTransformation(72, 0))))
                    .into(subredditViewHolder.binding.iconImageViewItemSelectedSubreddit);
            subredditViewHolder.binding.subredditNameItemSelectedSubreddit.setText(subreddit.getName());
        }
    }

    @Override
    public int getItemCount() {
        return subreddits.size();
    }

    @Override
    public void onViewRecycled(@NonNull RecyclerView.ViewHolder holder) {
        super.onViewRecycled(holder);
        if (holder instanceof SubredditViewHolder) {
            glide.clear(((SubredditViewHolder) holder).binding.iconImageViewItemSelectedSubreddit);
        }
    }

    public void addSubreddits(ArrayList<ExpandedSubredditInMultiReddit> newSubreddits) {
        int oldSize = subreddits.size();
        subreddits.addAll(newSubreddits);
        notifyItemRangeInserted(oldSize, newSubreddits.size());
    }

    public void addUserInSubredditType(String username) {
        subreddits.add(new ExpandedSubredditInMultiReddit(username, null));
        // The appended item is at size() - 1; notifying at size() reports a position past the end,
        // which desyncs RecyclerView's bookkeeping against getItemCount().
        notifyItemInserted(subreddits.size() - 1);
    }

    public ArrayList<ExpandedSubredditInMultiReddit> getSubreddits() {
        return subreddits;
    }

    class SubredditViewHolder extends RecyclerView.ViewHolder {
        ItemSelectedSubredditBinding binding;

        public SubredditViewHolder(@NonNull ItemSelectedSubredditBinding binding) {
            super(binding.getRoot());
            this.binding = binding;

            binding.subredditNameItemSelectedSubreddit.setTextColor(customThemeWrapper.getPrimaryIconColor());
            binding.deleteImageViewItemSelectedSubreddit.setColorFilter(customThemeWrapper.getPrimaryIconColor(), android.graphics.PorterDuff.Mode.SRC_IN);

            if (activity.typeface != null) {
                binding.subredditNameItemSelectedSubreddit.setTypeface(activity.typeface);
            }

            // Read the position once, at click time. notifyItemRemoved starts a ~250ms disappear
            // animation during which the row is still laid out and still takes touches, and its
            // position reads as NO_POSITION — so a second tap on a row already being deleted would
            // otherwise call subreddits.remove(-1).
            binding.deleteImageViewItemSelectedSubreddit.setOnClickListener(view -> {
                int position = getBindingAdapterPosition();
                if (position == RecyclerView.NO_POSITION) {
                    return;
                }

                subreddits.remove(position);
                notifyItemRemoved(position);
            });
        }
    }
}
