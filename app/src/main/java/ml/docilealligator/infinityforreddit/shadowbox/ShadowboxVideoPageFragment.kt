package ml.docilealligator.infinityforreddit.shadowbox

import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.annotation.OptIn
import androidx.core.net.toUri
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

    /**
     * Set once the post's direct-URL fallback has been tried, so a failure on it ends in the error
     * state rather than in the same file again. The feed's row makes the same single attempt.
     */
    private var fallbackTried = false

    /** Whether the error overlay is up: the player gave up on every file it had. */
    private var playbackFailed = false

    /**
     * Where a released player was, so the one built in its place carries on from there rather
     * than from the start. [C.TIME_UNSET] when there is nothing to carry on from.
     */
    private var resumePositionMs = C.TIME_UNSET

    /**
     * Set by [releaseMedia] when it let a player go, so [restoreMedia] knows to build one back.
     * A page whose media has never been loaded -- one still behind its blur overlay -- has no
     * player to release and must not get one on the way back either.
     */
    private var playerReleased = false

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

    /** Whether the player has put a frame on the surface; the preview only goes once it has. */
    private var firstFrameRendered = false

    /**
     * Whether the preview has been taken down. Once per player: nothing brings it back under a
     * running one, but [showPoster] puts it back for the next one -- a rebuild, or a retry -- so
     * this is not a one-way flag over the life of the page.
     */
    private var posterHidden = false

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

    /**
     * Set when the play button's time is up, cleared by whatever brings it back. Kept apart from
     * the rule in [updatePlayButton] so a passive refresh -- a rebuffer, say -- never revives a
     * button that has already gone: only a touch or a change of playback state does.
     */
    private var playButtonTimedOut = false

    /**
     * Takes the play button down, [Constants.VIDEO_CONTROLS_SHOW_TIMEOUT_MS] after it came up on a
     * playing video.
     */
    private val hidePlayButton = Runnable {
        playButtonTimedOut = true
        updatePlayButton()
    }

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
        binding.playbackErrorLinearLayoutShadowboxMediaVideo.setOnClickListener { retryPlayback() }
    }

    override fun loadMedia() {
        showPoster()
        isMute = initialMuteState()
        buildPlayer()
    }

    /**
     * Builds this page's player and starts it on the file the resolution preference points at --
     * the posted one, or the smaller file data saving asks for -- at [resumePositionMs] if there
     * is one. Everything the page decides per player -- the media source, the track overrides,
     * the fallback walks -- starts over here; what the user decided -- mute, pause, play -- is
     * kept, so a player rebuilt by [restoreMedia] behaves like the one it replaces.
     */
    private fun buildPlayer() {
        val binding = _binding ?: return
        playbackFailed = false
        binding.playbackErrorLinearLayoutShadowboxMediaVideo.visibility = View.GONE
        downgradeFailed = false
        fallbackTried = false
        appliedDefaultResolution = false
        appliedStereoAudioTrack = false
        firstFrameRendered = false
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

            override fun onRenderedFirstFrame() {
                firstFrameRendered = true
                hidePosterIfPlaying()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                hidePosterIfPlaying()
                if (isPlaying) {
                    host.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    host.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
                updatePlayButton()
            }

            override fun onPlayerError(error: PlaybackException) {
                // The code and the file, since this is the only record of a failure that the
                // device, not the post, decided: a decoder that would not open, say.
                Log.e(
                    TAG, "onPlayerError: errorCode=" + error.errorCode + " (" +
                        error.errorCodeName + ") uri=" + playbackUri, error
                )
                _binding?.progressBarShadowboxMediaVideo?.visibility = View.INVISIBLE
                // A downgraded URL is derived rather than confirmed, so it can name a file the
                // host never transcoded. Walking back up to the posted one keeps a data-saving
                // 404 from showing an error where the full-size file would have played. Failing
                // that, the post's direct-URL fallback, the way the feed's row does; failing that
                // too, say so -- a page left on its poster with a pause button up looked like a
                // video that was playing.
                if (!retryAtPostedQuality() && !retryAtFallbackUrl()) {
                    showPlaybackError()
                }
            }
        }
        playerListener = listener
        player.addListener(listener)
        player.prepare()
        if (resumePositionMs != C.TIME_UNSET) {
            player.seekTo(resumePositionMs)
            resumePositionMs = C.TIME_UNSET
        }
        applyPlayback()
    }

    /** Stops and releases this page's player, remembering where it was for the next one. */
    private fun releasePlayer() {
        // Ahead of the early return: a page whose player is already gone can still have a pending
        // hide posted on its button, and onDestroyView comes through here.
        _binding?.playButtonShadowboxMediaVideo?.removeCallbacks(hidePlayButton)
        val player = player ?: return
        if (!playbackFailed) {
            resumePositionMs = player.currentPosition
        }
        playerListener?.let { player.removeListener(it) }
        // Detached before the release: PlayerView clears its surface off the player it is losing,
        // and a released player only logs that it ignored the message.
        _binding?.playerViewShadowboxMediaVideo?.player = null
        player.stop()
        player.release()
        this.player = null
        trackSelector = null
        playerListener = null
        // Only if this page was the one playing: clearing the host's flag from any other page
        // would let the screen sleep during playback.
        if (pageActive) {
            host.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    override fun releaseMedia() {
        if (player == null) {
            return
        }
        releasePlayer()
        playerReleased = true
        // The poster goes back up over the surface: the player built on the way back has nothing
        // to show until its first frame, and the page was designed never to sit on black.
        showPoster()
    }

    override fun restoreMedia() {
        if (!playerReleased) {
            return
        }
        playerReleased = false
        buildPlayer()
    }

    /**
     * Puts the post's preview over the player.
     *
     * It is the still the feed showed, fetched when the page is built -- a page ahead of being
     * swiped to -- so the page comes up on the picture instead of on the player's black surface.
     * It stays there until the video is really running: a page sitting behind its play button
     * shows the preview, not whichever frame the decoder happened to leave on the surface when it
     * prepared.
     */
    private fun showPoster() {
        val binding = _binding ?: return
        val poster = binding.previewImageViewShadowboxMediaVideo
        val preview = ShadowboxPreviews.bestPreview(post, maxResolution, dataSavingMode)
        if (preview == null) {
            posterHidden = true
            poster.visibility = View.GONE
            return
        }
        // Put back explicitly rather than assumed fresh: a poster that has already faded out
        // once -- before a rebuild, or a retry -- is GONE at alpha 0, and its fade may still be
        // running.
        poster.animate().cancel()
        poster.alpha = 1f
        poster.visibility = View.VISIBLE
        posterHidden = false
        ShadowboxPreviews.previewRequest(glide, preview.previewUrl)
            .into(poster)
    }

    /**
     * Takes the preview down once the video is both playing and has a frame on the surface, so
     * the picture never goes from preview to black and back. It fades rather than disappears:
     * the first frame is drawn one frame after the player reports it, and a fade covers that.
     */
    private fun hidePosterIfPlaying() {
        if (posterHidden || !firstFrameRendered || player?.isPlaying != true) {
            return
        }
        val poster = _binding?.previewImageViewShadowboxMediaVideo ?: return
        posterHidden = true
        poster.animate().alpha(0f).setDuration(POSTER_FADE_MS).withEndAction {
            _binding?.previewImageViewShadowboxMediaVideo?.visibility = View.GONE
        }.start()
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
        showPlayButton()
    }

    /** Brings the play button back and, if the video is playing, starts its clock again. */
    private fun showPlayButton() {
        playButtonTimedOut = false
        updatePlayButton()
    }

    /**
     * Re-prepares on the file the post actually carries, once, after a downgrade failed. Returns
     * whether it did.
     */
    private fun retryAtPostedQuality(): Boolean {
        if (downgradeFailed) {
            return false
        }
        val postedUri = RedgifsUrlUtils.hdVariant(playbackUri)
            ?: MlbUrlUtils.postedVariant(playbackUri)
            ?: return false
        downgradeFailed = true
        return prepareAgain(postedUri)
    }

    /**
     * Re-prepares on the post's direct-URL fallback, once, after the posted file failed. Returns
     * whether it did. The post itself is left alone: the feed's row rewrites the post's video URL
     * when it does this, but these pages share Post objects with the feed, and what the full
     * player is handed should stay what was posted.
     */
    private fun retryAtFallbackUrl(): Boolean {
        if (fallbackTried) {
            return false
        }
        val fallbackUri = post.videoFallBackDirectUrl?.toUri() ?: return false
        if (fallbackUri == playbackUri) {
            return false
        }
        fallbackTried = true
        return prepareAgain(fallbackUri)
    }

    /** Points the existing player at [source] and prepares it again. False with no player. */
    private fun prepareAgain(source: Uri): Boolean {
        val player = player ?: return false
        playbackUri = source
        isRedditHls = Util.inferContentType(source) == C.CONTENT_TYPE_HLS
        player.setMediaSource(buildMediaSource(source))
        player.prepare()
        return true
    }

    /**
     * Puts the error overlay up. Black behind it, as on the image page: the message is white text
     * with no background of its own and has to stay readable. The play button goes with the
     * poster -- pressing play on a player that has given up would do nothing.
     */
    private fun showPlaybackError() {
        val binding = _binding ?: return
        playbackFailed = true
        binding.previewImageViewShadowboxMediaVideo.animate().cancel()
        binding.previewImageViewShadowboxMediaVideo.visibility = View.GONE
        posterHidden = true
        binding.playbackErrorLinearLayoutShadowboxMediaVideo.visibility = View.VISIBLE
        updatePlayButton()
    }

    /**
     * The tap on the error overlay: a fresh player on the file the page started from, from the
     * start. The tap is also a request to watch, so with autoplay off it counts as pressing play.
     * The failure was as likely the device's as the file's -- a decoder that was busy elsewhere --
     * so both fallback walks get another go.
     */
    private fun retryPlayback() {
        startedByUser = true
        userPaused = false
        releasePlayer()
        resumePositionMs = C.TIME_UNSET
        showPoster()
        buildPlayer()
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
     * start it, and there when the bar comes up, so a playing video can be paused from the same
     * spot. On a playing video it goes on the viewer's clock -- 5 s after it came up, cut rather
     * than faded, the way every other player in the app drops its controls -- and comes back with
     * the next tap or the next change of playback state. It gets out of the way at once in the
     * state meant for watching -- playing, bar hidden.
     */
    private fun updatePlayButton() {
        val binding = _binding ?: return
        val button = binding.playButtonShadowboxMediaVideo
        val playing = player?.playWhenReady ?: false
        button.setIconResource(
            if (playing) R.drawable.ic_pause_24dp else R.drawable.ic_play_arrow_24dp
        )
        val chromeVisible = host.panelVisible.value != false
        val visible = !playbackFailed && (!playing || (chromeVisible && !playButtonTimedOut))
        button.visibility = if (visible) View.VISIBLE else View.GONE
        // One clock, restarted by whatever shows the button on a playing video; a paused video's
        // button is not on it.
        button.removeCallbacks(hidePlayButton)
        if (visible && playing) {
            button.postDelayed(hidePlayButton, Constants.VIDEO_CONTROLS_SHOW_TIMEOUT_MS.toLong())
        }
    }

    override fun onChromeVisibilityChanged(visible: Boolean) {
        showPlayButton()
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

    override fun openFullViewer() {
        val progress = player?.currentPosition ?: 0L
        if (isGifMp4) {
            ShadowboxMediaIntents.openGifMp4(host, post, uri.toString(), progress)
        } else {
            ShadowboxMediaIntents.openVideo(host, post, progress)
        }
    }

    override fun onDestroyView() {
        releasePlayer()
        _binding?.let { glide.clear(it.previewImageViewShadowboxMediaVideo) }
        _binding = null
        super.onDestroyView()
    }

    companion object {
        private const val TAG = "ShadowboxVideoPage"
        private const val POSTER_FADE_MS = 150L
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
