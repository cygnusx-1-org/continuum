package ml.docilealligator.infinityforreddit.post

import java.io.InputStreamReader
import org.json.JSONObject

/**
 * The six r/test posts in `src/test/resources/inlinebodyimage/`, one per arrangement of text and
 * images in a body that embeds its own uploads. They are the real listing payloads, so a test
 * driven by them is a test of what Reddit actually sends.
 *
 * ```
 * text_image        1wfuohp  text, image                 tall 1344x2992
 * image_text        1wfupwu  image, text                 wide 2948x2020
 * text_image_text   1wfuqvm  text, image, text           wide
 * text_image_image  1wfus1y  text, image, image          wide, then 640x657
 * image_image_text  1wfusrn  image, image, text          640x657, then wide
 * image_text_image  1wfutn7  image, text, image          wide, then 640x657
 * ```
 *
 * Re-fetch one with:
 * `python3 ~/git/support-scripts/reddit_fetch_comments.py https://reddit.com/comments/<id> --post`
 */
object InlineBodyImageFixtures {

    /** In body-shape order, which is the order the table above reads in. */
    val NAMES = listOf(
        "text_image",
        "image_text",
        "text_image_text",
        "text_image_image",
        "image_image_text",
        "image_text_image",
    )

    fun json(name: String): JSONObject {
        val path = "inlinebodyimage/$name.json"
        val stream = checkNotNull(javaClass.classLoader?.getResourceAsStream(path)) {
            "missing fixture $path"
        }
        return JSONObject(InputStreamReader(stream).use { it.readText() })
    }

    /** The fixture as the app would have it, i.e. through the real parser. */
    fun post(name: String): Post = ParsePost.parseBasicData(json(name))
}
