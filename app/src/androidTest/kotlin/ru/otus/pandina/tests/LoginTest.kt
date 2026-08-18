package ru.otus.pandina.tests

import androidx.test.espresso.web.webdriver.Locator
import org.junit.Test
import ru.otus.pandina.screens.MainScreen
import ru.otus.pandina.screens.navigation.LoginScreen
import ru.otus.pandina.screens.navigation.NavigationViewLayout
import ru.otus.pandina.utils.NotificationDialogHelper


class LoginTest : BaseTest() {

    /**
     * Drives the nav drawer through to Reddit's OAuth page and asserts the login form really loaded.
     *
     * It stops at the form on purpose: the suite is given no Reddit credentials (only
     * REDDIT_CLIENT_ID is passed to the runner), so a successful sign-in cannot be asserted. This
     * test used to be called `loginTest` and end by typing "*****" into the username field, which
     * asserted nothing about logging in either -- the name now says what it actually covers.
     *
     * Requires a logged-out install: the whole subject is the anonymous add-account path, and the
     * drawer assertions below ("Anonymous", "Press here to login") only hold when signed out. There
     * is no signed-in equivalent of this test to write.
     */
    @Test
    fun opensRedditLoginPage() {
        requireAnonymousInstall()

        // Handle notification dialog immediately after app starts
        NotificationDialogHelper.handleNotificationDialog()

        run {
            step("Open navigation") {
                MainScreen {
                    navButton {
                        isVisible()
                        click()
                    }
                }
                NavigationViewLayout {
                    navBanner.isVisible()
                }
            }
            step("Go to login form") {
                NavigationViewLayout {
                    accountNameTextView {
                        isVisible()
                        hasText("Anonymous")
                    }
                    karmaTextView {
                        isVisible()
                        hasText("Press here to login")
                    }
                    accountSwitcher {
                        isVisible()
                        click()
                    }
                    addAccountTextView {
                        isVisible()
                        hasText("Add an account")
                    }
                    addAccountButton {
                        isVisible()
                        longClick()
                    }
                }
            }
            // There is deliberately no user-agreement step here. This test used to have one, wrapped
            // in a try/catch that swallowed its assertions -- which hid the fact that it could never
            // run: long-pressing "Add an account" goes to the legacy WebView LoginActivity
            // (MainActivity.java:1147), which has no agreement gate. The dialog belongs to the
            // normal-click path in AppAuthLoginActivity, and is a Compose dialog there, so the
            // R.id.alertTitle matcher the old step used could not have matched it either way.
            step("Reddit's login form is loaded") {
                LoginScreen {
                    webView {
                        withElement(
                            Locator.XPATH,
                            "//h1"
                        ) {
                            containsText("Log In")
                        }
                        withElement(
                            Locator.XPATH,
                            "//*[@id='login-username']"
                        ) {
                            containsText("Email or username")
                        }
                    }
                }
            }
        }
    }
}
