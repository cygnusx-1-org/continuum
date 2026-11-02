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
 * Looks things up by fullname for the lists whose rows hold little more than an id: posts through
 * `PostLookup` (read history, Local Saved, and the anonymous account's saved and voted posts),
 * comments through `CommentLookup`.
 *
 * `/api/info` does that in one request, but Reddit has answered it with a 403 "blocked by network
 * security" page since around 2026-10-29 (issue #476), the same block as `/api/morechildren` in
 * #475. The block follows the client ID, so it refuses logged-in and anonymous requests alike, and
 * every form of the endpoint: `id=`, `sr_name=`, `url=`, `/r/<sub>/api/info` and `/by_id`. A thing's
 * own `/comments/...` listing still answers, so each thing can be fetched from there instead, and the
 * results are put together into a listing shaped like the one `/api/info` returns, which the callers
 * parse as before. Which of the two is used is [infoUse]'s to say.
 *
 * @param infoUse whether `/api/info` is asked first, alone, or not at all
 * @param fullname the name `/api/info` is asked for, `t3_` or `t1_` included
 * @param fetchOne the thing's own listing, for when `/api/info` has refused, or null when the thing
 *   cannot be fetched on its own; it is then left out, as one that is gone would be
 * @param childOf the thing's listing child in a [fetchOne] response, or null when it is not there
 */
internal class ThingLookup<T>(
    private val fetchInfo: suspend (fullnames: String) -> Response<String>,
    private val infoUse: InfoUse,
    private val fullname: (T) -> String,
    private val fetchOne: suspend (T) -> Response<String>?,
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
        return when (infoUse) {
            InfoUse.Only -> fetchInfo(things.joinToString(",", transform = fullname))
            InfoUse.Never -> loadIndividually(things)
            is InfoUse.First -> {
                if (!infoUse.blocked.get()) {
                    val response = fetchInfo(things.joinToString(",", transform = fullname))
                    if (response.code() != 403) {
                        return response
                    }
                    infoUse.blocked.set(true)
                }
                loadIndividually(things)
            }
        }
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
                        fetchOne(thing)?.also {
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
         * Whether `/api/info` has refused the anonymous account in this process already, for
         * [InfoUse.First]: every attempt costs a 403, a token refresh and a second 403 in
         * `AnonymousAccessTokenInterceptor`. Shared by every anonymous lookup, posts and comments
         * alike, and by the random subreddit pick, since it is the one endpoint and the one client
         * ID, which only changes across a restart.
         */
        @JvmField
        val INFO_BLOCKED = AtomicBoolean(false)
    }
}
