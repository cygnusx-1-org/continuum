package ml.docilealligator.infinityforreddit.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The in-memory listing the Saved screen refines a query against. It is handed to and read from a
 * paging source that keeps running while the next generation starts, so a caller must not be able
 * to reach the list the cache is holding: a snapshot that shares storage with the cache turns an
 * ordinary "filter this list" into a mutation of everyone else's data.
 */
class SavedSearchCacheTest {

    @Test
    fun `a fresh cache holds nothing`() {
        val cache = SavedSearchCache<String>()

        assertFalse(cache.isValid)
        assertNull(cache.snapshot())
    }

    @Test
    fun `invalidating empties it again`() {
        val cache = SavedSearchCache<String>()
        cache.set(listOf("a", "b"))
        assertTrue(cache.isValid)

        cache.invalidate()

        assertFalse(cache.isValid)
        assertNull(cache.snapshot())
    }

    @Test
    fun `a snapshot cannot be used to edit the cache`() {
        val cache = SavedSearchCache<String>()
        cache.set(listOf("a", "b"))

        val snapshot = cache.snapshot()!!
        snapshot.clear()

        assertEquals(listOf("a", "b"), cache.snapshot())
    }

    @Test
    fun `the caller's list cannot be used to edit the cache after storing it`() {
        val cache = SavedSearchCache<String>()
        val caller = mutableListOf("a", "b")

        cache.set(caller)
        caller.add("c")

        assertEquals(listOf("a", "b"), cache.snapshot())
    }

    @Test
    fun `two snapshots do not share storage`() {
        val cache = SavedSearchCache<String>()
        cache.set(listOf("a", "b"))

        val first = cache.snapshot()!!
        val second = cache.snapshot()!!
        first.add("c")

        assertEquals(listOf("a", "b"), second)
    }
}
