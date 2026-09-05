package ml.docilealligator.infinityforreddit.settings

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.annotation.StringRes
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
import ml.docilealligator.infinityforreddit.account.AccountStoredData
import ml.docilealligator.infinityforreddit.account.AccountViewModel
import ml.docilealligator.infinityforreddit.bottomsheetfragments.AccountChooserBottomSheetFragment
import ml.docilealligator.infinityforreddit.customviews.preference.CustomFontPreferenceFragmentCompat
import ml.docilealligator.infinityforreddit.events.RecreateActivityEvent
import ml.docilealligator.infinityforreddit.readpost.ReadPostType
import ml.docilealligator.infinityforreddit.utils.AppRestartHelper
import ml.docilealligator.infinityforreddit.utils.SharedPreferencesUtils
import org.greenrobot.eventbus.EventBus

/**
 * Everything that acts on this account's settings and data as a whole rather than on one of them:
 * copying another account's settings over, putting this one back to defaults, and throwing away one
 * kind of accumulated data at a time.
 *
 * The two settings operations end by restarting the app: they rewrite preference files that the
 * running process has already read, and a screen showing values from before the copy is worse than
 * a restart. The deletions below do not — each is narrow enough that recreating the open activities
 * is all the running app needs, where it needs anything.
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

        // The subscription lists are observed as LiveData by the drawer and the Subscriptions
        // screen, so those redraw on their own once the rows are gone.
        deleteAction(SharedPreferencesUtils.DELETE_ACCOUNT_SUBREDDITS,
            R.string.delete_account_subreddits_confirmation,
            R.string.delete_all_subreddits_success,
            recreateActivities = false) { _, accountName ->
            AccountStoredData.deleteSubscribedSubreddits(redditDataRoomDatabase, accountName)
        }

        deleteAction(SharedPreferencesUtils.DELETE_ACCOUNT_USERS,
            R.string.delete_account_users_confirmation,
            R.string.delete_all_users_success,
            recreateActivities = false) { _, accountName ->
            AccountStoredData.deleteSubscribedUsers(redditDataRoomDatabase, accountName)
        }

        // A feed reads its sort order and its layout once, when it is built, so the open activities
        // have to be recreated for either deletion to show.
        deleteAction(SharedPreferencesUtils.DELETE_ACCOUNT_SORT_TYPES,
            R.string.delete_account_sort_types_confirmation,
            R.string.delete_all_sort_types_success,
            recreateActivities = true) { context, accountName ->
            AccountStoredData.deleteSortTypes(context, accountName)
        }

        deleteAction(SharedPreferencesUtils.DELETE_ACCOUNT_POST_LAYOUTS,
            R.string.delete_account_post_layouts_confirmation,
            R.string.delete_all_post_layouts_success,
            recreateActivities = true) { context, accountName ->
            AccountStoredData.deletePostLayouts(context, accountName)
        }

        deleteAction(SharedPreferencesUtils.DELETE_ACCOUNT_FRONT_PAGE_SCROLLED_POSITION,
            R.string.delete_account_front_page_scrolled_position_confirmation,
            R.string.delete_all_front_page_scrolled_positions_success,
            recreateActivities = false) { context, accountName ->
            AccountStoredData.deleteFrontPageScrolledPosition(context, accountName)
        }

        // The only row whose summary is a measurement, so the only one with anything to say once
        // it has been acted on. Nothing else here recreates this screen, so it re-reads it itself.
        deleteAction(SharedPreferencesUtils.DELETE_ACCOUNT_READ_POSTS,
            R.string.delete_account_read_posts_confirmation,
            R.string.delete_all_read_posts_success,
            recreateActivities = false,
            afterDelete = ::showReadPostsSize) { _, accountName ->
            AccountStoredData.deleteReadPosts(redditDataRoomDatabase, accountName)
        }

        showReadPostsSize()
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

    /**
     * Wires one of the delete rows: confirm naming the account, then do it off the main thread.
     *
     * The account name and the context are read while the dialog is built rather than when it is
     * answered, so a deletion always applies to the account whose screen was open.
     */
    private fun deleteAction(
        key: String,
        @StringRes confirmation: Int,
        @StringRes success: Int,
        recreateActivities: Boolean,
        afterDelete: () -> Unit = {},
        delete: (Context, String) -> Unit,
    ) {
        val preference = findPreference<Preference>(key) ?: return
        preference.setOnPreferenceClickListener {
            val accountName = mActivity.accountName
            val context = mActivity.applicationContext
            MaterialAlertDialogBuilder(mActivity, R.style.MaterialAlertDialogTheme)
                .setTitle(preference.title)
                .setMessage(getString(confirmation, displayName(accountName)))
                .setPositiveButton(R.string.yes) { _, _ ->
                    executor.execute {
                        delete(context, accountName)
                        handler.post {
                            // The application context, not the fragment's: backing out of the
                            // screen while the deletion runs would leave the toast without one.
                            Toast.makeText(context, success, Toast.LENGTH_SHORT).show()
                            if (isAdded) {
                                afterDelete()
                            }
                            if (recreateActivities) {
                                EventBus.getDefault().post(RecreateActivityEvent())
                            }
                        }
                    }
                }
                .setNegativeButton(R.string.no, null)
                .show()
            true
        }
    }

    /**
     * How much the read history is costing, on the row that deletes it.
     *
     * The measurement is two queries, so the row is given a summary of the same one-line shape
     * first. Without it the row is a line shorter until the count arrives and then grows, and it is
     * the last row in its group — the group's bottom edge would move a moment after the screen
     * opened.
     */
    private fun showReadPostsSize() {
        val preference =
            findPreference<Preference>(SharedPreferencesUtils.DELETE_ACCOUNT_READ_POSTS) ?: return
        preference.setSummary(R.string.settings_read_posts_size_measuring)
        val accountName = mActivity.accountName
        executor.execute {
            val readPostDao = redditDataRoomDatabase.readPostDao()
            val count = readPostDao.getReadPostsCount(accountName, ReadPostType.READ_POSTS)
            val sizeInKb = readPostDao.maxReadPostEntrySize.toLong() * count / 1024
            handler.post {
                // Backing out of the screen while those two queries run detaches the fragment, and
                // getString() then throws "not attached to a context".
                if (isAdded) {
                    preference.summary =
                        getString(R.string.settings_read_posts_size_summary, sizeInKb, count)
                }
            }
        }
    }

    /** What to call an account in a sentence; the anonymous one is stored as a placeholder name. */
    private fun displayName(accountName: String?): String =
        if (Account.ANONYMOUS_ACCOUNT == accountName) getString(R.string.anonymous_account)
        else accountName.orEmpty()
}
