package ml.docilealligator.infinityforreddit.adapters;

import android.graphics.PorterDuff;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.recyclerview.widget.PagerSnapHelper;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.button.MaterialButton;
import com.libRG.CustomTextView;
import ml.docilealligator.infinityforreddit.PostGalleryGridLayoutItemDecoration;
import ml.docilealligator.infinityforreddit.R;
import ml.docilealligator.infinityforreddit.activities.BaseActivity;
import ml.docilealligator.infinityforreddit.customviews.AspectRatioGifImageView;
import ml.docilealligator.infinityforreddit.customviews.LinearLayoutManagerBugFixed;
import ml.docilealligator.infinityforreddit.post.FetchImageHostMedia;
import ml.docilealligator.infinityforreddit.utils.SharedPreferencesUtils;
import ml.docilealligator.infinityforreddit.videoautoplay.PlayerSelector;
import ml.docilealligator.infinityforreddit.videoautoplay.ToroPlayer;
import ml.docilealligator.infinityforreddit.videoautoplay.media.PlaybackInfo;
import ml.docilealligator.infinityforreddit.videoautoplay.widget.Container;

// Fields such as the views are assigned by setBaseView, not by the constructor itself.
@SuppressWarnings("NullAway.Init")
public abstract class PostBaseGalleryTypeViewHolder extends PostBaseViewHolder implements ToroPlayer {
    /** The album scrape behind an image-host gallery, so a recycled row stops paying for it. */
    @Nullable
    FetchImageHostMedia.Cancellable fetchAlbumCancellable;
    /**
     * Whether the user has moved this carousel themselves. A rebind must not drag one out from
     * under a finger, but a carousel that is merely settling after a programmatic scroll or a
     * layout is not under anyone's finger -- it is just not where it has been told to be yet.
     */
    boolean galleryTouchedByUser;

    FrameLayout frameLayout;
    RecyclerView galleryRecyclerView;
    CustomTextView imageIndexTextView;
    ImageView noPreviewImageView;

    private final PostGalleryHost host;
    private final BaseActivity mActivity;

    PostGalleryTypeImageRecyclerViewAdapter adapter;
    GalleryGifAutoplay toroPlayer;
    private boolean swipeLocked;

    PostBaseGalleryTypeViewHolder(View rootView,
                                AspectRatioGifImageView iconGifImageView,
                                TextView subredditTextView,
                                TextView userTextView,
                                ImageView stickiedPostImageView,
                                TextView postTimeTextView,
                                TextView titleTextView,
                                @Nullable CustomTextView typeTextView,
                                @Nullable ImageView archivedImageView,
                                @Nullable ImageView lockedImageView,
                                @Nullable ImageView crosspostImageView,
                                @Nullable CustomTextView nsfwTextView,
                                @Nullable CustomTextView spoilerTextView,
                                @Nullable CustomTextView flairTextView,
                                FrameLayout frameLayout,
                                RecyclerView galleryRecyclerView,
                                CustomTextView imageIndexTextView,
                                ImageView noPreviewImageView,
                                ConstraintLayout bottomConstraintLayout,
                                MaterialButton upvoteButton,
                                TextView scoreTextView,
                                MaterialButton downvoteButton,
                                MaterialButton commentsCountButton,
                                MaterialButton saveButton,
                                MaterialButton shareButton,
                                PostGalleryHost host) {
        super(rootView, host);
        this.host = host;
        this.mActivity = host.activity();
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

        this.frameLayout = frameLayout;
        this.galleryRecyclerView = galleryRecyclerView;
        this.imageIndexTextView = imageIndexTextView;
        this.noPreviewImageView = noPreviewImageView;

        imageIndexTextView.setTextColor(host.mediaIndicatorIconTint());
        imageIndexTextView.setBackgroundColor(host.mediaIndicatorBackgroundColor());
        imageIndexTextView.setBorderColor(host.mediaIndicatorBackgroundColor());
        if (host.hideImageCountInGallery()) {
            imageIndexTextView.setVisibility(View.GONE);
        }
        if (mActivity.typeface != null) {
            imageIndexTextView.setTypeface(mActivity.typeface);
        }

        noPreviewImageView.setBackgroundColor(host.noPreviewPostTypeBackgroundColor());
        noPreviewImageView.setColorFilter(host.noPreviewPostTypeIconTint(), android.graphics.PorterDuff.Mode.SRC_IN);

        adapter = new PostGalleryTypeImageRecyclerViewAdapter(host.glide(), mActivity.typeface,
                host.saveMemoryDownsampleStrategy(), host.colorAccent(), host.primaryTextColor());
        toroPlayer = new GalleryGifAutoplay(rootView, galleryRecyclerView, adapter) {
            @Override
            protected boolean canPlay() {
                return host.canPlayVideo();
            }

            @Override
            protected double visibleAreaThreshold() {
                return host.startAutoplayVisibleAreaOffset();
            }

            @Override
            @Nullable
            protected PlayerSelector playerSelector() {
                return host.playerSelector();
            }

            @Override
            public int getPlayerOrder() {
                return getBindingAdapterPosition();
            }
        };
        galleryRecyclerView.setAdapter(adapter);
        new PagerSnapHelper().attachToRecyclerView(galleryRecyclerView);
        galleryRecyclerView.setRecycledViewPool(host.galleryRecycledViewPool());
        // Keep neighbouring gallery pages attached so swiping one over and back doesn't recycle
        // (and glide.clear()) the previous image, which forces a reload. Default is 2.
        galleryRecyclerView.setItemViewCacheSize(3);
        LinearLayoutManagerBugFixed layoutManager = new LinearLayoutManagerBugFixed(mActivity, RecyclerView.HORIZONTAL, false);
        galleryRecyclerView.setLayoutManager(layoutManager);
        PostGalleryGridLayoutItemDecoration itemDecoration =
                new PostGalleryGridLayoutItemDecoration(mActivity, R.dimen.galleryGridItemOffset, 2);
        galleryRecyclerView.addItemDecoration(itemDecoration);

        galleryRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                if (newState != RecyclerView.SCROLL_STATE_IDLE) {
                    return;
                }
                RecyclerView.LayoutManager layoutManager = recyclerView.getLayoutManager();
                if (layoutManager instanceof LinearLayoutManagerBugFixed) {
                    int settled = ((LinearLayoutManagerBugFixed) layoutManager)
                            .findFirstVisibleItemPosition();
                    toroPlayer.onGalleryPageSettled(settled);
                    // Written back to the post, which is what survives this holder being
                    // recycled and what the feed cache records for the next launch. Not while
                    // the card is drawing the single placeholder tile an unread image-host
                    // album is seeded with: a one-tile carousel settles on tile zero the moment
                    // it is laid out, which is not the user moving it and must not overwrite
                    // the image they had swiped to.
                    if (settled != RecyclerView.NO_POSITION && post != null
                            && !(post.isImageHostAlbum() && !post.isImageHostGalleryResolved())) {
                        post.setGalleryPageIndex(settled);
                    }
                }
            }

            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                RecyclerView.LayoutManager layoutManager = recyclerView.getLayoutManager();
                if (layoutManager instanceof LinearLayoutManagerBugFixed) {
                    imageIndexTextView.setText(mActivity.getString(R.string.image_index_in_gallery,
                            ((LinearLayoutManagerBugFixed) layoutManager).findFirstVisibleItemPosition() + 1, post.getGallery().size()));
                }
            }
        });
        galleryRecyclerView.addOnItemTouchListener(new RecyclerView.OnItemTouchListener() {
            private float downX;
            private float downY;
            private boolean dragged;
            private long downTime;
            private final int minTouchSlop = ViewConfiguration.get(mActivity).getScaledTouchSlop();
            private final int longClickThreshold = ViewConfiguration.getLongPressTimeout();
            private boolean longPressed;

            @Override
            public boolean onInterceptTouchEvent(@NonNull RecyclerView rv, @NonNull MotionEvent e) {
                int action = e.getAction();
                switch (action) {
                    case MotionEvent.ACTION_DOWN:
                        downX = e.getRawX();
                        downY = e.getRawY();
                        downTime = System.currentTimeMillis();
                        galleryTouchedByUser = true;

                        if (mActivity.mSliderPanel != null) {
                            mActivity.mSliderPanel.requestDisallowInterceptTouchEvent(true);
                        }
                        if (mActivity.mViewPager2 != null) {
                            mActivity.mViewPager2.setUserInputEnabled(false);
                        }
                        mActivity.lockSwipeRightToGoBack();
                        swipeLocked = true;
                        break;
                    case MotionEvent.ACTION_MOVE:
                        if (Math.abs(e.getRawX() - downX) > minTouchSlop || Math.abs(e.getRawY() - downY) > minTouchSlop) {
                            dragged = true;
                        }

                        if (!dragged && !longPressed) {
                            if (System.currentTimeMillis() - downTime >= longClickThreshold) {
                                View itemView = galleryRecyclerView.findChildViewUnder(e.getX(), e.getY());
                                int currentItemPosition = -1;
                                if (itemView != null) {
                                    currentItemPosition = galleryRecyclerView.getChildAdapterPosition(itemView);
                                }
                                if (host.longPressPostMediaAction().equals(SharedPreferencesUtils.LONG_PRESS_POST_VALUE_SHOW_POST_OPTIONS)) {
                                    galleryRecyclerView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                                    showPostOptions(currentItemPosition);
                                    longPressed = true;
                                } else if (host.longPressPostMediaAction().equals(SharedPreferencesUtils.LONG_PRESS_POST_VALUE_PREVIEW_IN_FULLSCREEN)) {
                                    galleryRecyclerView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                                    if (currentItemPosition >= 0) {
                                        markPostRead(post, true);
                                        host.openMedia(post, currentItemPosition, true);
                                    } else {
                                        showPostOptions(currentItemPosition);
                                    }
                                    longPressed = true;
                                }
                            }
                        }

                        if (mActivity.mSliderPanel != null) {
                            mActivity.mSliderPanel.requestDisallowInterceptTouchEvent(true);
                        }
                        if (mActivity.mViewPager2 != null) {
                            mActivity.mViewPager2.setUserInputEnabled(false);
                        }
                        mActivity.lockSwipeRightToGoBack();
                        swipeLocked = true;
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        if (e.getActionMasked() == MotionEvent.ACTION_UP && !dragged) {
                            if (System.currentTimeMillis() - downTime < longClickThreshold) {
                                int position = getBindingAdapterPosition();
                                if (position >= 0) {
                                    if (post != null) {
                                        markPostRead(post, true);
                                        View itemView = galleryRecyclerView.findChildViewUnder(e.getX(), e.getY());
                                        int currentItemPosition = -1;
                                        if (itemView != null) {
                                            currentItemPosition = galleryRecyclerView.getChildAdapterPosition(itemView);
                                        }
                                        if (currentItemPosition >= 0) {
                                            host.openMedia(post, currentItemPosition, false);
                                        } else {
                                            host.openViewPostDetailActivity(post, getBindingAdapterPosition());
                                        }
                                    }
                                }
                            }
                        }

                        downX = 0;
                        downY = 0;
                        dragged = false;
                        longPressed = false;

                        if (mActivity.mSliderPanel != null) {
                            mActivity.mSliderPanel.requestDisallowInterceptTouchEvent(false);
                        }

                        if (mActivity.mViewPager2 != null) {
                            mActivity.mViewPager2.setUserInputEnabled(!host.swipingBetweenTabsDisabled());
                        }
                        mActivity.unlockSwipeRightToGoBack();
                        swipeLocked = false;
                }
                return false;
            }

            @Override
            public void onTouchEvent(@NonNull RecyclerView rv, @NonNull MotionEvent e) {

            }

            @Override
            public void onRequestDisallowInterceptTouchEvent(boolean disallowIntercept) {

            }
        });

        noPreviewImageView.setOnClickListener(view -> {
            int position = getBindingAdapterPosition();
            if (position < 0) {
                return;
            }
            if (post != null) {
                markPostRead(post, true);
                host.openMedia(post, 0);
            }
        });

        noPreviewImageView.setOnLongClickListener(view -> {
            if (host.longPressPostMediaAction().equals(SharedPreferencesUtils.LONG_PRESS_POST_VALUE_SHOW_POST_OPTIONS)) {
                showPostOptions(-1);
                return true;
            } else if (host.longPressPostMediaAction().equals(SharedPreferencesUtils.LONG_PRESS_POST_VALUE_PREVIEW_IN_FULLSCREEN)) {
                markPostRead(post, true);
                host.openMedia(post, true);
                return true;
            }
            return false;
        });
    }

    public boolean isSwipeLocked() {
        return swipeLocked;
    }

    @NonNull
    @Override
    public View getPlayerView() {
        return toroPlayer.getPlayerView();
    }

    @NonNull
    @Override
    public PlaybackInfo getCurrentPlaybackInfo() {
        return toroPlayer.getCurrentPlaybackInfo();
    }

    @Override
    public void initialize(@NonNull Container container, @NonNull PlaybackInfo playbackInfo) {
        toroPlayer.initialize(container, playbackInfo);
    }

    @Override
    public void play() {
        toroPlayer.play();
    }

    @Override
    public void pause() {
        toroPlayer.pause();
    }

    @Override
    public boolean isPlaying() {
        return toroPlayer.isPlaying();
    }

    @Override
    public void release() {
        toroPlayer.release();
    }

    @Override
    public boolean wantsToPlay() {
        return toroPlayer.wantsToPlay();
    }

    @Override
    public int getPlayerOrder() {
        return toroPlayer.getPlayerOrder();
    }
}
