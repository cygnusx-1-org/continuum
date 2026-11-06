package ml.docilealligator.infinityforreddit.font;

import androidx.annotation.Nullable;
import ml.docilealligator.infinityforreddit.R;

public enum TitleFontFamily {
    Default(R.style.TitleFontFamily, "Default"),
    Custom(R.style.TitleFontFamily, "Custom");

    private final int resId;
    private final String title;

    TitleFontFamily(int resId, String title) {
        this.resId = resId;
        this.title = title;
    }

    /**
     * The family a stored name stands for. A name of a font that has since been removed, or none at
     * all, is the default font rather than a crash.
     */
    public static TitleFontFamily of(@Nullable String name) {
        for (TitleFontFamily family : values()) {
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
