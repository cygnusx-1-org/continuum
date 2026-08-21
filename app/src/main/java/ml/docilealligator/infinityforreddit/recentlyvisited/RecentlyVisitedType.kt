package ml.docilealligator.infinityforreddit.recentlyvisited

import androidx.annotation.IntDef

/** Which kind of thing a [RecentlyVisited] row records. Stored as an Int so the two tabs can share one table. */
@IntDef(RecentlyVisitedType.SUBREDDIT, RecentlyVisitedType.USER)
@Retention(AnnotationRetention.SOURCE)
annotation class RecentlyVisitedType {
    companion object {
        const val SUBREDDIT = 0
        const val USER = 1
    }
}
