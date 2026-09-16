package ml.docilealligator.infinityforreddit.markdown.spoiler;

import android.text.Layout;
import android.text.Spannable;
import android.text.TextPaint;
import android.text.style.ClickableSpan;
import android.view.View;
import android.widget.TextView;
import androidx.annotation.NonNull;
import ml.docilealligator.infinityforreddit.customviews.SpoilerOnClickTextView;

public class SpoilerSpan extends ClickableSpan {
    final int textColor;
    final int backgroundColor;
    private boolean isShowing = false;

    public SpoilerSpan(int textColor, int backgroundColor) {
        this.textColor = textColor;
        this.backgroundColor = backgroundColor;
    }

    @Override
    public void onClick(@NonNull View widget) {
        if (!(widget instanceof TextView)) {
            return;
        }

        final TextView textView = (TextView) widget;
        final Spannable spannable = (Spannable) textView.getText();

        final int end = spannable.getSpanEnd(this);

        if (end < 0) {
            return;
        }

        final Layout layout = textView.getLayout();
        if (layout == null) {
            return;
        }

        if (widget instanceof SpoilerOnClickTextView) {
            ((SpoilerOnClickTextView) textView).setSpoilerOnClick(true);
        }
        isShowing = !isShowing;
        widget.invalidate();
    }

    public void onLongClick(@NonNull View widget) {
        if (widget instanceof SpoilerOnClickTextView) {
            ((SpoilerOnClickTextView) widget).setSpoilerOnClick(true);
        }
    }

    public boolean isShowing() {
        return isShowing;
    }

    /**
     * The colour of the block drawn behind a hidden spoiler, so {@link SpoilerMaskDrawable} can
     * repaint it over the text.
     */
    public int getBackgroundColor() {
        return backgroundColor;
    }

    /**
     * Note that painting the glyphs in the block's colour is not on its own enough to hide a
     * spoiler: colour emoji and replacement spans do not draw with this paint's colour. They are
     * covered afterwards by {@link SpoilerMaskDrawable}.
     */
    @Override
    public void updateDrawState(@NonNull TextPaint ds) {
        if (isShowing) {
            ds.bgColor = backgroundColor & 0x0D000000; //Slightly darker background color for revealed spoiler
            super.updateDrawState(ds);
        } else {
            ds.bgColor = backgroundColor;
        }
        ds.setColor(textColor);
        ds.setUnderlineText(false);
    }
}