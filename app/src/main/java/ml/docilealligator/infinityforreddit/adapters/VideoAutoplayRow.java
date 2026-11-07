package ml.docilealligator.infinityforreddit.adapters;

import android.view.View;
import androidx.annotation.NonNull;
import androidx.media3.common.util.UnstableApi;
import ml.docilealligator.infinityforreddit.videoautoplay.ToroPlayer;
import ml.docilealligator.infinityforreddit.videoautoplay.media.PlaybackInfo;
import ml.docilealligator.infinityforreddit.videoautoplay.widget.Container;

/**
 * A feed row that autoplays a video: to the feed's Container it is a {@link ToroPlayer}, and every
 * part of that is the row's {@link VideoAutoplayImpl}.
 */
@UnstableApi
interface VideoAutoplayRow extends ToroPlayer {
    VideoAutoplayImpl toroPlayer();

    @NonNull
    @Override
    default View getPlayerView() {
        return toroPlayer().getPlayerView();
    }

    @NonNull
    @Override
    default PlaybackInfo getCurrentPlaybackInfo() {
        return toroPlayer().getCurrentPlaybackInfo();
    }

    @Override
    default void initialize(@NonNull Container container, @NonNull PlaybackInfo playbackInfo) {
        toroPlayer().initialize(container, playbackInfo);
    }

    @Override
    default void play() {
        toroPlayer().play();
    }

    @Override
    default void pause() {
        toroPlayer().pause();
    }

    @Override
    default boolean isPlaying() {
        return toroPlayer().isPlaying();
    }

    @Override
    default void release() {
        toroPlayer().release();
    }

    @Override
    default boolean wantsToPlay() {
        return toroPlayer().wantsToPlay();
    }

    @Override
    default int getPlayerOrder() {
        return toroPlayer().getPlayerOrder();
    }
}
