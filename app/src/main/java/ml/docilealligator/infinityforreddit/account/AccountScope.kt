package ml.docilealligator.infinityforreddit.account

/**
 * The one way to build a preference key that belongs to a single account.
 *
 * Per-account settings grew up five separate times in this app, and the results disagreed: the
 * anonymous account was written as `""` by the NSFW screen, as `"-"` by the post-history screen, and
 * as `".anonymous"` by the scrolled-position cache, while the link-handler settings wrote `"-"` and
 * read `""`. Every new per-account setting goes through here instead.
 *
 * A key is `namespace + SEPARATOR + base`. The separator matters: without it, an account called
 * `foo_blur` and the blur setting of an account called `foo` produce the same key. Reddit usernames
 * are letters, digits, underscores and hyphens, so a `.` can never appear in one, which makes the
 * namespace unambiguous in both directions.
 */
object AccountScope {
    /**
     * Namespace for anonymous browsing. Contains a `.`, so it can never collide with a username.
     * Matches the spelling the scrolled-position cache already used.
     */
    const val ANONYMOUS_NAMESPACE = ".anonymous"

    private const val SEPARATOR = "."

    /**
     * The namespace [accountName] stores its settings under. Anonymous browsing reaches this as
     * [Account.ANONYMOUS_ACCOUNT], as `null` from a preferences file that has been cleared on
     * logout, or as `""` from the older key spellings.
     */
    @JvmStatic
    fun namespace(accountName: String?): String =
        if (accountName.isNullOrEmpty() || accountName == Account.ANONYMOUS_ACCOUNT) {
            ANONYMOUS_NAMESPACE
        } else {
            accountName
        }

    /** The key [base] takes for [accountName]. */
    @JvmStatic
    fun key(accountName: String?, base: String): String = namespace(accountName) + SEPARATOR + base

    /**
     * The base of a key built by [key], or `null` if it was not. Bases never contain the separator,
     * so the last one is always the boundary; namespaces may contain it, as the anonymous one does.
     */
    @JvmStatic
    fun baseOf(key: String): String? {
        val separator = key.lastIndexOf(SEPARATOR)
        return if (separator <= 0) null else key.substring(separator + SEPARATOR.length)
    }

    /** The namespace of a key built by [key], or `null` if it was not. */
    @JvmStatic
    fun namespaceOf(key: String): String? {
        val separator = key.lastIndexOf(SEPARATOR)
        return if (separator <= 0) null else key.substring(0, separator)
    }
}
