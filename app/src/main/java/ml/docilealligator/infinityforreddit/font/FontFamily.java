package ml.docilealligator.infinityforreddit.font;

import androidx.annotation.Nullable;
import ml.docilealligator.infinityforreddit.R;

public enum FontFamily {
    Default(R.style.FontFamily, "Default"),
    Custom(R.style.FontFamily, "Custom");

    private final int resId;
    private final String title;

    FontFamily(int resId, String title) {
        this.resId = resId;
        this.title = title;
    }

    /**
     * The family a stored name stands for. A name of a font that has since been removed, or none at
     * all, is the default font rather than a crash.
     */
    public static FontFamily of(@Nullable String name) {
        for (FontFamily family : values()) {
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
