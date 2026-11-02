package ml.docilealligator.infinityforreddit.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import ml.docilealligator.infinityforreddit.TestInfinity
import ml.docilealligator.infinityforreddit.account.AccountScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Use Old Load More Comments Method was a per-account switch in the default preferences; it became
 * Use /api/info the Old Way, one setting for every account on the API Keys screen. Any account that
 * had it on turns the global setting on.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestInfinity::class)
class OldApiInfoMigrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var defaults: SharedPreferences
    private lateinit var apiKeys: SharedPreferences

    @Before
    fun setUp() {
        defaults = context.getSharedPreferences("old_api_info_migration_test_defaults", Context.MODE_PRIVATE)
        apiKeys = context.getSharedPreferences("old_api_info_migration_test_api_keys", Context.MODE_PRIVATE)
        defaults.edit().clear().commit()
        apiKeys.edit().clear().commit()
    }

    private fun switchFor(accountName: String?, on: Boolean) {
        defaults.edit().putBoolean(AccountScope.key(accountName, SharedPreferencesUtils.USE_LEGACY_MORE_CHILDREN), on)
            .commit()
    }

    private fun migrate() = OldApiInfoMigration.migrate(defaults, apiKeys)

    private fun global() = apiKeys.getBoolean(SharedPreferencesUtils.USE_OLD_API_INFO_PREF_KEY, false)

    private fun switchesLeft() = defaults.all.keys.filter {
        (AccountScope.baseOf(it) ?: it) == SharedPreferencesUtils.USE_LEGACY_MORE_CHILDREN
    }

    @Test
    fun `one account with it on turns it on for every account`() {
        switchFor("alice", false)
        switchFor("bob", true)
        switchFor(null, false)

        migrate()

        assertTrue(global())
        assertEquals(emptyList<String>(), switchesLeft())
    }

    @Test
    fun `the copy from before accounts had their own counts too`() {
        defaults.edit().putBoolean(SharedPreferencesUtils.USE_LEGACY_MORE_CHILDREN, true).commit()

        migrate()

        assertTrue(global())
        assertEquals(emptyList<String>(), switchesLeft())
    }

    @Test
    fun `switches that are all off leave it off`() {
        switchFor("alice", false)
        switchFor(null, false)

        migrate()

        assertFalse(global())
        assertFalse(apiKeys.contains(SharedPreferencesUtils.USE_OLD_API_INFO_PREF_KEY))
        assertEquals(emptyList<String>(), switchesLeft())
    }

    @Test
    fun `a switch that is off never turns off a choice already made`() {
        // A restore of a backup from before the move brings its switches back.
        apiKeys.edit().putBoolean(SharedPreferencesUtils.USE_OLD_API_INFO_PREF_KEY, true).commit()
        switchFor("alice", false)

        migrate()

        assertTrue(global())
        assertEquals(emptyList<String>(), switchesLeft())
    }

    @Test
    fun `other settings are left alone`() {
        defaults.edit()
            .putBoolean(AccountScope.key("alice", SharedPreferencesUtils.FULLY_COLLAPSE_COMMENT), true)
            .putBoolean(SharedPreferencesUtils.FULLY_COLLAPSE_COMMENT, true)
            .commit()
        switchFor("alice", true)

        migrate()

        assertEquals(
            setOf(AccountScope.key("alice", SharedPreferencesUtils.FULLY_COLLAPSE_COMMENT),
                SharedPreferencesUtils.FULLY_COLLAPSE_COMMENT),
            defaults.all.keys
        )
    }

    @Test
    fun `with no switches left it writes nothing`() {
        migrate()

        assertTrue(defaults.all.isEmpty())
        assertTrue(apiKeys.all.isEmpty())
    }
}
