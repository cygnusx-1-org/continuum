package ml.docilealligator.infinityforreddit.commentfilter

/**
 * Turns an "Add to Comment Filter" choice about one comment into a change to a [CommentFilter].
 *
 * A comment filter stores its excluded users as one comma-separated column rather than as rules, so
 * "add this author" is an append — and appending is exactly where duplicates and stray commas creep
 * in, which is why it lives here with a test rather than inline in the screen.
 */
object CommentFilterSeeds {

    /**
     * Adds [username] to [commentFilter]'s excluded users, and reports whether that changed anything.
     *
     * False means there was nothing to add: a blank name, or one the filter already excludes.
     * Duplicates are judged the way [CommentFilter.isCommentAllowed] matches — trimmed and
     * case-insensitively — so a term that would never match twice is never stored twice.
     *
     * Whatever the user typed into the column is left as it is, spacing and all; only the new name
     * is appended.
     */
    @JvmStatic
    fun addExcludedUser(commentFilter: CommentFilter, username: String?): Boolean {
        val trimmed = username?.trim().orEmpty()
        if (trimmed.isEmpty()) {
            return false
        }
        val existing = commentFilter.excludeUsers.orEmpty()
        if (existing.split(",").any { it.trim().equals(trimmed, ignoreCase = true) }) {
            return false
        }
        val kept = existing.trimEnd()
        commentFilter.excludeUsers = when {
            kept.isBlank() -> trimmed
            kept.endsWith(",") -> kept + trimmed
            else -> "$kept,$trimmed"
        }
        return true
    }
}
