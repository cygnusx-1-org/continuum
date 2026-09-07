package ml.docilealligator.infinityforreddit.resume

import android.os.Bundle

/**
 * Where a feed was when the user left it: which listing, which post they were looking at, and how
 * many posts had been loaded behind it.
 *
 * This travels three ways, which is why it is a record rather than a pile of loose bundle keys:
 * a screen writes it into its resume snapshot, the screen reads it back on the next launch, and it
 * is handed down into the feed fragment as arguments. The same keys are used throughout, so a
 * record written by one hop is readable by the next with no translation.
 *
 * The sort is deliberately absent. It already comes back on its own -- from the per-subreddit sort
 * preference on a cold start, and from the fragment's own saved instance state across a rotation --
 * so recording it here would give the same value two owners that could disagree.
 */
class FeedResumeState {

    /** The listing this record belongs to. Null means "nothing to restore". */
    @JvmField var feedKey: String? = null

    /** Fullname of the post the user was looking at. The primary way the anchor is found again. */
    @JvmField var anchorFullname: String? = null

    /** Adapter position of that post, used only when the fullname is no longer in the feed. */
    @JvmField var anchorPosition: Int = ScrollAnchor.NO_POSITION

    /** Pixel offset of the anchor from the top of the viewport. See [ScrollAnchor]. */
    @JvmField var anchorOffset: Int = 0

    /** How many posts the feed held, against which a shrunken cache is rejected. */
    @JvmField var expectedCount: Int = 0

    fun isPending(): Boolean = feedKey != null

    /** Read a record out of [bundle]. A bundle with no record leaves this one empty. */
    fun read(bundle: Bundle?) {
        if (bundle == null || !bundle.containsKey(KEY_FEED)) {
            return
        }
        feedKey = bundle.getString(KEY_FEED)
        anchorFullname = bundle.getString(KEY_ANCHOR_FULLNAME)
        anchorPosition = bundle.getInt(KEY_ANCHOR_POSITION, ScrollAnchor.NO_POSITION)
        anchorOffset = bundle.getInt(KEY_ANCHOR_OFFSET, 0)
        expectedCount = bundle.getInt(KEY_EXPECTED_COUNT, 0)
    }

    /** Write this record into [bundle]. A record with no feed key writes nothing. */
    fun writeTo(bundle: Bundle) {
        val key = feedKey ?: return
        bundle.putString(KEY_FEED, key)
        bundle.putString(KEY_ANCHOR_FULLNAME, anchorFullname)
        bundle.putInt(KEY_ANCHOR_POSITION, anchorPosition)
        bundle.putInt(KEY_ANCHOR_OFFSET, anchorOffset)
        bundle.putInt(KEY_EXPECTED_COUNT, expectedCount)
    }

    /**
     * Hand this record to a fragment's arguments and forget it.
     *
     * One-shot on purpose: a pager builds its neighbouring page as well as the current one, and
     * rebuilding the adapter would otherwise hand the same restore to a second fragment and scroll
     * a feed the user never left.
     */
    fun applyTo(args: Bundle) {
        writeTo(args)
        feedKey = null
    }

    companion object {
        /**
         * Remove a record from [args] once it has been acted on, so that a view rebuilt without its
         * fragment being rebuilt -- a pager detaching and reattaching a page -- does not restore a
         * second time and scroll the feed out from under the user.
         */
        @JvmStatic
        fun clearFrom(args: Bundle?) {
            args ?: return
            args.remove(KEY_FEED)
            args.remove(KEY_ANCHOR_FULLNAME)
            args.remove(KEY_ANCHOR_POSITION)
            args.remove(KEY_ANCHOR_OFFSET)
            args.remove(KEY_EXPECTED_COUNT)
        }

        const val KEY_FEED = "resumeFeedKey"
        const val KEY_ANCHOR_FULLNAME = "resumeAnchorFullname"
        const val KEY_ANCHOR_POSITION = "resumeAnchorPosition"
        const val KEY_ANCHOR_OFFSET = "resumeAnchorOffset"
        const val KEY_EXPECTED_COUNT = "resumeExpectedCount"

        /**
         * Record a feed into [out].
         *
         * Returns false, writing nothing, when there is no anchor to record. Nothing at all is
         * better than the listing name on its own: a record that names a feed but cannot say where
         * in it the user was would reopen that feed scrolled to the top, which is not a resume.
         */
        @JvmStatic
        fun capture(
            out: Bundle,
            feedKey: String,
            anchor: ScrollAnchor.Anchor,
            anchorFullname: String?,
            loadedCount: Int,
        ): Boolean {
            if (!anchor.isValid) {
                return false
            }
            val state = FeedResumeState()
            state.feedKey = feedKey
            state.anchorFullname = anchorFullname
            state.anchorPosition = anchor.position
            state.anchorOffset = anchor.offset
            state.expectedCount = loadedCount
            state.writeTo(out)
            return true
        }
    }
}
