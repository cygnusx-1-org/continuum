package ml.docilealligator.infinityforreddit.recentsearchquery;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import java.util.List;

@Dao
public interface RecentSearchQueryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(RecentSearchQuery recentSearchQuery);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<RecentSearchQuery> recentSearchQueries);

    @Query("SELECT * FROM recent_search_queries WHERE username = :username ORDER BY time DESC")
    LiveData<List<RecentSearchQuery>> getAllRecentSearchQueriesLiveData(String username);

    @Query("SELECT * FROM recent_search_queries WHERE username = :username ORDER BY time DESC LIMIT :limit")
    LiveData<List<RecentSearchQuery>> getRecentSearchQueriesLiveData(String username, int limit);

    @Query("SELECT * FROM recent_search_queries WHERE username = :username ORDER BY time DESC")
    List<RecentSearchQuery> getAllRecentSearchQueries(String username);

    // Every account's rows, for Settings backup. Search history is local-only and cannot be
    // re-synced, and restoring deletes the accounts it hangs off, so it has to travel in the backup
    // or it is gone -- the same reason read_posts and local_saved are in there.
    @Query("SELECT * FROM recent_search_queries")
    List<RecentSearchQuery> getAllForBackup();

    @Query("DELETE FROM recent_search_queries WHERE username = :username")
    void deleteAllRecentSearchQueries(String username);

    @Delete
    void deleteRecentSearchQueries(RecentSearchQuery recentSearchQuery);
}