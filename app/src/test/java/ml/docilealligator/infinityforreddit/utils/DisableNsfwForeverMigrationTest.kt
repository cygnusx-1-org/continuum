package ml.docilealligator.infinityforreddit.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import ml.docilealligator.infinityforreddit.RedditDataRoomDatabase
import ml.docilealligator.infinityforreddit.account.Account
import ml.docilealligator.infinityforreddit.account.AccountDao
import ml.docilealligator.infinityforreddit.account.AccountScope
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner

/**
 * Retiring "Forever Disable NSFW".
 *
 * The deleted feature hid NSFW content without ever clearing the per-account switch underneath it,
 * so removing it would hand that content back to exactly the people who had most deliberately
 * turned it off. This runs once and turns their switches off instead — and it only gets one go,
 * because the orphaned key is both the record of who was affected and the thing that stops it
 * running twice.
 */
@RunWith(RobolectricTestRunner::class)
class DisableNsfwForeverMigrationTest {

    /**
     * The key the deleted feature wrote. Deliberately private in the class under test — nothing may
     * read it again — so the test names it too rather than reaching for a constant.
     */
    private val disableNsfwForever = "disable_nsfw_forever"

    private val context: Context = ApplicationProvider.getApplicationContext()

    private lateinit var defaultPreferences: SharedPreferences
    private lateinit var nsfwAndSpoiler: SharedPreferences

    @Before
    fun setUp() {
        defaultPreferences = context.getSharedPreferences("nsfw_forever_default", Context.MODE_PRIVATE)
        nsfwAndSpoiler = context.getSharedPreferences("nsfw_forever_switches", Context.MODE_PRIVATE)
        for (preferences in listOf(defaultPreferences, nsfwAndSpoiler)) {
            preferences.edit().clear().commit()
        }
    }

    private fun run(vararg accountNames: String) {
        val accountDao = mock<AccountDao>()
        whenever(accountDao.allAccounts).thenReturn(
            accountNames.map { Account(it, null, null, null, null, null, 0, false, false) })
        val database = mock<RedditDataRoomDatabase>()
        whenever(database.accountDao()).thenReturn(accountDao)

        DisableNsfwForeverMigration.turnOffNsfwForAffectedAccounts(
            defaultPreferences, nsfwAndSpoiler, database)
    }

    private fun nsfwOf(accountName: String?) = nsfwAndSpoiler.getBoolean(
        AccountScope.key(accountName, SharedPreferencesUtils.NSFW_BASE), true)

    @Test
    fun `an install that never enabled it is left completely alone`() {
        nsfwAndSpoiler.edit()
            .putBoolean(AccountScope.key("alice", SharedPreferencesUtils.NSFW_BASE), true)
            .commit()

        run("alice")

        assertTrue("the common path is a map lookup and no write", nsfwOf("alice"))
    }

    @Test
    fun `a key written once and left off only takes the key away`() {
        defaultPreferences.edit().putBoolean(disableNsfwForever, false).commit()
        nsfwAndSpoiler.edit()
            .putBoolean(AccountScope.key("alice", SharedPreferencesUtils.NSFW_BASE), true)
            .commit()

        run("alice")

        assertTrue("nothing was being overridden, so there is no switch to correct", nsfwOf("alice"))
        assertFalse("no install should carry the deleted feature's key around",
            defaultPreferences.contains(disableNsfwForever))
    }

    @Test
    fun `every account and anonymous have NSFW turned off`() {
        defaultPreferences.edit().putBoolean(disableNsfwForever, true).commit()
        nsfwAndSpoiler.edit()
            .putBoolean(AccountScope.key("alice", SharedPreferencesUtils.NSFW_BASE), true)
            .putBoolean(AccountScope.key("bob", SharedPreferencesUtils.NSFW_BASE), true)
            .putBoolean(AccountScope.key(null, SharedPreferencesUtils.NSFW_BASE), true)
            .commit()

        run("alice", "bob")

        assertFalse("alice", nsfwOf("alice"))
        assertFalse("bob", nsfwOf("bob"))
        assertFalse("anonymous", nsfwOf(null))
    }

    @Test
    fun `an account whose switch was never written still gets it turned off`() {
        // The switch reads false by default anyway, but the row has to be written: the override is
        // going away, and a default is not a decision anyone made.
        defaultPreferences.edit().putBoolean(disableNsfwForever, true).commit()

        run("alice")

        assertTrue(nsfwAndSpoiler.contains(
            AccountScope.key("alice", SharedPreferencesUtils.NSFW_BASE)))
        assertFalse(nsfwOf("alice"))
    }

    @Test
    fun `the run is not repeatable, because the key it read is gone`() {
        defaultPreferences.edit().putBoolean(disableNsfwForever, true).commit()

        run("alice")
        // Someone turns NSFW back on afterwards; a second run must not undo that.
        nsfwAndSpoiler.edit()
            .putBoolean(AccountScope.key("alice", SharedPreferencesUtils.NSFW_BASE), true)
            .commit()
        run("alice")

        assertFalse(defaultPreferences.contains(disableNsfwForever))
        assertTrue("turning it back on is the user's to do once the override is gone",
            nsfwOf("alice"))
    }

    @Test
    fun `the other blurring switches are not touched`() {
        defaultPreferences.edit().putBoolean(disableNsfwForever, true).commit()
        nsfwAndSpoiler.edit()
            .putBoolean(AccountScope.key("alice", SharedPreferencesUtils.BLUR_NSFW_BASE), false)
            .commit()

        run("alice")

        assertFalse("only the NSFW switch was ever being overridden",
            nsfwAndSpoiler.getBoolean(
                AccountScope.key("alice", SharedPreferencesUtils.BLUR_NSFW_BASE), true))
    }
}
