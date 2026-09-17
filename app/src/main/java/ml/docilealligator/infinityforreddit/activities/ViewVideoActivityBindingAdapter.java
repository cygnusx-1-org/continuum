package ml.docilealligator.infinityforreddit.activities;

import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import app.futured.hauler.HaulerView;
import app.futured.hauler.LockableNestedScrollView;
import com.google.android.material.bottomappbar.BottomAppBar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.loadingindicator.LoadingIndicator;
import java.util.Objects;
import ml.docilealligator.infinityforreddit.R;
import ml.docilealligator.infinityforreddit.databinding.ActivityViewVideoBinding;
import ml.docilealligator.infinityforreddit.databinding.ActivityViewVideoZoomableBinding;

public class ViewVideoActivityBindingAdapter {
    @Nullable
    private ActivityViewVideoBinding binding;
    @Nullable
    private ActivityViewVideoZoomableBinding zoomableBinding;

    private final MaterialButton playPauseButton;
    private final ImageButton forwardButton;
    private final ImageButton rewindButton;
    private final MaterialButton muteButton;
    private final MaterialButton videoQualityButton;
    private final BottomAppBar bottomAppBar;
    private final TextView titleTextView;
    private final ImageView downloadButton;
    private final ImageView shareButton;
    private final ImageView playbackSpeedButton;
    private final ImageView rotateLeftButton;
    private final ImageView rotateRightButton;

    public ViewVideoActivityBindingAdapter(ActivityViewVideoBinding binding) {
        this.binding = binding;
        playPauseButton = binding.getRoot().findViewById(R.id.exo_play_pause_button_exo_playback_control_view);
        forwardButton = binding.getRoot().findViewById(R.id.exo_ffwd);
        rewindButton = binding.getRoot().findViewById(R.id.exo_rew);
        muteButton = binding.getRoot().findViewById(R.id.mute_exo_playback_control_view);
        videoQualityButton = binding.getRoot().findViewById(R.id.video_quality_exo_playback_control_view);
        bottomAppBar = binding.getRoot().findViewById(R.id.bottom_navigation_exo_playback_control_view);
        titleTextView = binding.getRoot().findViewById(R.id.title_text_view_exo_playback_control_view);
        downloadButton = binding.getRoot().findViewById(R.id.download_image_view_exo_playback_control_view);
        shareButton = binding.getRoot().findViewById(R.id.share_image_view_exo_playback_control_view);
        playbackSpeedButton = binding.getRoot().findViewById(R.id.playback_speed_image_view_exo_playback_control_view);
        rotateLeftButton = binding.getRoot().findViewById(R.id.rotate_left_image_view_exo_playback_control_view);
        rotateRightButton = binding.getRoot().findViewById(R.id.rotate_right_image_view_exo_playback_control_view);
    }

    public ViewVideoActivityBindingAdapter(ActivityViewVideoZoomableBinding binding) {
        zoomableBinding = binding;
        playPauseButton = binding.getRoot().findViewById(R.id.exo_play_pause_button_exo_playback_control_view);
        forwardButton = binding.getRoot().findViewById(R.id.exo_ffwd);
        rewindButton = binding.getRoot().findViewById(R.id.exo_rew);
        muteButton = binding.getRoot().findViewById(R.id.mute_exo_playback_control_view);
        videoQualityButton = binding.getRoot().findViewById(R.id.video_quality_exo_playback_control_view);
        bottomAppBar = binding.getRoot().findViewById(R.id.bottom_navigation_exo_playback_control_view);
        titleTextView = binding.getRoot().findViewById(R.id.title_text_view_exo_playback_control_view);
        downloadButton = binding.getRoot().findViewById(R.id.download_image_view_exo_playback_control_view);
        shareButton = binding.getRoot().findViewById(R.id.share_image_view_exo_playback_control_view);
        playbackSpeedButton = binding.getRoot().findViewById(R.id.playback_speed_image_view_exo_playback_control_view);
        rotateLeftButton = binding.getRoot().findViewById(R.id.rotate_left_image_view_exo_playback_control_view);
        rotateRightButton = binding.getRoot().findViewById(R.id.rotate_right_image_view_exo_playback_control_view);
    }

    public HaulerView getHaulerView() {
        return binding == null ? Objects.requireNonNull(zoomableBinding).haulerViewViewVideoActivity : binding.haulerViewViewVideoActivity;
    }

    public CoordinatorLayout getRoot() {
        return binding == null ? Objects.requireNonNull(zoomableBinding).coordinatorLayoutViewVideoActivity : binding.coordinatorLayoutViewVideoActivity;
    }

    public Toolbar getToolbar() {
        return binding == null ? Objects.requireNonNull(zoomableBinding).toolbarViewVideoActivity : binding.toolbarViewVideoActivity;
    }

    public LoadingIndicator getLoadingIndicator() {
        return binding == null ? Objects.requireNonNull(zoomableBinding).progressBarViewVideoActivity : binding.progressBarViewVideoActivity;
    }

    public MaterialButton getPlayPauseButton() {
        return playPauseButton;
    }

    public ImageButton getForwardButton() {
        return forwardButton;
    }

    public ImageButton getRewindButton() {
        return rewindButton;
    }

    public MaterialButton getMuteButton() {
        return muteButton;
    }

    public MaterialButton getVideoQualityButton() {
        return videoQualityButton;
    }

    public BottomAppBar getBottomAppBar() {
        return bottomAppBar;
    }

    public TextView getTitleTextView() {
        return titleTextView;
    }

    public ImageView getDownloadButton() {
        return downloadButton;
    }

    public ImageView getShareButton() {
        return shareButton;
    }

    public ImageView getPlaybackSpeedButton() {
        return playbackSpeedButton;
    }

    public ImageView getRotateLeftButton() {
        return rotateLeftButton;
    }

    public ImageView getRotateRightButton() {
        return rotateRightButton;
    }

    public LockableNestedScrollView getNestedScrollView() {
        return binding == null ? Objects.requireNonNull(zoomableBinding).lockableNestedScrollViewViewVideoActivity : binding.lockableNestedScrollViewViewVideoActivity;
    }
}
