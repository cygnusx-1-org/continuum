package ml.docilealligator.infinityforreddit.account

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface AccountDaoKt {
    @Insert(onConflict = OnConflictStrategy.Companion.REPLACE)
    suspend fun insert(account: Account)

    // REPLACE would delete the existing row first, taking every ON DELETE CASCADE child (read posts,
    // subscriptions, search history) and the stored anonymous access token with it. Use this when the
    // row is only needed to satisfy a foreign key and an existing one must be left alone.
    @Insert(onConflict = OnConflictStrategy.Companion.IGNORE)
    suspend fun insertIfNotExists(account: Account)

    @Query("UPDATE accounts SET is_current_user = 0 WHERE is_current_user = 1 AND username != '.anonymous'")
    suspend fun markAllAccountsNonCurrent()

    @Query(
        "UPDATE accounts SET profile_image_url = :profileImageUrl, banner_image_url = :bannerImageUrl, " +
                "karma = :karma, is_mod = :isMod WHERE username = :username"
    )
    suspend fun updateAccountInfo(
        username: String,
        profileImageUrl: String,
        bannerImageUrl: String?,
        karma: Int,
        isMod: Boolean
    )

    @Query("SELECT access_token FROM accounts WHERE username = '.anonymous'")
    fun getAnonymousAccessToken(): String?

    @Query("UPDATE accounts SET access_token = :accessToken WHERE username = '.anonymous'")
    fun setAnonymousAccessToken(accessToken: String?)
}