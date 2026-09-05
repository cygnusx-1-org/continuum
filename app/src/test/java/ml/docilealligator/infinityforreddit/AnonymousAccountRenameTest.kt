package ml.docilealligator.infinityforreddit

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import ml.docilealligator.infinityforreddit.account.Account
import ml.docilealligator.infinityforreddit.account.AccountScope
import ml.docilealligator.infinityforreddit.account.AnonymousAccountRename
import ml.docilealligator.infinityforreddit.utils.SharedPreferencesUtils
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Renaming the anonymous account from `"-"` to `".anonymous"`.
 *
 * The rename is the one piece of this that can destroy data rather than misplace it: a table left
 * out, and those rows keep an account name nothing looks up again, which is the anonymous profile
 * silently emptying. So the completeness of the table list is asserted against the schema itself
 * rather than against a copy of the list, and the rows are moved through a real database rather
 * than a hand-built one -- a hand-built schema is another copy, and would drift the same way.
 *
 * Both call paths are covered because they differ in a way the routine has to absorb: a migrating
 * database has only the old row, while a restore runs long after the new one exists.
 */
@RunWith(RobolectricTestRunner::class)
class AnonymousAccountRenameTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private lateinit var database: RedditDataRoomDatabase

    @Before
    fun setUp() {
        database = RedditDataRoomDatabase.createInMemoryForTest(context)
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun db() = database.openHelper.writableDatabase

    private fun account(name: String, token: String? = null) {
        db().execSQL(
            "INSERT OR REPLACE INTO accounts (username, karma, is_current_user, is_mod, access_token)"
                + " VALUES (?, 0, 0, 0, ?)", arrayOf(name, token))
    }

    private fun usernamesIn(table: String): List<String> {
        val names = mutableListOf<String>()
        db().query("SELECT username FROM $table ORDER BY username").use {
            while (it.moveToNext()) names.add(it.getString(0))
        }
        return names
    }

    private fun tokenOf(name: String): String? {
        db().query("SELECT access_token FROM accounts WHERE username = ?", arrayOf(name)).use {
            return if (it.moveToNext()) it.getString(0) else null
        }
    }

    /** Removes the row the open callback guarantees, to get back to a pre-rename database. */
    private fun asDatabaseBeingMigrated() {
        db().execSQL("DELETE FROM accounts WHERE username = '.anonymous'")
    }

    @Test
    fun `the table list covers every table that stores an account name`() {
        // The assertion that matters: an entity added later with a username column is caught here
        // rather than by a user losing their anonymous data. Read from the schema, so there is no
        // second list to keep in step.
        val tables = mutableListOf<String>()
        db().query("SELECT name FROM sqlite_master WHERE type = 'table'").use {
            while (it.moveToNext()) tables.add(it.getString(0))
        }

        val storesAnAccountName = tables.filter { table ->
            db().query("PRAGMA table_info(`$table`)").use { columns ->
                var found = false
                while (columns.moveToNext()) {
                    // A username column of any other type is not an account name: custom_themes
                    // has one holding a theme colour as an int.
                    if (columns.getString(1) == "username" && columns.getString(2) == "TEXT") {
                        found = true
                    }
                }
                found
            }
        }

        // accounts is the parent, handled by name rather than in the loop over the children.
        assertEquals(
            (RedditDataRoomDatabase.ACCOUNT_NAME_TABLES.toList() + "accounts").sorted(),
            storesAnAccountName.sorted())
    }

    @Test
    fun `a migrating database has its rows and its token moved off the old name`() {
        asDatabaseBeingMigrated()
        account("-", token = "anonymous-token")
        account("alice")
        db().execSQL("INSERT INTO read_posts (username, id, read_post_type, time)"
            + " VALUES ('-', 't3_1', 0, 1)")
        db().execSQL("INSERT INTO subscribed_subreddits (id, name, icon, username, is_favorite)"
            + " VALUES ('t5_1', 'pics', '', '-', 0)")

        RedditDataRoomDatabase.renameAnonymousAccount(db())

        assertEquals(listOf(".anonymous"), usernamesIn("read_posts"))
        assertEquals(listOf(".anonymous"), usernamesIn("subscribed_subreddits"))
        assertEquals(listOf(".anonymous", "alice"), usernamesIn("accounts"))
        // Renamed rather than recreated: AccountDaoKt reads the application-only token back by
        // name, and an insert-and-delete would have dropped it.
        assertEquals("anonymous-token", tokenOf(".anonymous"))
    }

    /**
     * The restore path. The new row already exists here, so the parent cannot simply be renamed
     * onto it -- the children have to be moved across and the old parent dropped.
     */
    @Test
    fun `a restore folds the old rows into the row that already exists`() {
        account("-")
        db().execSQL("INSERT INTO read_posts (username, id, read_post_type, time)"
            + " VALUES ('-', 't3_old', 0, 1)")
        db().execSQL("INSERT INTO read_posts (username, id, read_post_type, time)"
            + " VALUES ('.anonymous', 't3_new', 0, 2)")

        RedditDataRoomDatabase.renameAnonymousAccount(db())

        assertEquals(listOf(".anonymous", ".anonymous"), usernamesIn("read_posts"))
        assertEquals(listOf(".anonymous"), usernamesIn("accounts"))
    }

    @Test
    fun `dropping the old parent does not cascade away the rows that just moved`() {
        asDatabaseBeingMigrated()
        account("-")
        for (id in listOf("t3_1", "t3_2", "t3_3")) {
            db().execSQL("INSERT INTO read_posts (username, id, read_post_type, time)"
                + " VALUES ('-', '$id', 0, 1)")
        }

        RedditDataRoomDatabase.renameAnonymousAccount(db())

        assertEquals(3, usernamesIn("read_posts").size)
    }

    /**
     * The migration's own context, which is the one every upgrading install takes.
     *
     * Room hands a migration a transaction that is already open, so the one this routine starts is
     * a nested one -- counted rather than opened again, and the only place `defer_foreign_keys` can
     * take effect. Called directly, as the tests above do, it opens the outermost transaction
     * itself and the nesting is never exercised. This is the path that matters most and the one a
     * test can most easily miss.
     */
    @Test
    fun `it works nested inside a transaction the caller already opened`() {
        asDatabaseBeingMigrated()
        account("-", token = "anonymous-token")
        db().execSQL("INSERT INTO read_posts (username, id, read_post_type, time)"
            + " VALUES ('-', 't3_1', 0, 1)")

        db().beginTransaction()
        try {
            RedditDataRoomDatabase.renameAnonymousAccount(db())
            db().setTransactionSuccessful()
        } finally {
            db().endTransaction()
        }

        assertEquals(listOf(".anonymous"), usernamesIn("read_posts"))
        assertEquals(listOf(".anonymous"), usernamesIn("accounts"))
        assertEquals("anonymous-token", tokenOf(".anonymous"))
    }

    @Test
    fun `running it twice changes nothing the second time`() {
        asDatabaseBeingMigrated()
        account("-", token = "anonymous-token")
        db().execSQL("INSERT INTO read_posts (username, id, read_post_type, time)"
            + " VALUES ('-', 't3_1', 0, 1)")

        RedditDataRoomDatabase.renameAnonymousAccount(db())
        RedditDataRoomDatabase.renameAnonymousAccount(db())

        assertEquals(listOf(".anonymous"), usernamesIn("read_posts"))
        assertEquals(listOf(".anonymous"), usernamesIn("accounts"))
        assertEquals("anonymous-token", tokenOf(".anonymous"))
    }

    @Test
    fun `a database that never had an anonymous row ends up with one`() {
        asDatabaseBeingMigrated()
        account("alice")

        RedditDataRoomDatabase.renameAnonymousAccount(db())

        assertEquals(listOf(".anonymous", "alice"), usernamesIn("accounts"))
    }

    @Test
    fun `the open callback is what guarantees the row every anonymous write needs`() {
        // Nothing creates it on the way to a write any more, so this is the invariant those
        // twelve removed insertIfNotExists() calls were standing in for.
        assertEquals(listOf(".anonymous"), usernamesIn("accounts"))

        db().execSQL("INSERT INTO recently_visited (username, name, type, last_visited)"
            + " VALUES ('.anonymous', 'pics', 0, 1)")

        assertEquals(listOf(".anonymous"), usernamesIn("recently_visited"))
    }

    // --- the preferences half -------------------------------------------------------------------

    private fun currentAccount(): SharedPreferences = context.getSharedPreferences(
        SharedPreferencesUtils.CURRENT_ACCOUNT_SHARED_PREFERENCES_FILE, Context.MODE_PRIVATE)

    private fun sortType(): SharedPreferences = context.getSharedPreferences(
        SharedPreferencesUtils.SORT_TYPE_SHARED_PREFERENCES_FILE, Context.MODE_PRIVATE)

    @Test
    fun `the stored current-account name is brought up to the new spelling`() {
        currentAccount().edit().putString(SharedPreferencesUtils.ACCOUNT_NAME, "-").commit()

        AnonymousAccountRename.renameCurrentAccount(currentAccount())

        assertEquals(Account.ANONYMOUS_ACCOUNT,
            currentAccount().getString(SharedPreferencesUtils.ACCOUNT_NAME, null))
    }

    @Test
    fun `a signed-in account's stored name is left alone`() {
        currentAccount().edit().putString(SharedPreferencesUtils.ACCOUNT_NAME, "alice").commit()

        AnonymousAccountRename.renameCurrentAccount(currentAccount())

        assertEquals("alice", currentAccount().getString(SharedPreferencesUtils.ACCOUNT_NAME, null))
    }

    /**
     * Home's anonymous sort was keyed as `sort_type_subreddit_post_` plus the account name --
     * anonymous as a subreddit named `"-"`. Once the name contains a `.`, [AccountScope.baseOf]
     * splits the key there instead, and it stops being recognised as the anonymous account's.
     */
    @Test
    fun `home's anonymous sort moves onto a key of its own`() {
        val legacy = AccountScope.key(Account.ANONYMOUS_ACCOUNT,
            SharedPreferencesUtils.SORT_TYPE_SUBREDDIT_POST_BASE + "-")
        val legacyTime = AccountScope.key(Account.ANONYMOUS_ACCOUNT,
            SharedPreferencesUtils.SORT_TIME_SUBREDDIT_POST_BASE + "-")
        sortType().edit().clear()
            .putString(legacy, "TOP")
            .putString(legacyTime, "MONTH")
            .commit()

        AnonymousAccountRename.moveAnonymousHomeSortKeys(context)

        assertEquals("TOP", sortType().getString(AccountScope.key(Account.ANONYMOUS_ACCOUNT,
            SharedPreferencesUtils.SORT_TYPE_ANONYMOUS_FRONT_PAGE_POST), null))
        assertEquals("MONTH", sortType().getString(AccountScope.key(Account.ANONYMOUS_ACCOUNT,
            SharedPreferencesUtils.SORT_TIME_ANONYMOUS_FRONT_PAGE_POST), null))
        assertNull("the old key is gone, not merely shadowed", sortType().getString(legacy, null))
    }

    @Test
    fun `a subreddit's own remembered sort is not mistaken for home's`() {
        val pics = AccountScope.key(Account.ANONYMOUS_ACCOUNT,
            SharedPreferencesUtils.SORT_TYPE_SUBREDDIT_POST_BASE + "pics")
        sortType().edit().clear().putString(pics, "NEW").commit()

        AnonymousAccountRename.moveAnonymousHomeSortKeys(context)

        assertEquals("NEW", sortType().getString(pics, null))
        assertNull(sortType().getString(AccountScope.key(Account.ANONYMOUS_ACCOUNT,
            SharedPreferencesUtils.SORT_TYPE_ANONYMOUS_FRONT_PAGE_POST), null))
    }
}
