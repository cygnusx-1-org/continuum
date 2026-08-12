package ml.docilealligator.infinityforreddit.events;

import androidx.annotation.Nullable;
import ml.docilealligator.infinityforreddit.post.Post;

public class SubmitCrosspostEvent {
    public boolean postSuccess;
    @Nullable
    public Post post;
    @Nullable
    public String errorMessage;

    public SubmitCrosspostEvent(boolean postSuccess, @Nullable Post post, @Nullable String errorMessage) {
        this.postSuccess = postSuccess;
        this.post = post;
        this.errorMessage = errorMessage;
    }
}
