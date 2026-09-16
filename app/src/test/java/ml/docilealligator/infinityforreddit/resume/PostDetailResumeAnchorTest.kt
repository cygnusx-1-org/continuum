package ml.docilealligator.infinityforreddit.resume

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Where the post-detail screen records the user as being in a thread.
 *
 * The case that matters is the one the encoding could not express: a thread is written as a comment
 * plus an offset, and the row above the comments is the post, which is not one. That used to be
 * recorded as the first comment at offset zero, so closing the screen while reading the post
 * reopened it with the post scrolled off above -- on a gallery post, most of the screen.
 *
 * No Robolectric here on purpose: this is the rule, and the rule is what a test can hold still.
 * Standing up the screen itself would mean a fragment, a ConcatAdapter, a fetched thread and the
 * Dagger graph behind them, which is how this went unasserted in the first place.
 */
class PostDetailResumeAnchorTest {

    @Test
    fun `a row that is not a comment is recorded as being above them`() {
        // getLocalPosition answers -1 for the post, its media and any header above the thread.
        val anchor = PostDetailResumeAnchor.forTopRow(
            localPosition = -1, commentCount = 25, anchorPosition = 0, anchorOffset = -120)

        assertTrue(anchor.aboveComments)
        assertEquals(0, anchor.position)
    }

    @Test
    fun `being above the comments keeps the real offset rather than snapping to zero`() {
        // The regression itself. Forcing offset 0 -- and the first comment with it -- is what put
        // the reader below the post they had been reading. A reader part way down a long post is
        // entitled to come back to the same place, not to the top of it.
        val anchor = PostDetailResumeAnchor.forTopRow(
            localPosition = -1, commentCount = 25, anchorPosition = 0, anchorOffset = -840)

        assertEquals(-840, anchor.offset)
        assertEquals(-1, anchor.commentIndex)
    }

    @Test
    fun `a comment row is recorded as that comment`() {
        val anchor = PostDetailResumeAnchor.forTopRow(
            localPosition = 7, commentCount = 25, anchorPosition = 9, anchorOffset = -32)

        assertFalse(anchor.aboveComments)
        assertEquals(7, anchor.commentIndex)
        assertEquals(-32, anchor.offset)
        assertEquals(9, anchor.position)
    }

    @Test
    fun `the first comment is still a comment`() {
        // The boundary the old code collided with: index 0 is a real anchor, and has to stay
        // distinguishable from "above the comments", which is what index 0 used to stand in for.
        val anchor = PostDetailResumeAnchor.forTopRow(
            localPosition = 0, commentCount = 25, anchorPosition = 1, anchorOffset = 0)

        assertFalse(anchor.aboveComments)
        assertEquals(0, anchor.commentIndex)
    }

    @Test
    fun `an index past the end of a shrunken thread is not a comment`() {
        // Threads lose comments between sessions -- deleted, or collapsed out by a different sort.
        // An index that no longer names one has to fall back rather than read off the end.
        val anchor = PostDetailResumeAnchor.forTopRow(
            localPosition = 40, commentCount = 25, anchorPosition = 42, anchorOffset = -16)

        assertTrue(anchor.aboveComments)
        assertEquals(-1, anchor.commentIndex)
    }

    @Test
    fun `a thread with no comments has nothing to anchor to`() {
        val anchor = PostDetailResumeAnchor.forTopRow(
            localPosition = 0, commentCount = 0, anchorPosition = 0, anchorOffset = 0)

        assertTrue(anchor.aboveComments)
    }
}
