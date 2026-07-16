package ml.docilealligator.infinityforreddit.events;

import androidx.annotation.Nullable;

public class SubmitGalleryPostEvent {
    public boolean postSuccess;
    @Nullable
    public String postUrl;
    @Nullable
    public String errorMessage;

    public SubmitGalleryPostEvent(boolean postSuccess, @Nullable String postUrl, @Nullable String errorMessage) {
        this.postSuccess = postSuccess;
        this.postUrl = postUrl;
        this.errorMessage = errorMessage;
    }
}
