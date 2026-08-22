package ml.docilealligator.infinityforreddit.layout

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Asserts that a layout defined in more than one resource configuration defines the same set of view
 * ids in every one of them.
 *
 * <p>This is the tripwire for issue #369. `save_user_image_view_view_user_detail_activity` was added
 * to `layout/activity_view_user_detail.xml` only; `layout-land/` and `layout-sw600dp/` kept their old
 * copies. ViewBinding responds to that by typing the field `@Nullable`, and the activity dereferenced
 * it unconditionally — so every tablet, and every phone in landscape, crashed on `onCreate` the
 * moment a user profile was opened.
 *
 * <p>Android Lint ships `InconsistentLayout`, which is the same idea, but it only considers ids that
 * are referenced from code as `R.id.something`. The ViewBinding migration removed almost all of those
 * references, so the check is blind to exactly the ids we now reach through a binding — it stayed
 * silent on #369 while still reporting the four `navigation_rail` cases, which are `findViewById`
 * holdovers. This test does not care how a view is reached, only that the configurations agree.
 *
 * <p>It is a plain JUnit test on purpose: it parses the XML as text, so it needs no Robolectric, no
 * resource merge and no emulator, and it runs in milliseconds as part of `./gradlew test`.
 */
class LayoutVariantIdParityTest {

    private data class Divergence(val layout: String, val variant: String, val id: String)

    companion object {
        /**
         * Ids that are *deliberately* absent from some configurations. Every entry needs a reason,
         * and every entry must still be divergent — [allowlistHasNoStaleEntries] fails if one gets
         * fixed and is left behind, so this list cannot quietly rot into a blanket suppression.
         *
         * Allowlisting an id means "the code copes with it being null", not "the drift is fine".
         */
        private val ALLOWED: Map<String, Map<String, String>> = mapOf(
            // The navigation rail is the tablet/landscape replacement for the bottom app bar, so by
            // design it exists only in those configurations. NavigationWrapper.navigationRailView is
            // null-checked at every use (e.g. ViewUserDetailActivity.java:318, :386, :911, :944).
            "activity_view_multi_reddit_detail.xml" to mapOf(
                "navigation_rail" to "Tablet/landscape only; null-checked via NavigationWrapper.",
            ),
            "activity_view_subreddit_detail.xml" to mapOf(
                "navigation_rail" to "Tablet/landscape only; null-checked via NavigationWrapper.",
            ),
            "activity_view_user_detail.xml" to mapOf(
                "navigation_rail" to "Tablet/landscape only; null-checked via NavigationWrapper.",
            ),
            "app_bar_main.xml" to mapOf(
                "navigation_rail" to "Tablet/landscape only; null-checked via NavigationWrapper.",
            ),
            // Separate post/comment panes exist only where there is room for two columns.
            // ViewPostDetailFragmentNew.java:240-246 reads the binding field into a local and
            // null-checks it before every use.
            "fragment_view_post_detail.xml" to mapOf(
                "comments_recycler_view_view_post_detail_fragment" to
                    "Two-pane only; null-checked in ViewPostDetailFragmentNew.",
            ),
        )

        /**
         * `android:id="@+id/foo"`, the rarer `android:id="@id/foo"`, and the framework form
         * `android:id="@android:id/foo"` (as used by preference_slider.xml).
         *
         * The framework form matters: ViewBinding generates a field for those views too — a
         * PreferenceSliderBinding has `icon`, `title` and `summary` — so an android-namespace id
         * present in one configuration and missing from another produces exactly the same
         * @Nullable field, and exactly the same crash, as issue #369.
         *
         * The namespace is kept as part of the recorded name so `@android:id/title` and
         * `@+id/title` in one layout can never alias into a single id.
         */
        private val ID_PATTERN =
            Regex("""android:id\s*=\s*"@\+?(\*?android:)?id/([A-Za-z0-9_.]+)"""")

        /** XML comments, stripped so a commented-out view never counts as a declaration. */
        private val COMMENT_PATTERN = Regex("""<!--.*?-->""", RegexOption.DOT_MATCHES_ALL)

        /**
         * The module's `src/main/res`. Gradle runs unit tests with the module directory as the
         * working directory; the parent fallback keeps the test runnable from the repo root too.
         */
        private fun resDir(): File {
            val candidates = listOf(File("src/main/res"), File("app/src/main/res"))
            return candidates.firstOrNull { it.isDirectory }
                ?: error("Could not locate src/main/res from ${File("").absolutePath}")
        }

        /** filename -> (variant directory -> declared ids). */
        private fun idsByLayoutAndVariant(): Map<String, Map<String, Set<String>>> {
            val byLayout = mutableMapOf<String, MutableMap<String, Set<String>>>()
            resDir().listFiles { f -> f.isDirectory && f.name.startsWith("layout") }
                ?.sortedBy { it.name }
                ?.forEach { variantDir ->
                    variantDir.listFiles { f -> f.isFile && f.name.endsWith(".xml") }
                        ?.sortedBy { it.name }
                        ?.forEach { xml ->
                            val body = COMMENT_PATTERN.replace(xml.readText(), "")
                            val ids = ID_PATTERN.findAll(body)
                                .map { it.groupValues[1] + it.groupValues[2] }
                                .toSet()
                            byLayout.getOrPut(xml.name) { mutableMapOf() }[variantDir.name] = ids
                        }
                }
            return byLayout
        }

        /** Every id that some configuration of a multi-variant layout declares and another omits. */
        private fun divergences(): List<Divergence> {
            val found = mutableListOf<Divergence>()
            idsByLayoutAndVariant().forEach { (layout, byVariant) ->
                if (byVariant.size < 2) return@forEach
                val union = byVariant.values.flatten().toSet()
                byVariant.forEach { (variant, ids) ->
                    (union - ids).sorted().forEach { found += Divergence(layout, variant, it) }
                }
            }
            return found
        }
    }

    @Test
    fun everyLayoutVariantDeclaresTheSameIds() {
        val unexplained = divergences().filter { ALLOWED[it.layout]?.containsKey(it.id) != true }

        assertTrue(
            buildString {
                append("A view id is declared in some configurations of a layout but not others.\n")
                append("ViewBinding types such a field @Nullable, so any unguarded use of it is a\n")
                append("crash on the configurations that omit the view (see issue #369).\n\n")
                append("Add the view to the configurations below, or — if the code genuinely copes\n")
                append("with it being null — add it to ALLOWED in this test with the reason why.\n\n")
                unexplained.groupBy { it.layout }.toSortedMap().forEach { (layout, items) ->
                    append("  ").append(layout).append('\n')
                    items.groupBy { it.id }.toSortedMap().forEach { (id, rows) ->
                        append("    ").append(id).append(" missing from: ")
                        append(rows.map { it.variant }.sorted().joinToString(", ")).append('\n')
                    }
                }
            },
            unexplained.isEmpty(),
        )
    }

    /**
     * The mirror of Lint's `LintBaselineFixed`: an allowlist entry that no longer describes a real
     * divergence is deleted, so re-introducing the drift fails the build instead of silently
     * matching a stale exemption.
     */
    @Test
    fun allowlistHasNoStaleEntries() {
        val actual = divergences().map { it.layout to it.id }.toSet()
        val stale = ALLOWED.flatMap { (layout, ids) -> ids.keys.map { layout to it } }
            .filterNot { it in actual }

        assertTrue(
            buildString {
                append("ALLOWED lists divergences that no longer exist. Delete these entries:\n")
                stale.sortedBy { it.first + it.second }.forEach { (layout, id) ->
                    append("  ").append(layout).append(": ").append(id).append('\n')
                }
            },
            stale.isEmpty(),
        )
    }
}
