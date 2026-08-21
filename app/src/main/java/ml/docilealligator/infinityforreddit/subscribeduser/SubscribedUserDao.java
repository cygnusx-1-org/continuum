package ml.docilealligator.infinityforreddit.subscribeduser;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import java.util.List;

@Dao
public interface SubscribedUserDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(SubscribedUserData subscribedUserData);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<SubscribedUserData> subscribedUserDataList);

    @Query("SELECT * FROM subscribed_users WHERE username = :accountName AND name LIKE :searchQuery COLLATE NOCASE ORDER BY name COLLATE NOCASE ASC")
    LiveData<List<SubscribedUserData>> getAllSubscribedUsersWithSearchQuery(String accountName, String searchQuery);

    @Query("SELECT * FROM subscribed_users WHERE username = :accountName COLLATE NOCASE ORDER BY name COLLATE NOCASE ASC")
    List<SubscribedUserData> getAllSubscribedUsersList(String accountName);

    @Query("SELECT * FROM subscribed_users WHERE username = :accountName AND name LIKE :searchQuery COLLATE NOCASE AND is_favorite = 1 ORDER BY name COLLATE NOCASE ASC")
    LiveData<List<SubscribedUserData>> getAllFavoriteSubscribedUsersWithSearchQuery(String accountName, String searchQuery);

    @Query("SELECT * FROM subscribed_users WHERE name = :name COLLATE NOCASE AND username = :accountName COLLATE NOCASE LIMIT 1")
    @Nullable
    SubscribedUserData getSubscribedUser(String name, String accountName);

    @Query("DELETE FROM subscribed_users WHERE name = :name COLLATE NOCASE AND username = :accountName COLLATE NOCASE")
    void deleteSubscribedUser(String name, String accountName);

    /**
     * Creates the row only if it is not there yet, with both reasons cleared; the caller then sets
     * its own flag. Following and saving run on a shared thread pool and can therefore race, and a
     * read-then-REPLACE would let the later writer drop the other's flag -- this cannot.
     */
    @Query("INSERT OR IGNORE INTO subscribed_users (name, icon, username, is_favorite, is_followed, is_saved) "
            + "VALUES (:name, :iconUrl, :accountName, 0, 0, 0)")
    void insertIfAbsent(String name, String iconUrl, String accountName);

    @Query("UPDATE subscribed_users SET is_followed = :followed WHERE name = :name COLLATE NOCASE AND username = :accountName COLLATE NOCASE")
    void updateFollowed(String name, String accountName, boolean followed);

    @Query("UPDATE subscribed_users SET is_saved = :saved WHERE name = :name COLLATE NOCASE AND username = :accountName COLLATE NOCASE")
    void updateSaved(String name, String accountName, boolean saved);

    @Query("UPDATE subscribed_users SET is_favorite = :favorite WHERE name = :name COLLATE NOCASE AND username = :accountName COLLATE NOCASE")
    void updateFavorite(String name, String accountName, boolean favorite);

    /**
     * Writes back the columns the Reddit subscription sync owns, leaving {@code is_saved} -- which
     * only exists locally -- alone. A REPLACE here would drop a save made while the sync ran.
     */
    @Query("UPDATE subscribed_users SET icon = :iconUrl, is_favorite = :favorite, is_followed = 1 "
            + "WHERE name = :name COLLATE NOCASE AND username = :accountName COLLATE NOCASE")
    void updateFromSync(String name, String accountName, String iconUrl, boolean favorite);

    /**
     * Drops the row once it is neither followed nor saved -- a user is in the list because of one
     * of those two, so clearing both removes them.
     */
    @Query("DELETE FROM subscribed_users WHERE name = :name COLLATE NOCASE AND username = :accountName COLLATE NOCASE AND is_followed = 0 AND is_saved = 0")
    void deleteIfNeitherFollowedNorSaved(String name, String accountName);

    /**
     * Names only: Recently Visited needs the set to decide which rows still offer a follow. Saved
     * users are deliberately excluded -- saving someone does not follow them, so their follow
     * control must stay.
     */
    @Query("SELECT name FROM subscribed_users WHERE username = :accountName AND is_followed = 1")
    LiveData<List<String>> getFollowedUserNames(String accountName);

    @Query("SELECT name FROM subscribed_users WHERE username = :accountName AND is_saved = 1")
    LiveData<List<String>> getSavedUserNames(String accountName);

    @Query("SELECT EXISTS(SELECT 1 FROM subscribed_users WHERE name = :name COLLATE NOCASE "
            + "AND username = :accountName COLLATE NOCASE AND is_saved = 1)")
    LiveData<Boolean> isUserSavedLiveData(String name, String accountName);
}
