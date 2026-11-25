package ml.docilealligator.infinityforreddit.video

import android.net.Uri
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Which URL a failed Reddit video is retried on, and that the retry is offered once: issue #498's
 * videos had no HLS playlist for hours, but their DASH manifest was there.
 */
@RunWith(RobolectricTestRunner::class)
class RedditDashFallbackTest {

    private val postHls = Uri.parse(
        "https://v.redd.it/ezb7hozc3i3i1/HLSPlaylist.m3u8?a=1798135636%2CNzQ4NThi&v=1&f=sd"
    )
    private val postDash = "https://v.redd.it/ezb7hozc3i3i1/DASHPlaylist.mpd"

    @Before
    @After
    fun forget() {
        RedditDashFallback.forgetAll()
    }

    @Test
    fun postPlaylistMapsToItsDashManifestWithoutTheQuery() {
        assertEquals(postDash, RedditDashFallback.dashUriFor(postHls).toString())
    }

    @Test
    fun embeddedAssetPlaylistMapsToTheManifestBesideIt() {
        val embedded = Uri.parse("https://v.redd.it/link/1xswmv0/asset/o6d9di9p4u0i1/HLSPlaylist.m3u8")
        assertEquals(
            "https://v.redd.it/link/1xswmv0/asset/o6d9di9p4u0i1/DASHPlaylist.mpd",
            RedditDashFallback.dashUriFor(embedded).toString()
        )
    }

    @Test
    fun nothingElseHasADashManifest() {
        assertNull(RedditDashFallback.dashUriFor(null))
        assertNull(RedditDashFallback.dashUriFor(Uri.parse(postDash)))
        assertNull(RedditDashFallback.dashUriFor(Uri.parse("https://v.redd.it/ezb7hozc3i3i1/CMAF_720.mp4")))
        assertNull(RedditDashFallback.dashUriFor(Uri.parse("https://v.redd.it/ezb7hozc3i3i1/CMAF_480.m3u8")))
        assertNull(RedditDashFallback.dashUriFor(Uri.parse("https://example.com/v/HLSPlaylist.m3u8")))
        assertNull(RedditDashFallback.dashUriFor(Uri.parse("https://media.redgifs.com/Clip.mp4")))
    }

    @Test
    fun aVideoPlaysItsHlsPlaylistUntilThatFails() {
        assertEquals(postHls, RedditDashFallback.playbackUri(postHls))

        assertEquals(postDash, RedditDashFallback.fallbackFor("test", postHls).toString())

        assertEquals(postDash, RedditDashFallback.playbackUri(postHls).toString())
    }

    @Test
    fun theFallbackIsOfferedOnceSoAFailureOnDashGoesOn() {
        val dash = RedditDashFallback.fallbackFor("test", postHls)

        assertNull(RedditDashFallback.fallbackFor("test", dash))
    }

    @Test
    fun otherVideosAreLeftAlone() {
        RedditDashFallback.fallbackFor("test", postHls)
        val other = Uri.parse("https://v.redd.it/sd4w9uhxms3i1/HLSPlaylist.m3u8")
        val direct = Uri.parse("https://i.imgur.com/abc.mp4")

        assertEquals(other, RedditDashFallback.playbackUri(other))
        assertEquals(direct, RedditDashFallback.playbackUri(direct))
        assertNull(RedditDashFallback.fallbackFor("test", direct))
    }

    @Test
    fun theOldestRememberedVideoIsForgottenFirst() {
        val first = Uri.parse("https://v.redd.it/first/HLSPlaylist.m3u8")
        RedditDashFallback.fallbackFor("test", first)
        repeat(128) { RedditDashFallback.fallbackFor("test", Uri.parse("https://v.redd.it/v$it/HLSPlaylist.m3u8")) }

        assertEquals(first, RedditDashFallback.playbackUri(first))
        val latest = Uri.parse("https://v.redd.it/v127/HLSPlaylist.m3u8")
        assertEquals("https://v.redd.it/v127/DASHPlaylist.mpd", RedditDashFallback.playbackUri(latest).toString())
    }
}
