package ml.docilealligator.infinityforreddit.postfilter;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;
import java.util.List;

/**
 * A filter belongs to one account, so every query but the two the backup uses is asked about one.
 */
@Dao
public interface PostFilterDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(PostFilter postFilter);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<PostFilter> postFilters);

    @Query("DELETE FROM post_filter WHERE username = :username")
    void deleteAllPostFilters(String username);

    @Delete
    void deletePostFilter(PostFilter postFilter);

    @Query("DELETE FROM post_filter WHERE name = :name AND username = :username")
    void deletePostFilter(String name, String username);

    @Query("SELECT * FROM post_filter WHERE name = :name AND username = :username LIMIT 1")
    @Nullable
    PostFilter getPostFilter(String name, String username);

    @Query("SELECT * FROM post_filter WHERE username = :username ORDER BY name")
    LiveData<List<PostFilter>> getAllPostFiltersLiveData(String username);

    /** Every account's filters, for the backup. Everything else asks about one account. */
    @Query("SELECT * FROM post_filter")
    List<PostFilter> getAllPostFiltersForBackup();

    @Query("SELECT * FROM post_filter WHERE username = :username")
    List<PostFilter> getAllPostFilters(String username);

    @Query("SELECT * FROM post_filter WHERE post_filter.username = :username AND post_filter.name IN " +
            "(SELECT post_filter_usage.name FROM post_filter_usage WHERE post_filter_usage.username = :username " +
            "AND ((usage = :usage AND name_of_usage = :nameOfUsage COLLATE NOCASE) " +
            "OR (usage = :usage AND name_of_usage = '--')))")
    List<PostFilter> getValidPostFilters(int usage, @Nullable String nameOfUsage, String username);

    @Transaction
    @Query("SELECT * FROM post_filter WHERE username = :username ORDER BY name")
    LiveData<List<PostFilterWithUsage>> getAllPostFilterWithUsageLiveData(String username);
}
