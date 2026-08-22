package ml.docilealligator.infinityforreddit.utils

import java.io.IOException

/**
 * Thrown when a media upload to Reddit's asset host fails.
 *
 * [UploadImageUtils] used to report these by returning the string `"Error: 403"`, which every caller
 * had to recognise by its prefix — and none of them recognised the same way. One of them showed the
 * sentinel to the user verbatim, one treated it as a JSON body and failed on the parse instead, and a
 * `null` return (an empty body, or an upload response that could not be parsed) was a third failure
 * signal again. A thrown exception is the one channel: the callers all already run these uploads
 * inside a `try`, so failure lands where failure is handled.
 *
 * [serverStatus] separates the two kinds of failure for the caller reporting one to a user. It holds
 * Reddit's own status line when Reddit refused the upload, and is null when this app diagnosed the
 * failure itself — because [getMessage] is written for a log and is English whatever the device's
 * language is, while every screen that reports a failed submission already falls back to a translated
 * message of its own when handed nothing to show.
 */
class MediaUploadException private constructor(
    message: String,
    val serverStatus: String?,
) : IOException(message) {

    /** A failure this app diagnosed: [message] is a log line, not something to put on screen. */
    constructor(message: String) : this(message, null)

    companion object {
        /** Reddit refused the upload; [code] and [message] are its own status line. */
        @JvmStatic
        fun refused(code: Int, message: String): MediaUploadException {
            val status = if (message.isEmpty()) code.toString() else "$code $message"
            return MediaUploadException(status, status)
        }
    }
}
