package ml.docilealligator.infinityforreddit.post

import kotlinx.coroutines.suspendCancellableCoroutine
import retrofit2.Retrofit
import kotlin.coroutines.resume

/**
 * Automatic recovery of a removed post's content from the Arctic Shift archive.
 *
 * Only the post-detail view uses this. A feed is deliberately left alone: recovering there would fan
 * archive requests out across every removed post a scroll goes past, for content the reader has not
 * asked to see.
 */
object RecoverRemovedPost {

    /**
     * Whether Reddit has scrubbed [post] and the archive has not restored it yet. The scrubbed test
     * mirrors the gate on the manual "Recover Post" action — including the "[ Removed by Reddit ... ]"
     * sentence a content-policy takedown leaves, which `isRemoved` does not report. A recovered post
     * still reads as removed, so it is excluded here: asking again would only hold its draw on the
     * archive for content it is already showing.
     */
    @JvmStatic
    fun needsRecovery(post: Post): Boolean =
        (post.isRemoved || post.isAuthorDeleted
            || FetchRemovedPost.isRemovalPlaceholder(post.selfText)
            || FetchRemovedPost.isRemovalPlaceholder(post.title))
            && !post.isRecovered

    /**
     * What the archive holds for [post], or null when Reddit has not scrubbed it, the archive has
     * nothing, or the request failed. Waits as long as the archive takes — the caller owns the
     * deadline for drawing, and this call outliving it is what lets a slow answer still be applied
     * rather than thrown away.
     *
     * Written onto nothing: by the time a slow answer lands the post may have been voted on or
     * saved, so the caller writes it onto whatever the post has become, with [recovered].
     */
    suspend fun fetchArchived(arcticShiftRetrofit: Retrofit, post: Post): FetchRemovedPost.Result? {
        if (!needsRecovery(post)) {
            return null
        }

        return suspendCancellableCoroutine { continuation ->
            FetchRemovedPost.fetchArchivedPost(
                arcticShiftRetrofit,
                post.id,
                object : FetchRemovedPost.ArchivedPostListener {
                    override fun fetchSuccess(result: FetchRemovedPost.Result) = continuation.resume(result)

                    override fun fetchFailed() = continuation.resume(null)
                }
            )
        }
    }

    /**
     * A copy of [post] rewritten as the archive holds it. A copy, so a post already on screen is
     * never changed underneath it; drawing it stays the caller's decision.
     */
    fun recovered(post: Post, archived: FetchRemovedPost.Result): Post =
        Post(post).also { FetchRemovedPost.applyArchived(it, archived) }
}
