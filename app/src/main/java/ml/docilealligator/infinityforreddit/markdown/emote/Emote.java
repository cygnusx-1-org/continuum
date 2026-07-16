package ml.docilealligator.infinityforreddit.markdown.emote;

import androidx.annotation.Nullable;
import ml.docilealligator.infinityforreddit.thing.MediaMetadata;
import org.commonmark.node.CustomNode;
import org.commonmark.node.Visitor;

public class Emote extends CustomNode {
    private final MediaMetadata mediaMetadata;
    @Nullable
    private final String title;

    public Emote(MediaMetadata mediaMetadata, @Nullable String title) {
        this.mediaMetadata = mediaMetadata;
        this.title = title;
    }

    @Override
    public void accept(Visitor visitor) {
        visitor.visit(this);
    }

    public MediaMetadata getMediaMetadata() {
        return mediaMetadata;
    }

    @Nullable
    public String getTitle() {
        return title;
    }

    @Override
    protected String toStringAttributes() {
        return "destination=" + mediaMetadata.original.url + ", title=" + title;
    }
}
