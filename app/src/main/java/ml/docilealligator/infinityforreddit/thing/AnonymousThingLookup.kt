package ml.docilealligator.infinityforreddit.thing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import ml.docilealligator.infinityforreddit.utils.JSONUtils
import org.json.JSONArray
import org.json.JSONObject
import retrofit2.Response
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Looks things up by fullname for the anonymous account's local lists, whose rows hold little more
 * than an id: posts through `AnonymousPostLookup`, comments through `AnonymousCommentLookup`.
 *
 * `/api/info` does that in one request, but Reddit has answered it for anonymous requests with a 403
 * "blocked by network security" page since around 2026-10-29 (issue #476), the same block as
 * `/api/morechildren` in #475. A thing's own `/comments/...` listing still answers, so after a 403
 * each thing is fetched from there, and the results are put together into a listing shaped like the
 * one `/api/info` returns, which the callers parse as before. Once `/api/info` has refused, it is not
 * asked again for the rest of the process: every attempt costs a 403, a token refresh and a second
 * 403 in `AnonymousAccessTokenInterceptor`.
 *
 * @param fullname the name `/api/info` is asked for, `t3_` or `t1_` included
 * @param fetchOne the thing's own listing, for when `/api/info` has refused
 * @param childOf the thing's listing child in a [fetchOne] response, or null when it is not there
 */
internal class AnonymousThingLookup<T>(
    private val fetchInfo: suspend (fullnames: String) -> Response<String>,
    private val infoBlocked: AtomicBoolean,
    private val fullname: (T) -> String,
    private val fetchOne: suspend (T) -> Response<String>,
    private val childOf: (body: String) -> JSONObject?
) {
    /**
     * @param things in the order they are to appear
     * @return a listing, or the error response to report
     */
    suspend fun load(things: List<T>): Response<String> {
        if (things.isEmpty()) {
            return Response.success(listing(JSONArray()))
        }
        if (!infoBlocked.get()) {
            val response = fetchInfo(things.joinToString(",", transform = fullname))
            if (response.code() != 403) {
                return response
            }
            infoBlocked.set(true)
        }
        return loadIndividually(things)
    }

    private suspend fun loadIndividually(things: List<T>): Response<String> = coroutineScope {
        val gate = Semaphore(MAX_CONCURRENT_REQUESTS)
        val transientFailure = AtomicReference<Response<String>?>(null)
        val responses = things.map { thing ->
            async {
                gate.withPermit {
                    // A 429 or a server error will not be the only one: stop spending requests on
                    // this page and let the retry ask again.
                    if (transientFailure.get() != null) {
                        null
                    } else {
                        fetchOne(thing).also {
                            if (it.code() == 429 || it.code() >= 500) {
                                transientFailure.compareAndSet(null, it)
                            }
                        }
                    }
                }
            }
        }.awaitAll()
        transientFailure.get()?.let { return@coroutineScope it }

        withContext(Dispatchers.Default) {
            val children = JSONArray()
            // Any other 4xx is about the one thing -- a 404 for one that is gone, a 403 for one in a
            // private or quarantined subreddit -- so it is left out, as /api/info leaves out what it
            // cannot return. Unless nothing at all came back: then the block reaches these listings
            // too, and saying so beats an empty list.
            var refused: Response<String>? = null
            for (response in responses) {
                if (response == null) {
                    continue
                }
                if (response.isSuccessful) {
                    response.body()?.let(childOf)?.let { children.put(it) }
                } else if (response.code() != 404 && refused == null) {
                    refused = response
                }
            }
            val firstRefusal = refused
            if (children.length() == 0 && firstRefusal != null) {
                firstRefusal
            } else {
                Response.success(listing(children))
            }
        }
    }

    private fun listing(children: JSONArray): String = JSONObject()
        .put(JSONUtils.KIND_KEY, "Listing")
        .put(
            JSONUtils.DATA_KEY, JSONObject()
                .put(JSONUtils.CHILDREN_KEY, children)
                .put(JSONUtils.AFTER_KEY, JSONObject.NULL)
        )
        .toString()

    companion object {
        /** As in `MoreCommentsLoader`; a page of 25 things costs 25 requests. */
        private const val MAX_CONCURRENT_REQUESTS = 4

        /**
         * Whether `/api/info` has refused this process already. Shared by every lookup, posts and
         * comments alike, since it is the one endpoint.
         */
        @JvmField
        val INFO_BLOCKED = AtomicBoolean(false)
    }
}
