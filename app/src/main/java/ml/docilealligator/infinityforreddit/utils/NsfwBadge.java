package ml.docilealligator.infinityforreddit.utils;

import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.StyleSpan;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.Nullable;

/**
 * Shows NSFW inside the post type badge ("Image · NSFW") instead of as a badge of its own, which
 * frees a badge's width in the row.
 */
public final class NsfwBadge {
    private static final String SUFFIX = " · NSFW";

    private NsfwBadge() {
        // Prevent instantiation
    }

    /**
     * Call after the type badge's text is final for this bind. The type badge may be a recycled
     * view whose text still carries the suffix from the last post, so the suffix is always
     * removed first.
     *
     * @param nsfw whether to fold: the post is NSFW and the setting is on. The separate NSFW badge
     *             is left alone when the type badge is not showing, so NSFW is never lost.
     */
    public static void fold(@Nullable TextView typeBadge, @Nullable View nsfwBadge, boolean nsfw) {
        if (typeBadge == null) {
            return;
        }
        String text = typeBadge.getText().toString();
        boolean hadSuffix = text.endsWith(SUFFIX);
        String base = hadSuffix ? text.substring(0, text.length() - SUFFIX.length()) : text;

        if (nsfw && nsfwBadge != null && typeBadge.getVisibility() == View.VISIBLE) {
            SpannableStringBuilder folded = new SpannableStringBuilder(base).append(SUFFIX);
            folded.setSpan(new StyleSpan(Typeface.BOLD), base.length() + SUFFIX.length() - 4,
                    folded.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            typeBadge.setText(folded);
            nsfwBadge.setVisibility(View.GONE);
        } else if (hadSuffix) {
            typeBadge.setText(base);
        }
    }
}
