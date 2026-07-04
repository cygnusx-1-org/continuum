package ml.docilealligator.infinityforreddit.settings;

import ml.docilealligator.infinityforreddit.utils.SharedPreferencesUtils;

/**
 * One entry in the main page's ordered tab list (see {@link MainPageTabsUtils}).
 *
 * <p>An entry is either a user-added tab ({@code source == MAIN_PAGE_TAB_SOURCE_USER}) or an
 * individual item pulled in by a "Show ..." toggle ({@code source == MAIN_PAGE_TAB_SOURCE_GROUP_*},
 * e.g. one subscribed subreddit or one multireddit). Toggle-sourced items are flattened into the
 * same list so they can be reordered freely, and are tagged with their {@code source} so they can
 * be removed together when the toggle is turned off. Serialized to/from JSON with Gson, so field
 * names are part of the on-disk format.
 */
public class MainPageTabInput {
    public int postType;
    public String name;
    // For toggle-sourced items, the tab label (a multireddit's display name differs from its path,
    // stored in name). Null for user tabs, whose label is derived from type/name.
    public String displayName;
    public int source;

    public MainPageTabInput() {
        // Required for Gson deserialization.
    }

    public MainPageTabInput(int postType, String name, int source) {
        this.postType = postType;
        this.name = name == null ? "" : name;
        this.source = source;
    }

    /** True for toggle-sourced items (as opposed to user-added tabs). */
    public boolean isGroup() {
        return source != SharedPreferencesUtils.MAIN_PAGE_TAB_SOURCE_USER;
    }
}
