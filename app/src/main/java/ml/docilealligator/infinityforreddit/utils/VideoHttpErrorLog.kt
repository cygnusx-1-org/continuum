package ml.docilealligator.infinityforreddit.utils

import android.util.Log
import androidx.annotation.OptIn
import androidx.annotation.VisibleForTesting
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import java.util.Locale

/**
 * Logs what the server said when a video failed on an HTTP request: which URL, the status, the
 * headers that say where the answer came from, and the start of the body.
 *
 * ExoPlayer's own error line stops at "Response code: 403", which cannot tell a refused master
 * playlist from a refused rendition, a CDN replaying a cached refusal from an origin refusing
 * fresh, or Reddit's "Access denied" from its "blocked by network security" page. Issue #498 went
 * through three users' logs on exactly that ambiguity. Every player calls this from
 * `onPlayerError`, so a user's next log answers it.
 *
 * One line per failure under [TAG], at error level. Media3 already logs every playback error with
 * its stack under `ExoPlayerImplInternal`; an error with no HTTP request behind it has nothing to
 * add to that, so it logs nothing here.
 */
@OptIn(UnstableApi::class)
object VideoHttpErrorLog {

    const val TAG = "VideoHttpError"

    /**
     * The response headers worth having. `x-reddit-ct` carries Reddit's CDN location and whether
     * the answer was a cache hit, `age` how long a cached answer has been served, and the rest
     * which CDN answered and how long it may keep the answer.
     */
    private val HEADERS = listOf(
        "x-reddit-ct", "x-reddit-cdn", "x-cdn", "x-cdn-name", "x-cdn-server-region",
        "x-cdn-client-region", "x-served-by", "x-cache", "age", "cache-control", "content-type",
        "server", "x-auth-debug", "retry-after", "location"
    )

    private const val MAX_BODY_BYTES = 8192
    private const val MAX_BODY_CHARS = 300

    private val SCRIPT_OR_STYLE = Regex("(?is)<(script|style)\\b.*?</\\1\\s*>")
    private val TAG_MARKUP = Regex("<[^>]*>")
    private val WHITESPACE = Regex("\\s+")
    private val IPV4 = Regex("(?<![0-9.])(?:\\d{1,3}\\.){3}\\d{1,3}(?![0-9.])")
    private val IPV6_CANDIDATE =
        Regex("(?i)(?<![0-9a-z:])[0-9a-f]{0,4}(?::[0-9a-f]{0,4}){2,7}(?![0-9a-z:])")

    @JvmStatic
    fun log(player: String, error: PlaybackException) {
        describe(player, error)?.let { Log.e(TAG, it) }
    }

    /** The line [log] writes, or null when no HTTP request is behind [error]. */
    @VisibleForTesting
    @JvmStatic
    fun describe(player: String, error: PlaybackException): String? {
        // InvalidResponseCodeException is an HttpDataSourceException, so it has to be matched
        // first. The depth cap is only there so a cause cycle cannot spin forever.
        var cause: Throwable? = error
        var depth = 0
        while (cause != null && depth < 16) {
            if (cause is HttpDataSource.InvalidResponseCodeException) {
                return "[$player] ${error.errorCodeName}: " + describeResponse(
                    cause.responseCode, cause.responseMessage, cause.dataSpec.uri.toString(),
                    cause.headerFields, cause.responseBody
                )
            }
            if (cause is HttpDataSource.HttpDataSourceException) {
                return "[$player] ${error.errorCodeName}: " + describeFailure(
                    cause.type, cause.dataSpec.uri.toString(), cause.cause ?: cause
                )
            }
            cause = cause.cause
            depth++
        }
        return null
    }

    @VisibleForTesting
    @JvmStatic
    fun describeResponse(
        code: Int,
        message: String?,
        uri: String,
        headers: Map<out String?, List<String>>?,
        body: ByteArray?,
    ): String {
        val status = if (message.isNullOrBlank()) "HTTP $code" else "HTTP $code $message"
        val byName = HashMap<String, List<String>>()
        headers?.forEach { (name, values) ->
            if (name != null) byName[name.lowercase(Locale.ROOT)] = values
        }
        val headerText = HEADERS.mapNotNull { name ->
            byName[name]?.let { "$name=${it.joinToString(", ")}" }
        }
        return buildString {
            append(status).append(" for ").append(uri)
            headerText.forEach { append("; ").append(it) }
            append("; body=").append(summarizeBody(body))
        }
    }

    @VisibleForTesting
    @JvmStatic
    fun describeFailure(type: Int, uri: String, cause: Throwable): String {
        val stage = when (type) {
            HttpDataSource.HttpDataSourceException.TYPE_OPEN -> "open"
            HttpDataSource.HttpDataSourceException.TYPE_READ -> "read"
            HttpDataSource.HttpDataSourceException.TYPE_CLOSE -> "close"
            else -> "request"
        }
        val reason = cause.message?.let { redactAddresses(it) }
        return "$stage failed for $uri: ${cause.javaClass.simpleName}" +
            (if (reason.isNullOrBlank()) "" else ": $reason")
    }

    /**
     * The body as one short line of text: markup stripped, since a refusal is often an HTML page,
     * and capped, since the point is to recognise the page rather than read it.
     */
    @VisibleForTesting
    @JvmStatic
    fun summarizeBody(body: ByteArray?): String {
        if (body == null || body.isEmpty()) {
            return "empty"
        }
        val text = String(body, 0, minOf(body.size, MAX_BODY_BYTES), Charsets.UTF_8)
        // A media file sent with an error status would otherwise fill the line with garbage.
        val unprintable = text.count {
            it == '�' || (Character.isISOControl(it) && it != '\n' && it != '\r' && it != '\t')
        }
        if (unprintable * 20 > text.length) {
            return "(${body.size} bytes, not text)"
        }
        var flat = text.replace(SCRIPT_OR_STYLE, " ").replace(TAG_MARKUP, " ")
            .replace("&nbsp;", " ").replace("&quot;", "\"").replace("&#39;", "'")
            .replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")
            .replace(WHITESPACE, " ").trim()
        flat = redactAddresses(flat)
        if (flat.length > MAX_BODY_CHARS) {
            flat = flat.substring(0, MAX_BODY_CHARS) + "…"
        }
        return "\"$flat\" (${body.size} bytes)"
    }

    /**
     * Users paste these logs into public issues, and an error page can echo the address the
     * request came from. A timestamp like `12:30:45` matches the IPv6 shape but has neither a hex
     * letter, a `::`, nor enough groups, so it is left alone.
     */
    @VisibleForTesting
    @JvmStatic
    fun redactAddresses(text: String): String {
        return text.replace(IPV4, "<ip>").replace(IPV6_CANDIDATE) { match ->
            val token = match.value
            val colons = token.count { it == ':' }
            val hasHexLetter = token.any { it in 'a'..'f' || it in 'A'..'F' }
            if (token.contains("::") || hasHexLetter || colons >= 5) "<ip>" else token
        }
    }
}
