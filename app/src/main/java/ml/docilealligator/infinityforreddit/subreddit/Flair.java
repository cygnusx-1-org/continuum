package ml.docilealligator.infinityforreddit.subreddit;

import android.os.Parcel;
import android.os.Parcelable;
import androidx.annotation.Nullable;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import java.util.Objects;

public class Flair implements Parcelable {
    public static final Creator<Flair> CREATOR = new Creator<Flair>() {
        @Override
        public Flair createFromParcel(Parcel in) {
            return new Flair(in);
        }

        @Override
        public Flair[] newArray(int size) {
            return new Flair[size];
        }
    };
    private String id;
    private String text;
    // The flair's richtext as HTML with its emoji as <img>, from FlairUtils.richtextToHtml; "" when
    // the flair is plain text. For an emoji flair, text holds only the emoji's :shortcode: names.
    private String htmlText;
    private boolean editable;

    public Flair(String id, String text, String htmlText, boolean editable) {
        this.id = id;
        this.text = text;
        this.htmlText = htmlText;
        this.editable = editable;
    }

    protected Flair(Parcel in) {
        id = Objects.requireNonNull(in.readString());
        text = Objects.requireNonNull(in.readString());
        htmlText = Objects.requireNonNull(in.readString());
        editable = in.readByte() != 0;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getText() {
        return text;
    }

    /**
     * Replaces the flair's text with text the user typed. That text is the flair now, so the
     * template's richtext no longer describes it and is dropped.
     */
    public void setText(String text) {
        this.text = text;
        this.htmlText = "";
    }

    public String getHtmlText() {
        return htmlText;
    }

    public boolean isEditable() {
        return editable;
    }

    public void setEditable(boolean editable) {
        this.editable = editable;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int i) {
        parcel.writeString(id);
        parcel.writeString(text);
        parcel.writeString(htmlText);
        parcel.writeByte((byte) (editable ? 1 : 0));
    }

    public String getJSONModel() {
        return new Gson().toJson(this);
    }

    public static Flair fromJson(@Nullable String json) throws JsonParseException {
        return new Gson().fromJson(json, Flair.class);
    }
}
