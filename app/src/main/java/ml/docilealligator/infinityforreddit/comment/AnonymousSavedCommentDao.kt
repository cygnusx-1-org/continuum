package ml.docilealligator.infinityforreddit.comment

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface AnonymousSavedCommentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(comment: AnonymousSavedComment)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(comments: List<AnonymousSavedComment>)

    @Query("DELETE FROM anonymous_saved_comments WHERE username = :username AND id = :id")
    fun delete(username: String, id: String)

    /** Newest first, 25 to a page, continuing below [before] when it is given. */
    @Query("SELECT * FROM anonymous_saved_comments WHERE username = :username AND (:before IS NULL OR time < :before) ORDER BY time DESC LIMIT 25")
    fun getPage(username: String, before: Long?): List<AnonymousSavedComment>

    @Query("SELECT id FROM anonymous_saved_comments WHERE username = :username")
    fun getAllIds(username: String): List<String>

    @Query("SELECT * FROM anonymous_saved_comments")
    fun getAllForBackup(): List<AnonymousSavedComment>
}
