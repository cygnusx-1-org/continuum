package ml.docilealligator.infinityforreddit.utils;

import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.text.Html;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.bumptech.glide.Glide;
import com.bumptech.glide.request.Request;
import com.bumptech.glide.request.target.SizeReadyCallback;
import com.bumptech.glide.request.target.Target;
import com.bumptech.glide.request.transition.Transition;
import java.lang.ref.WeakReference;
import java.util.Objects;

public class GlideImageGetter implements Html.ImageGetter {

    private final WeakReference<TextView> container;
    private boolean enlargeImage;
    @Nullable
    private final HtmlImagesHandler imagesHandler;
    private float density = 1.0f;
    private final float textSize;

    public GlideImageGetter(TextView textView, boolean enlargeImage) {
        this(textView, false, null);
        this.enlargeImage = enlargeImage;
    }

    public GlideImageGetter(TextView textView, boolean densityAware,
                            @Nullable HtmlImagesHandler imagesHandler) {
        this.container = new WeakReference<>(textView);
        this.imagesHandler = imagesHandler;
        if (densityAware) {
            density = textView.getResources().getDisplayMetrics().density;
        }
        textSize = textView.getTextSize();
    }

    @Override
    public Drawable getDrawable(String source) {
        if (imagesHandler != null) {
            imagesHandler.addImage(source);
        }

        BitmapDrawablePlaceholder drawable = new BitmapDrawablePlaceholder();

        TextView containerView = container.get();
        if (containerView != null) {
            containerView.post(() -> {
                TextView textView = container.get();
                if (textView != null) {
                    Context context = textView.getContext();
                    if (!(context instanceof Activity && (((Activity) context).isFinishing() || ((Activity) context).isDestroyed()))) {
                        Glide.with(context)
                                .asBitmap()
                                .load(source)
                                .into(drawable);
                    }
                }
            });
        }

        return drawable;
    }

    private class BitmapDrawablePlaceholder extends BitmapDrawable implements Target<Bitmap> {

        @Nullable
        protected Drawable drawable;

        BitmapDrawablePlaceholder() {
            super(Objects.requireNonNull(container.get()).getResources(),
                    Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888));
            // Reserve the line box before the bitmap arrives. Until setBounds runs the drawable is
            // empty, and ImageSpan takes the span's width and line ascent straight from its bounds,
            // so a flair icon occupies nothing until it loads and then snaps to full size, growing
            // the line, the header and the row, and shifting everything below it. The height
            // setDrawable() settles on comes from the text size and never from the bitmap, so it is
            // already known here and reserving it is exact. Only the width depends on the bitmap's
            // aspect ratio, and correcting that moves text along the line instead of moving rows.
            int reservedSize = (int) (enlargeImage ? textSize * 1.5 : textSize);
            setBounds(0, 0, reservedSize, reservedSize);
        }

        @Override
        public void draw(final Canvas canvas) {
            if (drawable != null) {
                drawable.draw(canvas);
            }
        }

        private void setDrawable(Drawable drawable) {
            this.drawable = drawable;
            int drawableWidth = (int) (drawable.getIntrinsicWidth() * density);
            int drawableHeight = (int) (drawable.getIntrinsicHeight() * density);
            float ratio = (float) drawableWidth / (float) drawableHeight;
            drawableHeight = enlargeImage ? (int) (textSize * 1.5) : (int) textSize;
            drawableWidth = (int) (drawableHeight * ratio);
            drawable.setBounds(0, 0, drawableWidth, drawableHeight);
            setBounds(0, 0, drawableWidth, drawableHeight);

            TextView textView = container.get();
            if (textView != null) {
                textView.setText(textView.getText());
            }
        }

        @Override
        public void onLoadStarted(@Nullable Drawable placeholderDrawable) {
            if (placeholderDrawable != null) {
                setDrawable(placeholderDrawable);
            }
        }

        @Override
        public void onLoadFailed(@Nullable Drawable errorDrawable) {
            if (errorDrawable != null) {
                setDrawable(errorDrawable);
            }
        }

        @Override
        public void onResourceReady(@NonNull Bitmap bitmap, @Nullable Transition<? super Bitmap> transition) {
            if (container != null) {
                TextView textView = container.get();
                if (textView != null) {
                    Resources resources = textView.getResources();
                    if (resources != null) {
                        setDrawable(new BitmapDrawable(resources, bitmap));
                    }
                }
            }
        }

        @Override
        public void onLoadCleared(@Nullable Drawable placeholderDrawable) {
            if (placeholderDrawable != null) {
                setDrawable(placeholderDrawable);
            }
        }

        @Override
        public void getSize(@NonNull SizeReadyCallback cb) {
            cb.onSizeReady(Target.SIZE_ORIGINAL, Target.SIZE_ORIGINAL);
        }

        @Override
        public void removeCallback(@NonNull SizeReadyCallback cb) {}

        @Override
        public void setRequest(@Nullable Request request) {}

        @Nullable
        @Override
        public Request getRequest() {
            return null;
        }

        @Override
        public void onStart() {}

        @Override
        public void onStop() {}

        @Override
        public void onDestroy() {}

    }

    public interface HtmlImagesHandler {
        void addImage(String uri);
    }
}
