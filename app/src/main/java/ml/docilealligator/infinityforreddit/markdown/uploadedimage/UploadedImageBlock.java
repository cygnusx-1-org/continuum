package ml.docilealligator.infinityforreddit.markdown.uploadedimage;

import ml.docilealligator.infinityforreddit.thing.UploadedImage;
import org.commonmark.node.CustomBlock;

public class UploadedImageBlock extends CustomBlock {
    public UploadedImage uploadeImage;

    public UploadedImageBlock(UploadedImage uploadeImage) {
        this.uploadeImage = uploadeImage;
    }
}
