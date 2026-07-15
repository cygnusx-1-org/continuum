package ml.docilealligator.infinityforreddit.subreddit;

import androidx.annotation.Nullable;

public class Rule {
    private final String shortName;
    @Nullable
    private final String descriptionHtml;

    public Rule(String shortName, @Nullable String descriptionHtml) {
        this.shortName = shortName;
        this.descriptionHtml = descriptionHtml;
    }

    public String getShortName() {
        return shortName;
    }

    @Nullable
    public String getDescriptionHtml() {
        return descriptionHtml;
    }
}
