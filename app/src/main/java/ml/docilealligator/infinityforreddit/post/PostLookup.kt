package ml.docilealligator.infinityforreddit.post

import kotlinx.coroutines.runBlocking
import ml.docilealligator.infinityforreddit.apis.RedditAPIKt
import ml.docilealligator.infinityforreddit.thing.InfoUse
import ml.docilealligator.infinityforreddit.thing.ThingLookup
import ml.docilealligator.infinityforreddit.utils.JSONUtils
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import retrofit2.Response
import java.io.IOException

/**
 * Looks up posts by id for the lists whose rows hold nothing but the id: read history, Local Saved,
 * and the anonymous account's Saved, Upvoted, Downvoted and Hidden. One `/api/info` request, or
 * each post's own `/comments/<id>` listing, as [InfoUse] says: see [ThingLookup].
 */
class PostLookup internal constructor(
    fetchInfo: suspend (fullnames: String) -> Response<String>,
    fetchPost: suspend (postId: String) -> Response<String>,
    infoUse: InfoUse
) {
    private val lookup = ThingLookup(
        fetchInfo, infoUse, { postId: String -> "t3_$postId" }, fetchPost, ::postChild
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
        /**
         * For the anonymous account: `/api/info` first, then each post once it has refused. [api]
         * must be the anonymous (`no_oauth`) Retrofit's.
         */
        @JvmStatic
        fun forApi(api: RedditAPIKt) = PostLookup(
            { fullnames -> api.getInfo(fullnames) },
            { postId -> api.getPostWithMinimalComments(postId) },
            InfoUse.First(ThingLookup.INFO_BLOCKED)
        )

        /**
         * For a logged-in account. [api] must be the OAuth Retrofit's, so that both requests carry
         * the account's own vote, saved and hidden state on each post, as `/api/info` does.
         *
         * @param useOldApiInfo `APIUtils.useOldApiInfo()`: `/api/info` alone if set, else never
         */
        @JvmStatic
        fun forOauthApi(api: RedditAPIKt, headers: Map<String, String>, useOldApiInfo: Boolean) = PostLookup(
            { fullnames -> api.getInfoOauth(fullnames, headers) },
            { postId -> api.getPostWithMinimalCommentsOauth(postId, headers) },
            InfoUse.forAccount(useOldApiInfo)
        )
    }
}
