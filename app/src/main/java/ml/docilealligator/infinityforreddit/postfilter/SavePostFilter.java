package ml.docilealligator.infinityforreddit.postfilter;

import android.os.Handler;
import java.util.List;
import java.util.concurrent.Executor;
import ml.docilealligator.infinityforreddit.RedditDataRoomDatabase;

public class SavePostFilter {
    public interface SavePostFilterListener {
        void success();
        void duplicate();
        void failed();
    }

    public static void savePostFilter(Executor executor, Handler handler, RedditDataRoomDatabase redditDataRoomDatabase,
                                      PostFilter postFilter, String originalName, SavePostFilterListener savePostFilterListener) {
        executor.execute(() -> {
            try {
                if (!originalName.equals(postFilter.name) &&
                        redditDataRoomDatabase.postFilterDao().getPostFilter(postFilter.name) != null) {
                    handler.post(savePostFilterListener::duplicate);
                } else {
                    // Atomic: on a rename the delete + insert + usage re-key must all land or none, so a
                    // mid-write failure can't leave the old filter deleted and the new one never inserted.
                    redditDataRoomDatabase.runInTransaction(() -> {
                        List<PostFilterUsage> postFilterUsages = redditDataRoomDatabase.postFilterUsageDao().getAllPostFilterUsage(originalName);
                        if (!originalName.equals(postFilter.name)) {
                            redditDataRoomDatabase.postFilterDao().deletePostFilter(originalName);
                        }
                        redditDataRoomDatabase.postFilterDao().insert(postFilter);
                        for (PostFilterUsage postFilterUsage : postFilterUsages) {
                            postFilterUsage.name = postFilter.name;
                            redditDataRoomDatabase.postFilterUsageDao().insert(postFilterUsage);
                        }
                    });
                    handler.post(savePostFilterListener::success);
                }
            } catch (Exception e) {
                // A Room failure (locked/corrupt DB, disk-full, constraint) rolls the transaction back
                // and still reports a terminal outcome — otherwise a caller gating on this callback (the
                // ViewModel's in-flight guard) would hang forever and block every later save. The
                // exception text is developer-oriented, so it is logged, not surfaced to the user.
                e.printStackTrace();
                handler.post(savePostFilterListener::failed);
            }
        });
    }
}
