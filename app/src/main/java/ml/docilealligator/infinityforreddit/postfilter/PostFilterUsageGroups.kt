package ml.docilealligator.infinityforreddit.postfilter

/**
 * The four feeds a filter is almost always meant for, handled as one choice.
 *
 * `post_filter_usage` is keyed on the usage type, and `PostFilterDao.getValidPostFilters` matches
 * `usage` exactly, so a blank name only ever widens the type it was entered under: "Subreddit" with
 * no name covers every subreddit and still not Home. Wanting all four meant knowing that and adding
 * four entries by hand.
 *
 * They are still stored as four rows — there is no usage value meaning "any" for the query to match
 * — but they are offered and shown as one.
 */
object PostFilterUsageGroups {

    /** Not a stored usage. What the "Add feed" sheet reports when the grouped row is tapped. */
    const val ALL_FEEDS_TYPE = 0

    /** The four rows the group stands for, in the order their chips would otherwise appear. */
    @JvmField
    val ALL_FEEDS_TYPES = listOf(
        PostFilterUsage.HOME_TYPE,
        PostFilterUsage.SUBREDDIT_TYPE,
        PostFilterUsage.USER_TYPE,
        PostFilterUsage.MULTIREDDIT_TYPE,
    )

    /** True for a usage the group covers: one of the four types, with no name narrowing it. */
    @JvmStatic
    fun isAllFeedsMember(usage: PostFilterUsage): Boolean =
        usage.usage in ALL_FEEDS_TYPES && usage.nameOfUsage == PostFilterUsage.NO_USAGE

    /**
     * True when every one of the four is present, however they were added — a filter that collected
     * them one at a time reads the same as one that took the grouped row.
     */
    @JvmStatic
    fun coversAllFeeds(usages: List<PostFilterUsage>): Boolean =
        ALL_FEEDS_TYPES.all { type ->
            usages.any { it.usage == type && it.nameOfUsage == PostFilterUsage.NO_USAGE }
        }
}
