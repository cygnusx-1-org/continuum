package ml.docilealligator.infinityforreddit.settings;

import android.content.Context;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import ml.docilealligator.infinityforreddit.R;

/**
 * Singleton that holds a flat list of all searchable settings items.
 * Call buildRegistry(context) once (e.g. in SettingsActivity.onCreate) to populate.
 */
public class SettingsSearchRegistry {

    private static SettingsSearchRegistry sInstance;
    private List<SettingsSearchItem> mItems;

    private SettingsSearchRegistry() {}

    public static SettingsSearchRegistry getInstance() {
        if (sInstance == null) {
            sInstance = new SettingsSearchRegistry();
        }
        return sInstance;
    }

    public List<SettingsSearchItem> getItems() {
        return mItems != null ? mItems : Collections.emptyList();
    }

    public void buildRegistry(Context ctx) {
        if (mItems != null) return;
        List<SettingsSearchItem> items = new ArrayList<>();
        addApiKeysPreferences(ctx, items);
        addNotificationPreferences(ctx, items);
        addInterfacePreferences(ctx, items);
        addFontPreferences(ctx, items);
        addImmersiveInterfacePreferences(ctx, items);
        addNavigationDrawerPreferences(ctx, items);
        addTimeFormatPreferences(ctx, items);
        addPostPreferences(ctx, items);
        addNumberOfColumnsPreferences(ctx, items);
        addPostDetailsPreferences(ctx, items);
        addCommentPreferences(ctx, items);
        addThemePreferences(ctx, items);
        addVideoPreferences(ctx, items);
        addGesturesAndButtonsPreferences(ctx, items);
        addSwipeActionPreferences(ctx, items);
        addSecurityPreferences(ctx, items);
        addDataSavingModePreferences(ctx, items);
        addProxyPreferences(ctx, items);
        addSortTypePreferences(ctx, items);
        addDownloadLocationPreferences(ctx, items);
        addMiscellaneousPreferences(ctx, items);
        addAdvancedPreferences(ctx, items);
        addAboutPreferences(ctx, items);
        addCreditsPreferences(ctx, items);
        addDebugPreferences(ctx, items);
        mItems = Collections.unmodifiableList(items);
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    private static void add(List<SettingsSearchItem> items, String title, String summary,
                             String breadcrumb,
                             Class<? extends androidx.fragment.app.Fragment> fragmentClass,
                             int fragmentTitleResId) {
        items.add(new SettingsSearchItem(title, summary, breadcrumb, fragmentClass, fragmentTitleResId));
    }

    // -------------------------------------------------------------------------
    // API Keys
    // -------------------------------------------------------------------------

    private void addApiKeysPreferences(Context c, List<SettingsSearchItem> items) {
        String bc = c.getString(R.string.settings_api_keys_title);
        add(items, c.getString(R.string.settings_client_id_title), null, bc,
                APIKeysPreferenceFragment.class, R.string.settings_api_keys_title);
        add(items, c.getString(R.string.settings_giphy_api_key_title), null, bc,
                APIKeysPreferenceFragment.class, R.string.settings_api_keys_title);
    }

    // -------------------------------------------------------------------------
    // Notifications
    // -------------------------------------------------------------------------

    private void addNotificationPreferences(Context c, List<SettingsSearchItem> items) {
        String bc = c.getString(R.string.settings_notification_master_title);
        add(items, c.getString(R.string.settings_notification_enable_notification_title), null, bc,
                NotificationPreferenceFragment.class, R.string.settings_notification_master_title);
    }

    // -------------------------------------------------------------------------
    // Interface (top-level items + sub-screen navigation entries)
    // -------------------------------------------------------------------------

    private void addInterfacePreferences(Context c, List<SettingsSearchItem> items) {
        String bc = c.getString(R.string.settings_interface_title);
        add(items, c.getString(R.string.settings_font_title), null, bc,
                FontPreferenceFragment.class, R.string.settings_font_title);
        add(items, c.getString(R.string.settings_navigation_drawer_title), null, bc,
                NavigationDrawerPreferenceFragment.class, R.string.settings_navigation_drawer_title);
        add(items, c.getString(R.string.settings_customize_tabs_in_main_page_title), null, bc,
                CustomizeMainPageTabsFragment.class, R.string.settings_customize_tabs_in_main_page_title);
        add(items, c.getString(R.string.settings_customize_bottom_app_bar_title), null, bc,
                CustomizeBottomAppBarFragment.class, R.string.settings_customize_bottom_app_bar_title);
        add(items, c.getString(R.string.settings_hide_fab_in_post_feed), null, bc,
                InterfacePreferenceFragment.class, R.string.settings_interface_title);
        add(items, c.getString(R.string.settings_enable_bottom_app_bar_title), null, bc,
                InterfacePreferenceFragment.class, R.string.settings_interface_title);
        add(items, c.getString(R.string.settings_hide_subreddit_description_title), null, bc,
                InterfacePreferenceFragment.class, R.string.settings_interface_title);
        add(items, c.getString(R.string.settings_use_bottom_toolbar_in_media_viewer_title), null, bc,
                InterfacePreferenceFragment.class, R.string.settings_interface_title);
        add(items, c.getString(R.string.settings_default_search_result_tab), null, bc,
                InterfacePreferenceFragment.class, R.string.settings_interface_title);
        add(items, c.getString(R.string.settings_time_format_title), null, bc,
                TimeFormatPreferenceFragment.class, R.string.settings_time_format_title);
        add(items, c.getString(R.string.settings_category_post_title), null, bc,
                PostPreferenceFragment.class, R.string.settings_category_post_title);
        add(items, c.getString(R.string.settings_post_details_title), null, bc,
                PostDetailsPreferenceFragment.class, R.string.settings_post_details_title);
        add(items, c.getString(R.string.settings_category_comment_title), null, bc,
                CommentPreferenceFragment.class, R.string.settings_category_comment_title);
        add(items, c.getString(R.string.settings_lazy_mode_interval_title), null, bc,
                InterfacePreferenceFragment.class, R.string.settings_interface_title);
        add(items, c.getString(R.string.settings_vote_buttons_on_the_right_title), null, bc,
                InterfacePreferenceFragment.class, R.string.settings_interface_title);
        add(items, c.getString(R.string.settings_show_absolute_number_of_votes_title), null, bc,
                InterfacePreferenceFragment.class, R.string.settings_interface_title);
    }

    // -------------------------------------------------------------------------
    // Font
    // -------------------------------------------------------------------------

    private void addFontPreferences(Context c, List<SettingsSearchItem> items) {
        String parent = c.getString(R.string.settings_interface_title);
        String self = c.getString(R.string.settings_font_title);
        String bc = parent + " \u203a " + self;
        add(items, c.getString(R.string.settings_preview_font_title), null, bc,
                FontPreferenceFragment.class, R.string.settings_font_title);
        add(items, c.getString(R.string.settings_font_family_title), null, bc,
                FontPreferenceFragment.class, R.string.settings_font_title);
        add(items, c.getString(R.string.settings_font_size_title), null, bc,
                FontPreferenceFragment.class, R.string.settings_font_title);
        add(items, c.getString(R.string.settings_title_font_family_title), null, bc,
                FontPreferenceFragment.class, R.string.settings_font_title);
        add(items, c.getString(R.string.settings_title_font_size_title), null, bc,
                FontPreferenceFragment.class, R.string.settings_font_title);
        add(items, c.getString(R.string.settings_content_font_family_title), null, bc,
                FontPreferenceFragment.class, R.string.settings_font_title);
        add(items, c.getString(R.string.settings_content_font_size_title), null, bc,
                FontPreferenceFragment.class, R.string.settings_font_title);
    }

    // -------------------------------------------------------------------------
    // Immersive Interface
    // -------------------------------------------------------------------------

    private void addImmersiveInterfacePreferences(Context c, List<SettingsSearchItem> items) {
        String parent = c.getString(R.string.settings_interface_title);
        String self = c.getString(R.string.settings_immersive_interface_title);
        String bc = parent + " \u203a " + self;
        add(items, c.getString(R.string.settings_immersive_interface_title),
                c.getString(R.string.settings_immersive_interface_summary), bc,
                ImmersiveInterfacePreferenceFragment.class, R.string.settings_immersive_interface_title);
        add(items, c.getString(R.string.settings_disable_immersive_interface_in_landscape_mode), null, bc,
                ImmersiveInterfacePreferenceFragment.class, R.string.settings_immersive_interface_title);
    }

    // -------------------------------------------------------------------------
    // Navigation Drawer
    // -------------------------------------------------------------------------

    private void addNavigationDrawerPreferences(Context c, List<SettingsSearchItem> items) {
        String parent = c.getString(R.string.settings_interface_title);
        String self = c.getString(R.string.settings_navigation_drawer_title);
        String bc = parent + " \u203a " + self;
        add(items, c.getString(R.string.settings_show_avatar_on_the_right), null, bc,
                NavigationDrawerPreferenceFragment.class, R.string.settings_navigation_drawer_title);
        add(items, c.getString(R.string.settings_collapse_account_section_title), null, bc,
                NavigationDrawerPreferenceFragment.class, R.string.settings_navigation_drawer_title);
        add(items, c.getString(R.string.settings_collapse_reddit_section_title), null, bc,
                NavigationDrawerPreferenceFragment.class, R.string.settings_navigation_drawer_title);
        add(items, c.getString(R.string.settings_collapse_post_section_title), null, bc,
                NavigationDrawerPreferenceFragment.class, R.string.settings_navigation_drawer_title);
        add(items, c.getString(R.string.settings_collapse_preferences_section_title), null, bc,
                NavigationDrawerPreferenceFragment.class, R.string.settings_navigation_drawer_title);
        add(items, c.getString(R.string.settings_collapse_favorite_subreddits_section_title), null, bc,
                NavigationDrawerPreferenceFragment.class, R.string.settings_navigation_drawer_title);
        add(items, c.getString(R.string.settings_collapse_subscribed_subreddits_section_title), null, bc,
                NavigationDrawerPreferenceFragment.class, R.string.settings_navigation_drawer_title);
        add(items, c.getString(R.string.settings_hide_favorite_subreddits_sections_title), null, bc,
                NavigationDrawerPreferenceFragment.class, R.string.settings_navigation_drawer_title);
        add(items, c.getString(R.string.settings_hide_subscribed_subreddits_sections_title), null, bc,
                NavigationDrawerPreferenceFragment.class, R.string.settings_navigation_drawer_title);
        add(items, c.getString(R.string.settings_navigation_drawer_enable_hide_karma_title), null, bc,
                NavigationDrawerPreferenceFragment.class, R.string.settings_navigation_drawer_title);
    }

    // -------------------------------------------------------------------------
    // Time Format
    // -------------------------------------------------------------------------

    private void addTimeFormatPreferences(Context c, List<SettingsSearchItem> items) {
        String parent = c.getString(R.string.settings_interface_title);
        String self = c.getString(R.string.settings_time_format_title);
        String bc = parent + " \u203a " + self;
        add(items, c.getString(R.string.settings_show_elapsed_time), null, bc,
                TimeFormatPreferenceFragment.class, R.string.settings_time_format_title);
        add(items, c.getString(R.string.settings_time_format_title), null, bc,
                TimeFormatPreferenceFragment.class, R.string.settings_time_format_title);
    }

    // -------------------------------------------------------------------------
    // Post
    // -------------------------------------------------------------------------

    private void addPostPreferences(Context c, List<SettingsSearchItem> items) {
        String parent = c.getString(R.string.settings_interface_title);
        String self = c.getString(R.string.settings_category_post_title);
        String bc = parent + " \u203a " + self;
        add(items, c.getString(R.string.settings_default_post_layout), null, bc,
                PostPreferenceFragment.class, R.string.settings_category_post_title);
        add(items, c.getString(R.string.settings_default_post_layout_unfolded), null, bc,
                PostPreferenceFragment.class, R.string.settings_category_post_title);
        add(items, c.getString(R.string.settings_default_link_post_layout), null, bc,
                PostPreferenceFragment.class, R.string.settings_category_post_title);
        add(items, c.getString(R.string.settings_number_of_columns_in_post_feed_title), null, bc,
                NumberOfColumnsInPostFeedPreferenceFragment.class, R.string.settings_number_of_columns_in_post_feed_title);
        add(items, c.getString(R.string.settings_hide_post_type), null, bc,
                PostPreferenceFragment.class, R.string.settings_category_post_title);
        add(items, c.getString(R.string.settings_hide_post_flair), null, bc,
                PostPreferenceFragment.class, R.string.settings_category_post_title);
        add(items, c.getString(R.string.settings_hide_subreddit_and_user_prefix), null, bc,
                PostPreferenceFragment.class, R.string.settings_category_post_title);
        add(items, c.getString(R.string.settings_hide_the_number_of_votes), null, bc,
                PostPreferenceFragment.class, R.string.settings_category_post_title);
        add(items, c.getString(R.string.settings_hide_the_number_of_comments), null, bc,
                PostPreferenceFragment.class, R.string.settings_category_post_title);
        add(items, c.getString(R.string.settings_hide_text_post_content), null, bc,
                PostPreferenceFragment.class, R.string.settings_category_post_title);
        add(items, c.getString(R.string.settings_fixed_height_preview_in_card_title), null, bc,
                PostPreferenceFragment.class, R.string.settings_category_post_title);
        add(items, c.getString(R.string.settings_show_divider_in_compact_layout), null, bc,
                PostPreferenceFragment.class, R.string.settings_category_post_title);
        add(items, c.getString(R.string.settings_show_thumbnail_on_the_left_in_compact_layout), null, bc,
                PostPreferenceFragment.class, R.string.settings_category_post_title);
        add(items, c.getString(R.string.settings_long_press_to_hide_toolbar_in_compact_layout_title), null, bc,
                PostPreferenceFragment.class, R.string.settings_category_post_title);
        add(items, c.getString(R.string.settings_post_compact_layout_toolbar_hidden_by_default_title), null, bc,
                PostPreferenceFragment.class, R.string.settings_category_post_title);
        add(items, c.getString(R.string.settings_click_to_show_media_in_gallery_layout), null, bc,
                PostPreferenceFragment.class, R.string.settings_category_post_title);
    }

    // -------------------------------------------------------------------------
    // Number of Columns
    // -------------------------------------------------------------------------

    private void addNumberOfColumnsPreferences(Context c, List<SettingsSearchItem> items) {
        String grandparent = c.getString(R.string.settings_interface_title);
        String parent = c.getString(R.string.settings_category_post_title);
        String self = c.getString(R.string.settings_number_of_columns_in_post_feed_title);
        String bc = grandparent + " \u203a " + parent + " \u203a " + self;
        Class<?> frag = NumberOfColumnsInPostFeedPreferenceFragment.class;
        int titleRes = R.string.settings_number_of_columns_in_post_feed_title;
        add(items, c.getString(R.string.settings_number_of_columns_in_post_feed_portrait_title), null, bc,
                NumberOfColumnsInPostFeedPreferenceFragment.class, titleRes);
        add(items, c.getString(R.string.settings_number_of_columns_in_post_feed_landscape_title), null, bc,
                NumberOfColumnsInPostFeedPreferenceFragment.class, titleRes);
        add(items, c.getString(R.string.settings_number_of_columns_in_post_feed_unfolded_portrait_title), null, bc,
                NumberOfColumnsInPostFeedPreferenceFragment.class, titleRes);
        add(items, c.getString(R.string.settings_number_of_columns_in_post_feed_unfolded_landscape_title), null, bc,
                NumberOfColumnsInPostFeedPreferenceFragment.class, titleRes);
    }

    // -------------------------------------------------------------------------
    // Post Details
    // -------------------------------------------------------------------------

    private void addPostDetailsPreferences(Context c, List<SettingsSearchItem> items) {
        String parent = c.getString(R.string.settings_interface_title);
        String self = c.getString(R.string.settings_post_details_title);
        String bc = parent + " \u203a " + self;
        add(items, c.getString(R.string.settings_separate_post_and_comments_in_landscape_mode_title),
                c.getString(R.string.settings_separate_post_and_comments_summary), bc,
                PostDetailsPreferenceFragment.class, R.string.settings_post_details_title);
        add(items, c.getString(R.string.settings_swap_post_and_comments_in_split_mode_title),
                c.getString(R.string.settings_swap_post_and_comments_in_split_mode_summary), bc,
                PostDetailsPreferenceFragment.class, R.string.settings_post_details_title);
        add(items, c.getString(R.string.settings_hide_post_type), null, bc,
                PostDetailsPreferenceFragment.class, R.string.settings_post_details_title);
        add(items, c.getString(R.string.settings_hide_post_flair), null, bc,
                PostDetailsPreferenceFragment.class, R.string.settings_post_details_title);
        add(items, c.getString(R.string.settings_hide_upvote_ratio_title), null, bc,
                PostDetailsPreferenceFragment.class, R.string.settings_post_details_title);
        add(items, c.getString(R.string.settings_hide_subreddit_and_user_prefix), null, bc,
                PostDetailsPreferenceFragment.class, R.string.settings_post_details_title);
        add(items, c.getString(R.string.settings_hide_the_number_of_votes), null, bc,
                PostDetailsPreferenceFragment.class, R.string.settings_post_details_title);
        add(items, c.getString(R.string.settings_hide_the_number_of_comments), null, bc,
                PostDetailsPreferenceFragment.class, R.string.settings_post_details_title);
        add(items, c.getString(R.string.settings_hide_fab_in_post_details), null, bc,
                PostDetailsPreferenceFragment.class, R.string.settings_post_details_title);
        add(items, c.getString(R.string.settings_embedded_media_type_title), null, bc,
                PostDetailsPreferenceFragment.class, R.string.settings_post_details_title);
    }

    // -------------------------------------------------------------------------
    // Comment
    // -------------------------------------------------------------------------

    private void addCommentPreferences(Context c, List<SettingsSearchItem> items) {
        String parent = c.getString(R.string.settings_interface_title);
        String self = c.getString(R.string.settings_category_comment_title);
        String bc = parent + " \u203a " + self;
        add(items, c.getString(R.string.settings_show_top_level_comments_first_title), null, bc,
                CommentPreferenceFragment.class, R.string.settings_category_comment_title);
        add(items, c.getString(R.string.settings_show_comment_divider_title), null, bc,
                CommentPreferenceFragment.class, R.string.settings_category_comment_title);
        add(items, c.getString(R.string.settings_show_only_one_comment_level_indicator), null, bc,
                CommentPreferenceFragment.class, R.string.settings_category_comment_title);
        add(items, c.getString(R.string.settings_comment_toolbar_hidden), null, bc,
                CommentPreferenceFragment.class, R.string.settings_category_comment_title);
        add(items, c.getString(R.string.settings_comment_toolbar_hide_on_click), null, bc,
                CommentPreferenceFragment.class, R.string.settings_category_comment_title);
        add(items, c.getString(R.string.settings_fully_collapse_comment_title), null, bc,
                CommentPreferenceFragment.class, R.string.settings_category_comment_title);
        add(items, c.getString(R.string.settings_remember_comment_scroll_position), null, bc,
                CommentPreferenceFragment.class, R.string.settings_category_comment_title);
        add(items, c.getString(R.string.settings_show_author_avatar_title), null, bc,
                CommentPreferenceFragment.class, R.string.settings_category_comment_title);
        add(items, c.getString(R.string.settings_show_user_prefix_title), null, bc,
                CommentPreferenceFragment.class, R.string.settings_category_comment_title);
        add(items, c.getString(R.string.settings_show_fewer_toolbar_options_threshold_title), null, bc,
                CommentPreferenceFragment.class, R.string.settings_category_comment_title);
        add(items, c.getString(R.string.settings_embedded_media_type_title), null, bc,
                CommentPreferenceFragment.class, R.string.settings_category_comment_title);
    }

    // -------------------------------------------------------------------------
    // Theme
    // -------------------------------------------------------------------------

    private void addThemePreferences(Context c, List<SettingsSearchItem> items) {
        String bc = c.getString(R.string.settings_theme_title);
        add(items, c.getString(R.string.settings_theme_title), null, bc,
                ThemePreferenceFragment.class, R.string.settings_theme_title);
        add(items, c.getString(R.string.settings_amoled_dark_title), null, bc,
                ThemePreferenceFragment.class, R.string.settings_theme_title);
        add(items, c.getString(R.string.settings_manage_themes_title), null, bc,
                ThemePreferenceFragment.class, R.string.settings_theme_title);
        add(items, c.getString(R.string.settings_enable_material_you_title),
                c.getString(R.string.settings_enable_material_you_summary), bc,
                ThemePreferenceFragment.class, R.string.settings_theme_title);
        add(items, c.getString(R.string.settings_apply_material_you_title),
                c.getString(R.string.settings_apply_material_you_summary), bc,
                ThemePreferenceFragment.class, R.string.settings_theme_title);
    }

    // -------------------------------------------------------------------------
    // Video
    // -------------------------------------------------------------------------

    private void addVideoPreferences(Context c, List<SettingsSearchItem> items) {
        String bc = c.getString(R.string.settigns_video_title);
        add(items, c.getString(R.string.settings_mute_video_title), null, bc,
                VideoPreferenceFragment.class, R.string.settigns_video_title);
        add(items, c.getString(R.string.settings_mute_nsfw_video_title), null, bc,
                VideoPreferenceFragment.class, R.string.settigns_video_title);
        add(items, c.getString(R.string.settings_video_player_ignore_nav_bar_title),
                c.getString(R.string.settings_video_player_ignore_nav_bar_summary), bc,
                VideoPreferenceFragment.class, R.string.settigns_video_title);
        add(items, c.getString(R.string.settings_video_player_automatic_landscape_orientation), null, bc,
                VideoPreferenceFragment.class, R.string.settigns_video_title);
        add(items, c.getString(R.string.settings_loop_video_title),
                c.getString(R.string.settings_loop_video_summary), bc,
                VideoPreferenceFragment.class, R.string.settigns_video_title);
        add(items, c.getString(R.string.settings_default_playback_speed_title), null, bc,
                VideoPreferenceFragment.class, R.string.settigns_video_title);
        add(items, c.getString(R.string.settings_reddit_video_default_resolution), null, bc,
                VideoPreferenceFragment.class, R.string.settigns_video_title);
        add(items, c.getString(R.string.settings_video_autoplay_title), null, bc,
                VideoPreferenceFragment.class, R.string.settigns_video_title);
        add(items, c.getString(R.string.settings_simultaneous_autoplay_limit_title), null, bc,
                VideoPreferenceFragment.class, R.string.settigns_video_title);
        add(items, c.getString(R.string.settings_legacy_autoplay_video_controller_ui_title), null, bc,
                VideoPreferenceFragment.class, R.string.settigns_video_title);
        add(items, c.getString(R.string.settings_mute_autoplaying_videos_title), null, bc,
                VideoPreferenceFragment.class, R.string.settigns_video_title);
        add(items, c.getString(R.string.settings_remember_muting_option_in_post_feed), null, bc,
                VideoPreferenceFragment.class, R.string.settigns_video_title);
        add(items, c.getString(R.string.settings_autoplay_nsfw_videos_title), null, bc,
                VideoPreferenceFragment.class, R.string.settigns_video_title);
        add(items, c.getString(R.string.settings_easier_to_watch_in_full_screen_title), null, bc,
                VideoPreferenceFragment.class, R.string.settigns_video_title);
        add(items, c.getString(R.string.settings_start_autoplay_visible_area_offset_portrait_title), null, bc,
                VideoPreferenceFragment.class, R.string.settigns_video_title);
        add(items, c.getString(R.string.settings_start_autoplay_visible_area_offset_landscape_title), null, bc,
                VideoPreferenceFragment.class, R.string.settigns_video_title);
    }

    // -------------------------------------------------------------------------
    // Gestures & Buttons
    // -------------------------------------------------------------------------

    private void addGesturesAndButtonsPreferences(Context c, List<SettingsSearchItem> items) {
        String bc = c.getString(R.string.settings_gestures_and_buttons_title);
        add(items, c.getString(R.string.settings_swipe_to_go_back_title),
                c.getString(R.string.settings_swipe_to_go_back_summary), bc,
                GesturesAndButtonsPreferenceFragment.class, R.string.settings_gestures_and_buttons_title);
        add(items, c.getString(R.string.settings_lock_toolbar_title), null, bc,
                GesturesAndButtonsPreferenceFragment.class, R.string.settings_gestures_and_buttons_title);
        add(items, c.getString(R.string.settings_volume_keys_navigate_comments_title), null, bc,
                GesturesAndButtonsPreferenceFragment.class, R.string.settings_gestures_and_buttons_title);
        add(items, c.getString(R.string.settings_volume_keys_navigate_posts_title), null, bc,
                GesturesAndButtonsPreferenceFragment.class, R.string.settings_gestures_and_buttons_title);
        add(items, c.getString(R.string.settings_pull_to_refresh_title), null, bc,
                GesturesAndButtonsPreferenceFragment.class, R.string.settings_gestures_and_buttons_title);
        add(items, c.getString(R.string.settings_swipe_between_posts_title), null, bc,
                GesturesAndButtonsPreferenceFragment.class, R.string.settings_gestures_and_buttons_title);
        add(items, c.getString(R.string.settings_tab_switching_sensitivity), null, bc,
                GesturesAndButtonsPreferenceFragment.class, R.string.settings_gestures_and_buttons_title);
        add(items, c.getString(R.string.settings_swipe_right_to_go_back_sensitivity), null, bc,
                GesturesAndButtonsPreferenceFragment.class, R.string.settings_gestures_and_buttons_title);
        add(items, c.getString(R.string.settings_swipe_action_sensitivity_in_comments), null, bc,
                GesturesAndButtonsPreferenceFragment.class, R.string.settings_gestures_and_buttons_title);
        add(items, c.getString(R.string.settings_navigation_drawer_swipe_area), null, bc,
                GesturesAndButtonsPreferenceFragment.class, R.string.settings_gestures_and_buttons_title);
        add(items, c.getString(R.string.settings_swipe_vertically_to_go_back_from_media_title), null, bc,
                GesturesAndButtonsPreferenceFragment.class, R.string.settings_gestures_and_buttons_title);
        add(items, c.getString(R.string.settings_pinch_to_zoom_video_title),
                c.getString(R.string.settings_experimental_feature), bc,
                GesturesAndButtonsPreferenceFragment.class, R.string.settings_gestures_and_buttons_title);
        add(items, c.getString(R.string.settings_lock_jump_to_next_top_level_comment_button_title), null, bc,
                GesturesAndButtonsPreferenceFragment.class, R.string.settings_gestures_and_buttons_title);
        add(items, c.getString(R.string.settings_swap_tap_and_long_title), null, bc,
                GesturesAndButtonsPreferenceFragment.class, R.string.settings_gestures_and_buttons_title);
        add(items, c.getString(R.string.long_press_post_non_media_area), null, bc,
                GesturesAndButtonsPreferenceFragment.class, R.string.settings_gestures_and_buttons_title);
        add(items, c.getString(R.string.long_press_post_media), null, bc,
                GesturesAndButtonsPreferenceFragment.class, R.string.settings_gestures_and_buttons_title);
        add(items, c.getString(R.string.settings_swipe_action_title), null, bc,
                SwipeActionPreferenceFragment.class, R.string.settings_swipe_action_title);
    }

    // -------------------------------------------------------------------------
    // Swipe Action
    // -------------------------------------------------------------------------

    private void addSwipeActionPreferences(Context c, List<SettingsSearchItem> items) {
        String parent = c.getString(R.string.settings_gestures_and_buttons_title);
        String self = c.getString(R.string.settings_swipe_action_title);
        String bc = parent + " \u203a " + self;
        add(items, c.getString(R.string.settings_enable_swipe_action_title), null, bc,
                SwipeActionPreferenceFragment.class, R.string.settings_swipe_action_title);
        add(items, c.getString(R.string.settings_swipe_action_swipe_left_title), null, bc,
                SwipeActionPreferenceFragment.class, R.string.settings_swipe_action_title);
        add(items, c.getString(R.string.settings_swipe_action_swipe_right_title), null, bc,
                SwipeActionPreferenceFragment.class, R.string.settings_swipe_action_title);
        add(items, c.getString(R.string.settings_swipe_action_threshold), null, bc,
                SwipeActionPreferenceFragment.class, R.string.settings_swipe_action_title);
        add(items, c.getString(R.string.settings_swipe_action_haptic_feedback_title), null, bc,
                SwipeActionPreferenceFragment.class, R.string.settings_swipe_action_title);
        add(items, c.getString(R.string.settings_disable_swiping_between_tabs_title), null, bc,
                SwipeActionPreferenceFragment.class, R.string.settings_swipe_action_title);
    }

    // -------------------------------------------------------------------------
    // Security
    // -------------------------------------------------------------------------

    private void addSecurityPreferences(Context c, List<SettingsSearchItem> items) {
        String bc = c.getString(R.string.settings_security_title);
        add(items, c.getString(R.string.settings_require_authentication_to_show_accounts), null, bc,
                SecurityPreferenceFragment.class, R.string.settings_security_title);
        add(items, c.getString(R.string.settings_secure_mode_title),
                c.getString(R.string.settings_secure_mode_summary), bc,
                SecurityPreferenceFragment.class, R.string.settings_security_title);
        add(items, c.getString(R.string.settings_app_lock_title),
                c.getString(R.string.settings_app_lock_summary), bc,
                SecurityPreferenceFragment.class, R.string.settings_security_title);
        add(items, c.getString(R.string.settings_app_lock_timeout_title), null, bc,
                SecurityPreferenceFragment.class, R.string.settings_security_title);
    }

    // -------------------------------------------------------------------------
    // Data Saving Mode
    // -------------------------------------------------------------------------

    private void addDataSavingModePreferences(Context c, List<SettingsSearchItem> items) {
        String bc = c.getString(R.string.settings_data_saving_mode);
        add(items, c.getString(R.string.settings_data_saving_mode), null, bc,
                DataSavingModePreferenceFragment.class, R.string.settings_data_saving_mode);
        add(items, c.getString(R.string.settings_disable_image_preview_title), null, bc,
                DataSavingModePreferenceFragment.class, R.string.settings_data_saving_mode);
        add(items, c.getString(R.string.settings_only_disable_preview_in_video_and_gif_posts_title), null, bc,
                DataSavingModePreferenceFragment.class, R.string.settings_data_saving_mode);
        add(items, c.getString(R.string.settings_reddit_video_default_resolution), null, bc,
                DataSavingModePreferenceFragment.class, R.string.settings_data_saving_mode);
    }

    // -------------------------------------------------------------------------
    // Proxy
    // -------------------------------------------------------------------------

    private void addProxyPreferences(Context c, List<SettingsSearchItem> items) {
        String bc = c.getString(R.string.settings_proxy_title);
        add(items, c.getString(R.string.settings_proxy_enabled),
                c.getString(R.string.restart_app_see_changes), bc,
                ProxyPreferenceFragment.class, R.string.settings_proxy_title);
        add(items, c.getString(R.string.settings_proxy_type), null, bc,
                ProxyPreferenceFragment.class, R.string.settings_proxy_title);
        add(items, c.getString(R.string.settings_proxy_hostname), null, bc,
                ProxyPreferenceFragment.class, R.string.settings_proxy_title);
        add(items, c.getString(R.string.settings_proxy_port), null, bc,
                ProxyPreferenceFragment.class, R.string.settings_proxy_title);
    }

    // -------------------------------------------------------------------------
    // Sort Type
    // -------------------------------------------------------------------------

    private void addSortTypePreferences(Context c, List<SettingsSearchItem> items) {
        String bc = c.getString(R.string.settings_sort_type_title);
        add(items, c.getString(R.string.settings_save_sort_type_title), null, bc,
                SortTypePreferenceFragment.class, R.string.settings_sort_type_title);
        add(items, c.getString(R.string.settings_subreddit_default_sort_type_title), null, bc,
                SortTypePreferenceFragment.class, R.string.settings_sort_type_title);
        add(items, c.getString(R.string.settings_subreddit_default_sort_time_title), null, bc,
                SortTypePreferenceFragment.class, R.string.settings_sort_type_title);
        add(items, c.getString(R.string.settings_user_default_sort_type_title), null, bc,
                SortTypePreferenceFragment.class, R.string.settings_sort_type_title);
        add(items, c.getString(R.string.settings_user_default_sort_time_title), null, bc,
                SortTypePreferenceFragment.class, R.string.settings_sort_type_title);
        add(items, c.getString(R.string.settings_respect_subreddit_recommended_comment_sort_type_title),
                c.getString(R.string.settings_respect_subreddit_recommended_comment_sort_type_summary), bc,
                SortTypePreferenceFragment.class, R.string.settings_sort_type_title);
    }

    // -------------------------------------------------------------------------
    // Download Location
    // -------------------------------------------------------------------------

    private void addDownloadLocationPreferences(Context c, List<SettingsSearchItem> items) {
        String bc = c.getString(R.string.settings_download_location_title);
        add(items, c.getString(R.string.settings_image_download_location_title), null, bc,
                DownloadLocationPreferenceFragment.class, R.string.settings_download_location_title);
        add(items, c.getString(R.string.settings_gif_download_location_title), null, bc,
                DownloadLocationPreferenceFragment.class, R.string.settings_download_location_title);
        add(items, c.getString(R.string.settings_video_download_location_title), null, bc,
                DownloadLocationPreferenceFragment.class, R.string.settings_download_location_title);
        add(items, c.getString(R.string.settings_separate_folder_for_each_subreddit), null, bc,
                DownloadLocationPreferenceFragment.class, R.string.settings_download_location_title);
        add(items, c.getString(R.string.settings_save_nsfw_media_in_different_folder_title), null, bc,
                DownloadLocationPreferenceFragment.class, R.string.settings_download_location_title);
        add(items, c.getString(R.string.settings_nsfw_download_location_title), null, bc,
                DownloadLocationPreferenceFragment.class, R.string.settings_download_location_title);
    }

    // -------------------------------------------------------------------------
    // Miscellaneous
    // -------------------------------------------------------------------------

    private void addMiscellaneousPreferences(Context c, List<SettingsSearchItem> items) {
        String bc = c.getString(R.string.settings_miscellaneous_title);
        add(items, c.getString(R.string.settings_save_front_page_scrolled_position_title),
                c.getString(R.string.settings_save_front_page_scrolled_position_summary), bc,
                MiscellaneousPreferenceFragment.class, R.string.settings_miscellaneous_title);
        add(items, c.getString(R.string.settings_link_handler_title), null, bc,
                MiscellaneousPreferenceFragment.class, R.string.settings_miscellaneous_title);
        add(items, c.getString(R.string.settings_main_page_back_button_action), null, bc,
                MiscellaneousPreferenceFragment.class, R.string.settings_miscellaneous_title);
        add(items, c.getString(R.string.settings_enable_search_history_title),
                c.getString(R.string.only_for_logged_in_user), bc,
                MiscellaneousPreferenceFragment.class, R.string.settings_miscellaneous_title);
        add(items, c.getString(R.string.settings_disable_profile_avatar_animation_title), null, bc,
                MiscellaneousPreferenceFragment.class, R.string.settings_miscellaneous_title);
        add(items, c.getString(R.string.settings_language_title), null, bc,
                MiscellaneousPreferenceFragment.class, R.string.settings_miscellaneous_title);
        add(items, c.getString(R.string.settings_enable_fold_support_title), null, bc,
                MiscellaneousPreferenceFragment.class, R.string.settings_miscellaneous_title);
        add(items, c.getString(R.string.settings_post_feed_max_resolution_title), null, bc,
                MiscellaneousPreferenceFragment.class, R.string.settings_miscellaneous_title);
    }

    // -------------------------------------------------------------------------
    // Advanced
    // -------------------------------------------------------------------------

    private void addAdvancedPreferences(Context c, List<SettingsSearchItem> items) {
        String bc = c.getString(R.string.settings_advanced_master_title);
        add(items, c.getString(R.string.settings_delete_all_subreddits_data_in_database_title), null, bc,
                AdvancedPreferenceFragment.class, R.string.settings_advanced_master_title);
        add(items, c.getString(R.string.settings_delete_all_users_data_in_database_title), null, bc,
                AdvancedPreferenceFragment.class, R.string.settings_advanced_master_title);
        add(items, c.getString(R.string.settings_delete_all_sort_type_data_in_database_title), null, bc,
                AdvancedPreferenceFragment.class, R.string.settings_advanced_master_title);
        add(items, c.getString(R.string.settings_delete_all_post_layout_data_in_database_title), null, bc,
                AdvancedPreferenceFragment.class, R.string.settings_advanced_master_title);
        add(items, c.getString(R.string.settings_delete_all_themes_in_database_title), null, bc,
                AdvancedPreferenceFragment.class, R.string.settings_advanced_master_title);
        add(items, c.getString(R.string.settings_delete_front_page_scrolled_positions_in_database_title), null, bc,
                AdvancedPreferenceFragment.class, R.string.settings_advanced_master_title);
        add(items, c.getString(R.string.settings_delete_read_posts_in_database_title), null, bc,
                AdvancedPreferenceFragment.class, R.string.settings_advanced_master_title);
        add(items, c.getString(R.string.settings_delete_all_legacy_settings_title), null, bc,
                AdvancedPreferenceFragment.class, R.string.settings_advanced_master_title);
        add(items, c.getString(R.string.settings_reset_all_settings_title), null, bc,
                AdvancedPreferenceFragment.class, R.string.settings_advanced_master_title);
        add(items, c.getString(R.string.settings_backup_settings_title),
                c.getString(R.string.settings_backup_settings_summary), bc,
                AdvancedPreferenceFragment.class, R.string.settings_advanced_master_title);
        add(items, c.getString(R.string.settings_restore_settings_title), null, bc,
                AdvancedPreferenceFragment.class, R.string.settings_advanced_master_title);
        add(items, c.getString(R.string.settings_crash_reports_title), null, bc,
                CrashReportsFragment.class, R.string.settings_crash_reports_title);
    }

    // -------------------------------------------------------------------------
    // About
    // -------------------------------------------------------------------------

    private void addAboutPreferences(Context c, List<SettingsSearchItem> items) {
        String bc = c.getString(R.string.settings_about_master_title);
        add(items, c.getString(R.string.settings_acknowledgement_master_title), null, bc,
                AcknowledgementFragment.class, R.string.settings_acknowledgement_master_title);
        add(items, c.getString(R.string.settings_credits_master_title), null, bc,
                CreditsPreferenceFragment.class, R.string.settings_credits_master_title);
        add(items, c.getString(R.string.settings_translation_title),
                c.getString(R.string.settings_translation_summary), bc,
                TranslationFragment.class, R.string.settings_translation_title);
        add(items, c.getString(R.string.settings_open_source_title),
                c.getString(R.string.settings_open_source_summary), bc,
                AboutPreferenceFragment.class, R.string.settings_about_master_title);
        add(items, c.getString(R.string.settings_email_title),
                c.getString(R.string.settings_email_summary), bc,
                AboutPreferenceFragment.class, R.string.settings_about_master_title);
        add(items, c.getString(R.string.settings_reddit_account_title),
                c.getString(R.string.settings_reddit_account_summary), bc,
                AboutPreferenceFragment.class, R.string.settings_about_master_title);
        add(items, c.getString(R.string.settings_subreddit_title),
                c.getString(R.string.settings_subreddit_summary), bc,
                AboutPreferenceFragment.class, R.string.settings_about_master_title);
        add(items, c.getString(R.string.settings_share_title),
                c.getString(R.string.settings_share_summary), bc,
                AboutPreferenceFragment.class, R.string.settings_about_master_title);
        add(items, c.getString(R.string.settings_version_title), null, bc,
                AboutPreferenceFragment.class, R.string.settings_about_master_title);
    }

    // -------------------------------------------------------------------------
    // Credits
    // -------------------------------------------------------------------------

    private void addCreditsPreferences(Context c, List<SettingsSearchItem> items) {
        String parent = c.getString(R.string.settings_about_master_title);
        String self = c.getString(R.string.settings_credits_master_title);
        String bc = parent + " \u203a " + self;
        add(items, c.getString(R.string.settings_credits_icon_foreground_title),
                c.getString(R.string.settings_credits_icon_foreground_summary), bc,
                CreditsPreferenceFragment.class, R.string.settings_credits_master_title);
        add(items, c.getString(R.string.settings_credits_icon_background_title),
                c.getString(R.string.settings_credits_icon_background_summary), bc,
                CreditsPreferenceFragment.class, R.string.settings_credits_master_title);
        add(items, c.getString(R.string.settings_credits_error_image_title),
                c.getString(R.string.settings_credits_error_image_summary), bc,
                CreditsPreferenceFragment.class, R.string.settings_credits_master_title);
        add(items, c.getString(R.string.settings_credits_crosspost_icon_title),
                c.getString(R.string.settings_credits_crosspost_icon_summary), bc,
                CreditsPreferenceFragment.class, R.string.settings_credits_master_title);
        add(items, c.getString(R.string.settings_credits_thumbtack_icon_title),
                c.getString(R.string.settings_credits_thumbtack_icon_summary), bc,
                CreditsPreferenceFragment.class, R.string.settings_credits_master_title);
        add(items, c.getString(R.string.settings_credits_best_rocket_icon_title),
                c.getString(R.string.settings_credits_best_rocket_icon_summary), bc,
                CreditsPreferenceFragment.class, R.string.settings_credits_master_title);
        add(items, c.getString(R.string.settings_credits_material_icons_title), null, bc,
                CreditsPreferenceFragment.class, R.string.settings_credits_master_title);
        add(items, c.getString(R.string.settings_credits_national_flags), null, bc,
                CreditsPreferenceFragment.class, R.string.settings_credits_master_title);
        add(items, c.getString(R.string.settings_credits_ufo_capturing_animation_title), null, bc,
                CreditsPreferenceFragment.class, R.string.settings_credits_master_title);
        add(items, c.getString(R.string.settings_credits_love_animation_title), null, bc,
                CreditsPreferenceFragment.class, R.string.settings_credits_master_title);
        add(items, c.getString(R.string.settings_credits_lock_screen_animation_title), null, bc,
                CreditsPreferenceFragment.class, R.string.settings_credits_master_title);
    }

    // -------------------------------------------------------------------------
    // Debug
    // -------------------------------------------------------------------------

    private void addDebugPreferences(Context c, List<SettingsSearchItem> items) {
        String bc = c.getString(R.string.settings_debug_title);
        add(items, c.getString(R.string.settings_screen_width_dp_title),
                c.getString(R.string.settings_screen_width_dp_summary), bc,
                DebugPreferenceFragment.class, R.string.settings_debug_title);
        add(items, c.getString(R.string.settings_smallest_screen_width_dp_title),
                c.getString(R.string.settings_smallest_screen_width_dp_summary), bc,
                DebugPreferenceFragment.class, R.string.settings_debug_title);
        add(items, c.getString(R.string.settings_is_tablet_title),
                c.getString(R.string.settings_is_tablet_summary_false), bc,
                DebugPreferenceFragment.class, R.string.settings_debug_title);
    }
}
