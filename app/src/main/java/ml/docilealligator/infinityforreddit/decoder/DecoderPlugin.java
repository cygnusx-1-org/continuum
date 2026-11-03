package ml.docilealligator.infinityforreddit.decoder;

import androidx.annotation.NonNull;

import org.commonmark.parser.Parser;

import io.noties.markwon.AbstractMarkwonPlugin;

public class DecoderPlugin extends AbstractMarkwonPlugin {
    @Override
    public void configureParser(@NonNull Parser.Builder builder) {
        builder.customBlockParserFactory(new DecoderBlockParser.Factory());
    }
}
