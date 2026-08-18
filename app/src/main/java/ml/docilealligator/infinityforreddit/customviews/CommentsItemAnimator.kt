package ml.docilealligator.infinityforreddit.customviews

import androidx.recyclerview.widget.DefaultItemAnimator

/**
 * Item animator for the comment list.
 *
 * Collapsing a comment emits one change (`isExpanded` flipped) plus a range removal (the children)
 * in a single update, and DefaultItemAnimator stages those: moves and changes are posted with a
 * delay of `removeDuration`, and additions wait for `removeDuration + max(moveDuration,
 * changeDuration)`. With the stock 120/250/250 that is 370ms before the rows below a collapsed
 * subtree come back, and collapsing a subtree taller than the screen is the worst case, because
 * everything below the comment is newly laid out and therefore an addition.
 *
 * Both delays are computed from the durations, so shortening the move and change animations
 * shortens the staging with them: additions now start at 240ms instead of 370ms and the whole
 * sequence settles in 360ms instead of 490ms.
 *
 * Every phase is deliberately left animating. Suppressing one to buy back the rest of the time
 * looks worse rather than faster — measured over a collapse, the largest single-frame change goes
 * from ~1.8k RMSE with this animator to ~10.7k with the phases suppressed, i.e. the list jumps in
 * one frame instead of moving. Specifically:
 *
 *  - returning false from `animateAdd` means the rows below a collapsed subtree do not fade in,
 *    they teleport, and the whole visible list changing at once reads as a stutter;
 *  - clearing `supportsChangeAnimations` makes RecyclerView reuse the updated holder and rebind it
 *    in place, so the row's own LayoutTransition (`animateLayoutChanges` in item_comment.xml)
 *    animates the "+N" child-count badge in on a live view and drags the score across with it, and
 *    a row whose view type flips to the fully-collapsed one loses the cross-fade partner that
 *    bridges the two layouts and blinks straight from one to the other.
 */
class CommentsItemAnimator : DefaultItemAnimator() {
    init {
        removeDuration = 120
        moveDuration = 120
        changeDuration = 120
        addDuration = 120
    }
}
