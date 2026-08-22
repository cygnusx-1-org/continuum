package ml.docilealligator.infinityforreddit.utils

import android.content.Intent

/**
 * "Open in New Window" — launching a screen into its own task instead of stacking it on the
 * current one, so it becomes a separate card in Recents that the user can flip back to while the
 * feed carries on where they left it.
 *
 * The flags are the same pair Material Files uses for its New Window action:
 * [Intent.FLAG_ACTIVITY_NEW_DOCUMENT] asks for a task of its own, and
 * [Intent.FLAG_ACTIVITY_MULTIPLE_TASK] stops Android reusing an existing task for the same
 * component, so a second window on the same post is still a second window. NEW_DOCUMENT also makes
 * Android drop the task from Recents once its root activity finishes, so backing out of a window
 * leaves no empty card behind.
 *
 * Nothing in the manifest is needed for this: the activities are launchMode `standard` with no
 * `taskAffinity`, which is exactly what these flags require.
 *
 * Telling the resulting cards apart is left to their thumbnails. `Activity.setTaskDescription` was
 * tried and dropped: Pixel Launcher's card header chip shows the package's own name whatever label
 * is set, and the icon it does honour draws far too small for a subreddit avatar to be
 * recognisable.
 */
object NewWindowUtils {
    /**
     * Marks [intent] as opening a new window. Returns the same intent so it can be built inline.
     */
    @JvmStatic
    fun addNewWindowFlags(intent: Intent): Intent =
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
}
