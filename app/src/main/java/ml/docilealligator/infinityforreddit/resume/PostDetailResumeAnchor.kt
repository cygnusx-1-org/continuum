package ml.docilealligator.infinityforreddit.resume

/**
 * Where in a thread the post-detail screen records the user as being.
 *
 * A thread's position is written as a comment plus an offset, because a comment is the only thing
 * in that list stable enough to find again -- rows shift as replies load, threads collapse and the
 * sort changes, but a comment's fullname does not. What that vocabulary cannot say is "above the
 * comments", and the row up there is the post, not a comment.
 *
 * It used to answer that case with the first comment at offset zero, which is the wrong place by
 * the height of the post: a thread left at the top reopened with the post scrolled off above it,
 * and on a gallery post that is most of the screen. Since reading the post and then leaving is one
 * of the commonest ways to leave this screen, the case worth getting right was the one the
 * encoding could not express.
 *
 * Extracted for the same reason `PreloadWindow` is kept out of its preloader: standing up the real
 * screen means a fragment, a ConcatAdapter, a fetched thread and the Dagger graph behind them, and
 * a test that re-derived this rule instead would go on passing while the app shipped the opposite.
 */
object PostDetailResumeAnchor {

    /**
     * What to record for a thread whose topmost row is at [anchorPosition]/[anchorOffset].
     *
     * [localPosition] is that row's index among the comments, or negative when it is not a comment
     * at all -- the post, its media, a header. [commentCount] bounds it, because a stale index from
     * a thread that has since shrunk names a comment that is no longer there.
     */
    @JvmStatic
    fun forTopRow(
        localPosition: Int,
        commentCount: Int,
        anchorPosition: Int,
        anchorOffset: Int,
    ): Anchor =
        if (localPosition < 0 || localPosition >= commentCount) {
            // No comment to name, so the absolute row is recorded instead -- with its real offset,
            // not zero: the user may have been part way down the post rather than at its very top.
            Anchor(aboveComments = true, commentIndex = -1, position = anchorPosition, offset = anchorOffset)
        } else {
            Anchor(aboveComments = false, commentIndex = localPosition, position = anchorPosition, offset = anchorOffset)
        }

    /**
     * @param aboveComments whether [position] is an absolute row in the concatenated list rather
     *   than an index into the comments. The two are applied differently, because the comment
     *   adapter does not hold the row an above-comments anchor names.
     */
    data class Anchor(
        @JvmField val aboveComments: Boolean,
        @JvmField val commentIndex: Int,
        @JvmField val position: Int,
        @JvmField val offset: Int,
    )
}
