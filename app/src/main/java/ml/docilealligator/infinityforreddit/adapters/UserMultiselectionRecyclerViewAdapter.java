package ml.docilealligator.infinityforreddit.adapters;

import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
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
import ml.docilealligator.infinityforreddit.databinding.ItemSubscribedUserMultiSelectionBinding;
import ml.docilealligator.infinityforreddit.subscribeduser.SubscribedUserData;
import ml.docilealligator.infinityforreddit.user.UserWithSelection;

public class UserMultiselectionRecyclerViewAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private final BaseActivity activity;
    private ArrayList<UserWithSelection> subscribedUsers;
    private final RequestManager glide;
    private final int primaryTextColor;
    private final int colorAccent;

    public UserMultiselectionRecyclerViewAdapter(BaseActivity activity, CustomThemeWrapper customThemeWrapper) {
        this.activity = activity;
        glide = Glide.with(activity);
        primaryTextColor = customThemeWrapper.getPrimaryTextColor();
        colorAccent = customThemeWrapper.getColorAccent();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new SubscribedUserViewHolder(ItemSubscribedUserMultiSelectionBinding
                .inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof SubscribedUserViewHolder) {
            ((SubscribedUserViewHolder) holder).binding.nameTextViewItemSubscribedUserMultiselection.setText(subscribedUsers.get(position).getName());
            glide.load(subscribedUsers.get(position).getIconUrl())
                    .transform(new RoundedCornersTransformation(72, 0))
                    .error(glide.load(R.drawable.subreddit_default_icon)
                            .transform(new RoundedCornersTransformation(72, 0)))
                    .into(((SubscribedUserViewHolder) holder).binding.iconGifImageViewItemSubscribedUserMultiselection);
            ((SubscribedUserViewHolder) holder).binding.checkboxItemSubscribedUserMultiselection.setChecked(subscribedUsers.get(position).isSelected());
            ((SubscribedUserViewHolder) holder).binding.checkboxItemSubscribedUserMultiselection.setOnClickListener(view -> {
                if (subscribedUsers.get(position).isSelected()) {
                    ((SubscribedUserViewHolder) holder).binding.checkboxItemSubscribedUserMultiselection.setChecked(false);
                    subscribedUsers.get(position).setSelected(false);
                } else {
                    ((SubscribedUserViewHolder) holder).binding.checkboxItemSubscribedUserMultiselection.setChecked(true);
                    subscribedUsers.get(position).setSelected(true);
                }
            });
            ((SubscribedUserViewHolder) holder).itemView.setOnClickListener(view ->
                    ((SubscribedUserViewHolder) holder).binding.checkboxItemSubscribedUserMultiselection.performClick());
        }
    }

    @Override
    public int getItemCount() {
        return subscribedUsers == null ? 0 : subscribedUsers.size();
    }

    @Override
    public void onViewRecycled(@NonNull RecyclerView.ViewHolder holder) {
        super.onViewRecycled(holder);
        if (holder instanceof SubscribedUserViewHolder) {
            glide.clear(((SubscribedUserViewHolder) holder).binding.iconGifImageViewItemSubscribedUserMultiselection);
        }
    }

    public void setSubscribedUsers(List<SubscribedUserData> subscribedUsers, String selectedUsers) {
        this.subscribedUsers = UserWithSelection.convertSubscribedUsers(subscribedUsers);

        Set<String> selectedSet = new HashSet<>();
        if (selectedUsers != null && !selectedUsers.isEmpty()) {
            for (String name : selectedUsers.split(",")) {
                String trimmed = name.trim();
                if (!trimmed.isEmpty()) {
                    selectedSet.add(trimmed);
                }
            }
        }

        for (UserWithSelection u : this.subscribedUsers) {
            u.setSelected(selectedSet.contains(u.getName()));
        }

        notifyDataSetChanged();
    }


    public ArrayList<String> getAllSelectedUsers() {
        ArrayList<String> selectedUsers = new ArrayList<>();
        for (UserWithSelection s : subscribedUsers) {
            if (s.isSelected()) {
                selectedUsers.add(s.getName());
            }
        }
        return selectedUsers;
    }

    class SubscribedUserViewHolder extends RecyclerView.ViewHolder {
        ItemSubscribedUserMultiSelectionBinding binding;

        SubscribedUserViewHolder(@NonNull ItemSubscribedUserMultiSelectionBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
            binding.nameTextViewItemSubscribedUserMultiselection.setTextColor(primaryTextColor);
            binding.checkboxItemSubscribedUserMultiselection.setButtonTintList(ColorStateList.valueOf(colorAccent));

            if (activity.typeface != null) {
                binding.nameTextViewItemSubscribedUserMultiselection.setTypeface(activity.typeface);
            }
        }
    }
}