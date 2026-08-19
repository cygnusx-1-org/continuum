package ml.docilealligator.infinityforreddit.postfilter

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey

/**
 * One subreddit that a wildcard "Exclude subreddits" rule has hidden posts from.
 *
 * Rows are discovered, never authored: nobody could write r/Hairloss down in advance as something
 * an `*irl*` rule would hide. They exist so the Post Filter screen can show a broad rule's real
 * reach next to the rule itself, and so a single wrong match can be corrected with [excepted]
 * instead of forcing the user to delete a rule that is mostly doing what they wanted.
 *
 * Keyed by rule rather than by subreddit alone, because the same subreddit can be caught by two
 * different rules and each needs its own count and its own exception.
 */
@Entity(
    tableName = "post_filter_blocked_subreddit",
    primaryKeys = ["filter_name", "rule_value", "subreddit_name"],
    foreignKeys = [
        ForeignKey(
            entity = PostFilter::class,
            parentColumns = ["name"],
            childColumns = ["filter_name"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class PostFilterBlockedSubreddit(
    /** Name of the [PostFilter] that owns the rule. Cascades, so deleting a filter clears its rows. */
    @ColumnInfo(name = "filter_name")
    val filterName: String,

    /** The term exactly as stored in the filter's column, asterisks included (e.g. `*irl*`). */
    @ColumnInfo(name = "rule_value")
    val ruleValue: String,

    /** Lower-cased, so a subreddit seen as "Hairloss" and "hairloss" is one row. */
    @ColumnInfo(name = "subreddit_name")
    val subredditName: String,

    /** When this rule first hid a post from this subreddit. */
    @ColumnInfo(name = "first_blocked")
    val firstBlocked: Long,

    /** How many posts this rule has hidden from this subreddit. */
    @ColumnInfo(name = "block_count")
    val blockCount: Int,

    /**
     * When true the rule stops hiding this subreddit. This is the fix for a rule that is right in
     * general and wrong in one place — excepting r/EarthPorn from a `*porn*` rule leaves it hiding
     * r/CumPorn.
     */
    @ColumnInfo(name = "excepted")
    val excepted: Boolean = false,
)
