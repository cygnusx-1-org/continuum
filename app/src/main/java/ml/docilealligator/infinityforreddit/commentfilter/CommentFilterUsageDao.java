package ml.docilealligator.infinityforreddit.commentfilter;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import java.util.List;

@Dao
public interface CommentFilterUsageDao {
    @Query("SELECT * FROM comment_filter_usage WHERE name = :name AND username = :username")
    LiveData<List<CommentFilterUsage>> getAllCommentFilterUsageLiveData(String name, String username);

    @Query("SELECT * FROM comment_filter_usage WHERE name = :name AND username = :username")
    List<CommentFilterUsage> getAllCommentFilterUsage(String name, String username);

    /** Every account's usages, for the backup. */
    @Query("SELECT * FROM comment_filter_usage")
    List<CommentFilterUsage> getAllCommentFilterUsageForBackup();

    @Query("SELECT * FROM comment_filter_usage WHERE username = :username")
    List<CommentFilterUsage> getAllCommentFilterUsageForAccount(String username);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(CommentFilterUsage CommentFilterUsage);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<CommentFilterUsage> CommentFilterUsageList);

    @Delete
    void deleteCommentFilterUsage(CommentFilterUsage CommentFilterUsage);
}
