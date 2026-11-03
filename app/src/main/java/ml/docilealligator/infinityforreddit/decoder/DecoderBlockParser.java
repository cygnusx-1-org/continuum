package ml.docilealligator.infinityforreddit.decoder;

import org.commonmark.node.Block;
import org.commonmark.parser.block.AbstractBlockParser;
import org.commonmark.parser.block.AbstractBlockParserFactory;
import org.commonmark.parser.block.BlockContinue;
import org.commonmark.parser.block.BlockStart;
import org.commonmark.parser.block.MatchedBlockParser;
import org.commonmark.parser.block.ParserState;

/**
 * Turns the marker line CommentDecoder.annotate adds into a DecoderBlock.
 */
public class DecoderBlockParser extends AbstractBlockParser {
    private final DecoderBlock block;

    private DecoderBlockParser(DecoderBlock block) {
        this.block = block;
    }

    @Override
    public Block getBlock() {
        return block;
    }

    @Override
    public BlockContinue tryContinue(ParserState parserState) {
        return null;
    }

    public static class Factory extends AbstractBlockParserFactory {
        @Override
        public BlockStart tryStart(ParserState state, MatchedBlockParser matchedBlockParser) {
            String[] marker = CommentDecoder.parseMarker(state.getLine().toString());
            if (marker == null) return BlockStart.none();
            return BlockStart.of(new DecoderBlockParser(new DecoderBlock(marker[0], marker[1])));
        }
    }
}
