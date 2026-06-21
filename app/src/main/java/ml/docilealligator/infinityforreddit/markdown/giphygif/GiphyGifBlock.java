package ml.docilealligator.infinityforreddit.markdown.giphygif;

import ml.docilealligator.infinityforreddit.thing.GiphyGif;
import org.commonmark.node.CustomBlock;

public class GiphyGifBlock extends CustomBlock {
    public GiphyGif giphyGif;

    public GiphyGifBlock(GiphyGif giphyGif) {
        this.giphyGif = giphyGif;
    }
}
