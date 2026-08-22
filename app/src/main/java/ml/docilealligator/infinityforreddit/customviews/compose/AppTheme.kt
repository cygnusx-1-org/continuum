package ml.docilealligator.infinityforreddit.customviews.compose

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.flow.onEach
import ml.docilealligator.infinityforreddit.Infinity
import ml.docilealligator.infinityforreddit.customtheme.CustomTheme
import ml.docilealligator.infinityforreddit.customtheme.CustomThemeWrapper
import ml.docilealligator.infinityforreddit.customtheme.LocalCustomThemeRepository
import ml.docilealligator.infinityforreddit.utils.CustomThemeSharedPreferencesUtils

val LocalAppTheme = staticCompositionLocalOf<CustomTheme> {
    error("No default theme values")
}

@Composable
fun AppTheme(themeType: Int, content: @Composable () -> Unit) {
    val context = LocalContext.current
    // Both of these are remembered rather than rebuilt each pass. collectAsState keys its collection
    // on the flow instance, so a repository built fresh every recomposition yields a fresh flow and
    // tears down and restarts the database collection every time anything here recomposes — starting
    // with the recomposition that onEach itself triggers by setting themeLoaded.
    val localCustomThemeRepository = remember(context) {
        LocalCustomThemeRepository(((context.applicationContext) as Infinity).mRedditDataRoomDatabase)
    }
    // Held as the state object, not a `by` delegate, so onEach below writes to this remembered
    // instance rather than to whatever the enclosing recomposition captured.
    val themeLoadedState = remember { mutableStateOf(false) }
    val themeLoaded by themeLoadedState

    val currentThemeFlow = remember(localCustomThemeRepository, themeType) {
        when (themeType) {
            CustomThemeSharedPreferencesUtils.LIGHT -> localCustomThemeRepository.currentLightCustomThemeFlow
            CustomThemeSharedPreferencesUtils.DARK -> localCustomThemeRepository.currentDarkCustomThemeFlow
            CustomThemeSharedPreferencesUtils.AMOLED -> localCustomThemeRepository.currentAmoledCustomThemeFlow
            else -> localCustomThemeRepository.currentLightCustomThemeFlow
        }.onEach {
            themeLoadedState.value = true
        }
    }

    val customTheme by currentThemeFlow.collectAsState(initial = null)

    if (themeLoaded) {
        CompositionLocalProvider(LocalAppTheme provides (customTheme ?: getDefaultTheme(context, themeType))) {
            MaterialTheme {
                content()
            }
        }
    }
}

private fun getDefaultTheme(context: Context, themeType: Int): CustomTheme {
    return when(themeType) {
        CustomThemeSharedPreferencesUtils.LIGHT -> CustomThemeWrapper.getIndigo(context)
        CustomThemeSharedPreferencesUtils.DARK -> CustomThemeWrapper.getIndigoDark(context)
        CustomThemeSharedPreferencesUtils.AMOLED -> CustomThemeWrapper.getIndigoAmoled(context)
        else -> CustomThemeWrapper.getIndigo(context)
    }
}