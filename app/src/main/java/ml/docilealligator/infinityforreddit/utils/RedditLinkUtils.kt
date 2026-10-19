package ml.docilealligator.infinityforreddit.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.core.net.toUri
import ml.docilealligator.infinityforreddit.Infinity

/**
 * Helpers for the Reddit links the app reads in and hands out.
 *
 * Settings > Miscellaneous > Domain for shared links picks the host Reddit links leave the app on.
 * The default, www.reddit.com, leaves every link as it is. The other choices are of two kinds:
 *
 * - A domain of Reddit's own (old.reddit.com, or any reddit.com host typed in as a custom domain)
 *   serves every path www.reddit.com does. [applyShareDomain] and [applyBrowserDomain] both use
 *   it, for any link.
 * - Anything else is taken for an embed fixer (fxreddit, vxReddit, EmbedEZ, …): a site that
 *   serves chat apps a playable preview of a post. Those only understand posts and comments —
 *   vxReddit answers a subreddit or a user link with an "Invalid post path" page — so
 *   [applyShareDomain] uses one only for a `/comments/` link and leaves any other link on Reddit.
 *   [applyBrowserDomain] never uses one: a browser tap gains nothing from the preview, only an
 *   extra hop through someone else's server, and for EmbedEZ a download page instead of Reddit.
 *
 * Only hosts that serve the same paths as www.reddit.com are rewritten, so media (i.redd.it,
 * v.redd.it), share shorteners (s.reddit.com, redd.it, reddit.app.link) and non-Reddit links pass
 * through untouched. Permalinks stay canonical www.reddit.com in memory and API calls are
 * unaffected; only the outbound string changes, so the preference applies without a restart.
 */
object RedditLinkUtils {
    const val WWW_REDDIT_HOST = "www.reddit.com"
    const val OLD_REDDIT_HOST = "old.reddit.com"

    /** The Domain for shared links value that defers to the custom domain typed in beside it. */
    const val CUSTOM_SHARED_LINK_DOMAIN = "custom"

    private val REWRITABLE_HOSTS = setOf(WWW_REDDIT_HOST, "reddit.com")

    /** What may come before `/comments/<id>` in a post or comment link: `/r/<sub>`, `/u/<name>`, `/user/<name>`. */
    private val POST_OWNER_SEGMENTS = setOf("r", "u", "user")

    /**
     * A hostname with at least two labels, each of letters, digits and inner hyphens, after
     * [normalizeDomain] has lowercased it. No port: none of the embed fixers needs one, and a
     * shared link carrying one is far more likely a typo than a choice.
     */
    private val HOSTNAME = Regex("^(?=.{1,253}$)[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?)+$")

    /** The domains [ml.docilealligator.infinityforreddit.activities.LinkResolverActivity] opens itself. */
    private val REDDIT_DOMAINS = setOf("reddit.com", "redd.it", "reddit.app.link")

    /** RFC 3986 scheme, i.e. what tells "mailto:a@reddit.com" from the host-first "reddit.com/r/x". */
    private val SCHEME_PREFIX = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:")

    /**
     * The Reddit link in [text] as an absolute URL, or null when [text] is not one.
     *
     * Matched on the host rather than on a substring, so a look-alike domain
     * (reddit.com.example.net, www.reddit.example.com) is not taken for Reddit's. A link copied
     * without its scheme (reddit.com/r/pics) is still a Reddit link, so it comes back with
     * https:// in front: the link resolver reads a scheme-less string as a path under
     * www.reddit.com and would otherwise double up the host. Scheme and host come back
     * lowercased, while the path and query keep the case they were copied with.
     */
    @JvmStatic
    fun redditLinkOrNull(text: String?): String? {
        val trimmed = text?.trim()
        if (trimmed.isNullOrEmpty()) {
            return null
        }

        val url = if (SCHEME_PREFIX.containsMatchIn(trimmed)) trimmed else "https://$trimmed"
        val uri = url.toUri()
        val scheme = uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") {
            return null
        }

        val host = uri.host?.lowercase() ?: return null
        if (REDDIT_DOMAINS.none { host == it || host.endsWith(".$it") }) {
            return null
        }
        if (scheme == uri.scheme && host == uri.host) {
            return url
        }

        // Scheme and host are case-insensitive (RFC 3986 3.1 and 3.2.2), but LinkResolverActivity
        // matches the authority verbatim, so an as-copied "Reddit.com/r/pics" would miss every
        // in-app branch and open in a browser instead. Rebuilt from the parsed parts rather than
        // lowercased whole, to leave the path, query and any userinfo exactly as they were.
        val authority = buildString {
            uri.encodedUserInfo?.let { append(it).append('@') }
            append(host)
            if (uri.port != -1) {
                append(':').append(uri.port)
            }
        }
        return uri.buildUpon().scheme(scheme).encodedAuthority(authority).build().toString()
    }

    /**
     * The Domain for shared links value: a host, or [CUSTOM_SHARED_LINK_DOMAIN]. The Use
     * old.reddit.com switch it replaced is turned into this value at start-up, by
     * [SharedLinkDomainMigration].
     */
    @JvmStatic
    fun sharedLinkDomainChoice(sharedPreferences: SharedPreferences): String =
        sharedPreferences.getString(SharedPreferencesUtils.SHARED_LINK_DOMAIN, null)
            ?.takeIf { it.isNotEmpty() }
            ?: WWW_REDDIT_HOST

    /**
     * [input] as a bare lowercase hostname, or null when it is not one. Takes what someone is
     * likely to paste — `https://fxreddit.seria.moe/` or a whole example link — and keeps only the
     * host, so the setting holds exactly what links will go out on.
     */
    @JvmStatic
    fun normalizeDomain(input: String?): String? {
        var domain = input?.trim()?.lowercase()
        if (domain.isNullOrEmpty()) {
            return null
        }
        domain = domain.substringAfter("://")
        domain = domain.substringBefore('/').substringBefore('?').substringBefore('#')
        return domain.takeIf { HOSTNAME.matches(it) }
    }

    /** The host links go out on, or null to leave them on www.reddit.com. */
    private fun sharedLinkHost(sharedPreferences: SharedPreferences): String? {
        val choice = sharedLinkDomainChoice(sharedPreferences)
        val host = if (choice == CUSTOM_SHARED_LINK_DOMAIN) {
            normalizeDomain(sharedPreferences.getString(SharedPreferencesUtils.SHARED_LINK_CUSTOM_DOMAIN, null))
        } else {
            normalizeDomain(choice)
        }
        return host?.takeUnless { it in REWRITABLE_HOSTS }
    }

    /** Whether [host] is one of Reddit's own, which serves every path www.reddit.com does. */
    private fun isRedditHost(host: String): Boolean = host == "reddit.com" || host.endsWith(".reddit.com")

    /**
     * For callers holding only a Context. Resolves the preferences through the application graph:
     * `PreferenceManager.getDefaultSharedPreferences` would hand back the unscoped file and quietly
     * ignore whichever account is signed in.
     */
    @JvmStatic
    fun applyShareDomain(context: Context, url: String): String {
        val application = context.applicationContext
        if (application !is Infinity) {
            return url
        }
        return applyShareDomain(application.appComponent.defaultSharedPreferences(), url)
    }

    /** [url] as it should be shared or copied, for someone else to open. */
    @JvmStatic
    fun applyShareDomain(sharedPreferences: SharedPreferences, url: String): String {
        val host = sharedLinkHost(sharedPreferences) ?: return url
        return rewriteHost(url, host, postsOnly = !isRedditHost(host))
    }

    /** [applyShareDomain] for the nullable permalink on [ml.docilealligator.infinityforreddit.comment.Comment]. */
    @JvmStatic
    fun applyShareDomainOrNull(sharedPreferences: SharedPreferences, url: String?): String? =
        if (url == null) null else applyShareDomain(sharedPreferences, url)

    /**
     * [url] as it should be opened outside the app — in a browser, or from a QR code a phone
     * camera scans. Only a domain of Reddit's own applies; an embed fixer is for chat previews.
     */
    @JvmStatic
    fun applyBrowserDomain(sharedPreferences: SharedPreferences, url: String): String {
        val host = sharedLinkHost(sharedPreferences)?.takeIf(::isRedditHost) ?: return url
        return rewriteHost(url, host, postsOnly = false)
    }

    private fun rewriteHost(url: String, host: String, postsOnly: Boolean): String {
        val uri = url.toUri()
        val currentHost = uri.host?.lowercase() ?: return url
        if (currentHost !in REWRITABLE_HOSTS) {
            return url
        }
        if (postsOnly && !isPostPath(uri.pathSegments)) {
            return url
        }

        return uri.buildUpon().authority(host).build().toString()
    }

    /**
     * Whether [segments] are a post or comment link: `/comments/<id>`, optionally after
     * `/r/<sub>`, `/u/<name>` or `/user/<name>`, with anything after the id — slug, comment id.
     */
    private fun isPostPath(segments: List<String>): Boolean {
        val commentsIndex = if (segments.size > 2 && segments[0].lowercase() in POST_OWNER_SEGMENTS) 2 else 0
        return segments.size > commentsIndex + 1 &&
            segments[commentsIndex].equals("comments", ignoreCase = true)
    }
}
