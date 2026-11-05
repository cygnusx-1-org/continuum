package ml.docilealligator.infinityforreddit.activities;

import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
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
import ml.docilealligator.infinityforreddit.bottomsheetfragments.PostLayoutBottomSheetFragment;
import ml.docilealligator.infinityforreddit.customtheme.CustomThemeWrapper;
import ml.docilealligator.infinityforreddit.databinding.ActivityHistoryBinding;
import ml.docilealligator.infinityforreddit.events.ChangeNSFWEvent;
import ml.docilealligator.infinityforreddit.events.SwitchAccountEvent;
import ml.docilealligator.infinityforreddit.fragments.CommentsListingFragment;
import ml.docilealligator.infinityforreddit.fragments.HistoryPostFragment;
import ml.docilealligator.infinityforreddit.fragments.PostFragment;
import ml.docilealligator.infinityforreddit.readpost.ReadPostType;
import ml.docilealligator.infinityforreddit.resume.FeedResumeState;
import ml.docilealligator.infinityforreddit.resume.Restorable;
import ml.docilealligator.infinityforreddit.resume.ResumeState;
import ml.docilealligator.infinityforreddit.utils.SharedPreferencesUtils;
import ml.docilealligator.infinityforreddit.utils.Utils;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;

public class HistoryActivity extends BaseActivity implements ActivityToolbarInterface,
        PostLayoutBottomSheetFragment.PostLayoutSelectionCallback, Restorable {

    public static final String EXTRA_READ_POST_TYPE = "EHT";

    private static final String STATE_RESUME_TAB = "RTB";

    @Inject
    @Named("default")
    SharedPreferences mSharedPreferences;
    @Inject
    @Named("post_layout")
    SharedPreferences mPostLayoutSharedPreferences;
    @Inject
    @Named("current_account")
    SharedPreferences mCurrentAccountSharedPreferences;
    @Inject
    CustomThemeWrapper mCustomThemeWrapper;
    // Resume where I left off. The list kind travels in the intent extras; only where in the list
    // the user was needs storing, and the posts themselves are in Room already.
    private final FeedResumeState resumeFeed = new FeedResumeState();
    @ReadPostType
    private int readPostType;
    // The tab to open on, from a resume record; read before the pager is built.
    private int resumeTab = -1;
    private FragmentManager fragmentManager;
    private SectionsPagerAdapter sectionsPagerAdapter;
    private ActivityHistoryBinding binding;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        ((Infinity) getApplication()).getAppComponent().inject(this);

        super.onCreate(savedInstanceState);

        binding = ActivityHistoryBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        EventBus.getDefault().register(this);

        applyCustomTheme();

        attachSliderPanelIfApplicable();

        mViewPager2 = binding.viewPagerHistoryActivity;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Window window = getWindow();

            if (isChangeStatusBarIconColor()) {
                addOnOffsetChangedListener(binding.appbarLayoutHistoryActivity);
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

                        setMargins(binding.toolbarHistoryActivity,
                                allInsets.left,
                                allInsets.top,
                                allInsets.right,
                                BaseActivity.IGNORE_MARGIN);

                        binding.viewPagerHistoryActivity.setPadding(allInsets.left, 0, allInsets.right, 0);

                        return insets;
                    }
                });
                //adjustToolbar(binding.toolbarHistoryActivity);
            }
        }

        setSupportActionBar(binding.toolbarHistoryActivity);
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);
        setToolbarGoToTop(binding.toolbarHistoryActivity);

        readPostType = getIntent().getIntExtra(EXTRA_READ_POST_TYPE, ReadPostType.READ_POSTS);
        switch (readPostType) {
            case ReadPostType.READ_POSTS:
                setTitle(R.string.history_activity_read_posts_label);
                break;
            case ReadPostType.ANONYMOUS_UPVOTED_POSTS:
                setTitle(R.string.upvoted);
                break;
            case ReadPostType.ANONYMOUS_DOWNVOTED_POSTS:
                setTitle(R.string.downvoted);
                break;
            case ReadPostType.ANONYMOUS_HIDDEN_POSTS:
                setTitle(R.string.hidden);
                break;
            case ReadPostType.ANONYMOUS_SAVED_POSTS:
                setTitle(R.string.saved);
                break;
        }
        binding.toolbarHistoryActivity.setSubtitle(R.string.history_activity_subtitle);

        fragmentManager = getSupportFragmentManager();

        trackAppBarOffsetForResume(binding.appbarLayoutHistoryActivity);

        // Before the fragment is built, so the record it is given is the recorded one.
        claimResumeState();

        initializeViewPager();
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
        binding.getRoot().setBackgroundColor(mCustomThemeWrapper.getBackgroundColor());
        applyAppBarLayoutAndCollapsingToolbarLayoutAndToolbarTheme(binding.appbarLayoutHistoryActivity,
                binding.collapsingToolbarLayoutHistoryActivity, binding.toolbarHistoryActivity);
        applyAppBarScrollFlagsIfApplicable(binding.collapsingToolbarLayoutHistoryActivity);
        applyTabLayoutTheme(binding.tabLayoutTabLayoutHistoryActivityActivity);
    }

    /**
     * Whether this is the anonymous Saved screen, the one list here with a second tab: comments
     * saved while logged out, which are kept on the device like the posts.
     */
    private boolean hasCommentsTab() {
        return readPostType == ReadPostType.ANONYMOUS_SAVED_POSTS;
    }

    private void initializeViewPager() {
        sectionsPagerAdapter = new SectionsPagerAdapter(this);
        binding.viewPagerHistoryActivity.setAdapter(sectionsPagerAdapter);
        if (hasCommentsTab()) {
            binding.tabLayoutTabLayoutHistoryActivityActivity.setVisibility(View.VISIBLE);
            binding.viewPagerHistoryActivity.setUserInputEnabled(!mSharedPreferences.getBoolean(SharedPreferencesUtils.DISABLE_SWIPING_BETWEEN_TABS, false));
            new TabLayoutMediator(binding.tabLayoutTabLayoutHistoryActivityActivity, binding.viewPagerHistoryActivity, (tab, position) -> {
                switch (position) {
                    case 0:
                        Utils.setTitleWithCustomFontToTab(typeface, tab, getString(R.string.posts));
                        break;
                    case 1:
                        Utils.setTitleWithCustomFontToTab(typeface, tab, getString(R.string.comments));
                        break;
                }
            }).attach();
        } else {
            binding.tabLayoutTabLayoutHistoryActivityActivity.setVisibility(View.GONE);
            binding.viewPagerHistoryActivity.setUserInputEnabled(false);
        }

        binding.viewPagerHistoryActivity.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                if (position == 0) {
                    unlockSwipeRightToGoBack();
                } else {
                    lockSwipeRightToGoBack();
                }
                // Paging between this screen's own tabs moves no activity, so nothing else
                // writes the new one down until the next transition. A single-tab list has no
                // tab to note, and the capture is a synchronous write on the main thread.
                if (hasCommentsTab()) {
                    ResumeState.noteStateChanged(HistoryActivity.this);
                }
            }
        });

        fixViewPager2Sensitivity(binding.viewPagerHistoryActivity);

        if (resumeTab > 0 && resumeTab < sectionsPagerAdapter.getItemCount()) {
            binding.viewPagerHistoryActivity.setCurrentItem(resumeTab, false);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.history_activity, menu);
        applyMenuItemTheme(menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == android.R.id.home) {
            finish();
            return true;
        } else if (itemId == R.id.action_refresh_history_activity) {
            sectionsPagerAdapter.refresh();
            return true;
        } else if (handlePostLayoutMenuItem(itemId)) {
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

    @Subscribe
    public void onChangeNSFWEvent(ChangeNSFWEvent changeNSFWEvent) {
        sectionsPagerAdapter.changeNSFW(changeNSFWEvent.nsfw);
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
            mPostLayoutSharedPreferences.edit().putInt(SharedPreferencesUtils.HISTORY_POST_LAYOUT_READ_POST, postLayout).apply();
            sectionsPagerAdapter.changePostLayout(postLayout);
        }
    }

    @Override
    public void saveResumeState(@NonNull Bundle out) {
        if (fragmentManager == null) {
            return;
        }
        int tab = binding.viewPagerHistoryActivity.getCurrentItem();
        Fragment fragment = fragmentManager.findFragmentByTag("f" + tab);
        // The comments tab has no record of where it was, so a resume onto it restores the tab
        // alone. Nothing is written when the posts tab is showing but cannot describe itself,
        // rather than overwriting a good record with one that says nothing.
        if (fragment instanceof HistoryPostFragment) {
            if (!((HistoryPostFragment) fragment).captureResumeState(out)) {
                return;
            }
        } else if (!(fragment instanceof CommentsListingFragment)) {
            return;
        }
        out.putInt(STATE_RESUME_TAB, tab);
        saveResumeAppBarOffset(out);
    }

    @Override
    public void restoreResumeState(@NonNull Bundle state) {
        resumeTab = state.getInt(STATE_RESUME_TAB, -1);
        resumeFeed.read(state);
        restoreResumeAppBarOffset(state, binding.appbarLayoutHistoryActivity,
                binding.viewPagerHistoryActivity);
    }

    private class SectionsPagerAdapter extends FragmentStateAdapter {

        SectionsPagerAdapter(FragmentActivity fa) {
            super(fa);
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            if (position == 1) {
                // The comments the anonymous account saved, which CommentDataSource reads from the
                // device when the local-saved list is asked for while logged out.
                CommentsListingFragment fragment = new CommentsListingFragment();
                Bundle bundle = new Bundle();
                bundle.putString(CommentsListingFragment.EXTRA_USERNAME, accountName);
                bundle.putBoolean(CommentsListingFragment.EXTRA_ARE_LOCAL_SAVED_COMMENTS, true);
                fragment.setArguments(bundle);
                return fragment;
            }
            HistoryPostFragment fragment = new HistoryPostFragment();
            Bundle bundle = new Bundle();
            bundle.putInt(HistoryPostFragment.EXTRA_READ_POST_TYPE, readPostType);
            // One-shot, so a fragment rebuilt later cannot replay the restore.
            resumeFeed.applyTo(bundle);
            fragment.setArguments(bundle);
            return fragment;
        }

        @Nullable
        private Fragment getCurrentFragment() {
            if (fragmentManager == null) {
                return null;
            }
            return fragmentManager.findFragmentByTag("f" + binding.viewPagerHistoryActivity.getCurrentItem());
        }

        public boolean handleKeyDown(int keyCode) {
            if (binding.viewPagerHistoryActivity.getCurrentItem() == 0) {
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

        public void changeNSFW(boolean nsfw) {
            Fragment fragment = getCurrentFragment();
            if (fragment instanceof PostFragment) {
                ((PostFragment) fragment).changeNSFW(nsfw);
            }
        }

        public void changePostLayout(int postLayout) {
            Fragment fragment = getCurrentFragment();
            if (fragment instanceof HistoryPostFragment) {
                ((HistoryPostFragment) fragment).changePostLayout(postLayout);
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
            return hasCommentsTab() ? 2 : 1;
        }
    }
}
