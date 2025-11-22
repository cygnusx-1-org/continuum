package ml.docilealligator.infinityforreddit.recentsearchquery;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import java.util.List;

import ml.docilealligator.infinityforreddit.RedditDataRoomDatabase;

public class RecentSearchQueryViewModel extends ViewModel {
    public static final int DEFAULT_DISPLAY_LIMIT = 5;

    private final LiveData<List<RecentSearchQuery>> mAllRecentSearchQueries;
    private final LiveData<List<RecentSearchQuery>> mLimitedRecentSearchQueries;

    public RecentSearchQueryViewModel(RedditDataRoomDatabase redditDataRoomDatabase, String username, int limit) {
        RecentSearchQueryRepository repository = new RecentSearchQueryRepository(redditDataRoomDatabase, username, limit);
        mAllRecentSearchQueries = repository.getAllRecentSearchQueries();
        mLimitedRecentSearchQueries = repository.getLimitedRecentSearchQueries();
    }

    public LiveData<List<RecentSearchQuery>> getAllRecentSearchQueries() {
        return mAllRecentSearchQueries;
    }

    public LiveData<List<RecentSearchQuery>> getLimitedRecentSearchQueries() {
        return mLimitedRecentSearchQueries;
    }

    public static class Factory extends ViewModelProvider.NewInstanceFactory {
        private final RedditDataRoomDatabase mRedditDataRoomDatabase;
        private final String mUsername;
        private final int mLimit;

        public Factory(RedditDataRoomDatabase redditDataRoomDatabase, String username) {
            this(redditDataRoomDatabase, username, DEFAULT_DISPLAY_LIMIT);
        }

        public Factory(RedditDataRoomDatabase redditDataRoomDatabase, String username, int limit) {
            mRedditDataRoomDatabase = redditDataRoomDatabase;
            mUsername = username;
            mLimit = limit;
        }

        @NonNull
        @Override
        public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
            return (T) new RecentSearchQueryViewModel(mRedditDataRoomDatabase, mUsername, mLimit);
        }
    }
}
