package ml.docilealligator.infinityforreddit.utils

import android.annotation.SuppressLint
import android.content.SharedPreferences
import ml.docilealligator.infinityforreddit.account.AccountScope

/**
 * Swaps every stored swipe action from one side of the row to the other, once.
 *
 * `swipe_left_action` used to mean the action a swipe *to the left* ran. That swipe drags the row
 * left and uncovers its right edge, so the action's band and icon appeared on the right -- the
 * settings said "Left" and the screen showed it on the right. The keys now name the side the
 * action shows on, which is the opposite of what every existing value meant; swapping the values
 * between each left/right pair is what keeps every user's swipes doing what they did.
 *
 * The marker that records the swap lives in the default preferences file rather than the internal
 * one, because that file is what a backup carries. A backup taken before this change holds values
 * in the old sense and no marker, whichever build restores it, and [flipRestoredKeys] uses that to
 * put only the restored keys right; the device's own keys were swapped when it upgraded.
 */
object SwipeActionSideMigration {

    /** Every left key, each beside the right key it is paired with. Scoped or not, per account. */
    private val PAIRS: List<Pair<String, String>> = listOf(
        SharedPreferencesUtils.SWIPE_LEFT_ACTION to SharedPreferencesUtils.SWIPE_RIGHT_ACTION,
        SharedPreferencesUtils.SWIPE_LEFT_ACTION_LEVEL_2 to SharedPreferencesUtils.SWIPE_RIGHT_ACTION_LEVEL_2,
        SharedPreferencesUtils.SWIPE_LEFT_ACTION_LEVEL_3 to SharedPreferencesUtils.SWIPE_RIGHT_ACTION_LEVEL_3,
        SharedPreferencesUtils.SWIPE_LEFT_ACTION_LEVEL_4 to SharedPreferencesUtils.SWIPE_RIGHT_ACTION_LEVEL_4,
        SharedPreferencesUtils.COMMENT_SWIPE_LEFT_ACTION to SharedPreferencesUtils.COMMENT_SWIPE_RIGHT_ACTION,
        SharedPreferencesUtils.COMMENT_SWIPE_LEFT_ACTION_LEVEL_2 to SharedPreferencesUtils.COMMENT_SWIPE_RIGHT_ACTION_LEVEL_2,
        SharedPreferencesUtils.COMMENT_SWIPE_LEFT_ACTION_LEVEL_3 to SharedPreferencesUtils.COMMENT_SWIPE_RIGHT_ACTION_LEVEL_3,
    )

    /** The partner of every base that has one: left to right and right to left. */
    private val PARTNER: Map<String, String> = buildMap {
        for ((left, right) in PAIRS) {
            put(left, right)
            put(right, left)
        }
    }

    /** Whether [defaultSharedPreferences] already holds its swipe actions by side. */
    @JvmStatic
    fun isDone(defaultSharedPreferences: SharedPreferences): Boolean =
        defaultSharedPreferences.contains(SharedPreferencesUtils.SWIPE_ACTION_SIDES_MIGRATED)

    /**
     * Swaps every left/right pair in the file and marks it done. Takes the raw default preferences,
     * not the account façade: every account's keys are in the file and all of them turn over,
     * the inert pre-account copy included, so a seeding that has yet to run copies swapped values.
     *
     * Cheap enough to run on the main thread at start-up, which is where it has to run: the first
     * feed reads these keys as soon as it comes up, and an `apply()` shows in every later read.
     */
    @JvmStatic
    fun migrate(defaultSharedPreferences: SharedPreferences) {
        if (isDone(defaultSharedPreferences)) {
            // Already by side, or a fresh install that never held the old sense. The common path.
            return
        }
        val existing = defaultSharedPreferences.all
        val editor = defaultSharedPreferences.edit()
        swap(editor, existing, existing.keys)
        editor.putBoolean(SharedPreferencesUtils.SWIPE_ACTION_SIDES_MIGRATED, true)
        editor.apply()
    }

    /**
     * Puts right the swipe keys a restore has just merged in from a backup that predates the swap.
     *
     * A restore adds the backup's keys to the file without clearing it, so afterwards the file is a
     * mix: the device's own keys, already by side, and the backup's, in the old sense. Only the
     * backup's are turned over -- the ones in [restoredDefaultPreferences], the map the restore
     * imported -- and a backup that carries the marker is left alone, since it was taken by side.
     *
     * Runs on the restore's executor, and the restore ends by killing the process, so this commits.
     */
    @SuppressLint("ApplySharedPref")
    @JvmStatic
    fun flipRestoredKeys(
        defaultSharedPreferences: SharedPreferences,
        restoredDefaultPreferences: Map<String, *>?,
    ) {
        if (restoredDefaultPreferences == null ||
            restoredDefaultPreferences.containsKey(SharedPreferencesUtils.SWIPE_ACTION_SIDES_MIGRATED)
        ) {
            return
        }
        val editor = defaultSharedPreferences.edit()
        if (!swap(editor, defaultSharedPreferences.all, restoredDefaultPreferences.keys)) {
            return
        }
        // The restored file predates the swap, but the device's has now been brought up to date --
        // whichever way round the marker was, this file is by side from here on.
        editor.putBoolean(SharedPreferencesUtils.SWIPE_ACTION_SIDES_MIGRATED, true)
        editor.commit()
    }

    /**
     * Swaps the values of every left/right pair that [keys] touches, reading them from [existing],
     * and returns whether anything was written. A pair is turned over once, whichever of its two
     * keys came first.
     *
     * A value only crosses over if its own key is in [keys]; a side that is not moves to whatever
     * the other side sends it, and to nothing when the other side sends nothing. For the whole
     * file that is just "a one-sided setting stays one-sided, on the other side". For a restore it
     * is what keeps the backup authoritative: a backup holding only a left action is holding a
     * right-side action by the new naming, so it lands on the right and the left goes back to its
     * default -- rather than the left inheriting whatever this device happened to have on the
     * right, which is a side the backup never configured at all.
     */
    private fun swap(
        editor: SharedPreferences.Editor,
        existing: Map<String, *>,
        keys: Set<String>,
    ): Boolean {
        val done = HashSet<String>()
        var changed = false
        for (key in keys) {
            val partner = partnerOf(key) ?: continue
            if (!done.add(key) || !done.add(partner)) {
                continue
            }
            // [key] is one of [keys] by construction; its partner may not be.
            val keyValue = existing[key]
            val partnerValue = if (partner in keys) existing[partner] else null
            if (keyValue == null && partnerValue == null) {
                continue
            }
            put(editor, key, partnerValue)
            put(editor, partner, keyValue)
            changed = true
        }
        return changed
    }

    /** The other side's key for [key], scoped the same way, or null for a key that is not a side. */
    private fun partnerOf(key: String): String? {
        // An unscoped key is its own base: the inert pre-account copy, or a test's plain key.
        val base = AccountScope.baseOf(key) ?: key
        val partnerBase = PARTNER[base] ?: return null
        return key.substring(0, key.length - base.length) + partnerBase
    }

    /** Writes [value] under [key] whatever its type, or clears the key for a null. */
    private fun put(editor: SharedPreferences.Editor, key: String, value: Any?) {
        when (value) {
            null -> editor.remove(key)
            is String -> editor.putString(key, value)
            is Int -> editor.putInt(key, value)
            is Boolean -> editor.putBoolean(key, value)
            is Long -> editor.putLong(key, value)
            is Float -> editor.putFloat(key, value)
            // Not a type a swipe key can hold; leave it, rather than guess.
            else -> Unit
        }
    }
}
