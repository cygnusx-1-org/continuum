package ml.docilealligator.infinityforreddit.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The band boundaries, which are what the ladder is. The threshold is the whole swipe, divided
 * evenly between the slots a surface has -- three in these tests -- and never by how many are
 * filled. So on a row 1000 px wide with a 0.3 threshold the slots sit at 100, 200 and 300 px
 * whether one of them is bound or all three.
 */
class SwipeActionLevelsTest {

    private val width = 1000

    private val upvote = 0
    private val downvote = 1
    private val save = 2
    private val hide = 3

    private fun ladder(
        left: IntArray = intArrayOf(upvote, SwipeActionLevels.NONE, SwipeActionLevels.NONE),
        right: IntArray = intArrayOf(downvote, SwipeActionLevels.NONE, SwipeActionLevels.NONE),
        threshold: Float = 0.3f,
    ) = SwipeActionLevels().apply { configure(left, right, threshold) }

    @Test
    fun `a swipe below the first band is on no level`() {
        val levels = ladder()
        assertEquals(0, levels.levelFor(99f, width))
        assertEquals(0, levels.levelFor(-99f, width))
    }

    @Test
    fun `slots divide the threshold evenly between them`() {
        val levels = ladder(right = intArrayOf(upvote, downvote, hide))
        assertEquals(1, levels.levelFor(101f, width))
        assertEquals(1, levels.levelFor(200f, width))
        assertEquals(2, levels.levelFor(201f, width))
        assertEquals(2, levels.levelFor(300f, width))
        assertEquals(3, levels.levelFor(301f, width))
    }

    @Test
    fun `swiping past the last band stays on the last band`() {
        val levels = ladder(right = intArrayOf(upvote, downvote, hide))
        assertEquals(3, levels.levelFor(5000f, width))
    }

    @Test
    fun `the two directions have their own ladders`() {
        val levels = ladder(
            left = intArrayOf(upvote, save, SwipeActionLevels.NONE),
            right = intArrayOf(downvote, SwipeActionLevels.NONE, SwipeActionLevels.NONE),
        )
        // The slots are at 100, 200 and 300 on both sides. The left has two bound, so 250 is on
        // its second; the right has one, so 250 is past the only band it can reach.
        assertEquals(2, levels.levelFor(-250f, width))
        assertEquals(save, levels.actionFor(-250f, 2))
        assertEquals(1, levels.levelFor(250f, width))
        assertEquals(downvote, levels.actionFor(250f, 1))
    }

    @Test
    fun `an empty level puts everything past it out of reach`() {
        val levels = ladder(right = intArrayOf(upvote, SwipeActionLevels.NONE, hide))
        assertEquals(1, levels.levelFor(5000f, width))
        assertEquals(SwipeActionLevels.NONE, levels.actionFor(5000f, 2))
        assertEquals(SwipeActionLevels.NONE, levels.actionFor(5000f, 3))
    }

    @Test
    fun `an empty first level disables that direction`() {
        val levels = ladder(left = intArrayOf(SwipeActionLevels.NONE, SwipeActionLevels.NONE, SwipeActionLevels.NONE))
        assertEquals(0, levels.levelFor(-5000f, width))
        assertEquals(SwipeActionLevels.NONE, levels.actionFor(-5000f, 1))
        assertEquals(0f, levels.clamp(-5000f, width), 0f)
    }

    @Test
    fun `one action arms where it will still arm once the rest are filled in`() {
        val alone = ladder(right = intArrayOf(upvote, SwipeActionLevels.NONE, SwipeActionLevels.NONE))
        val full = ladder(right = intArrayOf(upvote, downvote, hide))
        // The first slot is the first slot either way: dividing by how many were filled would
        // have put this one at 300 instead.
        assertEquals(0, alone.levelFor(100f, width))
        assertEquals(1, alone.levelFor(101f, width))
        assertEquals(alone.levelFor(101f, width), full.levelFor(101f, width))

        assertEquals(1, alone.levelFor(5000f, width))
        assertEquals(SwipeActionLevels.NONE, alone.actionFor(5000f, 2))
        // The row stops on the band it can reach, with none of a ladder's overshoot past it.
        assertEquals(100f, alone.clamp(5000f, width), 0.001f)
    }

    @Test
    fun `the row stops a little past the last band`() {
        val levels = ladder(right = intArrayOf(upvote, downvote, SwipeActionLevels.NONE))
        assertEquals(225f, levels.clamp(5000f, width), 0.001f)
    }

    @Test
    fun `the last slot sits on the threshold itself`() {
        // 0.4 is the largest the setting offers.
        val levels = ladder(right = intArrayOf(upvote, downvote, hide), threshold = 0.4f)
        assertEquals("just short of it", 2, levels.levelFor(400f, width))
        assertEquals("just past it", 3, levels.levelFor(401f, width))
        // Travel stops there too, plus a ladder's slack.
        assertEquals(425f, levels.clamp(5000f, width), 0.001f)
    }

    @Test
    fun `a band boundary buzzes in both directions of travel`() {
        val levels = ladder(right = intArrayOf(upvote, downvote, hide))
        assertTrue("entering level 1", levels.arm(1, 150f))
        assertFalse("still on level 1", levels.arm(1, 180f))
        assertTrue("entering level 2", levels.arm(2, 250f))
        assertTrue("easing back to level 1", levels.arm(1, 150f))
        assertFalse("dropping out of the ladder", levels.arm(0, 40f))
    }

    @Test
    fun `releasing runs the level that was latched, once`() {
        val levels = ladder(right = intArrayOf(upvote, downvote, hide))
        levels.arm(3, 320f)
        assertEquals(hide, levels.consume())
        assertEquals(SwipeActionLevels.NONE, levels.consume())
    }

    @Test
    fun `coming back from a deeper level runs the level released on`() {
        val levels = ladder(right = intArrayOf(upvote, downvote, hide))
        levels.arm(3, 320f)
        levels.arm(1, 150f)
        assertEquals(upvote, levels.consume())
    }

    @Test
    fun `a cancelled swipe leaves nothing armed`() {
        val levels = ladder(right = intArrayOf(upvote, downvote, hide))
        levels.arm(2, 250f)
        levels.reset()
        assertEquals(SwipeActionLevels.NONE, levels.consume())
    }

    @Test
    fun `reconfiguring clears whatever was armed`() {
        val levels = ladder(right = intArrayOf(upvote, downvote, hide))
        levels.arm(2, 250f)
        levels.configure(
            intArrayOf(upvote, SwipeActionLevels.NONE, SwipeActionLevels.NONE),
            intArrayOf(downvote, SwipeActionLevels.NONE, SwipeActionLevels.NONE), 0.3f)
        assertEquals(SwipeActionLevels.NONE, levels.consume())
    }

    @Test
    fun `a row of no width is on no level`() {
        val levels = ladder()
        assertEquals(0, levels.levelFor(400f, 0))
        assertEquals(0f, levels.clamp(400f, 0), 0f)
    }
}
