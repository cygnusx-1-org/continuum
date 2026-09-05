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
import ml.docilealligator.infinityforreddit.asynctasks.DeleteAllThemes;
import ml.docilealligator.infinityforreddit.customviews.preference.CustomFontPreferenceFragmentCompat;
import ml.docilealligator.infinityforreddit.events.RecreateActivityEvent;
import ml.docilealligator.infinityforreddit.utils.SharedPreferencesUtils;
import org.greenrobot.eventbus.EventBus;

/**
 * The settings and data that belong to the app rather than to an account.
 *
 * What is left here after the per-account deletions moved to
 * {@link AccountSettingsManagementPreferenceFragment} is everything with no account to belong to:
 * the theme library is one library, and the legacy keys are by definition the spellings from before
 * settings were per-account. Every action on this screen affects all accounts at once.
 *
 * A simple {@link Fragment} subclass.
 */
public class GlobalSettingsManagementPreferenceFragment extends CustomFontPreferenceFragmentCompat {

    @Inject
    RedditDataRoomDatabase mRedditDataRoomDatabase;
    @Inject
    @Named("default")
    SharedPreferences mSharedPreferences;
    @Inject
    @Named("current_account")
    SharedPreferences mCurrentAccountSharedPreferences;
    @Inject
    @Named("light_theme")
    SharedPreferences lightThemeSharedPreferences;
    @Inject
    @Named("dark_theme")
    SharedPreferences darkThemeSharedPreferences;
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
        setPreferencesFromResource(R.xml.global_settings_management_preferences, rootKey);

        ((Infinity) mActivity.getApplication()).getAppComponent().inject(this);

        Preference deleteAllThemesPreference = findPreference(SharedPreferencesUtils.DELETE_ALL_THEMES_IN_DATABASE);
        Preference deleteAllLegacySettingsPreference = findPreference(SharedPreferencesUtils.DELETE_ALL_LEGACY_SETTINGS);
        Preference resetAllSettingsPreference = findPreference(SharedPreferencesUtils.RESET_ALL_SETTINGS);

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
                            // Sort types and post layouts lived in the default file before they got
                            // files of their own, and the per-account deletions that replaced
                            // "Delete All Sort Type Data" only look in those files. Left here, this
                            // is the one action that still reaches the older spellings.
                            editor.remove(SharedPreferencesUtils.SORT_TYPE_ALL_POST_LEGACY);
                            editor.remove(SharedPreferencesUtils.SORT_TIME_ALL_POST_LEGACY);
                            editor.remove(SharedPreferencesUtils.SORT_TYPE_POPULAR_POST_LEGACY);
                            editor.remove(SharedPreferencesUtils.SORT_TIME_POPULAR_POST_LEGACY);
                            editor.remove(SharedPreferencesUtils.POST_LAYOUT_ALL_POST_LEGACY);
                            editor.remove(SharedPreferencesUtils.POST_LAYOUT_POPULAR_POST_LEGACY);

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
