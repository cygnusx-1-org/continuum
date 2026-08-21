package ml.docilealligator.infinityforreddit.recentlyvisited

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import ml.docilealligator.infinityforreddit.account.Account

/**
 * A subreddit or user the account has opened. Rows are written when the thing's data finishes
 * loading in ViewSubredditDetailActivity / ViewUserDetailActivity, and only when the account is
 * neither subscribed to it nor following/saving it -- the exclusion happens at record time, so
 * anything already in `subscribed_subreddits` / `subscribed_users` never enters this table.
 */
@Entity(
    tableName = "recently_visited",
    primaryKeys = ["username", "name", "type"],
    foreignKeys = [ForeignKey(
        entity = Account::class,
        parentColumns = ["username"],
        childColumns = ["username"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("username", "type", "last_visited")]
)
data class RecentlyVisited(
    /** The account this visit belongs to; [Account.ANONYMOUS_ACCOUNT] when logged out. */
    @ColumnInfo(name = "username")
    val username: String,

    /** Subreddit name without the `r/` prefix, or the username without `u/`. */
    @ColumnInfo(name = "name")
    val name: String,

    @param:RecentlyVisitedType
    @ColumnInfo(name = "type")
    val type: Int,

    @ColumnInfo(name = "icon_url")
    val iconUrl: String?,

    @ColumnInfo(name = "last_visited")
    val lastVisited: Long
)
