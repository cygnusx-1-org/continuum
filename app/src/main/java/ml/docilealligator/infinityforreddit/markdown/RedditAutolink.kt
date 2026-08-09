package ml.docilealligator.infinityforreddit.markdown

import java.util.regex.Pattern

/**
 * Reddit's autolinker for bare `r/subreddit` and `u/username` references, ported from snudown
 * (`src/autolink.c`: `sd_autolink__subreddit`, `sd_autolink__username`,
 * `check_reddit_autolink_prefix`).
 *
 * Slide renders Reddit's own `body_html` and inherits this for free. Continuum renders the raw
 * `body` markdown through Markwon, a CommonMark engine with no concept of a subreddit, so the
 * references have to be rewritten into explicit `[label](url)` links before Markwon sees them.
 * Matching snudown exactly is the point — anything looser or tighter renders the text differently
 * from the site it was written for.
 *
 * The rules that are easy to get wrong, all pinned by `RedditAutolinkTest` against snudown's own
 * `test_snudown.py` vectors:
 *  * The `r`/`u` is **case-sensitive**. `/R/pics` and `/U/spez` are not links on Reddit.
 *  * A reference may be followed by a path: `r/sub/comments/<id>/<slug>/` links in full. The path
 *    charset is `[A-Za-z0-9_/-]`, so it stops at `.`, `:` or `?` — `/r/irc://foo` links `/r/irc`.
 *  * The character before the `r`/`u` must be ASCII punctuation, ASCII whitespace, `/`, or nothing.
 *    A letter or digit blocks it (`fuu/reddit` is not a link).
 *  * Subreddit names are 2-24 of `[A-Za-z0-9_]`, may be joined with `+` into a multireddit, and
 *    accept two special forms: the `t:` timereddit prefix and the literal `reddit.com`. Hyphens
 *    only separate components when the reference began with `all-`, so `/r/all-minus-something`
 *    links whole but `/r/notall-minus` links only `/r/notall`.
 *  * Usernames have no length ceiling and no `+` handling, but need at least two characters.
 *
 * Autolinking is suppressed inside code, existing links, and bare URLs — see [skipRegions] — and a
 * reference stops at an emphasis closer rather than eating the `_` — see [emphasisClosers].
 * Snudown gets both for free because its inline parser consumes those constructs before the
 * autolinker is reached; here they have to be found explicitly, which is why [addCodeBlocks] keeps
 * a stack of block containers: what counts as an indented code block depends on the blockquotes
 * and list items a line sits inside.
 */
object RedditAutolink {

    /** Wraps every snudown-recognized `r/…` and `u/…` reference in `markdown` as a markdown link. */
    @JvmStatic
    fun linkify(markdown: String): String {
        if (!hasCandidate(markdown)) {
            return markdown
        }

        val skipped = skipRegions(markdown)
        val closers = emphasisClosers(markdown, skipped)
        val result = StringBuilder(markdown.length + 64)
        var copied = 0
        var index = 0
        var skipIndex = 0
        var closerIndex = 0
        // Snudown's `size`. Recomputed only when the scan leaves the block it was measured for,
        // which keeps the whole pass linear.
        var blockEnd = 0

        while (index < markdown.length) {
            if (markdown[index] != '/') {
                index++
                continue
            }

            while (skipIndex < skipped.size && skipped[skipIndex][1] <= index) {
                skipIndex++
            }
            if (skipIndex < skipped.size && index >= skipped[skipIndex][0]) {
                index = skipped[skipIndex][1]
                continue
            }

            val prefixIndex = index - 1
            if (prefixIndex < 0) {
                index++
                continue
            }
            val prefix = markdown[prefixIndex]
            if (prefix != 'r' && prefix != 'u') {
                index++
                continue
            }

            val rewind = rewind(markdown, prefixIndex)
            if (rewind == 0) {
                index++
                continue
            }

            val start = index - rewind
            if (!canOpenLink(markdown, start)) {
                index++
                continue
            }

            if (index >= blockEnd) {
                blockEnd = blockEnd(markdown, index)
            }
            // `_` is both a name character and an emphasis delimiter, and a reference stops at the
            // closer. `start` never decreases, so this pointer only moves forward.
            while (closerIndex < closers.size && closers[closerIndex] <= start) {
                closerIndex++
            }
            val limit = if (closerIndex < closers.size) minOf(blockEnd, closers[closerIndex]) else blockEnd
            val end = if (prefix == 'r') {
                parseSubreddit(markdown, index, limit)
            } else {
                parseUsername(markdown, index, limit)
            }
            if (end < 0) {
                index++
                continue
            }

            val label = markdown.substring(start, end)
            result.append(markdown, copied, start)
            result.append('[').append(label).append("](https://www.reddit.com")
            // rewind == 1 means the reference was written without its leading slash, so the URL
            // needs one added; rewind == 2 means the label already carries it.
            if (rewind == 1) {
                result.append('/')
            }
            result.append(label).append(')')
            copied = end
            index = end
        }

        if (copied == 0) {
            return markdown
        }
        result.append(markdown, copied, markdown.length)
        return result.toString()
    }

    /** True if any `/` is preceded by an `r` or `u`, i.e. the scan below could find anything. */
    private fun hasCandidate(markdown: String): Boolean {
        for (index in 1 until markdown.length) {
            if (markdown[index] == '/') {
                val prefix = markdown[index - 1]
                if (prefix == 'r' || prefix == 'u') {
                    return true
                }
            }
        }
        return false
    }

    /**
     * How many characters before `/` the link label starts at, or 0 if this is not a link position.
     * Mirrors `check_reddit_autolink_prefix`: 2 when a `/` precedes the `r`/`u` (the label includes
     * it), 1 otherwise.
     */
    private fun rewind(markdown: String, prefixIndex: Int): Int {
        if (prefixIndex == 0) {
            return 1
        }
        val boundary = markdown[prefixIndex - 1]
        if (boundary == '/') {
            // Snudown's escape handler eats `\/` before the autolinker runs, which is how `\/r/foo`
            // stays literal while `\\/r/foo` (an escaped backslash) still links. Reading the raw
            // markdown instead, that distinction is the parity of the backslash run.
            var backslashes = 0
            var scan = prefixIndex - 2
            while (scan >= 0 && markdown[scan] == '\\') {
                backslashes++
                scan--
            }
            return if (backslashes % 2 == 1) 0 else 2
        }
        // Snudown uses C's byte-oriented ispunct/isspace here, so a non-ASCII boundary blocks the
        // link — "a。u/reddit" is deliberately not a link, and test_snudown.py pins that.
        return if (isAsciiPunct(boundary) || isAsciiSpace(boundary)) 1 else 0
    }

    /**
     * Whether a `[` inserted at [start] would actually open a link.
     *
     * Snudown emits HTML and so never has to care what precedes the reference, but this rewrites
     * markdown that Markwon then parses, and two characters change what the inserted `[` means:
     * `!` makes it an image (`>!r/pics!<`, a spoiler, would render as a broken image rather than a
     * link), and an odd run of backslashes escapes it (`\r/pics` would render as the literal text
     * `[r/pics](https://…)`). Dropping the link is the safe side of that trade — it is what the
     * previous whitespace-only rule did for these inputs anyway.
     */
    private fun canOpenLink(markdown: String, start: Int): Boolean {
        if (start == 0) {
            return true
        }
        if (markdown[start - 1] == '!') {
            return false
        }
        var backslashes = 0
        var scan = start - 1
        while (scan >= 0 && markdown[scan] == '\\') {
            backslashes++
            scan--
        }
        return backslashes % 2 == 0
    }

    private val NO_CLOSERS = IntArray(0)

    /**
     * Sorted start positions of the `_` runs that close an emphasis span, which a reference must
     * stop at.
     *
     * `_` is both a legal name character and an emphasis delimiter, and snudown never has to
     * choose: its inline parser consumes `_see r/pics_`'s delimiters and hands the autolinker only
     * `see r/pics`. Reading raw markdown, the trailing `_` looks like part of the name, so the
     * reference links to a nonexistent `/r/pics_` and the italics break along with it. Pairing the
     * runs here is what tells that apart from `u/foo_`, a username that really does end in one.
     *
     * The open/close conditions are snudown's, which runs with `MKDEXT_NO_INTRA_EMPHASIS`: a run
     * opens only where a word does not continue into it, and closes only where a word does not
     * continue out of it. `*` counts as an opening boundary because snudown reaches `_` inside
     * `**_r/pics_**` only after consuming the `**`, which puts the `_` at the start of the buffer
     * it then parses. Pairing is per block, since a paragraph break ends any span left open, and
     * runs inside [skipRegions] are invisible to it just as they are to the inline parser.
     */
    private fun emphasisClosers(markdown: String, skipped: List<IntArray>): IntArray {
        if (markdown.indexOf('_') < 0) {
            return NO_CLOSERS
        }

        val closers = ArrayList<Int>()
        var opened = 0
        var index = 0
        var skipIndex = 0
        var lineEnd = lineEnd(markdown, 0)

        while (index < markdown.length) {
            if (index >= lineEnd) {
                val nextStart = lineEnd + 1
                lineEnd = lineEnd(markdown, nextStart)
                if (isBlank(markdown, nextStart, lineEnd)) {
                    opened = 0
                }
                if (index < nextStart) {
                    index = nextStart
                }
                continue
            }

            while (skipIndex < skipped.size && skipped[skipIndex][1] <= index) {
                skipIndex++
            }
            if (skipIndex < skipped.size && index >= skipped[skipIndex][0]) {
                index = skipped[skipIndex][1]
                continue
            }

            if (markdown[index] != '_') {
                index++
                continue
            }

            var runEnd = index
            while (runEnd < markdown.length && markdown[runEnd] == '_') {
                runEnd++
            }
            // A run at the edge of the text behaves as if whitespace sat outside it, which is what
            // makes "_r/pics_" on a line of its own a span.
            val before = if (index > 0) markdown[index - 1] else '\n'
            val after = if (runEnd < markdown.length) markdown[runEnd] else '\n'
            val canClose = !isAsciiSpace(before) && !isAsciiAlnum(after)
            val canOpen = !isAsciiSpace(after) &&
                (isAsciiSpace(before) || before == '>' || before == '(' || before == '*')
            if (canClose && opened > 0) {
                closers.add(index)
                opened--
            } else if (canOpen) {
                opened++
            }
            index = runEnd
        }

        return closers.toIntArray()
    }

    /** End index of the subreddit reference starting at `slash`, or -1. `sd_autolink__subreddit`. */
    private fun parseSubreddit(markdown: String, slash: Int, limit: Int): Int {
        var position = slash + 1
        val isAllMinus = limit >= position + 4 &&
            markdown.regionMatches(position, "all-", 0, 4, ignoreCase = true)

        while (true) {
            val start = position
            var maxLength = 24

            if (limit >= position + 10 &&
                markdown.regionMatches(position, "reddit.com", 0, 10, ignoreCase = true)
            ) {
                // The one subreddit whose name contains a dot. Pinning maxLength to its exact
                // length is what makes "/r/reddit.commission" link nothing at all rather than
                // linking a truncated "/r/reddit.com".
                position += 10
                maxLength = 10
            } else {
                if (limit > position + 2 && markdown.regionMatches(position, "t:", 0, 2, ignoreCase = true)) {
                    position += 2
                }
                if (position >= limit || !isAsciiAlnum(markdown[position])) {
                    return -1
                }
                position++
            }

            while (position < limit && (isAsciiAlnum(markdown[position]) || markdown[position] == '_')) {
                position++
            }

            val length = position - start
            if (length < 2 || length > maxLength) {
                return -1
            }

            val separator = if (position < limit) markdown[position] else ' '
            if (separator == '+' || (isAllMinus && separator == '-')) {
                position++
            } else {
                break
            }
        }

        return consumePath(markdown, position, limit, requireSlash = true)
    }

    /** End index of the username reference starting at `slash`, or -1. `sd_autolink__username`. */
    private fun parseUsername(markdown: String, slash: Int, limit: Int): Int {
        // Snudown's `size < 3` guard, which is why a bare "u/m" at the end of a block is not a link.
        if (limit - slash < 3) {
            return -1
        }
        var position = slash + 1
        val first = markdown[position]
        if (!isAsciiAlnum(first) && first != '_' && first != '-') {
            return -1
        }
        position++
        return consumePath(markdown, position, limit, requireSlash = false)
    }

    /** Consumes the trailing path, the part continuum was previously dropping (issue #356). */
    private fun consumePath(markdown: String, from: Int, limit: Int, requireSlash: Boolean): Int {
        var position = from
        if (requireSlash && (position >= limit || markdown[position] != '/')) {
            return position
        }
        while (position < limit && isPathCharacter(markdown[position])) {
            position++
        }
        return position
    }

    private fun isPathCharacter(character: Char): Boolean =
        isAsciiAlnum(character) || character == '_' || character == '/' || character == '-'

    private fun isAsciiAlnum(character: Char): Boolean =
        character in '0'..'9' || character in 'A'..'Z' || character in 'a'..'z'

    private fun isAsciiSpace(character: Char): Boolean =
        character == ' ' || character in '\t'..'\r'

    private fun isAsciiPunct(character: Char): Boolean =
        character in '!'..'/' || character in ':'..'@' || character in '['..'`' || character in '{'..'~'

    private fun lineEnd(markdown: String, from: Int): Int {
        val newline = markdown.indexOf('\n', from)
        return if (newline < 0) markdown.length else newline
    }

    /**
     * End of the block containing [from] — snudown's `size`, which measures the paragraph buffer
     * being inline-parsed rather than the line.
     *
     * Only [parseUsername]'s two-character minimum can observe the difference, because every
     * consumption loop stops at a newline anyway (no charset here contains one) and the `all-` /
     * `reddit.com` / `t:` lookaheads can only match literals that contain none. So a blank line —
     * what ends a paragraph — is the only boundary worth modelling; a heading or list interrupting
     * a paragraph would end the block slightly earlier, but the only reference that could tell is a
     * one-character username, and Reddit's three-character minimum means no real one exists.
     */
    private fun blockEnd(markdown: String, from: Int): Int {
        var position = from
        while (true) {
            val lineEnd = lineEnd(markdown, position)
            if (lineEnd == markdown.length) {
                return markdown.length
            }
            val nextLineEnd = lineEnd(markdown, lineEnd + 1)
            if (isBlank(markdown, lineEnd + 1, nextLineEnd)) {
                return lineEnd
            }
            position = lineEnd + 1
        }
    }

    private fun isBlank(markdown: String, from: Int, to: Int): Boolean {
        for (index in from until to) {
            if (markdown[index] != ' ' && markdown[index] != '\t' && markdown[index] != '\r') {
                return false
            }
        }
        return true
    }

    // Constructs that must not be rewritten. Snudown never reaches the autolinker inside these
    // because its inline parser has already consumed them; the URL cases matter most, since
    // "https://www.reddit.com/r/pics" contains a perfectly valid-looking "/r/pics".
    private val MARKDOWN_LINK: Pattern =
        Pattern.compile("""!?\[(?:[^\[\]]|\\\[|\\\])*]\([^)]*\)""")
    // The scheme alternative is anchored to the start of its run — the lookbehind and the
    // possessive quantifier are both load-bearing. Without them the engine restarts the scheme
    // scan at every character of a long run of `[a-zA-Z0-9+.-]`, which is quadratic: a
    // 10,000-character comment with no whitespace took 300ms, and a wiki page (parsed on the main
    // thread in WikiActivity) four times that. A run can only be entered at its first character,
    // and `:` is outside the class, so neither changes which text matches.
    private val BARE_URL: Pattern =
        Pattern.compile("""(?<![a-zA-Z0-9+.\-])[a-zA-Z0-9][a-zA-Z0-9+.\-]*+://\S*|(?<![\w.])www\.\S*""")
    private val ANGLE_AUTOLINK: Pattern = Pattern.compile("""<[^\s<>]+>""")

    /** Character ranges, sorted and merged, that [linkify] must leave alone. */
    private fun skipRegions(markdown: String): List<IntArray> {
        val regions = ArrayList<IntArray>()
        addCodeBlocks(markdown, regions)
        addCodeSpans(markdown, regions)
        for (pattern in arrayOf(MARKDOWN_LINK, BARE_URL, ANGLE_AUTOLINK)) {
            val matcher = pattern.matcher(markdown)
            while (matcher.find()) {
                regions.add(intArrayOf(matcher.start(), matcher.end()))
            }
        }

        if (regions.size < 2) {
            return regions
        }
        regions.sortWith(compareBy { it[0] })
        val merged = ArrayList<IntArray>(regions.size)
        var current = regions[0]
        for (index in 1 until regions.size) {
            val next = regions[index]
            if (next[0] <= current[1]) {
                if (next[1] > current[1]) {
                    current = intArrayOf(current[0], next[1])
                }
            } else {
                merged.add(current)
                current = next
            }
        }
        merged.add(current)
        return merged
    }

    /** A blockquote on the container stack; any other value is a list item's content indent. */
    private const val BLOCK_QUOTE = -1

    /**
     * Adds every fenced and indented code block.
     *
     * Indentation only means "code" relative to the block containers a line sits in, so the
     * containers have to be tracked: four spaces after a `>` is quoted code, while a list item's
     * continuation indented to its content column is that item's paragraph. Measuring against
     * column zero instead gets both backwards — it rewrites references inside quoted code, and
     * drops them from indented list continuations.
     */
    private fun addCodeBlocks(markdown: String, regions: MutableList<IntArray>) {
        val containers = ArrayList<Int>()
        var fenceCharacter = ' '
        var fenceLength = 0
        var fenceDepth = 0
        // Whether a paragraph is open in the innermost container: an indented line continues it
        // rather than opening an indented code block.
        var paragraphOpen = false
        var lineStart = 0

        while (lineStart <= markdown.length) {
            val lineEnd = lineEnd(markdown, lineStart)
            val line = expandTabs(markdown, lineStart, lineEnd)
            val openBefore = containers.size
            var position = matchContainers(line, containers)

            // A fence lives in the containers that were open when it started, and closes with them.
            if (fenceLength > 0 && containers.size < fenceDepth) {
                fenceLength = 0
            }

            if (fenceLength > 0) {
                regions.add(intArrayOf(lineStart, lineEnd))
                if (isFenceClose(line, position, fenceCharacter, fenceLength)) {
                    fenceLength = 0
                }
            } else {
                // A line that closed a container is starting a new block, not continuing the
                // paragraph that container held — which is what lets "2." open the second item of
                // a list whose first item ends in a paragraph.
                val depthBefore = containers.size
                position = openContainers(line, position, containers, paragraphOpen && containers.size == openBefore)
                if (containers.size > depthBefore) {
                    paragraphOpen = false
                }

                val indent = spacesAt(line, position)
                val content = position + indent
                val fenceRun = if (indent <= 3 && content < line.length &&
                    (line[content] == '`' || line[content] == '~')
                ) {
                    runLengthAt(line, content)
                } else {
                    0
                }

                if (content >= line.length) {
                    // Blank once the container prefixes are off, which is what closes a paragraph.
                    paragraphOpen = false
                } else if (fenceRun >= 3) {
                    fenceCharacter = line[content]
                    fenceLength = fenceRun
                    fenceDepth = containers.size
                    regions.add(intArrayOf(lineStart, lineEnd))
                    // A fence is a leaf block, so no paragraph is left open across it.
                    paragraphOpen = false
                } else if (indent >= 4 && !paragraphOpen) {
                    regions.add(intArrayOf(lineStart, lineEnd))
                } else {
                    paragraphOpen = true
                }
            }

            if (lineEnd == markdown.length) {
                return
            }
            lineStart = lineEnd + 1
        }
    }

    /**
     * Consumes the prefixes of the containers [line] still matches, drops the rest from
     * [containers], and returns where the innermost surviving container's content starts.
     */
    private fun matchContainers(line: String, containers: MutableList<Int>): Int {
        var position = 0
        var matched = 0
        while (matched < containers.size) {
            val container = containers[matched]
            val next = if (container == BLOCK_QUOTE) {
                matchQuote(line, position)
            } else {
                matchListItem(line, position, container)
            }
            if (next < 0) {
                break
            }
            position = next
            matched++
        }
        while (containers.size > matched) {
            containers.removeAt(containers.size - 1)
        }
        return position
    }

    /** Consumes a `>` marker and the optional space after it, or -1 if the line has none. */
    private fun matchQuote(line: String, from: Int): Int {
        val indent = spacesAt(line, from)
        if (indent > 3) {
            return -1
        }
        var position = from + indent
        if (position >= line.length || line[position] != '>') {
            return -1
        }
        position++
        if (position < line.length && line[position] == ' ') {
            position++
        }
        return position
    }

    /** Consumes a list item's content indent, or -1 if the line is not indented that far. */
    private fun matchListItem(line: String, from: Int, indent: Int): Int {
        // A blank line does not close a list item; the next non-blank one decides.
        if (isBlankFrom(line, from)) {
            return from
        }
        return if (spacesAt(line, from) >= indent) from + indent else -1
    }

    /**
     * Opens the containers that start on this line, and returns where their content starts.
     * [paragraphOpen] blocks the markers that may not interrupt a paragraph.
     */
    private fun openContainers(
        line: String,
        from: Int,
        containers: MutableList<Int>,
        paragraphOpen: Boolean,
    ): Int {
        var position = from
        var interrupting = paragraphOpen
        while (true) {
            val indent = spacesAt(line, position)
            // Four columns in, the line is code rather than the start of a container.
            if (indent >= 4) {
                return position
            }
            val marker = position + indent
            if (marker >= line.length) {
                return position
            }

            if (line[marker] == '>') {
                containers.add(BLOCK_QUOTE)
                position = marker + 1
                if (position < line.length && line[position] == ' ') {
                    position++
                }
                interrupting = false
                continue
            }

            val markerLength = listMarkerLength(line, marker, interrupting)
            if (markerLength == 0) {
                return position
            }
            val afterMarker = marker + markerLength
            val spaces = spacesAt(line, afterMarker)
            // An item whose content is blank, or more than four columns past its marker, holds its
            // content one column past the marker; the rest counts as the content's own indent.
            val padding = if (spaces in 1..4 && afterMarker + spaces < line.length) spaces else 1
            containers.add(indent + markerLength + padding)
            position = minOf(afterMarker + padding, line.length)
            interrupting = false
        }
    }

    /** Length of the list marker at [marker], or 0 if there is none this line can start with. */
    private fun listMarkerLength(line: String, marker: Int, interrupting: Boolean): Int {
        val character = line[marker]
        if (character == '-' || character == '+' || character == '*') {
            return if (startsItemContent(line, marker + 1, interrupting)) 1 else 0
        }

        var position = marker
        while (position < line.length && line[position] in '0'..'9' && position - marker < 9) {
            position++
        }
        if (position == marker || position >= line.length) {
            return 0
        }
        if (line[position] != '.' && line[position] != ')') {
            return 0
        }
        // A list may only interrupt a paragraph when it starts at 1, which is what keeps
        // "I read it in\n1984. It was a good year" from becoming a list.
        if (interrupting && (position - marker != 1 || line[marker] != '1')) {
            return 0
        }
        return if (startsItemContent(line, position + 1, interrupting)) position + 1 - marker else 0
    }

    /** A marker needs a space after it, and non-blank content when it interrupts a paragraph. */
    private fun startsItemContent(line: String, from: Int, interrupting: Boolean): Boolean {
        if (from < line.length && line[from] != ' ') {
            return false
        }
        return !(interrupting && isBlankFrom(line, from))
    }

    /** Whether [line] from [from] is a closing fence for the run given. */
    private fun isFenceClose(line: String, from: Int, fenceCharacter: Char, fenceLength: Int): Boolean {
        val indent = spacesAt(line, from)
        if (indent > 3) {
            return false
        }
        var position = from + indent
        var run = 0
        while (position < line.length && line[position] == fenceCharacter) {
            position++
            run++
        }
        return run >= fenceLength && isBlankFrom(line, position)
    }

    private fun spacesAt(line: String, from: Int): Int {
        var index = from
        while (index < line.length && line[index] == ' ') {
            index++
        }
        return index - from
    }

    private fun runLengthAt(line: String, from: Int): Int {
        val character = line[from]
        var index = from
        while (index < line.length && line[index] == character) {
            index++
        }
        return index - from
    }

    private fun isBlankFrom(line: String, from: Int): Boolean {
        for (index in from until line.length) {
            if (line[index] != ' ' && line[index] != '\r') {
                return false
            }
        }
        return true
    }

    /**
     * One line of [markdown] with its tabs expanded to four-column stops, so that everything above
     * can treat a column as an index. Returns the line unchanged when it has no tab, which is
     * nearly always.
     */
    private fun expandTabs(markdown: String, from: Int, to: Int): String {
        var index = from
        while (index < to && markdown[index] != '\t') {
            index++
        }
        if (index == to) {
            return markdown.substring(from, to)
        }
        val line = StringBuilder(to - from + 8)
        for (scan in from until to) {
            if (markdown[scan] == '\t') {
                do {
                    line.append(' ')
                } while (line.length % 4 != 0)
            } else {
                line.append(markdown[scan])
            }
        }
        return line.toString()
    }

    private fun addCodeSpans(markdown: String, regions: MutableList<IntArray>) {
        var index = 0
        // A code span is inline, so its closing run has to be in the same block as its opener — a
        // stray backtick in one paragraph must not pair with a stray backtick in the next and skip
        // everything between them. Cached the same way [linkify] caches it, to stay linear.
        var blockEnd = 0
        while (index < markdown.length) {
            if (markdown[index] != '`') {
                index++
                continue
            }
            if (index >= blockEnd) {
                blockEnd = blockEnd(markdown, index)
            }
            val openEnd = runEnd(markdown, index)
            val runLength = openEnd - index

            var scan = openEnd
            var closed = -1
            while (scan < blockEnd) {
                if (markdown[scan] == '`') {
                    val end = runEnd(markdown, scan)
                    if (end - scan == runLength) {
                        closed = end
                        break
                    }
                    scan = end
                } else {
                    scan++
                }
            }

            if (closed < 0) {
                index = openEnd
            } else {
                regions.add(intArrayOf(index, closed))
                index = closed
            }
        }
    }

    private fun runEnd(markdown: String, from: Int): Int {
        var index = from
        while (index < markdown.length && markdown[index] == '`') {
            index++
        }
        return index
    }
}
