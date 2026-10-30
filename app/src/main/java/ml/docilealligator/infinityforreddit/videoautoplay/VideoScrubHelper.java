package ml.docilealligator.infinityforreddit.videoautoplay;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

import java.util.Locale;

/**
 * Attaches a horizontal swipe-to-scrub gesture to an inline PlayerView.
 * A centered overlay shows the seek position while scrubbing.
 */
public class VideoScrubHelper {

    public interface HelperProvider {
        @Nullable
        ExoPlayerViewHelper getHelper();
    }

    public static void attach(@NonNull PlayerView playerView,
                              @NonNull ViewGroup container,
                              @NonNull HelperProvider helperProvider) {
        Context context = playerView.getContext();
        float density = context.getResources().getDisplayMetrics().density;
        int touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();

        // Scrub time overlay — centered above the seek bar
        TextView overlay = new TextView(context);
        overlay.setTextColor(Color.WHITE);
        overlay.setTextSize(14f);
        int padH = (int) (14 * density);
        int padV = (int) (7 * density);
        overlay.setPadding(padH, padV, padH, padV);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xBB000000);
        bg.setCornerRadius(8 * density);
        overlay.setBackground(bg);
        overlay.setVisibility(android.view.View.GONE);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER);
        container.addView(overlay, lp);

        Handler handler = new Handler(Looper.getMainLooper());
        Runnable hideOverlay = () -> overlay.setVisibility(android.view.View.GONE);

        // Single-element arrays so lambdas can mutate them
        float[] scrubStartX = {0f};
        float[] scrubStartY = {0f};
        long[] scrubStartPosition = {0L};
        boolean[] isScrubbing = {false};
        boolean[] gestureRejected = {false};

        playerView.setOnTouchListener((v, event) -> {
            ExoPlayerViewHelper helper = helperProvider.getHelper();
            if (helper == null) return false;
            ExoPlayer player = helper.getPlayer();
            if (player == null) return false;

            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    scrubStartX[0] = event.getX();
                    scrubStartY[0] = event.getY();
                    isScrubbing[0] = false;
                    gestureRejected[0] = false;
                    handler.removeCallbacks(hideOverlay);
                    return false; // let PlayerView see ACTION_DOWN for tap detection

                case MotionEvent.ACTION_POINTER_DOWN:
                    // Second finger cancels any in-progress scrub
                    if (isScrubbing[0]) {
                        isScrubbing[0] = false;
                        overlay.setVisibility(android.view.View.GONE);
                        handler.removeCallbacks(hideOverlay);
                        if (v.getParent() != null) {
                            v.getParent().requestDisallowInterceptTouchEvent(false);
                        }
                    }
                    gestureRejected[0] = true;
                    return false;

                case MotionEvent.ACTION_MOVE: {
                    if (gestureRejected[0]) return false;

                    float dx = event.getX() - scrubStartX[0];
                    float dy = event.getY() - scrubStartY[0];

                    if (!isScrubbing[0]) {
                        if (Math.abs(dx) > touchSlop && Math.abs(dx) > Math.abs(dy) * 1.5f) {
                            // Horizontal swipe confirmed — cancel PlayerView's tap tracking
                            MotionEvent cancel = MotionEvent.obtain(event);
                            cancel.setAction(MotionEvent.ACTION_CANCEL);
                            playerView.onTouchEvent(cancel);
                            cancel.recycle();

                            isScrubbing[0] = true;
                            scrubStartPosition[0] = player.getCurrentPosition();
                            if (v.getParent() != null) {
                                v.getParent().requestDisallowInterceptTouchEvent(true);
                            }
                            overlay.setVisibility(android.view.View.VISIBLE);
                        } else if (Math.abs(dy) > touchSlop) {
                            // Vertical gesture — let RecyclerView scroll
                            gestureRejected[0] = true;
                            return false;
                        }
                    }

                    if (isScrubbing[0]) {
                        long duration = player.getDuration();
                        if (duration <= 0) return true;

                        int width = v.getWidth();
                        if (width <= 0) {
                            width = context.getResources().getDisplayMetrics().widthPixels;
                        }
                        float fraction = dx / (float) width;
                        long target = scrubStartPosition[0] + (long) (fraction * duration);
                        target = Math.max(0, Math.min(duration, target));
                        player.seekTo(target);
                        overlay.setText(buildOverlayText(dx, target, duration));
                        return true;
                    }
                    return false;
                }

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (isScrubbing[0]) {
                        isScrubbing[0] = false;
                        if (v.getParent() != null) {
                            v.getParent().requestDisallowInterceptTouchEvent(false);
                        }
                        handler.postDelayed(hideOverlay, 800);
                        return true; // consume so no tap fires after a scrub
                    }
                    return false;
            }
            return false;
        });
    }

    private static String buildOverlayText(float dx, long positionMs, long durationMs) {
        String position = formatTime(positionMs);
        String duration = formatTime(durationMs);
        if (dx < 0) {
            return "◄  " + position + " / " + duration;
        } else {
            return position + " / " + duration + "  ►";
        }
    }

    private static String formatTime(long ms) {
        long totalSec = ms / 1000;
        long min = totalSec / 60;
        long sec = totalSec % 60;
        if (min >= 60) {
            return String.format(Locale.US, "%d:%02d:%02d", min / 60, min % 60, sec);
        }
        return String.format(Locale.US, "%d:%02d", min, sec);
    }
}
