package ml.docilealligator.infinityforreddit.adapters;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.Executor;

import jp.wasabeef.glide.transformations.RoundedCornersTransformation;
import me.zhanghai.android.fastscroll.PopupTextProvider;
import ml.docilealligator.infinityforreddit.R;
import ml.docilealligator.infinityforreddit.RedditDataRoomDatabase;
import ml.docilealligator.infinityforreddit.activities.BaseActivity;
import ml.docilealligator.infinityforreddit.customtheme.CustomThemeWrapper;
import ml.docilealligator.infinityforreddit.databinding.ItemFavoriteThingDividerBinding;
import ml.docilealligator.infinityforreddit.databinding.ItemMultiRedditBinding;
import ml.docilealligator.infinityforreddit.multireddit.MultiReddit;

/**
 * Renders the locally-followed "Users MultiReddits" tab: feeds grouped under their owner's username
 * as a section header (owners alphabetical, feeds alphabetical within each owner). Each feed has a
 * heart toggle that marks it a favorite, persisted locally (followed feeds aren't owned, so this
 * never hits Reddit's favorite API).
 */
public class FollowedMultiRedditListingRecyclerViewAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> implements PopupTextProvider {

    private static final int VIEW_TYPE_HEADER = 0;
    private static final int VIEW_TYPE_MULTI_REDDIT = 1;

    private final BaseActivity mActivity;
    private final Executor mExecutor;
    private final RedditDataRoomDatabase mRedditDataRoomDatabase;
    private final RequestManager mGlide;
    private final int mPrimaryTextColor;
    private final int mSecondaryTextColor;
    private final MultiRedditListingRecyclerViewAdapter.OnItemClickListener mOnItemClickListener;
    private final List<Object> mRows = new ArrayList<>();

    public FollowedMultiRedditListingRecyclerViewAdapter(BaseActivity activity, Executor executor,
                                                         RedditDataRoomDatabase redditDataRoomDatabase,
                                                         CustomThemeWrapper customThemeWrapper,
                                                         MultiRedditListingRecyclerViewAdapter.OnItemClickListener onItemClickListener) {
        mActivity = activity;
        mExecutor = executor;
        mRedditDataRoomDatabase = redditDataRoomDatabase;
        mGlide = Glide.with(activity);
        mPrimaryTextColor = customThemeWrapper.getPrimaryTextColor();
        mSecondaryTextColor = customThemeWrapper.getSecondaryTextColor();
        mOnItemClickListener = onItemClickListener;
    }

    @Override
    public int getItemViewType(int position) {
        return mRows.get(position) instanceof String ? VIEW_TYPE_HEADER : VIEW_TYPE_MULTI_REDDIT;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == VIEW_TYPE_HEADER) {
            return new HeaderViewHolder(ItemFavoriteThingDividerBinding
                    .inflate(LayoutInflater.from(parent.getContext()), parent, false));
        }
        return new MultiRedditViewHolder(ItemMultiRedditBinding
                .inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof HeaderViewHolder) {
            ((HeaderViewHolder) holder).binding.dividerTextViewItemFavoriteThingDivider.setText((String) mRows.get(position));
            return;
        }

        MultiRedditViewHolder viewHolder = (MultiRedditViewHolder) holder;
        MultiReddit multiReddit = (MultiReddit) mRows.get(position);

        viewHolder.binding.multiRedditNameTextViewItemMultiReddit.setText(multiReddit.getDisplayName());

        String iconUrl = multiReddit.getIconUrl();
        if (iconUrl != null && !iconUrl.isEmpty()) {
            mGlide.load(iconUrl)
                    .transform(new RoundedCornersTransformation(72, 0))
                    .error(mGlide.load(R.drawable.subreddit_default_icon)
                            .transform(new RoundedCornersTransformation(72, 0)))
                    .into(viewHolder.binding.multiRedditIconGifImageViewItemMultiReddit);
        } else {
            mGlide.load(R.drawable.subreddit_default_icon)
                    .transform(new RoundedCornersTransformation(72, 0))
                    .into(viewHolder.binding.multiRedditIconGifImageViewItemMultiReddit);
        }

        bindHeart(viewHolder, multiReddit.isFavorite());

        viewHolder.binding.favoriteImageViewItemMultiReddit.setOnClickListener(view -> {
            boolean nowFavorite = !multiReddit.isFavorite();
            multiReddit.setFavorite(nowFavorite);
            bindHeart(viewHolder, nowFavorite);
            mExecutor.execute(() -> mRedditDataRoomDatabase.multiRedditDao().insert(multiReddit));
        });

        viewHolder.itemView.setOnClickListener(view -> mOnItemClickListener.onClick(multiReddit));
        viewHolder.itemView.setOnLongClickListener(view -> {
            mOnItemClickListener.onLongClick(multiReddit);
            return true;
        });
    }

    private void bindHeart(MultiRedditViewHolder holder, boolean favorite) {
        holder.binding.favoriteImageViewItemMultiReddit.setImageResource(
                favorite ? R.drawable.ic_favorite_24dp : R.drawable.ic_favorite_border_24dp);
    }

    @Override
    public int getItemCount() {
        return mRows.size();
    }

    @Override
    public void onViewRecycled(@NonNull RecyclerView.ViewHolder holder) {
        if (holder instanceof MultiRedditViewHolder) {
            mGlide.clear(((MultiRedditViewHolder) holder).binding.multiRedditIconGifImageViewItemMultiReddit);
        }
    }

    public void setMultiReddits(List<MultiReddit> multiReddits) {
        List<Object> newRows = buildRows(multiReddits);
        DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new RowsDiffCallback(mRows, newRows));
        mRows.clear();
        mRows.addAll(newRows);
        diff.dispatchUpdatesTo(this);
    }

    private List<Object> buildRows(List<MultiReddit> multiReddits) {
        List<Object> rows = new ArrayList<>();
        if (multiReddits != null && !multiReddits.isEmpty()) {
            Map<String, List<MultiReddit>> byOwner = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            for (MultiReddit multiReddit : multiReddits) {
                String owner = ownerOf(multiReddit);
                List<MultiReddit> list = byOwner.get(owner);
                if (list == null) {
                    list = new ArrayList<>();
                    byOwner.put(owner, list);
                }
                list.add(multiReddit);
            }
            for (Map.Entry<String, List<MultiReddit>> entry : byOwner.entrySet()) {
                rows.add(entry.getKey());
                List<MultiReddit> list = entry.getValue();
                Collections.sort(list, Comparator.comparing(MultiReddit::getDisplayName, String.CASE_INSENSITIVE_ORDER));
                rows.addAll(list);
            }
        }
        return rows;
    }

    private static class RowsDiffCallback extends DiffUtil.Callback {
        private final List<Object> oldRows;
        private final List<Object> newRows;

        RowsDiffCallback(List<Object> oldRows, List<Object> newRows) {
            this.oldRows = oldRows;
            this.newRows = newRows;
        }

        @Override
        public int getOldListSize() {
            return oldRows.size();
        }

        @Override
        public int getNewListSize() {
            return newRows.size();
        }

        @Override
        public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
            Object oldRow = oldRows.get(oldItemPosition);
            Object newRow = newRows.get(newItemPosition);
            if (oldRow instanceof String && newRow instanceof String) {
                return oldRow.equals(newRow);
            }
            if (oldRow instanceof MultiReddit && newRow instanceof MultiReddit) {
                return ((MultiReddit) oldRow).getPath().equals(((MultiReddit) newRow).getPath());
            }
            return false;
        }

        @Override
        public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
            Object oldRow = oldRows.get(oldItemPosition);
            Object newRow = newRows.get(newItemPosition);
            if (oldRow instanceof MultiReddit && newRow instanceof MultiReddit) {
                MultiReddit oldMulti = (MultiReddit) oldRow;
                MultiReddit newMulti = (MultiReddit) newRow;
                return oldMulti.isFavorite() == newMulti.isFavorite()
                        && oldMulti.getDisplayName().equals(newMulti.getDisplayName())
                        && Objects.equals(oldMulti.getIconUrl(), newMulti.getIconUrl());
            }
            return true;
        }
    }

    /**
     * Owner is encoded in the path {@code /user/<owner>/m/<name>}; the username column holds the
     * current account for followed feeds, so we can't use {@link MultiReddit#getOwner()}.
     */
    private String ownerOf(MultiReddit multiReddit) {
        String path = multiReddit.getPath();
        if (path != null) {
            String[] segments = path.split("/");
            if (segments.length > 2 && "user".equals(segments[1])) {
                return segments[2];
            }
        }
        return multiReddit.getOwner();
    }

    @NonNull
    @Override
    public String getPopupText(@NonNull android.view.View view, int position) {
        Object row = mRows.get(position);
        String text = row instanceof String ? (String) row : ((MultiReddit) row).getDisplayName();
        return text.isEmpty() ? "" : text.substring(0, 1).toUpperCase();
    }

    class HeaderViewHolder extends RecyclerView.ViewHolder {
        ItemFavoriteThingDividerBinding binding;

        HeaderViewHolder(@NonNull ItemFavoriteThingDividerBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
            if (mActivity.typeface != null) {
                binding.dividerTextViewItemFavoriteThingDivider.setTypeface(mActivity.typeface);
            }
            binding.dividerTextViewItemFavoriteThingDivider.setTextColor(mSecondaryTextColor);
        }
    }

    class MultiRedditViewHolder extends RecyclerView.ViewHolder {
        ItemMultiRedditBinding binding;

        MultiRedditViewHolder(@NonNull ItemMultiRedditBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
            if (mActivity.typeface != null) {
                binding.multiRedditNameTextViewItemMultiReddit.setTypeface(mActivity.typeface);
            }
            binding.multiRedditNameTextViewItemMultiReddit.setTextColor(mPrimaryTextColor);
        }
    }
}
