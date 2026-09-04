package ml.docilealligator.infinityforreddit.commentfilter;

import android.os.Handler;
import java.util.List;
import java.util.concurrent.Executor;
import ml.docilealligator.infinityforreddit.RedditDataRoomDatabase;

public class SaveCommentFilter {
    public interface SaveCommentFilterListener {
        void success();
        void duplicate();
    }

    public static void saveCommentFilter(Executor executor, Handler handler, RedditDataRoomDatabase redditDataRoomDatabase,
                                      CommentFilter commentFilter, String originalName, SaveCommentFilter.SaveCommentFilterListener saveCommentFilterListener) {
        executor.execute(() -> {
            if (!originalName.equals(commentFilter.name) &&
                    redditDataRoomDatabase.commentFilterDao().getCommentFilter(commentFilter.name, commentFilter.username) != null) {
                handler.post(saveCommentFilterListener::duplicate);
            } else {
                // Atomic, as the post side is: comment_filter_usage cascades off the filter row, so on
                // a rename the delete + insert + usage re-key must all land or none. A failure between
                // them loses the filter and every usage it had.
                redditDataRoomDatabase.runInTransaction(() -> {
                    List<CommentFilterUsage> commentFilterUsages = redditDataRoomDatabase.commentFilterUsageDao().getAllCommentFilterUsage(originalName, commentFilter.username);
                    if (!originalName.equals(commentFilter.name)) {
                        redditDataRoomDatabase.commentFilterDao().deleteCommentFilter(originalName, commentFilter.username);
                    }
                    redditDataRoomDatabase.commentFilterDao().insert(commentFilter);
                    for (CommentFilterUsage commentFilterUsage : commentFilterUsages) {
                        commentFilterUsage.name = commentFilter.name;
                        commentFilterUsage.username = commentFilter.username;
                        redditDataRoomDatabase.commentFilterUsageDao().insert(commentFilterUsage);
                    }
                });
                handler.post(saveCommentFilterListener::success);
            }
        });
    }
}
