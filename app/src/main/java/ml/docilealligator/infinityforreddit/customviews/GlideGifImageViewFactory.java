package ml.docilealligator.infinityforreddit.customviews;

import android.content.Context;
import android.net.Uri;
import android.view.View;
import android.widget.ImageView;
import com.bumptech.glide.Glide;
import com.github.piasy.biv.metadata.ImageInfoExtractor;
import com.github.piasy.biv.view.ImageViewFactory;
import java.io.File;
import ml.docilealligator.infinityforreddit.SaveMemoryCenterInisdeDownsampleStrategy;

public class GlideGifImageViewFactory extends ImageViewFactory {
    private SaveMemoryCenterInisdeDownsampleStrategy saveMemoryCenterInisdeDownsampleStrategy;

    public GlideGifImageViewFactory(SaveMemoryCenterInisdeDownsampleStrategy saveMemoryCenterInisdeDownsampleStrategy) {
        this.saveMemoryCenterInisdeDownsampleStrategy = saveMemoryCenterInisdeDownsampleStrategy;
    }

    @Override
    protected final View createAnimatedImageView(final Context context, final int imageType,
                                                 final int initScaleType) {
        switch (imageType) {
            case ImageInfoExtractor.TYPE_GIF:
            case ImageInfoExtractor.TYPE_ANIMATED_WEBP: {
                // ZoomableGifImageView is an ImageView subclass, so Glide animates the GIF into
                // it exactly as before while the otaliastudios zoom engine adds pinch-to-zoom and
                // pan on top. We intentionally don't call setScaleType here: ZoomImageView drives
                // its own MATRIX scale type and overriding it would break the zoom transform.
                return new ZoomableGifImageView(context);
            }
            default:
                return super.createAnimatedImageView(context, imageType, initScaleType);
        }
    }

    @Override
    public final void loadAnimatedContent(final View view, final int imageType,
                                          final File imageFile) {
        switch (imageType) {
            case ImageInfoExtractor.TYPE_GIF:
            case ImageInfoExtractor.TYPE_ANIMATED_WEBP: {
                if (view instanceof ImageView) {
                    Glide.with(view.getContext())
                            .load(imageFile)
                            .centerInside()
                            .downsample(saveMemoryCenterInisdeDownsampleStrategy)
                            .into((ImageView) view);
                }
                break;
            }

            default:
                super.loadAnimatedContent(view, imageType, imageFile);
        }
    }

    @Override
    public void loadThumbnailContent(final View view, final Uri thumbnail) {
        if (view instanceof ImageView) {
            Glide.with(view.getContext())
                    .load(thumbnail)
                    .into((ImageView) view);
        }
    }
}
