package ml.docilealligator.infinityforreddit.account

import ml.docilealligator.infinityforreddit.utils.SharedPreferencesUtils

/**
 * Which settings belong to an account rather than to the app.
 *
 * The classification is static: a setting is per-account or it is not, and the Settings screens are
 * grouped to match. That is what lets [AccountScopedSharedPreferences] be a plain key router with no
 * fallback layer, and what makes "reset this account" and "copy from another account" mean something
 * exact.
 *
 * Two whole files are per-account outright, because every key in them is one feed's appearance or
 * one feed's sort order. The default preferences file mixes both, so it is listed key by key.
 */
object AccountScopedKeys {

    /**
     * Identifier for the preferences `PreferenceManager.getDefaultSharedPreferences` returns. Not a
     * file name: [SharedPreferencesUtils.DEFAULT_PREFERENCES_FILE] names the legacy file only the
     * API keys screen uses, and opening that one by name silently reads nothing.
     */
    const val DEFAULT_PREFERENCES = "default_preferences"

    /**
     * Files where every key belongs to an account: one feed's layout, one feed's sort order, one
     * account's drawer, one account's post-detail arrangement, one account's bottom bar.
     */
    private val WHOLE_FILE_SCOPED = setOf(
        SharedPreferencesUtils.POST_LAYOUT_SHARED_PREFERENCES_FILE,
        SharedPreferencesUtils.SORT_TYPE_SHARED_PREFERENCES_FILE,
        SharedPreferencesUtils.NAVIGATION_DRAWER_SHARED_PREFERENCES_FILE,
        SharedPreferencesUtils.POST_DETAILS_SHARED_PREFERENCES_FILE,
        SharedPreferencesUtils.BOTTOM_APP_BAR_SHARED_PREFERENCES_FILE,
    )

    /**
     * Keys in the default preferences file that belong to an account, grouped by the screen they
     * appear on so this list can be read against the XML it mirrors. Written as the stored key
     * rather than the constant name because that is what the preference screens declare;
     * `AccountScopedKeysTest` fails if any of them stops being a real key.
     *
     * Everything absent from this set stays global, so a key added upstream is global until someone
     * decides otherwise — the safe direction to be wrong in.
     */
    private val DEFAULT_FILE_SCOPED = setOf(
        // Post
        "default_post_layout", "default_post_layout_unfolded", "default_link_post_layout",
        "hide_post_type", "post_type_triangle_indicator", "hide_post_type_indicator",
        "hide_image_count_in_gallery", "hide_post_flair", "hide_subreddit_and_user_prefix",
        "hide_the_number_of_votes", "hide_the_number_of_comments", "hide_text_post_content",
        "fixed_height_preview_in_card", "show_gallery_media_as_grid",
        "show_divider_in_compact_layout", "show_thumbnail_on_the_left_in_compact_layout",
        "long_press_to_hide_toolbar_in_compact_layout",
        "post_compact_layout_toolbar_hidden_by_default",
        "click_to_show_media_in_gallery_layout", "media_only_posts_in_gallery_layout",

        // Number of columns in the post feed
        "number_of_columns_in_post_feed_portrait", "number_of_columns_in_post_feed_landscape",
        "number_of_columns_in_post_feed_portrait_unfolded",
        "number_of_columns_in_post_feed_landscape_unfolded",
        "number_of_columns_in_post_feed_portrait_card_layout_2",
        "number_of_columns_in_post_feed_landscape_card_layout_2",
        "number_of_columns_in_post_feed_portrait_compact_layout",
        "number_of_columns_in_post_feed_landscape_compact_layout",
        "number_of_columns_in_post_feed_portrait_gallery_layout",
        "number_of_columns_in_post_feed_landscape_gallery_layout",

        // Interface. The bottom app bar toggle belongs with the bar itself, which is a whole
        // per-account file: without it here an account could arrange its own bar and then have
        // another account switch the bar off for everyone.
        "bottom_app_bar",
        "hide_fab_in_post_feed", "hide_subreddit_description", "default_search_result_tab",
        "lazy_mode_interval", "vote_buttons_on_the_right", "show_absolute_number_of_votes",
        "show_post_and_comment_toolbar_items_based_on_space", "force_max_refresh_rate",

        // Immersive interface, and time format
        "immersive_interface", "disable_immersive_interface_in_landscape_mode",
        "show_elapsed_time", "time_format",

        // Fonts
        "font_family", "custom_font_family", "font_size",
        "title_font_family", "custom_title_font_family", "title_font_size",
        "content_font_family", "custom_content_font_family", "content_font_size",

        // Comments
        "show_top_level_comments_first", "show_comment_divider", "show_comment_top_padding",
        "comment_divider_type", "show_only_one_comment_level_indicator", "comment_toolbar_hidden",
        "comment_toolbar_hide_on_click", "fully_collapse_comment", "show_author_avatar",
        "show_user_prefix", "hide_the_number_of_votes_in_comments",
        "show_fewer_toolbar_options_threshold", "embedded_media_type",

        // Video
        "mute_video", "mute_nsfw_video", "video_player_ignore_nav_bar",
        "video_player_automatic_landscape_orientation", "loop_video", "default_playback_speed",
        "reddit_video_default_resolution_no_data_saving", "video_autoplay",
        "simultaneous_autoplay_limit", "legacy_autoplay_video_controller_ui",
        "mute_autoplaying_videos", "remember_muting_option_in_post_feed", "autoplay_nsfw_videos",
        "autoplay_comment_gif", "easier_to_watch_in_full_screen",
        "start_autoplay_visible_area_offset_portrait",
        "start_autoplay_visible_area_offset_landscape",

        // Gestures and buttons
        "swipe_to_go_back_from_post_detail", "lock_toolbar", "lock_bottom_app_bar",
        "volume_keys_navigate_comments", "volume_keys_navigate_posts", "pull_to_refresh",
        "swipe_between_posts", "tab_switching_sensitivity", "swipe_right_to_go_back_sensitivity",
        "swipe_action_sensitivity_in_comments", "navigation_drawer_swipe_area",
        "swipe_vertically_to_go_back_from_media", "lock_jump_to_next_top_level_comment_button",
        "swipe_up_to_hide_jump_to_next_top_level_comments_button", "swap_tap_and_long_in_comments",
        "long_press_post_non_media_area", "long_press_post_media",
        "enable_swipe_action", "swipe_left_action", "swipe_right_action",
        "vibrate_when_action_triggered", "disable_swiping_between_tabs", "swipe_action_threshold",

        // Sort type. The defaults a feed falls back to, alongside the sort_type file that
        // remembers each feed's own — one account's reading habits are not another's.
        "save_post_sort", "subreddit_default_sort_type", "subreddit_default_sort_time",
        "user_default_sort_type", "user_default_sort_time",
        "save_comment_sort", "comment_default_sort_type",
        "respect_subreddit_recommended_comment_sort_type",

        // Theme. The choice only: the theme library itself stays global.
        "theme", "amoled_dark", "enable_material_you", "apply_material_you",

        // Miscellaneous. The link handler and browser pickers are absent on purpose: they are
        // already per-account through AccountScope keys of their own.
        "language", "use_old_reddit_domain", "main_page_back_button_action",
        "save_front_page_scrolled_position", "enable_search_history",
        "disable_profile_avatar_animation",
    )

    /**
     * Whether every key in the file named [fileName] belongs to an account, so that a screen on it
     * must be given a scoped instance rather than reaching the file by name.
     */
    @JvmStatic
    fun isWholeFileScoped(fileName: String): Boolean = fileName in WHOLE_FILE_SCOPED

    /** Whether [key] in the preferences file named [fileName] belongs to an account. */
    @JvmStatic
    fun isScoped(fileName: String, key: String): Boolean = when (fileName) {
        in WHOLE_FILE_SCOPED -> true
        DEFAULT_PREFERENCES -> key in DEFAULT_FILE_SCOPED
        else -> false
    }

    /**
     * The default file's per-account keys. Phase-by-phase this is also what a per-account reset
     * clears and what a copy between accounts carries.
     */
    @JvmStatic
    fun scopedDefaultKeys(): Set<String> = DEFAULT_FILE_SCOPED

    /** Every key this file scopes, for the migration that seeds each account's first copy. */
    @JvmStatic
    fun scopedKeysIn(fileName: String, presentKeys: Set<String>): Set<String> = when (fileName) {
        in WHOLE_FILE_SCOPED -> presentKeys
        DEFAULT_PREFERENCES -> presentKeys intersect DEFAULT_FILE_SCOPED
        else -> emptySet()
    }
}
