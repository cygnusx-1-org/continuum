package ml.docilealligator.infinityforreddit.utils

import android.view.View
import android.widget.TextView
import com.google.android.material.snackbar.Snackbar

/**
 * A Snackbar that shows the whole message.
 *
 * Both a Toast and a stock Snackbar clip at two lines, which is fine for "Rule removed" and useless
 * for a refusal: the sentence that says *why* is the only thing the user has to act on, and it is
 * the part that gets cut. Nothing else about the Snackbar changes.
 */
object Snackbars {

    private const val REFUSAL_MAX_LINES = 5

    @JvmStatic
    fun showMultiline(view: View, message: CharSequence) {
        val snackbar = Snackbar.make(view, message, Snackbar.LENGTH_LONG)
        snackbar.view.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)
            ?.maxLines = REFUSAL_MAX_LINES
        snackbar.show()
    }
}
