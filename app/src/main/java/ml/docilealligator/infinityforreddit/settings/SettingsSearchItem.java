package ml.docilealligator.infinityforreddit.settings;

import androidx.fragment.app.Fragment;

public class SettingsSearchItem {
    public final String title;
    public final String summary;
    public final String breadcrumb;
    public final Class<? extends Fragment> fragmentClass;
    public final int fragmentTitleResId;

    public SettingsSearchItem(String title, String summary, String breadcrumb,
                               Class<? extends Fragment> fragmentClass, int fragmentTitleResId) {
        this.title = title;
        this.summary = summary;
        this.breadcrumb = breadcrumb;
        this.fragmentClass = fragmentClass;
        this.fragmentTitleResId = fragmentTitleResId;
    }
}
