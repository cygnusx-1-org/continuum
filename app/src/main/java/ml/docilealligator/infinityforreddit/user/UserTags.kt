package ml.docilealligator.infinityforreddit.user

import android.content.SharedPreferences
import java.util.Locale
import ml.docilealligator.infinityforreddit.events.UserTagChangedEvent
import org.greenrobot.eventbus.EventBus

/**
 * The private tags the current account has put on other users (issue #413), after RES's user
 * tagger: a short label set from a user's profile and shown beside that user's name wherever it
 * appears. Nothing leaves the device.
 *
 * One `SharedPreferences` file, one key per tagged user. The key is the lowercased username —
 * Reddit usernames are case-insensitive, and a tag on `Alice` has to be found on `alice` — and the
 * file is account-scoped through `AppModule`, so each account keeps its own tags and a switch of
 * account changes what every read here returns without anyone re-installing it.
 *
 * Read from adapters on every bind, so it is a static lookup rather than one more constructor
 * argument threaded through the five adapters that show an author: the store is installed once by
 * `Infinity.onCreate` and answers "no tag" until then, which is what keeps those adapters
 * constructible in unit tests with no setup.
 */
object UserTags {

    @Volatile
    private var preferences: SharedPreferences? = null

    /** Gives the store its file. Called once, from `Infinity.onCreate`. */
    @JvmStatic
    fun install(preferences: SharedPreferences) {
        this.preferences = preferences
    }

    /** The tag on [username], or null when there is none — or when there is no author at all. */
    @JvmStatic
    fun get(username: String?): String? {
        if (username.isNullOrEmpty()) {
            return null
        }
        val tag = preferences?.getString(key(username), null)
        return if (tag.isNullOrEmpty()) null else tag
    }

    @JvmStatic
    fun isTagged(username: String?): Boolean = get(username) != null

    /**
     * Tags [username] as [tag]. Whitespace at the ends is not part of a tag, and a tag that is
     * nothing but whitespace is a removal: the dialog offers "Set tag" on an emptied field, and
     * that has to mean the same as "Remove tag".
     */
    @JvmStatic
    fun set(username: String, tag: String) {
        val trimmed = tag.trim()
        if (trimmed.isEmpty()) {
            remove(username)
            return
        }
        preferences?.edit()?.putString(key(username), trimmed)?.apply()
        EventBus.getDefault().post(UserTagChangedEvent(username))
    }

    @JvmStatic
    fun remove(username: String) {
        preferences?.edit()?.remove(key(username))?.apply()
        EventBus.getDefault().post(UserTagChangedEvent(username))
    }

    private fun key(username: String): String = username.lowercase(Locale.ROOT)
}
