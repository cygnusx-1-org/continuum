package ml.docilealligator.infinityforreddit.adapters;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.text.SpannableStringBuilder;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.Gravity;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.fragment.app.FragmentManager;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.ContextCompat;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.ui.PlayerView;
import androidx.paging.ItemSnapshotList;
import androidx.paging.PagingDataAdapter;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.PagerSnapHelper;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestBuilder;
import com.bumptech.glide.RequestManager;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.Request;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.Target;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.loadingindicator.LoadingIndicator;
import com.google.android.material.snackbar.Snackbar;
import com.libRG.CustomTextView;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import javax.inject.Provider;
import jp.wasabeef.glide.transformations.BlurTransformation;
import jp.wasabeef.glide.transformations.RoundedCornersTransformation;
import ml.docilealligator.infinityforreddit.PostGalleryGridLayoutItemDecoration;
import ml.docilealligator.infinityforreddit.R;
import ml.docilealligator.infinityforreddit.RedditDataRoomDatabase;
import ml.docilealligator.infinityforreddit.SaveMemoryCenterInisdeDownsampleStrategy;
import ml.docilealligator.infinityforreddit.account.Account;
import ml.docilealligator.infinityforreddit.account.AccountScope;
import ml.docilealligator.infinityforreddit.activities.BaseActivity;
import ml.docilealligator.infinityforreddit.activities.CommentActivity;
import ml.docilealligator.infinityforreddit.activities.LinkResolverActivity;
import ml.docilealligator.infinityforreddit.activities.SubmitCrosspostActivity;
import ml.docilealligator.infinityforreddit.activities.ViewImageOrGifActivity;
import ml.docilealligator.infinityforreddit.activities.ViewImgurMediaActivity;
import ml.docilealligator.infinityforreddit.activities.ViewPostDetailActivity;
import ml.docilealligator.infinityforreddit.activities.ViewRedditGalleryActivity;
import ml.docilealligator.infinityforreddit.activities.ViewUserDetailActivity;
import ml.docilealligator.infinityforreddit.activities.ViewVideoActivity;
import ml.docilealligator.infinityforreddit.apis.StreamableAPI;
import ml.docilealligator.infinityforreddit.bottomsheetfragments.ShareBottomSheetFragment;
import ml.docilealligator.infinityforreddit.customtheme.CustomThemeWrapper;
import ml.docilealligator.infinityforreddit.customviews.AspectRatioGifImageView;
import ml.docilealligator.infinityforreddit.customviews.BadgedNameTextView;
import ml.docilealligator.infinityforreddit.customviews.LinearLayoutManagerBugFixed;
import ml.docilealligator.infinityforreddit.customviews.PostTypeIndicatorView;
import ml.docilealligator.infinityforreddit.databinding.ItemPostCard3GalleryTypeBinding;
import ml.docilealligator.infinityforreddit.databinding.ItemPostCard3TextBinding;
import ml.docilealligator.infinityforreddit.databinding.ItemPostCard3VideoTypeAutoplayBinding;
import ml.docilealligator.infinityforreddit.databinding.ItemPostCard3VideoTypeAutoplayLegacyControllerBinding;
import ml.docilealligator.infinityforreddit.databinding.ItemPostCard3WithPreviewBinding;
import ml.docilealligator.infinityforreddit.databinding.ItemPostCompact2Binding;
import ml.docilealligator.infinityforreddit.databinding.ItemPostCompact2RightThumbnailBinding;
import ml.docilealligator.infinityforreddit.databinding.ItemPostGalleryBinding;
import ml.docilealligator.infinityforreddit.databinding.ItemPostGalleryGalleryTypeBinding;
import ml.docilealligator.infinityforreddit.databinding.ItemPostGalleryTypeBinding;
import ml.docilealligator.infinityforreddit.databinding.ItemPostTextBinding;
import ml.docilealligator.infinityforreddit.databinding.ItemPostVideoTypeAutoplayBinding;
import ml.docilealligator.infinityforreddit.databinding.ItemPostVideoTypeAutoplayLegacyControllerBinding;
import ml.docilealligator.infinityforreddit.databinding.ItemPostWithPreviewBinding;
import ml.docilealligator.infinityforreddit.events.PostUpdateEventToPostDetailFragment;
import ml.docilealligator.infinityforreddit.events.PostUpdateEventToPostList;
import ml.docilealligator.infinityforreddit.fragments.PostFragmentBase;
import ml.docilealligator.infinityforreddit.post.FetchImageHostMedia;
import ml.docilealligator.infinityforreddit.post.HidePost;
import ml.docilealligator.infinityforreddit.post.Post;
import ml.docilealligator.infinityforreddit.post.PostType;
import ml.docilealligator.infinityforreddit.readpost.ReadPostModification;
import ml.docilealligator.infinityforreddit.readpost.ReadPostType;
import ml.docilealligator.infinityforreddit.readpost.ReadPostsUtils;
import ml.docilealligator.infinityforreddit.user.UserMarkChanges;
import ml.docilealligator.infinityforreddit.user.UserMarks;
import ml.docilealligator.infinityforreddit.utils.ImageHostUtils;
import ml.docilealligator.infinityforreddit.utils.NewWindowUtils;
import ml.docilealligator.infinityforreddit.utils.SharedPreferencesUtils;
import ml.docilealligator.infinityforreddit.utils.ShortClipHostUtils;
import ml.docilealligator.infinityforreddit.utils.UserMarkIcon;
import ml.docilealligator.infinityforreddit.utils.UserTagChip;
import ml.docilealligator.infinityforreddit.utils.Utils;
import ml.docilealligator.infinityforreddit.videoautoplay.CacheManager;
import ml.docilealligator.infinityforreddit.videoautoplay.ExoCreator;
import ml.docilealligator.infinityforreddit.videoautoplay.MultiPlayPlayerSelector;
import ml.docilealligator.infinityforreddit.videoautoplay.PlayerSelector;
import ml.docilealligator.infinityforreddit.videoautoplay.ToroPlayer;
import ml.docilealligator.infinityforreddit.videoautoplay.ToroUtil;
import ml.docilealligator.infinityforreddit.videoautoplay.media.PlaybackInfo;
import ml.docilealligator.infinityforreddit.videoautoplay.widget.Container;
import okhttp3.OkHttpClient;
import org.greenrobot.eventbus.EventBus;
import retrofit2.Retrofit;

/**
 * Created by alex on 2/25/18.
 */

@SuppressWarnings("NullAway.Init")
public class PostRecyclerViewAdapter extends PagingDataAdapter<Post, RecyclerView.ViewHolder> implements CacheManager {
    /** @see PostCardPreviewStyle#SQUARE_PREVIEW_RATIO */
    private static final float SQUARE_PREVIEW_RATIO = PostCardPreviewStyle.SQUARE_PREVIEW_RATIO;

    private static final int VIEW_TYPE_POST_CARD_VIDEO_AUTOPLAY_TYPE = 1;
    private static final int VIEW_TYPE_POST_CARD_WITH_PREVIEW_TYPE = 2;
    private static final int VIEW_TYPE_POST_CARD_GALLERY_TYPE = 3;
    private static final int VIEW_TYPE_POST_CARD_TEXT_TYPE = 4;
    private static final int VIEW_TYPE_POST_COMPACT_2 = 6;
    private static final int VIEW_TYPE_POST_GALLERY = 7;
    private static final int VIEW_TYPE_POST_GALLERY_GALLERY_TYPE = 8;
    private static final int VIEW_TYPE_POST_GALLERY_VIDEO_AUTOPLAY_TYPE = 9;
    private static final int VIEW_TYPE_POST_CARD_3_VIDEO_AUTOPLAY_TYPE = 13;
    private static final int VIEW_TYPE_POST_CARD_3_WITH_PREVIEW_TYPE = 14;
    private static final int VIEW_TYPE_POST_CARD_3_GALLERY_TYPE = 15;
    private static final int VIEW_TYPE_POST_CARD_3_TEXT_TYPE = 16;
    private static final int VIEW_TYPE_STICKIED_COLLAPSED = 18;

    private static final DiffUtil.ItemCallback<Post> DIFF_CALLBACK = new DiffUtil.ItemCallback<>() {
        @Override
        public boolean areItemsTheSame(@NonNull Post post, @NonNull Post t1) {
            return post.getId().equals(t1.getId());
        }

        @Override
        public boolean areContentsTheSame(@NonNull Post post, @NonNull Post t1) {
            return false;
        }
    };

    private BaseActivity mActivity;
    private PostFragmentBase mFragment;
    private SharedPreferences mSharedPreferences;
    @Nullable
    private SharedPreferences mPostHistorySharedPreferences;
    private RedditDataRoomDatabase mRedditDataRoomDatabase;
    private Executor mExecutor;
    private Retrofit mOauthRetrofit;
    private Provider<StreamableAPI> mStreamableApiProvider;
    private OkHttpClient mShortClipOkHttpClient;
    private OkHttpClient mImageHostOkHttpClient;
    @Nullable
    private String mAccessToken;
    private String mAccountName;
    private RequestManager mGlide;
    private int mMaxResolution;
    private SaveMemoryCenterInisdeDownsampleStrategy mSaveMemoryCenterInsideDownsampleStrategy;
    private CustomThemeWrapper mCustomThemeWrapper;
    private Locale mLocale;
    private boolean canStartActivity = true;
    @PostType
    private int mPostType;
    private int mPostLayout;
    private int mDefaultLinkPostLayout;
    private int mColorAccent;
    private int mCardViewBackgroundColor;
    private int mReadPostCardViewBackgroundColor;
    private int mFilledCardViewBackgroundColor;
    private int mReadPostFilledCardViewBackgroundColor;
    private int mPrimaryTextColor;
    private int mSecondaryTextColor;
    private int mPostTitleColor;
    private int mPostContentColor;
    private int mReadPostTitleColor;
    private int mReadPostContentColor;
    private int mStickiedPostIconTint;
    private int mTextTypeBackgroundColor;
    private int mImageTypeBackgroundColor;
    private int mLinkTypeBackgroundColor;
    private int mVideoTypeBackgroundColor;
    private int mGifTypeBackgroundColor;
    private int mGalleryTypeBackgroundColor;
    private int mPostTypeTextColor;
    private int mSubredditColor;
    private int mUsernameColor;
    private int mModeratorColor;
    /** The account's followed, saved and favourited users; see {@link UserMarkIcon}. */
    private UserMarks mUserMarks = UserMarks.EMPTY;
    private int mSpoilerBackgroundColor;
    private int mSpoilerTextColor;
    private int mFlairBackgroundColor;
    private int mFlairTextColor;
    private int mNSFWBackgroundColor;
    private int mNSFWTextColor;
    private int mArchivedIconTint;
    private int mLockedIconTint;
    private int mCrosspostIconTint;
    private int mMediaIndicatorIconTint;
    private int mMediaIndicatorBackgroundColor;
    private int mNoPreviewPostTypeBackgroundColor;
    private int mNoPreviewPostTypeIconTint;
    private int mUpvotedColor;
    private int mDownvotedColor;
    private int mVoteAndReplyUnavailableVoteButtonColor;
    private int mPostIconAndInfoColor;
    private int mDividerColor;
    private float mScale;
    private int mCompactThumbnailSizeDp;
    private int mCompactThumbnailBoxSizePx;
    // The inset the gallery grid's item decoration takes from each cell, for working out the cells'
    // boxes before layout. Half the decoration's offset, as PostGalleryGridLayoutItemDecoration halves it.
    private int mGalleryGridCellInsetPx;
    private boolean mDisplaySubredditName;
    private boolean mVoteButtonsOnTheRight;
    private boolean mNeedBlurNsfw;
    private boolean mDoNotBlurNsfwInNsfwSubreddits;
    private boolean mNeedBlurSpoiler;
    private boolean mShowElapsedTime;
    private String mTimeFormatPattern;
    private boolean mShowDividerInCompactLayout;
    private boolean mShowAbsoluteNumberOfVotes;
    private boolean mAutoplay = false;
    private boolean mAutoplayNsfwVideos;
    private boolean mMuteAutoplayingVideos;
    private boolean mShowThumbnailOnTheLeftInCompactLayout;
    private boolean mHidePostTypeIndicator;
    private boolean mHideImageCountInGallery;
    private double mStartAutoplayVisibleAreaOffset;
    private boolean mMuteNSFWVideo;
    private boolean mLongPressToHideToolbarInCompactLayout;
    private boolean mCompactLayoutToolbarHiddenByDefault;
    private boolean mDataSavingMode = false;
    private boolean mDisableImagePreview;
    private boolean mOnlyDisablePreviewInVideoAndGifPosts;
    private boolean mMarkPostsAsRead;
    private boolean mMarkPostsAsReadAfterVoting;
    private boolean mMarkPostsAsReadOnScroll;
    private boolean mHidePostType;
    private boolean mHideNsfwBadge;
    private boolean mCollapseStickiedPosts;
    private boolean mPostTypeCardBorder = true;
    // Stickied posts the user opened up this session, so they stay open as the list rebinds.
    private final java.util.Set<String> mExpandedStickiedPosts = new java.util.HashSet<>();
    private boolean mPostTypeTriangleIndicator;
    private boolean mHidePostFlair;
    private boolean mHideSubredditAndUserPrefix;
    private boolean mHideTheNumberOfVotes;
    private boolean mHideVoteButtons;
    private boolean mHideTheNumberOfComments;
    private boolean mLegacyAutoplayVideoControllerUI;
    private boolean mFixedHeightPreviewInCard;
    private boolean mHideTextPostContent;
    private boolean mEasierToWatchInFullScreen;
    private boolean mDisableProfileAvatarAnimation;
    private boolean mShowGalleryMediaAsGrid;
    private boolean mShowToolbarItemsBasedOnSpace;
    private int mDataSavingModeDefaultResolution;
    private int mNonDataSavingModeDefaultResolution;
    private int mSimultaneousAutoplayLimit;
    private String mLongPressPostNonMediaAreaAction = SharedPreferencesUtils.LONG_PRESS_POST_VALUE_SHOW_POST_OPTIONS;
    private String mLongPressPostMediaAction = SharedPreferencesUtils.LONG_PRESS_POST_VALUE_SHOW_POST_OPTIONS;
    private boolean mHandleReadPost;
    private ExoCreator mExoCreator;
    private Callback mCallback;
    private boolean canPlayVideo = true;
    private RecyclerView.RecycledViewPool mGalleryRecycledViewPool;
    private MultiPlayPlayerSelector multiPlayPlayerSelector;
    private int itemWidth;
    // The same column in pixels, as laid out. Not itemWidth times the density: that is rounded to
    // a whole dp, and a box a pixel off the view's is a decode at the wrong size.
    private int itemWidthPx;
    // Whether the feed is in more than one column, where its decoration cancels Card Top's own
    // margins (PostFragmentBase.StaggeredGridLayoutManagerItemOffsetDecoration).
    private boolean multiColumn;

    // postHistorySharedPreferences will be null when being used in HistoryPostFragment.
    public PostRecyclerViewAdapter(BaseActivity activity, PostFragmentBase fragment, RedditDataRoomDatabase redditDataRoomDatabase,
                                Executor executor, Retrofit oauthRetrofit,
                                Retrofit redgifsRetrofit, Provider<StreamableAPI> streamableApiProvider,
                                OkHttpClient shortClipOkHttpClient,
                                OkHttpClient imageHostOkHttpClient,
                                CustomThemeWrapper customThemeWrapper, Locale locale,
                                @Nullable String accessToken, @NonNull String accountName, int postType,
                                int postLayout, boolean displaySubredditName,
                                SharedPreferences sharedPreferences, SharedPreferences currentAccountSharedPreferences,
                                SharedPreferences nsfwAndSpoilerSharedPreferences,
                                @Nullable SharedPreferences postHistorySharedPreferences,
                                ExoCreator exoCreator, Callback callback) {
        super(DIFF_CALLBACK);
        if (activity != null) {
            mActivity = activity;
            mFragment = fragment;
            mSharedPreferences = sharedPreferences;
            mRedditDataRoomDatabase = redditDataRoomDatabase;
            mExecutor = executor;
            mOauthRetrofit = oauthRetrofit;
            mStreamableApiProvider = streamableApiProvider;
            mShortClipOkHttpClient = shortClipOkHttpClient;
            mImageHostOkHttpClient = imageHostOkHttpClient;
            mAccessToken = accessToken;
            mAccountName = accountName;
            mPostType = postType;
            mDisplaySubredditName = displaySubredditName;
            mNeedBlurNsfw = nsfwAndSpoilerSharedPreferences.getBoolean(AccountScope.key(accountName, SharedPreferencesUtils.BLUR_NSFW_BASE), true);
            mDoNotBlurNsfwInNsfwSubreddits = nsfwAndSpoilerSharedPreferences.getBoolean(AccountScope.key(accountName, SharedPreferencesUtils.DO_NOT_BLUR_NSFW_IN_NSFW_SUBREDDITS), false);
            mNeedBlurSpoiler = nsfwAndSpoilerSharedPreferences.getBoolean(AccountScope.key(accountName, SharedPreferencesUtils.BLUR_SPOILER_BASE), false);
            mVoteButtonsOnTheRight = sharedPreferences.getBoolean(SharedPreferencesUtils.VOTE_BUTTONS_ON_THE_RIGHT_KEY, false);
            mShowElapsedTime = sharedPreferences.getBoolean(SharedPreferencesUtils.SHOW_ELAPSED_TIME_KEY, false);
            mTimeFormatPattern = java.util.Objects.requireNonNull(sharedPreferences.getString(SharedPreferencesUtils.TIME_FORMAT_KEY, SharedPreferencesUtils.TIME_FORMAT_DEFAULT_VALUE));
            mShowDividerInCompactLayout = sharedPreferences.getBoolean(SharedPreferencesUtils.SHOW_DIVIDER_IN_COMPACT_LAYOUT, true);
            mShowAbsoluteNumberOfVotes = sharedPreferences.getBoolean(SharedPreferencesUtils.SHOW_ABSOLUTE_NUMBER_OF_VOTES, true);
            String autoplayString = java.util.Objects.requireNonNull(sharedPreferences.getString(SharedPreferencesUtils.VIDEO_AUTOPLAY, SharedPreferencesUtils.VIDEO_AUTOPLAY_VALUE_NEVER));
            int networkType = Utils.getConnectedNetwork(activity);
            if (autoplayString.equals(SharedPreferencesUtils.VIDEO_AUTOPLAY_VALUE_ALWAYS_ON)) {
                mAutoplay = true;
            } else if (autoplayString.equals(SharedPreferencesUtils.VIDEO_AUTOPLAY_VALUE_ON_WIFI)) {
                mAutoplay = networkType == Utils.NETWORK_TYPE_WIFI;
            }
            mAutoplayNsfwVideos = sharedPreferences.getBoolean(SharedPreferencesUtils.AUTOPLAY_NSFW_VIDEOS, true);
            mMuteAutoplayingVideos = sharedPreferences.getBoolean(SharedPreferencesUtils.MUTE_AUTOPLAYING_VIDEOS, true);
            mShowThumbnailOnTheLeftInCompactLayout = sharedPreferences.getBoolean(
                    SharedPreferencesUtils.SHOW_THUMBNAIL_ON_THE_LEFT_IN_COMPACT_LAYOUT, false);
            mHidePostTypeIndicator = sharedPreferences.getBoolean(SharedPreferencesUtils.HIDE_POST_TYPE_INDICATOR, false);
            mHideImageCountInGallery = sharedPreferences.getBoolean(SharedPreferencesUtils.HIDE_IMAGE_COUNT_IN_GALLERY, false);

            Resources resources = activity.getResources();
            mStartAutoplayVisibleAreaOffset = resources.getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT ?
                    sharedPreferences.getInt(SharedPreferencesUtils.START_AUTOPLAY_VISIBLE_AREA_OFFSET_PORTRAIT, 75) / 100.0 :
                    sharedPreferences.getInt(SharedPreferencesUtils.START_AUTOPLAY_VISIBLE_AREA_OFFSET_LANDSCAPE, 50) / 100.0;

            mMuteNSFWVideo = sharedPreferences.getBoolean(SharedPreferencesUtils.MUTE_NSFW_VIDEO, false);

            mLongPressToHideToolbarInCompactLayout = sharedPreferences.getBoolean(SharedPreferencesUtils.LONG_PRESS_TO_HIDE_TOOLBAR_IN_COMPACT_LAYOUT, false);
            mCompactLayoutToolbarHiddenByDefault = sharedPreferences.getBoolean(SharedPreferencesUtils.POST_COMPACT_LAYOUT_TOOLBAR_HIDDEN_BY_DEFAULT, false);

            String dataSavingModeString = java.util.Objects.requireNonNull(sharedPreferences.getString(SharedPreferencesUtils.DATA_SAVING_MODE, SharedPreferencesUtils.DATA_SAVING_MODE_OFF));
            if (dataSavingModeString.equals(SharedPreferencesUtils.DATA_SAVING_MODE_ALWAYS)) {
                mDataSavingMode = true;
            } else if (dataSavingModeString.equals(SharedPreferencesUtils.DATA_SAVING_MODE_ONLY_ON_CELLULAR_DATA)) {
                mDataSavingMode = networkType == Utils.NETWORK_TYPE_CELLULAR;
            }
            mDisableImagePreview = sharedPreferences.getBoolean(SharedPreferencesUtils.DISABLE_IMAGE_PREVIEW, false);
            mOnlyDisablePreviewInVideoAndGifPosts = sharedPreferences.getBoolean(SharedPreferencesUtils.ONLY_DISABLE_PREVIEW_IN_VIDEO_AND_GIF_POSTS, false);
            mDisableProfileAvatarAnimation = sharedPreferences.getBoolean(SharedPreferencesUtils.DISABLE_PROFILE_AVATAR_ANIMATION, false);

            mPostHistorySharedPreferences = postHistorySharedPreferences;
            if (postHistorySharedPreferences != null) {
                mMarkPostsAsRead = postHistorySharedPreferences.getBoolean(AccountScope.key(accountName, SharedPreferencesUtils.MARK_POSTS_AS_READ_BASE), false);
                mMarkPostsAsReadAfterVoting = postHistorySharedPreferences.getBoolean(AccountScope.key(accountName, SharedPreferencesUtils.MARK_POSTS_AS_READ_AFTER_VOTING_BASE), false);
                mMarkPostsAsReadOnScroll = postHistorySharedPreferences.getBoolean(AccountScope.key(accountName, SharedPreferencesUtils.MARK_POSTS_AS_READ_ON_SCROLL_BASE), false);
                mHandleReadPost = true;
            }

            mHidePostType = sharedPreferences.getBoolean(SharedPreferencesUtils.HIDE_POST_TYPE, false);
            mHideNsfwBadge = sharedPreferences.getBoolean(SharedPreferencesUtils.HIDE_NSFW_BADGE, false);
            mCollapseStickiedPosts = sharedPreferences.getBoolean(SharedPreferencesUtils.COLLAPSE_STICKIED_POSTS, false);
            mPostTypeCardBorder = sharedPreferences.getBoolean(SharedPreferencesUtils.POST_TYPE_CARD_BORDER, true);
            mPostTypeTriangleIndicator = sharedPreferences.getBoolean(SharedPreferencesUtils.POST_TYPE_TRIANGLE_INDICATOR, false);
            mHidePostFlair = sharedPreferences.getBoolean(SharedPreferencesUtils.HIDE_POST_FLAIR, false);
            mHideSubredditAndUserPrefix = sharedPreferences.getBoolean(SharedPreferencesUtils.HIDE_SUBREDDIT_AND_USER_PREFIX, false);
            mHideTheNumberOfVotes = sharedPreferences.getBoolean(SharedPreferencesUtils.HIDE_THE_NUMBER_OF_VOTES, false);
            mHideVoteButtons = sharedPreferences.getBoolean(SharedPreferencesUtils.HIDE_VOTE_BUTTONS, false);
            mHideTheNumberOfComments = sharedPreferences.getBoolean(SharedPreferencesUtils.HIDE_THE_NUMBER_OF_COMMENTS, false);
            mLegacyAutoplayVideoControllerUI = sharedPreferences.getBoolean(SharedPreferencesUtils.LEGACY_AUTOPLAY_VIDEO_CONTROLLER_UI, false);
            mFixedHeightPreviewInCard = sharedPreferences.getBoolean(SharedPreferencesUtils.FIXED_HEIGHT_PREVIEW_IN_CARD, false);
            mHideTextPostContent = sharedPreferences.getBoolean(SharedPreferencesUtils.HIDE_TEXT_POST_CONTENT, false);
            mEasierToWatchInFullScreen = sharedPreferences.getBoolean(SharedPreferencesUtils.EASIER_TO_WATCH_IN_FULL_SCREEN, false);
            mShowGalleryMediaAsGrid = sharedPreferences.getBoolean(SharedPreferencesUtils.SHOW_GALLERY_MEDIA_AS_GRID, true);
            mShowToolbarItemsBasedOnSpace = sharedPreferences.getBoolean(SharedPreferencesUtils.SHOW_POST_AND_COMMENT_TOOLBAR_ITEMS_BASED_ON_SPACE, false);
            mDataSavingModeDefaultResolution = SharedPreferencesUtils.getInt(mSharedPreferences, SharedPreferencesUtils.REDDIT_VIDEO_DEFAULT_RESOLUTION, "360");
            mNonDataSavingModeDefaultResolution = SharedPreferencesUtils.getInt(mSharedPreferences, SharedPreferencesUtils.REDDIT_VIDEO_DEFAULT_RESOLUTION_NO_DATA_SAVING, "0");
            mSimultaneousAutoplayLimit = SharedPreferencesUtils.getInt(mSharedPreferences, SharedPreferencesUtils.SIMULTANEOUS_AUTOPLAY_LIMIT, "1");

            mPostLayout = postLayout;
            mDefaultLinkPostLayout = SharedPreferencesUtils.getInt(sharedPreferences, SharedPreferencesUtils.DEFAULT_LINK_POST_LAYOUT_KEY, "-1");

            mColorAccent = customThemeWrapper.getColorAccent();
            mCardViewBackgroundColor = customThemeWrapper.getCardViewBackgroundColor();
            mReadPostCardViewBackgroundColor = customThemeWrapper.getReadPostCardViewBackgroundColor();
            mFilledCardViewBackgroundColor = customThemeWrapper.getFilledCardViewBackgroundColor();
            mReadPostFilledCardViewBackgroundColor = customThemeWrapper.getReadPostFilledCardViewBackgroundColor();
            mPrimaryTextColor = customThemeWrapper.getPrimaryTextColor();
            mSecondaryTextColor = customThemeWrapper.getSecondaryTextColor();
            mPostTitleColor = customThemeWrapper.getPostTitleColor();
            mPostContentColor = customThemeWrapper.getPostContentColor();
            mReadPostTitleColor = customThemeWrapper.getReadPostTitleColor();
            mReadPostContentColor = customThemeWrapper.getReadPostContentColor();
            mStickiedPostIconTint = customThemeWrapper.getStickiedPostIconTint();
            mTextTypeBackgroundColor = customThemeWrapper.getTextTypeBackgroundColor();
            mImageTypeBackgroundColor = customThemeWrapper.getImageTypeBackgroundColor();
            mLinkTypeBackgroundColor = customThemeWrapper.getLinkTypeBackgroundColor();
            mVideoTypeBackgroundColor = customThemeWrapper.getVideoTypeBackgroundColor();
            mGifTypeBackgroundColor = customThemeWrapper.getGifTypeBackgroundColor();
            mGalleryTypeBackgroundColor = customThemeWrapper.getGalleryTypeBackgroundColor();
            mPostTypeTextColor = customThemeWrapper.getPostTypeTextColor();
            mSubredditColor = customThemeWrapper.getSubreddit();
            mUsernameColor = customThemeWrapper.getUsername();
            mModeratorColor = customThemeWrapper.getModerator();
            mSpoilerBackgroundColor = customThemeWrapper.getSpoilerBackgroundColor();
            mSpoilerTextColor = customThemeWrapper.getSpoilerTextColor();
            mFlairBackgroundColor = customThemeWrapper.getFlairBackgroundColor();
            mFlairTextColor = customThemeWrapper.getFlairTextColor();
            mNSFWBackgroundColor = customThemeWrapper.getNsfwBackgroundColor();
            mNSFWTextColor = customThemeWrapper.getNsfwTextColor();
            mArchivedIconTint = customThemeWrapper.getArchivedIconTint();
            mLockedIconTint = customThemeWrapper.getLockedIconTint();
            mCrosspostIconTint = customThemeWrapper.getCrosspostIconTint();
            mMediaIndicatorIconTint = customThemeWrapper.getMediaIndicatorIconColor();
            mMediaIndicatorBackgroundColor = customThemeWrapper.getMediaIndicatorBackgroundColor();
            mNoPreviewPostTypeBackgroundColor = customThemeWrapper.getNoPreviewPostTypeBackgroundColor();
            mNoPreviewPostTypeIconTint = customThemeWrapper.getNoPreviewPostTypeIconTint();
            mUpvotedColor = customThemeWrapper.getUpvoted();
            mDownvotedColor = customThemeWrapper.getDownvoted();
            mVoteAndReplyUnavailableVoteButtonColor = customThemeWrapper.getVoteAndReplyUnavailableButtonColor();
            mPostIconAndInfoColor = customThemeWrapper.getPostIconAndInfoColor();
            mDividerColor = customThemeWrapper.getDividerColor();

            mScale = resources.getDisplayMetrics().density;
            mCompactThumbnailSizeDp = SharedPreferencesUtils.getInt(sharedPreferences,
                    SharedPreferencesUtils.POST_COMPACT_THUMBNAIL_SIZE,
                    SharedPreferencesUtils.POST_COMPACT_THUMBNAIL_SIZE_DEFAULT_VALUE);
            mCompactThumbnailBoxSizePx = Math.round(mCompactThumbnailSizeDp * mScale);
            mGalleryGridCellInsetPx = resources.getDimensionPixelSize(R.dimen.staggeredLayoutManagerItemOffset) / 2;
            mGlide = Glide.with(mActivity);
            mMaxResolution = SharedPreferencesUtils.getInt(mSharedPreferences, SharedPreferencesUtils.POST_FEED_MAX_RESOLUTION, "5000000");
            mSaveMemoryCenterInsideDownsampleStrategy = new SaveMemoryCenterInisdeDownsampleStrategy(mMaxResolution);
            mCustomThemeWrapper = customThemeWrapper;
            mLocale = locale;
            mExoCreator = exoCreator;
            mCallback = callback;

            mGalleryRecycledViewPool = new RecyclerView.RecycledViewPool();
            multiPlayPlayerSelector = new MultiPlayPlayerSelector(mSimultaneousAutoplayLimit,
                    this::unifiedAutoplaySelection);
        }
    }

    public void setCanStartActivity(boolean canStartActivity) {
        this.canStartActivity = canStartActivity;
    }

    /** Hands the row classes live values from this adapter; see {@link PostRowHost}. */
    private final RowHost mRowHost = new RowHost();

    private final class RowHost implements PostCompactHost, PostGalleryHost, PostVideoHost, PostPreviewHost {
        @Override public BaseActivity activity() { return mActivity; }
        @Override public Callback callback() { return mCallback; }
        @Override public String accountName() { return mAccountName; }
        @Override @Nullable public String accessToken() { return mAccessToken; }
        @Override public Retrofit oauthRetrofit() { return mOauthRetrofit; }
        @Override public RedditDataRoomDatabase database() { return mRedditDataRoomDatabase; }
        @Override public Executor executor() { return mExecutor; }
        @Override @Nullable public SharedPreferences postHistorySharedPreferences() { return mPostHistorySharedPreferences; }
        @Override public boolean displaySubredditName() { return mDisplaySubredditName; }
        @Override public boolean markPostsAsReadAfterVoting() { return mMarkPostsAsReadAfterVoting; }
        @Override public boolean hideTheNumberOfVotes() { return mHideTheNumberOfVotes; }
        @Override public boolean showAbsoluteNumberOfVotes() { return mShowAbsoluteNumberOfVotes; }
        @Override public boolean handleReadPost() { return mHandleReadPost; }
        @Override public int postTitleColor() { return mPostTitleColor; }
        @Override public int readPostTitleColor() { return mReadPostTitleColor; }
        @Override public int postContentColor() { return mPostContentColor; }
        @Override public int readPostContentColor() { return mReadPostContentColor; }
        @Override public int upvotedColor() { return mUpvotedColor; }
        @Override public int downvotedColor() { return mDownvotedColor; }
        @Override public int postIconAndInfoColor() { return mPostIconAndInfoColor; }
        @Override @Nullable public Post getItem(int position) { return PostRecyclerViewAdapter.this.getItem(position); }
        @Override public boolean canStartActivity() { return canStartActivity; }
        @Override public void setCanStartActivity(boolean value) { canStartActivity = value; }
        @Override public void openViewPostDetailActivity(Post post, int position) { PostRecyclerViewAdapter.this.openViewPostDetailActivity(post, position); }
        @Override public void shareLink(Post post) { PostRecyclerViewAdapter.this.shareLink(post); }
        @Override public void storeReadState(Post post, boolean read) { PostRecyclerViewAdapter.this.storeReadState(post, read); }
        @Override public int archivedIconTint() { return mArchivedIconTint; }
        @Override public int cardViewBackgroundColor() { return mCardViewBackgroundColor; }
        @Override public int colorAccent() { return mColorAccent; }
        @Override public int compactThumbnailBoxSizePx() { return mCompactThumbnailBoxSizePx; }
        @Override public int crosspostIconTint() { return mCrosspostIconTint; }
        @Override public int dividerColor() { return mDividerColor; }
        @Override public int filledCardViewBackgroundColor() { return mFilledCardViewBackgroundColor; }
        @Override public int flairBackgroundColor() { return mFlairBackgroundColor; }
        @Override public int flairTextColor() { return mFlairTextColor; }
        @Override public int lockedIconTint() { return mLockedIconTint; }
        @Override public int mediaIndicatorBackgroundColor() { return mMediaIndicatorBackgroundColor; }
        @Override public int mediaIndicatorIconTint() { return mMediaIndicatorIconTint; }
        @Override public int nsfwBackgroundColor() { return mNSFWBackgroundColor; }
        @Override public int nsfwTextColor() { return mNSFWTextColor; }
        @Override public int noPreviewPostTypeBackgroundColor() { return mNoPreviewPostTypeBackgroundColor; }
        @Override public int noPreviewPostTypeIconTint() { return mNoPreviewPostTypeIconTint; }
        @Override public int postLayout() { return mPostLayout; }
        @Override public int postTypeTextColor() { return mPostTypeTextColor; }
        @Override public int readPostCardViewBackgroundColor() { return mReadPostCardViewBackgroundColor; }
        @Override public int readPostFilledCardViewBackgroundColor() { return mReadPostFilledCardViewBackgroundColor; }
        @Override public int secondaryTextColor() { return mSecondaryTextColor; }
        @Override public int spoilerBackgroundColor() { return mSpoilerBackgroundColor; }
        @Override public int spoilerTextColor() { return mSpoilerTextColor; }
        @Override public int stickiedPostIconTint() { return mStickiedPostIconTint; }
        @Override public boolean longPressToHideToolbarInCompactLayout() { return mLongPressToHideToolbarInCompactLayout; }
        @Override public boolean markPostsAsRead() { return mMarkPostsAsRead; }
        @Override public boolean voteButtonsOnTheRight() { return mVoteButtonsOnTheRight; }
        @Override public String longPressPostMediaAction() { return mLongPressPostMediaAction; }
        @Override public String longPressPostNonMediaAreaAction() { return mLongPressPostNonMediaAreaAction; }
        @Override public float scale() { return mScale; }
        @Override public FragmentManager childFragmentManager() { return mFragment.getChildFragmentManager(); }
        @Override public GradientDrawable createRoundedBackground(int color, float cornerRadiusDp) { return PostRecyclerViewAdapter.this.createRoundedBackground(color, cornerRadiusDp); }
        @Override public void openMedia(Post post) { PostRecyclerViewAdapter.this.openMedia(post); }
        @Override public void openMedia(Post post, boolean peekMedia) { PostRecyclerViewAdapter.this.openMedia(post, peekMedia); }
        @Override public int subredditColor() { return mSubredditColor; }
        @Override public int usernameColor() { return mUsernameColor; }
        @Override public PostFragmentBase fragment() { return mFragment; }
        @Override public CustomThemeWrapper customThemeWrapper() { return mCustomThemeWrapper; }
        @Override public boolean easierToWatchInFullScreen() { return mEasierToWatchInFullScreen; }
        @Override public boolean dataSavingMode() { return mDataSavingMode; }
        @Override public int dataSavingModeDefaultResolution() { return mDataSavingModeDefaultResolution; }
        @Override public int nonDataSavingModeDefaultResolution() { return mNonDataSavingModeDefaultResolution; }
        @Override public Provider<StreamableAPI> streamableApiProvider() { return mStreamableApiProvider; }
        @Override public OkHttpClient shortClipOkHttpClient() { return mShortClipOkHttpClient; }
        @Override public ExoCreator exoCreator() { return mExoCreator; }
        @Override public void applyNoPreviewPlaceholder(ImageView previewImageView) { PostRecyclerViewAdapter.this.applyNoPreviewPlaceholder(previewImageView); }
        @Override public void scheduleGifAutoplayUpdate() { PostRecyclerViewAdapter.this.scheduleGifAutoplayUpdate(); }
        @Override public void notifyItemChanged(int position) { PostRecyclerViewAdapter.this.notifyItemChanged(position); }
        @Override public void openMedia(Post post, long videoProgress) { PostRecyclerViewAdapter.this.openMedia(post, videoProgress); }
        @Override public boolean fixedHeightPreviewInCard() { return mFixedHeightPreviewInCard; }
        @Override public int primaryTextColor() { return mPrimaryTextColor; }
        @Override public SharedPreferences sharedPreferences() { return mSharedPreferences; }
        @Override public RequestManager glide() { return mGlide; }
        @Override public SaveMemoryCenterInisdeDownsampleStrategy saveMemoryDownsampleStrategy() { return mSaveMemoryCenterInsideDownsampleStrategy; }
        @Override public boolean hideImageCountInGallery() { return mHideImageCountInGallery; }
        @Override public double startAutoplayVisibleAreaOffset() { return mStartAutoplayVisibleAreaOffset; }
        @Override public boolean canPlayVideo() { return canPlayVideo; }
        @Override @Nullable public PlayerSelector playerSelector() { return multiPlayPlayerSelector; }
        @Override public RecyclerView.RecycledViewPool galleryRecycledViewPool() { return mGalleryRecycledViewPool; }
        @Override public boolean swipingBetweenTabsDisabled() { return isSwipingBetweenTabsDisabled(); }
        @Override public void openMedia(Post post, int galleryItemIndex, boolean peekMedia) { PostRecyclerViewAdapter.this.openMedia(post, galleryItemIndex, peekMedia); }
    }

    @Override
    public int getItemViewType(int position) {
        Post post = getItem(position);
        if (isCollapsedSticky(post)) {
            return VIEW_TYPE_STICKIED_COLLAPSED;
        }
        return viewTypeFor(post);
    }

    /**
     * Whether {@code post} is a subreddit's stickied post drawn as a one-line row: only on a
     * subreddit's own page, where the pin means something, and until the user taps it open.
     */
    private boolean isCollapsedSticky(@Nullable Post post) {
        return mCollapseStickiedPosts && post != null && post.isStickied()
                && mPostType == PostType.SUBREDDIT && !mDisplaySubredditName
                && !mExpandedStickiedPosts.contains(post.getFullName());
    }

    // return true if the current value is not the same as the new value
    public boolean setPostTypeCardBorder(boolean border) {
        if (mPostTypeCardBorder != border) {
            mPostTypeCardBorder = border;
            return true;
        }
        return false;
    }

    private static final String TOP_BORDER_TAG = "post_type_top_border";

    /**
     * A thin line across the top of a Card Bottom or Card Top card, in the post type's colour: the card
     * layouts' version of the compact layout's triangle. The card is a FrameLayout, so the line is
     * one more child laid over its top edge, which the card's rounded corners clip.
     */
    private void applyCardTopBorder(PostViewHolder holder) {
        if ((!SharedPreferencesUtils.isCardBottom(mPostLayout)
                && !SharedPreferencesUtils.isCardTop(mPostLayout))
                || !(holder.itemView instanceof FrameLayout) || holder.post == null) {
            return;
        }
        FrameLayout card = (FrameLayout) holder.itemView;
        View last = card.getChildAt(card.getChildCount() - 1);
        View border = last != null && TOP_BORDER_TAG.equals(last.getTag()) ? last : null;
        if (!mPostTypeCardBorder) {
            if (border != null) {
                border.setVisibility(View.GONE);
            }
            return;
        }
        if (border == null) {
            border = new View(card.getContext());
            border.setTag(TOP_BORDER_TAG);
            int height = Math.round(Utils.convertDpToPixel(3, card.getContext()));
            card.addView(border, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, height, Gravity.TOP));
        }
        border.setVisibility(View.VISIBLE);
        int typeColor = getTypeColor(holder.post.getPostType());
        if (holder.post.isNSFW()) {
            // The post type's colour on the left, blending into the NSFW colour on the right.
            border.setBackground(new android.graphics.drawable.GradientDrawable(
                    android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT,
                    new int[]{typeColor, mNSFWBackgroundColor}));
        } else {
            border.setBackgroundColor(typeColor);
        }
    }

    // return true if the current value is not the same as the new value
    public boolean setCollapseStickiedPosts(boolean collapse) {
        if (mCollapseStickiedPosts != collapse) {
            mCollapseStickiedPosts = collapse;
            return true;
        }
        return false;
    }

    /**
     * With Collapse Stickied Posts on, the pin on an opened-up stickied post folds it back into its
     * one-line row. The pin is small, so its touch area is widened to a fingertip.
     */
    private void makePinCollapseStickiedPost(PostViewHolder holder, Post post) {
        ImageView pin = holder.stickiedPostImageView;
        if (!mCollapseStickiedPosts) {
            pin.setOnClickListener(null);
            pin.setClickable(false);
            return;
        }
        pin.setOnClickListener(view -> {
            int position = holder.getBindingAdapterPosition();
            if (position != RecyclerView.NO_POSITION) {
                mExpandedStickiedPosts.remove(post.getFullName());
                notifyItemChanged(position);
            }
        });
        pin.post(() -> {
            View parent = pin.getParent() instanceof View ? (View) pin.getParent() : null;
            if (parent == null) {
                return;
            }
            android.graphics.Rect area = new android.graphics.Rect();
            pin.getHitRect(area);
            int extra = Math.round(Utils.convertDpToPixel(14, pin.getContext()));
            area.inset(-extra, -extra);
            parent.setTouchDelegate(new android.view.TouchDelegate(area, pin));
        });
    }

    private class StickiedCollapsedViewHolder extends RecyclerView.ViewHolder {
        final ml.docilealligator.infinityforreddit.databinding.ItemPostStickiedCollapsedBinding binding;

        StickiedCollapsedViewHolder(ml.docilealligator.infinityforreddit.databinding.ItemPostStickiedCollapsedBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
            if (mActivity.titleTypeface != null) {
                binding.titleTextViewItemPostStickiedCollapsed.setTypeface(mActivity.titleTypeface);
            }
        }

        void bind(Post post) {
            itemView.setBackgroundColor(mCardViewBackgroundColor);
            binding.titleTextViewItemPostStickiedCollapsed.setText(post.getTitle());
            binding.titleTextViewItemPostStickiedCollapsed.setTextColor(post.isRead() && mHandleReadPost
                    ? mReadPostTitleColor : mPostTitleColor);
            binding.pinImageViewItemPostStickiedCollapsed.setColorFilter(mStickiedPostIconTint, PorterDuff.Mode.SRC_IN);
            binding.expandImageViewItemPostStickiedCollapsed.setColorFilter(mSecondaryTextColor, PorterDuff.Mode.SRC_IN);
            itemView.setOnClickListener(view -> {
                int position = getBindingAdapterPosition();
                if (position != RecyclerView.NO_POSITION) {
                    mExpandedStickiedPosts.add(post.getFullName());
                    notifyItemChanged(position);
                }
            });
        }
    }

    /** Whether the video of a post plays by itself in the feed: autoplay is on, and NSFW and spoiler posts are not held back. */
    private boolean autoplaysVideo(Post post) {
        return mAutoplay && !((!mAutoplayNsfwVideos && post.isNSFW()) || post.isSpoiler());
    }

    /**
     * The view type a row showing {@code post} gets, or the layout's default while the post is not
     * loaded. Separate from {@link #getItemViewType} so {@link CompactThumbnailPreloader} can ask what
     * a post will be drawn as without going through a position, which would ask Paging to load it.
     */
    private int viewTypeFor(@Nullable Post post) {
        if (SharedPreferencesUtils.isCardBottom(mPostLayout)) {
            if (post != null) {
                switch (post.getPostType()) {
                    case Post.VIDEO_TYPE:
                        if (shouldUseCompactLayout(post)) {
                            return VIEW_TYPE_POST_COMPACT_2;
                        }

                        if (autoplaysVideo(post)) {
                            return VIEW_TYPE_POST_CARD_VIDEO_AUTOPLAY_TYPE;
                        }

                        return VIEW_TYPE_POST_CARD_WITH_PREVIEW_TYPE;
                    case Post.GIF_TYPE:
                    case Post.IMAGE_TYPE:
                        if (shouldUseCompactLayout(post)) {
                            return VIEW_TYPE_POST_COMPACT_2;
                        }

                        return VIEW_TYPE_POST_CARD_WITH_PREVIEW_TYPE;
                    case Post.GALLERY_TYPE:
                        if (shouldUseCompactLayout(post)) {
                            return VIEW_TYPE_POST_COMPACT_2;
                        }

                        return VIEW_TYPE_POST_CARD_GALLERY_TYPE;
                    case Post.LINK_TYPE:
                    case Post.NO_PREVIEW_LINK_TYPE:
                        if (shouldUseCompactLayout(post)) {
                            return VIEW_TYPE_POST_COMPACT_2;
                        }

                        switch (mDefaultLinkPostLayout) {
                            case SharedPreferencesUtils.POST_LAYOUT_CARD_3:
                                return VIEW_TYPE_POST_CARD_3_WITH_PREVIEW_TYPE;
                            case SharedPreferencesUtils.POST_LAYOUT_GALLERY:
                                return VIEW_TYPE_POST_GALLERY;
                            case SharedPreferencesUtils.POST_LAYOUT_COMPACT_2:
                                return VIEW_TYPE_POST_COMPACT_2;
                        }
                        return VIEW_TYPE_POST_CARD_WITH_PREVIEW_TYPE;
                    default:
                        // Self/text posts can carry a preview: one Reddit generated from a link in
                        // the body (an OpenGraph image), or the first image the body embeds (see
                        // ParsePost). Show it in the feed like Slide and reddit.com do. Unlike the
                        // post detail (issue #317), the feed renders no body inline, so the preview
                        // duplicates nothing here.
                        if (post.getPreviews() != null && !post.getPreviews().isEmpty()) {
                            return VIEW_TYPE_POST_CARD_WITH_PREVIEW_TYPE;
                        }
                        return VIEW_TYPE_POST_CARD_TEXT_TYPE;
                }
            }
            return VIEW_TYPE_POST_CARD_TEXT_TYPE;
        } else if (mPostLayout == SharedPreferencesUtils.POST_LAYOUT_COMPACT_2) {
            if (post != null) {
                if (post.getPostType() == Post.LINK_TYPE || post.getPostType() == Post.NO_PREVIEW_LINK_TYPE) {
                    switch (mDefaultLinkPostLayout) {
                        case SharedPreferencesUtils.POST_LAYOUT_CARD:
                        case SharedPreferencesUtils.POST_LAYOUT_CARD_CLEAN:
                            return VIEW_TYPE_POST_CARD_WITH_PREVIEW_TYPE;
                        case SharedPreferencesUtils.POST_LAYOUT_CARD_3:
                            return VIEW_TYPE_POST_CARD_3_WITH_PREVIEW_TYPE;
                        case SharedPreferencesUtils.POST_LAYOUT_GALLERY:
                            return VIEW_TYPE_POST_GALLERY;
                    }
                }
            }
            return VIEW_TYPE_POST_COMPACT_2;
        } else if (mPostLayout == SharedPreferencesUtils.POST_LAYOUT_GALLERY) {
            if (post != null) {
                if (post.getPostType() == Post.GALLERY_TYPE) {
                    return VIEW_TYPE_POST_GALLERY_GALLERY_TYPE;
                } else if (post.getPostType() == Post.VIDEO_TYPE && autoplaysVideo(post)) {
                    return VIEW_TYPE_POST_GALLERY_VIDEO_AUTOPLAY_TYPE;
                } else {
                    return VIEW_TYPE_POST_GALLERY;
                }
            } else {
                return VIEW_TYPE_POST_GALLERY;
            }
        } else {
            if (post != null) {
                switch (post.getPostType()) {
                    case Post.VIDEO_TYPE:
                        if (shouldUseCompactLayout(post)) {
                            return VIEW_TYPE_POST_COMPACT_2;
                        }

                        if (autoplaysVideo(post)) {
                            return VIEW_TYPE_POST_CARD_3_VIDEO_AUTOPLAY_TYPE;
                        }

                        return VIEW_TYPE_POST_CARD_3_WITH_PREVIEW_TYPE;
                    case Post.GIF_TYPE:
                    case Post.IMAGE_TYPE:
                        if (shouldUseCompactLayout(post)) {
                            return VIEW_TYPE_POST_COMPACT_2;
                        }

                        return VIEW_TYPE_POST_CARD_3_WITH_PREVIEW_TYPE;
                    case Post.GALLERY_TYPE:
                        if (shouldUseCompactLayout(post)) {
                            return VIEW_TYPE_POST_COMPACT_2;
                        }

                        return VIEW_TYPE_POST_CARD_3_GALLERY_TYPE;
                    case Post.LINK_TYPE:
                    case Post.NO_PREVIEW_LINK_TYPE:
                        if (shouldUseCompactLayout(post)) {
                            return VIEW_TYPE_POST_COMPACT_2;
                        }

                        switch (mDefaultLinkPostLayout) {
                            case SharedPreferencesUtils.POST_LAYOUT_CARD:
                            case SharedPreferencesUtils.POST_LAYOUT_CARD_CLEAN:
                                return VIEW_TYPE_POST_CARD_WITH_PREVIEW_TYPE;
                            case SharedPreferencesUtils.POST_LAYOUT_GALLERY:
                                return VIEW_TYPE_POST_GALLERY;
                            case SharedPreferencesUtils.POST_LAYOUT_COMPACT_2:
                                return VIEW_TYPE_POST_COMPACT_2;
                        }
                        return VIEW_TYPE_POST_CARD_3_WITH_PREVIEW_TYPE;
                    default:
                        // Self/text posts can carry a preview: one Reddit generated from a link in
                        // the body (an OpenGraph image), or the first image the body embeds (see
                        // ParsePost). Show it in the feed like Slide and reddit.com do. Unlike the
                        // post detail (issue #317), the feed renders no body inline, so the preview
                        // duplicates nothing here.
                        if (post.getPreviews() != null && !post.getPreviews().isEmpty()) {
                            return VIEW_TYPE_POST_CARD_3_WITH_PREVIEW_TYPE;
                        }
                        return VIEW_TYPE_POST_CARD_3_TEXT_TYPE;
                }
            }
            return VIEW_TYPE_POST_CARD_3_TEXT_TYPE;
        }
    }

    @OptIn(markerClass = UnstableApi.class)
    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == VIEW_TYPE_STICKIED_COLLAPSED) {
            return new StickiedCollapsedViewHolder(ml.docilealligator.infinityforreddit.databinding.ItemPostStickiedCollapsedBinding
                    .inflate(LayoutInflater.from(parent.getContext()), parent, false));
        }
        if (viewType == VIEW_TYPE_POST_CARD_VIDEO_AUTOPLAY_TYPE) {
            if (mDataSavingMode) {
                return new PostWithPreviewTypeViewHolder(ItemPostWithPreviewBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false), mRowHost);
            }

            if (mLegacyAutoplayVideoControllerUI) {
                return new PostVideoAutoplayLegacyControllerViewHolder(ItemPostVideoTypeAutoplayLegacyControllerBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false), mRowHost);
            } else {
                return new PostVideoAutoplayViewHolder(ItemPostVideoTypeAutoplayBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false), mRowHost);
            }
        } else if (viewType == VIEW_TYPE_POST_CARD_WITH_PREVIEW_TYPE) {
            return new PostWithPreviewTypeViewHolder(ItemPostWithPreviewBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false), mRowHost);
        } else if (viewType == VIEW_TYPE_POST_CARD_GALLERY_TYPE) {
            return new PostGalleryTypeViewHolder(ItemPostGalleryTypeBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false), mRowHost);
        } else if (viewType == VIEW_TYPE_POST_CARD_TEXT_TYPE) {
            return new PostTextTypeViewHolder(ItemPostTextBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false), mRowHost);
        } else if (viewType == VIEW_TYPE_POST_COMPACT_2) {
            if (mShowThumbnailOnTheLeftInCompactLayout) {
                return new PostCompact2LeftThumbnailViewHolder(ItemPostCompact2Binding.inflate(LayoutInflater.from(parent.getContext()), parent, false), mRowHost);
            } else {
                return new PostCompact2RightThumbnailViewHolder(ItemPostCompact2RightThumbnailBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false), mRowHost);
            }
        } else if (viewType == VIEW_TYPE_POST_GALLERY) {
            return new PostGalleryViewHolder(ItemPostGalleryBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false), mRowHost);
        } else if (viewType == VIEW_TYPE_POST_GALLERY_VIDEO_AUTOPLAY_TYPE) {
            if (mDataSavingMode) {
                return new PostGalleryViewHolder(ItemPostGalleryBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false), mRowHost);
            }
            return new PostGalleryVideoAutoplayViewHolder(ItemPostGalleryBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false), mRowHost);
        } else if (viewType == VIEW_TYPE_POST_GALLERY_GALLERY_TYPE) {
            return new PostGalleryGalleryTypeViewHolder(ItemPostGalleryGalleryTypeBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false), mRowHost);
        } else if (viewType == VIEW_TYPE_POST_CARD_3_VIDEO_AUTOPLAY_TYPE) {
            if (mDataSavingMode) {
                return new PostMaterial3CardWithPreviewViewHolder(ItemPostCard3WithPreviewBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false), mRowHost);
            }
            if (mLegacyAutoplayVideoControllerUI) {
                return new PostMaterial3CardVideoAutoplayLegacyControllerViewHolder(ItemPostCard3VideoTypeAutoplayLegacyControllerBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false), mRowHost);
            } else {
                return new PostMaterial3CardVideoAutoplayViewHolder(ItemPostCard3VideoTypeAutoplayBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false), mRowHost);
            }
        } else if (viewType == VIEW_TYPE_POST_CARD_3_WITH_PREVIEW_TYPE) {
            return new PostMaterial3CardWithPreviewViewHolder(ItemPostCard3WithPreviewBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false), mRowHost);
        } else if (viewType == VIEW_TYPE_POST_CARD_3_GALLERY_TYPE) {
            return new PostMaterial3CardGalleryTypeViewHolder(ItemPostCard3GalleryTypeBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false), mRowHost);
        } else {
            //VIEW_TYPE_POST_CARD_3_TEXT_TYPE
            return new PostMaterial3CardTextTypeViewHolder(ItemPostCard3TextBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false), mRowHost);
        }
    }

    /**
     * {@code @SuppressLint("RecyclerView")}: the two places that really did treat the bind-time
     * position as fixed are gone — {@link #onViewRecycled} now marks the holder's own bound post
     * read instead of re-reading getItem(staleIndex), and the video fetch callbacks compare the
     * bound post rather than a captured index. What lint still objects to is the gallery
     * branches handing {@code post} (and the {@code preview} derived from it) to layout-change
     * listeners that run later. Those capture the object, not an index, so they act on the right
     * post however the list shifts; the detector cannot tell the two apart. Verified by bisection:
     * removing the gallery branches' getItem(position) clears the error, the text branch's does
     * not.
     */
    @Override
    public void onBindViewHolder(@NonNull final RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof StickiedCollapsedViewHolder collapsed) {
            Post post = getItem(position);
            if (post != null) {
                collapsed.bind(post);
            }
            return;
        }
        bindPostViewHolder(holder, position);
        if (holder instanceof PostViewHolder asPost && asPost.post != null) {
            applyCardTopBorder(asPost);
        }
        if (holder instanceof PostBaseViewHolder asBase) {
            applyCleanCard(asBase);
        }
    }

    /**
     * Card Clean is Card Bottom without the row of buttons under the content. The row is made
     * invisible and squeezed to a strip of space, so nothing of it shows but the card keeps some
     * room under the text; every other layout gets it back at its natural height, because rows are
     * recycled.
     */
    private void applyCleanCard(PostBaseViewHolder holder) {
        ConstraintLayout bar = holder.bottomConstraintLayout;
        if (bar == null) {
            return;
        }
        boolean clean = mPostLayout == SharedPreferencesUtils.POST_LAYOUT_CARD_CLEAN;
        int height = clean ? Math.round(8 * mScale) : ViewGroup.LayoutParams.WRAP_CONTENT;
        ViewGroup.LayoutParams params = bar.getLayoutParams();
        if (params.height != height) {
            params.height = height;
            bar.setLayoutParams(params);
        }
        bar.setVisibility(clean ? View.INVISIBLE : View.VISIBLE);
        bar.setImportantForAccessibility(clean
                ? View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                : View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);
    }

    @SuppressLint("RecyclerView")
    @OptIn(markerClass = UnstableApi.class)
    private void bindPostViewHolder(@NonNull final RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof PostViewHolder asPost) {
            Post post = getItem(position);
            if (post == null) {
                return;
            }

            asPost.post = post;

            if (mHandleReadPost && post.isRead()) {
                asPost.setItemViewBackgroundColor(true);
                asPost.titleTextView.setTextColor(mReadPostTitleColor);
            }

            if (mDisplaySubredditName) {
                if (post.getAuthorNamePrefixed().equals(post.getSubredditNamePrefixed())) {
                    if (post.getAuthorIconUrl() == null && post.getAuthorFullname() != null && !post.getAuthorFullname().isEmpty()) {
                        ItemSnapshotList<Post> snapshot = snapshot();
                        mFragment.loadUserIcon(snapshot.subList(holder.getBindingAdapterPosition(),
                                Math.min(holder.getBindingAdapterPosition() + 100, snapshot.size())),
                                (subredditOrUserName, iconUrl) -> {
                                    if (mActivity != null && getItemCount() > 0 && post.getAuthor().equals(subredditOrUserName)) {
                                        if (iconUrl == null || iconUrl.isEmpty()) {
                                            mGlide.load(R.drawable.subreddit_default_icon)
                                                    .apply(RequestOptions.bitmapTransform(new RoundedCornersTransformation(72, 0)))
                                                    .into(asPost.iconGifImageView);
                                        } else {
                                            mGlide.load(iconUrl)
                                                    .apply(RequestOptions.bitmapTransform(new RoundedCornersTransformation(72, 0)))
                                                    .error(mGlide.load(R.drawable.subreddit_default_icon)
                                                            .apply(RequestOptions.bitmapTransform(new RoundedCornersTransformation(72, 0))))
                                                    .into(asPost.iconGifImageView);
                                        }

                                        if (holder.getBindingAdapterPosition() >= 0) {
                                            post.setAuthorIconUrl(iconUrl);
                                        }
                                    }
                                });
                        /*mFragment.loadIcon(post.getAuthor(), false, (subredditOrUserName, iconUrl) -> {
                            if (mActivity != null && getItemCount() > 0 && post.getAuthor().equals(subredditOrUserName)) {
                                if (iconUrl == null || iconUrl.isEmpty()) {
                                    mGlide.load(R.drawable.subreddit_default_icon)
                                            .transform(new RoundedCornersTransformation(72, 0))
                                            .into(asPost.iconGifImageView);
                                } else {
                                    if (mDisableProfileAvatarAnimation) {
                                        mGlide.asBitmap().load(iconUrl)
                                                .transform(new RoundedCornersTransformation(72, 0))
                                                .error(mGlide.load(R.drawable.subreddit_default_icon)
                                                        .transform(new RoundedCornersTransformation(72, 0)))
                                                .into(asPost.iconGifImageView);
                                    } else {
                                        mGlide.load(iconUrl)
                                                .transform(new RoundedCornersTransformation(72, 0))
                                                .error(mGlide.load(R.drawable.subreddit_default_icon)
                                                        .transform(new RoundedCornersTransformation(72, 0)))
                                                .into(asPost.iconGifImageView);
                                    }
                                }

                                if (holder.getBindingAdapterPosition() >= 0) {
                                    post.setAuthorIconUrl(iconUrl);
                                }
                            }
                        });*/
                    } else if (post.getAuthorIconUrl() != null && !post.getAuthorIconUrl().isEmpty()) {
                        if (mDisableProfileAvatarAnimation) {
                            mGlide.asBitmap().load(post.getAuthorIconUrl())
                                    .transform(new RoundedCornersTransformation(72, 0))
                                    .error(mGlide.load(R.drawable.subreddit_default_icon)
                                            .transform(new RoundedCornersTransformation(72, 0)))
                                    .into(asPost.iconGifImageView);
                        } else {
                            mGlide.load(post.getAuthorIconUrl())
                                    .transform(new RoundedCornersTransformation(72, 0))
                                    .error(mGlide.load(R.drawable.subreddit_default_icon)
                                            .transform(new RoundedCornersTransformation(72, 0)))
                                    .into(asPost.iconGifImageView);
                        }
                    } else {
                        mGlide.load(R.drawable.subreddit_default_icon)
                                .transform(new RoundedCornersTransformation(72, 0))
                                .into(asPost.iconGifImageView);
                    }
                } else {
                    if (post.getSubredditIconUrl() == null) {
                        mFragment.loadIcon(post.getSubredditName(), true, (subredditOrUserName, iconUrl) -> {
                            if (mActivity != null && getItemCount() > 0 && post.getSubredditName().equals(subredditOrUserName)) {
                                if (iconUrl == null || iconUrl.isEmpty()) {
                                    mGlide.load(R.drawable.subreddit_default_icon)
                                            .transform(new RoundedCornersTransformation(72, 0))
                                            .into(asPost.iconGifImageView);
                                } else {
                                    if (mDisableProfileAvatarAnimation) {
                                        mGlide.asBitmap().load(iconUrl)
                                                .transform(new RoundedCornersTransformation(72, 0))
                                                .error(mGlide.load(R.drawable.subreddit_default_icon)
                                                        .transform(new RoundedCornersTransformation(72, 0)))
                                                .into(asPost.iconGifImageView);
                                    } else {
                                        mGlide.load(iconUrl)
                                                .transform(new RoundedCornersTransformation(72, 0))
                                                .error(mGlide.load(R.drawable.subreddit_default_icon)
                                                        .transform(new RoundedCornersTransformation(72, 0)))
                                                .into(asPost.iconGifImageView);
                                    }
                                }

                                if (holder.getBindingAdapterPosition() >= 0) {
                                    post.setSubredditIconUrl(iconUrl);
                                }
                            }
                        });
                    } else if (!post.getSubredditIconUrl().isEmpty()) {
                        if (mDisableProfileAvatarAnimation) {
                            mGlide.asBitmap().load(post.getSubredditIconUrl())
                                    .transform(new RoundedCornersTransformation(72, 0))
                                    .error(mGlide.load(R.drawable.subreddit_default_icon)
                                            .transform(new RoundedCornersTransformation(72, 0)))
                                    .into(asPost.iconGifImageView);
                        } else {
                            mGlide.load(post.getSubredditIconUrl())
                                    .transform(new RoundedCornersTransformation(72, 0))
                                    .error(mGlide.load(R.drawable.subreddit_default_icon)
                                            .transform(new RoundedCornersTransformation(72, 0)))
                                    .into(asPost.iconGifImageView);
                        }
                    } else {
                        mGlide.load(R.drawable.subreddit_default_icon)
                                .transform(new RoundedCornersTransformation(72, 0))
                                .into(asPost.iconGifImageView);
                    }
                }
            } else {
                if (post.getAuthorIconUrl() == null && post.getAuthorFullname() != null && !post.getAuthorFullname().isEmpty()) {
                    String authorName = post.getAuthor();
                    ItemSnapshotList<Post> snapshot = snapshot();
                    mFragment.loadUserIcon(snapshot.subList(holder.getBindingAdapterPosition(),
                                    Math.min(holder.getBindingAdapterPosition() + 100, snapshot.size())),
                            (subredditOrUserName, iconUrl) -> {
                                if (mActivity != null && getItemCount() > 0) {
                                    if (iconUrl == null || iconUrl.isEmpty() && authorName.equals(subredditOrUserName)) {
                                        mGlide.load(R.drawable.subreddit_default_icon)
                                                .apply(RequestOptions.bitmapTransform(new RoundedCornersTransformation(72, 0)))
                                                .into(asPost.iconGifImageView);
                                    } else {
                                        mGlide.load(iconUrl)
                                                .apply(RequestOptions.bitmapTransform(new RoundedCornersTransformation(72, 0)))
                                                .error(mGlide.load(R.drawable.subreddit_default_icon)
                                                        .apply(RequestOptions.bitmapTransform(new RoundedCornersTransformation(72, 0))))
                                                .into(asPost.iconGifImageView);
                                    }

                                    if (holder.getBindingAdapterPosition() >= 0) {
                                        post.setAuthorIconUrl(iconUrl);
                                    }
                                }
                            });
                    /*mFragment.loadIcon(authorName, post.isAuthorDeleted(), (subredditOrUserName, iconUrl) -> {
                        if (mActivity != null && getItemCount() > 0) {
                            if (iconUrl == null || (iconUrl.isEmpty() && authorName.equals(subredditOrUserName))) {
                                mGlide.load(R.drawable.subreddit_default_icon)
                                        .transform(new RoundedCornersTransformation(72, 0))
                                        .into(asPost.iconGifImageView);
                            } else {
                                RequestBuilder<Drawable> requestBuilder = mGlide.load(iconUrl)
                                        .transform(new RoundedCornersTransformation(72, 0))
                                        .error(mGlide.load(R.drawable.subreddit_default_icon)
                                                .transform(new RoundedCornersTransformation(72, 0)));
                                if (mDisableProfileAvatarAnimation) {
                                    requestBuilder = requestBuilder.dontAnimate();
                                }
                                requestBuilder.into(asPost.iconGifImageView);
                            }

                            if (holder.getBindingAdapterPosition() >= 0) {
                                post.setAuthorIconUrl(iconUrl);
                            }
                        }
                    });*/
                } else if (post.getAuthorIconUrl() != null && !post.getAuthorIconUrl().isEmpty()) {
                    RequestBuilder<Drawable> requestBuilder = mGlide.load(post.getAuthorIconUrl())
                            .transform(new RoundedCornersTransformation(72, 0))
                            .error(mGlide.load(R.drawable.subreddit_default_icon)
                                    .transform(new RoundedCornersTransformation(72, 0)));
                    if (mDisableProfileAvatarAnimation) {
                        requestBuilder = requestBuilder.dontAnimate();
                    }
                    requestBuilder.into(asPost.iconGifImageView);
                } else {
                    mGlide.load(R.drawable.subreddit_default_icon)
                            .transform(new RoundedCornersTransformation(72, 0))
                            .into(asPost.iconGifImageView);
                }
            }

            asPost.postTimeTextView.setText(Utils.getTimeText(
                    mActivity, mShowElapsedTime, mLocale, post.getPostTimeMillis(), mTimeFormatPattern));

            asPost.titleTextView.setText(post.getTitle());
            if (!mHideTheNumberOfVotes) {
                asPost.scoreTextView.setText(Utils.getNVotes(mShowAbsoluteNumberOfVotes,
                        post.getScore() + (Account.ANONYMOUS_ACCOUNT.equals(mAccountName) ? 0 : post.getVoteType())));
            } else {
                asPost.scoreTextView.setText(mActivity.getString(R.string.vote));
            }
            // Hide Vote Buttons: the buttons and the count go together. Set both ways, since the
            // holder may have carried the other state for the post before.
            int voteVisibility = mHideVoteButtons ? View.GONE : View.VISIBLE;
            asPost.upvoteButton.setVisibility(voteVisibility);
            asPost.scoreTextView.setVisibility(voteVisibility);
            asPost.downvoteButton.setVisibility(voteVisibility);

            if (asPost.typeTextView != null) {
                // The triangle sits on the thumbnail, so a row with no thumbnail box (a text post, or
                // a link with no preview) keeps the text badge, or its type would show nowhere.
                if (mHidePostType || (mPostTypeTriangleIndicator && holder instanceof PostCompactBaseViewHolder
                        && showsCompactThumbnailBox(post))) {
                    asPost.typeTextView.setVisibility(View.GONE);
                } else {
                    asPost.typeTextView.setVisibility(View.VISIBLE);
                }
            }

            if (asPost.lockedImageView != null && post.isLocked()) {
                asPost.lockedImageView.setVisibility(View.VISIBLE);
            }

            if (asPost.nsfwTextView != null && post.isNSFW() && !mHideNsfwBadge) {
                asPost.nsfwTextView.setVisibility(View.VISIBLE);
            }

            if (asPost.spoilerTextView != null && post.isSpoiler()) {
                asPost.spoilerTextView.setVisibility(View.VISIBLE);
            }

            if (asPost.flairTextView != null && post.getFlair() != null && !post.getFlair().isEmpty()) {
                if (mHidePostFlair) {
                    asPost.flairTextView.setVisibility(View.GONE);
                } else {
                    asPost.flairTextView.setVisibility(View.VISIBLE);
                    Utils.setHTMLWithImageToTextView(asPost.flairTextView, post.getFlair(), false);
                }
            }

            if (post.isArchived()) {
                if (asPost.archivedImageView != null) {
                    asPost.archivedImageView.setVisibility(View.VISIBLE);
                }

                asPost.upvoteButton.setIconTint(ColorStateList.valueOf(mVoteAndReplyUnavailableVoteButtonColor));
                asPost.scoreTextView.setTextColor(mVoteAndReplyUnavailableVoteButtonColor);
                asPost.downvoteButton.setIconTint(ColorStateList.valueOf(mVoteAndReplyUnavailableVoteButtonColor));
            }

            if (asPost.crosspostImageView != null && post.isCrosspost()) {
                asPost.crosspostImageView.setVisibility(View.VISIBLE);
            }

            switch (post.getVoteType()) {
                case 1:
                    //Upvoted
                    asPost.upvoteButton.setIconResource(R.drawable.ic_upvote_filled_24dp);
                    asPost.upvoteButton.setIconTint(ColorStateList.valueOf(mUpvotedColor));
                    asPost.scoreTextView.setTextColor(mUpvotedColor);
                    break;
                case -1:
                    //Downvoted
                    asPost.downvoteButton.setIconResource(R.drawable.ic_downvote_filled_24dp);
                    asPost.downvoteButton.setIconTint(ColorStateList.valueOf(mDownvotedColor));
                    asPost.scoreTextView.setTextColor(mDownvotedColor);
                    break;
            }

            if (mPostType == PostType.SUBREDDIT && !mDisplaySubredditName && post.isStickied()) {
                asPost.stickiedPostImageView.setVisibility(View.VISIBLE);
                mGlide.load(R.drawable.ic_thumbtack_24dp).into(asPost.stickiedPostImageView);
                makePinCollapseStickiedPost((PostViewHolder) holder, post);
            }

            if (asPost.commentsCountButton != null ) {
                if (!mHideTheNumberOfComments) {
                    asPost.commentsCountButton.setVisibility(View.VISIBLE);
                    asPost.commentsCountButton.setText(Integer.toString(post.getNComments()));
                } else {
                    asPost.commentsCountButton.setVisibility(View.GONE);
                }
            }

            if (asPost.saveButton != null) {
                if (post.isSaved()) {
                    asPost.saveButton.setIconResource(R.drawable.ic_bookmark_grey_24dp);
                } else {
                    asPost.saveButton.setIconResource(R.drawable.ic_bookmark_border_grey_24dp);
                }
            }

            if (holder instanceof PostBaseViewHolder asBase) {
                // A feed row has no flair line under the author, so the marker and the user tag
                // follow the name.
                int userColor = userColor(post);
                if (mHideSubredditAndUserPrefix) {
                    asBase.subredditTextView.setText(post.getSubredditName());
                    asBase.userTextView.setText(userTagged(post, post.getAuthor(), userColor));
                } else {
                    asBase.subredditTextView.setText(post.getSubredditNamePrefixed());
                    asBase.userTextView.setText(userTagged(post, post.getAuthorNamePrefixed(), userColor));
                }

                asBase.userTextView.setTextColor(userColor);

                if (holder instanceof PostBaseVideoAutoplayViewHolder asVideo) {
                    asVideo.toroPlayer.previewImageView.setVisibility(View.VISIBLE);
                    Post.Preview preview = getSuitablePreviewWithThumbnailFallback(post.getPreviews(), post.getThumbnailUrl());
                    VideoAutoplayImpl toroPlayer = asVideo.toroPlayer;
                    if (!mFixedHeightPreviewInCard && preview != null) {
                        asVideo.previewFrameLayout.setSquarePreview(false);
                        asVideo.aspectRatioFrameLayout.setAspectRatio((float) preview.getPreviewWidth() / preview.getPreviewHeight());
                        // Restated because a recycled holder may carry the centred scale type the
                        // placeholder below sets, which would crop a real preview.
                        toroPlayer.previewImageView.setScaleType(ImageView.ScaleType.FIT_START);
                    } else {
                        // The square is the wrapper's to impose, so that it can bound the height;
                        // clearing the media3 frame's own ratio makes it honour that measurement.
                        asVideo.aspectRatioFrameLayout.setAspectRatio(0);
                        asVideo.previewFrameLayout.setMaxHeight(getMaxPreviewHeight());
                        asVideo.previewFrameLayout.setSquarePreview(true);
                        // Fitted rather than cropped, because the clip this still stands in for is
                        // fitted: the PlayerView keeps media3's default fit mode, so a 16:9 video
                        // in the square is shown whole between black bars. Cropping the still
                        // would show the card one picture and then replace it with a smaller one.
                        toroPlayer.previewImageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
                    }
                    // Outside the branch above, because a squared card has a preview to draw as
                    // often as a ratio-sized one does. Loading it only on the ratio path left
                    // every video card in the feed blank under "Fixed Height in Card" until the
                    // clip's first frame landed -- and permanently, for a card that never played.
                    if (preview == null) {
                        showNoPreviewPlaceholder(toroPlayer.previewImageView);
                    } else {
                        // The with-preview card's request, at the box this card measures to: sized
                        // by the view instead, a recycled card decoded the still into the shape the
                        // previous video left on it. The same request lets the preloader warm it.
                        // Never blurred: a video that would be is not given an autoplay card.
                        cardPreviewRequest(preview, false, false)
                                .listener(toroPlayer.previewRequestListener)
                                .into(toroPlayer.previewImageView);
                    }
                    bindAutoplayVideo(asVideo.toroPlayer, post, false);
                    applyTypeColor(asVideo.typeTextView, post.getPostType());
                } else if (holder instanceof PostWithPreviewTypeViewHolder asPreview) {
                    // Both snippet slots start hidden on every bind, not only on a recycle: a
                    // holder pulled off the scrap list is rebound without onViewRecycled, so the
                    // snippet of the text post this card last held would otherwise stay on screen
                    // under the next one -- a link post, which sets no snippet at all, or a text
                    // post whose body wants it on the other side of the image.
                    hideSnippetSlots(asPreview);
                    // The play badge starts hidden for the same reason, and only the video and gif
                    // branches below ever show it: without this, the badge of the gif card this
                    // holder last carried stays over a text post's image -- a pairing the feed now
                    // makes routine, since a text post with a body image shares this card with an
                    // autoplay-off gif post.
                    asPreview.videoOrGifIndicator.setVisibility(View.GONE);
                    if (post.getPostType() == Post.VIDEO_TYPE) {
                        asPreview.videoOrGifIndicator.setVisibility(mHidePostTypeIndicator ? View.GONE : View.VISIBLE);
                        asPreview.videoOrGifIndicator.setImageDrawable(ContextCompat.getDrawable(mActivity, R.drawable.ic_play_circle_36dp));
                        if (asPreview.typeTextView != null) {
                            asPreview.typeTextView.setText(mActivity.getString(R.string.video));
                        }
                    } else if (post.getPostType() == Post.GIF_TYPE) {
                        if (!mAutoplay) {
                            asPreview.videoOrGifIndicator.setVisibility(mHidePostTypeIndicator ? View.GONE : View.VISIBLE);
                            asPreview.videoOrGifIndicator.setImageDrawable(ContextCompat.getDrawable(mActivity, R.drawable.ic_play_circle_36dp));
                        }
                        if (asPreview.typeTextView != null) {
                            asPreview.typeTextView.setText(mActivity.getString(R.string.gif));
                        }
                    } else if (post.getPostType() == Post.IMAGE_TYPE) {
                        if (asPreview.typeTextView != null) {
                            asPreview.typeTextView.setText(mActivity.getString(R.string.image));
                        }
                    } else if (post.getPostType() == Post.LINK_TYPE || post.getPostType() == Post.NO_PREVIEW_LINK_TYPE) {
                        if (asPreview.typeTextView != null) {
                            asPreview.typeTextView.setText(mActivity.getString(R.string.link));
                        }
                        asPreview.linkTextView.setVisibility(View.VISIBLE);
                        String domain = Uri.parse(post.getUrl()).getHost();
                        asPreview.linkTextView.setText(domain);
                        if (post.getPostType() == Post.NO_PREVIEW_LINK_TYPE) {
                            asPreview.imageViewNoPreviewGallery.setVisibility(View.VISIBLE);
                            asPreview.imageViewNoPreviewGallery.setImageResource(R.drawable.ic_link_day_night_24dp);
                        }
                    } else if (post.getPostType() == Post.TEXT_TYPE) {
                        if (asPreview.typeTextView != null) {
                            asPreview.typeTextView.setText(mActivity.getString(R.string.text));
                        }
                        // Text post that also has a preview: keep the selftext snippet alongside the
                        // image, honouring the "Hide Text Post Content" setting like the text holder.
                        // The card reads in the same order the post detail does -- an image out of
                        // the body keeps its place in the body, so a body of text, image, text does
                        // not come out as text, text, image, and a preview Reddit built from a link
                        // leads the card the way the detail's link holder leads with it. Both slots
                        // were hidden on the way into this bind, so the one this post has nothing
                        // for stays hidden.
                        if (!mHideTextPostContent && !post.isSpoiler()) {
                            boolean hasBelowSlot =
                                    asPreview.contentTextViewBelowPreview != null;
                            bindSnippet(asPreview.contentTextView,
                                    PostCardPreviewStyle.snippetAbovePreview(post, hasBelowSlot), post);
                            bindSnippet(asPreview.contentTextViewBelowPreview,
                                    PostCardPreviewStyle.snippetBelowPreview(post, hasBelowSlot), post);
                        }
                    }
                    applyTypeColor(asPreview.typeTextView, post.getPostType());

                    if (mDataSavingMode && mDisableImagePreview) {
                        asPreview.imageViewNoPreviewGallery.setVisibility(View.VISIBLE);
                        // Every post type that reaches this card gets a glyph. A text post whose
                        // preview came out of its own body reaches it too, and had no branch here:
                        // its box was left empty, or showing the glyph of whatever post this
                        // holder carried before, since onViewRecycled only hides the box and never
                        // clears what is in it. The suppressed preview of a text post is an image,
                        // which is not what the no-preview fallback says for a text post -- there
                        // the post has no image at all.
                        asPreview.imageViewNoPreviewGallery.setImageResource(
                                post.getPostType() == Post.TEXT_TYPE
                                        ? R.drawable.ic_image_day_night_24dp
                                        : noPreviewIconFor(post.getPostType()));
                        // There is no still here to badge, whatever the video and gif branches
                        // above decided.
                        asPreview.videoOrGifIndicator.setVisibility(View.GONE);
                    } else if (mDataSavingMode && mOnlyDisablePreviewInVideoAndGifPosts && (post.getPostType() == Post.VIDEO_TYPE || post.getPostType() == Post.GIF_TYPE)) {
                        asPreview.imageViewNoPreviewGallery.setVisibility(View.VISIBLE);
                        asPreview.imageViewNoPreviewGallery.setImageResource(R.drawable.ic_video_day_night_24dp);
                        asPreview.videoOrGifIndicator.setVisibility(View.GONE);
                    } else {
                        if (post.getPostType() == Post.GIF_TYPE && ((post.isNSFW() && mNeedBlurNsfw && !(mDoNotBlurNsfwInNsfwSubreddits && mFragment != null && mFragment.getIsNsfwSubreddit()) && !(mAutoplay && mAutoplayNsfwVideos)) || (post.isSpoiler() && mNeedBlurSpoiler))) {
                            asPreview.imageViewNoPreviewGallery.setVisibility(View.VISIBLE);
                            asPreview.imageViewNoPreviewGallery.setImageResource(R.drawable.ic_image_day_night_24dp);
                            asPreview.videoOrGifIndicator.setVisibility(View.GONE);
                        } else {
                            Post.Preview preview = getSuitablePreviewWithThumbnailFallback(post.getPreviews(), post.getThumbnailUrl());
                            asPreview.preview = preview;
                            if (preview != null) {
                                if (asPreview.imageWrapperFrameLayout != null) {
                                    asPreview.imageWrapperFrameLayout.setVisibility(View.VISIBLE);
                                }
                                asPreview.imageView.setVisibility(View.VISIBLE);
                                PostCardPreviewStyle.applyPreviewShape(
                                        asPreview.imageView, preview, post,
                                        mFixedHeightPreviewInCard, mAutoplay, getMaxPreviewHeight());
                                asPreview.imageView.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
                                    @Override
                                    public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
                                        asPreview.imageView.removeOnLayoutChangeListener(this);
                                        loadImage(holder);
                                    }
                                });
                                // Hide placeholder since we have a preview (including thumbnail fallback)
                                asPreview.imageViewNoPreviewGallery.setVisibility(View.GONE);
                            } else {
                                asPreview.imageViewNoPreviewGallery.setVisibility(View.VISIBLE);
                                if (post.getPostType() == Post.VIDEO_TYPE) {
                                    asPreview.imageViewNoPreviewGallery.setImageResource(R.drawable.ic_video_day_night_24dp);
                                    asPreview.videoOrGifIndicator.setVisibility(View.GONE);
                                } else if (post.getPostType() == Post.IMAGE_TYPE || post.getPostType() == Post.GIF_TYPE) {
                                    asPreview.imageViewNoPreviewGallery.setImageResource(R.drawable.ic_image_day_night_24dp);
                                    asPreview.videoOrGifIndicator.setVisibility(View.GONE);
                                } else if (post.getPostType() == Post.LINK_TYPE) {
                                    asPreview.imageViewNoPreviewGallery.setImageResource(R.drawable.ic_link_day_night_24dp);
                                } else if (post.getPostType() == Post.GALLERY_TYPE) {
                                    asPreview.imageViewNoPreviewGallery.setImageResource(R.drawable.ic_gallery_day_night_24dp);
                                }
                            }
                        }
                    }
                } else if (holder instanceof PostBaseGalleryTypeViewHolder asGalleryType) {
                    int gallerySize = post.getGallery().size();
                    if (mDataSavingMode && mDisableImagePreview) {
                        asGalleryType.noPreviewImageView.setVisibility(View.VISIBLE);
                        asGalleryType.noPreviewImageView.setImageResource(R.drawable.ic_gallery_day_night_24dp);
                    } else {
                        asGalleryType.frameLayout.setVisibility(View.VISIBLE);
                        // The image the user swiped to, not image one. The holder is recycled and
                        // the inner list comes back at whatever position the last post left it, so
                        // this has to be set from the post either way -- and because the post
                        // carries it into the feed cache, a resume reopens the gallery where it was.
                        int galleryPage = Math.max(0, Math.min(post.getGalleryPageIndex(), gallerySize - 1));
                        asGalleryType.imageIndexTextView.setText(
                                mActivity.getString(R.string.image_index_in_gallery, galleryPage + 1, gallerySize));
                        RecyclerView galleryList = asGalleryType.galleryRecyclerView;
                        RecyclerView.LayoutManager galleryLayout = galleryList.getLayoutManager();
                        // One rule for this and the post-detail header, in GalleryPagePlacement:
                        // they draw the same carousel from the same recorded page, and while the
                        // condition lived inline in both binds it drifted apart.
                        boolean sameImages = asGalleryType.adapter
                                .showsImages(post.getGallery());
                        boolean atRightPage = galleryLayout instanceof LinearLayoutManagerBugFixed
                                && ((LinearLayoutManagerBugFixed) galleryLayout)
                                        .findFirstVisibleItemPosition() == galleryPage;
                        if (GalleryPagePlacement.shouldApplyPage(
                                !sameImages, atRightPage,
                                asGalleryType.galleryTouchedByUser,
                                galleryList.getScrollState() == RecyclerView.SCROLL_STATE_IDLE)) {
                            if (galleryLayout instanceof LinearLayoutManagerBugFixed) {
                                // Offset zero, so the tile lands flush at the leading edge rather
                                // than merely visible and left for PagerSnapHelper to settle --
                                // that settle is a card that shows one image and shifts to another.
                                ((LinearLayoutManagerBugFixed) galleryLayout)
                                        .scrollToPositionWithOffset(galleryPage, 0);
                            } else {
                                galleryList.scrollToPosition(galleryPage);
                            }
                        }
                        Post.Preview preview = getSuitablePreviewWithThumbnailFallback(post.getPreviews(), post.getThumbnailUrl());
                        // The tile adapter crops a square to fill it, and tells the two apart by
                        // the height cap, which only the square gets.
                        if (preview == null || galleryTileIsSquare(preview)) {
                            asGalleryType.adapter.setMaxPreviewHeight(getMaxPreviewHeight());
                            asGalleryType.adapter.setRatio(SQUARE_PREVIEW_RATIO);
                        } else {
                            asGalleryType.adapter.setMaxPreviewHeight(0);
                            asGalleryType.adapter.setRatio((float) preview.getPreviewHeight() / preview.getPreviewWidth());
                        }
                        // The box every tile decodes to, from the column rather than from the view,
                        // so a recycled tile cannot decode to the shape the last post left on it and
                        // the preloader can warm the page this card opens on at the same size.
                        int tileWidth = mShowGalleryMediaAsGrid ? 0 : cardPreviewWidthPx();
                        asGalleryType.adapter.setTileSize(
                                tileWidth, galleryTileHeightPx(preview, tileWidth));
                        asGalleryType.adapter.setDataSavingMode(mDataSavingMode);
                        boolean blurGallery = shouldBlurGallery(post);
                        asGalleryType.adapter.setBlurImage(blurGallery);
                        asGalleryType.adapter.setAutoplayGif(shouldAutoplayGalleryGif(post, blurGallery));
                        asGalleryType.adapter.setGalleryImages(post.getGallery());
                        resolveImageHostGallery((PostBaseGalleryTypeViewHolder) holder, post);
                    }
                    applyTypeColor(asGalleryType.typeTextView, post.getPostType());

                    RecyclerView.LayoutManager layoutManager = asGalleryType.galleryRecyclerView.getLayoutManager();
                    if (mShowGalleryMediaAsGrid) {
                        if (!(layoutManager instanceof GridLayoutManager)) {
                            layoutManager = new GridLayoutManager(mActivity, 3);
                            asGalleryType.galleryRecyclerView.setLayoutManager(layoutManager);
                        }

                        applyGalleryGridShape((PostBaseGalleryTypeViewHolder) holder, gallerySize);

                        int padding = galleryGridPaddingPx();
                        asGalleryType.galleryRecyclerView.setPadding(
                                0, (holder instanceof PostGalleryTypeViewHolder) ? 0 : padding,
                                padding, (holder instanceof PostGalleryTypeViewHolder) ? 0 : padding);
                        asGalleryType.adapter.setIsGridLayout(true);
                        asGalleryType.imageIndexTextView.setVisibility(View.GONE);
                    } else {
                        if (!(layoutManager instanceof LinearLayoutManagerBugFixed)) {
                            layoutManager = new LinearLayoutManagerBugFixed(mActivity, RecyclerView.HORIZONTAL, false);
                            asGalleryType.galleryRecyclerView.setLayoutManager(layoutManager);
                        }

                        asGalleryType.galleryRecyclerView.setPadding(0, 0, 0, 0);
                        asGalleryType.adapter.setIsGridLayout(false);
                        // Hide Image Count in Gallery: the holder hides it once when it is built,
                        // and this bind would otherwise show it again on every post.
                        asGalleryType.imageIndexTextView.setVisibility(
                                mHideImageCountInGallery ? View.GONE : View.VISIBLE);
                    }
                    /*if (layoutManager instanceof GridLayoutManager) {
                        int spanCount = gallerySize == 2 || gallerySize == 4 ? 2 : 3;
                        ((GridLayoutManager) layoutManager).setSpanCount(spanCount);
                        if (asGalleryType.galleryRecyclerView.getItemDecorationCount() > 0) {
                            RecyclerView.ItemDecoration itemDecoration = asGalleryType.galleryRecyclerView.getItemDecorationAt(0);
                            if (itemDecoration instanceof PostGalleryGridLayoutItemDecoration) {
                                ((PostGalleryGridLayoutItemDecoration) itemDecoration).setSpanCount(spanCount);
                            }
                        }

                        int padding = (int) (8 * mScale);
                        asGalleryType.galleryRecyclerView.setPadding(
                                0, (holder instanceof PostGalleryTypeViewHolder) ? 0 : padding,
                                padding, (holder instanceof PostGalleryTypeViewHolder) ? 0 : padding);
                        asGalleryType.adapter.setIsGridLayout(true);
                        asGalleryType.imageIndexTextView.setVisibility(View.GONE);
                    } else {
                        asGalleryType.galleryRecyclerView.setPadding(0, 0, 0, 0);
                        asGalleryType.adapter.setIsGridLayout(false);
                        asGalleryType.imageIndexTextView.setVisibility(View.VISIBLE);
                    }*/
                } else if (holder instanceof PostTextTypeViewHolder asText) {
                    if (!mHideTextPostContent && !post.isSpoiler() && post.getSelfTextPlainTrimmed() != null && !post.getSelfTextPlainTrimmed().isEmpty()) {
                        asText.contentTextView.setVisibility(View.VISIBLE);
                        if (mHandleReadPost && post.isRead()) {
                            asText.contentTextView.setTextColor(mReadPostContentColor);
                        }
                        asText.contentTextView.setText(post.getSelfTextPlainTrimmed());
                    }
                    applyTypeColor(asText.typeTextView, post.getPostType());
                }
                mCallback.currentlyBindItem(holder.getBindingAdapterPosition());
            } else if (holder instanceof PostCompactBaseViewHolder asCompact) {
                asCompact.applyCompactItemLayoutParams();

                // A compact row gives the author only what is left between the icon and the
                // timestamp, so its badges are handed over separately: BadgedNameTextView puts
                // them on a second line rather than let the name's ellipsis eat them.
                int compactUserColor = userColor(post);
                CharSequence inlineBadges = userBadges(post, compactUserColor, true);
                // Built only if the row turns out to need a second line for them. The two forms
                // differ only in the lead-in gap, so one exists exactly when the other does.
                Supplier<CharSequence> standaloneBadges = inlineBadges == null ? null
                        : () -> java.util.Objects.requireNonNull(userBadges(post, compactUserColor, false));
                String compactAuthor = mHideSubredditAndUserPrefix
                        ? post.getAuthor() : post.getAuthorNamePrefixed();
                if (mDisplaySubredditName) {
                    asCompact.nameTextView.setTextColor(mSubredditColor);
                    asCompact.nameTextView.setNameAndBadges(
                            mHideSubredditAndUserPrefix ? post.getSubredditName() : post.getSubredditNamePrefixed(),
                            null, null);

                    asCompact.usernameTextView.setTextColor(compactUserColor);
                    asCompact.usernameTextView.setNameAndBadges(
                            compactAuthor, inlineBadges, standaloneBadges);
                    asCompact.usernameTextView.setVisibility(View.VISIBLE);
                } else {
                    asCompact.nameTextView.setTextColor(compactUserColor);
                    asCompact.nameTextView.setNameAndBadges(
                            compactAuthor, inlineBadges, standaloneBadges);
                    asCompact.usernameTextView.setVisibility(View.GONE);
                }

                if (asCompact.bottomConstraintLayout != null) {
                    if (mCompactLayoutToolbarHiddenByDefault) {
                        ViewGroup.LayoutParams params = asCompact.bottomConstraintLayout.getLayoutParams();
                        params.height = 0;
                        asCompact.bottomConstraintLayout.setLayoutParams(params);
                    } else {
                        ViewGroup.LayoutParams params = asCompact.bottomConstraintLayout.getLayoutParams();
                        params.height = LinearLayout.LayoutParams.WRAP_CONTENT;
                        asCompact.bottomConstraintLayout.setLayoutParams(params);
                    }
                }

                if (mShowDividerInCompactLayout) {
                    asCompact.divider.setVisibility(View.VISIBLE);
                } else {
                    asCompact.divider.setVisibility(View.GONE);
                }

                if (showsCompactThumbnailBox(post)) {
                    asCompact.relativeLayout.setVisibility(View.VISIBLE);
                    if (postHasPreviews(post)) {
                        asCompact.imageView.setVisibility(View.VISIBLE);
                        // loadImage decides on the loading indicator: a thumbnail already in memory
                        // must not show one, even for the frame before the listener hides it.
                        loadImage(holder);
                    } else {
                        // Nothing to load. The indicator only ever goes away in loadImage's
                        // success and failure callbacks, which never run when there is no image, so
                        // a recycled holder that still carries a previous post's spinner would sit
                        // here spinning forever. Reddit generates no preview at all for some posts,
                        // such as the direct MP4 links r/baseball posts its highlights as.
                        asCompact.loadingIndicator.setVisibility(View.GONE);
                        asCompact.noPreviewPostImageFrameLayout.setVisibility(View.VISIBLE);
                        asCompact.noPreviewPostImageView.setImageResource(
                                noPreviewIconFor(post.getPostType()));
                    }
                }

                switch (post.getPostType()) {
                    case Post.IMAGE_TYPE:
                        if (asCompact.typeTextView != null) {
                            asCompact.typeTextView.setText(R.string.image);
                        }
                        if (mDataSavingMode && mDisableImagePreview) {
                            asCompact.noPreviewPostImageFrameLayout.setVisibility(View.VISIBLE);
                            asCompact.noPreviewPostImageView.setImageResource(R.drawable.ic_image_day_night_24dp);
                        }
                        break;
                    case Post.LINK_TYPE:
                        if (asCompact.typeTextView != null) {
                            asCompact.typeTextView.setText(R.string.link);
                        }
                        if (mDataSavingMode && mDisableImagePreview) {
                            asCompact.noPreviewPostImageFrameLayout.setVisibility(View.VISIBLE);
                            asCompact.noPreviewPostImageView.setImageResource(R.drawable.ic_link_day_night_24dp);
                        }

                        if (asCompact.linkTextView != null) {
                            asCompact.linkTextView.setVisibility(View.VISIBLE);
                        }
                        String domain = Uri.parse(post.getUrl()).getHost();
                        if (asCompact.linkTextView != null) {
                            asCompact.linkTextView.setText(domain);
                        }
                        break;
                    case Post.GIF_TYPE:
                        if (asCompact.typeTextView != null) {
                            asCompact.typeTextView.setText(R.string.gif);
                        }
                        if (mDataSavingMode && (mDisableImagePreview || mOnlyDisablePreviewInVideoAndGifPosts)) {
                            asCompact.noPreviewPostImageFrameLayout.setVisibility(View.VISIBLE);
                            asCompact.noPreviewPostImageView.setImageResource(R.drawable.ic_image_day_night_24dp);
                        } else {
                            asCompact.playButtonImageView.setVisibility(mHidePostTypeIndicator ? View.GONE : View.VISIBLE);
                            asCompact.playButtonImageView.setImageDrawable(ContextCompat.getDrawable(mActivity, R.drawable.ic_play_circle_24dp));
                        }
                        break;
                    case Post.VIDEO_TYPE:
                        if (asCompact.typeTextView != null) {
                            asCompact.typeTextView.setText(R.string.video);
                        }
                        if (mDataSavingMode && (mDisableImagePreview || mOnlyDisablePreviewInVideoAndGifPosts)) {
                            asCompact.noPreviewPostImageFrameLayout.setVisibility(View.VISIBLE);
                            asCompact.noPreviewPostImageView.setImageResource(R.drawable.ic_video_day_night_24dp);
                        } else {
                            asCompact.playButtonImageView.setVisibility(mHidePostTypeIndicator ? View.GONE : View.VISIBLE);
                            asCompact.playButtonImageView.setImageDrawable(ContextCompat.getDrawable(mActivity, R.drawable.ic_play_circle_24dp));
                        }
                        break;
                    case Post.NO_PREVIEW_LINK_TYPE:
                        if (asCompact.typeTextView != null) {
                            asCompact.typeTextView.setText(R.string.link);
                        }

                        if (asCompact.linkTextView != null) {
                            asCompact.linkTextView.setVisibility(View.VISIBLE);
                            String noPreviewLinkUrl = post.getUrl();
                            String noPreviewLinkDomain = Uri.parse(noPreviewLinkUrl).getHost();
                            asCompact.linkTextView.setText(noPreviewLinkDomain);
                        }
                        asCompact.noPreviewPostImageFrameLayout.setVisibility(View.VISIBLE);
                        asCompact.noPreviewPostImageView.setImageResource(R.drawable.ic_link_day_night_24dp);
                        break;
                    case Post.GALLERY_TYPE:
                        if (asCompact.typeTextView != null) {
                            asCompact.typeTextView.setText(R.string.gallery);
                        }
                        if (mDataSavingMode && mDisableImagePreview) {
                            asCompact.noPreviewPostImageFrameLayout.setVisibility(View.VISIBLE);
                            asCompact.noPreviewPostImageView.setImageResource(R.drawable.ic_gallery_day_night_24dp);
                        } else {
                            asCompact.playButtonImageView.setVisibility(mHidePostTypeIndicator ? View.GONE : View.VISIBLE);
                            asCompact.playButtonImageView.setImageDrawable(ContextCompat.getDrawable(mActivity, R.drawable.ic_gallery_day_night_24dp));
                        }
                        break;
                    case Post.TEXT_TYPE:
                        if (asCompact.typeTextView != null) {
                            asCompact.typeTextView.setText(R.string.text);
                        }
                        break;
                }
                applyTypeColor(asCompact.typeTextView, post.getPostType());
                applyTriangleIndicator((PostCompactBaseViewHolder) holder, post.getPostType());

                mCallback.currentlyBindItem(holder.getBindingAdapterPosition());
            }

            if (mShowToolbarItemsBasedOnSpace) {
                if (itemWidth < 250) {
                    if (asPost.commentsCountButton != null) {
                        asPost.commentsCountButton.setVisibility(View.GONE);
                    }
                    if (asPost.saveButton != null) {
                        asPost.saveButton.setVisibility(View.GONE);
                    }
                    if (asPost.shareButton != null) {
                        asPost.shareButton.setVisibility(View.GONE);
                    }
                } else if (itemWidth < 316) {
                    if (asPost.commentsCountButton != null) {
                        asPost.commentsCountButton.setVisibility(View.GONE);
                    }
                    if (asPost.saveButton != null) {
                        asPost.saveButton.setVisibility(View.VISIBLE);
                    }
                    if (asPost.shareButton != null) {
                        asPost.shareButton.setVisibility(View.GONE);
                    }
                } else if (itemWidth < 420) {
                    if (asPost.commentsCountButton != null) {
                        asPost.commentsCountButton.setVisibility(View.GONE);
                    }
                    if (asPost.saveButton != null) {
                        asPost.saveButton.setVisibility(View.VISIBLE);
                    }
                    if (asPost.shareButton != null) {
                        asPost.shareButton.setVisibility(View.VISIBLE);
                    }
                } else {
                    if (asPost.commentsCountButton != null) {
                        asPost.commentsCountButton.setVisibility(View.VISIBLE);
                    }
                    if (asPost.saveButton != null) {
                        asPost.saveButton.setVisibility(View.VISIBLE);
                    }
                    if (asPost.shareButton != null) {
                        asPost.shareButton.setVisibility(View.VISIBLE);
                    }
                }
            } else {
                if (asPost.commentsCountButton != null) {
                    asPost.commentsCountButton.setVisibility(View.VISIBLE);
                }
                if (asPost.saveButton != null) {
                    asPost.saveButton.setVisibility(View.VISIBLE);
                }
                if (asPost.shareButton != null) {
                    asPost.shareButton.setVisibility(View.VISIBLE);
                }
            }
        } else if (holder instanceof PostGalleryViewHolder asGallery) {
            Post post = getItem(position);
            if (post != null) {
                asGallery.post = post;
                if (mHandleReadPost && post.isRead()) {
                    holder.itemView.setBackgroundTintList(ColorStateList.valueOf(mReadPostCardViewBackgroundColor));
                    asGallery.binding.titleTextViewItemPostGallery.setTextColor(mReadPostTitleColor);
                }

                if (mDataSavingMode && (mDisableImagePreview ||
                        ((post.getPostType() == Post.VIDEO_TYPE || post.getPostType() == Post.GIF_TYPE) && mOnlyDisablePreviewInVideoAndGifPosts))) {
                    asGallery.binding.imageViewNoPreviewItemPostGallery.setVisibility(View.VISIBLE);
                    if (post.getPostType() == Post.VIDEO_TYPE) {
                        asGallery.binding.imageViewNoPreviewItemPostGallery.setImageResource(R.drawable.ic_video_day_night_24dp);
                        asGallery.binding.videoOrGifIndicatorImageViewItemPostGallery.setVisibility(View.GONE);
                    } else if (post.getPostType() == Post.IMAGE_TYPE || post.getPostType() == Post.GIF_TYPE) {
                        asGallery.binding.imageViewNoPreviewItemPostGallery.setImageResource(R.drawable.ic_image_day_night_24dp);
                        asGallery.binding.videoOrGifIndicatorImageViewItemPostGallery.setVisibility(View.GONE);
                    } else if (post.getPostType() == Post.LINK_TYPE) {
                        asGallery.binding.imageViewNoPreviewItemPostGallery.setImageResource(R.drawable.ic_link_day_night_24dp);
                    } else if (post.getPostType() == Post.GALLERY_TYPE) {
                        asGallery.binding.imageViewNoPreviewItemPostGallery.setImageResource(R.drawable.ic_gallery_day_night_24dp);
                    }
                } else {
                    switch (post.getPostType()) {
                        case Post.IMAGE_TYPE: {
                            Post.Preview preview = getSuitablePreviewWithThumbnailFallback(post.getPreviews(), post.getThumbnailUrl());
                            asGallery.preview = preview;
                            if (preview != null) {
                                asGallery.binding.imageViewItemPostGallery.setVisibility(View.VISIBLE);

                                if (mFixedHeightPreviewInCard || (preview.getPreviewWidth() <= 0 || preview.getPreviewHeight() <= 0)) {
                                    setSquarePreview(asGallery.binding.imageViewItemPostGallery, false);
                                } else {
                                    setPreviewRatio(asGallery.binding.imageViewItemPostGallery, preview);
                                }
                                asGallery.binding.imageViewItemPostGallery.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
                                    @Override
                                    public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
                                        asGallery.binding.imageViewItemPostGallery.removeOnLayoutChangeListener(this);
                                        loadImage(holder);
                                    }
                                });
                                // Hide placeholder since we have a preview (including thumbnail fallback)
                                asGallery.binding.imageViewNoPreviewItemPostGallery.setVisibility(View.GONE);
                            } else {
                                asGallery.binding.imageViewNoPreviewItemPostGallery.setVisibility(View.VISIBLE);
                                asGallery.binding.imageViewNoPreviewItemPostGallery.setImageResource(R.drawable.ic_image_day_night_24dp);
                            }
                            break;
                        }
                        case Post.GIF_TYPE: {
                            if (post.getPostType() == Post.GIF_TYPE && ((post.isNSFW() && mNeedBlurNsfw && !(mDoNotBlurNsfwInNsfwSubreddits && mFragment != null && mFragment.getIsNsfwSubreddit()) && !(mAutoplay && mAutoplayNsfwVideos)) || (post.isSpoiler() && mNeedBlurSpoiler))) {
                                asGallery.binding.imageViewNoPreviewItemPostGallery.setVisibility(View.VISIBLE);
                                asGallery.binding.imageViewNoPreviewItemPostGallery.setImageResource(R.drawable.ic_image_day_night_24dp);
                            } else {
                                Post.Preview preview = getSuitablePreviewWithThumbnailFallback(post.getPreviews(), post.getThumbnailUrl());
                                asGallery.preview = preview;
                                if (preview != null) {
                                    asGallery.binding.imageViewItemPostGallery.setVisibility(View.VISIBLE);
                                    asGallery.binding.videoOrGifIndicatorImageViewItemPostGallery.setVisibility(mHidePostTypeIndicator ? View.GONE : View.VISIBLE);
                                    asGallery.binding.videoOrGifIndicatorImageViewItemPostGallery.setImageDrawable(ContextCompat.getDrawable(mActivity, R.drawable.ic_play_circle_36dp));

                                    if (mFixedHeightPreviewInCard || (preview.getPreviewWidth() <= 0 || preview.getPreviewHeight() <= 0)) {
                                        setSquarePreview(asGallery.binding.imageViewItemPostGallery, false);
                                    } else {
                                        setPreviewRatio(asGallery.binding.imageViewItemPostGallery, preview);
                                    }
                                    asGallery.binding.imageViewItemPostGallery.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
                                        @Override
                                        public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
                                            asGallery.binding.imageViewItemPostGallery.removeOnLayoutChangeListener(this);
                                            loadImage(holder);
                                        }
                                    });
                                } else {
                                    asGallery.binding.imageViewNoPreviewItemPostGallery.setVisibility(View.VISIBLE);
                                    asGallery.binding.imageViewNoPreviewItemPostGallery.setImageResource(R.drawable.ic_image_day_night_24dp);
                                }
                            }
                            break;
                        }
                        case Post.VIDEO_TYPE: {
                            Post.Preview preview = getSuitablePreviewWithThumbnailFallback(post.getPreviews(), post.getThumbnailUrl());
                            asGallery.preview = preview;
                            if (preview != null) {
                                asGallery.binding.imageViewItemPostGallery.setVisibility(View.VISIBLE);
                                asGallery.binding.videoOrGifIndicatorImageViewItemPostGallery.setVisibility(mHidePostTypeIndicator ? View.GONE : View.VISIBLE);
                                asGallery.binding.videoOrGifIndicatorImageViewItemPostGallery.setImageDrawable(ContextCompat.getDrawable(mActivity, R.drawable.ic_play_circle_36dp));

                                if (mFixedHeightPreviewInCard || (preview.getPreviewWidth() <= 0 || preview.getPreviewHeight() <= 0)) {
                                    setSquarePreview(asGallery.binding.imageViewItemPostGallery, false);
                                } else {
                                    setPreviewRatio(asGallery.binding.imageViewItemPostGallery, preview);
                                }
                                asGallery.binding.imageViewItemPostGallery.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
                                    @Override
                                    public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
                                        asGallery.binding.imageViewItemPostGallery.removeOnLayoutChangeListener(this);
                                        loadImage(holder);
                                    }
                                });
                                // Hide placeholder since we have a preview (including thumbnail fallback)
                                asGallery.binding.imageViewNoPreviewItemPostGallery.setVisibility(View.GONE);
                            } else {
                                asGallery.binding.imageViewNoPreviewItemPostGallery.setVisibility(View.VISIBLE);
                                asGallery.binding.imageViewNoPreviewItemPostGallery.setImageResource(R.drawable.ic_video_day_night_24dp);
                            }
                            if (holder instanceof PostGalleryVideoAutoplayViewHolder asVideo) {
                                // The tile plays itself; the badge that says it can is not needed.
                                asGallery.binding.videoOrGifIndicatorImageViewItemPostGallery.setVisibility(View.GONE);
                                bindAutoplayVideo(asVideo.toroPlayer(), post, true);
                            }
                            break;
                        }
                        case Post.LINK_TYPE: {
                            Post.Preview preview = getSuitablePreviewWithThumbnailFallback(post.getPreviews(), post.getThumbnailUrl());
                            asGallery.preview = preview;
                            if (preview != null) {
                                asGallery.binding.imageViewItemPostGallery.setVisibility(View.VISIBLE);
                                asGallery.binding.videoOrGifIndicatorImageViewItemPostGallery.setVisibility(mHidePostTypeIndicator ? View.GONE : View.VISIBLE);
                                asGallery.binding.videoOrGifIndicatorImageViewItemPostGallery.setImageDrawable(ContextCompat.getDrawable(mActivity, R.drawable.ic_link_post_type_indicator_day_night_24dp));

                                if (mFixedHeightPreviewInCard || (preview.getPreviewWidth() <= 0 || preview.getPreviewHeight() <= 0)) {
                                    setSquarePreview(asGallery.binding.imageViewItemPostGallery, false);
                                } else {
                                    setPreviewRatio(asGallery.binding.imageViewItemPostGallery, preview);
                                }
                                asGallery.binding.imageViewItemPostGallery.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
                                    @Override
                                    public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
                                        asGallery.binding.imageViewItemPostGallery.removeOnLayoutChangeListener(this);
                                        loadImage(holder);
                                    }
                                });
                                // Hide placeholder since we have a preview (including thumbnail fallback)
                                asGallery.binding.imageViewNoPreviewItemPostGallery.setVisibility(View.GONE);
                            } else {
                                asGallery.binding.imageViewNoPreviewItemPostGallery.setVisibility(View.VISIBLE);
                                asGallery.binding.imageViewNoPreviewItemPostGallery.setImageResource(R.drawable.ic_link_day_night_24dp);
                            }
                            break;
                        }
                        case Post.NO_PREVIEW_LINK_TYPE: {
                            asGallery.binding.imageViewNoPreviewItemPostGallery.setVisibility(View.VISIBLE);
                            asGallery.binding.imageViewNoPreviewItemPostGallery.setImageResource(R.drawable.ic_link_day_night_24dp);
                            break;
                        }
                        case Post.TEXT_TYPE: {
                            asGallery.binding.titleTextViewItemPostGallery.setVisibility(View.VISIBLE);
                            asGallery.binding.titleTextViewItemPostGallery.setText(post.getTitle());
                            break;
                        }
                    }
                }
            }
        } else if (holder instanceof PostGalleryBaseGalleryTypeViewHolder asGalleryGalleryType) {
            Post post = getItem(position);
            if (post != null) {
                asGalleryGalleryType.post = post;
                if (mHandleReadPost && post.isRead()) {
                    holder.itemView.setBackgroundTintList(ColorStateList.valueOf(mReadPostCardViewBackgroundColor));
                }

                if (mDataSavingMode && mDisableImagePreview) {
                    asGalleryGalleryType.noPreviewImageView.setVisibility(View.VISIBLE);
                    asGalleryGalleryType.noPreviewImageView.setImageResource(R.drawable.ic_gallery_day_night_24dp);
                } else {
                    Post.Preview preview = getSuitablePreviewWithThumbnailFallback(post.getPreviews(), post.getThumbnailUrl());
                    asGalleryGalleryType.preview = preview;

                    asGalleryGalleryType.frameLayout.setVisibility(View.VISIBLE);
                    asGalleryGalleryType.imageIndexTextView.setText(mActivity.getString(R.string.image_index_in_gallery, 1, post.getGallery().size()));
                    if (preview != null) {
                        if (mFixedHeightPreviewInCard || (preview.getPreviewWidth() <= 0 || preview.getPreviewHeight() <= 0)) {
                            asGalleryGalleryType.adapter.setMaxPreviewHeight(getMaxPreviewHeight());
                            asGalleryGalleryType.adapter.setRatio(SQUARE_PREVIEW_RATIO);
                        } else {
                            asGalleryGalleryType.adapter.setMaxPreviewHeight(0);
                                asGalleryGalleryType.adapter.setRatio((float) preview.getPreviewHeight() / preview.getPreviewWidth());
                        }
                    } else {
                        asGalleryGalleryType.adapter.setMaxPreviewHeight(getMaxPreviewHeight());
                            asGalleryGalleryType.adapter.setRatio(SQUARE_PREVIEW_RATIO);
                    }
                    // No tile box: this layout's columns are only measured once laid out, so the
                    // tiles size themselves from their views.
                    asGalleryGalleryType.adapter.setDataSavingMode(mDataSavingMode);
                    boolean blurGallery = shouldBlurGallery(post);
                    asGalleryGalleryType.adapter.setBlurImage(blurGallery);
                    asGalleryGalleryType.adapter.setAutoplayGif(shouldAutoplayGalleryGif(post, blurGallery));
                    asGalleryGalleryType.adapter.setGalleryImages(post.getGallery());
                    resolveImageHostGallery((PostGalleryBaseGalleryTypeViewHolder) holder, post);
                }
            }
        }
    }

    /**
     * Size a preview to a square, as tall as the column it sits in is wide. See
     * {@link PostCardPreviewStyle#squareShape}, which holds this rule and the reasons for it.
     */
    private void setSquarePreview(AspectRatioGifImageView imageView, boolean letterbox) {
        PostCardPreviewStyle.squareShape(imageView, letterbox, getMaxPreviewHeight());
    }

    /**
     * Shows {@code text} in one of a card's selftext snippet slots, or leaves that slot hidden when
     * the body has nothing for it -- a card whose image opens the body has no words above it, one
     * whose body ends with its image has none below, and a card led by Reddit's own preview of a
     * link has none above at all.
     */
    private void bindSnippet(@Nullable TextView slot, @Nullable String text, Post post) {
        if (slot == null || text == null || text.isEmpty()) {
            return;
        }
        slot.setTextColor(mHandleReadPost && post.isRead() ? mReadPostContentColor : mPostContentColor);
        slot.setText(text);
        slot.setVisibility(View.VISIBLE);
    }

    /**
     * Hides both of a card's selftext snippet slots, the one above the preview and the one below
     * it. Called at the top of every bind of this holder, so only the slot the bound post asks for
     * is made visible again.
     */
    private static void hideSnippetSlots(PostWithPreviewTypeViewHolder holder) {
        if (holder.contentTextView != null) {
            holder.contentTextView.setVisibility(View.GONE);
        }
        if (holder.contentTextViewBelowPreview != null) {
            holder.contentTextViewBelowPreview.setVisibility(View.GONE);
        }
    }

    /**
     * Whether {@code post}'s letterboxed square gets an autoplaying video card's black bars rather
     * than the card colour. See {@link PostCardPreviewStyle#letterboxWithVideoBars}.
     */
    private boolean letterboxWithVideoBars(Post post) {
        return PostCardPreviewStyle.letterboxWithVideoBars(post, mAutoplay);
    }

    /**
     * Whether {@code preview} is drawn by {@link #setSquarePreview} rather than
     * {@link #setPreviewRatio}. See {@link PostCardPreviewStyle#squarePreview}.
     */
    private boolean isSquarePreview(Post.Preview preview) {
        return PostCardPreviewStyle.squarePreview(preview, mFixedHeightPreviewInCard);
    }

    /**
     * Size a preview from the dimensions Reddit reported for it, the behaviour when "Fixed Height
     * in Card" is off. See {@link PostCardPreviewStyle#ratioShape}.
     */
    private static void setPreviewRatio(AspectRatioGifImageView imageView, Post.Preview preview) {
        PostCardPreviewStyle.ratioShape(imageView, preview);
    }

    /**
     * Ceiling for a fixed-height preview: half the feed's visible height.
     *
     * <p>A square preview is as tall as its column is wide. In portrait a column is never
     * especially wide, so that lands well, but in landscape a column can be wider than the screen
     * is tall -- measured at one column on a phone, the preview alone came to more than two
     * screenfuls. Deriving the ceiling from the viewport instead of the width keeps a post's header
     * and the top of the next post on screen at any column count and any orientation.
     */
    /**
     * Draws the standing "video, no preview" glyph where the still would go.
     *
     * <p>Reddit does not generate a preview for every video post -- MLB highlights on r/baseball
     * are posted as direct MP4 links and have none -- and the autoplay card makes its preview view
     * visible whether or not there is anything to put in it. Left empty it is a black rectangle
     * with the player's buffering spinner turning on top of it, which reads as a card stuck
     * loading. This is the same placeholder the no-preview link and gallery cards already use.
     */
    private void showNoPreviewPlaceholder(ImageView previewImageView) {
        mGlide.clear(previewImageView);
        applyNoPreviewPlaceholder(previewImageView);
    }

    /**
     * The placeholder's appearance on its own, without cancelling any request.
     *
     * <p>Separate because a load that has just failed reports it from inside Glide's own callback,
     * where clearing the request it is reporting on is not allowed.
     */
    private void applyNoPreviewPlaceholder(ImageView previewImageView) {
        previewImageView.setScaleType(ImageView.ScaleType.CENTER);
        previewImageView.setImageResource(R.drawable.ic_video_day_night_24dp);
    }

    /**
     * The glyph a card shows where a preview would go when the post has none.
     *
     * <p>Mirrors what the compact and card layouts already draw for a post whose preview is
     * suppressed by data saving, so a post with no preview and one with a hidden preview look the
     * same rather than each having its own empty state.
     */
    private int noPreviewIconFor(int postType) {
        switch (postType) {
            case Post.VIDEO_TYPE:
                return R.drawable.ic_video_day_night_24dp;
            case Post.IMAGE_TYPE:
            case Post.GIF_TYPE:
                return R.drawable.ic_image_day_night_24dp;
            case Post.GALLERY_TYPE:
                return R.drawable.ic_gallery_day_night_24dp;
            default:
                return R.drawable.ic_link_day_night_24dp;
        }
    }

    /**
     * Fills a seeded image-host album in with its real images, once the album page has been read.
     *
     * The card is already a gallery card showing the cover Reddit previewed, so this only ever adds
     * tiles: nothing blanks, and the index badge goes from 1/1 to 1/N in place. Resolved albums are
     * cached, so scrolling back to a card, or opening the post, costs nothing and shows the full
     * count in the first frame.
     *
     * A scrape that fails leaves the cover tile, which is a card that still shows the post and still
     * opens the album on tap -- the same outcome the row would have had without any of this.
     */
    @SuppressWarnings("ReferenceEquality") // Holder identity, the same check the clip resolver makes.
    private void resolveImageHostGallery(PostBaseGalleryTypeViewHolder holder, Post post) {
        if (holder.fetchAlbumCancellable != null) {
            holder.fetchAlbumCancellable.cancel();
            holder.fetchAlbumCancellable = null;
        }
        ImageHostUtils.Host imageHost = post.getImageHost();
        String pageUrl = post.getUrl();
        if (post.isImageHostGalleryResolved() || imageHost == null || pageUrl == null) {
            return;
        }

        FetchImageHostMedia.Cancellable cancellable = new FetchImageHostMedia.Cancellable();
        holder.fetchAlbumCancellable = cancellable;
        FetchImageHostMedia.fetchAlbumInRecyclerViewAdapter(mExecutor, new Handler(),
                mImageHostOkHttpClient, imageHost, pageUrl, cancellable,
                media -> {
                    post.setResolvedImageHostGallery(FetchImageHostMedia.toGallery(
                            media, post.getSubredditName(), post.getId()));
                    // Only when this holder is still the one that asked: a fling rebinds it to
                    // another post while the page is still being read.
                    if (holder.fetchAlbumCancellable == cancellable) {
                        holder.fetchAlbumCancellable = null;
                        // The grid was shaped for the single cover tile the card was seeded with.
                        if (mShowGalleryMediaAsGrid) {
                            applyGalleryGridShape(holder, post.getGallery().size());
                        }
                        holder.adapter.setGalleryImages(post.getGallery());
                        // The page the user is on, not page one. The seeded card held a single
                        // tile, so a resume that reopened on image nine put the carousel back at
                        // the cover the moment the album arrived and overwrote nine with zero.
                        int gallerySize = post.getGallery().size();
                        int galleryPage = Math.max(0, Math.min(post.getGalleryPageIndex(), gallerySize - 1));
                        holder.imageIndexTextView.setText(mActivity.getString(
                                R.string.image_index_in_gallery, galleryPage + 1, gallerySize));
                        // Not under a finger: the album landing while the user is already swiping
                        // the cover must not pull the carousel back.
                        if (galleryPage > 0
                                && holder.galleryRecyclerView.getScrollState() == RecyclerView.SCROLL_STATE_IDLE) {
                            holder.galleryRecyclerView.scrollToPosition(galleryPage);
                        }
                    }
                });
    }

    /**
     * The same fill-in for the Gallery post layout, whose rows are a separate holder.
     *
     * Without it an image-host album in that layout keeps the single cover tile it was seeded with
     * for good -- nothing else ever reads the page -- so the card reads 1/1 and its carousel has
     * nothing to swipe to. Shorter than the card version because this layout's badge is a fixed
     * "1 of N" and it restores no page, so there is only the tile list to put back.
     */
    @SuppressWarnings("ReferenceEquality") // Holder identity, the same check the card resolver makes.
    private void resolveImageHostGallery(PostGalleryBaseGalleryTypeViewHolder holder, Post post) {
        if (holder.fetchAlbumCancellable != null) {
            holder.fetchAlbumCancellable.cancel();
            holder.fetchAlbumCancellable = null;
        }
        ImageHostUtils.Host imageHost = post.getImageHost();
        String pageUrl = post.getUrl();
        if (post.isImageHostGalleryResolved() || imageHost == null || pageUrl == null) {
            return;
        }

        FetchImageHostMedia.Cancellable cancellable = new FetchImageHostMedia.Cancellable();
        holder.fetchAlbumCancellable = cancellable;
        FetchImageHostMedia.fetchAlbumInRecyclerViewAdapter(mExecutor, new Handler(),
                mImageHostOkHttpClient, imageHost, pageUrl, cancellable,
                media -> {
                    post.setResolvedImageHostGallery(FetchImageHostMedia.toGallery(
                            media, post.getSubredditName(), post.getId()));
                    // Only when this holder is still the one that asked: a fling rebinds it to
                    // another post while the page is still being read.
                    if (holder.fetchAlbumCancellable == cancellable) {
                        holder.fetchAlbumCancellable = null;
                        holder.adapter.setGalleryImages(post.getGallery());
                        holder.imageIndexTextView.setText(mActivity.getString(
                                R.string.image_index_in_gallery, 1, post.getGallery().size()));
                    }
                });
    }

    /**
     * Reads {@code post}'s album page ahead of the row that will draw it, for the preloader warming
     * the rows the feed is about to reach.
     *
     * An album is seeded with one tile until its page has been read, and a one-tile carousel cannot
     * be swiped and reads 1/1. Resolving on bind means every card wears that for the length of a
     * page load; resolving ahead of the viewport means the card is a real carousel in its first
     * frame -- and so is the post opened from it, which shares the cache.
     */
    public void prefetchImageHostAlbum(Post post) {
        ImageHostUtils.Host imageHost = post.getImageHost();
        String pageUrl = post.getUrl();
        if (imageHost == null || pageUrl == null || post.isImageHostGalleryResolved()) {
            return;
        }
        FetchImageHostMedia.prefetch(mImageHostOkHttpClient, imageHost, pageUrl);
    }

    private int getMaxPreviewHeight() {
        return mActivity.getResources().getDisplayMetrics().heightPixels / 2;
    }

    @Nullable
    private Post.Preview getSuitablePreview(ArrayList<Post.Preview> previews) {
        @Nullable
        Post.Preview preview;
        if (!previews.isEmpty()) {
            int previewIndex;
            if (mDataSavingMode && previews.size() > 2) {
                previewIndex = previews.size() / 2;
            } else {
                previewIndex = 0;
            }
            preview = previews.get(previewIndex);
            if (preview.getPreviewWidth() * preview.getPreviewHeight() > mMaxResolution) {
                for (int i = previews.size() - 1; i >= 1; i--) {
                    preview = previews.get(i);
                    if (preview.getPreviewWidth() * preview.getPreviewHeight() <= mMaxResolution) {
                        return preview;
                    }
                }
            }
            return preview;
        }

        return null;
    }

    @Nullable
    private Post.Preview getSuitablePreviewWithThumbnailFallback(ArrayList<Post.Preview> previews, @Nullable String thumbnailUrl) {
        Post.Preview preview = getSuitablePreview(previews);
        if (preview == null && thumbnailUrl != null && !thumbnailUrl.isEmpty() && !thumbnailUrl.equals("self") && !thumbnailUrl.equals("default") && !thumbnailUrl.equals("nsfw") && !thumbnailUrl.equals("spoiler") && !thumbnailUrl.equals("image") && thumbnailUrl.startsWith("http")) {
            return new Post.Preview(thumbnailUrl, 0, 0, "", "");
        }
        return preview;
    }

    private boolean hasValidThumbnailFallback(@Nullable String thumbnailUrl) {
        return thumbnailUrl != null && !thumbnailUrl.isEmpty() && !thumbnailUrl.equals("self") && !thumbnailUrl.equals("default") && !thumbnailUrl.equals("nsfw") && !thumbnailUrl.equals("spoiler") && !thumbnailUrl.equals("image") && thumbnailUrl.startsWith("http");
    }

    private boolean shouldUseCompactLayout(Post post) {
        return (post.getPreviews() == null || post.getPreviews().isEmpty()) && !hasValidThumbnailFallback(post.getThumbnailUrl());
    }

    /**
     * Whether a gallery post may animate its gifs in the feed (issue #382). This is the same rule
     * an autoplaying video post follows — Settings -&gt; Video -&gt; "Video Autoplay" (already folded
     * into {@link #mAutoplay} together with the Wi-Fi check), skipping NSFW posts when "Autoplay
     * NSFW videos" is off, and skipping spoilers — plus two of its own: a blurred gallery keeps its
     * still, and so does the grid layout, where every tile is on screen at once and animating them
     * would mean pulling every gif in the gallery at full size.
     */
    private boolean shouldAutoplayGalleryGif(Post post, boolean blurGallery) {
        return mAutoplay && !blurGallery && !mShowGalleryMediaAsGrid
                && !((!mAutoplayNsfwVideos && post.isNSFW()) || post.isSpoiler())
                && post.hasGalleryGif();
    }

    /**
     * The scroll-idle hook that decides which gif cards animate.
     *
     * <p>Registered on whatever RecyclerView hosts this adapter, so every feed gets the behaviour
     * without its fragment wiring anything.
     */
    private final RecyclerView.OnScrollListener gifAutoplayScrollListener = new RecyclerView.OnScrollListener() {
        @Override
        public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
            if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                updateAnimatedGifs(recyclerView);
            }
        }
    };

    @Nullable
    private RecyclerView attachedRecyclerView;

    // Set while another screen, such as the gallery list, is drawn over the feed. That screen is
    // translucent, so the feed behind it keeps painting, and a gif card left animating there costs
    // frames the screen in front needs. Videos are already paused by the fragment's onPause.
    private boolean mFeedPaused;

    /**
     * Stops every animating gif card while the feed is covered, and lets them start again once
     * it is back on screen.
     */
    public void setFeedPaused(boolean paused) {
        if (mFeedPaused == paused) {
            return;
        }
        mFeedPaused = paused;
        if (attachedRecyclerView != null) {
            updateAnimatedGifs(attachedRecyclerView);
        }
    }

    private final Runnable gifAutoplayUpdate = () -> {
        if (attachedRecyclerView != null) {
            updateAnimatedGifs(attachedRecyclerView);
        }
    };

    @Override
    public void onAttachedToRecyclerView(@NonNull RecyclerView recyclerView) {
        super.onAttachedToRecyclerView(recyclerView);
        attachedRecyclerView = recyclerView;
        recyclerView.addOnScrollListener(gifAutoplayScrollListener);
    }

    @Override
    public void onDetachedFromRecyclerView(@NonNull RecyclerView recyclerView) {
        super.onDetachedFromRecyclerView(recyclerView);
        recyclerView.removeOnScrollListener(gifAutoplayScrollListener);
        recyclerView.removeCallbacks(gifAutoplayUpdate);
        attachedRecyclerView = null;
    }

    /**
     * Re-runs the gif selection once the current burst of binds has settled.
     *
     * <p>{@link #gifAutoplayScrollListener} only hears genuine scroll-state transitions, so a feed
     * that has just loaded -- or one whose rows resized under a late-arriving preview -- would sit
     * on stills until the user scrolled. Debounced because a screenful of rows binds at once.
     */
    private void scheduleGifAutoplayUpdate() {
        if (attachedRecyclerView == null) {
            return;
        }
        attachedRecyclerView.removeCallbacks(gifAutoplayUpdate);
        attachedRecyclerView.postDelayed(gifAutoplayUpdate, 100);
    }

    /**
     * Swaps gif cards between their still and their animation so that only the card being
     * autoplayed pulls the gif down.
     *
     * <p>Every gif card used to start loading its full-size original the moment it bound, so a
     * screenful of them fetched several multi-megabyte files at once and each card sat blank until
     * its own finished -- Glide cannot decode a gif frame until the whole file has arrived. The
     * preview is a small still that is already on its way, so binding to that first gives the card
     * something to show immediately, and the animation is fetched only for the card the same rule
     * would have autoplayed a video on.
     *
     * <p>That rule is deliberately the video one: Settings -&gt; Video -&gt; "Autoplay Videos
     * Visible Area Offset" and "Simultaneous Autoplay Limit", the nsfw/spoiler blur, and the data
     * saving switches that suppress a gif preview outright. The limit is one budget across both --
     * see {@link #unifiedAutoplaySelection}.
     */
    private void updateAnimatedGifs(@NonNull RecyclerView recyclerView) {
        RecyclerView.LayoutManager layoutManager = recyclerView.getLayoutManager();
        if (layoutManager == null) {
            return;
        }

        // null means no limit, in which case every card that is visible enough animates.
        Set<Integer> selected = mSimultaneousAutoplayLimit < 0 ? null
                : unifiedAutoplaySelection(recyclerView);

        for (int i = 0; i < layoutManager.getChildCount(); i++) {
            View child = layoutManager.getChildAt(i);
            if (child == null) {
                continue;
            }
            RecyclerView.ViewHolder holder = recyclerView.getChildViewHolder(child);
            View mediaView = gifMediaView(holder);
            if (mediaView == null) {
                continue;
            }
            // Every gif card on screen is visited, not just the winners: one that has dropped below
            // the threshold has to be put back to its still.
            boolean animate = !mFeedPaused
                    && ToroUtil.visibleAreaOffset(mediaView, recyclerView) >= mStartAutoplayVisibleAreaOffset
                    && (selected == null || selected.contains(holder.getBindingAdapterPosition()));
            setGifAnimating(holder, animate);
        }
    }

    /**
     * The adapter positions allowed to autoplay right now -- video rows and gif cards ranked
     * against one another by position and handed the same
     * {@link SharedPreferencesUtils#SIMULTANEOUS_AUTOPLAY_LIMIT} budget -- or null for a container
     * the budget does not cover.
     *
     * <p>Videos are selected by Toro and gifs here, so without a shared ranking each population
     * would spend the limit separately and a setting of one would autoplay one of each. Both sides
     * call this and act on the same answer: {@link MultiPlayPlayerSelector} keeps only the players
     * named here, and {@link #updateAnimatedGifs} animates only the gif cards named here.
     *
     * <p>Null for the gallery containers nested inside a card, whose player order is an index
     * within that gallery and so means nothing next to a feed position; those keep the old
     * first-N-candidates behaviour.
     */
    @Nullable
    @OptIn(markerClass = UnstableApi.class)
    private Set<Integer> unifiedAutoplaySelection(@NonNull RecyclerView recyclerView) {
        if (recyclerView != attachedRecyclerView || mSimultaneousAutoplayLimit < 0) {
            return null;
        }
        RecyclerView.LayoutManager layoutManager = recyclerView.getLayoutManager();
        if (layoutManager == null) {
            return Collections.emptySet();
        }

        List<Integer> candidates = new ArrayList<>();
        for (int i = 0; i < layoutManager.getChildCount(); i++) {
            View child = layoutManager.getChildAt(i);
            if (child == null) {
                continue;
            }
            RecyclerView.ViewHolder holder = recyclerView.getChildViewHolder(child);
            int position = holder.getBindingAdapterPosition();
            if (position == RecyclerView.NO_POSITION) {
                continue;
            }
            if (holder instanceof VideoAutoplayRow asVideo) {
                // The same question Toro asks of its own candidates.
                if (asVideo.wantsToPlay()) {
                    candidates.add(position);
                }
            } else {
                View mediaView = gifMediaView(holder);
                if (mediaView != null
                        && ToroUtil.visibleAreaOffset(mediaView, recyclerView) >= mStartAutoplayVisibleAreaOffset) {
                    candidates.add(position);
                }
            }
        }

        Collections.sort(candidates);
        Set<Integer> selected = new HashSet<>();
        for (int i = 0; i < Math.min(candidates.size(), mSimultaneousAutoplayLimit); i++) {
            selected.add(candidates.get(i));
        }
        return selected;
    }

    /**
     * The media view of a card that may animate a gif, or null when this holder is not one -- not a
     * gif post, blurred, preview-suppressed by data saving, or autoplay off. Video posts are
     * excluded because Toro already drives those.
     */
    @Nullable
    private View gifMediaView(RecyclerView.ViewHolder holder) {
        Post post;
        View mediaView;
        if (holder instanceof PostWithPreviewTypeViewHolder asPreview) {
            post = asPreview.post;
            mediaView = asPreview.imageView;
        } else if (holder instanceof PostGalleryViewHolder asGallery) {
            post = asGallery.post;
            mediaView = asGallery.binding.imageViewItemPostGallery;
        } else {
            return null;
        }

        if (post == null || post.getPostType() != Post.GIF_TYPE || !mAutoplay) {
            return null;
        }
        if (mDataSavingMode && (mDisableImagePreview || mOnlyDisablePreviewInVideoAndGifPosts)) {
            return null;
        }
        boolean blurImage = (post.isNSFW() && mNeedBlurNsfw
                && !(mDoNotBlurNsfwInNsfwSubreddits && mFragment != null && mFragment.getIsNsfwSubreddit())
                && !(mAutoplay && mAutoplayNsfwVideos))
                || (post.isSpoiler() && mNeedBlurSpoiler);
        return blurImage ? null : mediaView;
    }

    /**
     * Starts {@code toroPlayer} on the clip of {@code post}, with the sound the feed's muting
     * settings give it, or none at all when {@code silent}: a gallery tile has no mute button to
     * answer to.
     */
    private void bindAutoplayVideo(VideoAutoplayImpl toroPlayer, Post post, boolean silent) {
        boolean muted = silent || (post.isNSFW() && mMuteNSFWVideo);
        toroPlayer.setForceMuted(muted);
        if (!toroPlayer.isManuallyPaused) {
            if (mFragment.getMasterMutingOption() == null) {
                toroPlayer.setVolume(mMuteAutoplayingVideos || muted ? 0f : 1f);
            } else {
                toroPlayer.setVolume(mFragment.getMasterMutingOption() || muted ? 0f : 1f);
            }
        }
        toroPlayer.loadVideo();
    }

    /** Loads the animation or the still into {@code holder}, if it is not already showing it. */
    private void setGifAnimating(RecyclerView.ViewHolder holder, boolean animating) {
        Post post;
        Post.Preview preview;
        ImageView imageView;
        RequestListener<Drawable> listener;
        boolean showing;
        if (holder instanceof PostWithPreviewTypeViewHolder) {
            PostWithPreviewTypeViewHolder previewHolder = (PostWithPreviewTypeViewHolder) holder;
            showing = previewHolder.animatingGif;
            post = previewHolder.post;
            preview = previewHolder.preview;
            imageView = previewHolder.imageView;
            listener = previewHolder.glideRequestListener;
        } else if (holder instanceof PostGalleryViewHolder) {
            PostGalleryViewHolder galleryHolder = (PostGalleryViewHolder) holder;
            showing = galleryHolder.animatingGif;
            post = galleryHolder.post;
            preview = galleryHolder.preview;
            imageView = galleryHolder.binding.imageViewItemPostGallery;
            listener = galleryHolder.requestListener;
        } else {
            return;
        }

        if (showing == animating) {
            return;
        }
        // Every reason to do nothing is settled before the flag moves. Recording a swap that was
        // then skipped would latch the card into a state it never entered, and the guard above
        // would keep it there once the missing piece arrived.
        if (preview == null) {
            return;
        }
        String gifUrl = post.getUrl();
        if (animating && gifUrl == null) {
            // Glide clears the view for a null model, which would drop the still the card is
            // already showing in exchange for nothing.
            return;
        }

        if (holder instanceof PostWithPreviewTypeViewHolder asPreview) {
            asPreview.animatingGif = animating;
        } else {
            ((PostGalleryViewHolder) holder).animatingGif = animating;
        }

        if (animating) {
            // The still rides along as the thumbnail so the card keeps showing something for the
            // seconds the original takes to arrive, instead of blanking back to the placeholder.
            mGlide.load(gifUrl)
                    .listener(listener)
                    .thumbnail(mGlide.load(preview.getPreviewUrl()).centerInside()
                            .downsample(mSaveMemoryCenterInsideDownsampleStrategy))
                    .centerInside()
                    .downsample(mSaveMemoryCenterInsideDownsampleStrategy)
                    .into(imageView);
        } else {
            mGlide.load(preview.getPreviewUrl())
                    .listener(listener)
                    .centerInside()
                    .downsample(mSaveMemoryCenterInsideDownsampleStrategy)
                    .into(imageView);
        }
    }

    /**
     * The request a with-preview card draws its picture with, sized to the box that card will
     * measure to. One builder for the row's own load and for the preload that warms it; Glide keys
     * its memory cache on url, size and transformation, so anything that differs between the two
     * costs a second decode and leaves the pop-in in place.
     *
     * @param crop whether the card centre-crops the picture to a square; see
     *             {@link #cardPreviewCrops}
     */
    private RequestBuilder<Drawable> cardPreviewRequest(Post.Preview preview, boolean blurImage, boolean crop) {
        int width = cardPreviewWidthPx();
        int height = PostCardPreviewStyle.previewHeightPx(preview, mFixedHeightPreviewInCard,
                getMaxPreviewHeight(), width);
        RequestBuilder<Drawable> builder = mGlide.load(preview.getPreviewUrl());
        if (blurImage) {
            builder = builder.apply(RequestOptions.bitmapTransform(new BlurTransformation(50, 10)));
        } else if (crop) {
            // Decoded to cover the square and cut to it, so the card's centre crop is 1:1 rather
            // than an enlargement of a picture fitted inside it.
            builder = builder.centerCrop().downsample(mSaveMemoryCenterInsideDownsampleStrategy.filling());
        } else {
            builder = builder.centerInside().downsample(mSaveMemoryCenterInsideDownsampleStrategy);
        }
        // Only when both are known: override(0, 0) is not a size, and Glide would rather wait for
        // the view than be told that.
        return width > 0 && height > 0 ? builder.override(width, height) : builder;
    }

    /**
     * Width in pixels of the image a with-preview card draws.
     *
     * {@code itemWidthPx} is the item as laid out, which is the whole card in the card-1 layout.
     * Card 2 draws inside a card with a 16dp margin on each side, resolved to pixels the way a
     * layout resolves it. Card 3's item is the card itself, inset by those margins too -- except in
     * a multi-column feed, whose decoration cancels them, leaving the item the card's width. Zero
     * until the feed has been laid out once, which is the signal to leave the size to the view.
     */
    private int cardPreviewWidthPx() {
        if (itemWidthPx <= 0) {
            return 0;
        }
        boolean inset = SharedPreferencesUtils.isCardTop(mPostLayout) && !multiColumn;
        return Math.max(0, itemWidthPx - (inset ? 2 * Math.round(16 * mScale) : 0));
    }

    /**
     * Whether a with-preview card centre-crops {@code post}'s {@code preview} to its square, which
     * decides how the picture is decoded. A video card's still is fitted, never cropped.
     */
    private boolean cardPreviewCrops(Post post, Post.Preview preview) {
        return PostCardPreviewStyle.cropsSquare(preview, post, mFixedHeightPreviewInCard, mAutoplay);
    }

    /**
     * Whether {@code post}'s card blurs its preview, which is part of the request's cache key.
     */
    private boolean shouldBlurPreview(Post post) {
        return (post.isNSFW() && mNeedBlurNsfw
                && !(mDoNotBlurNsfwInNsfwSubreddits && mFragment != null && mFragment.getIsNsfwSubreddit())
                && !(post.getPostType() == Post.GIF_TYPE && mAutoplay && mAutoplayNsfwVideos))
                || (post.isSpoiler() && mNeedBlurSpoiler);
    }

    private int getTypeColor(int postType) {
        switch (postType) {
            case Post.VIDEO_TYPE:
                return mVideoTypeBackgroundColor;
            case Post.GIF_TYPE:
                return mGifTypeBackgroundColor;
            case Post.IMAGE_TYPE:
                return mImageTypeBackgroundColor;
            case Post.LINK_TYPE:
            case Post.NO_PREVIEW_LINK_TYPE:
                return mLinkTypeBackgroundColor;
            case Post.GALLERY_TYPE:
                return mGalleryTypeBackgroundColor;
            case Post.TEXT_TYPE:
            default:
                return mTextTypeBackgroundColor;
        }
    }

    private GradientDrawable createRoundedBackground(int color, float cornerRadiusDp) {
        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.RECTANGLE);
        background.setColor(color);
        background.setCornerRadius(cornerRadiusDp * mScale);
        return background;
    }

    private void applyTypeColor(@Nullable CustomTextView typeTextView, int postType) {
        if (typeTextView == null) return;
        int color = getTypeColor(postType);
        typeTextView.setBackgroundColor(color);
        typeTextView.setBorderColor(color);
    }

    private void applyTriangleIndicator(PostCompactBaseViewHolder holder, int postType) {
        // The indicator lives in both the preview-image wrapper and the no-preview placeholder
        // frame; only one of those frames is visible at a time, so colouring both keeps the
        // triangle visible whether or not the post has a usable preview image.
        applyTriangleIndicator(holder.postTypeIndicatorView, postType);
        applyTriangleIndicator(holder.noPreviewPostTypeIndicatorView, postType);
    }

    private void applyTriangleIndicator(@Nullable PostTypeIndicatorView indicatorView, int postType) {
        if (indicatorView == null) return;
        if (mPostTypeTriangleIndicator && !mHidePostType) {
            indicatorView.setIndicatorColor(getTypeColor(postType));
            indicatorView.setVisibility(View.VISIBLE);
        } else {
            indicatorView.setVisibility(View.GONE);
        }
    }

    private void loadImage(final RecyclerView.ViewHolder holder) {
        if (holder instanceof PostWithPreviewTypeViewHolder asPreview) {
            asPreview.loadingIndicator.setVisibility(View.VISIBLE);
            Post post = asPreview.post;
            Post.Preview preview = asPreview.preview;
            // A gif letterboxed in its square gets the black bars an autoplaying video card draws
            // around its clip (the #000000 behind that card's PlayerView); every other card keeps
            // the card colour behind its still. Cleared rather than left, because this holder may
            // have carried the bars, or the transparent-image backdrop, for the post before.
            if (preview != null && isSquarePreview(preview) && letterboxWithVideoBars(post)) {
                asPreview.imageView.setBackgroundColor(Color.BLACK);
            } else {
                asPreview.imageView.setBackground(null);
            }
            if (preview != null) {
                boolean blurImage = shouldBlurPreview(post);
                // The still, always -- a gif card starts on its preview even with autoplay on, and
                // only swaps to the animation once it is the card being autoplayed. See
                // updateAnimatedGifs. The flag has to follow, because this runs on an in-place
                // rebind too (a vote, a save, the read-state recolour) where the holder is not
                // recycled: leaving it set would have setGifAnimating believe the card was already
                // animating and decline to swap the animation back in.
                asPreview.animatingGif = false;
                // Sized through cardPreviewRequest rather than by the view, so that the preloader
                // warming this row ahead of the scroll asks Glide for the same box and the bitmap
                // it decoded is the one this load finds in the memory cache.
                RequestBuilder<Drawable> imageRequestBuilder =
                        cardPreviewRequest(preview, blurImage, cardPreviewCrops(post, preview))
                                .listener(asPreview.glideRequestListener);
                imageRequestBuilder.into(asPreview.imageView);
                if (post.getPostType() == Post.GIF_TYPE) {
                    scheduleGifAutoplayUpdate();
                }
            }
        } else if (holder instanceof PostCompactBaseViewHolder) {
            PostCompactBaseViewHolder compactHolder = (PostCompactBaseViewHolder) holder;
            Post post = compactHolder.post;
            // Reset to the rounded-edge shape (not null): it feeds clipToOutline the 8dp rounded
            // outline so opaque thumbnails keep rounded corners (Infinity parity), and it also
            // clears any transparent-image backdrop left on a recycled holder. The requestListener
            // swaps in the (also-rounded) transparent backdrop when the loaded image needs it.
            // Cache the drawable once per holder so scrolling doesn't re-inflate it every bind.
            if (compactHolder.thumbnailRoundedEdgeBackground == null) {
                compactHolder.thumbnailRoundedEdgeBackground =
                        ContextCompat.getDrawable(mActivity, R.drawable.thumbnail_compact_layout_rounded_edge);
            }
            compactHolder.imageView.setBackground(compactHolder.thumbnailRoundedEdgeBackground);
            RequestBuilder<Drawable> thumbnailRequest = compactThumbnailRequest(post);
            if (thumbnailRequest != null) {
                Request request = thumbnailRequest
                        .error(R.drawable.ic_error_outline_black_day_night_24dp)
                        .listener(compactHolder.requestListener)
                        .into(compactHolder.imageView)
                        .getRequest();
                // A thumbnail Glide already holds in memory -- which CompactThumbnailPreloader sees to
                // for rows about to scroll in -- is set, and reported to the listener, inside into()
                // itself. Only one still on its way gets the spinner.
                compactHolder.loadingIndicator.setVisibility(
                        request != null && request.isRunning() ? View.VISIBLE : View.GONE);
            } else {
                compactHolder.loadingIndicator.setVisibility(View.GONE);
            }
        } else if (holder instanceof PostGalleryViewHolder asGallery) {
            asGallery.binding.progressBarItemPostGallery.setVisibility(View.VISIBLE);
            asGallery.binding.imageViewItemPostGallery.setBackground(null);
            Post post = asGallery.post;
            Post.Preview preview = asGallery.preview;
            if (preview != null) {
                boolean blurImage = (post.isNSFW() && mNeedBlurNsfw && !(mDoNotBlurNsfwInNsfwSubreddits && mFragment != null && mFragment.getIsNsfwSubreddit()) && !(post.getPostType() == Post.GIF_TYPE && mAutoplay && mAutoplayNsfwVideos)) || (post.isSpoiler() && mNeedBlurSpoiler);
                // The still, always; updateAnimatedGifs swaps in the animation for the card that is
                // being autoplayed. The flag follows the load for the same reason as above.
                asGallery.animatingGif = false;
                RequestBuilder<Drawable> imageRequestBuilder = mGlide.load(preview.getPreviewUrl()).listener(asGallery.requestListener);

                if (blurImage) {
                    imageRequestBuilder.apply(RequestOptions.bitmapTransform(new BlurTransformation(50, 10)))
                            .into(asGallery.binding.imageViewItemPostGallery);
                } else {
                    imageRequestBuilder.centerInside().downsample(mSaveMemoryCenterInsideDownsampleStrategy).into(asGallery.binding.imageViewItemPostGallery);
                }
                if (post.getPostType() == Post.GIF_TYPE) {
                    scheduleGifAutoplayUpdate();
                }
            }
        }
    }

    /**
     * Picks the Reddit preview to show in the square compact/card-2 thumbnail box. {@code previews}
     * is ordered [source, resolutions ascending] (see ParsePost); Reddit's resolution rungs
     * are a prefix of 108/216/320/640/960/1080 px wide. We choose the smallest rung whose shorter
     * side still covers the box, so Glide down-scales it (crisp) instead of up-scaling a tiny rung
     * (the blur in issue #339). When no rung is large enough (e.g. wide/short banners) we fall back
     * to the largest available rung rather than the source, keeping the download bounded (Reddit
     * rungs cap at 1080px wide) instead of pulling the uncapped original. The box size comes from the
     * Thumbnail size preference at the device's density, so shrinking the box also shrinks the rung
     * we download rather than leaving it pinned to the largest size the setting offers.
     */
    private Post.Preview getBestPreviewForCompactThumbnail(ArrayList<Post.Preview> previews) {
        // Largest available rung (or the source itself when it's the only entry, e.g. galleries).
        Post.Preview best = previews.get(previews.size() - 1);
        for (int i = previews.size() - 1; i >= 1; i--) {
            Post.Preview preview = previews.get(i);
            if (Math.min(preview.getPreviewWidth(), preview.getPreviewHeight()) >= mCompactThumbnailBoxSizePx) {
                best = preview;
            } else {
                break;
            }
        }
        return best;
    }

    /**
     * The request a compact row makes for {@code post}'s thumbnail, or null when there is no url to
     * load. The row and {@link CompactThumbnailPreloader} both build from this, which is what makes a
     * warmed thumbnail the one the row asks for: Glide's memory cache is keyed on the url, the size
     * and the transformation, and a request that differed in any of them would decode a second copy.
     *
     * <p>So two things the row used to leave to Glide are spelled out. The size is the box's, which
     * the ImageView fills exactly in every compact layout; naming it also lets the load start as the
     * row binds, off screen during a prefetch, instead of waiting for the view to be measured. And
     * the center crop is the one into() derived from the view's scaleType, which a preload has no view
     * to derive from.
     */
    @Nullable
    private RequestBuilder<Drawable> compactThumbnailRequest(Post post) {
        String url = null;
        ArrayList<Post.Preview> previews = post.getPreviews();
        if (previews != null && !previews.isEmpty()) {
            url = getBestPreviewForCompactThumbnail(previews).getPreviewUrl();
        } else {
            // Use thumbnail as fallback for compact view
            String thumbnailUrl = post.getThumbnailUrl();
            if (hasValidThumbnailFallback(thumbnailUrl)) {
                url = thumbnailUrl;
            }
        }
        if (url == null) {
            return null;
        }

        RequestBuilder<Drawable> request = mGlide.load(url).override(mCompactThumbnailBoxSizePx);
        if ((post.isNSFW() && mNeedBlurNsfw && !(mDoNotBlurNsfwInNsfwSubreddits && mFragment != null && mFragment.getIsNsfwSubreddit())) || (post.isSpoiler() && mNeedBlurSpoiler)) {
            return request.transform(new BlurTransformation(50, 2));
        }
        return request.optionalCenterCrop();
    }

    /**
     * Whether a compact row shows its thumbnail box for {@code post}, holding either the thumbnail or,
     * for a post with no preview, the no-preview glyph.
     */
    private boolean showsCompactThumbnailBox(Post post) {
        boolean textPostWithPreview = post.getPostType() == Post.TEXT_TYPE && postHasPreviews(post);
        return ((post.getPostType() != Post.TEXT_TYPE && post.getPostType() != Post.NO_PREVIEW_LINK_TYPE) || textPostWithPreview)
                && !(mDataSavingMode && mDisableImagePreview);
    }

    private static boolean postHasPreviews(Post post) {
        return post.getPreviews() != null && !post.getPreviews().isEmpty();
    }


    /**
     * The requests and boxes that warming [post]'s row would need, empty when that row draws no
     * picture this preloader can get ahead of.
     *
     * Covers both families, because the pop-in is the same on either: a compact row's square
     * thumbnail, and a card's full-width picture -- its preview, a video card's still, the gallery
     * tile the card opens on, or every cell of its gallery grid. Everything comes from the builders
     * the rows themselves use, which is what makes the warmed bitmap the one they find.
     */
    public List<PreloadRequest> previewPreloadRequests(Post post) {
        int viewType = viewTypeFor(post);
        if (viewType == VIEW_TYPE_POST_CARD_GALLERY_TYPE
                || viewType == VIEW_TYPE_POST_CARD_3_GALLERY_TYPE) {
            return mShowGalleryMediaAsGrid ? galleryGridPreloadRequests(post) : asList(galleryPreloadRequest(post));
        }
        return asList(previewPreloadRequest(post, viewType));
    }

    private static List<PreloadRequest> asList(@Nullable PreloadRequest request) {
        return request == null ? Collections.emptyList() : Collections.singletonList(request);
    }

    // previewPreloadRequests for every row but a gallery card's.
    @Nullable
    private PreloadRequest previewPreloadRequest(Post post, int viewType) {
        if (viewType == VIEW_TYPE_POST_COMPACT_2) {
            if (!showsCompactThumbnailBox(post) || !postHasPreviews(post)) {
                return null;
            }
            RequestBuilder<Drawable> request = compactThumbnailRequest(post);
            return request == null ? null
                    : new PreloadRequest(request, mCompactThumbnailBoxSizePx, mCompactThumbnailBoxSizePx);
        }

        boolean videoAutoplayType = viewType == VIEW_TYPE_POST_CARD_VIDEO_AUTOPLAY_TYPE
                || viewType == VIEW_TYPE_POST_CARD_3_VIDEO_AUTOPLAY_TYPE;
        // Data saving builds a with-preview card for the autoplay type (onCreateViewHolder), so
        // only without it is the row a video card drawing its unblurred still.
        boolean videoStill = videoAutoplayType && !mDataSavingMode;
        if (!videoAutoplayType
                && viewType != VIEW_TYPE_POST_CARD_WITH_PREVIEW_TYPE
                && viewType != VIEW_TYPE_POST_CARD_3_WITH_PREVIEW_TYPE) {
            return null;
        }
        // A blurred gif card draws the no-preview icon instead of its picture, so there is nothing
        // to warm; the same exclusion the bind makes.
        if (post.getPostType() == Post.GIF_TYPE && shouldBlurPreview(post)
                && !(mAutoplay && mAutoplayNsfwVideos && post.isNSFW())) {
            return null;
        }
        // The same preview the card picks, so the url matches too.
        Post.Preview preview = getSuitablePreviewWithThumbnailFallback(post.getPreviews(), post.getThumbnailUrl());
        if (preview == null) {
            return null;
        }
        int width = cardPreviewWidthPx();
        int height = PostCardPreviewStyle.previewHeightPx(preview, mFixedHeightPreviewInCard,
                getMaxPreviewHeight(), width);
        if (width <= 0 || height <= 0) {
            // The feed has not been laid out yet, so the row would not decode to a known box
            // either and a preload could only miss.
            return null;
        }
        return new PreloadRequest(
                cardPreviewRequest(preview, !videoStill && shouldBlurPreview(post),
                        !videoStill && cardPreviewCrops(post, preview)), width, height);
    }

    /**
     * The page a gallery card opens on, warmed at the tile's box through the tile's own builder,
     * so the card arrives with its picture in place. The page after it is the tile adapter's to
     * warm once the card is bound.
     */
    @Nullable
    private PreloadRequest galleryPreloadRequest(Post post) {
        ArrayList<Post.Gallery> gallery = post.getGallery();
        if ((mDataSavingMode && mDisableImagePreview) || gallery == null || gallery.isEmpty()) {
            return null;
        }
        Post.Preview preview = getSuitablePreviewWithThumbnailFallback(post.getPreviews(), post.getThumbnailUrl());
        int width = cardPreviewWidthPx();
        int height = galleryTileHeightPx(preview, width);
        if (width <= 0 || height <= 0) {
            return null;
        }
        int page = Math.max(0, Math.min(post.getGalleryPageIndex(), gallery.size() - 1));
        String still = GalleryTileStill.url(gallery.get(page), width,
                mSaveMemoryCenterInsideDownsampleStrategy.getThreshold(), mDataSavingMode);
        if (still == null) {
            // Nothing but the animation, which is loaded only once autoplay settles on the tile.
            return null;
        }
        return new PreloadRequest(PostGalleryTypeImageRecyclerViewAdapter.tileRequest(mGlide, still,
                false, shouldBlurGallery(post), preview == null || galleryTileIsSquare(preview),
                mSaveMemoryCenterInsideDownsampleStrategy, width, height),
                width, height);
    }

    /**
     * Every cell of a gallery card's grid, warmed at its box through the cell's own builder: the
     * grid shows them all at once, so the card arrives with none of them blank.
     */
    private List<PreloadRequest> galleryGridPreloadRequests(Post post) {
        ArrayList<Post.Gallery> gallery = post.getGallery();
        if ((mDataSavingMode && mDisableImagePreview) || gallery == null || gallery.isEmpty()) {
            return Collections.emptyList();
        }
        int space = galleryGridSpacePx();
        int spanCount = galleryGridSpanCount(gallery.size());
        boolean blur = shouldBlurGallery(post);
        int cells = galleryGridCellCount(gallery.size());
        List<PreloadRequest> requests = new ArrayList<>(cells);
        for (int i = 0; i < cells; i++) {
            int cell = GalleryGridCell.widthPx(space, spanCount, i % spanCount, mGalleryGridCellInsetPx);
            // The cell's own choice of still (PostGalleryTypeImageRecyclerViewAdapter.loadImage).
            String still = GalleryTileStill.url(gallery.get(i), 0,
                    mSaveMemoryCenterInsideDownsampleStrategy.getThreshold(), mDataSavingMode);
            if (cell <= 0 || still == null) {
                continue;
            }
            requests.add(new PreloadRequest(PostGalleryTypeImageRecyclerViewAdapter.gridCellRequest(
                    mGlide, still, false, blur, mSaveMemoryCenterInsideDownsampleStrategy, cell), cell, cell));
        }
        return requests;
    }

    /**
     * Shapes a gallery card's grid for {@code gallerySize} images: its columns, which the item
     * decoration has to agree with, how many cells are bound while the card is prefetched off screen
     * (all of them, rather than the two a nested list is given by default), and the cells' boxes,
     * known before layout so each decodes off screen and finds what the preloader warmed. On every
     * bind, and again when an image-host album arrives after the card was bound with its cover.
     */
    private void applyGalleryGridShape(PostBaseGalleryTypeViewHolder holder, int gallerySize) {
        RecyclerView.LayoutManager layoutManager = holder.galleryRecyclerView.getLayoutManager();
        if (!(layoutManager instanceof GridLayoutManager)) {
            return;
        }
        int spanCount = galleryGridSpanCount(gallerySize);
        ((GridLayoutManager) layoutManager).setSpanCount(spanCount);
        ((GridLayoutManager) layoutManager).setInitialPrefetchItemCount(galleryGridCellCount(gallerySize));
        if (holder.galleryRecyclerView.getItemDecorationCount() > 0) {
            RecyclerView.ItemDecoration itemDecoration = holder.galleryRecyclerView.getItemDecorationAt(0);
            if (itemDecoration instanceof PostGalleryGridLayoutItemDecoration) {
                ((PostGalleryGridLayoutItemDecoration) itemDecoration).setSpanCount(spanCount);
            }
        }
        holder.adapter.setGridGeometry(galleryGridSpacePx(), spanCount, mGalleryGridCellInsetPx);
    }

    // Columns of a gallery card's grid (GalleryGridCell.spanCount).
    private static int galleryGridSpanCount(int gallerySize) {
        return GalleryGridCell.spanCount(gallerySize);
    }

    // Cells a gallery card's grid shows (GalleryGridCell.cellCount).
    private static int galleryGridCellCount(int gallerySize) {
        return GalleryGridCell.cellCount(gallerySize);
    }

    /**
     * The width a gallery card's grid divides between its columns: the card's picture width less
     * the end padding the bind gives the grid, or 0 before the feed has been laid out.
     */
    private int galleryGridSpacePx() {
        int width = cardPreviewWidthPx();
        return width > 0 ? Math.max(0, width - galleryGridPaddingPx()) : 0;
    }

    private int galleryGridPaddingPx() {
        return (int) (8 * mScale);
    }

    /**
     * Height in pixels of a gallery card's tile {@code widthPx} wide, or 0 when the width is not
     * known: the shape the bind gives the tile adapter -- the preview's own ratio, or the square
     * capped at {@link #getMaxPreviewHeight()} under "Fixed Height in Card" or with no usable
     * preview -- worked out the way {@code AspectRatioGifImageView.onMeasure} will measure it.
     */
    private int galleryTileHeightPx(@Nullable Post.Preview preview, int widthPx) {
        if (widthPx <= 0) {
            return 0;
        }
        if (preview == null) {
            return Math.min(widthPx, getMaxPreviewHeight());
        }
        return PostCardPreviewStyle.previewHeightPx(preview, mFixedHeightPreviewInCard,
                getMaxPreviewHeight(), widthPx);
    }

    /**
     * Whether a gallery card's tiles are the capped square, cropped to fill, rather than the
     * preview's own shape: "Fixed Height in Card", or a preview with no usable size. A card with no
     * preview at all is square too.
     */
    private boolean galleryTileIsSquare(Post.Preview preview) {
        return PostCardPreviewStyle.squarePreview(preview, mFixedHeightPreviewInCard);
    }

    /** Whether a gallery card blurs its tiles, which is part of each tile request's cache key. */
    private boolean shouldBlurGallery(Post post) {
        return (post.isNSFW() && mNeedBlurNsfw
                && !(mDoNotBlurNsfwInNsfwSubreddits && mFragment != null && mFragment.getIsNsfwSubreddit()))
                || (post.isSpoiler() && mNeedBlurSpoiler);
    }

    /** A warmable image: the row's own request, and the box the row will decode it to. */
    public static final class PreloadRequest {
        public final RequestBuilder<Drawable> request;
        public final int width;
        public final int height;

        PreloadRequest(RequestBuilder<Drawable> request, int width, int height) {
            this.request = request;
            this.width = width;
            this.height = height;
        }
    }

    /**
     * How many rows ahead of the viewport are worth warming, which depends entirely on how big each
     * row's picture is.
     *
     * Glide's default memory cache is about two screens of pixels. A compact row's thumbnail is a
     * 70dp square, so dozens fit and warming a couple of screens of them costs nothing. A card's
     * preview is the full column and can be 6MB decoded, so only a handful fit at once -- warming
     * the same count would evict the previews the user is about to reach to make room for ones they
     * have not got to yet, which is slower than not preloading at all.
     */
    public int preloadRowsAhead() {
        switch (mPostLayout) {
            case SharedPreferencesUtils.POST_LAYOUT_CARD:
            case SharedPreferencesUtils.POST_LAYOUT_CARD_3:
            case SharedPreferencesUtils.POST_LAYOUT_CARD_CLEAN:
                return PostCardPreviewStyle.CARD_PRELOAD_ROWS_AHEAD;
            default:
                return PreloadWindow.MAX_AHEAD;
        }
    }

    /** Edge of the square compact thumbnail box in pixels, which every thumbnail is decoded to. */
    public int getCompactThumbnailBoxSizePx() {
        return mCompactThumbnailBoxSizePx;
    }

    private void shareLink(Post post) {
        Bundle bundle = new Bundle();
        bundle.putString(ShareBottomSheetFragment.EXTRA_POST_LINK, post.getPermalink());
        if (post.getPostType() != Post.TEXT_TYPE) {
            bundle.putInt(ShareBottomSheetFragment.EXTRA_MEDIA_TYPE, post.getPostType());
            switch (post.getPostType()) {
                case Post.IMAGE_TYPE:
                case Post.GIF_TYPE:
                case Post.LINK_TYPE:
                case Post.NO_PREVIEW_LINK_TYPE:
                    bundle.putString(ShareBottomSheetFragment.EXTRA_MEDIA_LINK, post.getUrl());
                    break;
                case Post.VIDEO_TYPE:
                    bundle.putString(ShareBottomSheetFragment.EXTRA_MEDIA_LINK, post.getVideoDownloadUrl());
                    break;
            }
        }
        bundle.putParcelable(ShareBottomSheetFragment.EXTRA_POST, post);
        ShareBottomSheetFragment shareBottomSheetFragment = new ShareBottomSheetFragment();
        shareBottomSheetFragment.setArguments(bundle);
        shareBottomSheetFragment.show(mActivity.getSupportFragmentManager(), shareBottomSheetFragment.getTag());
    }

    @Nullable
    public Post getItemByPosition(int position) {
        if (position >= 0 && super.getItemCount() > position) {
            return super.getItem(position);
        }

        return null;
    }

    public void setVoteButtonsPosition(boolean voteButtonsOnTheRight) {
        mVoteButtonsOnTheRight = voteButtonsOnTheRight;
    }

    public void setPostLayout(int postLayout) {
        mPostLayout = postLayout;
    }

    public void setBlurNsfwAndDoNotBlurNsfwInNsfwSubreddits(boolean needBlurNsfw, boolean doNotBlurNsfwInNsfwSubreddits) {
        mNeedBlurNsfw = needBlurNsfw;
        mDoNotBlurNsfwInNsfwSubreddits = doNotBlurNsfwInNsfwSubreddits;
    }

    public void setBlurSpoiler(boolean needBlurSpoiler) {
        mNeedBlurSpoiler = needBlurSpoiler;
    }

    public void setShowElapsedTime(boolean showElapsedTime) {
        mShowElapsedTime = showElapsedTime;
    }

    public void setTimeFormat(String timeFormat) {
        mTimeFormatPattern = timeFormat;
    }

    public void setShowDividerInCompactLayout(boolean showDividerInCompactLayout) {
        mShowDividerInCompactLayout = showDividerInCompactLayout;
    }

    public void setShowAbsoluteNumberOfVotes(boolean showAbsoluteNumberOfVotes) {
        mShowAbsoluteNumberOfVotes = showAbsoluteNumberOfVotes;
    }

    public boolean setAutoplay(boolean autoplay) {
        if (mAutoplay != autoplay) {
            mAutoplay = autoplay;
            return true;
        }

        return false;
    }

    public boolean isAutoplay() {
        return mAutoplay;
    }

    public void setAutoplayNsfwVideos(boolean autoplayNsfwVideos) {
        mAutoplayNsfwVideos = autoplayNsfwVideos;
    }

    /** Carries a muting choice made on one video to every video bound in the list. */
    public void applyMuteToVideos(RecyclerView recyclerView, boolean mute) {
        for (int i = 0; i < recyclerView.getChildCount(); i++) {
            RecyclerView.ViewHolder holder = recyclerView.getChildViewHolder(recyclerView.getChildAt(i));
            if (holder instanceof PostBaseVideoAutoplayViewHolder asVideo) {
                asVideo.toroPlayer.applyMute(mute);
            }
        }
    }

    public void setMuteAutoplayingVideos(boolean muteAutoplayingVideos) {
        mMuteAutoplayingVideos = muteAutoplayingVideos;
    }

    public void setShowThumbnailOnTheLeftInCompactLayout(boolean showThumbnailOnTheLeftInCompactLayout) {
        mShowThumbnailOnTheLeftInCompactLayout = showThumbnailOnTheLeftInCompactLayout;
    }

    public void setLegacyAutoplayVideoControllerUI(boolean legacyAutoplayVideoControllerUI) {
        mLegacyAutoplayVideoControllerUI = legacyAutoplayVideoControllerUI;
    }

    public void setStartAutoplayVisibleAreaOffset(double startAutoplayVisibleAreaOffset) {
        this.mStartAutoplayVisibleAreaOffset = startAutoplayVisibleAreaOffset / 100.0;
    }

    public void setMuteNSFWVideo(boolean muteNSFWVideo) {
        this.mMuteNSFWVideo = muteNSFWVideo;
    }

    public void setLongPressToHideToolbarInCompactLayout(boolean longPressToHideToolbarInCompactLayout) {
        mLongPressToHideToolbarInCompactLayout = longPressToHideToolbarInCompactLayout;
    }

    public void setCompactLayoutToolbarHiddenByDefault(boolean compactLayoutToolbarHiddenByDefault) {
        mCompactLayoutToolbarHiddenByDefault = compactLayoutToolbarHiddenByDefault;
    }

    public boolean setDataSavingMode(boolean dataSavingMode) {
        if (mDataSavingMode != dataSavingMode) {
            mDataSavingMode = dataSavingMode;
            return true;
        }

        return false;
    }

    public void setDisableImagePreview(boolean disableImagePreview) {
        mDisableImagePreview = disableImagePreview;
    }

    public void setOnlyDisablePreviewInVideoPosts(boolean onlyDisablePreviewInVideoAndGifPosts) {
        mOnlyDisablePreviewInVideoAndGifPosts = onlyDisablePreviewInVideoAndGifPosts;
    }

    // return true if the current value is not the same as the new value
    public boolean setHideNsfwBadge(boolean hideNsfwBadge) {
        if (mHideNsfwBadge != hideNsfwBadge) {
            mHideNsfwBadge = hideNsfwBadge;
            return true;
        }

        return false;
    }

    public void setHidePostType(boolean hidePostType) {
        mHidePostType = hidePostType;
    }

    public void setHidePostFlair(boolean hidePostFlair) {
        mHidePostFlair = hidePostFlair;
    }

    /**
     * {@code name} with the author's followed/saved marker and user tag chip after it, when the
     * author has either; see {@link UserMarkIcon} and {@link UserTagChip}. {@code name} is
     * whichever spelling of the author the prefix setting calls for, and {@code nameColor} the
     * colour it is drawn in, which the marker follows.
     */
    private CharSequence userTagged(Post post, String name, int nameColor) {
        CharSequence marked = UserMarkIcon.appendTo(mActivity, name, mUserMarks.of(post.getAuthor()), nameColor);
        return UserTagChip.appendTo(mActivity, marked, post.getAuthor(), mFlairBackgroundColor, mFlairTextColor);
    }

    /** The colour this post's author is drawn in; see {@link #userTagged}. */
    private int userColor(Post post) {
        return post.isModerator() ? mModeratorColor : mUsernameColor;
    }

    /**
     * The author's marker and tag chip with no name in front of them, or null when the author has
     * neither, for a row that may have to move them to a line of their own. {@code leading} is
     * whether they follow a name on the line, which decides the gap in front of the first one.
     */
    @Nullable
    private CharSequence userBadges(Post post, int nameColor, boolean leading) {
        CharSequence marker = UserMarkIcon.badgeFor(mActivity, mUserMarks.of(post.getAuthor()), nameColor, leading);
        CharSequence chip = UserTagChip.chipFor(mActivity, post.getAuthor(), mFlairBackgroundColor,
                mFlairTextColor, leading || marker != null);
        if (marker == null) {
            return chip;
        }
        if (chip == null) {
            return marker;
        }
        return new SpannableStringBuilder(marker).append(chip);
    }

    /**
     * Takes the new list of followed, saved and favourited users and rebinds the rows whose author
     * moved between them. Only those rows, for the reason {@link #notifyUserTagChanged} gives.
     */
    public void setUserMarks(UserMarks userMarks) {
        UserMarkChanges changes = userMarks.changedFrom(mUserMarks);
        mUserMarks = userMarks;
        if (changes.isEmpty()) {
            return;
        }
        ItemSnapshotList<Post> snapshot = snapshot();
        for (int i = 0; i < snapshot.size(); i++) {
            Post post = snapshot.get(i);
            if (post != null && changes.affects(post.getAuthor())) {
                notifyItemChanged(i);
            }
        }
    }

    /**
     * Rebinds the rows whose author is {@code username}, whose tag has just changed — or every
     * row when {@code username} is null, which is every tag going at once. Only those rows: a
     * rebind restarts an autoplaying video, and the other rows have nothing new to show.
     */
    public void notifyUserTagChanged(@Nullable String username) {
        if (username == null) {
            notifyDataSetChanged();
            return;
        }
        ItemSnapshotList<Post> snapshot = snapshot();
        for (int i = 0; i < snapshot.size(); i++) {
            Post post = snapshot.get(i);
            if (post != null && username.equalsIgnoreCase(post.getAuthor())) {
                notifyItemChanged(i);
            }
        }
    }

    public void setHideSubredditAndUserPrefix(boolean hideSubredditAndUserPrefix) {
        mHideSubredditAndUserPrefix = hideSubredditAndUserPrefix;
    }

    // return true if the current value is not the same as the new value
    public boolean setHideVoteButtons(boolean hideVoteButtons) {
        if (mHideVoteButtons != hideVoteButtons) {
            mHideVoteButtons = hideVoteButtons;
            return true;
        }
        return false;
    }

    public void setHideTheNumberOfVotes(boolean hideTheNumberOfVotes) {
        mHideTheNumberOfVotes = hideTheNumberOfVotes;
    }

    public void setHideTheNumberOfComments(boolean hideTheNumberOfComments) {
        mHideTheNumberOfComments = hideTheNumberOfComments;
    }

    public void setDefaultLinkPostLayout(int defaultLinkPostLayout) {
        mDefaultLinkPostLayout = defaultLinkPostLayout;
    }

    public void setFixedHeightPreviewInCard(boolean fixedHeightPreviewInCard) {
        mFixedHeightPreviewInCard = fixedHeightPreviewInCard;
    }

    public void setMarkPostsAsReadSettings(boolean markPostsAsRead, boolean markPostsAsReadAfterVoting,
                                           boolean markPostsAsReadOnScroll) {
        mMarkPostsAsRead = markPostsAsRead;
        mMarkPostsAsReadAfterVoting = markPostsAsReadAfterVoting;
        mMarkPostsAsReadOnScroll = markPostsAsReadOnScroll;
    }

    public void setHideTextPostContent(boolean hideTextPostContent) {
        mHideTextPostContent = hideTextPostContent;
    }

    public void setPostFeedMaxResolution(int postFeedMaxResolution) {
        mMaxResolution = postFeedMaxResolution;
        if (mSaveMemoryCenterInsideDownsampleStrategy != null) {
            mSaveMemoryCenterInsideDownsampleStrategy.setThreshold(postFeedMaxResolution);
        }
    }

    public void setEasierToWatchInFullScreen(boolean easierToWatchInFullScreen) {
        this.mEasierToWatchInFullScreen = easierToWatchInFullScreen;
    }

    public void setLongPressPostNonMediaAreaAction(String value) {
        mLongPressPostNonMediaAreaAction = value;
    }

    public void setLongPressPostMediaAction(String value) {
        mLongPressPostMediaAction = value;
    }

    public void setDataSavingModeDefaultResolution(int value) {
        mDataSavingModeDefaultResolution = value;
    }

    public void setNonDataSavingModeDefaultResolution(int value) {
        mNonDataSavingModeDefaultResolution = value;
    }

    public void setSimultaneousAutoplayLimit(int limit) {
        mSimultaneousAutoplayLimit = limit;
        multiPlayPlayerSelector.setSimultaneousAutoplayLimit(limit);
        // Toro re-selects on its next dispatch; the gif cards need telling.
        scheduleGifAutoplayUpdate();
    }

    /**
     * The compact/card-2 thumbnail box edge, in dp. Returns true if it changed, in which case the
     * caller must rebuild the view holders -- the size is written into layout params in
     * PostCompactBaseViewHolder.setBaseView, so holders already in the pool keep the old one.
     */
    public boolean setCompactThumbnailSizeDp(int compactThumbnailSizeDp) {
        if (mCompactThumbnailSizeDp != compactThumbnailSizeDp) {
            mCompactThumbnailSizeDp = compactThumbnailSizeDp;
            mCompactThumbnailBoxSizePx = Math.round(compactThumbnailSizeDp * mScale);
            return true;
        }

        return false;
    }

    // return true if the current value is not the same as the new value
    public boolean setShowGalleryMediaAsGrid(boolean showGalleryMediaAsGrid) {
        if (mShowGalleryMediaAsGrid != showGalleryMediaAsGrid) {
            mShowGalleryMediaAsGrid = showGalleryMediaAsGrid;
            return true;
        }

        return false;
    }

    // return true if the current value is not the same as the new value
    public boolean setShowToolbarItemsBasedOnSpace(boolean showToolbarItemsBasedOnSpace) {
        if (mShowToolbarItemsBasedOnSpace != showToolbarItemsBasedOnSpace) {
            mShowToolbarItemsBasedOnSpace = showToolbarItemsBasedOnSpace;
            return true;
        }

        return false;
    }

    // return true if the current value is not the same as the new value
    public boolean setPostTypeTriangleIndicator(boolean postTypeTriangleIndicator) {
        if (mPostTypeTriangleIndicator != postTypeTriangleIndicator) {
            mPostTypeTriangleIndicator = postTypeTriangleIndicator;
            return true;
        }

        return false;
    }

    // return true if the current value is not the same as the new value
    public boolean setHidePostTypeIndicator(boolean hidePostTypeIndicator) {
        if (mHidePostTypeIndicator != hidePostTypeIndicator) {
            mHidePostTypeIndicator = hidePostTypeIndicator;
            return true;
        }

        return false;
    }

    // return true if the current value is not the same as the new value
    public boolean setHideImageCountInGallery(boolean hideImageCountInGallery) {
        if (mHideImageCountInGallery != hideImageCountInGallery) {
            mHideImageCountInGallery = hideImageCountInGallery;
            return true;
        }

        return false;
    }

    // return true if the current value is not the same as the new value
    public boolean setDisableProfileAvatarAnimation(boolean disableProfileAvatarAnimation) {
        if (mDisableProfileAvatarAnimation != disableProfileAvatarAnimation) {
            mDisableProfileAvatarAnimation = disableProfileAvatarAnimation;
            return true;
        }

        return false;
    }

    @OptIn(markerClass = UnstableApi.class)
    @Override
    public void onViewRecycled(@NonNull RecyclerView.ViewHolder holder) {
        if (holder instanceof PostViewHolder asPost) {
            if (mHandleReadPost && mMarkPostsAsReadOnScroll) {
                // Read from the holder's own bound post, not from a position captured at bind
                // time: by the time a holder is recycled the list may have shifted, and
                // getItem(staleIndex) then marks a different post read. getBindingAdapterPosition()
                // is not an option here either — it is NO_POSITION once the holder is recycled.
                Post recycledPost = asPost.post;
                if (recycledPost != null) {
                    asPost.markPostRead(recycledPost, false);
                }
            }

            asPost.setItemViewBackgroundColor(false);

            asPost.titleTextView.setTextColor(mPostTitleColor);
            mGlide.clear(asPost.iconGifImageView);

            asPost.stickiedPostImageView.setVisibility(View.GONE);
            asPost.stickiedPostImageView.setOnClickListener(null);
            asPost.stickiedPostImageView.setClickable(false);

            if (asPost.crosspostImageView != null) {
                asPost.crosspostImageView.setVisibility(View.GONE);
            }
            if (asPost.archivedImageView != null) {
                asPost.archivedImageView.setVisibility(View.GONE);
            }
            if (asPost.lockedImageView != null) {
                asPost.lockedImageView.setVisibility(View.GONE);
            }
            if (asPost.nsfwTextView != null) {
                asPost.nsfwTextView.setVisibility(View.GONE);
            }
            if (asPost.spoilerTextView != null) {
                asPost.spoilerTextView.setVisibility(View.GONE);
            }
            if (asPost.flairTextView != null) {
                asPost.flairTextView.setText("");
                asPost.flairTextView.setVisibility(View.GONE);
            }

            asPost.upvoteButton.setIconResource(R.drawable.ic_upvote_24dp);
            asPost.upvoteButton.setIconTint(ColorStateList.valueOf(mPostIconAndInfoColor));
            asPost.scoreTextView.setTextColor(mPostIconAndInfoColor);
            asPost.downvoteButton.setIconResource(R.drawable.ic_downvote_24dp);
            asPost.downvoteButton.setIconTint(ColorStateList.valueOf(mPostIconAndInfoColor));

            if (holder instanceof PostBaseViewHolder) {
                if (holder instanceof PostBaseVideoAutoplayViewHolder asVideo) {
                    asVideo.toroPlayer.reset();
                    mGlide.clear(asVideo.toroPlayer.previewImageView);
                    asVideo.toroPlayer.previewImageView.setVisibility(View.GONE);
                } else if (holder instanceof PostWithPreviewTypeViewHolder asPreview) {
                    mGlide.clear(asPreview.imageView);
                    // The next post bound here starts on its still like any other.
                    asPreview.animatingGif = false;
                    asPreview.imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
                    if (asPreview.imageWrapperFrameLayout != null) {
                        asPreview.imageWrapperFrameLayout.setVisibility(View.GONE);
                    }
                    asPreview.imageView.setVisibility(View.GONE);
                    asPreview.loadImageErrorTextView.setVisibility(View.GONE);
                    asPreview.imageViewNoPreviewGallery.setVisibility(View.GONE);
                    asPreview.loadingIndicator.setVisibility(View.GONE);
                    asPreview.videoOrGifIndicator.setVisibility(View.GONE);
                    asPreview.linkTextView.setVisibility(View.GONE);
                    if (asPreview.contentTextView != null) {
                        asPreview.contentTextView.setText("");
                        asPreview.contentTextView.setVisibility(View.GONE);
                    }
                    if (asPreview.contentTextViewBelowPreview != null) {
                        asPreview.contentTextViewBelowPreview.setText("");
                        asPreview.contentTextViewBelowPreview.setVisibility(View.GONE);
                    }
                } else if (holder instanceof PostBaseGalleryTypeViewHolder asGalleryType) {
                    if (asGalleryType.fetchAlbumCancellable != null) {
                        asGalleryType.fetchAlbumCancellable.cancel();
                        asGalleryType.fetchAlbumCancellable = null;
                    }
                    asGalleryType.frameLayout.setVisibility(View.GONE);
                    asGalleryType.noPreviewImageView.setVisibility(View.GONE);
                    asGalleryType.adapter.setGalleryImages(null);
                } else if (holder instanceof PostTextTypeViewHolder asText) {
                    asText.contentTextView.setText("");
                    asText.contentTextView.setTextColor(mPostContentColor);
                    asText.contentTextView.setVisibility(View.GONE);
                }
            } else if (holder instanceof PostCompactBaseViewHolder asCompact) {
                mGlide.clear(asCompact.imageView);
                asCompact.relativeLayout.setVisibility(View.GONE);
                if (asCompact.linkTextView != null) {
                    asCompact.linkTextView.setVisibility(View.GONE);
                }
                asCompact.loadingIndicator.setVisibility(View.GONE);
                asCompact.imageView.setVisibility(View.GONE);
                asCompact.playButtonImageView.setVisibility(View.GONE);
                asCompact.noPreviewPostImageFrameLayout.setVisibility(View.GONE);
                if (asCompact.postTypeIndicatorView != null) {
                    asCompact.postTypeIndicatorView.setVisibility(View.GONE);
                }
                if (asCompact.noPreviewPostTypeIndicatorView != null) {
                    asCompact.noPreviewPostTypeIndicatorView.setVisibility(View.GONE);
                }
            }
        } else if (holder instanceof PostGalleryViewHolder asGallery) {
            if (mHandleReadPost && mMarkPostsAsReadOnScroll) {
                // Read from the holder's own bound post, not from a position captured at bind
                // time: by the time a holder is recycled the list may have shifted, and
                // getItem(staleIndex) then marks a different post read. getBindingAdapterPosition()
                // is not an option here either — it is NO_POSITION once the holder is recycled.
                Post recycledPost = asGallery.post;
                if (recycledPost != null) {
                    asGallery.markPostRead(recycledPost, false);
                }
            }
            holder.itemView.setBackgroundTintList(ColorStateList.valueOf(mCardViewBackgroundColor));
            if (holder instanceof PostGalleryVideoAutoplayViewHolder asVideo) {
                asVideo.toroPlayer().reset();
            }

            asGallery.binding.titleTextViewItemPostGallery.setText("");
            asGallery.binding.titleTextViewItemPostGallery.setVisibility(View.GONE);
            mGlide.clear(asGallery.binding.imageViewItemPostGallery);
            // The next post bound here starts on its still like any other.
            asGallery.animatingGif = false;
            asGallery.binding.imageViewItemPostGallery.setScaleType(ImageView.ScaleType.FIT_CENTER);
            asGallery.binding.imageViewItemPostGallery.setVisibility(View.GONE);
            asGallery.binding.progressBarItemPostGallery.setVisibility(View.GONE);
            asGallery.binding.loadImageErrorTextViewItemGallery.setVisibility(View.GONE);
            asGallery.binding.videoOrGifIndicatorImageViewItemPostGallery.setVisibility(View.GONE);
            asGallery.binding.imageViewNoPreviewItemPostGallery.setVisibility(View.GONE);
        } else if (holder instanceof PostGalleryBaseGalleryTypeViewHolder asGalleryGalleryType) {
            // The album scrape this row started is of no use to whatever post binds here next.
            if (asGalleryGalleryType.fetchAlbumCancellable != null) {
                asGalleryGalleryType.fetchAlbumCancellable.cancel();
                asGalleryGalleryType.fetchAlbumCancellable = null;
            }
            if (mHandleReadPost && mMarkPostsAsReadOnScroll) {
                // Read from the holder's own bound post, not from a position captured at bind
                // time: by the time a holder is recycled the list may have shifted, and
                // getItem(staleIndex) then marks a different post read. getBindingAdapterPosition()
                // is not an option here either — it is NO_POSITION once the holder is recycled.
                Post recycledPost = asGalleryGalleryType.post;
                if (recycledPost != null) {
                    asGalleryGalleryType.markPostRead(recycledPost, false);
                }
            }
            holder.itemView.setBackgroundTintList(ColorStateList.valueOf(mCardViewBackgroundColor));
            asGalleryGalleryType.frameLayout.setVisibility(View.GONE);
            asGalleryGalleryType.noPreviewImageView.setVisibility(View.GONE);
        }
    }

    @Nullable
    @Override
    public Object getKeyForOrder(int order) {
        if (super.getItemCount() <= 0 || order >= super.getItemCount()) {
            return null;
        }
        return order;
    }

    @Nullable
    @Override
    public Integer getOrderForKey(@NonNull Object key) {
        if (key instanceof Integer) {
            return (Integer) key;
        }

        return null;
    }

    /**
     * Runs the action a swipe landed on. Which of the direction's three levels was reached is the
     * fragment's decision; by the time it gets here it is one action on one row.
     *
     * Voting goes through the row's own buttons, which every swipeable layout has and which carry
     * the optimistic vote styling with them. Nothing else can: a compact row's toolbar can be
     * hidden and the card layouts differ, so clicking a button that may not be there would be a
     * silent no-op on whichever layout lacks it.
     */
    public void onItemSwipe(RecyclerView.ViewHolder viewHolder, int action) {
        if (!(viewHolder instanceof PostViewHolder)) {
            return;
        }
        PostViewHolder holder = (PostViewHolder) viewHolder;
        int position = holder.getBindingAdapterPosition();
        if (position < 0) {
            return;
        }
        Post post = getItemByPosition(position);
        if (post == null) {
            return;
        }

        switch (action) {
            case SharedPreferencesUtils.SWIPE_ACITON_UPVOTE:
                holder.upvoteButton.performClick();
                break;
            case SharedPreferencesUtils.SWIPE_ACITON_DOWNVOTE:
                holder.downvoteButton.performClick();
                break;
            case SharedPreferencesUtils.SWIPE_ACITON_SAVE:
                holder.toggleSave(post, position);
                break;
            case SharedPreferencesUtils.SWIPE_ACITON_HIDE:
                toggleHideWithUndo(holder.itemView, post, position);
                break;
            case SharedPreferencesUtils.SWIPE_ACITON_MARK_AS_READ_AND_HIDE:
                holder.setReadStateFromSwipe(post, true);
                hideReadPostWithUndo(holder.itemView, post, position);
                break;
            case SharedPreferencesUtils.SWIPE_ACITON_MARK_AS_READ:
                holder.setReadStateFromSwipe(post, true);
                break;
            case SharedPreferencesUtils.SWIPE_ACITON_MARK_AS_UNREAD:
                holder.setReadStateFromSwipe(post, false);
                break;
            case SharedPreferencesUtils.SWIPE_ACITON_TOGGLE_READ:
                holder.setReadStateFromSwipe(post, !post.isRead());
                break;
            case SharedPreferencesUtils.SWIPE_ACITON_SHARE:
                shareLink(post);
                break;
            case SharedPreferencesUtils.SWIPE_ACITON_PROFILE: {
                if (!post.isAuthorDeleted()) {
                    Intent profileIntent = new Intent(mActivity, ViewUserDetailActivity.class);
                    profileIntent.putExtra(ViewUserDetailActivity.EXTRA_USER_NAME_KEY, post.getAuthor());
                    mActivity.startActivity(profileIntent);
                }
                break;
            }
            case SharedPreferencesUtils.SWIPE_ACITON_COMMENT:
                writeComment(post);
                break;
            case SharedPreferencesUtils.SWIPE_ACITON_OPEN_IN_NEW_WINDOW:
                openInNewWindow(holder, post, position);
                break;
            case SharedPreferencesUtils.SWIPE_ACITON_CROSSPOST:
                if (requiresAccount()) {
                    Intent crosspostIntent = new Intent(mActivity, SubmitCrosspostActivity.class);
                    crosspostIntent.putExtra(SubmitCrosspostActivity.EXTRA_POST, post);
                    mActivity.startActivity(crosspostIntent);
                }
                break;
            default:
                break;
        }
    }

    /** Whether this action can go ahead, telling the user to log in when it cannot. */
    private boolean requiresAccount() {
        if (Account.ANONYMOUS_ACCOUNT.equals(mAccountName)) {
            Toast.makeText(mActivity, R.string.login_first, Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }

    /** Reply to the post, the way the options sheet's Comment entry does. */
    private void writeComment(Post post) {
        if (!requiresAccount()) {
            return;
        }
        if (post.isArchived()) {
            Toast.makeText(mActivity, R.string.archived_post_comment_unavailable, Toast.LENGTH_SHORT).show();
            return;
        }
        if (post.isLocked()) {
            Toast.makeText(mActivity, R.string.locked_post_comment_unavailable, Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(mActivity, CommentActivity.class);
        intent.putExtra(CommentActivity.EXTRA_PARENT_FULLNAME_KEY, post.getFullName());
        intent.putExtra(CommentActivity.EXTRA_COMMENT_PARENT_TITLE_KEY, post.getTitle());
        intent.putExtra(CommentActivity.EXTRA_COMMENT_PARENT_BODY_MARKDOWN_KEY, post.getSelfText());
        intent.putExtra(CommentActivity.EXTRA_COMMENT_PARENT_BODY_KEY, post.getSelfTextPlain());
        intent.putExtra(CommentActivity.EXTRA_SUBREDDIT_NAME_KEY, post.getSubredditName());
        intent.putExtra(CommentActivity.EXTRA_IS_REPLYING_KEY, false);
        intent.putExtra(CommentActivity.EXTRA_PARENT_DEPTH_KEY, 0);
        mActivity.startActivity(intent);
    }

    /**
     * Opens the post in its own window, as the options sheet does. The list position travels with
     * it so the vote, save and hide the window reports back still find the row they came from;
     * what is deliberately not sent is the fragment id, which would tie the window to this feed.
     */
    private void openInNewWindow(PostViewHolder holder, Post post, int position) {
        holder.markPostRead(post, true);

        Intent intent = new Intent(mActivity, ViewPostDetailActivity.class);
        intent.putExtra(ViewPostDetailActivity.EXTRA_POST_DATA, post);
        intent.putExtra(ViewPostDetailActivity.EXTRA_POST_LIST_POSITION, position);
        mActivity.startActivity(NewWindowUtils.addNewWindowFlags(intent));
    }

    /**
     * Hides or unhides, and offers to put it back. Hiding is the one destructive action on the
     * list, and on a three-level swipe it is the easiest one to overshoot into.
     */
    private void toggleHideWithUndo(View anchor, Post post, int position) {
        boolean hide = !post.isHidden();
        setHidden(post, position, hide, () ->
                Snackbar.make(anchor,
                                hide ? R.string.post_hide_success : R.string.post_unhide_success,
                                Snackbar.LENGTH_LONG)
                        .setAction(R.string.undo, view -> setHidden(post, position, !hide, null))
                        .show());
    }

    /**
     * Hides a post that has just been marked read, and puts both halves back together.
     *
     * Unlike the plain Hide action this never unhides: "Mark as Read + Hide" is one direction, and
     * swiping an already-hidden post would otherwise mark it read and unhide it in the same
     * gesture -- two answers at once. An already-hidden post has only the read half left to do,
     * which the caller has already done.
     */
    private void hideReadPostWithUndo(View anchor, Post post, int position) {
        if (post.isHidden()) {
            return;
        }
        // Names both halves, because Undo puts both back: a message that mentioned only the
        // hiding would quietly un-mark the post read as well.
        setHidden(post, position, true, () ->
                Snackbar.make(anchor, R.string.post_marked_as_read_and_hidden, Snackbar.LENGTH_LONG)
                        .setAction(R.string.undo, view -> {
                            // The stored half only. Unhiding posts an update event that repaints
                            // the row, and by then this holder may show a different post.
                            storeReadState(post, false);
                            setHidden(post, position, false, null);
                        })
                        .show());
    }

    /**
     * Anonymous accounts have no Reddit-side hidden list, so they keep their own; both branches
     * have to announce the change or the row will not redraw.
     */
    private void setHidden(Post post, int position, boolean hidden, @Nullable Runnable onSuccess) {
        if (Account.ANONYMOUS_ACCOUNT.equals(mAccountName)) {
            if (hidden) {
                ReadPostModification.insertReadPost(mRedditDataRoomDatabase, mExecutor, mActivity.accountName,
                        post.getId(), ReadPostType.ANONYMOUS_HIDDEN_POSTS,
                        ReadPostsUtils.GetReadPostsLimit(mActivity.accountName, mPostHistorySharedPreferences));
            } else {
                ReadPostModification.deleteReadPost(mRedditDataRoomDatabase, mExecutor, mActivity.accountName,
                        post.getId(), ReadPostType.ANONYMOUS_HIDDEN_POSTS);
            }
            onHidden(post, position, hidden, onSuccess);
            return;
        }

        HidePost.HidePostListener listener = new HidePost.HidePostListener() {
            @Override
            public void success() {
                onHidden(post, position, hidden, onSuccess);
            }

            @Override
            public void failed() {
                post.setHidden(!hidden);
                Toast.makeText(mActivity,
                        hidden ? R.string.post_hide_failed : R.string.post_unhide_failed,
                        Toast.LENGTH_SHORT).show();
                EventBus.getDefault().post(new PostUpdateEventToPostList(post, position));
                EventBus.getDefault().post(new PostUpdateEventToPostDetailFragment(post));
            }
        };
        if (hidden) {
            HidePost.hidePost(mOauthRetrofit, mAccessToken, post.getFullName(), listener);
        } else {
            HidePost.unhidePost(mOauthRetrofit, mAccessToken, post.getFullName(), listener);
        }
    }

    /**
     * The stored half of a post's read state, with none of the row's colours. Assumes the caller
     * has checked that this feed handles read posts at all.
     */
    private void storeReadState(Post post, boolean read) {
        if (read) {
            post.markAsRead();
            ReadPostModification.insertReadPost(mRedditDataRoomDatabase, mExecutor, mActivity.accountName,
                    post.getId(), ReadPostType.READ_POSTS,
                    ReadPostsUtils.GetReadPostsLimit(mActivity.accountName, mPostHistorySharedPreferences));
        } else {
            post.markAsUnread();
            ReadPostModification.deleteReadPost(mRedditDataRoomDatabase, mExecutor, mActivity.accountName,
                    post.getId(), ReadPostType.READ_POSTS);
        }
    }

    private void onHidden(Post post, int position, boolean hidden, @Nullable Runnable onSuccess) {
        post.setHidden(hidden);
        EventBus.getDefault().post(new PostUpdateEventToPostList(post, position));
        EventBus.getDefault().post(new PostUpdateEventToPostDetailFragment(post));
        if (onSuccess != null) {
            onSuccess.run();
        } else {
            Toast.makeText(mActivity,
                    hidden ? R.string.post_hide_success : R.string.post_unhide_success,
                    Toast.LENGTH_SHORT).show();
        }
    }

    public interface Callback {
        void typeChipClicked(int filter);

        void flairChipClicked(String flair);

        void nsfwChipClicked();

        void currentlyBindItem(int position);

        void delayTransition();
    }

    private void openViewPostDetailActivity(Post post, int position) {
        if (canStartActivity) {
            canStartActivity = false;
            Intent intent = new Intent(mActivity, ViewPostDetailActivity.class);
            intent.putExtra(ViewPostDetailActivity.EXTRA_POST_DATA, post);
            intent.putExtra(ViewPostDetailActivity.EXTRA_POST_LIST_POSITION, position);
            intent.putExtra(ViewPostDetailActivity.EXTRA_POST_FRAGMENT_ID, mFragment.getPostFragmentId());
            intent.putExtra(ViewPostDetailActivity.EXTRA_IS_NSFW_SUBREDDIT, mFragment.getIsNsfwSubreddit());
            mActivity.startActivity(intent);
        }
    }

    private void openMedia(Post post) {
        openMedia(post, 0, false);
    }

    private void openMedia(Post post, boolean peekMedia) {
        openMedia(post, 0, peekMedia);
    }

    private void openMedia(Post post, int galleryItemIndex, boolean peekMedia) {
        openMedia(post, galleryItemIndex, -1, peekMedia);
    }

    private void openMedia(Post post, long videoProgress) {
        openMedia(post, 0, videoProgress, false);
    }

    @OptIn(markerClass = UnstableApi.class)
    private void openMedia(Post post, int galleryItemIndex, long videoProgress, boolean peekMedia) {
        if (canStartActivity) {
            canStartActivity = false;
            if (post.getPostType() == Post.VIDEO_TYPE) {
                if (peekMedia) {
                    mActivity.setShouldTrackFullscreenMediaPeekTouchEvent(true);
                }

                Intent intent = new Intent(mActivity, ViewVideoActivity.class);
                if (post.isImgur()) {
                    intent.setData(Uri.parse(post.getVideoUrl()));
                    intent.putExtra(ViewVideoActivity.EXTRA_VIDEO_TYPE, ViewVideoActivity.VIDEO_TYPE_IMGUR);
                } else if (post.isRedgifs()) {
                    intent.putExtra(ViewVideoActivity.EXTRA_VIDEO_TYPE, ViewVideoActivity.VIDEO_TYPE_REDGIFS);
                    intent.putExtra(ViewVideoActivity.EXTRA_REDGIFS_ID, post.getRedgifsId());
                    intent.setData(Uri.parse(post.getVideoUrl()));
                    intent.putExtra(ViewVideoActivity.EXTRA_VIDEO_DOWNLOAD_URL, post.getVideoDownloadUrl());
                    /*if (post.isLoadRedgifsOrStreamableVideoSuccess()) {
                        intent.setData(Uri.parse(post.getVideoUrl()));
                        intent.putExtra(ViewVideoActivity.EXTRA_VIDEO_DOWNLOAD_URL, post.getVideoDownloadUrl());
                    }*/
                } else if (post.isStreamable()) {
                    intent.putExtra(ViewVideoActivity.EXTRA_VIDEO_TYPE, ViewVideoActivity.VIDEO_TYPE_STREAMABLE);
                    intent.putExtra(ViewVideoActivity.EXTRA_STREAMABLE_SHORT_CODE, post.getStreamableShortCode());
                    if (post.isLoadedStreamableVideoAlready()) {
                        intent.setData(Uri.parse(post.getVideoUrl()));
                        intent.putExtra(ViewVideoActivity.EXTRA_VIDEO_DOWNLOAD_URL, post.getVideoDownloadUrl());
                    }
                } else if (post.isShortClip()) {
                    ShortClipHostUtils.Host shortClipHost = post.getShortClipHost();
                    intent.putExtra(ViewVideoActivity.EXTRA_VIDEO_TYPE, ViewVideoActivity.VIDEO_TYPE_SHORT_CLIP);
                    intent.putExtra(ViewVideoActivity.EXTRA_SHORT_CLIP_HOST, shortClipHost == null ? null : shortClipHost.name());
                    intent.putExtra(ViewVideoActivity.EXTRA_SHORT_CLIP_ID, post.getShortClipId());
                    // Without a data URI the player resolves for itself; with one it reuses what
                    // the feed already resolved and skips a second round trip.
                    if (post.isLoadedStreamableVideoAlready()) {
                        intent.setData(Uri.parse(post.getVideoUrl()));
                        intent.putExtra(ViewVideoActivity.EXTRA_VIDEO_DOWNLOAD_URL, post.getVideoDownloadUrl());
                    }
                } else if (post.isMlbClip()) {
                    // A direct single-file MP4. Without this it would fall into the branch below,
                    // which means VIDEO_TYPE_NORMAL, and the player builds an HlsMediaSource for
                    // that -- an HLS parser handed an MP4. Tumblr needed the same override.
                    intent.setData(Uri.parse(post.getVideoUrl()));
                    intent.putExtra(ViewVideoActivity.EXTRA_VIDEO_TYPE, ViewVideoActivity.VIDEO_TYPE_DIRECT);
                    intent.putExtra(ViewVideoActivity.EXTRA_VIDEO_DOWNLOAD_URL, post.getVideoDownloadUrl());
                } else {
                    intent.setData(Uri.parse(post.getVideoUrl()));
                    intent.putExtra(ViewVideoActivity.EXTRA_SUBREDDIT, post.getSubredditName());
                    intent.putExtra(ViewVideoActivity.EXTRA_ID, post.getId());
                    intent.putExtra(ViewVideoActivity.EXTRA_VIDEO_DOWNLOAD_URL, post.getVideoDownloadUrl());
                }
                intent.putExtra(ViewVideoActivity.EXTRA_POST, post);
                if (videoProgress > 0) {
                    intent.putExtra(ViewVideoActivity.EXTRA_PROGRESS_SECONDS, videoProgress);
                }
                intent.putExtra(ViewVideoActivity.EXTRA_IS_NSFW, post.isNSFW());
                mActivity.startActivity(intent);
            } else if (post.getPostType() == Post.IMAGE_TYPE) {
                if (peekMedia) {
                    mActivity.setShouldTrackFullscreenMediaPeekTouchEvent(true);
                }

                // An imgchest or imgbb post's url is the album's landing page rather than an image,
                // and it can address twenty of them, so it goes to the album pager. Handing that
                // url to the single-image viewer would hand Glide an HTML document.
                Intent albumIntent = ViewImgurMediaActivity.newImageHostAlbumIntent(mActivity, post);
                if (albumIntent == null) {
                    Intent intent = new Intent(mActivity, ViewImageOrGifActivity.class);
                    intent.putExtra(ViewImageOrGifActivity.EXTRA_IMAGE_URL_KEY, post.getUrl());
                    intent.putExtra(ViewImageOrGifActivity.EXTRA_FILE_NAME_KEY, post.getSubredditName()
                            + "-" + post.getId() + ".jpg");
                    intent.putExtra(ViewImageOrGifActivity.EXTRA_POST_TITLE_KEY, post.getTitle());
                    intent.putExtra(ViewImageOrGifActivity.EXTRA_POST_ID_KEY, post.getId());
                    intent.putExtra(ViewImageOrGifActivity.EXTRA_SUBREDDIT_OR_USERNAME_KEY, post.getSubredditName());
                    intent.putExtra(ViewImageOrGifActivity.EXTRA_IS_NSFW, post.isNSFW());
                    mActivity.startActivity(intent);
                } else {
                    mActivity.startActivity(albumIntent);
                }
            } else if (post.getPostType() == Post.GIF_TYPE) {
                if (peekMedia) {
                    mActivity.setShouldTrackFullscreenMediaPeekTouchEvent(true);
                }

                if (post.getMp4Variant() != null) {
                    Intent intent = new Intent(mActivity, ViewVideoActivity.class);
                    intent.setData(Uri.parse(post.getMp4Variant()));
                    intent.putExtra(ViewVideoActivity.EXTRA_VIDEO_TYPE, ViewVideoActivity.VIDEO_TYPE_DIRECT);
                    intent.putExtra(ViewVideoActivity.EXTRA_SUBREDDIT, post.getSubredditName());
                    intent.putExtra(ViewVideoActivity.EXTRA_ID, post.getId());
                    intent.putExtra(ViewVideoActivity.EXTRA_VIDEO_DOWNLOAD_URL, post.getMp4Variant());
                    intent.putExtra(ViewVideoActivity.EXTRA_POST, post);
                    intent.putExtra(ViewVideoActivity.EXTRA_IS_NSFW, post.isNSFW());
                    mActivity.startActivity(intent);
                } else {
                    Intent intent = new Intent(mActivity, ViewImageOrGifActivity.class);
                    intent.putExtra(ViewImageOrGifActivity.EXTRA_FILE_NAME_KEY, post.getSubredditName()
                            + "-" + post.getId() + ".gif");
                    intent.putExtra(ViewImageOrGifActivity.EXTRA_GIF_URL_KEY, post.getVideoUrl());
                    intent.putExtra(ViewImageOrGifActivity.EXTRA_POST_TITLE_KEY, post.getTitle());
                    intent.putExtra(ViewImageOrGifActivity.EXTRA_POST_ID_KEY, post.getId());
                    intent.putExtra(ViewImageOrGifActivity.EXTRA_SUBREDDIT_OR_USERNAME_KEY, post.getSubredditName());
                    intent.putExtra(ViewImageOrGifActivity.EXTRA_IS_NSFW, post.isNSFW());
                    mActivity.startActivity(intent);
                }
            } else if (post.getPostType() == Post.LINK_TYPE || post.getPostType() == Post.NO_PREVIEW_LINK_TYPE) {
                if (peekMedia) {
                    canStartActivity = true;
                } else {
                    Intent intent = new Intent(mActivity, LinkResolverActivity.class);
                    Uri uri = Uri.parse(post.getUrl());
                    intent.setData(uri);
                    intent.putExtra(LinkResolverActivity.EXTRA_IS_NSFW, post.isNSFW());
                    intent.putExtra(LinkResolverActivity.EXTRA_SUBREDDIT_NAME, post.getSubredditName());
                    intent.putExtra(LinkResolverActivity.EXTRA_POST_TITLE_KEY, post.getTitle());
                    mActivity.startActivity(intent);
                }
            } else if (post.getPostType() == Post.GALLERY_TYPE) {
                if (peekMedia) {
                    mActivity.setShouldTrackFullscreenMediaPeekTouchEvent(true);
                }

                // An image-host album whose page has not been read yet has only its cover tile, so the
                // gallery viewer would show one picture and call it the album. The album viewer reads
                // the page itself, which is the whole point of it.
                if (post.isImageHostAlbum() && !post.isImageHostGalleryResolved()) {
                    Intent albumIntent = ViewImgurMediaActivity.newImageHostAlbumIntent(mActivity, post);
                    if (albumIntent != null) {
                        mActivity.startActivity(albumIntent);
                        return;
                    }
                }
                Intent intent = new Intent(mActivity, ViewRedditGalleryActivity.class);
                intent.putExtra(ViewRedditGalleryActivity.EXTRA_POST, post);
                intent.putExtra(ViewRedditGalleryActivity.EXTRA_GALLERY_ITEM_INDEX, galleryItemIndex);
                mActivity.startActivity(intent);
            } else {
                // Text posts have no media viewer (they can still carry a preview image). Releasing
                // the guard lets the holder's click handler open the post detail instead of stalling.
                canStartActivity = true;
            }
        }
    }

    public void setCanPlayVideo(boolean canPlayVideo) {
        this.canPlayVideo = canPlayVideo;
    }

    /**
     * The feed's column in dp, the pixels an item is laid out across within it, and whether there is
     * more than one column.
     */
    public void provideItemWidth(int widthDp, int widthPx, boolean multiColumn) {
        itemWidth = widthDp;
        itemWidthPx = widthPx;
        this.multiColumn = multiColumn;
    }


    /**
     * Read live rather than cached at construction. These gallery touch listeners disable the
     * activity's pager for the duration of a gesture and restore it afterwards, so a value cached
     * here would overwrite whatever the activity's preference observer had set -- silently undoing
     * the user's "Disable Swiping Between Tabs" choice on the next gallery touch, until the feed
     * was rebuilt.
     */
    private boolean isSwipingBetweenTabsDisabled() {
        return mSharedPreferences != null
                && mSharedPreferences.getBoolean(SharedPreferencesUtils.DISABLE_SWIPING_BETWEEN_TABS, false);
    }


}
