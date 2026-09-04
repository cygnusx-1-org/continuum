package ml.docilealligator.infinityforreddit.settings

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.ViewModelProvider
import androidx.preference.Preference
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.concurrent.Executor
import javax.inject.Inject
import ml.docilealligator.infinityforreddit.Infinity
import ml.docilealligator.infinityforreddit.R
import ml.docilealligator.infinityforreddit.RedditDataRoomDatabase
import ml.docilealligator.infinityforreddit.account.Account
import ml.docilealligator.infinityforreddit.account.AccountSettings
import ml.docilealligator.infinityforreddit.account.AccountViewModel
import ml.docilealligator.infinityforreddit.bottomsheetfragments.AccountChooserBottomSheetFragment
import ml.docilealligator.infinityforreddit.customviews.preference.CustomFontPreferenceFragmentCompat
import ml.docilealligator.infinityforreddit.utils.AppRestartHelper
import ml.docilealligator.infinityforreddit.utils.SharedPreferencesUtils

/**
 * The two operations that act on this account's settings as a whole rather than on one of them.
 *
 * Both end by restarting the app: they rewrite preference files that the running process has
 * already read, and a screen showing values from before the copy is worse than a restart.
 */
class AccountSettingsManagementPreferenceFragment : CustomFontPreferenceFragmentCompat(),
    AccountChooserBottomSheetFragment.AccountChooserListener {

    @Inject
    lateinit var redditDataRoomDatabase: RedditDataRoomDatabase

    @Inject
    lateinit var executor: Executor

    private val handler = Handler(Looper.getMainLooper())

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.account_settings_management_preferences, rootKey)
        (mActivity.application as Infinity).appComponent.inject(this)

        val copySettingsPreference =
            findPreference<Preference>(SharedPreferencesUtils.COPY_SETTINGS_FROM_ACCOUNT)
        val resetAccountSettingsPreference =
            findPreference<Preference>(SharedPreferencesUtils.RESET_ACCOUNT_SETTINGS)

        if (copySettingsPreference != null) {
            // Disabled rather than hidden when there is nobody to copy from: the row keeps its place
            // in the group, so the rounded corners below it do not move with the account count.
            val accountViewModel = ViewModelProvider(this,
                AccountViewModel.Factory(executor, redditDataRoomDatabase))[AccountViewModel::class.java]
            accountViewModel.accountsExceptCurrentAccountLiveData.observe(this) { accounts ->
                val hasAnother = !accounts.isNullOrEmpty()
                copySettingsPreference.isEnabled = hasAnother
                copySettingsPreference.setSummary(
                    if (hasAnother) R.string.settings_copy_settings_from_account_summary
                    else R.string.settings_copy_settings_from_account_no_other_summary)
            }

            copySettingsPreference.setOnPreferenceClickListener {
                val fragment = AccountChooserBottomSheetFragment()
                fragment.arguments = Bundle().apply {
                    putBoolean(AccountChooserBottomSheetFragment.EXTRA_EXCLUDE_CURRENT_ACCOUNT, true)
                }
                fragment.show(parentFragmentManager, fragment.tag)
                true
            }
        }

        resetAccountSettingsPreference?.setOnPreferenceClickListener {
            val accountName = mActivity.accountName
            MaterialAlertDialogBuilder(mActivity, R.style.MaterialAlertDialogTheme)
                .setTitle(R.string.settings_reset_account_settings_title)
                .setMessage(getString(R.string.reset_account_settings_confirmation,
                    displayName(accountName)))
                .setPositiveButton(R.string.yes) { _, _ ->
                    val context = mActivity.applicationContext
                    executor.execute {
                        AccountSettings.reset(context, redditDataRoomDatabase, accountName)
                        handler.post { AppRestartHelper.triggerAppRestart(context) }
                    }
                }
                .setNegativeButton(R.string.no, null)
                .show()
            true
        }
    }

    /**
     * The account picked to copy from, handed on by `SettingsActivity`: the chooser reports to the
     * activity hosting it rather than to whoever opened it.
     */
    override fun onAccountSelected(account: Account) {
        val source = account.accountName
        val destination = mActivity.accountName

        MaterialAlertDialogBuilder(mActivity, R.style.MaterialAlertDialogTheme)
            .setTitle(R.string.settings_copy_settings_from_account_title)
            .setMessage(getString(R.string.copy_account_settings_confirmation,
                displayName(source), displayName(destination)))
            .setPositiveButton(R.string.yes) { _, _ ->
                val context = mActivity.applicationContext
                executor.execute {
                    AccountSettings.copyBetweenAccounts(
                        context, redditDataRoomDatabase, source, destination)
                    handler.post { AppRestartHelper.triggerAppRestart(context) }
                }
            }
            .setNegativeButton(R.string.no, null)
            .show()
    }

    /** What to call an account in a sentence; the anonymous one is stored as a placeholder name. */
    private fun displayName(accountName: String?): String =
        if (Account.ANONYMOUS_ACCOUNT == accountName) getString(R.string.anonymous_account)
        else accountName.orEmpty()
}
