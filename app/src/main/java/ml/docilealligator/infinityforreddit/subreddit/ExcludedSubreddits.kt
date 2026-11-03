package ml.docilealligator.infinityforreddit.subreddit

import java.util.Locale

/**
 * Subreddits a picker leaves out because its caller already has them, such as the ones already in
 * the multireddit being edited. Reddit treats subreddit names case-insensitively, and so does this.
 */
class ExcludedSubreddits(names: Collection<String>) {
    private val keys: Set<String> = names.mapTo(HashSet()) { key(it) }

    fun contains(name: String): Boolean = key(name) in keys

    /** [subreddits] without the excluded ones. */
    fun filter(subreddits: List<SubredditData>): ArrayList<SubredditData> =
        subreddits.filterTo(ArrayList()) { !contains(it.name) }

    companion object {
        @JvmField
        val NONE = ExcludedSubreddits(emptyList())

        private fun key(name: String): String = name.lowercase(Locale.ROOT)
    }
}
