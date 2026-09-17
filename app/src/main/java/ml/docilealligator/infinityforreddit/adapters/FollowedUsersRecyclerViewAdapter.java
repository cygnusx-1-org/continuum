package ml.docilealligator.infinityforreddit.adapters;

import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestManager;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executor;
import jp.wasabeef.glide.transformations.RoundedCornersTransformation;
import me.zhanghai.android.fastscroll.PopupTextProvider;
import ml.docilealligator.infinityforreddit.R;
import ml.docilealligator.infinityforreddit.RedditDataRoomDatabase;
import ml.docilealligator.infinityforreddit.account.Account;
import ml.docilealligator.infinityforreddit.activities.BaseActivity;
import ml.docilealligator.infinityforreddit.customtheme.CustomThemeWrapper;
import ml.docilealligator.infinityforreddit.databinding.ItemFavoriteThingDividerBinding;
import ml.docilealligator.infinityforreddit.databinding.ItemSubscribedUserBinding;
import ml.docilealligator.infinityforreddit.subscribeduser.SubscribedUserData;
import ml.docilealligator.infinityforreddit.thing.FavoriteThing;
import ml.docilealligator.infinityforreddit.user.UserFollowing;
import ml.docilealligator.infinityforreddit.user.UserSaving;
import retrofit2.Retrofit;

/**
 * The Subscriptions -&gt; Users list. A row is here because the user is followed, saved, or both;
 * the three trailing toggles are follow, save and favourite, in that order. Clearing both follow
 * and save deletes the row, which the list then drops on its own via LiveData.
 */
@SuppressWarnings("NullAway.Init")
public class FollowedUsersRecyclerViewAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> implements PopupTextProvider {
    private static final int VIEW_TYPE_FAVORITE_USER_DIVIDER = 0;
    private static final int VIEW_TYPE_FAVORITE_USER = 1;
    private static final int VIEW_TYPE_USER_DIVIDER = 2;
    private static final int VIEW_TYPE_USER = 3;

    /** Every user the account follows or saves, as the list's query delivered them. */
    private List<SubscribedUserData> mSubscribedUserData;
    private List<SubscribedUserData> mFavoriteSubscribedUserData;
    /**
     * What the "All" group shows: {@link #mSubscribedUserData} without the favourites, which are
     * already listed in their own group above it. Derived here rather than asked of Room, because
     * the query behind the full list is shared with the user multi-selection screen, which has no
     * favourites group to move them to and so must keep showing every user.
     */
    private List<SubscribedUserData> mOtherSubscribedUserData = Collections.emptyList();
    private final BaseActivity mActivity;
    private final Executor mExecutor;
    private final Retrofit mOauthRetrofit;
    private final Retrofit mRetrofit;
    private final RedditDataRoomDatabase mRedditDataRoomDatabase;
    @Nullable
    private final String mAccessToken;
    private final String mAccountName;
    private final boolean mIsAnonymous;
    private final RequestManager glide;
    private final int mPrimaryTextColor;
    private final int mSecondaryTextColor;
    private final int mPrimaryIconColor;
    private final ItemOnClickListener itemOnClickListener;

    public FollowedUsersRecyclerViewAdapter(BaseActivity activity, Executor executor, Retrofit oauthRetrofit,
                                            Retrofit retrofit, RedditDataRoomDatabase redditDataRoomDatabase,
                                            CustomThemeWrapper customThemeWrapper,
                                            @Nullable String accessToken, @NonNull String accountName,
                                            ItemOnClickListener itemOnClickListener) {
        mActivity = activity;
        mExecutor = executor;
        mOauthRetrofit = oauthRetrofit;
        mRetrofit = retrofit;
        mRedditDataRoomDatabase = redditDataRoomDatabase;
        mAccessToken = accessToken;
        mAccountName = accountName;
        mIsAnonymous = Account.ANONYMOUS_ACCOUNT.equals(accountName);
        glide = Glide.with(activity);
        mPrimaryTextColor = customThemeWrapper.getPrimaryTextColor();
        mSecondaryTextColor = customThemeWrapper.getSecondaryTextColor();
        mPrimaryIconColor = customThemeWrapper.getPrimaryIconColor();
        this.itemOnClickListener = itemOnClickListener;
    }

    private boolean hasFavorites() {
        return mFavoriteSubscribedUserData != null && !mFavoriteSubscribedUserData.isEmpty();
    }

    private boolean hasOthers() {
        return !mOtherSubscribedUserData.isEmpty();
    }

    /**
     * Recomputed whenever either list arrives: the two are separate queries and reach the adapter
     * one at a time, so the group below has to be rebuilt from whichever came last.
     */
    private void rebuildOthers() {
        if (mSubscribedUserData == null || mSubscribedUserData.isEmpty()) {
            mOtherSubscribedUserData = Collections.emptyList();
            return;
        }
        List<SubscribedUserData> others = new ArrayList<>(mSubscribedUserData.size());
        for (SubscribedUserData user : mSubscribedUserData) {
            // The row's own flag, not a scan of the favourites list: the two queries can land a
            // moment apart, and this way a row is never in both groups at once.
            if (!user.isFavorite()) {
                others.add(user);
            }
        }
        mOtherSubscribedUserData = others;
    }

    /**
     * Resolves an adapter position to its user, across the favourites group, its divider, and the
     * full list below. Returns null for divider rows and for positions left stale by a data change.
     */
    @Nullable
    private SubscribedUserData itemAt(int position) {
        if (position < 0) {
            return null;
        }
        if (hasFavorites()) {
            if (position >= 1 && position <= mFavoriteSubscribedUserData.size()) {
                return mFavoriteSubscribedUserData.get(position - 1);
            }
            int index = position - (mFavoriteSubscribedUserData.size() + 2);
            if (index >= 0 && index < mOtherSubscribedUserData.size()) {
                return mOtherSubscribedUserData.get(index);
            }
            return null;
        }
        if (position < mOtherSubscribedUserData.size()) {
            return mOtherSubscribedUserData.get(position);
        }
        return null;
    }

    @Override
    public int getItemViewType(int position) {
        if (hasFavorites()) {
            if (position == 0) {
                return VIEW_TYPE_FAVORITE_USER_DIVIDER;
            } else if (position == mFavoriteSubscribedUserData.size() + 1) {
                return VIEW_TYPE_USER_DIVIDER;
            } else if (position <= mFavoriteSubscribedUserData.size()) {
                return VIEW_TYPE_FAVORITE_USER;
            } else {
                return VIEW_TYPE_USER;
            }
        } else {
            return VIEW_TYPE_USER;
        }
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int i) {
        switch (i) {
            case VIEW_TYPE_FAVORITE_USER_DIVIDER:
                return new FavoriteUsersDividerViewHolder(ItemFavoriteThingDividerBinding
                        .inflate(LayoutInflater.from(viewGroup.getContext()), viewGroup, false));
            case VIEW_TYPE_USER_DIVIDER:
                return new AllUsersDividerViewHolder(ItemFavoriteThingDividerBinding
                        .inflate(LayoutInflater.from(viewGroup.getContext()), viewGroup, false));
            default:
                return new UserViewHolder(ItemSubscribedUserBinding
                        .inflate(LayoutInflater.from(viewGroup.getContext()), viewGroup, false));
        }
    }

    @Override
    public void onBindViewHolder(@NonNull final RecyclerView.ViewHolder viewHolder, int i) {
        if (!(viewHolder instanceof UserViewHolder)) {
            return;
        }
        SubscribedUserData user = itemAt(i);
        if (user == null) {
            return;
        }
        ItemSubscribedUserBinding binding = ((UserViewHolder) viewHolder).binding;

        String iconUrl = user.getIconUrl();
        if (iconUrl != null && !iconUrl.isEmpty()) {
            glide.load(iconUrl)
                    .transform(new RoundedCornersTransformation(72, 0))
                    .error(glide.load(R.drawable.subreddit_default_icon)
                            .transform(new RoundedCornersTransformation(72, 0)))
                    .into(binding.thingIconGifImageViewItemSubscribedUser);
        } else {
            glide.load(R.drawable.subreddit_default_icon)
                    .transform(new RoundedCornersTransformation(72, 0))
                    .into(binding.thingIconGifImageViewItemSubscribedUser);
        }
        binding.thingNameTextViewItemSubscribedUser.setText(user.getName());

        bindFollow(binding.followImageViewItemSubscribedUser, user.isFollowed());
        bindSave(binding.saveImageViewItemSubscribedUser, user.isSaved());
        bindFavorite(binding.favoriteImageViewItemSubscribedUser, user.isFavorite());
    }

    private void bindFollow(ImageView imageView, boolean followed) {
        imageView.setImageResource(followed ? R.drawable.ic_follow_24dp : R.drawable.ic_follow_border_24dp);
        imageView.setColorFilter(mPrimaryIconColor, android.graphics.PorterDuff.Mode.SRC_IN);
        imageView.setContentDescription(mActivity.getString(followed ? R.string.unfollow_user : R.string.follow_user));
    }

    private void bindSave(ImageView imageView, boolean saved) {
        imageView.setImageResource(saved
                ? R.drawable.ic_bookmark_day_night_24dp : R.drawable.ic_bookmark_border_day_night_24dp);
        imageView.setColorFilter(mPrimaryIconColor, android.graphics.PorterDuff.Mode.SRC_IN);
        imageView.setContentDescription(mActivity.getString(saved ? R.string.unsave_user : R.string.save_user));
    }

    private void bindFavorite(ImageView imageView, boolean favorite) {
        imageView.setImageResource(favorite ? R.drawable.ic_favorite_24dp : R.drawable.ic_favorite_border_24dp);
    }

    @Override
    public int getItemCount() {
        // Each group brings its own header, and neither is shown empty -- with every user
        // favourited there is no "All" group to head, and with none there is no favourites group.
        int count = hasFavorites() ? mFavoriteSubscribedUserData.size() + 1 : 0;
        if (hasOthers()) {
            count += hasFavorites() ? mOtherSubscribedUserData.size() + 1 : mOtherSubscribedUserData.size();
        }
        return count;
    }

    @Override
    public void onViewRecycled(@NonNull RecyclerView.ViewHolder holder) {
        if (holder instanceof UserViewHolder) {
            glide.clear(((UserViewHolder) holder).binding.thingIconGifImageViewItemSubscribedUser);
        }
    }

    public void setSubscribedUsers(List<SubscribedUserData> subscribedUsers) {
        mSubscribedUserData = subscribedUsers;
        rebuildOthers();
        notifyDataSetChanged();
    }

    public void setFavoriteSubscribedUsers(List<SubscribedUserData> favoriteSubscribedUsers) {
        mFavoriteSubscribedUserData = favoriteSubscribedUsers;
        rebuildOthers();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public CharSequence getPopupText(@NonNull View view, int position) {
        SubscribedUserData user = itemAt(position);
        if (user == null || user.getName().isEmpty()) {
            return "";
        }
        return user.getName().substring(0, 1).toUpperCase(Locale.getDefault());
    }

    class UserViewHolder extends RecyclerView.ViewHolder {
        ItemSubscribedUserBinding binding;

        UserViewHolder(@NonNull ItemSubscribedUserBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
            if (mActivity.typeface != null) {
                binding.thingNameTextViewItemSubscribedUser.setTypeface(mActivity.typeface);
            }
            binding.thingNameTextViewItemSubscribedUser.setTextColor(mPrimaryTextColor);

            itemView.setOnClickListener(view -> {
                SubscribedUserData user = itemAt(getBindingAdapterPosition());
                if (user != null) {
                    itemOnClickListener.onClick(user);
                }
            });

            binding.followImageViewItemSubscribedUser.setOnClickListener(view -> {
                SubscribedUserData user = itemAt(getBindingAdapterPosition());
                if (user != null) {
                    toggleFollow(user);
                }
            });

            binding.saveImageViewItemSubscribedUser.setOnClickListener(view -> {
                SubscribedUserData user = itemAt(getBindingAdapterPosition());
                if (user != null) {
                    toggleSave(user);
                }
            });

            binding.favoriteImageViewItemSubscribedUser.setOnClickListener(view -> {
                SubscribedUserData user = itemAt(getBindingAdapterPosition());
                if (user != null) {
                    toggleFavorite(user);
                }
            });
        }
    }

    private void toggleFollow(SubscribedUserData user) {
        boolean wasFollowed = user.isFollowed();
        UserFollowing.UserFollowingListener listener = new UserFollowing.UserFollowingListener() {
            @Override
            public void onUserFollowingSuccess() {
                // The row is backed by LiveData, so a follow change -- including the delete when
                // the user is no longer followed or saved -- redraws the list on its own.
            }

            @Override
            public void onUserFollowingFail() {
                Toast.makeText(mActivity,
                        wasFollowed ? R.string.unfollow_failed : R.string.follow_failed,
                        Toast.LENGTH_SHORT).show();
            }
        };

        if (wasFollowed) {
            if (mIsAnonymous) {
                UserFollowing.anonymousUnfollowUser(mExecutor, new Handler(), user.getName(),
                        mRedditDataRoomDatabase, listener);
            } else {
                UserFollowing.unfollowUser(mExecutor, new Handler(), mOauthRetrofit, mRetrofit, mAccessToken,
                        user.getName(), mAccountName, mRedditDataRoomDatabase, listener);
            }
        } else {
            if (mIsAnonymous) {
                UserFollowing.anonymousFollowUser(mExecutor, new Handler(), mRetrofit, user.getName(),
                        mRedditDataRoomDatabase, listener);
            } else {
                UserFollowing.followUser(mExecutor, new Handler(), mOauthRetrofit, mRetrofit, mAccessToken,
                        user.getName(), mAccountName, mRedditDataRoomDatabase, listener);
            }
        }
    }

    private void toggleSave(SubscribedUserData user) {
        if (user.isSaved()) {
            UserSaving.unsaveUser(mExecutor, new Handler(), mRedditDataRoomDatabase, mAccountName,
                    user.getName(), () -> {});
        } else {
            UserSaving.saveUser(mExecutor, new Handler(), mRedditDataRoomDatabase, mAccountName,
                    user.getName(), user.getIconUrl(), () -> {});
        }
    }

    private void toggleFavorite(SubscribedUserData user) {
        boolean wasFavorite = user.isFavorite();
        // Drawn optimistically and reverted on failure: the favourite call is remote and the local
        // row is only rewritten once it succeeds. The repaint goes through the adapter rather than
        // a captured ImageView, which by then may have been recycled onto a different user.
        user.setFavorite(!wasFavorite);
        notifyDataSetChanged();

        FavoriteThing.FavoriteThingListener listener = new FavoriteThing.FavoriteThingListener() {
            @Override
            public void success() {
            }

            @Override
            public void failed() {
                Toast.makeText(mActivity,
                        wasFavorite ? R.string.thing_unfavorite_failed : R.string.thing_favorite_failed,
                        Toast.LENGTH_SHORT).show();
                user.setFavorite(wasFavorite);
                notifyDataSetChanged();
            }
        };

        if (wasFavorite) {
            FavoriteThing.unfavoriteUser(mExecutor, new Handler(), mOauthRetrofit, mRedditDataRoomDatabase,
                    mAccessToken, mAccountName, user, listener);
        } else {
            FavoriteThing.favoriteUser(mExecutor, new Handler(), mOauthRetrofit, mRedditDataRoomDatabase,
                    mAccessToken, mAccountName, user, listener);
        }
    }

    class FavoriteUsersDividerViewHolder extends RecyclerView.ViewHolder {
        ItemFavoriteThingDividerBinding binding;

        FavoriteUsersDividerViewHolder(@NonNull ItemFavoriteThingDividerBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
            if (mActivity.typeface != null) {
                binding.dividerTextViewItemFavoriteThingDivider.setTypeface(mActivity.typeface);
            }
            binding.dividerTextViewItemFavoriteThingDivider.setText(R.string.favorites);
            binding.dividerTextViewItemFavoriteThingDivider.setTextColor(mSecondaryTextColor);
        }
    }

    class AllUsersDividerViewHolder extends RecyclerView.ViewHolder {
        ItemFavoriteThingDividerBinding binding;

        AllUsersDividerViewHolder(@NonNull ItemFavoriteThingDividerBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
            if (mActivity.typeface != null) {
                binding.dividerTextViewItemFavoriteThingDivider.setTypeface(mActivity.typeface);
            }
            binding.dividerTextViewItemFavoriteThingDivider.setText(R.string.all);
            binding.dividerTextViewItemFavoriteThingDivider.setTextColor(mSecondaryTextColor);
        }
    }

    public interface ItemOnClickListener {
        void onClick(SubscribedUserData subscribedUserData);
    }
}
