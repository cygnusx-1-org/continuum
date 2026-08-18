package ru.otus.pandina.utils

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiSelector

/**
 * Presence checks against the live view hierarchy, through UiAutomator rather than Espresso.
 *
 * These exist so a test can branch on something genuinely optional -- a first-run dialog that only
 * appears once per install -- without wrapping its assertions in try/catch. A catch swallows a
 * failure from a dialog that *is* present but wrong just as readily as it handles an absent one,
 * which turns the whole step into something that cannot fail.
 *
 * Use these only to decide whether a step runs. Assert with Espresso/Kakao once inside it.
 */
object OnScreen {

    private val device: UiDevice
        get() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    /** True once a view showing exactly [text] is on screen, waiting up to [timeoutMs] for it. */
    fun waitForText(text: String, timeoutMs: Long = 5_000): Boolean =
        device.findObject(UiSelector().text(text)).waitForExists(timeoutMs)
}
