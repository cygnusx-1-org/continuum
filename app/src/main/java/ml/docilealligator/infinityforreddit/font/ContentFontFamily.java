package ml.docilealligator.infinityforreddit.font;

import androidx.annotation.Nullable;
import ml.docilealligator.infinityforreddit.R;

public enum ContentFontFamily {
    Default(R.style.ContentFontFamily, "Default"),
    Custom(R.style.ContentFontFamily, "Custom");

    private final int resId;
    private final String title;

    ContentFontFamily(int resId, String title) {
        this.resId = resId;
        this.title = title;
    }

    /**
     * The family a stored name stands for. A name of a font that has since been removed, or none at
     * all, is the default font rather than a crash.
     */
    public static ContentFontFamily of(@Nullable String name) {
        for (ContentFontFamily family : values()) {
            if (family.name().equals(name)) {
                return family;
            }
        }
        return Default;
    }

    public int getResId() {
        return resId;
    }

    public String getTitle() {
        return title;
    }
}
