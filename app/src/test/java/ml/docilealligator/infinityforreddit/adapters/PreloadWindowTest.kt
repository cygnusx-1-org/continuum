package ml.docilealligator.infinityforreddit.adapters

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rows warmed ahead of the scroll: nearest first, in the direction of travel, never the rows
 * already on screen, and never a position past the posts.
 */
class PreloadWindowTest {

    @Test
    fun scrollingDownWarmsTwoScreensBelowThenAShortRunAbove() {
        // Eight rows on screen, so sixteen ahead.
        val positions = PreloadWindow.positions(10, 17, 100, scrollingUp = false)

        assertEquals((18..33).toList() + listOf(9, 8, 7, 6), positions)
    }

    @Test
    fun scrollingUpWarmsAboveFirstAndStopsAtTheTop() {
        val positions = PreloadWindow.positions(10, 17, 100, scrollingUp = true)

        assertEquals((9 downTo 0).toList() + (18..21).toList(), positions)
    }

    @Test
    fun rowsOnScreenAreNeverWarmed() {
        val positions = PreloadWindow.positions(40, 47, 100, scrollingUp = false)

        assertTrue(positions.none { it in 40..47 })
    }

    @Test
    fun theWindowStopsAtTheLastPost() {
        val positions = PreloadWindow.positions(90, 97, 100, scrollingUp = false)

        assertEquals(listOf(98, 99, 89, 88, 87, 86), positions)
    }

    @Test
    fun theFooterPastThePostsIsNotAPositionToWarm() {
        // The load-state footer is position 100 of a 100-post feed.
        val positions = PreloadWindow.positions(94, 100, 100, scrollingUp = false)

        assertTrue(positions.all { it in 0 until 100 })
        assertTrue(positions.none { it in 94..99 })
    }

    @Test
    fun aFewRowsOnScreenStillWarmTheMinimumAhead() {
        val positions = PreloadWindow.positions(0, 1, 100, scrollingUp = false)

        assertEquals((2 until 2 + PreloadWindow.MIN_AHEAD).toList(), positions)
    }

    @Test
    fun aScreenFullOfRowsIsCappedAhead() {
        // Thirty rows on screen, as in a tablet's staggered grid: two screens would be sixty.
        val positions = PreloadWindow.positions(0, 29, 500, scrollingUp = false)

        assertEquals((30 until 30 + PreloadWindow.MAX_AHEAD).toList(), positions)
    }

    @Test
    fun nothingToWarmWithoutPostsOrWithoutAVisibleRow() {
        assertEquals(emptyList<Int>(), PreloadWindow.positions(0, 5, 0, scrollingUp = false))
        assertEquals(emptyList<Int>(), PreloadWindow.positions(-1, -1, 100, scrollingUp = false))
    }
}
