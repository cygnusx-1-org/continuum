package ml.docilealligator.infinityforreddit.decoder;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class CommentDecoderTest {
    @Test
    public void decodesBinary() {
        assertEquals("Hi", CommentDecoder.binaryToText("01001000 01101001"));
    }

    @Test
    public void rejectsUnprintableBinary() {
        assertNull(CommentDecoder.binaryToText("00000001 00000010"));
    }

    @Test
    public void insertsMarkerAfterMatchingParagraph() {
        String annotated = CommentDecoder.annotate("01001000 01101001\n\nnothing here");
        assertEquals("01001000 01101001\n\n{{decoded:BIN:Hi}}\n\nnothing here", annotated);
    }

    @Test
    public void markerRoundTrips() {
        String annotated = CommentDecoder.annotate("01001000 01101001 00100000 00100110");
        String marker = annotated.substring(annotated.indexOf(CommentDecoder.MARKER_PREFIX));
        assertArrayEquals(new String[]{"BIN", "Hi &"}, CommentDecoder.parseMarker(marker));
        assertNull(CommentDecoder.parseMarker("just a line"));
        assertNull(CommentDecoder.parseMarker("{{decoded:OTHER:x}}"));
    }

    @Test
    public void doesNotDecodeNato() {
        String body = "kilo lima mike";
        assertSame(body, CommentDecoder.annotate(body));
    }

    @Test
    public void addsEachDecodedValueOnce() {
        String annotated = CommentDecoder.annotate("01001000 01101001\n\n01001000 01101001");
        assertEquals(annotated.indexOf("{{decoded:BIN"), annotated.lastIndexOf("{{decoded:BIN"));
    }

    @Test
    public void searchTermDropsLinkAndLabel() {
        assertEquals("name", CommentDecoder.searchTerm("https://link.me/name"));
        assertEquals("name", CommentDecoder.searchTerm("TikTok and IG: name"));
        assertEquals("name", CommentDecoder.searchTerm("name"));
        assertEquals("name", CommentDecoder.searchTerm("https://link.me/name/"));
        assertEquals("name", CommentDecoder.searchTerm("https://link.me/name//"));
        assertEquals("name", CommentDecoder.searchTerm("TikTok and IG: https://link.me/name/"));
        assertEquals("a", CommentDecoder.searchTerm("a/"));
        assertEquals("/", CommentDecoder.searchTerm("/"));
    }

    @Test
    public void searchUrlsCarryTheQuery() {
        assertTrue(CommentDecoder.simpCityUrl("n", true).contains("q=n&c%5Btitle_only%5D=1"));
        assertTrue(CommentDecoder.simpCityUrl("n", false).contains("q=n&o=relevance"));
        assertTrue(CommentDecoder.socialSearchUrl("n").contains("q=n+%28site%3Ainstagram.com"));
    }

    @Test
    public void decodesBinaryWrappedAcrossLines() {
        String annotated = CommentDecoder.annotate("01001000\n01101001");
        assertTrue(annotated, annotated.endsWith("{{decoded:BIN:Hi}}"));
    }

    @Test
    public void returnsSameStringWhenNothingDecodes() {
        String body = "just a normal comment";
        assertSame(body, CommentDecoder.annotate(body));
    }
}
