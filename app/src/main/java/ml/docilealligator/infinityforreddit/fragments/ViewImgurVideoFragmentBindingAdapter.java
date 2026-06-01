package ml.docilealligator.infinityforreddit.fragments;

import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.media3.ui.PlayerView;

import com.google.android.material.bottomappbar.BottomAppBar;
import com.google.android.material.button.MaterialButton;

import ml.docilealligator.infinityforreddit.R;
import ml.docilealligator.infinityforreddit.databinding.FragmentViewImgurVideoBinding;

class ViewImgurVideoFragmentBindingAdapter {
    private FragmentViewImgurVideoBinding binding;
    private LinearLayout controller;
    private MaterialButton muteButton;
    private BottomAppBar bottomAppBar;
    private TextView titleTextView;
    private MaterialButton backButton;
    private MaterialButton downloadButton;
    private MaterialButton playbackSpeedButton;
    private MaterialButton rotateLeftButton;
    private MaterialButton rotateRightButton;

    ViewImgurVideoFragmentBindingAdapter(FragmentViewImgurVideoBinding binding) {
        this.binding = binding;
        controller = binding.getRoot().findViewById(R.id.linear_layout_exo_playback_control_view);
        muteButton = binding.getRoot().findViewById(R.id.mute_exo_playback_control_view);
        bottomAppBar = binding.getRoot().findViewById(R.id.bottom_navigation_exo_playback_control_view);
        titleTextView = binding.getRoot().findViewById(R.id.title_text_view_exo_playback_control_view);
        backButton = binding.getRoot().findViewById(R.id.back_button_exo_playback_control_view);
        downloadButton = binding.getRoot().findViewById(R.id.download_image_view_exo_playback_control_view);
        playbackSpeedButton = binding.getRoot().findViewById(R.id.playback_speed_image_view_exo_playback_control_view);
        rotateLeftButton = binding.getRoot().findViewById(R.id.rotate_left_image_view_exo_playback_control_view);
        rotateRightButton = binding.getRoot().findViewById(R.id.rotate_right_image_view_exo_playback_control_view);
    }

    PlayerView getRoot() {
        return binding.getRoot();
    }

    LinearLayout getController() {
        return controller;
    }

    MaterialButton getMuteButton() {
        return muteButton;
    }

    BottomAppBar getBottomAppBar() {
        return bottomAppBar;
    }

    TextView getTitleTextView() {
        return titleTextView;
    }

    MaterialButton getBackButton() {
        return backButton;
    }

    MaterialButton getDownloadButton() {
        return downloadButton;
    }

    MaterialButton getPlaybackSpeedButton() {
        return playbackSpeedButton;
    }

    MaterialButton getRotateLeftButton() {
        return rotateLeftButton;
    }

    MaterialButton getRotateRightButton() {
        return rotateRightButton;
    }
}
