package ml.docilealligator.infinityforreddit.multireddit;

import androidx.annotation.Nullable;

public class SubredditInMultiReddit {
    @Nullable
    String name;

    SubredditInMultiReddit() {}

    SubredditInMultiReddit(String subredditName) {
        name = subredditName;
    }
}