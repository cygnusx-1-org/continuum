package ml.docilealligator.infinityforreddit.events

/**
 * Tells the post list which post the swipe-between-posts pager is currently showing, so the feed
 * can follow along and be scrolled to that post when the user comes back to it.
 *
 * [positionInList] indexes the snapshot the fragment handed over in
 * [ProvidePostListToViewPostDetailActivityEvent], so it lines up with the fragment's adapter
 * positions. [postFullName] identifies the post the index is meant to point at; the list verifies
 * it before scrolling, since the pager can run past the end of what the feed has loaded.
 */
class PostPositionUpdateEventToPostList(
    @JvmField val postFragmentId: Long,
    @JvmField val positionInList: Int,
    @JvmField val postFullName: String
)
