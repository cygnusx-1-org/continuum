package ml.docilealligator.infinityforreddit.adapters.navigationdrawer;

import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.view.LayoutInflater;
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

public class PreferenceSectionRecyclerViewAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_MENU_GROUP_TITLE = 1;
    private static final int VIEW_TYPE_MENU_ITEM = 2;
    private static final int PREFERENCES_SECTION_ITEMS = 3;

    private final BaseActivity baseActivity;
    private final Resources resources;
    private final int primaryTextColor;
    private final int secondaryTextColor;
    private final int primaryIconColor;
    private boolean isNSFWEnabled;
    private boolean collapsePreferencesSection;
    private final boolean hideThemeButton;
    private final boolean hideNSFWButton;
    private final NavigationDrawerRecyclerViewMergedAdapter.ItemClickListener itemClickListener;

    public PreferenceSectionRecyclerViewAdapter(BaseActivity baseActivity, CustomThemeWrapper customThemeWrapper,
                                                @NonNull String accountName, SharedPreferences nsfwAndSpoilerSharedPreferences,
                                                SharedPreferences navigationDrawerSharedPreferences,
                                                NavigationDrawerRecyclerViewMergedAdapter.ItemClickListener itemClickListener) {
        this.baseActivity = baseActivity;
        resources = baseActivity.getResources();
        primaryTextColor = customThemeWrapper.getPrimaryTextColor();
        secondaryTextColor = customThemeWrapper.getSecondaryTextColor();
        primaryIconColor = customThemeWrapper.getPrimaryIconColor();
        isNSFWEnabled = nsfwAndSpoilerSharedPreferences.getBoolean(
                (accountName.equals("ANONYMOUS_ACCOUNT") ? "" : accountName) + SharedPreferencesUtils.NSFW_BASE, false
        );
        collapsePreferencesSection = navigationDrawerSharedPreferences.getBoolean(SharedPreferencesUtils.COLLAPSE_PREFERENCES_SECTION, false);
        hideThemeButton = navigationDrawerSharedPreferences.getBoolean("hide_theme_button", false);
        hideNSFWButton = navigationDrawerSharedPreferences.getBoolean("hide_nsfw_button", false);
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
            vh.binding.titleTextViewItemNavDrawerMenuGroupTitle.setText(R.string.label_preferences);
            vh.binding.collapseIndicatorImageViewItemNavDrawerMenuGroupTitle.setImageResource(
                    collapsePreferencesSection ? R.drawable.ic_baseline_arrow_drop_up_24dp
                            : R.drawable.ic_baseline_arrow_drop_down_24dp
            );
            holder.itemView.setOnClickListener(view -> {
                collapsePreferencesSection = !collapsePreferencesSection;
                int visibleItems = getVisibleItemCount();
                if (collapsePreferencesSection) {
                    notifyItemRangeRemoved(holder.getBindingAdapterPosition() + 1, visibleItems);
                } else {
                    notifyItemRangeInserted(holder.getBindingAdapterPosition() + 1, visibleItems);
                }
                notifyItemChanged(holder.getBindingAdapterPosition());
            });
        } else if (holder instanceof MenuItemViewHolder vh) {
            int stringId = 0;
            int drawableId = 0;
            boolean setOnClickListener = true;

            int targetIndex = position - 1;
            int visibleIndex = 0;

            if (!hideThemeButton) {
                if (visibleIndex == targetIndex) {
                    if ((resources.getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) != Configuration.UI_MODE_NIGHT_YES) {
                        stringId = R.string.dark_theme;
                        drawableId = R.drawable.ic_dark_theme_24dp;
                    } else {
                        stringId = R.string.light_theme;
                        drawableId = R.drawable.ic_light_theme_24dp;
                    }
                }
                visibleIndex++;
            }

            if (!hideNSFWButton) {
                if (visibleIndex == targetIndex) {
                    setOnClickListener = false;
                    if (isNSFWEnabled) {
                        stringId = R.string.disable_nsfw;
                        drawableId = R.drawable.ic_nsfw_off_day_night_24dp;
                    } else {
                        stringId = R.string.enable_nsfw;
                        drawableId = R.drawable.ic_nsfw_on_day_night_24dp;
                    }

                    holder.itemView.setOnClickListener(view -> {
                        isNSFWEnabled = !isNSFWEnabled;
                        vh.binding.textViewItemNavDrawerMenuItem.setText(
                                isNSFWEnabled ? R.string.disable_nsfw : R.string.enable_nsfw
                        );
                        vh.binding.imageViewItemNavDrawerMenuItem.setImageDrawable(
                                ContextCompat.getDrawable(baseActivity,
                                        isNSFWEnabled ? R.drawable.ic_nsfw_off_day_night_24dp : R.drawable.ic_nsfw_on_day_night_24dp
                                )
                        );
                        itemClickListener.onMenuClick(isNSFWEnabled ? R.string.enable_nsfw : R.string.disable_nsfw);
                    });
                }
                visibleIndex++;
            }

            if (visibleIndex == targetIndex) {
                stringId = R.string.settings;
                drawableId = R.drawable.ic_settings_day_night_24dp;
            }

            if (stringId != 0) {
                vh.binding.textViewItemNavDrawerMenuItem.setText(stringId);
                vh.binding.imageViewItemNavDrawerMenuItem.setImageDrawable(ContextCompat.getDrawable(baseActivity, drawableId));
                if (setOnClickListener) {
                    int finalStringId = stringId;
                    holder.itemView.setOnClickListener(view -> itemClickListener.onMenuClick(finalStringId));
                }
            }
        }
    }

    @Override
    public int getItemCount() {
        return collapsePreferencesSection ? 1 : getVisibleItemCount() + 1;
    }

    private int getVisibleItemCount() {
        int count = PREFERENCES_SECTION_ITEMS;
        if (hideThemeButton) count--;
        if (hideNSFWButton) count--;
        return count;
    }

    public void setNSFWEnabled(boolean isNSFWEnabled) {
        this.isNSFWEnabled = isNSFWEnabled;
        if (!hideNSFWButton) {
            int nsfwIndex = 1;
            if (!hideThemeButton) nsfwIndex++;
            notifyItemChanged(nsfwIndex);
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
