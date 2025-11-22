package ml.docilealligator.infinityforreddit.recentsearchquery;

import androidx.lifecycle.LiveData;

import java.util.List;

import ml.docilealligator.infinityforreddit.RedditDataRoomDatabase;

public class RecentSearchQueryRepository {
    private final LiveData<List<RecentSearchQuery>> mAllRecentSearchQueries;
    private final LiveData<List<RecentSearchQuery>> mLimitedRecentSearchQueries;

    RecentSearchQueryRepository(RedditDataRoomDatabase redditDataRoomDatabase, String username, int limit) {
        mAllRecentSearchQueries = redditDataRoomDatabase.recentSearchQueryDao().getAllRecentSearchQueriesLiveData(username);
        mLimitedRecentSearchQueries = redditDataRoomDatabase.recentSearchQueryDao().getRecentSearchQueriesLiveData(username, limit);
    }

    LiveData<List<RecentSearchQuery>> getAllRecentSearchQueries() {
        return mAllRecentSearchQueries;
    }

    LiveData<List<RecentSearchQuery>> getLimitedRecentSearchQueries() {
        return mLimitedRecentSearchQueries;
    }
}
