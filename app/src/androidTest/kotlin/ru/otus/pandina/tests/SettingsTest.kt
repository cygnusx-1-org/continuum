package ru.otus.pandina.tests

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import ml.docilealligator.infinityforreddit.account.Account
import ml.docilealligator.infinityforreddit.activities.MainActivity
import ml.docilealligator.infinityforreddit.settings.MainPageTabInput
import ml.docilealligator.infinityforreddit.settings.MainPageTabsUtils
import ml.docilealligator.infinityforreddit.utils.SharedPreferencesUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.otus.pandina.screens.navigation.settings.interfaceScreen.CustomizeTabsScreen
import ru.otus.pandina.screens.MainScreen
import ru.otus.pandina.screens.navigation.NavigationViewLayout
import ru.otus.pandina.screens.navigation.settings.SettingsScreen
import ru.otus.pandina.screens.navigation.settings.ThemeScreen
import ru.otus.pandina.screens.navigation.settings.font.FontPreviewScreen
import ru.otus.pandina.screens.navigation.settings.font.FontScreen
import ru.otus.pandina.screens.navigation.settings.interfaceScreen.InterfaceScreen
import ru.otus.pandina.screens.navigation.settings.notification.NotificationScreen
import ru.otus.pandina.utils.NotificationDialogHelper
import ru.otus.pandina.utils.OnScreen

class SettingsTest : BaseTest() {

    private companion object {
        /** An entry of R.array.settings_font_family, with its R.array.settings_font_family_values value. */
        const val FONT_ENTRY = "Noto Sans"
        const val FONT_VALUE = "NotoSans"
    }

    private val targetContext: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    /**
     * The file the settings screens on this path actually write to.
     *
     * Deliberately not [SharedPreferencesUtils.DEFAULT_PREFERENCES_FILE], whose name is a trap: that
     * constant is the legacy "ml.docilealligator.infinityforreddit_preferences" file, and only
     * APIKeysPreferenceFragment opts into it. Everything else uses AppModule's @Named("default"),
     * which is PreferenceManager.getDefaultSharedPreferences — "<applicationId>_preferences". Read
     * the wrong one and every assertion here comes back null.
     */
    private fun defaultPreferences(): SharedPreferences =
        PreferenceManager.getDefaultSharedPreferences(targetContext)

    private fun mainPageTabsPreferences(): SharedPreferences = targetContext.getSharedPreferences(
        SharedPreferencesUtils.MAIN_PAGE_TABS_SHARED_PREFERENCES_FILE, Context.MODE_PRIVATE
    )

    /**
     * Every test here changes a real setting that outlives the test process, so each needs to put it
     * back. Without this they leak into each other -- setLightThemeTest asserting a white background
     * only means anything if setDarkThemeTest has not just left the install dark, and a test that
     * fails halfway through would otherwise poison every run after it.
     */
    private fun SharedPreferences.reset(vararg keys: String) {
        edit().apply { keys.forEach { remove(it) } }.commit()
    }

    fun openSettings() {
        run {
            step("Open navigation drawer") {
                MainScreen {
                    navButton {
                        isVisible()
                        click()
                    }
                }
                NavigationViewLayout {
                    navBanner.isVisible()
                    settings.isVisible()
                    nawDrawerRecyclerView {
                        scrollToEnd()
                    }
                }
            }
            step("Open settings") {
                NavigationViewLayout.settings.click()
                SettingsScreen {
                    screenTittle {
                        isVisible()
                        hasText("Settings")
                    }
                }
            }
        }
    }

    @Test
    fun disableNotificationTest() {
        before {
            openSettings()
        }.after {
            defaultPreferences().reset(SharedPreferencesUtils.ENABLE_NOTIFICATION_KEY)
        }.run {
            step("Open notifications screen and disable notifications") {
                SettingsScreen.notification.click()
                NotificationScreen {
                    screenTitle {
                        isVisible()
                        hasText("Notification")
                    }
                    enableNotifications.isVisible()
                    notificationInterval.isVisible()
                    notificationSwitch.click()
                    notificationInterval.doesNotExist()
                    notificationSwitch.click()
                    notificationInterval.isVisible()
                }
            }
        }
    }

    /**
     * Picks a font family and checks it took effect.
     *
     * This used to open the Font screen, click through to the font preview, assert that screen's
     * title and stop — despite its name it changed no font and would have passed just as happily
     * with the font list broken. It now selects an entry and asserts both the summary the user sees
     * and the value that was stored.
     */
    @Test
    fun setFontTest() {
        // Handle notification dialog immediately after app starts
        NotificationDialogHelper.handleNotificationDialog()

        before {
            openSettings()
        }.after {
            defaultPreferences().reset(SharedPreferencesUtils.FONT_FAMILY_KEY)
        }.run {
            step("Open interface screen") {
                SettingsScreen.interfaceSetting.click()
                InterfaceScreen {
                    screenTitle {
                        isVisible()
                        hasText("Interface")
                    }
                    font.isVisible()
                }
            }
            step("Open font screen") {
                InterfaceScreen.font.click()
                FontScreen {
                    screenTitle {
                        isVisible()
                        hasText("Font")
                    }
                    fontPreview.isVisible()
                    fontFamily.isVisible()
                }
            }
            step("Font preview screen opens and goes back") {
                FontScreen.fontPreview.click()
                FontPreviewScreen {
                    screenTitle {
                        isVisible()
                        hasText("Font Preview")
                    }
                }
                pressBack()
                FontScreen.fontFamily.isVisible()
            }
            step("Select a font family") {
                FontScreen.fontFamily.click()
                // Plain Espresso, so Kaspresso's flaky-safe retries do not cover it — wait for the
                // list dialog explicitly rather than sleeping a guessed number of milliseconds.
                assertTrue("font family dialog did not open", OnScreen.waitForText(FONT_ENTRY))
                onView(withText(FONT_ENTRY)).perform(click())
            }
            step("Font family was changed") {
                // Choosing a font recreates the activity, so this settles only once it is back up.
                FontScreen.summaryFontFamily.hasText(FONT_ENTRY)
                assertEquals(
                    FONT_VALUE,
                    defaultPreferences().getString(SharedPreferencesUtils.FONT_FAMILY_KEY, null)
                )
            }
        }
    }

    /**
     * The main page shows exactly the tabs it is configured with.
     *
     * The configuration is written through [MainPageTabsUtils] rather than driven through the UI.
     * This test used to click a numeric "Tab Count" picker and select 2, but that picker (and the
     * `tab_count_*` view ids it matched on) no longer exists — tabs are now added, removed and
     * reordered individually in CustomizeTabsOrderActivity. Driving a drag-and-drop reorder screen
     * is a test of its own; what this one is really about is the assertion at the end, that
     * MainActivity's tab strip reflects the configuration and drops "All".
     *
     * Works signed in or out. The tab order is stored per account -- MainPageTabsUtils.prefix()
     * prepends the account name for a real account and nothing for the anonymous one -- so both the
     * write and the teardown have to follow whichever account is current, not assume anonymous.
     */
    @Test
    fun mainPageShowsOnlyTheConfiguredTabs() {
        val account = currentAccountName()
        val tabsOrderKey = (if (account == Account.ANONYMOUS_ACCOUNT) "" else account) +
            SharedPreferencesUtils.MAIN_PAGE_TABS_ORDER

        before {
            openSettings()
        }.after {
            mainPageTabsPreferences().reset(tabsOrderKey)
        }.run {
            step("Customize Tabs in Main Page is reachable") {
                SettingsScreen.interfaceSetting.click()
                InterfaceScreen {
                    screenTitle {
                        isVisible()
                        hasText("Interface")
                    }
                    customizeTabs.isVisible()
                }
                InterfaceScreen.customizeTabs.click()
                CustomizeTabsScreen {
                    screenTitle {
                        isVisible()
                        hasText("Customize Tabs in Main Page")
                    }
                    tabsTitle.isVisible()
                }
            }
            step("Configure Home and Popular only") {
                MainPageTabsUtils.save(
                    mainPageTabsPreferences(),
                    account,
                    listOf(
                        MainPageTabInput(
                            SharedPreferencesUtils.MAIN_PAGE_TAB_POST_TYPE_HOME, "",
                            SharedPreferencesUtils.MAIN_PAGE_TAB_SOURCE_USER
                        ),
                        MainPageTabInput(
                            SharedPreferencesUtils.MAIN_PAGE_TAB_POST_TYPE_POPULAR, "",
                            SharedPreferencesUtils.MAIN_PAGE_TAB_SOURCE_USER
                        ),
                    )
                )
            }
            step("Restart app") {
                activityRule.scenario.close()
                ActivityScenario.launch(MainActivity::class.java, null)
            }
            step("Check tabs") {
                MainScreen {
                    tabLayout {
                        hasDescendant {
                            withText("Home")
                        }
                        hasDescendant {
                            withText("Popular")
                        }
                        hasNotDescendant {
                            withText("All")
                        }
                    }
                }
            }
        }
    }

    @Test
    fun setDarkThemeTest() {
        before {
            openSettings()
        }.after {
            defaultPreferences().reset(SharedPreferencesUtils.THEME_KEY)
        }.run {
            step("Open Theme screen and set dark theme") {
                SettingsScreen.theme.click()

                ThemeScreen {
                    themeRecycler {
                        // Click on the "Theme" preference item
                        childWith<ThemeScreen.ThemeRecyclerItem> {
                            withDescendant { withText("Theme") }
                        }.click()
                    }
                }

                assertTrue("theme dialog did not open", OnScreen.waitForText("Dark Theme"))
                onView(withText("Dark Theme")).perform(click())

                ThemeScreen {
                    themeRecycler {
                        // Verify the Theme item now shows "Dark Theme" in summary
                        childWith<ThemeScreen.ThemeRecyclerItem> {
                            withDescendant { withText("Theme") }
                        }.summary.hasText("Dark Theme")
                    }
                }
            }
        }
    }

    @Test
    fun setLightThemeTest() {
        before {
            openSettings()
        }.after {
            defaultPreferences().reset(SharedPreferencesUtils.THEME_KEY)
        }.run {
            step("Open Theme screen and set light theme") {
                SettingsScreen.theme.click()

                ThemeScreen {
                    themeRecycler {
                        // Click on the "Theme" preference item
                        childWith<ThemeScreen.ThemeRecyclerItem> {
                            withDescendant { withText("Theme") }
                        }.click()
                    }
                }

                assertTrue("theme dialog did not open", OnScreen.waitForText("Light Theme"))
                onView(withText("Light Theme")).perform(click())

                ThemeScreen {
                    themeRecycler {
                        // Verify the Theme item now shows "Light Theme" in summary
                        childWith<ThemeScreen.ThemeRecyclerItem> {
                            withDescendant { withText("Theme") }
                        }.summary.hasText("Light Theme")
                    }
                    frame.hasBackgroundColor(Color.WHITE)
                }
            }
        }
    }
}
