package ml.docilealligator.infinityforreddit.comment

import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ml.docilealligator.infinityforreddit.apis.ArcticShiftAPI
import ml.docilealligator.infinityforreddit.post.FetchRemovedPost
import org.json.JSONException
import org.json.JSONObject
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.awaitResponse
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
     * of hundred milliseconds. Losing it no longer costs the page its recovery — the pass carries on
     * past the deadline, and what it brings back is written into rows as they are drawn off screen —
     * but a row already on screen keeps its placeholder until it scrolls away, so the first screenful
     * is still worth the wait. Short enough that a slow archive cannot hold comments hostage.
     */
    const val RENDER_WAIT_MILLIS = 5_000L

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
     * Whether any comment in [comments] is a candidate that none of [skip] already accounts for.
     *
     * The same question [candidateIds] answers, without building the list to answer it: this drives
     * the post-detail menu, which is rebuilt on every data-state emission — a vote, a collapse, a
     * page of comments — so it runs far more often than a recovery pass does.
     */
    fun hasCandidateOutside(comments: List<Comment>, vararg skip: Set<String>): Boolean =
        comments.any { comment -> isCandidate(comment) && skip.none { it.contains(comment.id) } }

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
     * [comments] and, recursively, every reply under them — collapsed ones included, because
     * expanding a thread later puts those very objects on screen.
     */
    fun flatten(comments: List<Comment>): List<Comment> {
        val all = ArrayList<Comment>()
        flatten(comments, all)
        return all
    }

    private fun flatten(comments: List<Comment>, into: MutableList<Comment>) {
        for (comment in comments) {
            into.add(comment)
            comment.children?.let { flatten(it, into) }
        }
    }

    /**
     * Whether writing [result] onto [comment] would change anything. It would not when the archive
     * holds a copy of a comment whose body Reddit never scrubbed (only its author), or when the
     * record has already been written.
     */
    fun changes(comment: Comment, result: FetchRemovedComment.Result): Boolean =
        result.body != comment.commentMarkdown
            || (result.author != null && result.author != comment.author)

    /**
     * Writes an archived record onto [comment].
     *
     * @return false when the record would change nothing (see [changes]), so a no-op is never
     *         counted as a recovery.
     */
    fun applyTo(comment: Comment, result: FetchRemovedComment.Result): Boolean {
        if (!changes(comment, result)) {
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
     * Each request is cancelled along with the caller, so a deadline around this call holds. Parsing
     * happens on [Dispatchers.IO]; a 6-figure-character payload is not parsed on the caller's thread.
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
            val outcome = fetchChunk(api, chunk) ?: return false
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
    private suspend fun fetchChunk(api: ArcticShiftAPI, chunk: List<String>): ChunkOutcome? {
        // Awaited, not executed: a blocking execute() ignores cancellation, so a deadline wrapped
        // around it only fired once the archive answered or OkHttp's own 30s timeouts did — the
        // ~20s comment loads of issue #446.
        val response: Response<String> = try {
            api.getRemovedComments(chunk.joinToString(",")).awaitResponse()
        } catch (e: IOException) {
            // Transient: leave these ids unrecovered rather than remembering them as absent.
            return null
        }
        return withContext(Dispatchers.IO) { readChunk(response) }
    }

    private fun readChunk(response: Response<String>): ChunkOutcome? {
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
