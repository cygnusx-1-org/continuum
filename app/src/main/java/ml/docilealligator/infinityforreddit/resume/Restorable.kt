package ml.docilealligator.infinityforreddit.resume

import android.os.Bundle

/**
 * A screen that can say where it was and be put back there.
 *
 * Implemented by every activity worth resuming onto. The two halves are not symmetrical in when
 * they run: [saveResumeState] is called while the screen is still alive, on the way out;
 * [restoreResumeState] is called on the way in, from either the activity's own `onCreate` (for a
 * screen that needs the state before it builds anything, such as a feed choosing which tab to open)
 * or from `BaseActivity` afterwards for everything else.
 */
interface Restorable {

    /**
     * Write what this screen needs to come back. Leaving [out] empty means "nothing to say", which
     * is not the same as "nothing worth remembering": an empty bundle never overwrites state
     * already recorded, so a screen that is asked too early does not erase where the user was.
     *
     * Only primitives, strings and string lists survive the round trip -- see [ResumeState].
     */
    fun saveResumeState(out: Bundle)

    /** Put this screen back where [state] says it was. */
    fun restoreResumeState(state: Bundle)
}
