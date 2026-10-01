package ml.docilealligator.infinityforreddit.markdown.commentface

import java.util.EnumSet
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Where on old Reddit a piece of markdown would have been rendered. A stylesheet can scope a rule
 * to one of these (`.comment a[href^="#country"] { display: none }` hides r/soccer's flags in
 * comments but not in posts), so a face is resolved against the elements that would have enclosed
 * its link there.
 */
enum class CommentFaceContext(internal val ancestors: List<VirtualElement>) {
    COMMENT(listOf(
        element("html"), element("body", "comments-page", "single-page"), element("div", "content"),
        element("div", "commentarea"), element("div", "sitetable", "nestedlisting"),
        element("div", "thing", "comment", "noncollapsed"), element("div", "child"),
        element("div", "entry", "unvoted"), element("form", "usertext", "warn-on-unload"),
        element("div", "usertext-body", "may-blank-within", "md-container"), element("div", "md"),
        element("p")
    )),
    POST(listOf(
        element("html"), element("body", "comments-page", "single-page"), element("div", "content"),
        element("div", "sitetable", "linklisting"), element("div", "thing", "link", "self"),
        element("div", "entry", "unvoted"), element("div", "expando"),
        element("form", "usertext", "warn-on-unload"),
        element("div", "usertext-body", "may-blank-within", "md-container"), element("div", "md"),
        element("p")
    )),
    SIDEBAR(listOf(
        element("html"), element("body", "listing-page"), element("div", "side"),
        element("div", "spacer"), element("div", "titlebox"),
        element("form", "usertext", "warn-on-unload"),
        element("div", "usertext-body", "may-blank-within", "md-container"), element("div", "md"),
        element("p")
    )),
    WIKI(listOf(
        element("html"), element("body", "wiki-page"), element("div", "content"),
        element("div", "wiki-page-content", "md-container"), element("div", "md", "wiki"),
        element("p")
    )),

    /** Markdown with no fixed place on the old site, such as a subreddit's rules. */
    GENERIC(listOf(
        element("html"), element("body"), element("div", "content"), element("div", "md"),
        element("p")
    )),
}

internal class VirtualElement(val tag: String, val classes: Set<String>)

private fun element(tag: String, vararg classes: String) = VirtualElement(tag, classes.toSet())

/**
 * One subreddit's stylesheet, reduced to the rules that can reach a link in its markdown, and the
 * cascade that turns a link into a [CommentFace].
 *
 * The link is modelled as old Reddit rendered it: `<a href="…" title="…">` with no class or id,
 * inside the elements its [CommentFaceContext] lists. Every rule that can match it is applied in
 * cascade order — `!important`, then specificity, then source order — so shared base rules,
 * per-face overrides and later `background-position` tweaks combine the way a browser combined
 * them. Results are memoised per link.
 */
class CommentFaceStylesheet private constructor(
    private val linkEntries: List<Entry>,
    private val strongEntries: List<Entry>,
    private val images: Map<String, String>,
) {
    internal class Entry(val selector: ComplexSelector, val rule: CssRule, val contexts: Set<CommentFaceContext>)

    private class Winner(
        val value: String,
        val important: Boolean,
        val specificity: Int,
        val order: Int,
        val index: Int,
    ) {
        fun losesTo(important: Boolean, specificity: Int, order: Int, index: Int): Boolean = when {
            this.important != important -> important
            this.specificity != specificity -> specificity > this.specificity
            this.order != order -> order > this.order
            else -> index > this.index
        }
    }

    private val resolved = HashMap<String, CommentFace?>()

    /** True when nothing in the stylesheet can paint a face. */
    val isEmpty: Boolean get() = linkEntries.isEmpty()

    /**
     * The face a link to [href] with [title] becomes in [context], [CommentFace.HIDDEN] if the
     * stylesheet hides it, or null if it is an ordinary link.
     */
    fun resolve(href: String, title: String?, context: CommentFaceContext): CommentFace? {
        if (isEmpty) {
            return null
        }
        val key = "${context.ordinal}\u0000$href\u0000${title ?: "\u0001"}"
        synchronized(resolved) {
            if (resolved.containsKey(key)) {
                return resolved[key]
            }
        }
        val face = compute(href, title, context)
        synchronized(resolved) {
            resolved[key] = face
        }
        return face
    }

    private fun compute(href: String, title: String?, context: CommentFaceContext): CommentFace? {
        val element = cascade(linkEntries.filter {
            context in it.contexts && SelectorMatcher.matchesLink(it.selector.subject, href, title)
        })
        if (element.isEmpty()) {
            return null
        }
        val strong = cascade(strongEntries.filter {
            val compounds = it.selector.compounds
            context in it.contexts && SelectorMatcher.matchesLink(compounds[compounds.size - 2], href, title)
        })
        return CommentFaceBuilder.build(element, strong, images)
    }

    private fun cascade(entries: List<Entry>): Map<String, String> {
        val winners = HashMap<String, Winner>()
        for (entry in entries) {
            val specificity = entry.selector.specificity
            val order = entry.rule.order
            entry.rule.longhands.forEachIndexed { index, declaration ->
                val current = winners[declaration.property]
                if (current == null || current.losesTo(declaration.important, specificity, order, index)) {
                    winners[declaration.property] =
                            Winner(declaration.value, declaration.important, specificity, order, index)
                }
            }
        }
        return winners.mapValues { it.value.value }
    }

    companion object {
        /** A stylesheet with no faces in it; also what a subreddit with no stylesheet gets. */
        @JvmField
        val EMPTY = CommentFaceStylesheet(emptyList(), emptyList(), emptyMap())

        /**
         * Parses [css] as served by `/r/{subreddit}/about/stylesheet.json`, whose `url(%%name%%)`
         * placeholders [images] maps to the uploaded image URLs.
         */
        @JvmStatic
        fun parse(css: String, images: Map<String, String>): CommentFaceStylesheet {
            val link = ArrayList<Entry>()
            val strong = ArrayList<Entry>()
            for (rule in CssParser.parse(css)) {
                for (selector in rule.selectors) {
                    // Sibling combinators depend on markup around the link that nothing here knows.
                    if (selector.combinators.any { it != ' ' && it != '>' }) {
                        continue
                    }
                    val compounds = selector.compounds
                    val n = compounds.size
                    if (SelectorMatcher.couldBeLink(selector.subject)) {
                        val contexts = SelectorMatcher.contextsFor(compounds.subList(0, n - 1))
                        if (contexts.isNotEmpty()) {
                            link.add(Entry(selector, rule, contexts))
                        }
                    } else if (n >= 2 && SelectorMatcher.isStrong(selector.subject)
                            && SelectorMatcher.couldBeLink(compounds[n - 2])) {
                        val contexts = SelectorMatcher.contextsFor(compounds.subList(0, n - 2))
                        if (contexts.isNotEmpty()) {
                            strong.add(Entry(selector, rule, contexts))
                        }
                    }
                }
            }
            if (link.none { entry -> paintsImage(entry.rule) }) {
                return EMPTY
            }
            return CommentFaceStylesheet(link, strong, images)
        }

        private fun paintsImage(rule: CssRule): Boolean = rule.declarations.any {
            (it.property == "background" || it.property == "background-image")
                    && it.value.contains("url(", ignoreCase = true)
        }
    }
}

/** Selector matching against the modelled link and the elements around it. */
internal object SelectorMatcher {

    private val SELECTOR_LIST_PSEUDOS = setOf("is", "matches", "-webkit-any", "-moz-any", "where")

    /** Whether [compound] could ever select a bare `<a href title>`; pseudo-classes are checked later. */
    fun couldBeLink(compound: CompoundSelector): Boolean =
        compound.pseudoElement == null
                && (compound.type == null || compound.type == "a")
                && compound.ids.isEmpty()
                && compound.classes.isEmpty()
                && compound.attributes.all { it.name == "href" || it.name == "title" }

    fun isStrong(compound: CompoundSelector): Boolean =
        compound.pseudoElement == null && compound.type == "strong" && compound.ids.isEmpty()
                && compound.classes.isEmpty() && compound.attributes.isEmpty()
                && compound.pseudoClasses.isEmpty()

    fun matchesLink(compound: CompoundSelector, href: String, title: String?): Boolean {
        if (!couldBeLink(compound)) {
            return false
        }
        for (attribute in compound.attributes) {
            val actual = (if (attribute.name == "href") href else title) ?: return false
            if (!attribute.matches(actual)) {
                return false
            }
        }
        return compound.pseudoClasses.all { pseudo ->
            when (pseudo.name) {
                // A selector inside :not() that is more than one compound cannot be checked
                // against a single element; treating it as not matching keeps the :not() true.
                "not" -> pseudo.selectors.orEmpty().none {
                    it.compounds.size == 1 && matchesLink(it.subject, href, title)
                }
                in SELECTOR_LIST_PSEUDOS -> pseudo.selectors.orEmpty().any {
                    it.compounds.size == 1 && matchesLink(it.subject, href, title)
                }
                "link", "any-link" -> true
                // :hover, :visited, :first-child and the rest describe state or position that is
                // not there to see.
                else -> false
            }
        }
    }

    private fun matchesElement(compound: CompoundSelector, element: VirtualElement): Boolean {
        if (compound.pseudoElement != null || compound.ids.isNotEmpty() || compound.attributes.isNotEmpty()) {
            return false
        }
        if (compound.type != null && compound.type != element.tag) {
            return false
        }
        if (!element.classes.containsAll(compound.classes)) {
            return false
        }
        return compound.pseudoClasses.all { pseudo ->
            when (pseudo.name) {
                "not" -> pseudo.selectors.orEmpty().none {
                    it.compounds.size == 1 && matchesElement(it.subject, element)
                }
                in SELECTOR_LIST_PSEUDOS -> pseudo.selectors.orEmpty().any {
                    it.compounds.size == 1 && matchesElement(it.subject, element)
                }
                else -> false
            }
        }
    }

    /**
     * The contexts in which every one of [ancestors] matches some element enclosing the link.
     * Order is not checked: the enclosing chains are short and fixed, and a stylesheet that relies
     * on `.md .content` being wrong is not one worth modelling.
     */
    fun contextsFor(ancestors: List<CompoundSelector>): Set<CommentFaceContext> {
        val contexts = EnumSet.noneOf(CommentFaceContext::class.java)
        for (context in CommentFaceContext.values()) {
            if (ancestors.all { compound -> context.ancestors.any { matchesElement(compound, it) } }) {
                contexts.add(context)
            }
        }
        return contexts
    }
}

/** Reads the cascaded longhands into a [CommentFace]. */
internal object CommentFaceBuilder {

    /** Old Reddit's comment text size, which a face's `em` lengths and caption inherit. */
    private const val DEFAULT_FONT_SIZE = 14f

    /** Wider or taller than this is a whole sprite sheet shown by mistake, not a face. */
    private const val MAX_BOX = 1000f

    private val GLOBAL_KEYWORDS = setOf("inherit", "initial", "unset", "revert", "revert-layer")
    private val SIZED_DISPLAYS = setOf(
        "block", "inline-block", "flex", "inline-flex", "grid", "inline-grid", "table",
        "inline-table", "list-item", "flow-root"
    )

    fun build(element: Map<String, String>, strong: Map<String, String>, images: Map<String, String>): CommentFace? {
        val display = keyword(element["display"]) ?: "inline"
        if (display == "none") {
            return CommentFace.HIDDEN
        }
        // An inline element ignores width and height, so an empty one is nothing at all.
        val float = keyword(element["float"])
        val position = keyword(element["position"])
        if (display !in SIZED_DISPLAYS && float != "left" && float != "right"
                && position != "absolute" && position != "fixed") {
            return null
        }
        val imageUrl = imageUrl(element["background-image"], images) ?: return null
        val fontSize = fontSize(element["font-size"], DEFAULT_FONT_SIZE)

        var width = CssValues.length(element["width"], fontSize) ?: return null
        var height = CssValues.length(element["height"], fontSize) ?: return null
        CssValues.length(element["max-width"], fontSize)?.let { width = min(width, it) }
        CssValues.length(element["max-height"], fontSize)?.let { height = min(height, it) }
        CssValues.length(element["min-width"], fontSize)?.let { width = max(width, it) }
        CssValues.length(element["min-height"], fontSize)?.let { height = max(height, it) }

        val paddingTop = padding(element["padding-top"], fontSize)
        val paddingRight = padding(element["padding-right"], fontSize)
        val paddingBottom = padding(element["padding-bottom"], fontSize)
        val paddingLeft = padding(element["padding-left"], fontSize)
        val borderBox = keyword(element["box-sizing"]) == "border-box"
        val boxWidth = if (borderBox) max(width, paddingLeft + paddingRight) else width + paddingLeft + paddingRight
        val boxHeight = if (borderBox) max(height, paddingTop + paddingBottom) else height + paddingTop + paddingBottom
        if (boxWidth <= 0f || boxHeight <= 0f || boxWidth > MAX_BOX || boxHeight > MAX_BOX) {
            return null
        }

        val size = backgroundSize(element["background-size"], fontSize)
        val repeat = repeat(element["background-repeat"])
        val background = CommentFaceBackground(
            CssValues.positionComponent(element["background-position-x"], fontSize) ?: CssLength.ZERO,
            CssValues.positionComponent(element["background-position-y"], fontSize) ?: CssLength.ZERO,
            size.mode, size.x, size.y, repeat.first, repeat.second,
            CssValues.color(element["background-color"])?.takeIf { it ushr 24 != 0 }
        )

        val visibility = keyword(element["visibility"])
        val invisible = visibility == "hidden" || visibility == "collapse"
        val verticalAlign = when (keyword(element["vertical-align"])) {
            "middle" -> CommentFace.VerticalAlign.MIDDLE
            "top", "text-top", "bottom", "text-bottom" -> CommentFace.VerticalAlign.BOTTOM
            else -> CommentFace.VerticalAlign.BASELINE
        }
        return CommentFace(
            imageUrl, boxWidth, boxHeight, paddingLeft, paddingTop, paddingRight, paddingBottom,
            background,
            if (invisible) 0f else opacity(element["opacity"]),
            if (invisible) null else caption(element, strong, fontSize, boxWidth),
            verticalAlign
        )
    }

    private fun caption(
        element: Map<String, String>,
        strong: Map<String, String>,
        fontSize: Float,
        boxWidth: Float,
    ): CommentFaceCaptionStyle? {
        if (fontSize <= 0f) {
            return null
        }
        val color = CssValues.color(element["color"])
        if (color != null && color ushr 24 == 0) {
            return null
        }
        // text-indent: -9999px is the classic way of pushing a link's text out of its own box.
        val indent = CssValues.length(element["text-indent"], fontSize)
        if (indent != null && abs(indent) >= boxWidth) {
            return null
        }
        val outline = CssValues.shadowColor(element["text-shadow"])?.let {
            CommentFaceCaptionStyle.Outline(it.argb)
        }
        val bold = isBold(element["font-weight"]) ?: false
        val align = when (keyword(element["text-align"])) {
            "center" -> CommentFaceCaptionStyle.Align.CENTER
            "right", "end" -> CommentFaceCaptionStyle.Align.RIGHT
            else -> CommentFaceCaptionStyle.Align.LEFT
        }
        return CommentFaceCaptionStyle(fontSize, color, outline, bold, align, strongStyle(strong, fontSize, bold, color))
    }

    private fun strongStyle(
        strong: Map<String, String>,
        parentFontSize: Float,
        parentBold: Boolean,
        parentColor: Int?,
    ): CommentFaceStrongStyle {
        val visible = keyword(strong["display"]) != "none" && keyword(strong["visibility"]) != "hidden"
        val fontSize = fontSize(strong["font-size"], parentFontSize)
        val weight = strong["font-weight"]?.trim()?.lowercase(Locale.ROOT)
        // A <strong> is bold unless told otherwise; `inherit` takes the link's weight instead.
        val bold = when (weight) {
            null -> true
            "inherit", "unset" -> parentBold
            else -> isBold(weight) ?: true
        }
        val color = CssValues.color(strong["color"])?.takeIf { it ushr 24 != 0 } ?: parentColor
        val position = keyword(strong["position"])
        var anchor = CommentFaceStrongStyle.Anchor.FLOW
        var offset = 0f
        if (position == "absolute" || position == "fixed") {
            val top = CssValues.length(strong["top"], fontSize)
            val bottom = CssValues.length(strong["bottom"], fontSize)
            if (top != null) {
                anchor = CommentFaceStrongStyle.Anchor.TOP
                offset = top
            } else if (bottom != null) {
                anchor = CommentFaceStrongStyle.Anchor.BOTTOM
                offset = bottom
            }
        }
        return CommentFaceStrongStyle(anchor, offset, visible && fontSize > 0f, bold, fontSize, color)
    }

    /** A keyword value, lowercased; null for a global keyword, which here means the initial value. */
    private fun keyword(value: String?): String? =
        value?.trim()?.lowercase(Locale.ROOT)?.takeUnless { it in GLOBAL_KEYWORDS }

    private fun fontSize(value: String?, parent: Float): Float {
        val lower = keyword(value) ?: return parent
        return when (lower) {
            "xx-small" -> 9f
            "x-small" -> 10f
            "small" -> 13f
            "medium" -> 16f
            "large" -> 18f
            "x-large" -> 24f
            "xx-large" -> 32f
            "xxx-large" -> 48f
            "smaller" -> parent / 1.2f
            "larger" -> parent * 1.2f
            else -> when {
                lower.endsWith("%") -> lower.dropLast(1).toFloatOrNull()?.let { parent * it / 100f }
                else -> CssValues.length(lower, parent)
            } ?: parent
        }
    }

    /** True for bold, false for not, null where [value] says nothing readable. */
    private fun isBold(value: String?): Boolean? {
        val lower = value?.trim()?.lowercase(Locale.ROOT) ?: return null
        return when (lower) {
            "bold", "bolder" -> true
            "normal", "lighter", "initial" -> false
            else -> lower.toIntOrNull()?.let { it >= 600 }
        }
    }

    private fun padding(value: String?, fontSize: Float): Float =
        CssValues.length(keyword(value), fontSize)?.coerceAtLeast(0f) ?: 0f

    private fun opacity(value: String?): Float {
        val lower = keyword(value) ?: return 1f
        val n = if (lower.endsWith("%")) {
            lower.dropLast(1).toFloatOrNull()?.div(100f)
        } else {
            lower.toFloatOrNull()
        }
        return (n ?: 1f).coerceIn(0f, 1f)
    }

    /** The URL of an uploaded stylesheet image, or null for none, a gradient, or anything off Reddit. */
    private fun imageUrl(value: String?, images: Map<String, String>): String? {
        val trimmed = value?.trim() ?: return null
        if (!trimmed.lowercase(Locale.ROOT).startsWith("url(")) {
            return null
        }
        val target = trimmed.substringAfter('(').substringBeforeLast(')').trim().trim('"', '\'').trim()
        if (target.startsWith("%%") && target.endsWith("%%") && target.length > 4) {
            return images[target.substring(2, target.length - 2)]
        }
        val absolute = if (target.startsWith("//")) "https:$target" else target
        val host = absolute.substringAfter("://", "").substringBefore('/').lowercase(Locale.ROOT)
        val onReddit = host.endsWith(".redditmedia.com") || host.endsWith(".redd.it")
                || host.endsWith(".redditstatic.com")
        return if (absolute.startsWith("https://") && onReddit) absolute else null
    }

    private class BackgroundSize(val mode: CommentFaceBackground.SizeMode, val x: CssLength?, val y: CssLength?)

    private fun backgroundSize(value: String?, fontSize: Float): BackgroundSize {
        val lower = keyword(value) ?: return BackgroundSize(CommentFaceBackground.SizeMode.EXPLICIT, null, null)
        when (lower) {
            "cover" -> return BackgroundSize(CommentFaceBackground.SizeMode.COVER, null, null)
            "contain" -> return BackgroundSize(CommentFaceBackground.SizeMode.CONTAIN, null, null)
        }
        val tokens = CssValues.tokens(lower)
        val x = tokens.getOrNull(0)?.let { if (it == "auto") null else CssValues.lengthOrPercent(it, fontSize) }
        val y = tokens.getOrNull(1)?.let { if (it == "auto") null else CssValues.lengthOrPercent(it, fontSize) }
        return BackgroundSize(CommentFaceBackground.SizeMode.EXPLICIT, x, y)
    }

    /** `background-repeat` as repeat-x and repeat-y; `space` and `round` are drawn as `repeat`. */
    private fun repeat(value: String?): Pair<Boolean, Boolean> {
        val tokens = CssValues.tokens(keyword(value) ?: return true to true)
        fun repeats(token: String) = token != "no-repeat"
        return when {
            tokens.size == 1 && tokens[0] == "repeat-x" -> true to false
            tokens.size == 1 && tokens[0] == "repeat-y" -> false to true
            tokens.size == 1 -> repeats(tokens[0]) to repeats(tokens[0])
            tokens.size == 2 -> repeats(tokens[0]) to repeats(tokens[1])
            else -> true to true
        }
    }
}
