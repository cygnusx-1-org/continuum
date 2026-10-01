package ml.docilealligator.infinityforreddit.markdown

import java.util.WeakHashMap
import org.commonmark.internal.DocumentParser
import org.commonmark.node.Block
import org.commonmark.parser.Parser

/**
 * Switches off commonmark's own parser for a block type, for plugins that register a Reddit-specific
 * replacement.
 *
 * [Parser.Builder.enabledBlockTypes] replaces the whole set on each call and has no getter, so two
 * plugins that each passed `CorePlugin.enabledBlockTypes()` minus their own type would re-enable
 * whatever the other had turned off, and which one won would come down to plugin order. This keeps
 * everything disabled on a builder so far and applies the union on every call.
 */
object CoreBlockTypes {
    // Weak so a builder, garbage once its Markwon is built, doesn't stay reachable from here.
    private val disabled = WeakHashMap<Parser.Builder, MutableSet<Class<out Block>>>()

    @JvmStatic
    @Synchronized
    fun disable(builder: Parser.Builder, type: Class<out Block>) {
        val types = disabled.getOrPut(builder) { HashSet() }
        types.add(type)
        // commonmark tries its core parsers in this set's iteration order. CorePlugin.enabledBlockTypes()
        // is a HashSet, which would leave precedence (setext heading vs rule, say) to identity hash
        // codes, so start from commonmark's own ordered default instead.
        builder.enabledBlockTypes(LinkedHashSet(DocumentParser.getDefaultBlockParserTypes()).apply { removeAll(types) })
    }
}
