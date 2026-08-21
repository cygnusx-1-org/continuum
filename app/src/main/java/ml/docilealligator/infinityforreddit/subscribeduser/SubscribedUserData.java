package ml.docilealligator.infinityforreddit.subscribeduser;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import ml.docilealligator.infinityforreddit.account.Account;

@Entity(tableName = "subscribed_users", primaryKeys = {"name", "username"},
        foreignKeys = @ForeignKey(entity = Account.class, parentColumns = "username",
                childColumns = "username", onDelete = ForeignKey.CASCADE),
        indices = {@Index(value = "username")})
public class SubscribedUserData {
    @NonNull
    @ColumnInfo(name = "name")
    private final String name;
    @ColumnInfo(name = "icon")
    private final String iconUrl;
    @NonNull
    @ColumnInfo(name = "username")
    private String username;
    @ColumnInfo(name = "is_favorite")
    private boolean favorite;
    /**
     * Whether the account follows this user on Reddit. A row means followed <em>or</em> saved, so
     * this can no longer be inferred from the row existing.
     */
    @ColumnInfo(name = "is_followed", defaultValue = "1")
    private boolean followed;
    /** Whether the user was added to the list locally, without following them on Reddit. */
    @ColumnInfo(name = "is_saved", defaultValue = "0")
    private boolean saved;

    /**
     * Rows built from Reddit's subscription listing are follows; a locally saved user gets its row
     * from {@code SubscribedUserDao.insertIfAbsent} instead.
     */
    public SubscribedUserData(@NonNull String name, String iconUrl, @NonNull String username, boolean favorite) {
        this.name = name;
        this.iconUrl = iconUrl;
        this.username = username;
        this.favorite = favorite;
        this.followed = true;
        this.saved = false;
    }

    @NonNull
    public String getName() {
        return name;
    }

    public String getIconUrl() {
        return iconUrl;
    }

    @NonNull
    public String getUsername() {
        return username;
    }

    public void setUsername(@NonNull String username) {
        this.username = username;
    }

    public boolean isFavorite() {
        return favorite;
    }

    public void setFavorite(boolean favorite) {
        this.favorite = favorite;
    }

    public boolean isFollowed() {
        return followed;
    }

    public void setFollowed(boolean followed) {
        this.followed = followed;
    }

    public boolean isSaved() {
        return saved;
    }

    public void setSaved(boolean saved) {
        this.saved = saved;
    }
}
