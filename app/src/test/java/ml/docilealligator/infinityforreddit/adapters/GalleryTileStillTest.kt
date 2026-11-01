package ml.docilealligator.infinityforreddit.adapters

import java.io.InputStreamReader
import ml.docilealligator.infinityforreddit.TestInfinity
import ml.docilealligator.infinityforreddit.post.ParsePost
import ml.docilealligator.infinityforreddit.post.Post
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Which still an inline gallery tile loads for its width, against r/lotr 1xaas0t
 * (`src/test/resources/gallery/jpeg_png.json`), parsed by the real parser:
 *
 * ```
 * item 0  zv2kb6000iwh1  1500x1101  downscales 108 216 320 640 960 1080
 * item 1  k9f1jp700iwh1   507x604   downscales 108 216 320
 * ```
 *
 * The bug this pins: every tile took the widest downscale, which Reddit stops at 1080px, so on a
 * 1440px-wide screen every gallery was stretched by a third.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestInfinity::class)
class GalleryTileStillTest {

    private val json: JSONObject = run {
        val path = "gallery/jpeg_png.json"
        val stream = checkNotNull(javaClass.classLoader?.getResourceAsStream(path)) {
            "missing fixture $path"
        }
        JSONObject(InputStreamReader(stream).use { it.readText() })
    }
    private val gallery: List<Post.Gallery> = ParsePost.parseBasicData(json).gallery

    private fun downscaleUrl(mediaId: String, width: Int): String {
        val downscales = json.getJSONObject("media_metadata").getJSONObject(mediaId).getJSONArray("p")
        for (i in 0 until downscales.length()) {
            val downscale = downscales.getJSONObject(i)
            if (downscale.getInt("x") == width) {
                return downscale.getString("u")
            }
        }
        throw AssertionError("$mediaId has no ${width}px downscale")
    }

    private fun url(item: Post.Gallery, tileWidthPx: Int, maxResolution: Int = 5_000_000,
                    dataSaving: Boolean = false): String? =
        GalleryTileStill.url(item, tileWidthPx, maxResolution, dataSaving)

    @Test
    fun `a 1080px tile takes the 1080px downscale, not the source`() {
        assertEquals(downscaleUrl("zv2kb6000iwh1", 1080), url(gallery[0], 1080))
    }

    @Test
    fun `a tile between two downscales takes the next one up`() {
        assertEquals(downscaleUrl("zv2kb6000iwh1", 960), url(gallery[0], 700))
    }

    @Test
    fun `a tile wider than every downscale takes the source`() {
        assertEquals(gallery[0].url, url(gallery[0], 1440))
        // Reddit made nothing past 320px of this one, so even a phone-width tile needs the source.
        assertEquals(gallery[1].url, url(gallery[1], 1080))
    }

    @Test
    fun `a source over the max resolution leaves the tile on the widest downscale`() {
        // 1500x1101 is 1,651,500 pixels.
        assertEquals(downscaleUrl("zv2kb6000iwh1", 1080), url(gallery[0], 1440, maxResolution = 1_651_499))
        assertEquals(gallery[0].url, url(gallery[0], 1440, maxResolution = 1_651_500))
    }

    @Test
    fun `data saving never takes the source`() {
        assertEquals(downscaleUrl("zv2kb6000iwh1", 1080), url(gallery[0], 1440, dataSaving = true))
    }

    @Test
    fun `an unknown width takes the widest downscale, as every tile used to`() {
        assertEquals(gallery[0].feedPreviewUrl, url(gallery[0], 0))
        assertEquals(downscaleUrl("zv2kb6000iwh1", 1080), url(gallery[0], 0))
    }

    @Test
    fun `a gif never takes its source, the animation itself`() {
        val gif = Post.Gallery("image/gif", "https://i.redd.it/x.gif", "", "x.gif", "", "")
        gif.downscales = arrayListOf(
            Post.Preview("https://preview.redd.it/x.gif?width=640", 640, 360, "", ""),
            Post.Preview("https://preview.redd.it/x.gif?width=1080", 1080, 608, "", ""),
        )
        gif.feedPreviewUrl = "https://preview.redd.it/x.gif?width=1080"
        gif.sourceWidth = 1280
        gif.sourceHeight = 720

        assertEquals("https://preview.redd.it/x.gif?width=1080", url(gif, 1440))
    }

    @Test
    fun `an item with no downscale is its own still if it is an image, and has none if it is a gif`() {
        val image = Post.Gallery("image/jpeg", "https://i.imgchest.com/a.jpg", "", "a.jpg", "", "")
        val gif = Post.Gallery("image/gif", "https://i.imgchest.com/a.gif", "", "a.gif", "", "")

        assertEquals("https://i.imgchest.com/a.jpg", url(image, 1440))
        assertNull(url(gif, 1440))
    }
}
