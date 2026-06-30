package ml.docilealligator.infinityforreddit.events

/**
 * Posted by a download service when a media file has been prepared in the cache for sharing
 * (rather than saved to the user's download folder). The foreground activity that requested the
 * share listens for this and launches the system share sheet.
 */
data class ShareMediaEvent(
    @JvmField val filePath: String,
    @JvmField val mimeType: String
)
