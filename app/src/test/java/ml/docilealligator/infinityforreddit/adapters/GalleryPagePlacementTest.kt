package ml.docilealligator.infinityforreddit.adapters

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * When a binding gallery row may move its carousel.
 *
 * Both the feed card and the post-detail header bind through this, and a bind is not rare: a vote, a
 * save, a post marked read, an autoplay pass and the comment thread arriving all reach
 * `onBindViewHolder`. So it runs while the user is mid-swipe as readily as when the row is idle, and
 * the rule has been wrong in both directions -- each time showing up as a swipe that did not seem to
 * register, and each time for the opposite reason.
 *
 * No Robolectric: this is the rule, and a test that stood up two RecyclerView adapters, Glide and a
 * touch stream to re-derive it would be the reason it was never pinned in the first place.
 */
class GalleryPagePlacementTest {

    @Test
    fun `a carousel the user is dragging is left alone`() {
        // The first regression. A swipe moves the carousel long before it settles, and the page is
        // only recorded once it has -- so mid-gesture the recorded page is still the image being
        // left, and applying it drags the carousel back to it. The first swipe of a card therefore
        // did nothing, and only came right once some completed swipe had recorded a new page.
        assertFalse(
            GalleryPagePlacement.shouldApplyPage(
                imagesChanged = false, atRightPage = false, touchedByUser = true, settled = false))
    }

    @Test
    fun `a carousel nobody has touched is placed even while it is moving`() {
        // The opposite regression, and the subtler one. A carousel settles after a programmatic
        // scroll and after a layout too, so "not at rest" is not the same as "under a finger". Read
        // as the user's gesture, this case skipped the placement and left a restored gallery sitting
        // on image one -- which is the same symptom as the bug above, from the opposite cause.
        assertTrue(
            GalleryPagePlacement.shouldApplyPage(
                imagesChanged = false, atRightPage = false, touchedByUser = false, settled = false))
    }

    @Test
    fun `a carousel the user moved and then let go of is placed`() {
        // Once it is at rest the settle has been reported and the recorded page is the one the user
        // chose, so applying it either changes nothing or corrects a rebind that lost it.
        assertTrue(
            GalleryPagePlacement.shouldApplyPage(
                imagesChanged = false, atRightPage = false, touchedByUser = true, settled = true))
    }

    @Test
    fun `changed pictures are placed whatever else is true`() {
        // Replacing the pictures lays the carousel out from item zero, so the page has to be
        // re-applied even mid-gesture: this is a holder recycled onto a different post, and the
        // gesture belonged to the post that has just left.
        assertTrue(
            GalleryPagePlacement.shouldApplyPage(
                imagesChanged = true, atRightPage = false, touchedByUser = true, settled = false))
        assertTrue(
            GalleryPagePlacement.shouldApplyPage(
                imagesChanged = true, atRightPage = true, touchedByUser = true, settled = false))
    }

    @Test
    fun `a carousel already on the page is not touched`() {
        // The common case by far -- most binds change nothing -- and scrolling anyway would cancel
        // whatever the carousel was doing for no gain.
        assertFalse(
            GalleryPagePlacement.shouldApplyPage(
                imagesChanged = false, atRightPage = true, touchedByUser = false, settled = true))
        assertFalse(
            GalleryPagePlacement.shouldApplyPage(
                imagesChanged = false, atRightPage = true, touchedByUser = true, settled = false))
    }

    @Test
    fun `an untouched carousel at rest on the wrong page is corrected`() {
        // A freshly bound row that has not been placed yet, which is how a restored page reaches the
        // screen at all.
        assertTrue(
            GalleryPagePlacement.shouldApplyPage(
                imagesChanged = false, atRightPage = false, touchedByUser = false, settled = true))
    }
}
