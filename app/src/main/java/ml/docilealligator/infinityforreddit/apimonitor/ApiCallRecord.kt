package ml.docilealligator.infinityforreddit.apimonitor

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A single recorded API request. One row is inserted per network call captured by
 * [ApiMonitorEventListener]. Rows are pruned by age (see [ApiCallTracker]).
 */
@Entity(
    tableName = "api_call_records",
    indices = [Index("time"), Index("section", "endpoint")]
)
data class ApiCallRecord(
    @ColumnInfo(name = "time")
    val time: Long,

    @ColumnInfo(name = "host")
    val host: String,

    @ColumnInfo(name = "section")
    val section: String,

    @ColumnInfo(name = "endpoint")
    val endpoint: String,

    @ColumnInfo(name = "method")
    val method: String,

    /** HTTP status code, or 0 if the call failed before a response was received. */
    @ColumnInfo(name = "status_code")
    val statusCode: Int,

    /** Time to first byte (response headers) in milliseconds, or -1 if unknown. */
    @ColumnInfo(name = "ttfb_ms")
    val ttfbMs: Long,

    /** Total call duration in milliseconds. */
    @ColumnInfo(name = "total_ms")
    val totalMs: Long,

    /** Response body bytes, or -1 if unknown. */
    @ColumnInfo(name = "response_bytes")
    val responseBytes: Long,

    /** true if a response was received with a status code below 400. */
    @ColumnInfo(name = "success")
    val success: Boolean
) {
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    var id: Long = 0
}
