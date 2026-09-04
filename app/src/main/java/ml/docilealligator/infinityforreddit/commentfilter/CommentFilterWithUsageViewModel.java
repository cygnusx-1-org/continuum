package ml.docilealligator.infinityforreddit.commentfilter;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import java.util.List;
import ml.docilealligator.infinityforreddit.RedditDataRoomDatabase;

public class CommentFilterWithUsageViewModel extends ViewModel {
    private final LiveData<List<CommentFilterWithUsage>> mCommentFilterWithUsageListLiveData;

    public CommentFilterWithUsageViewModel(RedditDataRoomDatabase redditDataRoomDatabase, String accountName) {
        // See PostFilterWithUsageViewModel: @Relation joins on the name alone, so the usages have to
        // be trimmed to the account the parent rows already belong to.
        mCommentFilterWithUsageListLiveData = Transformations.map(
                redditDataRoomDatabase.commentFilterDao().getAllCommentFilterWithUsageLiveData(accountName),
                commentFilters -> {
                    for (CommentFilterWithUsage withUsage : commentFilters) {
                        withUsage.commentFilterUsageList.removeIf(usage -> !usage.username.equals(accountName));
                    }
                    return commentFilters;
                });
    }

    public LiveData<List<CommentFilterWithUsage>> getCommentFilterWithUsageListLiveData() {
        return mCommentFilterWithUsageListLiveData;
    }

    public static class Factory extends ViewModelProvider.NewInstanceFactory {

        private final RedditDataRoomDatabase mRedditDataRoomDatabase;
        private final String mAccountName;

        public Factory(RedditDataRoomDatabase redditDataRoomDatabase, String accountName) {
            mRedditDataRoomDatabase = redditDataRoomDatabase;
            mAccountName = accountName;
        }

        @NonNull
        @Override
        public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
            //noinspection unchecked
            return (T) new CommentFilterWithUsageViewModel(mRedditDataRoomDatabase, mAccountName);
        }
    }
}
