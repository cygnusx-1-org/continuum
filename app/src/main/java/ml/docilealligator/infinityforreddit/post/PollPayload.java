package ml.docilealligator.infinityforreddit.post;

import androidx.annotation.Nullable;
import com.google.gson.annotations.SerializedName;
import ml.docilealligator.infinityforreddit.subreddit.Flair;

public class PollPayload {
    @SerializedName("api_type")
    public String apiType = "json";
    @SerializedName("duration")
    public int duration;
    @SerializedName("nsfw")
    public boolean isNsfw;
    public String[] options;
    @SerializedName("flair_id")
    @Nullable
    public String flairId;
    @SerializedName("flair_text")
    @Nullable
    public String flairText;
    @SerializedName("raw_rtjson")
    @Nullable
    public String richTextJSON;
    @SerializedName("post_to_twitter")
    public boolean postToTwitter = false;
    @SerializedName("sendreplies")
    public boolean sendReplies;
    @SerializedName("show_error_list")
    public boolean showErrorList = true;
    @SerializedName("spoiler")
    public boolean isSpoiler;
    @SerializedName("sr")
    public String subredditName;
    @SerializedName("submit_type")
    public String submitType;
    @Nullable
    public String text;
    public String title;
    @SerializedName("validate_on_submit")
    public boolean validateOnSubmit = true;

    public PollPayload(String subredditName, String title, String[] options, int duration, boolean isNsfw,
                       boolean isSpoiler, @Nullable Flair flair, boolean sendReplies,
                       String submitType) {
        this.subredditName = subredditName;
        this.title = title;
        this.options = options;
        this.duration = duration;
        this.isNsfw = isNsfw;
        this.isSpoiler = isSpoiler;
        if (flair != null) {
            flairId = flair.getId();
            flairText = flair.getText();
        }
        this.sendReplies = sendReplies;
        this.submitType = submitType;
    }

    public PollPayload(String subredditName, String title, String[] options, int duration, boolean isNsfw,
                       boolean isSpoiler, @Nullable Flair flair, @Nullable String richTextJSON,
                       @Nullable String text, boolean sendReplies, String submitType) {
        this.subredditName = subredditName;
        this.title = title;
        this.options = options;
        this.duration = duration;
        this.isNsfw = isNsfw;
        this.isSpoiler = isSpoiler;
        if (flair != null) {
            flairId = flair.getId();
            flairText = flair.getText();
        }
        this.richTextJSON = richTextJSON;
        this.text = text;
        this.sendReplies = sendReplies;
        this.submitType = submitType;
    }
}
