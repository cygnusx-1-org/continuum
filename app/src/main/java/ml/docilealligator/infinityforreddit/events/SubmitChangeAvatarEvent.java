package ml.docilealligator.infinityforreddit.events;

import androidx.annotation.Nullable;

public class SubmitChangeAvatarEvent {
    public final boolean isSuccess;
    @Nullable
    public final String errorMessage;

    public SubmitChangeAvatarEvent(boolean isSuccess, @Nullable String errorMessage) {
        this.isSuccess = isSuccess;
        this.errorMessage = errorMessage;
    }
}
