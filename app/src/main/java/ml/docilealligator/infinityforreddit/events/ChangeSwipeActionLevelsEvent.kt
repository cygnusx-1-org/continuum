package ml.docilealligator.infinityforreddit.events

import android.os.Handler
import android.os.Looper
import org.greenrobot.eventbus.EventBus

/**
 * A swipe ladder changed: a level picker, or the switch that lets the ladder past level 1.
 *
 * Carries nothing. The three surfaces that draw a swipe -- post feed, post comments, profile
 * comments -- each read their own ladder back out of settings, which is what keeps one event
 * enough for two screens, two directions and three levels apiece.
 */
class ChangeSwipeActionLevelsEvent {
    companion object {
        /**
         * Posts the event once the preference that changed has actually been stored.
         *
         * A preference's change listener runs *before* the new value is persisted, and the
         * subscribers here re-read the whole ladder rather than being handed it, so posting from
         * inside the listener would hand them the value the user just replaced. Going through the
         * looper puts the read after the write.
         */
        @JvmStatic
        fun postAfterStoring() {
            Handler(Looper.getMainLooper()).post {
                EventBus.getDefault().post(ChangeSwipeActionLevelsEvent())
            }
        }
    }
}
