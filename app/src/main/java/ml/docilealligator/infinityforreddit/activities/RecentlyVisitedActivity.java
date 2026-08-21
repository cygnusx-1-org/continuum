package ml.docilealligator.infinityforreddit.activities;

import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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
import javax.inject.Inject;
import javax.inject.Named;
import ml.docilealligator.infinityforreddit.Infinity;
import ml.docilealligator.infinityforreddit.R;
import ml.docilealligator.infinityforreddit.customtheme.CustomThemeWrapper;
import ml.docilealligator.infinityforreddit.databinding.ActivityRecentlyVisitedBinding;
import ml.docilealligator.infinityforreddit.events.SwitchAccountEvent;
import ml.docilealligator.infinityforreddit.fragments.RecentlyVisitedListingFragment;
import ml.docilealligator.infinityforreddit.recentlyvisited.RecentlyVisitedType;
import ml.docilealligator.infinityforreddit.utils.Utils;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;

/**
 * The subreddits and users this account has opened lately, newest first. Reached from the
 * navigation drawer, which only shows the entry while the Recently Visited setting is on.
 */
public class RecentlyVisitedActivity extends BaseActivity implements ActivityToolbarInterface {

    private static final int TAB_COUNT = 2;
    private static final String SEARCH_OPEN_STATE = "SOS";

    @Inject
    @Named("default")
    SharedPreferences mSharedPreferences;
    @Inject
    @Named("current_account")
    SharedPreferences mCurrentAccountSharedPreferences;
    @Inject
    CustomThemeWrapper mCustomThemeWrapper;

    private FragmentManager fragmentManager;
    private SectionsPagerAdapter sectionsPagerAdapter;
    private ActivityRecentlyVisitedBinding binding;
    @Nullable
    private Menu mMenu;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        ((Infinity) getApplication()).getAppComponent().inject(this);

        super.onCreate(savedInstanceState);

        binding = ActivityRecentlyVisitedBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        EventBus.getDefault().register(this);

        applyCustomTheme();

        attachSliderPanelIfApplicable();

        mViewPager2 = binding.viewPagerRecentlyVisitedActivity;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Window window = getWindow();

            if (isChangeStatusBarIconColor()) {
                addOnOffsetChangedListener(binding.appbarLayoutRecentlyVisitedActivity);
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

                        setMargins(binding.toolbarRecentlyVisitedActivity,
                                allInsets.left,
                                allInsets.top,
                                allInsets.right,
                                BaseActivity.IGNORE_MARGIN);

                        binding.viewPagerRecentlyVisitedActivity.setPadding(allInsets.left, 0, allInsets.right, 0);

                        return insets;
                    }
                });
            }
        }

        setSupportActionBar(binding.toolbarRecentlyVisitedActivity);
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);
        setToolbarGoToTop(binding.toolbarRecentlyVisitedActivity);

        setTitle(R.string.recently_visited);
        binding.toolbarRecentlyVisitedActivity.setSubtitle(R.string.recently_visited_activity_subtitle);

        binding.searchEditTextRecentlyVisitedActivity.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {}

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {}

            @Override
            public void afterTextChanged(Editable editable) {
                applySearchQuery(editable.toString());
            }
        });

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (binding.searchContainerRecentlyVisitedActivity.getVisibility() == View.VISIBLE) {
                    closeSearch();
                } else {
                    setEnabled(false);
                    triggerBackPress();
                }
            }
        });

        // The EditText restores its own text across a rotation, so without this the list would come
        // back filtered while the search field it was typed into had vanished.
        if (savedInstanceState != null && savedInstanceState.getBoolean(SEARCH_OPEN_STATE, false)) {
            binding.searchContainerRecentlyVisitedActivity.setVisibility(View.VISIBLE);
        }

        fragmentManager = getSupportFragmentManager();

        initializeViewPager();
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
        applyAppBarLayoutAndCollapsingToolbarLayoutAndToolbarTheme(binding.appbarLayoutRecentlyVisitedActivity,
                binding.collapsingToolbarLayoutRecentlyVisitedActivity, binding.toolbarRecentlyVisitedActivity);
        applyAppBarScrollFlagsIfApplicable(binding.collapsingToolbarLayoutRecentlyVisitedActivity);
        applyTabLayoutTheme(binding.tabLayoutRecentlyVisitedActivity);
        binding.searchEditTextRecentlyVisitedActivity.setTextColor(mCustomThemeWrapper.getToolbarPrimaryTextAndIconColor());
        binding.searchEditTextRecentlyVisitedActivity.setHintTextColor(mCustomThemeWrapper.getToolbarSecondaryTextColor());
    }

    private void initializeViewPager() {
        sectionsPagerAdapter = new SectionsPagerAdapter(this);
        binding.viewPagerRecentlyVisitedActivity.setAdapter(sectionsPagerAdapter);
        binding.viewPagerRecentlyVisitedActivity.setUserInputEnabled(true);

        new TabLayoutMediator(binding.tabLayoutRecentlyVisitedActivity, binding.viewPagerRecentlyVisitedActivity,
                (tab, position) -> Utils.setTitleWithCustomFontToTab(typeface, tab,
                        getString(position == 0 ? R.string.subreddits : R.string.users))).attach();

        binding.viewPagerRecentlyVisitedActivity.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                if (position == 0) {
                    unlockSwipeRightToGoBack();
                } else {
                    lockSwipeRightToGoBack();
                }
                // The query belongs to the tab you are looking at, so it follows you across.
                applySearchQuery(getSearchQuery());
            }
        });

        fixViewPager2Sensitivity(binding.viewPagerRecentlyVisitedActivity);
    }

    private String getSearchQuery() {
        CharSequence text = binding.searchEditTextRecentlyVisitedActivity.getText();
        return text == null ? "" : text.toString();
    }

    private void applySearchQuery(String query) {
        if (sectionsPagerAdapter != null) {
            sectionsPagerAdapter.changeSearchQuery("%" + query + "%");
        }
    }

    private void closeSearch() {
        Utils.hideKeyboard(this);
        binding.searchContainerRecentlyVisitedActivity.setVisibility(View.GONE);
        binding.searchEditTextRecentlyVisitedActivity.setText("");
        if (mMenu != null) {
            mMenu.findItem(R.id.action_search_recently_visited_activity).setVisible(true);
        }
        applySearchQuery("");
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(SEARCH_OPEN_STATE,
                binding.searchContainerRecentlyVisitedActivity.getVisibility() == View.VISIBLE);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.recently_visited_activity, menu);
        mMenu = menu;
        // Derived rather than toggled, so a menu built after a rotation matches the search field
        // that was just restored.
        menu.findItem(R.id.action_search_recently_visited_activity).setVisible(
                binding.searchContainerRecentlyVisitedActivity.getVisibility() != View.VISIBLE);
        applyMenuItemTheme(menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == android.R.id.home) {
            triggerBackPress();
            return true;
        } else if (itemId == R.id.action_search_recently_visited_activity) {
            binding.searchContainerRecentlyVisitedActivity.setVisibility(View.VISIBLE);
            binding.searchEditTextRecentlyVisitedActivity.requestFocus();
            Utils.showKeyboard(this, new android.os.Handler(), binding.searchEditTextRecentlyVisitedActivity);
            item.setVisible(false);
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

    private class SectionsPagerAdapter extends FragmentStateAdapter {

        SectionsPagerAdapter(FragmentActivity fa) {
            super(fa);
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            RecentlyVisitedListingFragment fragment = new RecentlyVisitedListingFragment();
            Bundle bundle = new Bundle();
            bundle.putInt(RecentlyVisitedListingFragment.EXTRA_TYPE,
                    position == 0 ? RecentlyVisitedType.SUBREDDIT : RecentlyVisitedType.USER);
            // A tab first created while a search is already typed has never seen changeSearchQuery,
            // so it starts filtered rather than showing the whole list.
            bundle.putString(RecentlyVisitedListingFragment.EXTRA_SEARCH_QUERY, "%" + getSearchQuery() + "%");
            fragment.setArguments(bundle);
            return fragment;
        }

        @Override
        public int getItemCount() {
            return TAB_COUNT;
        }

        @Nullable
        private Fragment getCurrentFragment() {
            if (fragmentManager == null) {
                return null;
            }
            return fragmentManager.findFragmentByTag("f" + binding.viewPagerRecentlyVisitedActivity.getCurrentItem());
        }

        void changeSearchQuery(String searchQuery) {
            if (fragmentManager == null) {
                return;
            }
            // Both tabs are kept in step so switching tabs does not show a stale, unfiltered list.
            for (int i = 0; i < TAB_COUNT; i++) {
                Fragment fragment = fragmentManager.findFragmentByTag("f" + i);
                if (fragment instanceof RecentlyVisitedListingFragment) {
                    ((RecentlyVisitedListingFragment) fragment).setSearchQuery(searchQuery);
                }
            }
        }

        void goBackToTop() {
            Fragment fragment = getCurrentFragment();
            if (fragment instanceof RecentlyVisitedListingFragment) {
                ((RecentlyVisitedListingFragment) fragment).goBackToTop();
            }
        }
    }
}
