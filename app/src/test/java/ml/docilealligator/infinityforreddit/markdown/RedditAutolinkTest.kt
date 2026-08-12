package ml.docilealligator.infinityforreddit.markdown

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [RedditAutolink] is a port of snudown, so the cases below are transcribed from snudown's own
 * `test_snudown.py` — each `expectLink`/`expectNoLink` pair states what reddit.com itself renders
 * for that input. Anything that disagrees here renders differently from the site the comment was
 * written for, which is the whole class of bug issue #356 belongs to.
 *
 * The remaining tests cover what snudown gets for free from its inline parser but this port has to
 * find by hand: code, existing links, and bare URLs must be left alone.
 */
class RedditAutolinkTest {

    /** Asserts the whole of `reference` becomes one link, pointing at `path` on reddit.com. */
    private fun expectLink(input: String, reference: String, path: String) {
        val expected = input.replace(reference, "[$reference](https://www.reddit.com$path)")
        assertEquals("wrong autolink for \"$input\"", expected, RedditAutolink.linkify(input))
    }

    private fun expectNoLink(input: String) {
        assertEquals("expected no autolink in \"$input\"", input, RedditAutolink.linkify(input))
    }

    // ---- issue #356: the trailing path is part of the link ----

    @Test
    fun `links a post permalink written without the domain`() {
        expectLink(
            "If someone doesnt't know (somehow)\n\n" +
                "r/sysadmin/comments/1uvz7ns/telstra_australias_largest_telco_blames_outage_on/",
            "r/sysadmin/comments/1uvz7ns/telstra_australias_largest_telco_blames_outage_on/",
            "/r/sysadmin/comments/1uvz7ns/telstra_australias_largest_telco_blames_outage_on/",
        )
    }

    @Test
    fun `links paths after a subreddit or username`() {
        expectLink("/r/test/m/test test", "/r/test/m/test", "/r/test/m/test")
        expectLink("/r/test/w/test test", "/r/test/w/test", "/r/test/w/test")
        expectLink("/r/test/comments/test test", "/r/test/comments/test", "/r/test/comments/test")
        expectLink("/u/test/m/test test", "/u/test/m/test", "/u/test/m/test")
        expectLink("a u/reddit/foobaz", "u/reddit/foobaz", "/u/reddit/foobaz")
        expectLink(
            "/r/test/commentscommentscommentscommentscommentscommentscomments/test test",
            "/r/test/commentscommentscommentscommentscommentscommentscomments/test",
            "/r/test/commentscommentscommentscommentscommentscommentscomments/test",
        )
    }

    @Test
    fun `stops the path at characters reddit excludes`() {
        expectLink("/r/irc://foo.bar/", "/r/irc", "/r/irc")
        expectLink("/r/t:irc//foo.bar/", "/r/t:irc//foo", "/r/t:irc//foo")
        expectLink("/r/all-irc://foo.bar/", "/r/all-irc", "/r/all-irc")
        expectLink("/r/foo+irc://foo.bar/", "/r/foo+irc", "/r/foo+irc")
        expectLink("/r/whatever: fork", "/r/whatever", "/r/whatever")
    }

    // ---- the boundary before the reference ----

    @Test
    fun `links after punctuation and whitespace but not after a letter`() {
        expectLink("Words words /r/test words", "/r/test", "/r/test")
        expectLink("a u/reddit", "u/reddit", "/u/reddit")
        expectLink("foo:u/reddit", "u/reddit", "/u/reddit")
        expectLink("foo:r/reddit.com", "r/reddit.com", "/r/reddit.com")
        expectLink("*u/me*", "u/me", "/u/me")
        expectLink("foo^u/me", "u/me", "/u/me")
        expectLink("*foo*u/me", "u/me", "/u/me")
        expectNoLink("fuu/reddit")
        expectNoLink("foobar/reddit.com")
    }

    @Test
    fun `links at the very start of the text`() {
        expectLink("u/reddit", "u/reddit", "/u/reddit")
        expectLink("/r/test", "/r/test", "/r/test")
    }

    /** Snudown uses C's byte-oriented ispunct/isspace, so a non-ASCII boundary blocks the link. */
    @Test
    fun `does not link after a non-ascii boundary`() {
        expectNoLink("a\u3002u/reddit")
        expectNoLink("a\u3002r/reddit.com")
    }

    @Test
    fun `honors backslash escaping of the leading slash`() {
        expectNoLink("escaped \\/r/test")
        expectNoLink("\\/u/me")
        expectNoLink("u\\/me")
        expectLink("\\\\/u/me", "/u/me", "/u/me")
        expectLink("\\\\u/me", "u/me", "/u/me")
        // Snudown links "\u/me" too, but it emits HTML; this emits markdown, where an odd
        // backslash run escapes the inserted "[" into literal text. See canOpenLink.
        expectNoLink("\\u/me")
    }

    // ---- the reference itself ----

    @Test
    fun `is case sensitive on the r and u`() {
        expectNoLink("/R/reddit.com")
        expectNoLink("/U/nope")
        expectNoLink("R/pics")
        expectNoLink("U/spez")
    }

    @Test
    fun `ignores other single letter prefixes`() {
        expectNoLink("/f/oobar")
        expectNoLink("f/oobar")
    }

    @Test
    fun `requires a name of the right length`() {
        expectNoLink("/r/")
        expectNoLink("u/m")
        expectNoLink("/u/m")
        expectLink("/r/sr_with_underscores", "/r/sr_with_underscores", "/r/sr_with_underscores")
    }

    @Test
    fun `links multireddits joined with plus`() {
        expectLink("/r/multireddit+test+yay", "/r/multireddit+test+yay", "/r/multireddit+test+yay")
        expectLink(
            "/r/very+clever+multireddit+reddit.com+t:fork+yay",
            "/r/very+clever+multireddit+reddit.com+t:fork+yay",
            "/r/very+clever+multireddit+reddit.com+t:fork+yay",
        )
    }

    @Test
    fun `treats hyphens as separators only after all-`() {
        expectLink("/r/all-minus-something", "/r/all-minus-something", "/r/all-minus-something")
        expectLink("/r/notall-minus", "/r/notall", "/r/notall")
    }

    @Test
    fun `handles the timereddit and reddit_com special names`() {
        expectLink("/r/t:timereddit", "/r/t:timereddit", "/r/t:timereddit")
        expectLink("/r/t:heatdeathoftheuniverse", "/r/t:heatdeathoftheuniverse", "/r/t:heatdeathoftheuniverse")
        expectLink("/r/reddit.com", "/r/reddit.com", "/r/reddit.com")
        expectLink("a /r/reddit.com", "/r/reddit.com", "/r/reddit.com")
        expectLink("a r/reddit.com", "r/reddit.com", "/r/reddit.com")
        expectLink("/r/not.cool", "/r/not", "/r/not")
        expectLink("/r/www.example.com", "/r/www", "/r/www")
        // reddit.com's length ceiling is exact, so a longer name starting with it links nothing.
        expectNoLink("/r/reddit.commission")
    }

    @Test
    fun `adds the leading slash to the url but not the label`() {
        assertEquals(
            "[r/pics](https://www.reddit.com/r/pics)",
            RedditAutolink.linkify("r/pics"),
        )
        assertEquals(
            "[/r/pics](https://www.reddit.com/r/pics)",
            RedditAutolink.linkify("/r/pics"),
        )
    }

    // ---- constructs snudown's inline parser consumes before autolinking ----

    @Test
    fun `leaves existing markdown links alone`() {
        expectNoLink("[A link with a /r/subreddit in it](/lol)")
        expectNoLink("[label](https://www.reddit.com/r/sysadmin/comments/1uvz7ns/slug/)")
        expectNoLink("![alt r/pics](https://i.redd.it/abc.png)")
    }

    /**
     * The one place this port deliberately parts company with snudown, found by diffing it against
     * `body_html` for 115 live comments — the only disagreement in the set.
     *
     * Snudown rejects a scheme-less relative destination, so it renders `[r/help](r/help)` as
     * literal brackets and autolinks the bare text inside both of them. CommonMark accepts relative
     * destinations, so Markwon renders it as an ordinary link either way; autolinking inside it
     * would only nest a second `[…](…)` in the label and break the link that already works.
     */
    @Test
    fun `leaves a relative destination link alone even though snudown autolinks inside it`() {
        expectNoLink("Scroll thru [r/help](r/help) and [r/bugs](r/bugs)")
    }

    @Test
    fun `leaves bare urls alone`() {
        expectNoLink("bare https://www.reddit.com/r/sysadmin/comments/1uvz7ns/slug/ url")
        expectNoLink("http://www.reddit.com/r/pics")
        expectNoLink("see www.reddit.com/r/pics here")
        expectNoLink("<https://www.reddit.com/r/pics>")
    }

    @Test
    fun `leaves code alone`() {
        expectNoLink("use `r/pics` in a span")
        expectNoLink("``a ` and r/pics``")
        expectNoLink("```\nr/pics\n```")
        expectNoLink("~~~\nr/pics\n~~~")
        expectNoLink("paragraph\n\n    r/pics in indented code")
    }

    @Test
    fun `still links an indented line that continues a paragraph`() {
        expectLink("paragraph\n    r/pics", "r/pics", "/r/pics")
    }

    @Test
    fun `links normally around skipped regions`() {
        expectLink(
            "see [docs](https://example.com/r/nope) then r/pics",
            "r/pics",
            "/r/pics",
        )
        assertEquals(
            "[r/aww](https://www.reddit.com/r/aww) `r/pics` [r/news](https://www.reddit.com/r/news)",
            RedditAutolink.linkify("r/aww `r/pics` r/news"),
        )
    }

    // ---- whole-string behavior ----

    @Test
    fun `returns the input untouched when there is nothing to link`() {
        val input = "no references here at all"
        assertEquals(input, RedditAutolink.linkify(input))
        assertEquals("", RedditAutolink.linkify(""))
    }

    @Test
    fun `links every reference in a multi line body`() {
        assertEquals(
            "[r/one](https://www.reddit.com/r/one) and\n[u/two](https://www.reddit.com/u/two)",
            RedditAutolink.linkify("r/one and\nu/two"),
        )
    }
}
