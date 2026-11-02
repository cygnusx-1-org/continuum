package ml.docilealligator.infinityforreddit.utils

import android.content.SharedPreferences
import androidx.core.content.edit
import ml.docilealligator.infinityforreddit.account.AccountScope

/**
 * Turns every stored Use Old Load More Comments Method switch, which was per account, into the
 * global Use /api/info the Old Way setting on the API Keys screen, and drops the switches.
 *
 * Any switch that is on turns the setting on: someone who chose the old way for one account chose
 * it for the client ID, which every account shares. None turns it off, so a switch that is off
 * never undoes a choice already made on the API Keys screen.
 *
 * Runs at every start rather than once, for the reason [SharedLinkDomainMigration] gives: a restore
 * of a backup taken before the move brings its switches back. With none left in the file -- every
 * start after the first -- this is one pass over the keys and no write.
 */
object OldApiInfoMigration {

    /**
     * @param defaultSharedPreferences the raw default preferences, not the account façade: every
     *   account's switch is in the file
     * @param apiKeysSharedPreferences [SharedPreferencesUtils.DEFAULT_PREFERENCES_FILE], where the
     *   API Keys screen keeps its settings
     */
    @JvmStatic
    fun migrate(defaultSharedPreferences: SharedPreferences, apiKeysSharedPreferences: SharedPreferences) {
        val switches = defaultSharedPreferences.all.filterKeys {
            (AccountScope.baseOf(it) ?: it) == SharedPreferencesUtils.USE_LEGACY_MORE_CHILDREN
        }
        if (switches.isEmpty()) {
            return
        }

        if (switches.values.any { it == true }) {
            // Committed before the switches go, so a process killed in between finds them again
            // next start rather than losing the choice.
            apiKeysSharedPreferences.edit(commit = true) {
                putBoolean(SharedPreferencesUtils.USE_OLD_API_INFO_PREF_KEY, true)
            }
        }
        defaultSharedPreferences.edit {
            for (key in switches.keys) {
                remove(key)
            }
        }
    }
}
