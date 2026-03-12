package ml.docilealligator.infinityforreddit.customviews;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

/**
 * Draws a colored triangle in the bottom-right corner of its bounds.
 * Used as a post type indicator overlay on image previews/thumbnails.
 */
public class PostTypeIndicatorView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();

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
        paint.setColor(color);
        invalidate();
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
}
