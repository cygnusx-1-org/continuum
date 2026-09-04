package ml.docilealligator.infinityforreddit.settings;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.preference.Preference;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.util.concurrent.Executor;
import javax.inject.Inject;
import javax.inject.Named;
import ml.docilealligator.infinityforreddit.Infinity;
import ml.docilealligator.infinityforreddit.R;
import ml.docilealligator.infinityforreddit.RedditDataRoomDatabase;
import ml.docilealligator.infinityforreddit.asynctasks.DeleteAllPostLayouts;
import ml.docilealligator.infinityforreddit.asynctasks.DeleteAllReadPosts;
import ml.docilealligator.infinityforreddit.asynctasks.DeleteAllSortTypes;
import ml.docilealligator.infinityforreddit.asynctasks.DeleteAllSubreddits;
import ml.docilealligator.infinityforreddit.asynctasks.DeleteAllThemes;
import ml.docilealligator.infinityforreddit.asynctasks.DeleteAllUsers;
import ml.docilealligator.infinityforreddit.customviews.preference.CustomFontPreferenceFragmentCompat;
import ml.docilealligator.infinityforreddit.events.RecreateActivityEvent;
import ml.docilealligator.infinityforreddit.readpost.ReadPostDao;
import ml.docilealligator.infinityforreddit.readpost.ReadPostType;
import ml.docilealligator.infinityforreddit.utils.SharedPreferencesUtils;
import org.greenrobot.eventbus.EventBus;

/**
 * A simple {@link Fragment} subclass.
 */
public class AdvancedPreferenceFragment extends CustomFontPreferenceFragmentCompat {

    @Inject
    RedditDataRoomDatabase mRedditDataRoomDatabase;
    @Inject
    @Named("default")
    SharedPreferences mSharedPreferences;
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
    @Named("light_theme")
    SharedPreferences lightThemeSharedPreferences;
    @Inject
    @Named("dark_theme")
    SharedPreferences darkThemeSharedPreferences;
    @Inject
    @Named("post_feed_scrolled_position_cache")
    SharedPreferences postFeedScrolledPositionSharedPreferences;
    @Inject
    @Named("amoled_theme")
    SharedPreferences amoledThemeSharedPreferences;
    @Inject
    @Named("main_activity_tabs")
    SharedPreferences mainActivityTabsSharedPreferences;
    @Inject
    @Named("nsfw_and_spoiler")
    SharedPreferences nsfwAndBlurringSharedPreferences;
    @Inject
    Executor executor;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        setPreferencesFromResource(R.xml.advanced_preferences, rootKey);

        ((Infinity) mActivity.getApplication()).getAppComponent().inject(this);

        Preference deleteSubredditsPreference = findPreference(SharedPreferencesUtils.DELETE_ALL_SUBREDDITS_DATA_IN_DATABASE);
        Preference deleteUsersPreference = findPreference(SharedPreferencesUtils.DELETE_ALL_USERS_DATA_IN_DATABASE);
        Preference deleteSortTypePreference = findPreference(SharedPreferencesUtils.DELETE_ALL_SORT_TYPE_DATA_IN_DATABASE);
        Preference deletePostLaoutPreference = findPreference(SharedPreferencesUtils.DELETE_ALL_POST_LAYOUT_DATA_IN_DATABASE);
        Preference deleteAllThemesPreference = findPreference(SharedPreferencesUtils.DELETE_ALL_THEMES_IN_DATABASE);
        Preference deletePostFeedScrolledPositionsPreference = findPreference(SharedPreferencesUtils.DELETE_FRONT_PAGE_SCROLLED_POSITIONS_IN_DATABASE);
        Preference deleteReadPostsPreference = findPreference(SharedPreferencesUtils.DELETE_READ_POSTS_IN_DATABASE);
        Preference deleteAllLegacySettingsPreference = findPreference(SharedPreferencesUtils.DELETE_ALL_LEGACY_SETTINGS);
        Preference resetAllSettingsPreference = findPreference(SharedPreferencesUtils.RESET_ALL_SETTINGS);

        if (deleteSubredditsPreference != null) {
            deleteSubredditsPreference.setOnPreferenceClickListener(preference -> {
                new MaterialAlertDialogBuilder(mActivity, R.style.MaterialAlertDialogTheme)
                        .setTitle(R.string.are_you_sure)
                        .setPositiveButton(R.string.yes, (dialogInterface, i)
                                -> DeleteAllSubreddits.deleteAllSubreddits(executor, handler, mRedditDataRoomDatabase,
                                        () -> Toast.makeText(mActivity, R.string.delete_all_subreddits_success, Toast.LENGTH_SHORT).show()))
                        .setNegativeButton(R.string.no, null)
                        .show();
                return true;
            });
        }

        if (deleteUsersPreference != null) {
            deleteUsersPreference.setOnPreferenceClickListener(preference -> {
                new MaterialAlertDialogBuilder(mActivity, R.style.MaterialAlertDialogTheme)
                        .setTitle(R.string.are_you_sure)
                        .setPositiveButton(R.string.yes, (dialogInterface, i)
                                -> DeleteAllUsers.deleteAllUsers(executor, handler, mRedditDataRoomDatabase,
                                        () -> Toast.makeText(mActivity, R.string.delete_all_users_success, Toast.LENGTH_SHORT).show()))
                        .setNegativeButton(R.string.no, null)
                        .show();
                return true;
            });
        }

        if (deleteSortTypePreference != null) {
            deleteSortTypePreference.setOnPreferenceClickListener(preference -> {
                new MaterialAlertDialogBuilder(mActivity, R.style.MaterialAlertDialogTheme)
                        .setTitle(R.string.are_you_sure)
                        .setPositiveButton(R.string.yes, (dialogInterface, i)
                                -> DeleteAllSortTypes.deleteAllSortTypes(executor, handler,
                                mSharedPreferences, mSortTypeSharedPreferences, () -> {
                                    Toast.makeText(mActivity, R.string.delete_all_sort_types_success, Toast.LENGTH_SHORT).show();
                                    EventBus.getDefault().post(new RecreateActivityEvent());
                                }))
                        .setNegativeButton(R.string.no, null)
                        .show();
                return true;
            });
        }

        if (deletePostLaoutPreference != null) {
            deletePostLaoutPreference.setOnPreferenceClickListener(preference -> {
                new MaterialAlertDialogBuilder(mActivity, R.style.MaterialAlertDialogTheme)
                        .setTitle(R.string.are_you_sure)
                        .setPositiveButton(R.string.yes, (dialogInterface, i)
                                -> DeleteAllPostLayouts.deleteAllPostLayouts(executor, handler,
                                mSharedPreferences, mPostLayoutSharedPreferences, () -> {
                                    Toast.makeText(mActivity, R.string.delete_all_post_layouts_success, Toast.LENGTH_SHORT).show();
                                    EventBus.getDefault().post(new RecreateActivityEvent());
                                }))
                        .setNegativeButton(R.string.no, null)
                        .show();
                return true;
            });
        }

        if (deleteAllThemesPreference != null) {
            deleteAllThemesPreference.setOnPreferenceClickListener(preference -> {
                new MaterialAlertDialogBuilder(mActivity, R.style.MaterialAlertDialogTheme)
                        .setTitle(R.string.are_you_sure)
                        .setPositiveButton(R.string.yes, (dialogInterface, i)
                                -> DeleteAllThemes.deleteAllThemes(executor, handler,
                                mRedditDataRoomDatabase, lightThemeSharedPreferences,
                                        darkThemeSharedPreferences, amoledThemeSharedPreferences, () -> {
                                    Toast.makeText(mActivity, R.string.delete_all_themes_success, Toast.LENGTH_SHORT).show();
                                    EventBus.getDefault().post(new RecreateActivityEvent());
                                }))
                        .setNegativeButton(R.string.no, null)
                        .show();
                return true;
            });
        }

        if (deletePostFeedScrolledPositionsPreference != null) {
            deletePostFeedScrolledPositionsPreference.setOnPreferenceClickListener(preference -> {
                new MaterialAlertDialogBuilder(mActivity, R.style.MaterialAlertDialogTheme)
                        .setTitle(R.string.are_you_sure)
                        .setPositiveButton(R.string.yes, (dialogInterface, i)
                                -> {
                            postFeedScrolledPositionSharedPreferences.edit().clear().apply();
                            Toast.makeText(mActivity, R.string.delete_all_front_page_scrolled_positions_success, Toast.LENGTH_SHORT).show();
                        })
                        .setNegativeButton(R.string.no, null)
                        .show();
                return true;
            });
        }

        if (deleteReadPostsPreference != null) {
            executor.execute(() -> {
                ReadPostDao readPostDao = mRedditDataRoomDatabase.readPostDao();
                int tableCount = readPostDao.getReadPostsCount(mActivity.accountName, ReadPostType.READ_POSTS);
                long tableEntrySize = readPostDao.getMaxReadPostEntrySize();
                long tableSize = tableEntrySize * tableCount / 1024;
                handler.post(() -> {
                    // Backing out of the screen while these two queries run detaches the fragment,
                    // and getString() then throws "not attached to a context".
                    if (!isAdded()) {
                        return;
                    }
                    deleteReadPostsPreference.setSummary(getString(R.string.settings_read_posts_db_summary, tableSize, tableCount));
                });
            });
            deleteReadPostsPreference.setOnPreferenceClickListener(preference -> {
                new MaterialAlertDialogBuilder(mActivity, R.style.MaterialAlertDialogTheme)
                        .setTitle(R.string.are_you_sure)
                        .setPositiveButton(R.string.yes, (dialogInterface, i)
                                -> DeleteAllReadPosts.deleteAllReadPosts(executor, handler,
                                mRedditDataRoomDatabase, () -> {
                            Toast.makeText(mActivity, R.string.delete_all_read_posts_success, Toast.LENGTH_SHORT).show();
                        }))
                        .setNegativeButton(R.string.no, null)
                        .show();
                return true;
            });
        }

        if (deleteAllLegacySettingsPreference != null) {
            deleteAllLegacySettingsPreference.setOnPreferenceClickListener(preference -> {
                new MaterialAlertDialogBuilder(mActivity, R.style.MaterialAlertDialogTheme)
                        .setTitle(R.string.are_you_sure)
                        .setPositiveButton(R.string.yes, (dialogInterface, i)
                                -> {
                            SharedPreferences.Editor editor = mSharedPreferences.edit();
                            editor.remove(SharedPreferencesUtils.MAIN_PAGE_TAB_1_TITLE_LEGACY);
                            editor.remove(SharedPreferencesUtils.MAIN_PAGE_TAB_2_TITLE_LEGACY);
                            editor.remove(SharedPreferencesUtils.MAIN_PAGE_TAB_3_TITLE_LEGACY);
                            editor.remove(SharedPreferencesUtils.MAIN_PAGE_TAB_1_POST_TYPE_LEGACY);
                            editor.remove(SharedPreferencesUtils.MAIN_PAGE_TAB_2_POST_TYPE_LEGACY);
                            editor.remove(SharedPreferencesUtils.MAIN_PAGE_TAB_3_POST_TYPE_LEGACY);
                            editor.remove(SharedPreferencesUtils.MAIN_PAGE_TAB_1_NAME_LEGACY);
                            editor.remove(SharedPreferencesUtils.MAIN_PAGE_TAB_2_NAME_LEGACY);
                            editor.remove(SharedPreferencesUtils.MAIN_PAGE_TAB_3_NAME_LEGACY);
                            editor.remove(SharedPreferencesUtils.NSFW_KEY_LEGACY);
                            editor.remove(SharedPreferencesUtils.BLUR_NSFW_KEY_LEGACY);
                            editor.remove(SharedPreferencesUtils.BLUR_SPOILER_KEY_LEGACY);
                            editor.remove(SharedPreferencesUtils.CONFIRM_TO_EXIT_LEGACY);
                            editor.remove(SharedPreferencesUtils.OPEN_LINK_IN_APP_LEGACY);
                            editor.remove(SharedPreferencesUtils.AUTOMATICALLY_TRY_REDGIFS_LEGACY);
                            editor.remove(SharedPreferencesUtils.DO_NOT_SHOW_REDDIT_API_INFO_AGAIN_LEGACY);
                            editor.remove(SharedPreferencesUtils.HIDE_THE_NUMBER_OF_AWARDS_LEGACY);
                            editor.remove(SharedPreferencesUtils.HIDE_COMMENT_AWARDS_LEGACY);
                            editor.remove(SharedPreferencesUtils.IMMERSIVE_INTERFACE_IGNORE_NAV_BAR_KEY_LEGACY);

                            // The sort type and post layout files are per-account whole-file, so
                            // the injected instances would rewrite these keys into the signed-in
                            // account's namespace and leave the bare legacy spellings — which are
                            // the ones this action exists to delete — untouched. Opened raw.
                            SharedPreferences.Editor sortTypeEditor = mActivity.getSharedPreferences(
                                    SharedPreferencesUtils.SORT_TYPE_SHARED_PREFERENCES_FILE, Context.MODE_PRIVATE).edit();
                            sortTypeEditor.remove(SharedPreferencesUtils.SORT_TYPE_ALL_POST_LEGACY);
                            sortTypeEditor.remove(SharedPreferencesUtils.SORT_TIME_ALL_POST_LEGACY);
                            sortTypeEditor.remove(SharedPreferencesUtils.SORT_TYPE_POPULAR_POST_LEGACY);
                            sortTypeEditor.remove(SharedPreferencesUtils.SORT_TIME_POPULAR_POST_LEGACY);

                            SharedPreferences.Editor postLayoutEditor = mActivity.getSharedPreferences(
                                    SharedPreferencesUtils.POST_LAYOUT_SHARED_PREFERENCES_FILE, Context.MODE_PRIVATE).edit();
                            postLayoutEditor.remove(SharedPreferencesUtils.POST_LAYOUT_ALL_POST_LEGACY);
                            postLayoutEditor.remove(SharedPreferencesUtils.POST_LAYOUT_POPULAR_POST_LEGACY);

                            SharedPreferences.Editor currentAccountEditor = mCurrentAccountSharedPreferences.edit();
                            currentAccountEditor.remove(SharedPreferencesUtils.APPLICATION_ONLY_ACCESS_TOKEN_LEGACY);

                            editor.apply();
                            sortTypeEditor.apply();
                            postLayoutEditor.apply();
                            currentAccountEditor.apply();
                            Toast.makeText(mActivity, R.string.delete_all_legacy_settings_success, Toast.LENGTH_SHORT).show();
                        })
                        .setNegativeButton(R.string.no, null)
                        .show();
                return true;
            });
        }

        if (resetAllSettingsPreference != null) {
            resetAllSettingsPreference.setOnPreferenceClickListener(preference -> {
                new MaterialAlertDialogBuilder(mActivity, R.style.MaterialAlertDialogTheme)
                        .setTitle(R.string.are_you_sure)
                        .setPositiveButton(R.string.yes, (dialogInterface, i)
                                -> {
                            mSharedPreferences.edit().clear().apply();
                            mainActivityTabsSharedPreferences.edit().clear().apply();
                            nsfwAndBlurringSharedPreferences.edit().clear().apply();

                            Toast.makeText(mActivity, R.string.reset_all_settings_success, Toast.LENGTH_SHORT).show();
                            EventBus.getDefault().post(new RecreateActivityEvent());
                        })
                        .setNegativeButton(R.string.no, null)
                        .show();
                return true;
            });
        }
    }
}
