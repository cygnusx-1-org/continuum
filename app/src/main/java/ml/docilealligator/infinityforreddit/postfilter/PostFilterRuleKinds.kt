package ml.docilealligator.infinityforreddit.postfilter

/**
 * Keeps wildcard subreddit rules and every other kind of rule in filters of their own.
 *
 * A wildcard term — 'Starts with', 'Ends with', 'Contains' — only runs on
 * [ml.docilealligator.infinityforreddit.Constants.CONTINUUM_ALL_SUBREDDIT], so a filter holding one
 * is pinned to that feed. Everything else in the same filter is pinned there too and goes quietly
 * inert on Home and on the subreddits the user believes it covers: the filter reads as if it does
 * six things and does one. Issue #426 is that state, reached by adding a wildcard rule to a filter
 * that already held a thousand exact ones.
 *
 * Nothing here changes what gets stored — a rule's kind is read back off its own value by
 * [FilterRule.matchMode] — it only decides what a filter may still be given.
 */
object PostFilterRuleKinds {

    /** True for a subreddit rule that matches part of a name rather than the whole of it. */
    @JvmStatic
    fun isWildcard(rule: FilterRule): Boolean = rule.matchMode.isWildcard

    /**
     * True when [rules] holds a wildcard subreddit rule. One is enough: it only runs on
     * r/ContinuumAll, and a filter that runs there cannot also be pointed at other feeds without
     * being half inert on them. That is what pins a filter, mixed or not.
     */
    @JvmStatic
    fun hasWildcard(rules: List<FilterRule>): Boolean = rules.any { isWildcard(it) }

    /**
     * True when [rules] holds both kinds. Only a filter saved before this policy existed can, and
     * it takes no new rule of either kind until it is one or the other again.
     */
    @JvmStatic
    fun isMixed(rules: List<FilterRule>): Boolean =
        rules.any { isWildcard(it) } && rules.any { !isWildcard(it) }

    /**
     * Whether [candidate] may join [rules], where [replacing] is the rule it is being edited from.
     *
     * Only the rules that are there now count, so emptying a filter of one kind opens it to the
     * other — which is the one way a filter converts from one to the other.
     *
     * Editing a rule without changing its kind is always allowed, so a mixed filter left over from
     * before can be corrected rule by rule rather than only emptied.
     */
    @JvmStatic
    @JvmOverloads
    fun accepts(
        rules: List<FilterRule>,
        candidate: FilterRule,
        replacing: FilterRule? = null,
    ): Boolean {
        if (replacing != null && isWildcard(replacing) == isWildcard(candidate)) {
            return true
        }
        val others = if (replacing == null) rules else rules.filterNot { it.isSameTermAs(replacing) }
        return others.none { isWildcard(it) != isWildcard(candidate) }
    }
}
