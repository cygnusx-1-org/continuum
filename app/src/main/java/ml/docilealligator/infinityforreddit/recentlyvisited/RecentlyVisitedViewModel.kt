package ml.docilealligator.infinityforreddit.recentlyvisited

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.switchMap
import ml.docilealligator.infinityforreddit.RedditDataRoomDatabase

class RecentlyVisitedViewModel(
    redditDataRoomDatabase: RedditDataRoomDatabase,
    accountName: String,
    @RecentlyVisitedType type: Int
) : ViewModel() {
    private val repository = RecentlyVisitedRepository(redditDataRoomDatabase, accountName, type)
    private val searchQueryLiveData = MutableLiveData("%")

    val recentlyVisited: LiveData<List<RecentlyVisited>> =
        searchQueryLiveData.switchMap { repository.getRecentlyVisitedWithSearchQuery(it) }

    fun setSearchQuery(searchQuery: String) {
        searchQueryLiveData.postValue(searchQuery)
    }

    class Factory(
        private val redditDataRoomDatabase: RedditDataRoomDatabase,
        private val accountName: String,
        @param:RecentlyVisitedType private val type: Int
    ) : ViewModelProvider.NewInstanceFactory() {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            RecentlyVisitedViewModel(redditDataRoomDatabase, accountName, type) as T
    }
}
