package ml.docilealligator.infinityforreddit.decoder;

import org.commonmark.node.CustomBlock;

/**
 * A decoded binary or base64 result, shown as a card with Copy and search buttons.
 */
public class DecoderBlock extends CustomBlock {
    public final String label;
    public final String text;

    public DecoderBlock(String label, String text) {
        this.label = label;
        this.text = text;
    }
}
