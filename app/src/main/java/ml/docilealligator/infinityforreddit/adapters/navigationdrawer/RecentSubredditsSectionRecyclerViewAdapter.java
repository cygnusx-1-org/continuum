package ml.docilealligator.infinityforreddit.adapters.navigationdrawer;

import android.content.SharedPreferences;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.RequestManager;
import java.util.ArrayList;
import java.util.List;
import jp.wasabeef.glide.transformations.RoundedCornersTransformation;
import ml.docilealligator.infinityforreddit.R;
import ml.docilealligator.infinityforreddit.activities.BaseActivity;
import ml.docilealligator.infinityforreddit.customtheme.CustomThemeWrapper;
import ml.docilealligator.infinityforreddit.databinding.ItemNavDrawerMenuGroupTitleBinding;
import ml.docilealligator.infinityforreddit.databinding.ItemNavDrawerSubscribedThingBinding;
import ml.docilealligator.infinityforreddit.recentsubreddits.RecentSubreddits;
import ml.docilealligator.infinityforreddit.utils.SharedPreferencesUtils;

/**
 * The Recents section: the last few subreddits opened, above Favorites and Subscriptions.
 */
public class RecentSubredditsSectionRecyclerViewAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_MENU_GROUP_TITLE = 1;
    private static final int VIEW_TYPE_RECENT_SUBREDDIT = 2;

    private final BaseActivity baseActivity;
    private final RequestManager glide;
    private final int primaryTextColor;
    private final int secondaryTextColor;
    private boolean collapseSection;
    private boolean hideSection;
    private final SharedPreferences navigationDrawerSharedPreferences;
    private final NavigationDrawerRecyclerViewMergedAdapter.ItemClickListener itemClickListener;
    private List<RecentSubreddits.Entry> recentSubreddits = new ArrayList<>();

    public RecentSubredditsSectionRecyclerViewAdapter(BaseActivity baseActivity, RequestManager glide,
                                                      CustomThemeWrapper customThemeWrapper,
                                                      SharedPreferences navigationDrawerSharedPreferences,
                                                      NavigationDrawerRecyclerViewMergedAdapter.ItemClickListener itemClickListener) {
        this.baseActivity = baseActivity;
        this.glide = glide;
        primaryTextColor = customThemeWrapper.getPrimaryTextColor();
        secondaryTextColor = customThemeWrapper.getSecondaryTextColor();
        this.navigationDrawerSharedPreferences = navigationDrawerSharedPreferences;
        collapseSection = navigationDrawerSharedPreferences.getBoolean(SharedPreferencesUtils.COLLAPSE_RECENT_SUBREDDITS_SECTION, false);
        hideSection = navigationDrawerSharedPreferences.getBoolean(SharedPreferencesUtils.HIDE_RECENT_SUBREDDITS_SECTION, false);
        this.itemClickListener = itemClickListener;
    }

    public void setCollapseSection(boolean collapseSection) {
        this.collapseSection = collapseSection;
        notifyDataSetChanged();
    }

    public void setHideSection(boolean hideSection) {
        this.hideSection = hideSection;
        notifyDataSetChanged();
    }

    public void setRecentSubreddits(List<RecentSubreddits.Entry> recentSubreddits) {
        this.recentSubreddits = recentSubreddits;
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return position == 0 ? VIEW_TYPE_MENU_GROUP_TITLE : VIEW_TYPE_RECENT_SUBREDDIT;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == VIEW_TYPE_MENU_GROUP_TITLE) {
            return new MenuGroupTitleViewHolder(ItemNavDrawerMenuGroupTitleBinding
                    .inflate(LayoutInflater.from(parent.getContext()), parent, false));
        } else {
            return new RecentSubredditViewHolder(ItemNavDrawerSubscribedThingBinding
                    .inflate(LayoutInflater.from(parent.getContext()), parent, false));
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof MenuGroupTitleViewHolder) {
            ItemNavDrawerMenuGroupTitleBinding binding = ((MenuGroupTitleViewHolder) holder).binding;
            binding.titleTextViewItemNavDrawerMenuGroupTitle.setText(R.string.recents);
            binding.collapseIndicatorImageViewItemNavDrawerMenuGroupTitle.setImageResource(collapseSection
                    ? R.drawable.ic_baseline_arrow_drop_up_24dp : R.drawable.ic_baseline_arrow_drop_down_24dp);

            holder.itemView.setOnClickListener(view -> {
                collapseSection = !collapseSection;
                // Kept, so it is still collapsed (or not) after a restart.
                navigationDrawerSharedPreferences.edit()
                        .putBoolean(SharedPreferencesUtils.COLLAPSE_RECENT_SUBREDDITS_SECTION, collapseSection).apply();
                if (collapseSection) {
                    notifyItemRangeRemoved(holder.getBindingAdapterPosition() + 1, recentSubreddits.size());
                } else {
                    notifyItemRangeInserted(holder.getBindingAdapterPosition() + 1, recentSubreddits.size());
                }
                notifyItemChanged(holder.getBindingAdapterPosition());
            });
        } else if (holder instanceof RecentSubredditViewHolder) {
            RecentSubreddits.Entry subreddit = recentSubreddits.get(position - 1);
            ItemNavDrawerSubscribedThingBinding binding = ((RecentSubredditViewHolder) holder).binding;
            binding.thingNameTextViewItemNavDrawerSubscribedThing.setText(
                    subreddit.type == RecentSubreddits.TYPE_USER ? "u/" + subreddit.name : subreddit.name);
            if (subreddit.iconUrl != null && !subreddit.iconUrl.isEmpty()) {
                glide.load(subreddit.iconUrl)
                        .transform(new RoundedCornersTransformation(72, 0))
                        .error(glide.load(R.drawable.subreddit_default_icon)
                                .transform(new RoundedCornersTransformation(72, 0)))
                        .into(binding.thingIconGifImageViewItemNavDrawerSubscribedThing);
            } else {
                glide.load(R.drawable.subreddit_default_icon)
                        .transform(new RoundedCornersTransformation(72, 0))
                        .into(binding.thingIconGifImageViewItemNavDrawerSubscribedThing);
            }

            binding.pinImageViewItemNavDrawerSubscribedThing.setVisibility(View.VISIBLE);
            binding.pinImageViewItemNavDrawerSubscribedThing.setImageResource(subreddit.pinned
                    ? R.drawable.ic_pin_filled_24dp : R.drawable.ic_pin_outline_24dp);
            binding.pinImageViewItemNavDrawerSubscribedThing.setContentDescription(baseActivity.getString(
                    subreddit.pinned ? R.string.recents_unpin : R.string.recents_pin));
            binding.pinImageViewItemNavDrawerSubscribedThing.setColorFilter(
                    subreddit.pinned ? primaryTextColor : secondaryTextColor, android.graphics.PorterDuff.Mode.SRC_IN);
            binding.pinImageViewItemNavDrawerSubscribedThing.setOnClickListener(view ->
                    itemClickListener.onRecentPinToggle(subreddit.name, subreddit.type, !subreddit.pinned));
            binding.dismissImageViewItemNavDrawerSubscribedThing.setVisibility(View.VISIBLE);
            binding.dismissImageViewItemNavDrawerSubscribedThing.setColorFilter(
                    secondaryTextColor, android.graphics.PorterDuff.Mode.SRC_IN);
            binding.dismissImageViewItemNavDrawerSubscribedThing.setOnClickListener(view ->
                    itemClickListener.onRecentDismiss(subreddit.name, subreddit.type));

            holder.itemView.setOnClickListener(view -> {
                if (subreddit.type == RecentSubreddits.TYPE_USER) {
                    itemClickListener.onRecentUserClick(subreddit.name);
                } else {
                    itemClickListener.onSubscribedSubredditClick(subreddit.name);
                }
            });
        }
    }

    @Override
    public int getItemCount() {
        if (hideSection) {
            return 0;
        }
        return recentSubreddits.isEmpty() ? 0 : (collapseSection ? 1 : recentSubreddits.size() + 1);
    }

    @Override
    public void onViewRecycled(@NonNull RecyclerView.ViewHolder holder) {
        super.onViewRecycled(holder);
        if (holder instanceof RecentSubredditViewHolder) {
            glide.clear(((RecentSubredditViewHolder) holder).binding.thingIconGifImageViewItemNavDrawerSubscribedThing);
        }
    }

    class MenuGroupTitleViewHolder extends RecyclerView.ViewHolder {
        ItemNavDrawerMenuGroupTitleBinding binding;

        MenuGroupTitleViewHolder(@NonNull ItemNavDrawerMenuGroupTitleBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
            if (baseActivity.typeface != null) {
                binding.titleTextViewItemNavDrawerMenuGroupTitle.setTypeface(baseActivity.typeface);
            }
            binding.titleTextViewItemNavDrawerMenuGroupTitle.setTextColor(secondaryTextColor);
            binding.collapseIndicatorImageViewItemNavDrawerMenuGroupTitle.setColorFilter(secondaryTextColor, android.graphics.PorterDuff.Mode.SRC_IN);
        }
    }

    class RecentSubredditViewHolder extends RecyclerView.ViewHolder {
        ItemNavDrawerSubscribedThingBinding binding;

        RecentSubredditViewHolder(@NonNull ItemNavDrawerSubscribedThingBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
            if (baseActivity.typeface != null) {
                binding.thingNameTextViewItemNavDrawerSubscribedThing.setTypeface(baseActivity.typeface);
            }
            binding.thingNameTextViewItemNavDrawerSubscribedThing.setTextColor(primaryTextColor);
        }
    }
}
