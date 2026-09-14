package ml.docilealligator.infinityforreddit.post

import java.io.InputStreamReader
import org.json.JSONObject

/**
 * Three r/copypasta posts in `src/test/resources/redditpreview/`: text posts whose image is not
 * theirs at all, but a preview Reddit built from a link in the body. They are the other half of
 * [InlineBodyImageFixtures] -- the same card, the same `TEXT_TYPE`, and the opposite order, because
 * the post detail draws a preview like this above the selftext instead of inside it.
 *
 * ```
 * text_link_end       1w9nv8u  body, then a bare youtube link  480x360  external-preview
 * text_link_middle    1weyevh  long body with a link part way  1200x800 external-preview
 * text_link_labelled  1vxmew0  body, then [Hier Abonnieren]()  480x360  external-preview
 * ```
 *
 * All three carry `"enabled": false` on that preview, which is what Reddit says about an external
 * preview on a self post; the app shows it regardless, so these fixtures also pin that.
 *
 * Re-fetch one with:
 * `python3 ~/git/support-scripts/reddit_fetch_comments.py https://reddit.com/comments/<id> --post`
 */
object RedditPreviewFixtures {

    /** In the order the table above reads in. */
    val NAMES = listOf(
        "text_link_end",
        "text_link_middle",
        "text_link_labelled",
    )

    fun json(name: String): JSONObject {
        val path = "redditpreview/$name.json"
        val stream = checkNotNull(javaClass.classLoader?.getResourceAsStream(path)) {
            "missing fixture $path"
        }
        return JSONObject(InputStreamReader(stream).use { it.readText() })
    }

    /** The fixture as the app would have it, i.e. through the real parser. */
    fun post(name: String): Post = ParsePost.parseBasicData(json(name))
}
