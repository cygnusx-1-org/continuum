package ml.docilealligator.infinityforreddit.markdown.imageandgif;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import io.noties.markwon.AbstractMarkwonPlugin;
import java.util.Map;
import ml.docilealligator.infinityforreddit.thing.MediaMetadata;
import org.commonmark.parser.Parser;

public class ImageAndGifPlugin extends AbstractMarkwonPlugin {

    private final ImageAndGifBlockParser.Factory factory;

    public ImageAndGifPlugin() {
        this.factory = new ImageAndGifBlockParser.Factory();
    }

    @NonNull
    @Override
    public String processMarkdown(@NonNull String markdown) {
        return super.processMarkdown(markdown);
    }

    @Override
    public void configureParser(@NonNull Parser.Builder builder) {
        builder.customBlockParserFactory(factory);
    }

    public void setMediaMetadataMap(@Nullable Map<String, MediaMetadata> mediaMetadataMap) {
        factory.setMediaMetadataMap(mediaMetadataMap);
    }
}
