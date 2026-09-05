package ml.docilealligator.infinityforreddit.subscribedsubreddit;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import java.util.List;

@Dao
public interface SubscribedSubredditDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(SubscribedSubredditData subscribedSubredditData);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<SubscribedSubredditData> subscribedSubredditDataList);

    /**
     * One account's subscription list. For a signed-in account it is a copy of what Reddit holds --
     * favourites included, which come from {@code user_has_favorited} -- so it comes back on the
     * next sync. Anonymous subscriptions are written locally and never synced, so for anonymous
     * this is the only copy.
     */
    @Query("DELETE FROM subscribed_subreddits WHERE username = :accountName COLLATE NOCASE")
    void deleteAllSubscribedSubreddits(String accountName);

    @Query("SELECT * from subscribed_subreddits WHERE username = :accountName AND name LIKE :searchQuery ORDER BY name COLLATE NOCASE ASC")
    LiveData<List<SubscribedSubredditData>> getAllSubscribedSubredditsWithSearchQuery(String accountName, String searchQuery);

    @Query("SELECT * from subscribed_subreddits WHERE username = :accountName COLLATE NOCASE ORDER BY name COLLATE NOCASE ASC")
    List<SubscribedSubredditData> getAllSubscribedSubredditsList(String accountName);

    @Query("SELECT * from subscribed_subreddits WHERE username = :accountName AND name LIKE :searchQuery COLLATE NOCASE AND is_favorite = 1 ORDER BY name COLLATE NOCASE ASC")
    LiveData<List<SubscribedSubredditData>> getAllFavoriteSubscribedSubredditsWithSearchQuery(String accountName, String searchQuery);

    @Query("SELECT * from subscribed_subreddits WHERE name = :subredditName COLLATE NOCASE AND username = :accountName COLLATE NOCASE LIMIT 1")
    @Nullable
    SubscribedSubredditData getSubscribedSubreddit(String subredditName, String accountName);

    @Query("DELETE FROM subscribed_subreddits WHERE name = :subredditName COLLATE NOCASE AND username = :accountName COLLATE NOCASE")
    void deleteSubscribedSubreddit(String subredditName, String accountName);

    /** Names only: Recently Visited needs the set to decide which rows still offer a subscribe. */
    @Query("SELECT name FROM subscribed_subreddits WHERE username = :accountName")
    LiveData<List<String>> getSubscribedSubredditNames(String accountName);
}
