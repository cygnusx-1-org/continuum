package ru.otus.pandina.utils

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import java.util.regex.Pattern

object NotificationDialogHelper {

    private const val TIMEOUT_MS = 3_000L

    private val ALLOW: Pattern = Pattern.compile("Allow")

    /**
     * Either button, as a fallback so the dialog is always dismissed. Note the deny button is
     * spelled with a typographic apostrophe (U+2019), not an ASCII one — a literal "Don't allow"
     * matches nothing.
     */
    private val ANY_BUTTON: Pattern = Pattern.compile("Don['’]t allow|Allow")

    /**
     * Dismisses the runtime notification-permission dialog if this launch put one up.
     *
     * Waits for a button instead of sleeping a fixed three seconds. The dialog only appears on API
     * 33+ and only until the permission has been answered once, so the old sleep spent three
     * seconds doing nothing on most runs while still being able to expire early on a slow one.
     *
     * Answers "Allow" on purpose. SettingsTest.disableNotificationTest drives the notification
     * settings screen, and with the permission actually denied, switching notifications back on
     * sends the app out to the system settings app — Espresso then fails with "No activities in
     * stage RESUMED". The previous helper looked like it preferred denying, but its literal
     * "Don't allow" never matched the typographic apostrophe, so every run granted anyway; that is
     * the state the suite was written against.
     */
    fun handleNotificationDialog() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

        // Absent is the normal case, so a timeout here is not a failure.
        if (!device.wait(Until.hasObject(By.text(ANY_BUTTON)), TIMEOUT_MS)) {
            return
        }

        val button = device.findObject(By.text(ALLOW))
            ?: device.findObject(By.text(ANY_BUTTON))
            ?: return

        button.click()
        device.wait(Until.gone(By.text(ANY_BUTTON)), TIMEOUT_MS)
    }
}
