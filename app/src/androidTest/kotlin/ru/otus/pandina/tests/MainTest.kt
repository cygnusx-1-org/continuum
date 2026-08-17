package ru.otus.pandina.tests

import androidx.test.espresso.action.GeneralLocation
import org.junit.Test
import ru.otus.pandina.screens.CustomizePostFilterScreen
import ru.otus.pandina.screens.FilteredPostsScreen
import ru.otus.pandina.screens.MainScreen
import ru.otus.pandina.utils.NotificationDialogHelper


class MainTest : BaseTest() {

    /**
     * Requires a logged-out install. The FAB tapped below only opens CustomizePostFilterActivity for
     * the anonymous account: MainActivity defaults fabOption to SUBMIT_POSTS and rewrites it to
     * FILTER_POSTS only when the account is anonymous (MainActivity.java:888 and the `default`
     * branch that follows). Signed in, the same tap starts a post submission and this test fails
     * several steps later on a missing post-filter toolbar.
     */
    @Test
    fun popularPostFilterTest() {
        requireAnonymousInstall()

        // Handle notification dialog immediately after app starts
        NotificationDialogHelper.handleNotificationDialog()

        run {
            step("Main screen popular tab") {
                MainScreen {
                    tabLayout {
                        isVisible()
                        click(GeneralLocation.CENTER)
                    }
                    button {
                        isVisible()
                        click()
                    }
                }
            }
            step("Check customize filter") {
                CustomizePostFilterScreen {
                    toolBar {
                        isVisible()
                        hasTitle("Customize Post Filter")
                    }
                    customizeFilterEditText {
                        isEnabled()
                        hasText("New Filter")
                    }
                    textFilterChip {
                        isVisible()
                        hasText("Text")
                        isChecked()
                        click()
                        isNotChecked()
                    }
                    linkFilterChip {
                        isVisible()
                        hasText("Link")
                        isChecked()
                        click()
                        isNotChecked()
                    }
                    onlyNsfwChip {
                        isVisible()
                        hasText("Only NSFW Content")
                        isNotChecked()
                    }
                    saveButton.click()
                }
            }
            step("Popular Filtered Posts") {
                FilteredPostsScreen {
                    postFragmentList {
                        isVisible()

                        firstChild<FilteredPostsScreen.PostFragmentItem> {
                            isVisible()
                        }

                        lastChild<FilteredPostsScreen.PostFragmentItem> {
                            isVisible()
                            // `title` matches whichever card layout this post uses, so this asserts
                            // unconditionally -- a blank title now fails instead of being swallowed.
                            title.hasAnyText()
                        }

                        children<FilteredPostsScreen.PostFragmentItem> {
                            isVisible()
                        }
                    }
                    filterButton {
                        isVisible()
                        click()
                    }
                }
            }
        }
    }
}
