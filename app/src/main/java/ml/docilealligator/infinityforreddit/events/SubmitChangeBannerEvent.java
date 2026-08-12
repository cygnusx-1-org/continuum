package ml.docilealligator.infinityforreddit.events;

import androidx.annotation.Nullable;

public class SubmitChangeBannerEvent {
    public final boolean isSuccess;
    @Nullable
    public final String errorMessage;

    public SubmitChangeBannerEvent(boolean isSuccess, @Nullable String errorMessage) {
        this.isSuccess = isSuccess;
        this.errorMessage = errorMessage;
    }
}
