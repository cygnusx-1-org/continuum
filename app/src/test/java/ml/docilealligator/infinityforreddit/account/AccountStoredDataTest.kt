package ml.docilealligator.infinityforreddit.account

import android.content.Context
import android.content.SharedPreferences
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import ml.docilealligator.infinityforreddit.RedditDataRoomDatabase
import ml.docilealligator.infinityforreddit.readpost.ReadPost
import ml.docilealligator.infinityforreddit.readpost.ReadPostType
import ml.docilealligator.infinityforreddit.subreddit.SubredditData
import ml.docilealligator.infinityforreddit.subscribedsubreddit.SubscribedSubredditData
import ml.docilealligator.infinityforreddit.subscribeduser.SubscribedUserData
import ml.docilealligator.infinityforreddit.utils.SharedPreferencesUtils
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Each deletion is offered on a screen headed with one account's name, so the thing worth proving
 * about all four is the same: the account named loses that one kind of data, and nobody else loses
 * anything. These replaced actions that cleared whole files and whole tables, which is exactly the
 * behaviour a regression here would go back to.
 */
@RunWith(RobolectricTestRunner::class)
class AccountStoredDataTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private lateinit var sortType: SharedPreferences
    private lateinit var postLayout: SharedPreferences
    private lateinit var frontPageScrolledPosition: SharedPreferences
    private lateinit var database: RedditDataRoomDatabase

    @Before
    fun setUp() {
        sortType = file(SharedPreferencesUtils.SORT_TYPE_SHARED_PREFERENCES_FILE)
        postLayout = file(SharedPreferencesUtils.POST_LAYOUT_SHARED_PREFERENCES_FILE)
        frontPageScrolledPosition =
            file(SharedPreferencesUtils.FRONT_PAGE_SCROLLED_POSITION_SHARED_PREFERENCES_FILE)

        for (preferences in listOf(sortType, postLayout, frontPageScrolledPosition)) {
            preferences.edit().clear().commit()
        }

        database = Room.inMemoryDatabaseBuilder(context, RedditDataRoomDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        // read_posts has a foreign key onto accounts, so the rows below need somewhere to point.
        for (accountName in listOf(Account.ANONYMOUS_ACCOUNT, "alice", "bob")) {
            database.accountDao().insert(
                Account(accountName, null, null, null, null, null, 0, false, false))
        }
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun file(name: String): SharedPreferences =
        context.getSharedPreferences(name, Context.MODE_PRIVATE)

    @Test
    fun `deleting subscribed subreddits leaves the other accounts theirs`() {
        val dao = database.subscribedSubredditDao()
        dao.insert(SubscribedSubredditData("t5_1", "pics", "", "alice", true))
        dao.insert(SubscribedSubredditData("t5_2", "news", "", "alice", false))
        dao.insert(SubscribedSubredditData("t5_1", "pics", "", "bob", false))
        dao.insert(SubscribedSubredditData("t5_1", "pics", "", Account.ANONYMOUS_ACCOUNT, false))

        AccountStoredData.deleteSubscribedSubreddits(database, "alice")

        assertTrue(dao.getAllSubscribedSubredditsList("alice").isEmpty())
        assertEquals(1, dao.getAllSubscribedSubredditsList("bob").size)
        assertEquals(1, dao.getAllSubscribedSubredditsList(Account.ANONYMOUS_ACCOUNT).size)
    }

    @Test
    fun `deleting the anonymous account's subscribed subreddits leaves the signed-in ones theirs`() {
        val dao = database.subscribedSubredditDao()
        dao.insert(SubscribedSubredditData("t5_1", "pics", "", Account.ANONYMOUS_ACCOUNT, false))
        dao.insert(SubscribedSubredditData("t5_1", "pics", "", "alice", false))

        AccountStoredData.deleteSubscribedSubreddits(database, Account.ANONYMOUS_ACCOUNT)

        assertTrue(dao.getAllSubscribedSubredditsList(Account.ANONYMOUS_ACCOUNT).isEmpty())
        assertEquals(1, dao.getAllSubscribedSubredditsList("alice").size)
    }

    /**
     * One `subscribed_users` row means followed or saved, so the deletion has to take a row that is
     * only saved as well as one that is only followed — and neither of the other accounts' rows.
     */
    @Test
    fun `deleting subscribed users takes the followed and the saved`() {
        val dao = database.subscribedUserDao()
        dao.insert(SubscribedUserData("Alice_Follows", "", "alice", false))
        dao.updateFollowed("Alice_Follows", "alice", true)
        dao.insertIfAbsent("Alice_Saved", "", "alice")
        dao.updateSaved("Alice_Saved", "alice", true)
        dao.insert(SubscribedUserData("Bobs_Person", "", "bob", false))

        AccountStoredData.deleteSubscribedUsers(database, "alice")

        assertTrue(dao.getAllSubscribedUsersList("alice").isEmpty())
        assertEquals(1, dao.getAllSubscribedUsersList("bob").size)
    }

    /** The metadata caches have no account column, so a per-account deletion must not touch them. */
    @Test
    fun `deleting one account's subreddits leaves the shared metadata cache alone`() {
        database.subredditDao().insert(SubredditData(
            "t5_1", "pics", "", "", "", "", 1, 0L, "", false))
        database.subscribedSubredditDao()
            .insert(SubscribedSubredditData("t5_1", "pics", "", "alice", false))

        AccountStoredData.deleteSubscribedSubreddits(database, "alice")

        assertTrue(database.subscribedSubredditDao().getAllSubscribedSubredditsList("alice").isEmpty())
        assertEquals("pics", database.subredditDao().getSubredditData("pics").name)
    }

    @Test
    fun `deleting sort types leaves the other accounts theirs`() {
        sortType.edit()
            .putString(AccountScope.key("alice", SharedPreferencesUtils.SORT_TYPE_BEST_POST), "HOT")
            .putString(AccountScope.key("alice",
                SharedPreferencesUtils.SORT_TYPE_SUBREDDIT_POST_BASE + "pics"), "NEW")
            .putString(AccountScope.key("bob", SharedPreferencesUtils.SORT_TYPE_BEST_POST), "TOP")
            .putString(AccountScope.key(null, SharedPreferencesUtils.SORT_TYPE_BEST_POST), "RISING")
            .commit()

        AccountStoredData.deleteSortTypes(context, "alice")

        assertEquals(setOf(
            AccountScope.key("bob", SharedPreferencesUtils.SORT_TYPE_BEST_POST),
            AccountScope.key(null, SharedPreferencesUtils.SORT_TYPE_BEST_POST),
        ), sortType.all.keys)
    }

    @Test
    fun `deleting post layouts leaves the other accounts theirs`() {
        postLayout.edit()
            .putInt(AccountScope.key("alice", SharedPreferencesUtils.POST_LAYOUT_FRONT_PAGE_POST), 1)
            .putInt(AccountScope.key("bob", SharedPreferencesUtils.POST_LAYOUT_FRONT_PAGE_POST), 2)
            .commit()

        AccountStoredData.deletePostLayouts(context, "alice")

        assertEquals(setOf(AccountScope.key("bob", SharedPreferencesUtils.POST_LAYOUT_FRONT_PAGE_POST)),
            postLayout.all.keys)
    }

    /**
     * Anonymous is the case the old whole-file clear could not get wrong and a per-account delete
     * can: this file predates [AccountScope] and spells anonymous its own way.
     */
    @Test
    fun `deleting the anonymous scroll position leaves the signed-in accounts theirs`() {
        val anonymous = Account.ANONYMOUS_ACCOUNT +
            SharedPreferencesUtils.FRONT_PAGE_SCROLLED_POSITION_FRONT_PAGE_BASE
        val alice = "alice" + SharedPreferencesUtils.FRONT_PAGE_SCROLLED_POSITION_FRONT_PAGE_BASE
        frontPageScrolledPosition.edit()
            .putString(anonymous, "t3_anon")
            .putString(alice, "t3_alice")
            .commit()

        AccountStoredData.deleteFrontPageScrolledPosition(context, Account.ANONYMOUS_ACCOUNT)

        assertNull(frontPageScrolledPosition.getString(anonymous, null))
        assertEquals("t3_alice", frontPageScrolledPosition.getString(alice, null))
    }

    @Test
    fun `deleting a signed-in account's scroll position leaves anonymous its own`() {
        val anonymous = Account.ANONYMOUS_ACCOUNT +
            SharedPreferencesUtils.FRONT_PAGE_SCROLLED_POSITION_FRONT_PAGE_BASE
        val alice = "alice" + SharedPreferencesUtils.FRONT_PAGE_SCROLLED_POSITION_FRONT_PAGE_BASE
        frontPageScrolledPosition.edit()
            .putString(anonymous, "t3_anon")
            .putString(alice, "t3_alice")
            .commit()

        AccountStoredData.deleteFrontPageScrolledPosition(context, "alice")

        assertEquals("t3_anon", frontPageScrolledPosition.getString(anonymous, null))
        assertNull(frontPageScrolledPosition.getString(alice, null))
    }

    @Test
    fun `deleting read posts leaves the other accounts theirs`() {
        val readPostDao = database.readPostDao()
        readPostDao.insert(ReadPost("alice", "t3_1", ReadPostType.READ_POSTS))
        readPostDao.insert(ReadPost("alice", "t3_2", ReadPostType.READ_POSTS))
        readPostDao.insert(ReadPost("bob", "t3_3", ReadPostType.READ_POSTS))

        AccountStoredData.deleteReadPosts(database, "alice")

        assertEquals(0, readPostDao.getReadPostsCount("alice", ReadPostType.READ_POSTS))
        assertEquals(1, readPostDao.getReadPostsCount("bob", ReadPostType.READ_POSTS))
    }

    /**
     * The anonymous upvoted, downvoted, hidden and saved lists share the `read_posts` table. They
     * are things the user put there rather than a history that accumulated, and the row that
     * deletes a read history counts only the history.
     */
    @Test
    fun `deleting read posts keeps the anonymous lists in the same table`() {
        val readPostDao = database.readPostDao()
        val anonymous = Account.ANONYMOUS_ACCOUNT
        readPostDao.insert(ReadPost(anonymous, "t3_1", ReadPostType.READ_POSTS))
        readPostDao.insert(ReadPost(anonymous, "t3_2", ReadPostType.ANONYMOUS_SAVED_POSTS))
        readPostDao.insert(ReadPost(anonymous, "t3_3", ReadPostType.ANONYMOUS_UPVOTED_POSTS))

        AccountStoredData.deleteReadPosts(database, anonymous)

        assertEquals(0, readPostDao.getReadPostsCount(anonymous, ReadPostType.READ_POSTS))
        assertEquals(1, readPostDao.getReadPostsCount(anonymous, ReadPostType.ANONYMOUS_SAVED_POSTS))
        assertEquals(1, readPostDao.getReadPostsCount(anonymous, ReadPostType.ANONYMOUS_UPVOTED_POSTS))
    }

    /**
     * The delete rows sit beside "Reset This Account's Settings", so what they take has to be a
     * subset of what it takes — a row that outlived a full reset would be a gap in the reset.
     */
    @Test
    fun `a full reset covers what the sort type and post layout deletions do`() {
        sortType.edit()
            .putString(AccountScope.key("alice", SharedPreferencesUtils.SORT_TYPE_BEST_POST), "HOT")
            .commit()
        postLayout.edit()
            .putInt(AccountScope.key("alice", SharedPreferencesUtils.POST_LAYOUT_FRONT_PAGE_POST), 1)
            .commit()

        AccountSettings.reset(context, database, "alice")

        assertTrue(sortType.all.isEmpty())
        assertTrue(postLayout.all.isEmpty())
    }

    @Test
    fun `a file with no per-account keys is not something to reset`() {
        val thrown = try {
            AccountSettings.resetFile(
                context, SharedPreferencesUtils.FRONT_PAGE_SCROLLED_POSITION_SHARED_PREFERENCES_FILE,
                "alice")
            false
        } catch (expected: IllegalArgumentException) {
            true
        }
        assertTrue("the scrolled position file spells accounts its own way", thrown)
    }

    @Test
    fun `deleting one kind leaves the account's other kinds alone`() {
        sortType.edit()
            .putString(AccountScope.key("alice", SharedPreferencesUtils.SORT_TYPE_BEST_POST), "HOT")
            .commit()
        postLayout.edit()
            .putInt(AccountScope.key("alice", SharedPreferencesUtils.POST_LAYOUT_FRONT_PAGE_POST), 1)
            .commit()

        AccountStoredData.deleteSortTypes(context, "alice")

        assertTrue(sortType.all.isEmpty())
        assertFalse("post layouts are a row of their own", postLayout.all.isEmpty())
    }
}
