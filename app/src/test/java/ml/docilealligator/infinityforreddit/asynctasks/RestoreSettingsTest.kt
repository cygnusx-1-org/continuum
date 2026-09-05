package ml.docilealligator.infinityforreddit.asynctasks

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.ObjectOutputStream
import java.io.Serializable
import java.util.concurrent.Executor
import ml.docilealligator.infinityforreddit.RedditDataRoomDatabase
import ml.docilealligator.infinityforreddit.account.Account
import ml.docilealligator.infinityforreddit.account.AccountScope
import ml.docilealligator.infinityforreddit.utils.SharedPreferencesUtils
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.EncryptionMethod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * What comes back out of a backup.
 *
 * A backup is one file for the whole app, every account included, so the round trip has to be exact:
 * a key belonging to an account it names goes back to that account, whoever happens to be signed in
 * when the restore runs. Reading or writing these files through the injected preferences would break
 * that — the façade shows one account and renames its keys — so the two tasks open the files
 * themselves, and these tests are what says so.
 *
 * The restore runs inline on a direct executor. Its listener callbacks and the app restart are
 * posted to the main looper, which is deliberately never drained: the assertions are about what
 * landed in the preference files, and draining would reach `Process.killProcess`.
 */
@RunWith(RobolectricTestRunner::class)
class RestoreSettingsTest {

    private companion object {
        const val PASSWORD = "restore-test"
        const val VERSION_DIR = "8.3.0.2"
    }

    private val context: Context = ApplicationProvider.getApplicationContext()

    private lateinit var defaultPreferences: SharedPreferences
    private lateinit var sortType: SharedPreferences
    private lateinit var nsfwAndSpoiler: SharedPreferences
    private lateinit var internal: SharedPreferences

    @Before
    fun setUp() {
        defaultPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        sortType = file(SharedPreferencesUtils.SORT_TYPE_SHARED_PREFERENCES_FILE)
        nsfwAndSpoiler = file(SharedPreferencesUtils.NSFW_AND_SPOILER_SHARED_PREFERENCES_FILE)
        internal = file(SharedPreferencesUtils.INTERNAL_SHARED_PREFERENCES_FILE)

        for (preferences in listOf(defaultPreferences, sortType, nsfwAndSpoiler, internal)) {
            preferences.edit().clear().commit()
        }
        internal.edit()
            .putBoolean(SharedPreferencesUtils.ACCOUNT_SCOPE_MIGRATED, true)
            .putBoolean(SharedPreferencesUtils.ACCOUNT_SCOPE_SEEDED, true)
            .commit()
    }

    private fun file(name: String): SharedPreferences =
        context.getSharedPreferences(name, Context.MODE_PRIVATE)

    private fun key(accountName: String?, base: String) = AccountScope.key(accountName, base)

    /** Builds the zip a backup would have written, one entry per named preference file. */
    private fun restore(vararg backedUpFiles: Pair<String, Map<String, Serializable>>) =
        restore(mock<RedditDataRoomDatabase>(), emptyList(), backedUpFiles.toList())

    /**
     * The same, against a real database and with entries in the backup's `database` directory.
     *
     * The preference-only form hands the restore a mock database, so nothing it does to a table is
     * observable; the rows are the whole point of these two.
     */
    private fun restore(
        database: RedditDataRoomDatabase,
        databaseJson: List<Pair<String, String>>,
        backedUpFiles: List<Pair<String, Map<String, Serializable>>> = emptyList(),
    ) {
        val source = File(context.cacheDir, "backup-source")
        source.deleteRecursively()
        val versionDir = File(source, VERSION_DIR)
        versionDir.mkdirs()
        for ((name, contents) in backedUpFiles) {
            ObjectOutputStream(FileOutputStream(File(versionDir, name + ".txt"))).use {
                it.writeObject(HashMap(contents))
            }
        }
        if (databaseJson.isNotEmpty()) {
            val databaseDir = File(versionDir, "database")
            databaseDir.mkdirs()
            for ((name, json) in databaseJson) {
                File(databaseDir, name).writeText(json)
            }
        }

        val zip = File(context.cacheDir, "backup.zip")
        zip.delete()
        ZipFile(zip, PASSWORD.toCharArray()).use { archive ->
            archive.addFolder(versionDir, ZipParameters().apply {
                isEncryptFiles = true
                encryptionMethod = EncryptionMethod.AES
            })
        }

        val uri = Uri.parse("content://ml.docilealligator.infinityforreddit.test/backup.zip")
        shadowOf(context.contentResolver).registerInputStream(uri, FileInputStream(zip))

        RestoreSettings.restoreSettings(
            context,
            Executor { it.run() },
            Handler(Looper.getMainLooper()),
            context.contentResolver,
            uri,
            PASSWORD,
            database,
            file("current_account_test"),
            file("light_theme_test"),
            file("dark_theme_test"),
            file("amoled_theme_test"),
            file("scrolled_position_test"),
            file("main_page_tabs_test"),
            file("proxy_test"),
            nsfwAndSpoiler,
            file("post_history_test"),
            file("recently_visited_test"),
            object : RestoreSettings.RestoreSettingsListener {
                override fun success() = Unit
                override fun failed(errorMessage: String) = Unit
                override fun failedWithWrongPassword(errorMessage: String) = Unit
            },
        )
    }

    @Test
    fun `every account in the backup comes back under its own name`() {
        restore(
            SharedPreferencesUtils.DEFAULT_PREFERENCES_FILE to mapOf(
                key("alice", SharedPreferencesUtils.THEME_KEY) to "2",
                key("bob", SharedPreferencesUtils.THEME_KEY) to "5",
                key(null, SharedPreferencesUtils.THEME_KEY) to "1",
                SharedPreferencesUtils.APP_LOCK to true,
            )
        )

        assertEquals("2", defaultPreferences.getString(key("alice", SharedPreferencesUtils.THEME_KEY), null))
        assertEquals("5", defaultPreferences.getString(key("bob", SharedPreferencesUtils.THEME_KEY), null))
        assertEquals("1", defaultPreferences.getString(key(null, SharedPreferencesUtils.THEME_KEY), null))
        assertTrue("a global setting is still global",
            defaultPreferences.getBoolean(SharedPreferencesUtils.APP_LOCK, false))
    }

    @Test
    fun `an older backup's global keys land global, for the migration to share out`() {
        restore(
            SharedPreferencesUtils.DEFAULT_PREFERENCES_FILE to mapOf(
                SharedPreferencesUtils.THEME_KEY to "2"
            )
        )

        // Written through the injected preferences this would have become "<signed in>.theme":
        // one account served, the rest left on defaults, and nothing under the bare key for the
        // migration re-run to seed them from.
        assertEquals("2", defaultPreferences.getString(SharedPreferencesUtils.THEME_KEY, null))
        for (stored in defaultPreferences.all.keys) {
            assertFalse("scoped on the way in: " + stored,
                stored.endsWith("." + SharedPreferencesUtils.THEME_KEY))
        }
    }

    @Test
    fun `each backed up file goes to the preferences it was taken from`() {
        restore(
            SharedPreferencesUtils.DEFAULT_PREFERENCES_FILE to mapOf(
                key("alice", SharedPreferencesUtils.THEME_KEY) to "2"
            ),
            SharedPreferencesUtils.SORT_TYPE_SHARED_PREFERENCES_FILE to mapOf(
                key("alice", "sort_type_best_post") to "top"
            ),
            SharedPreferencesUtils.NSFW_AND_SPOILER_SHARED_PREFERENCES_FILE to mapOf(
                key("alice", SharedPreferencesUtils.NSFW_BASE) to true
            ),
        )

        assertEquals("top", sortType.getString(key("alice", "sort_type_best_post"), null))
        assertTrue(nsfwAndSpoiler.getBoolean(key("alice", SharedPreferencesUtils.NSFW_BASE), false))
        assertNull("the sort type belongs to its own file",
            defaultPreferences.getString(key("alice", "sort_type_best_post"), null))
        assertNull("and the theme to its own",
            sortType.getString(key("alice", SharedPreferencesUtils.THEME_KEY), null))
    }

    @Test
    fun `a file merely named after a known one is not poured into it`() {
        restore(
            SharedPreferencesUtils.SORT_TYPE_SHARED_PREFERENCES_FILE to mapOf(
                key("alice", "sort_type_best_post") to "top"
            ),
            SharedPreferencesUtils.SORT_TYPE_SHARED_PREFERENCES_FILE + "_something_else" to mapOf(
                key("alice", "sort_type_best_post") to "controversial"
            ),
        )

        assertEquals("matched by prefix, the second file would have overwritten the first",
            "top", sortType.getString(key("alice", "sort_type_best_post"), null))
    }

    /**
     * A real database, built the way the app builds one so that the anonymous row it depends on is
     * there.
     */
    private fun realDatabase(): RedditDataRoomDatabase =
        RedditDataRoomDatabase.createInMemoryForTest(context)

    private fun anonymousSubredditsJson(username: String) =
        """[{"id":"t5_1","name":"pics","iconUrl":"","username":"$username","favorite":false}]"""

    /**
     * The case the rename created. Rows in a backup carry the account name they were saved under,
     * so one taken before the rename says "-" however new the database restoring it is -- the Room
     * migration ran on upgrade, long before these rows existed. Left alone they are an account
     * named "-" holding the data anonymous browsing should have got back.
     */
    @Test
    fun `anonymous rows from a backup taken before the rename come back under the new name`() {
        val database = realDatabase()
        try {
            restore(database, listOf(
                "anonymous_subscribed_subreddits.json" to anonymousSubredditsJson("-")))

            val dao = database.subscribedSubredditDao()
            assertEquals(1, dao.getAllSubscribedSubredditsList(Account.ANONYMOUS_ACCOUNT).size)
            assertTrue("nothing may be left under the old name",
                dao.getAllSubscribedSubredditsList("-").isEmpty())
        } finally {
            database.close()
        }
    }

    @Test
    fun `anonymous rows from a backup taken after the rename are left where they are`() {
        val database = realDatabase()
        try {
            restore(database, listOf(
                "anonymous_subscribed_subreddits.json" to
                    anonymousSubredditsJson(Account.ANONYMOUS_ACCOUNT)))

            assertEquals(1, database.subscribedSubredditDao()
                .getAllSubscribedSubredditsList(Account.ANONYMOUS_ACCOUNT).size)
        } finally {
            database.close()
        }
    }

    @Test
    fun `restoring a backup from before the key scheme asks for the migration again`() {
        restore(
            SharedPreferencesUtils.DEFAULT_PREFERENCES_FILE to mapOf(
                SharedPreferencesUtils.THEME_KEY to "2"
            )
        )

        assertFalse(internal.getBoolean(SharedPreferencesUtils.ACCOUNT_SCOPE_MIGRATED, false))
        assertFalse(internal.getBoolean(SharedPreferencesUtils.ACCOUNT_SCOPE_SEEDED, false))
    }

    @Test
    fun `restoring a backup that already carries scoped keys leaves the migration alone`() {
        restore(
            SharedPreferencesUtils.DEFAULT_PREFERENCES_FILE to mapOf(
                key("alice", SharedPreferencesUtils.THEME_KEY) to "2"
            )
        )

        assertTrue(internal.getBoolean(SharedPreferencesUtils.ACCOUNT_SCOPE_MIGRATED, false))
        assertTrue(internal.getBoolean(SharedPreferencesUtils.ACCOUNT_SCOPE_SEEDED, false))
    }
}
