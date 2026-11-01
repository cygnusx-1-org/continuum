package ml.docilealligator.infinityforreddit;

import com.bumptech.glide.load.resource.bitmap.DownsampleStrategy;

public class SaveMemoryCenterInisdeDownsampleStrategy extends DownsampleStrategy {

    private int threshold;
    // One instance per strategy, so every request that crops through it carries the same option
    // value -- Glide's memory cache compares it by identity -- and a preload is found by the row.
    private final DownsampleStrategy filling = new Filling();

    public SaveMemoryCenterInisdeDownsampleStrategy(int threshold) {
        this.threshold = threshold;
    }

    @Override
    public float getScaleFactor(int sourceWidth, int sourceHeight, int requestedWidth, int requestedHeight) {
        return scaleFactor(sourceWidth, sourceHeight, requestedWidth, requestedHeight, false);
    }

    /**
     * QUALITY, as Glide's own CenterInside uses. Glide decodes in two steps: a power-of-two
     * {@code inSampleSize}, then density scaling from that size down to the exact one this strategy
     * asks for. MEMORY rounds the sample size up, so the first step already lands below the target,
     * and the second cannot climb back because {@link #getScaleFactor} never scales up. Every image
     * larger than its view came out at 50-100% of the view's size -- a 1500px-wide picture in a
     * 1440px card decoded to 750px and was stretched back across the card.
     *
     * QUALITY rounds the sample size down instead, so the first step stays at or above the target
     * and the second shrinks it to the exact size. The memory bound is unchanged in kind: the
     * intermediate is never larger than the source after the threshold halving in
     * {@link #scaleFactor}, so it stays within the "Post Feed Preview Max Resolution" setting.
     */
    @Override
    public SampleSizeRounding getSampleSizeRounding(int sourceWidth, int sourceHeight, int requestedWidth, int requestedHeight) {
        return SampleSizeRounding.QUALITY;
    }

    public void setThreshold(int threshold) {
        this.threshold = threshold;
    }

    /** The "Post Feed Preview Max Resolution" setting, in pixels, that this strategy holds to. */
    public int getThreshold() {
        return threshold;
    }

    /**
     * The same strategy for a picture its view crops to fill the box (a {@code centerCrop()}
     * request), under the same threshold.
     *
     * This one decodes to fit <em>inside</em> the box, which is right for a picture shown whole.
     * Cropped, the view then scales that up until it covers the box, and only the middle survives:
     * a 4:3 photo in a "Fixed Height in Card" square decoded to 1440x1080 and was blown up by a
     * third to fill 1440x1440. Filling decodes it to cover the box instead, so the crop is 1:1.
     */
    public DownsampleStrategy filling() {
        return filling;
    }

    private float scaleFactor(int sourceWidth, int sourceHeight, int requestedWidth, int requestedHeight, boolean fill) {
        int originalSourceWidth = sourceWidth;
        int originalSourceHeight = sourceHeight;
        if (sourceWidth * sourceHeight > threshold) {
            int divisor = 2;
            do {
                sourceWidth /= divisor;
                sourceHeight /= divisor;
            } while (sourceWidth * sourceHeight > threshold);
        }

        float widthPercentage = (float) requestedWidth / (float) sourceWidth;
        float heightPercentage = (float) requestedHeight / (float) sourceHeight;
        float fit = fill ? Math.max(widthPercentage, heightPercentage) : Math.min(widthPercentage, heightPercentage);

        return Math.min((float) sourceWidth / (float) originalSourceWidth, (float) sourceHeight / (float) originalSourceHeight) * Math.min(1.f, fit);
    }

    private final class Filling extends DownsampleStrategy {
        @Override
        public float getScaleFactor(int sourceWidth, int sourceHeight, int requestedWidth, int requestedHeight) {
            return scaleFactor(sourceWidth, sourceHeight, requestedWidth, requestedHeight, true);
        }

        // QUALITY for the same reason as the outer strategy's.
        @Override
        public SampleSizeRounding getSampleSizeRounding(int sourceWidth, int sourceHeight, int requestedWidth, int requestedHeight) {
            return SampleSizeRounding.QUALITY;
        }
    }
}
