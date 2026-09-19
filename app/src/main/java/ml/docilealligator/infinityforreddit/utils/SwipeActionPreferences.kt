package ml.docilealligator.infinityforreddit.utils

import android.content.SharedPreferences

/**
 * Reads the swipe ladders out of settings, in one place, for the three surfaces that draw them.
 *
 * Every level is stored as a string, like the rest of the numeric settings, and an id that is not
 * on this surface's list is read as empty rather than carried into the drawing code -- a restored
 * backup from a build with a different list must not be able to ask for a colour that does not
 * exist.
 */
object SwipeActionPreferences {

    /** Every level past the first starts empty, which is what keeps the ladder out of the way. */
    private const val EMPTY = "-1"

    /** The two actions that mean the same thing on the post list and the comment list. */
    private const val VOTE_UP = "0"
    private const val VOTE_DOWN = "1"

    private val POST_ACTIONS = setOf(
        SharedPreferencesUtils.SWIPE_ACITON_UPVOTE,
        SharedPreferencesUtils.SWIPE_ACITON_DOWNVOTE,
        SharedPreferencesUtils.SWIPE_ACITON_SAVE,
        SharedPreferencesUtils.SWIPE_ACITON_HIDE,
        SharedPreferencesUtils.SWIPE_ACITON_MARK_AS_READ,
        SharedPreferencesUtils.SWIPE_ACITON_MARK_AS_UNREAD,
        SharedPreferencesUtils.SWIPE_ACITON_TOGGLE_READ,
        SharedPreferencesUtils.SWIPE_ACITON_SHARE,
        SharedPreferencesUtils.SWIPE_ACITON_PROFILE,
        SharedPreferencesUtils.SWIPE_ACITON_COMMENT,
        SharedPreferencesUtils.SWIPE_ACITON_MARK_AS_READ_AND_HIDE,
        SharedPreferencesUtils.SWIPE_ACITON_OPEN_IN_NEW_WINDOW,
        SharedPreferencesUtils.SWIPE_ACITON_CROSSPOST,
    )

    private val COMMENT_ACTIONS = setOf(
        SharedPreferencesUtils.COMMENT_SWIPE_ACITON_UPVOTE,
        SharedPreferencesUtils.COMMENT_SWIPE_ACITON_DOWNVOTE,
        SharedPreferencesUtils.COMMENT_SWIPE_ACITON_SAVE,
        SharedPreferencesUtils.COMMENT_SWIPE_ACITON_REPLY,
        SharedPreferencesUtils.COMMENT_SWIPE_ACITON_SHARE,
        SharedPreferencesUtils.COMMENT_SWIPE_ACITON_PROFILE,
        SharedPreferencesUtils.COMMENT_SWIPE_ACITON_SHARE_AS_IMAGE,
        SharedPreferencesUtils.COMMENT_SWIPE_ACITON_SHARE_AS_IMAGE_WITH_THREAD,
        SharedPreferencesUtils.COMMENT_SWIPE_ACITON_SET_REMINDER,
    )

    /**
     * Whether comments swipe at all. Their own switch now, falling back to the post one, which
     * used to turn all three surfaces on together -- so someone who had swiping on keeps it in
     * comments until they say otherwise.
     */
    @JvmStatic
    fun commentSwipeEnabled(preferences: SharedPreferences): Boolean =
        preferences.getBoolean(SharedPreferencesUtils.ENABLE_COMMENT_SWIPE_ACTION,
            preferences.getBoolean(SharedPreferencesUtils.ENABLE_SWIPE_ACTION, false))

    @JvmStatic
    fun threshold(preferences: SharedPreferences): Float =
        SharedPreferencesUtils.getFloat(preferences, SharedPreferencesUtils.SWIPE_ACTION_THRESHOLD, "0.3")

    /**
     * Swipe actions and Swipe Between Posts both consume a horizontal swipe on a post, so only one
     * of them can be on. These are the two halves of that: switching either swipe-action switch on
     * turns Swipe Between Posts off, and switching Swipe Between Posts on turns both of them off.
     */
    @JvmStatic
    fun turnOffSwipeBetweenPosts(preferences: SharedPreferences) {
        preferences.edit().putBoolean(SharedPreferencesUtils.SWIPE_BETWEEN_POSTS, false).apply()
    }

    @JvmStatic
    fun turnOffSwipeActions(preferences: SharedPreferences) {
        preferences.edit()
            .putBoolean(SharedPreferencesUtils.ENABLE_SWIPE_ACTION, false)
            .putBoolean(SharedPreferencesUtils.ENABLE_COMMENT_SWIPE_ACTION, false)
            .apply()
    }

    /** The four post actions of a left swipe, deepest levels empty unless the user filled them. */
    @JvmStatic
    fun postLeftLevels(preferences: SharedPreferences): IntArray = intArrayOf(
        post(preferences, SharedPreferencesUtils.SWIPE_LEFT_ACTION, "0"),
        post(preferences, SharedPreferencesUtils.SWIPE_LEFT_ACTION_LEVEL_2, EMPTY),
        post(preferences, SharedPreferencesUtils.SWIPE_LEFT_ACTION_LEVEL_3, EMPTY),
        post(preferences, SharedPreferencesUtils.SWIPE_LEFT_ACTION_LEVEL_4, EMPTY),
    )

    @JvmStatic
    fun postRightLevels(preferences: SharedPreferences): IntArray = intArrayOf(
        post(preferences, SharedPreferencesUtils.SWIPE_RIGHT_ACTION, "1"),
        post(preferences, SharedPreferencesUtils.SWIPE_RIGHT_ACTION_LEVEL_2, EMPTY),
        post(preferences, SharedPreferencesUtils.SWIPE_RIGHT_ACTION_LEVEL_3, EMPTY),
        post(preferences, SharedPreferencesUtils.SWIPE_RIGHT_ACTION_LEVEL_4, EMPTY),
    )

    @JvmStatic
    fun commentLeftLevels(preferences: SharedPreferences): IntArray = intArrayOf(
        comment(preferences, SharedPreferencesUtils.COMMENT_SWIPE_LEFT_ACTION,
            seededLevel1(preferences, SharedPreferencesUtils.SWIPE_LEFT_ACTION, "0")),
        comment(preferences, SharedPreferencesUtils.COMMENT_SWIPE_LEFT_ACTION_LEVEL_2, EMPTY),
        comment(preferences, SharedPreferencesUtils.COMMENT_SWIPE_LEFT_ACTION_LEVEL_3, EMPTY),
    )

    @JvmStatic
    fun commentRightLevels(preferences: SharedPreferences): IntArray = intArrayOf(
        comment(preferences, SharedPreferencesUtils.COMMENT_SWIPE_RIGHT_ACTION,
            seededLevel1(preferences, SharedPreferencesUtils.SWIPE_RIGHT_ACTION, "1")),
        comment(preferences, SharedPreferencesUtils.COMMENT_SWIPE_RIGHT_ACTION_LEVEL_2, EMPTY),
        comment(preferences, SharedPreferencesUtils.COMMENT_SWIPE_RIGHT_ACTION_LEVEL_3, EMPTY),
    )

    /**
     * What a comment's first level falls back to before the user has ever opened the new screen:
     * whatever the single shared setting said, since comments used to read it.
     *
     * Only upvote and downvote carry across. They are 0 and 1 on both lists, and until this change
     * they were the only two values the shared setting could hold -- but it can hold Hide now, and
     * 3 means Reply on the comment list, so anything else falls back to this list's own default
     * rather than being read as the action that happens to share its number.
     */
    @JvmStatic
    fun seededLevel1(preferences: SharedPreferences, postKey: String, fallback: String): String {
        val shared = preferences.getString(postKey, fallback) ?: fallback
        return if (shared == VOTE_UP || shared == VOTE_DOWN) shared else fallback
    }

    private fun post(preferences: SharedPreferences, key: String, default: String): Int =
        validate(SharedPreferencesUtils.getInt(preferences, key, default), POST_ACTIONS)

    private fun comment(preferences: SharedPreferences, key: String, default: String): Int =
        validate(SharedPreferencesUtils.getInt(preferences, key, default), COMMENT_ACTIONS)

    private fun validate(action: Int, allowed: Set<Int>): Int =
        if (action in allowed) action else SwipeActionLevels.NONE
}
