package ml.docilealligator.infinityforreddit.videoautoplay

import ml.docilealligator.infinityforreddit.videoautoplay.widget.Container
import kotlin.math.min


class MultiPlayPlayerSelector(
    var simultaneousAutoplayLimit: Int,
    /**
     * Supplies the adapter positions allowed to autoplay in a given container, so that videos and
     * animating gifs draw on one budget instead of a limit each. Returns null for a container the
     * unified budget does not cover -- the nested gallery players, whose order is an index within
     * their own gallery and so cannot be compared against feed positions -- and those fall back to
     * taking the first [simultaneousAutoplayLimit] candidates.
     */
    private val unifiedSelection: UnifiedSelection
) : PlayerSelector {

    fun interface UnifiedSelection {
        fun selectedPositions(container: Container): Set<Int>?
    }

    override fun select(
        container: Container,
        items: List<ToroPlayer?>
    ): Collection<ToroPlayer?> {
        if (simultaneousAutoplayLimit < 0) {
            return items
        }

        unifiedSelection.selectedPositions(container)?.let { allowed ->
            // The gif cards have already been ranked against these players by adapter position, so
            // a slot a gif won is simply absent here and the video below it stays paused.
            return items.filter { it != null && allowed.contains(it.playerOrder) }
        }

        val result: MutableList<ToroPlayer?> = ArrayList()
        val count = min(items.size, simultaneousAutoplayLimit)
        for (i in 0..<count) {
            result.add(items[i])
        }
        return result
    }

    // Don't care about this cuz we don't need to play the videos in reverse order
    override fun reverse(): PlayerSelector {
        return PlayerSelector.DEFAULT_REVERSE;
    }
}
