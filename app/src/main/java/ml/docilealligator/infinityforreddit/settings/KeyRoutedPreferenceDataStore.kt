package ml.docilealligator.infinityforreddit.settings

import android.content.SharedPreferences
import androidx.preference.PreferenceDataStore

/**
 * Sends a few keys of one preference screen to a second file and the rest to the first.
 *
 * androidx gives a screen one preferences file; a screen that joins settings from two files needs
 * the store in front of them to choose, and it has to be in place before the screen inflates, or
 * the defaults of the second file's rows would be written into the first.
 */
class KeyRoutedPreferenceDataStore(
    private val primary: SharedPreferences,
    private val secondary: SharedPreferences,
    private val secondaryKeys: Set<String>,
) : PreferenceDataStore() {

    private fun prefsFor(key: String): SharedPreferences =
        if (key in secondaryKeys) secondary else primary

    override fun putString(key: String, value: String?) {
        prefsFor(key).edit().putString(key, value).apply()
    }

    override fun getString(key: String, defValue: String?): String? =
        prefsFor(key).getString(key, defValue)

    override fun putStringSet(key: String, values: MutableSet<String>?) {
        prefsFor(key).edit().putStringSet(key, values).apply()
    }

    override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? =
        prefsFor(key).getStringSet(key, defValues)

    override fun putInt(key: String, value: Int) {
        prefsFor(key).edit().putInt(key, value).apply()
    }

    override fun getInt(key: String, defValue: Int): Int = prefsFor(key).getInt(key, defValue)

    override fun putLong(key: String, value: Long) {
        prefsFor(key).edit().putLong(key, value).apply()
    }

    override fun getLong(key: String, defValue: Long): Long = prefsFor(key).getLong(key, defValue)

    override fun putFloat(key: String, value: Float) {
        prefsFor(key).edit().putFloat(key, value).apply()
    }

    override fun getFloat(key: String, defValue: Float): Float = prefsFor(key).getFloat(key, defValue)

    override fun putBoolean(key: String, value: Boolean) {
        prefsFor(key).edit().putBoolean(key, value).apply()
    }

    override fun getBoolean(key: String, defValue: Boolean): Boolean =
        prefsFor(key).getBoolean(key, defValue)
}
