package ml.docilealligator.infinityforreddit.customviews

import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.RecyclerView

/**
 * [DefaultItemAnimator] with one extra rule: a change notified with [PAYLOAD_REBIND_IN_PLACE]
 * rebinds the holder already on screen instead of cross-fading a replacement in.
 *
 * RecyclerView decides that in `Recycler.scrapView`, by asking the item animator. With change
 * animations on -- which the comment list wants, see [CommentsItemAnimator] -- an updated holder
 * goes to the changed scrap: a second holder is bound for the new state, and the first view is
 * removed once the animation ends. For a row that owns a player that removal is fatal, because
 * `Container.onChildDetachedFromWindow` releases the player along with the view, so the clip stops,
 * rebuffers and comes back sitting on its preview frame (issue #423). Reusing the holder keeps the
 * view, and the player attached to it, exactly where it was.
 *
 * The payload is what picks the rebinds this applies to, rather than the row: the post row is kept
 * in place when only the post's own state moved -- a vote, a save, an author tag -- and left to the
 * ordinary path when the bind has to build a different row, such as a video URL that has just
 * resolved and needs the Container to pick the player up again.
 *
 * Only a *change* consults this. `notifyDataSetChanged` marks every holder invalid, and an invalid
 * holder goes back to the pool before the animator is ever asked, which is why the post adapter
 * notifies its one row rather than the whole data set.
 */
open class RebindInPlaceItemAnimator : DefaultItemAnimator() {

    override fun canReuseUpdatedViewHolder(
        viewHolder: RecyclerView.ViewHolder,
        payloads: MutableList<Any>
    ): Boolean {
        return payloads.contains(PAYLOAD_REBIND_IN_PLACE) ||
                super.canReuseUpdatedViewHolder(viewHolder, payloads)
    }

    companion object {
        /** Pass as the payload of `notifyItemChanged` to keep the bound holder. */
        @JvmField
        val PAYLOAD_REBIND_IN_PLACE: Any = Any()
    }
}
