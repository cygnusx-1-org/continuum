package ml.docilealligator.infinityforreddit.postfilter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Customize Post Filter screen edits a flat [FilterRule] list instead of twelve comma-separated
 * text boxes, but stores it back into the same [PostFilter] columns — no Room migration, no change
 * to [PostFilter.isPostAllowed]. That only holds if the mapping round-trips, so these pin it:
 * every column, both polarities, the regex columns that must not be split, and the normalisation
 * (blanks, duplicates) that [PostFilter.isPostAllowed] already applies at match time.
 */
class PostFilterRulesTest {

    private fun filter(configure: PostFilter.() -> Unit) = PostFilter().apply(configure)

    private fun roundTrip(postFilter: PostFilter): PostFilter {
        val result = PostFilter()
        PostFilterRules.applyRules(result, PostFilterRules.toRules(postFilter))
        return result
    }

    @Test
    fun `every term column round-trips unchanged`() {
        val original = filter {
            excludeSubreddits = "politics,news"
            containSubreddits = "android"
            excludeUsers = "spammer"
            containUsers = "edgan,someone"
            excludeFlairs = "Meme"
            containFlairs = "OC,Discussion"
            excludeDomains = "example.com"
            containDomains = "bbc.co.uk,reuters.com"
            postTitleExcludesStrings = "leaked,spoiler"
            postTitleContainsStrings = "android"
            postTitleExcludesRegex = "^\\[OC\\]"
            postTitleContainsRegex = "release"
        }

        val actual = roundTrip(original)

        assertEquals("politics,news", actual.excludeSubreddits)
        assertEquals("android", actual.containSubreddits)
        assertEquals("spammer", actual.excludeUsers)
        assertEquals("edgan,someone", actual.containUsers)
        assertEquals("Meme", actual.excludeFlairs)
        assertEquals("OC,Discussion", actual.containFlairs)
        assertEquals("example.com", actual.excludeDomains)
        assertEquals("bbc.co.uk,reuters.com", actual.containDomains)
        assertEquals("leaked,spoiler", actual.postTitleExcludesStrings)
        assertEquals("android", actual.postTitleContainsStrings)
        assertEquals("^\\[OC\\]", actual.postTitleExcludesRegex)
        assertEquals("release", actual.postTitleContainsRegex)
    }

    @Test
    fun `a regex containing a comma survives verbatim`() {
        // The reason the regex columns are not treated as comma-separated: {2,3} is a legal
        // quantifier, and splitting on "," would leave two broken patterns behind.
        val pattern = "^\\[OC\\]\\s*\\w{2,3}$"
        val original = filter {
            postTitleExcludesRegex = pattern
            postTitleContainsRegex = "a{1,2}b"
        }

        val rules = PostFilterRules.toRules(original)
        assertEquals(
            listOf(pattern, "a{1,2}b"),
            rules.filter { it.field == RuleField.TITLE_REGEX }.map { it.value }
        )

        val actual = roundTrip(original)
        assertEquals(pattern, actual.postTitleExcludesRegex)
        assertEquals("a{1,2}b", actual.postTitleContainsRegex)
    }

    @Test
    fun `whitespace is trimmed and blank entries are dropped`() {
        val original = filter { excludeSubreddits = " politics , ,news,  " }

        assertEquals(listOf("politics", "news"), PostFilterRules.toRules(original).map { it.value })
        assertEquals("politics,news", roundTrip(original).excludeSubreddits)
    }

    @Test
    fun `duplicates collapse case-insensitively`() {
        // isPostAllowed compares with equalsIgnoreCase, so a second "Politics" never matched
        // anything the first one did not.
        val original = filter { excludeSubreddits = "politics,Politics,POLITICS,news" }

        assertEquals("politics,news", roundTrip(original).excludeSubreddits)
    }

    @Test
    fun `empty and null columns stay empty`() {
        val original = filter {
            excludeSubreddits = ""
            containUsers = null
        }

        val actual = roundTrip(original)

        assertTrue(PostFilterRules.toRules(original).isEmpty())
        assertEquals("", actual.excludeSubreddits)
        assertEquals("", actual.containUsers)
        assertEquals("", actual.postTitleExcludesRegex)
    }

    @Test
    fun `rules are grouped by field with excludes first`() {
        val original = filter {
            containDomains = "bbc.co.uk"
            excludeUsers = "spammer"
            containSubreddits = "android"
            excludeSubreddits = "politics"
        }

        assertEquals(
            listOf(
                FilterRule(RuleField.SUBREDDIT, true, "politics"),
                FilterRule(RuleField.SUBREDDIT, false, "android"),
                FilterRule(RuleField.USER, true, "spammer"),
                FilterRule(RuleField.DOMAIN, false, "bbc.co.uk"),
            ),
            PostFilterRules.toRules(original)
        )
    }

    @Test
    fun `applyRules clears columns that no rule covers`() {
        val postFilter = filter {
            excludeSubreddits = "politics"
            containUsers = "edgan"
            postTitleExcludesRegex = "^spam"
        }

        PostFilterRules.applyRules(postFilter, listOf(FilterRule(RuleField.USER, false, "edgan")))

        assertEquals("", postFilter.excludeSubreddits)
        assertEquals("edgan", postFilter.containUsers)
        assertEquals("", postFilter.postTitleExcludesRegex)
    }

    @Test
    fun `addRule rejects an equal term and inserts next to its own kind`() {
        val rules = PostFilterRules.toRules(
            filter {
                excludeSubreddits = "politics"
                excludeUsers = "spammer"
            }
        ).toMutableList()

        assertFalse(PostFilterRules.addRule(rules, FilterRule(RuleField.SUBREDDIT, true, "POLITICS")))
        assertTrue(PostFilterRules.addRule(rules, FilterRule(RuleField.SUBREDDIT, true, "news")))

        assertEquals(
            listOf("politics", "news", "spammer"),
            rules.map { it.value }
        )
    }

    @Test
    fun `only the first regex of a polarity reaches the column`() {
        val postFilter = PostFilter()

        PostFilterRules.applyRules(
            postFilter,
            listOf(
                FilterRule(RuleField.TITLE_REGEX, true, "^first"),
                FilterRule(RuleField.TITLE_REGEX, true, "^second"),
            )
        )

        assertEquals("^first", postFilter.postTitleExcludesRegex)
    }
}
