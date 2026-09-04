package ml.docilealligator.infinityforreddit.account

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import ml.docilealligator.infinityforreddit.RedditDataRoomDatabase
import ml.docilealligator.infinityforreddit.commentfilter.CommentFilter
import ml.docilealligator.infinityforreddit.postfilter.PostFilter
import ml.docilealligator.infinityforreddit.postfilter.PostFilterUsage
import ml.docilealligator.infinityforreddit.utils.SharedPreferencesUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Copying and resetting are the two places the classification is read for its own sake rather than
 * to route a single key, so what matters is that they agree with it exactly: everything an account
 * owns moves or goes, and nothing else is touched — not the app's own settings, not the account
 * being copied from, and not some third account that happens to share the file.
 */
@RunWith(RobolectricTestRunner::class)
class AccountSettingsTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private lateinit var defaultPreferences: SharedPreferences
    private lateinit var sortType: SharedPreferences
    private lateinit var nsfwAndSpoiler: SharedPreferences
    private lateinit var mainPageTabs: SharedPreferences
    private lateinit var bottomAppBar: SharedPreferences
    private lateinit var database: RedditDataRoomDatabase

    @Before
    fun setUp() {
        defaultPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        sortType = file(SharedPreferencesUtils.SORT_TYPE_SHARED_PREFERENCES_FILE)
        nsfwAndSpoiler = file(SharedPreferencesUtils.NSFW_AND_SPOILER_SHARED_PREFERENCES_FILE)
        mainPageTabs = file(SharedPreferencesUtils.MAIN_PAGE_TABS_SHARED_PREFERENCES_FILE)
        bottomAppBar = file(SharedPreferencesUtils.BOTTOM_APP_BAR_SHARED_PREFERENCES_FILE)

        for (preferences in listOf(defaultPreferences, sortType, nsfwAndSpoiler, mainPageTabs, bottomAppBar)) {
            preferences.edit().clear().commit()
        }

        // A real database, not a mock: filters are per-account settings too, so a copy and a reset
        // have to be answerable about them.
        database = Room.inMemoryDatabaseBuilder(context, RedditDataRoomDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun postFilter(name: String, accountName: String) = PostFilter().apply {
        this.name = name
        this.username = accountName
    }

    private fun commentFilter(name: String, accountName: String) = CommentFilter().apply {
        this.name = name
        this.username = accountName
    }

    private fun file(name: String): SharedPreferences =
        context.getSharedPreferences(name, Context.MODE_PRIVATE)

    private fun key(accountName: String?, base: String) = AccountScope.key(accountName, base)

    @Test
    fun `a copy gives the destination what the source has`() {
        defaultPreferences.edit()
            .putString(key("alice", SharedPreferencesUtils.THEME_KEY), "2")
            .putInt(key("alice", SharedPreferencesUtils.FONT_SIZE_KEY), 18)
            .commit()
        sortType.edit().putString(key("alice", "sort_type_best_post"), "top").commit()
        nsfwAndSpoiler.edit().putBoolean(key("alice", SharedPreferencesUtils.NSFW_BASE), true).commit()

        AccountSettings.copyBetweenAccounts(context, database, "alice", "bob")

        assertEquals("2", defaultPreferences.getString(key("bob", SharedPreferencesUtils.THEME_KEY), null))
        assertEquals(18, defaultPreferences.getInt(key("bob", SharedPreferencesUtils.FONT_SIZE_KEY), 0))
        assertEquals("top", sortType.getString(key("bob", "sort_type_best_post"), null))
        assertTrue(nsfwAndSpoiler.getBoolean(key("bob", SharedPreferencesUtils.NSFW_BASE), false))
    }

    @Test
    fun `a copy runs one way`() {
        defaultPreferences.edit()
            .putString(key("alice", SharedPreferencesUtils.THEME_KEY), "2")
            .putString(key("bob", SharedPreferencesUtils.THEME_KEY), "0")
            .commit()

        AccountSettings.copyBetweenAccounts(context, database, "alice", "bob")

        assertEquals("the source is only ever read",
            "2", defaultPreferences.getString(key("alice", SharedPreferencesUtils.THEME_KEY), null))
    }

    @Test
    fun `a copy mirrors rather than merges`() {
        defaultPreferences.edit()
            .putString(key("alice", SharedPreferencesUtils.THEME_KEY), "2")
            .putBoolean(key("bob", SharedPreferencesUtils.AMOLED_DARK_KEY), true)
            .commit()

        AccountSettings.copyBetweenAccounts(context, database, "alice", "bob")

        assertFalse("alice has never set this, so bob must not keep having it",
            defaultPreferences.contains(key("bob", SharedPreferencesUtils.AMOLED_DARK_KEY)))
    }

    @Test
    fun `a copy leaves the app's own settings alone`() {
        defaultPreferences.edit()
            .putBoolean(SharedPreferencesUtils.APP_LOCK, true)
            .putString(key("alice", SharedPreferencesUtils.THEME_KEY), "2")
            .commit()

        AccountSettings.copyBetweenAccounts(context, database, "alice", "bob")

        assertTrue("app lock is global, and has no namespace to be caught by",
            defaultPreferences.getBoolean(SharedPreferencesUtils.APP_LOCK, false))
    }

    @Test
    fun `a copy leaves a third account alone`() {
        defaultPreferences.edit()
            .putString(key("alice", SharedPreferencesUtils.THEME_KEY), "2")
            .putString(key("carol", SharedPreferencesUtils.THEME_KEY), "5")
            .commit()

        AccountSettings.copyBetweenAccounts(context, database, "alice", "bob")

        assertEquals("5", defaultPreferences.getString(key("carol", SharedPreferencesUtils.THEME_KEY), null))
    }

    @Test
    fun `a copy carries the link handler, which scopes its own key`() {
        defaultPreferences.edit()
            .putString(key("alice", SharedPreferencesUtils.LINK_HANDLER_BASE), "4")
            .commit()

        AccountSettings.copyBetweenAccounts(context, database, "alice", "bob")

        assertEquals("4", defaultPreferences.getString(key("bob", SharedPreferencesUtils.LINK_HANDLER_BASE), null))
    }

    @Test
    fun `a copy leaves main page tabs where they are`() {
        mainPageTabs.edit()
            .putString(key("alice", SharedPreferencesUtils.MAIN_PAGE_TABS_ORDER), "[alice's tabs]")
            .putString(key("bob", SharedPreferencesUtils.MAIN_PAGE_TABS_ORDER), "[bob's tabs]")
            .commit()

        AccountSettings.copyBetweenAccounts(context, database, "alice", "bob")

        assertEquals("tabs name subreddits bob may not have",
            "[bob's tabs]",
            mainPageTabs.getString(key("bob", SharedPreferencesUtils.MAIN_PAGE_TABS_ORDER), null))
    }

    @Test
    fun `anonymous can be copied onto`() {
        defaultPreferences.edit()
            .putString(key("alice", SharedPreferencesUtils.THEME_KEY), "2")
            .commit()

        AccountSettings.copyBetweenAccounts(context, database, "alice", Account.ANONYMOUS_ACCOUNT)

        assertEquals("2", defaultPreferences.getString(key(null, SharedPreferencesUtils.THEME_KEY), null))
    }

    @Test
    fun `copying an account onto itself changes nothing`() {
        defaultPreferences.edit()
            .putString(key("alice", SharedPreferencesUtils.THEME_KEY), "2")
            .commit()

        AccountSettings.copyBetweenAccounts(context, database, "alice", "alice")

        assertEquals("the mirror would otherwise remove the source's keys before writing them",
            "2", defaultPreferences.getString(key("alice", SharedPreferencesUtils.THEME_KEY), null))
    }

    @Test
    fun `a copy carries the bottom app bar`() {
        bottomAppBar.edit()
            .putInt(key("alice", SharedPreferencesUtils.MAIN_ACTIVITY_BOTTOM_APP_BAR_OPTION_1), 9)
            .commit()

        AccountSettings.copyBetweenAccounts(context, database, "alice", "bob")

        assertEquals(9, bottomAppBar.getInt(
            key("bob", SharedPreferencesUtils.MAIN_ACTIVITY_BOTTOM_APP_BAR_OPTION_1), 0))
    }

    @Test
    fun `a copy mirrors the filters too`() {
        database.postFilterDao().insert(postFilter("NSFW", "alice"))
        database.postFilterUsageDao().insert(
            PostFilterUsage("NSFW", "alice", PostFilterUsage.HOME_TYPE, null))
        database.commentFilterDao().insert(commentFilter("Rude", "alice"))
        database.postFilterDao().insert(postFilter("bob's own", "bob"))

        AccountSettings.copyBetweenAccounts(context, database, "alice", "bob")

        val bobs = database.postFilterDao().getAllPostFilters("bob")
        assertEquals(listOf("NSFW"), bobs.map { it.name })
        assertEquals(listOf("Rude"), database.commentFilterDao().getAllCommentFilters("bob").map { it.name })
        assertEquals("the usage follows the filter it belongs to",
            1, database.postFilterUsageDao().getAllPostFilterUsage("NSFW", "bob").size)
        assertEquals("the source keeps its own", 1,
            database.postFilterDao().getAllPostFilters("alice").size)
    }

    @Test
    fun `a reset takes the account's filters and nobody else's`() {
        database.postFilterDao().insert(postFilter("NSFW", "alice"))
        database.postFilterUsageDao().insert(
            PostFilterUsage("NSFW", "alice", PostFilterUsage.HOME_TYPE, null))
        database.commentFilterDao().insert(commentFilter("Rude", "alice"))
        database.postFilterDao().insert(postFilter("NSFW", "bob"))

        AccountSettings.reset(context, database, "alice")

        assertTrue(database.postFilterDao().getAllPostFilters("alice").isEmpty())
        assertTrue(database.commentFilterDao().getAllCommentFilters("alice").isEmpty())
        assertTrue("the usages cascade off the filter",
            database.postFilterUsageDao().getAllPostFilterUsage("NSFW", "alice").isEmpty())
        assertEquals("a filter of the same name is still another account's", 1,
            database.postFilterDao().getAllPostFilters("bob").size)
    }

    @Test
    fun `a reset takes everything the account owns, tabs included`() {
        defaultPreferences.edit()
            .putString(key("alice", SharedPreferencesUtils.THEME_KEY), "2")
            .putString(key("alice", SharedPreferencesUtils.LINK_HANDLER_BASE), "4")
            .commit()
        sortType.edit().putString(key("alice", "sort_type_best_post"), "top").commit()
        nsfwAndSpoiler.edit().putBoolean(key("alice", SharedPreferencesUtils.NSFW_BASE), true).commit()
        mainPageTabs.edit()
            .putString(key("alice", SharedPreferencesUtils.MAIN_PAGE_TABS_ORDER), "[alice's tabs]")
            .commit()
        bottomAppBar.edit()
            .putInt(key("alice", SharedPreferencesUtils.MAIN_ACTIVITY_BOTTOM_APP_BAR_OPTION_1), 9)
            .commit()

        AccountSettings.reset(context, database, "alice")

        assertFalse(defaultPreferences.contains(key("alice", SharedPreferencesUtils.THEME_KEY)))
        assertFalse(defaultPreferences.contains(key("alice", SharedPreferencesUtils.LINK_HANDLER_BASE)))
        assertFalse(sortType.contains(key("alice", "sort_type_best_post")))
        assertFalse(nsfwAndSpoiler.contains(key("alice", SharedPreferencesUtils.NSFW_BASE)))
        assertFalse("a default tab set always loads, so there is no reason to keep the old one",
            mainPageTabs.contains(key("alice", SharedPreferencesUtils.MAIN_PAGE_TABS_ORDER)))
        assertFalse(bottomAppBar.contains(
            key("alice", SharedPreferencesUtils.MAIN_ACTIVITY_BOTTOM_APP_BAR_OPTION_1)))
    }

    @Test
    fun `a reset leaves every other account and the app's own settings alone`() {
        defaultPreferences.edit()
            .putBoolean(SharedPreferencesUtils.APP_LOCK, true)
            .putString(key("alice", SharedPreferencesUtils.THEME_KEY), "2")
            .putString(key("bob", SharedPreferencesUtils.THEME_KEY), "5")
            .putString(key(null, SharedPreferencesUtils.THEME_KEY), "1")
            .commit()

        AccountSettings.reset(context, database, "alice")

        assertTrue(defaultPreferences.getBoolean(SharedPreferencesUtils.APP_LOCK, false))
        assertEquals("5", defaultPreferences.getString(key("bob", SharedPreferencesUtils.THEME_KEY), null))
        assertEquals("1", defaultPreferences.getString(key(null, SharedPreferencesUtils.THEME_KEY), null))
    }

    @Test
    fun `resetting anonymous does not reach an account whose name it is a prefix of`() {
        defaultPreferences.edit()
            .putString(key(null, SharedPreferencesUtils.THEME_KEY), "1")
            .putString(key("anonymous", SharedPreferencesUtils.THEME_KEY), "5")
            .commit()

        AccountSettings.reset(context, database, Account.ANONYMOUS_ACCOUNT)

        assertFalse(defaultPreferences.contains(key(null, SharedPreferencesUtils.THEME_KEY)))
        assertEquals("a user really called anonymous is a different account",
            "5", defaultPreferences.getString(key("anonymous", SharedPreferencesUtils.THEME_KEY), null))
    }
}
