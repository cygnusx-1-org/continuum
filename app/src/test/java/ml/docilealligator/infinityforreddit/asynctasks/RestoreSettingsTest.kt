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
import ml.docilealligator.infinityforreddit.customtheme.CustomThemeWrapper
import ml.docilealligator.infinityforreddit.resume.ResumeState
import ml.docilealligator.infinityforreddit.settings.MainPageTabsUtils
import ml.docilealligator.infinityforreddit.utils.SharedPreferencesUtils
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.EncryptionMethod
import org.junit.After
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
        const val CURRENT_ACCOUNT_FILE = "current_account_test"
    }

    private val context: Context = ApplicationProvider.getApplicationContext()

    private lateinit var defaultPreferences: SharedPreferences
    private lateinit var sortType: SharedPreferences
    private lateinit var nsfwAndSpoiler: SharedPreferences
    private lateinit var internal: SharedPreferences
    private lateinit var currentAccount: SharedPreferences
    private lateinit var bottomAppBar: SharedPreferences
    private lateinit var mainPageTabs: SharedPreferences

    @Before
    fun setUp() {
        defaultPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        sortType = file(SharedPreferencesUtils.SORT_TYPE_SHARED_PREFERENCES_FILE)
        nsfwAndSpoiler = file(SharedPreferencesUtils.NSFW_AND_SPOILER_SHARED_PREFERENCES_FILE)
        internal = file(SharedPreferencesUtils.INTERNAL_SHARED_PREFERENCES_FILE)
        currentAccount = file(CURRENT_ACCOUNT_FILE)
        bottomAppBar = file(SharedPreferencesUtils.BOTTOM_APP_BAR_SHARED_PREFERENCES_FILE)
        // The name restore() hands the restore for the main page tabs, not the app's own file.
        mainPageTabs = file("main_page_tabs_test")

        for (preferences in listOf(defaultPreferences, sortType, nsfwAndSpoiler, internal, currentAccount,
            bottomAppBar, mainPageTabs)) {
            preferences.edit().clear().commit()
        }
        internal.edit()
            .putBoolean(SharedPreferencesUtils.ACCOUNT_SCOPE_MIGRATED, true)
            .putBoolean(SharedPreferencesUtils.ACCOUNT_SCOPE_SEEDED, true)
            .commit()
    }

    /** The restore forgets cached feeds, which is ResumeState's in-memory state too. */
    @After
    fun tearDown() {
        ResumeState.resetForTests()
        File(context.filesDir, "resume_state.json").delete()
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
            currentAccount,
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
        // migration re-run to seed them from. Anonymous is the one account a restore hands it to.
        assertEquals("2", defaultPreferences.getString(SharedPreferencesUtils.THEME_KEY, null))
        for (stored in defaultPreferences.all.keys) {
            assertFalse("scoped on the way in: " + stored,
                stored != key(null, SharedPreferencesUtils.THEME_KEY)
                    && stored.endsWith("." + SharedPreferencesUtils.THEME_KEY))
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

    /** An accounts.json as a backup writes it, none of them marked current. */
    private fun accountsJson(vararg names: String) = names.joinToString(",", "[", "]") {
        """{"accountName":"$it","karma":0,"isCurrentUser":false,"isMod":false}"""
    }

    private fun usernamesIn(database: RedditDataRoomDatabase, table: String): List<String> {
        val names = mutableListOf<String>()
        database.openHelper.writableDatabase.query("SELECT username FROM $table ORDER BY username").use {
            while (it.moveToNext()) names.add(it.getString(0))
        }
        return names
    }

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

    /**
     * A backup taken before filters had owners shares each one out among the accounts, and the
     * placeholder a restore parks old-spelling rows on used to be counted among them. Every filter
     * then had a copy under "-" beside the one under ".anonymous", and folding the two together
     * crashed the restore.
     */
    @Test
    fun `filters from a backup taken before they had owners are shared out without the placeholder`() {
        val database = realDatabase()
        try {
            restore(database, listOf(
                "accounts.json" to accountsJson("alice"),
                "post_filters.json" to """[{"name":"NSFW"}]""",
                "post_filter_usage.json" to """[{"name":"NSFW","usage":2,"nameOfUsage":"pics"}]""",
            ))

            assertEquals(listOf(Account.ANONYMOUS_ACCOUNT, "alice"), usernamesIn(database, "post_filter"))
            assertEquals(listOf(Account.ANONYMOUS_ACCOUNT, "alice"), usernamesIn(database, "post_filter_usage"))
            assertEquals(listOf(Account.ANONYMOUS_ACCOUNT, "alice"), usernamesIn(database, "accounts"))
        } finally {
            database.close()
        }
    }

    /**
     * Infinity's filters have no owner and its backup no accounts. Shared out among the accounts
     * already on the device, they replaced each one's filter of the same name.
     */
    @Test
    fun `filters from a backup that brought no accounts go to anonymous alone`() {
        val database = realDatabase()
        try {
            database.openHelper.writableDatabase.execSQL(
                "INSERT INTO accounts (username, karma, is_current_user, is_mod) VALUES ('alice', 0, 1, 0)")

            restore(database, listOf(
                "post_filters.json" to """[{"name":"NSFW"}]""",
                "post_filter_usage.json" to """[{"name":"NSFW","usage":2,"nameOfUsage":"pics"}]""",
                "comment_filters.json" to """[{"name":"Spoilers"}]""",
            ))

            assertEquals(listOf(Account.ANONYMOUS_ACCOUNT), usernamesIn(database, "post_filter"))
            assertEquals(listOf(Account.ANONYMOUS_ACCOUNT), usernamesIn(database, "post_filter_usage"))
            assertEquals(listOf(Account.ANONYMOUS_ACCOUNT), usernamesIn(database, "comment_filter"))
        } finally {
            database.close()
        }
    }

    /**
     * A backup taken from an install that restore had crashed on: the placeholder is among its
     * accounts, and a filter is in it twice, once under each name. The copy under the new name is
     * the one the user was looking at when they took it.
     */
    @Test
    fun `a backup taken after a restore had crashed comes back without the leftovers`() {
        val database = realDatabase()
        try {
            restore(database, listOf(
                "accounts.json" to accountsJson("-", "alice"),
                "post_filters.json" to """[
                    {"name":"NSFW","username":"-","maxVote":1},
                    {"name":"NSFW","username":".anonymous","maxVote":2},
                    {"name":"NSFW","username":"alice","maxVote":1}]""",
            ))

            assertEquals(listOf(Account.ANONYMOUS_ACCOUNT, "alice"), usernamesIn(database, "post_filter"))
            database.openHelper.writableDatabase
                .query("SELECT max_vote FROM post_filter WHERE username = ?", arrayOf(Account.ANONYMOUS_ACCOUNT))
                .use {
                    it.moveToNext()
                    assertEquals(2, it.getInt(0))
                }
            assertEquals(listOf(Account.ANONYMOUS_ACCOUNT, "alice"), usernamesIn(database, "accounts"))
            assertEquals("the placeholder is not an account to make current",
                "alice", currentAccount.getString(SharedPreferencesUtils.ACCOUNT_NAME, null))
        } finally {
            database.close()
        }
    }

    /**
     * A restore that fails part of the way through takes back what it had written. It used to keep
     * it -- the local accounts already deleted, the placeholder and whatever was on it left for the
     * next backup to carry -- and the exception, escaping the executor, crashed the app.
     */
    @Test
    fun `a restore that fails part of the way through leaves the database as it was`() {
        val database = realDatabase()
        try {
            database.openHelper.writableDatabase.execSQL(
                "INSERT INTO accounts (username, karma, is_current_user, is_mod) VALUES ('alice', 0, 1, 0)")

            restore(database, listOf(
                "accounts.json" to accountsJson("bob"),
                "post_filters.json" to """[{"name":"NSFW"}]""",
                // Read after both of the above. An object where Gson expects a list throws.
                "read_posts.json" to """{"not":"a list"}""",
            ))

            assertEquals(listOf(Account.ANONYMOUS_ACCOUNT, "alice"), usernamesIn(database, "accounts"))
            assertTrue(usernamesIn(database, "post_filter").isEmpty())
            assertNull("nothing may name the account the rollback took back out",
                currentAccount.getString(SharedPreferencesUtils.ACCOUNT_NAME, null))
        } finally {
            database.close()
        }
    }

    @Test
    fun `restoring a backup from before the key scheme asks for the migration again`() {
        internal.edit().putBoolean(SharedPreferencesUtils.BOTTOM_APP_BAR_SCOPE_MIGRATED, true).commit()
        val database = realDatabase()
        try {
            restore(
                database,
                listOf("accounts.json" to accountsJson("alice")),
                listOf(SharedPreferencesUtils.DEFAULT_PREFERENCES_FILE to mapOf(
                    SharedPreferencesUtils.THEME_KEY to "2"
                )),
            )

            assertFalse(internal.getBoolean(SharedPreferencesUtils.ACCOUNT_SCOPE_MIGRATED, false))
            assertFalse(internal.getBoolean(SharedPreferencesUtils.ACCOUNT_SCOPE_SEEDED, false))
            assertFalse("the backup's bottom app bar is under its old keys too",
                internal.getBoolean(SharedPreferencesUtils.BOTTOM_APP_BAR_SCOPE_MIGRATED, true))
        } finally {
            database.close()
        }
    }

    // --- an Infinity backup: no accounts, the old spellings, the old numbering --------------

    /**
     * Infinity has one set of settings, and an account on this device with any history owns a
     * scoped copy of most of them -- which the migration, filling only what is missing, kept.
     */
    @Test
    fun `an older backup's settings replace anonymous's own`() {
        defaultPreferences.edit()
            .putString(key(null, SharedPreferencesUtils.THEME_KEY), "0")
            .putString(key("alice", SharedPreferencesUtils.THEME_KEY), "1")
            .commit()

        restore(
            SharedPreferencesUtils.DEFAULT_PREFERENCES_FILE to mapOf(
                SharedPreferencesUtils.THEME_KEY to "2"
            )
        )

        assertEquals("2", defaultPreferences.getString(key(null, SharedPreferencesUtils.THEME_KEY), null))
        assertEquals("a signed-in account the backup did not bring keeps its own",
            "1", defaultPreferences.getString(key("alice", SharedPreferencesUtils.THEME_KEY), null))
        assertTrue("nor is the migration asked to share the backup out to it",
            internal.getBoolean(SharedPreferencesUtils.ACCOUNT_SCOPE_MIGRATED, false))
    }

    /**
     * What anonymous was last looking at was fetched from the subscriptions the backup has just
     * replaced; reopened, it showed their posts until a refresh.
     */
    @Test
    fun `a backup that brought no accounts forgets where anonymous left off`() {
        val snapshot = File(context.filesDir, "resume_state.json")
        snapshot.writeText("""{"version":1,"account":"${Account.ANONYMOUS_ACCOUNT}","stack":[]}""")

        restore(
            SharedPreferencesUtils.DEFAULT_PREFERENCES_FILE to mapOf(
                SharedPreferencesUtils.THEME_KEY to "2"
            )
        )

        assertFalse(snapshot.exists())
    }

    @Test
    fun `a backup that brought no accounts leaves a signed-in account's place alone`() {
        val snapshot = File(context.filesDir, "resume_state.json")
        snapshot.writeText("""{"version":1,"account":"alice","stack":[]}""")

        restore(
            SharedPreferencesUtils.DEFAULT_PREFERENCES_FILE to mapOf(
                SharedPreferencesUtils.THEME_KEY to "2"
            )
        )

        assertTrue(snapshot.exists())
    }

    @Test
    fun `a backup that brought accounts forgets every account's place`() {
        val snapshot = File(context.filesDir, "resume_state.json")
        snapshot.writeText("""{"version":1,"account":"alice","stack":[]}""")
        val database = realDatabase()
        try {
            restore(database, listOf("accounts.json" to accountsJson("alice")))

            assertFalse(snapshot.exists())
        } finally {
            database.close()
        }
    }

    @Test
    fun `a swipe action the backup set on one side reaches anonymous turned over`() {
        defaultPreferences.edit()
            .putString(key(null, SharedPreferencesUtils.SWIPE_LEFT_ACTION), "3")
            .putString(key(null, SharedPreferencesUtils.SWIPE_RIGHT_ACTION), "4")
            .commit()

        restore(
            SharedPreferencesUtils.DEFAULT_PREFERENCES_FILE to mapOf(
                // The old sense: the action a swipe to the left ran, which showed on the right.
                SharedPreferencesUtils.SWIPE_LEFT_ACTION to "1"
            )
        )

        assertEquals("1", defaultPreferences.getString(key(null, SharedPreferencesUtils.SWIPE_RIGHT_ACTION), null))
        assertFalse("the side the backup left unset goes back to its default",
            defaultPreferences.contains(key(null, SharedPreferencesUtils.SWIPE_LEFT_ACTION)))
    }

    @Test
    fun `an older backup's own spelling of a per-account file replaces anonymous's`() {
        nsfwAndSpoiler.edit().putBoolean(key(null, SharedPreferencesUtils.NSFW_BASE), false).commit()

        restore(
            SharedPreferencesUtils.DEFAULT_PREFERENCES_FILE to mapOf(
                SharedPreferencesUtils.THEME_KEY to "2"
            ),
            // Infinity's spelling of anonymous's NSFW switch: no prefix at all.
            SharedPreferencesUtils.NSFW_AND_SPOILER_SHARED_PREFERENCES_FILE to mapOf(
                SharedPreferencesUtils.NSFW_BASE to true
            ),
        )

        assertTrue(nsfwAndSpoiler.getBoolean(key(null, SharedPreferencesUtils.NSFW_BASE), false))
        assertFalse(nsfwAndSpoiler.contains(SharedPreferencesUtils.NSFW_BASE))
    }

    @Test
    fun `a backup with Disable NSFW Forever on leaves anonymous's NSFW off`() {
        restore(
            SharedPreferencesUtils.DEFAULT_PREFERENCES_FILE to mapOf(
                SharedPreferencesUtils.THEME_KEY to "2",
                "disable_nsfw_forever" to true,
            ),
            SharedPreferencesUtils.NSFW_AND_SPOILER_SHARED_PREFERENCES_FILE to mapOf(
                SharedPreferencesUtils.NSFW_BASE to true
            ),
        )

        assertFalse(nsfwAndSpoiler.getBoolean(key(null, SharedPreferencesUtils.NSFW_BASE), true))
        assertFalse("the deleted feature's key goes", defaultPreferences.contains("disable_nsfw_forever"))
    }

    @Test
    fun `an Infinity bottom app bar comes back in this app's numbering`() {
        bottomAppBar.edit()
            .putInt(key(null, SharedPreferencesUtils.MAIN_ACTIVITY_BOTTOM_APP_BAR_OPTION_2), 5)
            .commit()

        restore(
            SharedPreferencesUtils.DEFAULT_PREFERENCES_FILE to mapOf(
                SharedPreferencesUtils.THEME_KEY to "2"
            ),
            // Infinity's numbering: 11 is Random, 13 Filter Posts; its FAB's 10 is Go To Top.
            SharedPreferencesUtils.BOTTOM_APP_BAR_SHARED_PREFERENCES_FILE to mapOf(
                "-" + SharedPreferencesUtils.MAIN_ACTIVITY_BOTTOM_APP_BAR_OPTION_1 to 13,
                "-" + SharedPreferencesUtils.MAIN_ACTIVITY_BOTTOM_APP_BAR_OPTION_2 to 11,
                "-" + SharedPreferencesUtils.MAIN_ACTIVITY_BOTTOM_APP_BAR_OPTION_3 to 5,
                "-" + SharedPreferencesUtils.MAIN_ACTIVITY_BOTTOM_APP_BAR_FAB to 10,
            ),
        )

        assertEquals(SharedPreferencesUtils.MAIN_ACTIVITY_BOTTOM_APP_BAR_OPTION_FILTER_POSTS,
            bottomAppBar.getInt(key(null, SharedPreferencesUtils.MAIN_ACTIVITY_BOTTOM_APP_BAR_OPTION_1), -1))
        assertFalse("Random is gone, so the slot goes back to its default",
            bottomAppBar.contains(key(null, SharedPreferencesUtils.MAIN_ACTIVITY_BOTTOM_APP_BAR_OPTION_2)))
        assertEquals("an option before Random keeps its number",
            5, bottomAppBar.getInt(key(null, SharedPreferencesUtils.MAIN_ACTIVITY_BOTTOM_APP_BAR_OPTION_3), -1))
        assertEquals(SharedPreferencesUtils.MAIN_ACTIVITY_BOTTOM_APP_BAR_FAB_GO_TO_TOP,
            bottomAppBar.getInt(key(null, SharedPreferencesUtils.MAIN_ACTIVITY_BOTTOM_APP_BAR_FAB), -1))
        assertFalse(bottomAppBar.contains("-" + SharedPreferencesUtils.MAIN_ACTIVITY_BOTTOM_APP_BAR_OPTION_1))
    }

    @Test
    fun `an older backup's numbered tabs replace anonymous's tab list`() {
        mainPageTabs.edit()
            .putString(key(null, SharedPreferencesUtils.MAIN_PAGE_TABS_ORDER), "[]")
            .commit()

        restore(
            SharedPreferencesUtils.DEFAULT_PREFERENCES_FILE to mapOf(
                SharedPreferencesUtils.THEME_KEY to "2"
            ),
            SharedPreferencesUtils.MAIN_PAGE_TABS_SHARED_PREFERENCES_FILE to mapOf(
                SharedPreferencesUtils.MAIN_PAGE_TAB_COUNT to 2,
                SharedPreferencesUtils.MAIN_PAGE_TAB_1_POST_TYPE to SharedPreferencesUtils.MAIN_PAGE_TAB_POST_TYPE_SUBREDDIT,
                SharedPreferencesUtils.MAIN_PAGE_TAB_1_NAME to "pics",
            ),
        )

        val tabs = MainPageTabsUtils.load(mainPageTabs, Account.ANONYMOUS_ACCOUNT)
        assertEquals(2, tabs.size)
        assertEquals(SharedPreferencesUtils.MAIN_PAGE_TAB_POST_TYPE_SUBREDDIT, tabs[0].postType)
        assertEquals("pics", tabs[0].name)
    }

    /**
     * Infinity's backup has no accounts.json, but read posts and reminders for every account it
     * had. One such row failing its foreign key took the whole database restore back with it.
     */
    @Test
    fun `rows for an account the backup did not bring do not take the database restore back`() {
        val database = realDatabase()
        try {
            restore(database, listOf(
                "anonymous_subscribed_subreddits.json" to anonymousSubredditsJson("-"),
                "read_posts.json" to """[
                    {"username":"edgan","id":"abc","time":1,"readPostType":0},
                    {"username":"-","id":"def","time":1,"readPostType":0}]""",
                "reminders.json" to """[{"accountName":"edgan","postId":"abc","commentId":"",
                    "content":"look again","createdAt":1,"reminderTime":2}]""",
            ))

            assertEquals(1, database.subscribedSubredditDao()
                .getAllSubscribedSubredditsList(Account.ANONYMOUS_ACCOUNT).size)
            assertEquals(listOf(Account.ANONYMOUS_ACCOUNT), usernamesIn(database, "read_posts"))
            val reminders = database.reminderDao().getAllRemindersForBackup()
            assertEquals("a reminder is kept, without the account", 1, reminders.size)
            assertNull(reminders[0].accountName)
        } finally {
            database.close()
        }
    }

    @Test
    fun `users an Infinity backup follows come back followed`() {
        val database = realDatabase()
        try {
            restore(database, listOf(
                "anonymous_subscribed_users.json" to
                    """[{"name":"spez","iconUrl":"","username":"-","favorite":false}]""",
            ))

            val user = database.subscribedUserDao().getSubscribedUser("spez", Account.ANONYMOUS_ACCOUNT)
            assertTrue(user!!.isFollowed)
        } finally {
            database.close()
        }
    }

    @Test
    fun `themes from before posts were coloured by type get the built-in colours`() {
        val database = realDatabase()
        try {
            restore(database, listOf(
                "custom_themes.json" to """[{"name":"Mine","isLightTheme":false,"isDarkTheme":false,"isAmoledTheme":false}]""",
            ))

            val theme = database.customThemeDao().getCustomTheme("Mine")
            assertEquals(CustomThemeWrapper.getSolarizedAmoled(context).imageTypeBackgroundColor,
                theme!!.imageTypeBackgroundColor)
        } finally {
            database.close()
        }
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
