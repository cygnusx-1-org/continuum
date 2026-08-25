package ml.docilealligator.infinityforreddit.customtheme

import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import ml.docilealligator.infinityforreddit.TestInfinity
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Customize Theme screen flattens a [CustomTheme] into a list of rows and rebuilds it from that
 * list when the user saves. The way back is a hand-written table of **positional** indices --
 * `customThemeSettingsItems.get(57).colorValue` -- one line per field, with nothing tying an index
 * to the row it is supposed to name.
 *
 * An off-by-one there does not fail: it silently assigns one colour to a neighbouring field, so
 * saving a theme quietly moves two colours. Inserting or removing a row shifts every index after it,
 * which is exactly when that happens.
 *
 * These walk the whole table rather than a sample, and pin the row count to the field count, so a
 * field added or removed on either side of the mapping fails here.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestInfinity::class)
class CustomThemeSettingsItemRoundTripTest {

    private fun colourFields(): List<Field> = CustomTheme::class.java.fields
        .filter { it.type == Int::class.javaPrimitiveType && !Modifier.isStatic(it.modifiers) }
        .sortedBy { it.name }

    private fun flagFields(): List<Field> = CustomTheme::class.java.fields
        .filter { it.type == Boolean::class.javaPrimitiveType && !Modifier.isStatic(it.modifiers) }
        .sortedBy { it.name }

    /** A theme whose every value is distinct from its neighbours' in the editor list. */
    private fun distinctTheme(): CustomTheme {
        val theme = CustomTheme("Test theme")
        colourFields().forEachIndexed { index, field -> field.setInt(theme, 0x21000000 + index) }
        // Set by name, not by index: the flags sit in two runs in the editor list, and neighbours
        // within a run have to differ for a swap between them to be visible at all.
        theme.isLightTheme = true
        theme.isDarkTheme = false
        theme.isAmoledTheme = true
        theme.isLightStatusBar = false
        theme.isLightNavBar = true
        theme.isChangeStatusBarIconColorAfterToolbarCollapsedInImmersiveInterface = false
        return theme
    }

    private fun editorRows(theme: CustomTheme) =
        CustomThemeSettingsItem.convertCustomThemeToSettingsItem(
            ApplicationProvider.getApplicationContext<Context>(),
            theme,
            Build.VERSION_CODES.TIRAMISU
        )

    @Test
    fun `every colour survives the trip out to the editor rows and back`() {
        val theme = distinctTheme()

        val rebuilt = CustomTheme.convertSettingsItemsToCustomTheme(editorRows(theme), "Test theme")

        colourFields().forEach { field ->
            assertEquals(
                "${field.name} came back as a different colour",
                field.getInt(theme),
                field.getInt(rebuilt)
            )
        }
    }

    @Test
    fun `every flag survives the trip out to the editor rows and back`() {
        val theme = distinctTheme()

        val rebuilt = CustomTheme.convertSettingsItemsToCustomTheme(editorRows(theme), "Test theme")

        flagFields().forEach { field ->
            assertEquals(
                "${field.name} came back set the other way",
                field.getBoolean(theme),
                field.getBoolean(rebuilt)
            )
        }
    }

    @Test
    fun `the editor has exactly one row per theme field`() {
        val fields = colourFields().size + flagFields().size

        assertEquals(fields, editorRows(distinctTheme()).size)
    }

    @Test
    fun `an empty row list leaves a theme at its defaults rather than throwing`() {
        val rebuilt = CustomTheme.convertSettingsItemsToCustomTheme(ArrayList(), "Test theme")

        assertEquals("Test theme", rebuilt.name)
        assertEquals(0, rebuilt.colorPrimary)
    }
}
