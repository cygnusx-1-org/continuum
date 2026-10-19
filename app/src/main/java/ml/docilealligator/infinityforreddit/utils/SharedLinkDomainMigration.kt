package ml.docilealligator.infinityforreddit.utils

import android.content.SharedPreferences
import androidx.core.content.edit
import ml.docilealligator.infinityforreddit.account.AccountScope

/**
 * Turns every stored Use old.reddit.com switch into the Domain for shared links value it means,
 * and drops the switch.
 *
 * Runs at every start rather than once, because a switch can come back after the upgrade: a
 * restore merges a backup's keys into the file without clearing it, so a backup taken before the
 * list brings its switch back beside whatever list value this device already holds. Restores end
 * by restarting the app, so converting at start-up is what lets the backup's switch win, as it
 * should, instead of leaving two keys that disagree. With no switch left in the file — every start
 * after the first — this is one pass over the keys and no write.
 */
object SharedLinkDomainMigration {

    /**
     * Takes the raw default preferences, not the account façade: every account's switch is in the
     * file, and the inert pre-account copy too, so a seeding that has yet to run copies the list.
     */
    @JvmStatic
    fun migrate(defaultSharedPreferences: SharedPreferences) {
        val switches = defaultSharedPreferences.all.filterKeys {
            (AccountScope.baseOf(it) ?: it) == SharedPreferencesUtils.USE_OLD_REDDIT_DOMAIN
        }
        if (switches.isEmpty()) {
            return
        }

        defaultSharedPreferences.edit {
            for ((key, value) in switches) {
                // The switch's own scope: its key with the base swapped, as the account façade
                // builds it.
                val scope = key.substring(0, key.length - SharedPreferencesUtils.USE_OLD_REDDIT_DOMAIN.length)
                val domain = if (value == true) RedditLinkUtils.OLD_REDDIT_HOST else RedditLinkUtils.WWW_REDDIT_HOST
                putString(scope + SharedPreferencesUtils.SHARED_LINK_DOMAIN, domain)
                remove(key)
            }
        }
    }
}
