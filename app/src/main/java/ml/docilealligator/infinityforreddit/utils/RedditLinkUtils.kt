package ml.docilealligator.infinityforreddit.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.core.net.toUri
import ml.docilealligator.infinityforreddit.Infinity

/**
 * Helpers for the Reddit links the app reads in and hands out.
 *
 * [applyLinkDomain] rewrites the host of Reddit links on their way out of the app — shared,
 * copied, or handed to a browser — to old.reddit.com when Settings > Miscellaneous > Use
 * old.reddit.com is on. Off (the default) leaves every link on www.reddit.com.
 *
 * Only hosts that serve the same paths as old.reddit.com are rewritten, so media (i.redd.it,
 * v.redd.it), share shorteners (s.reddit.com, redd.it, reddit.app.link) and non-Reddit links pass
 * through untouched. Permalinks stay canonical www.reddit.com in memory and API calls are
 * unaffected; only the outbound string changes, so the preference applies without a restart.
 */
object RedditLinkUtils {
    private const val OLD_REDDIT_HOST = "old.reddit.com"
    private val REWRITABLE_HOSTS = setOf("www.reddit.com", "reddit.com")

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
     * For callers holding only a Context. Resolves the preferences through the application graph:
     * `PreferenceManager.getDefaultSharedPreferences` would hand back the unscoped file and quietly
     * ignore whichever account is signed in.
     */
    @JvmStatic
    fun applyLinkDomain(context: Context, url: String): String {
        val application = context.applicationContext
        if (application !is Infinity) {
            return url
        }
        return applyLinkDomain(application.appComponent.defaultSharedPreferences(), url)
    }

    @JvmStatic
    fun applyLinkDomain(sharedPreferences: SharedPreferences, url: String): String {
        if (!sharedPreferences.getBoolean(SharedPreferencesUtils.USE_OLD_REDDIT_DOMAIN, false)) {
            return url
        }

        val uri = url.toUri()
        val host = uri.host?.lowercase() ?: return url
        if (host !in REWRITABLE_HOSTS) {
            return url
        }

        return uri.buildUpon().authority(OLD_REDDIT_HOST).build().toString()
    }

    /** [applyLinkDomain] for the nullable permalink on [ml.docilealligator.infinityforreddit.comment.Comment]. */
    @JvmStatic
    fun applyLinkDomainOrNull(sharedPreferences: SharedPreferences, url: String?): String? =
        if (url == null) null else applyLinkDomain(sharedPreferences, url)
}
