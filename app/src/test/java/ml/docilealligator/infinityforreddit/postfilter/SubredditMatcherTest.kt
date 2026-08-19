package ml.docilealligator.infinityforreddit.postfilter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The wildcard forms replaced two global Settings toggles whose 5- and 6-character floors existed
 * only because those toggles reinterpreted every stored term at once. The mode now lives in the
 * term, so these pin the encoding that makes that possible — and the collateral damage that
 * motivated scoping the whole feature to one feed.
 */
class SubredditMatcherTest {

    @Test
    fun `a term with no asterisk is exact`() {
        val (mode, bare) = SubredditMatcher.parse("politics")
        assertEquals(SubredditMatchMode.EXACT, mode)
        assertEquals("politics", bare)
    }

    @Test
    fun `asterisk position picks the mode`() {
        assertEquals(SubredditMatchMode.PREFIX, SubredditMatcher.modeOf("politics*"))
        assertEquals(SubredditMatchMode.SUFFIX, SubredditMatcher.modeOf("*memes"))
        assertEquals(SubredditMatchMode.CONTAINS, SubredditMatcher.modeOf("*india*"))
    }

    @Test
    fun `format and parse round-trip every mode`() {
        for (mode in SubredditMatchMode.entries) {
            val stored = SubredditMatcher.format(mode, "linux")
            val (parsedMode, parsedBare) = SubredditMatcher.parse(stored)
            assertEquals(mode, parsedMode)
            assertEquals("linux", parsedBare)
        }
    }

    @Test
    fun `a lone asterisk has no term and matches nothing`() {
        assertEquals("", SubredditMatcher.parse("*").bare)
        assertFalse(SubredditMatcher.matches("anything", "*"))
        assertFalse(SubredditMatcher.matches("anything", "**"))
    }

    @Test
    fun `exact matching ignores case and matches only the whole name`() {
        assertTrue(SubredditMatcher.matches("Politics", "politics"))
        assertFalse(SubredditMatcher.matches("politicshumor", "politics"))
    }

    @Test
    fun `prefix suffix and contains match where expected`() {
        assertTrue(SubredditMatcher.matches("androiddev", "android*"))
        assertFalse(SubredditMatcher.matches("lineageandroid", "android*"))

        assertTrue(SubredditMatcher.matches("dankmemes", "*memes"))
        assertFalse(SubredditMatcher.matches("memeeconomy", "*memes"))

        assertTrue(SubredditMatcher.matches("DankIndiaMemes", "*india*"))
        assertTrue(SubredditMatcher.matches("IndiaSpeaks", "*india*"))
    }

    @Test
    fun `a term shorter than the floor never matches, even if it reached storage`() {
        // An imported backup or a hand-edited database can hold a term the Add Rule sheet would have
        // refused. Failing closed keeps that from hiding a large slice of a feed.
        assertEquals(3, SubredditMatcher.MIN_WILDCARD_LENGTH)
        assertFalse(SubredditMatcher.matches("catpictures", "ca*"))
        assertFalse(SubredditMatcher.matches("anything", "*a*"))
        // Exact has no floor: naming one subreddit is unambiguous however short the name.
        assertTrue(SubredditMatcher.matches("ca", "ca"))
    }

    @Test
    fun `isLongEnough gates only the wildcard modes`() {
        assertTrue(SubredditMatcher.isLongEnough(SubredditMatchMode.EXACT, "ca"))
        assertFalse(SubredditMatcher.isLongEnough(SubredditMatchMode.CONTAINS, "ca"))
        assertTrue(SubredditMatcher.isLongEnough(SubredditMatchMode.CONTAINS, "irl"))
    }

    @Test
    fun `the collateral that scoping exists to contain`() {
        // Every one of these is a real subreddit an unsuspecting user would not predict. They are
        // why wildcard terms run on one feed only -- see PostFilterTest.
        assertTrue(SubredditMatcher.matches("airlines", "*irl*"))
        assertTrue(SubredditMatcher.matches("Hairloss", "*irl*"))
        assertTrue(SubredditMatcher.matches("Catholicism", "cat*"))
        assertTrue(SubredditMatcher.matches("CatastrophicFailure", "cat*"))
        assertTrue(SubredditMatcher.matches("memento", "*meme*"))
    }

    @Test
    fun `isWildcard reports what the mode implies`() {
        assertFalse(SubredditMatcher.isWildcard("politics"))
        assertTrue(SubredditMatcher.isWildcard("*politics"))
        assertFalse(SubredditMatchMode.EXACT.isWildcard)
        assertTrue(SubredditMatchMode.PREFIX.isWildcard)
    }
}
