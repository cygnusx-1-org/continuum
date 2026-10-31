package ml.docilealligator.infinityforreddit.comment

import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import ml.docilealligator.infinityforreddit.commentfilter.CommentFilter
import ml.docilealligator.infinityforreddit.utils.JSONUtils
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import retrofit2.Response
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Loads the comments behind a `more` object.
 *
 * Reddit has answered `/api/morechildren` with a 403 "blocked by network security" page since
 * around 2026-10-29 (issue #475), whatever the method or User-Agent, so everything here goes
 * through focused comment listings, which still work. They differ from morechildren in three ways
 * this class absorbs:
 *  - A listing holds one comment and its subtree, so a `more` with many ids could cost one request
 *    per id. Replies load from a single listing of their parent; a top-level `more` has no parent
 *    comment to focus, so it loads one page of ids at a time.
 *  - The focused comment comes back at depth 0 wherever it sits in the thread. Comment depth drives
 *    indentation and parent lookups, so it is rebased before anything is parsed.
 *  - A comment that no longer exists comes back as an empty listing rather than being left out.
 */
class MoreCommentsLoader(
    private val commentFilter: CommentFilter,
    private val session: Session,
    private val fetchFocusedComment: suspend (commentId: String, limit: Int?) -> Response<String>,
    private val parsingDispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    /**
     * [remainingIds] is what a later load still has to fetch, in order, and [remainingCount] Reddit's
     * count of the replies behind them, when the listing gave one.
     */
    class Page(
        val topLevelComments: ArrayList<Comment>,
        val remainingIds: ArrayList<String>,
        val remainingCount: Int? = null
    )

    /**
     * What loads of one thread have to remember between them. Each load is its own loader, but
     * scrolling to the footer starts the next one unasked, so stopping for the rate limit only works
     * if that one knows.
     */
    class Session(private val nowMillis: () -> Long = SystemClock::elapsedRealtime) {
        private val listedParentIds = HashSet<String>()

        @Volatile
        private var pausedUntilMillis = 0L

        /** @return false if a listing of this parent has already been spent. */
        internal fun claimParentListing(parentId: String) = listedParentIds.add(parentId)

        internal fun releaseParentListing(parentId: String) {
            listedParentIds.remove(parentId)
        }

        internal fun isPaused() = nowMillis() < pausedUntilMillis

        /**
         * Pauses while too little of the budget is left, so that votes and feeds sharing it still go
         * through. Reddit sends the remaining count as a decimal, such as `9993.0`.
         */
        internal fun onResponse(response: Response<*>) {
            val remaining = response.headers()["x-ratelimit-remaining"]?.toDoubleOrNull()
            if (response.code() == 429 || (remaining != null && remaining <= RATE_LIMIT_RESERVE)) {
                val resetSeconds = response.headers()["x-ratelimit-reset"]?.toDoubleOrNull() ?: 0.0
                pausedUntilMillis = nowMillis() + maxOf((resetSeconds * 1000).toLong(), MIN_RATE_LIMIT_PAUSE_MILLIS)
                Log.w(TAG, "Pausing focused comments for the rate limit, remaining=$remaining")
            }
        }

        fun onThreadReloaded() {
            listedParentIds.clear()
        }
    }

    /** @return null if nothing could be loaded, so the caller should offer a retry. */
    suspend fun loadTopLevel(ids: List<String>): Page? = loadIndividually(ids.distinct(), 0)

    /**
     * @param parentId id36 of the comment whose placeholder this is
     * @param childDepth the depth the loaded replies belong at
     * @return null if nothing could be loaded, so the caller should offer a retry.
     */
    suspend fun loadReplies(parentId: String, childDepth: Int, ids: List<String>): Page? {
        val wanted = ids.distinct()
        // A second listing of the parent would only repeat what the first one deferred.
        if (wanted.size < PARENT_LISTING_MIN_IDS || !session.claimParentListing(parentId)) {
            return loadIndividually(wanted, childDepth)
        }

        val listing = fetchListing(parentId, PARENT_LISTING_LIMIT)
        if (listing == null) {
            session.releaseParentListing(parentId)
            return null
        }
        val page = withContext(parsingDispatcher) {
            parseParentListing(listing, parentId, childDepth, wanted)
        }
        // No page, or one that found nothing while ids remain, means the parent is gone or the
        // limit went on replies already on screen. Asking again would get the same answer.
        return if (page != null && (page.topLevelComments.isNotEmpty() || page.remainingIds.isEmpty())) {
            page
        } else {
            loadIndividually(wanted, childDepth)
        }
    }

    private suspend fun loadIndividually(ids: List<String>, depth: Int): Page? {
        val comments = ArrayList<Comment>()
        var remaining = ids
        var emptyPages = 0
        while (remaining.isNotEmpty() && comments.isEmpty() && emptyPages < MAX_EMPTY_PAGES) {
            val pageIds = remaining.take(PAGE_SIZE)
            val listings = fetchInOrder(pageIds)
            // Comments are kept only up to the first failure, so a retry resumes in thread order.
            val fetchedCount = listings.indexOfFirst { it == null }.let { if (it == -1) listings.size else it }
            val parsed = withContext(parsingDispatcher) {
                parseFocusedListings(pageIds.take(fetchedCount), listings.take(fetchedCount), depth)
            }
            comments.addAll(parsed)

            val loadedIds = HashSet<String>()
            collectIds(parsed, loadedIds)
            remaining = remaining.drop(fetchedCount).filterNot { it in loadedIds }
            if (fetchedCount < pageIds.size) {
                return if (comments.isEmpty()) null else Page(comments, ArrayList(remaining))
            }
            emptyPages++
        }
        return Page(comments, ArrayList(remaining))
    }

    /** @return each id's listing at its index, or null if it failed or was skipped. */
    private suspend fun fetchInOrder(ids: List<String>): List<String?> = coroutineScope {
        val gate = Semaphore(MAX_CONCURRENT_REQUESTS)
        val failed = AtomicBoolean(false)
        ids.map { id ->
            async {
                gate.withPermit {
                    // A 403 or 429 will not be the only one: stop spending requests on this page. A
                    // low rate-limit budget ends up here too, as fetchListing refuses to spend it.
                    if (failed.get()) {
                        null
                    } else {
                        fetchListing(id, null).also { if (it == null) failed.set(true) }
                    }
                }
            }
        }.awaitAll()
    }

    private suspend fun fetchListing(commentId: String, limit: Int?): String? {
        if (session.isPaused()) {
            return null
        }
        return try {
            val response = fetchFocusedComment(commentId, limit)
            session.onResponse(response)
            if (!response.isSuccessful) {
                Log.w(TAG, "Focused comment $commentId failed with HTTP ${response.code()}")
            }
            if (response.isSuccessful) response.body() else null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Focused comment $commentId failed", e)
            null
        }
    }

    private fun parseFocusedListings(ids: List<String>, listings: List<String?>, depth: Int): ArrayList<Comment> {
        val roots = ArrayList<Comment>()
        for ((index, listing) in listings.withIndex()) {
            try {
                parseFocusedListing(listing ?: continue, ids[index], depth)?.let { roots.add(it) }
            } catch (e: JSONException) {
                // One comment Reddit sent in an odd shape must not block every comment after it.
                Log.w(TAG, "Dropping focused comment ${ids[index]}", e)
            } catch (e: RuntimeException) {
                Log.w(TAG, "Dropping focused comment ${ids[index]}", e)
            }
        }

        // The ids in a `more` can include a comment and its descendant, whose own listing repeats it.
        val descendantIds = HashSet<String>()
        roots.forEach { root -> root.children?.let { collectIds(it, descendantIds) } }
        val seenIds = HashSet<String>()
        return roots.filterTo(ArrayList()) { root ->
            root.id?.let { it !in descendantIds && seenIds.add(it) } == true
        }
    }

    private fun parseFocusedListing(listing: String, id: String, depth: Int): Comment? {
        val thing = commentThings(listing).optJSONObject(0) ?: return null
        val data = thing.getJSONObject(JSONUtils.DATA_KEY)
        if (thing.getString(JSONUtils.KIND_KEY) == JSONUtils.KIND_VALUE_MORE || data.getString(JSONUtils.ID_KEY) != id) {
            Log.w(TAG, "Focused comment $id returned something else")
            return null
        }
        rebaseDepth(thing, depth - data.optInt(JSONUtils.DEPTH_KEY))

        val comments = ArrayList<Comment>()
        ParseComment.parseCommentRecursion(JSONArray().put(thing), comments, ArrayList(), depth, commentFilter)
        return comments.firstOrNull()
    }

    /** @return null if [listing] does not hold the parent, or is not a usable listing. */
    private fun parseParentListing(listing: String, parentId: String, childDepth: Int, wanted: List<String>): Page? {
        try {
            val parent = commentThings(listing).optJSONObject(0)?.getJSONObject(JSONUtils.DATA_KEY)
            if (parent == null || parent.getString(JSONUtils.ID_KEY) != parentId) {
                return null
            }
            val replies = parent.optJSONObject(JSONUtils.REPLIES_KEY)
                ?.getJSONObject(JSONUtils.DATA_KEY)?.getJSONArray(JSONUtils.CHILDREN_KEY)
                ?: JSONArray()

            // The listing also repeats the replies already on screen, so keep only what was asked for.
            // An id it neither returns nor defers to a `more` is a comment that no longer exists.
            val wantedIds = wanted.toHashSet()
            val deferredIds = HashSet<String>()
            var deferredCount = 0
            val wantedReplies = JSONArray()
            for (i in 0..<replies.length()) {
                val thing = replies.getJSONObject(i)
                val data = thing.getJSONObject(JSONUtils.DATA_KEY)
                if (thing.getString(JSONUtils.KIND_KEY) == JSONUtils.KIND_VALUE_MORE) {
                    val children = data.getJSONArray(JSONUtils.CHILDREN_KEY)
                    for (j in 0..<children.length()) {
                        deferredIds.add(children.getString(j))
                    }
                    deferredCount += data.optInt(JSONUtils.COUNT_KEY)
                } else if (data.getString(JSONUtils.ID_KEY) in wantedIds) {
                    rebaseDepth(thing, childDepth - data.optInt(JSONUtils.DEPTH_KEY))
                    wantedReplies.put(thing)
                }
            }

            val comments = ArrayList<Comment>()
            ParseComment.parseCommentRecursion(wantedReplies, comments, ArrayList(), childDepth, commentFilter)
            return Page(comments, wanted.filterTo(ArrayList()) { it in deferredIds }, deferredCount)
        } catch (e: JSONException) {
            Log.w(TAG, "Unusable listing for parent comment $parentId", e)
        } catch (e: RuntimeException) {
            Log.w(TAG, "Unusable listing for parent comment $parentId", e)
        }
        return null
    }

    private fun commentThings(listing: String): JSONArray {
        return JSONArray(listing).getJSONObject(1).getJSONObject(JSONUtils.DATA_KEY)
            .getJSONArray(JSONUtils.CHILDREN_KEY)
    }

    private fun rebaseDepth(thing: JSONObject, offset: Int) {
        val data = thing.getJSONObject(JSONUtils.DATA_KEY)
        data.put(JSONUtils.DEPTH_KEY, data.optInt(JSONUtils.DEPTH_KEY) + offset)
        val replies = data.optJSONObject(JSONUtils.REPLIES_KEY)
            ?.optJSONObject(JSONUtils.DATA_KEY)?.optJSONArray(JSONUtils.CHILDREN_KEY) ?: return
        for (i in 0..<replies.length()) {
            rebaseDepth(replies.getJSONObject(i), offset)
        }
    }

    private fun collectIds(comments: List<Comment>, ids: MutableSet<String>) {
        for (comment in comments) {
            comment.id?.let { ids.add(it) }
            comment.children?.let { collectIds(it, ids) }
        }
    }

    companion object {
        private const val TAG = "MoreCommentsLoader"

        /** Below this, individual listings cost less than a parent's subtree of up to about 1 MB. */
        const val PARENT_LISTING_MIN_IDS = 5
        const val PARENT_LISTING_LIMIT = 500
        const val PAGE_SIZE = 25
        const val MAX_CONCURRENT_REQUESTS = 4

        /** About one id in eight is empty, so pages of nothing but empties are vanishingly rare. */
        const val MAX_EMPTY_PAGES = 8

        const val RATE_LIMIT_RESERVE = 20.0
        const val MIN_RATE_LIMIT_PAUSE_MILLIS = 10_000L
    }
}
