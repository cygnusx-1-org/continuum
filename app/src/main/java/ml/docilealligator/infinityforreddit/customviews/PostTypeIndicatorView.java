package ml.docilealligator.infinityforreddit.customviews;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

/**
 * Draws a colored triangle with a diagonal depth gradient in the bottom-right
 * corner of its bounds. Used as a post type indicator overlay on image
 * previews/thumbnails.
 */
public class PostTypeIndicatorView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();

    private int baseColor = Color.TRANSPARENT;
    // Cached shader inputs so the gradient is only rebuilt when the colour or size changes,
    // not on every bind.
    private int shaderColor = 0;
    private int shaderWidth = 0;
    private int shaderHeight = 0;

    public PostTypeIndicatorView(Context context) {
        super(context);
    }

    public PostTypeIndicatorView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public PostTypeIndicatorView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void setIndicatorColor(int color) {
        if (color == baseColor) return;
        baseColor = color;
        // Fallback solid colour in case the gradient shader isn't built yet (view not laid out).
        paint.setColor(color);
        updateShader();
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        updateShader();
    }

    private void updateShader() {
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }
        if (baseColor == shaderColor && w == shaderWidth && h == shaderHeight) {
            return;
        }
        // Lighter toward the hypotenuse (top-left), darker into the outer corner.
        paint.setShader(new LinearGradient(0, 0, w, h,
                lighten(baseColor, 0.30f), darken(baseColor, 0.55f), Shader.TileMode.CLAMP));
        shaderColor = baseColor;
        shaderWidth = w;
        shaderHeight = h;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w == 0 || h == 0) return;

        path.reset();
        path.moveTo(w, 0);    // top-right
        path.lineTo(w, h);    // bottom-right
        path.lineTo(0, h);    // bottom-left
        path.close();

        canvas.drawPath(path, paint);
    }

    private static int darken(int color, float factor) {
        return Color.rgb(
                (int) (Color.red(color) * factor),
                (int) (Color.green(color) * factor),
                (int) (Color.blue(color) * factor));
    }

    private static int lighten(int color, float factor) {
        return Color.rgb(
                (int) (Color.red(color) + (255 - Color.red(color)) * factor),
                (int) (Color.green(color) + (255 - Color.green(color)) * factor),
                (int) (Color.blue(color) + (255 - Color.blue(color)) * factor));
    }
}
