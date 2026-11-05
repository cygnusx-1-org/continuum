package ml.docilealligator.infinityforreddit.decoder;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds 8-bit binary and base64 in comment and post Markdown and appends the decoded text, as a
 * marker line DecoderBlockParser turns into a card, below the paragraph it appears in.
 *
 * <p>Each decoder is a pure function, string in and decoded string or null out. The rules for
 * which results get a card are in {@link #annotateUncached}.
 */
public final class CommentDecoder {
    static final String MARKER_PREFIX = "{{decoded:";
    private static final Pattern MARKER = Pattern.compile("^\\{\\{decoded:(BIN|B64):([^}]*)\\}\\}$");

    private static final String[] SOCIAL_SITES = {
            "instagram.com",
            "tiktok.com",
            "onlyfans.com",
            "fansly.com",
            "simpcity.cr",
            "fapello.com",
            "boobpedia.com",
            "babepedia.com",
    };

    private static final Pattern WHITESPACE = Pattern.compile("\\s");
    private static final Pattern PARAGRAPH_BREAK = Pattern.compile("\\n[ \\t]*\\n");
    private static final Pattern BINARY = Pattern.compile("\\b(?:[01]{8}\\s*){2,}\\b");
    // Sixteen or more base64 characters in groups of four, with the padding that closes it, not
    // part of a longer run of them: the lookbehind and lookahead are what keep it off the middle
    // of a URL or an ID.
    private static final Pattern BASE64 = Pattern.compile(
            "(?<![A-Za-z0-9+/=])(?:[A-Za-z0-9+/]{4}){4,}"
                    + "(?:[A-Za-z0-9+/]{2}==|[A-Za-z0-9+/]{3}=)?(?![A-Za-z0-9+/=])");

    // Search terms: handles, then what is left of the text once its links are gone, then the
    // pieces of that between "and", "&", "+", "x", commas, semicolons and newlines.
    private static final Pattern HANDLE = Pattern.compile("(?:^|\\s)@([\\w.]{2,})");
    private static final Pattern URL = Pattern.compile("https?://\\S+");
    private static final Pattern TERM_SEPARATOR = Pattern.compile(
            "\\s+(?:and|&|\\+|x)\\s+|[,;\\n]", Pattern.CASE_INSENSITIVE);
    private static final Pattern TRAILING_SLASHES = Pattern.compile("/+$");
    private static final Pattern LINK_PREFIX = Pattern.compile("^.*/");
    private static final Pattern LABEL_PREFIX = Pattern.compile("^.*:\\s+");
    private static final Pattern LABEL_ONLY = Pattern.compile("^.*:$");
    private static final int MAX_TERMS = 4;

    private CommentDecoder() {
        // Prevent instantiation
    }

    // A comment is bound again every time it scrolls into view, and the scan is regex work over
    // the whole body, so the results of recent bodies are kept.
    private static final int CACHE_SIZE = 256;
    private static final Map<String, String> CACHE = Collections.synchronizedMap(
            new LinkedHashMap<String, String>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
                    return size() > CACHE_SIZE;
                }
            });

    /**
     * Returns the Markdown with decoded blocks added, or the same string if nothing decoded.
     */
    public static String annotate(String markdown) {
        if (markdown == null) return null;
        String cached = CACHE.get(markdown);
        if (cached != null) return cached;
        String result = annotateUncached(markdown);
        CACHE.put(markdown, result);
        return result;
    }

    /**
     * The rules for what gets a card:
     * <ul>
     * <li>Per paragraph, binary first, and base64 only if binary rendered nothing. Several binary
     * matches in one paragraph each get a card; base64 gets at most one.
     * <li>Per comment, a decoded text that has already been shown is not shown again, whichever
     * decoder or paragraph it came from: one set of what was shown, keyed on the text alone, for the
     * whole body.
     * </ul>
     */
    private static String annotateUncached(String markdown) {
        String[] paragraphs = PARAGRAPH_BREAK.split(markdown, -1);
        Set<String> seen = new HashSet<>();
        StringBuilder out = new StringBuilder(markdown.length());
        boolean changed = false;

        for (int i = 0; i < paragraphs.length; i++) {
            String paragraph = paragraphs[i];
            if (i > 0) out.append("\n\n");
            out.append(paragraph);

            boolean rendered = false;
            Matcher binary = BINARY.matcher(paragraph);
            while (binary.find()) {
                rendered |= insertDecoded("BIN", binaryToText(binary.group()), out, seen);
            }
            if (!rendered) {
                Matcher base64 = BASE64.matcher(paragraph);
                while (base64.find()) {
                    if (insertDecoded("B64", base64ToText(base64.group()), out, seen)) {
                        rendered = true;
                        break;
                    }
                }
            }
            changed |= rendered;
        }
        return changed ? out.toString() : markdown;
    }

    static String binaryToText(String bits) {
        String clean = WHITESPACE.matcher(bits).replaceAll("");
        if (clean.length() % 8 != 0) return null;
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < clean.length(); i += 8) {
            int c = Integer.parseInt(clean.substring(i, i + 8), 2);
            if ((c < 32 && c != 9 && c != 10 && c != 13) || c > 126) return null;
            result.append((char) c);
        }
        return result.toString();
    }

    /**
     * The text a base64 string holds, or null if it holds none: the bytes must be valid UTF-8, and
     * the text free of control characters other than tab, line feed and carriage return. Without
     * those checks ordinary long words and IDs, which are made of the same characters, "decode".
     */
    static String base64ToText(String base64) {
        byte[] bytes = decodeBase64(base64);
        if (bytes == null) return null;
        String text;
        try {
            text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException ex) {
            return null;
        }
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isISOControl(c) && c != '\t' && c != '\n' && c != '\r') return null;
        }
        return text;
    }

    // Done by hand: java.util.Base64 needs API 26 and android.util.Base64 does not run in a plain
    // unit test. The input is what the BASE64 pattern matched, so it is whole groups of four.
    private static byte[] decodeBase64(String s) {
        int padding = s.endsWith("==") ? 2 : s.endsWith("=") ? 1 : 0;
        int length = s.length() - padding;
        if (s.length() % 4 != 0) return null;
        byte[] out = new byte[length * 6 / 8];
        int buffer = 0;
        int bits = 0;
        int o = 0;
        for (int i = 0; i < length; i++) {
            int value = base64Value(s.charAt(i));
            if (value < 0) return null;
            buffer = (buffer << 6) | value;
            bits += 6;
            if (bits >= 8) {
                bits -= 8;
                out[o++] = (byte) (buffer >> bits);
                buffer &= (1 << bits) - 1;
            }
        }
        return out;
    }

    private static int base64Value(char c) {
        if (c >= 'A' && c <= 'Z') return c - 'A';
        if (c >= 'a' && c <= 'z') return c - 'a' + 26;
        if (c >= '0' && c <= '9') return c - '0' + 52;
        if (c == '+') return 62;
        if (c == '/') return 63;
        return -1;
    }

    /**
     * Adds a card for {@code decoded} after what {@code after} holds so far, unless it is empty or
     * its text has already been shown in this comment. Returns whether it added one.
     *
     * @param type    the label on the card, {@code BIN} or {@code B64}
     * @param seen    the texts already shown in the comment, shared by all its paragraphs
     */
    private static boolean insertDecoded(String type, String decoded, StringBuilder after, Set<String> seen) {
        if (decoded == null) return false;
        decoded = decoded.trim();
        if (decoded.isEmpty() || !seen.add(decoded)) return false;
        after.append("\n\n").append(MARKER_PREFIX).append(type).append(':').append(encode(decoded)).append("}}");
        return true;
    }

    /**
     * Returns {label, decoded text} if the line is a marker annotate added, else null.
     */
    public static String[] parseMarker(String line) {
        if (line == null || !line.startsWith(MARKER_PREFIX)) return null;
        Matcher matcher = MARKER.matcher(line.trim());
        if (!matcher.matches()) return null;
        try {
            return new String[]{matcher.group(1), URLDecoder.decode(matcher.group(2), "UTF-8")};
        } catch (UnsupportedEncodingException | IllegalArgumentException ex) {
            return null;
        }
    }

    /**
     * What the search buttons look for, so they do not search the whole decoded text: its
     * {@code @handles} if it has any, otherwise what is left of it without its links, split
     * where it lists several names. At most four, and the whole text if nothing else is left.
     */
    public static List<String> extractSearchTerms(String text) {
        String trimmed = text.trim();
        Set<String> terms = new LinkedHashSet<>();

        Matcher handles = HANDLE.matcher(trimmed);
        while (handles.find()) {
            terms.add(handles.group(1));
        }

        if (terms.isEmpty()) {
            String rest = URL.matcher(trimmed).replaceAll("").trim();
            // A label left behind by its link ("TikTok and IG: https://...") is not a name.
            if (rest.isEmpty() || rest.equals(trimmed) || LABEL_ONLY.matcher(rest).matches()) {
                // No links to drop: take what follows the last slash and any leading label.
                rest = TRAILING_SLASHES.matcher(trimmed).replaceFirst("");
                rest = LINK_PREFIX.matcher(rest).replaceFirst("");
                rest = LABEL_PREFIX.matcher(rest).replaceFirst("").trim();
                if (rest.isEmpty()) rest = trimmed;
            }
            for (String piece : TERM_SEPARATOR.split(rest)) {
                piece = piece.trim();
                if (!piece.isEmpty()) terms.add(piece);
            }
        }

        List<String> result = new ArrayList<>(terms);
        if (result.size() > MAX_TERMS) result = new ArrayList<>(result.subList(0, MAX_TERMS));
        if (result.isEmpty()) result.add(trimmed);
        return result;
    }

    // XenForo re-runs the query from q= when the search ID in the path isn't one
    // it has stored for you, so a dummy ID works.
    public static String simpCityUrl(String query, boolean titleOnly) {
        return "https://simpcity.cr/search/1/?q=" + encode(query)
                + (titleOnly ? "&c%5Btitle_only%5D=1" : "") + "&o=relevance";
    }

    public static String socialSearchUrl(String query) {
        StringBuilder sites = new StringBuilder();
        for (String site : SOCIAL_SITES) {
            if (sites.length() > 0) sites.append(" OR ");
            sites.append("site:").append(site);
        }
        return "https://www.google.com/search?q=" + encode(query + " (" + sites + ")");
    }

    private static String encode(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (UnsupportedEncodingException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
