package ml.docilealligator.infinityforreddit.utils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Holds the full, unfiltered listing loaded by a Saved-screen search so that refining the query
 * filters the already-loaded items in memory instead of re-walking the whole listing over the
 * network again. The ViewModel owns one instance and invalidates it whenever the underlying data
 * must be refetched (sort/filter change, pull-to-refresh, or the search being cleared). Individual
 * save/unsave/vote actions are not tracked here — same as the normal listing, which only reflects
 * such changes after a refresh.
 *
 * <p>Accessed from the paging executor across successive source generations (an outgoing generation
 * may still be finishing as the next one starts), so every accessor is synchronized and returns a
 * defensive copy.
 */
public final class SavedSearchCache<T> {

    @Nullable
    private List<T> items;

    public synchronized boolean isValid() {
        return items != null;
    }

    @Nullable
    public synchronized List<T> snapshot() {
        return items == null ? null : new ArrayList<>(items);
    }

    public synchronized void set(@NonNull List<T> newItems) {
        items = new ArrayList<>(newItems);
    }

    public synchronized void invalidate() {
        items = null;
    }
}
