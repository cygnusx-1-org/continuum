package ml.docilealligator.infinityforreddit.utils

import ml.docilealligator.infinityforreddit.events.SavedThingChangedEvent
import org.greenrobot.eventbus.EventBus

/**
 * Keeps the Saved comments tabs' in-memory search caches coherent after an in-app comment
 * save/unsave. Comments have no persistent cache (unlike posts' [SavedPostCache]), so this only posts
 * a [SavedThingChangedEvent] of kind COMMENT; an open Saved screen drops the server- and local-saved
 * comments tabs' in-memory search caches in response. Call it from a comment save/unsave call site
 * once the server change has succeeded.
 *
 * Only comment (t1) changes matter here: the Saved comments listings are unaffected by saving a post.
 */
object SavedCommentCacheNotifier {

    @JvmStatic
    fun onSavedCommentChanged() {
        EventBus.getDefault().post(SavedThingChangedEvent(SavedThingChangedEvent.Kind.COMMENT))
    }
}
