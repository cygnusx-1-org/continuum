package ml.docilealligator.infinityforreddit.post

import kotlinx.coroutines.runBlocking
import ml.docilealligator.infinityforreddit.apis.RedditAPIKt
import ml.docilealligator.infinityforreddit.thing.AnonymousThingLookup
import ml.docilealligator.infinityforreddit.utils.JSONUtils
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import retrofit2.Response
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Looks up posts by id for the anonymous account's local lists (Saved, Upvoted, Downvoted, Hidden
 * and read history), whose rows hold nothing but the id. `/api/info` first, then each post's own
 * `/comments/<id>` listing once it has refused: see [AnonymousThingLookup].
 */
class AnonymousPostLookup internal constructor(
    fetchInfo: suspend (fullnames: String) -> Response<String>,
    fetchPost: suspend (postId: String) -> Response<String>,
    infoBlocked: AtomicBoolean
) {
    private val lookup = AnonymousThingLookup(
        fetchInfo, infoBlocked, { postId: String -> "t3_$postId" }, fetchPost, ::postChild
    )

    /**
     * @param postIds id36s without the `t3_` prefix, in the order the posts are to appear
     * @return a listing, or the error response to report
     */
    suspend fun load(postIds: List<String>): Response<String> = lookup.load(postIds)

    /** [load] for Java callers already on a background thread. */
    @Throws(IOException::class)
    fun loadBlocking(postIds: List<String>): Response<String> = runBlocking { load(postIds) }

    /** The post in a `/comments/<id>` response: the only child of its first listing. */
    private fun postChild(body: String): JSONObject? = try {
        JSONArray(body).getJSONObject(0).getJSONObject(JSONUtils.DATA_KEY)
            .getJSONArray(JSONUtils.CHILDREN_KEY).optJSONObject(0)
            ?.takeIf { it.optString(JSONUtils.KIND_KEY) == "t3" }
    } catch (e: JSONException) {
        null
    }

    companion object {
        /** [api] must be the anonymous (`no_oauth`) Retrofit's. */
        @JvmStatic
        fun forApi(api: RedditAPIKt) = AnonymousPostLookup(
            { fullnames -> api.getInfo(fullnames) },
            { postId -> api.getPostWithMinimalComments(postId) },
            AnonymousThingLookup.INFO_BLOCKED
        )
    }
}
