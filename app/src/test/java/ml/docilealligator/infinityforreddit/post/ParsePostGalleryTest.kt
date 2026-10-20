package ml.docilealligator.infinityforreddit.post

import java.io.InputStreamReader
import ml.docilealligator.infinityforreddit.TestInfinity
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Reddit gallery posts through the real parser, from the listing payloads in
 * `src/test/resources/gallery/`.
 *
 * ```
 * jpeg_png   1xaas0t  r/lotr               image/jpeg, image/jpeg, image/png  no body
 * jpeg_body  1xag53f  r/IllegallySmolCats  image/jpeg x10                     body text
 * jpg        1xahglw  r/mildlyinfuriating  image/jpg x2                       no body
 * ```
 *
 * Reddit's `m` field said image/jpg for years, and since October 2026 says image/jpeg for most new
 * uploads; both spellings are live at once. Matching only the old one read every image/jpeg item as
 * a video, the missing `mp4` key threw, and the post lost its whole gallery and was shown as a text
 * post with no images (issue #459).
 *
 * Re-fetch one with:
 * `python3 ~/git/support-scripts/reddit_fetch_comments.py https://reddit.com/comments/<id> --post`
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestInfinity::class)
class ParsePostGalleryTest {

    private fun json(name: String): JSONObject {
        val path = "gallery/$name.json"
        val stream = checkNotNull(javaClass.classLoader?.getResourceAsStream(path)) {
            "missing fixture $path"
        }
        return JSONObject(InputStreamReader(stream).use { it.readText() })
    }

    private fun sourceUrl(json: JSONObject, mediaId: String): String =
        json.getJSONObject("media_metadata").getJSONObject(mediaId).getJSONObject("s").getString("u")

    @Test
    fun `an image-jpeg gallery stays a gallery`() {
        val json = json("jpeg_png")
        val post = ParsePost.parseBasicData(json)

        assertEquals(Post.GALLERY_TYPE, post.postType)
        // Demotion to text nulls the url, so this also pins that the post was never demoted.
        assertEquals("https://www.reddit.com/gallery/1xaas0t", post.url)
        assertEquals(
            listOf("lotr-zv2kb6000iwh1.jpeg", "lotr-k9f1jp700iwh1.jpeg", "lotr-uiaucc900iwh1.png"),
            post.gallery.map { it.fileName }
        )
        post.gallery.forEach { assertEquals(it.fileName, Post.Gallery.TYPE_IMAGE, it.mediaType) }
        assertEquals(sourceUrl(json, "zv2kb6000iwh1"), post.gallery[0].url)
    }

    @Test
    fun `an image-jpeg item falls back to i-redd-it with its own extension`() {
        val post = ParsePost.parseBasicData(json("jpeg_png"))

        // The extension comes from the MIME type, which is what i.redd.it serves the file under:
        // zv2kb6000iwh1.jpeg answers 200 and zv2kb6000iwh1.jpg answers 404.
        assertTrue(post.gallery[0].hasFallback())
        assertEquals("https://i.redd.it/zv2kb6000iwh1.jpeg", post.gallery[0].fallbackUrl)
        assertEquals("https://i.redd.it/uiaucc900iwh1.png", post.gallery[2].fallbackUrl)
    }

    @Test
    fun `an image-jpeg gallery takes its card preview from the first item`() {
        val json = json("jpeg_png")
        val post = ParsePost.parseBasicData(json)

        // Gallery posts carry no `preview` block, so this is the only preview the card gets.
        assertEquals(1, post.previews.size)
        assertEquals(sourceUrl(json, "zv2kb6000iwh1"), post.previews[0].previewUrl)
        assertEquals(1500, post.previews[0].previewWidth)
        assertEquals(1101, post.previews[0].previewHeight)
    }

    @Test
    fun `an image-jpeg gallery with a body keeps both`() {
        val post = ParsePost.parseBasicData(json("jpeg_body"))

        assertEquals(Post.GALLERY_TYPE, post.postType)
        assertEquals(10, post.gallery.size)
        val selfText = post.selfText.orEmpty()
        assertTrue(selfText, selfText.startsWith("Photos range from 3ish weeks to 14 weeks."))
    }

    @Test
    fun `an image-jpg gallery still parses`() {
        val post = ParsePost.parseBasicData(json("jpg"))

        assertEquals(Post.GALLERY_TYPE, post.postType)
        assertEquals(
            listOf("mildlyinfuriating-0mol4nrojjwh1.jpg", "mildlyinfuriating-5bc6y34pjjwh1.jpg"),
            post.gallery.map { it.fileName }
        )
        assertEquals("https://i.redd.it/0mol4nrojjwh1.jpg", post.gallery[0].fallbackUrl)
    }

    @Test
    fun `an item read under the wrong type is skipped and the rest survive`() {
        // A type the parser routes to a key the source does not have -- here a video whose `s` holds
        // only the still-image `u`. It used to throw, and the catch discarded the items already read.
        val json = json("jpeg_png")
        json.getJSONObject("media_metadata").getJSONObject("k9f1jp700iwh1").put("m", "video/mp4")
        val post = ParsePost.parseBasicData(json)

        assertEquals(Post.GALLERY_TYPE, post.postType)
        assertEquals(
            listOf("lotr-zv2kb6000iwh1.jpeg", "lotr-uiaucc900iwh1.png"),
            post.gallery.map { it.fileName }
        )
    }

    @Test
    fun `every image type except gif is a still`() {
        listOf("image/jpg", "image/jpeg", "image/png", "image/webp").forEach {
            assertTrue(it, Post.Gallery.isStillImage(it))
        }
        listOf("image/gif", "video/mp4").forEach {
            assertFalse(it, Post.Gallery.isStillImage(it))
        }
    }
}
