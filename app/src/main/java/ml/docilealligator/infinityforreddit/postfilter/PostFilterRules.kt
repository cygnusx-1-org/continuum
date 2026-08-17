package ml.docilealligator.infinityforreddit.postfilter

/**
 * Converts between [PostFilter]'s twelve term columns and the flat [FilterRule] list the
 * Customize Post Filter screen edits.
 *
 * Deliberately a *view*, not a schema change: the columns, [PostFilter.isPostAllowed], the
 * `Parcelable` implementation and the Gson keys used by backup/restore all stay exactly as they
 * are, so this UI rewrite needs no Room migration and cannot change which posts get filtered.
 *
 * Round-tripping a filter through [toRules] and [applyRules] is stable — order within a field is
 * preserved — except that blank entries and case-insensitive duplicates are dropped, since
 * [PostFilter.isPostAllowed] already ignores both.
 */
object PostFilterRules {

    /**
     * The regex columns hold **one** pattern each, not a comma-separated list: `a{2,3}` is a legal
     * pattern and splitting it would corrupt it. So there is at most one rule per polarity for
     * [RuleField.TITLE_REGEX], and its value is the column verbatim.
     */
    @JvmStatic
    fun isCommaSeparated(field: RuleField): Boolean = field != RuleField.TITLE_REGEX

    /**
     * Reads a filter's term columns as a flat list, grouped by [RuleField] in enum order and, within
     * a field, excludes before includes.
     */
    @JvmStatic
    fun toRules(postFilter: PostFilter): List<FilterRule> {
        val rules = ArrayList<FilterRule>()
        for (field in RuleField.entries) {
            for (exclude in booleanArrayOf(true, false)) {
                val column = column(postFilter, field, exclude)
                if (column.isNullOrEmpty()) {
                    continue
                }
                val values = if (isCommaSeparated(field)) column.split(",") else listOf(column)
                for (value in values) {
                    val trimmed = value.trim()
                    if (trimmed.isEmpty()) {
                        continue
                    }
                    val rule = FilterRule(field, exclude, trimmed)
                    if (rules.none { it.isSameTermAs(rule) }) {
                        rules.add(rule)
                    }
                }
            }
        }
        return rules
    }

    /**
     * Writes [rules] back over every term column of [postFilter], clearing the columns that no rule
     * covers. Extra rules for a regex field beyond the first of a polarity are dropped, matching
     * the single-value column.
     */
    @JvmStatic
    fun applyRules(postFilter: PostFilter, rules: List<FilterRule>) {
        // isPostAllowed caches the regex columns into these @Ignore lists and, once populated, matches
        // against the cache instead of the column. A filter that has already been used for matching
        // carries that cache through its Parcel, so without this an edited regex would be written to
        // the column and then quietly ignored in favour of the pattern it replaced.
        postFilter.postTitleExcludesRegexes.clear()
        postFilter.postTitleContainsRegexes.clear()
        for (field in RuleField.entries) {
            for (exclude in booleanArrayOf(true, false)) {
                val values = rules.filter { it.field == field && it.exclude == exclude }.map { it.value }
                val column = if (isCommaSeparated(field)) {
                    values.joinToString(",")
                } else {
                    values.firstOrNull().orEmpty()
                }
                setColumn(postFilter, field, exclude, column)
            }
        }
    }

    /** Adds [rule] unless an equal term is already present. Returns true when the list changed. */
    @JvmStatic
    fun addRule(rules: MutableList<FilterRule>, rule: FilterRule): Boolean {
        if (rules.any { it.isSameTermAs(rule) }) {
            return false
        }
        // Keep the grouping toRules() produces, so a new term lands next to its own kind instead of
        // at the bottom of the list.
        val insertAt = rules.indexOfLast { sortKey(it) <= sortKey(rule) } + 1
        rules.add(insertAt, rule)
        return true
    }

    private fun sortKey(rule: FilterRule): Int = rule.field.ordinal * 2 + if (rule.exclude) 0 else 1

    private fun column(postFilter: PostFilter, field: RuleField, exclude: Boolean): String? =
        when (field) {
            RuleField.SUBREDDIT -> if (exclude) postFilter.excludeSubreddits else postFilter.containSubreddits
            RuleField.USER -> if (exclude) postFilter.excludeUsers else postFilter.containUsers
            RuleField.FLAIR -> if (exclude) postFilter.excludeFlairs else postFilter.containFlairs
            RuleField.DOMAIN -> if (exclude) postFilter.excludeDomains else postFilter.containDomains
            RuleField.TITLE_KEYWORD ->
                if (exclude) postFilter.postTitleExcludesStrings else postFilter.postTitleContainsStrings
            RuleField.TITLE_REGEX ->
                if (exclude) postFilter.postTitleExcludesRegex else postFilter.postTitleContainsRegex
        }

    private fun setColumn(postFilter: PostFilter, field: RuleField, exclude: Boolean, value: String) {
        when (field) {
            RuleField.SUBREDDIT ->
                if (exclude) postFilter.excludeSubreddits = value else postFilter.containSubreddits = value
            RuleField.USER ->
                if (exclude) postFilter.excludeUsers = value else postFilter.containUsers = value
            RuleField.FLAIR ->
                if (exclude) postFilter.excludeFlairs = value else postFilter.containFlairs = value
            RuleField.DOMAIN ->
                if (exclude) postFilter.excludeDomains = value else postFilter.containDomains = value
            RuleField.TITLE_KEYWORD ->
                if (exclude) {
                    postFilter.postTitleExcludesStrings = value
                } else {
                    postFilter.postTitleContainsStrings = value
                }
            RuleField.TITLE_REGEX ->
                if (exclude) postFilter.postTitleExcludesRegex = value else postFilter.postTitleContainsRegex = value
        }
    }
}
