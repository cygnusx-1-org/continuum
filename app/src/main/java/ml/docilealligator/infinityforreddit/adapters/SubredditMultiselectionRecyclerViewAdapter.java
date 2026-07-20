package ml.docilealligator.infinityforreddit.adapters;

import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestManager;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import jp.wasabeef.glide.transformations.RoundedCornersTransformation;
import ml.docilealligator.infinityforreddit.R;
import ml.docilealligator.infinityforreddit.activities.BaseActivity;
import ml.docilealligator.infinityforreddit.customtheme.CustomThemeWrapper;
import ml.docilealligator.infinityforreddit.databinding.ItemSubscribedSubredditMultiSelectionBinding;
import ml.docilealligator.infinityforreddit.subreddit.SubredditWithSelection;
import ml.docilealligator.infinityforreddit.subscribedsubreddit.SubscribedSubredditData;

public class SubredditMultiselectionRecyclerViewAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private final BaseActivity activity;
    private ArrayList<SubredditWithSelection> subscribedSubreddits = new ArrayList<>();
    private final RequestManager glide;
    private final int primaryTextColor;
    private final int colorAccent;

    public SubredditMultiselectionRecyclerViewAdapter(BaseActivity activity, CustomThemeWrapper customThemeWrapper) {
        this.activity = activity;
        glide = Glide.with(activity);
        primaryTextColor = customThemeWrapper.getPrimaryTextColor();
        colorAccent = customThemeWrapper.getColorAccent();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new SubscribedSubredditViewHolder(ItemSubscribedSubredditMultiSelectionBinding
                .inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof SubscribedSubredditViewHolder) {
            SubredditWithSelection subreddit = subscribedSubreddits.get(position);
            SubscribedSubredditViewHolder subredditViewHolder = (SubscribedSubredditViewHolder) holder;

            subredditViewHolder.binding.nameTextViewItemSubscribedSubredditMultiselection.setText(subreddit.getName());
            glide.load(subreddit.getIconUrl())
                    .transform(new RoundedCornersTransformation(72, 0))
                    .error(glide.load(R.drawable.subreddit_default_icon)
                            .transform(new RoundedCornersTransformation(72, 0)))
                    .into(subredditViewHolder.binding.iconGifImageViewItemSubscribedSubredditMultiselection);
            subredditViewHolder.binding.checkboxItemSubscribedSubredditMultiselection.setChecked(subreddit.isSelected());
        }
    }

    @Override
    public int getItemCount() {
        return subscribedSubreddits.size();
    }

    @Override
    public void onViewRecycled(@NonNull RecyclerView.ViewHolder holder) {
        super.onViewRecycled(holder);
        if (holder instanceof SubscribedSubredditViewHolder) {
            glide.clear(((SubscribedSubredditViewHolder) holder).binding.iconGifImageViewItemSubscribedSubredditMultiselection);
        }
    }

    public void setSubscribedSubreddits(List<SubscribedSubredditData> subscribedSubreddits, @Nullable String selectedSubreddits) {
        this.subscribedSubreddits = SubredditWithSelection.convertSubscribedSubreddits(subscribedSubreddits);
        Set<String> selectedSet = new HashSet<>();
        if (selectedSubreddits != null && !selectedSubreddits.isEmpty()) {
            for (String name : selectedSubreddits.split(",")) {
                String trimmed = name.trim();
                if (!trimmed.isEmpty()) {
                    selectedSet.add(trimmed);
                }
            }
        }

        for (SubredditWithSelection s : this.subscribedSubreddits) {
            s.setSelected(selectedSet.contains(s.getName()));
        }

        notifyDataSetChanged();
    }

    public ArrayList<SubredditWithSelection> getAllSelectedSubreddits() {
        ArrayList<SubredditWithSelection> selectedSubreddits = new ArrayList<>();
        for (SubredditWithSelection s : subscribedSubreddits) {
            if (s.isSelected()) {
                selectedSubreddits.add(s);
            }
        }
        return selectedSubreddits;
    }

    class SubscribedSubredditViewHolder extends RecyclerView.ViewHolder {
        ItemSubscribedSubredditMultiSelectionBinding binding;

        SubscribedSubredditViewHolder(@NonNull ItemSubscribedSubredditMultiSelectionBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
            binding.nameTextViewItemSubscribedSubredditMultiselection.setTextColor(primaryTextColor);
            binding.checkboxItemSubscribedSubredditMultiselection.setButtonTintList(ColorStateList.valueOf(colorAccent));

            if (activity.typeface != null) {
                binding.nameTextViewItemSubscribedSubredditMultiselection.setTypeface(activity.typeface);
            }

            // Read the position at click time: setSubscribedSubreddits replaces the whole list and
            // calls notifyDataSetChanged(), which does not rebind until the next layout pass, so an
            // index captured during bind could toggle a different subreddit — silently, since the
            // change only surfaces later via getAllSelectedSubreddits().
            binding.checkboxItemSubscribedSubredditMultiselection.setOnClickListener(view -> {
                int position = getBindingAdapterPosition();
                if (position == RecyclerView.NO_POSITION) {
                    return;
                }

                SubredditWithSelection subreddit = subscribedSubreddits.get(position);
                boolean nowSelected = !subreddit.isSelected();
                subreddit.setSelected(nowSelected);
                binding.checkboxItemSubscribedSubredditMultiselection.setChecked(nowSelected);
            });
            itemView.setOnClickListener(view ->
                    binding.checkboxItemSubscribedSubredditMultiselection.performClick());
        }
    }
}