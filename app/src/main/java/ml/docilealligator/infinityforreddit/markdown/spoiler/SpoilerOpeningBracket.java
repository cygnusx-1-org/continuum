package ml.docilealligator.infinityforreddit.markdown.spoiler;

import androidx.annotation.Nullable;
import org.commonmark.internal.Delimiter;
import org.commonmark.node.Node;

public class SpoilerOpeningBracket {
    /**
     * Node that contains spoiler opening markdown ({@code >!}).
     */
    public final Node node;

    /**
     * Previous bracket.
     */
    @Nullable
    public final SpoilerOpeningBracket previous;

    /**
     * Previous delimiter (emphasis, etc) before this bracket.
     */
    public final Delimiter previousDelimiter;

    public SpoilerOpeningBracket(Node node, @Nullable SpoilerOpeningBracket previous, Delimiter previousDelimiter) {
        this.node = node;
        this.previous = previous;
        this.previousDelimiter = previousDelimiter;
    }
}
