package ml.docilealligator.infinityforreddit.post

import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sign
import ml.docilealligator.infinityforreddit.thing.SortType
import ml.docilealligator.infinityforreddit.utils.JSONUtils
import org.json.JSONObject

/**
 * One feed woven from listings Reddit pages separately: a multireddit, and the users added to it,
 * whose posts its own listing leaves out.
 *
 * No listing is re-sorted. Each keeps Reddit's order, and at every step the feed takes whichever
 * listing's next post ranks highest under the chosen sort. The merge used to put every page newest
 * first whatever the sort, so a multireddit with a user in it could not be read by Top (issue #469).
 * New and Top rank on a field of the post itself; Hot, Best, Rising and Controversial have no such
 * field, and are weighed with the formulas Reddit published, which is close enough to decide where
 * one listing's post falls among another's.
 *
 * A post is let out only once no listing with pages left could yet give something ranked above it.
 * That is what keeps the order right across pages and not only within one: a listing that has run
 * dry holds the feed at the last post it gave until its next page is in.
 *
 * A post pinned to a user's profile heads that user's listing in every sort, so pinned posts are
 * kept out of their listing's order and placed by rank alone.
 *
 * Not thread-safe: [PostPagingSource] drives it one load at a time.
 */
class MergedListing(sourceCount: Int, private val sort: SortType.Type) {

    private class Source {
        val queue = ArrayDeque<Post>()
        var started = false
        var after: String? = null

        /**
         * Rank of the last unpinned post the listing gave, whether the post filter kept it or not.
         * Nothing on its later pages ranks above it.
         */
        var floor: Double? = null

        val hasPages: Boolean get() = !started || after != null
    }

    private val sources = List(sourceCount) { Source() }
    private val pinned = ArrayList<Post>()

    /** The listings the next load has to page: every one with pages left and nothing queued. */
    fun sourcesToFetch(): List<Int> =
        sources.indices.filter { sources[it].hasPages && sources[it].queue.isEmpty() }

    /** Where [source]'s next page starts; null before its first. */
    fun cursor(source: Int): String? = sources[source].after

    /**
     * Queues a page of [source]'s listing. [posts] is what is left of it after the post filter and
     * the dedup; [listing] is the whole page, which still says how far down the listing has got when
     * the filter took every post on it. [after] is the listing's next cursor, null at its end.
     */
    fun addPage(source: Int, posts: Collection<Post>, listing: JSONObject?, after: String?) {
        val s = sources[source]
        s.started = true
        s.after = after
        floorOf(listing)?.let { s.floor = it }
        for (post in posts) {
            if (post.isStickied) pinned.add(post) else s.queue.addLast(post)
        }
    }

    /** Stops paging [source], whose listing failed to load. What it already gave stays. */
    fun endSource(source: Int) {
        sources[source].started = true
        sources[source].after = null
    }

    fun hasMore(): Boolean =
        pinned.isNotEmpty() || sources.any { it.hasPages || it.queue.isNotEmpty() }

    /** Every post that can be placed now, in feed order. */
    fun drain(): List<Post> {
        val placed = ArrayList<Post>()
        while (true) {
            var next: Post? = null
            var nextRank = 0.0
            var nextSource: Source? = null
            var nextPinned = -1
            for (s in sources) {
                val head = s.queue.firstOrNull() ?: continue
                val rank = rankOf(head)
                if (next == null || rank > nextRank) {
                    next = head
                    nextRank = rank
                    nextSource = s
                }
            }
            for ((i, post) in pinned.withIndex()) {
                val rank = rankOf(post)
                if (next == null || rank > nextRank) {
                    next = post
                    nextRank = rank
                    nextSource = null
                    nextPinned = i
                }
            }
            if (next == null || heldBack(nextRank)) {
                return placed
            }
            if (nextSource != null) nextSource.queue.removeFirst() else pinned.removeAt(nextPinned)
            placed.add(next)
        }
    }

    /** Whether a listing that has run dry could still give something ranked above [rank]. */
    private fun heldBack(rank: Double): Boolean = sources.any { s ->
        s.queue.isEmpty() && s.hasPages && s.floor.let { it == null || it > rank }
    }

    // Ranks by the score the post shows. ParsePost takes the user's own vote out of Post.score, and
    // Reddit ranks with it in, as does the floor, which reads the score straight off the listing.
    private fun rankOf(post: Post): Double =
        rank(post.postTimeMillis, post.score + post.voteType, post.upvoteRatio)

    // Reads the fields the way ParsePost.parseBasicData does, so a post ranks the same whether it is
    // measured here or from the parsed Post.
    private fun floorOf(listing: JSONObject?): Double? {
        val children = listing?.optJSONObject(JSONUtils.DATA_KEY)?.optJSONArray(JSONUtils.CHILDREN_KEY)
            ?: return null
        for (i in children.length() - 1 downTo 0) {
            val child = children.optJSONObject(i) ?: continue
            val data = child.optJSONObject(JSONUtils.DATA_KEY)
            if (child.optString(JSONUtils.KIND_KEY) != "t3" || data == null ||
                data.optBoolean(JSONUtils.STICKIED_KEY)
            ) {
                continue
            }
            return rank(
                data.optLong(JSONUtils.CREATED_UTC_KEY) * 1000,
                data.optInt(JSONUtils.SCORE_KEY),
                (data.optDouble(JSONUtils.UPVOTE_RATIO_KEY, 0.0) * 100).toInt()
            )
        }
        return null
    }

    private fun rank(timeMillis: Long, score: Int, upvoteRatioPercent: Int): Double = when (sort) {
        SortType.Type.NEW -> timeMillis.toDouble()
        SortType.Type.TOP -> score.toDouble()
        SortType.Type.CONTROVERSIAL -> controversy(score, upvoteRatioPercent / 100.0)
        else -> hot(timeMillis, score)
    }

    private companion object {
        // Reddit's published hot ranking, in which a tenfold score is worth 12.5 hours.
        fun hot(timeMillis: Long, score: Int): Double =
            sign(score.toDouble()) * log10(max(abs(score), 1).toDouble()) +
                (timeMillis / 1000 - 1134028003) / 45000.0

        // Reddit's published controversy ranking. Listings give the score and the upvote ratio, not
        // the vote counts it is defined on, so the counts are worked back from those two.
        fun controversy(score: Int, upvoteRatio: Double): Double {
            if (upvoteRatio <= 0.0 || upvoteRatio >= 1.0 || upvoteRatio == 0.5) {
                return 0.0
            }
            val votes = score / (2 * upvoteRatio - 1)
            val ups = upvoteRatio * votes
            val downs = votes - ups
            if (ups <= 0 || downs <= 0) {
                return 0.0
            }
            return votes.pow(min(ups, downs) / max(ups, downs))
        }
    }
}
