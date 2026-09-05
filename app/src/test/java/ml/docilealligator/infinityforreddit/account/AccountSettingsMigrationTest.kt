package ml.docilealligator.infinityforreddit.account

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import java.util.concurrent.Executor
import ml.docilealligator.infinityforreddit.RedditDataRoomDatabase
import ml.docilealligator.infinityforreddit.utils.SharedPreferencesUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner

/**
 * The one-time migration onto [AccountScope], and the two later additions to it.
 *
 * This is the least reversible code in the whole change: it runs once, rewrites keys five different
 * conventions wrote, and deletes the originals. Getting the anonymous precedence backwards, or
 * seeding an account that already had a value, is not a crash — it is somebody's settings quietly
 * becoming somebody else's.
 */
@RunWith(RobolectricTestRunner::class)
class AccountSettingsMigrationTest {

    /**
     * How anonymous was spelled in the key prefixes this migration reads. A literal, like the
     * production copy: [Account.ANONYMOUS_ACCOUNT] has since become ".anonymous", while the keys
     * these tests write are the ones that are still on disk saying "-".
     */
    private val LEGACY_ANONYMOUS_PREFIX = "-"

    private val context: Context = ApplicationProvider.getApplicationContext()

    private lateinit var internal: SharedPreferences
    private lateinit var bottomAppBar: SharedPreferences
    private lateinit var defaultPreferences: SharedPreferences
    private lateinit var nsfwAndSpoiler: SharedPreferences
    private lateinit var postLayout: SharedPreferences

    @Before
    fun setUp() {
        internal = context.getSharedPreferences("internal_test", Context.MODE_PRIVATE)
        bottomAppBar = file(SharedPreferencesUtils.BOTTOM_APP_BAR_SHARED_PREFERENCES_FILE)
        defaultPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        nsfwAndSpoiler = file(SharedPreferencesUtils.NSFW_AND_SPOILER_SHARED_PREFERENCES_FILE)
        postLayout = file(SharedPreferencesUtils.POST_LAYOUT_SHARED_PREFERENCES_FILE)

        // Every file the migration opens, so one test cannot inherit another's keys.
        for (preferences in listOf(
            bottomAppBar, defaultPreferences, nsfwAndSpoiler, postLayout,
            file(SharedPreferencesUtils.POST_HISTORY_SHARED_PREFERENCES_FILE),
            file(SharedPreferencesUtils.RECENTLY_VISITED_SHARED_PREFERENCES_FILE),
            file(SharedPreferencesUtils.MAIN_PAGE_TABS_SHARED_PREFERENCES_FILE),
            file(SharedPreferencesUtils.SORT_TYPE_SHARED_PREFERENCES_FILE),
            file(SharedPreferencesUtils.NAVIGATION_DRAWER_SHARED_PREFERENCES_FILE),
            file(SharedPreferencesUtils.POST_DETAILS_SHARED_PREFERENCES_FILE),
        )) {
            preferences.edit().clear().commit()
        }
        internal.edit().clear().commit()
        internal.edit()
            .putBoolean(SharedPreferencesUtils.ACCOUNT_SCOPE_MIGRATED, true)
            .putBoolean(SharedPreferencesUtils.ACCOUNT_SCOPE_SEEDED, true)
            .commit()
    }

    private fun file(name: String): SharedPreferences =
        context.getSharedPreferences(name, Context.MODE_PRIVATE)

    private fun database(vararg accountNames: String): RedditDataRoomDatabase {
        val accountDao = mock<AccountDao>()
        whenever(accountDao.allAccounts).thenReturn(
            accountNames.map { Account(it, null, null, null, null, null, 0, false, false) })
        return mock<RedditDataRoomDatabase>().also { whenever(it.accountDao()).thenReturn(accountDao) }
    }

    /** Runs the migration as a first upgrade does, with none of its steps yet taken. */
    private fun migrateFromScratch(vararg accountNames: String) {
        internal.edit().clear().commit()
        AccountSettingsMigration.migrate(
            context, internal, Executor { it.run() }, database(*accountNames))
    }

    private fun hasRun() =
        internal.getBoolean(SharedPreferencesUtils.ACCOUNT_SCOPE_MIGRATED, false) &&
            internal.getBoolean(SharedPreferencesUtils.ACCOUNT_SCOPE_SEEDED, false)

    @Test
    fun `a backup from before the key scheme asks for the migration again`() {
        AccountSettingsMigration.rerunIfBackupPredatesAccountScope(
            internal,
            mapOf(SharedPreferencesUtils.THEME_KEY to "2", SharedPreferencesUtils.APP_LOCK to true),
        )

        assertFalse("its per-account settings are all under their old global spellings", hasRun())
    }

    @Test
    fun `a backup that already carries scoped keys is left alone`() {
        AccountSettingsMigration.rerunIfBackupPredatesAccountScope(
            internal,
            mapOf(
                AccountScope.key("alice", SharedPreferencesUtils.THEME_KEY) to "2",
                SharedPreferencesUtils.APP_LOCK to true,
            ),
        )

        assertTrue(hasRun())
    }

    @Test
    fun `a key that only looks scoped does not count`() {
        // A namespace is not enough: the base has to be a setting the classification calls
        // per-account, or any file with a dotted key would pass for a migrated one.
        AccountSettingsMigration.rerunIfBackupPredatesAccountScope(
            internal,
            mapOf(AccountScope.key("alice", SharedPreferencesUtils.APP_LOCK) to true),
        )

        assertFalse(hasRun())
    }

    @Test
    fun `a default file the restore could not read decides nothing`() {
        AccountSettingsMigration.rerunIfBackupPredatesAccountScope(internal, null)

        assertTrue("with nothing to judge by, leave the device as it was", hasRun())
    }

    /** Runs the migration as a later upgrade does: the first two steps already done, this one not. */
    private fun migrateBottomAppBar(vararg accountNames: String) {
        internal.edit()
            .putBoolean(SharedPreferencesUtils.ACCOUNT_SCOPE_MIGRATED, true)
            .putBoolean(SharedPreferencesUtils.ACCOUNT_SCOPE_SEEDED, true)
            .putInt(SharedPreferencesUtils.ACCOUNT_SCOPE_SEED_VERSION, 99)
            .putBoolean(SharedPreferencesUtils.BOTTOM_APP_BAR_SCOPE_MIGRATED, false)
            .commit()

        AccountSettingsMigration.migrate(
            context, internal, Executor { it.run() }, database(*accountNames))
    }

    @Test
    fun `every account inherits the bar they were all sharing, and anonymous keeps its own`() {
        // The old convention: one bar under the bare key for whoever was signed in, and a second
        // under "-" for anonymous.
        bottomAppBar.edit()
            .putInt(SharedPreferencesUtils.MAIN_ACTIVITY_BOTTOM_APP_BAR_OPTION_1, 5)
            .putInt(LEGACY_ANONYMOUS_PREFIX + SharedPreferencesUtils.MAIN_ACTIVITY_BOTTOM_APP_BAR_OPTION_1, 9)
            .commit()

        migrateBottomAppBar("alice", "bob")

        assertEquals(5, bottomAppBar.getInt(
            AccountScope.key("alice", SharedPreferencesUtils.MAIN_ACTIVITY_BOTTOM_APP_BAR_OPTION_1), -1))
        assertEquals(5, bottomAppBar.getInt(
            AccountScope.key("bob", SharedPreferencesUtils.MAIN_ACTIVITY_BOTTOM_APP_BAR_OPTION_1), -1))
        assertEquals(9, bottomAppBar.getInt(
            AccountScope.key(null, SharedPreferencesUtils.MAIN_ACTIVITY_BOTTOM_APP_BAR_OPTION_1), -1))
    }

    @Test
    fun `the old spellings go, so a second run has nothing to do`() {
        bottomAppBar.edit()
            .putInt(SharedPreferencesUtils.MAIN_ACTIVITY_BOTTOM_APP_BAR_OPTION_1, 5)
            .putInt(LEGACY_ANONYMOUS_PREFIX + SharedPreferencesUtils.MAIN_ACTIVITY_BOTTOM_APP_BAR_OPTION_1, 9)
            .commit()

        migrateBottomAppBar("alice")

        assertFalse(bottomAppBar.contains(SharedPreferencesUtils.MAIN_ACTIVITY_BOTTOM_APP_BAR_OPTION_1))
        assertFalse(bottomAppBar.contains(
            LEGACY_ANONYMOUS_PREFIX + SharedPreferencesUtils.MAIN_ACTIVITY_BOTTOM_APP_BAR_OPTION_1))
    }

    @Test
    fun `a bar an account already owns is not overwritten`() {
        bottomAppBar.edit()
            .putInt(SharedPreferencesUtils.MAIN_ACTIVITY_BOTTOM_APP_BAR_OPTION_1, 5)
            .putInt(AccountScope.key("alice", SharedPreferencesUtils.MAIN_ACTIVITY_BOTTOM_APP_BAR_OPTION_1), 3)
            .commit()

        migrateBottomAppBar("alice")

        assertEquals("the canonical key wins over any old one it disagrees with",
            3, bottomAppBar.getInt(
                AccountScope.key("alice", SharedPreferencesUtils.MAIN_ACTIVITY_BOTTOM_APP_BAR_OPTION_1), -1))
    }

    // --- rescope: the five old conventions, retired ------------------------------------------

    @Test
    fun `a signed-in account's old key moves to its scoped spelling`() {
        nsfwAndSpoiler.edit().putBoolean("alice" + SharedPreferencesUtils.NSFW_BASE, true).commit()

        migrateFromScratch("alice")

        assertTrue(nsfwAndSpoiler.getBoolean(
            AccountScope.key("alice", SharedPreferencesUtils.NSFW_BASE), false))
        assertFalse("the old spelling is what a second run must not find again",
            nsfwAndSpoiler.contains("alice" + SharedPreferencesUtils.NSFW_BASE))
    }

    @Test
    fun `anonymous prefers the spelling the user chose over the one silently in force`() {
        // The link handler was written under "-" and read back with no prefix at all, so both keys
        // exist and disagree. The one the user's choice landed in has to win.
        defaultPreferences.edit()
            .putString(LEGACY_ANONYMOUS_PREFIX + SharedPreferencesUtils.LINK_HANDLER_BASE, "4")
            .putString(SharedPreferencesUtils.LINK_HANDLER, "0")
            .commit()

        migrateFromScratch()

        assertEquals("4", defaultPreferences.getString(
            AccountScope.key(null, SharedPreferencesUtils.LINK_HANDLER_BASE), null))
        assertFalse(defaultPreferences.contains(
            LEGACY_ANONYMOUS_PREFIX + SharedPreferencesUtils.LINK_HANDLER_BASE))
        assertFalse("every spelling it could have been read from goes, not just the one taken",
            defaultPreferences.contains(SharedPreferencesUtils.LINK_HANDLER))
    }

    @Test
    fun `anonymous prefers the dash spelling over the empty-prefix one`() {
        // The other half of the disagreement: the NSFW screen wrote anonymous with an empty prefix
        // and post history wrote it with "-", so both spellings of the same switch can exist.
        nsfwAndSpoiler.edit()
            .putBoolean(LEGACY_ANONYMOUS_PREFIX + SharedPreferencesUtils.NSFW_BASE, true)
            .putBoolean(SharedPreferencesUtils.NSFW_BASE, false)
            .commit()

        migrateFromScratch()

        assertTrue(nsfwAndSpoiler.getBoolean(
            AccountScope.key(null, SharedPreferencesUtils.NSFW_BASE), false))
        assertFalse(nsfwAndSpoiler.contains(SharedPreferencesUtils.NSFW_BASE))
    }

    @Test
    fun `anonymous falls back to the key that was silently in force`() {
        defaultPreferences.edit().putString(SharedPreferencesUtils.LINK_HANDLER, "3").commit()

        migrateFromScratch()

        assertEquals("what anonymous was actually getting, with nothing better on offer",
            "3", defaultPreferences.getString(
                AccountScope.key(null, SharedPreferencesUtils.LINK_HANDLER_BASE), null))
    }

    @Test
    fun `a key already in its canonical spelling is not overwritten`() {
        nsfwAndSpoiler.edit()
            .putBoolean(AccountScope.key("alice", SharedPreferencesUtils.NSFW_BASE), false)
            .putBoolean("alice" + SharedPreferencesUtils.NSFW_BASE, true)
            .commit()

        migrateFromScratch("alice")

        assertFalse("a second run must not undo the first",
            nsfwAndSpoiler.getBoolean(
                AccountScope.key("alice", SharedPreferencesUtils.NSFW_BASE), true))
    }

    // --- seed: what a setting that has just become per-account starts as ----------------------

    @Test
    fun `every account inherits the global value of a newly per-account setting`() {
        defaultPreferences.edit()
            .putString(SharedPreferencesUtils.FONT_SIZE_KEY, "XLarge")
            .commit()

        migrateFromScratch("alice", "bob")

        for (accountName in listOf("alice", "bob", null)) {
            assertEquals(accountName.toString(), "XLarge", defaultPreferences.getString(
                AccountScope.key(accountName, SharedPreferencesUtils.FONT_SIZE_KEY), null))
        }
    }

    @Test
    fun `an account that already has its own value keeps it`() {
        defaultPreferences.edit()
            .putString(SharedPreferencesUtils.FONT_SIZE_KEY, "XLarge")
            .putString(AccountScope.key("alice", SharedPreferencesUtils.FONT_SIZE_KEY), "Small")
            .commit()

        migrateFromScratch("alice", "bob")

        assertEquals("Small", defaultPreferences.getString(
            AccountScope.key("alice", SharedPreferencesUtils.FONT_SIZE_KEY), null))
        assertEquals("XLarge", defaultPreferences.getString(
            AccountScope.key("bob", SharedPreferencesUtils.FONT_SIZE_KEY), null))
    }

    @Test
    fun `a setting that stays global is not seeded`() {
        defaultPreferences.edit().putBoolean(SharedPreferencesUtils.APP_LOCK, true).commit()

        migrateFromScratch("alice")

        assertFalse("app lock is one lock for the device, not one per account",
            defaultPreferences.contains(AccountScope.key("alice", SharedPreferencesUtils.APP_LOCK)))
        assertTrue(defaultPreferences.getBoolean(SharedPreferencesUtils.APP_LOCK, false))
    }

    @Test
    fun `a whole-file scope seeds every key it finds, not a list of them`() {
        postLayout.edit().putInt("post_layout_subreddit_post_pics", 2).commit()

        migrateFromScratch("alice")

        assertEquals(2, postLayout.getInt(
            AccountScope.key("alice", "post_layout_subreddit_post_pics"), -1))
        assertEquals(2, postLayout.getInt(
            AccountScope.key(null, "post_layout_subreddit_post_pics"), -1))
    }

    // --- the seeding round, and what a later one has to pick up --------------------------------

    @Test
    fun `a device seeded before the version existed is caught up, not seeded from scratch`() {
        // What an upgrade looks like: round one done under the old boolean, and a setting that has
        // become per-account since.
        defaultPreferences.edit()
            .putString(SharedPreferencesUtils.SUBREDDIT_DEFAULT_SORT_TYPE, "top")
            .putString(SharedPreferencesUtils.FONT_SIZE_KEY, "XLarge")
            .putString(AccountScope.key("alice", SharedPreferencesUtils.FONT_SIZE_KEY), "Small")
            .commit()
        internal.edit()
            .putBoolean(SharedPreferencesUtils.ACCOUNT_SCOPE_MIGRATED, true)
            .putBoolean(SharedPreferencesUtils.ACCOUNT_SCOPE_SEEDED, true)
            .putBoolean(SharedPreferencesUtils.BOTTOM_APP_BAR_SCOPE_MIGRATED, true)
            .commit()

        AccountSettingsMigration.migrate(
            context, internal, Executor { it.run() }, database("alice"))

        assertEquals("the newly per-account key is seeded on the second round",
            "top", defaultPreferences.getString(
                AccountScope.key("alice", SharedPreferencesUtils.SUBREDDIT_DEFAULT_SORT_TYPE), null))
        assertEquals("and one the first round already settled is left alone",
            "Small", defaultPreferences.getString(
                AccountScope.key("alice", SharedPreferencesUtils.FONT_SIZE_KEY), null))
    }

    @Test
    fun `a device seeded a round ago picks up the bottom app bar toggle`() {
        // The seeding round the toggle was added in. A device that stopped at the round before it
        // has the global value and no scoped one, and reads the switch as off however it was left.
        defaultPreferences.edit()
            .putBoolean(SharedPreferencesUtils.BOTTOM_APP_BAR_KEY, true)
            .commit()
        internal.edit()
            .putBoolean(SharedPreferencesUtils.ACCOUNT_SCOPE_MIGRATED, true)
            .putInt(SharedPreferencesUtils.ACCOUNT_SCOPE_SEED_VERSION, 2)
            .putBoolean(SharedPreferencesUtils.BOTTOM_APP_BAR_SCOPE_MIGRATED, true)
            .commit()

        AccountSettingsMigration.migrate(
            context, internal, Executor { it.run() }, database("alice"))

        assertTrue("the bar the account had, kept",
            defaultPreferences.getBoolean(
                AccountScope.key("alice", SharedPreferencesUtils.BOTTOM_APP_BAR_KEY), false))
        assertTrue("anonymous keeps it too",
            defaultPreferences.getBoolean(
                AccountScope.key(null, SharedPreferencesUtils.BOTTOM_APP_BAR_KEY), false))
    }

    @Test
    fun `a device already at this version seeds nothing`() {
        defaultPreferences.edit()
            .putString(SharedPreferencesUtils.SUBREDDIT_DEFAULT_SORT_TYPE, "top")
            .commit()
        internal.edit()
            .putBoolean(SharedPreferencesUtils.ACCOUNT_SCOPE_MIGRATED, true)
            .putInt(SharedPreferencesUtils.ACCOUNT_SCOPE_SEED_VERSION, 99)
            .putBoolean(SharedPreferencesUtils.BOTTOM_APP_BAR_SCOPE_MIGRATED, true)
            .commit()

        AccountSettingsMigration.migrate(
            context, internal, Executor { it.run() }, database("alice"))

        assertFalse("a round that has already happened must not happen again",
            defaultPreferences.contains(
                AccountScope.key("alice", SharedPreferencesUtils.SUBREDDIT_DEFAULT_SORT_TYPE)))
    }

    @Test
    fun `restoring an older backup asks for the seeding round again, not just the flag`() {
        internal.edit()
            .putBoolean(SharedPreferencesUtils.ACCOUNT_SCOPE_MIGRATED, true)
            .putInt(SharedPreferencesUtils.ACCOUNT_SCOPE_SEED_VERSION, 99)
            .commit()

        AccountSettingsMigration.rerunIfBackupPredatesAccountScope(
            internal, mapOf(SharedPreferencesUtils.THEME_KEY to "2"))

        defaultPreferences.edit()
            .putString(SharedPreferencesUtils.SUBREDDIT_DEFAULT_SORT_TYPE, "top")
            .commit()
        AccountSettingsMigration.migrate(
            context, internal, Executor { it.run() }, database("alice"))

        assertEquals("a version left standing would have skipped the seeding",
            "top", defaultPreferences.getString(
                AccountScope.key("alice", SharedPreferencesUtils.SUBREDDIT_DEFAULT_SORT_TYPE), null))
    }

    // --- the combined Save Sort Type toggle, split per account --------------------------------

    @Test
    fun `every account takes the old combined save-sort choice`() {
        defaultPreferences.edit()
            .putBoolean(SharedPreferencesUtils.SAVE_SORT_TYPE, false)
            .commit()

        migrateFromScratch("alice", "bob")

        for (accountName in listOf("alice", "bob", null)) {
            assertFalse(accountName.toString(), defaultPreferences.getBoolean(
                AccountScope.key(accountName, SharedPreferencesUtils.SAVE_POST_SORT), true))
            assertFalse(accountName.toString(), defaultPreferences.getBoolean(
                AccountScope.key(accountName, SharedPreferencesUtils.SAVE_COMMENT_SORT), true))
        }
    }

    @Test
    fun `the newer choice outranks the combined one it replaced`() {
        // Both keys present is the common case for anyone who upgraded through the split: the
        // seeding runs first, and the legacy value must not land on top of what it seeded.
        defaultPreferences.edit()
            .putBoolean(SharedPreferencesUtils.SAVE_SORT_TYPE, false)
            .putBoolean(SharedPreferencesUtils.SAVE_POST_SORT, true)
            .commit()

        migrateFromScratch("alice")

        assertTrue("seeded from save_post_sort, not from the combined toggle",
            defaultPreferences.getBoolean(
                AccountScope.key("alice", SharedPreferencesUtils.SAVE_POST_SORT), false))
        assertFalse("the comment half had nothing newer, so it takes the combined one",
            defaultPreferences.getBoolean(
                AccountScope.key("alice", SharedPreferencesUtils.SAVE_COMMENT_SORT), true))
    }

    @Test
    fun `an install that never had the combined toggle is left alone`() {
        migrateFromScratch("alice")

        assertFalse(defaultPreferences.contains(
            AccountScope.key("alice", SharedPreferencesUtils.SAVE_POST_SORT)))
    }
}
