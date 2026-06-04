package ml.docilealligator.infinityforreddit.apimonitor

import okhttp3.Call
import okhttp3.EventListener
import okhttp3.HttpUrl
import okhttp3.Response
import java.io.IOException

/**
 * An OkHttp [EventListener] that times each Reddit API call and hands a finished
 * [ApiCallRecord] to [ApiCallTracker]. Registered once on the base OkHttpClient in NetworkModule,
 * so every derived client (oauth, anonymous, default, ...) is instrumented.
 *
 * Captures total call duration and time-to-first-byte (response headers), which lets the stats
 * screen distinguish Reddit-side slowness from local network/connection slowness.
 */
class ApiMonitorEventListener private constructor(
    private val tracker: ApiCallTracker
) : EventListener() {

    private var callStartNanos = 0L
    private var ttfbNanos = -1L
    private var responseBytes = -1L
    private var statusCode = 0
    private var url: HttpUrl? = null
    private var method = ""
    private var finished = false

    override fun callStart(call: Call) {
        callStartNanos = System.nanoTime()
        url = call.request().url
        method = call.request().method
    }

    override fun responseHeadersStart(call: Call) {
        if (ttfbNanos < 0 && callStartNanos != 0L) {
            ttfbNanos = System.nanoTime() - callStartNanos
        }
    }

    override fun responseHeadersEnd(call: Call, response: Response) {
        statusCode = response.code
    }

    override fun responseBodyEnd(call: Call, byteCount: Long) {
        responseBytes = byteCount
    }

    override fun callEnd(call: Call) {
        finish(true)
    }

    override fun callFailed(call: Call, ioe: IOException) {
        finish(false)
    }

    private fun finish(completed: Boolean) {
        if (finished || callStartNanos == 0L) {
            return
        }
        finished = true
        val requestUrl = url ?: return
        val totalMs = (System.nanoTime() - callStartNanos) / 1_000_000
        val ttfbMs = if (ttfbNanos >= 0) ttfbNanos / 1_000_000 else -1L
        val success = completed && statusCode in 1..399
        val classification = ApiCallClassifier.classify(requestUrl)
        tracker.record(
            ApiCallRecord(
                time = System.currentTimeMillis(),
                host = requestUrl.host,
                section = classification.section,
                endpoint = classification.endpoint,
                method = method,
                statusCode = statusCode,
                ttfbMs = ttfbMs,
                totalMs = totalMs,
                responseBytes = responseBytes,
                success = success
            )
        )
    }

    /**
     * Creates a listener per call while monitoring is enabled. Covers Reddit API calls as well as
     * media retrieval (images via Glide, video via the media3 client) and third-party hosts; the
     * classifier buckets each by section. Returns [EventListener.NONE] when disabled so there is no
     * overhead.
     */
    class Factory(private val tracker: ApiCallTracker) : EventListener.Factory {
        override fun create(call: Call): EventListener {
            return if (tracker.isEnabled) ApiMonitorEventListener(tracker) else EventListener.NONE
        }
    }
}
