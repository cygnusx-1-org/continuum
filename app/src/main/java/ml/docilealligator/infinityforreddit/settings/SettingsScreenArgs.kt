package ml.docilealligator.infinityforreddit.settings

import android.os.Bundle
import androidx.fragment.app.Fragment

/**
 * The fragment arguments a settings screen can be opened with.
 *
 * Both outlive the navigation that set them -- arguments survive going back and configuration
 * changes -- which is the point: the toolbar title stays correct on the way back without the
 * activity re-deriving it from the fragment's type, and the scroll target survives the fragment
 * being recreated before its list exists.
 */
object SettingsScreenArgs {

    private const val ARG_SCREEN_TITLE = "ASCT"
    private const val ARG_SCROLL_TO_KEY = "ASTK"

    /** Records the toolbar title for the screen [args] will open. */
    @JvmStatic
    fun putScreenTitle(args: Bundle, title: CharSequence?) {
        args.putCharSequence(ARG_SCREEN_TITLE, title)
    }

    /** The toolbar title a screen was opened with, or null if it was opened without one. */
    @JvmStatic
    fun screenTitle(fragment: Fragment?): CharSequence? =
        fragment?.arguments?.getCharSequence(ARG_SCREEN_TITLE)

    /**
     * Arguments asking the opened screen to scroll to [key]. Copies [base] rather than writing
     * through to it, since callers pass bundles owned by something else.
     */
    @JvmStatic
    fun withScrollTarget(base: Bundle?, key: String?): Bundle =
        (if (base == null) Bundle() else Bundle(base)).apply {
            if (key != null) putString(ARG_SCROLL_TO_KEY, key)
        }

    /**
     * Reads the scroll target and clears it, so coming back to the screen -- or rotating it --
     * does not scroll all over again.
     */
    @JvmStatic
    fun takeScrollTarget(args: Bundle?): String? {
        if (args == null) return null
        return args.getString(ARG_SCROLL_TO_KEY).also { args.remove(ARG_SCROLL_TO_KEY) }
    }
}
