package ml.docilealligator.infinityforreddit.utils

import android.net.Uri
import ml.docilealligator.infinityforreddit.TestInfinity
import ml.docilealligator.infinityforreddit.utils.ShortClipHostUtils.Host
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [ShortClipHostUtils] decides, from a URL alone, whether a link post is a playable clip and where
 * its MP4 might be. Everything downstream -- classification, the resolver's probes, the fullscreen
 * intent -- is keyed off what these functions return, so a mistake here is invisible until a feed
 * shows a row that never plays.
 *
 * The URLs below were taken from live r/soccer, r/formula1 and r/MMA posts on 2026-09-09, one per
 * shape each host actually publishes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestInfinity::class)
class ShortClipHostUtilsTest {

    @Test
    fun everyAliasIsRecognised() {
        // These sites hand out new top-level domains regularly and posters use whichever one the
        // share button gave them, so the alias list is the feature's real surface area.
        val hosts = mapOf(
            "https://streamain.com/en/7OWwVWjPv6GNI4W/watch" to Host.STREAMAIN,
            "https://streama.in/le8F08XutVuuz7r/watch" to Host.STREAMAIN,
            "https://streamin.link/v/dfc4c91e" to Host.STREAMIN,
            "https://streamin.me/v/dfc4c91e" to Host.STREAMIN,
            "https://streamin.one/v/0c5e534e" to Host.STREAMIN,
            "https://streamff.pro/v/3cdd35fe" to Host.STREAMFF,
            "https://streamff.link/v/059df2d6" to Host.STREAMFF,
            "https://dubz.link/c/29640a" to Host.DUBZ,
            "https://dubz.co/c/3af919" to Host.DUBZ,
            "https://dropr.co/v/1363fa0c" to Host.DROPR
        )
        for ((url, expected) in hosts) {
            assertEquals(url, expected, ShortClipHostUtils.hostOf(Uri.parse(url)))
        }
    }

    @Test
    fun aWwwPrefixAndUpperCaseHostStillMatch() {
        // Reddit hands the URL back as the poster typed it, and shared links pick up www.
        assertEquals(Host.DUBZ, ShortClipHostUtils.hostOf(Uri.parse("https://www.dubz.link/c/29640a")))
        assertEquals(Host.DROPR, ShortClipHostUtils.hostOf(Uri.parse("https://DROPR.co/v/1363fa0c")))
    }

    @Test
    fun clipIdComesFromTheLastSegmentForTheVAndCShapes() {
        assertEquals("dfc4c91e", ShortClipHostUtils.clipIdOf(Host.STREAMIN, Uri.parse("https://streamin.link/v/dfc4c91e")))
        assertEquals("3cdd35fe", ShortClipHostUtils.clipIdOf(Host.STREAMFF, Uri.parse("https://streamff.pro/v/3cdd35fe")))
        assertEquals("29640a", ShortClipHostUtils.clipIdOf(Host.DUBZ, Uri.parse("https://dubz.link/c/29640a")))
        assertEquals("1363fa0c", ShortClipHostUtils.clipIdOf(Host.DROPR, Uri.parse("https://dropr.co/v/1363fa0c")))
    }

    @Test
    fun streamainTakesTheSegmentBeforeWatch() {
        // Three shapes in the wild: with a language segment, without one, and the bare share
        // domain form, which carries no /watch at all.
        assertEquals(
            "7OWwVWjPv6GNI4W",
            ShortClipHostUtils.clipIdOf(Host.STREAMAIN, Uri.parse("https://streamain.com/en/7OWwVWjPv6GNI4W/watch"))
        )
        assertEquals(
            "lV5DaAYWJ9Gu1JX",
            ShortClipHostUtils.clipIdOf(Host.STREAMAIN, Uri.parse("https://streamain.com/lV5DaAYWJ9Gu1JX/watch"))
        )
        assertEquals(
            "le8F08XutVuuz7r",
            ShortClipHostUtils.clipIdOf(Host.STREAMAIN, Uri.parse("https://streama.in/le8F08XutVuuz7r"))
        )
    }

    @Test
    fun aUrlThatNamesNoClipHasNoId() {
        assertNull(ShortClipHostUtils.clipIdOf(Host.DROPR, Uri.parse("https://dropr.co/")))
        assertNull(ShortClipHostUtils.clipIdOf(Host.STREAMIN, Uri.parse("https://streamin.link")))
    }

    @Test
    fun streaminProbesTheNewestCdnFirst() {
        // The two CDNs are not mirrors: as of 2026-09-09 the newest clips were on c-cdn only and
        // month-old ones on w-cdn only. A feed shows new clips, so the order is what keeps the
        // common case to a single probe.
        assertEquals(
            listOf(
                "https://c-cdn.streamin.top/uploads/dfc4c91e.mp4",
                "https://w-cdn.streamin.top/uploads/dfc4c91e.mp4"
            ),
            ShortClipHostUtils.candidateVideoUrls(Host.STREAMIN, "dfc4c91e", Uri.parse("https://streamin.link/v/dfc4c91e"))
        )
    }

    @Test
    fun dubzPicksItsCdnFromTheUrlShape() {
        // The id sets are strictly disjoint and the wrong CDN answers 404, so getting this
        // backwards means every dubz clip needs two probes instead of one.
        assertEquals(
            "https://cdn.makevos.com/videos/29640a.mp4",
            ShortClipHostUtils.candidateVideoUrls(Host.DUBZ, "29640a", Uri.parse("https://dubz.link/c/29640a")).first()
        )
        assertEquals(
            "https://cdn.squeelab.com/guest/videos/d5792a.mp4",
            ShortClipHostUtils.candidateVideoUrls(Host.DUBZ, "d5792a", Uri.parse("https://dubz.link/v/d5792a")).first()
        )
    }

    @Test
    fun dubzKeepsTheOtherCdnAsAFallback() {
        // A future re-shuffle should cost one extra probe rather than dead clips.
        for (url in listOf("https://dubz.link/c/29640a", "https://dubz.link/v/d5792a")) {
            assertEquals(url, 2, ShortClipHostUtils.candidateVideoUrls(Host.DUBZ, "x", Uri.parse(url)).size)
        }
    }

    @Test
    fun aBareDubzUrlGetsTheMakevosDefault() {
        assertEquals(
            "https://cdn.makevos.com/videos/29640a.mp4",
            ShortClipHostUtils.candidateVideoUrls(Host.DUBZ, "29640a", Uri.parse("https://dubz.link/29640a")).first()
        )
    }

    @Test
    fun aQueryStringCannotFlipTheDubzCdn() {
        // The shape is read from the path only. Reading the whole URL would let a tracking
        // parameter pick the CDN that answers 404 for every clip.
        assertEquals(
            "https://cdn.makevos.com/videos/29640a.mp4",
            ShortClipHostUtils.candidateVideoUrls(
                Host.DUBZ, "29640a", Uri.parse("https://dubz.link/c/29640a?ref=/v/other")
            ).first()
        )
    }

    @Test
    fun theThreeScrapedHostsDeriveNoCandidates() {
        // streamain's MP4 filename is unrelated to the page id, dropr's is an unrelated hex string,
        // and streamff's storage has moved once already. Guessing for these is what produces an
        // empty player, so they must fall through to a page or API read.
        for (host in listOf(Host.STREAMAIN, Host.STREAMFF, Host.DROPR)) {
            assertTrue(
                host.name,
                ShortClipHostUtils.candidateVideoUrls(host, "abc", Uri.parse("https://example.com/v/abc")).isEmpty()
            )
        }
    }

    @Test
    fun sharePageKeepsTheUrlThePosterUsed() {
        // Rebuilding would drop the alias domain the poster used, which resolves, for no gain.
        val posted = "https://streamin.one/v/0c5e534e"
        assertEquals(posted, ShortClipHostUtils.sharePageUrl(Host.STREAMIN, "0c5e534e", Uri.parse(posted)))
    }

    @Test
    fun theHostsWithNoUsableOgVideoHaveNoSharePage() {
        // Each for its own reason: streamff's og:video points back at the page itself, dubz
        // publishes none, and streamain's watch page carries only an og:image. A scrape of any of
        // them would be a wasted request that always fails.
        assertNull(ShortClipHostUtils.sharePageUrl(Host.STREAMFF, "3cdd35fe", Uri.parse("https://streamff.pro/v/3cdd35fe")))
        assertNull(ShortClipHostUtils.sharePageUrl(Host.DUBZ, "29640a", Uri.parse("https://dubz.link/c/29640a")))
        assertNull(ShortClipHostUtils.sharePageUrl(Host.STREAMAIN, "7OWwVWjPv6GNI4W", Uri.parse("https://streamain.com/en/7OWwVWjPv6GNI4W/watch")))
    }

    @Test
    fun nonClipUrlsPassStraightThrough() {
        // Every link post's URL is offered to hostOf, so anything that is not one of the five has
        // to come back null rather than being promoted to a video that will never resolve.
        val untouched = listOf(
            "https://streamable.com/abc123",
            "https://www.reddit.com/r/soccer/comments/1wc9uc7/title/",
            "https://v.redd.it/abc123/HLSPlaylist.m3u8",
            "https://media.redgifs.com/Name.mp4",
            "https://bangr.im/v/abc123",
            "https://notstreamain.com/en/abc/watch",
            "https://streamain.com.example.org/en/abc/watch"
        )
        for (url in untouched) {
            assertNull(url, ShortClipHostUtils.hostOf(Uri.parse(url)))
        }
    }

    @Test
    fun nullPassesThrough() {
        assertNull(ShortClipHostUtils.hostOf(null))
        assertNull(ShortClipHostUtils.clipIdOf(Host.DUBZ, null))
    }
}
