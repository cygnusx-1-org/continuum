package ml.docilealligator.infinityforreddit.post

import ml.docilealligator.infinityforreddit.TestInfinity
import ml.docilealligator.infinityforreddit.utils.ImageHostUtils
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.File
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [FetchImageHostMedia] is the only thing standing between an album landing page and the pager that
 * shows it. Both hosts are scraped rather than queried -- neither has an anonymous API -- so the
 * contract is entirely about markup this app does not control, and a host re-skinning its page is a
 * question of when rather than whether.
 *
 * What that buys is a sharp distinction worth pinning: the precise route sees the whole album, and
 * `og:image` only ever sees the cover. A regression that silently drops to the fallback turns a
 * twenty-image post into a one-image post, which looks like working software.
 *
 * Fixtures are trimmed from the live `imgchest.com/p/n87wl2angyx` and `ibb.co` pages of
 * 2026-09-16, keeping the shapes the parser actually reads.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestInfinity::class)
class FetchImageHostMediaTest {

    @get:Rule
    val folder = TemporaryFolder()

    @Before
    fun forgetResolvedAlbums() {
        // fetchSync caches by page URL and the cache outlives a test, so without this the first
        // test to read a URL answers for every later one that uses it -- which is the feature
        // working, and a suite that stops testing anything.
        FetchImageHostMedia.clearCacheForTest()
    }

    /**
     * Serves [body] with [contentType] without a network, so the real request building, the
     * markup check and the bounded read all run against a canned page.
     */
    private fun clientServing(body: String, contentType: String? = "text/html", code: Int = 200) =
        OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(code)
                    .message("")
                    .body(body.toResponseBody(contentType?.toMediaType()))
                    .build()
            }
            .build()

    /**
     * The root div imgchest serialises its whole page model into, HTML-escaped as it is served.
     * [cover] is the `og:image` the real page also carries; pass null for a page that has none.
     */
    private fun inertiaPage(
        vararg links: String,
        cover: String? = "https://cdn.imgchest.com/files/cover.jpg"
    ): String {
        val files = links.joinToString(",") { """{&quot;id&quot;:&quot;x&quot;,&quot;link&quot;:&quot;$it&quot;}""" }
        val head = if (cover == null) "" else """<meta property="og:image" content="$cover">"""
        return """
            <html><head>$head</head>
            <body><div id="app" data-page="{&quot;props&quot;:{&quot;post&quot;:{&quot;files&quot;:[$files]}}}"></div></body>
            </html>
        """.trimIndent()
    }

    private fun fetch(client: OkHttpClient, host: ImageHostUtils.Host, url: String) =
        FetchImageHostMedia.fetchSync(client, host, url)

    @Test
    fun theInertiaPayloadYieldsEveryImageInAlbumOrder() {
        // The whole point of reading it. Order matters as much as count: the pager's page numbers
        // are positions in this list, so a reordering makes "Image 4/20" name a different picture
        // than the album does.
        val links = listOf(
            "https://cdn.imgchest.com/files/ye3c2o9pqq4.jpeg",
            "https://cdn.imgchest.com/files/4gdcx96vjk4.jpeg",
            "https://cdn.imgchest.com/files/y8xcngjl5z4.jpg"
        )
        val media = fetch(clientServing(inertiaPage(*links.toTypedArray())), ImageHostUtils.Host.IMGCHEST, "https://imgchest.com/p/n87wl2angyx")

        assertEquals(links, media!!.map { it.link })
    }

    @Test
    fun everyScrapedImageIsTypedAsAnImage() {
        // ViewImgurMediaActivity keys its per-page fragment off this. Neither host serves video, so
        // a mistyped entry would hand a still picture to the video fragment.
        val media = fetch(
            clientServing(inertiaPage("https://cdn.imgchest.com/files/ye3c2o9pqq4.jpeg")),
            ImageHostUtils.Host.IMGCHEST, "https://imgchest.com/p/n87wl2angyx"
        )

        assertEquals(ImgurMedia.TYPE_IMAGE, media!!.single().type)
    }

    @Test
    fun anImageIsIdentifiedByItsCdnFilename() {
        // The id ends up in the download filename, and MediaFileNameUtils uses it to keep two
        // albums that happen to share a title from colliding. Numbering by position would put
        // "0" on the first image of every album, which is the collision it exists to prevent.
        val media = fetch(
            clientServing(
                inertiaPage(
                    "https://cdn.imgchest.com/files/ye3c2o9pqq4.jpeg",
                    "https://cdn.imgchest.com/files/4gdcx96vjk4.jpeg"
                )
            ),
            ImageHostUtils.Host.IMGCHEST, "https://imgchest.com/p/n87wl2angyx"
        )

        assertEquals(listOf("ye3c2o9pqq4", "4gdcx96vjk4"), media!!.map { it.id })
    }

    @Test
    fun aQueryOnTheCdnLinkIsNotPartOfTheId() {
        val media = fetch(
            clientServing(inertiaPage("https://cdn.imgchest.com/files/ye3c2o9pqq4.jpeg?w=1200")),
            ImageHostUtils.Host.IMGCHEST, "https://imgchest.com/p/n87wl2angyx"
        )

        assertEquals("ye3c2o9pqq4", media!!.single().id)
    }

    @Test
    fun imgchestFallsBackToOgImageWhenThePayloadIsGone() {
        // The fallback is the cover only, which is the degradation to accept rather than showing
        // nothing -- but it is a degradation, so it must not be what a healthy page takes.
        val page = """
            <html><head><meta property="og:image" content="https://cdn.imgchest.com/files/cover.jpg"></head>
            <body><div id="app"></div></body></html>
        """.trimIndent()
        val media = fetch(clientServing(page), ImageHostUtils.Host.IMGCHEST, "https://imgchest.com/p/n87wl2angyx")

        assertEquals(listOf("https://cdn.imgchest.com/files/cover.jpg"), media!!.map { it.link })
    }

    @Test
    fun aMalformedPayloadFallsBackRatherThanFailing() {
        // Inertia is still there but the shape underneath it has moved. Returning null from the
        // precise route (not an empty list) is what lets og:image still answer.
        val page = """
            <html><head><meta property="og:image" content="https://cdn.imgchest.com/files/cover.jpg"></head>
            <body><div id="app" data-page="{&quot;props&quot;:{&quot;renamed&quot;:{}}}"></div></body></html>
        """.trimIndent()
        val media = fetch(clientServing(page), ImageHostUtils.Host.IMGCHEST, "https://imgchest.com/p/n87wl2angyx")

        assertEquals(listOf("https://cdn.imgchest.com/files/cover.jpg"), media!!.map { it.link })
    }

    @Test
    fun imgbbReadsItsOgImage() {
        // imgbb writes property before content; the reversed order is accepted too because neither
        // host guarantees it and swapping would silently cost the only route this host has.
        val forward = """<html><head><meta property="og:image" content="https://i.ibb.co/abc/pic.png"></head></html>"""
        val reversed = """<html><head><meta content="https://i.ibb.co/abc/pic.png" property="og:image"></head></html>"""
        // Distinct URLs: one cache entry would otherwise answer for both orders and only the
        // first would really be parsed.
        for ((index, page) in listOf(forward, reversed).withIndex()) {
            val media = fetch(clientServing(page), ImageHostUtils.Host.IMGBB, "https://ibb.co/order$index")
            assertEquals(page, listOf("https://i.ibb.co/abc/pic.png"), media!!.map { it.link })
        }
    }

    @Test
    fun ampIsDecodedLastSoAnEscapedQuoteStaysText() {
        // A post title is user-supplied and lives inside this JSON. Decoding &amp; first would turn
        // &amp;quot; into a real quote and terminate the string early, mangling everything after
        // it -- which for a 20-image album means losing 19 of them to a title someone typed.
        val page = """
            <html><body><div data-page="{&quot;props&quot;:{&quot;post&quot;:{&quot;title&quot;:&quot;a &amp;quot;quoted&amp;quot; name&quot;,&quot;files&quot;:[{&quot;link&quot;:&quot;https://cdn.imgchest.com/files/one.jpg&quot;},{&quot;link&quot;:&quot;https://cdn.imgchest.com/files/two.jpg&quot;}]}}}"></div></body></html>
        """.trimIndent()
        val media = fetch(clientServing(page), ImageHostUtils.Host.IMGCHEST, "https://imgchest.com/p/n87wl2angyx")

        assertEquals(
            listOf("https://cdn.imgchest.com/files/one.jpg", "https://cdn.imgchest.com/files/two.jpg"),
            media!!.map { it.link }
        )
    }

    @Test
    fun anEmptyFileListStillFallsBackToTheCover() {
        // An album whose files array is present but empty is the same situation as one whose
        // payload moved: the precise route has nothing, so og:image answers instead of the whole
        // page being treated as a failure.
        val media = fetch(clientServing(inertiaPage()), ImageHostUtils.Host.IMGCHEST, "https://imgchest.com/p/n87wl2angyx")

        assertEquals(listOf("https://cdn.imgchest.com/files/cover.jpg"), media!!.map { it.link })
    }

    @Test
    fun anAlbumWithNothingLeftResolvesToNothing() {
        // No files and no cover. This has to read as "could not resolve" so the caller shows its
        // retry view, rather than opening a pager with no pages in it.
        assertNull(
            fetch(
                clientServing(inertiaPage(cover = null)),
                ImageHostUtils.Host.IMGCHEST, "https://imgchest.com/p/n87wl2angyx"
            )
        )
    }

    @Test
    fun aPageWithNoImageAtAllResolvesToNothing() {
        assertNull(
            fetch(
                clientServing("<html><head><title>Not found</title></head></html>"),
                ImageHostUtils.Host.IMGBB, "https://ibb.co/zNBxjX8"
            )
        )
    }

    @Test
    fun aNonMarkupResponseIsRejectedBeforeItIsParsed() {
        // A poster can link anything. Running the regexes over a binary body is the hazard
        // HtmlBodyUtils exists to head off.
        assertNull(
            fetch(
                clientServing(inertiaPage("https://cdn.imgchest.com/files/one.jpg"), contentType = "video/mp4"),
                ImageHostUtils.Host.IMGCHEST, "https://imgchest.com/p/n87wl2angyx"
            )
        )
    }

    @Test
    fun anErrorResponseResolvesToNothingEvenWhenItLooksLikeAnAlbum() {
        // The status is checked before the markup, and the body here is deliberately a page that
        // would otherwise parse perfectly -- serving a plausible-looking error page is exactly what
        // these sites do. Reading only the markup would resolve a deleted album to whatever
        // placeholder graphic the 404 page carries and show that instead of the retry view.
        assertNull(
            fetch(
                clientServing(inertiaPage("https://cdn.imgchest.com/files/one.jpg"), code = 404),
                ImageHostUtils.Host.IMGCHEST, "https://imgchest.com/p/n87wl2angyx"
            )
        )
    }

    @Test
    fun aMalformedPageUrlResolvesToNothing() {
        // The URL comes from the post, so it is only as well-formed as what the poster pasted.
        assertNull(
            fetch(
                clientServing(inertiaPage("https://cdn.imgchest.com/files/one.jpg")),
                ImageHostUtils.Host.IMGCHEST, "not a url"
            )
        )
    }

    /**
     * An album read in one run is still an album in the next.
     *
     * This is what the persistence is for, and it is invisible without a restart: within a session
     * the in-memory cache answers either way. A cold start with nothing stored re-seeds the post with
     * its single cover tile, and a one-tile carousel reads 1/1, cannot be swiped, and jumps to the
     * right image a beat later when the scrape lands -- which is the whole defect.
     */
    @Test
    fun `a resolved album survives a restart`() {
        val dir = folder.newFolder("files")
        FetchImageHostMedia.resetForTest(dir)
        val links = listOf(
            "https://cdn.imgchest.com/files/one.jpg",
            "https://cdn.imgchest.com/files/two.jpg",
            "https://cdn.imgchest.com/files/three.jpg"
        )
        fetch(
            clientServing(inertiaPage(*links.toTypedArray())),
            ImageHostUtils.Host.IMGCHEST, "https://imgchest.com/p/n87wl2angyx"
        )

        FetchImageHostMedia.flushForTest()
        FetchImageHostMedia.resetForTest(dir)

        // No client that could answer: anything returned here came off the disk.
        val gallery = FetchImageHostMedia.cachedGallery(
            "https://imgchest.com/p/n87wl2angyx", "anime", "1j1t9v4")
        assertNotNull("the album was not stored", gallery)
        assertEquals(links, gallery!!.map { it.url })
    }

    @Test
    fun `an album that could not be read is not remembered as one that failed`() {
        // A host having a bad minute must not leave a card as a placeholder for a day. Storing the
        // failure would do exactly that, so only real albums are written.
        val dir = folder.newFolder("files")
        FetchImageHostMedia.resetForTest(dir)
        fetch(
            clientServing("<html><head><title>Not found</title></head></html>"),
            ImageHostUtils.Host.IMGCHEST, "https://imgchest.com/p/n87wl2angyx"
        )

        FetchImageHostMedia.flushForTest()
        FetchImageHostMedia.resetForTest(dir)

        assertNull(
            FetchImageHostMedia.cachedGallery(
                "https://imgchest.com/p/n87wl2angyx", "anime", "1j1t9v4"))
    }

    @Test
    fun `a single image is not stored as an album`() {
        // One picture is not something to swipe through, and the cover tile the post is seeded with
        // is Reddit's own preview -- a better thing to draw than a CDN file.
        val dir = folder.newFolder("files")
        FetchImageHostMedia.resetForTest(dir)
        fetch(
            clientServing(inertiaPage("https://cdn.imgchest.com/files/one.jpg")),
            ImageHostUtils.Host.IMGCHEST, "https://imgchest.com/p/n87wl2angyx"
        )

        FetchImageHostMedia.flushForTest()
        FetchImageHostMedia.resetForTest(dir)

        assertNull(
            FetchImageHostMedia.cachedGallery(
                "https://imgchest.com/p/n87wl2angyx", "anime", "1j1t9v4"))
    }

    @Test
    fun `an unreadable store is a store with no albums in it`() {
        val dir = folder.newFolder("files")
        File(dir, "image_host_albums.json").writeText("{not json")
        FetchImageHostMedia.resetForTest(dir)

        assertNull(
            FetchImageHostMedia.cachedGallery(
                "https://imgchest.com/p/n87wl2angyx", "anime", "1j1t9v4"))
    }
}
