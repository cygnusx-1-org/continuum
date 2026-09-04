package ml.docilealligator.infinityforreddit.commentfilter;

import android.os.Parcel;
import android.os.Parcelable;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import ml.docilealligator.infinityforreddit.account.Account;
import ml.docilealligator.infinityforreddit.comment.Comment;

@Entity(tableName = "comment_filter", primaryKeys = {"name", "username"})
public class CommentFilter implements Parcelable {
    /**
     * The account this filter belongs to, [Account.ANONYMOUS_ACCOUNT] when logged out. Half the
     * primary key, so two accounts can each have a filter of the same name.
     */
    @NonNull
    @ColumnInfo(name = "username")
    public String username = Account.ANONYMOUS_ACCOUNT;
    @NonNull
    // Empty, not a placeholder: a filter has to be named by whoever makes it, and a default name
    // that looks filled in is one the user saves without reading. The Customize screen refuses to
    // write an unnamed filter, and its name field shows its hint instead.
    public String name = "";
    @DisplayMode
    @ColumnInfo(name = "display_mode")
    public int displayMode;
    @ColumnInfo(name = "max_vote")
    public int maxVote = -1;
    @ColumnInfo(name = "min_vote")
    public int minVote = -1;
    @Nullable
    @ColumnInfo(name = "exclude_strings")
    public String excludeStrings;
    @Nullable
    @ColumnInfo(name = "exclude_users")
    public String excludeUsers;

    public CommentFilter() {

    }

    protected CommentFilter(Parcel in) {
        username = Objects.requireNonNull(in.readString());
        name = Objects.requireNonNull(in.readString());
        displayMode = in.readInt();
        maxVote = in.readInt();
        minVote = in.readInt();
        excludeStrings = in.readString();
        excludeUsers = in.readString();
    }

    public static final Creator<CommentFilter> CREATOR = new Creator<CommentFilter>() {
        @Override
        public CommentFilter createFromParcel(Parcel in) {
            return new CommentFilter(in);
        }

        @Override
        public CommentFilter[] newArray(int size) {
            return new CommentFilter[size];
        }
    };

    public static boolean isCommentAllowed(Comment comment, CommentFilter commentFilter) {
        if (commentFilter.maxVote > 0 && comment.getVoteType() + comment.getScore() > commentFilter.maxVote) {
            return false;
        }
        if (commentFilter.minVote > 0 && comment.getVoteType() + comment.getScore() < commentFilter.minVote) {
            return false;
        }
        if (commentFilter.excludeStrings != null && !commentFilter.excludeStrings.equals("")) {
            String[] titles = commentFilter.excludeStrings.split(",", 0);
            for (String t : titles) {
                // Locale.ROOT: on a Turkish/Azeri device the device locale lower-cases "I" to
                // dotless "i", so a keyword would stop matching a comment that differs from it only
                // in the case of an I. This folds Reddit content, never text shown to the user.
                if (!t.trim().equals("") && comment.getCommentRawText() != null && comment.getCommentRawText().toLowerCase(Locale.ROOT).contains(t.toLowerCase(Locale.ROOT).trim())) {
                    return false;
                }
            }
        }
        if (commentFilter.excludeUsers != null && !commentFilter.excludeUsers.equals("")) {
            String[] users = commentFilter.excludeUsers.split(",", 0);
            for (String u : users) {
                if (!u.trim().equals("") && u.trim().equalsIgnoreCase(comment.getAuthor())) {
                    return false;
                }
            }
        }

        return true;
    }

    public static CommentFilter mergeCommentFilter(List<CommentFilter> commentFilterList) {
        if (commentFilterList.size() == 1) {
            return commentFilterList.get(0);
        }
        CommentFilter commentFilter = new CommentFilter();
        StringBuilder stringBuilder;
        commentFilter.name = "Merged";

        for (CommentFilter c : commentFilterList) {
            commentFilter.displayMode = Math.max(c.displayMode, commentFilter.displayMode);
            // Not Math.min: the seed above is a fresh CommentFilter, whose maxVote is the -1 "no
            // bound" sentinel that isCommentAllowed tests with `> 0`, and Math.min against -1 can
            // never leave -1 -- so every maximum the user set was dropped by any merge of two or
            // more filters. Math.max on minVote does accumulate the strictest minimum, which is why
            // the pair reads as one symmetric block while only half of it worked.
            commentFilter.maxVote = strictestMaximum(commentFilter.maxVote, c.maxVote);
            commentFilter.minVote = Math.max(c.minVote, commentFilter.minVote);

            if (c.excludeStrings != null && !c.excludeStrings.isEmpty()) {
                stringBuilder = new StringBuilder(commentFilter.excludeStrings == null ? "" : commentFilter.excludeStrings);
                stringBuilder.append(",").append(c.excludeStrings);
                commentFilter.excludeStrings = stringBuilder.toString();
            }

            if (c.excludeUsers != null && !c.excludeUsers.isEmpty()) {
                stringBuilder = new StringBuilder(commentFilter.excludeUsers == null ? "" : commentFilter.excludeUsers);
                stringBuilder.append(",").append(c.excludeUsers);
                commentFilter.excludeUsers = stringBuilder.toString();
            }
        }

        return commentFilter;
    }

    /**
     * Folds one filter's maximum score into a running merge, strictest-wins. Only a positive bound
     * is in force -- {@link #isCommentAllowed} tests it with {@code > 0}, so both -1 (the unset
     * sentinel) and 0 mean "no maximum" -- and a filter that sets none leaves the running value
     * alone.
     */
    private static int strictestMaximum(int running, int candidate) {
        if (candidate <= 0) {
            return running;
        }
        return running <= 0 ? candidate : Math.min(running, candidate);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeString(username);
        dest.writeString(name);
        dest.writeInt(displayMode);
        dest.writeInt(maxVote);
        dest.writeInt(minVote);
        dest.writeString(excludeStrings);
        dest.writeString(excludeUsers);
    }

    @IntDef({DisplayMode.REMOVE_COMMENT, DisplayMode.COLLAPSE_COMMENT})
    @Retention(RetentionPolicy.SOURCE)
    public @interface DisplayMode {
        int REMOVE_COMMENT = 0;
        int COLLAPSE_COMMENT = 10;
    }
}
