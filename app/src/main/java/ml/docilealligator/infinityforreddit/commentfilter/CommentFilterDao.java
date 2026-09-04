package ml.docilealligator.infinityforreddit.commentfilter;

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
 * A filter belongs to one account, so every query but the one the backup uses is asked about one.
 */
@Dao
public interface CommentFilterDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(CommentFilter CommentFilter);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<CommentFilter> CommentFilters);

    @Query("DELETE FROM comment_filter WHERE username = :username")
    void deleteAllCommentFilters(String username);

    @Delete
    void deleteCommentFilter(CommentFilter CommentFilter);

    @Query("DELETE FROM comment_filter WHERE name = :name AND username = :username")
    void deleteCommentFilter(String name, String username);

    @Query("SELECT * FROM comment_filter WHERE name = :name AND username = :username LIMIT 1")
    @Nullable
    CommentFilter getCommentFilter(String name, String username);

    @Query("SELECT * FROM comment_filter WHERE username = :username ORDER BY name")
    LiveData<List<CommentFilter>> getAllCommentFiltersLiveData(String username);

    /** Every account's filters, for the backup. Everything else asks about one account. */
    @Query("SELECT * FROM comment_filter")
    List<CommentFilter> getAllCommentFiltersForBackup();

    @Query("SELECT * FROM comment_filter WHERE username = :username")
    List<CommentFilter> getAllCommentFilters(String username);

    @Query("SELECT * FROM comment_filter WHERE comment_filter.username = :username AND " +
            "((comment_filter.name IN (SELECT comment_filter_usage.name FROM comment_filter_usage " +
            "WHERE comment_filter_usage.username = :username " +
            "AND (usage = :usage AND name_of_usage = :nameOfUsage COLLATE NOCASE))) " +
            "OR (comment_filter.name NOT IN (SELECT comment_filter_usage.name FROM comment_filter_usage " +
            "WHERE comment_filter_usage.username = :username)))")
    List<CommentFilter> getValidCommentFilters(int usage, String nameOfUsage, String username);

    @Transaction
    @Query("SELECT * FROM comment_filter WHERE username = :username ORDER BY name")
    LiveData<List<CommentFilterWithUsage>> getAllCommentFilterWithUsageLiveData(String username);
}
