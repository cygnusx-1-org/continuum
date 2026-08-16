package ru.otus.pandina.screens.navigation.settings.interfaceScreen

import io.github.kakaocup.kakao.screen.Screen
import io.github.kakaocup.kakao.text.KTextView
import ml.docilealligator.infinityforreddit.R

object CustomizeTabsScreen : Screen<CustomizeTabsScreen>() {

    val screenTitle = KTextView { withText("Customize Tabs in Main Page") }

    /**
     * The row that opens CustomizeTabsOrderActivity.
     *
     * This screen used to offer a numeric "Tab Count" picker, and the ids here still named it
     * (`tab_count_title_text_view_…` / `tab_count_text_view_…`) long after both the picker and those
     * ids were removed — which is one of the two reasons this whole source set stopped compiling.
     * Tabs are now added, removed and reordered individually in a separate activity.
     */
    val tabsTitle = KTextView { withId(R.id.tabs_title_text_view_customize_main_page_tabs_fragment) }

    val tabsSummary = KTextView { withId(R.id.tabs_summary_text_view_customize_main_page_tabs_fragment) }
}
