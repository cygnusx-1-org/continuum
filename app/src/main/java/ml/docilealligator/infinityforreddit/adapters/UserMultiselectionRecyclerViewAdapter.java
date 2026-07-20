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
import ml.docilealligator.infinityforreddit.databinding.ItemSubscribedUserMultiSelectionBinding;
import ml.docilealligator.infinityforreddit.subscribeduser.SubscribedUserData;
import ml.docilealligator.infinityforreddit.user.UserWithSelection;

public class UserMultiselectionRecyclerViewAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private final BaseActivity activity;
    private ArrayList<UserWithSelection> subscribedUsers = new ArrayList<>();
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
            UserWithSelection user = subscribedUsers.get(position);
            SubscribedUserViewHolder userViewHolder = (SubscribedUserViewHolder) holder;

            userViewHolder.binding.nameTextViewItemSubscribedUserMultiselection.setText(user.getName());
            glide.load(user.getIconUrl())
                    .transform(new RoundedCornersTransformation(72, 0))
                    .error(glide.load(R.drawable.subreddit_default_icon)
                            .transform(new RoundedCornersTransformation(72, 0)))
                    .into(userViewHolder.binding.iconGifImageViewItemSubscribedUserMultiselection);
            userViewHolder.binding.checkboxItemSubscribedUserMultiselection.setChecked(user.isSelected());
        }
    }

    @Override
    public int getItemCount() {
        return subscribedUsers.size();
    }

    @Override
    public void onViewRecycled(@NonNull RecyclerView.ViewHolder holder) {
        super.onViewRecycled(holder);
        if (holder instanceof SubscribedUserViewHolder) {
            glide.clear(((SubscribedUserViewHolder) holder).binding.iconGifImageViewItemSubscribedUserMultiselection);
        }
    }

    public void setSubscribedUsers(List<SubscribedUserData> subscribedUsers, @Nullable String selectedUsers) {
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

            // Read the position at click time: setSubscribedUsers replaces the whole list and calls
            // notifyDataSetChanged(), which does not rebind until the next layout pass, so an index
            // captured during bind could toggle a different user — silently, since the change only
            // surfaces later via getAllSelectedUsers().
            binding.checkboxItemSubscribedUserMultiselection.setOnClickListener(view -> {
                int position = getBindingAdapterPosition();
                if (position == RecyclerView.NO_POSITION) {
                    return;
                }

                UserWithSelection user = subscribedUsers.get(position);
                boolean nowSelected = !user.isSelected();
                user.setSelected(nowSelected);
                binding.checkboxItemSubscribedUserMultiselection.setChecked(nowSelected);
            });
            itemView.setOnClickListener(view ->
                    binding.checkboxItemSubscribedUserMultiselection.performClick());
        }
    }
}