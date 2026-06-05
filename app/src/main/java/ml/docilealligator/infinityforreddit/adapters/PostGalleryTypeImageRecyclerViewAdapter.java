package ml.docilealligator.infinityforreddit.adapters;

import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.RequestBuilder;
import com.bumptech.glide.RequestManager;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.Target;

import java.util.ArrayList;

import io.noties.markwon.Markwon;
import jp.wasabeef.glide.transformations.BlurTransformation;
import ml.docilealligator.infinityforreddit.SaveMemoryCenterInisdeDownsampleStrategy;
import ml.docilealligator.infinityforreddit.databinding.ItemGalleryImageInPostFeedBinding;
import ml.docilealligator.infinityforreddit.post.Post;

public class PostGalleryTypeImageRecyclerViewAdapter extends RecyclerView.Adapter<PostGalleryTypeImageRecyclerViewAdapter.ImageViewHolder> {
    private final RequestManager glide;
    private final Typeface typeface;
    private Markwon mPostDetailMarkwon;
    private final SaveMemoryCenterInisdeDownsampleStrategy saveMemoryCenterInisdeDownsampleStrategy;
    private final int mColorAccent;
    private final int mPrimaryTextColor;
    private int mCardViewColor;
    private int mCommentColor;
    private final float mScale;
    private ArrayList<Post.Gallery> galleryImages;
    private boolean blurImage;
    private float ratio;
    private final boolean showCaption;

    public PostGalleryTypeImageRecyclerViewAdapter(RequestManager glide, Typeface typeface,
                                                   SaveMemoryCenterInisdeDownsampleStrategy saveMemoryCenterInisdeDownsampleStrategy,
                                                   int mColorAccent, int mPrimaryTextColor, float scale) {
        this.glide = glide;
        this.typeface = typeface;
        this.saveMemoryCenterInisdeDownsampleStrategy = saveMemoryCenterInisdeDownsampleStrategy;
        this.mColorAccent = mColorAccent;
        this.mPrimaryTextColor = mPrimaryTextColor;
        this.mScale = scale;
        showCaption = false;
    }

    public PostGalleryTypeImageRecyclerViewAdapter(RequestManager glide, Typeface typeface, Markwon postDetailMarkwon,
                                                   SaveMemoryCenterInisdeDownsampleStrategy saveMemoryCenterInisdeDownsampleStrategy,
                                                   int mColorAccent, int mPrimaryTextColor, int mCardViewColor,
                                                   int mCommentColor, float scale) {
        this.glide = glide;
        this.typeface = typeface;
        this.mPostDetailMarkwon = postDetailMarkwon;
        this.saveMemoryCenterInisdeDownsampleStrategy = saveMemoryCenterInisdeDownsampleStrategy;
        this.mColorAccent = mColorAccent;
        this.mPrimaryTextColor = mPrimaryTextColor;
        this.mCardViewColor = mCardViewColor;
        this.mCommentColor = mCommentColor;
        this.mScale = scale;
        showCaption = true;
    }

    @NonNull
    @Override
    public ImageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ImageViewHolder(ItemGalleryImageInPostFeedBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ImageViewHolder holder, int position) {
        if (ratio < 0) {
            int height = (int) (400 * mScale);
            holder.binding.imageViewItemGalleryImageInPostFeed.setScaleType(ImageView.ScaleType.CENTER_CROP);
            holder.binding.imageViewItemGalleryImageInPostFeed.getLayoutParams().height = height;
        } else {
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

        if (hasUsableWidth(imageView)) {
            // The recycled view is already laid out at its final width, so no layout change will
            // fire. Load now — otherwise loadImage() would never run and the spinner spins forever.
            loadImage(holder);
        } else {
            holder.pendingLayoutListener = new View.OnLayoutChangeListener() {
                @Override
                public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
                    int viewWidth = right - left;
                    // In split/weighted layouts, views get intermediate layout passes with
                    // incorrect (tiny) dimensions. Skip those and wait for the real size.
                    ViewGroup parent = (ViewGroup) v.getParent();
                    while (parent != null && !(parent instanceof RecyclerView)) {
                        parent = (ViewGroup) parent.getParent();
                    }
                    if (parent != null && parent.getWidth() > 0 && viewWidth < parent.getWidth() / 2) {
                        return;
                    }
                    v.removeOnLayoutChangeListener(this);
                    holder.pendingLayoutListener = null;
                    loadImage(holder);
                }
            };
            imageView.addOnLayoutChangeListener(holder.pendingLayoutListener);
        }

        if (showCaption) {
            loadCaptionPreview(holder);
        }
    }

    @Override
    public int getItemCount() {
        return galleryImages == null ? 0 : galleryImages.size();
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
        glide.clear(holder.binding.imageViewItemGalleryImageInPostFeed);
    }

    // Whether the view already has its final width, mirroring the guard in the layout listener: a
    // width below half the RecyclerView width is an intermediate (split/weighted) layout pass.
    private boolean hasUsableWidth(View imageView) {
        int viewWidth = imageView.getWidth();
        if (viewWidth <= 0) {
            return false;
        }
        ViewGroup parent = (ViewGroup) imageView.getParent();
        while (parent != null && !(parent instanceof RecyclerView)) {
            parent = (ViewGroup) parent.getParent();
        }
        return parent == null || parent.getWidth() <= 0 || viewWidth >= parent.getWidth() / 2;
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
        // Prefer the resolution-bounded feed preview for static images; fall back to the source
        // (always used for GIFs/videos and for images without a usable preview). The full-screen
        // media view is unaffected — it loads `url` directly.
        String loadUrl = galleryImage.feedPreviewUrl != null ? galleryImage.feedPreviewUrl : galleryImage.url;

        RequestBuilder<Drawable> imageRequestBuilder = glide.load(loadUrl)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .listener(new RequestListener<>() {
            @Override
            public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
                holder.binding.progressBarItemGalleryImageInPostFeed.setVisibility(View.GONE);
                holder.binding.errorTextViewItemGalleryImageInPostFeed.setVisibility(View.VISIBLE);
                return false;
            }

            @Override
            public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
                holder.binding.errorTextViewItemGalleryImageInPostFeed.setVisibility(View.GONE);
                holder.binding.progressBarItemGalleryImageInPostFeed.setVisibility(View.GONE);
                return false;
            }
        });
        if (blurImage) {
            imageRequestBuilder.apply(RequestOptions.bitmapTransform(new BlurTransformation(50, 10)))
                    .into(holder.binding.imageViewItemGalleryImageInPostFeed);
        } else {
            imageRequestBuilder.centerInside().downsample(saveMemoryCenterInisdeDownsampleStrategy).into(holder.binding.imageViewItemGalleryImageInPostFeed);
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
            String domain = Uri.parse(previewCaptionUrl).getHost();
            domain = domain.startsWith("www.") ? domain.substring(4) : domain;
            mPostDetailMarkwon.setMarkdown(holder.binding.captionUrlTextViewItemGalleryImageInPostFeed, String.format("[%s](%s)", domain, previewCaptionUrl));
        }
    }

    public void setGalleryImages(ArrayList<Post.Gallery> galleryImages) {
        this.galleryImages = galleryImages;
        notifyDataSetChanged();
    }

    public void setBlurImage(boolean blurImage) {
        this.blurImage = blurImage;
    }

    public void setRatio(float ratio) {
        this.ratio = ratio;
    }

    class ImageViewHolder extends RecyclerView.ViewHolder {

        ItemGalleryImageInPostFeedBinding binding;
        // The deferred-load layout listener for this holder, if it hasn't fired yet. Tracked so a
        // stale one can be removed on rebind/recycle.
        View.OnLayoutChangeListener pendingLayoutListener;

        public ImageViewHolder(ItemGalleryImageInPostFeedBinding binding) {
            super(binding.getRoot());

            this.binding = binding;

            if (typeface != null) {
                binding.errorTextViewItemGalleryImageInPostFeed.setTypeface(typeface);
            }
            binding.progressBarItemGalleryImageInPostFeed.setIndicatorColor(mColorAccent);
            binding.errorTextViewItemGalleryImageInPostFeed.setTextColor(mPrimaryTextColor);

            binding.errorTextViewItemGalleryImageInPostFeed.setOnClickListener(view -> {
                binding.progressBarItemGalleryImageInPostFeed.setVisibility(View.VISIBLE);
                binding.errorTextViewItemGalleryImageInPostFeed.setVisibility(View.GONE);
                loadImage(this);
            });
        }
    }
}
