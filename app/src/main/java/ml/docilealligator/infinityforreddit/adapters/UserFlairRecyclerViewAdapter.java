package ml.docilealligator.infinityforreddit.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import ml.docilealligator.infinityforreddit.R;
import ml.docilealligator.infinityforreddit.activities.BaseActivity;
import ml.docilealligator.infinityforreddit.customtheme.CustomThemeWrapper;
import ml.docilealligator.infinityforreddit.databinding.ItemUserFlairBinding;
import ml.docilealligator.infinityforreddit.user.UserFlair;
import ml.docilealligator.infinityforreddit.utils.Utils;

public class UserFlairRecyclerViewAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private final BaseActivity activity;
    private final CustomThemeWrapper customThemeWrapper;
    private final ArrayList<UserFlair> userFlairs;
    private final ItemClickListener itemClickListener;

    public UserFlairRecyclerViewAdapter(BaseActivity activity, CustomThemeWrapper customThemeWrapper,
                                        ArrayList<UserFlair> userFlairs,
                                        ItemClickListener itemClickListener) {
        this.activity = activity;
        this.customThemeWrapper = customThemeWrapper;
        this.userFlairs = userFlairs;
        this.itemClickListener = itemClickListener;
    }

    public interface ItemClickListener {
        void onClick(@Nullable UserFlair userFlair, boolean editUserFlair);
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new UserFlairViewHolder(ItemUserFlairBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof UserFlairViewHolder) {
            if (position == 0) {
                ((UserFlairViewHolder) holder).binding.userFlairHtmlTextViewItemUserFlair.setText(R.string.clear_user_flair);
                ((UserFlairViewHolder) holder).binding.editUserFlairImageViewItemUserFlair.setVisibility(View.GONE);
            } else {
                UserFlair userFlair = userFlairs.get(position - 1);
                if (userFlair.getHtmlText() == null || userFlair.getHtmlText().equals("")) {
                    ((UserFlairViewHolder) holder).binding.userFlairHtmlTextViewItemUserFlair.setText(userFlair.getText());
                } else {
                    Utils.setHTMLWithImageToTextView(((UserFlairViewHolder) holder).binding.userFlairHtmlTextViewItemUserFlair, userFlair.getHtmlText(), true);
                }
                if (userFlair.isEditable()) {
                    ((UserFlairViewHolder) holder).binding.editUserFlairImageViewItemUserFlair.setVisibility(View.VISIBLE);
                } else {
                    ((UserFlairViewHolder) holder).binding.editUserFlairImageViewItemUserFlair.setVisibility(View.GONE);
                }
            }
        }
    }

    @Override
    public int getItemCount() {
        // The flairs plus the leading "clear flair" row.
        return userFlairs.size() + 1;
    }

    class UserFlairViewHolder extends RecyclerView.ViewHolder {
        ItemUserFlairBinding binding;

        public UserFlairViewHolder(@NonNull ItemUserFlairBinding binding) {
            super(binding.getRoot());
            this.binding = binding;

            binding.userFlairHtmlTextViewItemUserFlair.setTextColor(customThemeWrapper.getPrimaryTextColor());
            binding.editUserFlairImageViewItemUserFlair.setColorFilter(customThemeWrapper.getPrimaryTextColor(), android.graphics.PorterDuff.Mode.SRC_IN);

            if (activity.typeface != null) {
                binding.userFlairHtmlTextViewItemUserFlair.setTypeface(activity.typeface);
            }

            itemView.setOnClickListener(view -> {
                int position = getBindingAdapterPosition();
                if (position == RecyclerView.NO_POSITION) {
                    return;
                }

                if (position == 0) {
                    itemClickListener.onClick(null, false);
                } else {
                    itemClickListener.onClick(userFlairs.get(position - 1), false);
                }
            });

            binding.editUserFlairImageViewItemUserFlair.setOnClickListener(view -> {
                // Position 0 is the "clear flair" row, whose edit icon onBindViewHolder hides, so
                // this only ever fires for a real flair.
                int position = getBindingAdapterPosition();
                if (position == RecyclerView.NO_POSITION || position == 0) {
                    return;
                }

                itemClickListener.onClick(userFlairs.get(position - 1), true);
            });
        }
    }
}
