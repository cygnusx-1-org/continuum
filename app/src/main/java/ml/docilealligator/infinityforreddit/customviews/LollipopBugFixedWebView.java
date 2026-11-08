package ml.docilealligator.infinityforreddit.customviews;

import android.content.Context;
import android.os.Build;
import android.util.AttributeSet;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.webkit.WebView;

public class LollipopBugFixedWebView extends WebView {
    private boolean isAnonymous;

    public LollipopBugFixedWebView(Context context) {
        super(context);
    }

    public LollipopBugFixedWebView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public LollipopBugFixedWebView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void setAnonymous(boolean isAnonymous) {
        this.isAnonymous = isAnonymous;
    }

    @Override
    public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
        InputConnection inputConnection = super.onCreateInputConnection(outAttrs);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && isAnonymous) {
            outAttrs.imeOptions |= EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING;
        }
        return inputConnection;
    }
}
