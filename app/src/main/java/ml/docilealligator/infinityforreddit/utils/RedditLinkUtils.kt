package ml.docilealligator.infinityforreddit.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.core.net.toUri
import androidx.preference.PreferenceManager

/**
 * Rewrites the host of Reddit links on their way out of the app — shared, copied, or handed to a
 * browser — to old.reddit.com when Settings > Miscellaneous > Use old.reddit.com is on. Off (the
 * default) leaves every link on www.reddit.com.
 *
 * Only hosts that serve the same paths as old.reddit.com are rewritten, so media (i.redd.it,
 * v.redd.it), share shorteners (s.reddit.com, redd.it, reddit.app.link) and non-Reddit links pass
 * through untouched. Permalinks stay canonical www.reddit.com in memory and API calls are
 * unaffected; only the outbound string changes, so the preference applies without a restart.
 */
object RedditLinkUtils {
    private const val OLD_REDDIT_HOST = "old.reddit.com"
    private val REWRITABLE_HOSTS = setOf("www.reddit.com", "reddit.com")

    @JvmStatic
    fun applyLinkDomain(context: Context, url: String): String =
        applyLinkDomain(PreferenceManager.getDefaultSharedPreferences(context), url)

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
