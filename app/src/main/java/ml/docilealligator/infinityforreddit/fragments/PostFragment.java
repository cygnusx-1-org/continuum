package ml.docilealligator.infinityforreddit.fragments;

import static ml.docilealligator.infinityforreddit.videoautoplay.media.PlaybackInfo.INDEX_UNSET;
import static ml.docilealligator.infinityforreddit.videoautoplay.media.PlaybackInfo.TIME_UNSET;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.OnApplyWindowInsetsListener;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.paging.CombinedLoadStates;
import androidx.paging.ItemSnapshotList;
import androidx.paging.LoadState;
import androidx.paging.PagingData;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.transition.AutoTransition;
import androidx.transition.TransitionManager;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Random;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import kotlin.Unit;
import kotlin.jvm.functions.Function1;
import ml.docilealligator.infinityforreddit.Constants;
import ml.docilealligator.infinityforreddit.FetchPostFilterAndConcatenatedSubredditNames;
import ml.docilealligator.infinityforreddit.Infinity;
import ml.docilealligator.infinityforreddit.PostModerationActionHandler;
import ml.docilealligator.infinityforreddit.R;
import ml.docilealligator.infinityforreddit.RecyclerViewContentScrollingInterface;
import ml.docilealligator.infinityforreddit.account.Account;
import ml.docilealligator.infinityforreddit.account.AccountScope;
import ml.docilealligator.infinityforreddit.activities.AccountPostsActivity;
import ml.docilealligator.infinityforreddit.activities.AccountSavedThingActivity;
import ml.docilealligator.infinityforreddit.activities.ActivityToolbarInterface;
import ml.docilealligator.infinityforreddit.activities.CustomizePostFilterActivity;
import ml.docilealligator.infinityforreddit.activities.FilteredPostsActivity;
import ml.docilealligator.infinityforreddit.activities.ViewSubredditDetailActivity;
import ml.docilealligator.infinityforreddit.adapters.Paging3LoadingStateAdapter;
import ml.docilealligator.infinityforreddit.adapters.PostRecyclerViewAdapter;
import ml.docilealligator.infinityforreddit.apis.StreamableAPI;
import ml.docilealligator.infinityforreddit.bottomsheetfragments.FABMoreOptionsBottomSheetFragment;
import ml.docilealligator.infinityforreddit.bottomsheetfragments.FlairBottomSheetFragment;
import ml.docilealligator.infinityforreddit.customviews.LinearLayoutManagerBugFixed;
import ml.docilealligator.infinityforreddit.databinding.FragmentPostBinding;
import ml.docilealligator.infinityforreddit.events.ChangeAnonymousSubredditSubscriptionEvent;
import ml.docilealligator.infinityforreddit.events.ChangeAutoplayVideoControllerUIEvent;
import ml.docilealligator.infinityforreddit.events.ChangeDefaultPostLayoutEvent;
import ml.docilealligator.infinityforreddit.events.ChangeDefaultPostLayoutUnfoldedEvent;
import ml.docilealligator.infinityforreddit.events.ChangeNColumnsEvent;
import ml.docilealligator.infinityforreddit.events.ChangeNetworkStatusEvent;
import ml.docilealligator.infinityforreddit.events.ChangePostHistorySettingsEvent;
import ml.docilealligator.infinityforreddit.events.ChangeSavePostFeedScrolledPositionEvent;
import ml.docilealligator.infinityforreddit.events.FlairSelectedEvent;
import ml.docilealligator.infinityforreddit.events.NeedForPostListFromPostFragmentEvent;
import ml.docilealligator.infinityforreddit.events.PostUpdateEventToPostDetailFragment;
import ml.docilealligator.infinityforreddit.events.PostUpdateEventToPostList;
import ml.docilealligator.infinityforreddit.events.ProvidePostListToViewPostDetailActivityEvent;
import ml.docilealligator.infinityforreddit.post.FeedCache;
import ml.docilealligator.infinityforreddit.post.Post;
import ml.docilealligator.infinityforreddit.post.PostPagingSource;
import ml.docilealligator.infinityforreddit.post.PostType;
import ml.docilealligator.infinityforreddit.post.PostViewModel;
import ml.docilealligator.infinityforreddit.postfilter.PostFilter;
import ml.docilealligator.infinityforreddit.postfilter.PostFilterUsage;
import ml.docilealligator.infinityforreddit.randomsubreddit.RandomSubredditNames;
import ml.docilealligator.infinityforreddit.randomsubreddit.RandomSubredditRepository;
import ml.docilealligator.infinityforreddit.readpost.ReadPostType;
import ml.docilealligator.infinityforreddit.readpost.ReadPostsList;
import ml.docilealligator.infinityforreddit.readpost.ReadPostsListInterface;
import ml.docilealligator.infinityforreddit.resume.FeedResumeState;
import ml.docilealligator.infinityforreddit.resume.ResumeState;
import ml.docilealligator.infinityforreddit.resume.ScrollAnchor;
import ml.docilealligator.infinityforreddit.thing.SortType;
import ml.docilealligator.infinityforreddit.user.UserProfileImagesBatchLoader;
import ml.docilealligator.infinityforreddit.utils.APIUtils;
import ml.docilealligator.infinityforreddit.utils.SharedPreferencesLiveDataKt;
import ml.docilealligator.infinityforreddit.utils.SharedPreferencesUtils;
import ml.docilealligator.infinityforreddit.utils.Utils;
import ml.docilealligator.infinityforreddit.videoautoplay.ExoCreator;
import ml.docilealligator.infinityforreddit.videoautoplay.media.PlaybackInfo;
import ml.docilealligator.infinityforreddit.videoautoplay.media.VolumeInfo;
import okhttp3.OkHttpClient;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.jetbrains.annotations.NotNull;
import retrofit2.Retrofit;


/**
 * A simple {@link PostFragmentBase} subclass.
 */
public class PostFragment extends PostFragmentBase implements FragmentCommunicator, PostModerationActionHandler {

    public static final String EXTRA_NAME = "EN";
    public static final String EXTRA_USER_NAME = "EUN";
    public static final String EXTRA_USER_WHERE = "EUW";
    public static final String EXTRA_QUERY = "EQ";
    public static final String EXTRA_TRENDING_SOURCE = "ETS";
    public static final String EXTRA_POST_TYPE = "EPT";
    public static final String EXTRA_FILTER = "EF";
    public static final String EXTRA_DISABLE_READ_POSTS = "EDRP";
    // Sort carried by an opening deep link (e.g. reddit.com/r/x/top), as SortType.Type/Time names.
    // Applied once on fresh creation; overrides the saved/default sort for that launch only.
    /**
     * Names the host when more than one shows the same listing. See {@link #buildFeedKey()}; hosts
     * that are the only place a listing appears leave it unset.
     */
    public static final String EXTRA_RESUME_FEED_SCOPE = "ERFS";
    public static final String EXTRA_INITIAL_SORT_TYPE = "EIST";
    public static final String EXTRA_INITIAL_SORT_TIME = "EISTM";

    private static final String IS_IN_LAZY_MODE_STATE = "IILMS";
    private static final String SORT_TYPE_STATE = "STS";
    private static final String SORT_TIME_STATE = "STMS";
    private static final String RECYCLER_VIEW_POSITION_STATE = "RVPS";
    private static final String RECYCLER_VIEW_POSITION_OFFSET_STATE = "RVPOS";
    private static final String RECYCLER_VIEW_USER_ANCHOR_STATE = "RVUA";
    private static final String RECYCLER_VIEW_USER_ANCHOR_OFFSET_STATE = "RVUAO";
    private static final String POST_FILTER_STATE = "PFS";
    private static final String CONCATENATED_SUBREDDIT_NAMES_STATE = "CSNS";
    private static final String POST_FRAGMENT_ID_STATE = "PFIS";
    // How long a resume may keep the list hidden waiting for its posts. Longer than the scroll
    // backstop in ScrollAnchor because this one spans a disk read and a parse, and on a cache miss
    // a whole network fetch.
    private static final long RESUME_REVEAL_TIMEOUT_MS = 8000L;
    // Least time between two writes of the resume cache while the user is scrolling. The write is a
    // JSON serialization of every loaded post, so it is worth doing often enough to survive a kill
    // and no oftener.
    private static final long RESUME_STORE_THROTTLE_MS = 5000L;

    // The user's "intended" anchor position + offset, persisted across rotation round-trips.
    // The offset is sticky: it's only re-captured when the anchor item itself changes (the
    // user scrolled away). This preserves the original offset through the lossy multi-col
    // intermediate, where StaggeredGridLayoutManager's gap-handling snaps the anchor to the
    // top and would otherwise overwrite the good offset with the snapped one.
    private int userAnchorPos = RecyclerView.NO_POSITION;
    private int userAnchorOffset = 0;

    /**
     * Non-null when this feed is a random tab -- the pseudo-name it was created with
     * ("random"/"randnsfw"/"myrandom"). It stays the key for this tab's sort type, post layout and
     * post filter, so those survive a re-roll; {@link #currentRandomSubreddit} is the subreddit
     * actually being shown underneath it.
     */
    @Nullable
    private String randomSubredditPseudoName;
    /** The subreddit a random tab is currently showing. Null until the first roll lands. */
    @Nullable
    private String currentRandomSubreddit;

    @SuppressWarnings("NullAway.Init")
    PostViewModel mPostViewModel;
    @Inject
    RandomSubredditRepository mRandomSubredditRepository;
    @Inject
    @Named("redgifs")
    Retrofit mRedgifsRetrofit;
    @Inject
    Provider<StreamableAPI> mStreamableApiProvider;
    @Inject
    @Named("short_clip")
    OkHttpClient mShortClipOkHttpClient;
    @Inject
    @Named("current_account")
    SharedPreferences mCurrentAccountSharedPreferences;
    @Inject
    @Named("sort_type")
    SharedPreferences mSortTypeSharedPreferences;
    @Inject
    @Named("post_layout")
    SharedPreferences mPostLayoutSharedPreferences;
    @Inject
    @Named("nsfw_and_spoiler")
    SharedPreferences mNsfwAndSpoilerSharedPreferences;
    @Inject
    @Named("post_history")
    SharedPreferences mPostHistorySharedPreferences;
    @Inject
    @Named("post_feed_scrolled_position_cache")
    SharedPreferences mPostFeedScrolledPositionSharedPreferences;
    @Inject
    ExoCreator mExoCreator;
    @Inject
    UserProfileImagesBatchLoader loader;
    @PostType
    private int postType;
    private boolean savePostFeedScrolledPosition;
    private PostRecyclerViewAdapter mAdapter;
    @Nullable
    private String subredditName;
    @Nullable
    private String username;
    @Nullable
    private String query;
    @Nullable
    private String trendingSource;
    @Nullable
    private String where;
    @Nullable
    private String multiRedditPath;
    @Nullable
    private String concatenatedSubredditNames;
    private int maxPosition = -1;
    // Resume where I left off. resumeFeedKey is non-null only while the setting is on, and is what
    // makes this feed both readable from and writable to the resume cache; resumeState carries the
    // pending restore handed down in the fragment arguments, and is spent by the first load.
    private final FeedResumeState resumeState = new FeedResumeState();
    // Whether a restore is still to be acted on. Separate from resumeState because the scroll
    // restore is set up early in onCreateView and the view model is told much later in the same
    // pass: one flag read by both, cleared only once both have had it.
    private boolean resumePending;
    private boolean resumeWhereILeftOff;
    @Nullable
    private String resumeFeedKey;
    @Nullable
    private String resumeFeedScope;
    private long lastResumeStoreAt;
    private final Runnable resumeStoreRunnable = this::storeResumeCache;
    private SortType sortType;
    @Nullable
    private PostFilter postFilter;
    private ReadPostsListInterface readPostsList;
    private FragmentPostBinding binding;

    public PostFragment() {
        // Required empty public constructor
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mAdapter != null) {
            mAdapter.setCanStartActivity(true);
        }
        if (isInLazyMode) {
            resumeLazyMode(false);
        }
        if (mAdapter != null && binding.recyclerViewPostFragment != null) {
            binding.recyclerViewPostFragment.onWindowVisibilityChanged(View.VISIBLE);
        }
    }

    @Override
    protected boolean scrollPostsByCount(int count) {
        if (mLinearLayoutManager != null) {
            int pos = mLinearLayoutManager.findFirstVisibleItemPosition();
            int targetPosition = pos + count;
            mLinearLayoutManager.scrollToPositionWithOffset(targetPosition, 0);
            return true;
        } else {
            return false;
        }
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        binding = FragmentPostBinding.inflate(inflater, container, false);

        ((Infinity) mActivity.getApplication()).getAppComponent().inject(this);

        super.onCreateView(inflater, container, savedInstanceState);

        setHasOptionsMenu(true);

        applyTheme();

        if (mActivity.isImmersiveInterfaceRespectForcedEdgeToEdge()) {
            ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), new OnApplyWindowInsetsListener() {
                @NonNull
                @Override
                public WindowInsetsCompat onApplyWindowInsets(@NonNull View v, @NonNull WindowInsetsCompat insets) {
                    Insets allInsets = Utils.getInsets(insets, false, mActivity.isForcedImmersiveInterface());
                    getPostRecyclerView().setPadding(
                            0, 0, 0, allInsets.bottom
                    );
                    return WindowInsetsCompat.CONSUMED;
                }
            });
        }

        binding.recyclerViewPostFragment.addOnWindowFocusChangedListener(this::onWindowFocusChanged);

        Resources resources = getResources();

        binding.swipeRefreshLayoutPostFragment.setEnabled(mSharedPreferences.getBoolean(SharedPreferencesUtils.PULL_TO_REFRESH, true));
        binding.swipeRefreshLayoutPostFragment.setOnRefreshListener(this::refresh);

        int recyclerViewPosition;
        int recyclerViewPositionOffset;
        if (savedInstanceState != null) {
            recyclerViewPosition = savedInstanceState.getInt(RECYCLER_VIEW_POSITION_STATE);
            recyclerViewPositionOffset = savedInstanceState.getInt(RECYCLER_VIEW_POSITION_OFFSET_STATE);
            userAnchorPos = savedInstanceState.getInt(
                    RECYCLER_VIEW_USER_ANCHOR_STATE, RecyclerView.NO_POSITION);
            userAnchorOffset = savedInstanceState.getInt(RECYCLER_VIEW_USER_ANCHOR_OFFSET_STATE, 0);

            isInLazyMode = savedInstanceState.getBoolean(IS_IN_LAZY_MODE_STATE);
            postFilter = savedInstanceState.getParcelable(POST_FILTER_STATE);
            concatenatedSubredditNames = savedInstanceState.getString(CONCATENATED_SUBREDDIT_NAMES_STATE);
            postFragmentId = savedInstanceState.getLong(POST_FRAGMENT_ID_STATE);
        } else {
            recyclerViewPosition = 0;
            recyclerViewPositionOffset = 0;
            userAnchorPos = RecyclerView.NO_POSITION;
            userAnchorOffset = 0;
            postFilter = getArguments().getParcelable(EXTRA_FILTER);
            postFragmentId = System.currentTimeMillis() + new Random().nextInt(1000);
        }

        readPostsList = new ReadPostsList(mRedditDataRoomDatabase.readPostDao(), mActivity.accountName,
                getArguments().getBoolean(EXTRA_DISABLE_READ_POSTS, false));

        if (mActivity instanceof RecyclerViewContentScrollingInterface) {
            binding.recyclerViewPostFragment.addOnScrollListener(new RecyclerView.OnScrollListener() {
                @Override
                public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                    if (dy > 0) {
                        ((RecyclerViewContentScrollingInterface) mActivity).contentScrollDown();
                    } else if (dy < 0) {
                        ((RecyclerViewContentScrollingInterface) mActivity).contentScrollUp();
                    }
                }
            });
        }

        // Keep the resume cache current as the user reads, so that a process killed without
        // lifecycle callbacks -- dismissal from recents -- still leaves an accurate position on
        // disk. No-op while the setting is off, since resumeFeedKey stays null.
        binding.recyclerViewPostFragment.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                if (newState != RecyclerView.SCROLL_STATE_IDLE || resumeFeedKey == null) {
                    return;
                }
                recyclerView.removeCallbacks(resumeStoreRunnable);
                long since = SystemClock.uptimeMillis() - lastResumeStoreAt;
                if (since >= RESUME_STORE_THROTTLE_MS) {
                    storeResumeCache();
                } else {
                    // Trailing edge, not just leading: the last time the list comes to rest is the
                    // position worth having, and dropping it because a write happened three seconds
                    // ago records where the user was passing through rather than where they stopped.
                    recyclerView.postDelayed(resumeStoreRunnable, RESUME_STORE_THROTTLE_MS - since);
                }
            }
        });

        postType = getArguments().getInt(EXTRA_POST_TYPE);

        int defaultPostLayout;
        boolean foldEnabled = mSharedPreferences.getBoolean(SharedPreferencesUtils.ENABLE_FOLD_SUPPORT, false);
        boolean isTablet = getResources().getBoolean(R.bool.isTablet);
        if (foldEnabled && isTablet) {
            defaultPostLayout = SharedPreferencesUtils.getInt(mSharedPreferences,
                    SharedPreferencesUtils.DEFAULT_POST_LAYOUT_UNFOLDED_KEY, "0");
        } else {
            defaultPostLayout = SharedPreferencesUtils.getInt(mSharedPreferences,
                    SharedPreferencesUtils.DEFAULT_POST_LAYOUT_KEY, "0");
        }
        savePostFeedScrolledPosition = mSharedPreferences.getBoolean(SharedPreferencesUtils.SAVE_FRONT_PAGE_SCROLLED_POSITION, false);
        resumeWhereILeftOff = mSharedPreferences.getBoolean(SharedPreferencesUtils.RESUME_WHERE_I_LEFT_OFF, false);
        resumeFeedScope = getArguments().getString(EXTRA_RESUME_FEED_SCOPE);
        if (resumeWhereILeftOff && savedInstanceState == null) {
            // Only on a fresh creation: across a rotation the fragment's own saved state already
            // holds a newer position than the arguments do.
            resumeState.read(getArguments());
            resumePending = resumeState.isPending();
        }
        Locale locale = resources.getConfiguration().locale;

        int usage;
        String nameOfUsage;

        if (postType == PostType.SEARCH) {
            subredditName = getArguments().getString(EXTRA_NAME);
            query = getArguments().getString(EXTRA_QUERY);
            trendingSource = getArguments().getString(EXTRA_TRENDING_SOURCE);
            if (savedInstanceState == null) {
                postFragmentId += Objects.requireNonNull(query).hashCode();
            }

            usage = PostFilterUsage.SEARCH_TYPE;
            nameOfUsage = PostFilterUsage.NO_USAGE;

            SortType overrideSortType = getOverrideSortType(savedInstanceState);
            if (overrideSortType != null) {
                sortType = overrideSortType;
            } else {
                String sort = mSortTypeSharedPreferences.getString(SharedPreferencesUtils.SORT_TYPE_SEARCH_POST, SortType.Type.RELEVANCE.name());
                String sortTime = mSortTypeSharedPreferences.getString(SharedPreferencesUtils.SORT_TIME_SEARCH_POST, SortType.Time.ALL.name());
                sortType = new SortType(SortType.Type.valueOf(Objects.requireNonNull(sort)), SortType.Time.valueOf(Objects.requireNonNull(sortTime)));
            }
            postLayout = mPostLayoutSharedPreferences.getInt(SharedPreferencesUtils.POST_LAYOUT_SEARCH_POST, defaultPostLayout);

            mAdapter = new PostRecyclerViewAdapter(mActivity, this, mRedditDataRoomDatabase, mExecutor,
                    mOauthRetrofit, mRedgifsRetrofit, mStreamableApiProvider, mShortClipOkHttpClient,
                    mCustomThemeWrapper, locale,
                    mActivity.accessToken, mActivity.accountName, postType, postLayout, true,
                    mSharedPreferences, mCurrentAccountSharedPreferences, mNsfwAndSpoilerSharedPreferences, mPostHistorySharedPreferences,
                    mExoCreator, new PostRecyclerViewAdapter.Callback() {
                @Override
                public void typeChipClicked(int filter) {
                    Intent intent = new Intent(mActivity, FilteredPostsActivity.class);
                    intent.putExtra(FilteredPostsActivity.EXTRA_NAME, subredditName);
                    intent.putExtra(FilteredPostsActivity.EXTRA_QUERY, query);
                    intent.putExtra(FilteredPostsActivity.EXTRA_TRENDING_SOURCE, trendingSource);
                    intent.putExtra(FilteredPostsActivity.EXTRA_POST_TYPE, postType);
                    intent.putExtra(FilteredPostsActivity.EXTRA_POST_TYPE_FILTER, filter);
                    startActivity(intent);
                }

                @Override
                public void flairChipClicked(String flair) {
                    Intent intent = new Intent(mActivity, FilteredPostsActivity.class);
                    intent.putExtra(FilteredPostsActivity.EXTRA_NAME, subredditName);
                    intent.putExtra(FilteredPostsActivity.EXTRA_QUERY, query);
                    intent.putExtra(FilteredPostsActivity.EXTRA_TRENDING_SOURCE, trendingSource);
                    intent.putExtra(FilteredPostsActivity.EXTRA_POST_TYPE, postType);
                    intent.putExtra(FilteredPostsActivity.EXTRA_CONTAIN_FLAIR, flair);
                    startActivity(intent);
                }

                @Override
                public void nsfwChipClicked() {
                    Intent intent = new Intent(mActivity, FilteredPostsActivity.class);
                    intent.putExtra(FilteredPostsActivity.EXTRA_NAME, subredditName);
                    intent.putExtra(FilteredPostsActivity.EXTRA_QUERY, query);
                    intent.putExtra(FilteredPostsActivity.EXTRA_TRENDING_SOURCE, trendingSource);
                    intent.putExtra(FilteredPostsActivity.EXTRA_POST_TYPE, postType);
                    intent.putExtra(FilteredPostsActivity.EXTRA_POST_TYPE_FILTER, Post.NSFW_TYPE);
                    startActivity(intent);
                }

                @Override
                public void currentlyBindItem(int position) {
                    if (maxPosition < position) {
                        maxPosition = position;
                    }
                }

                @Override
                public void delayTransition() {
                    TransitionManager.beginDelayedTransition(binding.recyclerViewPostFragment, new AutoTransition());
                }
            });
        } else if (postType == PostType.SUBREDDIT) {
            subredditName = getArguments().getString(EXTRA_NAME);
            // A tab named random/randnsfw/myrandom is a feed that re-rolls rather than a subreddit.
            randomSubredditPseudoName = RandomSubredditNames.canonicalise(subredditName);
            if (savedInstanceState == null) {
                postFragmentId += Objects.requireNonNull(subredditName).hashCode();
            }

            usage = PostFilterUsage.SUBREDDIT_TYPE;
            nameOfUsage = subredditName;

            SortType overrideSortType = getOverrideSortType(savedInstanceState);
            if (overrideSortType != null) {
                sortType = overrideSortType;
            } else {
                String sort = mSortTypeSharedPreferences.getString(SharedPreferencesUtils.SORT_TYPE_SUBREDDIT_POST_BASE + subredditName,
                        mSharedPreferences.getString(SharedPreferencesUtils.SUBREDDIT_DEFAULT_SORT_TYPE, SortType.Type.HOT.name()));
                String sortTime = null;
                if (SortType.Type.CONTROVERSIAL.name().equals(sort) || SortType.Type.TOP.name().equals(sort)) {
                    sortTime = mSortTypeSharedPreferences.getString(SharedPreferencesUtils.SORT_TIME_SUBREDDIT_POST_BASE + subredditName,
                            mSharedPreferences.getString(SharedPreferencesUtils.SUBREDDIT_DEFAULT_SORT_TIME, SortType.Time.ALL.name()));
                }
                if (sortTime != null) {
                    sortType = new SortType(SortType.Type.valueOf(Objects.requireNonNull(sort)), SortType.Time.valueOf(Objects.requireNonNull(sortTime)));
                } else {
                    sortType = new SortType(SortType.Type.valueOf(Objects.requireNonNull(sort)));
                }
            }
            // A random tab shows a different subreddit after every refresh, so each post names its
            // own -- the same reason the firehose feeds do.
            boolean displaySubredditName = Constants.isFirehoseSubreddit(subredditName)
                    || randomSubredditPseudoName != null;
            postLayout = mPostLayoutSharedPreferences.getInt(SharedPreferencesUtils.POST_LAYOUT_SUBREDDIT_POST_BASE + subredditName, defaultPostLayout);

            mAdapter = new PostRecyclerViewAdapter(mActivity, this, mRedditDataRoomDatabase, mExecutor,
                    mOauthRetrofit, mRedgifsRetrofit, mStreamableApiProvider, mShortClipOkHttpClient,
                    mCustomThemeWrapper, locale,
                    mActivity.accessToken, mActivity.accountName, postType, postLayout, displaySubredditName,
                    mSharedPreferences, mCurrentAccountSharedPreferences, mNsfwAndSpoilerSharedPreferences, mPostHistorySharedPreferences,
                    mExoCreator, new PostRecyclerViewAdapter.Callback() {
                @Override
                public void typeChipClicked(int filter) {
                    Intent intent = new Intent(mActivity, FilteredPostsActivity.class);
                    intent.putExtra(FilteredPostsActivity.EXTRA_NAME, subredditName);
                    intent.putExtra(FilteredPostsActivity.EXTRA_POST_TYPE, postType);
                    intent.putExtra(FilteredPostsActivity.EXTRA_POST_TYPE_FILTER, filter);
                    startActivity(intent);
                }

                @Override
                public void flairChipClicked(String flair) {
                    Intent intent = new Intent(mActivity, FilteredPostsActivity.class);
                    intent.putExtra(FilteredPostsActivity.EXTRA_NAME, subredditName);
                    intent.putExtra(FilteredPostsActivity.EXTRA_POST_TYPE, postType);
                    intent.putExtra(FilteredPostsActivity.EXTRA_CONTAIN_FLAIR, flair);
                    startActivity(intent);
                }

                @Override
                public void nsfwChipClicked() {
                    Intent intent = new Intent(mActivity, FilteredPostsActivity.class);
                    intent.putExtra(FilteredPostsActivity.EXTRA_NAME, subredditName);
                    intent.putExtra(FilteredPostsActivity.EXTRA_POST_TYPE, postType);
                    intent.putExtra(FilteredPostsActivity.EXTRA_POST_TYPE_FILTER, Post.NSFW_TYPE);
                    startActivity(intent);
                }

                @Override
                public void currentlyBindItem(int position) {
                    if (maxPosition < position) {
                        maxPosition = position;
                    }
                }

                @Override
                public void delayTransition() {
                    TransitionManager.beginDelayedTransition(binding.recyclerViewPostFragment, new AutoTransition());
                }
            });
        } else if (postType == PostType.MULTIREDDIT) {
            multiRedditPath = getArguments().getString(EXTRA_NAME);
            query = getArguments().getString(EXTRA_QUERY);
            if (savedInstanceState == null) {
                postFragmentId += Objects.requireNonNull(multiRedditPath).hashCode() + (query == null ? 0 : query.hashCode());
            }

            usage = PostFilterUsage.MULTIREDDIT_TYPE;
            nameOfUsage = multiRedditPath;

            SortType overrideSortType = getOverrideSortType(savedInstanceState);
            if (overrideSortType != null) {
                sortType = overrideSortType;
            } else {
                String sort = mSortTypeSharedPreferences.getString(SharedPreferencesUtils.SORT_TYPE_MULTI_REDDIT_POST_BASE + multiRedditPath,
                        SortType.Type.HOT.name());
                String sortTime = null;
                if (SortType.Type.CONTROVERSIAL.name().equals(sort) || SortType.Type.TOP.name().equals(sort)) {
                    sortTime = mSortTypeSharedPreferences.getString(SharedPreferencesUtils.SORT_TIME_MULTI_REDDIT_POST_BASE + multiRedditPath,
                            SortType.Time.ALL.name());
                }
                if (sortTime != null) {
                    sortType = new SortType(SortType.Type.valueOf(Objects.requireNonNull(sort)), SortType.Time.valueOf(Objects.requireNonNull(sortTime)));
                } else {
                    sortType = new SortType(SortType.Type.valueOf(Objects.requireNonNull(sort)));
                }
            }
            postLayout = mPostLayoutSharedPreferences.getInt(SharedPreferencesUtils.POST_LAYOUT_MULTI_REDDIT_POST_BASE + multiRedditPath,
                    defaultPostLayout);

            mAdapter = new PostRecyclerViewAdapter(mActivity, this, mRedditDataRoomDatabase, mExecutor,
                    mOauthRetrofit, mRedgifsRetrofit, mStreamableApiProvider, mShortClipOkHttpClient,
                    mCustomThemeWrapper, locale,
                    mActivity.accessToken, mActivity.accountName, postType, postLayout, true,
                    mSharedPreferences, mCurrentAccountSharedPreferences, mNsfwAndSpoilerSharedPreferences, mPostHistorySharedPreferences,
                    mExoCreator, new PostRecyclerViewAdapter.Callback() {
                @Override
                public void typeChipClicked(int filter) {
                    Intent intent = new Intent(mActivity, FilteredPostsActivity.class);
                    intent.putExtra(FilteredPostsActivity.EXTRA_NAME, multiRedditPath);
                    intent.putExtra(FilteredPostsActivity.EXTRA_QUERY, query);
                    intent.putExtra(FilteredPostsActivity.EXTRA_POST_TYPE, postType);
                    intent.putExtra(FilteredPostsActivity.EXTRA_POST_TYPE_FILTER, filter);
                    startActivity(intent);
                }

                @Override
                public void flairChipClicked(String flair) {
                    Intent intent = new Intent(mActivity, FilteredPostsActivity.class);
                    intent.putExtra(FilteredPostsActivity.EXTRA_NAME, multiRedditPath);
                    intent.putExtra(FilteredPostsActivity.EXTRA_QUERY, query);
                    intent.putExtra(FilteredPostsActivity.EXTRA_POST_TYPE, postType);
                    intent.putExtra(FilteredPostsActivity.EXTRA_CONTAIN_FLAIR, flair);
                    startActivity(intent);
                }

                @Override
                public void nsfwChipClicked() {
                    Intent intent = new Intent(mActivity, FilteredPostsActivity.class);
                    intent.putExtra(FilteredPostsActivity.EXTRA_NAME, multiRedditPath);
                    intent.putExtra(FilteredPostsActivity.EXTRA_QUERY, query);
                    intent.putExtra(FilteredPostsActivity.EXTRA_POST_TYPE, postType);
                    intent.putExtra(FilteredPostsActivity.EXTRA_POST_TYPE_FILTER, Post.NSFW_TYPE);
                    startActivity(intent);
                }

                @Override
                public void currentlyBindItem(int position) {
                    if (maxPosition < position) {
                        maxPosition = position;
                    }
                }

                @Override
                public void delayTransition() {
                    TransitionManager.beginDelayedTransition(binding.recyclerViewPostFragment, new AutoTransition());
                }
            });
        } else if (postType == PostType.USER) {
            username = getArguments().getString(EXTRA_USER_NAME);
            where = getArguments().getString(EXTRA_USER_WHERE);
            if (savedInstanceState == null) {
                postFragmentId += Objects.requireNonNull(username).hashCode();
            }

            usage = PostFilterUsage.USER_TYPE;
            nameOfUsage = username;

            SortType overrideSortType = getOverrideSortType(savedInstanceState);
            if (overrideSortType != null) {
                sortType = overrideSortType;
            } else {
                String sort = mSortTypeSharedPreferences.getString(SharedPreferencesUtils.SORT_TYPE_USER_POST_BASE + username,
                        mSharedPreferences.getString(SharedPreferencesUtils.USER_DEFAULT_SORT_TYPE, SortType.Type.NEW.name()));
                if (SortType.Type.CONTROVERSIAL.name().equals(sort) || SortType.Type.TOP.name().equals(sort)) {
                    String sortTime = mSortTypeSharedPreferences.getString(SharedPreferencesUtils.SORT_TIME_USER_POST_BASE + username,
                            mSharedPreferences.getString(SharedPreferencesUtils.USER_DEFAULT_SORT_TIME, SortType.Time.ALL.name()));
                    sortType = new SortType(SortType.Type.valueOf(Objects.requireNonNull(sort)), SortType.Time.valueOf(Objects.requireNonNull(sortTime)));
                } else {
                    sortType = new SortType(SortType.Type.valueOf(Objects.requireNonNull(sort)));
                }
            }
            postLayout = mPostLayoutSharedPreferences.getInt(SharedPreferencesUtils.POST_LAYOUT_USER_POST_BASE + username, defaultPostLayout);

            mAdapter = new PostRecyclerViewAdapter(mActivity, this, mRedditDataRoomDatabase, mExecutor,
                    mOauthRetrofit, mRedgifsRetrofit, mStreamableApiProvider, mShortClipOkHttpClient,
                    mCustomThemeWrapper, locale,
                    mActivity.accessToken, mActivity.accountName, postType, postLayout, true,
                    mSharedPreferences, mCurrentAccountSharedPreferences, mNsfwAndSpoilerSharedPreferences, mPostHistorySharedPreferences,
                    mExoCreator, new PostRecyclerViewAdapter.Callback() {
                @Override
                public void typeChipClicked(int filter) {
                    Intent intent = new Intent(mActivity, FilteredPostsActivity.class);
                    intent.putExtra(FilteredPostsActivity.EXTRA_NAME, username);
                    intent.putExtra(FilteredPostsActivity.EXTRA_POST_TYPE, postType);
                    intent.putExtra(FilteredPostsActivity.EXTRA_USER_WHERE, where);
                    intent.putExtra(FilteredPostsActivity.EXTRA_POST_TYPE_FILTER, filter);
                    startActivity(intent);
                }

                @Override
                public void flairChipClicked(String flair) {
                    Intent intent = new Intent(mActivity, FilteredPostsActivity.class);
                    intent.putExtra(FilteredPostsActivity.EXTRA_NAME, username);
                    intent.putExtra(FilteredPostsActivity.EXTRA_POST_TYPE, postType);
                    intent.putExtra(FilteredPostsActivity.EXTRA_USER_WHERE, where);
                    intent.putExtra(FilteredPostsActivity.EXTRA_CONTAIN_FLAIR, flair);
                    startActivity(intent);
                }

                @Override
                public void nsfwChipClicked() {
                    Intent intent = new Intent(mActivity, FilteredPostsActivity.class);
                    intent.putExtra(FilteredPostsActivity.EXTRA_NAME, username);
                    intent.putExtra(FilteredPostsActivity.EXTRA_POST_TYPE, postType);
                    intent.putExtra(FilteredPostsActivity.EXTRA_USER_WHERE, where);
                    intent.putExtra(FilteredPostsActivity.EXTRA_POST_TYPE_FILTER, Post.NSFW_TYPE);
                    startActivity(intent);
                }

                @Override
                public void currentlyBindItem(int position) {
                    if (maxPosition < position) {
                        maxPosition = position;
                    }
                }

                @Override
                public void delayTransition() {
                    TransitionManager.beginDelayedTransition(binding.recyclerViewPostFragment, new AutoTransition());
                }
            });
        } else if (postType == PostType.ANONYMOUS_FRONT_PAGE) {
            usage = PostFilterUsage.HOME_TYPE;
            nameOfUsage = PostFilterUsage.NO_USAGE;

            String sort = mSortTypeSharedPreferences.getString(SharedPreferencesUtils.SORT_TYPE_ANONYMOUS_FRONT_PAGE_POST, SortType.Type.HOT.name());
            if (SortType.Type.CONTROVERSIAL.name().equals(sort) || SortType.Type.TOP.name().equals(sort)) {
                String sortTime = mSortTypeSharedPreferences.getString(SharedPreferencesUtils.SORT_TIME_ANONYMOUS_FRONT_PAGE_POST, SortType.Time.ALL.name());
                sortType = new SortType(SortType.Type.valueOf(Objects.requireNonNull(sort)), SortType.Time.valueOf(Objects.requireNonNull(sortTime)));
            } else {
                sortType = new SortType(SortType.Type.valueOf(Objects.requireNonNull(sort)));
            }

            postLayout = mPostLayoutSharedPreferences.getInt(SharedPreferencesUtils.POST_LAYOUT_FRONT_PAGE_POST, defaultPostLayout);

            mAdapter = new PostRecyclerViewAdapter(mActivity, this, mRedditDataRoomDatabase, mExecutor,
                    mOauthRetrofit, mRedgifsRetrofit, mStreamableApiProvider, mShortClipOkHttpClient,
                    mCustomThemeWrapper, locale,
                    mActivity.accessToken, mActivity.accountName, postType, postLayout, true,
                    mSharedPreferences, mCurrentAccountSharedPreferences, mNsfwAndSpoilerSharedPreferences, mPostHistorySharedPreferences,
                    mExoCreator, new PostRecyclerViewAdapter.Callback() {
                @Override
                public void typeChipClicked(int filter) {
                    Intent intent = new Intent(mActivity, FilteredPostsActivity.class);
                    intent.putExtra(FilteredPostsActivity.EXTRA_POST_TYPE, postType);
                    intent.putExtra(FilteredPostsActivity.EXTRA_POST_TYPE_FILTER, filter);
                    startActivity(intent);
                }

                @Override
                public void flairChipClicked(String flair) {
                    Intent intent = new Intent(mActivity, FilteredPostsActivity.class);
                    intent.putExtra(FilteredPostsActivity.EXTRA_POST_TYPE, postType);
                    intent.putExtra(FilteredPostsActivity.EXTRA_CONTAIN_FLAIR, flair);
                    startActivity(intent);
                }

                @Override
                public void nsfwChipClicked() {
                    Intent intent = new Intent(mActivity, FilteredPostsActivity.class);
                    intent.putExtra(FilteredPostsActivity.EXTRA_POST_TYPE, postType);
                    intent.putExtra(FilteredPostsActivity.EXTRA_POST_TYPE_FILTER, Post.NSFW_TYPE);
                    startActivity(intent);
                }

                @Override
                public void currentlyBindItem(int position) {
                    if (maxPosition < position) {
                        maxPosition = position;
                    }
                }

                @Override
                public void delayTransition() {
                    TransitionManager.beginDelayedTransition(binding.recyclerViewPostFragment, new AutoTransition());
                }
            });
        } else if (postType == PostType.ANONYMOUS_MULTIREDDIT) {
            multiRedditPath = getArguments().getString(EXTRA_NAME);
            if (savedInstanceState == null) {
                postFragmentId += Objects.requireNonNull(multiRedditPath).hashCode();
            }

            usage = PostFilterUsage.MULTIREDDIT_TYPE;
            nameOfUsage = multiRedditPath;

            SortType overrideSortType = getOverrideSortType(savedInstanceState);
            if (overrideSortType != null) {
                sortType = overrideSortType;
            } else {
                String sort = mSortTypeSharedPreferences.getString(SharedPreferencesUtils.SORT_TYPE_MULTI_REDDIT_POST_BASE + multiRedditPath, SortType.Type.HOT.name());
                if (SortType.Type.CONTROVERSIAL.name().equals(sort) || SortType.Type.TOP.name().equals(sort)) {
                    String sortTime = mSortTypeSharedPreferences.getString(SharedPreferencesUtils.SORT_TIME_MULTI_REDDIT_POST_BASE + multiRedditPath, SortType.Time.ALL.name());
                    sortType = new SortType(SortType.Type.valueOf(Objects.requireNonNull(sort)), SortType.Time.valueOf(Objects.requireNonNull(sortTime)));
                } else {
                    sortType = new SortType(SortType.Type.valueOf(Objects.requireNonNull(sort)));
                }
            }

            postLayout = mPostLayoutSharedPreferences.getInt(SharedPreferencesUtils.POST_LAYOUT_MULTI_REDDIT_POST_BASE + multiRedditPath, defaultPostLayout);

            mAdapter = new PostRecyclerViewAdapter(mActivity, this, mRedditDataRoomDatabase, mExecutor,
                    mOauthRetrofit, mRedgifsRetrofit, mStreamableApiProvider, mShortClipOkHttpClient,
                    mCustomThemeWrapper, locale,
                    mActivity.accessToken, mActivity.accountName, postType, postLayout, true,
                    mSharedPreferences, mCurrentAccountSharedPreferences, mNsfwAndSpoilerSharedPreferences, mPostHistorySharedPreferences,
                    mExoCreator, new PostRecyclerViewAdapter.Callback() {
                @Override
                public void typeChipClicked(int filter) {
                    Intent intent = new Intent(mActivity, FilteredPostsActivity.class);
                    intent.putExtra(FilteredPostsActivity.EXTRA_NAME, multiRedditPath);
                    intent.putExtra(FilteredPostsActivity.EXTRA_POST_TYPE, postType);
                    intent.putExtra(FilteredPostsActivity.EXTRA_POST_TYPE_FILTER, filter);
                    startActivity(intent);
                }

                @Override
                public void flairChipClicked(String flair) {
                    Intent intent = new Intent(mActivity, FilteredPostsActivity.class);
                    intent.putExtra(FilteredPostsActivity.EXTRA_NAME, multiRedditPath);
                    intent.putExtra(FilteredPostsActivity.EXTRA_POST_TYPE, postType);
                    intent.putExtra(FilteredPostsActivity.EXTRA_CONTAIN_FLAIR, flair);
                    startActivity(intent);
                }

                @Override
                public void nsfwChipClicked() {
                    Intent intent = new Intent(mActivity, FilteredPostsActivity.class);
                    intent.putExtra(FilteredPostsActivity.EXTRA_NAME, multiRedditPath);
                    intent.putExtra(FilteredPostsActivity.EXTRA_POST_TYPE, postType);
                    intent.putExtra(FilteredPostsActivity.EXTRA_POST_TYPE_FILTER, Post.NSFW_TYPE);
                    startActivity(intent);
                }

                @Override
                public void currentlyBindItem(int position) {
                    if (maxPosition < position) {
                        maxPosition = position;
                    }
                }

                @Override
                public void delayTransition() {
                    TransitionManager.beginDelayedTransition(binding.recyclerViewPostFragment, new AutoTransition());
                }
            });
        } else if (postType == PostType.DUPLICATES) {
            // The post id is carried in EXTRA_NAME and used as the duplicates listing key.
            subredditName = getArguments().getString(EXTRA_NAME);
            if (savedInstanceState == null) {
                postFragmentId += Objects.requireNonNull(subredditName).hashCode();
            }

            usage = PostFilterUsage.HOME_TYPE;
            nameOfUsage = PostFilterUsage.NO_USAGE;

            // The duplicates endpoint has its own ordering and ignores the standard sort params; the
            // ViewModel still needs a non-null SortType, so use a neutral default.
            sortType = new SortType(SortType.Type.BEST);
            postLayout = mPostLayoutSharedPreferences.getInt(SharedPreferencesUtils.POST_LAYOUT_FRONT_PAGE_POST, defaultPostLayout);

            mAdapter = new PostRecyclerViewAdapter(mActivity, this, mRedditDataRoomDatabase, mExecutor,
                    mOauthRetrofit, mRedgifsRetrofit, mStreamableApiProvider, mShortClipOkHttpClient,
                    mCustomThemeWrapper, locale,
                    mActivity.accessToken, mActivity.accountName, postType, postLayout, true,
                    mSharedPreferences, mCurrentAccountSharedPreferences, mNsfwAndSpoilerSharedPreferences, mPostHistorySharedPreferences,
                    mExoCreator, new PostRecyclerViewAdapter.Callback() {
                @Override
                public void typeChipClicked(int filter) {}

                @Override
                public void flairChipClicked(String flair) {}

                @Override
                public void nsfwChipClicked() {}

                @Override
                public void currentlyBindItem(int position) {
                    if (maxPosition < position) {
                        maxPosition = position;
                    }
                }

                @Override
                public void delayTransition() {
                    TransitionManager.beginDelayedTransition(binding.recyclerViewPostFragment, new AutoTransition());
                }
            });
        } else {
            usage = PostFilterUsage.HOME_TYPE;
            nameOfUsage = PostFilterUsage.NO_USAGE;

            String sort = mSortTypeSharedPreferences.getString(SharedPreferencesUtils.SORT_TYPE_BEST_POST, SortType.Type.BEST.name());
            if (SortType.Type.CONTROVERSIAL.name().equals(sort) || SortType.Type.TOP.name().equals(sort)) {
                String sortTime = mSortTypeSharedPreferences.getString(SharedPreferencesUtils.SORT_TIME_BEST_POST, SortType.Time.ALL.name());
                sortType = new SortType(SortType.Type.valueOf(Objects.requireNonNull(sort)), SortType.Time.valueOf(Objects.requireNonNull(sortTime)));
            } else {
                sortType = new SortType(SortType.Type.valueOf(Objects.requireNonNull(sort)));
            }
            postLayout = mPostLayoutSharedPreferences.getInt(SharedPreferencesUtils.POST_LAYOUT_FRONT_PAGE_POST, defaultPostLayout);

            mAdapter = new PostRecyclerViewAdapter(mActivity, this, mRedditDataRoomDatabase, mExecutor,
                    mOauthRetrofit, mRedgifsRetrofit, mStreamableApiProvider, mShortClipOkHttpClient,
                    mCustomThemeWrapper, locale,
                    mActivity.accessToken, mActivity.accountName, postType, postLayout, true,
                    mSharedPreferences, mCurrentAccountSharedPreferences, mNsfwAndSpoilerSharedPreferences, mPostHistorySharedPreferences,
                    mExoCreator, new PostRecyclerViewAdapter.Callback() {
                @Override
                public void typeChipClicked(int filter) {
                    Intent intent = new Intent(mActivity, FilteredPostsActivity.class);
                    intent.putExtra(FilteredPostsActivity.EXTRA_POST_TYPE, postType);
                    intent.putExtra(FilteredPostsActivity.EXTRA_POST_TYPE_FILTER, filter);
                    startActivity(intent);
                }

                @Override
                public void flairChipClicked(String flair) {
                    Intent intent = new Intent(mActivity, FilteredPostsActivity.class);
                    intent.putExtra(FilteredPostsActivity.EXTRA_POST_TYPE, postType);
                    intent.putExtra(FilteredPostsActivity.EXTRA_CONTAIN_FLAIR, flair);
                    startActivity(intent);
                }

                @Override
                public void nsfwChipClicked() {
                    Intent intent = new Intent(mActivity, FilteredPostsActivity.class);
                    intent.putExtra(FilteredPostsActivity.EXTRA_POST_TYPE, postType);
                    intent.putExtra(FilteredPostsActivity.EXTRA_POST_TYPE_FILTER, Post.NSFW_TYPE);
                    startActivity(intent);
                }

                @Override
                public void currentlyBindItem(int position) {
                    if (maxPosition < position) {
                        maxPosition = position;
                    }
                }

                @Override
                public void delayTransition() {
                    TransitionManager.beginDelayedTransition(binding.recyclerViewPostFragment, new AutoTransition());
                }
            });
        }

        int nColumns = getNColumns(resources);
        if (nColumns == 1) {
            mLinearLayoutManager = new LinearLayoutManagerBugFixed(mActivity);
            binding.recyclerViewPostFragment.setLayoutManager(mLinearLayoutManager);
        } else {
            mStaggeredGridLayoutManager = new StaggeredGridLayoutManager(nColumns, StaggeredGridLayoutManager.VERTICAL);
            binding.recyclerViewPostFragment.setLayoutManager(mStaggeredGridLayoutManager);
            StaggeredGridLayoutManagerItemOffsetDecoration itemDecoration =
                    new StaggeredGridLayoutManagerItemOffsetDecoration(mActivity, R.dimen.staggeredLayoutManagerItemOffset, nColumns);
            binding.recyclerViewPostFragment.addItemDecoration(itemDecoration);
        }

        if (resumePending) {
            restoreAnchorWhenLoaded(resumeState.anchorFullname, resumeState.anchorPosition,
                    resumeState.anchorOffset, RESUME_REVEAL_TIMEOUT_MS);
        } else if (recyclerViewPosition > 0) {
            final int restorePosition = recyclerViewPosition;
            final int restoreOffset = recyclerViewPositionOffset;
            mAdapter.addLoadStateListener(new Function1<>() {
                @Override
                public Unit invoke(CombinedLoadStates combinedLoadStates) {
                    if (combinedLoadStates.getRefresh() instanceof LoadState.NotLoading && mAdapter.getItemCount() > 0) {
                        // scrollToPositionWithOffset, not scrollToPosition: the exact pixel
                        // offset of the anchor item is preserved, not just its visibility.
                        ScrollAnchor.scrollTo(binding.recyclerViewPostFragment,
                                restorePosition, restoreOffset);
                        mAdapter.removeLoadStateListener(this);
                    }
                    return Unit.INSTANCE;
                }
            });
        }

        if (mActivity instanceof ActivityToolbarInterface) {
            ((ActivityToolbarInterface) mActivity).displaySortType();
        }

        where = getArguments().getString(EXTRA_USER_WHERE);

        if (!mActivity.accountName.equals(Account.ANONYMOUS_ACCOUNT)) {
            if(Objects.equals(where, PostPagingSource.USER_WHERE_UPVOTED)){
                usage = PostFilterUsage.UPVOTED_TYPE;
                nameOfUsage = PostFilterUsage.NO_USAGE;
            }
            else if(Objects.equals(where, PostPagingSource.USER_WHERE_DOWNVOTED)){
                usage = PostFilterUsage.DOWNVOTED_TYPE;
                nameOfUsage = PostFilterUsage.NO_USAGE;
            }
            else if(Objects.equals(where, PostPagingSource.USER_WHERE_HIDDEN)){
                usage = PostFilterUsage.HIDDEN_TYPE;
                nameOfUsage = PostFilterUsage.NO_USAGE;
            }
            else if(Objects.equals(where, PostPagingSource.USER_WHERE_SAVED)){
                usage = PostFilterUsage.SAVED_TYPE;
                nameOfUsage = PostFilterUsage.NO_USAGE;
            }
        }

        if (!mActivity.accountName.equals(Account.ANONYMOUS_ACCOUNT)) {
            if (postFilter == null) {
                FetchPostFilterAndConcatenatedSubredditNames.fetchPostFilter(mRedditDataRoomDatabase, mExecutor,
                        new Handler(), usage, nameOfUsage, (postFilter) -> {
                            if (mActivity != null && !mActivity.isFinishing() && !mActivity.isDestroyed() && !isDetached()) {
                                this.postFilter = postFilter;
                                this.postFilter.allowNSFW = mNsfwAndSpoilerSharedPreferences.getBoolean(AccountScope.key(mActivity.accountName, SharedPreferencesUtils.NSFW_BASE), false);
                                initializeAndBindPostViewModel();
                            }
                        });
            } else {
                initializeAndBindPostViewModel();
            }
        } else {
            if (postFilter == null) {
                if (postType == PostType.ANONYMOUS_FRONT_PAGE) {
                    if (concatenatedSubredditNames == null) {
                        FetchPostFilterAndConcatenatedSubredditNames.fetchPostFilterAndConcatenatedSubredditNames(mRedditDataRoomDatabase, mExecutor, new Handler(), usage, nameOfUsage,
                                (postFilter, concatenatedSubredditNames) -> {
                                    if (mActivity != null && !mActivity.isFinishing() && !mActivity.isDestroyed() && !isDetached()) {
                                        this.postFilter = postFilter;
                                        this.postFilter.allowNSFW = mNsfwAndSpoilerSharedPreferences.getBoolean(AccountScope.key(mActivity.accountName, SharedPreferencesUtils.NSFW_BASE), false);
                                        this.concatenatedSubredditNames = concatenatedSubredditNames;
                                        if (concatenatedSubredditNames == null) {
                                            showErrorView(R.string.anonymous_front_page_no_subscriptions);
                                        } else {
                                            initializeAndBindPostViewModelForAnonymous(concatenatedSubredditNames);
                                        }
                                    }
                                });
                    } else {
                        initializeAndBindPostViewModelForAnonymous(concatenatedSubredditNames);
                    }
                } else if (postType == PostType.ANONYMOUS_MULTIREDDIT) {
                    if (concatenatedSubredditNames == null) {
                        FetchPostFilterAndConcatenatedSubredditNames.fetchPostFilterAndConcatenatedSubredditNames(mRedditDataRoomDatabase, mExecutor, new Handler(), multiRedditPath, usage, nameOfUsage,
                                (postFilter, concatenatedSubredditNames) -> {
                                    if (mActivity != null && !mActivity.isFinishing() && !mActivity.isDestroyed() && !isDetached()) {
                                        this.postFilter = postFilter;
                                        this.postFilter.allowNSFW = mNsfwAndSpoilerSharedPreferences.getBoolean(AccountScope.key(mActivity.accountName, SharedPreferencesUtils.NSFW_BASE), false);
                                        this.concatenatedSubredditNames = concatenatedSubredditNames;
                                        if (concatenatedSubredditNames == null) {
                                            showErrorView(R.string.anonymous_multireddit_no_subreddit);
                                        } else {
                                            initializeAndBindPostViewModelForAnonymous(concatenatedSubredditNames);
                                        }
                                    }
                                });
                    } else {
                        initializeAndBindPostViewModelForAnonymous(concatenatedSubredditNames);
                    }
                } else if (postType == PostType.MULTIREDDIT) {
                    FetchPostFilterAndConcatenatedSubredditNames.fetchPostFilter(mRedditDataRoomDatabase, mExecutor,
                            new Handler(), usage, nameOfUsage, (postFilter) -> {
                                if (mActivity != null && !mActivity.isFinishing() && !mActivity.isDestroyed() && !isDetached()) {
                                    this.postFilter = postFilter;
                                    this.postFilter.allowNSFW = mNsfwAndSpoilerSharedPreferences.getBoolean(AccountScope.key(mActivity.accountName, SharedPreferencesUtils.NSFW_BASE), false);
                                    initializeAndBindPostViewModel();
                                }
                            });
                } else {
                    FetchPostFilterAndConcatenatedSubredditNames.fetchPostFilter(mRedditDataRoomDatabase, mExecutor,
                            new Handler(), usage, nameOfUsage, (postFilter) -> {
                                if (mActivity != null && !mActivity.isFinishing() && !mActivity.isDestroyed() && !isDetached()) {
                                    this.postFilter = postFilter;
                                    this.postFilter.allowNSFW = mNsfwAndSpoilerSharedPreferences.getBoolean(AccountScope.key(mActivity.accountName, SharedPreferencesUtils.NSFW_BASE), false);
                                    initializeAndBindPostViewModelForAnonymous(null);
                                }
                            });
                }
            } else {
                if (postType == PostType.ANONYMOUS_FRONT_PAGE) {
                    if (concatenatedSubredditNames == null) {
                        FetchPostFilterAndConcatenatedSubredditNames.fetchPostFilterAndConcatenatedSubredditNames(mRedditDataRoomDatabase, mExecutor, new Handler(), usage, nameOfUsage,
                                (postFilter, concatenatedSubredditNames) -> {
                                    if (mActivity != null && !mActivity.isFinishing() && !mActivity.isDestroyed() && !isDetached()) {
                                        Objects.requireNonNull(this.postFilter).allowNSFW = mNsfwAndSpoilerSharedPreferences.getBoolean(AccountScope.key(mActivity.accountName, SharedPreferencesUtils.NSFW_BASE), false);
                                        this.concatenatedSubredditNames = concatenatedSubredditNames;
                                        if (concatenatedSubredditNames == null) {
                                            showErrorView(R.string.anonymous_front_page_no_subscriptions);
                                        } else {
                                            initializeAndBindPostViewModelForAnonymous(concatenatedSubredditNames);
                                        }
                                    }
                                });
                    } else {
                        initializeAndBindPostViewModelForAnonymous(concatenatedSubredditNames);
                    }
                } else if (postType == PostType.ANONYMOUS_MULTIREDDIT) {
                    if (concatenatedSubredditNames == null) {
                        FetchPostFilterAndConcatenatedSubredditNames.fetchPostFilterAndConcatenatedSubredditNames(mRedditDataRoomDatabase, mExecutor, new Handler(), multiRedditPath, usage, nameOfUsage,
                                (postFilter, concatenatedSubredditNames) -> {
                                    if (mActivity != null && !mActivity.isFinishing() && !mActivity.isDestroyed() && !isDetached()) {
                                        Objects.requireNonNull(this.postFilter).allowNSFW = mNsfwAndSpoilerSharedPreferences.getBoolean(AccountScope.key(mActivity.accountName, SharedPreferencesUtils.NSFW_BASE), false);
                                        this.concatenatedSubredditNames = concatenatedSubredditNames;
                                        if (concatenatedSubredditNames == null) {
                                            showErrorView(R.string.anonymous_multireddit_no_subreddit);
                                        } else {
                                            initializeAndBindPostViewModelForAnonymous(concatenatedSubredditNames);
                                        }
                                    }
                                });
                    } else {
                        initializeAndBindPostViewModelForAnonymous(concatenatedSubredditNames);
                    }
                } else if (postType == PostType.MULTIREDDIT) {
                    initializeAndBindPostViewModel();
                } else {
                    initializeAndBindPostViewModelForAnonymous(null);
                }
            }
        }

        if (nColumns == 1 && mSharedPreferences.getBoolean(SharedPreferencesUtils.ENABLE_SWIPE_ACTION, false)) {
            swipeActionEnabled = true;
            touchHelper.attachToRecyclerView(binding.recyclerViewPostFragment, 1);
        }
        binding.recyclerViewPostFragment.setAdapter(mAdapter);
        binding.recyclerViewPostFragment.setCacheManager(mAdapter);
        binding.recyclerViewPostFragment.setPlayerInitializer(order -> {
            VolumeInfo volumeInfo = new VolumeInfo(true, 0f);
            return new PlaybackInfo(INDEX_UNSET, TIME_UNSET, volumeInfo);
        });

        SharedPreferencesLiveDataKt.stringLiveData(mSharedPreferences, SharedPreferencesUtils.SIMULTANEOUS_AUTOPLAY_LIMIT, "1").observe(getViewLifecycleOwner(), limit -> {
            if (getPostAdapter() != null) {
                getPostAdapter().setSimultaneousAutoplayLimit(Integer.parseInt(limit));
            }
        });

        // Observed rather than read once at onCreateView. MainActivity outlives a settings change,
        // so this feed is exactly the thing that goes stale: turning the setting on left it holding
        // a false for the rest of the process, so nothing was cached and the next launch had no
        // posts to resume onto -- the stack came back but the feed opened at the top. The key is
        // what makes a feed record itself, so it follows the setting both ways.
        SharedPreferencesLiveDataKt.booleanLiveData(mSharedPreferences,
                        SharedPreferencesUtils.RESUME_WHERE_I_LEFT_OFF, false)
                .observe(getViewLifecycleOwner(), enabled -> {
                    if (enabled == resumeWhereILeftOff) {
                        // Including the emission that arrives on registration, which is the value
                        // onCreateView already read.
                        return;
                    }
                    resumeWhereILeftOff = enabled;
                    resumeFeedKey = buildFeedKey();
                    if (mPostViewModel != null) {
                        // The key alone, never a restore: there is nothing to come back to
                        // mid-session, only a feed to start recording. Turning it off clears the
                        // key, which is what stops the writes.
                        mPostViewModel.setResumeRequest(resumeFeedKey, false, 0);
                    }
                });

        SharedPreferencesLiveDataKt.booleanLiveData(mSharedPreferences, SharedPreferencesUtils.SHOW_GALLERY_MEDIA_AS_GRID, false).observe(getViewLifecycleOwner(), showGalleryMediaAsGrid -> {
            if (getPostAdapter() != null) {
                if (getPostAdapter().setShowGalleryMediaAsGrid(showGalleryMediaAsGrid)) {
                    refreshAdapter();
                }
            }
        });

        SharedPreferencesLiveDataKt.booleanLiveData(mSharedPreferences, SharedPreferencesUtils.SHOW_POST_AND_COMMENT_TOOLBAR_ITEMS_BASED_ON_SPACE, false).observe(getViewLifecycleOwner(), showPostAndCommentToolbarItemsBasedOnSpace -> {
            if (getPostAdapter() != null) {
                if (getPostAdapter().setShowToolbarItemsBasedOnSpace(showPostAndCommentToolbarItemsBasedOnSpace)) {
                    refreshAdapter();
                }
            }
        });

        return binding.getRoot();
    }

    private void initializeAndBindPostViewModel() {
        if (randomSubredditPseudoName != null && currentRandomSubreddit == null) {
            // Nothing to page yet. The roll comes back here once it has a subreddit.
            rollRandomSubreddit();
            return;
        }
        if (postType == PostType.SEARCH) {
            mPostViewModel = new ViewModelProvider(PostFragment.this, new PostViewModel.Factory(mExecutor,
                    mActivity.accountName.equals(Account.ANONYMOUS_ACCOUNT) ? mRetrofit : mOauthRetrofit,
                    mRedditDataRoomDatabase, mActivity.accessToken, mActivity.accountName, mSharedPreferences,
                    mPostFeedScrolledPositionSharedPreferences, mPostHistorySharedPreferences, subredditName,
                    query, trendingSource, postType, sortType, Objects.requireNonNull(postFilter), readPostsList, loader)
            ).get(PostViewModel.class);
        } else if (postType == PostType.SUBREDDIT || postType == PostType.DUPLICATES) {
            mPostViewModel = new ViewModelProvider(PostFragment.this, new PostViewModel.Factory(mExecutor,
                    mActivity.accountName.equals(Account.ANONYMOUS_ACCOUNT) ? mRetrofit : mOauthRetrofit,
                    mRedditDataRoomDatabase, mActivity.accessToken, mActivity.accountName, mSharedPreferences,
                    mPostFeedScrolledPositionSharedPreferences, mPostHistorySharedPreferences,
                    randomSubredditPseudoName != null ? currentRandomSubreddit : subredditName,
                    postType, sortType, Objects.requireNonNull(postFilter), readPostsList, loader)
            ).get(PostViewModel.class);
        } else if (postType == PostType.MULTIREDDIT) {
            mPostViewModel = new ViewModelProvider(PostFragment.this, new PostViewModel.Factory(mExecutor,
                    mActivity.accountName.equals(Account.ANONYMOUS_ACCOUNT) ? mRetrofit : mOauthRetrofit,
                    mRedditDataRoomDatabase, mActivity.accessToken, mActivity.accountName, mSharedPreferences,
                    mPostFeedScrolledPositionSharedPreferences, mPostHistorySharedPreferences, multiRedditPath,
                    query, postType, sortType, Objects.requireNonNull(postFilter), readPostsList, loader)
            ).get(PostViewModel.class);
        } else if (postType == PostType.USER) {
            mPostViewModel = new ViewModelProvider(PostFragment.this, new PostViewModel.Factory(mExecutor,
                    mActivity.accountName.equals(Account.ANONYMOUS_ACCOUNT) ? mRetrofit : mOauthRetrofit,
                    mRedditDataRoomDatabase, mActivity.accessToken, mActivity.accountName, mSharedPreferences,
                    mPostFeedScrolledPositionSharedPreferences, mPostHistorySharedPreferences, username,
                    postType, sortType, Objects.requireNonNull(postFilter), where, readPostsList, loader)
            ).get(PostViewModel.class);
        } else {
            mPostViewModel = new ViewModelProvider(PostFragment.this, new PostViewModel.Factory(mExecutor,
                    mOauthRetrofit, mRedditDataRoomDatabase, mActivity.accessToken,
                    mActivity.accountName, mSharedPreferences, mPostFeedScrolledPositionSharedPreferences,
                    mPostHistorySharedPreferences, postType, sortType, Objects.requireNonNull(postFilter), readPostsList, loader)
            ).get(PostViewModel.class);
        }

        bindPostViewModel();
    }

    // Loads the anonymous home feed or anonymous multireddit. Unlike a signed-in account, these
    // feeds are assembled from the locally stored subscription list rather than a server-side feed,
    // so the set of subreddits can change while this fragment is alive (e.g. subscribing from the
    // Popular tab or another screen). Re-queries the concatenated subreddit names from the database,
    // then either binds the post view model or shows the "no subscriptions" empty state.
    //
    // Safe to call again on pull-to-refresh: it lets the feed pick up newly added/removed
    // subreddits and recover from the empty state, where no post view model was ever created and
    // mAdapter.refresh() would otherwise do nothing.
    private void loadAnonymousFrontPageOrMultireddit(int usage, @Nullable String nameOfUsage) {
        FetchPostFilterAndConcatenatedSubredditNames.FetchPostFilterAndConcatenatecSubredditNamesListener listener =
                (fetchedPostFilter, fetchedConcatenatedSubredditNames) -> {
                    if (mActivity != null && !mActivity.isFinishing() && !mActivity.isDestroyed() && !isDetached()) {
                        if (postFilter == null) {
                            postFilter = fetchedPostFilter;
                        }
                        Objects.requireNonNull(postFilter).allowNSFW = mNsfwAndSpoilerSharedPreferences.getBoolean(AccountScope.key(mActivity.accountName, SharedPreferencesUtils.NSFW_BASE), false);
                        concatenatedSubredditNames = fetchedConcatenatedSubredditNames;
                        if (concatenatedSubredditNames == null) {
                            // Unsubscribing from the last subreddit leaves no feed to show. The
                            // adapter still holds the previously loaded posts, so clear them before
                            // showing the empty state; otherwise the old posts stay visible behind it.
                            mAdapter.submitData(getViewLifecycleOwner().getLifecycle(), PagingData.empty());
                            showErrorView(postType == PostType.ANONYMOUS_MULTIREDDIT
                                    ? R.string.anonymous_multireddit_no_subreddit
                                    : R.string.anonymous_front_page_no_subscriptions);
                        } else {
                            initializeAndBindPostViewModelForAnonymous(concatenatedSubredditNames);
                        }
                    }
                };
        if (postType == PostType.ANONYMOUS_MULTIREDDIT) {
            FetchPostFilterAndConcatenatedSubredditNames.fetchPostFilterAndConcatenatedSubredditNames(
                    mRedditDataRoomDatabase, mExecutor, new Handler(), multiRedditPath, usage, nameOfUsage, listener);
        } else {
            FetchPostFilterAndConcatenatedSubredditNames.fetchPostFilterAndConcatenatedSubredditNames(
                    mRedditDataRoomDatabase, mExecutor, new Handler(), usage, nameOfUsage, listener);
        }
    }

    private void initializeAndBindPostViewModelForAnonymous(@Nullable String concatenatedSubredditNames) {
        if (postType == PostType.SEARCH) {
            mPostViewModel = new ViewModelProvider(PostFragment.this, new PostViewModel.Factory(mExecutor,
                    mRetrofit, mRedditDataRoomDatabase, null, mActivity.accountName, mSharedPreferences,
                    mPostFeedScrolledPositionSharedPreferences, null, subredditName,
                    query, trendingSource, postType, sortType, Objects.requireNonNull(postFilter), readPostsList, loader)
            ).get(PostViewModel.class);
        } else if (postType == PostType.SUBREDDIT || postType == PostType.DUPLICATES) {
            mPostViewModel = new ViewModelProvider(this, new PostViewModel.Factory(mExecutor,
                    mRetrofit, mRedditDataRoomDatabase, null, mActivity.accountName,
                    mSharedPreferences, mPostFeedScrolledPositionSharedPreferences,
                    null, subredditName, postType, sortType, Objects.requireNonNull(postFilter), readPostsList, loader)
            ).get(PostViewModel.class);
        } else if (postType == PostType.USER) {
            mPostViewModel = new ViewModelProvider(PostFragment.this, new PostViewModel.Factory(mExecutor,
                    mRetrofit, mRedditDataRoomDatabase, null, mActivity.accountName, mSharedPreferences,
                    mPostFeedScrolledPositionSharedPreferences, null, username,
                    postType, sortType, Objects.requireNonNull(postFilter), where, readPostsList, loader)
            ).get(PostViewModel.class);
        } else {
            //Anonymous front page or multireddit
            boolean reusedExistingViewModel = mPostViewModel != null;
            mPostViewModel = new ViewModelProvider(PostFragment.this, new PostViewModel.Factory(mExecutor,
                    mRetrofit, mRedditDataRoomDatabase, mSharedPreferences, concatenatedSubredditNames,
                    postType, sortType, Objects.requireNonNull(postFilter), readPostsList, loader)
            ).get(PostViewModel.class);
            if (reusedExistingViewModel) {
                // On a reload (e.g. after subscribing) ViewModelProvider.get() hands back the existing
                // fragment-scoped ViewModel and ignores the Factory above, so its subreddit names are
                // still the ones captured when it was first created. Push the freshly read names onto
                // it so a newly (un)subscribed subreddit shows up now instead of only after a restart.
                mPostViewModel.changeSubredditName(concatenatedSubredditNames);
            }
        }

        bindPostViewModel();
    }

    private void bindPostViewModel() {
        // Before the first observe, so the initial value costs no pipeline rebuild. Both
        // initializeAndBindPostViewModel paths land here.
        applyMediaOnlyPosts();

        // Also before the first observe: the source has to know whether its first load comes from
        // disk before that load is asked for. The key is set even with nothing to restore, because
        // that is what records this feed for next time.
        resumeFeedKey = buildFeedKey();
        if (resumeFeedKey != null) {
            mPostViewModel.setResumeRequest(resumeFeedKey, resumePending, resumeState.expectedCount);
        }
        // Both halves have now had it. Strip it from the arguments as well, so a view rebuilt
        // without its fragment does not restore a second time.
        resumePending = false;
        FeedResumeState.clearFrom(getArguments());

        mPostViewModel.getPosts().observe(getViewLifecycleOwner(), posts -> {
            mAdapter.submitData(getViewLifecycleOwner().getLifecycle(), posts);
        });

        mPostViewModel.moderationEventLiveData.observe(getViewLifecycleOwner(), moderationEvent -> {
            // getPost() is @Nullable (only DeleteFailed carries a null post); guard so a null-post
            // event still shows its toast instead of crashing.
            Post moderatedPost = moderationEvent.getPost();
            if (moderatedPost != null) {
                EventBus.getDefault().post(new PostUpdateEventToPostList(moderatedPost, moderationEvent.getPosition()));
                EventBus.getDefault().post(new PostUpdateEventToPostDetailFragment(moderatedPost));
            }
            Toast.makeText(mActivity, moderationEvent.getToastMessageResId(), Toast.LENGTH_SHORT).show();
        });

        mAdapter.addLoadStateListener(combinedLoadStates -> {
            LoadState refreshLoadState = combinedLoadStates.getRefresh();
            LoadState appendLoadState = combinedLoadStates.getAppend();

            binding.swipeRefreshLayoutPostFragment.setRefreshing(refreshLoadState instanceof LoadState.Loading);
            if (refreshLoadState instanceof LoadState.NotLoading) {
                if (refreshLoadState.getEndOfPaginationReached() && mAdapter.getItemCount() < 1) {
                    noPostFound();
                } else {
                    binding.fetchPostInfoLinearLayoutPostFragment.setVisibility(View.GONE);
                    hasPost = true;
                }
            } else if (refreshLoadState instanceof LoadState.Error) {
                binding.fetchPostInfoLinearLayoutPostFragment.setOnClickListener(view -> refresh());
                Throwable e = ((LoadState.Error) refreshLoadState).getError();
                if (e instanceof PostPagingSource.PostPagingSourceError) {
                    if (((PostPagingSource.PostPagingSourceError) e).code == 403 && Account.ANONYMOUS_ACCOUNT.equals(mActivity.accountName)) {
                        showErrorView(R.string.load_posts_error_anonymous_403);
                    } else if (((PostPagingSource.PostPagingSourceError) e).message != null) {
                        showErrorView(getString(R.string.load_posts_error_with_reason, ((PostPagingSource.PostPagingSourceError) e).message));
                    } else {
                        showErrorView(R.string.load_posts_error);
                    }
                } else {
                    showErrorView(R.string.load_posts_error);
                }
            }
            if (!(refreshLoadState instanceof LoadState.Loading) && appendLoadState instanceof LoadState.NotLoading) {
                if (appendLoadState.getEndOfPaginationReached() && mAdapter.getItemCount() < 1) {
                    noPostFound();
                }
            }
            return null;
        });

        binding.recyclerViewPostFragment.setAdapter(mAdapter.withLoadStateFooter(new Paging3LoadingStateAdapter(mActivity, mCustomThemeWrapper, R.string.load_more_posts_error,
                view -> mAdapter.retry())));
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.post_fragment, menu);
        for (int i = 0; i < menu.size(); i++) {
            Utils.setTitleWithCustomFontToMenuItem(mActivity.typeface, menu.getItem(i), null);
        }
        lazyModeItem = menu.findItem(R.id.action_lazy_mode_post_fragment);

        if (isInLazyMode) {
            Utils.setTitleWithCustomFontToMenuItem(mActivity.typeface, lazyModeItem, getString(R.string.action_stop_lazy_mode));
        } else {
            Utils.setTitleWithCustomFontToMenuItem(mActivity.typeface, lazyModeItem, getString(R.string.action_start_lazy_mode));
        }

        if (mActivity instanceof FilteredPostsActivity) {
            menu.findItem(R.id.action_filter_posts_post_fragment).setVisible(false);
        }

        if (mActivity instanceof FilteredPostsActivity || mActivity instanceof AccountPostsActivity
                || mActivity instanceof AccountSavedThingActivity) {
            menu.findItem(R.id.action_more_options_post_fragment).setVisible(false);
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_lazy_mode_post_fragment) {
            if (isInLazyMode) {
                stopLazyMode();
            } else {
                startLazyMode();
            }
            return true;
        } else if (item.getItemId() == R.id.action_filter_posts_post_fragment) {
            filterPosts();
            return true;
        } else if (item.getItemId() == R.id.action_more_options_post_fragment) {
            FABMoreOptionsBottomSheetFragment fabMoreOptionsBottomSheetFragment= new FABMoreOptionsBottomSheetFragment();
            Bundle bundle = new Bundle();
            bundle.putBoolean(FABMoreOptionsBottomSheetFragment.EXTRA_ANONYMOUS_MODE, mActivity.accountName.equals(Account.ANONYMOUS_ACCOUNT));
            fabMoreOptionsBottomSheetFragment.setArguments(bundle);
            fabMoreOptionsBottomSheetFragment.show(mActivity.getSupportFragmentManager(), fabMoreOptionsBottomSheetFragment.getTag());
            return true;
        }
        return false;
    }

    private void noPostFound() {
        hasPost = false;
        if (isInLazyMode) {
            stopLazyMode();
        }

        binding.fetchPostInfoLinearLayoutPostFragment.setOnClickListener(null);
        if (isAnonymousFrontPageOrMultireddit() && concatenatedSubredditNames == null) {
            // An anonymous home/multireddit feed with no subscriptions has no posts to load, but the
            // generic "no posts" message is misleading here. Tell the user to add a subreddit instead.
            showErrorView(postType == PostType.ANONYMOUS_MULTIREDDIT
                    ? R.string.anonymous_multireddit_no_subreddit
                    : R.string.anonymous_front_page_no_subscriptions);
        } else {
            showErrorView(R.string.no_posts);
        }
    }

    public void changeSortType(SortType sortType) {
        if (mPostViewModel != null) {
            if (mSharedPreferences.getBoolean(SharedPreferencesUtils.SAVE_POST_SORT, true)) {
                switch (postType) {
                    case PostType.FRONT_PAGE:
                        mSortTypeSharedPreferences.edit().putString(SharedPreferencesUtils.SORT_TYPE_BEST_POST, sortType.getType().name()).apply();
                        if (sortType.getTime() != null) {
                            mSortTypeSharedPreferences.edit().putString(SharedPreferencesUtils.SORT_TIME_BEST_POST, sortType.getTime().name()).apply();
                        }
                        break;
                    case PostType.SUBREDDIT:
                        mSortTypeSharedPreferences.edit().putString(SharedPreferencesUtils.SORT_TYPE_SUBREDDIT_POST_BASE + subredditName, sortType.getType().name()).apply();
                        if (sortType.getTime() != null) {
                            mSortTypeSharedPreferences.edit().putString(SharedPreferencesUtils.SORT_TIME_SUBREDDIT_POST_BASE + subredditName, sortType.getTime().name()).apply();
                        }
                        break;
                    case PostType.USER:
                        mSortTypeSharedPreferences.edit().putString(SharedPreferencesUtils.SORT_TYPE_USER_POST_BASE + username, sortType.getType().name()).apply();
                        if (sortType.getTime() != null) {
                            mSortTypeSharedPreferences.edit().putString(SharedPreferencesUtils.SORT_TIME_USER_POST_BASE + username, sortType.getTime().name()).apply();
                        }
                        break;
                    case PostType.SEARCH:
                        mSortTypeSharedPreferences.edit().putString(SharedPreferencesUtils.SORT_TYPE_SEARCH_POST, sortType.getType().name()).apply();
                        if (sortType.getTime() != null) {
                            mSortTypeSharedPreferences.edit().putString(SharedPreferencesUtils.SORT_TIME_SEARCH_POST, sortType.getTime().name()).apply();
                        }
                        break;
                    case PostType.MULTIREDDIT:
                    case PostType.ANONYMOUS_MULTIREDDIT:
                        mSortTypeSharedPreferences.edit().putString(SharedPreferencesUtils.SORT_TYPE_MULTI_REDDIT_POST_BASE + multiRedditPath,
                                sortType.getType().name()).apply();
                        if (sortType.getTime() != null) {
                            mSortTypeSharedPreferences.edit().putString(SharedPreferencesUtils.SORT_TIME_MULTI_REDDIT_POST_BASE + multiRedditPath,
                                    sortType.getTime().name()).apply();
                        }
                        break;
                    case PostType.ANONYMOUS_FRONT_PAGE:
                        mSortTypeSharedPreferences.edit().putString(SharedPreferencesUtils.SORT_TYPE_ANONYMOUS_FRONT_PAGE_POST, sortType.getType().name()).apply();
                        if (sortType.getTime() != null) {
                            mSortTypeSharedPreferences.edit().putString(SharedPreferencesUtils.SORT_TIME_ANONYMOUS_FRONT_PAGE_POST, sortType.getTime().name()).apply();
                        }
                        break;
                }
            }
            if (binding.fetchPostInfoLinearLayoutPostFragment.getVisibility() != View.GONE) {
                binding.fetchPostInfoLinearLayoutPostFragment.setVisibility(View.GONE);
                mGlide.clear(binding.fetchPostInfoImageViewPostFragment);
            }
            hasPost = false;
            if (isInLazyMode) {
                stopLazyMode();
            }
            this.sortType = sortType;
            mPostViewModel.changeSortType(sortType);
            goBackToTop();
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(IS_IN_LAZY_MODE_STATE, isInLazyMode);

        ScrollAnchor.Anchor anchor = ScrollAnchor.capture(
                binding.recyclerViewPostFragment, userAnchorPos, userAnchorOffset);
        userAnchorPos = anchor.stickyPosition;
        userAnchorOffset = anchor.stickyOffset;

        if (anchor.isValid()) {
            outState.putInt(RECYCLER_VIEW_POSITION_STATE, anchor.position);
            outState.putInt(RECYCLER_VIEW_POSITION_OFFSET_STATE, anchor.offset);
            outState.putInt(RECYCLER_VIEW_USER_ANCHOR_STATE, userAnchorPos);
            outState.putInt(RECYCLER_VIEW_USER_ANCHOR_OFFSET_STATE, userAnchorOffset);
        }

        outState.putParcelable(POST_FILTER_STATE, postFilter);
        outState.putString(CONCATENATED_SUBREDDIT_NAMES_STATE, concatenatedSubredditNames);
        outState.putLong(POST_FRAGMENT_ID_STATE, postFragmentId);

        // Only the subreddit/multireddit/user/search paths restore this (see getOverrideSortType).
        if ((postType == PostType.SUBREDDIT || postType == PostType.MULTIREDDIT
                || postType == PostType.ANONYMOUS_MULTIREDDIT || postType == PostType.USER
                || postType == PostType.SEARCH)
                && sortType != null) {
            outState.putString(SORT_TYPE_STATE, sortType.getType().name());
            if (sortType.getTime() != null) {
                outState.putString(SORT_TIME_STATE, sortType.getTime().name());
            }
        }
    }

    /**
     * Resolves a sort that should take precedence over the saved/default sort: the live sort
     * restored across a config change, or a sort carried by an opening deep link on fresh
     * creation. Returns null to fall back to the saved/default sort. Only consulted by the
     * subreddit, multireddit (incl. anonymous), user, and search paths.
     */
    @Nullable
    private SortType getOverrideSortType(@Nullable Bundle savedInstanceState) {
        String typeName;
        String timeName;
        if (savedInstanceState != null) {
            typeName = savedInstanceState.getString(SORT_TYPE_STATE);
            timeName = savedInstanceState.getString(SORT_TIME_STATE);
        } else {
            Bundle args = getArguments();
            typeName = args == null ? null : args.getString(EXTRA_INITIAL_SORT_TYPE);
            timeName = args == null ? null : args.getString(EXTRA_INITIAL_SORT_TIME);
        }
        if (typeName == null) {
            return null;
        }
        try {
            SortType.Type type = SortType.Type.valueOf(typeName);
            return timeName == null ? new SortType(type) : new SortType(type, SortType.Time.valueOf(timeName));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        saveCache();
        storeResumeCache();
    }

    /**
     * Persist the loaded feed for a later resume, anchored on the post in view.
     *
     * <p>Called from {@link #onStop()} and, throttled, when the list comes to rest. The second is
     * not redundant: dismissing the app from recents delivers pause, stop and destroy in one short
     * burst and then kills the process, so the position that reaches disk should already be current
     * before the lifecycle callbacks arrive.
     */
    private void storeResumeCache() {
        if (resumeFeedKey == null || mPostViewModel == null || binding == null) {
            return;
        }
        binding.recyclerViewPostFragment.removeCallbacks(resumeStoreRunnable);
        lastResumeStoreAt = SystemClock.uptimeMillis();
        mPostViewModel.storeFeedCache(currentAnchorFullName(), shownPosts());
        // And the snapshot that points at it, for the same reason: the two have to describe the
        // same moment. Writing only the posts would leave the next launch restoring a fresh feed
        // onto a stale anchor, which lands the user somewhere they never were.
        if (mActivity != null) {
            ResumeState.capture(mActivity);
        }
    }

    /**
     * The posts the feed is showing, as a plain list.
     *
     * <p>Read from the adapter rather than from the paging source: this runs on the main thread and
     * the source builds its own list on the paging executor, where copying it would race a load.
     * Paging's snapshot is made for exactly this. It is only wanted when the source has no raw
     * listing JSON left to cache -- see {@link PostPagingSource#storeFeedCache}.
     */
    @Nullable
    private List<Post> shownPosts() {
        if (mAdapter == null) {
            return null;
        }
        ItemSnapshotList<Post> snapshot = mAdapter.snapshot();
        List<Post> out = new ArrayList<>(snapshot.size());
        for (Post post : snapshot) {
            if (post != null) {
                out.add(post);
            }
        }
        return out;
    }

    /**
     * The resume cache key for this feed, or null when the setting is off or the listing is not one
     * worth resuming onto.
     *
     * <p>Deliberately not {@code MainPageTabsUtils.userKey}: that is keyed on the main page's own
     * tab-type constants, which are a different enumeration from {@link PostType}, and this has to
     * name feeds that never appear as a tab at all.
     */
    @Nullable
    private String buildFeedKey() {
        if (!resumeWhereILeftOff) {
            return null;
        }
        String name;
        switch (postType) {
            case PostType.FRONT_PAGE:
            case PostType.ANONYMOUS_FRONT_PAGE:
                name = "";
                break;
            case PostType.SUBREDDIT:
            case PostType.ANONYMOUS_MULTIREDDIT:
                name = subredditName;
                break;
            case PostType.MULTIREDDIT:
                name = multiRedditPath;
                break;
            case PostType.USER:
                name = username + "/" + (where == null ? "" : where);
                break;
            case PostType.SEARCH:
                // A global search names no subreddit, which is itself a listing worth naming.
                name = subredditName == null ? "" : subredditName;
                break;
            default:
                // A duplicates listing is reached from one post, is short, and leads nowhere: there
                // is no place in it worth coming back to.
                return null;
        }
        if (name == null) {
            return null;
        }
        // A search is a listing in its own right, not a filter over the one it names: searching
        // r/pics and browsing r/pics return different posts in a different order, and searching it
        // for two different things returns two more. Subreddit, multireddit and search feeds can all
        // carry a query, so this is appended for all of them rather than in one branch above.
        if (query != null) {
            name += "?" + query + (trendingSource == null ? "" : "&" + trendingSource);
        }
        // The scope keeps hosts that show the same listing through a different lens -- the filtered
        // posts screen, above all -- out of each other's cache. Without it a subreddit filtered to
        // images and the same subreddit unfiltered would write the same key with different windows
        // and different cursors, and each would keep overwriting the other.
        String scoped = resumeFeedScope == null ? "" : resumeFeedScope + "|";
        return FeedCache.key(mActivity.accountName,
                scoped + postType + "|" + name.toLowerCase(Locale.US));
    }

    /**
     * Record where this feed is into {@code out}, for a host building a resume snapshot. Returns
     * false, writing nothing, when there is no anchor worth recording.
     */
    public boolean captureResumeState(@NonNull Bundle out) {
        return captureAnchorInto(out, resumeFeedKey);
    }

    private void saveCache() {
        if (savePostFeedScrolledPosition && postType == PostType.FRONT_PAGE && sortType != null && sortType.getType() == SortType.Type.BEST && mAdapter != null) {
            Post currentPost = mAdapter.getItemByPosition(maxPosition);
            if (currentPost != null) {
                // The account name is the whole prefix, anonymous included: it is spelled
                // ".anonymous", which is what this file used before account names agreed with it.
                String key = mActivity.accountName + SharedPreferencesUtils.FRONT_PAGE_SCROLLED_POSITION_FRONT_PAGE_BASE;
                String value = currentPost.getFullName();
                mPostFeedScrolledPositionSharedPreferences.edit().putString(key, value).apply();
            }
        }
    }

    // Client-side search used by the Saved screen. Filters the loaded posts against the query and
    // keeps loading further pages while the query is active (see PostViewModel#buildSearchLayer).
    public void filterSaved(String query) {
        if (mPostViewModel != null) {
            mPostViewModel.searchSaved(query);
        }
    }

    // "Bypass cache (fetch fresh)" toggle on the Saved screen: drop the hard-TTL cache and reload
    // the whole history from the network.
    public void forceFreshSaved() {
        if (mPostViewModel != null) {
            mPostViewModel.forceFreshSavedLoad();
        }
    }

    // A thing was saved/unsaved in-app: drop the in-memory Saved search cache so a search in progress
    // refetches rather than re-surfacing the just-changed item.
    public void onSavedThingChanged() {
        if (mPostViewModel != null) {
            mPostViewModel.invalidateInMemorySavedSearchCache();
        }
    }

    @Override
    public void refresh() {
        binding.fetchPostInfoLinearLayoutPostFragment.setVisibility(View.GONE);
        hasPost = false;
        // A refresh is a request for the top of the listing, which is the one thing a resume must
        // not serve. Dropped here rather than in the view model alone so the pull-to-refresh, the
        // retry button and the random-tab roll below all take it.
        resumePending = false;
        if (mPostViewModel != null) {
            mPostViewModel.cancelResumeRestore();
        }
        cancelAnchorRestore();
        if (isInLazyMode) {
            stopLazyMode();
        }
        if (randomSubredditPseudoName != null) {
            // On a random tab a refresh is the whole feature: it means "show me a different
            // subreddit", not "reload this one". Rolling replaces the name the pager reads and
            // re-triggers it, exactly as the anonymous feed does when its subscription list changes.
            saveCache();
            rollRandomSubreddit();
            return;
        }
        if (isAnonymousFrontPageOrMultireddit()) {
            // The anonymous home/multireddit feed is built from the locally stored subscription
            // list, so a refresh must re-read it from the database. This both picks up subreddits
            // (un)subscribed to elsewhere and recovers from the empty state, where no post view
            // model was created and refreshing the adapter below would have no effect.
            reloadAnonymousFrontPageOrMultireddit();
            return;
        }
        saveCache();
        // Drop any cached Saved-search listing so a refresh while a search is active refetches.
        if (mPostViewModel != null) {
            mPostViewModel.invalidateSavedSearchCache();
        }
        mAdapter.refresh();
        goBackToTop();
    }

    private boolean isAnonymousFrontPageOrMultireddit() {
        return mActivity != null && mActivity.accountName.equals(Account.ANONYMOUS_ACCOUNT)
                && (postType == PostType.ANONYMOUS_FRONT_PAGE || postType == PostType.ANONYMOUS_MULTIREDDIT);
    }

    /**
     * Picks the subreddit a random tab shows, and shows it. Called once when the tab first needs a
     * feed, and again on every pull-to-refresh.
     *
     * A first roll has no view model yet and builds one; later rolls push the new name onto the
     * existing one, which re-triggers the paging pipeline without rebuilding the Pager.
     */
    private void rollRandomSubreddit() {
        boolean anonymous = mActivity.accountName.equals(Account.ANONYMOUS_ACCOUNT);
        mRandomSubredditRepository.pickForName(
                randomSubredditPseudoName,
                mActivity.accountName,
                anonymous ? mRetrofit : mOauthRetrofit,
                anonymous ? Collections.emptyMap() : APIUtils.getOAuthHeader(mActivity.accessToken),
                new Handler(Looper.getMainLooper()),
                new RandomSubredditRepository.PickListener() {
                    @Override
                    public void onRandomSubredditPicked(@NonNull String pickedSubredditName) {
                        if (!isUsableForRandomRoll()) {
                            return;
                        }
                        currentRandomSubreddit = pickedSubredditName;
                        binding.fetchPostInfoLinearLayoutPostFragment.setVisibility(View.GONE);
                        if (mPostViewModel == null) {
                            initializeAndBindPostViewModel();
                        } else {
                            mPostViewModel.changeSubredditName(pickedSubredditName);
                            goBackToTop();
                        }
                    }

                    @Override
                    public void onRandomSubredditPickFailed() {
                        if (!isUsableForRandomRoll()) {
                            return;
                        }
                        // Also the no-subscriptions case for myrandom, which is a healthy device
                        // with nothing to pick from. showErrorView stops the refresh spinner.
                        showErrorView(R.string.fetch_random_thing_failed);
                    }
                });
    }

    /** Whether a roll that has come back still has a live fragment and view to land in. */
    private boolean isUsableForRandomRoll() {
        return mActivity != null && !mActivity.isFinishing() && !mActivity.isDestroyed()
                && isAdded() && !isDetached() && getView() != null;
    }

    private void reloadAnonymousFrontPageOrMultireddit() {
        concatenatedSubredditNames = null;
        loadAnonymousFrontPageOrMultireddit(
                postType == PostType.ANONYMOUS_MULTIREDDIT ? PostFilterUsage.MULTIREDDIT_TYPE : PostFilterUsage.HOME_TYPE,
                postType == PostType.ANONYMOUS_MULTIREDDIT ? multiRedditPath : PostFilterUsage.NO_USAGE);
    }

    @Subscribe
    public void onChangeAnonymousSubredditSubscriptionEvent(ChangeAnonymousSubredditSubscriptionEvent event) {
        if (!isAnonymousFrontPageOrMultireddit()) {
            return;
        }
        // Always force the next load to re-read the local subscription list. If the view is gone
        // (e.g. an offscreen pager page), clearing the cached names is enough: onCreateView re-reads
        // them when the view is recreated. Only reload immediately when a view actually exists,
        // because the reload binds to it.
        if (getView() == null) {
            concatenatedSubredditNames = null;
        } else {
            reloadAnonymousFrontPageOrMultireddit();
        }
    }

    @Override
    public void loadUserIcon(List<Post> posts, UserProfileImagesBatchLoader.LoadIconListener loadIconListener) {
        /*if (subredditOrUserIcons.containsKey(subredditOrUserFullname)) {
            loadIconListener.loadIconSuccess(subredditOrUserFullname, subredditOrUserIcons.get(subredditOrUserFullname));
        }*/

        mPostViewModel.loadAuthorIcons(posts, loadIconListener);
    }

    @Override
    protected void showErrorView(int stringResId) {
        if (mActivity != null && isAdded()) {
            binding.swipeRefreshLayoutPostFragment.setRefreshing(false);
            binding.fetchPostInfoLinearLayoutPostFragment.setVisibility(View.VISIBLE);
            binding.fetchPostInfoTextViewPostFragment.setText(stringResId);
            mGlide.load(R.drawable.error_image).into(binding.fetchPostInfoImageViewPostFragment);
        }
    }

    @Override
    protected void showErrorView(String errorMessage) {
        if (mActivity != null && isAdded()) {
            binding.swipeRefreshLayoutPostFragment.setRefreshing(false);
            binding.fetchPostInfoLinearLayoutPostFragment.setVisibility(View.VISIBLE);
            binding.fetchPostInfoTextViewPostFragment.setText(errorMessage);
            mGlide.load(R.drawable.error_image).into(binding.fetchPostInfoImageViewPostFragment);
        }
    }

    @NonNull
    @Override
    protected SwipeRefreshLayout getSwipeRefreshLayout() {
        return binding.swipeRefreshLayoutPostFragment;
    }

    @NonNull
    @Override
    protected RecyclerView getPostRecyclerView() {
        return binding.recyclerViewPostFragment;
    }

    @Nullable
    @Override
    protected PostRecyclerViewAdapter getPostAdapter() {
        return mAdapter;
    }

    @Override
    public void changeNSFW(boolean nsfw) {
        Objects.requireNonNull(postFilter).allowNSFW = nsfw;
        if (mPostViewModel != null) {
            mPostViewModel.changePostFilter(postFilter);
        }
    }

    @Override
    public void changePostLayout(int postLayout, boolean temporary) {
        this.postLayout = postLayout;
        if (!temporary) {
            switch (postType) {
                case PostType.FRONT_PAGE:
                case PostType.ANONYMOUS_FRONT_PAGE:
                    mPostLayoutSharedPreferences.edit().putInt(SharedPreferencesUtils.POST_LAYOUT_FRONT_PAGE_POST, postLayout).apply();
                    break;
                case PostType.SUBREDDIT:
                    mPostLayoutSharedPreferences.edit().putInt(SharedPreferencesUtils.POST_LAYOUT_SUBREDDIT_POST_BASE + subredditName, postLayout).apply();
                    break;
                case PostType.USER:
                    mPostLayoutSharedPreferences.edit().putInt(SharedPreferencesUtils.POST_LAYOUT_USER_POST_BASE + username, postLayout).apply();
                    break;
                case PostType.SEARCH:
                    mPostLayoutSharedPreferences.edit().putInt(SharedPreferencesUtils.POST_LAYOUT_SEARCH_POST, postLayout).apply();
                    break;
                case PostType.MULTIREDDIT:
                case PostType.ANONYMOUS_MULTIREDDIT:
                    mPostLayoutSharedPreferences.edit().putInt(SharedPreferencesUtils.POST_LAYOUT_MULTI_REDDIT_POST_BASE + multiRedditPath, postLayout).apply();
                    break;
            }
        }

        int previousPosition = -1;
        if (mLinearLayoutManager != null) {
            previousPosition = mLinearLayoutManager.findFirstVisibleItemPosition();
        } else if (mStaggeredGridLayoutManager != null) {
            int[] into = new int[mStaggeredGridLayoutManager.getSpanCount()];
            previousPosition = mStaggeredGridLayoutManager.findFirstVisibleItemPositions(into)[0];
        }
        int nColumns = getNColumns(getResources());
        if (nColumns == 1) {
            mLinearLayoutManager = new LinearLayoutManagerBugFixed(mActivity);
            if (binding.recyclerViewPostFragment.getItemDecorationCount() > 0) {
                binding.recyclerViewPostFragment.removeItemDecorationAt(0);
            }
            binding.recyclerViewPostFragment.setLayoutManager(mLinearLayoutManager);
            mStaggeredGridLayoutManager = null;
        } else {
            mStaggeredGridLayoutManager = new StaggeredGridLayoutManager(nColumns, StaggeredGridLayoutManager.VERTICAL);
            if (binding.recyclerViewPostFragment.getItemDecorationCount() > 0) {
                binding.recyclerViewPostFragment.removeItemDecorationAt(0);
            }
            binding.recyclerViewPostFragment.setLayoutManager(mStaggeredGridLayoutManager);
            StaggeredGridLayoutManagerItemOffsetDecoration itemDecoration =
                    new StaggeredGridLayoutManagerItemOffsetDecoration(mActivity, R.dimen.staggeredLayoutManagerItemOffset, nColumns);
            binding.recyclerViewPostFragment.addItemDecoration(itemDecoration);
            mLinearLayoutManager = null;
        }

        if (previousPosition > 0) {
            binding.recyclerViewPostFragment.scrollToPosition(previousPosition);
        }

        if (mAdapter != null) {
            mAdapter.setPostLayout(postLayout);
            refreshAdapter();
        }

        applyMediaOnlyPosts();
    }

    @Override
    protected void applyMediaOnlyPosts() {
        if (mPostViewModel != null) {
            mPostViewModel.setMediaOnly(shouldShowMediaOnlyPosts());
        }
    }

    @Override
    public void applyTheme() {
        binding.swipeRefreshLayoutPostFragment.setProgressBackgroundColorSchemeColor(mCustomThemeWrapper.getCircularProgressBarBackground());
        binding.swipeRefreshLayoutPostFragment.setColorSchemeColors(mCustomThemeWrapper.getColorAccent());
        binding.fetchPostInfoTextViewPostFragment.setTextColor(mCustomThemeWrapper.getSecondaryTextColor());
        if (mActivity.typeface != null) {
            binding.fetchPostInfoTextViewPostFragment.setTypeface(mActivity.typeface);
        }
    }

    @Override
    public void hideReadPosts() {
        mPostViewModel.hideReadPosts();
    }

    @Override
    public void changePostFilter(PostFilter postFilter) {
        this.postFilter = postFilter;
        if (mPostViewModel != null) {
            mPostViewModel.changePostFilter(postFilter);
        }
    }

    @Override
    @Nullable
    public PostFilter getPostFilter() {
        return postFilter;
    }

    @Override
    public void filterPosts() {
        if (postType == PostType.SEARCH) {
            Intent intent = new Intent(mActivity, CustomizePostFilterActivity.class);
            intent.putExtra(FilteredPostsActivity.EXTRA_NAME, subredditName);
            intent.putExtra(FilteredPostsActivity.EXTRA_QUERY, query);
            intent.putExtra(FilteredPostsActivity.EXTRA_TRENDING_SOURCE, trendingSource);
            intent.putExtra(FilteredPostsActivity.EXTRA_POST_TYPE, postType);
            intent.putExtra(CustomizePostFilterActivity.EXTRA_START_FILTERED_POSTS_WHEN_FINISH, true);
            startActivity(intent);
        } else if (postType == PostType.SUBREDDIT) {
            Intent intent = new Intent(mActivity, CustomizePostFilterActivity.class);
            intent.putExtra(FilteredPostsActivity.EXTRA_NAME, subredditName);
            intent.putExtra(FilteredPostsActivity.EXTRA_POST_TYPE, postType);
            intent.putExtra(CustomizePostFilterActivity.EXTRA_START_FILTERED_POSTS_WHEN_FINISH, true);
            startActivity(intent);
        } else if (postType == PostType.MULTIREDDIT || postType == PostType.ANONYMOUS_MULTIREDDIT) {
            Intent intent = new Intent(mActivity, CustomizePostFilterActivity.class);
            intent.putExtra(FilteredPostsActivity.EXTRA_NAME, multiRedditPath);
            intent.putExtra(FilteredPostsActivity.EXTRA_QUERY, query);
            intent.putExtra(FilteredPostsActivity.EXTRA_POST_TYPE, postType);
            intent.putExtra(CustomizePostFilterActivity.EXTRA_START_FILTERED_POSTS_WHEN_FINISH, true);
            startActivity(intent);
        } else if (postType == PostType.USER) {
            Intent intent = new Intent(mActivity, CustomizePostFilterActivity.class);
            intent.putExtra(FilteredPostsActivity.EXTRA_NAME, username);
            intent.putExtra(FilteredPostsActivity.EXTRA_POST_TYPE, postType);
            intent.putExtra(FilteredPostsActivity.EXTRA_USER_WHERE, where);
            intent.putExtra(CustomizePostFilterActivity.EXTRA_START_FILTERED_POSTS_WHEN_FINISH, true);
            startActivity(intent);
        } else {
            Intent intent = new Intent(mActivity, CustomizePostFilterActivity.class);
            intent.putExtra(FilteredPostsActivity.EXTRA_NAME, mActivity.getString(R.string.best));
            intent.putExtra(FilteredPostsActivity.EXTRA_POST_TYPE, postType);
            intent.putExtra(CustomizePostFilterActivity.EXTRA_START_FILTERED_POSTS_WHEN_FINISH, true);
            startActivity(intent);
        }
    }

    @Override
    public boolean getIsNsfwSubreddit() {
        if (mActivity instanceof ViewSubredditDetailActivity) {
            return ((ViewSubredditDetailActivity) mActivity).isNsfwSubreddit();
        } else if (mActivity instanceof FilteredPostsActivity) {
            return ((FilteredPostsActivity) mActivity).isNsfwSubreddit();
        } else {
            return false;
        }
    }

    @Subscribe
    public void onChangeNColumnsEvent(ChangeNColumnsEvent changeNColumnsEvent) {
        // Re-apply the current layout so getNColumns() is re-read and the layout manager rebuilt.
        changePostLayout(postLayout, true);
    }

    @Subscribe
    public void onChangePostHistorySettingsEvent(ChangePostHistorySettingsEvent event) {
        if (mAdapter != null) {
            mAdapter.setMarkPostsAsReadSettings(event.markPostsAsRead, event.markPostsAsReadAfterVoting,
                    event.markPostsAsReadOnScroll);
        }
    }

    @Subscribe
    public void onChangeAutoplayVideoControllerUIEvent(ChangeAutoplayVideoControllerUIEvent event) {
        if (mAdapter != null) {
            // The controller UI is chosen in onCreateViewHolder, so re-attach the adapter to
            // recreate the view holders with the new layout.
            mAdapter.setLegacyAutoplayVideoControllerUI(event.legacyAutoplayVideoControllerUI);
            refreshAdapter();
        }
    }

    @Subscribe
    public void onChangeDefaultPostLayoutEvent(ChangeDefaultPostLayoutEvent changeDefaultPostLayoutEvent) {
        Bundle bundle = getArguments();
        if (bundle != null) {
            switch (postType) {
                case PostType.SUBREDDIT:
                    if (!mPostLayoutSharedPreferences.contains(SharedPreferencesUtils.POST_LAYOUT_SUBREDDIT_POST_BASE + bundle.getString(EXTRA_NAME))) {
                        changePostLayout(changeDefaultPostLayoutEvent.defaultPostLayout, true);
                    }
                    break;
                case PostType.USER:
                    if (!mPostLayoutSharedPreferences.contains(SharedPreferencesUtils.POST_LAYOUT_USER_POST_BASE + bundle.getString(EXTRA_USER_NAME))) {
                        changePostLayout(changeDefaultPostLayoutEvent.defaultPostLayout, true);
                    }
                    break;
                case PostType.MULTIREDDIT:
                    if (!mPostLayoutSharedPreferences.contains(SharedPreferencesUtils.POST_LAYOUT_MULTI_REDDIT_POST_BASE + bundle.getString(EXTRA_NAME))) {
                        changePostLayout(changeDefaultPostLayoutEvent.defaultPostLayout, true);
                    }
                    break;
                case PostType.SEARCH:
                    if (!mPostLayoutSharedPreferences.contains(SharedPreferencesUtils.POST_LAYOUT_SEARCH_POST)) {
                        changePostLayout(changeDefaultPostLayoutEvent.defaultPostLayout, true);
                    }
                    break;
                case PostType.FRONT_PAGE:
                    if (!mPostLayoutSharedPreferences.contains(SharedPreferencesUtils.POST_LAYOUT_FRONT_PAGE_POST)) {
                        changePostLayout(changeDefaultPostLayoutEvent.defaultPostLayout, true);
                    }
                    break;
            }
        }
    }

    @Subscribe
    public void onChangeDefaultPostLayoutUnfoldedEvent(ChangeDefaultPostLayoutUnfoldedEvent event) {
        boolean foldEnabled = mSharedPreferences.getBoolean(SharedPreferencesUtils.ENABLE_FOLD_SUPPORT, false);
        boolean isTablet = getResources().getBoolean(R.bool.isTablet);
        if (foldEnabled && isTablet) {
            Bundle bundle = getArguments();
            if (bundle != null) {
                switch (postType) {
                    case PostType.SUBREDDIT:
                        if (!mPostLayoutSharedPreferences.contains(SharedPreferencesUtils.POST_LAYOUT_SUBREDDIT_POST_BASE + bundle.getString(EXTRA_NAME))) {
                            changePostLayout(event.defaultPostLayoutUnfolded, true);
                        }
                        break;
                    case PostType.USER:
                        if (!mPostLayoutSharedPreferences.contains(SharedPreferencesUtils.POST_LAYOUT_USER_POST_BASE + bundle.getString(EXTRA_USER_NAME))) {
                            changePostLayout(event.defaultPostLayoutUnfolded, true);
                        }
                        break;
                    case PostType.MULTIREDDIT:
                        if (!mPostLayoutSharedPreferences.contains(SharedPreferencesUtils.POST_LAYOUT_MULTI_REDDIT_POST_BASE + bundle.getString(EXTRA_NAME))) {
                            changePostLayout(event.defaultPostLayoutUnfolded, true);
                        }
                        break;
                    case PostType.SEARCH:
                        if (!mPostLayoutSharedPreferences.contains(SharedPreferencesUtils.POST_LAYOUT_SEARCH_POST)) {
                            changePostLayout(event.defaultPostLayoutUnfolded, true);
                        }
                        break;
                    case PostType.FRONT_PAGE:
                        if (!mPostLayoutSharedPreferences.contains(SharedPreferencesUtils.POST_LAYOUT_FRONT_PAGE_POST)) {
                            changePostLayout(event.defaultPostLayoutUnfolded, true);
                        }
                        break;
                }
            }
        }
    }

    @Subscribe
    @Override
    public void onChangeNetworkStatusEvent(ChangeNetworkStatusEvent changeNetworkStatusEvent) {
        if (mAdapter != null) {
            String autoplay = Objects.requireNonNull(mSharedPreferences.getString(SharedPreferencesUtils.VIDEO_AUTOPLAY, SharedPreferencesUtils.VIDEO_AUTOPLAY_VALUE_NEVER));
            String dataSavingMode = Objects.requireNonNull(mSharedPreferences.getString(SharedPreferencesUtils.DATA_SAVING_MODE, SharedPreferencesUtils.DATA_SAVING_MODE_OFF));
            boolean stateChanged = false;
            if (autoplay.equals(SharedPreferencesUtils.VIDEO_AUTOPLAY_VALUE_ON_WIFI)) {
                mAdapter.setAutoplay(changeNetworkStatusEvent.connectedNetwork == Utils.NETWORK_TYPE_WIFI);
                stateChanged = true;
            }
            if (dataSavingMode.equals(SharedPreferencesUtils.DATA_SAVING_MODE_ONLY_ON_CELLULAR_DATA)) {
                mAdapter.setDataSavingMode(changeNetworkStatusEvent.connectedNetwork == Utils.NETWORK_TYPE_CELLULAR);
                stateChanged = true;
            }

            if (stateChanged) {
                refreshAdapter();
            }
        }
    }

    @Subscribe
    public void onChangeSavePostFeedScrolledPositionEvent(ChangeSavePostFeedScrolledPositionEvent changeSavePostFeedScrolledPositionEvent) {
        savePostFeedScrolledPosition = changeSavePostFeedScrolledPositionEvent.savePostFeedScrolledPosition;
    }

    @Subscribe
    public void onNeedForPostListFromPostRecyclerViewAdapterEvent(NeedForPostListFromPostFragmentEvent event) {
        if (postFragmentId == event.postFragmentTimeId && mAdapter != null) {
            EventBus.getDefault().post(new ProvidePostListToViewPostDetailActivityEvent(postFragmentId,
                    new ArrayList<>(mAdapter.snapshot()), postType, subredditName,
                    concatenatedSubredditNames, username, where, multiRedditPath, query, trendingSource,
                    ReadPostType.INVALID, postFilter, sortType, readPostsList,
                    shouldShowMediaOnlyPosts()));
        }
    }

    @Subscribe
    public void onFlairSelectedEvent(FlairSelectedEvent event) {

    }

    @Override
    protected void refreshAdapter() {
        int previousPosition = -1;
        if (mLinearLayoutManager != null) {
            previousPosition = mLinearLayoutManager.findFirstVisibleItemPosition();
        } else if (mStaggeredGridLayoutManager != null) {
            int[] into = new int[mStaggeredGridLayoutManager.getSpanCount()];
            previousPosition = mStaggeredGridLayoutManager.findFirstVisibleItemPositions(into)[0];
        }

        RecyclerView.LayoutManager layoutManager = binding.recyclerViewPostFragment.getLayoutManager();
        binding.recyclerViewPostFragment.setAdapter(null);
        binding.recyclerViewPostFragment.setLayoutManager(null);
        binding.recyclerViewPostFragment.setAdapter(mAdapter);
        binding.recyclerViewPostFragment.setLayoutManager(layoutManager);
        if (previousPosition > 0) {
            binding.recyclerViewPostFragment.scrollToPosition(previousPosition);
        }
    }

    public void goBackToTop() {
        if (mLinearLayoutManager != null) {
            mLinearLayoutManager.scrollToPositionWithOffset(0, 0);
            if (isInLazyMode) {
                lazyModeRunnable.resetOldPosition();
            }
        } else if (mStaggeredGridLayoutManager != null) {
            mStaggeredGridLayoutManager.scrollToPositionWithOffset(0, 0);
            if (isInLazyMode) {
                lazyModeRunnable.resetOldPosition();
            }
        }
    }

    public SortType getSortType() {
        return sortType;
    }

    @PostType
    public int getPostType() {
        return postType;
    }

    @Override
    public void onPause() {
        super.onPause();
        if (isInLazyMode) {
            pauseLazyMode(false);
        }
        if (mAdapter != null) {
            binding.recyclerViewPostFragment.onWindowVisibilityChanged(View.GONE);
        }
    }

    @Override
    public void onDestroyView() {
        binding.recyclerViewPostFragment.addOnWindowFocusChangedListener(null);
        // A trailing resume store still pending would fire against a torn-down view.
        binding.recyclerViewPostFragment.removeCallbacks(resumeStoreRunnable);
        super.onDestroyView();
    }

    private void onWindowFocusChanged(boolean hasWindowsFocus) {
        if (mAdapter != null) {
            mAdapter.setCanPlayVideo(hasWindowsFocus);
        }
    }

    @Override
    public void approvePost(@NonNull Post post, int position) {
        mPostViewModel.approvePost(post, position);
    }

    @Override
    public void removePost(@NonNull Post post, int position, boolean isSpam) {
        mPostViewModel.removePost(post, position, isSpam);
    }

    @Override
    public void toggleSticky(@NonNull Post post, int position) {
        mPostViewModel.toggleSticky(post, position);
    }

    @Override
    public void toggleLock(@NonNull Post post, int position) {
        mPostViewModel.toggleLock(post, position);
    }

    @Override
    public void toggleNSFW(@NonNull Post post, int position) {
        mPostViewModel.toggleNSFW(post, position);
    }

    @Override
    public void toggleSpoiler(@NonNull Post post, int position) {
        mPostViewModel.toggleSpoiler(post, position);
    }

    @Override
    public void changeFlair(@NonNull Post post, int position) {
        FlairBottomSheetFragment flairBottomSheetFragment = new FlairBottomSheetFragment();
        Bundle bundle = new Bundle();
        bundle.putString(FlairBottomSheetFragment.EXTRA_SUBREDDIT_NAME, post.getSubredditName());
        bundle.putLong(FlairBottomSheetFragment.EXTRA_CALLING_FRAGMENT_ID, postFragmentId);
        bundle.putBoolean(FlairBottomSheetFragment.EXTRA_SHOW_REMOVE_FLAIR_OPTION, true);
        flairBottomSheetFragment.setArguments(bundle);
        flairBottomSheetFragment.show(mActivity.getSupportFragmentManager(), flairBottomSheetFragment.getTag());
    }

    @Override
    public void toggleMod(@NonNull Post post, int position) {
        mPostViewModel.toggleMod(post, position);
    }

    @Override
    public void toggleNotification(@NotNull Post post, int position) {
        mPostViewModel.toggleNotification(post, position);
    }
}
