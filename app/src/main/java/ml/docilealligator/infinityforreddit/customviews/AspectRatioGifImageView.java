package ml.docilealligator.infinityforreddit.customviews;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import pl.droidsonroids.gif.GifImageView;

public class AspectRatioGifImageView extends GifImageView {
    private float ratio;
    private int ratioMaxHeight;

    public AspectRatioGifImageView(Context context) {
        super(context);
        this.ratio = 1.0F;
    }

    public AspectRatioGifImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.ratio = 1.0F;
        this.init(context, attrs);
    }

    public final float getRatio() {
        return this.ratio;
    }

    public final void setRatio(float var1) {
        if (Math.abs(this.ratio - var1) > 0.0001) {
            this.ratio = var1;

            requestLayout();
            invalidate();
        }
    }

    /**
     * Ceiling, in pixels, for the height {@link #onMeasure} derives from the ratio. {@code 0}
     * (the default) leaves the derived height alone.
     *
     * <p>A ratio alone ties the height to the width, which is what a caller wants right up until
     * the view is wider than there is room to be tall -- a square preview in a landscape feed can
     * come out taller than the whole screen. The cap is applied only where the height is derived
     * from the width; deriving the width from the height is left untouched, since clamping there
     * would break the pair.
     */
    public final void setRatioMaxHeight(int ratioMaxHeight) {
        if (this.ratioMaxHeight != ratioMaxHeight) {
            this.ratioMaxHeight = ratioMaxHeight;

            requestLayout();
            invalidate();
        }
    }

    private void init(Context context, AttributeSet attrs) {
        if (attrs != null) {
            TypedArray a = context.obtainStyledAttributes(attrs, com.santalu.aspectratioimageview.R.styleable.AspectRatioImageView);
            this.ratio = a.getFloat(com.santalu.aspectratioimageview.R.styleable.AspectRatioImageView_ari_ratio, 1.0F);
            a.recycle();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        if (this.ratio > 0) {
            int width = this.getMeasuredWidth();
            int height = this.getMeasuredHeight();
            if (width != 0 || height != 0) {
                if (width > 0) {
                    height = (int) ((float) width * this.ratio);
                    if (this.ratioMaxHeight > 0 && height > this.ratioMaxHeight) {
                        height = this.ratioMaxHeight;
                    }
                } else {
                    width = (int) ((float) height / this.ratio);
                }

                this.setMeasuredDimension(width, height);
            }
        }
    }
}
