package ru.otus.pandina.screens.navigation.settings

import io.github.kakaocup.kakao.screen.Screen
import io.github.kakaocup.kakao.text.KTextView
import ml.docilealligator.infinityforreddit.R

object APIKeysScreen : Screen<APIKeysScreen>() {

    val screenTitle = KTextView {
        withParent { withId(R.id.toolbar_settings_activity) }
        withText("API Keys")
    }

    val enableOverrides = KTextView { withText("Enable Overrides") }

    /**
     * The Client ID preference row. AndroidX Preference pushes its enabled state down onto the
     * row's child views, so asserting isEnabled/isDisabled on the title reflects whether the
     * android:dependency on [enableOverrides] is currently satisfied.
     */
    val clientId = KTextView { withText("Reddit API Client ID") }

    /** The row's summary, which useSimpleSummaryProvider fills with the stored value. */
    fun clientIdSummary(value: String) = KTextView {
        withResourceName("summary")
        withText(value)
    }
}
