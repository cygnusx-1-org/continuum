package ml.docilealligator.infinityforreddit.localsaved;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.List;

@Dao
public interface LocalSavedThingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(LocalSavedThing localSavedThing);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<LocalSavedThing> localSavedThings);

    @Query("SELECT * FROM local_saved")
    List<LocalSavedThing> getAllForBackup();

    @Query("SELECT * FROM local_saved WHERE username = :username AND state = :state")
    List<LocalSavedThing> getByState(String username, @LocalSavedState int state);

    // Promoted posts (t3_) newest-first, paginated by time (25/page). The '_' in the prefix is
    // escaped so it is matched literally rather than as a single-character LIKE wildcard.
    @Query("SELECT * FROM local_saved WHERE username = :username AND state = 1 " +
            "AND full_name LIKE 't3\\_%' ESCAPE '\\' " +
            "AND (:before IS NULL OR time < :before) ORDER BY time DESC LIMIT 25")
    ListenableFuture<List<LocalSavedThing>> getPromotedPosts(String username, Long before);

    // All promoted posts (t3_) newest-first, no limit. Used by the Local Saved posts tab's search,
    // which loads the whole list up front so it can filter and present every match at once. Runs on
    // a background executor.
    @Query("SELECT * FROM local_saved WHERE username = :username AND state = 1 " +
            "AND full_name LIKE 't3\\_%' ESCAPE '\\' ORDER BY time DESC")
    List<LocalSavedThing> getAllPromotedPostsSync(String username);

    // Promoted comments (t1_) newest-first, paginated by time (25/page). Synchronous variant used
    // by the comment PageKeyedDataSource, which already runs on a background executor.
    @Query("SELECT * FROM local_saved WHERE username = :username AND state = 1 " +
            "AND full_name LIKE 't1\\_%' ESCAPE '\\' " +
            "AND (:before IS NULL OR time < :before) ORDER BY time DESC LIMIT 25")
    List<LocalSavedThing> getPromotedCommentsSync(String username, Long before);

    @Query("UPDATE local_saved SET state = :state WHERE username = :username AND full_name = :fullName")
    void setState(String username, String fullName, @LocalSavedState int state);

    @Query("DELETE FROM local_saved WHERE username = :username AND full_name = :fullName")
    void delete(String username, String fullName);

    @Query("DELETE FROM local_saved")
    void deleteAll();
}
