package ml.docilealligator.infinityforreddit;

import android.graphics.Typeface;
import androidx.annotation.Nullable;

public interface CustomFontReceiver {
    void setCustomFont(@Nullable Typeface typeface, @Nullable Typeface titleTypeface, @Nullable Typeface contentTypeface);
}
