package ml.docilealligator.infinityforreddit.markdown.video

import ml.docilealligator.infinityforreddit.thing.MediaMetadata
import org.commonmark.node.Block
import org.commonmark.parser.block.AbstractBlockParser
import org.commonmark.parser.block.AbstractBlockParserFactory
import org.commonmark.parser.block.BlockContinue
import org.commonmark.parser.block.BlockStart
import org.commonmark.parser.block.MatchedBlockParser
import org.commonmark.parser.block.ParserState
import java.util.regex.Pattern

class VideoBlockParser(
    mediaMetadata: MediaMetadata
): AbstractBlockParser() {
    private val videoBlock: VideoBlock = VideoBlock(mediaMetadata)

    override fun getBlock(): Block {
        return videoBlock
    }

    override fun tryContinue(parserState: ParserState?): BlockContinue? {
        return null
    }

    /**
     * One factory per [VideoPlugin], holding that plugin's map -- the same shape as
     * ImageAndGifBlockParser.Factory.
     *
     * The map is rebound before every render, from whichever comment or post body is being bound,
     * so it must not outlive the plugin that owns it. As a companion object it was a single
     * process-wide slot shared by all three adapters that build a VideoPlugin, which both kept the
     * last-bound comment's MediaMetadata alive for the life of the process and left every plugin
     * reading a map some other screen had set.
     */
    class Factory : AbstractBlockParserFactory() {
        private var mediaMetadataMap: Map<String, MediaMetadata>? = null

        override fun tryStart(
            state: ParserState,
            matchedBlockParser: MatchedBlockParser?
        ): BlockStart? {
            return mediaMetadataMap?.let { mediaMetadataMap ->
                val line = state.line.toString()
                val matcher = videoPattern.matcher(line)
                if (matcher.find() && matcher.end() == line.length) {
                    val id = matcher.group(2)
                    mediaMetadataMap[id ?: ""]?.let {
                        BlockStart.of(VideoBlockParser(it))
                    } ?: BlockStart.none()
                } else BlockStart.none()
            } ?: BlockStart.none()
        }

        fun setMediaMetadataMap(mediaMetadataMap: Map<String, MediaMetadata>?) {
            this.mediaMetadataMap = mediaMetadataMap
        }

        private companion object {
            // Stateless, so it stays shared: compiling this per factory would be pure waste.
            private val videoPattern: Pattern =
                Pattern.compile("!\\[.*]\\(https://reddit\\.com/link/([^/]+)/video/([^/]+)/player\\)")
        }
    }
}
