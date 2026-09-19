package ml.docilealligator.infinityforreddit.utils

import kotlin.math.abs
import kotlin.math.min

/**
 * Which of a swipe direction's actions the finger is currently over.
 *
 * The `threshold` setting is how far a swipe travels in total, and a surface's slots divide that
 * distance evenly: on posts, which have four, they sit at a quarter, a half, three quarters and
 * all of it. The division is by how many slots there *are*, never by how many are filled, so a
 * direction with one action bound arms at the same distance it will still arm at after three
 * more are added. Filling a slot in never moves the ones before it.
 *
 * Each band is bound to its own action, so the distance travelled is what picks between them,
 * and the action only runs when the finger lifts: the user can swipe past a level and come back
 * without triggering it.
 *
 * How many slots a surface has is the caller's: [configure] takes one entry per slot, so posts
 * pass four and comments three. Everything past the first starts empty, and an empty slot ends
 * the ladder there. No switch turns the ladder on; filling a slot in is what does.
 *
 * Pure geometry and latching, with no Android types, so the band boundaries are unit-testable.
 * [ml.docilealligator.infinityforreddit.customviews.SwipeActionPainter] adds the colours, the
 * icons and the haptic.
 */
class SwipeActionLevels {

    /** Each level of a left swipe, deepest last; [NONE] for a level the user left empty. */
    private var leftLevels = intArrayOf(NONE)

    /** Each level of a right swipe, deepest last; [NONE] for a level the user left empty. */
    private var rightLevels = intArrayOf(NONE)

    /** How many bands this surface offers: four on posts, three on comments. */
    private var maxLevels = 1

    /**
     * Fraction of the row's width between one slot and the next: the whole swipe divided by how
     * many slots this surface has, whatever is bound to them.
     */
    private var bandFraction = DEFAULT_THRESHOLD

    /** The deepest band the finger has reached since the drag began; 0 before the first. */
    private var armedLevel = 0

    /** The action [armedLevel] resolved to, held so releasing needs no direction of its own. */
    private var armedAction = NONE

    fun configure(leftLevels: IntArray, rightLevels: IntArray, baseThreshold: Float) {
        maxLevels = maxOf(leftLevels.size, rightLevels.size).coerceIn(1, MAX_LEVELS)
        // Padded with NONE rather than copyOf's zero, which is Upvote.
        this.leftLevels = IntArray(maxLevels) { i -> leftLevels.getOrElse(i) { NONE } }
        this.rightLevels = IntArray(maxLevels) { i -> rightLevels.getOrElse(i) { NONE } }
        val threshold = if (baseThreshold > 0f) baseThreshold else DEFAULT_THRESHOLD
        this.bandFraction = threshold / maxLevels
        reset()
    }

    /** The band the finger is over right now: 0 when it has not reached the first one yet. */
    fun levelFor(dX: Float, rowWidth: Int): Int {
        if (rowWidth <= 0) return 0
        val reachable = reachableLevels(dX)
        if (reachable == 0) return 0
        val travelled = abs(dX)
        var level = 0
        for (n in 1..reachable) {
            // Strictly greater, as the single-level swipe has always tested it.
            if (travelled > n * bandFraction * rowWidth) level = n
        }
        return level
    }

    /** The action bound to [level] in the direction [dX] points, or [NONE]. */
    fun actionFor(dX: Float, level: Int): Int {
        if (level < 1 || level > maxLevels) return NONE
        val levels = levelsFor(dX)
        // An empty level collapses the ladder: everything past it is out of reach.
        for (i in 0 until level) {
            if (levels[i] == NONE) return NONE
        }
        return levels[level - 1]
    }

    /**
     * How far the row itself may move for a swipe of this size.
     *
     * The row stops on the deepest band that has an action bound to it -- not on the threshold,
     * which is where the last *slot* is whether or not anything fills it. A ladder gets a little
     * overshoot past its last band so that action does not sit against a hard stop; a single
     * band gets none, which is how the one-level swipe has always felt. Nothing ever travels far
     * enough for the framework to decide the row was dismissed.
     */
    fun clamp(dX: Float, rowWidth: Int): Float {
        if (rowWidth <= 0) return 0f
        val reachable = reachableLevels(dX)
        if (reachable == 0) return 0f
        val fraction = if (reachable > 1) {
            min(reachable * bandFraction + OVERSHOOT_FRACTION, MAX_TRAVEL_FRACTION)
        } else {
            bandFraction
        }
        val limit = fraction * rowWidth
        return dX.coerceIn(-limit, limit)
    }

    /**
     * Records the band the finger is over. Returns true when the armed level changed to one that
     * should buzz -- which is every change except falling back out of the first band, so pushing
     * deeper and easing back both give one pulse per boundary.
     */
    fun arm(level: Int, dX: Float): Boolean {
        if (level == armedLevel) return false
        armedLevel = level
        armedAction = actionFor(dX, level)
        return level >= 1
    }

    /** The action the release should run, clearing the latch so it runs exactly once. */
    fun consume(): Int {
        val action = if (armedLevel >= 1) armedAction else NONE
        reset()
        return action
    }

    fun reset() {
        armedLevel = 0
        armedAction = NONE
    }

    private fun levelsFor(dX: Float): IntArray = if (dX > 0) rightLevels else leftLevels

    /**
     * How many of this direction's slots the user actually filled in, counting from the first.
     * An empty slot collapses the ladder, so everything past it is out of reach. This says how
     * far a swipe can get, never how far apart the bands are -- that is fixed by the slot count.
     */
    private fun reachableLevels(dX: Float): Int {
        val levels = levelsFor(dX)
        if (levels[0] == NONE) return 0
        var reachable = 1
        for (n in 2..maxLevels) {
            if (levels[n - 1] == NONE) break
            reachable = n
        }
        return reachable
    }

    companion object {
        /** A level the user left empty, and the answer when nothing is bound. */
        const val NONE = -1

        /** The most bands any surface offers; posts use all of them, comments three. */
        const val MAX_LEVELS = 4

        /** The `swipe_action_threshold` default, and the fallback for an unparseable one. */
        private const val DEFAULT_THRESHOLD = 0.3f

        /** Slack past the deepest band, so the last action is not against a hard stop. */
        private const val OVERSHOOT_FRACTION = 0.025f

        /** No band, and no travel, past this much of the row -- a finger cannot reach it. */
        private const val MAX_TRAVEL_FRACTION = 0.95f
    }
}
