package ml.docilealligator.infinityforreddit.markdown

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [RedditUrlEnd] against snudown's `autolink_delim` (issue #480). Reddit's own `body_html` comes from
 * snudown, so it is the reference: `(http://tsfr.org)` is a `test_snudown.py` vector, and the issue's
 * text is checked against the post's `selftext_html`.
 */
class RedditUrlEndTest {

    /**
     * The URL Reddit links in [text], starting at its scheme (or `www.`). Linkify is taken to have
     * ended the URL one character in, the least it could have, which leaves the end entirely to
     * [RedditUrlEnd.end].
     */
    private fun link(text: String): String {
        val start = Regex("https?://|www\\.", RegexOption.IGNORE_CASE).find(text)!!.range.first
        return text.substring(start, RedditUrlEnd.end(text, start, start + 1))
    }

    @Test
    fun `the issue's share link loses the closing parenthesis it is wrapped in`() {
        assertEquals(
            "https://www.reddit.com/r/BoyDinnerDiaries/s/MsFP5ijHqA",
            link("[Previous post] (https://www.reddit.com/r/BoyDinnerDiaries/s/MsFP5ijHqA)")
        )
        assertEquals("http://tsfr.org", link("(http://tsfr.org)"))
    }

    @Test
    fun `what follows the URL does not change where it ends`() {
        for (after in listOf("", " and more", ". Next", "\tx", "\n")) {
            assertEquals(after, "https://example.com/a", link("(https://example.com/a)$after"))
            assertEquals(after, "https://example.com/a", link("see https://example.com/a.$after"))
        }
    }

    @Test
    fun `parentheses the URL opens itself are kept`() {
        assertEquals("https://en.wikipedia.org/wiki/Foo_(bar)", link("https://en.wikipedia.org/wiki/Foo_(bar)"))
        assertEquals("https://en.wikipedia.org/wiki/Foo_(bar)", link("https://en.wikipedia.org/wiki/Foo_(bar) is a page"))
        assertEquals("https://en.wikipedia.org/wiki/Foo_(bar)", link("(https://en.wikipedia.org/wiki/Foo_(bar))"))
        assertEquals("https://example.com/a(b)c", link("https://example.com/a(b)c"))
    }

    /** Snudown drops one unmatched closer, not all of them. */
    @Test
    fun `only one unmatched closing parenthesis goes`() {
        assertEquals("https://example.com/a)", link("((https://example.com/a))"))
    }

    @Test
    fun `trailing sentence punctuation goes`() {
        for (punctuation in listOf(".", ",", "!", "?", "...", "?!", ";")) {
            assertEquals(punctuation, "https://example.com/a", link("see https://example.com/a$punctuation"))
        }
        assertEquals("https://example.com/a", link("(see https://example.com/a)."))
        assertEquals("https://example.com/a", link("(see https://example.com/a), then"))
    }

    /** The punctuation goes before the parenthesis is looked at, and is not looked at again after. */
    @Test
    fun `punctuation inside a trailing parenthesis stays`() {
        assertEquals("https://example.com/a.", link("(see https://example.com/a.)"))
    }

    @Test
    fun `punctuation inside the URL stays`() {
        assertEquals("https://example.com/a.b,c?d=e!f;g", link("https://example.com/a.b,c?d=e!f;g"))
        assertEquals("http://www.google.com?test&blah", link("ampersands http://www.google.com?test&blah"))
    }

    @Test
    fun `square and curly brackets are matched like parentheses`() {
        assertEquals("https://example.com/a", link("[https://example.com/a]"))
        assertEquals("https://example.com/a", link("{https://example.com/a}"))
        assertEquals("https://example.com/[a]", link("https://example.com/[a]"))
    }

    /** Snudown counts every quote as an opener, so a trailing one always goes. */
    @Test
    fun `a trailing quote goes`() {
        assertEquals("https://example.com/a", link("\"https://example.com/a\""))
        assertEquals("https://example.com/a", link("'https://example.com/a'"))
        assertEquals("https://example.com/\"a", link("https://example.com/\"a\""))
    }

    @Test
    fun `the URL is cut at a less-than sign`() {
        assertEquals("https://example.com/a", link("https://example.com/a<b"))
        assertEquals("https://example.com/a", link(">!https://example.com/a!<"))
    }

    @Test
    fun `a trailing HTML entity goes whole`() {
        assertEquals("https://example.com/a", link("https://example.com/a&amp;"))
        assertEquals("https://example.com/a&", link("https://example.com/a&;"))
    }

    @Test
    fun `a www link ends the same way`() {
        assertEquals("www.example.com/a", link("(www.example.com/a)."))
    }

    @Test
    fun `characters Linkify stopped at are taken up to the next whitespace`() {
        assertEquals("https://example.com/a|b", link("https://example.com/a|b c"))
        assertEquals("https://example.com/100%", link("https://example.com/100% sure"))
    }

    /** Linkify allows non-ASCII spaces inside a URL, and so does snudown, so the match is never cut. */
    @Test
    fun `a URL Linkify took across a non-breaking space is not cut there`() {
        val text = "https://example.com/a b"
        assertEquals(text.length, RedditUrlEnd.end(text, 0, text.length))
    }

    @Test
    fun `a URL trimmed to nothing ends at its start`() {
        assertEquals(0, RedditUrlEnd.autolinkDelim("?!.,", 0, 4))
        assertEquals(3, RedditUrlEnd.autolinkDelim("ab <c", 3, 5))
    }

    @Test
    fun `the URL Linkify built is trimmed and extended at its tail`() {
        // Linkify prepends http:// to a URL written without a scheme ...
        assertEquals("http://example.com/a", RedditUrlEnd.resize("http://example.com/a)", "example.com/a)", 14, 13))
        // ... and lowercases one written in capitals; both changes are at the front.
        assertEquals("https://Example.com/a", RedditUrlEnd.resize("https://Example.com/a.", "HTTPS://Example.com/a.", 22, 21))
        val wikipedia = "https://en.wikipedia.org/wiki/Foo_(bar) is"
        assertEquals(
            "https://en.wikipedia.org/wiki/Foo_(bar)",
            RedditUrlEnd.resize("https://en.wikipedia.org/wiki/Foo_(bar", wikipedia, 38, 39)
        )
        assertEquals("https://example.com/a", RedditUrlEnd.resize("https://example.com/a", "https://example.com/a", 21, 21))
    }
}
