package ml.docilealligator.infinityforreddit.recentlyvisited

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface RecentlyVisitedDao {
    /** REPLACE so revisiting a thing bumps its `last_visited` and floats it back to the top. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(recentlyVisited: RecentlyVisited)

    @Query(
        "SELECT * FROM recently_visited WHERE username = :accountName AND type = :type " +
                "AND name LIKE :searchQuery COLLATE NOCASE ORDER BY last_visited DESC"
    )
    fun getRecentlyVisitedWithSearchQuery(
        accountName: String,
        @RecentlyVisitedType type: Int,
        searchQuery: String
    ): LiveData<List<RecentlyVisited>>

    /**
     * Trims the account's rows of one type back to [limit], dropping the least recently visited.
     * `LIMIT -1 OFFSET :limit` selects everything past the newest [limit] rows.
     */
    @Query(
        "DELETE FROM recently_visited WHERE rowid IN (SELECT rowid FROM recently_visited " +
                "WHERE username = :accountName AND type = :type " +
                "ORDER BY last_visited DESC LIMIT -1 OFFSET :limit)"
    )
    fun trimToLimit(accountName: String, @RecentlyVisitedType type: Int, limit: Int)

    @Query("DELETE FROM recently_visited WHERE username = :accountName")
    fun deleteAllForAccount(accountName: String)
}
