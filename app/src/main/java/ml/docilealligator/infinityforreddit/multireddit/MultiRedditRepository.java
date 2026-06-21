package ml.docilealligator.infinityforreddit.multireddit;

import androidx.lifecycle.LiveData;
import java.util.List;
import ml.docilealligator.infinityforreddit.RedditDataRoomDatabase;

public class MultiRedditRepository {
    private final MultiRedditDao mMultiRedditDao;
    private final String mAccountName;
    private final boolean mFollowed;

    MultiRedditRepository(RedditDataRoomDatabase redditDataRoomDatabase, String accountName, boolean followed) {
        mMultiRedditDao = redditDataRoomDatabase.multiRedditDao();
        mAccountName = accountName;
        mFollowed = followed;
    }

    LiveData<List<MultiReddit>> getAllMultiRedditsWithSearchQuery(String searchQuery) {
        return mMultiRedditDao.getAllMultiRedditsWithSearchQuery(mAccountName, mFollowed, searchQuery);
    }

    LiveData<List<MultiReddit>> getAllFavoriteMultiRedditsWithSearchQuery(String searchQuery) {
        return mMultiRedditDao.getAllFavoriteMultiRedditsWithSearchQuery(mAccountName, mFollowed, searchQuery);
    }
}
