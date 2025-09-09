package ml.docilealligator.infinityforreddit.adapters.navigationdrawer;

import android.content.Intent;
import android.content.SharedPreferences;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import ml.docilealligator.infinityforreddit.R;
import ml.docilealligator.infinityforreddit.activities.BaseActivity;
import ml.docilealligator.infinityforreddit.activities.InboxActivity;
import ml.docilealligator.infinityforreddit.customtheme.CustomThemeWrapper;
import ml.docilealligator.infinityforreddit.databinding.ItemNavDrawerMenuGroupTitleBinding;
import ml.docilealligator.infinityforreddit.databinding.ItemNavDrawerMenuItemBinding;
import ml.docilealligator.infinityforreddit.utils.SharedPreferencesUtils;

public class AccountSectionRecyclerViewAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_MENU_GROUP_TITLE = 1;
    private static final int VIEW_TYPE_MENU_ITEM = 2;
    private final int ACCOUNT_SECTION_ITEMS;
    private final int ANONYMOUS_ACCOUNT_SECTION_ITEMS ;

    private final BaseActivity baseActivity;
    private int inboxCount;
    private final int primaryTextColor;
    private final int secondaryTextColor;
    private final int primaryIconColor;
    private boolean collapseAccountSection;
    private final boolean hideAccountSection;
    private final boolean hideProfileButton;
    private final boolean hideSubscriptionsButton;
    private final boolean hideMultiredditButton;
    private final boolean hideInboxButton;
    private final boolean hideHistoryButton;
    private final boolean isLoggedIn;
    private final NavigationDrawerRecyclerViewMergedAdapter.ItemClickListener itemClickListener;

    public AccountSectionRecyclerViewAdapter(BaseActivity baseActivity, CustomThemeWrapper customThemeWrapper,
                                             SharedPreferences navigationDrawerSharedPreferences, boolean isLoggedIn,
                                             NavigationDrawerRecyclerViewMergedAdapter.ItemClickListener itemClickListener) {
        this.baseActivity = baseActivity;
        primaryTextColor = customThemeWrapper.getPrimaryTextColor();
        secondaryTextColor = customThemeWrapper.getSecondaryTextColor();
        primaryIconColor = customThemeWrapper.getPrimaryIconColor();
        collapseAccountSection = navigationDrawerSharedPreferences.getBoolean(SharedPreferencesUtils.COLLAPSE_ACCOUNT_SECTION, false);
        hideAccountSection = navigationDrawerSharedPreferences.getBoolean(SharedPreferencesUtils.HIDE_ACCOUNT_SECTION, false);
        hideProfileButton = navigationDrawerSharedPreferences.getBoolean(SharedPreferencesUtils.HIDE_PROFILE_BUTTON, false);
        hideSubscriptionsButton = navigationDrawerSharedPreferences.getBoolean(SharedPreferencesUtils.HIDE_SUBSCRIPTIONS_BUTTON, false);
        hideMultiredditButton = navigationDrawerSharedPreferences.getBoolean(SharedPreferencesUtils.HIDE_MULTIREDDIT_BUTTON, false);
        hideInboxButton = navigationDrawerSharedPreferences.getBoolean(SharedPreferencesUtils.HIDE_INBOX_BUTTON, false);
        hideHistoryButton = navigationDrawerSharedPreferences.getBoolean(SharedPreferencesUtils.HIDE_HISTORY_BUTTON, false);

        int tmpACCOUNT_SECTION_ITEMS = 5;
        int tmpANONYMOUS_ACCOUNT_SECTION_ITEMS = 3;
        if(hideProfileButton){
            tmpACCOUNT_SECTION_ITEMS--;
        }
        if(hideSubscriptionsButton){
            tmpACCOUNT_SECTION_ITEMS--;
            tmpANONYMOUS_ACCOUNT_SECTION_ITEMS--;
        }
        if(hideMultiredditButton){
            tmpACCOUNT_SECTION_ITEMS--;
            tmpANONYMOUS_ACCOUNT_SECTION_ITEMS--;
        }
        if(hideInboxButton){
            tmpACCOUNT_SECTION_ITEMS--;
        }
        if(hideHistoryButton){
            tmpACCOUNT_SECTION_ITEMS--;
            tmpANONYMOUS_ACCOUNT_SECTION_ITEMS--;
        }
        if(hideAccountSection) {
            tmpACCOUNT_SECTION_ITEMS = 0;
            tmpANONYMOUS_ACCOUNT_SECTION_ITEMS = 0;
        }
        ACCOUNT_SECTION_ITEMS = tmpACCOUNT_SECTION_ITEMS;
        ANONYMOUS_ACCOUNT_SECTION_ITEMS = tmpANONYMOUS_ACCOUNT_SECTION_ITEMS;
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
            if (hideAccountSection) {
                vh.itemView.setVisibility(View.GONE);
                vh.binding.titleTextViewItemNavDrawerMenuGroupTitle.setText(null);
                vh.binding.collapseIndicatorImageViewItemNavDrawerMenuGroupTitle.setImageDrawable(null);
                vh.itemView.setOnClickListener(null);
                return;
            }
            vh.itemView.setVisibility(View.VISIBLE);
            vh.binding.titleTextViewItemNavDrawerMenuGroupTitle.setText(R.string.label_account);
            if (collapseAccountSection) {
                vh.binding.collapseIndicatorImageViewItemNavDrawerMenuGroupTitle.setImageResource(R.drawable.ic_baseline_arrow_drop_up_24dp);
            } else {
                vh.binding.collapseIndicatorImageViewItemNavDrawerMenuGroupTitle.setImageResource(R.drawable.ic_baseline_arrow_drop_down_24dp);
            }
            vh.itemView.setOnClickListener(view -> {
                if (collapseAccountSection) {
                    collapseAccountSection = false;
                    notifyItemRangeInserted(vh.getBindingAdapterPosition() + 1, isLoggedIn ? ACCOUNT_SECTION_ITEMS : ANONYMOUS_ACCOUNT_SECTION_ITEMS);
                } else {
                    collapseAccountSection = true;
                    notifyItemRangeRemoved(vh.getBindingAdapterPosition() + 1, isLoggedIn ? ACCOUNT_SECTION_ITEMS : ANONYMOUS_ACCOUNT_SECTION_ITEMS);
                }
                notifyItemChanged(vh.getBindingAdapterPosition());
            });
            return;
        }

        if (holder instanceof MenuItemViewHolder vh) {
            vh.itemView.setVisibility(View.VISIBLE);
            vh.binding.textViewItemNavDrawerMenuItem.setText(null);
            vh.binding.imageViewItemNavDrawerMenuItem.setImageDrawable(null);
            vh.itemView.setOnClickListener(null);

            int stringId = 0;
            int drawableId = 0;
            boolean setOnClickListener = true;

            int targetIndex = position - 1;
            int visibleIndex = 0;
            boolean matched = false;

            if (isLoggedIn) {
                if (!hideAccountSection && !hideProfileButton) {
                    if (visibleIndex == targetIndex) {
                        stringId = R.string.profile;
                        drawableId = R.drawable.ic_account_circle_day_night_24dp;
                        matched = true;
                    }
                    visibleIndex++;
                }
                if (!hideSubscriptionsButton) {
                    if (!matched && visibleIndex == targetIndex) {
                        stringId = R.string.subscriptions;
                        drawableId = R.drawable.ic_subscriptions_bottom_app_bar_day_night_24dp;
                        matched = true;
                    }
                    visibleIndex++;
                }
                if (!hideMultiredditButton) {
                    if (!matched && visibleIndex == targetIndex) {
                        stringId = R.string.multi_reddit;
                        drawableId = R.drawable.ic_multi_reddit_day_night_24dp;
                        matched = true;
                    }
                    visibleIndex++;
                }
                if (!hideInboxButton) {
                    if (!matched && visibleIndex == targetIndex) {
                        setOnClickListener = false;
                        if (inboxCount > 0) vh.binding.textViewItemNavDrawerMenuItem.setText(baseActivity.getString(R.string.inbox_with_count, inboxCount));
                        else vh.binding.textViewItemNavDrawerMenuItem.setText(R.string.inbox);
                        vh.binding.imageViewItemNavDrawerMenuItem.setImageDrawable(ContextCompat.getDrawable(baseActivity, R.drawable.ic_inbox_day_night_24dp));
                        vh.itemView.setOnClickListener(view -> {
                            Intent intent = new Intent(baseActivity, InboxActivity.class);
                            baseActivity.startActivity(intent);
                        });
                        matched = true;
                    }
                    visibleIndex++;
                }

            } else {
                if (!hideSubscriptionsButton) {
                    if (visibleIndex == targetIndex) {
                        stringId = R.string.subscriptions;
                        drawableId = R.drawable.ic_subscriptions_bottom_app_bar_day_night_24dp;
                        matched = true;
                    }
                    visibleIndex++;
                }
                if (!hideMultiredditButton) {
                    if (!matched && visibleIndex == targetIndex) {
                        stringId = R.string.multi_reddit;
                        drawableId = R.drawable.ic_multi_reddit_day_night_24dp;
                        matched = true;
                    }
                    visibleIndex++;
                }
            }
            if (!hideHistoryButton) {
                if (!matched && visibleIndex == targetIndex) {
                    stringId = R.string.history;
                    drawableId = R.drawable.ic_history_day_night_24dp;
                    matched = true;
                }
            }
            if (!matched) {
                vh.itemView.setVisibility(View.GONE);
                vh.binding.textViewItemNavDrawerMenuItem.setText(null);
                vh.binding.imageViewItemNavDrawerMenuItem.setImageDrawable(null);
                vh.itemView.setOnClickListener(null);
                return;
            }
            vh.itemView.setVisibility(View.VISIBLE);
            if (stringId != 0) {
                vh.binding.textViewItemNavDrawerMenuItem.setText(stringId);
                vh.binding.imageViewItemNavDrawerMenuItem.setImageDrawable(ContextCompat.getDrawable(baseActivity, drawableId));
            }
            if (setOnClickListener) {
                int finalStringId = stringId;
                vh.itemView.setOnClickListener(view -> itemClickListener.onMenuClick(finalStringId));
            }
        }
    }



    @Override
    public int getItemCount() {
        if (hideAccountSection)
            return 0;
        if (collapseAccountSection)
            return 1;

        return isLoggedIn ? ACCOUNT_SECTION_ITEMS + 1 : ANONYMOUS_ACCOUNT_SECTION_ITEMS + 1;
    }


    public void setInboxCount(int inboxCount) {
        if (inboxCount < 0) {
            this.inboxCount = Math.max(0, this.inboxCount + inboxCount);
        } else {
            this.inboxCount = inboxCount;
        }
        notifyDataSetChanged();
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
