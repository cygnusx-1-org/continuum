package ml.docilealligator.infinityforreddit.adapters;

import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestManager;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import jp.wasabeef.glide.transformations.RoundedCornersTransformation;
import ml.docilealligator.infinityforreddit.R;
import ml.docilealligator.infinityforreddit.activities.BaseActivity;
import ml.docilealligator.infinityforreddit.customtheme.CustomThemeWrapper;
import ml.docilealligator.infinityforreddit.databinding.ItemMultiRedditSelectionBinding;
import ml.docilealligator.infinityforreddit.multireddit.MultiReddit;

/**
 * Lists another user's public multireddits with a checkbox each, so the user can pick which ones to
 * save into the current account's locally-followed "Users MultiReddits". Selection is collected and
 * persisted by the hosting activity when the user confirms.
 */
public class UserMultiRedditsRecyclerViewAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private final BaseActivity mActivity;
    private final RequestManager mGlide;
    private final int mPrimaryTextColor;
    private final int mColorAccent;
    private final List<MultiReddit> mMultiReddits;
    private final Set<String> mSelectedPaths;

    public UserMultiRedditsRecyclerViewAdapter(BaseActivity activity, CustomThemeWrapper customThemeWrapper,
                                               List<MultiReddit> multiReddits, Set<String> initiallySelectedPaths) {
        mActivity = activity;
        mGlide = Glide.with(activity);
        mPrimaryTextColor = customThemeWrapper.getPrimaryTextColor();
        mColorAccent = customThemeWrapper.getColorAccent();
        mMultiReddits = multiReddits;
        mSelectedPaths = new HashSet<>(initiallySelectedPaths);
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new MultiRedditViewHolder(ItemMultiRedditSelectionBinding
                .inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        MultiReddit multiReddit = mMultiReddits.get(position);
        MultiRedditViewHolder viewHolder = (MultiRedditViewHolder) holder;

        viewHolder.binding.nameTextViewItemMultiRedditSelection.setText(multiReddit.getDisplayName());

        String iconUrl = multiReddit.getIconUrl();
        if (iconUrl != null && !iconUrl.isEmpty()) {
            mGlide.load(iconUrl)
                    .transform(new RoundedCornersTransformation(72, 0))
                    .error(mGlide.load(R.drawable.subreddit_default_icon)
                            .transform(new RoundedCornersTransformation(72, 0)))
                    .into(viewHolder.binding.iconGifImageViewItemMultiRedditSelection);
        } else {
            mGlide.load(R.drawable.subreddit_default_icon)
                    .transform(new RoundedCornersTransformation(72, 0))
                    .into(viewHolder.binding.iconGifImageViewItemMultiRedditSelection);
        }

        viewHolder.binding.checkboxItemMultiRedditSelection.setChecked(mSelectedPaths.contains(multiReddit.getPath()));
        viewHolder.itemView.setOnClickListener(view -> {
            boolean nowChecked = !mSelectedPaths.contains(multiReddit.getPath());
            if (nowChecked) {
                mSelectedPaths.add(multiReddit.getPath());
            } else {
                mSelectedPaths.remove(multiReddit.getPath());
            }
            viewHolder.binding.checkboxItemMultiRedditSelection.setChecked(nowChecked);
        });
        viewHolder.binding.checkboxItemMultiRedditSelection.setOnClickListener(view ->
                viewHolder.itemView.performClick());
    }

    @Override
    public int getItemCount() {
        return mMultiReddits.size();
    }

    @Override
    public void onViewRecycled(@NonNull RecyclerView.ViewHolder holder) {
        if (holder instanceof MultiRedditViewHolder) {
            mGlide.clear(((MultiRedditViewHolder) holder).binding.iconGifImageViewItemMultiRedditSelection);
        }
    }

    public Set<String> getSelectedPaths() {
        return mSelectedPaths;
    }

    class MultiRedditViewHolder extends RecyclerView.ViewHolder {
        ItemMultiRedditSelectionBinding binding;

        MultiRedditViewHolder(@NonNull ItemMultiRedditSelectionBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
            binding.nameTextViewItemMultiRedditSelection.setTextColor(mPrimaryTextColor);
            binding.checkboxItemMultiRedditSelection.setButtonTintList(ColorStateList.valueOf(mColorAccent));
            if (mActivity.typeface != null) {
                binding.nameTextViewItemMultiRedditSelection.setTypeface(mActivity.typeface);
            }
        }
    }
}
