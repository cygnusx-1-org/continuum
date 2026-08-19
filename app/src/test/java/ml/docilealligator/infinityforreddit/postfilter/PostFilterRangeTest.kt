package ml.docilealligator.infinityforreddit.postfilter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Score and comment limits are two columns behind one `100-5000` box, so the pair has to survive the
 * trip in both directions — including the half-open forms. An upper-only limit is spelled `0-5000`
 * rather than `-5000`, which would read as a negative number, and these pin that a re-save never
 * silently turns "no lower bound" into a real one.
 */
class PostFilterRangeTest {

    @Test
    fun `both bounds round-trip`() {
        assertEquals("100-5000", PostFilterRange.format(100, 5000))
        assertEquals(100 to 5000, PostFilterRange.parse("100-5000"))
    }

    @Test
    fun `an open upper end round-trips`() {
        assertEquals("100-", PostFilterRange.format(100, -1))
        assertEquals(100 to -1, PostFilterRange.parse("100-"))
    }

    @Test
    fun `an upper-only limit is written with both numbers`() {
        // Not "-5000": a leading minus reads as negative five thousand, not "up to five thousand".
        assertEquals("0-5000", PostFilterRange.format(-1, 5000))
        assertEquals(-1 to 5000, PostFilterRange.parse("0-5000"))
    }

    @Test
    fun `a bare leading minus is rejected rather than read as an upper bound`() {
        assertTrue(PostFilterRange.isMissingLowerBound("-5000"))
        assertTrue(PostFilterRange.isMissingLowerBound("  -5000  "))
        // ...and it sets no limit at all, so it cannot quietly filter while the box shows an error.
        assertEquals(-1 to -1, PostFilterRange.parse("-5000"))
    }

    @Test
    fun `a valid range is never flagged as missing its lower bound`() {
        assertFalse(PostFilterRange.isMissingLowerBound("0-5000"))
        assertFalse(PostFilterRange.isMissingLowerBound("100-5000"))
        assertFalse(PostFilterRange.isMissingLowerBound("100-"))
        assertFalse(PostFilterRange.isMissingLowerBound(""))
        // A lone "-" is one keystroke of "100-", so it must not nag mid-typing.
        assertFalse(PostFilterRange.isMissingLowerBound("-"))
    }

    @Test
    fun `no limit is an empty box`() {
        assertEquals("", PostFilterRange.format(-1, -1))
        assertEquals(-1 to -1, PostFilterRange.parse(""))
        assertEquals(-1 to -1, PostFilterRange.parse("   "))
    }

    @Test
    fun `a bare number is a lower bound`() {
        assertEquals(100 to -1, PostFilterRange.parse("100"))
        // ...which is exactly what the half-typed form means too, so the value does not jump around
        // as the user keeps typing.
        assertEquals(PostFilterRange.parse("100"), PostFilterRange.parse("100-"))
    }

    @Test
    fun `bounds that could never restrict anything are dropped`() {
        // isPostAllowed guards every limit with `> 0`, so 0 and negatives were always inert.
        assertEquals(-1 to -1, PostFilterRange.parse("0-0"))
        assertEquals("", PostFilterRange.format(0, 0))
        assertEquals("", PostFilterRange.format(-7, -3))
    }

    @Test
    fun `a partly typed range keeps the end that is already valid`() {
        assertEquals(100 to -1, PostFilterRange.parse("100-x"))
        assertEquals(-1 to 5000, PostFilterRange.parse("x-5000"))
        assertEquals(-1 to -1, PostFilterRange.parse("-"))
    }

    @Test
    fun `surrounding whitespace is ignored`() {
        assertEquals(100 to 5000, PostFilterRange.parse("  100 - 5000  "))
    }
}
