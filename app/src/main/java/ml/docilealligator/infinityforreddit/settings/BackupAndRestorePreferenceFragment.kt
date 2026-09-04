package ml.docilealligator.infinityforreddit.settings

import android.app.Activity.RESULT_OK
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.preference.Preference
import com.google.android.material.dialog.MaterialAlertDialogBuilder
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

        /** What a backup password has to be, and what both dialogs enforce before enabling OK. */
        private const val MINIMUM_PASSWORD_LENGTH = 6
        private const val MAXIMUM_PASSWORD_LENGTH = 32
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
    @Named("recently_visited")
    lateinit var recentlyVisitedSharedPreferences: SharedPreferences

    @Inject
    lateinit var executor: Executor

    private val handler = Handler(Looper.getMainLooper())

    private var backupPassword: String? = null
    private var restorePassword: String? = null
    private var restoreFileUri: Uri? = null

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.backup_and_restore_preferences, rootKey)

        (mActivity.application as Infinity).appComponent.inject(this)

        val backupSettingsPreference =
            findPreference<Preference>(SharedPreferencesUtils.BACKUP_SETTINGS)
        val restoreSettingsPreference =
            findPreference<Preference>(SharedPreferencesUtils.RESTORE_SETTINGS)

        backupSettingsPreference?.setOnPreferenceClickListener {
            showPasswordDialog()
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
            val password = backupPassword
            if (uri != null && password != null) {
                BackupSettings.backupSettings(mActivity, executor, handler,
                    mActivity.contentResolver, uri, password, mRedditDataRoomDatabase,
                    lightThemeSharedPreferences, darkThemeSharedPreferences,
                    amoledThemeSharedPreferences, postFeedScrolledPositionSharedPreferences,
                    mainActivityTabsSharedPreferences, proxySharedPreferences,
                    nsfwAndBlurringSharedPreferences, postHistorySharedPreferences,
                    recentlyVisitedSharedPreferences,
                    object : BackupSettings.BackupSettingsListener {
                        override fun success() {
                            Toast.makeText(mActivity, R.string.backup_settings_success,
                                Toast.LENGTH_LONG).show()
                            // Clear the password from memory after use
                            backupPassword = null
                        }

                        override fun failed(errorMessage: String) {
                            Toast.makeText(mActivity, errorMessage, Toast.LENGTH_LONG).show()
                            // Clear the password from memory after use
                            backupPassword = null
                        }
                    })
            }
        } else if (requestCode == SELECT_RESTORE_SETTINGS_DIRECTORY_REQUEST_CODE) {
            restoreFileUri = data.data
            showRestorePasswordDialog()
        }
    }

    private fun showPasswordDialog() {
        passwordDialog(R.string.enter_backup_password, R.string.backup_password_dialog_title,
            R.string.backup_password_dialog_message) { password ->
            backupPassword = password
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE),
                SELECT_BACKUP_SETTINGS_DIRECTORY_REQUEST_CODE)
        }
    }

    private fun showRestorePasswordDialog() {
        passwordDialog(R.string.enter_restore_password, R.string.restore_password_dialog_title,
            R.string.restore_password_dialog_message) { password ->
            restorePassword = password
            performRestore()
        }
    }

    /**
     * The password prompt both directions use. OK stays disabled until the length is one the
     * archive format accepts, which is why the button is reached for after `show()`.
     */
    private fun passwordDialog(hintRes: Int, titleRes: Int, messageRes: Int,
                               onPassword: (String) -> Unit) {
        // A wrong password comes back from the zip library asynchronously and re-opens this
        // dialog. Backing out of the screen, or rotating, in the second that takes leaves
        // mActivity destroyed, and show() on it throws BadTokenException.
        if (!isAdded || mActivity.isFinishing || mActivity.isDestroyed) {
            return
        }

        val passwordEditText = EditText(mActivity)
        passwordEditText.inputType =
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        passwordEditText.setHint(hintRes)

        val showPasswordCheckBox = CheckBox(mActivity)
        showPasswordCheckBox.setText(R.string.show_password)
        showPasswordCheckBox.setOnCheckedChangeListener { _, isChecked ->
            passwordEditText.inputType = if (isChecked) {
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            } else {
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
            passwordEditText.setSelection(passwordEditText.text.length)
        }

        val layout = LinearLayout(mActivity)
        layout.orientation = LinearLayout.VERTICAL
        val padding = (16 * resources.displayMetrics.density).toInt() // 16dp
        layout.setPadding(padding, padding, padding, padding)
        layout.addView(passwordEditText)
        layout.addView(showPasswordCheckBox)

        val dialog = MaterialAlertDialogBuilder(mActivity, R.style.MaterialAlertDialogTheme)
            .setTitle(titleRes)
            .setMessage(messageRes)
            .setView(layout)
            .setPositiveButton(R.string.ok) { _, _ ->
                onPassword(passwordEditText.text.toString().trim())
            }
            .setNegativeButton(R.string.cancel, null)
            .create()

        dialog.show()
        // Initially disable the OK button
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false

        passwordEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                val password = s?.toString()?.trim().orEmpty()
                val isValid = password.length in MINIMUM_PASSWORD_LENGTH..MAXIMUM_PASSWORD_LENGTH
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = isValid
                // Show an error only once something has been typed and it is invalid.
                passwordEditText.error = when {
                    isValid || password.isEmpty() -> null
                    password.length < MINIMUM_PASSWORD_LENGTH ->
                        getString(R.string.password_too_short_error, MINIMUM_PASSWORD_LENGTH)
                    else -> getString(R.string.password_too_long_error, MAXIMUM_PASSWORD_LENGTH)
                }
            }
        })
    }

    private fun performRestore() {
        val fileUri = restoreFileUri ?: return
        val password = restorePassword ?: return

        RestoreSettings.restoreSettings(mActivity, executor, handler, mActivity.contentResolver,
            fileUri, password, mRedditDataRoomDatabase, mCurrentAccountSharedPreferences,
            lightThemeSharedPreferences, darkThemeSharedPreferences, amoledThemeSharedPreferences,
            postFeedScrolledPositionSharedPreferences, mainActivityTabsSharedPreferences,
            proxySharedPreferences, nsfwAndBlurringSharedPreferences, postHistorySharedPreferences,
            recentlyVisitedSharedPreferences,
            object : RestoreSettings.RestoreSettingsListener {
                override fun success() {
                    Toast.makeText(mActivity, R.string.restore_settings_success,
                        Toast.LENGTH_LONG).show()
                    // Clear the password from memory after use
                    restorePassword = null
                    restoreFileUri = null
                }

                override fun failed(errorMessage: String) {
                    Toast.makeText(mActivity, errorMessage, Toast.LENGTH_LONG).show()
                    // Clear the password from memory after use
                    restorePassword = null
                    restoreFileUri = null
                }

                override fun failedWithWrongPassword(errorMessage: String) {
                    Toast.makeText(mActivity, errorMessage, Toast.LENGTH_LONG).show()
                    // Don't clear restoreFileUri so it can be reused
                    restorePassword = null
                    showRestorePasswordDialog()
                }
            })
    }
}
