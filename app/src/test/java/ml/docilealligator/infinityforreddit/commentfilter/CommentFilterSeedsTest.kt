package ml.docilealligator.infinityforreddit.commentfilter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * "Add to Comment Filter" appends the comment's author to one comma-separated column, and the
 * screen now writes that straight to the database instead of handing it to a form the user checks.
 * These pin the append: no duplicate a filter would never match twice, no stray comma, and nothing
 * of what the user typed rewritten underneath them.
 */
class CommentFilterSeedsTest {

    private fun filter(excludeUsers: String?) = CommentFilter().apply {
        name = "Test"
        this.excludeUsers = excludeUsers
    }

    @Test
    fun addsToAnEmptyColumn() {
        val commentFilter = filter(null)
        assertTrue(CommentFilterSeeds.addExcludedUser(commentFilter, "spez"))
        assertEquals("spez", commentFilter.excludeUsers)

        val blank = filter("")
        assertTrue(CommentFilterSeeds.addExcludedUser(blank, "spez"))
        assertEquals("spez", blank.excludeUsers)
    }

    @Test
    fun appendsAfterExistingUsers() {
        val commentFilter = filter("edgan,random")
        assertTrue(CommentFilterSeeds.addExcludedUser(commentFilter, "spez"))
        assertEquals("edgan,random,spez", commentFilter.excludeUsers)
    }

    @Test
    fun doesNotDoubleUpTheSeparator() {
        val commentFilter = filter("edgan,")
        assertTrue(CommentFilterSeeds.addExcludedUser(commentFilter, "spez"))
        assertEquals("edgan,spez", commentFilter.excludeUsers)
    }

    /** The column is matched trimmed and case-insensitively, so it is deduplicated that way too. */
    @Test
    fun aUserAlreadyExcludedIsNotAddedAgain() {
        val commentFilter = filter("edgan, SPEZ ,random")
        assertFalse(CommentFilterSeeds.addExcludedUser(commentFilter, "spez"))
        assertEquals("edgan, SPEZ ,random", commentFilter.excludeUsers)
    }

    @Test
    fun whatTheUserTypedIsLeftAlone() {
        val commentFilter = filter("edgan ,  random")
        assertTrue(CommentFilterSeeds.addExcludedUser(commentFilter, " spez "))
        assertEquals("edgan ,  random,spez", commentFilter.excludeUsers)
    }

    @Test
    fun aMissingNameAddsNothing() {
        val commentFilter = filter(null)
        assertFalse(CommentFilterSeeds.addExcludedUser(commentFilter, null))
        assertFalse(CommentFilterSeeds.addExcludedUser(commentFilter, " "))
        assertNull(commentFilter.excludeUsers)
    }
}
