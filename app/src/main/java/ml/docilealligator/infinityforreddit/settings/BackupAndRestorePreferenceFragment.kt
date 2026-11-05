package ml.docilealligator.infinityforreddit.settings

import android.app.Activity.RESULT_OK
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.preference.Preference
import java.util.concurrent.Executor
import javax.inject.Inject
import javax.inject.Named
import ml.docilealligator.infinityforreddit.Infinity
import ml.docilealligator.infinityforreddit.R
import ml.docilealligator.infinityforreddit.RedditDataRoomDatabase
import ml.docilealligator.infinityforreddit.asynctasks.BackupSettings
import ml.docilealligator.infinityforreddit.asynctasks.RestoreSettings
import ml.docilealligator.infinityforreddit.customviews.preference.CustomFontPreferenceFragmentCompat
import ml.docilealligator.infinityforreddit.utils.SharedPreferencesUtils

/**
 * Writing every setting out to an encrypted archive, and reading one back.
 *
 * Whole-app rather than per-account: an archive holds every account's settings, which is what makes
 * it a way back from a mistake with one of them. The account-scoped preference files are absent from
 * the injected fields below on purpose — [BackupSettings] and [RestoreSettings] open those
 * themselves, because the injected instances show only the account that is signed in.
 */
class BackupAndRestorePreferenceFragment : CustomFontPreferenceFragmentCompat() {

    companion object {
        private const val SELECT_BACKUP_SETTINGS_DIRECTORY_REQUEST_CODE = 1
        private const val SELECT_RESTORE_SETTINGS_DIRECTORY_REQUEST_CODE = 2
    }

    @Inject
    lateinit var mRedditDataRoomDatabase: RedditDataRoomDatabase

    @Inject
    @Named("current_account")
    lateinit var mCurrentAccountSharedPreferences: SharedPreferences

    @Inject
    @Named("light_theme")
    lateinit var lightThemeSharedPreferences: SharedPreferences

    @Inject
    @Named("dark_theme")
    lateinit var darkThemeSharedPreferences: SharedPreferences

    @Inject
    @Named("amoled_theme")
    lateinit var amoledThemeSharedPreferences: SharedPreferences

    @Inject
    @Named("post_feed_scrolled_position_cache")
    lateinit var postFeedScrolledPositionSharedPreferences: SharedPreferences

    @Inject
    @Named("main_activity_tabs")
    lateinit var mainActivityTabsSharedPreferences: SharedPreferences

    @Inject
    @Named("proxy")
    lateinit var proxySharedPreferences: SharedPreferences

    @Inject
    @Named("nsfw_and_spoiler")
    lateinit var nsfwAndBlurringSharedPreferences: SharedPreferences

    @Inject
    @Named("post_history")
    lateinit var postHistorySharedPreferences: SharedPreferences

    @Inject
    lateinit var executor: Executor

    private val handler = Handler(Looper.getMainLooper())

    private var restoreFileUri: Uri? = null

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.backup_and_restore_preferences, rootKey)

        (mActivity.application as Infinity).appComponent.inject(this)

        val backupSettingsPreference =
            findPreference<Preference>(SharedPreferencesUtils.BACKUP_SETTINGS)
        val restoreSettingsPreference =
            findPreference<Preference>(SharedPreferencesUtils.RESTORE_SETTINGS)

        backupSettingsPreference?.setOnPreferenceClickListener {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE),
                SELECT_BACKUP_SETTINGS_DIRECTORY_REQUEST_CODE)
            true
        }

        restoreSettingsPreference?.setOnPreferenceClickListener {
            var chooseFile = Intent(Intent.ACTION_GET_CONTENT)
            chooseFile.type = "application/zip"
            chooseFile = Intent.createChooser(chooseFile, "Choose a backup file")
            startActivityForResult(chooseFile, SELECT_RESTORE_SETTINGS_DIRECTORY_REQUEST_CODE)
            true
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode != RESULT_OK || data == null) {
            return
        }

        if (requestCode == SELECT_BACKUP_SETTINGS_DIRECTORY_REQUEST_CODE) {
            val uri = data.data
            if (uri != null) {
                BackupSettings.backupSettings(mActivity, executor, handler,
                    mActivity.contentResolver, uri, mRedditDataRoomDatabase,
                    lightThemeSharedPreferences, darkThemeSharedPreferences,
                    amoledThemeSharedPreferences, postFeedScrolledPositionSharedPreferences,
                    mainActivityTabsSharedPreferences, proxySharedPreferences,
                    nsfwAndBlurringSharedPreferences, postHistorySharedPreferences,
                    object : BackupSettings.BackupSettingsListener {
                        override fun success() {
                            Toast.makeText(mActivity, R.string.backup_settings_success,
                                Toast.LENGTH_LONG).show()
                        }

                        override fun failed(errorMessage: String) {
                            Toast.makeText(mActivity, errorMessage, Toast.LENGTH_LONG).show()
                        }
                    })
            }
        } else if (requestCode == SELECT_RESTORE_SETTINGS_DIRECTORY_REQUEST_CODE) {
            restoreFileUri = data.data
            performRestore()
        }
    }

    private fun performRestore() {
        val fileUri = restoreFileUri ?: return

        RestoreSettings.restoreSettings(mActivity, executor, handler, mActivity.contentResolver,
            fileUri, mRedditDataRoomDatabase, mCurrentAccountSharedPreferences,
            lightThemeSharedPreferences, darkThemeSharedPreferences, amoledThemeSharedPreferences,
            postFeedScrolledPositionSharedPreferences, mainActivityTabsSharedPreferences,
            proxySharedPreferences, nsfwAndBlurringSharedPreferences, postHistorySharedPreferences,
            object : RestoreSettings.RestoreSettingsListener {
                override fun success() {
                    Toast.makeText(mActivity, R.string.restore_settings_success,
                        Toast.LENGTH_LONG).show()
                    restoreFileUri = null
                }

                override fun failed(errorMessage: String) {
                    Toast.makeText(mActivity, errorMessage, Toast.LENGTH_LONG).show()
                    restoreFileUri = null
                }

            })
    }
}
