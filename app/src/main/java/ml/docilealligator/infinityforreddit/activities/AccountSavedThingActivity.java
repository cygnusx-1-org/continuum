package ml.docilealligator.infinityforreddit.activities;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SearchView;
import androidx.core.graphics.Insets;
import androidx.core.view.OnApplyWindowInsetsListener;
import androidx.core.view.ViewCompat;
import androidx.core.view.ViewGroupCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;
import com.google.android.material.tabs.TabLayoutMediator;
import java.util.Objects;
import java.util.concurrent.Executor;
import javax.inject.Inject;
import javax.inject.Named;
import ml.docilealligator.infinityforreddit.Infinity;
import ml.docilealligator.infinityforreddit.R;
import ml.docilealligator.infinityforreddit.RedditDataRoomDatabase;
import ml.docilealligator.infinityforreddit.bottomsheetfragments.PostLayoutBottomSheetFragment;
import ml.docilealligator.infinityforreddit.customtheme.CustomThemeWrapper;
import ml.docilealligator.infinityforreddit.databinding.ActivityAccountSavedThingBinding;
import ml.docilealligator.infinityforreddit.events.ChangeNSFWEvent;
import ml.docilealligator.infinityforreddit.events.SavedThingChangedEvent;
import ml.docilealligator.infinityforreddit.events.SwitchAccountEvent;
import ml.docilealligator.infinityforreddit.fragments.CommentsListingFragment;
import ml.docilealligator.infinityforreddit.fragments.HistoryPostFragment;
import ml.docilealligator.infinityforreddit.fragments.PostFragment;
import ml.docilealligator.infinityforreddit.localsaved.LocalSaved;
import ml.docilealligator.infinityforreddit.post.MarkPostAsReadInterface;
import ml.docilealligator.infinityforreddit.post.Post;
import ml.docilealligator.infinityforreddit.post.PostPagingSource;
import ml.docilealligator.infinityforreddit.post.PostType;
import ml.docilealligator.infinityforreddit.readpost.ReadPostModification;
import ml.docilealligator.infinityforreddit.readpost.ReadPostType;
import ml.docilealligator.infinityforreddit.readpost.ReadPostsUtils;
import ml.docilealligator.infinityforreddit.utils.SharedPreferencesUtils;
import ml.docilealligator.infinityforreddit.utils.Utils;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import retrofit2.Retrofit;

public class AccountSavedThingActivity extends BaseActivity implements ActivityToolbarInterface,
        PostLayoutBottomSheetFragment.PostLayoutSelectionCallback, MarkPostAsReadInterface {

    @Inject
    @Named("oauth")
    Retrofit mOauthRetrofit;
    @Inject
    RedditDataRoomDatabase mRedditDataRoomDatabase;
    @Inject
    @Named("default")
    SharedPreferences mSharedPreferences;
    @Inject
    @Named("post_history")
    SharedPreferences mPostHistorySharedPreferences;
    @Inject
    @Named("post_layout")
    SharedPreferences mPostLayoutSharedPreferences;
    @Inject
    @Named("current_account")
    SharedPreferences mCurrentAccountSharedPreferences;
    @Inject
    CustomThemeWrapper mCustomThemeWrapper;
    @Inject
    Executor mExecutor;
    private FragmentManager fragmentManager;
    private SectionsPagerAdapter sectionsPagerAdapter;
    private PostLayoutBottomSheetFragment postLayoutBottomSheetFragment;
    private ActivityAccountSavedThingBinding binding;

    private static final long SEARCH_DEBOUNCE_MS = 500;
    private String currentSearchQuery = "";
    private final Handler searchDebounceHandler = new Handler(Looper.getMainLooper());
    private Runnable searchDebounceRunnable;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        ((Infinity) getApplication()).getAppComponent().inject(this);

        super.onCreate(savedInstanceState);
        binding = ActivityAccountSavedThingBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        EventBus.getDefault().register(this);

        applyCustomTheme();

        attachSliderPanelIfApplicable();

        mViewPager2 = binding.accountSavedThingViewPager2;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Window window = getWindow();

            if (isChangeStatusBarIconColor()) {
                addOnOffsetChangedListener(binding.accountSavedThingAppbarLayout);
            }

            if (isImmersiveInterfaceRespectForcedEdgeToEdge()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    window.setDecorFitsSystemWindows(false);
                } else {
                    window.setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
                }

                ViewGroupCompat.installCompatInsetsDispatch(binding.getRoot());
                ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), new OnApplyWindowInsetsListener() {
                    @NonNull
                    @Override
                    public WindowInsetsCompat onApplyWindowInsets(@NonNull View v, @NonNull WindowInsetsCompat insets) {
                        Insets allInsets = Utils.getInsets(insets, false, isForcedImmersiveInterface());

                        setMargins(binding.accountSavedThingToolbar,
                                allInsets.left,
                                allInsets.top,
                                allInsets.right,
                                BaseActivity.IGNORE_MARGIN);

                        binding.accountSavedThingViewPager2.setPadding(allInsets.left, 0, allInsets.right, 0);

                        return insets;
                    }
                });
                //adjustToolbar(binding.accountSavedThingToolbar);
            }
        }

        setSupportActionBar(binding.accountSavedThingToolbar);
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);
        setToolbarGoToTop(binding.accountSavedThingToolbar);

        postLayoutBottomSheetFragment = new PostLayoutBottomSheetFragment();

        fragmentManager = getSupportFragmentManager();

        initializeViewPager();

        // Surface any saves Reddit has since dropped from /saved into the Local Saved tabs.
        LocalSaved.reconcile(mRedditDataRoomDatabase, mOauthRetrofit, accessToken, accountName);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (sectionsPagerAdapter != null) {
            return sectionsPagerAdapter.handleKeyDown(keyCode) || super.onKeyDown(keyCode, event);
        }

        return super.onKeyDown(keyCode, event);
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
        binding.accountSavedThingCoordinatorLayout.setBackgroundColor(mCustomThemeWrapper.getBackgroundColor());
        applyAppBarLayoutAndCollapsingToolbarLayoutAndToolbarTheme(
                binding.accountSavedThingAppbarLayout,
                binding.accountSavedThingCollapsingToolbarLayout,
                binding.accountSavedThingToolbar);
        applyAppBarScrollFlagsIfApplicable(binding.accountSavedThingCollapsingToolbarLayout);
        applyTabLayoutTheme(binding.accountSavedThingTabLayout);
    }

    private void initializeViewPager() {
        sectionsPagerAdapter = new SectionsPagerAdapter(this);
        binding.accountSavedThingViewPager2.setAdapter(sectionsPagerAdapter);
        binding.accountSavedThingViewPager2.setUserInputEnabled(!mSharedPreferences.getBoolean(SharedPreferencesUtils.DISABLE_SWIPING_BETWEEN_TABS, false));
        new TabLayoutMediator(binding.accountSavedThingTabLayout, binding.accountSavedThingViewPager2, (tab, position) -> {
            switch (position) {
                case 0:
                    Utils.setTitleWithCustomFontToTab(typeface, tab, getString(R.string.posts));
                    break;
                case 1:
                    Utils.setTitleWithCustomFontToTab(typeface, tab, getString(R.string.comments));
                    break;
                case 2:
                    Utils.setTitleWithCustomFontToTab(typeface, tab, getString(R.string.local_posts));
                    break;
                case 3:
                    Utils.setTitleWithCustomFontToTab(typeface, tab, getString(R.string.local_comments));
                    break;
            }
        }).attach();

        binding.accountSavedThingViewPager2.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                if (position == 0) {
                    unlockSwipeRightToGoBack();
                } else {
                    lockSwipeRightToGoBack();
                }
                // Keep the active search applied to whichever tab is now showing. Posted so the
                // freshly selected fragment has been added before we look it up; guarded because a
                // view-posted runnable can still fire after the activity is torn down.
                binding.accountSavedThingViewPager2.post(() -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }
                    if (sectionsPagerAdapter != null) {
                        sectionsPagerAdapter.search(currentSearchQuery);
                    }
                });
            }
        });

        fixViewPager2Sensitivity(binding.accountSavedThingViewPager2);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.account_saved_thing_activity, menu);
        applyMenuItemTheme(menu);
        setupSearchView(menu);
        return true;
    }

    private void setupSearchView(Menu menu) {
        MenuItem searchItem = menu.findItem(R.id.action_search_account_saved_thing_activity);
        if (searchItem == null) {
            return;
        }
        SearchView searchView = (SearchView) searchItem.getActionView();
        if (searchView == null) {
            return;
        }
        searchView.setQueryHint(getString(R.string.action_search));

        // Make the embedded text field legible on the themed toolbar.
        int textColor = mCustomThemeWrapper.getToolbarPrimaryTextAndIconColor();
        EditText searchEditText = searchView.findViewById(androidx.appcompat.R.id.search_src_text);
        if (searchEditText != null) {
            searchEditText.setTextColor(textColor);
            searchEditText.setHintTextColor(Color.argb(150, Color.red(textColor), Color.green(textColor), Color.blue(textColor)));
        }

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                applySearch(query, false);
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                applySearch(newText, true);
                return true;
            }
        });

        searchView.setOnCloseListener(() -> {
            applySearch("", false);
            return false;
        });
    }

    // Reddit has no server-side search over /saved, so this filters the loaded items client-side and
    // lets each tab keep loading further pages while a query is active. Live text changes are
    // debounced (and only distinct queries reach the ViewModels) to avoid churning the paging
    // pipeline on every keystroke.
    private void applySearch(String query, boolean debounce) {
        final String normalized = query == null ? "" : query;
        // Track the latest query synchronously so a tab swipe (onPageSelected) applies the current
        // query to the newly shown tab even while the debounce is still pending.
        currentSearchQuery = normalized;
        if (searchDebounceRunnable != null) {
            searchDebounceHandler.removeCallbacks(searchDebounceRunnable);
        }
        searchDebounceRunnable = () -> {
            if (sectionsPagerAdapter != null) {
                sectionsPagerAdapter.search(normalized);
            }
        };
        if (debounce) {
            searchDebounceHandler.postDelayed(searchDebounceRunnable, SEARCH_DEBOUNCE_MS);
        } else {
            searchDebounceHandler.post(searchDebounceRunnable);
        }
    }

    // The bypass toggle only applies to the server-side Saved posts tab (position 0), so hide it on
    // the other tabs. onPrepareOptionsMenu runs each time the overflow is about to show, so it always
    // reflects the current tab -- no menu invalidation (which could collapse the SearchView) needed.
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem bypassItem = menu.findItem(R.id.action_bypass_cache_account_saved_thing_activity);
        if (bypassItem != null) {
            bypassItem.setVisible(binding.accountSavedThingViewPager2.getCurrentItem() == 0);
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == android.R.id.home) {
            finish();
            return true;
        } else if (itemId == R.id.action_bypass_cache_account_saved_thing_activity) {
            item.setChecked(!item.isChecked());
            if (item.isChecked()) {
                // Fetch fresh now: drop the cache and reload the whole saved history from the network.
                sectionsPagerAdapter.forceFreshSaved();
                Toast.makeText(this, R.string.bypass_saved_cache_on, Toast.LENGTH_SHORT).show();
            }
            return true;
        } else if (itemId == R.id.action_refresh_account_saved_thing_activity) {
            sectionsPagerAdapter.refresh();
            return true;
        } else if (itemId == R.id.action_change_post_layout_account_saved_thing_activity) {
            postLayoutBottomSheetFragment.show(getSupportFragmentManager(), postLayoutBottomSheetFragment.getTag());
            return true;
        }
        return false;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        searchDebounceHandler.removeCallbacksAndMessages(null);
        EventBus.getDefault().unregister(this);
    }

    @Subscribe
    public void onAccountSwitchEvent(SwitchAccountEvent event) {
        finish();
    }

    @Subscribe
    public void onChangeNSFWEvent(ChangeNSFWEvent changeNSFWEvent) {
        sectionsPagerAdapter.changeNSFW(changeNSFWEvent.nsfw);
    }

    // MAIN so it touches the FragmentManager on the UI thread regardless of the posting thread.
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onSavedThingChangedEvent(SavedThingChangedEvent event) {
        if (sectionsPagerAdapter != null) {
            sectionsPagerAdapter.onSavedThingChanged(event.kind);
        }
    }

    @Override
    public void onLongPress() {
        if (sectionsPagerAdapter != null) {
            sectionsPagerAdapter.goBackToTop();
        }
    }

    @Override
    public void lockSwipeRightToGoBack() {
        if (mSliderPanel != null) {
            mSliderPanel.lock();
        }
    }

    @Override
    public void unlockSwipeRightToGoBack() {
        if (mSliderPanel != null) {
            mSliderPanel.unlock();
        }
    }

    @Override
    public void postLayoutSelected(int postLayout) {
        if (sectionsPagerAdapter != null) {
            mPostLayoutSharedPreferences.edit().putInt(SharedPreferencesUtils.POST_LAYOUT_USER_POST_BASE + accountName, postLayout).apply();
            sectionsPagerAdapter.changePostLayout(postLayout);
        }
    }

    @Override
    public void markPostAsRead(Post post) {
        int readPostsLimit = ReadPostsUtils.GetReadPostsLimit(accountName, mPostHistorySharedPreferences);
        ReadPostModification.insertReadPost(mRedditDataRoomDatabase, mExecutor, accountName, post.getId(), ReadPostType.READ_POSTS, readPostsLimit);
    }

    private class SectionsPagerAdapter extends FragmentStateAdapter {

        SectionsPagerAdapter(FragmentActivity fa) {
            super(fa);
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            if (position == 0) {
                PostFragment fragment = new PostFragment();
                Bundle bundle = new Bundle();
                bundle.putInt(PostFragment.EXTRA_POST_TYPE, PostType.USER);
                bundle.putString(PostFragment.EXTRA_USER_NAME, accountName);
                bundle.putString(PostFragment.EXTRA_USER_WHERE, PostPagingSource.USER_WHERE_SAVED);
                bundle.putBoolean(PostFragment.EXTRA_DISABLE_READ_POSTS, true);
                fragment.setArguments(bundle);
                return fragment;
            } else if (position == 2) {
                HistoryPostFragment fragment = new HistoryPostFragment();
                Bundle bundle = new Bundle();
                bundle.putInt(HistoryPostFragment.EXTRA_READ_POST_TYPE, ReadPostType.LOCAL_SAVED_POSTS);
                fragment.setArguments(bundle);
                return fragment;
            } else if (position == 3) {
                CommentsListingFragment fragment = new CommentsListingFragment();
                Bundle bundle = new Bundle();
                bundle.putString(CommentsListingFragment.EXTRA_USERNAME, accountName);
                bundle.putBoolean(CommentsListingFragment.EXTRA_ARE_LOCAL_SAVED_COMMENTS, true);
                fragment.setArguments(bundle);
                return fragment;
            }
            CommentsListingFragment fragment = new CommentsListingFragment();
            Bundle bundle = new Bundle();
            bundle.putString(CommentsListingFragment.EXTRA_USERNAME, accountName);
            bundle.putBoolean(CommentsListingFragment.EXTRA_ARE_SAVED_COMMENTS, true);
            fragment.setArguments(bundle);
            return fragment;
        }

        @Nullable
        private Fragment getCurrentFragment() {
            if (fragmentManager == null) {
                return null;
            }
            return fragmentManager.findFragmentByTag("f" + binding.accountSavedThingViewPager2.getCurrentItem());
        }

        public boolean handleKeyDown(int keyCode) {
            if (binding.accountSavedThingViewPager2.getCurrentItem() == 0) {
                Fragment fragment = getCurrentFragment();
                if (fragment instanceof PostFragment) {
                    return ((PostFragment) fragment).handleKeyDown(keyCode);
                }
            }
            return false;
        }

        public void refresh() {
            Fragment fragment = getCurrentFragment();
            if (fragment instanceof PostFragment) {
                ((PostFragment) fragment).refresh();
            } else if (fragment instanceof CommentsListingFragment) {
                ((CommentsListingFragment) fragment).refresh();
            }
        }

        public void search(String query) {
            Fragment fragment = getCurrentFragment();
            if (fragment instanceof PostFragment) {
                ((PostFragment) fragment).filterSaved(query);
            } else if (fragment instanceof HistoryPostFragment) {
                ((HistoryPostFragment) fragment).filterSaved(query);
            } else if (fragment instanceof CommentsListingFragment) {
                ((CommentsListingFragment) fragment).filterSaved(query);
            }
        }

        // Only the server-side Saved posts tab (position 0) has the hard-TTL cache to bypass.
        public void forceFreshSaved() {
            Fragment fragment = getCurrentFragment();
            if (fragment instanceof PostFragment) {
                ((PostFragment) fragment).forceFreshSaved();
            }
        }

        // Drop the in-memory search cache of the tabs affected by this change, regardless of which tab
        // is showing (the save/unsave may have happened from a post/comment opened off another tab).
        // Posts change the server (f0) and local (f2) saved posts tabs; comments change the server (f1)
        // and local (f3) saved comments tabs. The unaffected tab type is left untouched so its cache
        // isn't needlessly dropped.
        public void onSavedThingChanged(SavedThingChangedEvent.Kind kind) {
            if (fragmentManager == null) {
                return;
            }
            String[] tags = kind == SavedThingChangedEvent.Kind.POST
                    ? new String[]{"f0", "f2"}
                    : new String[]{"f1", "f3"};
            for (String tag : tags) {
                Fragment fragment = fragmentManager.findFragmentByTag(tag);
                if (fragment instanceof PostFragment) {
                    ((PostFragment) fragment).onSavedThingChanged();
                } else if (fragment instanceof HistoryPostFragment) {
                    ((HistoryPostFragment) fragment).onSavedThingChanged();
                } else if (fragment instanceof CommentsListingFragment) {
                    ((CommentsListingFragment) fragment).onSavedThingChanged();
                }
            }
        }

        public void changeNSFW(boolean nsfw) {
            Fragment fragment = getCurrentFragment();
            if (fragment instanceof PostFragment) {
                ((PostFragment) fragment).changeNSFW(nsfw);
            }
        }

        public void changePostLayout(int postLayout) {
            Fragment fragment = getCurrentFragment();
            if (fragment instanceof PostFragment) {
                ((PostFragment) fragment).changePostLayout(postLayout);
            }
        }


        public void goBackToTop() {
            Fragment fragment = getCurrentFragment();
            if (fragment instanceof PostFragment) {
                ((PostFragment) fragment).goBackToTop();
            } else if (fragment instanceof CommentsListingFragment) {
                ((CommentsListingFragment) fragment).goBackToTop();
            }
        }

        @Override
        public int getItemCount() {
            return 4;
        }
    }
}
