package ml.docilealligator.infinityforreddit.postfilter

/**
 * How an "Exclude subreddits" term matches a subreddit name.
 *
 * The order of the constants is the order the match-type chips appear in on the Add Rule sheet.
 */
enum class SubredditMatchMode {
    /** The whole name, as it always has: `politics` hides r/politics and nothing else. */
    EXACT,

    /** `politics*` hides r/politicshumor. */
    PREFIX,

    /** `*memes` hides r/dankmemes. */
    SUFFIX,

    /** `*india*` hides r/DankIndiaMemes. */
    CONTAINS,
    ;

    val isWildcard: Boolean
        get() = this != EXACT
}

/**
 * Parses and applies the wildcard syntax of an "Exclude subreddits" term.
 *
 * The mode lives *inside* the stored term as leading/trailing asterisks (`foo*`, `*foo`, `*foo*`)
 * rather than in a column of its own, which is what lets [PostFilterRules] stay a view over
 * [PostFilter]'s existing comma-separated columns: no Room migration, no change to the Gson keys
 * backup/restore writes, and a term that has no asterisks keeps matching exactly the way it did
 * before this syntax existed.
 *
 * That encoding is unambiguous because Reddit restricts subreddit names to `[A-Za-z0-9_]`, so an
 * asterisk can never be part of a name and never needs escaping. The Add Rule sheet refuses a
 * literal `*` typed into the value for the same reason — the match-type chips own the asterisks.
 */
object SubredditMatcher {

    /**
     * The shortest bare term a wildcard mode will accept. Three is low enough for the terms people
     * actually ask for (`irl`, `nba`, `fbi`) and high enough to keep one- and two-letter terms —
     * which would sweep up a large fraction of Reddit — out of reach.
     *
     * [SubredditMatchMode.EXACT] has no minimum: it matches one name, so length buys nothing.
     */
    const val MIN_WILDCARD_LENGTH = 3

    private const val WILDCARD = '*'

    /**
     * Splits a stored term into its mode and its bare text. A term that is nothing but asterisks
     * has no bare text to match on and is reported as [SubredditMatchMode.EXACT] of the empty
     * string, which [matches] never matches — callers do not have to special-case it.
     */
    @JvmStatic
    fun parse(term: String): ParsedTerm {
        val trimmed = term.trim()
        val prefixed = trimmed.startsWith(WILDCARD)
        val suffixed = trimmed.length > 1 && trimmed.endsWith(WILDCARD)
        val bare = trimmed.trim(WILDCARD)
        if (bare.isEmpty()) {
            return ParsedTerm(SubredditMatchMode.EXACT, "")
        }
        val mode = when {
            // "*foo*" is contains; "*foo" is a suffix match; "foo*" is a prefix match.
            prefixed && suffixed -> SubredditMatchMode.CONTAINS
            prefixed -> SubredditMatchMode.SUFFIX
            suffixed -> SubredditMatchMode.PREFIX
            else -> SubredditMatchMode.EXACT
        }
        return ParsedTerm(mode, bare)
    }

    /** Builds the stored form of a term. The inverse of [parse]. */
    @JvmStatic
    fun format(mode: SubredditMatchMode, bare: String): String {
        val trimmed = bare.trim()
        if (trimmed.isEmpty()) {
            return ""
        }
        return when (mode) {
            SubredditMatchMode.EXACT -> trimmed
            SubredditMatchMode.PREFIX -> "$trimmed$WILDCARD"
            SubredditMatchMode.SUFFIX -> "$WILDCARD$trimmed"
            SubredditMatchMode.CONTAINS -> "$WILDCARD$trimmed$WILDCARD"
        }
    }

    /** The mode a stored term encodes, without its text. */
    @JvmStatic
    fun modeOf(term: String): SubredditMatchMode = parse(term).mode

    /** True when [term] uses one of the wildcard forms. */
    @JvmStatic
    fun isWildcard(term: String): Boolean = modeOf(term).isWildcard

    /**
     * True when [bare] is long enough for [mode]. Callers validating user input should reject a
     * shorter term rather than silently storing one that will not be honoured.
     */
    @JvmStatic
    fun isLongEnough(mode: SubredditMatchMode, bare: String): Boolean =
        !mode.isWildcard || bare.trim().length >= MIN_WILDCARD_LENGTH

    /**
     * True when [subredditName] matches [term]. Case-insensitive, matching how every other term in
     * [PostFilter.isPostAllowed] compares.
     *
     * A wildcard term whose bare text is under [MIN_WILDCARD_LENGTH] never matches, so a term that
     * somehow reached storage without passing validation (an imported backup, a hand-edited
     * database) fails closed instead of hiding a large slice of a feed.
     */
    @JvmStatic
    fun matches(subredditName: String, term: String): Boolean {
        // Hot path: run for every stored term against every parsed post, so a list of thousands of
        // exact names must not allocate here. The overwhelmingly common case -- a plain name, no
        // asterisks -- is answered before [parse] builds anything, and the wildcard branches compare
        // in place rather than lower-casing copies of both strings.
        if (term.isEmpty()) {
            return false
        }
        val prefixed = term[0] == WILDCARD
        val suffixed = term.length > 1 && term[term.length - 1] == WILDCARD
        if (!prefixed && !suffixed) {
            return subredditName.equals(term, ignoreCase = true)
        }
        val start = if (prefixed) 1 else 0
        val end = if (suffixed) term.length - 1 else term.length
        val bareLength = end - start
        if (bareLength < MIN_WILDCARD_LENGTH) {
            return false
        }
        return when {
            // "*foo*" is contains; "*foo" is a suffix match; "foo*" is a prefix match.
            prefixed && suffixed ->
                subredditName.contains(term.substring(start, end), ignoreCase = true)
            prefixed ->
                subredditName.length >= bareLength &&
                    subredditName.regionMatches(
                        subredditName.length - bareLength, term, start, bareLength, ignoreCase = true,
                    )
            else ->
                subredditName.regionMatches(0, term, start, bareLength, ignoreCase = true)
        }
    }

    /** A term split into the mode it encodes and the text that mode applies to. */
    data class ParsedTerm(val mode: SubredditMatchMode, val bare: String)
}
