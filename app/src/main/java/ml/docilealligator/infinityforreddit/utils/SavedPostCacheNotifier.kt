package ml.docilealligator.infinityforreddit.utils

import ml.docilealligator.infinityforreddit.events.SavedThingChangedEvent
import org.greenrobot.eventbus.EventBus

/**
 * Keeps the Saved posts screen's caches coherent after an in-app post save/unsave. Deliberately a
 * standalone helper -- separate from the `SaveThing` API code and the fork's `LocalSaved` feature,
 * neither of which should own this concern. Call it from a post save/unsave call site once the
 * server change has succeeded: it drops the persistent hard-TTL [SavedPostCache] so the next Saved
 * tab open refetches, and posts a [SavedThingChangedEvent] so an open Saved screen drops its
 * in-memory search cache too.
 *
 * Only post (t3) changes matter here: the Saved posts listing is unaffected by saving a comment.
 */
object SavedPostCacheNotifier {

    @JvmStatic
    fun onSavedPostChanged() {
        SavedPostCache.invalidate()
        EventBus.getDefault().post(SavedThingChangedEvent(SavedThingChangedEvent.Kind.POST))
    }
}
