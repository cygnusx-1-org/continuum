package ml.docilealligator.infinityforreddit.recentlyvisited

import androidx.lifecycle.LiveData
import ml.docilealligator.infinityforreddit.RedditDataRoomDatabase

class RecentlyVisitedRepository(
    redditDataRoomDatabase: RedditDataRoomDatabase,
    private val accountName: String,
    @param:RecentlyVisitedType private val type: Int
) {
    private val dao = redditDataRoomDatabase.recentlyVisitedDao()

    fun getRecentlyVisitedWithSearchQuery(searchQuery: String): LiveData<List<RecentlyVisited>> =
        dao.getRecentlyVisitedWithSearchQuery(accountName, type, searchQuery)
}
