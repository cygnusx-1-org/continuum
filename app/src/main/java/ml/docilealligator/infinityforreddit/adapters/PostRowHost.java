package ml.docilealligator.infinityforreddit.adapters;

import android.content.SharedPreferences;

import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentManager;

import java.util.concurrent.Executor;

import ml.docilealligator.infinityforreddit.RedditDataRoomDatabase;
import ml.docilealligator.infinityforreddit.activities.BaseActivity;
import ml.docilealligator.infinityforreddit.post.Post;
import ml.docilealligator.infinityforreddit.videoautoplay.PlayerSelector;
import retrofit2.Retrofit;

/**
 * What a post row needs from the adapter that owns it. Every value is read at the moment it is
 * asked for, never copied, so a setting the fragment changes while a feed is open (votes hidden,
 * absolute scores, mark as read after voting) reaches rows that already exist.
 */
interface PostRowHost {
    BaseActivity activity();

    PostRecyclerViewAdapter.Callback callback();

    String accountName();

    @Nullable
    String accessToken();

    Retrofit oauthRetrofit();

    RedditDataRoomDatabase database();

    Executor executor();

    @Nullable
    SharedPreferences postHistorySharedPreferences();

    boolean displaySubredditName();

    boolean markPostsAsReadAfterVoting();

    boolean hideTheNumberOfVotes();

    boolean showAbsoluteNumberOfVotes();

    boolean handleReadPost();

    int postTitleColor();

    int readPostTitleColor();

    int postContentColor();

    int readPostContentColor();

    int upvotedColor();

    int downvotedColor();

    int postIconAndInfoColor();

    int cardViewBackgroundColor();

    int colorAccent();

    int noPreviewPostTypeBackgroundColor();

    int noPreviewPostTypeIconTint();

    int mediaIndicatorIconTint();

    int mediaIndicatorBackgroundColor();

    int readPostCardViewBackgroundColor();

    boolean markPostsAsRead();

    void openMedia(Post post);

    int stickiedPostIconTint();

    int secondaryTextColor();

    int spoilerBackgroundColor();

    int spoilerTextColor();

    int nsfwBackgroundColor();

    int nsfwTextColor();

    int flairBackgroundColor();

    int flairTextColor();

    int archivedIconTint();

    int lockedIconTint();

    int crosspostIconTint();

    int postTypeTextColor();

    int filledCardViewBackgroundColor();

    int readPostFilledCardViewBackgroundColor();

    int subredditColor();

    int usernameColor();

    boolean voteButtonsOnTheRight();

    String longPressPostNonMediaAreaAction();

    FragmentManager childFragmentManager();

    void openMedia(Post post, boolean peekMedia);

    double startAutoplayVisibleAreaOffset();

    boolean canPlayVideo();

    @Nullable
    PlayerSelector playerSelector();

    @Nullable
    Post getItem(int position);

    boolean canStartActivity();

    void setCanStartActivity(boolean canStartActivity);

    void openViewPostDetailActivity(Post post, int position);

    void shareLink(Post post);

    void storeReadState(Post post, boolean read);
}
