package ml.docilealligator.infinityforreddit.postfilter

import android.net.Uri
import ml.docilealligator.infinityforreddit.post.Post

/**
 * Turns an "Add to Post Filter" choice about one post, subreddit or user into [FilterRule]s.
 *
 * Both ends of that flow need the same rules — `PostFilterPreferenceActivity` writes them straight
 * into a saved filter, and hands them to `CustomizePostFilterActivity` when the filter still has to
 * be named — so the mapping lives here rather than in either screen.
 */
object PostFilterSeeds {

    private class Option(
        val field: RuleField,
        val exclude: Boolean,
        val term: (Post) -> String?,
    )

    /**
     * The options of the "Add to Post Filter" dialog, **in the order of
     * `R.array.add_to_post_filter_options`**: the dialog reports what was ticked by index, so the
     * two lists have to stay in step. `PostFilterSeedsTest` fails if they drift apart.
     */
    private val OPTIONS = listOf(
        Option(RuleField.SUBREDDIT, true) { it.subredditName },
        Option(RuleField.USER, true) { it.author },
        Option(RuleField.FLAIR, true) { it.flair },
        Option(RuleField.FLAIR, false) { it.flair },
        Option(RuleField.DOMAIN, true) { domainTerm(it.url) },
        Option(RuleField.DOMAIN, false) { domainTerm(it.url) },
        Option(RuleField.SUBREDDIT, false) { it.subredditName },
        Option(RuleField.USER, false) { it.author },
    )

    /** How many options the dialog offers. */
    @JvmStatic
    val optionCount: Int
        get() = OPTIONS.size

    /**
     * The rules for the ticked options, grouped the way the rule list displays them.
     *
     * Options the post has no value for are skipped: a post with no flair contributes no flair rule
     * however its box was ticked, which is what keeps an empty term out of the filter.
     */
    @JvmStatic
    fun rulesForPost(post: Post, selected: BooleanArray): List<FilterRule> {
        val rules = ArrayList<FilterRule>()
        for (i in OPTIONS.indices) {
            if (i >= selected.size || !selected[i]) {
                continue
            }
            val option = OPTIONS[i]
            val term = option.term(post)?.trim().orEmpty()
            if (term.isEmpty()) {
                continue
            }
            PostFilterRules.addRule(rules, FilterRule(option.field, option.exclude, term))
        }
        return rules
    }

    /**
     * The single rule behind "Add to Post Filter" on a subreddit or user page, or nothing when the
     * name is missing.
     */
    @JvmStatic
    fun excludeRule(field: RuleField, name: String?): List<FilterRule> {
        val trimmed = name?.trim().orEmpty()
        return if (trimmed.isEmpty()) emptyList() else listOf(FilterRule(field, true, trimmed))
    }

    /**
     * Reduces a post URL to the domain a filter stores, or null when there is no domain to store.
     *
     * [Uri.getHost] is null for anything without a scheme — a bare "example.com" parses as a relative
     * path — and in that case the value already is the domain. It is empty for a scheme with no
     * authority ("https://"), which is neither a URL nor a domain; returning null there lets the
     * caller skip it rather than seeding an empty rule.
     */
    @JvmStatic
    fun domainTerm(urlOrDomain: String?): String? {
        if (urlOrDomain.isNullOrEmpty()) {
            return null
        }
        val host = Uri.parse(urlOrDomain).host
        if (!host.isNullOrEmpty()) {
            return host
        }
        return if (urlOrDomain.contains("://")) null else urlOrDomain
    }
}
