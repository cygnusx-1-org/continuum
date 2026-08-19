package ml.docilealligator.infinityforreddit.postfilter

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
abstract class PostFilterBlockedSubredditDao {

    /**
     * Adds [count] to an existing row. Returns the number of rows changed, so the caller knows
     * whether the row existed.
     */
    @Query(
        "UPDATE post_filter_blocked_subreddit SET block_count = block_count + :count " +
            "WHERE filter_name = :filterName AND rule_value = :ruleValue " +
            "AND subreddit_name = :subredditName",
    )
    abstract fun addToCount(
        filterName: String,
        ruleValue: String,
        subredditName: String,
        count: Int,
    ): Int

    /**
     * Inserts a first sighting, doing nothing if the row is already there.
     *
     * <p>Deliberately not an `ON CONFLICT ... DO UPDATE` upsert: that syntax needs SQLite 3.24,
     * which only arrives with API 30, and this app supports API 24. `INSERT OR IGNORE` is
     * understood by every version.
     */
    @Query(
        "INSERT OR IGNORE INTO post_filter_blocked_subreddit " +
            "(filter_name, rule_value, subreddit_name, first_blocked, block_count, excepted) " +
            "VALUES (:filterName, :ruleValue, :subredditName, :now, :count, 0)",
    )
    abstract fun insertIfAbsent(
        filterName: String,
        ruleValue: String,
        subredditName: String,
        now: Long,
        count: Int,
    )

    /**
     * Applies a batch from the recorder. Update-then-insert inside one transaction, so two batches
     * cannot both insert the same first sighting and lose one of the counts.
     */
    @Transaction
    open fun upsertAll(blocks: Collection<PostFilterBlockRecorder.PendingBlock>) {
        val now = System.currentTimeMillis()
        for (block in blocks) {
            val subredditName = PostFilterBlockRecorder.normalizeSubredditName(block.subredditName)
            val updated = addToCount(block.filterName, block.ruleValue, subredditName, block.count)
            if (updated == 0) {
                insertIfAbsent(block.filterName, block.ruleValue, subredditName, now, block.count)
            }
        }
    }

    /** Everything one rule has blocked, worst offender first — the order the list screen shows. */
    @Query(
        "SELECT * FROM post_filter_blocked_subreddit " +
            "WHERE filter_name = :filterName AND rule_value = :ruleValue " +
            "ORDER BY block_count DESC, subreddit_name ASC",
    )
    abstract fun getBlockedSubredditsLiveData(
        filterName: String,
        ruleValue: String,
    ): LiveData<List<PostFilterBlockedSubreddit>>

    /** How many distinct subreddits each rule of a filter has blocked, for the rule row counts. */
    @Query(
        "SELECT rule_value AS ruleValue, COUNT(*) AS blockedCount " +
            "FROM post_filter_blocked_subreddit " +
            "WHERE filter_name = :filterName AND excepted = 0 GROUP BY rule_value",
    )
    abstract fun getBlockedCountsLiveData(filterName: String): LiveData<List<RuleBlockedCount>>

    @Query(
        "UPDATE post_filter_blocked_subreddit SET excepted = :excepted " +
            "WHERE filter_name = :filterName AND rule_value = :ruleValue AND subreddit_name = :subredditName",
    )
    abstract fun setExcepted(
        filterName: String,
        ruleValue: String,
        subredditName: String,
        excepted: Boolean,
    )

    /**
     * Every row of one filter, so a rename can carry them over. The foreign key cascades, so the
     * rows have to be read before the old filter row is deleted and re-inserted after the new one
     * lands — otherwise renaming a filter silently discards the very history this table exists for.
     */
    @Query("SELECT * FROM post_filter_blocked_subreddit WHERE filter_name = :filterName")
    abstract fun getAllForFilter(filterName: String): List<PostFilterBlockedSubreddit>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun insertAll(blocked: List<PostFilterBlockedSubreddit>)

    /** Every exception, for the in-memory set the match path consults. */
    @Query("SELECT * FROM post_filter_blocked_subreddit WHERE excepted = 1")
    abstract fun getAllExceptions(): List<PostFilterBlockedSubreddit>

    /**
     * Drops a rule's rows. Called when a rule is deleted or its term edited — an edited term is a
     * different standing query, so its old blocked list describes a rule that no longer exists.
     */
    @Query(
        "DELETE FROM post_filter_blocked_subreddit " +
            "WHERE filter_name = :filterName AND rule_value = :ruleValue",
    )
    abstract fun deleteRule(filterName: String, ruleValue: String)

    @Query("DELETE FROM post_filter_blocked_subreddit WHERE filter_name = :filterName")
    abstract fun deleteAllForFilter(filterName: String)

    /** A rule's term and the number of subreddits it has blocked. */
    data class RuleBlockedCount(val ruleValue: String, val blockedCount: Int)
}
