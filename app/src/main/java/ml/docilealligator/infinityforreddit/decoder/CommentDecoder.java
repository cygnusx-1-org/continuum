/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package ml.docilealligator.infinityforreddit.decoder;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds NATO phonetic spelling and 8-bit binary in comment and post Markdown and appends the
 * decoded text, with Copy and search links, below the paragraph it appears in.
 */
public final class CommentDecoder {
    // A private scheme so EvenBetterLinkMovementMethod can route taps to DecoderLinkHandler, which
    // copies the text instead of opening a page.
    static final String COPY_URL_PREFIX = "continuum-decoder://copy?text=";

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

    private static final Pattern PARAGRAPH_BREAK = Pattern.compile("\\n[ \\t]*\\n");
    private static final Pattern TOKEN_SEPARATOR = Pattern.compile("[\\s,+/]+");
    private static final Pattern BINARY = Pattern.compile("\\b(?:[01]{8}\\s*){2,}\\b");
    private static final Pattern LETTER = Pattern.compile("[a-zA-Z]");
    // A link's host and path, and a leading label such as "TikTok and IG: ".
    private static final Pattern LINK_PREFIX = Pattern.compile("^.*/");
    private static final Pattern LABEL_PREFIX = Pattern.compile("^.*:\\s+");

    private static final Map<String, String> NATO_ALPHABET = new HashMap<>();

    static {
        String[][] entries = {
                {"alpha", "A"}, {"alfa", "A"}, {"bravo", "B"}, {"charlie", "C"}, {"delta", "D"},
                {"echo", "E"}, {"foxtrot", "F"}, {"golf", "G"}, {"hotel", "H"}, {"india", "I"},
                {"juliet", "J"}, {"juliett", "J"}, {"kilo", "K"}, {"lima", "L"}, {"mike", "M"},
                {"november", "N"}, {"oscar", "O"}, {"papa", "P"}, {"quebec", "Q"}, {"romeo", "R"},
                {"sierra", "S"}, {"tango", "T"}, {"uniform", "U"}, {"victor", "V"},
                {"whiskey", "W"}, {"xray", "X"}, {"x-ray", "X"}, {"yankee", "Y"}, {"zulu", "Z"},
                {"zero", "0"}, {"one", "1"}, {"two", "2"}, {"three", "3"}, {"four", "4"},
                {"five", "5"}, {"six", "6"}, {"seven", "7"}, {"eight", "8"}, {"nine", "9"},
                {"space", " "}, {"dot", "."}, {"period", "."}, {"underscore", "_"},
                {"dash", "-"}, {"hyphen", "-"}, {".", "."}, {"_", "_"}, {"-", "-"},
        };
        for (String[] entry : entries) NATO_ALPHABET.put(entry[0], entry[1]);
    }

    private CommentDecoder() {
        // Prevent instantiation
    }

    /**
     * Returns the Markdown with decoded blocks added, or the same string if nothing decoded.
     */
    public static String annotate(String markdown) {
        if (markdown == null) return null;
        String[] paragraphs = PARAGRAPH_BREAK.split(markdown, -1);
        Set<String> seen = new HashSet<>();
        StringBuilder out = new StringBuilder(markdown.length());
        boolean changed = false;

        for (int i = 0; i < paragraphs.length; i++) {
            String paragraph = paragraphs[i];
            if (i > 0) out.append("\n\n");
            out.append(paragraph);

            Matcher binary = BINARY.matcher(paragraph);
            while (binary.find()) {
                changed |= appendDecoded(out, seen, "BIN", binaryToText(binary.group()));
            }
            if (LETTER.matcher(paragraph).find()) {
                changed |= appendDecoded(out, seen, "NATO", natoToText(paragraph));
            }
        }
        return changed ? out.toString() : markdown;
    }

    static String natoToText(String text) {
        StringBuilder result = new StringBuilder();
        int count = 0;
        for (String token : TOKEN_SEPARATOR.split(text.toLowerCase(Locale.ROOT))) {
            String letter = NATO_ALPHABET.get(token.trim());
            if (letter != null) {
                result.append(letter);
                count++;
            }
        }
        return count >= 3 ? result.toString() : null;
    }

    static String binaryToText(String bits) {
        String clean = bits.replaceAll("\\s", "");
        if (clean.length() % 8 != 0) return null;
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < clean.length(); i += 8) {
            int c = Integer.parseInt(clean.substring(i, i + 8), 2);
            if ((c < 32 && c != 9 && c != 10 && c != 13) || c > 126) return null;
            result.append((char) c);
        }
        return result.toString();
    }

    private static boolean appendDecoded(StringBuilder out, Set<String> seen, String label, String decoded) {
        if (decoded == null) return false;
        decoded = decoded.replaceAll("\\s+", " ").trim();
        if (decoded.isEmpty() || !seen.add(label + ":" + decoded)) return false;
        String term = searchTerm(decoded);

        out.append("\n\n> **").append(label).append("** ").append(codeSpan(decoded))
                .append("\n>\n> [📋](").append(COPY_URL_PREFIX).append(encode(decoded)).append(')')
                .append(" · [🏷️](").append(simpCityUrl(term, true)).append(')')
                .append(" · [🌐](").append(simpCityUrl(term, false)).append(')')
                .append(" · [🔍](").append(socialSearchUrl(term)).append(')');
        return true;
    }

    /**
     * Only the handle is worth searching: drops a link's host and path (https://link.me/name)
     * or a leading label ("TikTok and IG: name"). Copy still carries the full text.
     */
    static String searchTerm(String decoded) {
        String term = LINK_PREFIX.matcher(decoded).replaceFirst("");
        term = LABEL_PREFIX.matcher(term).replaceFirst("").trim();
        return term.isEmpty() ? decoded : term;
    }

    /**
     * Returns the text a Copy link carries, or null if the URL is not a Copy link.
     */
    public static String copyText(String url) {
        if (url == null || !url.startsWith(COPY_URL_PREFIX)) return null;
        try {
            return URLDecoder.decode(url.substring(COPY_URL_PREFIX.length()), "UTF-8");
        } catch (UnsupportedEncodingException | IllegalArgumentException ex) {
            return null;
        }
    }

    private static String codeSpan(String text) {
        return text.contains("`") ? "`` " + text + " ``" : "`" + text + "`";
    }

    // XenForo re-runs the query from q= when the search ID in the path isn't one
    // it has stored for you, so a dummy ID works. Brackets are encoded so they
    // can't end the Markdown link early.
    private static String simpCityUrl(String query, boolean titleOnly) {
        return "https://simpcity.cr/search/1/?q=" + encode(query)
                + (titleOnly ? "&c%5Btitle_only%5D=1" : "") + "&o=relevance";
    }

    private static String socialSearchUrl(String query) {
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
