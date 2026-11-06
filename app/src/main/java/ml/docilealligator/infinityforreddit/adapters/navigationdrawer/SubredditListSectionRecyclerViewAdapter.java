package ml.docilealligator.infinityforreddit.adapters.navigationdrawer;

import android.content.SharedPreferences;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
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
import ml.docilealligator.infinityforreddit.subscribedsubreddit.SubscribedSubredditData;

/**
 * A titled list of subreddits in the navigation drawer, which can be shown, collapsed or hidden.
 * The favorites and the subscriptions are two of these.
 */
public class SubredditListSectionRecyclerViewAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_MENU_GROUP_TITLE = 1;
    private static final int VIEW_TYPE_SUBREDDIT = 2;

    private final BaseActivity baseActivity;
    private final RequestManager glide;
    private final int primaryTextColor;
    private final int secondaryTextColor;
    private final SharedPreferences navigationDrawerSharedPreferences;
    private final DrawerSection section;
    @StringRes
    private final int titleRes;
    private DrawerSectionState state;
    private ArrayList<SubscribedSubredditData> subreddits = new ArrayList<>();
    private final NavigationDrawerRecyclerViewMergedAdapter.ItemClickListener itemClickListener;

    public SubredditListSectionRecyclerViewAdapter(BaseActivity baseActivity, RequestManager glide,
                                                   CustomThemeWrapper customThemeWrapper,
                                                   SharedPreferences navigationDrawerSharedPreferences,
                                                   DrawerSection section, @StringRes int titleRes,
                                                   NavigationDrawerRecyclerViewMergedAdapter.ItemClickListener itemClickListener) {
        this.baseActivity = baseActivity;
        this.glide = glide;
        primaryTextColor = customThemeWrapper.getPrimaryTextColor();
        secondaryTextColor = customThemeWrapper.getSecondaryTextColor();
        this.navigationDrawerSharedPreferences = navigationDrawerSharedPreferences;
        this.section = section;
        this.titleRes = titleRes;
        this.state = section.read(navigationDrawerSharedPreferences);
        this.itemClickListener = itemClickListener;
    }

    public void setState(DrawerSectionState state) {
        this.state = state;
        notifyDataSetChanged();
    }

    public void setSubreddits(List<SubscribedSubredditData> subreddits) {
        this.subreddits = (ArrayList<SubscribedSubredditData>) subreddits;
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return position == 0 ? VIEW_TYPE_MENU_GROUP_TITLE : VIEW_TYPE_SUBREDDIT;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == VIEW_TYPE_MENU_GROUP_TITLE) {
            return new MenuGroupTitleViewHolder(ItemNavDrawerMenuGroupTitleBinding
                    .inflate(LayoutInflater.from(parent.getContext()), parent, false));
        } else {
            return new SubredditViewHolder(ItemNavDrawerSubscribedThingBinding
                    .inflate(LayoutInflater.from(parent.getContext()), parent, false));
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof MenuGroupTitleViewHolder) {
            ItemNavDrawerMenuGroupTitleBinding binding = ((MenuGroupTitleViewHolder) holder).binding;
            binding.titleTextViewItemNavDrawerMenuGroupTitle.setText(titleRes);
            binding.collapseIndicatorImageViewItemNavDrawerMenuGroupTitle.setImageResource(
                    state == DrawerSectionState.COLLAPSED
                            ? R.drawable.ic_baseline_arrow_drop_up_24dp : R.drawable.ic_baseline_arrow_drop_down_24dp);

            holder.itemView.setOnClickListener(view -> {
                boolean wasCollapsed = state == DrawerSectionState.COLLAPSED;
                state = wasCollapsed ? DrawerSectionState.SHOWN : DrawerSectionState.COLLAPSED;
                // Kept, so it is still collapsed (or not) after a restart.
                section.write(navigationDrawerSharedPreferences, state);
                if (wasCollapsed) {
                    notifyItemRangeInserted(holder.getBindingAdapterPosition() + 1, subreddits.size());
                } else {
                    notifyItemRangeRemoved(holder.getBindingAdapterPosition() + 1, subreddits.size());
                }
                notifyItemChanged(holder.getBindingAdapterPosition());
            });
        } else if (holder instanceof SubredditViewHolder) {
            SubscribedSubredditData subreddit = subreddits.get(position - 1);
            String subredditName = subreddit.getName();
            String iconUrl = subreddit.getIconUrl();
            ((SubredditViewHolder) holder).binding.thingNameTextViewItemNavDrawerSubscribedThing.setText(subredditName);
            if (iconUrl != null && !iconUrl.equals("")) {
                glide.load(iconUrl)
                        .transform(new RoundedCornersTransformation(72, 0))
                        .error(glide.load(R.drawable.subreddit_default_icon)
                                .transform(new RoundedCornersTransformation(72, 0)))
                        .into(((SubredditViewHolder) holder).binding.thingIconGifImageViewItemNavDrawerSubscribedThing);
            } else {
                glide.load(R.drawable.subreddit_default_icon)
                        .transform(new RoundedCornersTransformation(72, 0))
                        .into(((SubredditViewHolder) holder).binding.thingIconGifImageViewItemNavDrawerSubscribedThing);
            }

            holder.itemView.setOnClickListener(view -> {
                itemClickListener.onSubscribedSubredditClick(subredditName);
            });
        }
    }

    @Override
    public int getItemCount() {
        if (state == DrawerSectionState.HIDDEN) {
            return 0;
        }
        return subreddits.isEmpty() ? 0 : (state == DrawerSectionState.COLLAPSED ? 1 : subreddits.size() + 1);
    }

    @Override
    public void onViewRecycled(@NonNull RecyclerView.ViewHolder holder) {
        super.onViewRecycled(holder);
        if (holder instanceof SubredditViewHolder) {
            glide.clear(((SubredditViewHolder) holder).binding.thingIconGifImageViewItemNavDrawerSubscribedThing);
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

    class SubredditViewHolder extends RecyclerView.ViewHolder {
        ItemNavDrawerSubscribedThingBinding binding;

        SubredditViewHolder(@NonNull ItemNavDrawerSubscribedThingBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
            if (baseActivity.typeface != null) {
                binding.thingNameTextViewItemNavDrawerSubscribedThing.setTypeface(baseActivity.typeface);
            }
            binding.thingNameTextViewItemNavDrawerSubscribedThing.setTextColor(primaryTextColor);
        }
    }
}
