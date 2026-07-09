package ml.docilealligator.infinityforreddit.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Robolectric supplies the real Android {@code org.json} and {@code HtmlCompat} implementations,
 * both of which {@link TitleSuggestionUtils} depends on for correctness: the stub org.json would
 * return defaults instead of coercing a JSON null to the string "null", and HtmlCompat cannot
 * unescape entities off-device.
 *
 * <p>The oEmbed status codes asserted here were verified against YouTube's live endpoint. Routing a
 * URL oEmbed rejects is not a harmless miss — there is no HTML fallback on the oEmbed path, so it
 * becomes an unconditional "failed to suggest a title".
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, application = Application.class)
public class TitleSuggestionUtilsTest {

    private static final String OEMBED = "https://www.youtube.com/oembed?format=json&url=";

    /** The value oEmbed is asked about, decoded back out of the query string. */
    private static String embeddedUrl(String url) {
        String oEmbedUrl = TitleSuggestionUtils.youTubeOEmbedUrl(url);
        assertNotNull("expected oEmbed routing for " + url, oEmbedUrl);
        assertEquals("unexpected oEmbed endpoint for " + url, OEMBED, oEmbedUrl.substring(0, OEMBED.length()));
        return URLDecoder.decode(oEmbedUrl.substring(OEMBED.length()), StandardCharsets.UTF_8);
    }

    private static void assertScraped(String url) {
        assertNull("expected HTML scrape for " + url, TitleSuggestionUtils.youTubeOEmbedUrl(url));
    }

    @Test
    public void youTubeShortLinkIsRoutedToOEmbedVerbatim() {
        assertEquals(
                OEMBED + "https%3A%2F%2Fyoutu.be%2FdQw4w9WgXcQ%3Fsi%3Dabc",
                TitleSuggestionUtils.youTubeOEmbedUrl("https://youtu.be/dQw4w9WgXcQ?si=abc"));
    }

    @Test
    public void oEmbedSupportedFormsAreRebuiltInCanonicalForm() {
        // Each canonical form returns 200 from YouTube's oEmbed endpoint.
        String watch = "https://www.youtube.com/watch?v=dQw4w9WgXcQ";
        assertEquals(watch, embeddedUrl("https://www.youtube.com/watch?v=dQw4w9WgXcQ"));
        assertEquals(watch, embeddedUrl("https://youtube.com/watch?v=dQw4w9WgXcQ"));
        assertEquals(watch, embeddedUrl("https://m.youtube.com/watch?v=dQw4w9WgXcQ"));
        assertEquals(watch, embeddedUrl("https://music.youtube.com/watch?v=dQw4w9WgXcQ"));
        // Host matching is case insensitive (OkHttp canonicalizes the host).
        assertEquals(watch, embeddedUrl("https://WWW.YouTube.com/watch?v=dQw4w9WgXcQ"));
        assertEquals("https://www.youtube.com/shorts/GtL1huin9EE",
                embeddedUrl("https://www.youtube.com/shorts/GtL1huin9EE"));
        assertEquals("https://www.youtube.com/live/21X5lGlDOfg",
                embeddedUrl("https://www.youtube.com/live/21X5lGlDOfg"));
        assertEquals("https://www.youtube.com/playlist?list=PL123",
                embeddedUrl("https://www.youtube.com/playlist?list=PL123"));
    }

    @Test
    public void uppercaseRouteKeywordsAreCanonicalizedWithIdsUntouched() {
        // YouTube itself misroutes these (/WATCH is a channel lookup, /Shorts 404s) and oEmbed
        // rejects them verbatim, but they still unambiguously name a video. The ids must keep
        // their case: lowercasing dQw4w9WgXcQ would name a different (nonexistent) video.
        assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ",
                embeddedUrl("https://www.youtube.com/WATCH?v=dQw4w9WgXcQ"));
        assertEquals("https://www.youtube.com/shorts/GtL1huin9EE",
                embeddedUrl("https://www.youtube.com/Shorts/GtL1huin9EE"));
        assertEquals("https://www.youtube.com/live/21X5lGlDOfg",
                embeddedUrl("https://www.youtube.com/LIVE/21X5lGlDOfg"));
        assertEquals("https://www.youtube.com/playlist?list=PL123",
                embeddedUrl("https://www.youtube.com/Playlist?list=PL123"));
        assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ",
                embeddedUrl("https://www.youtube.com/EMBED/dQw4w9WgXcQ"));
    }

    @Test
    public void uppercaseSchemeIsAccepted() {
        // URLUtil.isHttpsUrl is case-insensitive, so "HTTPS://..." reaches routing unprefixed;
        // OkHttp canonicalizes the scheme and host, leaving the case-sensitive id untouched.
        assertEquals("https://youtu.be/dQw4w9WgXcQ",
                embeddedUrl("HTTPS://youtu.be/dQw4w9WgXcQ"));
    }

    @Test
    public void strayQueryParametersAreDroppedFromTheProbeUrl() {
        assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ",
                embeddedUrl("https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=90s&pp=xyz"));
    }

    @Test
    public void queryParameterNamesStayCaseSensitive() {
        // YouTube does not honor ?V= either; scraping mirrors what the site would actually serve.
        assertScraped("https://www.youtube.com/watch?V=dQw4w9WgXcQ");
    }

    @Test
    public void embedUrlsAreRewrittenToTheirCanonicalForm() {
        // oEmbed 404s on /embed/ URLs even though it serves the video they play.
        assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ",
                embeddedUrl("https://www.youtube.com/embed/dQw4w9WgXcQ"));
        assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ",
                embeddedUrl("https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ"));
    }

    @Test
    public void embedVideoseriesIsRewrittenToAPlaylist() {
        // /embed/videoseries?list=… names a playlist; watch?v=videoseries would 404.
        assertEquals("https://www.youtube.com/playlist?list=PL123",
                embeddedUrl("https://www.youtube-nocookie.com/embed/videoseries?list=PL123"));
    }

    @Test
    public void youTubeUrlsOEmbedRejectsFallBackToScraping() {
        // Every one of these 404s on oEmbed, and scraped fine before oEmbed was introduced.
        assertScraped("https://www.youtube.com/@RickAstleyYT");
        assertScraped("https://www.youtube.com/c/RickAstleyYT");
        assertScraped("https://www.youtube.com/user/RickAstleyYT");
        assertScraped("https://www.youtube.com/feed/trending");
        assertScraped("https://www.youtube.com/results?search_query=rickroll");
    }

    @Test
    public void malformedYouTubeUrlsFallBackToScraping() {
        assertScraped("https://www.youtube.com/watch?v=");
        assertScraped("https://www.youtube.com/watch");
        assertScraped("https://www.youtube.com/playlist");
        assertScraped("https://www.youtube.com/shorts");
        assertScraped("https://www.youtube.com/embed");
        assertScraped("https://www.youtube.com/embed/videoseries");
        assertScraped("https://youtu.be/");
        assertScraped("https://youtu.be/dQw4w9WgXcQ/extra");
    }

    @Test
    public void nonYouTubeHostsFallBackToScraping() {
        assertScraped("https://example.com/not-youtube");
        assertScraped("https://en.wikipedia.org/wiki/AT%26T");
        // A host that merely contains the YouTube domain must not be treated as YouTube.
        assertScraped("https://notyoutube.com/watch?v=x");
        assertScraped("https://youtube.com.evil.example/watch?v=x");
    }

    @Test
    public void unparseableUrlFallsBackToScraping() {
        assertScraped("not a url at all");
    }

    @Test
    public void oEmbedTitleIsExtracted() {
        assertEquals("Never Gonna Give You Up",
                TitleSuggestionUtils.parseOEmbedTitle("{\"title\":\"Never Gonna Give You Up\"}"));
    }

    @Test
    public void oEmbedTitleKeepsAmpersandDecoded() {
        assertEquals("InsurAAAnce & Rick Astley",
                TitleSuggestionUtils.parseOEmbedTitle("{\"title\":\"InsurAAAnce & Rick Astley\"}"));
    }

    @Test
    public void oEmbedTitlesAreTrimmedAndWhitespaceNormalized() {
        assertEquals("Foo", TitleSuggestionUtils.parseOEmbedTitle("{\"title\":\"  Foo  \"}"));
        assertEquals("Foo bar", TitleSuggestionUtils.parseOEmbedTitle("{\"title\":\"Foo\\n bar\"}"));
        // U+00A0 is not \s; the shared normalizer must still strip it.
        assertEquals("Foo", TitleSuggestionUtils.parseOEmbedTitle("{\"title\":\"Foo\u00A0\"}"));
    }

    @Test
    public void oEmbedNullTitleIsNotTheStringNull() {
        assertNull(TitleSuggestionUtils.parseOEmbedTitle("{\"title\":null}"));
    }

    @Test
    public void oEmbedMissingOrJunkBodyYieldsNull() {
        assertNull(TitleSuggestionUtils.parseOEmbedTitle("{}"));
        assertNull(TitleSuggestionUtils.parseOEmbedTitle("{\"title\":\"  \"}"));
        assertNull(TitleSuggestionUtils.parseOEmbedTitle("Not Found"));
        assertNull(TitleSuggestionUtils.parseOEmbedTitle(""));
        assertNull(TitleSuggestionUtils.parseOEmbedTitle(null));
    }

    @Test
    public void htmlEntitiesInTitleAreUnescaped() {
        assertEquals("AT&T - Wikipedia",
                TitleSuggestionUtils.parseHtmlTitle("<head><title>AT&amp;T - Wikipedia</title></head>"));
    }

    @Test
    public void nonBreakingSpacesAreNormalized() {
        // HtmlCompat decodes &nbsp; to U+00A0, which neither \s nor trim() considers whitespace.
        assertEquals("Hello",
                TitleSuggestionUtils.parseHtmlTitle("<head><title>&nbsp;Hello&nbsp;</title></head>"));
        assertEquals("Hello there",
                TitleSuggestionUtils.parseHtmlTitle("<head><title>Hello&nbsp;there</title></head>"));
        assertNull(TitleSuggestionUtils.parseHtmlTitle("<head><title>&nbsp;</title></head>"));
    }

    @Test
    public void otherUnicodeSpacesAreNormalized() {
        // U+3000 ideographic, U+202F narrow no-break, U+2007 figure space: none match \s.
        assertEquals("Hello there", TitleSuggestionUtils.parseHtmlTitle(
                "<head><title>&#12288;Hello&#8239;there&#8199;</title></head>"));
    }

    @Test
    public void titleTagIsMatchedCaseInsensitivelyAndWithAttributes() {
        assertEquals("Hello", TitleSuggestionUtils.parseHtmlTitle("<HEAD><TITLE>Hello</TITLE></HEAD>"));
        assertEquals("Hello", TitleSuggestionUtils.parseHtmlTitle("<head><title lang=\"en\">Hello</title></head>"));
        assertEquals("Hello", TitleSuggestionUtils.parseHtmlTitle("<head><title\n  lang=\"en\">Hello</title></head>"));
    }

    @Test
    public void tagsThatMerelyStartWithTitleAreNotMatched() {
        assertNull(TitleSuggestionUtils.parseHtmlTitle("<head><titlecase>oops</title></head>"));
    }

    @Test
    public void surroundingWhitespaceIsCollapsedAndTrimmed() {
        assertEquals("Hello there",
                TitleSuggestionUtils.parseHtmlTitle("<head><title>\n  Hello\n  there\n</title></head>"));
    }

    @Test
    public void headTitleWinsOverASvgTitleInTheBody() {
        assertEquals("Real Title", TitleSuggestionUtils.parseHtmlTitle(
                "<html><head><title>Real Title</title></head>"
                        + "<body><svg><title>Logo</title></svg></body></html>"));
    }

    @Test
    public void titleOutsideHeadIsStillFound() {
        // Malformed pages that close <head> without ever declaring a title.
        assertEquals("Body Title", TitleSuggestionUtils.parseHtmlTitle(
                "<html><head><meta charset=\"utf-8\"></head><body><title>Body Title</title></body></html>"));
    }

    @Test
    public void titleAtTheEndOfAVeryLargeHeadIsFound() {
        // The shape of the real desktop youtube.com watch page: <title> ~636 KB into a ~646 KB
        // <head>. Any scan capped near the top of the document silently returns no title here.
        String html = "<html><head>" + "<meta name=\"x\" content=\"y\">".repeat(25_000)
                + "<title>Deep Title</title></head><body>hi</body></html>";
        assertEquals("Deep Title", TitleSuggestionUtils.parseHtmlTitle(html));
    }

    @Test
    public void titleFarIntoTheBodyIsFound() {
        String html = "<html><head></head><body>" + "x".repeat(200_000)
                + "<title>Far Title</title></body></html>";
        assertEquals("Far Title", TitleSuggestionUtils.parseHtmlTitle(html));
    }

    /**
     * A lazy {@code <title...>(.*?)</title>} rescans the tail of the document once per open tag that
     * never closes; this input takes ~55 s that way, versus ~2 ms for an indexOf scan. Asserted on
     * elapsed time rather than {@code @Test(timeout)}, which RobolectricTestRunner ignores.
     */
    @Test
    public void manyUnclosedTitleTagsDoNotBacktrackQuadratically() {
        String html = "<title>".repeat(20_000) + "y".repeat(200_000);

        long startNanos = System.nanoTime();
        assertNull(TitleSuggestionUtils.parseHtmlTitle(html));
        long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000;

        assertTrue("parseHtmlTitle took " + elapsedMs + " ms; a quadratic scan takes ~55 s", elapsedMs < 5_000);
    }

    @Test
    public void missingOrEmptyTitleYieldsNull() {
        assertNull(TitleSuggestionUtils.parseHtmlTitle("<html><head></head></html>"));
        assertNull(TitleSuggestionUtils.parseHtmlTitle("<head><title>   </title></head>"));
        assertNull(TitleSuggestionUtils.parseHtmlTitle(""));
        assertNull(TitleSuggestionUtils.parseHtmlTitle(null));
    }
}
