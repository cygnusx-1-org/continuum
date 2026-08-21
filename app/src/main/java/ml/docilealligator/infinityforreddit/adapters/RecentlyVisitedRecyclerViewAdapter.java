package ml.docilealligator.infinityforreddit.adapters;

import android.os.Handler;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestManager;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Executor;
import jp.wasabeef.glide.transformations.RoundedCornersTransformation;
import ml.docilealligator.infinityforreddit.R;
import ml.docilealligator.infinityforreddit.RedditDataRoomDatabase;
import ml.docilealligator.infinityforreddit.account.Account;
import ml.docilealligator.infinityforreddit.activities.BaseActivity;
import ml.docilealligator.infinityforreddit.customtheme.CustomThemeWrapper;
import ml.docilealligator.infinityforreddit.databinding.ItemRecentlyVisitedSubredditBinding;
import ml.docilealligator.infinityforreddit.databinding.ItemRecentlyVisitedUserBinding;
import ml.docilealligator.infinityforreddit.recentlyvisited.RecentlyVisited;
import ml.docilealligator.infinityforreddit.recentlyvisited.RecentlyVisitedType;
import ml.docilealligator.infinityforreddit.subreddit.SubredditSubscription;
import ml.docilealligator.infinityforreddit.user.UserFollowing;
import ml.docilealligator.infinityforreddit.user.UserSaving;
import pl.droidsonroids.gif.GifImageView;
import retrofit2.Retrofit;

/**
 * One Recently Visited tab. Rows stay put when acted on, and every control shows state rather than
 * coming and going: plus/minus for subscribe, hollow/filled for follow and save.
 *
 * <p>Each tab has its own row layout: subreddits carry the one subscribe toggle, users carry the
 * same follow and save toggles as the Subscriptions Users list.
 *
 * <p>Followed, subscribed and saved are all read live from the subscription tables rather than
 * latched on the row, so the icons stay honest after a change made anywhere else.
 */
public class RecentlyVisitedRecyclerViewAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private final BaseActivity mActivity;
    private final Executor mExecutor;
    private final Retrofit mOauthRetrofit;
    private final Retrofit mRetrofit;
    private final RedditDataRoomDatabase mRedditDataRoomDatabase;
    @Nullable
    private final String mAccessToken;
    private final String mAccountName;
    private final boolean mIsAnonymous;
    @RecentlyVisitedType
    private final int mType;
    private final RequestManager glide;
    private final int mPrimaryTextColor;
    private final int mPrimaryIconColor;
    private final ItemOnClickListener itemOnClickListener;

    private List<RecentlyVisited> mRecentlyVisited = new ArrayList<>();
    /** Subreddits subscribed to, or users followed, lowercased. */
    private Set<String> mAddedNames = new HashSet<>();
    /** Users with `is_saved` set, lowercased. Empty on the subreddits tab. */
    private Set<String> mSavedNames = new HashSet<>();

    public RecentlyVisitedRecyclerViewAdapter(BaseActivity activity, Executor executor,
                                              Retrofit oauthRetrofit, Retrofit retrofit,
                                              RedditDataRoomDatabase redditDataRoomDatabase,
                                              CustomThemeWrapper customThemeWrapper,
                                              @Nullable String accessToken, @NonNull String accountName,
                                              @RecentlyVisitedType int type,
                                              ItemOnClickListener itemOnClickListener) {
        mActivity = activity;
        mExecutor = executor;
        mOauthRetrofit = oauthRetrofit;
        mRetrofit = retrofit;
        mRedditDataRoomDatabase = redditDataRoomDatabase;
        mAccessToken = accessToken;
        mAccountName = accountName;
        mIsAnonymous = Account.ANONYMOUS_ACCOUNT.equals(accountName);
        mType = type;
        glide = Glide.with(activity);
        mPrimaryTextColor = customThemeWrapper.getPrimaryTextColor();
        mPrimaryIconColor = customThemeWrapper.getPrimaryIconColor();
        this.itemOnClickListener = itemOnClickListener;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (mType == RecentlyVisitedType.USER) {
            return new UserViewHolder(ItemRecentlyVisitedUserBinding.inflate(inflater, parent, false));
        }
        return new SubredditViewHolder(ItemRecentlyVisitedSubredditBinding.inflate(inflater, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        RecentlyVisited item = mRecentlyVisited.get(position);
        boolean added = mAddedNames.contains(item.getName().toLowerCase(Locale.US));

        if (holder instanceof UserViewHolder) {
            ItemRecentlyVisitedUserBinding binding = ((UserViewHolder) holder).binding;
            bindIconAndName(item, binding.thingIconGifImageViewItemRecentlyVisitedUser,
                    binding.thingNameTextViewItemRecentlyVisitedUser);

            binding.followImageViewItemRecentlyVisitedUser.setImageResource(
                    added ? R.drawable.ic_follow_24dp : R.drawable.ic_follow_border_24dp);
            binding.followImageViewItemRecentlyVisitedUser.setColorFilter(
                    mPrimaryIconColor, android.graphics.PorterDuff.Mode.SRC_IN);
            binding.followImageViewItemRecentlyVisitedUser.setContentDescription(
                    mActivity.getString(added ? R.string.unfollow_user : R.string.follow_user));

            boolean saved = mSavedNames.contains(item.getName().toLowerCase(Locale.US));
            binding.saveImageViewItemRecentlyVisitedUser.setImageResource(saved
                    ? R.drawable.ic_bookmark_day_night_24dp : R.drawable.ic_bookmark_border_day_night_24dp);
            binding.saveImageViewItemRecentlyVisitedUser.setColorFilter(
                    mPrimaryIconColor, android.graphics.PorterDuff.Mode.SRC_IN);
            binding.saveImageViewItemRecentlyVisitedUser.setContentDescription(
                    mActivity.getString(saved ? R.string.unsave_user : R.string.save_user));
        } else {
            ItemRecentlyVisitedSubredditBinding binding = ((SubredditViewHolder) holder).binding;
            bindIconAndName(item, binding.thingIconGifImageViewItemRecentlyVisitedSubreddit,
                    binding.thingNameTextViewItemRecentlyVisitedSubreddit);

            binding.subscribeImageViewItemRecentlyVisitedSubreddit.setImageResource(
                    added ? R.drawable.ic_remove_day_night_24dp : R.drawable.ic_add_day_night_24dp);
            binding.subscribeImageViewItemRecentlyVisitedSubreddit.setColorFilter(
                    mPrimaryIconColor, android.graphics.PorterDuff.Mode.SRC_IN);
            binding.subscribeImageViewItemRecentlyVisitedSubreddit.setContentDescription(mActivity.getString(
                    added ? R.string.unsubscribe_subreddit : R.string.subscribe_subreddit));
        }
    }

    private void bindIconAndName(RecentlyVisited item, GifImageView iconImageView, TextView nameTextView) {
        String iconUrl = item.getIconUrl();
        if (iconUrl != null && !iconUrl.isEmpty()) {
            glide.load(iconUrl)
                    .transform(new RoundedCornersTransformation(72, 0))
                    .error(glide.load(R.drawable.subreddit_default_icon)
                            .transform(new RoundedCornersTransformation(72, 0)))
                    .into(iconImageView);
        } else {
            glide.load(R.drawable.subreddit_default_icon)
                    .transform(new RoundedCornersTransformation(72, 0))
                    .into(iconImageView);
        }
        nameTextView.setText(item.getName());
    }

    @Override
    public int getItemCount() {
        return mRecentlyVisited.size();
    }

    @Override
    public void onViewRecycled(@NonNull RecyclerView.ViewHolder holder) {
        if (holder instanceof UserViewHolder) {
            glide.clear(((UserViewHolder) holder).binding.thingIconGifImageViewItemRecentlyVisitedUser);
        } else if (holder instanceof SubredditViewHolder) {
            glide.clear(((SubredditViewHolder) holder).binding.thingIconGifImageViewItemRecentlyVisitedSubreddit);
        }
    }

    public void setRecentlyVisited(List<RecentlyVisited> recentlyVisited) {
        mRecentlyVisited = recentlyVisited == null ? new ArrayList<>() : recentlyVisited;
        notifyDataSetChanged();
    }

    /**
     * @param addedNames subreddits subscribed to, or users followed, lowercased
     * @param savedNames users with the save flag set, lowercased
     */
    public void setSubscriptionState(Set<String> addedNames, Set<String> savedNames) {
        mAddedNames = addedNames;
        mSavedNames = savedNames;
        notifyDataSetChanged();
    }

    @Nullable
    private RecentlyVisited itemAt(int position) {
        return position >= 0 && position < mRecentlyVisited.size() ? mRecentlyVisited.get(position) : null;
    }

    private void toggleSubscribe(RecentlyVisited item) {
        boolean wasSubscribed = mAddedNames.contains(item.getName().toLowerCase(Locale.US));
        SubredditSubscription.SubredditSubscriptionListener listener =
                new SubredditSubscription.SubredditSubscriptionListener() {
                    @Override
                    public void onSubredditSubscriptionSuccess() {
                    }

                    @Override
                    public void onSubredditSubscriptionFail() {
                        Toast.makeText(mActivity,
                                wasSubscribed ? R.string.unsubscribe_failed : R.string.subscribe_failed,
                                Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onSubredditSubscriptionNSFWBlocked() {
                        Toast.makeText(mActivity, R.string.cannot_subscribe_nsfw_subreddit_anonymous,
                                Toast.LENGTH_SHORT).show();
                    }
                };

        if (wasSubscribed) {
            if (mIsAnonymous) {
                SubredditSubscription.anonymousUnsubscribeToSubreddit(mExecutor, new Handler(),
                        mRedditDataRoomDatabase, item.getName(), listener);
            } else {
                SubredditSubscription.unsubscribeToSubreddit(mExecutor, new Handler(), mOauthRetrofit,
                        mAccessToken, item.getName(), mAccountName, mRedditDataRoomDatabase, listener);
            }
        } else {
            if (mIsAnonymous) {
                SubredditSubscription.anonymousSubscribeToSubreddit(mExecutor, new Handler(), mRetrofit,
                        mRedditDataRoomDatabase, item.getName(), listener);
            } else {
                SubredditSubscription.subscribeToSubreddit(mExecutor, new Handler(), mOauthRetrofit, mRetrofit,
                        mAccessToken, item.getName(), mAccountName, mRedditDataRoomDatabase, listener);
            }
        }
    }

    private void toggleFollow(RecentlyVisited item) {
        boolean wasFollowed = mAddedNames.contains(item.getName().toLowerCase(Locale.US));
        UserFollowing.UserFollowingListener listener = new UserFollowing.UserFollowingListener() {
            @Override
            public void onUserFollowingSuccess() {
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
                UserFollowing.anonymousUnfollowUser(mExecutor, new Handler(), item.getName(),
                        mRedditDataRoomDatabase, listener);
            } else {
                UserFollowing.unfollowUser(mExecutor, new Handler(), mOauthRetrofit, mRetrofit, mAccessToken,
                        item.getName(), mAccountName, mRedditDataRoomDatabase, listener);
            }
        } else {
            if (mIsAnonymous) {
                UserFollowing.anonymousFollowUser(mExecutor, new Handler(), mRetrofit, item.getName(),
                        mRedditDataRoomDatabase, listener);
            } else {
                UserFollowing.followUser(mExecutor, new Handler(), mOauthRetrofit, mRetrofit, mAccessToken,
                        item.getName(), mAccountName, mRedditDataRoomDatabase, listener);
            }
        }
    }

    private void toggleSave(RecentlyVisited item) {
        if (mSavedNames.contains(item.getName().toLowerCase(Locale.US))) {
            UserSaving.unsaveUser(mExecutor, new Handler(), mRedditDataRoomDatabase, mAccountName,
                    item.getName(), () -> {});
        } else {
            UserSaving.saveUser(mExecutor, new Handler(), mRedditDataRoomDatabase, mAccountName,
                    item.getName(), item.getIconUrl(), () -> {});
        }
    }

    private void applyRowTheme(TextView nameTextView) {
        if (mActivity.typeface != null) {
            nameTextView.setTypeface(mActivity.typeface);
        }
        nameTextView.setTextColor(mPrimaryTextColor);
    }

    class SubredditViewHolder extends RecyclerView.ViewHolder {
        final ItemRecentlyVisitedSubredditBinding binding;

        SubredditViewHolder(@NonNull ItemRecentlyVisitedSubredditBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
            applyRowTheme(binding.thingNameTextViewItemRecentlyVisitedSubreddit);

            itemView.setOnClickListener(view -> {
                RecentlyVisited item = itemAt(getBindingAdapterPosition());
                if (item != null) {
                    itemOnClickListener.onClick(item);
                }
            });

            binding.subscribeImageViewItemRecentlyVisitedSubreddit.setOnClickListener(view -> {
                RecentlyVisited item = itemAt(getBindingAdapterPosition());
                if (item != null) {
                    toggleSubscribe(item);
                }
            });
        }
    }

    class UserViewHolder extends RecyclerView.ViewHolder {
        final ItemRecentlyVisitedUserBinding binding;

        UserViewHolder(@NonNull ItemRecentlyVisitedUserBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
            applyRowTheme(binding.thingNameTextViewItemRecentlyVisitedUser);

            itemView.setOnClickListener(view -> {
                RecentlyVisited item = itemAt(getBindingAdapterPosition());
                if (item != null) {
                    itemOnClickListener.onClick(item);
                }
            });

            binding.followImageViewItemRecentlyVisitedUser.setOnClickListener(view -> {
                RecentlyVisited item = itemAt(getBindingAdapterPosition());
                if (item != null) {
                    toggleFollow(item);
                }
            });

            binding.saveImageViewItemRecentlyVisitedUser.setOnClickListener(view -> {
                RecentlyVisited item = itemAt(getBindingAdapterPosition());
                if (item != null) {
                    toggleSave(item);
                }
            });
        }
    }

    public interface ItemOnClickListener {
        void onClick(RecentlyVisited recentlyVisited);
    }
}
