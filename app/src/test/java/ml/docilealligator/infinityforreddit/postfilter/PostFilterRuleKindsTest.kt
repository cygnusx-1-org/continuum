package ml.docilealligator.infinityforreddit.postfilter

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A filter holds wildcard subreddit rules or ordinary ones, never both.
 *
 * Issue #426: a filter of 1008 exact subreddit rules and 42 title words took one 'Contains' rule
 * and was pinned to r/ContinuumAll for it — every other rule in it silently stopped running on
 * Home, and the "Applies to" controls that could have said so were locked away by the same pin.
 */
class PostFilterRuleKindsTest {

    private fun exact(name: String) = FilterRule(RuleField.SUBREDDIT, true, name)

    private fun contains(bare: String) =
        FilterRule(RuleField.SUBREDDIT, true, SubredditMatcher.format(SubredditMatchMode.CONTAINS, bare))

    private fun titleWord(word: String) = FilterRule(RuleField.TITLE_KEYWORD, true, word)

    @Test
    fun `a wildcard rule is refused by a filter that holds other rules`() {
        assertFalse(PostFilterRuleKinds.accepts(listOf(titleWord("spoiler")), contains("india")))
        assertFalse(PostFilterRuleKinds.accepts(listOf(exact("politics")), contains("india")))
    }

    @Test
    fun `an ordinary rule is refused by a filter that holds a wildcard rule`() {
        val wildcardFilter = listOf(contains("india"))

        assertFalse(PostFilterRuleKinds.accepts(wildcardFilter, exact("politics")))
        assertFalse(PostFilterRuleKinds.accepts(wildcardFilter, titleWord("spoiler")))
    }

    @Test
    fun `a filter of one kind takes more of that kind`() {
        assertTrue(PostFilterRuleKinds.accepts(listOf(contains("india")), contains("memes")))
        assertTrue(PostFilterRuleKinds.accepts(listOf(exact("politics")), titleWord("spoiler")))
    }

    @Test
    fun `an empty filter takes either kind`() {
        assertTrue(PostFilterRuleKinds.accepts(emptyList(), contains("india")))
        assertTrue(PostFilterRuleKinds.accepts(emptyList(), titleWord("spoiler")))
    }

    @Test
    fun `emptying a filter of one kind converts it to the other`() {
        // The conversion is not a mode anywhere. It is what is left once the last rule of a kind
        // has been deleted, which is the only state accepts() ever looks at.
        val rules = mutableListOf(contains("india"), contains("memes"))
        assertFalse(PostFilterRuleKinds.accepts(rules, titleWord("spoiler")))

        rules.removeAt(1)
        assertFalse("one wildcard rule is still a wildcard filter",
            PostFilterRuleKinds.accepts(rules, titleWord("spoiler")))

        rules.removeAt(0)
        assertTrue(PostFilterRuleKinds.accepts(rules, titleWord("spoiler")))
        assertTrue(PostFilterRuleKinds.accepts(rules, contains("india")))
    }

    @Test
    fun `the last rule of a kind can be edited into the other kind`() {
        // Nothing else is left to conflict with it, so this is the in-place conversion: the filter
        // is whatever its one remaining rule is.
        val only = contains("india")

        assertTrue(PostFilterRuleKinds.accepts(listOf(only), exact("india"), replacing = only))
    }

    @Test
    fun `a rule cannot be edited into the other kind while its own kind remains`() {
        val rules = listOf(contains("india"), contains("memes"))

        assertFalse(PostFilterRuleKinds.accepts(rules, exact("india"), replacing = rules[0]))
    }

    @Test
    fun `a rule of a mixed filter can still be edited within its own kind`() {
        // Mixed filters exist: they were saved before this policy did. Editing has to keep working
        // on them or the only way to correct one would be to empty it.
        val mixed = listOf(contains("india"), contains("memes"), titleWord("spoiler"))

        assertTrue(PostFilterRuleKinds.accepts(mixed, contains("indian"), replacing = mixed[0]))
        assertTrue(PostFilterRuleKinds.accepts(mixed, titleWord("leaked"), replacing = mixed[2]))
        // Across kinds is judged like any other add: with a second wildcard rule still there the
        // filter would stay mixed, so it is refused; were 'india' the only one, it would be the
        // in-place conversion above and go through.
        assertFalse(PostFilterRuleKinds.accepts(mixed, exact("india"), replacing = mixed[0]))
        assertTrue(
            PostFilterRuleKinds.accepts(
                listOf(contains("india"), titleWord("spoiler")), exact("india"), replacing = contains("india")
            )
        )
    }

    @Test
    fun `one wildcard rule pins the filter`() {
        assertTrue(PostFilterRuleKinds.hasWildcard(listOf(contains("india"), contains("memes"))))
        assertFalse(PostFilterRuleKinds.hasWildcard(emptyList()))
        assertFalse(PostFilterRuleKinds.hasWildcard(listOf(exact("politics"))))
    }

    @Test
    fun `a mixed filter is pinned too`() {
        // Its wildcard rule runs on r/ContinuumAll and nowhere else, so the feeds stay locked until
        // that rule is deleted — the same way out as any other pinned filter.
        assertTrue(PostFilterRuleKinds.hasWildcard(listOf(contains("india"), titleWord("spoiler"))))
    }

    @Test
    fun `a wildcard form of a field that has none is an ordinary rule`() {
        // Only subreddit names carry the asterisk syntax; anywhere else it is just text.
        assertFalse(PostFilterRuleKinds.isWildcard(FilterRule(RuleField.TITLE_KEYWORD, true, "*india*")))
        assertTrue(
            PostFilterRuleKinds.accepts(
                listOf(titleWord("spoiler")), FilterRule(RuleField.TITLE_KEYWORD, true, "*india*")
            )
        )
    }
}
