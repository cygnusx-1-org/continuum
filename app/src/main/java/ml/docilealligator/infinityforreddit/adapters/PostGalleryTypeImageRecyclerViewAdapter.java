package ml.docilealligator.infinityforreddit.adapters;

import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.ImageView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.RequestBuilder;
import com.bumptech.glide.RequestManager;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.MultiTransformation;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.load.resource.bitmap.CenterCrop;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.Target;
import io.noties.markwon.Markwon;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Objects;
import java.util.function.Consumer;
import jp.wasabeef.glide.transformations.BlurTransformation;
import jp.wasabeef.glide.transformations.RoundedCornersTransformation;
import ml.docilealligator.infinityforreddit.SaveMemoryCenterInisdeDownsampleStrategy;
import ml.docilealligator.infinityforreddit.databinding.ItemGalleryImageInPostFeedBinding;
import ml.docilealligator.infinityforreddit.post.Post;

@SuppressWarnings("NullAway.Init")
public class PostGalleryTypeImageRecyclerViewAdapter extends RecyclerView.Adapter<PostGalleryTypeImageRecyclerViewAdapter.ImageViewHolder> {
    private final RequestManager glide;
    @Nullable
    private final Typeface typeface;
    private Markwon mPostDetailMarkwon;
    private final SaveMemoryCenterInisdeDownsampleStrategy saveMemoryCenterInisdeDownsampleStrategy;
    private final int mColorAccent;
    private final int mPrimaryTextColor;
    private int mCardViewColor;
    private int mCommentColor;
    private ArrayList<Post.Gallery> galleryImages;
    private boolean blurImage;
    private float ratio;
    private int maxPreviewHeight;
    private final boolean showCaption;
    private boolean isGridLayout;
    // Whether this post is eligible to animate its gifs at all: Video Autoplay is on (and, on the
    // "On Wi-Fi" setting, we are on Wi-Fi), and the post is not one autoplay skips (NSFW with
    // "Autoplay NSFW videos" off, or a spoiler). Set by the host adapter on every bind.
    private boolean autoplayGif;
    // Whether the host RecyclerView's autoplay coordinator has currently selected this post to
    // play, honouring Settings -> Video -> "Simultaneous autoplay limit" across the whole feed.
    private boolean playing;
    // The gallery page the pager is settled on. Only that tile animates -- the neighbouring pages
    // RecyclerView keeps bound for a smooth swipe are off screen, and animating them would spend
    // the autoplay budget on frames nobody sees.
    private int currentPosition;
    @Nullable
    private RecyclerView attachedRecyclerView;
    // The box, in pixels, every tile of the row layout decodes to, when the host knows it before
    // layout -- the feed's cards do, from the column width. 0 otherwise (the post detail, a feed not
    // laid out yet), and a tile is then sized from its own view once that has been laid out.
    private int tileWidthPx;
    private int tileHeightPx;
    private boolean dataSavingMode;
    // Swipe neighbours being warmed, by page. Kept so a change of pictures can cancel them; one that
    // has finished has already handed its bitmap to the memory cache.
    private final HashMap<Integer, Target<Drawable>> tilePreloads = new HashMap<>();

    public PostGalleryTypeImageRecyclerViewAdapter(RequestManager glide, @Nullable Typeface typeface,
                                                   SaveMemoryCenterInisdeDownsampleStrategy saveMemoryCenterInisdeDownsampleStrategy,
                                                   int mColorAccent, int mPrimaryTextColor) {
        this.glide = glide;
        this.typeface = typeface;
        this.saveMemoryCenterInisdeDownsampleStrategy = saveMemoryCenterInisdeDownsampleStrategy;
        this.mColorAccent = mColorAccent;
        this.mPrimaryTextColor = mPrimaryTextColor;
        showCaption = false;
    }

    public PostGalleryTypeImageRecyclerViewAdapter(RequestManager glide, @Nullable Typeface typeface, Markwon postDetailMarkwon,
                                                   SaveMemoryCenterInisdeDownsampleStrategy saveMemoryCenterInisdeDownsampleStrategy,
                                                   int mColorAccent, int mPrimaryTextColor, int mCardViewColor,
                                                   int mCommentColor) {
        this.glide = glide;
        this.typeface = typeface;
        this.mPostDetailMarkwon = postDetailMarkwon;
        this.saveMemoryCenterInisdeDownsampleStrategy = saveMemoryCenterInisdeDownsampleStrategy;
        this.mColorAccent = mColorAccent;
        this.mPrimaryTextColor = mPrimaryTextColor;
        this.mCardViewColor = mCardViewColor;
        this.mCommentColor = mCommentColor;
        showCaption = true;
    }

    @NonNull
    @Override
    public ImageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ImageViewHolder(ItemGalleryImageInPostFeedBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ImageViewHolder holder, int position) {
        if (isGridLayout) {
            // Grid tiles are square by construction, so the post's preview shape does not apply.
            holder.binding.imageViewItemGalleryImageInPostFeed.setScaleType(ImageView.ScaleType.CENTER_CROP);
            holder.binding.imageViewItemGalleryImageInPostFeed.setRatioMaxHeight(0);
            holder.binding.imageViewItemGalleryImageInPostFeed.setRatio(1);
        } else {
            // Every tile in a gallery is given the same shape, taken from the post's first preview,
            // so the other images in it need not match. Only the square preview is a shape none of
            // them was measured against, and it is the one that has to crop to fill; sizing from
            // the post's own ratio letterboxes instead, which is what this has always done.
            // maxPreviewHeight is set alongside the square ratio and left at 0 otherwise, so it
            // distinguishes the two.
            holder.binding.imageViewItemGalleryImageInPostFeed.setScaleType(
                    maxPreviewHeight > 0 ? ImageView.ScaleType.CENTER_CROP : ImageView.ScaleType.FIT_CENTER);
            holder.binding.imageViewItemGalleryImageInPostFeed.setRatioMaxHeight(maxPreviewHeight);
            holder.binding.imageViewItemGalleryImageInPostFeed.setRatio(ratio);
        }
        holder.binding.errorTextViewItemGalleryImageInPostFeed.setVisibility(View.GONE);
        holder.binding.progressBarItemGalleryImageInPostFeed.setVisibility(View.VISIBLE);

        ImageView imageView = holder.binding.imageViewItemGalleryImageInPostFeed;

        // Drop any listener left over from a previous bind of this recycled holder.
        if (holder.pendingLayoutListener != null) {
            imageView.removeOnLayoutChangeListener(holder.pendingLayoutListener);
            holder.pendingLayoutListener = null;
        }

        if (hasTileSize()) {
            // The box is known without the view, so the decode cannot pick up a size the view is
            // about to lose, and a picture already warmed in memory is in place within this bind.
            loadImage(holder);
        } else if (hasUsableWidth(imageView) && !imageView.isLayoutRequested()) {
            // The recycled view is already laid out at its final size, so no layout change will
            // fire. Load now — otherwise loadImage() would never run and the spinner spins forever.
            //
            // A pending layout means the size it has is not the one it is about to get: setRatio()
            // above re-shapes the tile for this post, and the bounds still on it are the previous
            // post's. Glide takes the view's current size as the decode box, so loading now would
            // fit this image into the old tile -- a 2:1 tile left behind under a portrait
            // screenshot decoded it at a third of the card's width or less, stretched across the
            // card. Wait for the layout instead; the listener below runs once it has happened.
            loadImage(holder);
        } else {
            holder.pendingLayoutListener = new View.OnLayoutChangeListener() {
                @Override
                public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
                    // In split/weighted layouts, views get intermediate layout passes with
                    // incorrect (tiny) dimensions. Skip those and wait for the real size.
                    if (!isSettledWidth(v, right - left)) {
                        return;
                    }
                    v.removeOnLayoutChangeListener(this);
                    holder.pendingLayoutListener = null;
                    loadImage(holder);
                }
            };
            imageView.addOnLayoutChangeListener(holder.pendingLayoutListener);
        }

        warmNextTile(holder.getBindingAdapterPosition());

        if (showCaption) {
            loadCaptionPreview(holder);
        }
    }

    @Override
    public int getItemCount() {
        return galleryImages == null ? 0 : galleryImages.size();
    }

    @Override
    public void onAttachedToRecyclerView(@NonNull RecyclerView recyclerView) {
        super.onAttachedToRecyclerView(recyclerView);
        attachedRecyclerView = recyclerView;
    }

    @Override
    public void onDetachedFromRecyclerView(@NonNull RecyclerView recyclerView) {
        super.onDetachedFromRecyclerView(recyclerView);
        attachedRecyclerView = null;
    }

    @Override
    public void onViewRecycled(@NonNull ImageViewHolder holder) {
        super.onViewRecycled(holder);
        if (holder.pendingLayoutListener != null) {
            holder.binding.imageViewItemGalleryImageInPostFeed.removeOnLayoutChangeListener(holder.pendingLayoutListener);
            holder.pendingLayoutListener = null;
        }
        holder.binding.captionConstraintLayoutItemGalleryImageInPostFeed.setVisibility(View.GONE);
        holder.binding.captionTextViewItemGalleryImageInPostFeed.setText("");
        holder.binding.captionUrlTextViewItemGalleryImageInPostFeed.setText("");
        holder.binding.progressBarItemGalleryImageInPostFeed.setVisibility(View.GONE);
        holder.binding.errorImageViewItemGalleryImageInPostFeed.setVisibility(View.GONE);
        glide.clear(holder.binding.imageViewItemGalleryImageInPostFeed);
    }

    // Whether the view's width is a real one, by the same rule as the layout listener.
    private boolean hasUsableWidth(View imageView) {
        int viewWidth = imageView.getWidth();
        return viewWidth > 0 && isSettledWidth(imageView, viewWidth);
    }

    /**
     * Whether {@code viewWidth} is the width a tile keeps, rather than an intermediate pass of a
     * split or weighted layout, which can lay a view out at a fraction of its size first.
     *
     * A tile is as wide as one span of the gallery list -- the whole list in the pager, a half or a
     * third of it in the grid -- and less than half of that is a pass to wait out. Measured against
     * the whole list instead, every grid cell looked like one: a cell that missed loading on its
     * first layout never loaded at all, its spinner turning for good.
     */
    private static boolean isSettledWidth(View view, int viewWidth) {
        ViewParent parent = view.getParent();
        while (parent != null && !(parent instanceof RecyclerView)) {
            parent = parent.getParent();
        }
        if (!(parent instanceof RecyclerView) || ((RecyclerView) parent).getWidth() <= 0) {
            return true;
        }
        RecyclerView list = (RecyclerView) parent;
        RecyclerView.LayoutManager layoutManager = list.getLayoutManager();
        int spans = layoutManager instanceof GridLayoutManager
                ? ((GridLayoutManager) layoutManager).getSpanCount() : 1;
        return viewWidth >= list.getWidth() / spans / 2;
    }

    private void loadImage(ImageViewHolder holder) {
        if (galleryImages == null || galleryImages.isEmpty()) {
            return;
        }
        int index = holder.getBindingAdapterPosition();
        if (index < 0 || index >= galleryImages.size()) {
            return;
        }

        Post.Gallery galleryImage = galleryImages.get(index);
        // Prefer a resolution-bounded still, which for a gif is a static frame. The source -- the
        // animated gif itself, often tens of MB -- is loaded only for the one tile that is playing
        // (issue #382), or when there is no still to fall back on. The full-screen media view is
        // unaffected — it loads `url` directly.
        //
        // The still is picked for the tile's width (GalleryTileStill). The grid keeps the widest
        // downscale it has always had: its cells are only measured once laid out.
        int stillWidthPx = hasTileSize() ? tileWidthPx
                : isGridLayout ? 0 : holder.binding.imageViewItemGalleryImageInPostFeed.getWidth();
        String still = GalleryTileStill.url(galleryImage, stillWidthPx,
                saveMemoryCenterInisdeDownsampleStrategy.getThreshold(), dataSavingMode);
        boolean loadSource;
        String loadUrl;
        if (still == null || shouldAnimate(index)) {
            loadSource = true;
            loadUrl = galleryImage.url;
        } else {
            loadSource = false;
            loadUrl = still;
        }

        // A still is worth caching decoded (ALL); an animated gif must not be. Its decoded resource
        // is the animation library's GifDecoder, which has no Glide result encoder, so ALL fails
        // the load outright with NoResultEncoderAvailableException — which also left a gif with no
        // still to fall back on showing the error tile. DATA caches the downloaded bytes instead,
        // which is the expensive half anyway.
        boolean animatedResource = loadSource && galleryImage.mediaType == Post.Gallery.TYPE_GIF;
        RequestListener<Drawable> listener = new RequestListener<>() {
            @Override
            public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
                holder.binding.progressBarItemGalleryImageInPostFeed.setVisibility(View.GONE);
                if (isGridLayout) {
                    holder.binding.errorImageViewItemGalleryImageInPostFeed.setVisibility(View.VISIBLE);
                } else {
                    holder.binding.errorTextViewItemGalleryImageInPostFeed.setVisibility(View.VISIBLE);
                }
                return false;
            }

            @Override
            public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
                holder.binding.errorImageViewItemGalleryImageInPostFeed.setVisibility(View.GONE);
                holder.binding.errorTextViewItemGalleryImageInPostFeed.setVisibility(View.GONE);
                holder.binding.progressBarItemGalleryImageInPostFeed.setVisibility(View.GONE);
                if (resource instanceof Animatable) {
                    // A gif with no usable still to fall back on lands here even when this tile is
                    // not the one playing. Glide starts the animation immediately after this
                    // callback returns, so undo it on the next loop rather than from here.
                    holder.binding.imageViewItemGalleryImageInPostFeed.post(() -> {
                        if (!shouldAnimate(holder.getBindingAdapterPosition())) {
                            stopAnimation(holder);
                        }
                    });
                }
                return false;
            }
        };
        if (!isGridLayout) {
            // Through the one builder the swipe warm-up and the feed's preloader use, at the same
            // box, so the bitmap either of them decoded is the one this finds in memory.
            tileRequest(glide, loadUrl, animatedResource, blurImage, cropsTile(),
                    saveMemoryCenterInisdeDownsampleStrategy, tileWidthPx, tileHeightPx)
                    .listener(listener)
                    .into(holder.binding.imageViewItemGalleryImageInPostFeed);
            return;
        }
        RequestBuilder<Drawable> imageRequestBuilder = glide.load(loadUrl)
                .diskCacheStrategy(animatedResource ? DiskCacheStrategy.DATA : DiskCacheStrategy.ALL)
                .listener(listener);
        if (blurImage) {
            imageRequestBuilder
                    .apply(RequestOptions.bitmapTransform(new MultiTransformation<>(new CenterCrop(), new RoundedCornersTransformation(32, 0), new BlurTransformation(50, 2))))
                    .into(holder.binding.imageViewItemGalleryImageInPostFeed);
        } else {
            // Filling, since the cell is centre-cropped: fitted inside it, the crop would enlarge it.
            imageRequestBuilder
                    .apply(RequestOptions.bitmapTransform(new MultiTransformation<>(new CenterCrop(), new RoundedCornersTransformation(32, 0))))
                    .downsample(saveMemoryCenterInisdeDownsampleStrategy.filling()).into(holder.binding.imageViewItemGalleryImageInPostFeed);
        }
    }

    /**
     * The request a tile of the row (pager) layout draws with -- one builder for the tile's own
     * load, for the next page it warms, and for the feed's preloader warming the page a card opens
     * on. Glide's memory cache is keyed on the url, the size and the transformation, so anything
     * that differs between them is a second decode and the pop-in left in place.
     *
     * @param crop   whether the tile is the square that crops its picture to fill it, rather than
     *               a shape the picture is fitted inside
     * @param width  the tile's box in pixels, or 0 to leave the size to the view
     * @param height the tile's box in pixels, or 0 to leave the size to the view
     */
    public static RequestBuilder<Drawable> tileRequest(RequestManager glide, String url, boolean animated,
                                                       boolean blur, boolean crop,
                                                       SaveMemoryCenterInisdeDownsampleStrategy downsampleStrategy,
                                                       int width, int height) {
        // A still is worth caching decoded (ALL); an animated gif must not be -- see loadImage.
        RequestBuilder<Drawable> builder = glide.load(url)
                .diskCacheStrategy(animated ? DiskCacheStrategy.DATA : DiskCacheStrategy.ALL);
        if (blur) {
            builder = builder.apply(RequestOptions.bitmapTransform(new BlurTransformation(50, 10)));
        } else if (crop) {
            // Decoded to cover the square and cut to it, so the tile's centre crop is 1:1 rather
            // than an enlargement of a picture fitted inside it.
            builder = builder.centerCrop().downsample(downsampleStrategy.filling());
        } else {
            builder = builder.centerInside().downsample(downsampleStrategy);
        }
        // Only when both are known: override(0, 0) is not a size.
        return width > 0 && height > 0 ? builder.override(width, height) : builder;
    }

    /**
     * Warms the page after {@code position} in Glide's memory cache at the tile's box, so a swipe
     * finds it there rather than starting its download as the tile slides in. Only the next page:
     * the one before was on screen a moment ago, and Glide keeps what a recycled tile let go of.
     */
    private void warmNextTile(int position) {
        int next = position + 1;
        if (!hasTileSize() || galleryImages == null || position < 0 || next >= galleryImages.size()
                || tilePreloads.containsKey(next)) {
            return;
        }
        String still = GalleryTileStill.url(galleryImages.get(next), tileWidthPx,
                saveMemoryCenterInisdeDownsampleStrategy.getThreshold(), dataSavingMode);
        if (still == null) {
            // Nothing but the animation, which only the tile autoplay settles on ever loads.
            return;
        }
        tilePreloads.put(next, tileRequest(glide, still, false, blurImage, cropsTile(),
                saveMemoryCenterInisdeDownsampleStrategy, tileWidthPx, tileHeightPx)
                .preload(tileWidthPx, tileHeightPx));
    }

    private void clearTilePreloads() {
        for (Target<Drawable> target : tilePreloads.values()) {
            glide.clear(target);
        }
        tilePreloads.clear();
    }

    // Whether a row-layout tile is the square that crops to fill (CENTER_CROP in onBindViewHolder).
    private boolean cropsTile() {
        return maxPreviewHeight > 0;
    }

    // Whether the host gave this gallery its tile box ahead of layout. The grid never has one.
    private boolean hasTileSize() {
        return !isGridLayout && tileWidthPx > 0 && tileHeightPx > 0;
    }

    /**
     * Whether the tile at {@code index} should be showing a running gif right now: this post is
     * eligible, the autoplay coordinator has picked it, and {@code index} is the page the pager is
     * settled on. Grid layout is excluded — every tile is on screen at once there, so animating
     * them would mean fetching every gif in the gallery at full size.
     */
    private boolean shouldAnimate(int index) {
        return playing && index == currentPosition && canAnimateCurrentTile();
    }

    /**
     * Whether the settled page is a gif this post is allowed to animate. Used by the host
     * ViewHolder to decide whether to ask for one of the autoplay slots at all, so a gallery
     * sitting on a still image never takes a slot from a video.
     */
    public boolean canAnimateCurrentTile() {
        return autoplayGif && !isGridLayout && !blurImage
                && galleryImages != null && currentPosition >= 0 && currentPosition < galleryImages.size()
                && galleryImages.get(currentPosition).mediaType == Post.Gallery.TYPE_GIF;
    }

    public void setAutoplayGif(boolean autoplayGif) {
        this.autoplayGif = autoplayGif;
    }

    /** Called by the host ViewHolder when the autoplay coordinator starts or stops this post. */
    public void setPlaying(boolean playing) {
        if (this.playing == playing) {
            return;
        }
        this.playing = playing;
        if (playing) {
            startCurrentTile();
        } else {
            forEachAttachedHolder(this::stopAnimation);
        }
    }

    /**
     * Called by the host ViewHolder when the pager settles. Returns whether the settled page
     * actually changed, i.e. whether which tile may animate has to be reconsidered.
     */
    public boolean setCurrentPosition(int currentPosition) {
        if (this.currentPosition == currentPosition) {
            return false;
        }
        this.currentPosition = currentPosition;
        if (!playing) {
            return true;
        }
        forEachAttachedHolder(holder -> {
            if (holder.getBindingAdapterPosition() != this.currentPosition) {
                stopAnimation(holder);
            }
        });
        startCurrentTile();
        return true;
    }

    // The settled tile may already hold the animated gif (paused, or scrolled back to), in which
    // case it only needs restarting; otherwise it is showing the still and has to load the source.
    private void startCurrentTile() {
        if (!shouldAnimate(currentPosition)) {
            return;
        }
        ImageViewHolder holder = findAttachedHolder(currentPosition);
        if (holder == null) {
            // Not bound yet — loadImage() consults shouldAnimate() when it is.
            return;
        }
        Drawable drawable = holder.binding.imageViewItemGalleryImageInPostFeed.getDrawable();
        if (drawable instanceof Animatable) {
            ((Animatable) drawable).start();
        } else {
            loadImage(holder);
        }
    }

    private void stopAnimation(ImageViewHolder holder) {
        Drawable drawable = holder.binding.imageViewItemGalleryImageInPostFeed.getDrawable();
        if (drawable instanceof Animatable && ((Animatable) drawable).isRunning()) {
            // Leave the frame it stopped on: it is a frame of the gif itself, so freezing beats
            // reloading the still and flashing a different image in its place.
            ((Animatable) drawable).stop();
        }
    }

    @Nullable
    private ImageViewHolder findAttachedHolder(int position) {
        if (attachedRecyclerView == null) {
            return null;
        }
        RecyclerView.ViewHolder holder = attachedRecyclerView.findViewHolderForAdapterPosition(position);
        return holder instanceof ImageViewHolder ? (ImageViewHolder) holder : null;
    }

    private void forEachAttachedHolder(Consumer<ImageViewHolder> action) {
        if (attachedRecyclerView == null) {
            return;
        }
        for (int i = 0; i < attachedRecyclerView.getChildCount(); i++) {
            RecyclerView.ViewHolder holder =
                    attachedRecyclerView.getChildViewHolder(attachedRecyclerView.getChildAt(i));
            if (holder instanceof ImageViewHolder) {
                action.accept((ImageViewHolder) holder);
            }
        }
    }

    private void loadCaptionPreview(ImageViewHolder holder) {
        if (galleryImages == null || galleryImages.isEmpty()) {
            return;
        }

        int index = holder.getBindingAdapterPosition();
        if (index < 0 || index >= galleryImages.size()) {
            return;
        }

        String previewCaption = galleryImages.get(index).caption;
        String previewCaptionUrl = galleryImages.get(index).captionUrl;
        boolean previewCaptionIsEmpty = TextUtils.isEmpty(previewCaption);
        boolean previewCaptionUrlIsEmpty = TextUtils.isEmpty(previewCaptionUrl);
        if (!previewCaptionIsEmpty || !previewCaptionUrlIsEmpty) {
            holder.binding.captionConstraintLayoutItemGalleryImageInPostFeed.setBackgroundColor(mCardViewColor & 0x0D000000); // Make 10% darker than CardViewColor
            holder.binding.captionConstraintLayoutItemGalleryImageInPostFeed.setVisibility(View.VISIBLE);
        }
        if (!previewCaptionIsEmpty) {
            holder.binding.captionTextViewItemGalleryImageInPostFeed.setTextColor(mCommentColor);
            holder.binding.captionTextViewItemGalleryImageInPostFeed.setText(previewCaption);
            holder.binding.captionTextViewItemGalleryImageInPostFeed.setSelected(true);
        }
        if (!previewCaptionUrlIsEmpty) {
            String host = Uri.parse(previewCaptionUrl).getHost();
            // A URL with no authority has no host; label the link with the URL rather than "null".
            String domain = host == null ? previewCaptionUrl : (host.startsWith("www.") ? host.substring(4) : host);
            mPostDetailMarkwon.setMarkdown(holder.binding.captionUrlTextViewItemGalleryImageInPostFeed, String.format("[%s](%s)", domain, previewCaptionUrl));
        }
    }

    public void setGalleryImages(@Nullable ArrayList<Post.Gallery> galleryImages) {
        ArrayList<Post.Gallery> incoming = galleryImages != null ? galleryImages : new ArrayList<>();
        if (showsSameImages(incoming)) {
            // Same pictures in the same order, so there is nothing to redraw. Keeping the newer list
            // is still right -- it belongs to the post object the screen now holds -- but the
            // notify below must not run.
            //
            // This is called from every bind, and a post's header is rebound on every update the
            // screen receives: the thread arriving, more children, a vote, a read mark. A
            // notifyDataSetChanged is a structural change, and a horizontal LinearLayoutManager
            // answers one by discarding its anchor and laying out from item zero again -- so a
            // carousel the user was dragging away from the first image was pulled back to it, and
            // the swipe looked like it had simply not registered. It came right once the updates
            // stopped, which is the "it needs a trigger to unlock" this removes.
            this.galleryImages = incoming;
            return;
        }
        this.galleryImages = incoming;
        clearTilePreloads();
        // A recycled holder is showing a different post now, so its pager starts back at page one.
        currentPosition = 0;
        notifyDataSetChanged();
    }

    /**
     * Whether {@code incoming} is the set of pictures already being drawn.
     *
     * Compared by url and order, which is what this adapter draws from; {@link Post.Gallery} has no
     * equality of its own, and a re-parsed post carries equal tiles in a different list.
     *
     * <p>Public because a binding holder has to know the answer <em>before</em> it decides whether to
     * reposition the carousel: a set that is about to change is about to be laid out from item zero,
     * so the position has to be re-applied whatever the carousel is doing, while an unchanged one
     * must be left alone if it is moving under a finger.
     */
    public boolean showsImages(@Nullable ArrayList<Post.Gallery> images) {
        return showsSameImages(images != null ? images : new ArrayList<>());
    }

    private boolean showsSameImages(@NonNull ArrayList<Post.Gallery> incoming) {
        ArrayList<Post.Gallery> current = galleryImages;
        if (current == null || current.size() != incoming.size()) {
            return false;
        }
        for (int i = 0; i < current.size(); i++) {
            if (!Objects.equals(current.get(i).url, incoming.get(i).url)) {
                return false;
            }
        }
        return true;
    }

    public void setBlurImage(boolean blurImage) {
        this.blurImage = blurImage;
    }

    /**
     * @param ratio height-to-width ratio for every tile, always positive: the gallery images' own
     *              ratio, or {@code 1} for the square "Fixed Height in Card" preview.
     */
    public void setRatio(float ratio) {
        this.ratio = ratio;
    }

    /**
     * @param maxPreviewHeight ceiling in pixels for a tile's height, or {@code 0} for none. Only
     *                         the square preview needs one -- a tile sized from the image's own
     *                         ratio is already the shape the caller asked for.
     */
    public void setMaxPreviewHeight(int maxPreviewHeight) {
        this.maxPreviewHeight = maxPreviewHeight;
    }

    public void setIsGridLayout(boolean isGridLayout) {
        this.isGridLayout = isGridLayout;
    }

    /**
     * The box every tile of the row layout decodes to, in pixels, or 0 for either to size tiles
     * from their views. Must be the box the tile measures to -- the width of the pager and
     * {@link #setRatio}'s shape of it -- since the picture is fitted inside it.
     */
    public void setTileSize(int tileWidthPx, int tileHeightPx) {
        this.tileWidthPx = tileWidthPx;
        this.tileHeightPx = tileHeightPx;
    }

    /** Data saving keeps every tile on its widest downscale, never the original upload. */
    public void setDataSavingMode(boolean dataSavingMode) {
        this.dataSavingMode = dataSavingMode;
    }

    class ImageViewHolder extends RecyclerView.ViewHolder {

        ItemGalleryImageInPostFeedBinding binding;
        // The deferred-load layout listener for this holder, if it hasn't fired yet. Tracked so a
        // stale one can be removed on rebind/recycle.
        @Nullable
        View.OnLayoutChangeListener pendingLayoutListener;

        public ImageViewHolder(ItemGalleryImageInPostFeedBinding binding) {
            super(binding.getRoot());

            this.binding = binding;

            if (typeface != null) {
                binding.errorTextViewItemGalleryImageInPostFeed.setTypeface(typeface);
            }
            binding.progressBarItemGalleryImageInPostFeed.setIndicatorColor(mColorAccent);
            binding.errorTextViewItemGalleryImageInPostFeed.setTextColor(mPrimaryTextColor);
            binding.errorImageViewItemGalleryImageInPostFeed.setColorFilter(
                    // mPrimaryTextColor is the correct color here.
                    mPrimaryTextColor,
                    PorterDuff.Mode.SRC_IN
            );

            binding.errorTextViewItemGalleryImageInPostFeed.setOnClickListener(view -> {
                binding.progressBarItemGalleryImageInPostFeed.setVisibility(View.VISIBLE);
                binding.errorTextViewItemGalleryImageInPostFeed.setVisibility(View.GONE);
                loadImage(this);
            });

            binding.errorImageViewItemGalleryImageInPostFeed.setOnClickListener(view -> {
                binding.progressBarItemGalleryImageInPostFeed.setVisibility(View.VISIBLE);
                binding.errorImageViewItemGalleryImageInPostFeed.setVisibility(View.GONE);
                loadImage(this);
            });
        }
    }
}
