package ml.docilealligator.infinityforreddit.message

import android.content.SharedPreferences
import ml.docilealligator.infinityforreddit.utils.SharedPreferenceLiveData
import ml.docilealligator.infinityforreddit.utils.SharedPreferencesUtils
import ml.docilealligator.infinityforreddit.utils.intLiveData

/**
 * The number of unread inbox items, stored in the current account's preferences.
 *
 * The stored value is the only source of truth. Every badge observes it through [liveData], so a
 * change reaches the activities that are running and the ones created later through the same
 * channel, and nothing can move a badge without persisting the new count.
 */
object InboxCount {
    @JvmStatic
    fun get(currentAccountSharedPreferences: SharedPreferences): Int =
        currentAccountSharedPreferences.getInt(SharedPreferencesUtils.INBOX_COUNT, 0)

    @JvmStatic
    fun set(currentAccountSharedPreferences: SharedPreferences, count: Int) {
        currentAccountSharedPreferences.edit()
            .putInt(SharedPreferencesUtils.INBOX_COUNT, count.coerceAtLeast(0))
            .apply()
    }

    /**
     * Subtracts the [count] items that have just been marked read. Call it once the read request
     * has succeeded, so a failed one leaves the badge alone.
     */
    @JvmStatic
    @JvmOverloads
    @Synchronized
    fun decrement(currentAccountSharedPreferences: SharedPreferences, count: Int = 1) {
        set(currentAccountSharedPreferences, get(currentAccountSharedPreferences) - count)
    }

    @JvmStatic
    fun liveData(currentAccountSharedPreferences: SharedPreferences): SharedPreferenceLiveData<Int> =
        currentAccountSharedPreferences.intLiveData(SharedPreferencesUtils.INBOX_COUNT, 0)
}
