package ml.docilealligator.infinityforreddit.recentsearchquery;

import android.os.Handler;

import java.util.concurrent.Executor;

import ml.docilealligator.infinityforreddit.RedditDataRoomDatabase;
import ml.docilealligator.infinityforreddit.multireddit.MultiReddit;

public class InsertRecentSearchQuery {
    public interface InsertRecentSearchQueryListener {
        void success();
    }

    public static void insertRecentSearchQueryListener(Executor executor, Handler handler,
                                                       RedditDataRoomDatabase redditDataRoomDatabase,
                                                       String username,
                                                       String recentSearchQuery,
                                                       String searchInSubredditOrUserName,
                                                       MultiReddit searchInMultiReddit,
                                                       int searchInThingType,
                                                       InsertRecentSearchQueryListener insertRecentSearchQueryListener) {
        executor.execute(() -> {
            RecentSearchQueryDao recentSearchQueryDao = redditDataRoomDatabase.recentSearchQueryDao();

            if (searchInMultiReddit == null) {
                recentSearchQueryDao.insert(new RecentSearchQuery(username, recentSearchQuery,
                        searchInSubredditOrUserName, null, null, searchInThingType));
            } else {
                recentSearchQueryDao.insert(new RecentSearchQuery(username, recentSearchQuery,
                        searchInSubredditOrUserName, searchInMultiReddit.getPath(),
                        searchInMultiReddit.getDisplayName(), searchInThingType));
            }

            handler.post(insertRecentSearchQueryListener::success);
        });
    }
}
