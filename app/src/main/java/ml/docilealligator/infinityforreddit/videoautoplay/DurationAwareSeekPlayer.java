package ml.docilealligator.infinityforreddit.videoautoplay;

import static ml.docilealligator.infinityforreddit.Constants.VIDEO_SEEK_BACK_INCREMENT_MS;
import static ml.docilealligator.infinityforreddit.Constants.VIDEO_SEEK_FORWARD_INCREMENT_MS;
import static ml.docilealligator.infinityforreddit.Constants.VIDEO_SHORT_DURATION_THRESHOLD_MS;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.media3.common.C;
import androidx.media3.common.ForwardingPlayer;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;

import java.util.concurrent.TimeUnit;

@OptIn(markerClass = UnstableApi.class)
public class DurationAwareSeekPlayer extends ForwardingPlayer {

    private static final int SHORT_VIDEO_SEEK_DIVISOR = 5;

    public DurationAwareSeekPlayer(@NonNull Player player) {
        super(player);
    }

    @Override
    public long getSeekBackIncrement() {
        long duration = getDuration();
        if (isShortVideo(duration)) {
            return getShortVideoSeekIncrement(duration);
        }

        return VIDEO_SEEK_BACK_INCREMENT_MS;
    }

    @Override
    public long getSeekForwardIncrement() {
        long duration = getDuration();
        if (isShortVideo(duration)) {
            return getShortVideoSeekIncrement(duration);
        }

        return VIDEO_SEEK_FORWARD_INCREMENT_MS;
    }

    @Override
    public void seekBack() {
        seekBy(-getSeekBackIncrement());
    }

    @Override
    public void seekForward() {
        seekBy(getSeekForwardIncrement());
    }

    private void seekBy(long offsetMs) {
        long seekPositionMs = Math.max(0, getCurrentPosition() + offsetMs);
        long duration = getDuration();
        if (duration != C.TIME_UNSET) {
            seekPositionMs = Math.min(duration, seekPositionMs);
        }
        seekTo(seekPositionMs);
    }

    private static boolean isShortVideo(long durationMs) {
        return durationMs != C.TIME_UNSET && durationMs > 0 && durationMs < VIDEO_SHORT_DURATION_THRESHOLD_MS;
    }

    static long getShortVideoSeekIncrement(long durationMs) {
        long durationSeconds = Math.round((double) durationMs / TimeUnit.SECONDS.toMillis(1));
        long incrementSeconds = Math.max(1, (long) Math.floor(
                ((double) durationSeconds / SHORT_VIDEO_SEEK_DIVISOR) + 0.5
        ));
        return TimeUnit.SECONDS.toMillis(incrementSeconds);
    }
}
