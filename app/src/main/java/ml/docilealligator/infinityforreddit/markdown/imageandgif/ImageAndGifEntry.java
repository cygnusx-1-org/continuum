package ml.docilealligator.infinityforreddit.markdown.imageandgif;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Outline;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.URLSpan;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.bumptech.glide.RequestBuilder;
import com.bumptech.glide.RequestManager;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.load.resource.bitmap.CenterInside;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.Target;
import io.noties.markwon.Markwon;
import io.noties.markwon.recycler.MarkwonAdapter;
import java.util.Objects;
import jp.wasabeef.glide.transformations.BlurTransformation;
import me.saket.bettermovementmethod.BetterLinkMovementMethod;
import ml.docilealligator.infinityforreddit.SaveMemoryCenterInisdeDownsampleStrategy;
import ml.docilealligator.infinityforreddit.activities.BaseActivity;
import ml.docilealligator.infinityforreddit.activities.LinkResolverActivity;
import ml.docilealligator.infinityforreddit.bottomsheetfragments.UrlMenuBottomSheetFragment;
import ml.docilealligator.infinityforreddit.customviews.AspectRatioGifImageView;
import ml.docilealligator.infinityforreddit.databinding.MarkdownImageAndGifBlockBinding;
import ml.docilealligator.infinityforreddit.thing.MediaMetadata;
import ml.docilealligator.infinityforreddit.utils.SharedPreferencesUtils;
import ml.docilealligator.infinityforreddit.utils.Utils;

public class ImageAndGifEntry extends MarkwonAdapter.Entry<ImageAndGifBlock, ImageAndGifEntry.Holder> {
    private final BaseActivity baseActivity;
    private final RequestManager glide;
    private final SaveMemoryCenterInisdeDownsampleStrategy saveMemoryCenterInsideDownsampleStrategy;
    private final OnItemClickListener onItemClickListener;
    private boolean dataSavingMode;
    private final boolean disableImagePreview;
    private boolean blurImage;
    private final int colorAccent;
    private final int primaryTextColor;
    private final int postContentColor;
    private final int linkColor;
    private final boolean canShowImage;
    private final boolean canShowGif;
    private boolean autoplayCommentGif;

    public ImageAndGifEntry(BaseActivity baseActivity, RequestManager glide, int embeddedMediaType,
                            OnItemClickListener onItemClickListener) {
        this.baseActivity = baseActivity;
        this.glide = glide;
        SharedPreferences sharedPreferences = baseActivity.getDefaultSharedPreferences();
        this.saveMemoryCenterInsideDownsampleStrategy = new SaveMemoryCenterInisdeDownsampleStrategy(
                Integer.parseInt(sharedPreferences.getString(SharedPreferencesUtils.POST_FEED_MAX_RESOLUTION, "5000000")));
        this.onItemClickListener = onItemClickListener;
        colorAccent = baseActivity.getCustomThemeWrapper().getColorAccent();
        primaryTextColor = baseActivity.getCustomThemeWrapper().getPrimaryTextColor();
        postContentColor = baseActivity.getCustomThemeWrapper().getPostContentColor();
        linkColor = baseActivity.getCustomThemeWrapper().getLinkColor();
        canShowImage = SharedPreferencesUtils.canShowImage(embeddedMediaType);
        canShowGif = SharedPreferencesUtils.canShowGif(embeddedMediaType);
        autoplayCommentGif = sharedPreferences.getBoolean(SharedPreferencesUtils.AUTOPLAY_COMMENT_GIF, true);

        String dataSavingModeString = Objects.requireNonNull(sharedPreferences.getString(SharedPreferencesUtils.DATA_SAVING_MODE, SharedPreferencesUtils.DATA_SAVING_MODE_OFF));
        if (dataSavingModeString.equals(SharedPreferencesUtils.DATA_SAVING_MODE_ALWAYS)) {
            dataSavingMode = true;
        } else if (dataSavingModeString.equals(SharedPreferencesUtils.DATA_SAVING_MODE_ONLY_ON_CELLULAR_DATA)) {
            dataSavingMode = Utils.getConnectedNetwork(baseActivity) == Utils.NETWORK_TYPE_CELLULAR;
        }
        disableImagePreview = sharedPreferences.getBoolean(SharedPreferencesUtils.DISABLE_IMAGE_PREVIEW, false);
    }

    public ImageAndGifEntry(BaseActivity baseActivity, RequestManager glide, int embeddedMediaType,
                            boolean blurImage, OnItemClickListener onItemClickListener) {
        this(baseActivity, glide, embeddedMediaType, onItemClickListener);
        this.blurImage = blurImage;
    }

    public ImageAndGifEntry(BaseActivity baseActivity, RequestManager glide, int embeddedMediaType, boolean dataSavingMode,
                            boolean disableImagePreview, boolean blurImage,
                            OnItemClickListener onItemClickListener) {
        this.baseActivity = baseActivity;
        this.glide = glide;
        this.dataSavingMode = dataSavingMode;
        this.disableImagePreview = disableImagePreview;
        this.blurImage = blurImage;
        SharedPreferences sharedPreferences = baseActivity.getDefaultSharedPreferences();
        this.saveMemoryCenterInsideDownsampleStrategy = new SaveMemoryCenterInisdeDownsampleStrategy(
                Integer.parseInt(sharedPreferences.getString(SharedPreferencesUtils.POST_FEED_MAX_RESOLUTION, "5000000")));
        this.onItemClickListener = onItemClickListener;
        colorAccent = baseActivity.getCustomThemeWrapper().getColorAccent();
        primaryTextColor = baseActivity.getCustomThemeWrapper().getPrimaryTextColor();
        postContentColor = baseActivity.getCustomThemeWrapper().getPostContentColor();
        linkColor = baseActivity.getCustomThemeWrapper().getLinkColor();
        canShowImage = SharedPreferencesUtils.canShowImage(embeddedMediaType);
        canShowGif = SharedPreferencesUtils.canShowGif(embeddedMediaType);
        autoplayCommentGif = sharedPreferences.getBoolean(SharedPreferencesUtils.AUTOPLAY_COMMENT_GIF, true);
    }

    @NonNull
    @Override
    public Holder createHolder(@NonNull LayoutInflater inflater, @NonNull ViewGroup parent) {
        return new Holder(MarkdownImageAndGifBlockBinding.inflate(inflater, parent, false));
    }

    @Override
    public void bindHolder(@NonNull Markwon markwon, @NonNull Holder holder, @NonNull ImageAndGifBlock node) {
        holder.imageAndGifBlock = node;
        holder.commentId = currentCommentId;
        holder.postId = currentPostId;

        holder.binding.progressBarMarkdownImageAndGifBlock.setVisibility(View.VISIBLE);

        String url;
        int srcWidth;
        int srcHeight;
        if (dataSavingMode) {
            if (disableImagePreview) {
                showImageAsUrl(holder, node);
                return;
            } else {
                url = node.mediaMetadata.downscaled.url;
                srcWidth = node.mediaMetadata.downscaled.x;
                srcHeight = node.mediaMetadata.downscaled.y;
            }
        } else if ((node.mediaMetadata.isGIF && !canShowGif) || (!node.mediaMetadata.isGIF && !canShowImage)) {
            showImageAsUrl(holder, node);
            return;
        } else {
            url = node.mediaMetadata.original.url;
            srcWidth = node.mediaMetadata.original.x;
            srcHeight = node.mediaMetadata.original.y;
        }

        // Size images and gifs to a uniform on-screen area (preserving aspect ratio); the box is
        // re-corrected from the real drawable in onResourceReady. See issue #4.
        applyBoundedSize(holder.binding.imageViewMarkdownImageAndGifBlock, srcWidth, srcHeight);

        if (node.mediaMetadata.isGIF && !autoplayCommentGif) {
            // Autoplay-gifs-in-comments is off: load only the first frame so the gif stays still.
            // The animated-image decoder ignores Glide's dontAnimate(), but asBitmap() always
            // yields a single frame. Tapping it still opens the animated gif in the media viewer.
            glide.asBitmap()
                    .load(url)
                    .listener(holder.bitmapRequestListener)
                    .apply(RequestOptions.bitmapTransform(new CenterInside()))
                    .downsample(saveMemoryCenterInsideDownsampleStrategy)
                    .into(holder.binding.imageViewMarkdownImageAndGifBlock);
        } else {
            RequestBuilder<Drawable> imageRequestBuilder = glide.load(url).listener(holder.requestListener);
            // Rounded corners are applied at the view level (clipToOutline) so they're identical for
            // static images and animated gifs, which ignore Glide bitmap transformations. See issue #4.
            if (blurImage && !node.mediaMetadata.isGIF) {
                imageRequestBuilder
                        .apply(RequestOptions.bitmapTransform(new BlurTransformation(100, 4)))
                        .into(holder.binding.imageViewMarkdownImageAndGifBlock);
            } else {
                imageRequestBuilder
                        .apply(RequestOptions.bitmapTransform(new CenterInside()))
                        .downsample(saveMemoryCenterInsideDownsampleStrategy)
                        .into(holder.binding.imageViewMarkdownImageAndGifBlock);
            }
        }

        if (node.mediaMetadata.caption != null) {
            holder.binding.captionTextViewMarkdownImageAndGifBlock.setVisibility(View.VISIBLE);
            holder.binding.captionTextViewMarkdownImageAndGifBlock.setText(node.mediaMetadata.caption);
        }
    }

    /**
     * Target rendered area for embedded comment media, expressed as a reference box in dp. Each
     * image/gif is scaled (keeping its aspect ratio) so its area roughly matches this box's area,
     * so a wide image and a tall image take up about the same amount of space. See issue #4.
     */
    private static final int COMMENT_MEDIA_TARGET_WIDTH_DP = 270;
    private static final int COMMENT_MEDIA_TARGET_HEIGHT_DP = 165;

    private void applyBoundedSize(AspectRatioGifImageView imageView, int srcWidth, int srcHeight) {
        int[] size = boundedSize(srcWidth, srcHeight);
        // Disable the aspect-ratio-derived measurement so the explicit pixel size below is used.
        imageView.setRatio(0);
        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) imageView.getLayoutParams();
        if (params.width != size[0] || params.height != size[1] || params.gravity != Gravity.NO_GRAVITY) {
            params.width = size[0];
            params.height = size[1];
            params.gravity = Gravity.NO_GRAVITY;
            imageView.setLayoutParams(params);
        }
    }

    /**
     * Display size for media: scaled (preserving aspect ratio) so the rendered area roughly matches
     * the target box's area, so every image/gif occupies about the same amount of space regardless
     * of shape. Small sources are upscaled and large ones downscaled to hit that area; the width is
     * then capped at the screen width so very wide images don't overflow.
     */
    private int[] boundedSize(int width, int height) {
        float targetWidthPx = Utils.convertDpToPixel(COMMENT_MEDIA_TARGET_WIDTH_DP, baseActivity);
        float targetHeightPx = Utils.convertDpToPixel(COMMENT_MEDIA_TARGET_HEIGHT_DP, baseActivity);
        double targetArea = (double) targetWidthPx * targetHeightPx;
        if (width <= 0 || height <= 0) {
            return new int[]{(int) targetWidthPx, (int) targetHeightPx};
        }
        // scale^2 * (width * height) == targetArea  ->  same rendered area, aspect ratio preserved.
        double scale = Math.sqrt(targetArea / ((double) width * height));
        int w = (int) (width * scale);
        int h = (int) (height * scale);
        // A wide image can still exceed the screen width; cap it (keeping ratio).
        int screen = baseActivity.getResources().getDisplayMetrics().widthPixels;
        if (screen > 0 && w > screen) {
            h = (int) ((long) h * screen / w);
            w = screen;
        }
        return new int[]{Math.max(1, w), Math.max(1, h)};
    }

    private void showImageAsUrl(@NonNull Holder holder, @NonNull ImageAndGifBlock node) {
        holder.binding.imageWrapperRelativeLayoutMarkdownImageAndGifBlock.setVisibility(View.GONE);
        holder.binding.captionTextViewMarkdownImageAndGifBlock.setVisibility(View.VISIBLE);
        holder.binding.captionTextViewMarkdownImageAndGifBlock.setGravity(Gravity.NO_GRAVITY);
        SpannableString spannableString = new SpannableString(node.mediaMetadata.caption == null ? node.mediaMetadata.original.url : node.mediaMetadata.caption);
        spannableString.setSpan(new URLSpan(node.mediaMetadata.original.url), 0, spannableString.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        holder.binding.captionTextViewMarkdownImageAndGifBlock.setText(spannableString);
    }

    @Override
    public void onViewRecycled(@NonNull Holder holder) {
        super.onViewRecycled(holder);
        holder.binding.imageWrapperRelativeLayoutMarkdownImageAndGifBlock.setVisibility(View.VISIBLE);
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) holder.binding.imageViewMarkdownImageAndGifBlock.getLayoutParams();
        params.width = ViewGroup.LayoutParams.MATCH_PARENT;
        params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        params.gravity = Gravity.NO_GRAVITY;
        holder.binding.imageViewMarkdownImageAndGifBlock.setLayoutParams(params);

        FrameLayout.LayoutParams progressBarParams = (FrameLayout.LayoutParams) holder.binding.progressBarMarkdownImageAndGifBlock.getLayoutParams();
        progressBarParams.gravity = Gravity.CENTER;
        progressBarParams.leftMargin = (int) Utils.convertDpToPixel(8, baseActivity);
        holder.binding.progressBarMarkdownImageAndGifBlock.setLayoutParams(progressBarParams);

        glide.clear(holder.binding.imageViewMarkdownImageAndGifBlock);
        holder.binding.progressBarMarkdownImageAndGifBlock.setVisibility(View.GONE);
        holder.binding.loadImageErrorTextViewMarkdownImageAndGifBlock.setVisibility(View.GONE);
        holder.binding.captionTextViewMarkdownImageAndGifBlock.setVisibility(View.GONE);
        holder.binding.captionTextViewMarkdownImageAndGifBlock.setGravity(Gravity.CENTER_HORIZONTAL);
    }

    @Nullable
    private String currentCommentId;
    @Nullable
    private String currentPostId;

    public void setCurrentCommentId(@Nullable String commentId) {
        this.currentCommentId = commentId;
    }

    public void setCurrentPostId(@Nullable String postId) {
        this.currentPostId = postId;
    }

    public void setDataSavingMode(boolean dataSavingMode) {
        this.dataSavingMode = dataSavingMode;
    }

    public void setBlurImage(boolean blurImage) {
        this.blurImage = blurImage;
    }

    public void setAutoplayCommentGif(boolean autoplayCommentGif) {
        this.autoplayCommentGif = autoplayCommentGif;
    }

    public class Holder extends MarkwonAdapter.Holder {
        public MarkdownImageAndGifBlockBinding binding;
        RequestListener<Drawable> requestListener;
        RequestListener<Bitmap> bitmapRequestListener;
        @Nullable
        ImageAndGifBlock imageAndGifBlock;
        @Nullable
        String commentId;
        @Nullable
        String postId;

        public Holder(@NonNull MarkdownImageAndGifBlockBinding binding) {
            super(binding.getRoot());
            this.binding = binding;

            binding.progressBarMarkdownImageAndGifBlock.setIndicatorColor(colorAccent);
            binding.loadImageErrorTextViewMarkdownImageAndGifBlock.setTextColor(primaryTextColor);
            binding.captionTextViewMarkdownImageAndGifBlock.setTextColor(postContentColor);
            binding.captionTextViewMarkdownImageAndGifBlock.setLinkTextColor(linkColor);

            if (baseActivity.typeface != null) {
                binding.loadImageErrorTextViewMarkdownImageAndGifBlock.setTypeface(baseActivity.typeface);
            }
            if (baseActivity.contentTypeface != null) {
                binding.captionTextViewMarkdownImageAndGifBlock.setTypeface(baseActivity.contentTypeface);
            }

            // Round the corners at the view level so static images and animated gifs (which ignore
            // Glide bitmap transformations) get identical rounded corners. See issue #4.
            final float cornerRadius = Utils.convertDpToPixel(8, baseActivity);
            binding.imageViewMarkdownImageAndGifBlock.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(@NonNull View view, @NonNull Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), cornerRadius);
                }
            });
            binding.imageViewMarkdownImageAndGifBlock.setClipToOutline(true);

            requestListener = new RequestListener<>() {
                @Override
                public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
                    binding.progressBarMarkdownImageAndGifBlock.setVisibility(View.GONE);
                    binding.loadImageErrorTextViewMarkdownImageAndGifBlock.setVisibility(View.VISIBLE);
                    return false;
                }

                @Override
                public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
                    binding.progressBarMarkdownImageAndGifBlock.setVisibility(View.GONE);
                    AspectRatioGifImageView iv = binding.imageViewMarkdownImageAndGifBlock;
                    // The media metadata is unreliable (giphy gifs report a square 480x480 even when
                    // they're 16:9), which makes the box the wrong shape and the media fill only part
                    // of it. Re-size from the real drawable so every image/gif fills its box at the
                    // same target area. See issue #4.
                    if (resource.getIntrinsicWidth() > 0 && resource.getIntrinsicHeight() > 0) {
                        applyBoundedSize(iv, resource.getIntrinsicWidth(), resource.getIntrinsicHeight());
                    }
                    return false;
                }
            };

            // Used for the still first-frame load when comment-gif autoplay is off; mirrors
            // requestListener but reads dimensions off the Bitmap instead of a Drawable.
            bitmapRequestListener = new RequestListener<>() {
                @Override
                public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Bitmap> target, boolean isFirstResource) {
                    binding.progressBarMarkdownImageAndGifBlock.setVisibility(View.GONE);
                    binding.loadImageErrorTextViewMarkdownImageAndGifBlock.setVisibility(View.VISIBLE);
                    return false;
                }

                @Override
                public boolean onResourceReady(Bitmap resource, Object model, Target<Bitmap> target, DataSource dataSource, boolean isFirstResource) {
                    binding.progressBarMarkdownImageAndGifBlock.setVisibility(View.GONE);
                    if (resource.getWidth() > 0 && resource.getHeight() > 0) {
                        applyBoundedSize(binding.imageViewMarkdownImageAndGifBlock, resource.getWidth(), resource.getHeight());
                    }
                    return false;
                }
            };

            binding.imageViewMarkdownImageAndGifBlock.setOnClickListener(view -> {
                if (imageAndGifBlock != null) {
                    onItemClickListener.onItemClick(imageAndGifBlock.mediaMetadata, commentId, postId);
                }
            });

            binding.captionTextViewMarkdownImageAndGifBlock.setMovementMethod(
                    BetterLinkMovementMethod.newInstance()
                            .setOnLinkClickListener((textView, url) -> {
                                Intent intent = new Intent(baseActivity, LinkResolverActivity.class);
                                intent.setData(Uri.parse(url));
                                baseActivity.startActivity(intent);
                                return true;
                            })
                            .setOnLinkLongClickListener((textView, url) -> {
                                UrlMenuBottomSheetFragment urlMenuBottomSheetFragment = UrlMenuBottomSheetFragment.newInstance(url);
                                urlMenuBottomSheetFragment.show(baseActivity.getSupportFragmentManager(), urlMenuBottomSheetFragment.getTag());
                                return true;
                            }));
        }
    }

    public interface OnItemClickListener {
        void onItemClick(MediaMetadata mediaMetadata, @Nullable String commentId, @Nullable String postId);
    }
}
