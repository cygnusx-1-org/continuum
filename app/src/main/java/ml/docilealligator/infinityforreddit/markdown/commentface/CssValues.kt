package ml.docilealligator.infinityforreddit.markdown.commentface

import java.util.Locale

/**
 * A length that may be a share of something not known until the face is drawn: `px` plus
 * `fraction` of the free space. `10px` is (10, 0), `50%` of a background position is (0, 0.5), and
 * `right 10px` is (-10, 1).
 */
data class CssLength(val px: Float, val fraction: Float) {
    fun resolve(available: Float): Float = px + fraction * available

    companion object {
        @JvmField
        val ZERO = CssLength(0f, 0f)
    }
}

/** Splits the shorthands the cascade reads into the longhands it resolves one at a time. */
internal object CssLonghands {

    private val GLOBAL_KEYWORDS = setOf("inherit", "initial", "unset", "revert", "revert-layer")
    private val BACKGROUND_LONGHANDS = listOf(
        "background-image", "background-position-x", "background-position-y",
        "background-size", "background-repeat", "background-color"
    )
    private val REPEAT_KEYWORDS = setOf("repeat", "repeat-x", "repeat-y", "no-repeat", "space", "round")
    private val IGNORED_BACKGROUND_KEYWORDS = setOf(
        "scroll", "fixed", "local", "border-box", "padding-box", "content-box", "text"
    )
    private val SIZE_KEYWORDS = setOf("auto", "cover", "contain")
    private val FONT_SIZE_KEYWORDS = setOf(
        "xx-small", "x-small", "small", "medium", "large", "x-large", "xx-large", "xxx-large",
        "smaller", "larger"
    )
    private val FONT_WEIGHT_KEYWORDS = setOf("normal", "bold", "bolder", "lighter")

    fun expand(declarations: List<CssDeclaration>): List<CssDeclaration> {
        val out = ArrayList<CssDeclaration>(declarations.size)
        for (declaration in declarations) {
            when (declaration.property) {
                "background" -> expandBackground(declaration, out)
                "background-position" -> expandPosition(declaration, out)
                "padding" -> expandBox(declaration, "padding", out)
                "font" -> expandFont(declaration, out)
                else -> out.add(declaration)
            }
        }
        return out
    }

    private fun expandBackground(declaration: CssDeclaration, out: MutableList<CssDeclaration>) {
        val value = declaration.value
        if (value.lowercase(Locale.ROOT) in GLOBAL_KEYWORDS) {
            BACKGROUND_LONGHANDS.forEach { out.add(CssDeclaration(it, value, declaration.important)) }
            return
        }
        // The first layer is the one on top, and only the last may carry the colour.
        val layers = CssParser.splitTopLevel(value, ',')
        val top = parseLayer(layers.first()) ?: return
        val color = if (layers.size > 1) parseLayer(layers.last())?.color ?: "transparent" else top.color
        val important = declaration.important
        out.add(CssDeclaration("background-image", top.image, important))
        out.add(CssDeclaration("background-position-x", top.positionX, important))
        out.add(CssDeclaration("background-position-y", top.positionY, important))
        out.add(CssDeclaration("background-size", top.size, important))
        out.add(CssDeclaration("background-repeat", top.repeat, important))
        out.add(CssDeclaration("background-color", color, important))
    }

    private class Layer(
        val image: String,
        val positionX: String,
        val positionY: String,
        val size: String,
        val repeat: String,
        val color: String,
    )

    /** One comma-separated layer of a `background`, or null if a browser would reject it. */
    private fun parseLayer(text: String): Layer? {
        var image = "none"
        val position = ArrayList<String>()
        val size = ArrayList<String>()
        val repeat = ArrayList<String>()
        var color = "transparent"
        var afterSlash = false
        for (token in CssValues.tokens(text)) {
            val lower = token.lowercase(Locale.ROOT)
            when {
                token == "/" -> {
                    if (position.isEmpty()) {
                        return null
                    }
                    afterSlash = true
                }
                lower == "none" || CssValues.isImageFunction(lower) -> image = token
                lower in REPEAT_KEYWORDS -> repeat.add(lower)
                lower in IGNORED_BACKGROUND_KEYWORDS -> {}
                afterSlash && (lower in SIZE_KEYWORDS || CssValues.isLengthOrPercent(lower)) -> size.add(lower)
                CssValues.isPositionToken(lower) -> position.add(lower)
                else -> color = token
            }
        }
        val (x, y) = if (position.isEmpty()) "0%" to "0%" else splitPosition(position) ?: return null
        return Layer(
            image, x, y,
            if (size.isEmpty()) "auto" else size.joinToString(" "),
            if (repeat.isEmpty()) "repeat" else repeat.joinToString(" "),
            color
        )
    }

    private fun expandPosition(declaration: CssDeclaration, out: MutableList<CssDeclaration>) {
        val value = declaration.value
        val (x, y) = if (value.lowercase(Locale.ROOT) in GLOBAL_KEYWORDS) {
            value to value
        } else {
            // Only the first layer of a multi-layer position is drawn.
            val first = CssParser.splitTopLevel(value, ',').first()
            splitPosition(CssValues.tokens(first).map { it.lowercase(Locale.ROOT) }) ?: return
        }
        out.add(CssDeclaration("background-position-x", x, declaration.important))
        out.add(CssDeclaration("background-position-y", y, declaration.important))
    }

    /**
     * Splits one to four position tokens into the horizontal and vertical components, each still
     * a string `[edge] [offset]` for [CssValues.positionComponent] to read.
     */
    fun splitPosition(tokens: List<String>): Pair<String, String>? {
        val vertical = setOf("top", "bottom")
        val horizontal = setOf("left", "right")
        fun ordered(first: String, second: String): Pair<String, String> {
            val firstEdge = first.substringBefore(' ')
            val secondEdge = second.substringBefore(' ')
            return if (firstEdge in vertical || secondEdge in horizontal) second to first else first to second
        }
        return when (tokens.size) {
            1 -> {
                val t = tokens[0]
                if (t in vertical) "center" to t else t to "center"
            }
            2 -> ordered(tokens[0], tokens[1])
            3 -> if (CssValues.isLengthOrPercent(tokens[1])) {
                ordered("${tokens[0]} ${tokens[1]}", tokens[2])
            } else {
                ordered(tokens[0], "${tokens[1]} ${tokens[2]}")
            }
            4 -> ordered("${tokens[0]} ${tokens[1]}", "${tokens[2]} ${tokens[3]}")
            else -> null
        }
    }

    private fun expandBox(declaration: CssDeclaration, prefix: String, out: MutableList<CssDeclaration>) {
        val tokens = CssValues.tokens(declaration.value)
        val sides = when (tokens.size) {
            1 -> listOf(tokens[0], tokens[0], tokens[0], tokens[0])
            2 -> listOf(tokens[0], tokens[1], tokens[0], tokens[1])
            3 -> listOf(tokens[0], tokens[1], tokens[2], tokens[1])
            4 -> tokens
            else -> return
        }
        listOf("top", "right", "bottom", "left").forEachIndexed { index, side ->
            out.add(CssDeclaration("$prefix-$side", sides[index], declaration.important))
        }
    }

    /** Only the size and weight of `font`; `font: 0/0 a` is a common way of hiding a link's text. */
    private fun expandFont(declaration: CssDeclaration, out: MutableList<CssDeclaration>) {
        val value = declaration.value
        if (value.lowercase(Locale.ROOT) in GLOBAL_KEYWORDS) {
            out.add(CssDeclaration("font-size", value, declaration.important))
            out.add(CssDeclaration("font-weight", value, declaration.important))
            return
        }
        val tokens = CssValues.tokens(value).map { it.lowercase(Locale.ROOT) }
        val sizeIndex = tokens.indexOfFirst { it in FONT_SIZE_KEYWORDS || CssValues.isLengthOrPercent(it) }
        if (sizeIndex < 0) {
            return
        }
        val weight = tokens.subList(0, sizeIndex).lastOrNull {
            it in FONT_WEIGHT_KEYWORDS || it.toIntOrNull() != null
        } ?: "normal"
        out.add(CssDeclaration("font-size", tokens[sizeIndex], declaration.important))
        out.add(CssDeclaration("font-weight", weight, declaration.important))
    }
}

/** Reads the values the face builder needs out of the strings the cascade settled on. */
internal object CssValues {

    private val LENGTH = Regex("^([+-]?(?:[0-9]+\\.?[0-9]*|\\.[0-9]+)(?:e[+-]?[0-9]+)?)([a-z%]*)$")
    private val POSITION_KEYWORDS = setOf("left", "right", "top", "bottom", "center")

    /** Splits on whitespace outside parentheses, with each `/` a token of its own. */
    fun tokens(value: String): List<String> {
        val out = ArrayList<String>()
        val current = StringBuilder()
        var depth = 0
        var i = 0
        fun flush() {
            if (current.isNotEmpty()) {
                out.add(current.toString())
                current.setLength(0)
            }
        }
        while (i < value.length) {
            val c = value[i]
            when {
                c == '"' || c == '\'' -> {
                    val end = CssParser.skipString(value, i)
                    current.append(value, i, end)
                    i = end
                    continue
                }
                c == '(' -> {
                    depth++
                    current.append(c)
                }
                c == ')' -> {
                    if (depth > 0) depth--
                    current.append(c)
                }
                depth == 0 && c.isWhitespace() -> flush()
                depth == 0 && c == '/' -> {
                    flush()
                    out.add("/")
                }
                else -> current.append(c)
            }
            i++
        }
        flush()
        return out
    }

    fun isImageFunction(lower: String): Boolean =
        lower.startsWith("url(") || lower.contains("gradient(") || lower.contains("image-set(")
                || lower.startsWith("cross-fade(") || lower.startsWith("element(")

    fun isLengthOrPercent(token: String): Boolean {
        val match = LENGTH.find(token) ?: return false
        val unit = match.groupValues[2]
        return when (unit) {
            "" -> match.groupValues[1].toFloatOrNull() == 0f
            "%", "px", "pt", "pc", "em", "rem", "ex", "ch", "vw", "vh", "vmin", "vmax", "cm", "mm", "in", "q" -> true
            else -> false
        }
    }

    fun isPositionToken(lower: String) = lower in POSITION_KEYWORDS || isLengthOrPercent(lower)

    /**
     * An absolute length in CSS pixels, or null for a percentage, `auto`, or anything unreadable.
     * A unitless number is only a length when it is zero, as in a standards-mode page.
     */
    fun length(token: String?, fontSize: Float): Float? {
        val lower = token?.trim()?.lowercase(Locale.ROOT) ?: return null
        val match = LENGTH.find(lower) ?: return null
        val n = match.groupValues[1].toFloatOrNull() ?: return null
        return when (match.groupValues[2]) {
            "" -> if (n == 0f) 0f else null
            "px" -> n
            "pt" -> n * 4f / 3f
            "pc" -> n * 16f
            "em" -> n * fontSize
            "ex", "ch" -> n * fontSize / 2f
            "rem" -> n * 16f
            "in" -> n * 96f
            "cm" -> n * 96f / 2.54f
            "mm" -> n * 96f / 25.4f
            "q" -> n * 96f / 101.6f
            else -> null
        }
    }

    fun lengthOrPercent(token: String?, fontSize: Float): CssLength? {
        val lower = token?.trim()?.lowercase(Locale.ROOT) ?: return null
        if (lower.endsWith("%")) {
            val n = lower.dropLast(1).toFloatOrNull() ?: return null
            return CssLength(0f, n / 100f)
        }
        return length(lower, fontSize)?.let { CssLength(it, 0f) }
    }

    /** One component of a background position: `[edge] [offset]`, a length, or a keyword. */
    fun positionComponent(value: String?, fontSize: Float): CssLength? {
        val tokens = tokens(value ?: return null).map { it.lowercase(Locale.ROOT) }
        return when (tokens.size) {
            1 -> when (val t = tokens[0]) {
                "left", "top" -> CssLength(0f, 0f)
                "center" -> CssLength(0f, 0.5f)
                "right", "bottom" -> CssLength(0f, 1f)
                else -> lengthOrPercent(t, fontSize)
            }
            2 -> {
                val offset = lengthOrPercent(tokens[1], fontSize) ?: return null
                when (tokens[0]) {
                    "left", "top" -> offset
                    "right", "bottom" -> CssLength(-offset.px, 1f - offset.fraction)
                    else -> null
                }
            }
            else -> null
        }
    }

    /** A CSS colour as ARGB, 0 for `transparent`, or null where it is unknown or `currentcolor`. */
    fun color(value: String?): Int? {
        val lower = value?.trim()?.lowercase(Locale.ROOT) ?: return null
        if (lower.startsWith("#")) {
            return hexColor(lower.substring(1))
        }
        if (lower.startsWith("rgb(") || lower.startsWith("rgba(")) {
            return rgbColor(lower.substringAfter('(').substringBeforeLast(')'))
        }
        return NAMED_COLORS[lower]
    }

    /** The first colour in a `text-shadow`; null for none, and `currentcolor` for one with no colour. */
    fun shadowColor(value: String?): ShadowColor? {
        val lower = value?.trim()?.lowercase(Locale.ROOT) ?: return null
        if (lower == "none" || lower.isEmpty()) {
            return null
        }
        val firstShadow = CssParser.splitTopLevel(lower, ',').first()
        for (token in tokens(firstShadow)) {
            if (isLengthOrPercent(token)) {
                continue
            }
            return ShadowColor(color(token))
        }
        return ShadowColor(null)
    }

    /** A shadow's colour; a null [argb] means the text's own colour. */
    class ShadowColor(val argb: Int?)

    private fun hexColor(hex: String): Int? {
        if (hex.any { Character.digit(it, 16) < 0 }) {
            return null
        }
        fun nibble(i: Int) = Character.digit(hex[i], 16) * 17
        fun byte(i: Int) = hex.substring(i, i + 2).toInt(16)
        return when (hex.length) {
            3 -> argb(255, nibble(0), nibble(1), nibble(2))
            4 -> argb(nibble(3), nibble(0), nibble(1), nibble(2))
            6 -> argb(255, byte(0), byte(2), byte(4))
            8 -> argb(byte(6), byte(0), byte(2), byte(4))
            else -> null
        }
    }

    private fun rgbColor(arguments: String): Int? {
        val parts = arguments.replace("/", " ").replace(",", " ").trim().split(Regex("\\s+"))
        if (parts.size != 3 && parts.size != 4) {
            return null
        }
        fun channel(text: String): Int? = if (text.endsWith("%")) {
            text.dropLast(1).toFloatOrNull()?.let { (it * 2.55f).toInt() }
        } else {
            text.toFloatOrNull()?.toInt()
        }?.coerceIn(0, 255)
        val r = channel(parts[0]) ?: return null
        val g = channel(parts[1]) ?: return null
        val b = channel(parts[2]) ?: return null
        val a = if (parts.size == 4) {
            val alpha = parts[3]
            val fraction = if (alpha.endsWith("%")) {
                alpha.dropLast(1).toFloatOrNull()?.div(100f)
            } else {
                alpha.toFloatOrNull()
            } ?: return null
            (fraction.coerceIn(0f, 1f) * 255f + 0.5f).toInt()
        } else {
            255
        }
        return argb(a, r, g, b)
    }

    private fun argb(a: Int, r: Int, g: Int, b: Int): Int = (a shl 24) or (r shl 16) or (g shl 8) or b

    private val NAMED_COLORS: Map<String, Int> = mapOf(
        "transparent" to 0,
        "black" to 0xFF000000.toInt(), "white" to 0xFFFFFFFF.toInt(),
        "red" to 0xFFFF0000.toInt(), "green" to 0xFF008000.toInt(), "blue" to 0xFF0000FF.toInt(),
        "yellow" to 0xFFFFFF00.toInt(), "orange" to 0xFFFFA500.toInt(),
        "purple" to 0xFF800080.toInt(), "pink" to 0xFFFFC0CB.toInt(),
        "gray" to 0xFF808080.toInt(), "grey" to 0xFF808080.toInt(),
        "darkgray" to 0xFFA9A9A9.toInt(), "darkgrey" to 0xFFA9A9A9.toInt(),
        "lightgray" to 0xFFD3D3D3.toInt(), "lightgrey" to 0xFFD3D3D3.toInt(),
        "silver" to 0xFFC0C0C0.toInt(), "maroon" to 0xFF800000.toInt(),
        "navy" to 0xFF000080.toInt(), "teal" to 0xFF008080.toInt(),
        "olive" to 0xFF808000.toInt(), "lime" to 0xFF00FF00.toInt(),
        "aqua" to 0xFF00FFFF.toInt(), "cyan" to 0xFF00FFFF.toInt(),
        "fuchsia" to 0xFFFF00FF.toInt(), "magenta" to 0xFFFF00FF.toInt(),
        "brown" to 0xFFA52A2A.toInt(), "gold" to 0xFFFFD700.toInt(),
        "crimson" to 0xFFDC143C.toInt(), "darkred" to 0xFF8B0000.toInt(),
        "darkblue" to 0xFF00008B.toInt(), "darkgreen" to 0xFF006400.toInt(),
        "deepskyblue" to 0xFF00BFFF.toInt(), "skyblue" to 0xFF87CEEB.toInt(),
        "steelblue" to 0xFF4682B4.toInt(), "royalblue" to 0xFF4169E1.toInt(),
        "indigo" to 0xFF4B0082.toInt(), "violet" to 0xFFEE82EE.toInt(),
        "hotpink" to 0xFFFF69B4.toInt(), "tomato" to 0xFFFF6347.toInt(),
        "coral" to 0xFFFF7F50.toInt(), "salmon" to 0xFFFA8072.toInt(),
        "khaki" to 0xFFF0E68C.toInt(), "beige" to 0xFFF5F5DC.toInt(),
        "ivory" to 0xFFFFFFF0.toInt(), "lavender" to 0xFFE6E6FA.toInt(),
        "turquoise" to 0xFF40E0D0.toInt(), "tan" to 0xFFD2B48C.toInt(),
        "chocolate" to 0xFFD2691E.toInt(), "firebrick" to 0xFFB22222.toInt(),
        "slategray" to 0xFF708090.toInt(), "slategrey" to 0xFF708090.toInt(),
        "whitesmoke" to 0xFFF5F5F5.toInt(), "gainsboro" to 0xFFDCDCDC.toInt(),
    )
}
