package ml.docilealligator.infinityforreddit.decoder;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.widget.Toast;

import ml.docilealligator.infinityforreddit.R;

/**
 * Copies decoded text for the decoder card's Copy button.
 */
public final class DecoderLinkHandler {
    private DecoderLinkHandler() {
        // Prevent instantiation
    }

    public static void copy(Context context, String text) {
        try {
            ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(ClipData.newPlainText("Decoded text", text));
            Toast.makeText(context, context.getString(R.string.decoder_copied, text), Toast.LENGTH_SHORT).show();
        } catch (Exception ignored) {
            // Copying is best effort.
        }
    }
}
