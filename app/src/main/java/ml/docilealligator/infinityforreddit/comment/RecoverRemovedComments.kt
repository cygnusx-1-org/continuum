package ml.docilealligator.infinityforreddit.comment

import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import ml.docilealligator.infinityforreddit.apis.ArcticShiftAPI
import ml.docilealligator.infinityforreddit.post.FetchRemovedPost
import org.json.JSONException
import org.json.JSONObject
import retrofit2.Response
import retrofit2.Retrofit
import java.io.IOException

/**
 * Bulk recovery of removed/deleted comment bodies from the Arctic Shift archive.
 *
 * The archive's `api/comments/ids` takes a comma-separated batch, so a thread with 150 removed
 * comments costs one request rather than 150. That fan-out of 1 instead of N is what makes doing
 * this automatically affordable at all, and it is also why the results land as one list update per
 * chunk instead of a per-comment refresh.
 */
object RecoverRemovedComments {

    /**
     * Threads with at most this many comments recover automatically as their comments load; a
     * bigger one waits for the explicit overflow-menu action, so opening a mega-thread is never
     * taxed by the archive. This is Reddit's own comment ceiling — a thread of this size is what a
     * single comment fetch returns.
     */
    const val AUTO_RECOVERY_COMMENT_LIMIT = 500

    /**
     * Ids per request. The limit is URL length rather than a documented item count — 500 ids answer
     * normally, 1000 come back `414 URI Too Long` — so this keeps a wide margin while still holding
     * a full 500-comment thread to two requests.
     */
    private const val CHUNK_SIZE = 250

    /** Floor for the back-off after a rate limit, whatever `x-ratelimit-reset` asks for. */
    private const val RATE_LIMIT_COOLDOWN_MILLIS = 60_000L

    /** Back-off after the archive reports its own throttle or an error in the payload. */
    private const val ERROR_COOLDOWN_MILLIS = 30_000L

    /** Treat this few remaining requests as spent, rather than racing the limit to zero. */
    private const val LOW_REMAINING_THRESHOLD = 3

    /**
     * How long a page of comments waits for the archive before it is rendered anyway.
     *
     * Generous for the same reason post recovery is: the first archive call of a session pays DNS
     * and the TLS handshake on top of the request, and 2s was measured losing that race by a couple
     * of hundred milliseconds — which costs the page its recovery, since a late answer is dropped
     * rather than swapped in. Still short enough that a dead archive cannot hold comments hostage.
     */
    private const val RENDER_WAIT_MILLIS = 5_000L

    /**
     * Process-wide, because the rate limit is: two posts open in the same session share one budget.
     */
    @Volatile
    private var cooldownUntilElapsedMillis = 0L

    /**
     * The ids of the comments in [comments] that Reddit has scrubbed — body replaced with a removal
     * placeholder, or author replaced with `[deleted]`. Mirrors the gate on the per-comment
     * "Recover comment" action so the automatic pass and the manual one agree on what is
     * recoverable, and skips the "load more"/"continue thread" rows, which are UI placeholders with
     * no archive record behind them.
     */
    @JvmStatic
    fun candidateIds(comments: List<Comment>): List<String> {
        val ids = ArrayList<String>()
        for (comment in comments) {
            val id = comment.id
            if (id != null && isCandidate(comment)) {
                ids.add(id)
            }
        }
        return ids
    }

    /**
     * Whether any comment in [comments] is a candidate that [skip] does not already account for.
     *
     * The same question [candidateIds] answers, without building the list to answer it: this drives
     * the post-detail menu, which is rebuilt on every data-state emission — a vote, a collapse, a
     * page of comments — so it runs far more often than a recovery pass does.
     */
    fun hasCandidateOutside(comments: List<Comment>, skip: Set<String>): Boolean =
        comments.any { isCandidate(it) && !skip.contains(it.id) }

    /** A comment Reddit has scrubbed and that the archive can be asked about by id. */
    private fun isCandidate(comment: Comment): Boolean {
        if (comment.placeholderType != Comment.NOT_PLACEHOLDER || comment.id == null) {
            return false
        }
        return comment.isRemoved || comment.isAuthorDeleted
            || FetchRemovedPost.isRemovalPlaceholder(comment.commentRawText)
            || FetchRemovedPost.isRemovalPlaceholder(comment.commentMarkdown)
    }

    /**
     * Recovers the removed comments in [comments] — and, recursively, in their replies — writing the
     * archived bodies straight onto the freshly parsed objects.
     *
     * This is the path that matters for how recovery *looks*. Patching a list the adapter is already
     * showing makes each removed comment visibly flash "[deleted]" and then swap to its real body;
     * patching comments no adapter has seen yet means they simply arrive recovered, with no redraw.
     * Replies are included even when they are currently collapsed, because expanding a thread later
     * puts those very objects on screen.
     *
     * Waits at most [RENDER_WAIT_MILLIS] for the archive and then gives up, leaving the comments as
     * Reddit served them rather than delaying the page behind a slow archive.
     *
     * @param skip ids already asked about, so a page that repeats one does not re-spend a request.
     * @return the ids the archive answered for, whether or not it held anything. Empty when the wait
     *         ran out, so those ids stay eligible for a later attempt.
     */
    suspend fun recoverInPlace(
        arcticShiftRetrofit: Retrofit,
        comments: List<Comment>,
        skip: Set<String>
    ): Set<String> {
        val all = ArrayList<Comment>()
        flatten(comments, all)

        val ids = candidateIds(all).filterNot { skip.contains(it) }
        if (ids.isEmpty()) {
            return emptySet()
        }

        val attempted = HashSet<String>()
        withTimeoutOrNull(RENDER_WAIT_MILLIS) {
            recover(arcticShiftRetrofit, ids) { requested, recovered ->
                attempted.addAll(requested)
                for (comment in all) {
                    comment.id?.let { recovered[it] }?.let { applyTo(comment, it) }
                }
            }
        }
        return attempted
    }

    private fun flatten(comments: List<Comment>, into: MutableList<Comment>) {
        for (comment in comments) {
            into.add(comment)
            comment.children?.let { flatten(it, into) }
        }
    }

    /**
     * Writes an archived record onto [comment].
     *
     * @return false when the record would change nothing — the archive holds a copy of a comment
     *         whose body Reddit never scrubbed (only its author), so applying it would count a no-op
     *         as a recovery.
     */
    fun applyTo(comment: Comment, result: FetchRemovedComment.Result): Boolean {
        if (result.body == comment.commentMarkdown
            && (result.author == null || result.author == comment.author)) {
            return false
        }

        comment.commentMarkdown = result.body
        comment.commentRawText = result.body
        comment.setRecovered(true)
        if (result.author != null) {
            comment.setAuthor(result.author)
            comment.setAuthorFlair(result.authorFlair)
            comment.setAuthorFlairHTML(result.authorFlairHTML)
        }
        return true
    }

    /**
     * Fetches [ids] from the archive in chunks, handing each chunk to [onChunk] as it arrives so a
     * long thread fills in progressively instead of in one late jump. [onChunk] is called on the
     * caller's context with the ids that were asked about and the bodies that came back for them;
     * an id present in the former and absent from the latter is one the archive genuinely does not
     * have.
     *
     * Requests and parsing happen on [Dispatchers.IO]; a 6-figure-character payload is not parsed
     * on the caller's thread.
     *
     * @return true when every chunk was fetched. False means the pass stopped early — a cooldown, a
     *         network failure or an error from the archive — so the caller can retry those ids later
     *         rather than recording them as absent from the archive.
     */
    suspend fun recover(
        arcticShiftRetrofit: Retrofit,
        ids: List<String>,
        onChunk: suspend (requested: List<String>, recovered: Map<String, FetchRemovedComment.Result>) -> Unit
    ): Boolean {
        if (ids.isEmpty()) {
            return true
        }
        if (SystemClock.elapsedRealtime() < cooldownUntilElapsedMillis) {
            return false
        }

        val api = arcticShiftRetrofit.create(ArcticShiftAPI::class.java)
        for (chunk in ids.chunked(CHUNK_SIZE)) {
            val outcome = withContext(Dispatchers.IO) { fetchChunk(api, chunk) } ?: return false
            onChunk(chunk, outcome.recovered)
            if (!outcome.mayContinue) {
                return false
            }
        }
        return true
    }

    /**
     * What one archive request yielded: the bodies recovered for the chunk, and whether the pass may
     * ask for another one.
     */
    private class ChunkOutcome(
        val recovered: Map<String, FetchRemovedComment.Result>,
        val mayContinue: Boolean
    )

    /**
     * One archive request. Returns what it recovered — possibly empty, meaning the archive answered
     * and holds none of the chunk — or null when the request yielded nothing usable and the pass
     * must stop.
     */
    private fun fetchChunk(api: ArcticShiftAPI, chunk: List<String>): ChunkOutcome? {
        val response: Response<String> = try {
            api.getRemovedComments(chunk.joinToString(",")).execute()
        } catch (e: IOException) {
            // Transient: leave these ids unrecovered rather than remembering them as absent.
            return null
        }

        // Checked before isSuccessful() because a 429 is a back-off rather than a plain failure —
        // and it carries no data to salvage, so it stops the pass outright.
        if (response.code() == 429) {
            startCooldown(rateLimitCooldown(response))
            return null
        }

        val body = response.body()
        if (!response.isSuccessful || body == null) {
            return null
        }

        val payload = try {
            JSONObject(body)
        } catch (e: JSONException) {
            return null
        }

        // The archive's app-level throttle answers {"data":null,"error":"Timeout. Maybe slow down a
        // bit"} with an HTTP 200, so no status check catches it.
        if (!payload.isNull("error")) {
            startCooldown(ERROR_COOLDOWN_MILLIS)
            return null
        }

        // Only now that the response has been read: this one is good, so keep what it recovered
        // before backing off. Discarding it would spend the very request the throttle is protecting
        // and leave the same comments to be asked about all over again. The header is absent until
        // the limit is close, so a missing value means "plenty left", never zero.
        val remaining = response.headers()["x-ratelimit-remaining"]?.toIntOrNull() ?: Int.MAX_VALUE
        if (remaining <= LOW_REMAINING_THRESHOLD) {
            startCooldown(rateLimitCooldown(response))
            return ChunkOutcome(FetchRemovedComment.parseResults(payload), mayContinue = false)
        }

        return ChunkOutcome(FetchRemovedComment.parseResults(payload), mayContinue = true)
    }

    private fun rateLimitCooldown(response: Response<String>): Long {
        val resetMillis = (response.headers()["x-ratelimit-reset"]?.toLongOrNull() ?: 0L) * 1000L
        return maxOf(resetMillis, RATE_LIMIT_COOLDOWN_MILLIS)
    }

    private fun startCooldown(durationMillis: Long) {
        cooldownUntilElapsedMillis = SystemClock.elapsedRealtime() + durationMillis
    }
}
