package ml.docilealligator.infinityforreddit.postfilter;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import java.util.List;
import ml.docilealligator.infinityforreddit.RedditDataRoomDatabase;

public class PostFilterWithUsageViewModel extends ViewModel {
    private final LiveData<List<PostFilterWithUsage>> mPostFilterWithUsageListLiveData;

    public PostFilterWithUsageViewModel(RedditDataRoomDatabase redditDataRoomDatabase, String accountName) {
        // Room's @Relation joins on one column pair, and the key is (name, username) now, so a
        // same-named filter belonging to another account brings its usages along with this one's.
        // The parent rows are already scoped; trim the children to match them.
        mPostFilterWithUsageListLiveData = Transformations.map(
                redditDataRoomDatabase.postFilterDao().getAllPostFilterWithUsageLiveData(accountName),
                postFilters -> {
                    for (PostFilterWithUsage withUsage : postFilters) {
                        withUsage.postFilterUsages.removeIf(usage -> !usage.username.equals(accountName));
                    }
                    return postFilters;
                });
    }

    public LiveData<List<PostFilterWithUsage>> getPostFilterWithUsageListLiveData() {
        return mPostFilterWithUsageListLiveData;
    }

    public static class Factory extends ViewModelProvider.NewInstanceFactory {

        private final RedditDataRoomDatabase mRedditDataRoomDatabase;
        private final String mAccountName;

        public Factory(RedditDataRoomDatabase redditDataRoomDatabase, String accountName) {
            mRedditDataRoomDatabase = redditDataRoomDatabase;
            mAccountName = accountName;
        }

        @Override
        public <T extends ViewModel> T create(Class<T> modelClass) {
            //noinspection unchecked
            return (T) new PostFilterWithUsageViewModel(mRedditDataRoomDatabase, mAccountName);
        }
    }
}
