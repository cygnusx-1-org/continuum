package ml.docilealligator.infinityforreddit.markdown.imageandgif;

import ml.docilealligator.infinityforreddit.thing.MediaMetadata;
import org.commonmark.node.CustomBlock;

public class ImageAndGifBlock extends CustomBlock {
    public MediaMetadata mediaMetadata;

    public ImageAndGifBlock(MediaMetadata mediaMetadata) {
        this.mediaMetadata = mediaMetadata;
    }
}
