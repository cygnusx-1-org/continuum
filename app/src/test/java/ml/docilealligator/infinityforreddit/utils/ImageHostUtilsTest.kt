package ml.docilealligator.infinityforreddit.utils

import android.net.Uri
import ml.docilealligator.infinityforreddit.TestInfinity
import ml.docilealligator.infinityforreddit.utils.ImageHostUtils.Host
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [ImageHostUtils] decides, from a URL alone, whether a link post is really an album. Everything
 * downstream is keyed off that: `ParsePost` promotes the post to an image card, and the tap opens
 * the album pager instead of a browser. A false positive is the expensive direction -- it turns a
 * working link card into one that opens a viewer which can never resolve anything -- so most of
 * what is pinned here is what must *not* match.
 *
 * The URLs below were taken from live r/anime and r/AdvLiterateRP posts on 2026-09-16.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestInfinity::class)
class ImageHostUtilsTest {

    @Test
    fun bothHostsAndTheImgbbAliasAreRecognised() {
        // ibb.co is what imgbb's own share button produces, so both spellings arrive from Reddit.
        val hosts = mapOf(
            "https://imgchest.com/p/n87wl2angyx" to Host.IMGCHEST,
            "https://imgbb.com/zNBxjX8" to Host.IMGBB,
            "https://ibb.co/zNBxjX8" to Host.IMGBB
        )
        for ((url, expected) in hosts) {
            assertEquals(url, expected, ImageHostUtils.hostOf(Uri.parse(url)))
        }
    }

    @Test
    fun aWwwPrefixAndUpperCaseHostStillMatch() {
        // Reddit hands the URL back as the poster typed it, and shared links pick up www.
        assertEquals(Host.IMGCHEST, ImageHostUtils.hostOf(Uri.parse("https://www.imgchest.com/p/n87wl2angyx")))
        assertEquals(Host.IMGBB, ImageHostUtils.hostOf(Uri.parse("https://IBB.CO/zNBxjX8")))
    }

    @Test
    fun theCdnHostsAreNotLandingPages() {
        // The single most important non-match. These are direct images that ParsePost's ordinary
        // extension branches already handle, and the r/Deltarune post that prompted this feature is
        // one of them. Promoting a cdn.imgchest.com file to an album would send the viewer off to
        // scrape a PNG as HTML.
        assertNull(ImageHostUtils.hostOf(Uri.parse("https://cdn.imgchest.com/files/95805b54f207.png")))
        assertNull(ImageHostUtils.hostOf(Uri.parse("https://i.ibb.co/abc123/name.jpg")))
    }

    @Test
    fun anAlbumIdComesFromTheLastSegment() {
        assertEquals(
            "n87wl2angyx",
            ImageHostUtils.albumIdOf(Host.IMGCHEST, Uri.parse("https://imgchest.com/p/n87wl2angyx"))
        )
        assertEquals(
            "9rydn3x8d4k",
            ImageHostUtils.albumIdOf(Host.IMGCHEST, Uri.parse("https://imgchest.com/p/9rydn3x8d4k"))
        )
        assertEquals(
            "zNBxjX8",
            ImageHostUtils.albumIdOf(Host.IMGBB, Uri.parse("https://ibb.co/zNBxjX8"))
        )
    }

    @Test
    fun imgchestNeedsThePSegment() {
        // imgchest serves plenty of other two-segment paths. Without this check a user page or a
        // tag listing would be promoted to an album and the scrape would find no files.
        assertNull(ImageHostUtils.albumIdOf(Host.IMGCHEST, Uri.parse("https://imgchest.com/u/someone")))
        assertNull(ImageHostUtils.albumIdOf(Host.IMGCHEST, Uri.parse("https://imgchest.com/n87wl2angyx")))
        assertNull(ImageHostUtils.albumIdOf(Host.IMGCHEST, Uri.parse("https://imgchest.com/p/n87wl2angyx/edit")))
        assertNull(ImageHostUtils.albumIdOf(Host.IMGCHEST, Uri.parse("https://imgchest.com/")))
    }

    @Test
    fun imgbbsOwnPagesAreNotAlbums() {
        // imgbb puts these at the same single-segment depth as an image, so the id shape alone
        // cannot tell them apart and the reserved list is the only thing that does.
        for (path in listOf("about", "login", "signup", "upload", "explore", "search", "premium")) {
            assertNull(path, ImageHostUtils.albumIdOf(Host.IMGBB, Uri.parse("https://imgbb.com/$path")))
        }
        // Matched case-insensitively: the path is what the poster typed.
        assertNull(ImageHostUtils.albumIdOf(Host.IMGBB, Uri.parse("https://imgbb.com/About")))
    }

    @Test
    fun imgbbTakesOnlyASingleSegment() {
        assertNull(ImageHostUtils.albumIdOf(Host.IMGBB, Uri.parse("https://ibb.co/album/abcdef")))
        assertNull(ImageHostUtils.albumIdOf(Host.IMGBB, Uri.parse("https://ibb.co/")))
    }

    @Test
    fun anIdOutsideTheLengthBoundsIsRejected() {
        // Loose on purpose -- a wrong accept costs one failed fetch, a wrong reject loses the
        // feature silently -- but not unbounded, or every one-segment path on the site matches.
        assertNull(ImageHostUtils.albumIdOf(Host.IMGBB, Uri.parse("https://ibb.co/abcd")))
        assertNull(ImageHostUtils.albumIdOf(Host.IMGBB, Uri.parse("https://ibb.co/" + "a".repeat(25))))
        assertEquals("abcde", ImageHostUtils.albumIdOf(Host.IMGBB, Uri.parse("https://ibb.co/abcde")))
        assertEquals("a".repeat(24), ImageHostUtils.albumIdOf(Host.IMGBB, Uri.parse("https://ibb.co/" + "a".repeat(24))))
    }

    @Test
    fun anIdIsAlphanumericOnly() {
        assertNull(ImageHostUtils.albumIdOf(Host.IMGBB, Uri.parse("https://ibb.co/has-a-dash")))
        assertNull(ImageHostUtils.albumIdOf(Host.IMGCHEST, Uri.parse("https://imgchest.com/p/has_underscore")))
    }

    @Test
    fun aQueryStringCannotContributeAnId() {
        // Read from the path segments only. Reading the whole URL would let a tracking parameter
        // change which album is fetched.
        assertEquals(
            "n87wl2angyx",
            ImageHostUtils.albumIdOf(Host.IMGCHEST, Uri.parse("https://imgchest.com/p/n87wl2angyx?ref=share"))
        )
        assertNull(ImageHostUtils.albumIdOf(Host.IMGBB, Uri.parse("https://imgbb.com/about?id=zNBxjX8")))
    }

    @Test
    fun aPortOrLookalikeDomainDoesNotMatch() {
        // The authority is compared whole, so a host that merely contains the name is not one.
        assertNull(ImageHostUtils.hostOf(Uri.parse("https://imgchest.com.example.org/p/n87wl2angyx")))
        assertNull(ImageHostUtils.hostOf(Uri.parse("https://notimgchest.com/p/n87wl2angyx")))
        assertNull(ImageHostUtils.hostOf(Uri.parse("https://evil.com/imgbb.com/zNBxjX8")))
    }

    @Test
    fun nonAlbumUrlsPassStraightThrough() {
        // Every link post's URL is offered to hostOf, so anything else has to come back null
        // rather than becoming an image card whose viewer will never resolve.
        val untouched = listOf(
            "https://imgur.com/a/abc123",
            "https://www.reddit.com/r/anime/comments/1j1t9v4/title/",
            "https://i.redd.it/abc123.jpg",
            "https://files.catbox.moe/abc123.png",
            "https://streamable.com/abc123"
        )
        for (url in untouched) {
            assertNull(url, ImageHostUtils.hostOf(Uri.parse(url)))
        }
    }
}
