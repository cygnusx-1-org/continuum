package ml.docilealligator.infinityforreddit.events;

import androidx.annotation.Nullable;

public class SubmitImagePostEvent {
    public boolean postSuccess;
    @Nullable
    public String errorMessage;

    public SubmitImagePostEvent(boolean postSuccess, @Nullable String errorMessage) {
        this.postSuccess = postSuccess;
        this.errorMessage = errorMessage;
    }
}
