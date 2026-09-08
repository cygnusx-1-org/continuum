package ml.docilealligator.infinityforreddit.fragments;


import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.res.ResourcesCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.OnApplyWindowInsetsListener;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.Executor;
import javax.inject.Inject;
import javax.inject.Named;
import ml.docilealligator.infinityforreddit.CommentModerationActionHandler;
import ml.docilealligator.infinityforreddit.Infinity;
import ml.docilealligator.infinityforreddit.NetworkState;
import ml.docilealligator.infinityforreddit.R;
import ml.docilealligator.infinityforreddit.RecyclerViewContentScrollingInterface;
import ml.docilealligator.infinityforreddit.RedditDataRoomDatabase;
import ml.docilealligator.infinityforreddit.account.Account;
import ml.docilealligator.infinityforreddit.activities.ActivityToolbarInterface;
import ml.docilealligator.infinityforreddit.activities.BaseActivity;
import ml.docilealligator.infinityforreddit.adapters.CommentsListingRecyclerViewAdapter;
import ml.docilealligator.infinityforreddit.comment.Comment;
import ml.docilealligator.infinityforreddit.comment.CommentViewModel;
import ml.docilealligator.infinityforreddit.customtheme.CustomThemeWrapper;
import ml.docilealligator.infinityforreddit.customviews.AdjustableTouchSlopItemTouchHelper;
import ml.docilealligator.infinityforreddit.customviews.LinearLayoutManagerBugFixed;
import ml.docilealligator.infinityforreddit.databinding.FragmentCommentsListingBinding;
import ml.docilealligator.infinityforreddit.events.ChangeAutoplayCommentGifEvent;
import ml.docilealligator.infinityforreddit.events.ChangeNetworkStatusEvent;
import ml.docilealligator.infinityforreddit.resume.FeedResumeState;
import ml.docilealligator.infinityforreddit.resume.ResumeState;
import ml.docilealligator.infinityforreddit.resume.ScrollAnchor;
import ml.docilealligator.infinityforreddit.thing.ReplyNotificationsToggle;
import ml.docilealligator.infinityforreddit.thing.SaveThing;
import ml.docilealligator.infinityforreddit.thing.SortType;
import ml.docilealligator.infinityforreddit.utils.SharedPreferencesUtils;
import ml.docilealligator.infinityforreddit.utils.Utils;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import retrofit2.Retrofit;


/**
 * A simple {@link Fragment} subclass.
 */
public class CommentsListingFragment extends Fragment implements FragmentCommunicator, CommentModerationActionHandler {

    public static final String EXTRA_USERNAME = "EN";
    public static final String EXTRA_ARE_SAVED_COMMENTS = "EISC";
    public static final String EXTRA_ARE_LOCAL_SAVED_COMMENTS = "EIALSC";
    // Sort carried by an opening deep link (e.g. /user/x/comments?sort=top), as SortType.Type/Time names.
    // Applied once on fresh creation; overrides the saved/default comment sort for that launch only.
    public static final String EXTRA_INITIAL_SORT_TYPE = "EIST";
    public static final String EXTRA_INITIAL_SORT_TIME = "EISTM";
    /**
     * How long the list may stay hidden waiting for the page that holds the recorded row. Longer
     * than a rotation restore's because this one spans however many network round trips it takes to
     * page back to where the user was.
     */
    private static final long RESUME_REVEAL_TIMEOUT_MS = 5000L;
    private static final String SORT_TYPE_STATE = "STS";
    private static final String SORT_TIME_STATE = "STMS";

    @SuppressWarnings("NullAway.Init")
    CommentViewModel mCommentViewModel;
    @Inject
    @Named("no_oauth")
    Retrofit mRetrofit;
    @Inject
    @Named("oauth")
    Retrofit mOauthRetrofit;
    @Inject
    RedditDataRoomDatabase mRedditDataRoomDatabase;
    @Inject
    @Named("default")
    SharedPreferences mSharedPreferences;
    @Inject
    @Named("sort_type")
    SharedPreferences mSortTypeSharedPreferences;
    @Inject
    @Named("post_layout")
    SharedPreferences mPostLayoutSharedPreferences;
    @Inject
    @Named("current_account")
    SharedPreferences mCurrentAccountSharedPreferences;
    @Inject
    CustomThemeWrapper customThemeWrapper;
    @Inject
    Executor mExecutor;
    private BaseActivity mActivity;
    @Nullable
    private LinearLayoutManagerBugFixed mLinearLayoutManager;
    @SuppressWarnings("NullAway.Init")
    private CommentsListingRecyclerViewAdapter mAdapter;
    @SuppressWarnings("NullAway.Init")
    private SortType sortType;
    // True on a fresh (non-recreation) launch; gates the one-time deep-link sort override in bindView.
    private boolean freshCreation;
    // Live sort restored across a config change (captured from savedInstanceState in onCreateView,
    // consumed by bindView which has no savedInstanceState of its own since it is posted).
    @Nullable
    private String restoredSortTypeName;
    @Nullable
    private String restoredSortTimeName;
    private ColorDrawable backgroundSwipeRight;
    private ColorDrawable backgroundSwipeLeft;
    @SuppressWarnings("NullAway.Init")
    private Drawable drawableSwipeRight;
    @SuppressWarnings("NullAway.Init")
    private Drawable drawableSwipeLeft;
    private int swipeLeftAction;
    private int swipeRightAction;
    private float swipeActionThreshold;
    private AdjustableTouchSlopItemTouchHelper touchHelper;
    private boolean shouldSwipeBack;
    private FragmentCommentsListingBinding binding;
    /**
     * Resume where I left off. The record this screen was launched with, and the key naming the
     * listing it belongs to -- non-null only while the setting is on, which is what keeps the
     * capture below from costing anything to someone who never turned it on.
     */
    private final FeedResumeState resumeState = new FeedResumeState();
    private boolean resumePending;
    @Nullable
    private String resumeFeedKey;

    public CommentsListingFragment() {
        // Required empty public constructor
    }


    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentCommentsListingBinding.inflate(inflater, container, false);

        freshCreation = savedInstanceState == null;
        // Only on a fresh creation: across a rotation the RecyclerView restores its own position,
        // and replaying the launch record on top of that would scroll the list out from under the
        // user to where they were in the session before this one.
        if (savedInstanceState == null) {
            resumeState.read(getArguments());
        }
        if (savedInstanceState != null) {
            restoredSortTypeName = savedInstanceState.getString(SORT_TYPE_STATE);
            restoredSortTimeName = savedInstanceState.getString(SORT_TIME_STATE);
        }

        ((Infinity) mActivity.getApplication()).getAppComponent().inject(this);

        EventBus.getDefault().register(this);

        applyTheme();


        if (mActivity.isImmersiveInterfaceRespectForcedEdgeToEdge()) {
            ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), new OnApplyWindowInsetsListener() {
                @NonNull
                @Override
                public WindowInsetsCompat onApplyWindowInsets(@NonNull View v, @NonNull WindowInsetsCompat insets) {
                    Insets allInsets = Utils.getInsets(insets, false, mActivity.isForcedImmersiveInterface());
                    binding.recyclerViewCommentsListingFragment.setPadding(
                            0, 0, 0, allInsets.bottom
                    );
                    return WindowInsetsCompat.CONSUMED;
                }
            });
            //binding.recyclerViewCommentsListingFragment.setPadding(0, 0, 0, mActivity.getNavBarHeight());
        }/* else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && mSharedPreferences.getBoolean(SharedPreferencesUtils.IMMERSIVE_INTERFACE_KEY, true)) {
            int navBarResourceId = resources.getIdentifier("navigation_bar_height", "dimen", "android");
            if (navBarResourceId > 0) {
                binding.recyclerViewCommentsListingFragment.setPadding(0, 0, 0, resources.getDimensionPixelSize(navBarResourceId));
            }
        }*/

        boolean enableSwipeAction = mSharedPreferences.getBoolean(SharedPreferencesUtils.ENABLE_SWIPE_ACTION, false);
        boolean vibrateWhenActionTriggered = mSharedPreferences.getBoolean(SharedPreferencesUtils.VIBRATE_WHEN_ACTION_TRIGGERED, true);
        swipeActionThreshold = SharedPreferencesUtils.getFloat(mSharedPreferences, SharedPreferencesUtils.SWIPE_ACTION_THRESHOLD, "0.3");
        swipeRightAction = SharedPreferencesUtils.getInt(mSharedPreferences, SharedPreferencesUtils.SWIPE_RIGHT_ACTION, "1");
        swipeLeftAction = SharedPreferencesUtils.getInt(mSharedPreferences, SharedPreferencesUtils.SWIPE_LEFT_ACTION, "0");
        initializeSwipeActionDrawable();
        touchHelper = new AdjustableTouchSlopItemTouchHelper(new AdjustableTouchSlopItemTouchHelper.Callback() {
            boolean exceedThreshold = false;

            @Override
            public int getMovementFlags(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
                if (!(viewHolder instanceof CommentsListingRecyclerViewAdapter.CommentBaseViewHolder)) {
                    return makeMovementFlags(0, 0);
                }
                int swipeFlags = ItemTouchHelper.START | ItemTouchHelper.END;
                return makeMovementFlags(0, swipeFlags);
            }

            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public boolean isItemViewSwipeEnabled() {
                return true;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {}

            @Override
            public int convertToAbsoluteDirection(int flags, int layoutDirection) {
                if (shouldSwipeBack) {
                    shouldSwipeBack = false;
                    return 0;
                }
                return super.convertToAbsoluteDirection(flags, layoutDirection);
            }

            @Override
            public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, float dX, float dY, int actionState, boolean isCurrentlyActive) {
                View itemView = viewHolder.itemView;
                int horizontalOffset = (int) Utils.convertDpToPixel(16, mActivity);
                if (dX > 0) {
                    if (dX > (itemView.getRight() - itemView.getLeft()) * swipeActionThreshold) {
                        dX = (itemView.getRight() - itemView.getLeft()) * swipeActionThreshold;
                        if (!exceedThreshold && isCurrentlyActive) {
                            exceedThreshold = true;
                            if (vibrateWhenActionTriggered) {
                                itemView.setHapticFeedbackEnabled(true);
                                itemView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
                            }
                        }
                        backgroundSwipeRight.setBounds(0, itemView.getTop(), itemView.getRight(), itemView.getBottom());
                    } else {
                        exceedThreshold = false;
                        backgroundSwipeRight.setBounds(0, 0, 0, 0);
                    }

                    drawableSwipeRight.setBounds(itemView.getLeft() + ((int) dX) - horizontalOffset - drawableSwipeRight.getIntrinsicWidth(),
                            (itemView.getBottom() + itemView.getTop() - drawableSwipeRight.getIntrinsicHeight()) / 2,
                            itemView.getLeft() + ((int) dX) - horizontalOffset,
                            (itemView.getBottom() + itemView.getTop() + drawableSwipeRight.getIntrinsicHeight()) / 2);
                    backgroundSwipeRight.draw(c);
                    drawableSwipeRight.draw(c);
                } else if (dX < 0) {
                    if (-dX > (itemView.getRight() - itemView.getLeft()) * swipeActionThreshold) {
                        dX = -(itemView.getRight() - itemView.getLeft()) * swipeActionThreshold;
                        if (!exceedThreshold && isCurrentlyActive) {
                            exceedThreshold = true;
                            if (vibrateWhenActionTriggered) {
                                itemView.setHapticFeedbackEnabled(true);
                                itemView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
                            }
                        }
                        backgroundSwipeLeft.setBounds(0, itemView.getTop(), itemView.getRight(), itemView.getBottom());
                    } else {
                        exceedThreshold = false;
                        backgroundSwipeLeft.setBounds(0, 0, 0, 0);
                    }
                    drawableSwipeLeft.setBounds(itemView.getRight() + ((int) dX) + horizontalOffset,
                            (itemView.getBottom() + itemView.getTop() - drawableSwipeLeft.getIntrinsicHeight()) / 2,
                            itemView.getRight() + ((int) dX) + horizontalOffset + drawableSwipeLeft.getIntrinsicWidth(),
                            (itemView.getBottom() + itemView.getTop() + drawableSwipeLeft.getIntrinsicHeight()) / 2);
                    backgroundSwipeLeft.draw(c);
                    drawableSwipeLeft.draw(c);
                }

                if (!isCurrentlyActive && exceedThreshold) {
                    mAdapter.onItemSwipe(viewHolder, dX > 0 ? ItemTouchHelper.END : ItemTouchHelper.START, swipeLeftAction, swipeRightAction);
                    exceedThreshold = false;
                }
                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
            }

            @Override
            public float getSwipeThreshold(@NonNull RecyclerView.ViewHolder viewHolder) {
                return 100;
            }
        });

        binding.recyclerViewCommentsListingFragment.setOnTouchListener((view, motionEvent) -> {
            shouldSwipeBack = motionEvent.getAction() == MotionEvent.ACTION_CANCEL || motionEvent.getAction() == MotionEvent.ACTION_UP;
            return false;
        });

        if (enableSwipeAction) {
            touchHelper.attachToRecyclerView(
                    binding.recyclerViewCommentsListingFragment,
                    SharedPreferencesUtils.getFloat(mSharedPreferences, SharedPreferencesUtils.SWIPE_ACTION_SENSITIVITY_IN_COMMENTS, "5")
            );
        }

        new Handler().postDelayed(this::bindView, 0);

        return binding.getRoot();
    }

    private void bindView() {
        if (mActivity != null && !mActivity.isFinishing() && !mActivity.isDestroyed()) {
            mLinearLayoutManager = new LinearLayoutManagerBugFixed(mActivity);
            binding.recyclerViewCommentsListingFragment.setLayoutManager(mLinearLayoutManager);

            Bundle arguments = getArguments();
            if (arguments == null) {
                return;
            }
            String username = Objects.requireNonNull(arguments.getString(EXTRA_USERNAME));
            SortType overrideSortType = getOverrideSortType(arguments);
            if (overrideSortType != null) {
                sortType = overrideSortType;
            } else {
                String sort = Objects.requireNonNull(mSortTypeSharedPreferences.getString(SharedPreferencesUtils.SORT_TYPE_USER_COMMENT, SortType.Type.NEW.name()));
                if (sort.equals(SortType.Type.CONTROVERSIAL.name()) || sort.equals(SortType.Type.TOP.name())) {
                    String sortTime = Objects.requireNonNull(mSortTypeSharedPreferences.getString(SharedPreferencesUtils.SORT_TIME_USER_COMMENT, SortType.Time.ALL.name()));
                    sortType = new SortType(SortType.Type.valueOf(sort.toUpperCase(Locale.US)), SortType.Time.valueOf(sortTime.toUpperCase(Locale.US)));
                } else {
                    sortType = new SortType(SortType.Type.valueOf(sort.toUpperCase(Locale.US)));
                }
            }
            // The list is sorted correctly regardless, but the host toolbar's sort subtitle is only
            // set via its page-change callback, which can fire before this posted bindView assigns
            // sortType. Notify now that it is ready (mirrors PostFragment).
            if (mActivity instanceof ActivityToolbarInterface) {
                ((ActivityToolbarInterface) mActivity).displaySortType();
            }

            mAdapter = new CommentsListingRecyclerViewAdapter(mActivity, this, mOauthRetrofit, customThemeWrapper,
                    getResources().getConfiguration().locale, mSharedPreferences,
                    mActivity.accessToken, mActivity.accountName,
                    username, () -> mCommentViewModel.retryLoadingMore());

            binding.recyclerViewCommentsListingFragment.setAdapter(mAdapter);

            // Behind the setting, like every other entry point into the resume feature: with it off
            // the key stays null and neither the capture nor the restore below does any work.
            if (ResumeState.isEnabled(mActivity)) {
                resumeFeedKey = buildResumeFeedKey(username, arguments);
                // A record belongs to one listing. The pager hands the same one-shot record to
                // whichever page it builds first, so a record naming the posts feed must be dropped
                // here rather than applied to the comments the user was not looking at.
                resumePending = resumeState.isPending()
                        && resumeFeedKey.equals(resumeState.feedKey);
            }
            FeedResumeState.clearFrom(arguments);
            if (resumePending) {
                restoreAnchorWhenLoaded(resumeState.anchorPosition, resumeState.anchorOffset,
                        resumeState.expectedCount);
                resumePending = false;
            }

            if (mActivity instanceof RecyclerViewContentScrollingInterface) {
                binding.recyclerViewCommentsListingFragment.addOnScrollListener(new RecyclerView.OnScrollListener() {
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

            CommentViewModel.Factory factory;

            boolean areLocalSavedComments = arguments.getBoolean(EXTRA_ARE_LOCAL_SAVED_COMMENTS);
            if (mActivity.accountName.equals(Account.ANONYMOUS_ACCOUNT)) {
                factory = new CommentViewModel.Factory(mExecutor, mActivity.mHandler, mRetrofit,
                        null, mActivity.accountName, username, sortType,
                        arguments.getBoolean(EXTRA_ARE_SAVED_COMMENTS), areLocalSavedComments,
                        mRedditDataRoomDatabase);
            } else {
                factory = new CommentViewModel.Factory(mExecutor, mActivity.mHandler, mOauthRetrofit,
                        mActivity.accessToken, mActivity.accountName, username, sortType,
                        arguments.getBoolean(EXTRA_ARE_SAVED_COMMENTS), areLocalSavedComments,
                        mRedditDataRoomDatabase);
            }

            mCommentViewModel = new ViewModelProvider(this, factory).get(CommentViewModel.class);
            mCommentViewModel.getComments().observe(getViewLifecycleOwner(), comments -> mAdapter.submitList(comments));

            mCommentViewModel.hasComment().observe(getViewLifecycleOwner(), hasComment -> {
                binding.swipeRefreshLayoutViewCommentsListingFragment.setRefreshing(false);
                if (hasComment) {
                    binding.fetchCommentsInfoLinearLayoutCommentsListingFragment.setVisibility(View.GONE);
                } else {
                    binding.fetchCommentsInfoLinearLayoutCommentsListingFragment.setOnClickListener(null);
                    showErrorView(R.string.no_comments);
                }
            });

            mCommentViewModel.getInitialLoadingState().observe(getViewLifecycleOwner(), networkState -> {
                if (networkState.getStatus().equals(NetworkState.Status.SUCCESS)) {
                    binding.swipeRefreshLayoutViewCommentsListingFragment.setRefreshing(false);
                } else if (networkState.getStatus().equals(NetworkState.Status.FAILED)) {
                    binding.swipeRefreshLayoutViewCommentsListingFragment.setRefreshing(false);
                    binding.fetchCommentsInfoLinearLayoutCommentsListingFragment.setOnClickListener(view -> refresh());
                    showErrorView(R.string.load_comments_failed);
                } else {
                    binding.swipeRefreshLayoutViewCommentsListingFragment.setRefreshing(true);
                }
            });

            mCommentViewModel.getPaginationNetworkState().observe(getViewLifecycleOwner(), networkState -> mAdapter.setNetworkState(networkState));

            mCommentViewModel.commentModerationEventLiveData.observe(getViewLifecycleOwner(), moderationEvent -> {
                if (mAdapter != null) {
                    mAdapter.updateModdedStatus(moderationEvent.getPosition());
                }
                Toast.makeText(mActivity, moderationEvent.getToastMessageResId(), Toast.LENGTH_SHORT).show();
            });

            binding.swipeRefreshLayoutViewCommentsListingFragment.setOnRefreshListener(() -> mCommentViewModel.refresh());
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mAdapter != null) {
            mAdapter.setCanStartActivity(true);
        }
    }

    @Override
    public void onDestroy() {
        EventBus.getDefault().unregister(this);
        super.onDestroy();
    }

    public void changeSortType(SortType sortType) {
        mCommentViewModel.changeSortType(sortType);
        this.sortType = sortType;
    }

    /**
     * Resolves a sort that should take precedence over the saved/default comment sort: the live sort
     * restored across a config change, or a sort carried by an opening deep link on fresh creation.
     * Returns null to fall back to the saved/default sort. Malformed values are ignored.
     */
    @Nullable
    private SortType getOverrideSortType(Bundle arguments) {
        String typeName;
        String timeName;
        if (freshCreation) {
            typeName = arguments.getString(EXTRA_INITIAL_SORT_TYPE);
            timeName = arguments.getString(EXTRA_INITIAL_SORT_TIME);
        } else {
            typeName = restoredSortTypeName;
            timeName = restoredSortTimeName;
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
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (sortType != null) {
            outState.putString(SORT_TYPE_STATE, sortType.getType().name());
            if (sortType.getTime() != null) {
                outState.putString(SORT_TIME_STATE, sortType.getTime().name());
            }
        }
    }

    private void initializeSwipeActionDrawable() {
        if (swipeRightAction == SharedPreferencesUtils.SWIPE_ACITON_DOWNVOTE) {
            backgroundSwipeRight = new ColorDrawable(customThemeWrapper.getDownvoted());
            drawableSwipeRight = Objects.requireNonNull(ResourcesCompat.getDrawable(mActivity.getResources(), R.drawable.ic_arrow_downward_day_night_24dp, null));
        } else {
            backgroundSwipeRight = new ColorDrawable(customThemeWrapper.getUpvoted());
            drawableSwipeRight = Objects.requireNonNull(ResourcesCompat.getDrawable(mActivity.getResources(), R.drawable.ic_arrow_upward_day_night_24dp, null));
        }

        if (swipeLeftAction == SharedPreferencesUtils.SWIPE_ACITON_UPVOTE) {
            backgroundSwipeLeft = new ColorDrawable(customThemeWrapper.getUpvoted());
            drawableSwipeLeft = Objects.requireNonNull(ResourcesCompat.getDrawable(mActivity.getResources(), R.drawable.ic_arrow_upward_day_night_24dp, null));
        } else {
            backgroundSwipeLeft = new ColorDrawable(customThemeWrapper.getDownvoted());
            drawableSwipeLeft = Objects.requireNonNull(ResourcesCompat.getDrawable(mActivity.getResources(), R.drawable.ic_arrow_downward_day_night_24dp, null));
        }
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        this.mActivity = (BaseActivity) context;
    }

    @Override
    public void refresh() {
        binding.fetchCommentsInfoLinearLayoutCommentsListingFragment.setVisibility(View.GONE);
        mCommentViewModel.refresh();
        mAdapter.setNetworkState(null);
    }

    // Client-side search used by the Saved screen's (Local) Comments tabs.
    public void filterSaved(String query) {
        if (mCommentViewModel != null) {
            mCommentViewModel.search(query);
        }
    }

    // A comment was saved/unsaved in-app: drop the in-memory Saved search cache so a search in
    // progress refetches rather than re-surfacing the just-changed comment.
    public void onSavedThingChanged() {
        if (mCommentViewModel != null) {
            mCommentViewModel.invalidateInMemorySavedSearchCache();
        }
    }

    @Override
    public void applyTheme() {
        binding.swipeRefreshLayoutViewCommentsListingFragment.setProgressBackgroundColorSchemeColor(customThemeWrapper.getCircularProgressBarBackground());
        binding.swipeRefreshLayoutViewCommentsListingFragment.setColorSchemeColors(customThemeWrapper.getColorAccent());
        binding.fetchCommentsInfoTextViewCommentsListingFragment.setTextColor(customThemeWrapper.getSecondaryTextColor());
        if (mActivity.typeface != null) {
            binding.fetchCommentsInfoTextViewCommentsListingFragment.setTypeface(mActivity.typeface);
        }
    }

    private void showErrorView(int stringResId) {
        if (mActivity != null && isAdded()) {
            binding.swipeRefreshLayoutViewCommentsListingFragment.setRefreshing(false);
            binding.fetchCommentsInfoLinearLayoutCommentsListingFragment.setVisibility(View.VISIBLE);
            binding.fetchCommentsInfoTextViewCommentsListingFragment.setText(stringResId);
        }
    }

    public void goBackToTop() {
        if (mLinearLayoutManager != null) {
            mLinearLayoutManager.scrollToPositionWithOffset(0, 0);
        }
    }

    /**
     * The name of the listing this fragment is showing, for a resume record to be matched against.
     *
     * <p>The sort is part of it. A position means nothing under a different ordering -- comment 40
     * of New is not comment 40 of Top -- so a record written under one sort must not be applied
     * under another, and a key that spelled only the user's name would let it be.
     */
    @NonNull
    private String buildResumeFeedKey(@NonNull String username, @NonNull Bundle arguments) {
        StringBuilder key = new StringBuilder(mActivity.accountName)
                .append(".comments.")
                .append(username);
        if (arguments.getBoolean(EXTRA_ARE_SAVED_COMMENTS)) {
            key.append(arguments.getBoolean(EXTRA_ARE_LOCAL_SAVED_COMMENTS) ? ".localsaved" : ".saved");
        }
        key.append('.').append(sortType.getType().name());
        if (sortType.getTime() != null) {
            key.append('.').append(sortType.getTime().name());
        }
        return key.toString();
    }

    /**
     * Put the list back where the user left it, once the page holding that row has arrived.
     *
     * <p>Unlike the posts feed there is no cache of the comments themselves, so the rows come back
     * from the network a page at a time and the recorded row does not exist yet when this is
     * called. The list is hidden and each page that lands is asked whether it reached far enough;
     * while it has not, scrolling to the last loaded row is what asks the pager for the next one.
     *
     * <p>Bounded in both directions. {@code expectedCount} caps how far it will page -- a record is
     * only worth this many rows -- and {@link ScrollAnchor#applyHidden} reveals the list anyway if
     * the jump never lands. A comment deleted since the record was written shortens the list, so
     * "no more pages" reveals it too rather than waiting for a row that is never coming.
     */
    private void restoreAnchorWhenLoaded(int anchorPosition, int anchorOffset, int expectedCount) {
        if (anchorPosition == ScrollAnchor.NO_POSITION || binding == null || mAdapter == null) {
            return;
        }
        final RecyclerView recyclerView = binding.recyclerViewCommentsListingFragment;
        // Held rather than read off the field each time. This observer outlives the call that
        // registered it -- it waits on the network -- and a view recreated in the meantime runs
        // bindView again and puts a different adapter in the field. Unregistering against that one
        // throws IllegalStateException, having never been registered with it.
        final CommentsListingRecyclerViewAdapter adapter = mAdapter;
        if (adapter.getItemCount() > anchorPosition) {
            ScrollAnchor.applyHidden(recyclerView, anchorPosition, anchorOffset);
            return;
        }
        ScrollAnchor.hideUntilRestored(recyclerView, RESUME_REVEAL_TIMEOUT_MS);
        adapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            /** The size the last page left behind, to tell a page that grew it from one that did not. */
            private int lastCount = -1;

            @Override
            public void onChanged() {
                onListGrew();
            }

            @Override
            public void onItemRangeInserted(int positionStart, int itemCount) {
                onListGrew();
            }

            private void onListGrew() {
                int count = adapter.getItemCount();
                if (count > anchorPosition) {
                    done();
                    ScrollAnchor.applyHidden(recyclerView, anchorPosition, anchorOffset);
                    return;
                }
                if (count == 0 || count <= lastCount || count >= expectedCount) {
                    // Empty, or the listing has stopped growing, or it has given back everything
                    // the record said it held and still cannot reach the row -- comments have been
                    // deleted since. Either way the row is not coming; show what there is. The
                    // empty case is its own branch because the scroll below would be to -1.
                    done();
                    recyclerView.setVisibility(View.VISIBLE);
                    return;
                }
                lastCount = count;
                // Asking the pager for the next page. The list is invisible, so this is not a jump
                // the user sees.
                recyclerView.scrollToPosition(count - 1);
            }

            private void done() {
                adapter.unregisterAdapterDataObserver(this);
            }
        });
    }

    /**
     * Record where in the comment list the user is, for {@link ResumeState}.
     *
     * <p>By position rather than by comment id, which is the difference between this and the posts
     * feed: that one restores a cached list and can find its anchor by name, where this one is
     * rebuilt from the network in the order the sort gives it. Returns false, writing nothing, when
     * there is no row on screen to anchor on -- a record that named the listing but not the place
     * in it would reopen the tab at the top, which is not a resume.
     */
    public boolean captureResumeState(@NonNull Bundle out) {
        if (binding == null || mAdapter == null || resumeFeedKey == null) {
            return false;
        }
        ScrollAnchor.Anchor anchor =
                ScrollAnchor.captureTopmost(binding.recyclerViewCommentsListingFragment);
        return FeedResumeState.capture(out, resumeFeedKey, anchor, null, mAdapter.getItemCount());
    }

    public SortType getSortType() {
        return sortType;
    }

    public void editComment(Comment comment, int position) {
        if (mAdapter != null) {
            mAdapter.editComment(comment, position);
        }
    }

    public void editComment(String commentMarkdown, int position) {
        if (mAdapter != null) {
            mAdapter.editComment(commentMarkdown, position);
        }
    }

    public void toggleReplyNotifications(Comment comment, int position) {
        ReplyNotificationsToggle.toggleEnableNotification(new Handler(Looper.getMainLooper()), mOauthRetrofit,
                mActivity.accessToken, comment, new ReplyNotificationsToggle.SendNotificationListener() {
                    @Override
                    public void onSuccess() {
                        Toast.makeText(mActivity,
                                comment.isSendReplies() ? R.string.reply_notifications_disabled : R.string.reply_notifications_enabled,
                                Toast.LENGTH_SHORT).show();
                        mAdapter.toggleReplyNotifications(position);
                    }

                    @Override
                    public void onError() {
                        Toast.makeText(mActivity, R.string.toggle_reply_notifications_failed, Toast.LENGTH_SHORT).show();
                    }
                });
    }

    public void toggleSaveComment(Comment comment, int position) {
        if (comment.isSaved()) {
            SaveThing.unsaveThing(mOauthRetrofit, mActivity.accessToken, comment.getFullName(), new SaveThing.SaveThingListener() {
                @Override
                public void success() {
                    Toast.makeText(mActivity, R.string.comment_unsaved_success, Toast.LENGTH_SHORT).show();
                    comment.setSaved(false);
                    if (mAdapter != null) {
                        mAdapter.toggleSaveComment(comment, position);
                    }
                }

                @Override
                public void failed() {
                    Toast.makeText(mActivity, R.string.comment_unsaved_failed, Toast.LENGTH_SHORT).show();
                    comment.setSaved(true);
                    if (mAdapter != null) {
                        mAdapter.toggleSaveComment(comment, position);
                    }
                }
            });
        } else {
            SaveThing.saveThing(mOauthRetrofit, mActivity.accessToken, comment.getFullName(), new SaveThing.SaveThingListener() {
                @Override
                public void success() {
                    Toast.makeText(mActivity, R.string.comment_saved_success, Toast.LENGTH_SHORT).show();
                    comment.setSaved(true);
                    if (mAdapter != null) {
                        mAdapter.toggleSaveComment(comment, position);
                    }
                }

                @Override
                public void failed() {
                    Toast.makeText(mActivity, R.string.comment_saved_failed, Toast.LENGTH_SHORT).show();
                    comment.setSaved(false);
                    if (mAdapter != null) {
                        mAdapter.toggleSaveComment(comment, position);
                    }
                }
            });
        }
    }

    @Subscribe
    public void onChangeNetworkStatusEvent(ChangeNetworkStatusEvent changeNetworkStatusEvent) {
        if (mAdapter != null) {
            String dataSavingMode = Objects.requireNonNull(mSharedPreferences.getString(SharedPreferencesUtils.DATA_SAVING_MODE, SharedPreferencesUtils.DATA_SAVING_MODE_OFF));
            if (dataSavingMode.equals(SharedPreferencesUtils.DATA_SAVING_MODE_ONLY_ON_CELLULAR_DATA)) {
                if (mAdapter.setDataSavingMode(changeNetworkStatusEvent.connectedNetwork == Utils.NETWORK_TYPE_CELLULAR)) {
                    refreshAdapter(binding.recyclerViewCommentsListingFragment, mAdapter);
                }
            }
        }
    }

    @Subscribe
    public void onChangeAutoplayCommentGifEvent(ChangeAutoplayCommentGifEvent event) {
        if (mAdapter != null) {
            mAdapter.setAutoplayCommentGif(event.autoplayCommentGif);
            refreshAdapter(binding.recyclerViewCommentsListingFragment, mAdapter);
        }
    }

    private void refreshAdapter(RecyclerView recyclerView, RecyclerView.Adapter<RecyclerView.ViewHolder> adapter) {
        int previousPosition = -1;
        if (recyclerView.getLayoutManager() != null) {
            previousPosition = ((LinearLayoutManagerBugFixed) recyclerView.getLayoutManager()).findFirstVisibleItemPosition();
        }

        RecyclerView.LayoutManager layoutManager = recyclerView.getLayoutManager();
        recyclerView.setAdapter(null);
        recyclerView.setLayoutManager(null);
        recyclerView.setAdapter(adapter);
        recyclerView.setLayoutManager(layoutManager);

        if (previousPosition > 0) {
            recyclerView.scrollToPosition(previousPosition);
        }
    }

    @Override
    public void approveComment(@NonNull Comment comment, int position) {
        mCommentViewModel.approveComment(comment, position);
    }

    @Override
    public void removeComment(@NonNull Comment comment, int position, boolean isSpam) {
        mCommentViewModel.removeComment(comment, position, isSpam);
    }

    @Override
    public void toggleLock(@NonNull Comment comment, int position) {
        mCommentViewModel.toggleLock(comment, position);
    }

    @Override
    public void toggleMod(@NonNull Comment comment, int position) {
        mCommentViewModel.toggleMod(comment, position);
    }
}
