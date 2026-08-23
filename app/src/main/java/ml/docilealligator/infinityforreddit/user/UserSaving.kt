package ml.docilealligator.infinityforreddit.user

import android.os.Handler
import java.util.concurrent.Executor
import ml.docilealligator.infinityforreddit.RedditDataRoomDatabase
import ml.docilealligator.infinityforreddit.account.Account

/**
 * Saving a user: purely local, never a Reddit call, and available while logged out.
 *
 * A `subscribed_users` row means followed **or** saved, so saving only sets [is_saved] and
 * unsaving drops the row only when the user is not followed either.
 */
object UserSaving {

    @JvmStatic
    fun saveUser(
        executor: Executor,
        handler: Handler,
        redditDataRoomDatabase: RedditDataRoomDatabase,
        accountName: String,
        username: String,
        iconUrl: String?,
        listener: UserSavingListener
    ) {
        executor.execute {
            if (accountName == Account.ANONYMOUS_ACCOUNT) {
                redditDataRoomDatabase.accountDao().insertIfNotExists(Account.getAnonymousAccount())
            }
            val dao = redditDataRoomDatabase.subscribedUserDao()
            // The entity treats "no icon" as an empty string, not null.
            dao.insertIfAbsent(username, iconUrl ?: "", accountName)
            dao.updateSaved(username, accountName, true)
            handler.post { listener.onUserSavingSuccess() }
        }
    }

    @JvmStatic
    fun unsaveUser(
        executor: Executor,
        handler: Handler,
        redditDataRoomDatabase: RedditDataRoomDatabase,
        accountName: String,
        username: String,
        listener: UserSavingListener
    ) {
        executor.execute {
            val dao = redditDataRoomDatabase.subscribedUserDao()
            dao.updateSaved(username, accountName, false)
            dao.deleteIfNeitherFollowedNorSaved(username, accountName)
            handler.post { listener.onUserSavingSuccess() }
        }
    }


    fun interface UserSavingListener {
        fun onUserSavingSuccess()
    }
}
