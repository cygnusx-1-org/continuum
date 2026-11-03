/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package ml.docilealligator.infinityforreddit.decoder;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.widget.Toast;

/**
 * Handles taps on the decoder's Copy links before a link is opened.
 */
public final class DecoderLinkHandler {
    private DecoderLinkHandler() {
        // Prevent instantiation
    }

    /**
     * @return true if the link was a Copy link and has been handled.
     */
    public static boolean handleLink(Context context, String url) {
        String text = CommentDecoder.copyText(url);
        if (text == null) return false;
        try {
            ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(ClipData.newPlainText("Decoded text", text));
            Toast.makeText(context, "Copied " + text, Toast.LENGTH_SHORT).show();
        } catch (Exception ignored) {
            // Copying is best effort; the tap is still ours.
        }
        return true;
    }
}
