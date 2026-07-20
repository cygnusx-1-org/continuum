package ml.docilealligator.infinityforreddit.adapters;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.RequestManager;
import java.util.ArrayList;
import java.util.List;
import jp.wasabeef.glide.transformations.RoundedCornersTransformation;
import ml.docilealligator.infinityforreddit.R;
import ml.docilealligator.infinityforreddit.account.Account;
import ml.docilealligator.infinityforreddit.activities.BaseActivity;
import ml.docilealligator.infinityforreddit.customtheme.CustomThemeWrapper;
import ml.docilealligator.infinityforreddit.databinding.ItemNavDrawerAccountBinding;

public class AccountChooserRecyclerViewAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private final BaseActivity baseActivity;
    private List<Account> accounts = new ArrayList<>();
    private final RequestManager glide;
    private final int primaryTextColor;
    private final ItemClickListener itemClickListener;

    public AccountChooserRecyclerViewAdapter(BaseActivity baseActivity, CustomThemeWrapper customThemeWrapper,
                                             RequestManager glide, ItemClickListener itemClickListener) {
        this.baseActivity = baseActivity;
        this.glide = glide;
        primaryTextColor = customThemeWrapper.getPrimaryTextColor();
        this.itemClickListener = itemClickListener;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new AccountViewHolder(ItemNavDrawerAccountBinding
                .inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof AccountViewHolder) {
            Account account = accounts.get(position);
            AccountViewHolder accountViewHolder = (AccountViewHolder) holder;

            glide.load(account.getProfileImageUrl())
                    .error(glide.load(R.drawable.subreddit_default_icon))
                    .transform(new RoundedCornersTransformation(128, 0))
                    .into(accountViewHolder.binding.profileImageItemAccount);
            accountViewHolder.binding.usernameTextViewItemAccount.setText(account.getAccountName());
        }
    }

    @Override
    public int getItemCount() {
        return accounts.size();
    }

    public void changeAccountsDataset(List<Account> accounts) {
        this.accounts = accounts;
        notifyDataSetChanged();
    }

    class AccountViewHolder extends RecyclerView.ViewHolder {
        ItemNavDrawerAccountBinding binding;

        AccountViewHolder(@NonNull ItemNavDrawerAccountBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
            if (baseActivity.typeface != null) {
                binding.usernameTextViewItemAccount.setTypeface(baseActivity.typeface);
            }
            binding.usernameTextViewItemAccount.setTextColor(primaryTextColor);

            // Read the position at click time, not the one captured during bind: the accounts
            // LiveData can re-emit while the chooser is open, and notifyDataSetChanged() does not
            // rebind until the next layout pass. A stale index here would submit as the wrong
            // account, which the user gets no visual cue about.
            itemView.setOnClickListener(view -> {
                int position = getBindingAdapterPosition();
                if (position == RecyclerView.NO_POSITION) {
                    return;
                }

                itemClickListener.onClick(accounts.get(position));
            });
        }
    }

    public interface ItemClickListener {
        void onClick(Account account);
    }
}
