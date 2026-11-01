package ml.docilealligator.infinityforreddit.comment

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
 * Fetches the comments saved while logged out, for the Saved screen. `/api/info` first, then each
 * comment's focused thread once it has refused (see [AnonymousThingLookup]); the thread is addressed
 * by post and comment, which is why [AnonymousSavedComment] keeps the post id.
 */
class AnonymousCommentLookup internal constructor(
    fetchInfo: suspend (fullnames: String) -> Response<String>,
    fetchComment: suspend (postId: String, commentId: String) -> Response<String>,
    infoBlocked: AtomicBoolean
) {
    private val lookup = AnonymousThingLookup(
        fetchInfo,
        infoBlocked,
        { saved: AnonymousSavedComment -> "t1_${saved.id}" },
        { saved: AnonymousSavedComment -> fetchComment(saved.linkId, saved.id) },
        ::commentChild
    )

    /**
     * @param saved in the order the comments are to appear
     * @return a listing, or the error response to report
     */
    suspend fun load(saved: List<AnonymousSavedComment>): Response<String> = lookup.load(saved)

    /** [load] for Java callers already on a background thread. */
    @Throws(IOException::class)
    fun loadBlocking(saved: List<AnonymousSavedComment>): Response<String> = runBlocking { load(saved) }

    /**
     * The comment in a focused `/comments/<post>/_/<comment>` response: the only child of its second
     * listing, made to look as it does in `/api/info`. A thread leaves out the post's title, which
     * the Saved list shows, so it is copied over from the post in the first listing; and it carries
     * the comment's first reply, which `/api/info` does not.
     */
    private fun commentChild(body: String): JSONObject? = try {
        val listings = JSONArray(body)
        val comment = listings.getJSONObject(1).getJSONObject(JSONUtils.DATA_KEY)
            .getJSONArray(JSONUtils.CHILDREN_KEY).optJSONObject(0)
            ?.takeIf { it.optString(JSONUtils.KIND_KEY) == "t1" }
        comment?.getJSONObject(JSONUtils.DATA_KEY)?.let { data ->
            data.put(JSONUtils.REPLIES_KEY, "")
            val post = listings.getJSONObject(0).getJSONObject(JSONUtils.DATA_KEY)
                .getJSONArray(JSONUtils.CHILDREN_KEY).optJSONObject(0)?.optJSONObject(JSONUtils.DATA_KEY)
            if (post != null && data.isNull(JSONUtils.LINK_TITLE_KEY)) {
                data.put(JSONUtils.LINK_TITLE_KEY, post.optString(JSONUtils.TITLE_KEY))
            }
        }
        comment
    } catch (e: JSONException) {
        null
    }

    companion object {
        /** [api] must be the anonymous (`no_oauth`) Retrofit's. */
        @JvmStatic
        fun forApi(api: RedditAPIKt) = AnonymousCommentLookup(
            { fullnames -> api.getInfo(fullnames) },
            // limit=1 keeps the reply tree out: one sampled comment came to 6 KB this way against
            // 430 KB without it.
            { postId, commentId -> api.getFocusedComment(postId, commentId, null, 1) },
            AnonymousThingLookup.INFO_BLOCKED
        )
    }
}
