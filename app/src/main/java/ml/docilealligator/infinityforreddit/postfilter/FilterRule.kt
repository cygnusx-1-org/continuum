package ml.docilealligator.infinityforreddit.postfilter

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Which part of a post a [FilterRule] looks at.
 *
 * The order of the constants is the order rules are grouped in on screen, and the order
 * [PostFilterRules.toRules] emits them in.
 */
enum class RuleField {
    SUBREDDIT,
    USER,
    FLAIR,
    DOMAIN,
    TITLE_KEYWORD,
    TITLE_REGEX,
}

/**
 * One term of a post filter, as the UI presents it: "exclude subreddit politics",
 * "only include domain bbc.co.uk".
 *
 * This is a *view* over [PostFilter]'s twelve comma-separated / regex columns, not a stored
 * entity — see [PostFilterRules] for the mapping. Polarity belongs to the individual term here,
 * which is what lets one list replace the twelve include/exclude text boxes.
 *
 * @param exclude true hides posts that match, false keeps only posts that match.
 */
@Parcelize
data class FilterRule(
    val field: RuleField,
    val exclude: Boolean,
    val value: String,
) : Parcelable {

    /**
     * How this rule's [value] matches, for display and for the Add Rule sheet's chips.
     *
     * Only [RuleField.SUBREDDIT] honours the wildcard forms; every other field treats an asterisk as
     * ordinary text, so they always read as [SubredditMatchMode.EXACT].
     */
    // `this.` is load-bearing: inside a property accessor a bare `field` is Kotlin's backing-field
    // keyword, not this class's `field` property.
    val matchMode: SubredditMatchMode
        get() = if (this.field == RuleField.SUBREDDIT) {
            SubredditMatcher.modeOf(value)
        } else {
            SubredditMatchMode.EXACT
        }

    /** The rule's text without its wildcard markers, for display and for editing. */
    val bareValue: String
        get() = if (this.field == RuleField.SUBREDDIT) SubredditMatcher.parse(value).bare else value

    /** Rules are equal by term regardless of case, which is how [PostFilter] matches them. */
    fun isSameTermAs(other: FilterRule): Boolean =
        field == other.field && exclude == other.exclude && value.equals(other.value, ignoreCase = true)
}
