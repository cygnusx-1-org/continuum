package ml.docilealligator.infinityforreddit.adapters;

import android.app.Dialog;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Handler;
import android.view.View;
import android.widget.ImageView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.media3.common.C;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.TrackSelectionOverride;
import androidx.media3.common.Tracks;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import androidx.media3.ui.DefaultTimeBar;
import androidx.media3.ui.PlayerView;
import androidx.media3.ui.TimeBar;
import androidx.media3.ui.TrackSelectionDialogBuilder;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.google.common.collect.ImmutableList;
import ml.docilealligator.infinityforreddit.FetchVideoLinkListener;
import ml.docilealligator.infinityforreddit.R;
import ml.docilealligator.infinityforreddit.activities.BaseActivity;
import ml.docilealligator.infinityforreddit.post.FetchShortClipVideo;
import ml.docilealligator.infinityforreddit.post.FetchStreamableVideo;
import ml.docilealligator.infinityforreddit.post.Post;
import ml.docilealligator.infinityforreddit.thing.StreamableVideo;
import ml.docilealligator.infinityforreddit.utils.APIUtils;
import ml.docilealligator.infinityforreddit.utils.SharedPreferencesUtils;
import ml.docilealligator.infinityforreddit.utils.ShortClipHostUtils;
import ml.docilealligator.infinityforreddit.videoautoplay.ExoPlayerViewHelper;
import ml.docilealligator.infinityforreddit.videoautoplay.Playable;
import ml.docilealligator.infinityforreddit.videoautoplay.ToroPlayer;
import ml.docilealligator.infinityforreddit.videoautoplay.ToroUtil;
import ml.docilealligator.infinityforreddit.videoautoplay.media.PlaybackInfo;
import ml.docilealligator.infinityforreddit.videoautoplay.widget.Container;
import pl.droidsonroids.gif.GifImageView;
import retrofit2.Call;

@UnstableApi
public abstract class VideoAutoplayImpl implements ToroPlayer {
    View itemView;
    GifImageView previewImageView;
    ImageView errorLoadingRedgifsImageView;
    PlayerView videoPlayer;
    @Nullable
    final Controls controls;
    @Nullable
    Container container;
    @Nullable
    ExoPlayerViewHelper helper;
    @Nullable
    Uri mediaUri;
    /**
     * The URI {@link #helper} was built for, which is not always the one this card shows now:
     * a helper is created once and goes on rendering into this surface until it is released.
     */
    @Nullable
    private Uri initializedUri;
    /**
     * Puts the no-preview glyph up when a still fails to load, so a card whose preview could
     * not be fetched shows what a card with no preview at all shows, rather than a blank box
     * that stays blank until the clip's first frame.
     */
    final RequestListener<Drawable> previewRequestListener = new RequestListener<>() {
        @Override
        public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
            host.applyNoPreviewPlaceholder(previewImageView);
            // Handled here: returning false lets Glide put its own error drawable up instead,
            // and there is none, which is the blank box this exists to replace.
            return true;
        }

        @Override
        public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
            return false;
        }
    };
    private float volume;
    /** A video "Mute NSFW videos" covers: it stays silent whatever the remembered muting option says. */
    private boolean forceMuted;
    @Nullable
    public Call<String> fetchRedgifsOrStreamableVideoCall;
    @Nullable
    public FetchShortClipVideo.Cancellable fetchShortClipVideoCancellable;
    boolean isManuallyPaused;
    boolean setDefaultResolutionAlready;

    private final PostVideoHost host;
    private final BaseActivity mActivity;

    /**
     * The controls a feed card lays over its player. A tile that plays muted and bare, as the
     * gallery layout's does, has none, and passes null in their place.
     */
    static final class Controls {
        final ImageView videoQualityButton;
        final ImageView muteButton;
        final ImageView fullscreenButton;
        final ImageView playPauseButton;
        final DefaultTimeBar progressBar;
        final Drawable playDrawable;
        final Drawable pauseDrawable;

        Controls(ImageView videoQualityButton, ImageView muteButton, ImageView fullscreenButton,
                 ImageView playPauseButton, DefaultTimeBar progressBar,
                 Drawable playDrawable, Drawable pauseDrawable) {
            this.videoQualityButton = videoQualityButton;
            this.muteButton = muteButton;
            this.fullscreenButton = fullscreenButton;
            this.playPauseButton = playPauseButton;
            this.progressBar = progressBar;
            this.playDrawable = playDrawable;
            this.pauseDrawable = pauseDrawable;
        }
    }

    public VideoAutoplayImpl(View itemView, GifImageView previewImageView,
                             ImageView errorLoadingRedgifsImageView, PlayerView videoPlayer,
                             @Nullable Controls controls, PostVideoHost host) {
        this.host = host;
        this.mActivity = host.activity();
        this.itemView = itemView;
        this.previewImageView = previewImageView;
        this.errorLoadingRedgifsImageView = errorLoadingRedgifsImageView;
        this.videoPlayer = videoPlayer;
        this.controls = controls;
        if (controls != null) {
            wireControls(controls);
        }
    }

    private void wireControls(Controls controls) {
        controls.muteButton.setOnClickListener(view -> {
            if (helper != null) {
                if (helper.getVolume() != 0) {
                    controls.muteButton.setImageDrawable(ContextCompat.getDrawable(mActivity, R.drawable.ic_mute_24dp));
                    helper.setVolume(0f);
                    volume = 0f;
                    host.fragment().videoAutoplayChangeMutingOption(true);
                } else {
                    controls.muteButton.setImageDrawable(ContextCompat.getDrawable(mActivity, R.drawable.ic_unmute_24dp));
                    helper.setVolume(1f);
                    volume = 1f;
                    host.fragment().videoAutoplayChangeMutingOption(false);
                }
            }
        });

        controls.fullscreenButton.setOnClickListener(view -> {
            Post post = getPost();
            if (post != null) {
                markPostRead(post, true);

                if (helper != null) {
                    host.openMedia(post, helper.getLatestPlaybackInfo().getResumePosition());
                } else {
                    host.openMedia(post, -1);
                }
            }
        });

        controls.playPauseButton.setOnClickListener(view -> {
            if (isPlaying()) {
                pause();
                isManuallyPaused = true;
                savePlaybackInfo(getPlayerOrder(), getCurrentPlaybackInfo());
            } else {
                isManuallyPaused = false;
                play();
            }
        });

        controls.progressBar.addListener(new TimeBar.OnScrubListener() {
            @Override
            public void onScrubStart(TimeBar timeBar, long position) {

            }

            @Override
            public void onScrubMove(TimeBar timeBar, long position) {

            }

            @Override
            public void onScrubStop(TimeBar timeBar, long position, boolean canceled) {
                if (!canceled) {
                    savePlaybackInfo(getPlayerOrder(), getCurrentPlaybackInfo());
                }
            }
        });

        previewImageView.setOnClickListener(view -> controls.fullscreenButton.performClick());

        videoPlayer.setOnClickListener(view -> {
            if (host.easierToWatchInFullScreen() && videoPlayer.isControllerFullyVisible()) {
                controls.fullscreenButton.performClick();
            }
        });
    }

    void bindVideoUri(Uri videoUri) {
        // A helper is built once, for one URI, and keeps rendering into this card's surface
        // until something releases it. A holder rebound off the scrap list never passes
        // through onViewRecycled, so without this the clip of the post this card last held
        // plays on under the new post's still -- and takes that still down with its next
        // frame. Letting it go here is also what makes a switch to the direct-URL fallback
        // take effect, since the old helper would otherwise keep the source that just failed.
        if (helper != null && initializedUri != null && !initializedUri.equals(videoUri)) {
            release();
        }
        mediaUri = videoUri;
    }

    void setVolume(float volume) {
        this.volume = volume;
    }

    void setForceMuted(boolean forceMuted) {
        this.forceMuted = forceMuted;
    }

    /**
     * Follows a muting choice made on another video, so every video the feed has set up -- the
     * ones sharing the screen when the limit allows several, and the ones ready below -- answers
     * to the same mute state instead of keeping the volume they were prepared with.
     */
    void applyMute(boolean mute) {
        if (forceMuted) {
            // Stays silent unless the reader unmutes this very video with its own button.
            return;
        }
        volume = mute ? 0f : 1f;
        if (helper != null && helper.getPlayer() != null) {
            helper.setVolume(volume);
            if (controls != null && controls.muteButton.getVisibility() == View.VISIBLE) {
                showMuteState(controls.muteButton);
            }
        }
    }

    private void showQualityChoice(ImageView button) {
        button.setVisibility(View.VISIBLE);
        button.setOnClickListener(view -> {
            if (helper == null) {
                return;
            }
            TrackSelectionDialogBuilder builder = new TrackSelectionDialogBuilder(mActivity, mActivity.getString(R.string.select_video_quality), helper.getPlayer(), C.TRACK_TYPE_VIDEO);
            builder.setShowDisableOption(true);
            builder.setAllowAdaptiveSelections(false);
            Dialog dialog = builder.setTheme(R.style.MaterialAlertDialogTheme).build();
            dialog.show();
            if (dialog instanceof AlertDialog) {
                ((AlertDialog) dialog).getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(host.customThemeWrapper().getPrimaryTextColor());
                ((AlertDialog) dialog).getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(host.customThemeWrapper().getPrimaryTextColor());
            }
        });
    }

    void resetVolume() {
        volume = 0f;
    }

    /** Shows {@code button} with the icon for the volume the clip has now. */
    private void showMuteState(ImageView button) {
        button.setVisibility(View.VISIBLE);
        button.setImageDrawable(ContextCompat.getDrawable(mActivity,
                volume != 0f ? R.drawable.ic_unmute_24dp : R.drawable.ic_mute_24dp));
    }

    /**
     * Lets go of the post this player was bound to, for the next one to bind: its clip, its
     * pending lookups, and whatever its controls were showing.
     */
    void reset() {
        mediaUri = null;
        if (fetchRedgifsOrStreamableVideoCall != null && !fetchRedgifsOrStreamableVideoCall.isCanceled()) {
            fetchRedgifsOrStreamableVideoCall.cancel();
            fetchRedgifsOrStreamableVideoCall = null;
        }
        if (fetchShortClipVideoCancellable != null) {
            fetchShortClipVideoCancellable.cancel();
            fetchShortClipVideoCancellable = null;
        }
        errorLoadingRedgifsImageView.setVisibility(View.GONE);
        if (controls != null) {
            controls.videoQualityButton.setVisibility(View.GONE);
            controls.muteButton.setVisibility(View.GONE);
        }
        if (!isManuallyPaused) {
            resetVolume();
        }
        setDefaultResolutionAlready = false;
    }

    private void savePlaybackInfo(int order, @Nullable PlaybackInfo playbackInfo) {
        if (container != null) container.savePlaybackInfo(order, playbackInfo);
    }

    /**
     * The fetch callbacks below re-check that this holder is still showing the post the
     * fetch was started for. That used to be a position captured at bind time compared
     * against getAdapterPosition(); comparing the post itself says what is actually meant
     * and stays right when the list shifts under a holder that never rebound.
     */
    void loadVideo() {
        Post post = getPost();
        /*if (post.isRedgifs() && !post.isLoadedStreamableVideoAlready()) {
            fetchRedgifsOrStreamableVideoCall =
                    mRedgifsRetrofit.create(RedgifsAPI.class).getRedgifsData(
                            APIUtils.getRedgifsOAuthHeader(mCurrentAccountSharedPreferences
                                    .getString(SharedPreferencesUtils.REDGIFS_ACCESS_TOKEN, "")),
                            post.getRedgifsId(), APIUtils.USER_AGENT);
            FetchRedgifsVideoLinks.fetchRedgifsVideoLinksInRecyclerViewAdapter(host.executor(), new Handler(),
                    fetchRedgifsOrStreamableVideoCall,
                    new FetchVideoLinkListener() {
                        @Override
                        public void onFetchRedgifsVideoLinkSuccess(String webm, String mp4) {
                            post.setVideoDownloadUrl(mp4);
                            post.setVideoUrl(mp4);
                            post.setLoadedStreamableVideoAlready(true);
                            if (post == getPost()) {
                                bindVideoUri(Uri.parse(post.getVideoUrl()));
                            }
                        }

                        @Override
                        public void failed(@Nullable Integer messageRes) {
                            if (post == getPost()) {
                                loadFallbackDirectVideo();
                            }
                        }
                    });
        } else */if(post.isStreamable() && !post.isLoadedStreamableVideoAlready()) {
            String streamableShortCode = post.getStreamableShortCode();
            if (streamableShortCode != null) {
                fetchRedgifsOrStreamableVideoCall =
                        host.streamableApiProvider().get().getStreamableData(streamableShortCode);
                FetchStreamableVideo.fetchStreamableVideoInRecyclerViewAdapter(host.executor(), new Handler(),
                        fetchRedgifsOrStreamableVideoCall,
                        new FetchVideoLinkListener() {
                            // Reference equality is the point, not an oversight: this asks
                            // whether the holder is still bound to the very object the fetch was
                            // started for. Post.equals() compares id *plus* mutable state
                            // (voteType, saved, isRead...), so upvoting the post while the video
                            // URL was in flight would make equals() false and drop the binding
                            // for a post still on screen.
                            @SuppressWarnings("ReferenceEquality")
                            @Override
                            public void onFetchStreamableVideoLinkSuccess(StreamableVideo streamableVideo) {
                                StreamableVideo.Media media = streamableVideo.mp4 == null ? streamableVideo.mp4Mobile : streamableVideo.mp4;
                                if (media == null) {
                                    return;
                                }
                                post.setVideoDownloadUrl(media.url);
                                post.setVideoUrl(media.url);
                                post.setLoadedStreamableVideoAlready(true);
                                if (post == getPost()) {
                                    rebindResolvedVideo();
                                }
                            }

                            @SuppressWarnings("ReferenceEquality") // Same holder-identity check as above.
                            @Override
                            public void failed(@Nullable Integer messageRes) {
                                if (post == getPost()) {
                                    loadFallbackDirectVideo();
                                }
                            }
                        });
            }
        } else if (post.isShortClip() && !post.isLoadedStreamableVideoAlready()) {
            ShortClipHostUtils.Host shortClipHost = post.getShortClipHost();
            String shortClipId = post.getShortClipId();
            if (shortClipHost != null && shortClipId != null) {
                fetchShortClipVideoCancellable = new FetchShortClipVideo.Cancellable();
                FetchShortClipVideo.fetchShortClipVideoInRecyclerViewAdapter(host.executor(), new Handler(),
                        host.shortClipOkHttpClient(), shortClipHost, shortClipId, post.getUrl(),
                        fetchShortClipVideoCancellable,
                        new FetchVideoLinkListener() {
                            @SuppressWarnings("ReferenceEquality") // Same holder-identity check as above.
                            @Override
                            public void onFetchShortClipVideoLinkSuccess(String videoUrl) {
                                post.setVideoDownloadUrl(videoUrl);
                                post.setVideoUrl(videoUrl);
                                post.setLoadedStreamableVideoAlready(true);
                                if (post == getPost()) {
                                    rebindResolvedVideo();
                                }
                            }

                            @SuppressWarnings("ReferenceEquality") // Same holder-identity check as above.
                            @Override
                            public void failed(@Nullable Integer messageRes) {
                                if (post == getPost()) {
                                    demoteToLinkPost(post);
                                }
                            }
                        });
            }
        } else {
            bindVideoUri(Uri.parse(post.getVideoUrl()));
        }
    }

    /**
     * Rebinds the row now that its video URL is known.
     *
     * <p>Rebinding rather than writing the URI onto the holder the fetch was dispatched from,
     * which may since have been recycled or rebound. A rebind runs the same branch that serves
     * a post whose URL was already known, so a resolved clip and a cached one take one code
     * path, and Toro starts playback the way it does for every other row. It also matches the
     * failure path, which rebinds to swap in the link card.
     */
    void rebindResolvedVideo() {
        int position = getAdapterPosition();
        if (position != RecyclerView.NO_POSITION) {
            host.notifyItemChanged(position);
        }
    }

    /**
     * Renders {@code post} as the link card it was parsed from, after its video could not
     * be resolved.
     *
     * <p>Runs from a fetch callback, which is a later main-loop turn than the bind that started
     * it -- notifyItemChanged during a layout pass throws. getItemViewType reads the post type
     * live, so re-binding is all it takes to swap in the link holder.
     *
     * <p>The feed is a paging adapter, so this lasts until the next PagingData submission and
     * then has to happen again. That is the right trade: making it durable would mean writing a
     * host's outage into the post cache.
     */
    void demoteToLinkPost(Post post) {
        post.demoteToLinkPost();
        int position = getAdapterPosition();
        if (position != RecyclerView.NO_POSITION) {
            host.notifyItemChanged(position);
        }
    }

    void loadFallbackDirectVideo() {
        Post post = getPost();
        if (post.getVideoFallBackDirectUrl() != null) {
            // Held across the rebind below: binding a different URI lets go of the player that
            // just failed, and a released player has no container to ask for the pass that
            // starts the replacement.
            Container settled = container;
            bindVideoUri(Uri.parse(post.getVideoFallBackDirectUrl()));
            post.setVideoDownloadUrl(post.getVideoFallBackDirectUrl());
            post.setVideoUrl(post.getVideoFallBackDirectUrl());
            post.setLoadedStreamableVideoAlready(true);
            if (settled != null) {
                settled.onScrollStateChanged(RecyclerView.SCROLL_STATE_IDLE);
            }
        }
    }

    @NonNull
    @Override
    public View getPlayerView() {
        return videoPlayer;
    }

    @NonNull
    @Override
    public PlaybackInfo getCurrentPlaybackInfo() {
        return helper != null && mediaUri != null ? helper.getLatestPlaybackInfo() : new PlaybackInfo();
    }

    @OptIn(markerClass = UnstableApi.class)
    @Override
    public void initialize(@NonNull Container container, @NonNull PlaybackInfo playbackInfo) {
        if (this.container == null) {
            this.container = container;
            this.container.setPlayerSelector(host.playerSelector());
        }
        if (mediaUri == null) {
            return;
        }
        // A finished clip is already set up and resting on its last frame, and re-initializing it
        // does harm: onCompleted() leaves SCRAP in the playback cache, so the PlaybackInfo passed
        // here is the fragment initializer's, which is muted. Since a finished clip no longer
        // counts as playing, the Container re-initializes it on every selection pass -- including
        // the one onPlaybackFinished() triggers -- so a clip the reader had unmuted would come back
        // silent when they pressed play.
        if (helper != null && helper.isEnded()) {
            return;
        }
        if (helper == null) {
            helper = new ExoPlayerViewHelper(this, mediaUri, null, host.exoCreator());
            initializedUri = mediaUri;
            helper.addEventListener(new Playable.DefaultEventListener() {
                @Override
                public void onEvents(@NonNull Player player, @NonNull Player.Events events) {
                    if (events.containsAny(
                            Player.EVENT_PLAY_WHEN_READY_CHANGED,
                            Player.EVENT_PLAYBACK_STATE_CHANGED,
                            Player.EVENT_PLAYBACK_SUPPRESSION_REASON_CHANGED)) {
                        if (controls != null) {
                            controls.playPauseButton.setImageDrawable(
                                    Util.shouldShowPlayButton(player) ? controls.playDrawable : controls.pauseDrawable);
                        }
                    }
                    if (events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED)
                            && player.getPlaybackState() == Player.STATE_ENDED) {
                        onPlaybackFinished();
                    }
                }

                @Override
                public void onTracksChanged(@NonNull Tracks tracks) {
                    if (helper == null) {
                        return;
                    }
                    ImmutableList<Tracks.Group> trackGroups = tracks.getGroups();
                    if (!trackGroups.isEmpty()) {
                        if (getPost().isNormalVideo()) {
                            if (controls != null) {
                                showQualityChoice(controls.videoQualityButton);
                            }

                            if (!setDefaultResolutionAlready) {
                                int desiredResolution = 0;
                                if (host.dataSavingMode()) {
                                    if (host.dataSavingModeDefaultResolution() > 0) {
                                        desiredResolution = host.dataSavingModeDefaultResolution();
                                    }
                                } else if (host.nonDataSavingModeDefaultResolution() > 0) {
                                    desiredResolution = host.nonDataSavingModeDefaultResolution();
                                }

                                if (desiredResolution > 0) {
                                    TrackSelectionOverride trackSelectionOverride = null;
                                    int bestTrackIndex = -1;
                                    int bestResolution = -1;
                                    int worstResolution = Integer.MAX_VALUE;
                                    int worstTrackIndex = -1;
                                    Tracks.Group bestTrackGroup = null;
                                    Tracks.Group worstTrackGroup = null;
                                    for (Tracks.Group trackGroup : tracks.getGroups()) {
                                        if (trackGroup.getType() == C.TRACK_TYPE_VIDEO) {
                                            for (int trackIndex = 0; trackIndex < trackGroup.length; trackIndex++) {
                                                int trackResolution = Math.min(trackGroup.getTrackFormat(trackIndex).height, trackGroup.getTrackFormat(trackIndex).width);
                                                if (trackResolution <= desiredResolution && trackResolution > bestResolution) {
                                                    bestTrackIndex = trackIndex;
                                                    bestResolution = trackResolution;
                                                    bestTrackGroup = trackGroup;
                                                }
                                                if (trackResolution < worstResolution) {
                                                    worstTrackIndex = trackIndex;
                                                    worstResolution = trackResolution;
                                                    worstTrackGroup = trackGroup;
                                                }
                                            }
                                        }
                                    }

                                    if (bestTrackIndex != -1 && bestTrackGroup != null) {
                                        trackSelectionOverride = new TrackSelectionOverride(
                                                bestTrackGroup.getMediaTrackGroup(),
                                                ImmutableList.of(bestTrackIndex)
                                        );
                                    } else if (worstTrackIndex != -1 && worstTrackGroup != null) {
                                        trackSelectionOverride = new TrackSelectionOverride(
                                                worstTrackGroup.getMediaTrackGroup(),
                                                ImmutableList.of(worstTrackIndex)
                                        );
                                    }

                                    if (trackSelectionOverride != null) {
                                        helper.getPlayer().setTrackSelectionParameters(
                                                helper.getPlayer().getTrackSelectionParameters()
                                                        .buildUpon()
                                                        .addOverride(trackSelectionOverride)
                                                        .build()
                                        );
                                    }
                                }
                                setDefaultResolutionAlready = true;
                            }
                        }

                        for (int i = 0; i < trackGroups.size(); i++) {
                            String mimeType = trackGroups.get(i).getTrackFormat(0).sampleMimeType;
                            if (mimeType != null && mimeType.contains("audio")) {
                                if (host.fragment().getMasterMutingOption() != null) {
                                    volume = host.fragment().getMasterMutingOption() || forceMuted ? 0f : 1f;
                                }
                                helper.setVolume(volume);
                                if (controls != null) {
                                    showMuteState(controls.muteButton);
                                }
                                break;
                            }
                        }
                    } else if (controls != null) {
                        controls.muteButton.setVisibility(View.GONE);
                    }
                }

                @Override
                public void onRenderedFirstFrame() {
                    // Only the clip this card is bound to now may take its still down. This
                    // listener outlives the post it was made for: a player released on a
                    // posted pass, or one under a holder rebound off the scrap list, can put
                    // a frame of the previous post's video up after the card has moved on,
                    // and hiding the still for it leaves a video card showing nothing until
                    // the new clip starts -- the blank preview that came and went while
                    // scrolling a feed of videos.
                    if (mediaUri == null || !mediaUri.equals(initializedUri)) {
                        return;
                    }
                    // Don't clear the preview image - just hide it
                    // This allows it to be shown again if the player is released while scrolling.
                    // Hidden but kept in the layout: a gallery tile is sized by its still, and
                    // would collapse around the clip without it.
                    previewImageView.setVisibility(View.INVISIBLE);
                }

                @Override
                public void onPlayerError(@NonNull PlaybackException error) {
                    Post post = getPost();
                    if (post.getVideoFallBackDirectUrl() == null || post.getVideoFallBackDirectUrl().equals(java.util.Objects.requireNonNull(mediaUri).toString())) {
                        errorLoadingRedgifsImageView.setVisibility(View.VISIBLE);
                    } else {
                        loadFallbackDirectVideo();
                    }
                }
            });
        }
        helper.initialize(container, playbackInfo);
    }

    @Override
    public void play() {
        if (helper != null && mediaUri != null) {
            if (!isPlaying() && isManuallyPaused) {
                helper.play();
                pause();
                helper.setVolume(volume);
            } else {
                helper.play();
            }
        }
    }

    @Override
    public void pause() {
        if (helper != null) helper.pause();
    }

    /**
     * Hands the autoplay slot on once this clip has run out.
     *
     * <p>Finishing releases the slot -- wantsToPlay() stops asking for it -- but nothing
     * re-runs the selection, and the Container only re-selects when the feed scrolls. Without
     * this the reader who sits still after a video ends is left with a feed where nothing
     * plays at all, which is no better than the finished clip holding the slot.
     *
     * <p>Posted rather than called here: this arrives inside an ExoPlayer callback, and the
     * re-selection starts and stops other players. Only while the feed is at rest, because a
     * forced idle pass during a fling would start a player that is on its way off screen.
     */
    private void onPlaybackFinished() {
        Container settled = container;
        if (settled == null) {
            return;
        }
        settled.post(() -> {
            if (settled.getScrollState() == RecyclerView.SCROLL_STATE_IDLE) {
                settled.onScrollStateChanged(RecyclerView.SCROLL_STATE_IDLE);
                // The Container's own dispatch does not reach the adapter's scroll listener,
                // so the gif cards are told separately.
                host.scheduleGifAutoplayUpdate();
            }
        });
    }

    @Override
    public boolean isPlaying() {
        return helper != null && helper.isPlaying();
    }

    @Override
    public void release() {
        if (helper != null) {
            helper.release();
            helper = null;
        }
        initializedUri = null;
        // Show the preview image again when player is released
        if (previewImageView != null) {
            previewImageView.setVisibility(View.VISIBLE);
        }
        isManuallyPaused = false;
        container = null;
    }

    @Override
    public boolean wantsToPlay() {
        // A clip that has run out stops asking for the slot, so the next card down gets it.
        // Otherwise, with looping off, one finished video at the top of the screen held the
        // only autoplay slot and nothing below it ever started. It keeps its last frame -- the
        // Container leaves a player it did not select alone -- and the play button restarts it,
        // because play() re-prepares a finished player.
        if (helper != null && helper.isEnded()) {
            return false;
        }
        return host.canPlayVideo() && mediaUri != null && ToroUtil.visibleAreaOffset(this, itemView.getParent()) >= host.startAutoplayVisibleAreaOffset();
    }

    abstract int getAdapterPosition();

    abstract Post getPost();

    abstract void markPostRead(Post post, boolean changePostItemColor);
}
