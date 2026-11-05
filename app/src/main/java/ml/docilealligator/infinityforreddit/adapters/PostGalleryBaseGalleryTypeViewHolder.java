package ml.docilealligator.infinityforreddit.adapters;

import android.content.res.ColorStateList;
import android.graphics.PorterDuff;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;
import android.widget.ImageView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.PagerSnapHelper;
import androidx.recyclerview.widget.RecyclerView;
import com.libRG.CustomTextView;
import ml.docilealligator.infinityforreddit.R;
import ml.docilealligator.infinityforreddit.activities.BaseActivity;
import ml.docilealligator.infinityforreddit.customviews.LinearLayoutManagerBugFixed;
import ml.docilealligator.infinityforreddit.post.FetchImageHostMedia;
import ml.docilealligator.infinityforreddit.post.MarkPostAsReadInterface;
import ml.docilealligator.infinityforreddit.post.Post;
import ml.docilealligator.infinityforreddit.utils.SharedPreferencesUtils;
import ml.docilealligator.infinityforreddit.videoautoplay.PlayerSelector;
import ml.docilealligator.infinityforreddit.videoautoplay.ToroPlayer;
import ml.docilealligator.infinityforreddit.videoautoplay.media.PlaybackInfo;
import ml.docilealligator.infinityforreddit.videoautoplay.widget.Container;

// Fields such as post are assigned at bind time, not by the constructor.
@SuppressWarnings("NullAway.Init")
class PostGalleryBaseGalleryTypeViewHolder extends RecyclerView.ViewHolder implements ToroPlayer {

    FrameLayout frameLayout;
    RecyclerView recyclerView;
    CustomTextView imageIndexTextView;
    ImageView noPreviewImageView;
    /** The album scrape behind an image-host gallery, so a recycled row stops paying for it. */
    @Nullable
    FetchImageHostMedia.Cancellable fetchAlbumCancellable;

    PostGalleryTypeImageRecyclerViewAdapter adapter;
    GalleryGifAutoplay toroPlayer;
    private final LinearLayoutManagerBugFixed layoutManager;

    Post post;
    @Nullable
    Post.Preview preview;

    private final PostGalleryHost host;
    private final BaseActivity mActivity;

    public PostGalleryBaseGalleryTypeViewHolder(@NonNull View itemView,
                                                FrameLayout frameLayout,
                                                RecyclerView recyclerView,
                                                CustomTextView imageIndexTextView,
                                                ImageView noPreviewImageView,
                                                PostGalleryHost host) {
        super(itemView);
        this.host = host;
        this.mActivity = host.activity();

        this.frameLayout = frameLayout;
        this.recyclerView = recyclerView;
        this.imageIndexTextView = imageIndexTextView;
        this.noPreviewImageView = noPreviewImageView;

        if (mActivity.typeface != null) {
            imageIndexTextView.setTypeface(mActivity.typeface);
        }

        itemView.setBackgroundTintList(ColorStateList.valueOf(host.cardViewBackgroundColor()));
        noPreviewImageView.setBackgroundColor(host.noPreviewPostTypeBackgroundColor());
        noPreviewImageView.setColorFilter(host.noPreviewPostTypeIconTint(), android.graphics.PorterDuff.Mode.SRC_IN);

        imageIndexTextView.setTextColor(host.mediaIndicatorIconTint());
        imageIndexTextView.setBackgroundColor(host.mediaIndicatorBackgroundColor());
        imageIndexTextView.setBorderColor(host.mediaIndicatorBackgroundColor());
        if (host.hideImageCountInGallery()) {
            imageIndexTextView.setVisibility(View.GONE);
        }

        adapter = new PostGalleryTypeImageRecyclerViewAdapter(host.glide(), mActivity.typeface,
                host.saveMemoryDownsampleStrategy(), host.colorAccent(), host.primaryTextColor());
        toroPlayer = new GalleryGifAutoplay(itemView, recyclerView, adapter) {
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
        recyclerView.setAdapter(adapter);
        new PagerSnapHelper().attachToRecyclerView(recyclerView);
        recyclerView.setRecycledViewPool(host.galleryRecycledViewPool());
        // Keep neighbouring gallery pages attached so swiping one over and back doesn't recycle
        // (and glide.clear()) the previous image, which forces a reload. Default is 2.
        recyclerView.setItemViewCacheSize(3);
        layoutManager = new LinearLayoutManagerBugFixed(mActivity, RecyclerView.HORIZONTAL, false);
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    toroPlayer.onGalleryPageSettled(layoutManager.findFirstVisibleItemPosition());
                }
            }

            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                imageIndexTextView.setText(mActivity.getString(R.string.image_index_in_gallery, layoutManager.findFirstVisibleItemPosition() + 1, post.getGallery().size()));
            }
        });
        recyclerView.addOnItemTouchListener(new RecyclerView.OnItemTouchListener() {
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

                        if (mActivity.mSliderPanel != null) {
                            mActivity.mSliderPanel.requestDisallowInterceptTouchEvent(true);
                        }
                        if (mActivity.mViewPager2 != null) {
                            mActivity.mViewPager2.setUserInputEnabled(false);
                        }
                        mActivity.lockSwipeRightToGoBack();
                        break;
                    case MotionEvent.ACTION_MOVE:
                        if (Math.abs(e.getRawX() - downX) > minTouchSlop || Math.abs(e.getRawY() - downY) > minTouchSlop) {
                            dragged = true;
                        }
                        if (!dragged && !longPressed) {
                            if (System.currentTimeMillis() - downTime >= longClickThreshold) {
                                onLongClick();
                                longPressed = true;
                            }
                        }

                        if (mActivity.mSliderPanel != null) {
                            mActivity.mSliderPanel.requestDisallowInterceptTouchEvent(true);
                        }
                        if (mActivity.mViewPager2 != null) {
                            mActivity.mViewPager2.setUserInputEnabled(false);
                        }
                        mActivity.lockSwipeRightToGoBack();
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        if (e.getActionMasked() == MotionEvent.ACTION_UP && !dragged) {
                            if (System.currentTimeMillis() - downTime < longClickThreshold) {
                                onClick();
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
            onClick();
        });

        noPreviewImageView.setOnLongClickListener(view -> onLongClick());
    }

    void onClick() {
        int position = getBindingAdapterPosition();
        if (position >= 0 && host.canStartActivity()) {
            Post post = host.getItem(position);
            if (post != null) {
                markPostRead(post, true);

                if (post.getPostType() == Post.TEXT_TYPE || !host.sharedPreferences().getBoolean(SharedPreferencesUtils.CLICK_TO_SHOW_MEDIA_IN_GALLERY_LAYOUT, true)) {
                    host.openViewPostDetailActivity(post, getBindingAdapterPosition());
                } else {
                    host.openMedia(post, layoutManager.findFirstVisibleItemPosition(), false);
                }
            }
        }
    }

    boolean onLongClick() {
        int position = getBindingAdapterPosition();
        if (position >= 0 && host.canStartActivity()) {
            Post post = host.getItem(position);
            if (post != null) {
                markPostRead(post, true);

                if (post.getPostType() == Post.TEXT_TYPE || host.sharedPreferences().getBoolean(SharedPreferencesUtils.CLICK_TO_SHOW_MEDIA_IN_GALLERY_LAYOUT, true)) {
                    host.openViewPostDetailActivity(post, getBindingAdapterPosition());
                } else {
                    host.openMedia(post, layoutManager.findFirstVisibleItemPosition(), false);
                }
            }
        }

        return true;
    }

    void markPostRead(Post post, boolean changePostItemColor) {
        if (!host.handleReadPost()) {
            return;
        }

        if (!post.isRead() && host.markPostsAsRead()) {
            post.markAsRead();
            if (changePostItemColor) {
                itemView.setBackgroundTintList(ColorStateList.valueOf(host.readPostCardViewBackgroundColor()));
            }
            if (mActivity != null && mActivity instanceof MarkPostAsReadInterface) {
                ((MarkPostAsReadInterface) mActivity).markPostAsRead(post);
            }
        }
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
