package ml.docilealligator.infinityforreddit.account;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import java.util.List;

/**
 * The accounts table, which the anonymous account is a row in but never a member of.
 *
 * <p>Its row exists so that the foreign keys of everything stored per account have a parent to
 * point at; {@code RedditDataRoomDatabase} guarantees it on every open. It is not an account anyone
 * signed into -- no token, no karma, no avatar -- so every query below that answers "which accounts
 * are there" or "which one is current" excludes it with {@code username != '.anonymous'}. That
 * literal is repeated rather than shared because a Room {@code @Query} cannot reference a constant.
 *
 * <p>Anonymous is therefore a browsing mode in the UI -- an entry beside "Add account" in the
 * switcher -- rather than a row in the account list, and is deliberately not offered as a source to
 * copy settings from. The stored settings underneath it are per-account like any other's.
 */
@Dao
public interface AccountDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(Account account);

    // REPLACE would delete the existing row first, taking every ON DELETE CASCADE child (read posts,
    // subscriptions, search history) and the stored anonymous access token with it. Use this when the
    // row is only needed to satisfy a foreign key and an existing one must be left alone.
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insertIfNotExists(Account account);

    // Ordered so the account chooser's rows keep a stable, predictable position across re-emissions
    // of this LiveData, and match the order the navigation drawer already uses.
    @Query("SELECT * FROM accounts WHERE username != '.anonymous' ORDER BY username COLLATE NOCASE ASC")
    LiveData<List<Account>> getAllAccountsLiveData();

    @Query("SELECT * FROM accounts WHERE username != '.anonymous'")
    List<Account> getAllAccounts();

    @Query("SELECT * FROM accounts WHERE is_current_user = 0 AND username != '.anonymous'")
    List<Account> getAllNonCurrentAccounts();

    @Query("UPDATE accounts SET is_current_user = 0 WHERE is_current_user = 1 AND username != '.anonymous'")
    void markAllAccountsNonCurrent();

    @Query("DELETE FROM accounts WHERE is_current_user = 1 AND username != '.anonymous'")
    void deleteCurrentAccount();

    @Query("DELETE FROM accounts WHERE username = :accountName")
    void deleteAccount(String accountName);

    /**
     * Every signed-in account. Used by a restore, to clear the local accounts before the backed-up
     * ones go in.
     *
     * <p>Spares {@code '-'} as well as the current spelling: a restore of a backup taken before the
     * rename lands its anonymous rows on a row of that name, and this runs in the middle of it.
     * Deleting it there would take those rows with it through {@code ON DELETE CASCADE}.
     */
    @Query("DELETE FROM accounts WHERE username != '.anonymous' AND username != '-'")
    void deleteAllAccounts();

    @Query("SELECT * FROM accounts WHERE username = :username COLLATE NOCASE LIMIT 1")
    LiveData<Account> getAccountLiveData(String username);

    @Query("SELECT * FROM accounts WHERE username = :username COLLATE NOCASE LIMIT 1")
    Account getAccountData(String username);

    @Query("SELECT * FROM accounts WHERE is_current_user = 1 AND username != '.anonymous' LIMIT 1")
    Account getCurrentAccount();

    @Query("SELECT * FROM accounts WHERE is_current_user = 1 AND username != '.anonymous' LIMIT 1")
    LiveData<Account> getCurrentAccountLiveData();

    @Query("UPDATE accounts SET profile_image_url = :profileImageUrl, banner_image_url = :bannerImageUrl, " +
            "karma = :karma, is_mod = :isMod WHERE username = :username")
    void updateAccountInfo(String username, String profileImageUrl, @Nullable String bannerImageUrl, int karma, boolean isMod);

    @Query("SELECT * FROM accounts WHERE is_current_user = 0 AND username != '.anonymous' ORDER BY username COLLATE NOCASE ASC")
    LiveData<List<Account>> getAccountsExceptCurrentAccountLiveData();

    @Query("UPDATE accounts SET is_current_user = 1 WHERE username = :username")
    void markAccountCurrent(String username);

    @Query("UPDATE accounts SET access_token = :accessToken, refresh_token = :refreshToken WHERE username = :username")
    void updateAccessTokenAndRefreshToken(String username, String accessToken, String refreshToken);

    @Query("UPDATE accounts SET access_token = :accessToken WHERE username = :username")
    void updateAccessToken(String username, String accessToken);
}
