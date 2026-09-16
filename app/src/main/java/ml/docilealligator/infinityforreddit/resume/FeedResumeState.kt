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

    /**
     * Which image each gallery card on screen was showing, as `fullname:page` entries.
     *
     * Every visible card, not just the anchor: the anchor is the row at the top EDGE of the
     * viewport, which is normally the post above the one being read and partly scrolled off it --
     * the recorded offset is negative for exactly that reason. Reading the page off the anchor
     * therefore reads it off the wrong post nearly every time.
     *
     * Only for this one restore, and only for the screen being restored. The page is not a property
     * of the post: a card scrolled back to later, or opened from somewhere else, starts on its first
     * image like any other.
     */
    @JvmField var galleryPages: ArrayList<String>? = null

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
        galleryPages = bundle.getStringArrayList(KEY_GALLERY_PAGES)
    }

    /** Write this record into [bundle]. A record with no feed key writes nothing. */
    fun writeTo(bundle: Bundle) {
        val key = feedKey ?: return
        bundle.putString(KEY_FEED, key)
        bundle.putString(KEY_ANCHOR_FULLNAME, anchorFullname)
        bundle.putInt(KEY_ANCHOR_POSITION, anchorPosition)
        bundle.putInt(KEY_ANCHOR_OFFSET, anchorOffset)
        bundle.putInt(KEY_EXPECTED_COUNT, expectedCount)
        // Only when there is one, so an ordinary feed's record is unchanged.
        val pages = galleryPages
        if (pages != null && pages.isNotEmpty()) {
            bundle.putStringArrayList(KEY_GALLERY_PAGES, pages)
        }
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
            args.remove(KEY_GALLERY_PAGES)
        }

        /** `fullname:page`, which is all [BundleJson] can carry -- it has string lists, not maps. */
        @JvmStatic
        fun encodePage(fullName: String, page: Int): String = "$fullName:$page"

        /** The fullname and page in [entry], or null when it is not one this wrote. */
        @JvmStatic
        fun decodePage(entry: String): Pair<String, Int>? {
            val split = entry.lastIndexOf(':')
            if (split <= 0 || split == entry.length - 1) {
                return null
            }
            val page = entry.substring(split + 1).toIntOrNull() ?: return null
            return if (page < 0) null else entry.substring(0, split) to page
        }

        const val KEY_FEED = "resumeFeedKey"
        const val KEY_ANCHOR_FULLNAME = "resumeAnchorFullname"
        const val KEY_ANCHOR_POSITION = "resumeAnchorPosition"
        const val KEY_ANCHOR_OFFSET = "resumeAnchorOffset"
        const val KEY_EXPECTED_COUNT = "resumeExpectedCount"
        const val KEY_GALLERY_PAGES = ResumeGalleryPage.KEY_FEED

        /**
         * Record a feed into [out].
         *
         * Returns false, writing nothing, when there is no anchor to record. Nothing at all is
         * better than the listing name on its own: a record that names a feed but cannot say where
         * in it the user was would reopen that feed scrolled to the top, which is not a resume.
         */
        @JvmStatic
        @JvmOverloads
        fun capture(
            out: Bundle,
            feedKey: String,
            anchor: ScrollAnchor.Anchor,
            anchorFullname: String?,
            loadedCount: Int,
            galleryPages: ArrayList<String>? = null,
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
            state.galleryPages = galleryPages
            state.writeTo(out)
            return true
        }
    }
}
