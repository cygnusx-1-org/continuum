package ml.docilealligator.infinityforreddit.comment

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import ml.docilealligator.infinityforreddit.account.Account

/**
 * A comment saved while logged out. There is no Reddit account to save it to, so this row is the
 * whole save: written and deleted here, never sent to `/api/save`.
 *
 * The post id is kept alongside the comment's own because the Saved screen has to fetch the comment
 * to show it, and `/api/info` is refused on some clients (issue #476). The fallback is
 * the comment's focused thread, `/comments/<post>/_/<comment>`, which needs both.
 */
@Entity(
    tableName = "anonymous_saved_comments",
    primaryKeys = ["username", "id"],
    foreignKeys = [ForeignKey(
        entity = Account::class,
        parentColumns = ["username"],
        childColumns = ["username"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("username")]
)
data class AnonymousSavedComment(
    /** Always [Account.ANONYMOUS_ACCOUNT]; kept as a column so the row hangs off the account. */
    @ColumnInfo(name = "username")
    val username: String,

    /** The comment's id36, without `t1_`. */
    @ColumnInfo(name = "id")
    val id: String,

    /** The id36 of the post the comment is on, without `t3_`. */
    @ColumnInfo(name = "link_id")
    val linkId: String,

    @ColumnInfo(name = "time")
    val time: Long
)
