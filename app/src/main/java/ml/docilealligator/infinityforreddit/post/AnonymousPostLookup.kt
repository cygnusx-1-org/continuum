package ml.docilealligator.infinityforreddit.post

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import ml.docilealligator.infinityforreddit.apis.RedditAPIKt
import ml.docilealligator.infinityforreddit.utils.JSONUtils
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import retrofit2.Response
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Looks up posts by id for the anonymous account's local lists (Saved, Upvoted, Downvoted, Hidden
 * and read history), whose rows hold nothing but the id.
 *
 * `/api/info` does that in one request, but Reddit has answered it for anonymous requests with a 403
 * "blocked by network security" page since around 2026-10-29 (issue #476), the same block as
 * `/api/morechildren` in #475. A post's own `/comments/<id>` listing still answers, so after a 403
 * each post is fetched from there, and the results are put together into a listing shaped like the
 * one `/api/info` returns, which the callers parse as before. Once `/api/info` has refused, it is not
 * asked again for the rest of the process: every attempt costs a 403, a token refresh and a second
 * 403 in `AnonymousAccessTokenInterceptor`.
 */
class AnonymousPostLookup internal constructor(
    private val fetchInfo: suspend (fullnames: String) -> Response<String>,
    private val fetchPost: suspend (postId: String) -> Response<String>,
    private val infoBlocked: AtomicBoolean
) {
    /**
     * @param postIds id36s without the `t3_` prefix, in the order the posts are to appear
     * @return a listing, or the error response to report
     */
    suspend fun load(postIds: List<String>): Response<String> {
        if (postIds.isEmpty()) {
            return Response.success(listing(JSONArray()))
        }
        if (!infoBlocked.get()) {
            val response = fetchInfo(postIds.joinToString(",") { "t3_$it" })
            if (response.code() != 403) {
                return response
            }
            infoBlocked.set(true)
        }
        return loadIndividually(postIds)
    }

    /** [load] for Java callers already on a background thread. */
    @Throws(IOException::class)
    fun loadBlocking(postIds: List<String>): Response<String> = runBlocking { load(postIds) }

    private suspend fun loadIndividually(postIds: List<String>): Response<String> = coroutineScope {
        val gate = Semaphore(MAX_CONCURRENT_REQUESTS)
        val transientFailure = AtomicReference<Response<String>?>(null)
        val responses = postIds.map { postId ->
            async {
                gate.withPermit {
                    // A 429 or a server error will not be the only one: stop spending requests on
                    // this page and let the retry ask again.
                    if (transientFailure.get() != null) {
                        null
                    } else {
                        fetchPost(postId).also {
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
            // Any other 4xx is about the one post -- a 404 for one that is gone, a 403 for one in a
            // private or quarantined subreddit -- so the post is left out, as /api/info leaves out
            // what it cannot return. Unless nothing at all came back: then the block reaches these
            // listings too, and saying so beats an empty list.
            var refused: Response<String>? = null
            for (response in responses) {
                if (response == null) {
                    continue
                }
                if (response.isSuccessful) {
                    response.body()?.let(::postChild)?.let { children.put(it) }
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

    /** The post in a `/comments/<id>` response: the only child of its first listing. */
    private fun postChild(body: String): JSONObject? = try {
        JSONArray(body).getJSONObject(0).getJSONObject(JSONUtils.DATA_KEY)
            .getJSONArray(JSONUtils.CHILDREN_KEY).optJSONObject(0)
            ?.takeIf { it.optString(JSONUtils.KIND_KEY) == "t3" }
    } catch (e: JSONException) {
        null
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
        /** As in `MoreCommentsLoader`; a page of 25 posts costs 25 requests. */
        private const val MAX_CONCURRENT_REQUESTS = 4

        /** Whether `/api/info` has refused this process already. Shared by every lookup. */
        private val INFO_BLOCKED = AtomicBoolean(false)

        /** [api] must be the anonymous (`no_oauth`) Retrofit's. */
        @JvmStatic
        fun forApi(api: RedditAPIKt) = AnonymousPostLookup(
            { fullnames -> api.getInfo(fullnames) },
            { postId -> api.getPostWithMinimalComments(postId) },
            INFO_BLOCKED
        )
    }
}
