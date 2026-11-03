package ml.docilealligator.infinityforreddit.adapters;

import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestManager;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import jp.wasabeef.glide.transformations.RoundedCornersTransformation;
import ml.docilealligator.infinityforreddit.R;
import ml.docilealligator.infinityforreddit.activities.BaseActivity;
import ml.docilealligator.infinityforreddit.customtheme.CustomThemeWrapper;
import ml.docilealligator.infinityforreddit.databinding.ItemSubscribedSubredditMultiSelectionBinding;
import ml.docilealligator.infinityforreddit.subreddit.ExcludedSubreddits;
import ml.docilealligator.infinityforreddit.subreddit.SubredditWithSelection;
import ml.docilealligator.infinityforreddit.subscribedsubreddit.SubscribedSubredditData;

public class SubredditMultiselectionRecyclerViewAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private final BaseActivity activity;
    private ArrayList<SubredditWithSelection> subscribedSubreddits = new ArrayList<>();
    private final RequestManager glide;
    private final int primaryTextColor;
    private final int colorAccent;
    /**
     * What the user has ticked, as {@link #key} names. Held apart from the rows because every
     * {@link #setSubscribedSubreddits} builds new ones: Room re-emits the subscriptions whenever the
     * table changes, and again to an activity recreated by a rotation, and ticks kept only on the
     * rows would go with each old copy.
     */
    private final Set<String> selectedNames = new HashSet<>();
    /** What the caller already has: left out of the list altogether. */
    private final ExcludedSubreddits excludedSubreddits;

    public SubredditMultiselectionRecyclerViewAdapter(BaseActivity activity, CustomThemeWrapper customThemeWrapper,
                                                      Collection<String> selectedNames,
                                                      ExcludedSubreddits excludedSubreddits) {
        this.activity = activity;
        glide = Glide.with(activity);
        primaryTextColor = customThemeWrapper.getPrimaryTextColor();
        colorAccent = customThemeWrapper.getColorAccent();
        for (String name : selectedNames) {
            this.selectedNames.add(key(name));
        }
        this.excludedSubreddits = excludedSubreddits;
    }

    /** Reddit treats subreddit names case-insensitively, so every comparison here does too. */
    private static String key(String name) {
        return name.toLowerCase(Locale.ROOT);
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

    public void setSubscribedSubreddits(List<SubscribedSubredditData> subscribedSubreddits) {
        ArrayList<SubscribedSubredditData> shown = new ArrayList<>();
        for (SubscribedSubredditData s : subscribedSubreddits) {
            if (!excludedSubreddits.contains(s.getName())) {
                shown.add(s);
            }
        }
        this.subscribedSubreddits = SubredditWithSelection.convertSubscribedSubreddits(shown);
        for (SubredditWithSelection s : this.subscribedSubreddits) {
            s.setSelected(selectedNames.contains(key(s.getName())));
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

    /** Everything ticked so far, for the activity to keep across a recreation. */
    public ArrayList<String> getSelectedNames() {
        return new ArrayList<>(selectedNames);
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
                if (nowSelected) {
                    selectedNames.add(key(subreddit.getName()));
                } else {
                    selectedNames.remove(key(subreddit.getName()));
                }
                binding.checkboxItemSubscribedSubredditMultiselection.setChecked(nowSelected);
            });
            itemView.setOnClickListener(view ->
                    binding.checkboxItemSubscribedSubredditMultiselection.performClick());
        }
    }
}