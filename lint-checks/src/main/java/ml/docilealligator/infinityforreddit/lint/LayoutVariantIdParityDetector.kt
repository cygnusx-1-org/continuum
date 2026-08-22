package ml.docilealligator.infinityforreddit.lint

import com.android.SdkConstants.ATTR_ID
import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Attr

/**
 * Flags a view id that one configuration of a layout declares and another omits.
 *
 * ViewBinding types the field for such an id `@Nullable`, because it cannot know which
 * configuration will be inflated. Dereferencing it without a null check is then a crash on exactly
 * the configurations that omit the view — which is issue #369: the save-user ribbon was added to
 * `layout/activity_view_user_detail.xml` and not to `layout-land/` or `layout-sw600dp/`, so opening
 * any user profile killed the app on every tablet and on every phone in landscape.
 *
 * AGP ships [InconsistentLayout][com.android.tools.lint.checks.LayoutConsistencyDetector], which is
 * the same idea, but it only reports ids that are referenced from code as `R.id.something`. The
 * ViewBinding migration removed nearly all such references, so it stayed silent on #369 while still
 * reporting the four `navigation_rail` cases — the remaining `findViewById` holdovers. This check
 * makes no distinction: how a view is reached is irrelevant to whether the configurations agree.
 *
 * Divergence that is genuinely intended — a view that only exists on tablets, whose absence the
 * code null-checks — is exempted with `tools:ignore="InconsistentLayoutVariantIds"` on the
 * declaring element. That keeps the exemption in the file it applies to, next to the view and
 * visible in review, rather than in a baseline nobody reads.
 *
 * Runs in two phases, the way the built-in consistency check does: phase 1 records every id per
 * layout and per resource folder, and if anything diverges it asks the driver to repeat; phase 2
 * reports on the declaring element, which is what gives `tools:ignore` and precise line numbers.
 */
class LayoutVariantIdParityDetector : ResourceXmlDetector() {

    /** layout file name -> resource folder name -> ids declared there. Phase 1 output. */
    private val declared = mutableMapOf<String, MutableMap<String, MutableSet<String>>>()

    /** layout file name -> folder that declares it -> ids to report there. Phase 2 input. */
    private var toReport = mapOf<String, Map<String, Set<String>>>()

    override fun appliesTo(folderType: ResourceFolderType): Boolean =
        folderType == ResourceFolderType.LAYOUT

    override fun getApplicableAttributes(): Collection<String> = listOf(ATTR_ID)

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        val id = idName(attribute.value) ?: return
        val folder = context.file.parentFile?.name ?: return
        val layout = context.file.name

        if (context.phase == 1) {
            declared.getOrPut(layout) { mutableMapOf() }
                .getOrPut(folder) { mutableSetOf() }
                .add(id)
            return
        }

        if (id !in (toReport[layout]?.get(folder) ?: emptySet())) return

        val absentFrom = declared[layout].orEmpty()
            .filterValues { id !in it }
            .keys
            .sorted()
        context.report(
            ISSUE,
            attribute,
            context.getLocation(attribute),
            "The id `$id` is declared in `$folder` but missing from ${absentFrom.joinToString(", ") { "`$it`" }}. " +
                "ViewBinding will type this field @Nullable, so any unguarded use of it crashes on " +
                "those configurations. Add the view there, or suppress with " +
                "tools:ignore=\"$ID\" if the code null-checks it.",
        )
    }

    override fun afterCheckRootProject(context: Context) {
        if (context.phase != 1) return

        val pending = mutableMapOf<String, MutableMap<String, MutableSet<String>>>()
        for ((layout, byFolder) in declared) {
            if (byFolder.size < 2) continue
            val union = byFolder.values.flatten().toSet()
            for ((folder, ids) in byFolder) {
                // Report against the folders that DO declare the id, matching where the built-in
                // check anchors its findings — and giving tools:ignore somewhere to live.
                for (id in union - ids) {
                    byFolder.filterValues { id in it }.keys.forEach { declaringFolder ->
                        pending.getOrPut(layout) { mutableMapOf() }
                            .getOrPut(declaringFolder) { mutableSetOf() }
                            .add(id)
                    }
                }
            }
        }

        if (pending.isEmpty()) return
        toReport = pending
        context.driver.requestRepeat(this, Scope.ALL_RESOURCES_SCOPE)
    }

    /**
     * `@+id/foo` / `@id/foo` -> `foo`, and `@android:id/foo` -> `android:foo`; anything else -> null.
     *
     * The framework form is included because ViewBinding generates fields for those views too
     * (PreferenceSliderBinding has `icon`, `title`, `summary` from `@android:id/...`), so they carry
     * the same @Nullable-on-divergence risk. The namespace is kept in the returned name so
     * `@android:id/title` and `@+id/title` in one layout cannot alias into a single id.
     *
     * Matching the whole value rather than splitting on the last `/` matters: `substringAfterLast`
     * returns its input unchanged when the delimiter is absent, so a slashless value such as
     * `android:id="@null"` used to be recorded as an id literally named `@null` — and, if it
     * appeared in one configuration only, reported as a build-failing error naming a view that does
     * not exist.
     */
    private fun idName(value: String?): String? {
        val match = value?.let { ID_REFERENCE.matchEntire(it) } ?: return null
        return match.groupValues[1] + match.groupValues[2]
    }

    companion object {
        private const val ID = "InconsistentLayoutVariantIds"

        /** `@id/foo`, `@+id/foo`, `@android:id/foo`, `@*android:id/foo` — and nothing else. */
        private val ID_REFERENCE = Regex("""^@\+?(\*?android:)?id/([A-Za-z0-9_.]+)$""")

        @JvmField
        val ISSUE: Issue = Issue.create(
            id = ID,
            briefDescription = "View id missing from a layout configuration",
            explanation = """
                A view id declared in one configuration of a layout but not another makes ViewBinding \
                type the corresponding field `@Nullable`, because it cannot know which configuration \
                will be inflated. Code that dereferences it without a null check then crashes on \
                exactly the configurations that omit the view — typically tablets (`layout-sw600dp`) \
                and landscape (`layout-land`), which are the easiest to forget when editing the \
                default `layout/` copy.

                The built-in `InconsistentLayout` check covers only ids referenced from code as \
                `R.id.something`, so it does not see ids reached through a ViewBinding field. This \
                check ignores how the view is reached.

                If the divergence is deliberate and the code null-checks the view, suppress it with \
                `tools:ignore="InconsistentLayoutVariantIds"` on the declaring element.
            """,
            category = Category.CORRECTNESS,
            priority = 8,
            severity = Severity.ERROR,
            androidSpecific = true,
            implementation = Implementation(
                LayoutVariantIdParityDetector::class.java,
                Scope.ALL_RESOURCES_SCOPE,
            ),
        )
    }
}
