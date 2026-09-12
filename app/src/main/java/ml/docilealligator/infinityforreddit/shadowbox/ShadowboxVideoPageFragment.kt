package ml.docilealligator.infinityforreddit.shadowbox

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import ml.docilealligator.infinityforreddit.Constants
import ml.docilealligator.infinityforreddit.R
import ml.docilealligator.infinityforreddit.databinding.ShadowboxMediaVideoBinding
import ml.docilealligator.infinityforreddit.utils.APIUtils
import ml.docilealligator.infinityforreddit.utils.MlbUrlUtils
import ml.docilealligator.infinityforreddit.utils.RedgifsUrlUtils
import ml.docilealligator.infinityforreddit.utils.SharedPreferencesUtils
import ml.docilealligator.infinityforreddit.utils.Utils
import ml.docilealligator.infinityforreddit.videoautoplay.DurationAwareSeekPlayer

/**
 * A video page (or a GIF served as mp4): its own ExoPlayer, set up the way
 * ViewRedditGalleryVideoFragment sets one up, playing only while the pager is on this page.
 */
@OptIn(UnstableApi::class)
class ShadowboxVideoPageFragment : ShadowboxPageFragment() {

    private var _binding: ShadowboxMediaVideoBinding? = null
    private val binding: ShadowboxMediaVideoBinding
        get() = _binding!!
    /** The URL the post carries. Kept as posted, so handing off to the full player is unaffected. */
    private lateinit var uri: Uri

    /**
     * What the player was actually given: [uri], or the smaller file the data-saving resolution
     * settings point at. Redgifs and MLB publish a ladder of separate files rather than a track
     * ladder inside one stream, so for those two the preference can only be honoured by swapping
     * the URL -- the same thing ViewVideoViewModel.dataSavingPlaybackUri does for the full player.
     */
    private var playbackUri: Uri = Uri.EMPTY

    /** Set once a downgraded file has failed, so the walk back up to the posted one happens once. */
    private var downgradeFailed = false

    private var isGifMp4 = false
    private var player: ExoPlayer? = null
    private var trackSelector: DefaultTrackSelector? = null
    private var playerListener: Player.Listener? = null
    private var isMute = false

    /**
     * Whether this is the Reddit-hosted HLS stream, which is the only thing here with a track
     * ladder to choose from and the only one whose first audio track is mono.
     */
    private var isRedditHls = false

    /** Both track overrides below are chosen from the first track list and not revisited. */
    private var appliedDefaultResolution = false
    private var appliedStereoAudioTrack = false

    /** Whether this is the page in front. Nothing plays on any other page. */
    private var pageActive = false

    /** Whether this video may start on its own, per the app's own video autoplay settings. */
    private var autoplay = false

    /**
     * Set when the user presses play, cleared when they leave the page. Leaving is what makes a
     * pause a pause: come back and the video is where it was, stopped, waiting to be started
     * again -- not resumed out from under the user because it happened to be playing earlier.
     */
    private var startedByUser = false

    /** Set when the user presses pause, so autoplay does not start it up again behind their back. */
    private var userPaused = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        uri = Uri.parse(requireArguments().getString(ARG_URI) ?: "")
        isGifMp4 = requireArguments().getBoolean(ARG_IS_GIF_MP4, false)
    }

    override fun onCreateMediaView(inflater: LayoutInflater, container: ViewGroup) {
        // Not onCreate: the autoplay rule reads the post, which the base class sets just before
        // this runs.
        autoplay = shouldAutoplay()
        val binding = ShadowboxMediaVideoBinding.inflate(inflater, container, true)
        _binding = binding
        binding.playerViewShadowboxMediaVideo.setOnClickListener { toggleChrome() }
        binding.progressBarShadowboxMediaVideo.visibility = View.INVISIBLE
        binding.playButtonShadowboxMediaVideo.setOnClickListener { togglePlayback() }
    }

    /**
     * The same rule the feed applies in PostRecyclerViewAdapter: "Video Autoplay" set to Never, or
     * to Wi-Fi only while on mobile data, means nothing starts by itself, and "Autoplay NSFW
     * Videos" excludes NSFW posts on top of that.
     */
    private fun shouldAutoplay(): Boolean {
        val setting = sharedPreferences.getString(
            SharedPreferencesUtils.VIDEO_AUTOPLAY, SharedPreferencesUtils.VIDEO_AUTOPLAY_VALUE_NEVER
        )
        val autoplayAllowed = when (setting) {
            SharedPreferencesUtils.VIDEO_AUTOPLAY_VALUE_ALWAYS_ON -> true
            SharedPreferencesUtils.VIDEO_AUTOPLAY_VALUE_ON_WIFI ->
                Utils.getConnectedNetwork(requireContext()) == Utils.NETWORK_TYPE_WIFI
            else -> false
        }
        if (!autoplayAllowed) {
            return false
        }
        return !post.isNSFW || sharedPreferences.getBoolean(SharedPreferencesUtils.AUTOPLAY_NSFW_VIDEOS, true)
    }

    override fun loadMedia() {
        val trackSelector = DefaultTrackSelector(host)
        this.trackSelector = trackSelector
        val player = ExoPlayer.Builder(host)
            .setTrackSelector(trackSelector)
            .setRenderersFactory(DefaultRenderersFactory(host).setEnableDecoderFallback(true))
            .setSeekBackIncrementMs(Constants.VIDEO_SEEK_BACK_INCREMENT_MS)
            .setSeekForwardIncrementMs(Constants.VIDEO_SEEK_FORWARD_INCREMENT_MS)
            .build()
        this.player = player
        binding.playerViewShadowboxMediaVideo.player = DurationAwareSeekPlayer(player)

        playbackUri = dataSavingPlaybackUri(uri)
        // v.redd.it hands out an HLS playlist; everything else the pager plays inline is a plain
        // mp4 -- the same split MediaSourceBuilder.DEFAULT makes for the feed's autoplay.
        isRedditHls = Util.inferContentType(playbackUri) == C.CONTENT_TYPE_HLS
        player.setMediaSource(buildMediaSource(playbackUri))

        player.repeatMode = if (sharedPreferences.getBoolean(SharedPreferencesUtils.LOOP_VIDEO, true)) {
            Player.REPEAT_MODE_ALL
        } else {
            Player.REPEAT_MODE_OFF
        }
        // "Default Playback Speed", stored as a percentage, the way ViewVideoActivity applies it.
        val playbackSpeed = SharedPreferencesUtils.getInt(
            sharedPreferences, SharedPreferencesUtils.DEFAULT_PLAYBACK_SPEED, "100"
        )
        player.playbackParameters = PlaybackParameters(if (playbackSpeed <= 0) 1f else playbackSpeed / 100f)
        isMute = initialMuteState()
        applyMute()

        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                val binding = _binding ?: return
                binding.progressBarShadowboxMediaVideo.visibility =
                    if (playbackState == Player.STATE_BUFFERING) View.VISIBLE else View.INVISIBLE
            }

            override fun onTracksChanged(tracks: Tracks) {
                applyDefaultResolution(tracks)
                applyStereoAudioTrack(tracks)
                val hasAudio = tracks.groups.any { group ->
                    group.length > 0 && group.getTrackFormat(0).sampleMimeType?.contains("audio") == true
                }
                if (hasAudio) {
                    panel?.showMuteControl(isMute) { toggleMute() }
                } else {
                    panel?.hideMuteControl()
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    host.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    host.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
                updatePlayButton()
            }

            override fun onPlayerError(error: PlaybackException) {
                _binding?.progressBarShadowboxMediaVideo?.visibility = View.INVISIBLE
                // A downgraded URL is derived rather than confirmed, so it can name a file the
                // host never transcoded. Walking back up to the posted one keeps a data-saving
                // 404 from showing an error where the full-size file would have played.
                retryAtPostedQuality()
            }
        }
        playerListener = listener
        player.addListener(listener)
        player.prepare()
        applyPlayback()
    }

    private fun buildMediaSource(source: Uri): MediaSource {
        val dataSourceFactory: DataSource.Factory = CacheDataSource.Factory()
            .setCache(simpleCache)
            .setUpstreamDataSourceFactory(OkHttpDataSource.Factory(okHttpClient).setUserAgent(APIUtils.USER_AGENT))
        val mediaItem = MediaItem.fromUri(source)
        return if (Util.inferContentType(source) == C.CONTENT_TYPE_HLS) {
            HlsMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem)
        } else {
            ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem)
        }
    }

    /**
     * [source] downgraded to the file "Redgifs Video Default Resolution" or "MLB Video Default
     * Bitrate" asks for, while data saving is on. Both helpers pass anything that is not one of
     * their own URLs straight through, so neither needs to be asked what kind this is.
     */
    private fun dataSavingPlaybackUri(source: Uri): Uri {
        val afterRedgifs = RedgifsUrlUtils.playbackUri(
            source, dataSavingMode,
            SharedPreferencesUtils.getInt(
                sharedPreferences, SharedPreferencesUtils.REDGIFS_VIDEO_DEFAULT_RESOLUTION, "480"
            )
        )
        return MlbUrlUtils.playbackUri(
            afterRedgifs, dataSavingMode,
            SharedPreferencesUtils.getInt(
                sharedPreferences, SharedPreferencesUtils.MLB_VIDEO_DEFAULT_BITRATE, "4000"
            )
        ) ?: source
    }

    /**
     * Whether this video starts muted.
     *
     * "Mute Video" and "Mute NSFW Video" are the standing instructions and come first. Under them
     * sits the feed's rule: with "Remember Muting Option in Post Feed" on, the last choice the
     * user made with a mute button wins, and otherwise a video that starts on its own follows
     * "Mute Autoplaying Videos" while one the user pressed play on is a video they chose to watch.
     */
    private fun initialMuteState(): Boolean {
        if (sharedPreferences.getBoolean(SharedPreferencesUtils.MUTE_VIDEO, false)) {
            return true
        }
        if (post.isNSFW && sharedPreferences.getBoolean(SharedPreferencesUtils.MUTE_NSFW_VIDEO, false)) {
            return true
        }
        videoMuteManager.getMasterMutingOption()?.let { return it }
        return autoplay && sharedPreferences.getBoolean(SharedPreferencesUtils.MUTE_AUTOPLAYING_VIDEOS, true)
    }

    /**
     * Picks the video track "Reddit Video Default Resolution" asks for, ported from
     * ViewVideoActivity's onTracksChanged: the largest track at or below the wanted resolution,
     * or the smallest one there is when every track is above it. Only the Reddit stream has a
     * ladder to choose from -- every other host here is a single file, and the two that publish
     * several are handled by [dataSavingPlaybackUri] instead.
     */
    private fun applyDefaultResolution(tracks: Tracks) {
        val player = player ?: return
        if (appliedDefaultResolution || !isRedditHls) {
            return
        }
        appliedDefaultResolution = true
        val desiredResolution = if (dataSavingMode) {
            SharedPreferencesUtils.getInt(
                sharedPreferences, SharedPreferencesUtils.REDDIT_VIDEO_DEFAULT_RESOLUTION, "360"
            )
        } else {
            SharedPreferencesUtils.getInt(
                sharedPreferences, SharedPreferencesUtils.REDDIT_VIDEO_DEFAULT_RESOLUTION_NO_DATA_SAVING, "0"
            )
        }
        if (desiredResolution <= 0) {
            return
        }

        var bestGroup: Tracks.Group? = null
        var bestTrackIndex = -1
        var bestResolution = -1
        var worstGroup: Tracks.Group? = null
        var worstTrackIndex = -1
        var worstResolution = Int.MAX_VALUE
        for (group in tracks.groups) {
            if (group.type != C.TRACK_TYPE_VIDEO) {
                continue
            }
            for (trackIndex in 0 until group.length) {
                val format = group.getTrackFormat(trackIndex)
                val trackResolution = minOf(format.height, format.width)
                if (trackResolution in (bestResolution + 1)..desiredResolution) {
                    bestGroup = group
                    bestTrackIndex = trackIndex
                    bestResolution = trackResolution
                }
                if (trackResolution < worstResolution) {
                    worstGroup = group
                    worstTrackIndex = trackIndex
                    worstResolution = trackResolution
                }
            }
        }

        val override = when {
            bestGroup != null -> TrackSelectionOverride(bestGroup.mediaTrackGroup, listOf(bestTrackIndex))
            worstGroup != null -> TrackSelectionOverride(worstGroup.mediaTrackGroup, listOf(worstTrackIndex))
            else -> return
        }
        player.trackSelectionParameters =
            player.trackSelectionParameters.buildUpon().addOverride(override).build()
    }

    /**
     * Reddit video HLS usually carries two audio tracks, the first of them mono; ViewVideoActivity
     * picks the second for that reason, and without the same override this page plays a Reddit
     * video in mono where the full player has it in stereo.
     */
    private fun applyStereoAudioTrack(tracks: Tracks) {
        val trackSelector = trackSelector ?: return
        if (appliedStereoAudioTrack || !isRedditHls) {
            return
        }
        for (group in tracks.groups) {
            if (group.type != C.TRACK_TYPE_AUDIO) {
                continue
            }
            if (group.length > 1) {
                appliedStereoAudioTrack = true
                trackSelector.setParameters(
                    trackSelector.buildUponParameters().setOverrideForType(
                        TrackSelectionOverride(
                            group.mediaTrackGroup,
                            if (group.mediaTrackGroup.length > 1) 1 else 0
                        )
                    )
                )
            }
            break
        }
    }

    /**
     * The single rule for whether this video runs: it is the page in front, the user has not
     * paused it, and either autoplay is on or they pressed play during this visit to the page.
     *
     * Everything that can change playback -- the pager moving, the screen going away and coming
     * back, the play button -- sets one of those and calls this, rather than each deciding for
     * itself whether to start the player again. Deciding it in several places is what let a video
     * that had been swiped away from pick straight up again on the way back.
     */
    private fun applyPlayback() {
        val player = player ?: return
        player.playWhenReady = pageActive && !userPaused && (autoplay || startedByUser)
        updatePlayButton()
    }

    /** Re-prepares on the file the post actually carries, once, after a downgrade failed. */
    private fun retryAtPostedQuality() {
        if (downgradeFailed) {
            return
        }
        val postedUri = RedgifsUrlUtils.hdVariant(playbackUri)
            ?: MlbUrlUtils.postedVariant(playbackUri)
            ?: return
        val player = player ?: return
        downgradeFailed = true
        playbackUri = postedUri
        isRedditHls = Util.inferContentType(postedUri) == C.CONTENT_TYPE_HLS
        player.setMediaSource(buildMediaSource(postedUri))
        player.prepare()
    }

    private fun togglePlayback() {
        val player = player ?: return
        if (player.playWhenReady) {
            userPaused = true
            startedByUser = false
        } else {
            startedByUser = true
            userPaused = false
        }
        applyPlayback()
    }

    /**
     * The centre button is this page's play/pause control, the way the controller is on the video
     * viewer: always there while the video is paused, so a page autoplay left alone says how to
     * start it, and there whenever the bar is up, so a playing video can be paused from the same
     * spot. It gets out of the way only in the state meant for watching -- playing, bar hidden.
     */
    private fun updatePlayButton() {
        val binding = _binding ?: return
        val playing = player?.playWhenReady ?: false
        binding.playButtonShadowboxMediaVideo.setIconResource(
            if (playing) R.drawable.ic_pause_24dp else R.drawable.ic_play_arrow_24dp
        )
        val chromeVisible = host.panelVisible.value != false
        binding.playButtonShadowboxMediaVideo.visibility =
            if (!playing || chromeVisible) View.VISIBLE else View.GONE
    }

    override fun onChromeVisibilityChanged(visible: Boolean) {
        updatePlayButton()
    }

    private fun applyMute() {
        player?.volume = if (isMute) 0f else 1f
        panel?.setMuted(isMute)
    }

    private fun toggleMute() {
        isMute = !isMute
        // Stored only when "Remember Muting Option in Post Feed" is on; the manager drops the
        // write otherwise, so unmuting one video cannot clear "Mute Autoplaying Videos" for good.
        videoMuteManager.isMuted = isMute
        applyMute()
    }

    override fun onPageActive() {
        // Autoplay off means the page comes up paused behind the play button. A video the user
        // started and then swiped away from comes back paused too, at the frame it stopped on.
        pageActive = true
        applyPlayback()
    }

    override fun pausePlayback() {
        // No rewind: a pause keeps its place, so pressing play on the way back carries on from
        // the frame the user left rather than starting the video over.
        pageActive = false
        startedByUser = false
        applyPlayback()
    }

    override fun onResume() {
        super.onResume()
        applyPlayback()
    }

    override fun onPause() {
        super.onPause()
        // Nothing plays while the screen is away. The flags are left alone, so coming back to the
        // same page restores exactly what the rule in applyPlayback says it should be.
        val player = player ?: return
        player.playWhenReady = false
        updatePlayButton()
    }

    override fun openFullViewer() {
        val progress = player?.currentPosition ?: 0L
        if (isGifMp4) {
            ShadowboxMediaIntents.openGifMp4(host, post, uri.toString(), progress)
        } else {
            ShadowboxMediaIntents.openVideo(host, post, progress)
        }
    }

    override fun onDestroyView() {
        player?.let { player ->
            playerListener?.let { player.removeListener(it) }
            player.stop()
            player.release()
        }
        player = null
        trackSelector = null
        playerListener = null
        // Only if this page was the one playing: the pager destroys off-screen pages while the
        // page in front keeps playing, and clearing the host's flag from one of those would let
        // the screen sleep during playback.
        if (pageActive) {
            host.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        _binding = null
        super.onDestroyView()
    }

    companion object {
        private const val ARG_URI = "AU"
        private const val ARG_IS_GIF_MP4 = "AIGM"

        fun newInstance(position: Int, blur: Boolean, uri: String, isGifMp4: Boolean): ShadowboxVideoPageFragment {
            val fragment = ShadowboxVideoPageFragment()
            val args = baseArguments(position, blur)
            args.putString(ARG_URI, uri)
            args.putBoolean(ARG_IS_GIF_MP4, isGifMp4)
            fragment.arguments = args
            return fragment
        }
    }
}
