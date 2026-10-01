package ml.docilealligator.infinityforreddit.markdown.commentface

import java.util.Locale

/**
 * Just enough of CSS to read a subreddit stylesheet the way old Reddit's browser read it for one
 * element: the empty link a comment face is written as.
 *
 * Nothing here knows about faces. [CssParser] turns the stylesheet into rules, each a list of
 * [ComplexSelector]s and the declarations they carry; [CommentFaceStylesheet] runs the cascade over
 * them. At-rules other than a matching `@media` or `@supports` are skipped, as is anything that
 * does not parse — a browser drops what it cannot read, and so does this.
 */
internal class CssRule(
    val selectors: List<ComplexSelector>,
    val declarations: List<CssDeclaration>,
    /** Source order, which breaks ties between equally specific declarations. */
    val order: Int,
) {
    /** The declarations with every shorthand the cascade cares about split into its longhands. */
    val longhands: List<CssDeclaration> by lazy { CssLonghands.expand(declarations) }
}

internal class CssDeclaration(val property: String, val value: String, val important: Boolean)

internal class AttributeSelector(
    val name: String,
    /** `=`, `~=`, `|=`, `^=`, `$=` or `*=`; null for a bare `[name]`. */
    val operator: String?,
    val value: String,
    val ignoreCase: Boolean,
) {
    fun matches(actual: String): Boolean {
        val a = if (ignoreCase) actual.lowercase(Locale.ROOT) else actual
        val v = if (ignoreCase) value.lowercase(Locale.ROOT) else value
        return when (operator) {
            null -> true
            "=" -> a == v
            "~=" -> v.isNotEmpty() && v.none { it.isWhitespace() } && a.split(WHITESPACE).contains(v)
            "|=" -> a == v || a.startsWith("$v-")
            "^=" -> v.isNotEmpty() && a.startsWith(v)
            "$=" -> v.isNotEmpty() && a.endsWith(v)
            "*=" -> v.isNotEmpty() && a.contains(v)
            else -> false
        }
    }

    private companion object {
        val WHITESPACE = Regex("\\s+")
    }
}

/** A pseudo-class; [selectors] holds the argument of the ones that take a selector list. */
internal class PseudoClass(val name: String, val selectors: List<ComplexSelector>?)

internal class CompoundSelector(
    /** Lowercased element name; null for none or `*`. */
    val type: String?,
    val ids: List<String>,
    val classes: List<String>,
    val attributes: List<AttributeSelector>,
    val pseudoClasses: List<PseudoClass>,
    val pseudoElement: String?,
) {
    val specificity: Int by lazy {
        var idCount = ids.size
        var classCount = classes.size + attributes.size
        var typeCount = (if (type != null) 1 else 0) + (if (pseudoElement != null) 1 else 0)
        for (pseudo in pseudoClasses) {
            when (pseudo.name) {
                // These count as their most specific argument; :where counts as nothing.
                "not", "is", "matches", "-webkit-any", "-moz-any" -> {
                    val inner = pseudo.selectors.orEmpty().maxOfOrNull { it.specificity } ?: 0
                    idCount += inner / ID_WEIGHT
                    classCount += (inner % ID_WEIGHT) / CLASS_WEIGHT
                    typeCount += inner % CLASS_WEIGHT
                }
                "where" -> {}
                else -> classCount++
            }
        }
        idCount * ID_WEIGHT + classCount * CLASS_WEIGHT + typeCount
    }

    companion object {
        const val ID_WEIGHT = 1_000_000
        const val CLASS_WEIGHT = 1_000
    }
}

internal class ComplexSelector(
    val compounds: List<CompoundSelector>,
    /** The combinator between `compounds[i]` and `compounds[i + 1]`: ' ', '>', '+' or '~'. */
    val combinators: List<Char>,
) {
    val subject: CompoundSelector get() = compounds.last()
    val specificity: Int by lazy { compounds.sumOf { it.specificity } }
}

internal object CssParser {

    fun parse(css: String): List<CssRule> {
        val rules = ArrayList<CssRule>()
        parseBlock(stripComments(css), rules)
        return rules
    }

    private fun parseBlock(css: String, out: MutableList<CssRule>) {
        var i = 0
        var start = 0
        val n = css.length
        while (i < n) {
            when (css[i]) {
                '"', '\'' -> {
                    i = skipString(css, i)
                    continue
                }
                '{' -> {
                    val prelude = css.substring(start, i).trim()
                    val end = matchingBrace(css, i)
                    val body = css.substring(i + 1, end)
                    if (prelude.startsWith("@")) {
                        val name = atRuleName(prelude)
                        if (name == "supports"
                                || (name == "media" && MediaQuery.matches(prelude.substring(6)))) {
                            parseBlock(body, out)
                        }
                    } else if (prelude.isNotEmpty()) {
                        val selectors = SelectorParser.parseList(prelude)
                        if (selectors.isNotEmpty()) {
                            out.add(CssRule(selectors, parseDeclarations(body), out.size))
                        }
                    }
                    i = end + 1
                    start = i
                    continue
                }
                // A statement at-rule (@import, @charset) or a stray closer ends whatever came
                // before it.
                ';', '}' -> start = i + 1
            }
            i++
        }
    }

    private fun atRuleName(prelude: String): String {
        var end = 1
        while (end < prelude.length && (prelude[end].isLetterOrDigit() || prelude[end] == '-')) {
            end++
        }
        return prelude.substring(1, end).lowercase(Locale.ROOT)
    }

    private fun parseDeclarations(body: String): List<CssDeclaration> {
        val out = ArrayList<CssDeclaration>()
        var i = 0
        var start = 0
        var depth = 0
        while (i < body.length) {
            when (body[i]) {
                '"', '\'' -> {
                    i = skipString(body, i)
                    continue
                }
                '(' -> depth++
                ')' -> if (depth > 0) depth--
                // A nested rule (CSS nesting) is not a declaration; drop it whole.
                '{' -> {
                    i = matchingBrace(body, i) + 1
                    start = i
                    continue
                }
                ';' -> if (depth == 0) {
                    addDeclaration(body.substring(start, i), out)
                    start = i + 1
                }
            }
            i++
        }
        if (start < body.length) {
            addDeclaration(body.substring(start), out)
        }
        return out
    }

    private val IMPORTANT = Regex("!\\s*important\\s*$", RegexOption.IGNORE_CASE)

    private fun addDeclaration(text: String, out: MutableList<CssDeclaration>) {
        val colon = text.indexOf(':')
        if (colon <= 0) {
            return
        }
        val property = text.substring(0, colon).trim().lowercase(Locale.ROOT)
        if (property.isEmpty() || property.startsWith("--")) {
            return
        }
        var value = text.substring(colon + 1).trim()
        val important = IMPORTANT.containsMatchIn(value)
        if (important) {
            value = value.replace(IMPORTANT, "").trim()
        }
        if (value.isNotEmpty()) {
            out.add(CssDeclaration(property, value, important))
        }
    }

    fun stripComments(css: String): String {
        val out = StringBuilder(css.length)
        var i = 0
        val n = css.length
        while (i < n) {
            val c = css[i]
            if (c == '"' || c == '\'') {
                val end = skipString(css, i)
                out.append(css, i, end)
                i = end
            } else if (c == '/' && i + 1 < n && css[i + 1] == '*') {
                val end = css.indexOf("*/", i + 2)
                i = if (end < 0) n else end + 2
                // A comment separates tokens the way whitespace does.
                out.append(' ')
            } else {
                out.append(c)
                i++
            }
        }
        return out.toString()
    }

    /** The index just past the string that opens at [start], or the end of [text] if it never closes. */
    fun skipString(text: String, start: Int): Int {
        val quote = text[start]
        var i = start + 1
        while (i < text.length) {
            val c = text[i]
            if (c == '\\') {
                i += 2
                continue
            }
            if (c == quote || c == '\n') {
                return i + 1
            }
            i++
        }
        return text.length
    }

    /** The index of the `}` that closes the `{` at [open], or the end of [text] if none does. */
    private fun matchingBrace(text: String, open: Int): Int {
        var depth = 0
        var i = open
        while (i < text.length) {
            when (text[i]) {
                '"', '\'' -> {
                    i = skipString(text, i)
                    continue
                }
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) {
                        return i
                    }
                }
            }
            i++
        }
        return text.length
    }

    /** Splits [text] on [separator] wherever it is not inside brackets, parentheses or a string. */
    fun splitTopLevel(text: String, separator: Char): List<String> {
        val parts = ArrayList<String>()
        var depth = 0
        var start = 0
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                c == '"' || c == '\'' -> {
                    i = skipString(text, i)
                    continue
                }
                c == '(' || c == '[' -> depth++
                (c == ')' || c == ']') && depth > 0 -> depth--
                c == separator && depth == 0 -> {
                    parts.add(text.substring(start, i))
                    start = i + 1
                }
            }
            i++
        }
        parts.add(text.substring(start))
        return parts
    }
}

/**
 * `@media` evaluated for the page old Reddit drew faces on: a desktop browser window, 1280 by 800
 * CSS pixels at one device pixel per CSS pixel. Anything it does not recognise does not match, so
 * a stylesheet's retina, mobile and print variants all stay out of the cascade.
 */
internal object MediaQuery {
    private const val WIDTH = 1280f
    private const val HEIGHT = 800f
    private const val RESOLUTION = 1f

    private val AND = Regex("\\s+and\\s+")

    fun matches(queryList: String): Boolean =
        CssParser.splitTopLevel(queryList, ',').any { matchesOne(it.trim().lowercase(Locale.ROOT)) }

    private fun matchesOne(query: String): Boolean {
        if (query.isEmpty()) {
            return true
        }
        var text = query
        var negate = false
        if (text.startsWith("not ")) {
            negate = true
            text = text.substring(4).trim()
        } else if (text.startsWith("only ")) {
            text = text.substring(5).trim()
        }
        val result = text.split(AND).all { part ->
            val p = part.trim()
            if (p.startsWith("(")) {
                feature(p.removePrefix("(").removeSuffix(")").trim())
            } else {
                p == "all" || p == "screen"
            }
        }
        return result != negate
    }

    private fun feature(text: String): Boolean {
        val colon = text.indexOf(':')
        if (colon < 0) {
            return text in setOf("color", "hover", "pointer", "any-hover", "any-pointer")
        }
        val name = text.substring(0, colon).trim()
        val value = text.substring(colon + 1).trim()
        return when (name) {
            "min-width", "min-device-width" -> length(value)?.let { WIDTH >= it } ?: false
            "max-width", "max-device-width" -> length(value)?.let { WIDTH <= it } ?: false
            "width", "device-width" -> length(value)?.let { WIDTH == it } ?: false
            "min-height", "min-device-height" -> length(value)?.let { HEIGHT >= it } ?: false
            "max-height", "max-device-height" -> length(value)?.let { HEIGHT <= it } ?: false
            "height", "device-height" -> length(value)?.let { HEIGHT == it } ?: false
            "orientation" -> value == "landscape"
            "-webkit-min-device-pixel-ratio", "min--moz-device-pixel-ratio",
            "-o-min-device-pixel-ratio", "min-device-pixel-ratio" ->
                ratio(value)?.let { RESOLUTION >= it } ?: false
            "-webkit-max-device-pixel-ratio", "max--moz-device-pixel-ratio",
            "-o-max-device-pixel-ratio", "max-device-pixel-ratio" ->
                ratio(value)?.let { RESOLUTION <= it } ?: false
            "min-resolution" -> resolution(value)?.let { RESOLUTION >= it } ?: false
            "max-resolution" -> resolution(value)?.let { RESOLUTION <= it } ?: false
            "resolution" -> resolution(value)?.let { RESOLUTION == it } ?: false
            "prefers-color-scheme" -> value == "light"
            "prefers-reduced-motion" -> value == "no-preference"
            "hover", "any-hover" -> value == "hover"
            "pointer", "any-pointer" -> value == "fine"
            else -> false
        }
    }

    private fun length(value: String): Float? {
        val number = NUMBER.find(value) ?: return null
        val n = number.groupValues[1].toFloatOrNull() ?: return null
        return when (number.groupValues[2]) {
            "px", "" -> n
            "em", "rem" -> n * 16f
            "pt" -> n * 4f / 3f
            else -> null
        }
    }

    private fun ratio(value: String): Float? {
        val slash = value.indexOf('/')
        if (slash < 0) {
            return value.toFloatOrNull()
        }
        val top = value.substring(0, slash).trim().toFloatOrNull() ?: return null
        val bottom = value.substring(slash + 1).trim().toFloatOrNull() ?: return null
        return if (bottom == 0f) null else top / bottom
    }

    private fun resolution(value: String): Float? {
        val number = NUMBER.find(value) ?: return null
        val n = number.groupValues[1].toFloatOrNull() ?: return null
        return when (number.groupValues[2]) {
            "dppx", "x" -> n
            "dpi" -> n / 96f
            "dpcm" -> n * 2.54f / 96f
            else -> null
        }
    }

    private val NUMBER = Regex("^(-?[0-9]*\\.?[0-9]+)([a-z]*)$")
}

/** Selectors Level 4, minus what cannot apply to an element nobody is hovering over. */
internal object SelectorParser {

    private val SELECTOR_LIST_PSEUDOS = setOf("not", "is", "matches", "-webkit-any", "-moz-any", "where")
    private val LEGACY_PSEUDO_ELEMENTS = setOf("before", "after", "first-line", "first-letter")

    /** Every selector in [text] that parses; the ones that do not are dropped. */
    fun parseList(text: String): List<ComplexSelector> =
        CssParser.splitTopLevel(text, ',').mapNotNull { parseComplex(it.trim()) }

    private fun parseComplex(text: String): ComplexSelector? {
        if (text.isEmpty()) {
            return null
        }
        val reader = Reader(text)
        val compounds = ArrayList<CompoundSelector>()
        val combinators = ArrayList<Char>()
        while (true) {
            reader.skipWhitespace()
            if (reader.atEnd()) {
                break
            }
            if (compounds.isNotEmpty()) {
                val c = reader.peek()
                if (c == '>' || c == '+' || c == '~') {
                    reader.next()
                    reader.skipWhitespace()
                    combinators.add(c)
                } else {
                    combinators.add(' ')
                }
            }
            compounds.add(parseCompound(reader) ?: return null)
        }
        if (compounds.isEmpty() || combinators.size != compounds.size - 1) {
            return null
        }
        return ComplexSelector(compounds, combinators)
    }

    private fun parseCompound(reader: Reader): CompoundSelector? {
        var type: String? = null
        var any = false
        val ids = ArrayList<String>()
        val classes = ArrayList<String>()
        val attributes = ArrayList<AttributeSelector>()
        val pseudoClasses = ArrayList<PseudoClass>()
        var pseudoElement: String? = null

        if (reader.peek() == '*') {
            reader.next()
            any = true
        } else if (reader.isIdentStart()) {
            type = reader.readIdent()?.lowercase(Locale.ROOT) ?: return null
            any = true
        }
        // A namespace prefix (`svg|a`, `*|a`) names the same element for our purposes.
        if (reader.peek() == '|') {
            reader.next()
            type = if (reader.peek() == '*') {
                reader.next()
                null
            } else {
                reader.readIdent()?.lowercase(Locale.ROOT) ?: return null
            }
        }

        loop@ while (!reader.atEnd()) {
            when (reader.peek()) {
                '.' -> {
                    reader.next()
                    classes.add(reader.readIdent() ?: return null)
                }
                '#' -> {
                    reader.next()
                    ids.add(reader.readIdent() ?: return null)
                }
                '[' -> {
                    reader.next()
                    attributes.add(parseAttribute(reader) ?: return null)
                }
                ':' -> {
                    reader.next()
                    if (reader.peek() == ':') {
                        reader.next()
                        pseudoElement = reader.readIdent()?.lowercase(Locale.ROOT) ?: return null
                        if (reader.peek() == '(') {
                            reader.readParenthesised() ?: return null
                        }
                    } else {
                        val name = reader.readIdent()?.lowercase(Locale.ROOT) ?: return null
                        if (reader.peek() == '(') {
                            val argument = reader.readParenthesised() ?: return null
                            pseudoClasses.add(PseudoClass(name,
                                    if (name in SELECTOR_LIST_PSEUDOS) parseList(argument) else null))
                        } else if (name in LEGACY_PSEUDO_ELEMENTS) {
                            pseudoElement = name
                        } else {
                            pseudoClasses.add(PseudoClass(name, null))
                        }
                    }
                }
                else -> break@loop
            }
            any = true
        }
        if (!any) {
            return null
        }
        // Anything but a combinator or the end after a compound is a syntax error.
        val c = reader.peek()
        if (c != null && !c.isWhitespace() && c != '>' && c != '+' && c != '~') {
            return null
        }
        return CompoundSelector(type, ids, classes, attributes, pseudoClasses, pseudoElement)
    }

    private fun parseAttribute(reader: Reader): AttributeSelector? {
        reader.skipWhitespace()
        var name = reader.readIdent()?.lowercase(Locale.ROOT) ?: return null
        if (reader.peek() == '|' && reader.peekAt(1) != '=') {
            reader.next()
            name = reader.readIdent()?.lowercase(Locale.ROOT) ?: return null
        }
        reader.skipWhitespace()
        if (reader.peek() == ']') {
            reader.next()
            return AttributeSelector(name, null, "", false)
        }
        val operator = when (reader.peek()) {
            '=' -> "="
            '~', '|', '^', '$', '*' -> if (reader.peekAt(1) == '=') "${reader.peek()}=" else return null
            else -> return null
        }
        repeat(operator.length) { reader.next() }
        reader.skipWhitespace()
        val value = if (reader.peek() == '"' || reader.peek() == '\'') {
            reader.readString() ?: return null
        } else {
            reader.readIdent() ?: return null
        }
        reader.skipWhitespace()
        var ignoreCase = false
        val flag = reader.peek()
        if ((flag == 'i' || flag == 'I' || flag == 's' || flag == 'S')
                && (reader.peekAt(1) == ']' || reader.peekAt(1)?.isWhitespace() == true)) {
            ignoreCase = flag == 'i' || flag == 'I'
            reader.next()
            reader.skipWhitespace()
        }
        if (reader.peek() != ']') {
            return null
        }
        reader.next()
        return AttributeSelector(name, operator, value, ignoreCase)
    }

    private class Reader(private val text: String) {
        private var index = 0

        fun atEnd() = index >= text.length
        fun peek(): Char? = text.getOrNull(index)
        fun peekAt(offset: Int): Char? = text.getOrNull(index + offset)
        fun next() {
            index++
        }

        fun skipWhitespace() {
            while (index < text.length && text[index].isWhitespace()) {
                index++
            }
        }

        fun isIdentStart(): Boolean {
            val c = peek() ?: return false
            if (c == '-') {
                val next = peekAt(1) ?: return false
                return next.isLetter() || next == '_' || next == '-' || next == '\\' || next.code >= 0x80
            }
            return c.isLetter() || c == '_' || c == '\\' || c.code >= 0x80
        }

        /** An identifier with its escapes resolved, or null if one does not start here. */
        fun readIdent(): String? {
            if (!isIdentStart()) {
                return null
            }
            val out = StringBuilder()
            while (index < text.length) {
                val c = text[index]
                if (c == '\\') {
                    index++
                    readEscape(out)
                } else if (c.isLetterOrDigit() || c == '-' || c == '_' || c.code >= 0x80) {
                    out.append(c)
                    index++
                } else {
                    break
                }
            }
            return out.toString()
        }

        fun readString(): String? {
            val quote = text[index]
            index++
            val out = StringBuilder()
            while (index < text.length) {
                val c = text[index]
                when {
                    c == quote -> {
                        index++
                        return out.toString()
                    }
                    c == '\\' -> {
                        index++
                        // An escaped newline continues the string and contributes nothing.
                        if (index < text.length && text[index] == '\n') {
                            index++
                        } else {
                            readEscape(out)
                        }
                    }
                    c == '\n' -> return null
                    else -> {
                        out.append(c)
                        index++
                    }
                }
            }
            return null
        }

        /** Reads what follows a backslash: up to six hex digits and one space, or one literal. */
        private fun readEscape(out: StringBuilder) {
            if (index >= text.length) {
                return
            }
            var end = index
            while (end < text.length && end - index < 6 && text[end].isHexDigit()) {
                end++
            }
            if (end > index) {
                val codePoint = text.substring(index, end).toInt(16)
                out.appendCodePoint(if (codePoint == 0 || codePoint > 0x10FFFF) 0xFFFD else codePoint)
                index = end
                if (index < text.length && text[index].isWhitespace()) {
                    index++
                }
            } else {
                out.append(text[index])
                index++
            }
        }

        /** The text between a `(` here and its matching `)`, or null if it never closes. */
        fun readParenthesised(): String? {
            val open = index
            var depth = 0
            while (index < text.length) {
                when (text[index]) {
                    '"', '\'' -> {
                        index = CssParser.skipString(text, index)
                        continue
                    }
                    '(' -> depth++
                    ')' -> {
                        depth--
                        if (depth == 0) {
                            index++
                            return text.substring(open + 1, index - 1)
                        }
                    }
                }
                index++
            }
            return null
        }

        private fun Char.isHexDigit() = this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'
    }
}
