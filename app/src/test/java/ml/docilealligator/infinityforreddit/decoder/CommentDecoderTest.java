package ml.docilealligator.infinityforreddit.decoder;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;

import org.junit.Test;

public class CommentDecoderTest {
    // Four lines: a name, a handle and two links.
    private static final String SAMPLE_B64 =
            "RGVlbmEgVWRkaW4KQGRlZW51dHR0Cmh0dHBzOi8vYnVua3IuY3IvYS9WTVZFVjdqdgpodHRwczovL2J1bmtyLmNyL2EvS1pDUHlOWnk=";
    private static final String SAMPLE_TEXT =
            "Deena Uddin\n@deenuttt\nhttps://bunkr.cr/a/VMVEV7jv\nhttps://bunkr.cr/a/KZCPyNZy";

    private static String b64(String text) {
        return Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }

    private static String bin(String text) {
        StringBuilder bits = new StringBuilder();
        for (byte b : text.getBytes(StandardCharsets.UTF_8)) {
            if (bits.length() > 0) bits.append(' ');
            String s = Integer.toBinaryString(b & 0xFF);
            for (int i = s.length(); i < 8; i++) bits.append('0');
            bits.append(s);
        }
        return bits.toString();
    }

    private static int count(String haystack, String needle) {
        int n = 0;
        for (int i = haystack.indexOf(needle); i >= 0; i = haystack.indexOf(needle, i + 1)) n++;
        return n;
    }

    // --- binary -------------------------------------------------------------------------------

    @Test
    public void decodesBinary() {
        assertEquals("Hi", CommentDecoder.binaryToText("01001000 01101001"));
    }

    @Test
    public void rejectsUnprintableBinary() {
        assertNull(CommentDecoder.binaryToText("00000001 00000010"));
    }

    @Test
    public void rejectsBinaryAboveAscii() {
        assertNull(CommentDecoder.binaryToText("01001000 11111111"));
    }

    @Test
    public void rejectsBinaryThatIsNotWholeBytes() {
        assertNull(CommentDecoder.binaryToText("01001000 0110100"));
    }

    @Test
    public void insertsMarkerAfterMatchingParagraph() {
        String annotated = CommentDecoder.annotate("01001000 01101001\n\nnothing here");
        assertEquals("01001000 01101001\n\n{{decoded:BIN:Hi}}\n\nnothing here", annotated);
    }

    @Test
    public void decodesBinaryWrappedAcrossLines() {
        String annotated = CommentDecoder.annotate("01001000\n01101001");
        assertTrue(annotated, annotated.endsWith("{{decoded:BIN:Hi}}"));
    }

    @Test
    public void severalBinaryMatchesInOneParagraphEachGetABox() {
        String annotated = CommentDecoder.annotate("01001000 01101001 and then 01001111 01001011");
        assertTrue(annotated, annotated.contains("{{decoded:BIN:Hi}}"));
        assertTrue(annotated, annotated.contains("{{decoded:BIN:OK}}"));
    }

    // --- base64 -------------------------------------------------------------------------------

    @Test
    public void decodesTheSampleBase64() {
        assertEquals(SAMPLE_TEXT, CommentDecoder.base64ToText(SAMPLE_B64));
    }

    @Test
    public void theSampleGetsOneB64Box() {
        String annotated = CommentDecoder.annotate(SAMPLE_B64);
        String marker = annotated.substring(annotated.indexOf(CommentDecoder.MARKER_PREFIX));
        assertArrayEquals(new String[]{"B64", SAMPLE_TEXT}, CommentDecoder.parseMarker(marker));
        assertEquals(1, count(annotated, "{{decoded:"));
    }

    @Test
    public void ordinaryLongWordsAndIdsAreNotDecoded() {
        for (String body : new String[]{
                "internationalization", "Pneumonoultramicroscopicsilicovolcanoconiosis",
                "id AbCdEfGhIjKlMnOpQrSt1234 here", "https://i.imgur.com/AbCdEfGhIjKlMnOpQrSt1234"}) {
            assertSame(body, CommentDecoder.annotate(body));
        }
    }

    @Test
    public void rejectsBase64ThatIsNotUtf8() {
        byte[] bytes = new byte[16];
        Arrays.fill(bytes, (byte) 0xFF);
        assertNull(CommentDecoder.base64ToText(Base64.getEncoder().encodeToString(bytes)));
    }

    @Test
    public void rejectsBase64WithControlCharacters() {
        byte[] bytes = new byte[16];
        for (int i = 0; i < bytes.length; i++) bytes[i] = (byte) (i + 1);
        assertNull(CommentDecoder.base64ToText(Base64.getEncoder().encodeToString(bytes)));
    }

    @Test
    public void keepsTabsAndLineBreaksInBase64() {
        assertEquals("a\tb\r\nc and more text", CommentDecoder.base64ToText(b64("a\tb\r\nc and more text")));
    }

    @Test
    public void base64IsNotTakenFromTheMiddleOfALongerRun() {
        // Preceded by a base64 character, so it is not a match of its own.
        String body = "x" + b64("hello world!!");
        assertSame(body, CommentDecoder.annotate(body));
    }

    // --- which results get a box ----------------------------------------------------------------

    @Test
    public void binaryWinsOverBase64InAParagraph() {
        String annotated = CommentDecoder.annotate("01001000 01101001 " + SAMPLE_B64);
        assertTrue(annotated, annotated.contains("{{decoded:BIN:Hi}}"));
        assertFalse(annotated, annotated.contains("{{decoded:B64"));
    }

    @Test
    public void base64IsTriedWhenBinaryRenderedNothing() {
        String annotated = CommentDecoder.annotate("00000001 00000010 " + b64("hello there world"));
        assertTrue(annotated, annotated.contains("{{decoded:B64:hello+there+world}}"));
    }

    @Test
    public void base64GetsAtMostOneBoxPerParagraph() {
        String annotated = CommentDecoder.annotate(b64("first message here") + " " + b64("second message here"));
        assertEquals(1, count(annotated, "{{decoded:B64"));
    }

    @Test
    public void aTextShownOnceIsNotShownAgainWhateverDecodedIt() {
        // A binary paragraph and a separate base64 paragraph with the same text: one box.
        String text = "hello world!!";
        String annotated = CommentDecoder.annotate(bin(text) + "\n\n" + b64(text));
        assertEquals(annotated, 1, count(annotated, "{{decoded:"));
        assertTrue(annotated, annotated.contains("{{decoded:BIN:"));
    }

    @Test
    public void theOtherWayRoundIsOneBoxToo() {
        String text = "hello world!!";
        String annotated = CommentDecoder.annotate(b64(text) + "\n\n" + bin(text));
        assertEquals(annotated, 1, count(annotated, "{{decoded:"));
        assertTrue(annotated, annotated.contains("{{decoded:B64:"));
    }

    @Test
    public void differentMessagesInOneCommentEachGetABox() {
        String annotated = CommentDecoder.annotate(
                bin("first message") + "\n\n" + b64("second message here") + "\n\n" + bin("third one"));
        assertEquals(annotated, 3, count(annotated, "{{decoded:"));
    }

    @Test
    public void addsEachDecodedValueOnce() {
        String annotated = CommentDecoder.annotate("01001000 01101001\n\n01001000 01101001");
        assertEquals(annotated.indexOf("{{decoded:BIN"), annotated.lastIndexOf("{{decoded:BIN"));
    }

    @Test
    public void emptyAndWhitespaceResultsGetNoBox() {
        String body = "00100000 00100000 00100000";
        assertSame(body, CommentDecoder.annotate(body));
    }

    @Test
    public void markerRoundTrips() {
        String annotated = CommentDecoder.annotate("01001000 01101001 00100000 00100110");
        String marker = annotated.substring(annotated.indexOf(CommentDecoder.MARKER_PREFIX));
        assertArrayEquals(new String[]{"BIN", "Hi &"}, CommentDecoder.parseMarker(marker));
        assertNull(CommentDecoder.parseMarker("just a line"));
        assertNull(CommentDecoder.parseMarker("{{decoded:OTHER:x}}"));
        assertNull(CommentDecoder.parseMarker("{{decoded:NATO:x}}"));
    }

    @Test
    public void doesNotDecodeNato() {
        String body = "kilo lima mike";
        assertSame(body, CommentDecoder.annotate(body));
    }

    @Test
    public void returnsSameStringWhenNothingDecodes() {
        String body = "just a normal comment";
        assertSame(body, CommentDecoder.annotate(body));
    }

    // --- search terms ---------------------------------------------------------------------------

    @Test
    public void handlesAreTheTermsWhenThereAreAny() {
        assertEquals(Arrays.asList("deenuttt"), CommentDecoder.extractSearchTerms(SAMPLE_TEXT));
        assertEquals(Arrays.asList("deenuttt"),
                CommentDecoder.extractSearchTerms("Deena Uddin @deenuttt https://bunkr.cr/a/VMVEV7jv"));
        assertEquals(Arrays.asList("a.b", "cd"), CommentDecoder.extractSearchTerms("@a.b and @cd"));
    }

    @Test
    public void linksAreDropped() {
        assertEquals(Arrays.asList("name"), CommentDecoder.extractSearchTerms("https://link.me/name"));
        assertEquals(Arrays.asList("name"), CommentDecoder.extractSearchTerms("https://link.me/name/"));
        assertEquals(Arrays.asList("name"), CommentDecoder.extractSearchTerms("https://link.me/name//"));
        assertEquals(Arrays.asList("Sha Rizel"),
                CommentDecoder.extractSearchTerms("Sha Rizel https://link.me/x"));
    }

    @Test
    public void aLeadingLabelIsDropped() {
        assertEquals(Arrays.asList("name"), CommentDecoder.extractSearchTerms("TikTok and IG: name"));
        assertEquals(Arrays.asList("name"), CommentDecoder.extractSearchTerms("TikTok and IG: https://link.me/name/"));
    }

    @Test
    public void severalNamesAreSplit() {
        assertEquals(Arrays.asList("Sha Rizel", "Skylor"), CommentDecoder.extractSearchTerms("Sha Rizel and Skylor"));
        assertEquals(Arrays.asList("a", "b"), CommentDecoder.extractSearchTerms("a & b"));
        assertEquals(Arrays.asList("a", "b"), CommentDecoder.extractSearchTerms("a + b"));
        assertEquals(Arrays.asList("a", "b"), CommentDecoder.extractSearchTerms("a X b"));
        assertEquals(Arrays.asList("a", "b", "c"), CommentDecoder.extractSearchTerms("a, b; c"));
        assertEquals(Arrays.asList("a", "b"), CommentDecoder.extractSearchTerms("a\nb"));
    }

    @Test
    public void termsAreDedupedAndCappedAtFour() {
        assertEquals(Arrays.asList("a", "b"), CommentDecoder.extractSearchTerms("a, b, a"));
        assertEquals(Arrays.asList("a", "b", "c", "d"), CommentDecoder.extractSearchTerms("a, b, c, d, e, f"));
    }

    @Test
    public void whatCannotBeSplitIsTheWholeText() {
        assertEquals(Arrays.asList("name"), CommentDecoder.extractSearchTerms("name"));
        assertEquals(Arrays.asList("a"), CommentDecoder.extractSearchTerms("a/"));
        assertEquals(Arrays.asList("/"), CommentDecoder.extractSearchTerms("/"));
        assertEquals(Arrays.asList("x"), CommentDecoder.extractSearchTerms("x"));
    }

    // --- search URLs ----------------------------------------------------------------------------

    @Test
    public void searchUrlsCarryTheQuery() {
        assertTrue(CommentDecoder.simpCityUrl("n", true).contains("q=n&c%5Btitle_only%5D=1"));
        assertTrue(CommentDecoder.simpCityUrl("n", false).contains("q=n&o=relevance"));
        assertFalse(CommentDecoder.simpCityUrl("n", false).contains("title_only"));
        assertTrue(CommentDecoder.socialSearchUrl("n").contains("q=n+%28site%3Ainstagram.com"));
    }

    @Test
    public void socialSearchCoversTheSites() {
        String url = CommentDecoder.socialSearchUrl("n");
        for (String site : new String[]{"instagram.com", "tiktok.com", "onlyfans.com", "fansly.com",
                "simpcity.cr", "fapello.com", "boobpedia.com", "babepedia.com"}) {
            assertTrue(url, url.contains("site%3A" + site));
        }
    }
}
