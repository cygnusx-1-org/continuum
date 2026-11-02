package ml.docilealligator.infinityforreddit.randomsubreddit

import ml.docilealligator.infinityforreddit.utils.JSONUtils
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Reads the survivors out of one `/api/info?sr_name=...` batch response, or out of one
 * `/r/<name>/about` response when `/api/info` refuses.
 *
 * Reddit answers a batch of names with a listing that carries only what it could resolve, and it
 * has two different ways of saying no:
 *
 *  - a banned or private subreddit comes back as a `t5` whose `subscribers` is null
 *  - a deleted or never-existing one is simply **absent** from the response
 *
 * Both mean "do not send anyone here", and because this reads the response rather than the request,
 * an absent name is excluded by construction.
 */
object RandomSubredditInfoParser {

    private const val SUBREDDIT_KIND = "t5"

    /**
     * How many posts a subreddit listing response carries.
     *
     * The pick asks for a single post purely to learn whether there are any, so this counts
     * children rather than reading them. A body that cannot be parsed counts as -1, which the
     * caller must read as "could not tell" and not as "empty" -- a network or parse failure is no
     * reason to discard a subreddit.
     */
    fun countPosts(response: String): Int {
        return try {
            JSONObject(response)
                .getJSONObject(JSONUtils.DATA_KEY)
                .getJSONArray(JSONUtils.CHILDREN_KEY)
                .length()
        } catch (e: JSONException) {
            UNKNOWN_POST_COUNT
        }
    }

    /** [countPosts] could not tell. Distinct from 0, which means the subreddit really is empty. */
    const val UNKNOWN_POST_COUNT = -1

    /**
     * @param requireOver18 what the live `over18` flag has to say for a name to be kept. The flag
     *   drifts -- thousands of subreddits flipped SFW to NSFW inside the window the source data
     *   covers -- so it is re-checked here against live data rather than trusted from the file.
     */
    fun parseLiveSubredditNames(response: String, requireOver18: Boolean): List<String> {
        val children = try {
            JSONObject(response)
                .getJSONObject(JSONUtils.DATA_KEY)
                .getJSONArray(JSONUtils.CHILDREN_KEY)
        } catch (e: JSONException) {
            return emptyList()
        }

        val names = ArrayList<String>(children.length())
        for (i in 0 until children.length()) {
            val child = children.optJSONObject(i) ?: continue
            if (child.optString(JSONUtils.KIND_KEY) != SUBREDDIT_KIND) {
                continue
            }
            val data = child.optJSONObject(JSONUtils.DATA_KEY) ?: continue
            if (data.isNull(JSONUtils.SUBSCRIBERS_KEY)) {
                continue
            }
            if (data.optBoolean(JSONUtils.OVER18_KEY, false) != requireOver18) {
                continue
            }
            // isNull before optString: Android's org.json coerces a JSON null to the *string*
            // "null" rather than to "", so reading this straight would hand back a survivor named
            // "null" and send someone to r/null.
            if (data.isNull(JSONUtils.DISPLAY_NAME_KEY)) {
                continue
            }
            val name = data.optString(JSONUtils.DISPLAY_NAME_KEY)
            if (name.isNotEmpty()) {
                names.add(name)
            }
        }
        return names
    }

    /**
     * The name in one `/r/<name>/about` response, if it passes the checks [parseLiveSubredditNames]
     * makes; else null. The response is the same `t5` an `/api/info` batch holds, so it is checked
     * as a batch of one.
     */
    fun parseLiveSubredditName(aboutResponse: String, requireOver18: Boolean): String? {
        val about = try {
            JSONObject(aboutResponse)
        } catch (e: JSONException) {
            return null
        }
        val listing = JSONObject().put(JSONUtils.DATA_KEY, JSONObject().put(JSONUtils.CHILDREN_KEY, JSONArray().put(about)))
        return parseLiveSubredditNames(listing.toString(), requireOver18).firstOrNull()
    }
}
