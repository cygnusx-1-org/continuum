package ml.docilealligator.infinityforreddit.asynctasks

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ProviderInfo
import android.provider.DocumentsContract
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.io.ObjectInputStream
import java.util.concurrent.Executor
import ml.docilealligator.infinityforreddit.BuildConfig
import ml.docilealligator.infinityforreddit.RedditDataRoomDatabase
import ml.docilealligator.infinityforreddit.account.Account
import ml.docilealligator.infinityforreddit.account.AccountScope
import ml.docilealligator.infinityforreddit.subscribedsubreddit.SubscribedSubredditData
import ml.docilealligator.infinityforreddit.subscribeduser.SubscribedUserData
import ml.docilealligator.infinityforreddit.utils.SharedPreferencesUtils
import net.lingala.zip4j.ZipFile
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

/**
 * What goes into a backup.
 *
 * The mirror of [RestoreSettingsTest], and the half that used to rest on a device check. What it is
 * really asking is which `SharedPreferences` the backup reads: the injected ones answer for the
 * account that is signed in — renaming its keys and dropping everybody else's — so a backup taken
 * through them holds one account's settings under names belonging to nobody. Every assertion below
 * is about the file as it is on disk.
 */
@RunWith(RobolectricTestRunner::class)
class BackupSettingsTest {

    private companion object {
        const val PASSWORD = "backup-test"
    }

    private val context: Context = ApplicationProvider.getApplicationContext()

    private lateinit var destination: File
    private lateinit var defaultPreferences: SharedPreferences
    private lateinit var sortType: SharedPreferences
    private lateinit var bottomAppBar: SharedPreferences
    private lateinit var database: RedditDataRoomDatabase

    @Before
    fun setUp() {
        destination = File(context.cacheDir, "backup-destination")
        destination.deleteRecursively()
        destination.mkdirs()
        FakeDocumentsProvider.root = destination

        val info = ProviderInfo().apply {
            authority = FakeDocumentsProvider.AUTHORITY
            exported = true
            grantUriPermissions = true
            readPermission = Manifest.permission.MANAGE_DOCUMENTS
            writePermission = Manifest.permission.MANAGE_DOCUMENTS
        }
        Robolectric.buildContentProvider(FakeDocumentsProvider::class.java).create(info)

        defaultPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        sortType = file(SharedPreferencesUtils.SORT_TYPE_SHARED_PREFERENCES_FILE)
        bottomAppBar = file(SharedPreferencesUtils.BOTTOM_APP_BAR_SHARED_PREFERENCES_FILE)
        for (preferences in listOf(defaultPreferences, sortType, bottomAppBar)) {
            preferences.edit().clear().commit()
        }

        database = RedditDataRoomDatabase.createInMemoryForTest(context)
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun file(name: String): SharedPreferences =
        context.getSharedPreferences(name, Context.MODE_PRIVATE)

    private fun key(accountName: String?, base: String) = AccountScope.key(accountName, base)

    /** Runs a backup into the fake provider's directory and returns the zip it wrote. */
    private fun backUp(): ZipFile {
        BackupSettings.backupSettings(
            context,
            Executor { it.run() },
            android.os.Handler(android.os.Looper.getMainLooper()),
            context.contentResolver,
            DocumentsContract.buildTreeDocumentUri(
                FakeDocumentsProvider.AUTHORITY, FakeDocumentsProvider.ROOT_DOCUMENT_ID),
            PASSWORD,
            database,
            file("light_theme_test"),
            file("dark_theme_test"),
            file("amoled_theme_test"),
            file("scrolled_position_test"),
            file("main_page_tabs_test"),
            file("proxy_test"),
            file("nsfw_and_spoiler_test"),
            file("post_history_test"),
            file("recently_visited_test"),
            object : BackupSettings.BackupSettingsListener {
                override fun success() = Unit
                override fun failed(errorMessage: String) = Unit
            },
        )

        val written = destination.listFiles()?.singleOrNull { it.name.endsWith(".zip") }
        assertNotNull("the backup wrote no zip into the destination directory", written)
        return ZipFile(written!!, PASSWORD.toCharArray())
    }

    /** One backed up preference file, read back out of the zip as the map it was written from. */
    private fun backedUpFile(zip: ZipFile, name: String): Map<*, *> {
        val entry = BuildConfig.VERSION_NAME + "/" + name + BackupSettings.PREFERENCES_FILE_SUFFIX
        val header = zip.getFileHeader(entry)
        assertNotNull("no entry named " + entry, header)
        return ObjectInputStream(zip.getInputStream(header)).use { it.readObject() as Map<*, *> }
    }

    @Test
    fun `every account's keys are in the zip, under the names they have on disk`() {
        defaultPreferences.edit()
            .putString(key("alice", SharedPreferencesUtils.THEME_KEY), "2")
            .putString(key("bob", SharedPreferencesUtils.THEME_KEY), "5")
            .putString(key(null, SharedPreferencesUtils.THEME_KEY), "1")
            .commit()

        val backedUp = backedUpFile(backUp(), SharedPreferencesUtils.DEFAULT_PREFERENCES_FILE)

        assertEquals("2", backedUp[key("alice", SharedPreferencesUtils.THEME_KEY)])
        assertEquals("5", backedUp[key("bob", SharedPreferencesUtils.THEME_KEY)])
        assertEquals("1", backedUp[key(null, SharedPreferencesUtils.THEME_KEY)])
    }

    @Test
    fun `a global setting stays global`() {
        defaultPreferences.edit().putBoolean(SharedPreferencesUtils.APP_LOCK, true).commit()

        val backedUp = backedUpFile(backUp(), SharedPreferencesUtils.DEFAULT_PREFERENCES_FILE)

        assertEquals(true, backedUp[SharedPreferencesUtils.APP_LOCK])
    }

    @Test
    fun `the account-scoped files are read past the facade`() {
        // One key each in two of the files the injected instances scope. Read through those, a
        // backup would hold whatever the signed-in account had and nothing of anyone else's.
        sortType.edit().putString(key("alice", "sort_type_best_post"), "top").commit()
        bottomAppBar.edit()
            .putInt(key("bob", SharedPreferencesUtils.MAIN_ACTIVITY_BOTTOM_APP_BAR_OPTION_1), 9)
            .commit()

        val zip = backUp()

        assertEquals("top", backedUpFile(zip, SharedPreferencesUtils.SORT_TYPE_SHARED_PREFERENCES_FILE)[
            key("alice", "sort_type_best_post")])
        assertEquals(9, backedUpFile(zip, SharedPreferencesUtils.BOTTOM_APP_BAR_SHARED_PREFERENCES_FILE)[
            key("bob", SharedPreferencesUtils.MAIN_ACTIVITY_BOTTOM_APP_BAR_OPTION_1)])
    }

    /** One backed up database table, read back out of the zip as the JSON it was written as. */
    private fun backedUpTable(zip: ZipFile, name: String): String {
        val entry = BuildConfig.VERSION_NAME + "/database/" + name
        val header = zip.getFileHeader(entry)
        assertNotNull("no entry named " + entry, header)
        return zip.getInputStream(header).use { it.readBytes().decodeToString() }
    }

    /**
     * The anonymous account's rows are exported by name, from three tables the backup asks for
     * explicitly rather than wholesale. Nothing else in this suite reaches the database directory,
     * so a change to that name would have gone unnoticed here.
     */
    @Test
    fun `the anonymous account's own rows are in the backup`() {
        database.subscribedSubredditDao().insert(SubscribedSubredditData(
            "t5_1", "pics", "", Account.ANONYMOUS_ACCOUNT, false))
        database.subscribedUserDao().insert(SubscribedUserData(
            "someone", "", Account.ANONYMOUS_ACCOUNT, false))

        val zip = backUp()

        assertTrue("the anonymous subscription is missing from the backup",
            backedUpTable(zip, "anonymous_subscribed_subreddits.json").contains("pics"))
        assertTrue("the anonymous followed user is missing from the backup",
            backedUpTable(zip, "anonymous_subscribed_users.json").contains("someone"))
    }

    @Test
    fun `a signed-in account's rows are not in the anonymous entries`() {
        database.accountDao().insert(
            Account("alice", null, null, null, null, null, 0, true, false))
        database.subscribedSubredditDao().insert(SubscribedSubredditData(
            "t5_2", "news", "", "alice", false))

        val zip = backUp()

        assertFalse("alice's subscription is in the anonymous entry",
            backedUpTable(zip, "anonymous_subscribed_subreddits.json").contains("news"))
    }

    @Test
    fun `one entry per preference file, named after the file`() {
        val names = backUp().fileHeaders.map { it.fileName }

        // RestoreSettings looks these up by exact name, so the two halves have to agree.
        for (name in listOf(
            SharedPreferencesUtils.DEFAULT_PREFERENCES_FILE,
            SharedPreferencesUtils.DEFAULT_PREFERENCES_FILE + "_private",
            SharedPreferencesUtils.SORT_TYPE_SHARED_PREFERENCES_FILE,
            SharedPreferencesUtils.POST_LAYOUT_SHARED_PREFERENCES_FILE,
            SharedPreferencesUtils.POST_DETAILS_SHARED_PREFERENCES_FILE,
            SharedPreferencesUtils.NAVIGATION_DRAWER_SHARED_PREFERENCES_FILE,
            SharedPreferencesUtils.BOTTOM_APP_BAR_SHARED_PREFERENCES_FILE,
        )) {
            val entry = BuildConfig.VERSION_NAME + "/" + name + BackupSettings.PREFERENCES_FILE_SUFFIX
            assertTrue(entry, names.contains(entry))
        }
    }
}
