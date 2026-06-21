package ml.docilealligator.infinityforreddit.apimonitor

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface ApiCallRecordDao {
    @Insert
    fun insert(record: ApiCallRecord)

    /**
     * Per-endpoint statistics in a single scan over the retained week, split into the last
     * 15 minutes / 1 hour / 1 day / 1 week. The window boundaries are passed in as absolute
     * epoch-millis cutoffs ([w15m] >= [w1h] >= [w1d] >= [w1w]). Averages for a window are NULL
     * when it contains no calls. Busiest endpoints (by week count) first.
     */
    @Query(
        "SELECT section AS section, endpoint AS endpoint, " +
            "SUM(CASE WHEN time >= :w15m THEN 1 ELSE 0 END) AS count15m, " +
            "AVG(CASE WHEN time >= :w15m THEN total_ms ELSE NULL END) AS avg15m, " +
            "SUM(CASE WHEN time >= :w1h THEN 1 ELSE 0 END) AS count1h, " +
            "AVG(CASE WHEN time >= :w1h THEN total_ms ELSE NULL END) AS avg1h, " +
            "SUM(CASE WHEN time >= :w1d THEN 1 ELSE 0 END) AS count1d, " +
            "AVG(CASE WHEN time >= :w1d THEN total_ms ELSE NULL END) AS avg1d, " +
            "COUNT(*) AS count1w, " +
            "AVG(total_ms) AS avg1w, " +
            "AVG(CASE WHEN ttfb_ms >= 0 THEN ttfb_ms ELSE NULL END) AS avg_ttfb1w, " +
            "MAX(total_ms) AS max1w, " +
            "SUM(CASE WHEN success = 0 THEN 1 ELSE 0 END) AS errors1w " +
            "FROM api_call_records WHERE time >= :w1w " +
            "GROUP BY section, endpoint ORDER BY count1w DESC"
    )
    fun getWindowStats(w15m: Long, w1h: Long, w1d: Long, w1w: Long): List<ApiCallWindowStat>

    @Query("DELETE FROM api_call_records WHERE time < :olderThan")
    fun deleteOlderThan(olderThan: Long): Int

    @Query("DELETE FROM api_call_records")
    fun deleteAll()
}
