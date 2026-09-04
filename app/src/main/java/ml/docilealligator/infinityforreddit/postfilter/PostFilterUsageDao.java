package ml.docilealligator.infinityforreddit.postfilter;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import java.util.List;

@Dao
public interface PostFilterUsageDao {
    @Query("SELECT * FROM post_filter_usage WHERE name = :name AND username = :username")
    LiveData<List<PostFilterUsage>> getAllPostFilterUsageLiveData(String name, String username);

    @Query("SELECT * FROM post_filter_usage WHERE name = :name AND username = :username")
    List<PostFilterUsage> getAllPostFilterUsage(String name, String username);

    /** Every account's usages, for the backup. */
    @Query("SELECT * FROM post_filter_usage")
    List<PostFilterUsage> getAllPostFilterUsageForBackup();

    @Query("SELECT * FROM post_filter_usage WHERE username = :username")
    List<PostFilterUsage> getAllPostFilterUsageForAccount(String username);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(PostFilterUsage postFilterUsage);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<PostFilterUsage> postFilterUsageList);

    @Delete
    void deletePostFilterUsage(PostFilterUsage postFilterUsage);

    @Query("DELETE FROM post_filter_usage WHERE name = :name AND username = :username")
    void deleteAllPostFilterUsage(String name, String username);
}
