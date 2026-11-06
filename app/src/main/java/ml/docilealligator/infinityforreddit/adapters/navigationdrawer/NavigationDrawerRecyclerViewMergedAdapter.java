package ml.docilealligator.infinityforreddit.adapters.navigationdrawer;

import android.content.SharedPreferences;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.ConcatAdapter;
import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestManager;
import java.util.List;
import ml.docilealligator.infinityforreddit.account.Account;
import ml.docilealligator.infinityforreddit.R;
import ml.docilealligator.infinityforreddit.activities.BaseActivity;
import ml.docilealligator.infinityforreddit.customtheme.CustomThemeWrapper;
import ml.docilealligator.infinityforreddit.recentsubreddits.RecentSubreddits;
import ml.docilealligator.infinityforreddit.subscribedsubreddit.SubscribedSubredditData;
import ml.docilealligator.infinityforreddit.utils.SharedPreferencesUtils;

public class NavigationDrawerRecyclerViewMergedAdapter {
    private final HeaderSectionRecyclerViewAdapter headerSectionRecyclerViewAdapter;
    private final AccountSectionRecyclerViewAdapter accountSectionRecyclerViewAdapter;
    private final RedditSectionRecyclerViewAdapter redditSectionRecyclerViewAdapter;
    private final PostSectionRecyclerViewAdapter postSectionRecyclerViewAdapter;
    private final PreferenceSectionRecyclerViewAdapter preferenceSectionRecyclerViewAdapter;
    private final RecentSubredditsSectionRecyclerViewAdapter recentSubredditsSectionRecyclerViewAdapter;
    private final SubredditListSectionRecyclerViewAdapter favoriteSubredditsSectionRecyclerViewAdapter;
    private final SubredditListSectionRecyclerViewAdapter subscribedSubredditsRecyclerViewAdapter;
    private final AccountManagementSectionRecyclerViewAdapter accountManagementSectionRecyclerViewAdapter;
    private final ConcatAdapter mainPageConcatAdapter;

    public NavigationDrawerRecyclerViewMergedAdapter(BaseActivity baseActivity, SharedPreferences sharedPreferences,
                                                     SharedPreferences nsfwAndSpoilerSharedPreferences,
                                                     SharedPreferences navigationDrawerSharedPreferences,
                                                     SharedPreferences securitySharedPreferences,
                                                     CustomThemeWrapper customThemeWrapper,
                                                     @NonNull String accountName,
                                                     ItemClickListener itemClickListener) {
        RequestManager glide = Glide.with(baseActivity);

        headerSectionRecyclerViewAdapter = new HeaderSectionRecyclerViewAdapter(baseActivity, customThemeWrapper,
                glide, accountName, sharedPreferences, navigationDrawerSharedPreferences, securitySharedPreferences,
                new HeaderSectionRecyclerViewAdapter.PageToggle() {
                    @Override
                    public void openAccountManagement() {
                        NavigationDrawerRecyclerViewMergedAdapter.this.openAccountSection();
                    }

                    @Override
                    public void closeAccountManagement() {
                        NavigationDrawerRecyclerViewMergedAdapter.this.closeAccountManagement(false);
                    }
                });
        accountSectionRecyclerViewAdapter = new AccountSectionRecyclerViewAdapter(baseActivity, customThemeWrapper,
                navigationDrawerSharedPreferences, !accountName.equals(Account.ANONYMOUS_ACCOUNT),
                itemClickListener);
        redditSectionRecyclerViewAdapter = new RedditSectionRecyclerViewAdapter(baseActivity, customThemeWrapper,
                navigationDrawerSharedPreferences, itemClickListener);
        postSectionRecyclerViewAdapter = new PostSectionRecyclerViewAdapter(baseActivity, customThemeWrapper,
                navigationDrawerSharedPreferences, itemClickListener);
        preferenceSectionRecyclerViewAdapter = new PreferenceSectionRecyclerViewAdapter(baseActivity, customThemeWrapper,
                accountName, sharedPreferences, nsfwAndSpoilerSharedPreferences, navigationDrawerSharedPreferences, itemClickListener);
        recentSubredditsSectionRecyclerViewAdapter = new RecentSubredditsSectionRecyclerViewAdapter(
                baseActivity, glide, customThemeWrapper, navigationDrawerSharedPreferences, itemClickListener);
        favoriteSubredditsSectionRecyclerViewAdapter = new SubredditListSectionRecyclerViewAdapter(
                baseActivity, glide, customThemeWrapper, navigationDrawerSharedPreferences,
                DrawerSection.FAVORITES, R.string.favorites, itemClickListener);
        subscribedSubredditsRecyclerViewAdapter = new SubredditListSectionRecyclerViewAdapter(
                baseActivity, glide, customThemeWrapper, navigationDrawerSharedPreferences,
                DrawerSection.SUBSCRIBED, R.string.subscriptions, itemClickListener);
        accountManagementSectionRecyclerViewAdapter = new AccountManagementSectionRecyclerViewAdapter(baseActivity,
                customThemeWrapper, glide, !accountName.equals(Account.ANONYMOUS_ACCOUNT), itemClickListener);

        mainPageConcatAdapter = new ConcatAdapter(
                headerSectionRecyclerViewAdapter,
                accountSectionRecyclerViewAdapter,
                redditSectionRecyclerViewAdapter,
                postSectionRecyclerViewAdapter,
                preferenceSectionRecyclerViewAdapter,
                recentSubredditsSectionRecyclerViewAdapter,
                favoriteSubredditsSectionRecyclerViewAdapter,
                subscribedSubredditsRecyclerViewAdapter);
    }

    public ConcatAdapter getConcatAdapter() {
        return mainPageConcatAdapter;
    }

    public void openAccountSection() {
        mainPageConcatAdapter.removeAdapter(accountSectionRecyclerViewAdapter);
        mainPageConcatAdapter.removeAdapter(redditSectionRecyclerViewAdapter);
        mainPageConcatAdapter.removeAdapter(postSectionRecyclerViewAdapter);
        mainPageConcatAdapter.removeAdapter(preferenceSectionRecyclerViewAdapter);
        mainPageConcatAdapter.removeAdapter(recentSubredditsSectionRecyclerViewAdapter);
        mainPageConcatAdapter.removeAdapter(favoriteSubredditsSectionRecyclerViewAdapter);
        mainPageConcatAdapter.removeAdapter(subscribedSubredditsRecyclerViewAdapter);

        mainPageConcatAdapter.addAdapter(accountManagementSectionRecyclerViewAdapter);
    }

    public void openAccountManagementPage() {
        if (mainPageConcatAdapter.getAdapters().contains(accountManagementSectionRecyclerViewAdapter)) {
            return;
        }
        openAccountSection();
        headerSectionRecyclerViewAdapter.openAccountManagement(true);
    }

    public boolean isInAccountManagementPage() {
        return mainPageConcatAdapter.getAdapters().contains(accountManagementSectionRecyclerViewAdapter);
    }

    public void closeAccountManagement(boolean refreshHeader) {
        mainPageConcatAdapter.removeAdapter(accountManagementSectionRecyclerViewAdapter);

        mainPageConcatAdapter.addAdapter(accountSectionRecyclerViewAdapter);
        mainPageConcatAdapter.addAdapter(redditSectionRecyclerViewAdapter);
        mainPageConcatAdapter.addAdapter(postSectionRecyclerViewAdapter);
        mainPageConcatAdapter.addAdapter(preferenceSectionRecyclerViewAdapter);
        mainPageConcatAdapter.addAdapter(recentSubredditsSectionRecyclerViewAdapter);
        mainPageConcatAdapter.addAdapter(favoriteSubredditsSectionRecyclerViewAdapter);
        mainPageConcatAdapter.addAdapter(subscribedSubredditsRecyclerViewAdapter);

        if (refreshHeader) {
            headerSectionRecyclerViewAdapter.closeAccountManagement(true);
        }
    }

    public void updateAccountInfo(@Nullable String profileImageUrl, @Nullable String bannerImageUrl, int karma) {
        headerSectionRecyclerViewAdapter.updateAccountInfo(profileImageUrl, bannerImageUrl, karma);
    }

    public void setRequireAuthToAccountSection(boolean requireAuthToAccountSection) {
        headerSectionRecyclerViewAdapter.setRequireAuthToAccountSection(requireAuthToAccountSection);
    }

    public void setShowAvatarOnTheRightInTheNavigationDrawer(boolean showAvatarOnTheRightInTheNavigationDrawer) {
        headerSectionRecyclerViewAdapter.setShowAvatarOnTheRightInTheNavigationDrawer(showAvatarOnTheRightInTheNavigationDrawer);
    }

    public void changeAccountsDataset(List<Account> accounts) {
        accountManagementSectionRecyclerViewAdapter.changeAccountsDataset(accounts);
    }

    public void setInboxCount(int inboxCount) {
        accountSectionRecyclerViewAdapter.setInboxCount(inboxCount);
    }

    public void setNSFWEnabled(boolean isNSFWEnabled) {
        preferenceSectionRecyclerViewAdapter.setNSFWEnabled(isNSFWEnabled);
    }

    public void setShowThumbnailOnTheLeft(boolean showThumbnailOnTheLeft) {
        preferenceSectionRecyclerViewAdapter.setShowThumbnailOnTheLeft(showThumbnailOnTheLeft);
    }

    public void setResumeWhereILeftOff(boolean resumeWhereILeftOff) {
        preferenceSectionRecyclerViewAdapter.setResumeWhereILeftOff(resumeWhereILeftOff);
    }

    public void setRecentSubreddits(List<RecentSubreddits.Entry> recentSubreddits) {
        recentSubredditsSectionRecyclerViewAdapter.setRecentSubreddits(recentSubreddits);
    }

    public void setFavoriteSubscribedSubreddits(List<SubscribedSubredditData> favoriteSubscribedSubreddits) {
        favoriteSubredditsSectionRecyclerViewAdapter.setSubreddits(favoriteSubscribedSubreddits);
    }

    public void setSubscribedSubreddits(List<SubscribedSubredditData> subscribedSubreddits) {
        subscribedSubredditsRecyclerViewAdapter.setSubreddits(subscribedSubreddits);
    }

    public void setHideKarma(boolean hideKarma) {
        headerSectionRecyclerViewAdapter.setHideKarma(hideKarma);
    }

    // Re-reads the section settings and applies them live to each section,
    // so the navigation drawer updates without an app restart.
    public void refreshNavigationDrawerSections(SharedPreferences navigationDrawerSharedPreferences) {
        accountSectionRecyclerViewAdapter.setCollapseAccountSection(
                navigationDrawerSharedPreferences.getBoolean(SharedPreferencesUtils.COLLAPSE_ACCOUNT_SECTION, false));
        redditSectionRecyclerViewAdapter.setCollapseRedditSection(
                navigationDrawerSharedPreferences.getBoolean(SharedPreferencesUtils.COLLAPSE_REDDIT_SECTION, false));
        postSectionRecyclerViewAdapter.setCollapsePostSection(
                navigationDrawerSharedPreferences.getBoolean(SharedPreferencesUtils.COLLAPSE_POST_SECTION, false));
        preferenceSectionRecyclerViewAdapter.setCollapsePreferencesSection(
                navigationDrawerSharedPreferences.getBoolean(SharedPreferencesUtils.COLLAPSE_PREFERENCES_SECTION, false));
        recentSubredditsSectionRecyclerViewAdapter.setState(DrawerSection.RECENTS.read(navigationDrawerSharedPreferences));
        favoriteSubredditsSectionRecyclerViewAdapter.setState(DrawerSection.FAVORITES.read(navigationDrawerSharedPreferences));
        subscribedSubredditsRecyclerViewAdapter.setState(DrawerSection.SUBSCRIBED.read(navigationDrawerSharedPreferences));
        preferenceSectionRecyclerViewAdapter.refreshVisibleRows(navigationDrawerSharedPreferences);
    }

    public interface ItemClickListener {
        void onMenuClick(int stringId);
        void onMenuLongClick(int stringId);
        void onSubscribedSubredditClick(String subredditName);
        void onRecentUserClick(String username);
        void onRecentPinToggle(String name, int type, boolean pin);
        void onRecentDismiss(String name, int type);
        void onAccountClick(@NonNull String accountName);
        void onAccountLongClick(@NonNull String accountName);
    }
}
