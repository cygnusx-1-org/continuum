package ml.docilealligator.infinityforreddit.adapters.navigationdrawer;

import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;
import ml.docilealligator.infinityforreddit.R;
import ml.docilealligator.infinityforreddit.account.Account;
import ml.docilealligator.infinityforreddit.activities.BaseActivity;
import ml.docilealligator.infinityforreddit.customtheme.CustomThemeWrapper;
import ml.docilealligator.infinityforreddit.databinding.ItemNavDrawerMenuGroupTitleBinding;
import ml.docilealligator.infinityforreddit.databinding.ItemNavDrawerMenuItemBinding;
import ml.docilealligator.infinityforreddit.utils.SharedPreferencesUtils;

public class PreferenceSectionRecyclerViewAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_MENU_GROUP_TITLE = 1;
    private static final int VIEW_TYPE_MENU_ITEM = 2;

    private static final int ROW_REMINDERS = 0;
    private static final int ROW_THEME = 1;
    private static final int ROW_NSFW = 2;
    private static final int ROW_THUMBNAIL = 3;
    private static final int ROW_SETTINGS = 4;

    // Canonical top-to-bottom order of the rows. visibleRows is always a subsequence of this,
    // which lets refreshVisibleRows() dispatch minimal insert/remove notifications.
    private static final int[] ROW_ORDER = {ROW_REMINDERS, ROW_THEME, ROW_NSFW, ROW_THUMBNAIL, ROW_SETTINGS};

    private final BaseActivity baseActivity;
    private final Resources resources;
    private final int primaryTextColor;
    private final int secondaryTextColor;
    private final int primaryIconColor;
    private boolean isNSFWEnabled;
    private boolean showThumbnailOnTheLeft;
    private boolean showRemindersToggle;
    private boolean showThemeToggle;
    private boolean showNSFWToggle;
    private boolean showThumbnailToggle;
    private boolean collapsePreferencesSection;
    private final List<Integer> visibleRows = new ArrayList<>();
    private final NavigationDrawerRecyclerViewMergedAdapter.ItemClickListener itemClickListener;

    public PreferenceSectionRecyclerViewAdapter(BaseActivity baseActivity, CustomThemeWrapper customThemeWrapper,
                                                @NonNull String accountName, SharedPreferences sharedPreferences,
                                                SharedPreferences nsfwAndSpoilerSharedPreferences,
                                                SharedPreferences navigationDrawerSharedPreferences,
                                                NavigationDrawerRecyclerViewMergedAdapter.ItemClickListener itemClickListener) {
        this.baseActivity = baseActivity;
        resources = baseActivity.getResources();
        primaryTextColor = customThemeWrapper.getPrimaryTextColor();
        secondaryTextColor = customThemeWrapper.getSecondaryTextColor();
        primaryIconColor = customThemeWrapper.getPrimaryIconColor();
        isNSFWEnabled = nsfwAndSpoilerSharedPreferences.getBoolean((accountName.equals(Account.ANONYMOUS_ACCOUNT) ? "" : accountName) + SharedPreferencesUtils.NSFW_BASE, false);
        showThumbnailOnTheLeft = sharedPreferences.getBoolean(SharedPreferencesUtils.SHOW_THUMBNAIL_ON_THE_LEFT_IN_COMPACT_LAYOUT, false);
        showRemindersToggle = navigationDrawerSharedPreferences.getBoolean(SharedPreferencesUtils.SHOW_REMINDERS_TOGGLE_IN_NAVIGATION_DRAWER, true);
        showThemeToggle = navigationDrawerSharedPreferences.getBoolean(SharedPreferencesUtils.SHOW_THEME_TOGGLE_IN_NAVIGATION_DRAWER, true);
        showNSFWToggle = navigationDrawerSharedPreferences.getBoolean(SharedPreferencesUtils.SHOW_NSFW_TOGGLE_IN_NAVIGATION_DRAWER, true);
        showThumbnailToggle = navigationDrawerSharedPreferences.getBoolean(SharedPreferencesUtils.SHOW_THUMBNAIL_ON_THE_LEFT_TOGGLE_IN_NAVIGATION_DRAWER, false);
        collapsePreferencesSection = navigationDrawerSharedPreferences.getBoolean(SharedPreferencesUtils.COLLAPSE_PREFERENCES_SECTION, false);
        this.itemClickListener = itemClickListener;
        buildVisibleRows();
    }

    private boolean isRowVisible(int rowType) {
        switch (rowType) {
            case ROW_REMINDERS:
                return showRemindersToggle;
            case ROW_THEME:
                return showThemeToggle;
            case ROW_NSFW:
                return showNSFWToggle;
            case ROW_THUMBNAIL:
                return showThumbnailToggle;
            case ROW_SETTINGS:
                return true;
            default:
                return false;
        }
    }

    private void buildVisibleRows() {
        visibleRows.clear();
        for (int rowType : ROW_ORDER) {
            if (isRowVisible(rowType)) {
                visibleRows.add(rowType);
            }
        }
    }

    public void setCollapsePreferencesSection(boolean collapsePreferencesSection) {
        if (this.collapsePreferencesSection == collapsePreferencesSection) {
            return;
        }
        this.collapsePreferencesSection = collapsePreferencesSection;
        notifyDataSetChanged();
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
        if (holder instanceof MenuGroupTitleViewHolder) {
            ((MenuGroupTitleViewHolder) holder).binding.titleTextViewItemNavDrawerMenuGroupTitle.setText(R.string.label_preferences);
            if (collapsePreferencesSection) {
                ((MenuGroupTitleViewHolder) holder).binding.collapseIndicatorImageViewItemNavDrawerMenuGroupTitle.setImageResource(R.drawable.ic_baseline_arrow_drop_up_24dp);
            } else {
                ((MenuGroupTitleViewHolder) holder).binding.collapseIndicatorImageViewItemNavDrawerMenuGroupTitle.setImageResource(R.drawable.ic_baseline_arrow_drop_down_24dp);
            }

            holder.itemView.setOnClickListener(view -> {
                int titlePosition = holder.getBindingAdapterPosition();
                if (titlePosition == RecyclerView.NO_POSITION) {
                    return;
                }
                if (collapsePreferencesSection) {
                    collapsePreferencesSection = false;
                    notifyItemRangeInserted(titlePosition + 1, visibleRows.size());
                } else {
                    collapsePreferencesSection = true;
                    notifyItemRangeRemoved(titlePosition + 1, visibleRows.size());
                }
                notifyItemChanged(titlePosition);
            });
        } else if (holder instanceof MenuItemViewHolder) {
            MenuItemViewHolder itemHolder = (MenuItemViewHolder) holder;
            int row = visibleRows.get(position - 1);
            int stringId = 0;
            int drawableId = 0;

            switch (row) {
                case ROW_REMINDERS:
                    stringId = R.string.reminders;
                    drawableId = R.drawable.ic_reminder_day_night_24dp;
                    itemHolder.itemView.setOnClickListener(view -> itemClickListener.onMenuClick(R.string.reminders));
                    break;
                case ROW_THEME:
                    if ((resources.getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) != Configuration.UI_MODE_NIGHT_YES) {
                        stringId = R.string.dark_theme;
                        drawableId = R.drawable.ic_dark_theme_24dp;
                    } else {
                        stringId = R.string.light_theme;
                        drawableId = R.drawable.ic_light_theme_24dp;
                    }
                    int themeStringId = stringId;
                    itemHolder.itemView.setOnClickListener(view -> itemClickListener.onMenuClick(themeStringId));
                    break;
                case ROW_NSFW:
                    if (isNSFWEnabled) {
                        stringId = R.string.disable_nsfw;
                        drawableId = R.drawable.ic_nsfw_off_day_night_24dp;
                    } else {
                        stringId = R.string.enable_nsfw;
                        drawableId = R.drawable.ic_nsfw_on_day_night_24dp;
                    }
                    // The label/icon are refreshed via setNSFWEnabled() when the resulting
                    // ChangeNSFWEvent is delivered back, so there is no inline update here
                    // (a single, authoritative update path, mirroring the thumbnail row).
                    int nsfwStringId = stringId;
                    itemHolder.itemView.setOnClickListener(view -> itemClickListener.onMenuClick(nsfwStringId));
                    break;
                case ROW_THUMBNAIL:
                    drawableId = R.drawable.ic_thumbnail_left_day_night_24dp;
                    stringId = showThumbnailOnTheLeft
                            ? R.string.settings_show_thumbnail_on_the_right_in_compact_layout
                            : R.string.settings_show_thumbnail_on_the_left_in_compact_layout;
                    // The label is refreshed via setShowThumbnailOnTheLeft() when the resulting
                    // ShowThumbnailOnTheLeftInCompactLayoutEvent is delivered back, so there is no
                    // inline update here (a single, authoritative update path).
                    itemHolder.itemView.setOnClickListener(view ->
                            itemClickListener.onMenuClick(R.string.settings_show_thumbnail_on_the_left_in_compact_layout));
                    break;
                case ROW_SETTINGS:
                    stringId = R.string.settings;
                    drawableId = R.drawable.ic_settings_day_night_24dp;
                    itemHolder.itemView.setOnClickListener(view -> itemClickListener.onMenuClick(R.string.settings));
                    break;
                default:
                    throw new IllegalStateException("Unexpected preference row: " + row);
            }

            if (stringId != 0) {
                itemHolder.binding.textViewItemNavDrawerMenuItem.setText(stringId);
            }
            if (drawableId != 0) {
                itemHolder.binding.imageViewItemNavDrawerMenuItem.setImageDrawable(ContextCompat.getDrawable(baseActivity, drawableId));
            }
        }
    }

    @Override
    public int getItemCount() {
        return collapsePreferencesSection ? 1 : visibleRows.size() + 1;
    }

    public void setNSFWEnabled(boolean isNSFWEnabled) {
        this.isNSFWEnabled = isNSFWEnabled;
        int index = visibleRows.indexOf(ROW_NSFW);
        if (index >= 0 && !collapsePreferencesSection) {
            notifyItemChanged(index + 1);
        }
    }

    public void setShowThumbnailOnTheLeft(boolean showThumbnailOnTheLeft) {
        this.showThumbnailOnTheLeft = showThumbnailOnTheLeft;
        int index = visibleRows.indexOf(ROW_THUMBNAIL);
        if (index >= 0 && !collapsePreferencesSection) {
            notifyItemChanged(index + 1);
        }
    }

    public void refreshVisibleRows(SharedPreferences navigationDrawerSharedPreferences) {
        showRemindersToggle = navigationDrawerSharedPreferences.getBoolean(SharedPreferencesUtils.SHOW_REMINDERS_TOGGLE_IN_NAVIGATION_DRAWER, true);
        showThemeToggle = navigationDrawerSharedPreferences.getBoolean(SharedPreferencesUtils.SHOW_THEME_TOGGLE_IN_NAVIGATION_DRAWER, true);
        showNSFWToggle = navigationDrawerSharedPreferences.getBoolean(SharedPreferencesUtils.SHOW_NSFW_TOGGLE_IN_NAVIGATION_DRAWER, true);
        showThumbnailToggle = navigationDrawerSharedPreferences.getBoolean(SharedPreferencesUtils.SHOW_THUMBNAIL_ON_THE_LEFT_TOGGLE_IN_NAVIGATION_DRAWER, false);

        if (collapsePreferencesSection) {
            // Only the title is shown while collapsed (item count stays 1), so just rebuild the
            // list; the correct rows appear when the section is next expanded.
            buildVisibleRows();
            return;
        }

        List<Integer> oldRows = new ArrayList<>(visibleRows);
        buildVisibleRows();
        // Both lists are subsequences of ROW_ORDER, so a single walk yields the minimal set of
        // insert/remove notifications (offset by 1 for the always-present title at position 0).
        int oldIndex = 0;
        int newIndex = 0;
        for (int rowType : ROW_ORDER) {
            boolean inOld = oldIndex < oldRows.size() && oldRows.get(oldIndex) == rowType;
            boolean inNew = newIndex < visibleRows.size() && visibleRows.get(newIndex) == rowType;
            if (inOld && inNew) {
                oldIndex++;
                newIndex++;
            } else if (inNew) {
                notifyItemInserted(newIndex + 1);
                newIndex++;
            } else if (inOld) {
                notifyItemRemoved(newIndex + 1);
                oldIndex++;
            }
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
