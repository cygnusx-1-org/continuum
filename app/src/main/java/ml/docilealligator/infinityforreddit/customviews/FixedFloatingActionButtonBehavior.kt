package ml.docilealligator.infinityforreddit.customviews

import android.content.Context
import android.util.AttributeSet
import androidx.coordinatorlayout.widget.CoordinatorLayout
import com.google.android.material.floatingactionbutton.FloatingActionButton

/**
 * A floating action button that stays where the layout puts it when a Snackbar comes and goes.
 *
 * [FloatingActionButton.Behavior] forces `dodgeInsetEdges = BOTTOM` in
 * [onAttachedToLayoutParams] whenever the layout leaves it unset, and CoordinatorLayout then offsets
 * the button upwards for the duration of every Snackbar. Setting
 * `app:layout_dodgeInsetEdges="none"` in XML does not help, because "none" is the same zero the
 * behavior treats as "unset" and overwrites.
 *
 * So this overrides that one method and leaves the value alone. Everything else — the auto-hide on
 * an AppBarLayout, the bottom-sheet handling — is inherited unchanged. A Snackbar now passes in
 * front of the button instead of pushing it around.
 */
class FixedFloatingActionButtonBehavior(context: Context, attrs: AttributeSet?) :
    FloatingActionButton.Behavior(context, attrs) {

    override fun onAttachedToLayoutParams(lp: CoordinatorLayout.LayoutParams) {
        // Deliberately does not call super: super's only job here is the dodge default this exists
        // to suppress.
    }
}
