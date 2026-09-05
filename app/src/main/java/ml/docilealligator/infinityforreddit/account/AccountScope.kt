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
 * namespace unambiguous in both directions — and is why [Account.ANONYMOUS_ACCOUNT] is spelled
 * with one.
 */
object AccountScope {
    private const val SEPARATOR = "."

    /**
     * The namespace [accountName] stores its settings under, which for anonymous browsing is
     * [Account.ANONYMOUS_ACCOUNT] itself — the account name and the namespace are one string, so
     * there is no second spelling to keep in step.
     *
     * Anonymous reaches this as that constant, as `null` from a preferences file cleared on logout,
     * or as `""` from the older key spellings; [Account.isAnonymous] is the authority on which
     * shapes count. The `isNullOrEmpty` test is repeated here only so Kotlin can see that the other
     * branch is non-null.
     */
    @JvmStatic
    fun namespace(accountName: String?): String =
        if (accountName.isNullOrEmpty() || Account.isAnonymous(accountName)) {
            Account.ANONYMOUS_ACCOUNT
        } else {
            accountName
        }

    /** The key [base] takes for [accountName]. */
    @JvmStatic
    fun key(accountName: String?, base: String): String = namespace(accountName) + SEPARATOR + base

    /** The base of a key built by [key], or `null` if it was not. */
    @JvmStatic
    fun baseOf(key: String): String? {
        val namespace = namespaceOf(key) ?: return null
        return key.substring(namespace.length + SEPARATOR.length)
    }

    /**
     * The namespace of a key built by [key], or `null` if it was not.
     *
     * Found from the front rather than the back. A namespace is either [Account.ANONYMOUS_ACCOUNT]
     * or a username, and a username cannot contain the separator — so the boundary is the first one
     * after either. Reading from the back instead assumed the base had no separator in it, which
     * several older keys break: anything built as `<some>_base` + an account name ends in
     * `.anonymous` while logged out, and such a key used to parse as a namespace of
     * `.anonymous.<some>_base` and a base of `anonymous`, belonging to no account at all.
     */
    @JvmStatic
    fun namespaceOf(key: String): String? {
        if (key.startsWith(Account.ANONYMOUS_ACCOUNT + SEPARATOR)) {
            return Account.ANONYMOUS_ACCOUNT
        }
        val separator = key.indexOf(SEPARATOR)
        return if (separator <= 0) null else key.substring(0, separator)
    }
}
