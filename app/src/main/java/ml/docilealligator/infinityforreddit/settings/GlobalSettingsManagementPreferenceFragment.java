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
