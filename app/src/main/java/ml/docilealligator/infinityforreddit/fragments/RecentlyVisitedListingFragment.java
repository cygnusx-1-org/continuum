package ml.docilealligator.infinityforreddit.fragments;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.OnApplyWindowInsetsListener;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Executor;
import javax.inject.Inject;
import javax.inject.Named;
import me.zhanghai.android.fastscroll.FastScrollerBuilder;
import ml.docilealligator.infinityforreddit.Infinity;
import ml.docilealligator.infinityforreddit.R;
import ml.docilealligator.infinityforreddit.RedditDataRoomDatabase;
import ml.docilealligator.infinityforreddit.activities.BaseActivity;
import ml.docilealligator.infinityforreddit.activities.ViewSubredditDetailActivity;
import ml.docilealligator.infinityforreddit.activities.ViewUserDetailActivity;
import ml.docilealligator.infinityforreddit.adapters.RecentlyVisitedRecyclerViewAdapter;
import ml.docilealligator.infinityforreddit.customtheme.CustomThemeWrapper;
import ml.docilealligator.infinityforreddit.customviews.LinearLayoutManagerBugFixed;
import ml.docilealligator.infinityforreddit.databinding.FragmentRecentlyVisitedListingBinding;
import ml.docilealligator.infinityforreddit.recentlyvisited.RecentlyVisitedType;
import ml.docilealligator.infinityforreddit.recentlyvisited.RecentlyVisitedViewModel;
import ml.docilealligator.infinityforreddit.utils.Utils;
import retrofit2.Retrofit;

/**
 * One tab of Recently Visited -- subreddits or users, chosen by {@link #EXTRA_TYPE}.
 */
public class RecentlyVisitedListingFragment extends Fragment implements FragmentCommunicator {

    public static final String EXTRA_TYPE = "ET";
    /** Search query in effect when the tab was created; "%" matches everything. */
    public static final String EXTRA_SEARCH_QUERY = "ESQ";

    @Inject
    @Named("oauth")
    Retrofit mOauthRetrofit;
    @Inject
    @Named("no_oauth")
    Retrofit mRetrofit;
    @Inject
    @Named("default")
    SharedPreferences mSharedPreferences;
    @Inject
    RedditDataRoomDatabase mRedditDataRoomDatabase;
    @Inject
    CustomThemeWrapper mCustomThemeWrapper;
    @Inject
    Executor mExecutor;

    private BaseActivity mActivity;
    private FragmentRecentlyVisitedListingBinding binding;
    private RecentlyVisitedViewModel mViewModel;
    @RecentlyVisitedType
    private int mType;

    private Set<String> mAddedNames = new HashSet<>();
    private Set<String> mSavedNames = new HashSet<>();

    public RecentlyVisitedListingFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentRecentlyVisitedListingBinding.inflate(inflater, container, false);

        ((Infinity) mActivity.getApplication()).getAppComponent().inject(this);

        Bundle arguments = getArguments();
        mType = arguments == null ? RecentlyVisitedType.SUBREDDIT
                : arguments.getInt(EXTRA_TYPE, RecentlyVisitedType.SUBREDDIT);

        applyTheme();

        if (mActivity.isImmersiveInterfaceRespectForcedEdgeToEdge()) {
            ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), new OnApplyWindowInsetsListener() {
                @NonNull
                @Override
                public WindowInsetsCompat onApplyWindowInsets(@NonNull View v, @NonNull WindowInsetsCompat insets) {
                    Insets allInsets = Utils.getInsets(insets, false, mActivity.isForcedImmersiveInterface());
                    binding.recyclerViewRecentlyVisitedListingFragment.setPadding(0, 0, 0, allInsets.bottom);
                    return WindowInsetsCompat.CONSUMED;
                }
            });
        }

        binding.recyclerViewRecentlyVisitedListingFragment.setLayoutManager(new LinearLayoutManagerBugFixed(mActivity));
        RecentlyVisitedRecyclerViewAdapter adapter = new RecentlyVisitedRecyclerViewAdapter(mActivity,
                mExecutor, mOauthRetrofit, mRetrofit, mRedditDataRoomDatabase, mCustomThemeWrapper,
                mActivity.accessToken, mActivity.accountName, mType, recentlyVisited -> {
                    Intent intent;
                    if (mType == RecentlyVisitedType.USER) {
                        intent = new Intent(mActivity, ViewUserDetailActivity.class);
                        intent.putExtra(ViewUserDetailActivity.EXTRA_USER_NAME_KEY, recentlyVisited.getName());
                    } else {
                        intent = new Intent(mActivity, ViewSubredditDetailActivity.class);
                        intent.putExtra(ViewSubredditDetailActivity.EXTRA_SUBREDDIT_NAME_KEY, recentlyVisited.getName());
                    }
                    mActivity.startActivity(intent);
                });
        binding.recyclerViewRecentlyVisitedListingFragment.setAdapter(adapter);
        new FastScrollerBuilder(binding.recyclerViewRecentlyVisitedListingFragment).useMd2Style().build();

        mViewModel = new ViewModelProvider(this,
                new RecentlyVisitedViewModel.Factory(mRedditDataRoomDatabase, mActivity.accountName, mType))
                .get(RecentlyVisitedViewModel.class);
        if (arguments != null) {
            String searchQuery = arguments.getString(EXTRA_SEARCH_QUERY);
            if (searchQuery != null && !"%%".equals(searchQuery)) {
                mViewModel.setSearchQuery(searchQuery);
            }
        }

        mViewModel.getRecentlyVisited().observe(getViewLifecycleOwner(), recentlyVisited -> {
            if (recentlyVisited == null || recentlyVisited.isEmpty()) {
                binding.recyclerViewRecentlyVisitedListingFragment.setVisibility(View.GONE);
                binding.noRecentlyVisitedLinearLayoutRecentlyVisitedListingFragment.setVisibility(View.VISIBLE);
            } else {
                binding.noRecentlyVisitedLinearLayoutRecentlyVisitedListingFragment.setVisibility(View.GONE);
                binding.recyclerViewRecentlyVisitedListingFragment.setVisibility(View.VISIBLE);
            }
            adapter.setRecentlyVisited(recentlyVisited);
        });

        // The add control is derived from the subscription tables rather than latched on the row,
        // so unsubscribing anywhere else brings it back here.
        if (mType == RecentlyVisitedType.USER) {
            mRedditDataRoomDatabase.subscribedUserDao().getFollowedUserNames(mActivity.accountName)
                    .observe(getViewLifecycleOwner(), names -> {
                        mAddedNames = lowercased(names);
                        adapter.setSubscriptionState(mAddedNames, mSavedNames);
                    });
            mRedditDataRoomDatabase.subscribedUserDao().getSavedUserNames(mActivity.accountName)
                    .observe(getViewLifecycleOwner(), names -> {
                        mSavedNames = lowercased(names);
                        adapter.setSubscriptionState(mAddedNames, mSavedNames);
                    });
        } else {
            mRedditDataRoomDatabase.subscribedSubredditDao().getSubscribedSubredditNames(mActivity.accountName)
                    .observe(getViewLifecycleOwner(), names -> {
                        mAddedNames = lowercased(names);
                        adapter.setSubscriptionState(mAddedNames, mSavedNames);
                    });
        }

        return binding.getRoot();
    }

    private static Set<String> lowercased(@Nullable List<String> names) {
        Set<String> set = new HashSet<>();
        if (names != null) {
            for (String name : names) {
                if (name != null) {
                    set.add(name.toLowerCase(Locale.US));
                }
            }
        }
        return set;
    }

    @Override
    public void applyTheme() {
        binding.errorTextViewRecentlyVisitedListingFragment.setTextColor(mCustomThemeWrapper.getSecondaryTextColor());
        binding.errorTextViewRecentlyVisitedListingFragment.setText(
                mType == RecentlyVisitedType.USER
                        ? R.string.no_users_visited_yet : R.string.no_subreddits_visited_yet);
        if (mActivity.contentTypeface != null) {
            binding.errorTextViewRecentlyVisitedListingFragment.setTypeface(mActivity.contentTypeface);
        }
    }

    public void setSearchQuery(String searchQuery) {
        if (mViewModel != null) {
            mViewModel.setSearchQuery(searchQuery);
        }
    }

    public void goBackToTop() {
        binding.recyclerViewRecentlyVisitedListingFragment.scrollToPosition(0);
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        mActivity = (BaseActivity) context;
    }
}
