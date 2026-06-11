@file:JvmName("UtilsKt")
package ml.docilealligator.infinityforreddit.utils

import android.content.Context
import androidx.browser.customtabs.CustomTabsClient

fun getChromeCustomTabPackageName(context: Context): String? {
    // ignoreDefault = false so the user's default browser is preferred when it
    // supports Custom Tabs (e.g. Vanadium and other Chromium-based browsers that
    // aren't in the explicit fallback list below). Without this, browsers not
    // hardcoded here are rejected with "You must have Chrome browser installed."
    return CustomTabsClient.getPackageName(
        context, listOf("com.android.chrome", "com.chrome.beta", "com.chrome.dev", "com.chrome.canary", "org.mozilla.firefox", "org.mozilla.focus"), false
    )
}

fun getRandomString(length: Int = 6) : String {
    val allowedChars = ('A'..'Z') + ('a'..'z') + ('0'..'9')
    return (1..length)
        .map { allowedChars.random() }
        .joinToString("")
}
