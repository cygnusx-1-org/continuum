package ml.docilealligator.infinityforreddit.viewmodels

import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import ml.docilealligator.infinityforreddit.AppResult
import ml.docilealligator.infinityforreddit.R
import ml.docilealligator.infinityforreddit.VReddItReturnType
import ml.docilealligator.infinityforreddit.activities.ViewVideoActivity
import ml.docilealligator.infinityforreddit.apis.StreamableAPIKt
import ml.docilealligator.infinityforreddit.fetchVideoLink
import ml.docilealligator.infinityforreddit.post.Post
import ml.docilealligator.infinityforreddit.thing.StreamableVideo
import ml.docilealligator.infinityforreddit.utils.getRandomString
import org.apache.commons.io.FilenameUtils
import retrofit2.Retrofit
import javax.inject.Provider

@UnstableApi
class ViewVideoViewModel(
    var post: Post? = null,
    videoUri: Uri? = null,
    var videoDownloadUrl: String? = null,
    private var videoFallbackDirectUrl: String? = null,
    var subredditName: String? = null,
    var id: String? = null,
    var isNSFW: Boolean = false,
    var resumePosition: Long = -1,
    var videoType: Int = 0,
    private var redgifsId: String?,
    private val vReddItUrl: String?,
    private var streamableShortCode: String?,
    var isDataSavingMode: Boolean = false,
    var dataSavingModeDefaultResolution: Int = 0,
    var nonDataSavingModeDefaultResolution: Int = 0,
    var playbackSpeed: Int
) : ViewModel() {
    var wasPlaying: Boolean = false
    var isDownloading: Boolean = false
    var isMute: Boolean = false
    var setDefaultResolutionAlready: Boolean = false

    private val _videoUri = MutableStateFlow(videoUri)
    val videoUriLiveData = _videoUri.asLiveData()

    private val _errorResId = MutableStateFlow<Int?>(null)

    /**
     * The last fetch error, deliberately sticky. [loadVideoLink] runs at most once per ViewModel,
     * so a recreate after a failure never re-fetches and never republishes a URI — if this were
     * cleared once shown, the recreated activity would sit on its loading indicator forever with
     * nothing left to hide it.
     */
    val errorResId = _errorResId.asLiveData()

    /** Guards [loadVideoLink] so it runs at most once per ViewModel, across activity recreates. */
    private var videoLinkRequested = false

    /**
     * Single point where a resolved URI becomes available. Clearing [errorResId] here is what keeps
     * it sticky *only* while there is nothing playable: with no URI the error is the only thing that
     * can hide ViewVideoActivity's loading indicator, but once a URI exists that activity's URI
     * observer hides it, so a stale error must not keep toasting over a working video.
     */
    private fun publishVideoUri(uri: Uri?) {
        // A null URI means the fetch produced nothing playable. Recording an error here rather than
        // at each call site is what makes the invariant structural: every caller either publishes
        // something the player can use, or surfaces a failure. Publishing null silently is what
        // stranded callers on the loading indicator with no explanation.
        //
        // Order matters. The URI has to go first: ViewVideoActivity's URI observer *shows* the
        // loading indicator for a null URI, and its error observer hides it. Setting the error
        // first would let that re-show win, leaving the indicator up with the error already
        // consumed. This way the final state is correct whichever value _videoUri held before.
        _videoUri.value = uri
        if (uri == null) {
            // A null URI makes the observer's null-branch re-call loadVideoLink. That is a no-op
            // once a fetch has been attempted, but loadFallbackVideo can publish null for a normal
            // video that started from a non-null intent URI and so never ran loadVideoLink — the
            // guard would be unset and the re-entry would fetch with an unsupported videoType. A
            // null publish always means "nothing playable", so close the fetch cycle here.
            videoLinkRequested = true
        }
        _errorResId.value = if (uri == null) R.string.error_fetching_video else null
    }

    val fileName: String
        get() {
            return if (redgifsId != null) {
                "Redgifs-$redgifsId.mp4"
            } else if (streamableShortCode != null) {
                "Streamable-$streamableShortCode.mp4"
            } else {
                post?.let {
                    if (it.isImgur) {
                        "Imgur-" + FilenameUtils.getName(videoDownloadUrl);
                    } else {
                        if (videoType == ViewVideoActivity.VIDEO_TYPE_DIRECT) {
                            FilenameUtils.getName(videoDownloadUrl) ?: (getRandomString() + ".mp4")
                        } else {
                            it.subredditName + "-" + it.id + ".mp4";
                        }
                    }
                } ?: FilenameUtils.getName(videoDownloadUrl) ?: (getRandomString() + ".mp4")
            }
        }

    init {
        if (videoType == ViewVideoActivity.VIDEO_TYPE_DIRECT || videoType == ViewVideoActivity.VIDEO_TYPE_IMGUR) {
            videoDownloadUrl = videoUri?.toString()
        }
    }

    fun loadVideoLink(
        retrofit: Retrofit, vReddItRetrofit: Retrofit,
        redgifsRetrofit: Retrofit,
        streamableApiProvider: Provider<StreamableAPIKt>,
        currentAccountSharedPreferences: SharedPreferences,
    ) {
        // ViewVideoActivity re-runs this whenever it observes a null URI, which includes every
        // recreate. Without this guard a link that can't resolve — a removed post, say — re-hits
        // the network and re-toasts its error on each rotation. The ViewModel outlives those
        // recreates, so one attempt per instance is the right scope.
        if (videoLinkRequested) {
            return
        }
        videoLinkRequested = true

        viewModelScope.launch {
            val result = fetchVideoLink(
                retrofit, vReddItRetrofit, redgifsRetrofit, streamableApiProvider,
                currentAccountSharedPreferences, videoType, redgifsId, vReddItUrl,
                streamableShortCode
            )

            when (result) {
                is AppResult.Success<*> -> {
                    when (val data = result.data) {
                        is StreamableVideo -> {
                            videoDownloadUrl = data.mp4?.url ?: data.mp4Mobile?.url
                            publishVideoUri(videoDownloadUrl?.toUri())
                            //title =
                        }

                        is Pair<*, *> -> {
                            // Redgifs
                            publishVideoUri((data.first as? String)?.toUri())
                            videoDownloadUrl = data.first as? String
                        }

                        is VReddItReturnType -> {
                            redgifsId = data.newRedgifsId
                            streamableShortCode = data.newStreamableShortCode
                            videoFallbackDirectUrl = data.post.videoFallBackDirectUrl
                            // ViewVideoActivity gates both share and download on a non-null post,
                            // and builds their filenames from it, so this has to be populated for
                            // every v.redd.it sub-case below — not just the plain-video one.
                            post = data.post
                            subredditName = data.post.subredditName
                            id = data.post.id

                            val optionalResult = data.optionalResult
                            optionalResult?.let {
                                when (it) {
                                    is AppResult.Success<*> -> {
                                        when (val optionalData = it.data) {
                                            is StreamableVideo -> {
                                                videoDownloadUrl = optionalData.mp4?.url ?: optionalData.mp4Mobile?.url
                                                publishVideoUri(videoDownloadUrl?.toUri())
                                                //title =
                                            }

                                            is Pair<*, *> -> {
                                                if (redgifsId == null) {
                                                    // Imgur. loadVReddItVideo packs this as
                                                    // Pair(videoUrl, videoDownloadUrl), so unlike
                                                    // the redgifs arm below the two differ. Leaving
                                                    // it empty stranded the caller on the loading
                                                    // indicator with no URI and no error.
                                                    videoType = ViewVideoActivity.VIDEO_TYPE_IMGUR
                                                    videoDownloadUrl = optionalData.second as? String
                                                    publishVideoUri(
                                                        (optionalData.first as? String)?.toUri()
                                                    )
                                                } else {
                                                    // Redgifs
                                                    publishVideoUri((optionalData.first as? String)?.toUri())
                                                    videoDownloadUrl = optionalData.first as? String
                                                }
                                            }

                                            else -> {
                                                // Unreachable today, but this `when` is a statement
                                                // over a star-projected type, so Kotlin won't force
                                                // exhaustiveness: a new payload type would fall
                                                // through setting neither URI nor error.
                                                _errorResId.value = R.string.error_fetching_video
                                            }
                                        }
                                    }

                                    is AppResult.Error<*> -> {
                                        // Mirrors the outer handler below. Without it a failed
                                        // redgifs/streamable sub-fetch leaves both the URI and the
                                        // error unset, so the loading indicator spins forever with
                                        // nothing shown to the user.
                                        _errorResId.value =
                                            (it.error as? Int) ?: R.string.error_fetching_video
                                    }
                                }
                            } ?: run {
                                // A plain Reddit-hosted video: post.videoUrl is an HLS playlist, so
                                // the type has to flip to VIDEO_TYPE_NORMAL before the URI is
                                // published or ViewVideoActivity's observer builds a progressive
                                // media source for it and playback fails to recognise the stream.
                                // Only flip it once there is a URI to publish — mutating the type
                                // and then emitting null would make the observer's retry re-enter
                                // fetchVideoLink with a type its `when` doesn't handle.
                                val resolvedUri = data.post.videoUrl?.toUri()
                                if (resolvedUri == null) {
                                    _errorResId.value =
                                        R.string.error_fetching_v_redd_it_video_cannot_get_video_url
                                } else {
                                    videoType = ViewVideoActivity.VIDEO_TYPE_NORMAL
                                    videoDownloadUrl = data.post.videoDownloadUrl
                                    publishVideoUri(resolvedUri)
                                }
                            }
                        }

                        else -> {
                            // Same guard as the nested `when` above: fetchVideoLink only produces
                            // the three types handled here today, but nothing structurally prevents
                            // a fourth from landing in a silent fall-through.
                            _errorResId.value = R.string.error_fetching_video
                        }
                    }
                }

                is AppResult.Error<*> -> {
                    _errorResId.value = result.error as? Int ?: R.string.error_fetching_video
                }
            }
        }
    }

    fun loadFallbackVideo(mediaItem: MediaItem?, savedInstanceState: Bundle?) {
        val fallbackUrl = videoFallbackDirectUrl
        val canRetryWithFallback = mediaItem == null ||
            (mediaItem.localConfiguration != null &&
                fallbackUrl != mediaItem.localConfiguration?.uri?.toString())

        if (fallbackUrl == null || !canRetryWithFallback) {
            // Called from ViewVideoActivity's onPlayerError, so playback has already failed and
            // there is nothing left to try: either the post carries no direct-URL fallback, or the
            // player just failed on the fallback itself. Report it — silently returning leaves the
            // user staring at a dead player with no indication anything went wrong.
            //
            // Goes through publishVideoUri rather than writing the error directly so the URI is
            // dropped alongside it. onPlayerError only fires with a live media source, so writing
            // the error on its own would leave a stale one sitting next to a non-null URI, and
            // every later recreate would re-attempt playback *and* re-toast a failure that may no
            // longer apply.
            publishVideoUri(null)
            return
        }

        videoType = ViewVideoActivity.VIDEO_TYPE_DIRECT
        videoDownloadUrl = fallbackUrl
        publishVideoUri(fallbackUrl.toUri())
    }

    companion object {
        fun provideFactory(
            post: Post?,
            videoUri: Uri?,
            videoDownloadUrl: String? = null,
            videoFallbackDirectUrl: String? = null,
            subredditName: String? = null,
            id: String? = null,
            isNSFW: Boolean = false,
            resumePosition: Long = -1,
            videoType: Int = 0,
            redgifsId: String?,
            vReddItUrl: String?,
            streamableShortCode: String?,
            isDataSavingMode: Boolean = false,
            dataSavingModeDefaultResolution: Int = 0,
            nonDataSavingModeDefaultResolution: Int = 0,
            playbackSpeed: Int
        ): ViewModelProvider.Factory {
            return object: ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(
                    modelClass: Class<T>,
                    extras: CreationExtras
                ): T {
                    return ViewVideoViewModel(post, videoUri,
                        videoDownloadUrl, videoFallbackDirectUrl, subredditName, id,
                        isNSFW, resumePosition, videoType, redgifsId, vReddItUrl, streamableShortCode,
                        isDataSavingMode, dataSavingModeDefaultResolution,
                        nonDataSavingModeDefaultResolution, playbackSpeed) as T
                }
            }
        }
    }
}