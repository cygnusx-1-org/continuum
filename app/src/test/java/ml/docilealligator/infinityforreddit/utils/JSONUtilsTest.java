package ml.docilealligator.infinityforreddit.utils;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.Map;

import ml.docilealligator.infinityforreddit.thing.MediaMetadata;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class JSONUtilsTest {

    /**
     * Valid Giphy entries must be stored under the full key (e.g. "giphy|abc123|downsized")
     * so that ImageAndGifBlockParser can find them via containsKey().
     */
    @Test
    public void parseMediaMetadata_giphyValidStatus_storedUnderFullKey() throws Exception {
        JSONObject mediaEntry = new JSONObject()
                .put("e", "AnimatedImage")
                .put("id", "abc123")
                .put("s", new JSONObject()
                        .put("gif", "https://media.giphy.com/media/abc123/giphy.gif")
                        .put("mp4", "https://media.giphy.com/media/abc123/giphy.mp4")
                        .put("x", 480)
                        .put("y", 270));

        JSONObject mediaMetadata = new JSONObject()
                .put("giphy|abc123|downsized", mediaEntry);

        JSONObject data = new JSONObject()
                .put("media_metadata", mediaMetadata);

        Map<String, MediaMetadata> result = JSONUtils.parseMediaMetadata(data);

        assertNotNull(result);
        // Must be keyed by the full markdown id, not the bare giphy id
        assertTrue(result.containsKey("giphy|abc123|downsized"),
                "Giphy metadata must be stored under the full key 'giphy|abc123|downsized'");
        assertFalse(result.containsKey("abc123"),
                "Giphy metadata must NOT be stored under the bare id 'abc123'");
    }

    /**
     * Giphy entries without the "|downsized" suffix must also be stored under the full key.
     */
    @Test
    public void parseMediaMetadata_giphyValidStatusNoSuffix_storedUnderFullKey() throws Exception {
        JSONObject mediaEntry = new JSONObject()
                .put("e", "AnimatedImage")
                .put("id", "xyz789")
                .put("s", new JSONObject()
                        .put("gif", "https://media.giphy.com/media/xyz789/giphy.gif")
                        .put("mp4", "https://media.giphy.com/media/xyz789/giphy.mp4")
                        .put("x", 480)
                        .put("y", 270));

        JSONObject mediaMetadata = new JSONObject()
                .put("giphy|xyz789", mediaEntry);

        JSONObject data = new JSONObject()
                .put("media_metadata", mediaMetadata);

        Map<String, MediaMetadata> result = JSONUtils.parseMediaMetadata(data);

        assertNotNull(result);
        assertTrue(result.containsKey("giphy|xyz789"),
                "Giphy metadata must be stored under the full key 'giphy|xyz789'");
        assertFalse(result.containsKey("xyz789"),
                "Giphy metadata must NOT be stored under the bare id 'xyz789'");
    }

    /**
     * Non-Giphy entries (regular images) must still be stored under the bare id.
     */
    @Test
    public void parseMediaMetadata_regularImage_storedUnderBareId() throws Exception {
        JSONObject mediaEntry = new JSONObject()
                .put("e", "Image")
                .put("id", "imgabc123")
                .put("s", new JSONObject()
                        .put("u", "https://preview.redd.it/imgabc123.jpg")
                        .put("x", 1080)
                        .put("y", 720));

        JSONObject mediaMetadata = new JSONObject()
                .put("imgabc123", mediaEntry);

        JSONObject data = new JSONObject()
                .put("media_metadata", mediaMetadata);

        Map<String, MediaMetadata> result = JSONUtils.parseMediaMetadata(data);

        assertNotNull(result);
        assertTrue(result.containsKey("imgabc123"),
                "Regular image metadata must be stored under the bare id");
    }

    /**
     * When media_metadata is absent, parseMediaMetadata must return null.
     */
    @Test
    public void parseMediaMetadata_noMediaMetadata_returnsNull() throws Exception {
        JSONObject data = new JSONObject().put("body", "hello world");

        Map<String, MediaMetadata> result = JSONUtils.parseMediaMetadata(data);

        assertNull(result);
    }
}
