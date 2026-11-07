package ml.docilealligator.infinityforreddit.adapters;

import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.DefaultTimeBar;
import androidx.media3.ui.PlayerView;
import com.google.android.material.button.MaterialButton;
import com.libRG.CustomTextView;
import ml.docilealligator.infinityforreddit.R;
import ml.docilealligator.infinityforreddit.activities.BaseActivity;
import ml.docilealligator.infinityforreddit.customviews.AspectRatioGifImageView;
import ml.docilealligator.infinityforreddit.customviews.MaxHeightSquareFrameLayout;
import ml.docilealligator.infinityforreddit.post.Post;
import ml.docilealligator.infinityforreddit.videoautoplay.ToroPlayer;
import ml.docilealligator.infinityforreddit.videoautoplay.media.PlaybackInfo;
import ml.docilealligator.infinityforreddit.videoautoplay.widget.Container;
import pl.droidsonroids.gif.GifImageView;

@UnstableApi
abstract class PostBaseVideoAutoplayViewHolder extends PostBaseViewHolder implements VideoAutoplayRow {
    VideoAutoplayImpl toroPlayer;
    final MaxHeightSquareFrameLayout previewFrameLayout;
    final AspectRatioFrameLayout aspectRatioFrameLayout;
    private final BaseActivity mActivity;

    @OptIn(markerClass = UnstableApi.class)
    PostBaseVideoAutoplayViewHolder(View rootView,
                                    PostVideoHost host,
                                    AspectRatioGifImageView iconGifImageView,
                                    TextView subredditTextView,
                                    TextView userTextView,
                                    ImageView stickiedPostImageView,
                                    TextView postTimeTextView,
                                    TextView titleTextView,
                                    @Nullable CustomTextView typeTextView,
                                    @Nullable ImageView crosspostImageView,
                                    @Nullable ImageView archivedImageView,
                                    @Nullable ImageView lockedImageView,
                                    @Nullable CustomTextView nsfwTextView,
                                    @Nullable CustomTextView spoilerTextView,
                                    @Nullable CustomTextView flairTextView,
                                    MaxHeightSquareFrameLayout previewFrameLayout,
                                    AspectRatioFrameLayout aspectRatioFrameLayout,
                                    GifImageView previewImageView,
                                    ImageView errorLoadingRedgifsImageView,
                                    PlayerView videoPlayer,
                                    ImageView videoQualityButton,
                                    ImageView muteButton,
                                    ImageView fullscreenButton,
                                    ImageView playPauseButton,
                                    DefaultTimeBar progressBar,
                                    ConstraintLayout bottomConstraintLayout,
                                    MaterialButton upvoteButton,
                                    TextView scoreTextView,
                                    MaterialButton downvoteButton,
                                    MaterialButton commentsCountButton,
                                    MaterialButton saveButton,
                                    MaterialButton shareButton) {
        super(rootView, host);
        this.mActivity = host.activity();
        this.previewFrameLayout = previewFrameLayout;
        this.aspectRatioFrameLayout = aspectRatioFrameLayout;
        aspectRatioFrameLayout.setOnClickListener(null);
        setBaseView(
                iconGifImageView,
                subredditTextView,
                userTextView,
                stickiedPostImageView,
                postTimeTextView,
                titleTextView,
                typeTextView,
                archivedImageView,
                lockedImageView,
                crosspostImageView,
                nsfwTextView,
                spoilerTextView,
                flairTextView,
                bottomConstraintLayout,
                upvoteButton,
                scoreTextView,
                downvoteButton,
                commentsCountButton,
                saveButton,
                shareButton);

        toroPlayer = new VideoAutoplayImpl(rootView, previewImageView, errorLoadingRedgifsImageView, videoPlayer,
                new VideoAutoplayImpl.Controls(videoQualityButton, muteButton, fullscreenButton, playPauseButton,
                        progressBar,
                        AppCompatResources.getDrawable(mActivity, R.drawable.ic_play_arrow_24dp),
                        AppCompatResources.getDrawable(mActivity, R.drawable.ic_pause_24dp)),
                host) {
            @Override
            public int getPlayerOrder() {
                return getBindingAdapterPosition();
            }

            @Override
            int getAdapterPosition() {
                return getBindingAdapterPosition();
            }

            @Override
            Post getPost() {
                return post;
            }

            @Override
            void markPostRead(Post post, boolean changePostItemColor) {
                PostBaseVideoAutoplayViewHolder.this.markPostRead(post, changePostItemColor);
            }
        };
    }

    @Override
    public VideoAutoplayImpl toroPlayer() {
        return toroPlayer;
    }
}
