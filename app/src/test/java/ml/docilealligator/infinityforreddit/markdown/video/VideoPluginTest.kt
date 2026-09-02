package ml.docilealligator.infinityforreddit.markdown.video

import ml.docilealligator.infinityforreddit.TestInfinity
import ml.docilealligator.infinityforreddit.thing.MediaMetadata
import org.commonmark.node.Node
import org.commonmark.parser.Parser
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A [VideoPlugin] resolves an embedded `reddit.com/link/.../video/.../player` link through a media
 * metadata map that is rebound before every render, from whichever comment or post body is being
 * bound at that moment.
 *
 * Three adapters build a plugin of their own and rebind it independently, so the map has to belong
 * to the plugin. While the parser factory was a companion object there was one process-wide slot
 * behind all of them: a plugin that had never been given a map read whatever another screen last
 * set, and rebinding one plugin silently rebound the rest.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestInfinity::class)
class VideoPluginTest {

    private fun metadata(id: String) = MediaMetadata(
        id, "video",
        MediaMetadata.MediaItem(1920, 1080, "https://v.redd.it/$id/DASH_1080.mp4"),
        MediaMetadata.MediaItem(640, 360, "https://v.redd.it/$id/DASH_360.mp4")
    )

    private fun playerLink(mediaId: String) =
        "![gif](https://reddit.com/link/abc123/video/$mediaId/player)"

    private fun parseWith(plugin: VideoPlugin, markdown: String): List<VideoBlock> {
        val builder = Parser.builder()
        plugin.configureParser(builder)
        return videoBlocks(builder.build().parse(markdown))
    }

    private fun videoBlocks(document: Node): List<VideoBlock> {
        val blocks = mutableListOf<VideoBlock>()
        var child = document.firstChild
        while (child != null) {
            if (child is VideoBlock) {
                blocks.add(child)
            }
            child = child.next
        }
        return blocks
    }

    @Test
    fun `a plugin renders the video its own map describes`() {
        val plugin = VideoPlugin()
        plugin.setMediaMetadataMap(mapOf("vid1" to metadata("vid1")))

        val blocks = parseWith(plugin, playerLink("vid1"))

        assertEquals(1, blocks.size)
        assertEquals("vid1", blocks[0].mediaMetadata.id)
    }

    @Test
    fun `a player link the map does not describe renders as ordinary markdown`() {
        val plugin = VideoPlugin()
        plugin.setMediaMetadataMap(mapOf("vid1" to metadata("vid1")))

        assertEquals(emptyList<VideoBlock>(), parseWith(plugin, playerLink("vid2")))
    }

    @Test
    fun `one plugin's map never reaches a plugin that was never given one`() {
        val bound = VideoPlugin()
        val unbound = VideoPlugin()
        bound.setMediaMetadataMap(mapOf("vid1" to metadata("vid1")))

        // The post detail screen builds one plugin for the post body and another for the comments;
        // a comment with no media metadata at all must not render the post's video.
        assertEquals(emptyList<VideoBlock>(), parseWith(unbound, playerLink("vid1")))
    }

    @Test
    fun `rebinding one plugin's map leaves another plugin's alone`() {
        val first = VideoPlugin()
        val second = VideoPlugin()
        first.setMediaMetadataMap(mapOf("vid1" to metadata("vid1")))
        second.setMediaMetadataMap(mapOf("vid2" to metadata("vid2")))

        assertEquals("vid1", parseWith(first, playerLink("vid1")).single().mediaMetadata.id)
        assertEquals("vid2", parseWith(second, playerLink("vid2")).single().mediaMetadata.id)

        // Clearing one -- what binding a comment that carries no media does -- must not blind the
        // other.
        second.setMediaMetadataMap(null)

        assertEquals("vid1", parseWith(first, playerLink("vid1")).single().mediaMetadata.id)
        assertEquals(emptyList<VideoBlock>(), parseWith(second, playerLink("vid2")))
    }
}
