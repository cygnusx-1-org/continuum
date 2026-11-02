package ml.docilealligator.infinityforreddit.markdown

/**
 * Where Reddit ends a bare URL, ported from snudown (`src/autolink.c`: `sd_autolink__url`,
 * `sd_autolink__www`, `autolink_delim`). [RedditLinkifyPlugin] lets Android's `Linkify` find the
 * URL and asks this where it stops.
 *
 * Snudown runs a URL to the next whitespace, then trims it:
 *  * at the first `<`;
 *  * any trailing `?`, `!`, `.` and `,`, and a trailing `;` — together with the `&name` before it
 *    when it closes an HTML entity;
 *  * then one closing `)`, `]` or `}` when the URL's own opening and closing brackets differ in
 *    number. Brackets outside the URL don't count: in `(https://example.com/a)` the `)` goes
 *    because the URL holds no `(`, and in `https://example.com/Foo_(bar)` it stays. Only one goes,
 *    so `https://example.com/a))` keeps a `)`, as it does on Reddit;
 *  * a trailing `"` or `'` likewise, except that snudown counts every quote as an opener, so one
 *    always goes.
 */
internal object RedditUrlEnd {

    /**
     * End (exclusive) of the URL that starts at [start] in [text], or [start] if snudown would trim
     * it away entirely. [linkifyEnd] is where `Linkify` ended it. The URL runs on from there to the
     * next whitespace, as snudown's does, before it is trimmed; Linkify's match itself is never cut at
     * whitespace, because Linkify allows non-ASCII spaces inside a URL and so does snudown.
     */
    fun end(text: CharSequence, start: Int, linkifyEnd: Int): Int {
        var end = linkifyEnd
        while (end < text.length && !text[end].isWhitespace()) {
            end++
        }
        return autolinkDelim(text, start, end)
    }

    /**
     * [url] — `Linkify`'s URL for `text[start, linkifyEnd)` — ending at [end] instead. Linkify only
     * changes the front of a URL (lowercasing the scheme, or prepending `http://`), so the tail is
     * the text verbatim and can be cut or extended in place.
     */
    fun resize(url: String, text: CharSequence, linkifyEnd: Int, end: Int): String =
        if (end >= linkifyEnd) {
            url + text.subSequence(linkifyEnd, end)
        } else {
            url.substring(0, url.length - (linkifyEnd - end))
        }

    /** Snudown's `autolink_delim` over `text[start, end)`: the end after trimming. */
    fun autolinkDelim(text: CharSequence, start: Int, end: Int): Int {
        var linkEnd = end
        for (i in start until linkEnd) {
            if (text[i] == '<') {
                linkEnd = i
                break
            }
        }

        while (linkEnd > start) {
            val c = text[linkEnd - 1]
            if (c == '\u0000') {
                break
            }
            if (c == '?' || c == '!' || c == '.' || c == ',') {
                linkEnd--
            } else if (c == ';') {
                var entityStart = linkEnd - 2
                while (entityStart > start && isAsciiLetter(text[entityStart])) {
                    entityStart--
                }
                linkEnd = if (entityStart < linkEnd - 2 && text[entityStart] == '&') entityStart else linkEnd - 1
            } else {
                break
            }
        }

        if (linkEnd == start) {
            return start
        }

        val close = text[linkEnd - 1]
        val open = when (close) {
            '"' -> '"'
            '\'' -> '\''
            ')' -> '('
            ']' -> '['
            '}' -> '{'
            else -> return linkEnd
        }
        var opening = 0
        var closing = 0
        for (i in start until linkEnd) {
            // Tested for the opener first, so a quote — its own opener — only ever counts as one.
            if (text[i] == open) {
                opening++
            } else if (text[i] == close) {
                closing++
            }
        }
        return if (closing != opening) linkEnd - 1 else linkEnd
    }

    private fun isAsciiLetter(c: Char): Boolean = c in 'a'..'z' || c in 'A'..'Z'
}
