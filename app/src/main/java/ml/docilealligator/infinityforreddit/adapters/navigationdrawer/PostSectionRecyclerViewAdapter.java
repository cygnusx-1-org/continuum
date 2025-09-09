package ml.docilealligator.infinityforreddit.adapters.navigationdrawer;

import android.content.SharedPreferences;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import ml.docilealligator.infinityforreddit.R;
import ml.docilealligator.infinityforreddit.activities.BaseActivity;
import ml.docilealligator.infinityforreddit.customtheme.CustomThemeWrapper;
import ml.docilealligator.infinityforreddit.databinding.ItemNavDrawerMenuGroupTitleBinding;
import ml.docilealligator.infinityforreddit.databinding.ItemNavDrawerMenuItemBinding;
import ml.docilealligator.infinityforreddit.utils.SharedPreferencesUtils;

public class PostSectionRecyclerViewAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_MENU_GROUP_TITLE = 1;
    private static final int VIEW_TYPE_MENU_ITEM = 2;
    private final int POST_SECTION_ITEMS;

    private final BaseActivity baseActivity;
    private final int primaryTextColor;
    private final int secondaryTextColor;
    private final int primaryIconColor;
    private boolean collapsePostSection;
    private final boolean hidePostSection;
    private final boolean hideUpvotedButton;
    private final boolean hideDownvotedButton;
    private final boolean hideHiddenButton;
    private final boolean hideSavedButton;
    private final boolean isLoggedIn;
    private final NavigationDrawerRecyclerViewMergedAdapter.ItemClickListener itemClickListener;

    public PostSectionRecyclerViewAdapter(BaseActivity baseActivity, CustomThemeWrapper customThemeWrapper,
                                          SharedPreferences navigationDrawerSharedPreferences,
                                          boolean isLoggedIn, NavigationDrawerRecyclerViewMergedAdapter.ItemClickListener itemClickListener) {
        this.baseActivity = baseActivity;
        primaryTextColor = customThemeWrapper.getPrimaryTextColor();
        secondaryTextColor = customThemeWrapper.getSecondaryTextColor();
        primaryIconColor = customThemeWrapper.getPrimaryIconColor();
        collapsePostSection = navigationDrawerSharedPreferences.getBoolean(SharedPreferencesUtils.COLLAPSE_POST_SECTION, false);
        hidePostSection = navigationDrawerSharedPreferences.getBoolean(SharedPreferencesUtils.HIDE_POST_SECTION, false);
        hideUpvotedButton = navigationDrawerSharedPreferences.getBoolean(SharedPreferencesUtils.HIDE_UPVOTED_BUTTON, false);
        hideDownvotedButton = navigationDrawerSharedPreferences.getBoolean(SharedPreferencesUtils.HIDE_DOWNVOTED_BUTTON, false);
        hideHiddenButton = navigationDrawerSharedPreferences.getBoolean(SharedPreferencesUtils.HIDE_HIDDEN_BUTTON, false);
        hideSavedButton = navigationDrawerSharedPreferences.getBoolean(SharedPreferencesUtils.HIDE_SAVED_BUTTON, false);
        int count = 4;
        if (hideUpvotedButton) count--;
        if (hideDownvotedButton) count--;
        if (hideHiddenButton) count--;
        if (hideSavedButton) count--;
        POST_SECTION_ITEMS = count;

        this.isLoggedIn = isLoggedIn;
        this.itemClickListener = itemClickListener;
    }

    @Override
    public int getItemViewType(int position) {
        return position == 0 ? VIEW_TYPE_MENU_GROUP_TITLE : VIEW_TYPE_MENU_ITEM;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == VIEW_TYPE_MENU_GROUP_TITLE) {
            return new MenuGroupTitleViewHolder(ItemNavDrawerMenuGroupTitleBinding
                    .inflate(LayoutInflater.from(parent.getContext()), parent, false));
        } else {
            return new MenuItemViewHolder(ItemNavDrawerMenuItemBinding
                    .inflate(LayoutInflater.from(parent.getContext()), parent, false));
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof MenuGroupTitleViewHolder vh) {
            vh.binding.titleTextViewItemNavDrawerMenuGroupTitle.setText(R.string.label_post);
            if (collapsePostSection) {
                vh.binding.collapseIndicatorImageViewItemNavDrawerMenuGroupTitle.setImageResource(R.drawable.ic_baseline_arrow_drop_up_24dp);
            } else {
                vh.binding.collapseIndicatorImageViewItemNavDrawerMenuGroupTitle.setImageResource(R.drawable.ic_baseline_arrow_drop_down_24dp);
            }

            holder.itemView.setOnClickListener(view -> {
                if (collapsePostSection) {
                    collapsePostSection = false;
                    notifyItemRangeInserted(holder.getBindingAdapterPosition() + 1, POST_SECTION_ITEMS);
                } else {
                    collapsePostSection = true;
                    notifyItemRangeRemoved(holder.getBindingAdapterPosition() + 1, POST_SECTION_ITEMS);
                }
                notifyItemChanged(holder.getBindingAdapterPosition());
            });
        }
        else if (holder instanceof MenuItemViewHolder vh) {
            vh.itemView.setVisibility(View.VISIBLE);
            vh.binding.textViewItemNavDrawerMenuItem.setText(null);
            vh.binding.imageViewItemNavDrawerMenuItem.setImageDrawable(null);
            vh.itemView.setOnClickListener(null);

            int targetIndex = position - 1;
            int visibleIndex = 0;
            int stringId = 0;
            int drawableId = 0;
            boolean matched = false;
            if (!hideUpvotedButton) {
                if (visibleIndex == targetIndex) {
                    stringId = R.string.upvoted;
                    drawableId = R.drawable.ic_arrow_upward_day_night_24dp;
                    matched = true;
                }
                visibleIndex++;
            }
            if (!hideDownvotedButton && !matched) {
                if (visibleIndex == targetIndex) {
                    stringId = R.string.downvoted;
                    drawableId = R.drawable.ic_arrow_downward_day_night_24dp;
                    matched = true;
                }
                visibleIndex++;
            }
            if (!hideHiddenButton && !matched) {
                if (visibleIndex == targetIndex) {
                    stringId = R.string.hidden;
                    drawableId = R.drawable.ic_lock_day_night_24dp;
                    matched = true;
                }
                visibleIndex++;
            }

            if (!hideSavedButton && !matched) {
                if (visibleIndex == targetIndex) {
                    stringId = R.string.account_saved_thing_activity_label;
                    drawableId = R.drawable.ic_bookmarks_day_night_24dp;
                    matched = true;
                }
            }
            if (!matched) {
                vh.itemView.setVisibility(View.GONE);
                return;
            }

            vh.binding.textViewItemNavDrawerMenuItem.setText(stringId);
            vh.binding.imageViewItemNavDrawerMenuItem.setImageDrawable(ContextCompat.getDrawable(baseActivity, drawableId));
            int finalStringId = stringId;
            vh.itemView.setOnClickListener(view -> itemClickListener.onMenuClick(finalStringId));
        }
    }

    @Override
    public int getItemCount() {
        if (!isLoggedIn || hidePostSection) return 0;
        return collapsePostSection ? 1: POST_SECTION_ITEMS + 1;
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

    class MenuItemViewHolder extends RecyclerView.ViewHolder {
        ItemNavDrawerMenuItemBinding binding;

        MenuItemViewHolder(@NonNull ItemNavDrawerMenuItemBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
            if (baseActivity.typeface != null) {
                binding.textViewItemNavDrawerMenuItem.setTypeface(baseActivity.typeface);
            }
            binding.textViewItemNavDrawerMenuItem.setTextColor(primaryTextColor);
            binding.imageViewItemNavDrawerMenuItem.setColorFilter(primaryIconColor, android.graphics.PorterDuff.Mode.SRC_IN);
        }
    }
}
