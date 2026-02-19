package ml.docilealligator.infinityforreddit.activities;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.core.graphics.Insets;
import androidx.core.view.OnApplyWindowInsetsListener;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.snackbar.Snackbar;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;

import java.util.concurrent.Executor;

import javax.inject.Inject;
import javax.inject.Named;

import ml.docilealligator.infinityforreddit.Infinity;
import ml.docilealligator.infinityforreddit.R;
import ml.docilealligator.infinityforreddit.RedditDataRoomDatabase;
import ml.docilealligator.infinityforreddit.adapters.SearchActivityRecyclerViewAdapter;
import ml.docilealligator.infinityforreddit.customtheme.CustomThemeWrapper;
import ml.docilealligator.infinityforreddit.databinding.ActivitySearchHistoryBinding;
import ml.docilealligator.infinityforreddit.events.SwitchAccountEvent;
import ml.docilealligator.infinityforreddit.multireddit.MultiReddit;
import ml.docilealligator.infinityforreddit.recentsearchquery.RecentSearchQuery;
import ml.docilealligator.infinityforreddit.recentsearchquery.RecentSearchQueryViewModel;
import ml.docilealligator.infinityforreddit.utils.Utils;

public class SearchHistoryActivity extends BaseActivity {

    public static final String EXTRA_ACCOUNT_NAME = "EAN";

    @Inject
    RedditDataRoomDatabase mRedditDataRoomDatabase;
    @Inject
    @Named("default")
    SharedPreferences mSharedPreferences;
    @Inject
    @Named("current_account")
    SharedPreferences mCurrentAccountSharedPreferences;
    @Inject
    CustomThemeWrapper mCustomThemeWrapper;
    @Inject
    Executor mExecutor;

    private ActivitySearchHistoryBinding binding;
    private SearchActivityRecyclerViewAdapter mAdapter;
    private Handler mHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ((Infinity) getApplication()).getAppComponent().inject(this);

        super.onCreate(savedInstanceState);

        binding = ActivitySearchHistoryBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        EventBus.getDefault().register(this);

        applyCustomTheme();

        attachSliderPanelIfApplicable();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Window window = getWindow();

            if (isChangeStatusBarIconColor()) {
                addOnOffsetChangedListener(binding.appbarLayoutSearchHistoryActivity);
            }

            if (isImmersiveInterfaceRespectForcedEdgeToEdge()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    window.setDecorFitsSystemWindows(false);
                } else {
                    window.setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
                }

                ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), new OnApplyWindowInsetsListener() {
                    @NonNull
                    @Override
                    public WindowInsetsCompat onApplyWindowInsets(@NonNull View v, @NonNull WindowInsetsCompat insets) {
                        Insets allInsets = Utils.getInsets(insets, false, isForcedImmersiveInterface());

                        setMargins(binding.toolbarSearchHistoryActivity,
                                allInsets.left,
                                allInsets.top,
                                allInsets.right,
                                BaseActivity.IGNORE_MARGIN);

                        binding.recyclerViewSearchHistoryActivity.setPadding(
                                allInsets.left,
                                0,
                                allInsets.right,
                                allInsets.bottom);

                        return WindowInsetsCompat.CONSUMED;
                    }
                });
            }
        }

        binding.appbarLayoutSearchHistoryActivity.setBackgroundColor(mCustomThemeWrapper.getColorPrimary());
        setSupportActionBar(binding.toolbarSearchHistoryActivity);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        setTitle(R.string.search_history);

        mHandler = new Handler();

        mAdapter = new SearchActivityRecyclerViewAdapter(this, mCustomThemeWrapper,
                new SearchActivityRecyclerViewAdapter.ItemOnClickListener() {
                    @Override
                    public void onClick(RecentSearchQuery recentSearchQuery, boolean searchImmediately) {
                        Intent intent = new Intent(SearchHistoryActivity.this, SearchResultActivity.class);
                        intent.putExtra(SearchResultActivity.EXTRA_QUERY, recentSearchQuery.getSearchQuery());
                        intent.putExtra(SearchResultActivity.EXTRA_SEARCH_IN_SUBREDDIT_OR_USER_NAME,
                                recentSearchQuery.getSearchInSubredditOrUserName());
                        if (recentSearchQuery.getMultiRedditPath() != null) {
                            MultiReddit multiReddit = MultiReddit.getDummyMultiReddit(recentSearchQuery.getMultiRedditPath());
                            if (multiReddit != null && recentSearchQuery.getMultiRedditDisplayName() != null) {
                                multiReddit.setDisplayName(recentSearchQuery.getMultiRedditDisplayName());
                            }
                            intent.putExtra(SearchResultActivity.EXTRA_SEARCH_IN_MULTIREDDIT, multiReddit);
                        }
                        intent.putExtra(SearchResultActivity.EXTRA_SEARCH_IN_THING_TYPE, recentSearchQuery.getSearchInThingType());
                        startActivity(intent);
                    }

                    @Override
                    public void onDelete(RecentSearchQuery recentSearchQuery) {
                        mExecutor.execute(() -> {
                            mRedditDataRoomDatabase.recentSearchQueryDao().deleteRecentSearchQueries(recentSearchQuery);
                            mHandler.post(() -> Snackbar.make(binding.getRoot(), R.string.recent_search_deleted, Snackbar.LENGTH_SHORT)
                                    .setAction(R.string.undo, v -> mExecutor.execute(() ->
                                            mRedditDataRoomDatabase.recentSearchQueryDao().insert(recentSearchQuery)))
                                    .show());
                        });
                    }
                });

        binding.recyclerViewSearchHistoryActivity.setAdapter(mAdapter);
        binding.recyclerViewSearchHistoryActivity.addItemDecoration(new RecyclerView.ItemDecoration() {
            final int spacing = (int) Utils.convertDpToPixel(16, SearchHistoryActivity.this);
            final int halfSpacing = spacing / 2;

            @Override
            public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
                int column = ((GridLayoutManager.LayoutParams) view.getLayoutParams()).getSpanIndex();
                boolean toTheLeft = column == 0;

                if (toTheLeft) {
                    outRect.left = spacing;
                    outRect.right = halfSpacing;
                } else {
                    outRect.left = halfSpacing;
                    outRect.right = spacing;
                }
                outRect.top = spacing;
            }
        });

        RecentSearchQueryViewModel viewModel = new ViewModelProvider(this,
                new RecentSearchQueryViewModel.Factory(mRedditDataRoomDatabase, accountName))
                .get(RecentSearchQueryViewModel.class);

        viewModel.getAllRecentSearchQueries().observe(this, recentSearchQueries -> {
            if (recentSearchQueries == null || recentSearchQueries.isEmpty()) {
                binding.emptyTextViewSearchHistoryActivity.setVisibility(View.VISIBLE);
                binding.recyclerViewSearchHistoryActivity.setVisibility(View.GONE);
            } else {
                binding.emptyTextViewSearchHistoryActivity.setVisibility(View.GONE);
                binding.recyclerViewSearchHistoryActivity.setVisibility(View.VISIBLE);
            }
            mAdapter.setRecentSearchQueries(recentSearchQueries);
        });
    }

    @Override
    public SharedPreferences getDefaultSharedPreferences() {
        return mSharedPreferences;
    }

    @Override
    public SharedPreferences getCurrentAccountSharedPreferences() {
        return mCurrentAccountSharedPreferences;
    }

    @Override
    public CustomThemeWrapper getCustomThemeWrapper() {
        return mCustomThemeWrapper;
    }

    @Override
    protected void applyCustomTheme() {
        binding.getRoot().setBackgroundColor(mCustomThemeWrapper.getBackgroundColor());
        applyAppBarLayoutAndCollapsingToolbarLayoutAndToolbarTheme(binding.appbarLayoutSearchHistoryActivity,
                binding.collapsingToolbarLayoutSearchHistoryActivity, binding.toolbarSearchHistoryActivity);
        binding.emptyTextViewSearchHistoryActivity.setTextColor(mCustomThemeWrapper.getSecondaryTextColor());
        if (typeface != null) {
            binding.emptyTextViewSearchHistoryActivity.setTypeface(typeface);
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return false;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        EventBus.getDefault().unregister(this);
    }

    @Subscribe
    public void onAccountSwitchEvent(SwitchAccountEvent event) {
        finish();
    }
}
