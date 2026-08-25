package ml.docilealligator.infinityforreddit.recentlyvisited

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.LiveData
import androidx.test.core.app.ApplicationProvider
import java.util.concurrent.Executor
import ml.docilealligator.infinityforreddit.RedditDataRoomDatabase
import ml.docilealligator.infinityforreddit.TestInfinity
import ml.docilealligator.infinityforreddit.account.AccountDao
import ml.docilealligator.infinityforreddit.subscribedsubreddit.SubscribedSubredditDao
import ml.docilealligator.infinityforreddit.subscribeduser.SubscribedUserDao
import ml.docilealligator.infinityforreddit.utils.SharedPreferencesUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * What Recently Visited refuses to write down, and what it does on the way in.
 *
 * Every exclusion lives in [RecordRecentlyVisited] and nothing downstream re-checks it: a name that
 * reaches [RecentlyVisitedDao.insert] is in the list for good, so the record-time decision is the
 * only thing standing between "opened r/all once" and a permanent row. The recording path runs on
 * an executor, so these drive it with a direct one and read back what reached the DAO.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestInfinity::class)
class RecordRecentlyVisitedTest {

    /** Records what was written instead of writing it, so the assertions can be about names. */
    private class FakeRecentlyVisitedDao : RecentlyVisitedDao {
        val inserted = mutableListOf<RecentlyVisited>()
        val trims = mutableListOf<Triple<String, Int, Int>>()

        override fun insert(recentlyVisited: RecentlyVisited) {
            inserted += recentlyVisited
        }

        override fun trimToLimit(accountName: String, type: Int, limit: Int) {
            trims += Triple(accountName, type, limit)
        }

        override fun getRecentlyVisitedWithSearchQuery(
            accountName: String,
            type: Int,
            searchQuery: String
        ): LiveData<List<RecentlyVisited>> = throw UnsupportedOperationException()

        override fun deleteAllForAccount(accountName: String) = Unit
    }

    private val directExecutor = Executor { it.run() }
    private val account = "Alice"

    private lateinit var preferences: SharedPreferences
    private lateinit var dao: FakeRecentlyVisitedDao
    private lateinit var database: RedditDataRoomDatabase

    @Before
    fun setUp() {
        preferences = ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences("recently_visited_test", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()

        dao = FakeRecentlyVisitedDao()
        // Not subscribed to anything and not following anyone: the mocks return null, which is what
        // the DAOs return for a thing that is not in those tables.
        database = mock()
        whenever(database.recentlyVisitedDao()).thenReturn(dao)
        whenever(database.subscribedSubredditDao()).thenReturn(mock<SubscribedSubredditDao>())
        whenever(database.subscribedUserDao()).thenReturn(mock<SubscribedUserDao>())
        whenever(database.accountDao()).thenReturn(mock<AccountDao>())
    }

    private fun turnTheSettingOn() {
        preferences.edit()
            .putBoolean(account + SharedPreferencesUtils.RECENTLY_VISITED_ENABLED_BASE, true)
            .commit()
    }

    private fun visitSubreddit(subredditName: String) {
        RecordRecentlyVisited.recordSubreddit(
            directExecutor, database, preferences, account, subredditName, null
        )
    }

    private fun visitUser(username: String) {
        RecordRecentlyVisited.recordUser(
            directExecutor, database, preferences, account, username, null
        )
    }

    private fun recordedNames() = dao.inserted.map { it.name }

    @Test
    fun `nothing is recorded until the setting is turned on`() {
        assertFalse(RecordRecentlyVisited.isEnabled(account, preferences))

        visitSubreddit("Android")
        visitUser("Bob")

        assertEquals(emptyList<String>(), recordedNames())
    }

    @Test
    fun `reddit's own listings are not communities and are never recorded`() {
        turnTheSettingOn()

        listOf("popular", "all", "random", "randnsfw", "mod", "friends").forEach(::visitSubreddit)
        // The name comes off a URL, so its case is whatever was typed or linked.
        listOf("Popular", "ALL", "Random").forEach(::visitSubreddit)

        assertEquals(emptyList<String>(), recordedNames())

        // A real community whose name merely starts with one of them is still a real community.
        visitSubreddit("popularmechanics")
        assertEquals(listOf("popularmechanics"), recordedNames())
    }

    @Test
    fun `your own profile feed is not a subreddit you visited`() {
        turnTheSettingOn()

        visitSubreddit("u_Alice")
        visitSubreddit("u_alice")

        assertEquals(emptyList<String>(), recordedNames())

        visitSubreddit("u_Bob")
        assertEquals(listOf("u_Bob"), recordedNames())
    }

    @Test
    fun `your own profile is not a user you visited`() {
        turnTheSettingOn()

        visitUser("Alice")
        visitUser("alice")

        assertEquals(emptyList<String>(), recordedNames())

        visitUser("Bob")
        assertEquals(listOf("Bob"), recordedNames())
    }

    @Test
    fun `each insert trims the account's rows of that type back to fifty`() {
        turnTheSettingOn()

        visitSubreddit("Android")
        visitUser("Bob")

        assertEquals(
            listOf(
                Triple(account, RecentlyVisitedType.SUBREDDIT, 50),
                Triple(account, RecentlyVisitedType.USER, 50)
            ),
            dao.trims
        )
    }
}
