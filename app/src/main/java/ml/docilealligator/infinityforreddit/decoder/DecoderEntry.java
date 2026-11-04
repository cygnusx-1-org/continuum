package ml.docilealligator.infinityforreddit.decoder;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.widget.ImageViewCompat;

import java.util.List;
import java.util.function.Function;

import io.noties.markwon.Markwon;
import io.noties.markwon.recycler.MarkwonAdapter;
import ml.docilealligator.infinityforreddit.activities.BaseActivity;
import ml.docilealligator.infinityforreddit.customtheme.CustomThemeWrapper;
import ml.docilealligator.infinityforreddit.databinding.MarkdownDecoderBlockBinding;

/**
 * Renders a DecoderBlock as a card: the decoded text, a Copy button, and search buttons.
 */
public class DecoderEntry extends MarkwonAdapter.Entry<DecoderBlock, DecoderEntry.Holder> {
    private final BaseActivity activity;
    private final int accentColor;
    private final int primaryTextColor;
    private final int cardColor;
    private final int strokeColor;

    public DecoderEntry(@NonNull BaseActivity activity) {
        this.activity = activity;
        CustomThemeWrapper theme = activity.getCustomThemeWrapper();
        accentColor = theme.getColorAccent();
        primaryTextColor = theme.getPrimaryTextColor();
        cardColor = theme.getFilledCardViewBackgroundColor();
        strokeColor = theme.getDividerColor();
    }

    @NonNull
    @Override
    public Holder createHolder(@NonNull LayoutInflater inflater, @NonNull ViewGroup parent) {
        return new Holder(MarkdownDecoderBlockBinding.inflate(inflater, parent, false));
    }

    @Override
    public void bindHolder(@NonNull Markwon markwon, @NonNull Holder holder, @NonNull DecoderBlock node) {
        MarkdownDecoderBlockBinding b = holder.binding;

        float density = b.getRoot().getResources().getDisplayMetrics().density;
        GradientDrawable background = new GradientDrawable();
        background.setColor(cardColor);
        background.setCornerRadius(density * 8);
        background.setStroke(Math.round(density), strokeColor);
        b.getRoot().setBackground(background);

        b.labelTextViewMarkdownDecoderBlock.setText(node.label);
        b.labelTextViewMarkdownDecoderBlock.setTextColor(accentColor);
        b.resultTextViewMarkdownDecoderBlock.setText(node.text);
        b.resultTextViewMarkdownDecoderBlock.setTextColor(primaryTextColor);
        ImageViewCompat.setImageTintList(b.copyButtonMarkdownDecoderBlock, ColorStateList.valueOf(primaryTextColor));

        // Copy keeps the full decoded text; the searches use only the names in it, one search
        // for each.
        List<String> terms = CommentDecoder.extractSearchTerms(node.text);
        b.copyButtonMarkdownDecoderBlock.setOnClickListener(v -> DecoderLinkHandler.copy(activity, node.text));
        bindSearch(b.simpcityTitlesTextViewMarkdownDecoderBlock, terms, term -> CommentDecoder.simpCityUrl(term, true));
        bindSearch(b.simpcityAllTextViewMarkdownDecoderBlock, terms, term -> CommentDecoder.simpCityUrl(term, false));
        bindSearch(b.socialTextViewMarkdownDecoderBlock, terms, CommentDecoder::socialSearchUrl);
    }

    // One tab for each term. The browser may ask to allow several to open at once.
    private void bindSearch(TextView button, List<String> terms, Function<String, String> urlFor) {
        button.setTextColor(accentColor);
        button.setOnClickListener(v -> {
            for (String term : terms) {
                activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(urlFor.apply(term))));
            }
        });
    }

    public static class Holder extends MarkwonAdapter.Holder {
        final MarkdownDecoderBlockBinding binding;

        Holder(MarkdownDecoderBlockBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
