package ml.docilealligator.infinityforreddit.decoder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class CommentDecoderTest {
    @Test
    public void decodesNato() {
        assertEquals("AB1", CommentDecoder.natoToText("Alpha, bravo one"));
    }

    @Test
    public void ignoresFewerThanThreeCodeWords() {
        assertNull(CommentDecoder.natoToText("alpha bravo"));
    }

    @Test
    public void decodesBinary() {
        assertEquals("Hi", CommentDecoder.binaryToText("01001000 01101001"));
    }

    @Test
    public void rejectsUnprintableBinary() {
        assertNull(CommentDecoder.binaryToText("00000001 00000010"));
    }

    @Test
    public void insertsBlockAfterMatchingParagraph() {
        String annotated = CommentDecoder.annotate("charlie delta echo\n\nnothing here");
        assertTrue(annotated, annotated.startsWith(
                "charlie delta echo\n\n> **NATO** `CDE`\n>\n> [📋](" + CommentDecoder.COPY_URL_PREFIX + "CDE)"));
        assertTrue(annotated, annotated.contains(" · [🏷️](") && annotated.contains(" · [🌐](")
                && annotated.contains(" · [🔍]("));
        assertTrue(annotated, annotated.endsWith("\n\nnothing here"));
        assertTrue(annotated, annotated.contains("&c%5Btitle_only%5D=1"));
    }

    @Test
    public void copyLinksRoundTrip() {
        String annotated = CommentDecoder.annotate("01001000 01101001 00100000 00100110");
        int start = annotated.indexOf(CommentDecoder.COPY_URL_PREFIX);
        String url = annotated.substring(start, annotated.indexOf(')', start));
        assertEquals("Hi &", CommentDecoder.copyText(url));
        assertNull(CommentDecoder.copyText("https://www.reddit.com/r/pics"));
    }

    @Test
    public void addsEachDecodedValueOnce() {
        String annotated = CommentDecoder.annotate("kilo lima mike\n\nkilo lima mike");
        assertEquals(annotated.indexOf("**NATO**"), annotated.lastIndexOf("**NATO**"));
    }

    @Test
    public void returnsSameStringWhenNothingDecodes() {
        String body = "just a normal comment";
        assertSame(body, CommentDecoder.annotate(body));
    }
}
