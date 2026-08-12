package ml.docilealligator.infinityforreddit.shadows

import android.view.Display
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowContextImpl
import org.robolectric.shadows.ShadowDisplay

/**
 * Robolectric's ContextImpl is not display-associated, so `Context.getDisplay()` (API 30+) throws.
 * Every BaseActivity reads the display on create to log the refresh rate, so hand it the default
 * display instead -- a test seam only, no bearing on production behaviour.
 */
@Implements(className = "android.app.ContextImpl")
class ShadowContextImplWithDisplay : ShadowContextImpl() {

    @Implementation
    fun getDisplay(): Display = ShadowDisplay.getDefaultDisplay()
}
