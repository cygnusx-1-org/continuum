package ml.docilealligator.infinityforreddit.user

import java.util.Locale
import ml.docilealligator.infinityforreddit.subscribeduser.SubscribedUserData

/**
 * Why a user is on the current account's list, in the order the marker beside their name shows
 * them (issue #415).
 *
 * The three reasons are not exclusive — a followed user is very often saved as well, and a
 * favourite is always one or the other — so the marker takes the first that applies rather than
 * stacking glyphs on a name.
 */
enum class UserMark {
    FAVORITED,
    FOLLOWED,
    SAVED,
}

/**
 * Which mark, if any, each user on one account's list carries: an immutable snapshot of the
 * `subscribed_users` rows, keyed by lowercased name because Reddit usernames are case-insensitive
 * and a post's author is spelled however Reddit felt like spelling it.
 *
 * Held by the adapters that draw an author and replaced wholesale when Room emits, which it does
 * on every write to the table. [changedFrom] is what keeps that cheap: a sync that re-writes the
 * same rows changes nothing, and a follow changes one name, so nothing else needs rebinding.
 */
class UserMarks private constructor(private val marks: Map<String, UserMark>) {

    /** The mark for [username], or null when they are on no list — or are not a user at all. */
    fun of(username: String?): UserMark? {
        if (username.isNullOrEmpty()) {
            return null
        }
        return marks[username.lowercase(Locale.ROOT)]
    }

    /** The users whose mark differs between [previous] and this snapshot. */
    fun changedFrom(previous: UserMarks): UserMarkChanges {
        if (previous.marks === marks) {
            return UserMarkChanges.NONE
        }
        val changed = mutableSetOf<String>()
        for ((name, mark) in marks) {
            if (previous.marks[name] != mark) {
                changed.add(name)
            }
        }
        for (name in previous.marks.keys) {
            if (!marks.containsKey(name)) {
                changed.add(name)
            }
        }
        return if (changed.isEmpty()) UserMarkChanges.NONE else UserMarkChanges(changed)
    }

    companion object {
        /** What an adapter holds until Room has answered, and what an empty list produces. */
        @JvmField
        val EMPTY = UserMarks(emptyMap())

        /**
         * The snapshot [rows] describe; see [markOf] for what one row is worth.
         */
        @JvmStatic
        fun from(rows: List<SubscribedUserData>): UserMarks {
            if (rows.isEmpty()) {
                return EMPTY
            }
            val marks = HashMap<String, UserMark>(rows.size)
            for (row in rows) {
                val mark = markOf(row)
                if (mark != null) {
                    marks[row.name.lowercase(Locale.ROOT)] = mark
                }
            }
            return if (marks.isEmpty()) EMPTY else UserMarks(marks)
        }

        /**
         * One row's mark: the highest reason it is on the list, or null when there is no row —
         * and when there is one carrying no reason at all, which is the moment between
         * `insertIfAbsent` and the flag it was created for.
         */
        @JvmStatic
        fun markOf(row: SubscribedUserData?): UserMark? = when {
            row == null -> null
            row.isFavorite -> UserMark.FAVORITED
            row.isFollowed -> UserMark.FOLLOWED
            row.isSaved -> UserMark.SAVED
            else -> null
        }
    }
}

/**
 * The users whose mark just changed, so that an adapter can rebind their rows and leave the rest
 * alone — a blanket rebind restarts an autoplaying video, which a follow made on another screen
 * has no business doing.
 */
class UserMarkChanges internal constructor(private val names: Set<String>) {

    val isEmpty: Boolean get() = names.isEmpty()

    fun affects(username: String?): Boolean =
        !username.isNullOrEmpty() && names.contains(username.lowercase(Locale.ROOT))

    companion object {
        @JvmField
        val NONE = UserMarkChanges(emptySet())
    }
}
