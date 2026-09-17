package ml.docilealligator.infinityforreddit.user

import ml.docilealligator.infinityforreddit.subscribeduser.SubscribedUserData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The marker beside an author's name (issue #415). What matters: the three reasons a user is on
 * the list are ranked and only one shows, a name is found however Reddit spelled it, and a
 * snapshot that says the same thing as the last one reports no change — that last one is what
 * keeps a subscription sync from rebinding every row on screen.
 */
class UserMarksTest {

    private fun row(
        name: String,
        followed: Boolean = false,
        saved: Boolean = false,
        favorite: Boolean = false,
    ) = SubscribedUserData(name, "", "account", favorite).apply {
        setFollowed(followed)
        setSaved(saved)
    }

    @Test
    fun `favourite outranks followed and saved`() {
        assertEquals(
            UserMark.FAVORITED,
            UserMarks.markOf(row("alice", followed = true, saved = true, favorite = true))
        )
    }

    @Test
    fun `a favourite that is only saved still shows the heart`() {
        // FavoriteThing records a favourite locally when the user is not followed, so this row is
        // reachable and must not fall through to the bookmark.
        assertEquals(UserMark.FAVORITED, UserMarks.markOf(row("alice", saved = true, favorite = true)))
    }

    @Test
    fun `followed outranks saved`() {
        assertEquals(UserMark.FOLLOWED, UserMarks.markOf(row("alice", followed = true, saved = true)))
    }

    @Test
    fun `saved alone is the bookmark`() {
        assertEquals(UserMark.SAVED, UserMarks.markOf(row("alice", saved = true)))
    }

    @Test
    fun `a row with no reason carries no mark`() {
        assertNull(UserMarks.markOf(row("alice")))
        assertNull(UserMarks.markOf(null))
    }

    @Test
    fun `a name is found however it is spelled`() {
        val marks = UserMarks.from(listOf(row("Alice", followed = true)))

        assertEquals(UserMark.FOLLOWED, marks.of("alice"))
        assertEquals(UserMark.FOLLOWED, marks.of("ALICE"))
        assertEquals(UserMark.FOLLOWED, marks.of("Alice"))
    }

    @Test
    fun `an author who is on no list has no mark`() {
        val marks = UserMarks.from(listOf(row("alice", followed = true)))

        assertNull(marks.of("bob"))
        assertNull(marks.of(null))
        assertNull(marks.of(""))
    }

    @Test
    fun `an empty list is the shared empty snapshot`() {
        assertSame(UserMarks.EMPTY, UserMarks.from(emptyList()))
        assertSame(UserMarks.EMPTY, UserMarks.from(listOf(row("alice"))))
    }

    @Test
    fun `re-reading the same rows changes nothing`() {
        val rows = listOf(row("alice", followed = true), row("bob", saved = true))
        val before = UserMarks.from(rows)

        assertTrue(UserMarks.from(rows).changedFrom(before).isEmpty)
    }

    @Test
    fun `following someone changes only them`() {
        val before = UserMarks.from(listOf(row("alice", followed = true)))
        val after = UserMarks.from(listOf(row("alice", followed = true), row("bob", followed = true)))

        val changes = after.changedFrom(before)

        assertFalse(changes.isEmpty)
        assertTrue(changes.affects("bob"))
        assertFalse(changes.affects("alice"))
    }

    @Test
    fun `unfollowing someone is a change`() {
        val before = UserMarks.from(listOf(row("alice", followed = true)))

        val changes = UserMarks.EMPTY.changedFrom(before)

        assertTrue(changes.affects("alice"))
    }

    @Test
    fun `favouriting someone already followed is a change`() {
        val before = UserMarks.from(listOf(row("alice", followed = true)))
        val after = UserMarks.from(listOf(row("alice", followed = true, favorite = true)))

        assertTrue(after.changedFrom(before).affects("alice"))
    }

    @Test
    fun `saving someone already followed is not`() {
        // The follow already decides the glyph, so the row has nothing new to show.
        val before = UserMarks.from(listOf(row("alice", followed = true)))
        val after = UserMarks.from(listOf(row("alice", followed = true, saved = true)))

        assertTrue(after.changedFrom(before).isEmpty)
    }

    @Test
    fun `a change is matched however the author is spelled`() {
        val changes = UserMarks.from(listOf(row("Alice", followed = true))).changedFrom(UserMarks.EMPTY)

        assertTrue(changes.affects("ALICE"))
        assertFalse(changes.affects(null))
        assertFalse(changes.affects(""))
    }
}
