package ml.docilealligator.infinityforreddit.video

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy

/**
 * Gives up on a playlist or manifest the server refused, instead of asking again.
 *
 * The default policy retries every failed load, so a 403 on a playlist surfaces only after three
 * more tries over about three seconds. For v.redd.it those tries cannot succeed: the CDN keeps a
 * refusal for ten minutes and serves it from cache (issue #498). Failing at once is what lets
 * [RedditDashFallback] start the DASH retry without the user watching a spinner first.
 *
 * Only 403, 404 and 410 on manifest loads are affected. Segments, network failures and server
 * errors keep the default retries, since those can come right on a second try.
 */
@OptIn(UnstableApi::class)
class RefusedPlaylistLoadErrorPolicy : DefaultLoadErrorHandlingPolicy() {

    override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo): Long {
        val error = loadErrorInfo.exception
        if (loadErrorInfo.mediaLoadData.dataType == C.DATA_TYPE_MANIFEST &&
            error is HttpDataSource.InvalidResponseCodeException &&
            error.responseCode in REFUSED
        ) {
            return C.TIME_UNSET
        }
        return super.getRetryDelayMsFor(loadErrorInfo)
    }

    private companion object {
        val REFUSED = setOf(403, 404, 410)
    }
}
