package ml.docilealligator.infinityforreddit.post

import androidx.annotation.VisibleForTesting
import androidx.annotation.WorkerThread
import ml.docilealligator.infinityforreddit.apis.RedditAPI
import ml.docilealligator.infinityforreddit.utils.JSONUtils
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException

/**
 * Holds a submission back until Reddit has processed the images it carries.
 *
 * For about a second after a gallery or a text post with inline images is submitted, the new post's
 * `media_metadata` reports every item as `"status": "unprocessed"`, with no `m` or `s` to build an
 * image from, and a body says `*Processing img <id>...*` where each image goes. The app opens the
 * post the moment the submission succeeds, so it read that window: the gallery showed as a text post
 * and the body as placeholders. Reddit's own web client polls the media status after creating a post
 * for the same reason. This polls the post the same way, briefly, before the success is reported.
 */
object SubmittedPostMedia {

    /** Long enough for the second or so processing takes, short enough not to hold a posting UI. */
    private const val ATTEMPTS = 10
    private const val INTERVAL_MILLIS = 500L

    private const val STATUS_UNPROCESSED = "unprocessed"

    @JvmStatic
    @WorkerThread
    fun awaitProcessed(api: RedditAPI, headers: Map<String, String>, postFullname: String) {
        awaitProcessed(INTERVAL_MILLIS) {
            try {
                api.getInfoOauth(postFullname, headers).execute().body()
            } catch (e: IOException) {
                null
            }
        }
    }

    /**
     * Polls [fetchInfo] (an `/api/info` listing holding the post) until no gallery item is still
     * unprocessed, or the attempts run out. A reply that cannot be read ends the wait: the post
     * exists either way, and the wait is only there to make its first render right.
     *
     * @return how many times the post was fetched
     */
    @VisibleForTesting
    internal fun awaitProcessed(intervalMillis: Long, fetchInfo: () -> String?): Int {
        for (attempt in 1..ATTEMPTS) {
            val info = fetchInfo() ?: return attempt
            val unprocessed =
                try {
                    hasUnprocessedMedia(info)
                } catch (e: JSONException) {
                    return attempt
                }
            if (!unprocessed || attempt == ATTEMPTS) {
                return attempt
            }
            try {
                Thread.sleep(intervalMillis)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                return attempt
            }
        }
        return ATTEMPTS
    }

    /** Whether the post in an `/api/info` listing has a `media_metadata` entry still processing. */
    @VisibleForTesting
    @Throws(JSONException::class)
    internal fun hasUnprocessedMedia(infoListing: String): Boolean {
        val children = JSONObject(infoListing).getJSONObject(JSONUtils.DATA_KEY).getJSONArray(JSONUtils.CHILDREN_KEY)
        if (children.length() == 0) {
            return false
        }
        val metadata = children.getJSONObject(0).getJSONObject(JSONUtils.DATA_KEY)
            .optJSONObject(JSONUtils.MEDIA_METADATA_KEY) ?: return false
        for (key in metadata.keys()) {
            if (STATUS_UNPROCESSED == metadata.optJSONObject(key)?.optString(JSONUtils.STATUS_KEY)) {
                return true
            }
        }
        return false
    }
}
