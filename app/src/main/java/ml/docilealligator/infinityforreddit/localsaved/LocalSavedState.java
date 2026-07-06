package ml.docilealligator.infinityforreddit.localsaved;

import androidx.annotation.IntDef;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@IntDef({LocalSavedState.INVALID,
        LocalSavedState.PENDING,
        LocalSavedState.PROMOTED})
@Retention(RetentionPolicy.SOURCE)
public @interface LocalSavedState {
    int INVALID = -1;
    // Saved in-app, not yet reconciled against the live /saved listing.
    int PENDING = 0;
    // Confirmed dropped from /saved by Reddit -> shown in the Local Saved tabs.
    int PROMOTED = 1;
}
