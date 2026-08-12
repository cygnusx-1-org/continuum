package ml.docilealligator.infinityforreddit.settings;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import java.util.Locale;

public class SettingsSearchItem {
    public final String title;
    @Nullable
    public final String summary;
    public final String breadcrumb;
    /**
     * {@code app:key} of the preference, used to scroll to it once its screen opens. Null when
     * this row only navigates: its key names it on the screen it sits on, not the one it opens.
     */
    @Nullable
    public final String key;
    public final Class<? extends Fragment> fragmentClass;
    /**
     * Toolbar title for {@link #fragmentClass}, already resolved -- preference titles may be
     * literals rather than string resources, so this cannot be a resource id.
     */
    public final String fragmentTitle;
    /**
     * Every searchable field, lowercased once at build time so filtering does not redo it on each
     * keystroke. Folded with {@link Locale#ROOT} rather than the default locale, which would make
     * matching depend on the in-app language -- in Turkish, "I".toLowerCase() is a dotless "ı",
     * so a query of "i" would stop matching titles containing "I".
     */
    public final String searchHaystack;
    /** Lowercased title, so filtering can rank title matches without refolding per keystroke. */
    public final String titleLower;

    public SettingsSearchItem(String title, @Nullable String summary, String breadcrumb,
                               @Nullable String key, Class<? extends Fragment> fragmentClass,
                               String fragmentTitle) {
        this.title = title;
        this.summary = summary;
        this.breadcrumb = breadcrumb;
        this.key = key;
        this.fragmentClass = fragmentClass;
        this.fragmentTitle = fragmentTitle;

        this.titleLower = title.toLowerCase(Locale.ROOT);
        StringBuilder haystack = new StringBuilder(title);
        if (summary != null) {
            haystack.append("\n").append(summary);
        }
        haystack.append("\n").append(breadcrumb);
        this.searchHaystack = haystack.toString().toLowerCase(Locale.ROOT);
    }
}
