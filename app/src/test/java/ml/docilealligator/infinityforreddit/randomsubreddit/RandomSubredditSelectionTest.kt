package ml.docilealligator.infinityforreddit.randomsubreddit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The draw behind "Random Subscribed Subreddit".
 *
 * Unlike the two list-backed flavours, nothing downstream re-checks this pick: whatever comes out
 * goes straight to `ViewSubredditDetailActivity`, so a blank or null name that survives the filter
 * becomes a visit to `r/` or `r/null` rather than a caught error. That makes the filter, and its
 * order relative to the draw, the whole of the correctness here.
 *
 * Pure JVM on purpose -- no Room, no Robolectric. The function takes the names, not the DAO.
 */
class RandomSubredditSelectionTest {

    private fun select(vararg names: String?) =
        RandomSubredditRepository.selectRandomName(names.toList())

    @Test
    fun noSubscriptionsYieldsNothingToPick() {
        // An account with nothing subscribed. A legitimate outcome on a healthy device, which the
        // caller turns into the "try again later" toast rather than an error.
        assertNull(RandomSubredditRepository.selectRandomName(emptyList()))
    }

    @Test
    fun aSingleSubscriptionIsAlwaysTheOneReturned() {
        repeat(20) { assertEquals("pics", select("pics")) }
    }

    @Test
    fun namesThatCouldNotBeOpenedAreDropped() {
        // r/null and r/ are what these become if they reach the destination.
        assertNull(select(null))
        assertNull(select(""))
        assertNull(select("   "))
        assertNull(select(null, "", "  ", null))
    }

    @Test
    fun theOneUsableNameAmongJunkIsFoundEveryTime() {
        // Filtering has to happen before the draw. Drawing first and discarding afterwards would
        // report "nothing to pick" most of the time here, with a perfectly good name in the list.
        repeat(50) { assertEquals("AskReddit", select(null, "", "AskReddit", "   ", null)) }
    }

    @Test
    fun surroundingWhitespaceIsTrimmedRatherThanCarriedIntoTheUrl() {
        assertEquals("AskReddit", select("  AskReddit\n"))
    }

    @Test
    fun everyNameIsReachableByTheDraw() {
        // A distribution smoke check: an off-by-one in the bound would strand the first or last
        // name permanently, which no single-draw assertion can see.
        val names = listOf("pics", "AskReddit", "worldnews", "science")
        val seen = HashSet<String>()
        repeat(400) { seen.add(RandomSubredditRepository.selectRandomName(names)!!) }

        assertEquals(names.toSet(), seen)
        assertTrue("the draw must never invent a name", names.containsAll(seen))
    }
}
