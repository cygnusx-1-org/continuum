package ml.docilealligator.infinityforreddit.comment

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
 * Fetches saved comments by id for the Saved screen: the anonymous account's, and a logged-in
 * account's Local Saved ones. One `/api/info` request, or each comment's focused thread, as
 * [InfoUse] says (see [ThingLookup]); the thread is addressed by post and comment, which is why
 * [AnonymousSavedComment] and `LocalSavedThing` keep the post id.
 */
class CommentLookup internal constructor(
    fetchInfo: suspend (fullnames: String) -> Response<String>,
    fetchComment: suspend (postId: String, commentId: String) -> Response<String>,
    infoUse: InfoUse
) {
    /**
     * A comment to look up.
     *
     * @param id the comment's id36, without `t1_`
     * @param linkId the id36 of the post it is on, without `t3_`. Null for a Local Saved comment
     *   saved before the post was recorded, which only `/api/info` can find: without it, the
     *   comment is left out, and it comes back once `/api/info` is used and answers.
     */
    class Ref(val id: String, val linkId: String?)

    private val lookup = ThingLookup(
        fetchInfo,
        infoUse,
        { ref: Ref -> "t1_${ref.id}" },
        { ref: Ref -> ref.linkId?.let { fetchComment(it, ref.id) } },
        ::commentChild
    )

    /**
     * @param refs in the order the comments are to appear
     * @return a listing, or the error response to report
     */
    suspend fun load(refs: List<Ref>): Response<String> = lookup.load(refs)

    /** [load] for Java callers already on a background thread. */
    @Throws(IOException::class)
    fun loadBlocking(refs: List<Ref>): Response<String> = runBlocking { load(refs) }

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
        /**
         * For the anonymous account: `/api/info` first, then each comment once it has refused.
         * [api] must be the anonymous (`no_oauth`) Retrofit's.
         */
        @JvmStatic
        fun forApi(api: RedditAPIKt) = CommentLookup(
            { fullnames -> api.getInfo(fullnames) },
            // limit=1 keeps the reply tree out: one sampled comment came to 6 KB this way against
            // 430 KB without it.
            { postId, commentId -> api.getFocusedComment(postId, commentId, null, 1) },
            InfoUse.First(ThingLookup.INFO_BLOCKED)
        )

        /**
         * For a logged-in account. [api] must be the OAuth Retrofit's, so that both requests carry
         * the account's own vote and saved state on each comment, as `/api/info` does.
         *
         * @param useOldApiInfo `APIUtils.useOldApiInfo()`: `/api/info` alone if set, else never
         */
        @JvmStatic
        fun forOauthApi(api: RedditAPIKt, headers: Map<String, String>, useOldApiInfo: Boolean) = CommentLookup(
            { fullnames -> api.getInfoOauth(fullnames, headers) },
            { postId, commentId -> api.getFocusedCommentOauth(postId, commentId, null, 1, headers) },
            InfoUse.forAccount(useOldApiInfo)
        )
    }
}
