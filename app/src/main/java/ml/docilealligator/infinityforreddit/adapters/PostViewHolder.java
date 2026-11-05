package ml.docilealligator.infinityforreddit.adapters;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.button.MaterialButton;
import com.libRG.CustomTextView;
import ml.docilealligator.infinityforreddit.R;
import ml.docilealligator.infinityforreddit.account.Account;
import ml.docilealligator.infinityforreddit.activities.BaseActivity;
import ml.docilealligator.infinityforreddit.activities.FilteredPostsActivity;
import ml.docilealligator.infinityforreddit.activities.ViewSubredditDetailActivity;
import ml.docilealligator.infinityforreddit.activities.ViewUserDetailActivity;
import ml.docilealligator.infinityforreddit.customviews.AspectRatioGifImageView;
import ml.docilealligator.infinityforreddit.events.PostUpdateEventToPostDetailFragment;
import ml.docilealligator.infinityforreddit.localsaved.LocalSaved;
import ml.docilealligator.infinityforreddit.post.Post;
import ml.docilealligator.infinityforreddit.readpost.ReadPostModification;
import ml.docilealligator.infinityforreddit.readpost.ReadPostType;
import ml.docilealligator.infinityforreddit.readpost.ReadPostsUtils;
import ml.docilealligator.infinityforreddit.thing.SaveThing;
import ml.docilealligator.infinityforreddit.thing.VoteThing;
import ml.docilealligator.infinityforreddit.utils.APIUtils;
import ml.docilealligator.infinityforreddit.utils.SavedPostCacheNotifier;
import ml.docilealligator.infinityforreddit.utils.Utils;
import org.greenrobot.eventbus.EventBus;

// Views and the post are assigned by setBaseView and bind, not by the constructor (as when this was
// an inner class of the adapter).
@SuppressWarnings("NullAway.Init")
public abstract class PostViewHolder extends RecyclerView.ViewHolder {
    AspectRatioGifImageView iconGifImageView;
    ImageView stickiedPostImageView;
    TextView postTimeTextView;
    TextView titleTextView;

    @Nullable
    CustomTextView typeTextView;
    @Nullable
    ImageView archivedImageView;
    @Nullable
    ImageView lockedImageView;
    @Nullable
    ImageView crosspostImageView;
    @Nullable
    CustomTextView nsfwTextView;
    @Nullable
    CustomTextView spoilerTextView;
    @Nullable
    CustomTextView flairTextView;

    MaterialButton upvoteButton;
    TextView scoreTextView;
    MaterialButton downvoteButton;

    @Nullable
    MaterialButton commentsCountButton;
    @Nullable
    MaterialButton saveButton;
    @Nullable
    MaterialButton shareButton;

    Post post;

    private final PostRowHost host;
    private final BaseActivity mActivity;

    PostViewHolder(@NonNull View itemView, PostRowHost host) {
        super(itemView);
        this.host = host;
        this.mActivity = host.activity();
    }

    void startSubredditOrUserActivity(String subredditName) {
        if (subredditName.startsWith("u_")) {
            Intent intent = new Intent(mActivity, ViewUserDetailActivity.class);
            intent.putExtra(ViewUserDetailActivity.EXTRA_USER_NAME_KEY, subredditName.substring(2));
            mActivity.startActivity(intent);
        } else {
            Intent intent = new Intent(mActivity, ViewSubredditDetailActivity.class);
            intent.putExtra(ViewSubredditDetailActivity.EXTRA_SUBREDDIT_NAME_KEY, subredditName);
            mActivity.startActivity(intent);
        }
    }

    void setBaseView(AspectRatioGifImageView iconGifImageView,
                    TextView subredditTextView,
                    TextView userTextView,
                    ImageView stickiedPostImageView,
                    TextView postTimeTextView,
                    TextView titleTextView,
                    @Nullable CustomTextView typeTextView,
                    @Nullable ImageView archivedImageView,
                    @Nullable ImageView lockedImageView,
                    @Nullable ImageView crosspostImageView,
                    @Nullable CustomTextView nsfwTextView,
                    @Nullable CustomTextView spoilerTextView,
                    @Nullable CustomTextView flairTextView,
                    MaterialButton upvoteButton,
                    TextView scoreTextView,
                    MaterialButton downvoteButton,
                    @Nullable MaterialButton commentsCountButton,
                    @Nullable MaterialButton saveButton,
                    @Nullable MaterialButton shareButton) {
        this.iconGifImageView = iconGifImageView;
        this.stickiedPostImageView = stickiedPostImageView;
        this.postTimeTextView = postTimeTextView;
        this.titleTextView = titleTextView;

        this.typeTextView = typeTextView;
        this.archivedImageView = archivedImageView;
        this.lockedImageView = lockedImageView;
        this.crosspostImageView = crosspostImageView;
        this.nsfwTextView = nsfwTextView;
        this.spoilerTextView = spoilerTextView;
        this.flairTextView = flairTextView;

        this.upvoteButton = upvoteButton;
        this.scoreTextView = scoreTextView;
        this.downvoteButton = downvoteButton;
        this.commentsCountButton = commentsCountButton;
        this.saveButton = saveButton;
        this.shareButton = shareButton;

        if (host.displaySubredditName()) {
            subredditTextView.setOnClickListener(view -> {
                int position = getBindingAdapterPosition();
                if (position < 0) {
                    return;
                }
                Post post = host.getItem(position);
                if (post != null) {
                    if (host.canStartActivity()) {
                        host.setCanStartActivity(false);
                        startSubredditOrUserActivity(post.getSubredditName());
                    }
                }
            });

            iconGifImageView.setOnClickListener(view -> subredditTextView.performClick());
        } else {
            subredditTextView.setOnClickListener(view -> {
                int position = getBindingAdapterPosition();
                if (position < 0) {
                    return;
                }
                Post post = host.getItem(position);
                if (post != null) {
                    if (host.canStartActivity()) {
                        host.setCanStartActivity(false);
                        startSubredditOrUserActivity(post.getSubredditName());
                    }
                }
            });

            iconGifImageView.setOnClickListener(view -> userTextView.performClick());
        }

        userTextView.setOnClickListener(view -> {
            if (!host.canStartActivity()) {
                return;
            }
            int position = getBindingAdapterPosition();
            if (position < 0) {
                return;
            }
            Post post = host.getItem(position);
            if (post == null || post.isAuthorDeleted()) {
                return;
            }
            host.setCanStartActivity(false);
            Intent intent = new Intent(mActivity, ViewUserDetailActivity.class);
            intent.putExtra(ViewUserDetailActivity.EXTRA_USER_NAME_KEY, post.getAuthor());
            mActivity.startActivity(intent);
        });

        setOnClickListeners(typeTextView,
                nsfwTextView,
                flairTextView,
                upvoteButton,
                scoreTextView,
                downvoteButton,
                commentsCountButton,
                saveButton,
                shareButton);
    }

    void setBaseView(AspectRatioGifImageView iconGifImageView,
                    TextView nameTextView,
                    ImageView stickiedPostImageView,
                    TextView postTimeTextView,
                    TextView titleTextView,
                    @Nullable CustomTextView typeTextView,
                    @Nullable ImageView archivedImageView,
                    @Nullable ImageView lockedImageView,
                    @Nullable ImageView crosspostImageView,
                    @Nullable CustomTextView nsfwTextView,
                    @Nullable CustomTextView spoilerTextView,
                    @Nullable CustomTextView flairTextView,
                    MaterialButton upvoteButton,
                    TextView scoreTextView,
                    MaterialButton downvoteButton,
                    @Nullable MaterialButton commentsCountButton,
                    @Nullable MaterialButton saveButton,
                    @Nullable MaterialButton shareButton) {
        this.iconGifImageView = iconGifImageView;
        this.stickiedPostImageView = stickiedPostImageView;
        this.postTimeTextView = postTimeTextView;
        this.titleTextView = titleTextView;

        this.typeTextView = typeTextView;
        this.archivedImageView = archivedImageView;
        this.lockedImageView = lockedImageView;
        this.crosspostImageView = crosspostImageView;
        this.nsfwTextView = nsfwTextView;
        this.spoilerTextView = spoilerTextView;
        this.flairTextView = flairTextView;

        this.upvoteButton = upvoteButton;
        this.scoreTextView = scoreTextView;
        this.downvoteButton = downvoteButton;
        this.commentsCountButton = commentsCountButton;
        this.saveButton = saveButton;
        this.shareButton = shareButton;

        nameTextView.setOnClickListener(view -> {
            int position = getBindingAdapterPosition();
            if (position < 0) {
                return;
            }
            Post post = host.getItem(position);
            if (post != null && host.canStartActivity()) {
                host.setCanStartActivity(false);
                if (host.displaySubredditName()) {
                    startSubredditOrUserActivity(post.getSubredditName());
                } else if (!post.isAuthorDeleted()) {
                    Intent intent = new Intent(mActivity, ViewUserDetailActivity.class);
                    intent.putExtra(ViewUserDetailActivity.EXTRA_USER_NAME_KEY, post.getAuthor());
                    mActivity.startActivity(intent);
                }
            }
        });

        iconGifImageView.setOnClickListener(view -> nameTextView.performClick());

        setOnClickListeners(typeTextView,
                nsfwTextView,
                flairTextView,
                upvoteButton,
                scoreTextView,
                downvoteButton,
                commentsCountButton,
                saveButton,
                shareButton);
    }

    void setOnClickListeners(MaterialButton upvoteButton,
                            TextView scoreTextView,
                            MaterialButton downvoteButton,
                            @Nullable MaterialButton commentsCountButton,
                            @Nullable MaterialButton saveButton,
                            @Nullable MaterialButton shareButton) {
        itemView.setOnClickListener(view -> {
            int position = getBindingAdapterPosition();
            if (position >= 0 && host.canStartActivity()) {
                Post post = host.getItem(position);
                if (post != null) {
                    markPostRead(post, true);

                    host.openViewPostDetailActivity(post, getBindingAdapterPosition());
                }
            }
        });

        upvoteButton.setOnClickListener(view -> {
            int position = getBindingAdapterPosition();
            if (position < 0) {
                return;
            }
            Post post = host.getItem(position);
            if (post != null) {
                if (!Account.ANONYMOUS_ACCOUNT.equals(host.accountName())) {
                    if (post.isArchived()) {
                        Toast.makeText(mActivity, R.string.archived_post_vote_unavailable, Toast.LENGTH_SHORT).show();
                        return;
                    }
                }

                if (host.markPostsAsReadAfterVoting()) {
                    markPostRead(post, true);
                }

                ColorStateList previousUpvoteButtonIconTint = upvoteButton.getIconTint();
                ColorStateList previousDownvoteButtonIconTint = downvoteButton.getIconTint();
                int previousScoreTextViewColor = scoreTextView.getCurrentTextColor();
                Drawable previousUpvoteButtonDrawable = upvoteButton.getIcon();
                Drawable previousDownvoteButtonDrawable = downvoteButton.getIcon();

                int previousVoteType = post.getVoteType();
                String newVoteType;

                downvoteButton.setIconResource(R.drawable.ic_downvote_24dp);
                downvoteButton.setIconTint(ColorStateList.valueOf(host.postIconAndInfoColor()));

                if (previousVoteType != 1) {
                    //Not upvoted before
                    post.setVoteType(1);
                    newVoteType = APIUtils.DIR_UPVOTE;
                    upvoteButton.setIconResource(R.drawable.ic_upvote_filled_24dp);
                    upvoteButton.setIconTint(ColorStateList.valueOf(host.upvotedColor()));
                    scoreTextView.setTextColor(host.upvotedColor());
                } else {
                    //Upvoted before
                    post.setVoteType(0);
                    newVoteType = APIUtils.DIR_UNVOTE;
                    upvoteButton.setIconResource(R.drawable.ic_upvote_24dp);
                    upvoteButton.setIconTint(ColorStateList.valueOf(host.postIconAndInfoColor()));
                    scoreTextView.setTextColor(host.postIconAndInfoColor());
                }

                if (Account.ANONYMOUS_ACCOUNT.equals(host.accountName())) {
                    if (previousVoteType == 1) {
                        ReadPostModification.deleteReadPost(host.database(), host.executor(), mActivity.accountName,
                                post.getId(), ReadPostType.ANONYMOUS_UPVOTED_POSTS);
                    } else {
                        ReadPostModification.insertReadPost(host.database(), host.executor(), mActivity.accountName,
                                post.getId(), ReadPostType.ANONYMOUS_UPVOTED_POSTS,
                                ReadPostsUtils.GetReadPostsLimit(mActivity.accountName, host.postHistorySharedPreferences()));
                    }
                    EventBus.getDefault().post(new PostUpdateEventToPostDetailFragment(post));
                    return;
                } else {
                    if (!host.hideTheNumberOfVotes()) {
                        scoreTextView.setText(Utils.getNVotes(host.showAbsoluteNumberOfVotes(), post.getScore() + post.getVoteType()));
                    }
                }

                VoteThing.voteThing(mActivity, host.oauthRetrofit(), host.accessToken(), new VoteThing.VoteThingListener() {
                    @Override
                    public void onVoteThingSuccess(int position1) {
                        int currentPosition = getBindingAdapterPosition();
                        if (newVoteType.equals(APIUtils.DIR_UPVOTE)) {
                            post.setVoteType(1);
                            if (currentPosition == position) {
                                upvoteButton.setIconResource(R.drawable.ic_upvote_filled_24dp);
                                upvoteButton.setIconTint(ColorStateList.valueOf(host.upvotedColor()));
                                scoreTextView.setTextColor(host.upvotedColor());
                            }
                        } else {
                            post.setVoteType(0);
                            if (currentPosition == position) {
                                upvoteButton.setIconResource(R.drawable.ic_upvote_24dp);
                                upvoteButton.setIconTint(ColorStateList.valueOf(host.postIconAndInfoColor()));
                                scoreTextView.setTextColor(host.postIconAndInfoColor());
                            }
                        }

                        if (currentPosition == position) {
                            downvoteButton.setIconResource(R.drawable.ic_downvote_24dp);
                            downvoteButton.setIconTint(ColorStateList.valueOf(host.postIconAndInfoColor()));
                            if (!host.hideTheNumberOfVotes()) {
                                scoreTextView.setText(Utils.getNVotes(host.showAbsoluteNumberOfVotes(), post.getScore() + post.getVoteType()));
                            }
                        }

                        EventBus.getDefault().post(new PostUpdateEventToPostDetailFragment(post));
                    }

                    @Override
                    public void onVoteThingFail(int position1) {
                        Toast.makeText(mActivity, R.string.vote_failed, Toast.LENGTH_SHORT).show();
                        post.setVoteType(previousVoteType);
                        if (getBindingAdapterPosition() == position) {
                            if (!host.hideTheNumberOfVotes()) {
                                scoreTextView.setText(Utils.getNVotes(host.showAbsoluteNumberOfVotes(), post.getScore() + previousVoteType));
                            }
                            upvoteButton.setIcon(previousUpvoteButtonDrawable);
                            upvoteButton.setIconTint(previousUpvoteButtonIconTint);
                            scoreTextView.setTextColor(previousScoreTextViewColor);
                            downvoteButton.setIcon(previousDownvoteButtonDrawable);
                            downvoteButton.setIconTint(previousDownvoteButtonIconTint);
                        }

                        EventBus.getDefault().post(new PostUpdateEventToPostDetailFragment(post));
                    }
                }, post.getFullName(), newVoteType, getBindingAdapterPosition());
            }
        });

        scoreTextView.setOnClickListener(view -> {
            upvoteButton.performClick();
        });

        downvoteButton.setOnClickListener(view -> {
            int position = getBindingAdapterPosition();
            if (position < 0) {
                return;
            }
            Post post = host.getItem(position);
            if (post != null) {
                if (!Account.ANONYMOUS_ACCOUNT.equals(host.accountName())) {
                    if (post.isArchived()) {
                        Toast.makeText(mActivity, R.string.archived_post_vote_unavailable, Toast.LENGTH_SHORT).show();
                        return;
                    }
                }

                if (host.markPostsAsReadAfterVoting()) {
                    markPostRead(post, true);
                }

                ColorStateList previousUpvoteButtonIconTint = upvoteButton.getIconTint();
                ColorStateList previousDownvoteButtonIconTint = downvoteButton.getIconTint();
                int previousScoreTextViewColor = scoreTextView.getCurrentTextColor();
                Drawable previousUpvoteButtonDrawable = upvoteButton.getIcon();
                Drawable previousDownvoteButtonDrawable = downvoteButton.getIcon();

                int previousVoteType = post.getVoteType();
                String newVoteType;

                upvoteButton.setIconResource(R.drawable.ic_upvote_24dp);
                upvoteButton.setIconTint(ColorStateList.valueOf(host.postIconAndInfoColor()));

                if (previousVoteType != -1) {
                    //Not downvoted before
                    post.setVoteType(-1);
                    newVoteType = APIUtils.DIR_DOWNVOTE;
                    downvoteButton.setIconResource(R.drawable.ic_downvote_filled_24dp);
                    downvoteButton.setIconTint(ColorStateList.valueOf(host.downvotedColor()));
                    scoreTextView.setTextColor(host.downvotedColor());
                } else {
                    //Downvoted before
                    post.setVoteType(0);
                    newVoteType = APIUtils.DIR_UNVOTE;
                    downvoteButton.setIconResource(R.drawable.ic_downvote_24dp);
                    downvoteButton.setIconTint(ColorStateList.valueOf(host.postIconAndInfoColor()));
                    scoreTextView.setTextColor(host.postIconAndInfoColor());
                }

                if (Account.ANONYMOUS_ACCOUNT.equals(host.accountName())) {
                    if (previousVoteType == -1) {
                        ReadPostModification.deleteReadPost(host.database(), host.executor(), mActivity.accountName,
                                post.getId(), ReadPostType.ANONYMOUS_DOWNVOTED_POSTS);
                    } else {
                        ReadPostModification.insertReadPost(host.database(), host.executor(), mActivity.accountName,
                                post.getId(), ReadPostType.ANONYMOUS_DOWNVOTED_POSTS,
                                ReadPostsUtils.GetReadPostsLimit(mActivity.accountName, host.postHistorySharedPreferences()));
                    }
                    EventBus.getDefault().post(new PostUpdateEventToPostDetailFragment(post));
                    return;
                } else {
                    if (!host.hideTheNumberOfVotes()) {
                        scoreTextView.setText(Utils.getNVotes(host.showAbsoluteNumberOfVotes(), post.getScore() + post.getVoteType()));
                    }
                }

                VoteThing.voteThing(mActivity, host.oauthRetrofit(), host.accessToken(), new VoteThing.VoteThingListener() {
                    @Override
                    public void onVoteThingSuccess(int position1) {
                        int currentPosition = getBindingAdapterPosition();
                        if (newVoteType.equals(APIUtils.DIR_DOWNVOTE)) {
                            post.setVoteType(-1);
                            if (currentPosition == position) {
                                downvoteButton.setIconResource(R.drawable.ic_downvote_filled_24dp);
                                downvoteButton.setIconTint(ColorStateList.valueOf(host.downvotedColor()));
                                scoreTextView.setTextColor(host.downvotedColor());
                            }
                        } else {
                            post.setVoteType(0);
                            if (currentPosition == position) {
                                downvoteButton.setIconResource(R.drawable.ic_downvote_24dp);
                                downvoteButton.setIconTint(ColorStateList.valueOf(host.postIconAndInfoColor()));
                                scoreTextView.setTextColor(host.postIconAndInfoColor());
                            }
                        }

                        if (currentPosition == position) {
                            upvoteButton.setIconResource(R.drawable.ic_upvote_24dp);
                            upvoteButton.setIconTint(ColorStateList.valueOf(host.postIconAndInfoColor()));
                            if (!host.hideTheNumberOfVotes()) {
                                scoreTextView.setText(Utils.getNVotes(host.showAbsoluteNumberOfVotes(), post.getScore() + post.getVoteType()));
                            }
                        }

                        EventBus.getDefault().post(new PostUpdateEventToPostDetailFragment(post));
                    }

                    @Override
                    public void onVoteThingFail(int position1) {
                        Toast.makeText(mActivity, R.string.vote_failed, Toast.LENGTH_SHORT).show();
                        post.setVoteType(previousVoteType);
                        if (getBindingAdapterPosition() == position) {
                            if (!host.hideTheNumberOfVotes()) {
                                scoreTextView.setText(Utils.getNVotes(host.showAbsoluteNumberOfVotes(), post.getScore() + previousVoteType));
                            }
                            upvoteButton.setIcon(previousUpvoteButtonDrawable);
                            upvoteButton.setIconTint(previousUpvoteButtonIconTint);
                            scoreTextView.setTextColor(previousScoreTextViewColor);
                            downvoteButton.setIcon(previousDownvoteButtonDrawable);
                            downvoteButton.setIconTint(previousDownvoteButtonIconTint);
                        }

                        EventBus.getDefault().post(new PostUpdateEventToPostDetailFragment(post));
                    }
                }, post.getFullName(), newVoteType, getBindingAdapterPosition());
            }
        });

        if (commentsCountButton != null) {
            commentsCountButton.setOnClickListener(view -> itemView.performClick());
        }

        if (saveButton != null) {
            saveButton.setOnClickListener(view -> {
                int position = getBindingAdapterPosition();
                if (position < 0) {
                    return;
                }
                Post post = host.getItem(position);
                if (post != null) {
                    toggleSave(post, position);
                }
            });
        }

        if (shareButton != null) {
            shareButton.setOnClickListener(view -> {
                int position = getBindingAdapterPosition();
                if (position < 0) {
                    return;
                }
                Post post = host.getItem(position);
                if (post != null) {
                    host.shareLink(post);
                }
            });

            shareButton.setOnLongClickListener(view -> {
                int position = getBindingAdapterPosition();
                if (position < 0) {
                    return false;
                }
                Post post = host.getItem(position);
                if (post != null) {
                    mActivity.copyLink(post.getPermalink());
                    return true;
                }
                return false;
            });
        }
    }

    void setOnClickListeners(@Nullable CustomTextView typeTextView,
                            @Nullable CustomTextView nsfwTextView,
                            @Nullable CustomTextView flairTextView,
                            MaterialButton upvoteButton,
                            TextView scoreTextView,
                            MaterialButton downvoteButton,
                            @Nullable MaterialButton commentsCountButton,
                            @Nullable MaterialButton saveButton,
                            @Nullable MaterialButton shareButton) {
        setOnClickListeners(upvoteButton,
                scoreTextView,
                downvoteButton,
                commentsCountButton,
                saveButton,
                shareButton);

        if (!(mActivity instanceof FilteredPostsActivity)) {
            if (nsfwTextView != null) {
                nsfwTextView.setOnClickListener(view -> {
                    int position = getBindingAdapterPosition();
                    if (position < 0) {
                        return;
                    }
                    Post post = host.getItem(position);
                    if (post != null) {
                        host.callback().nsfwChipClicked();
                    }
                });
            }

            if (typeTextView != null) {
                typeTextView.setOnClickListener(view -> {
                    int position = getBindingAdapterPosition();
                    if (position < 0) {
                        return;
                    }
                    Post post = host.getItem(position);
                    if (post != null) {
                        host.callback().typeChipClicked(post.getPostType());
                    }
                });
            }

            if (flairTextView != null) {
                flairTextView.setOnClickListener(view -> {
                    int position = getBindingAdapterPosition();
                    if (position < 0) {
                        return;
                    }
                    Post post = host.getItem(position);
                    if (post != null) {
                        host.callback().flairChipClicked(post.getFlair());
                    }
                });
            }
        }
    }

    /**
     * Save or unsave, from the button or from a swipe. A swipe can land on a layout whose
     * toolbar has no save button at all, so the icon write is the part that is optional here,
     * not the save.
     */
    void toggleSave(Post post, int position) {
        if (post.isSaved()) {
            setSaveIcon(false);
            if (host.accountName().equals(Account.ANONYMOUS_ACCOUNT)) {
                ReadPostModification.deleteReadPost(host.database(), host.executor(), mActivity.accountName,
                        post.getId(), ReadPostType.ANONYMOUS_SAVED_POSTS);
                post.setSaved(!post.isSaved());
                Toast.makeText(mActivity, R.string.post_unsaved_success, Toast.LENGTH_SHORT).show();
                EventBus.getDefault().post(new PostUpdateEventToPostDetailFragment(post));
            } else {
                SaveThing.unsaveThing(host.oauthRetrofit(), host.accessToken(), post.getFullName(),
                        new SaveThing.SaveThingListener() {
                            @Override
                            public void success() {
                                post.setSaved(false);
                                LocalSaved.onUnsaved(host.database(), host.executor(),
                                        host.accountName(), post.getFullName());
                                SavedPostCacheNotifier.onSavedPostChanged();
                                if (getBindingAdapterPosition() == position) {
                                    setSaveIcon(false);
                                }
                                Toast.makeText(mActivity, R.string.post_unsaved_success, Toast.LENGTH_SHORT).show();
                                EventBus.getDefault().post(new PostUpdateEventToPostDetailFragment(post));
                            }

                            @Override
                            public void failed() {
                                post.setSaved(true);
                                if (getBindingAdapterPosition() == position) {
                                    setSaveIcon(true);
                                }
                                Toast.makeText(mActivity, R.string.post_unsaved_failed, Toast.LENGTH_SHORT).show();
                                EventBus.getDefault().post(new PostUpdateEventToPostDetailFragment(post));
                            }
                        });
            }
        } else {
            setSaveIcon(true);
            if (host.accountName().equals(Account.ANONYMOUS_ACCOUNT)) {
                ReadPostModification.insertReadPost(host.database(), host.executor(), mActivity.accountName,
                        post.getId(), ReadPostType.ANONYMOUS_SAVED_POSTS,
                        ReadPostsUtils.GetReadPostsLimit(mActivity.accountName, host.postHistorySharedPreferences()));
                post.setSaved(!post.isSaved());
                Toast.makeText(mActivity, R.string.post_saved_success, Toast.LENGTH_SHORT).show();
                EventBus.getDefault().post(new PostUpdateEventToPostDetailFragment(post));
            } else {
                SaveThing.saveThing(host.oauthRetrofit(), host.accessToken(), post.getFullName(),
                        new SaveThing.SaveThingListener() {
                            @Override
                            public void success() {
                                post.setSaved(true);
                                LocalSaved.onSaved(host.database(), host.executor(),
                                        host.oauthRetrofit(), host.accessToken(), host.accountName(), post.getFullName());
                                SavedPostCacheNotifier.onSavedPostChanged();
                                if (getBindingAdapterPosition() == position) {
                                    setSaveIcon(true);
                                }
                                Toast.makeText(mActivity, R.string.post_saved_success, Toast.LENGTH_SHORT).show();
                                EventBus.getDefault().post(new PostUpdateEventToPostDetailFragment(post));
                            }

                            @Override
                            public void failed() {
                                post.setSaved(false);
                                if (getBindingAdapterPosition() == position) {
                                    setSaveIcon(false);
                                }
                                Toast.makeText(mActivity, R.string.post_saved_failed, Toast.LENGTH_SHORT).show();
                                EventBus.getDefault().post(new PostUpdateEventToPostDetailFragment(post));
                            }
                        });
            }
        }
    }

    private void setSaveIcon(boolean saved) {
        if (saveButton != null) {
            saveButton.setIconResource(saved
                    ? R.drawable.ic_bookmark_grey_24dp : R.drawable.ic_bookmark_border_grey_24dp);
        }
    }

    /**
     * The read state the user asked for by swiping. Unlike {@link #markPostRead}, which is the
     * feed deciding on its own, this does not consult "Mark Posts As Read" -- the swipe is the
     * request. It also writes the row's colours back, which nothing else undoes.
     */
    void setReadStateFromSwipe(Post post, boolean read) {
        if (!host.handleReadPost()) {
            Toast.makeText(mActivity, R.string.post_history_unavailable, Toast.LENGTH_SHORT).show();
            return;
        }

        host.storeReadState(post, read);
        setItemViewBackgroundColor(read);
        titleTextView.setTextColor(read ? host.readPostTitleColor() : host.postTitleColor());
        if (this instanceof PostRecyclerViewAdapter.PostTextTypeViewHolder) {
            ((PostRecyclerViewAdapter.PostTextTypeViewHolder) this).contentTextView.setTextColor(
                    read ? host.readPostContentColor() : host.postContentColor());
        }
        if (!read) {
            Toast.makeText(mActivity, R.string.post_marked_as_unread, Toast.LENGTH_SHORT).show();
        }
    }

    abstract void setItemViewBackgroundColor(boolean isReadPost);
    abstract void markPostRead(Post post, boolean changePostItemColor);
}
