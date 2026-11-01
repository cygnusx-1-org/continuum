package ml.docilealligator.infinityforreddit.comment

import androidx.test.core.app.ApplicationProvider
import ml.docilealligator.infinityforreddit.RedditDataRoomDatabase
import ml.docilealligator.infinityforreddit.TestInfinity
import ml.docilealligator.infinityforreddit.account.Account
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Saving a comment while logged out is a row on the device and nothing else, and a thread fetched
 * afterwards -- which Reddit reports as all unsaved to an anonymous request -- has to show it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestInfinity::class)
class AnonymousSavedCommentsTest {
    private lateinit var database: RedditDataRoomDatabase

    @Before
    fun setUp() {
        database = RedditDataRoomDatabase.createInMemoryForTest(ApplicationProvider.getApplicationContext())
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun comment(id: String, depth: Int = 0, saved: Boolean = false): Comment = Comment(
        id, "t1_$id", "someone", "t2_author", "", "", null, null,
        0L, "body", "body",
        "link", "subreddit", "t3_link", 0,
        0, false, "", "/r/subreddit/comments/link/_/$id/",
        depth, false, false,
        false, saved, false, false, false,
        false, 0L, null, false, false,
        0L, null
    )

    private fun savedIds(): Set<String> =
        database.anonymousSavedCommentDao().getAllIds(Account.ANONYMOUS_ACCOUNT).toSet()

    /** The write is on a background thread; wait for it rather than for a fixed time. */
    private fun awaitSavedIds(expected: Set<String>) {
        val deadline = System.currentTimeMillis() + 5_000
        while (savedIds() != expected && System.currentTimeMillis() < deadline) {
            Thread.sleep(10)
        }
        assertEquals(expected, savedIds())
    }

    @Test
    fun `toggling saves on the device and toggling again unsaves`() {
        val comment = comment("abc")

        assertTrue(AnonymousSavedComments.toggle(database, Account.ANONYMOUS_ACCOUNT, comment))
        assertTrue(comment.isSaved)
        awaitSavedIds(setOf("abc"))
        val row = database.anonymousSavedCommentDao().getPage(Account.ANONYMOUS_ACCOUNT, null).single()
        assertEquals("link", row.linkId)

        assertFalse(AnonymousSavedComments.toggle(database, Account.ANONYMOUS_ACCOUNT, comment))
        assertFalse(comment.isSaved)
        awaitSavedIds(emptySet())
    }

    @Test
    fun `saved comments are marked however deep in the thread they are`() {
        val dao = database.anonymousSavedCommentDao()
        dao.insert(AnonymousSavedComment(Account.ANONYMOUS_ACCOUNT, "reply", "link", 1L))
        dao.insert(AnonymousSavedComment(Account.ANONYMOUS_ACCOUNT, "deep", "link", 2L))
        val top = comment("top")
        val reply = comment("reply", depth = 1)
        val deep = comment("deep", depth = 2)
        val other = comment("other")
        reply.addChild(deep, 0)
        top.addChild(reply, 0)
        top.addChild(Comment("t1_top", 1, Comment.PLACEHOLDER_LOAD_MORE_COMMENTS), 1)

        AnonymousSavedComments.markSaved(database, Account.ANONYMOUS_ACCOUNT, listOf(top, other))

        assertFalse(top.isSaved)
        assertTrue(reply.isSaved)
        assertTrue(deep.isSaved)
        assertFalse(other.isSaved)
    }

    @Test
    fun `pages run newest first and continue below the last one`() {
        val dao = database.anonymousSavedCommentDao()
        for (i in 1..30) {
            dao.insert(AnonymousSavedComment(Account.ANONYMOUS_ACCOUNT, "c$i", "link", i.toLong()))
        }

        val first = dao.getPage(Account.ANONYMOUS_ACCOUNT, null)
        val second = dao.getPage(Account.ANONYMOUS_ACCOUNT, first.last().time)

        assertEquals(25, first.size)
        assertEquals("c30", first.first().id)
        assertEquals((5 downTo 1).map { "c$it" }, second.map { it.id })
    }
}
