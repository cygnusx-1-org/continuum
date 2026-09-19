package ml.docilealligator.infinityforreddit.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import java.util.concurrent.Executor
import ml.docilealligator.infinityforreddit.RedditDataRoomDatabase
import ml.docilealligator.infinityforreddit.account.Account
import ml.docilealligator.infinityforreddit.account.AccountDao
import ml.docilealligator.infinityforreddit.account.AccountScope
import ml.docilealligator.infinityforreddit.account.AccountSettingsMigration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner

/**
 * The one-time swap that turned "the action of a swipe to the left" into "the action on the left
 * side of the row". Every existing value is on the wrong side until this has run, so what matters
 * is that it runs exactly once per file, for every account and every level, and that a restored
 * backup from before it is caught too.
 */
@RunWith(RobolectricTestRunner::class)
class SwipeActionSideMigrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private lateinit var preferences: SharedPreferences

    @Before
    fun setUp() {
        preferences = PreferenceManager.getDefaultSharedPreferences(context)
        preferences.edit().clear().commit()
    }

    private fun scoped(accountName: String?, base: String) = AccountScope.key(accountName, base)

    private fun string(key: String): String? = preferences.getString(key, null)

    @Test
    fun `every level of every account changes sides`() {
        preferences.edit()
            .putString(scoped("alice", SharedPreferencesUtils.SWIPE_LEFT_ACTION), "0")
            .putString(scoped("alice", SharedPreferencesUtils.SWIPE_RIGHT_ACTION), "1")
            .putString(scoped("alice", SharedPreferencesUtils.SWIPE_LEFT_ACTION_LEVEL_2), "2")
            .putString(scoped("alice", SharedPreferencesUtils.SWIPE_RIGHT_ACTION_LEVEL_2), "3")
            .putString(scoped("alice", SharedPreferencesUtils.SWIPE_LEFT_ACTION_LEVEL_3), "4")
            .putString(scoped("alice", SharedPreferencesUtils.SWIPE_RIGHT_ACTION_LEVEL_3), "5")
            .putString(scoped("alice", SharedPreferencesUtils.SWIPE_LEFT_ACTION_LEVEL_4), "6")
            .putString(scoped("alice", SharedPreferencesUtils.SWIPE_RIGHT_ACTION_LEVEL_4), "7")
            .putString(scoped("alice", SharedPreferencesUtils.COMMENT_SWIPE_LEFT_ACTION), "0")
            .putString(scoped("alice", SharedPreferencesUtils.COMMENT_SWIPE_RIGHT_ACTION), "1")
            .putString(scoped("alice", SharedPreferencesUtils.COMMENT_SWIPE_LEFT_ACTION_LEVEL_2), "2")
            .putString(scoped("alice", SharedPreferencesUtils.COMMENT_SWIPE_RIGHT_ACTION_LEVEL_2), "3")
            .putString(scoped("alice", SharedPreferencesUtils.COMMENT_SWIPE_LEFT_ACTION_LEVEL_3), "4")
            .putString(scoped("alice", SharedPreferencesUtils.COMMENT_SWIPE_RIGHT_ACTION_LEVEL_3), "5")
            // A second account, with a different choice, so one account's swap cannot pass for
            // another's.
            .putString(scoped(null, SharedPreferencesUtils.SWIPE_LEFT_ACTION), "3")
            .putString(scoped(null, SharedPreferencesUtils.SWIPE_RIGHT_ACTION), "6")
            .commit()

        SwipeActionSideMigration.migrate(preferences)

        assertEquals("1", string(scoped("alice", SharedPreferencesUtils.SWIPE_LEFT_ACTION)))
        assertEquals("0", string(scoped("alice", SharedPreferencesUtils.SWIPE_RIGHT_ACTION)))
        assertEquals("3", string(scoped("alice", SharedPreferencesUtils.SWIPE_LEFT_ACTION_LEVEL_2)))
        assertEquals("2", string(scoped("alice", SharedPreferencesUtils.SWIPE_RIGHT_ACTION_LEVEL_2)))
        assertEquals("5", string(scoped("alice", SharedPreferencesUtils.SWIPE_LEFT_ACTION_LEVEL_3)))
        assertEquals("4", string(scoped("alice", SharedPreferencesUtils.SWIPE_RIGHT_ACTION_LEVEL_3)))
        assertEquals("7", string(scoped("alice", SharedPreferencesUtils.SWIPE_LEFT_ACTION_LEVEL_4)))
        assertEquals("6", string(scoped("alice", SharedPreferencesUtils.SWIPE_RIGHT_ACTION_LEVEL_4)))
        assertEquals("1", string(scoped("alice", SharedPreferencesUtils.COMMENT_SWIPE_LEFT_ACTION)))
        assertEquals("0", string(scoped("alice", SharedPreferencesUtils.COMMENT_SWIPE_RIGHT_ACTION)))
        assertEquals("3", string(scoped("alice", SharedPreferencesUtils.COMMENT_SWIPE_LEFT_ACTION_LEVEL_2)))
        assertEquals("2", string(scoped("alice", SharedPreferencesUtils.COMMENT_SWIPE_RIGHT_ACTION_LEVEL_2)))
        assertEquals("5", string(scoped("alice", SharedPreferencesUtils.COMMENT_SWIPE_LEFT_ACTION_LEVEL_3)))
        assertEquals("4", string(scoped("alice", SharedPreferencesUtils.COMMENT_SWIPE_RIGHT_ACTION_LEVEL_3)))
        assertEquals("6", string(scoped(null, SharedPreferencesUtils.SWIPE_LEFT_ACTION)))
        assertEquals("3", string(scoped(null, SharedPreferencesUtils.SWIPE_RIGHT_ACTION)))
        assertTrue(SwipeActionSideMigration.isDone(preferences))
    }

    @Test
    fun `the inert pre-account copy turns over with the rest`() {
        // Seeding left the original global key in place. Nothing reads it, but a file whose keys
        // disagree about which side is which is a trap for the next migration to read it.
        preferences.edit()
            .putString(SharedPreferencesUtils.SWIPE_LEFT_ACTION, "0")
            .putString(SharedPreferencesUtils.SWIPE_RIGHT_ACTION, "1")
            .commit()

        SwipeActionSideMigration.migrate(preferences)

        assertEquals("1", string(SharedPreferencesUtils.SWIPE_LEFT_ACTION))
        assertEquals("0", string(SharedPreferencesUtils.SWIPE_RIGHT_ACTION))
    }

    @Test
    fun `a side with nothing stored moves across as nothing`() {
        // Only the left was ever changed from its default; the right is unset, and so reads the
        // code default. After the swap the right holds the choice and the left is back on default.
        preferences.edit()
            .putString(scoped("alice", SharedPreferencesUtils.SWIPE_LEFT_ACTION_LEVEL_2), "2")
            .commit()

        SwipeActionSideMigration.migrate(preferences)

        assertNull(string(scoped("alice", SharedPreferencesUtils.SWIPE_LEFT_ACTION_LEVEL_2)))
        assertEquals("2", string(scoped("alice", SharedPreferencesUtils.SWIPE_RIGHT_ACTION_LEVEL_2)))
    }

    @Test
    fun `runs once`() {
        preferences.edit()
            .putString(scoped("alice", SharedPreferencesUtils.SWIPE_LEFT_ACTION), "0")
            .putString(scoped("alice", SharedPreferencesUtils.SWIPE_RIGHT_ACTION), "1")
            .commit()

        SwipeActionSideMigration.migrate(preferences)
        SwipeActionSideMigration.migrate(preferences)

        assertEquals("a second run would swap them back",
            "1", string(scoped("alice", SharedPreferencesUtils.SWIPE_LEFT_ACTION)))
        assertEquals("0", string(scoped("alice", SharedPreferencesUtils.SWIPE_RIGHT_ACTION)))
    }

    @Test
    fun `a fresh install is marked done with nothing to swap`() {
        SwipeActionSideMigration.migrate(preferences)

        assertTrue(SwipeActionSideMigration.isDone(preferences))
        assertEquals(setOf(SharedPreferencesUtils.SWIPE_ACTION_SIDES_MIGRATED), preferences.all.keys)
    }

    @Test
    fun `keys that are not swipe sides are left alone`() {
        preferences.edit()
            .putString(SharedPreferencesUtils.SWIPE_ACTION_THRESHOLD, "0.4")
            .putBoolean(scoped("alice", SharedPreferencesUtils.ENABLE_SWIPE_ACTION), true)
            .putString(SharedPreferencesUtils.THEME_KEY, "2")
            .commit()

        SwipeActionSideMigration.migrate(preferences)

        assertEquals("0.4", string(SharedPreferencesUtils.SWIPE_ACTION_THRESHOLD))
        assertTrue(preferences.getBoolean(scoped("alice", SharedPreferencesUtils.ENABLE_SWIPE_ACTION), false))
        assertEquals("2", string(SharedPreferencesUtils.THEME_KEY))
    }

    @Test
    fun `the application's migration chain runs it even when every other step is done`() {
        val internal = context.getSharedPreferences("internal_test", Context.MODE_PRIVATE)
        internal.edit().clear()
            // Everything else already done, which is what every upgrading device looks like.
            .putBoolean(SharedPreferencesUtils.ACCOUNT_SCOPE_MIGRATED, true)
            .putInt(SharedPreferencesUtils.ACCOUNT_SCOPE_SEED_VERSION, 99)
            .putBoolean(SharedPreferencesUtils.BOTTOM_APP_BAR_SCOPE_MIGRATED, true)
            .commit()
        preferences.edit()
            .putString(scoped("alice", SharedPreferencesUtils.SWIPE_LEFT_ACTION), "0")
            .putString(scoped("alice", SharedPreferencesUtils.SWIPE_RIGHT_ACTION), "1")
            .commit()

        AccountSettingsMigration.migrate(context, internal, Executor { it.run() }, database("alice"))

        assertEquals("1", string(scoped("alice", SharedPreferencesUtils.SWIPE_LEFT_ACTION)))
        assertEquals("0", string(scoped("alice", SharedPreferencesUtils.SWIPE_RIGHT_ACTION)))
        assertTrue(SwipeActionSideMigration.isDone(preferences))
    }

    @Test
    fun `a device that has never been seeded ends up by side on both copies`() {
        // The global key is all there is. The swap turns it over first, and the seeding then copies
        // the swapped value onto the account -- never the original, which would put the account
        // back on the wrong side.
        val internal = context.getSharedPreferences("internal_test", Context.MODE_PRIVATE)
        internal.edit().clear().commit()
        preferences.edit()
            .putString(SharedPreferencesUtils.SWIPE_LEFT_ACTION, "0")
            .putString(SharedPreferencesUtils.SWIPE_RIGHT_ACTION, "1")
            .commit()

        AccountSettingsMigration.migrate(context, internal, Executor { it.run() }, database("alice"))

        assertEquals("1", string(scoped("alice", SharedPreferencesUtils.SWIPE_LEFT_ACTION)))
        assertEquals("0", string(scoped("alice", SharedPreferencesUtils.SWIPE_RIGHT_ACTION)))
        assertEquals("1", string(SharedPreferencesUtils.SWIPE_LEFT_ACTION))
        assertEquals("0", string(SharedPreferencesUtils.SWIPE_RIGHT_ACTION))
    }

    @Test
    fun `a restored backup from before the swap has its keys turned over`() {
        // The device is by side already; the restore merged in a backup that is not.
        SwipeActionSideMigration.migrate(preferences)
        val restored = mapOf(
            scoped("alice", SharedPreferencesUtils.SWIPE_LEFT_ACTION) to "0",
            scoped("alice", SharedPreferencesUtils.SWIPE_RIGHT_ACTION) to "1",
            SharedPreferencesUtils.THEME_KEY to "2",
        )
        preferences.edit()
            .putString(scoped("alice", SharedPreferencesUtils.SWIPE_LEFT_ACTION), "0")
            .putString(scoped("alice", SharedPreferencesUtils.SWIPE_RIGHT_ACTION), "1")
            .putString(SharedPreferencesUtils.THEME_KEY, "2")
            .commit()

        SwipeActionSideMigration.flipRestoredKeys(preferences, restored)

        assertEquals("1", string(scoped("alice", SharedPreferencesUtils.SWIPE_LEFT_ACTION)))
        assertEquals("0", string(scoped("alice", SharedPreferencesUtils.SWIPE_RIGHT_ACTION)))
        assertTrue(SwipeActionSideMigration.isDone(preferences))
    }

    @Test
    fun `only the keys the backup brought in are turned over`() {
        // bob was not in the backup. His keys are the device's own, already by side, and a restore
        // that swapped them would undo the upgrade for him.
        SwipeActionSideMigration.migrate(preferences)
        preferences.edit()
            .putString(scoped("bob", SharedPreferencesUtils.SWIPE_LEFT_ACTION), "2")
            .putString(scoped("bob", SharedPreferencesUtils.SWIPE_RIGHT_ACTION), "3")
            .putString(scoped("alice", SharedPreferencesUtils.SWIPE_LEFT_ACTION), "0")
            .commit()

        SwipeActionSideMigration.flipRestoredKeys(
            preferences, mapOf(scoped("alice", SharedPreferencesUtils.SWIPE_LEFT_ACTION) to "0"))

        assertEquals("2", string(scoped("bob", SharedPreferencesUtils.SWIPE_LEFT_ACTION)))
        assertEquals("3", string(scoped("bob", SharedPreferencesUtils.SWIPE_RIGHT_ACTION)))
        assertNull(string(scoped("alice", SharedPreferencesUtils.SWIPE_LEFT_ACTION)))
        assertEquals("0", string(scoped("alice", SharedPreferencesUtils.SWIPE_RIGHT_ACTION)))
    }

    @Test
    fun `a one-sided backup does not hand the other side this device's old value`() {
        // The backup configured one action; this device had its own on the opposite side. The
        // backup's action is a right-side one by the new naming, so it lands on the right, and
        // the left goes back to its default rather than inheriting the value being vacated.
        SwipeActionSideMigration.migrate(preferences)
        preferences.edit()
            .putString(scoped("alice", SharedPreferencesUtils.SWIPE_RIGHT_ACTION), "5")
            .commit()
        // What the restore itself wrote, before the flip gets to look at it.
        preferences.edit()
            .putString(scoped("alice", SharedPreferencesUtils.SWIPE_LEFT_ACTION), "0")
            .commit()

        SwipeActionSideMigration.flipRestoredKeys(
            preferences, mapOf(scoped("alice", SharedPreferencesUtils.SWIPE_LEFT_ACTION) to "0"))

        assertNull("the backup named no left-side action",
            string(scoped("alice", SharedPreferencesUtils.SWIPE_LEFT_ACTION)))
        assertEquals("the backup's action, on the side it was really on",
            "0", string(scoped("alice", SharedPreferencesUtils.SWIPE_RIGHT_ACTION)))
    }

    @Test
    fun `a backup taken by side is left as restored`() {
        SwipeActionSideMigration.migrate(preferences)
        preferences.edit()
            .putString(scoped("alice", SharedPreferencesUtils.SWIPE_LEFT_ACTION), "0")
            .putString(scoped("alice", SharedPreferencesUtils.SWIPE_RIGHT_ACTION), "1")
            .commit()

        SwipeActionSideMigration.flipRestoredKeys(
            preferences,
            mapOf(
                scoped("alice", SharedPreferencesUtils.SWIPE_LEFT_ACTION) to "0",
                scoped("alice", SharedPreferencesUtils.SWIPE_RIGHT_ACTION) to "1",
                SharedPreferencesUtils.SWIPE_ACTION_SIDES_MIGRATED to true,
            ),
        )

        assertEquals("0", string(scoped("alice", SharedPreferencesUtils.SWIPE_LEFT_ACTION)))
        assertEquals("1", string(scoped("alice", SharedPreferencesUtils.SWIPE_RIGHT_ACTION)))
    }

    @Test
    fun `a backup with no swipe keys, or none the restore could read, changes nothing`() {
        SwipeActionSideMigration.migrate(preferences)
        preferences.edit()
            .putString(scoped("alice", SharedPreferencesUtils.SWIPE_LEFT_ACTION), "0")
            .commit()

        SwipeActionSideMigration.flipRestoredKeys(preferences, mapOf(SharedPreferencesUtils.THEME_KEY to "2"))
        SwipeActionSideMigration.flipRestoredKeys(preferences, null)

        assertEquals("0", string(scoped("alice", SharedPreferencesUtils.SWIPE_LEFT_ACTION)))
        assertFalse(preferences.contains(scoped("alice", SharedPreferencesUtils.SWIPE_RIGHT_ACTION)))
    }

    private fun database(vararg accountNames: String): RedditDataRoomDatabase {
        val accountDao = mock<AccountDao>()
        whenever(accountDao.allAccounts).thenReturn(
            accountNames.map { Account(it, null, null, null, null, null, 0, false, false) })
        return mock<RedditDataRoomDatabase>().also { whenever(it.accountDao()).thenReturn(accountDao) }
    }
}
