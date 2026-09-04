package ml.docilealligator.infinityforreddit.account

import android.content.SharedPreferences
import androidx.preference.PreferenceDataStore

/**
 * Points a preference screen at an [AccountScopedSharedPreferences] instead of the file androidx
 * would open by name.
 *
 * Without this the split is only half done: a screen would show the current account's value, having
 * read it through the injected preferences, and then write the edit back to the unscoped file where
 * nothing reads it. A data store is the one hook androidx offers for redirecting both directions.
 *
 * Note that `PreferenceManager.getSharedPreferences()` returns null once a store is set, so any
 * screen reading its own preferences that way has to be given the injected instance instead.
 */
class AccountScopedPreferenceDataStore(
    private val sharedPreferences: SharedPreferences,
) : PreferenceDataStore() {

    override fun putString(key: String, value: String?) {
        sharedPreferences.edit().putString(key, value).apply()
    }

    override fun getString(key: String, defValue: String?): String? =
        sharedPreferences.getString(key, defValue)

    override fun putStringSet(key: String, values: MutableSet<String>?) {
        sharedPreferences.edit().putStringSet(key, values).apply()
    }

    override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? =
        sharedPreferences.getStringSet(key, defValues)

    override fun putInt(key: String, value: Int) {
        sharedPreferences.edit().putInt(key, value).apply()
    }

    override fun getInt(key: String, defValue: Int): Int = sharedPreferences.getInt(key, defValue)

    override fun putLong(key: String, value: Long) {
        sharedPreferences.edit().putLong(key, value).apply()
    }

    override fun getLong(key: String, defValue: Long): Long = sharedPreferences.getLong(key, defValue)

    override fun putFloat(key: String, value: Float) {
        sharedPreferences.edit().putFloat(key, value).apply()
    }

    override fun getFloat(key: String, defValue: Float): Float =
        sharedPreferences.getFloat(key, defValue)

    override fun putBoolean(key: String, value: Boolean) {
        sharedPreferences.edit().putBoolean(key, value).apply()
    }

    override fun getBoolean(key: String, defValue: Boolean): Boolean =
        sharedPreferences.getBoolean(key, defValue)
}
