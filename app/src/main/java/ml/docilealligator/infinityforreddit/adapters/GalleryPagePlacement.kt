package ml.docilealligator.infinityforreddit.adapters

/**
 * Whether a binding gallery row should move its carousel to the page the post says it is on.
 *
 * Both the feed card and the post-detail header ask this, and they have to agree: they draw the same
 * carousel from the same recorded page, and while the rule lived inline in two binds it drifted --
 * one of them was always wrong. It is a rule rather than a condition because it has been got wrong
 * in both directions, and each time the symptom was the same, a swipe that appeared not to register.
 *
 * A bind is not a rare event. A vote, a save, a post marked read, an autoplay pass and the comment
 * thread arriving all come through `onBindViewHolder`, so this runs while the user is touching the
 * carousel as readily as when they are not.
 */
object GalleryPagePlacement {

    /**
     * @param imagesChanged whether this bind is about to replace the carousel's pictures. That lays
     *   it out from item zero again, so the page has to be re-applied whatever else is true.
     * @param atRightPage whether the carousel is already showing the recorded page.
     * @param touchedByUser whether the user has moved this carousel themselves since it was bound.
     * @param settled whether the carousel is at rest (`SCROLL_STATE_IDLE`).
     */
    @JvmStatic
    fun shouldApplyPage(
        imagesChanged: Boolean,
        atRightPage: Boolean,
        touchedByUser: Boolean,
        settled: Boolean,
    ): Boolean {
        if (imagesChanged) {
            return true
        }
        if (atRightPage) {
            return false
        }
        // Nothing else may move a carousel the user is moving: a swipe travels for a while before it
        // settles, and the page is only recorded once it has, so mid-gesture the recorded page is
        // still the image being left. Applying it there drags the carousel back to the image the
        // finger is pulling away from, which is what made the first swipe of a card do nothing.
        //
        // "Being moved" means the user's gesture, not merely "not at rest". A carousel settles after
        // a programmatic scroll and after a layout too, and one that has never been touched is not
        // being moved by anyone -- it is simply not yet where it has been told to be. Reading that
        // as a gesture is what left a restored gallery sitting on image one.
        return !(touchedByUser && !settled)
    }
}
