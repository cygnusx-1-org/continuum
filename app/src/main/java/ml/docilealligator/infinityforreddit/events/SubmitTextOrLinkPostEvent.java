package ml.docilealligator.infinityforreddit.events;

import androidx.annotation.Nullable;
import ml.docilealligator.infinityforreddit.post.Post;

public class SubmitTextOrLinkPostEvent {
    public boolean postSuccess;
    @Nullable
    public Post post;
    @Nullable
    public String errorMessage;

    public SubmitTextOrLinkPostEvent(boolean postSuccess, @Nullable Post post, @Nullable String errorMessage) {
        this.postSuccess = postSuccess;
        this.post = post;
        this.errorMessage = errorMessage;
    }
}
