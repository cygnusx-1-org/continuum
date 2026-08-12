package ml.docilealligator.infinityforreddit.events;

import androidx.annotation.Nullable;

public class SubmitVideoOrGifPostEvent {
    public boolean postSuccess;
    public boolean errorProcessingVideoOrGif;
    @Nullable
    public String errorMessage;

    public SubmitVideoOrGifPostEvent(boolean postSuccess, boolean errorProcessingVideoOrGif, @Nullable String errorMessage) {
        this.postSuccess = postSuccess;
        this.errorProcessingVideoOrGif = errorProcessingVideoOrGif;
        this.errorMessage = errorMessage;
    }
}
