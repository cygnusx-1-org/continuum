package ml.docilealligator.infinityforreddit.viewmodels

import android.net.Uri
import android.os.Looper
import androidx.annotation.OptIn
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import ml.docilealligator.infinityforreddit.R
import ml.docilealligator.infinityforreddit.TestInfinity
import ml.docilealligator.infinityforreddit.activities.ViewVideoActivity
import ml.docilealligator.infinityforreddit.video.RedditDashFallback
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The full player's fallback order for a Reddit video (issue #498): HLS, then the DASH manifest
 * beside it, then the post's direct URL when it has one, then the error -- each once, so a video
 * with nothing left to play ends on the error rather than in a loop.
 */
@OptIn(UnstableApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = TestInfinity::class)
class ViewVideoViewModelDashFallbackTest {

    @get:Rule
    val instantRule = InstantTaskExecutorRule()

    private val hls = Uri.parse("https://v.redd.it/ezb7hozc3i3i1/HLSPlaylist.m3u8?a=1798135636%2CNzQ4&v=1&f=sd")
    private val dash = Uri.parse("https://v.redd.it/ezb7hozc3i3i1/DASHPlaylist.mpd")
    private val direct = Uri.parse("https://v.redd.it/ezb7hozc3i3i1/CMAF_720.mp4?source=fallback")

    private val uris = mutableListOf<Uri?>()
    private val errors = mutableListOf<Int?>()

    @Before
    @After
    fun forget() {
        RedditDashFallback.forgetAll()
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun viewModel(
        uri: Uri,
        fallbackDirectUrl: String? = null,
        videoType: Int = ViewVideoActivity.VIDEO_TYPE_NORMAL,
    ): ViewVideoViewModel {
        val vm = ViewVideoViewModel(
            videoUri = uri,
            videoFallbackDirectUrl = fallbackDirectUrl,
            videoType = videoType,
            redgifsId = null,
            vReddItUrl = null,
            streamableShortCode = null,
            shortClipHost = null,
            shortClipId = null,
            shortClipPageUrl = null,
            playbackSpeed = 100,
        )
        vm.videoUriLiveData.observeForever { uris += it }
        vm.errorResId.observeForever { errors += it }
        idle()
        return vm
    }

    private fun ViewVideoViewModel.failOn(uri: Uri) {
        loadFallbackVideo(MediaItem.fromUri(uri), null)
        idle()
    }

    @Test
    fun aFailedHlsPlaylistIsRetriedOnDashAndThenReportedAsAnError() {
        val vm = viewModel(hls)
        assertEquals(hls, uris.last())

        vm.failOn(hls)
        assertEquals(dash, uris.last())
        assertNull(errors.last())
        assertEquals(ViewVideoActivity.VIDEO_TYPE_NORMAL, vm.videoType)

        vm.failOn(dash)
        assertNull(uris.last())
        assertEquals(R.string.error_fetching_video, errors.last())
    }

    @Test
    fun dashComesBeforeTheDirectUrlAndTheDirectUrlStillFollows() {
        val vm = viewModel(hls, fallbackDirectUrl = direct.toString())

        vm.failOn(hls)
        assertEquals(dash, uris.last())

        vm.failOn(dash)
        assertEquals(direct, uris.last())
        assertEquals(ViewVideoActivity.VIDEO_TYPE_DIRECT, vm.videoType)

        vm.failOn(direct)
        assertNull(uris.last())
        assertEquals(R.string.error_fetching_video, errors.last())
    }

    @Test
    fun anEmbeddedVideoKeepsItsTypeOnDash() {
        val embedded = Uri.parse("https://v.redd.it/link/1xswmv0/asset/o6d9di9p4u0i1/HLSPlaylist.m3u8")
        val vm = viewModel(embedded, videoType = ViewVideoActivity.VIDEO_TYPE_MARKDOWN_PARSED)

        vm.failOn(embedded)

        assertEquals("https://v.redd.it/link/1xswmv0/asset/o6d9di9p4u0i1/DASHPlaylist.mpd", uris.last().toString())
        assertEquals(ViewVideoActivity.VIDEO_TYPE_MARKDOWN_PARSED, vm.videoType)
    }

    @Test
    fun aVideoThatAlreadyFellBackElsewhereOpensOnDash() {
        RedditDashFallback.fallbackFor("feed", hls)

        viewModel(hls)

        assertEquals(dash, uris.last())
    }

    @Test
    fun aVideoThatIsNotFromRedditGoesStraightToItsError() {
        val mp4 = Uri.parse("https://i.imgur.com/abc.mp4")
        val vm = viewModel(mp4, videoType = ViewVideoActivity.VIDEO_TYPE_DIRECT)

        vm.failOn(mp4)

        assertNull(uris.last())
        assertEquals(R.string.error_fetching_video, errors.last())
    }
}
