package ml.docilealligator.infinityforreddit.utils

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The guard the Customize Bottom App Bar screen puts between a stored option index and the array it
 * indexes. The stored value outlives the array: options were added and removed across releases, so
 * a preference written by an older build can name a slot that no longer exists, and the screen
 * indexes the array with the result without checking it again.
 */
class FixIndexOutOfBoundsTest {

    private val options = arrayOf("Home", "Subscriptions", "Inbox", "Profile")

    @Test
    fun `an index the array does not have is pulled back to the last one`() {
        assertEquals(3, Utils.fixIndexOutOfBounds(options, 4))
        assertEquals(3, Utils.fixIndexOutOfBounds(options, 99))
    }

    @Test
    fun `an index the array does have is left alone`() {
        assertEquals(0, Utils.fixIndexOutOfBounds(options, 0))
        assertEquals(2, Utils.fixIndexOutOfBounds(options, 2))
        assertEquals(3, Utils.fixIndexOutOfBounds(options, 3))
    }

    @Test
    fun `the fallback index is used instead when one is given`() {
        assertEquals(1, Utils.fixIndexOutOfBoundsUsingPredetermined(options, 4, 1))
        assertEquals(1, Utils.fixIndexOutOfBoundsUsingPredetermined(options, 99, 1))
        assertEquals(2, Utils.fixIndexOutOfBoundsUsingPredetermined(options, 2, 1))
    }
}
