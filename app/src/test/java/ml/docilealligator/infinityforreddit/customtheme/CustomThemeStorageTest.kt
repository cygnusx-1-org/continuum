package ml.docilealligator.infinityforreddit.customtheme

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import ml.docilealligator.infinityforreddit.TestInfinity
import ml.docilealligator.infinityforreddit.utils.CustomThemeSharedPreferencesUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A theme is stored as ninety-odd loose preference keys and read back one hand-written getter at a
 * time, with nothing but matching names holding the two halves together. A colour written under one
 * key and read under another does not fail anywhere -- the getter quietly returns its built-in
 * default, so the app just shows the wrong colour in one place and looks like a theming bug.
 *
 * These walk the whole set instead of a sample, so adding a colour to [CustomTheme] without also
 * writing it in [CustomThemeSharedPreferencesUtils] or reading it in [CustomThemeWrapper] fails
 * here rather than on a screen nobody checked.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestInfinity::class)
class CustomThemeStorageTest {

    /** Colours whose getter is not `get` + the field name. */
    private val getterAliases = mapOf(
        "archivedTint" to "getArchivedIconTint",
        "fabIconColor" to "getFABIconColor"
    )

    /**
     * Colours the theme stores but nothing reads. Empty, and meant to stay that way: a colour with
     * no getter is a colour the theme editor can set and no screen will ever show. The award
     * colours used to sit here, and were removed outright once it was clear nothing would draw
     * them again.
     */
    private val storedButNeverRead = emptySet<String>()

    private lateinit var lightPreferences: SharedPreferences
    private lateinit var darkPreferences: SharedPreferences
    private lateinit var amoledPreferences: SharedPreferences

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        lightPreferences = context.getSharedPreferences("theme_light_test", Context.MODE_PRIVATE)
        darkPreferences = context.getSharedPreferences("theme_dark_test", Context.MODE_PRIVATE)
        amoledPreferences = context.getSharedPreferences("theme_amoled_test", Context.MODE_PRIVATE)
        listOf(lightPreferences, darkPreferences, amoledPreferences)
            .forEach { it.edit().clear().commit() }
    }

    private fun colourFields(): List<Field> = CustomTheme::class.java.fields
        .filter { it.type == Int::class.javaPrimitiveType && !Modifier.isStatic(it.modifiers) }
        .sortedBy { it.name }

    private fun getterNameFor(field: Field) =
        getterAliases[field.name] ?: ("get" + field.name.replaceFirstChar { it.uppercase() })

    private fun wrapper(themeType: Int) =
        CustomThemeWrapper(lightPreferences, darkPreferences, amoledPreferences)
            .apply { setThemeType(themeType) }

    @Test
    fun `every colour the theme stores is the colour the app reads back`() {
        val fields = colourFields()
        val theme = CustomTheme("test")
        // A distinct value per colour, so a getter reading a neighbour's key is a mismatch and not
        // a coincidence.
        fields.forEachIndexed { index, field -> field.setInt(theme, 0x11000000 + index) }

        CustomThemeSharedPreferencesUtils.insertThemeToSharedPreferences(theme, lightPreferences)
        val wrapper = wrapper(CustomThemeSharedPreferencesUtils.LIGHT)

        fields.filterNot { it.name in storedButNeverRead }.forEach { field ->
            val getterName = getterNameFor(field)
            val getter = CustomThemeWrapper::class.java.getMethod(getterName)
            assertEquals(
                "${field.name} was stored, but $getterName() read back something else",
                field.getInt(theme),
                getter.invoke(wrapper) as Int
            )
        }
    }

    @Test
    fun `every colour has a getter`() {
        val withoutAGetter = colourFields()
            .filter { field ->
                runCatching { CustomThemeWrapper::class.java.getMethod(getterNameFor(field)) }
                    .isFailure
            }
            .map { it.name }

        assertEquals(storedButNeverRead.sorted(), withoutAGetter.sorted())
    }

    @Test
    fun `the theme's status and nav bar flags survive the round trip`() {
        val on = CustomTheme("on").apply {
            isLightStatusBar = true
            isLightNavBar = true
            isChangeStatusBarIconColorAfterToolbarCollapsedInImmersiveInterface = true
        }
        CustomThemeSharedPreferencesUtils.insertThemeToSharedPreferences(on, lightPreferences)

        wrapper(CustomThemeSharedPreferencesUtils.LIGHT).let {
            assertTrue(it.isLightStatusBar)
            assertTrue(it.isLightNavBar)
            assertTrue(it.isChangeStatusBarIconColorAfterToolbarCollapsedInImmersiveInterface)
        }

        CustomThemeSharedPreferencesUtils
            .insertThemeToSharedPreferences(CustomTheme("off"), lightPreferences)

        wrapper(CustomThemeSharedPreferencesUtils.LIGHT).let {
            assertFalse(it.isLightStatusBar)
            assertFalse(it.isLightNavBar)
            assertFalse(it.isChangeStatusBarIconColorAfterToolbarCollapsedInImmersiveInterface)
        }
    }

    @Test
    fun `each theme type reads the file that theme was stored in`() {
        fun store(colorPrimary: Int, into: SharedPreferences) {
            CustomThemeSharedPreferencesUtils.insertThemeToSharedPreferences(
                CustomTheme("t").apply { this.colorPrimary = colorPrimary }, into
            )
        }
        store(0x11111111, lightPreferences)
        store(0x22222222, darkPreferences)
        store(0x33333333, amoledPreferences)

        assertEquals(
            0x11111111,
            wrapper(CustomThemeSharedPreferencesUtils.LIGHT).colorPrimary
        )
        assertEquals(
            0x22222222,
            wrapper(CustomThemeSharedPreferencesUtils.DARK).colorPrimary
        )
        assertEquals(
            0x33333333,
            wrapper(CustomThemeSharedPreferencesUtils.AMOLED).colorPrimary
        )
    }
}
