package ml.docilealligator.infinityforreddit.markdown.giphygif;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import io.noties.markwon.AbstractMarkwonPlugin;
import java.util.List;
import ml.docilealligator.infinityforreddit.thing.GiphyGif;
import ml.docilealligator.infinityforreddit.thing.UploadedImage;
import org.commonmark.parser.Parser;

public class GiphyGifPlugin extends AbstractMarkwonPlugin {
    private final GiphyGifBlockParser.Factory factory;

    public GiphyGifPlugin(@Nullable GiphyGif giphyGif, @Nullable List<UploadedImage> uploadedImages) {
        this.factory = new GiphyGifBlockParser.Factory(giphyGif, uploadedImages);
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
}
