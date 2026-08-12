package ml.docilealligator.infinityforreddit.localsaved;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import ml.docilealligator.infinityforreddit.account.Account;

/**
 * A local marker for something the user saved on Reddit. Stores only the Reddit fullname
 * (t3_... for a post, t1_... for a comment) plus a save timestamp and a pending/promoted
 * state. The actual content is re-fetched live via /api/info when displayed.
 * <p>
 * Modeled on {@link ml.docilealligator.infinityforreddit.readpost.ReadPost}.
 */
@Entity(tableName = "local_saved", primaryKeys = {"username", "full_name"},
        foreignKeys = @ForeignKey(entity = Account.class, parentColumns = "username",
                childColumns = "username", onDelete = ForeignKey.CASCADE),
        indices = {@Index("username")})
public class LocalSavedThing {
    @NonNull
    @ColumnInfo(name = "username")
    private String username;
    @NonNull
    @ColumnInfo(name = "full_name")
    private String fullName;
    @LocalSavedState
    @ColumnInfo(name = "state")
    private int state;
    @ColumnInfo(name = "time")
    private long time;

    public LocalSavedThing(@NonNull String username, @NonNull String fullName,
                           @LocalSavedState int state, long time) {
        this.username = username;
        this.fullName = fullName;
        this.state = state;
        this.time = time;
    }

    @NonNull
    public String getUsername() {
        return username;
    }

    public void setUsername(@NonNull String username) {
        this.username = username;
    }

    @NonNull
    public String getFullName() {
        return fullName;
    }

    public void setFullName(@NonNull String fullName) {
        this.fullName = fullName;
    }

    @LocalSavedState
    public int getState() {
        return state;
    }

    public void setState(@LocalSavedState int state) {
        this.state = state;
    }

    public long getTime() {
        return time;
    }

    public void setTime(long time) {
        this.time = time;
    }
}
