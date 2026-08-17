package ml.docilealligator.infinityforreddit.postfilter

/**
 * Converts between [PostFilter]'s separate min/max columns and the single `100-5000` range box the
 * Customize Post Filter screen shows for score and comment count.
 *
 * Only positive bounds mean anything: [PostFilter.isPostAllowed] guards every limit with `> 0`, so a
 * zero or negative bound has never restricted anything, and `-1` is the stored "no limit". An
 * upper-only limit is therefore written with both numbers, `0-5000`, rather than as a bare `-5000`,
 * which reads as negative five thousand instead of "up to five thousand".
 */
object PostFilterRange {

    /** The stored value for "no bound". */
    const val NO_LIMIT = -1

    /**
     * Renders a stored pair as the user types it: `100-5000`, `100-` (no upper bound), `0-5000` (no
     * lower bound), or empty when neither end is set.
     */
    @JvmStatic
    fun format(min: Int, max: Int): String {
        val hasMin = min > 0
        val hasMax = max > 0
        return when {
            hasMin && hasMax -> "$min-$max"
            hasMin -> "$min-"
            // Both numbers, because a leading "-" reads as a negative number.
            hasMax -> "0-$max"
            else -> ""
        }
    }

    /**
     * Parses a range box back to a (min, max) pair, using [NO_LIMIT] for an end the user left open.
     * A bare number is a lower bound, so "100" and "100-" mean the same thing — that is the way
     * these filters are almost always used ("hide anything below 100"), and it keeps a half-typed
     * range from silently meaning something else.
     *
     * Anything unparseable leaves that end open rather than rejecting the input, so a partly typed
     * range never throws away the end that is already valid. A leading `-` is not an open lower end
     * — see [isMissingLowerBound] — so it yields no limit at all rather than quietly becoming one.
     */
    @JvmStatic
    fun parse(text: String): Pair<Int, Int> {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || trimmed.startsWith("-")) {
            return NO_LIMIT to NO_LIMIT
        }
        val separator = trimmed.indexOf('-')
        if (separator < 0) {
            return bound(trimmed) to NO_LIMIT
        }
        val minPart = trimmed.substring(0, separator)
        val maxPart = trimmed.substring(separator + 1)
        return bound(minPart) to bound(maxPart)
    }

    /**
     * True when the user wrote an upper bound as a bare `-5000`. That has to be `0-5000` instead, so
     * the screen asks for the missing number rather than reading it as negative five thousand.
     *
     * A lone `-` is not flagged: it is what a half-typed `100-` looks like for one keystroke.
     */
    @JvmStatic
    fun isMissingLowerBound(text: String): Boolean {
        val trimmed = text.trim()
        return trimmed.startsWith("-") && (trimmed.drop(1).trim().toIntOrNull() ?: 0) > 0
    }

    private fun bound(part: String): Int {
        val value = part.trim().toIntOrNull() ?: return NO_LIMIT
        return if (value > 0) value else NO_LIMIT
    }
}
