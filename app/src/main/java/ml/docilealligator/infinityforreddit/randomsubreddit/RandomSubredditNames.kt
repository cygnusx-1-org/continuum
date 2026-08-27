package ml.docilealligator.infinityforreddit.randomsubreddit

import java.util.Locale

/**
 * The three names Reddit used to resolve to a random subreddit server-side, and now does not.
 *
 * `r/random`, `r/randnsfw` and `r/myrandom` all answer 404 `{"reason": "banned"}` under every auth
 * mode, so anything that hands one of them to `ViewSubredditDetailActivity` as an ordinary name
 * lands on an empty page offering to subscribe to a banned subreddit. Every route that accepts a
 * typed subreddit name — Go to Subreddit in six activities, the Subscriptions search field, a deep
 * link — reaches that same destination, which is where they are intercepted rather than at each
 * call site.
 *
 * Case-insensitive throughout: these arrive from a keyboard.
 */
object RandomSubredditNames {

    /** A random subreddit anyone can reach. */
    const val RANDOM = "random"

    /** A random subreddit Reddit has flagged `over18`. */
    const val RANDNSFW = "randnsfw"

    /** A random subreddit from the signed-in account's own subscriptions. */
    const val MYRANDOM = "myrandom"

    /**
     * Every name that means "pick one for me". Kept lowercase; compare against [canonicalise]
     * rather than against a raw user string.
     */
    @JvmField
    val ALL = setOf(RANDOM, RANDNSFW, MYRANDOM)

    /**
     * The canonical form of [subredditName] if it names a random pick, otherwise null. Accepts the
     * `r/` and `/r/` prefixes some callers pass through, and any casing.
     */
    @JvmStatic
    fun canonicalise(subredditName: String?): String? {
        var name = subredditName?.trim() ?: return null
        if (name.startsWith("/r/", ignoreCase = true)) {
            name = name.substring(3)
        } else if (name.startsWith("r/", ignoreCase = true)) {
            name = name.substring(2)
        }
        name = name.trim().lowercase(Locale.US)
        return if (name in ALL) name else null
    }

    /** Whether [subredditName] names a random pick rather than a real subreddit. */
    @JvmStatic
    fun isRandomName(subredditName: String?): Boolean = canonicalise(subredditName) != null
}
