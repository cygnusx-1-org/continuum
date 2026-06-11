package ml.docilealligator.infinityforreddit.apimonitor

import androidx.room.ColumnInfo

/**
 * Aggregated statistics for a (section, endpoint) group computed in one pass over the retained
 * (last 7 days) records, broken out into the last 15 minutes / 1 hour / 1 day / 1 week windows.
 * The windowed averages are null when that window has no calls.
 */
data class ApiCallWindowStat(
    @ColumnInfo(name = "section") val section: String,
    @ColumnInfo(name = "endpoint") val endpoint: String,

    @ColumnInfo(name = "count15m") val count15m: Int,
    @ColumnInfo(name = "avg15m") val avg15m: Double?,

    @ColumnInfo(name = "count1h") val count1h: Int,
    @ColumnInfo(name = "avg1h") val avg1h: Double?,

    @ColumnInfo(name = "count1d") val count1d: Int,
    @ColumnInfo(name = "avg1d") val avg1d: Double?,

    @ColumnInfo(name = "count1w") val count1w: Int,
    @ColumnInfo(name = "avg1w") val avg1w: Double?,

    @ColumnInfo(name = "avg_ttfb1w") val avgTtfb1w: Double?,
    @ColumnInfo(name = "max1w") val max1w: Long,
    @ColumnInfo(name = "errors1w") val errors1w: Int
)
