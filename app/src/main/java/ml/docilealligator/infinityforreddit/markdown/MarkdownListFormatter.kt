package ml.docilealligator.infinityforreddit.markdown

/**
 * Turns the lines of an editor selection into a list, for the markdown toolbar's list buttons
 * (issue #447).
 *
 * Works on whole lines, because a marker only starts a list item at the start of one: a selection
 * beginning mid-line still marks that line, and one ending just after a newline stops at that
 * newline. Blank lines are skipped, since a bare marker is text and not an item (see
 * [RedditListPlugin]) and would show up as a stray `*` in the item above it. Lines that are already
 * list items are left alone, so that selecting everything after marking a line by hand doesn't nest
 * that line.
 */
object MarkdownListFormatter {
    /** Replace [start] until [end] with [replacement]. */
    class Edit(
        @JvmField val start: Int,
        @JvmField val end: Int,
        @JvmField val replacement: String,
    )

    // [0-9] rather than \d, which also matches non-ASCII digits on Android; those are no marker.
    private val LIST_ITEM = Regex("""^[ \t]*(?:[-+*]|([0-9]{1,9})[.)])[ \t]""")

    /** Bullets the selected lines, or returns null if there is nothing to bullet. */
    @JvmStatic
    fun bulletLines(text: CharSequence, selectionStart: Int, selectionEnd: Int): Edit? =
        markLines(text, selectionStart, selectionEnd) { line, indent ->
            if (LIST_ITEM.containsMatchIn(line)) line else "$indent* ${line.substring(indent.length)}"
        }

    /**
     * Numbers the selected lines from 1, or returns null if there is nothing to number. A line that
     * is already numbered keeps its number, and the lines after it carry on from there.
     */
    @JvmStatic
    fun numberLines(text: CharSequence, selectionStart: Int, selectionEnd: Int): Edit? {
        var next = 1
        return markLines(text, selectionStart, selectionEnd) { line, indent ->
            val item = LIST_ITEM.find(line)
            if (item != null) {
                item.groupValues[1].toIntOrNull()?.let { next = it + 1 }
                line
            } else {
                "$indent${next++}. ${line.substring(indent.length)}"
            }
        }
    }

    private fun markLines(
        text: CharSequence,
        selectionStart: Int,
        selectionEnd: Int,
        mark: (line: String, indent: String) -> String,
    ): Edit? {
        val blockStart = if (selectionStart == 0) 0 else text.lastIndexOf('\n', selectionStart - 1) + 1
        val lastSelected =
            if (selectionEnd > selectionStart && text[selectionEnd - 1] == '\n') selectionEnd - 1 else selectionEnd
        val blockEnd = text.indexOf('\n', lastSelected).let { if (it == -1) text.length else it }

        val block = text.substring(blockStart, blockEnd)
        val marked = block.split('\n').joinToString("\n") { line ->
            if (line.isBlank()) line else mark(line, line.takeWhile { it == ' ' || it == '\t' })
        }
        return if (marked == block) null else Edit(blockStart, blockEnd, marked)
    }
}
