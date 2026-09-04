package ml.docilealligator.infinityforreddit.utils

import android.content.SharedPreferences
import ml.docilealligator.infinityforreddit.RedditDataRoomDatabase
import ml.docilealligator.infinityforreddit.account.AccountScope

/**
 * "Forever Disable NSFW" was a global override that hid NSFW content without ever clearing the
 * per-account NSFW switch underneath it, so that switch could still read true while the override did
 * the hiding. Deleting the feature would therefore hand NSFW content back to exactly the people who
 * had most deliberately turned it off, on their next launch. This turns their switches off once
 * instead.
 *
 * The orphaned preference is the only remaining record of who was affected, so it is read before it
 * is removed; removing it is also what stops this from running a second time. It is cleared whatever
 * its value, so no install carries the deleted feature's key around afterwards.
 *
 * Called from [ml.docilealligator.infinityforreddit.account.AccountSettingsMigration] rather than
 * from the application directly, so that it always runs after the keys it writes have been moved to
 * their [AccountScope] spelling.
 */
object DisableNsfwForeverMigration {
    /**
     * The key the deleted feature wrote. Deliberately not in [SharedPreferencesUtils]: nothing may
     * read it again, and it exists here only long enough to be acted on and erased.
     */
    private const val DISABLE_NSFW_FOREVER = "disable_nsfw_forever"

    @JvmStatic
    fun turnOffNsfwForAffectedAccounts(
        defaultSharedPreferences: SharedPreferences,
        nsfwAndSpoilerSharedPreferences: SharedPreferences,
        redditDataRoomDatabase: RedditDataRoomDatabase,
    ) {
        if (!defaultSharedPreferences.contains(DISABLE_NSFW_FOREVER)) {
            // Never enabled, or already migrated. The common path: a map lookup and no write.
            return
        }

        if (!defaultSharedPreferences.getBoolean(DISABLE_NSFW_FOREVER, false)) {
            // Written once and left off. No switch to correct, so just take the key away.
            defaultSharedPreferences.edit().remove(DISABLE_NSFW_FOREVER).apply()
            return
        }

        val editor = nsfwAndSpoilerSharedPreferences.edit()
        editor.putBoolean(AccountScope.key(null, SharedPreferencesUtils.NSFW_BASE), false)
        for (account in redditDataRoomDatabase.accountDao().allAccounts) {
            editor.putBoolean(AccountScope.key(account.accountName, SharedPreferencesUtils.NSFW_BASE), false)
        }
        editor.apply()

        defaultSharedPreferences.edit().remove(DISABLE_NSFW_FOREVER).apply()
    }
}
