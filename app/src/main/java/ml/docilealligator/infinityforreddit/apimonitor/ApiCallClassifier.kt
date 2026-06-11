package ml.docilealligator.infinityforreddit.apimonitor

import okhttp3.HttpUrl

/**
 * Maps a request URL to one of the official Reddit API sections (see https://www.reddit.com/dev/api/)
 * and a normalized endpoint template, collapsing dynamic path segments (subreddit names, usernames,
 * thing IDs, query strings) so that calls to the same logical endpoint aggregate together.
 *
 * Non-Reddit hosts (imgur, redgifs, streamable, ...) are bucketed by host with their first path
 * segment as the endpoint, so they can be filtered out or inspected separately.
 */
object ApiCallClassifier {

    const val SECTION_ACCOUNT = "account"
    const val SECTION_ANNOUNCEMENTS = "announcements"
    const val SECTION_CAPTCHA = "captcha"
    const val SECTION_EMOJI = "emoji"
    const val SECTION_FLAIR = "flair"
    const val SECTION_LINKS = "links & comments"
    const val SECTION_LISTINGS = "listings"
    const val SECTION_LIVE = "live threads"
    const val SECTION_MESSAGES = "private messages"
    const val SECTION_MISC = "misc"
    const val SECTION_MODERATION = "moderation"
    const val SECTION_MODMAIL = "modmail"
    const val SECTION_MODNOTE = "mod notes"
    const val SECTION_MULTIS = "multis"
    const val SECTION_SEARCH = "search"
    const val SECTION_SUBREDDITS = "subreddits"
    const val SECTION_USERS = "users"
    const val SECTION_WIDGETS = "widgets"
    const val SECTION_WIKI = "wiki"
    const val SECTION_OTHER = "other"

    // Media retrieval (content viewing, not the API itself).
    const val SECTION_MEDIA_IMAGE = "media: images"
    const val SECTION_MEDIA_VIDEO = "media: video"
    const val SECTION_MEDIA_OTHER = "media: other"
    const val SECTION_THIRD_PARTY_API = "third-party api"

    private val VIDEO_EXT = setOf("mp4", "m3u8", "mpd", "ts", "webm", "mkv", "mov")
    private val IMAGE_EXT = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif", "avif")

    data class Classification(val section: String, val endpoint: String)

    private val SORTS = setOf("best", "hot", "new", "rising", "top", "controversial")

    // Listing locations under /user/{name}/{where}
    private val USER_WHERE = setOf(
        "overview", "submitted", "comments", "upvoted", "downvoted",
        "hidden", "saved", "gilded"
    )

    // /r/{sub}/about/{location} that belong to the moderation section
    private val MOD_ABOUT = setOf(
        "log", "modqueue", "reports", "spam", "edited", "unmoderated"
    )

    // /api/{action} -> section
    private val API_ACTION_SECTION: Map<String, String> = buildMap {
        // links & comments
        for (a in listOf(
            "comment", "del", "editusertext", "info", "morechildren", "vote", "save",
            "unsave", "hide", "unhide", "lock", "unlock", "marknsfw", "unmarknsfw",
            "spoiler", "unspoiler", "submit", "sendreplies", "report", "follow_post",
            "set_contest_mode", "set_subreddit_sticky", "set_suggested_sort",
            "saved_categories", "store_visits", "favorite", "media"
        )) put(a, SECTION_LINKS)
        // subreddits
        for (a in listOf(
            "subscribe", "subreddit_autocomplete", "subreddit_autocomplete_v2",
            "search_subreddits", "search_reddit_names", "site_admin", "submit_text",
            "subreddit_stylesheet", "upload_sr_img", "delete_sr_banner",
            "delete_sr_header", "delete_sr_icon", "delete_sr_img", "recommend",
            "crosspostable_subreddits", "quarantine_option", "quarantine_optin",
            "quarantine_optout"
        )) put(a, SECTION_SUBREDDITS)
        // search
        put("trending_searches_v1", SECTION_SEARCH)
        // users
        for (a in listOf(
            "friend", "unfriend", "block_user", "report_user", "username_available",
            "user_data_by_account_ids", "setpermissions"
        )) put(a, SECTION_USERS)
        // private messages
        for (a in listOf(
            "compose", "read_message", "read_all_messages", "unread_message",
            "del_msg", "block", "collapse_message", "uncollapse_message"
        )) put(a, SECTION_MESSAGES)
        // multis
        for (a in listOf("multi", "filter")) put(a, SECTION_MULTIS)
        // flair
        for (a in listOf(
            "flair", "flairconfig", "flaircsv", "flairlist", "flairselector",
            "flairtemplate", "flairtemplate_v2", "flair_template_order",
            "clearflairtemplates", "deleteflair", "deleteflairtemplate", "selectflair",
            "setflairenabled", "link_flair", "link_flair_v2", "user_flair", "user_flair_v2"
        )) put(a, SECTION_FLAIR)
        // moderation
        for (a in listOf(
            "approve", "remove", "distinguish", "ignore_reports", "unignore_reports",
            "accept_moderator_invite", "leavecontributor", "leavemoderator",
            "mute_message_author", "unmute_message_author", "snooze_reports",
            "unsnooze_reports", "update_crowd_control_level", "show_comment"
        )) put(a, SECTION_MODERATION)
        // widgets
        for (a in listOf("widget", "widgets", "widget_image_upload_s3", "widget_order")) {
            put(a, SECTION_WIDGETS)
        }
        // announcements / captcha / live
        put("announcements", SECTION_ANNOUNCEMENTS)
        put("needs_captcha", SECTION_CAPTCHA)
        put("live", SECTION_LIVE)
    }

    fun isRedditApiHost(host: String): Boolean =
        host == "oauth.reddit.com" || host == "www.reddit.com" || host.endsWith(".reddit.com")

    fun classify(url: HttpUrl): Classification {
        val host = url.host
        if (!isRedditApiHost(host)) {
            return classifyMedia(url)
        }

        val s = url.pathSegments
            .filter { it.isNotEmpty() }
            .map { it.removeSuffix(".json") }

        if (s.isEmpty()) return Classification(SECTION_LISTINGS, "/ (front page)")

        return when (val head = s[0]) {
            in SORTS -> Classification(SECTION_LISTINGS, "/{sort}")
            "r" -> classifyR(s)
            "user", "u" -> classifyUser(s)
            "api" -> classifyApi(s)
            "comments" -> Classification(SECTION_LINKS, "/comments/{id}")
            "duplicates" -> Classification(SECTION_LISTINGS, "/duplicates/{id}")
            "by_id" -> Classification(SECTION_LISTINGS, "/by_id/{names}")
            "message" -> Classification(SECTION_MESSAGES, "/message/${s.getOrElse(1) { "{where}" }}")
            "prefs" -> Classification(SECTION_ACCOUNT, "/prefs/${s.getOrElse(1) { "{where}" }}")
            "subreddits" -> Classification(SECTION_SUBREDDITS, "/subreddits/${s.getOrElse(1) { "{where}" }}")
            "users" -> Classification(SECTION_USERS, "/users/${s.getOrElse(1) { "{where}" }}")
            "search" -> Classification(SECTION_SEARCH, "/search")
            "sidebar" -> Classification(SECTION_SUBREDDITS, "/sidebar")
            "sticky" -> Classification(SECTION_LINKS, "/sticky")
            "wiki" -> Classification(SECTION_WIKI, "/wiki/{page}")
            "stylesheet" -> Classification(SECTION_MODERATION, "/stylesheet")
            "live" -> Classification(SECTION_LIVE, "/live/${s.getOrElse(1) { "{thread}" }}")
            else -> Classification(SECTION_OTHER, "/$head")
        }
    }

    /**
     * Classifies media retrieval and third-party hosts (everything that is not a Reddit API host).
     * The endpoint is the host, so each CDN aggregates into one row and slow hosts stand out.
     */
    private fun classifyMedia(url: HttpUrl): Classification {
        val host = url.host
        val lastSegment = url.pathSegments.lastOrNull { it.isNotEmpty() }.orEmpty().lowercase()
        val ext = lastSegment.substringAfterLast('.', "")
        return when {
            host == "v.redd.it" -> Classification(SECTION_MEDIA_VIDEO, host)
            host == "i.redd.it" -> Classification(SECTION_MEDIA_IMAGE, host)
            host.endsWith("preview.redd.it") -> Classification(SECTION_MEDIA_IMAGE, host)
            host.endsWith("redditmedia.com") -> Classification(SECTION_MEDIA_IMAGE, host)
            host.endsWith("redditstatic.com") -> Classification(SECTION_MEDIA_OTHER, host)
            host.startsWith("api.") -> Classification(SECTION_THIRD_PARTY_API, host)
            ext in VIDEO_EXT -> Classification(SECTION_MEDIA_VIDEO, host)
            ext in IMAGE_EXT -> Classification(SECTION_MEDIA_IMAGE, host)
            else -> Classification(SECTION_MEDIA_OTHER, host)
        }
    }

    private fun classifyR(s: List<String>): Classification {
        // s = [r, {sub}, rest...]
        val base = "/r/{sub}"
        if (s.size <= 2) return Classification(SECTION_LISTINGS, base)
        val rest = s.subList(2, s.size)
        return when (val next = rest[0]) {
            in SORTS -> Classification(SECTION_LISTINGS, "$base/{sort}")
            "comments" -> Classification(SECTION_LINKS, "$base/comments/{id}")
            "search" -> Classification(SECTION_SEARCH, "$base/search")
            "wiki" -> Classification(SECTION_WIKI, "$base/wiki/{page}")
            "sidebar" -> Classification(SECTION_SUBREDDITS, "$base/sidebar")
            "sticky" -> Classification(SECTION_LINKS, "$base/sticky")
            "api" -> classifyApi(rest.subList(1, rest.size).map { it }, "$base/api")
            "about" -> {
                val loc = rest.getOrNull(1)
                when {
                    loc == null -> Classification(SECTION_SUBREDDITS, "$base/about")
                    loc == "edit" || loc == "rules" || loc == "traffic" ->
                        Classification(SECTION_SUBREDDITS, "$base/about/$loc")
                    loc in MOD_ABOUT -> Classification(SECTION_MODERATION, "$base/about/$loc")
                    else -> Classification(SECTION_SUBREDDITS, "$base/about/$loc")
                }
            }
            else -> Classification(SECTION_LISTINGS, "$base/$next")
        }
    }

    private fun classifyUser(s: List<String>): Classification {
        // s = [user|u, {name}, rest...]
        val base = "/user/{username}"
        if (s.size <= 2) return Classification(SECTION_USERS, "$base/about")
        val rest = s.subList(2, s.size)
        return when (val next = rest[0]) {
            "about" -> Classification(SECTION_USERS, "$base/about")
            "m" -> Classification(SECTION_MULTIS, "$base/m/{multi}")
            "f" -> Classification(SECTION_MULTIS, "$base/f/{filter}")
            in USER_WHERE -> Classification(SECTION_USERS, "$base/$next")
            else -> Classification(SECTION_USERS, "$base/$next")
        }
    }

    private fun classifyApi(s: List<String>, prefix: String = "/api"): Classification {
        // s = [api, ...]  OR (when prefix overridden) [v1?, action, ...] already stripped of "api"
        val parts = if (s.isNotEmpty() && s[0] == "api") s.subList(1, s.size) else s
        if (parts.isEmpty()) return Classification(SECTION_MISC, prefix)

        if (parts[0] == "v1") return classifyApiV1(parts, prefix)

        val action = parts[0]
        // mod conversations / notes live under /api/mod/...
        if (action == "mod") {
            return when (parts.getOrNull(1)) {
                "conversations" -> Classification(SECTION_MODMAIL, "$prefix/mod/conversations")
                "notes" -> Classification(SECTION_MODNOTE, "$prefix/mod/notes")
                "bulk_read" -> Classification(SECTION_MODMAIL, "$prefix/mod/bulk_read")
                else -> Classification(SECTION_MODERATION, "$prefix/mod/${parts.getOrElse(1) { "" }}".trimEnd('/'))
            }
        }
        if (action == "multi" || action == "filter") {
            return Classification(SECTION_MULTIS, "$prefix/$action/...")
        }

        val section = API_ACTION_SECTION[action] ?: SECTION_MISC
        return Classification(section, "$prefix/$action")
    }

    private fun classifyApiV1(parts: List<String>, prefix: String): Classification {
        // parts = [v1, x, ...]
        return when (val x = parts.getOrNull(1)) {
            "me" -> {
                val sub = parts.getOrNull(2)
                Classification(SECTION_ACCOUNT, if (sub == null) "$prefix/v1/me" else "$prefix/v1/me/$sub")
            }
            "scopes" -> Classification(SECTION_ACCOUNT, "$prefix/v1/scopes")
            null -> Classification(SECTION_MISC, "$prefix/v1")
            else -> {
                // /api/v1/{sub}/emoji... patterns
                if (parts.any { it == "emoji" }) {
                    Classification(SECTION_EMOJI, "$prefix/v1/{sub}/emoji")
                } else {
                    Classification(SECTION_MISC, "$prefix/v1/$x")
                }
            }
        }
    }
}
