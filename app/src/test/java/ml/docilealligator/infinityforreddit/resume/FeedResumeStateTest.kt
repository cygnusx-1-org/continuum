package ml.docilealligator.infinityforreddit.resume

import android.os.Bundle
import ml.docilealligator.infinityforreddit.TestInfinity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The feed record travels three hops -- a screen writes it into its snapshot, reads it back on the
 * next launch, and hands it down to a fragment as arguments -- using the same keys throughout. What
 * is worth pinning down is the two ways it deliberately refuses to travel: it is not written when
 * there is no anchor to record, and it is handed on only once.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestInfinity::class)
class FeedResumeStateTest {

    private fun anchor(position: Int, offset: Int) =
        ScrollAnchor.Anchor(position, offset, position, offset)

    @Test
    fun `a record survives being written and read`() {
        val out = Bundle()
        assertTrue(FeedResumeState.capture(out, "alice.2|pics", anchor(17, -467), "t3_abc", 100))

        val read = FeedResumeState().apply { read(out) }

        assertEquals("alice.2|pics", read.feedKey)
        assertEquals("t3_abc", read.anchorFullname)
        assertEquals(17, read.anchorPosition)
        assertEquals(-467, read.anchorOffset)
        assertEquals(100, read.expectedCount)
        assertTrue(read.isPending())
    }

    @Test
    fun `a negative offset survives`() {
        // The common case, not an edge one: the topmost row usually starts above the viewport, so a
        // record that could not carry a negative offset would be wrong nearly every time.
        val out = Bundle()
        FeedResumeState.capture(out, "feed", anchor(4, -1065), "t3_abc", 40)

        assertEquals(-1065, FeedResumeState().apply { read(out) }.anchorOffset)
    }

    @Test
    fun `an invalid anchor writes nothing at all`() {
        // Nothing is better than the listing name on its own: a record naming a feed but unable to
        // say where in it the user was would reopen that feed at the top, and writing it would
        // overwrite a good record from a moment ago.
        val out = Bundle()
        val invalid = ScrollAnchor.Anchor(
            ScrollAnchor.NO_POSITION, 0, ScrollAnchor.NO_POSITION, 0)

        assertFalse(FeedResumeState.capture(out, "feed", invalid, "t3_abc", 100))
        assertTrue(out.isEmpty)
    }

    @Test
    fun `a bundle with no record leaves the reader empty`() {
        val read = FeedResumeState().apply { read(Bundle()) }

        assertNull(read.feedKey)
        assertFalse(read.isPending())
    }

    @Test
    fun `a record with no feed key writes nothing`() {
        val out = Bundle()
        FeedResumeState().writeTo(out)

        assertTrue(out.isEmpty)
    }

    @Test
    fun `applyTo hands the record on exactly once`() {
        // A pager builds its neighbouring page as well as the current one. Without the one-shot,
        // rebuilding the adapter hands the same restore to a second fragment and scrolls a feed the
        // user never left.
        val out = Bundle()
        FeedResumeState.capture(out, "feed", anchor(3, -12), "t3_abc", 20)
        val record = FeedResumeState().apply { read(out) }

        val first = Bundle()
        record.applyTo(first)
        val second = Bundle()
        record.applyTo(second)

        assertEquals("feed", first.getString(FeedResumeState.KEY_FEED))
        assertTrue("the second fragment must get nothing", second.isEmpty)
        assertFalse(record.isPending())
    }

    @Test
    fun `clearFrom removes every key the record wrote`() {
        // A view rebuilt without its fragment -- a pager detaching and reattaching a page -- reads
        // the arguments again, and would restore a second time if anything were left in them.
        val args = Bundle()
        FeedResumeState.capture(args, "feed", anchor(3, -12), "t3_abc", 20)
        args.putString("unrelated", "kept")

        FeedResumeState.clearFrom(args)

        assertFalse(FeedResumeState().apply { read(args) }.isPending())
        assertEquals("kept", args.getString("unrelated"))
    }

    @Test
    fun `a null anchor fullname still records the position`() {
        // The adapter can be holding a placeholder where the anchor is. The row is a worse key than
        // the fullname, but it is not nothing, and it is what the restore falls back to anyway.
        val out = Bundle()
        assertTrue(FeedResumeState.capture(out, "feed", anchor(9, 0), null, 30))

        val read = FeedResumeState().apply { read(out) }

        assertNull(read.anchorFullname)
        assertEquals(9, read.anchorPosition)
    }
}
