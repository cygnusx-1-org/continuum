package ml.docilealligator.infinityforreddit.events;

/**
 * Posted after an in-app save/unsave succeeds so the Saved screen drops the affected tabs' in-memory
 * search caches (a search in progress would otherwise keep surfacing a just-unsaved item). {@link
 * #kind} says whether a post or a comment changed, so only the matching tabs are refreshed. The
 * persistent {@code SavedPostCache} (posts only) is dropped separately at the save call site.
 */
public class SavedThingChangedEvent {
    public enum Kind { POST, COMMENT }

    public final Kind kind;

    public SavedThingChangedEvent(Kind kind) {
        this.kind = kind;
    }
}
