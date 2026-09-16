package ml.docilealligator.infinityforreddit.resume

import org.json.JSONObject

/**
 * Which image of a gallery was showing, as it travels in a resume snapshot.
 *
 * Only the screen that was on top when the app was put down records one, and only for the single
 * return it describes. Both halves matter and both were got wrong before:
 *
 *  - **Top screen only.** Every live screen is asked to describe itself -- that is how the stack
 *    comes back -- so a feed sitting under a post-detail screen would otherwise record the pages of
 *    cards that were last on screen some time ago. The user was not looking at that feed, and
 *    putting its carousels back to images they left long before is not a resume.
 *  - **Spent once.** The page is a note about one return, not a property of the post. A page that
 *    outlived its resume made a gallery reopen on that image every time it was seen again, on any
 *    screen, which is the opposite of coming back to where you were.
 *
 * The keys live here rather than on the screens that write them because [ResumeState] is what knows
 * which entry is the top one, and it strips the rest without knowing anything else about them.
 */
object ResumeGalleryPage {

    /** The post-detail screen's page. Referenced by `ViewPostDetailFragmentNew`. */
    const val KEY_POST_DETAIL = "ERGP"

    /** A feed's per-card pages. Referenced by [FeedResumeState]. */
    const val KEY_FEED = "resumeGalleryPages"

    /**
     * Remove any gallery page from an encoded state document, leaving everything else untouched.
     *
     * The encoded document rather than the screen's [Bundle]: the bundle belongs to a live screen
     * that may be the top one the next time a snapshot is taken, and a screen must not be made to
     * forget where it is by having been described while something else was in front of it.
     */
    @JvmStatic
    fun stripFrom(state: JSONObject?) {
        state ?: return
        state.remove(KEY_POST_DETAIL)
        state.remove(KEY_FEED)
    }
}
