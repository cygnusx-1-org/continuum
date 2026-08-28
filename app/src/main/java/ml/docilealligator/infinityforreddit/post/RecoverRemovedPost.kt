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
     * Whether Reddit has scrubbed [post]. Mirrors the gate on the manual "Recover Post" action —
     * including the "[ Removed by Reddit ... ]" sentence a content-policy takedown leaves, which
     * `isRemoved` does not report.
     */
    @JvmStatic
    fun needsRecovery(post: Post): Boolean =
        post.isRemoved || post.isAuthorDeleted
            || FetchRemovedPost.isRemovalPlaceholder(post.selfText)
            || FetchRemovedPost.isRemovalPlaceholder(post.title)

    /**
     * The archived copy of [post], or null when Reddit has not scrubbed it or the archive has
     * nothing. Waits as long as the archive takes — the caller owns the deadline for drawing, and
     * this call outliving it is what lets a slow answer still be applied rather than thrown away.
     *
     * The archive is asked about a *copy*, so a reply the caller has stopped waiting for cannot
     * quietly rewrite a post already on screen; applying it stays the caller's decision.
     */
    suspend fun recoverIfRemoved(arcticShiftRetrofit: Retrofit, post: Post): Post? {
        if (!needsRecovery(post)) {
            return null
        }

        val candidate = Post(post)
        val recovered = suspendCancellableCoroutine { continuation ->
            FetchRemovedPost.fetchRemovedPost(
                arcticShiftRetrofit,
                candidate,
                object : FetchRemovedPost.FetchRemovedPostListener {
                    override fun fetchSuccess(post: Post) = continuation.resume(true)

                    override fun fetchFailed() = continuation.resume(false)
                }
            )
        }

        return if (recovered) candidate else null
    }
}
