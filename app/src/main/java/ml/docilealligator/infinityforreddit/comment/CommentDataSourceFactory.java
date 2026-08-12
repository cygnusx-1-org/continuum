package ml.docilealligator.infinityforreddit.comment;

import android.os.Handler;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.MutableLiveData;
import androidx.paging.DataSource;
import java.util.concurrent.Executor;
import ml.docilealligator.infinityforreddit.RedditDataRoomDatabase;
import ml.docilealligator.infinityforreddit.thing.SortType;
import ml.docilealligator.infinityforreddit.utils.SavedSearchCache;
import retrofit2.Retrofit;

class CommentDataSourceFactory extends DataSource.Factory {
    private final Executor executor;
    private final Handler handler;
    private final Retrofit retrofit;
    @Nullable
    private final String accessToken;
    private final String accountName;
    private final String username;
    private SortType sortType;
    private final boolean areSavedComments;
    private final boolean areLocalSavedComments;
    @Nullable
    private String query;
    private final SavedSearchCache<Comment> savedSearchCache;
    private final RedditDataRoomDatabase redditDataRoomDatabase;

    @Nullable
    private CommentDataSource commentDataSource;
    private final MutableLiveData<CommentDataSource> commentDataSourceLiveData;

    CommentDataSourceFactory(Executor executor, Handler handler, Retrofit retrofit, @Nullable String accessToken, @NonNull String accountName,
                             String username, SortType sortType,
                             boolean areSavedComments, boolean areLocalSavedComments,
                             SavedSearchCache<Comment> savedSearchCache,
                             RedditDataRoomDatabase redditDataRoomDatabase) {
        this.executor = executor;
        this.handler = handler;
        this.retrofit = retrofit;
        this.accessToken = accessToken;
        this.accountName = accountName;
        this.username = username;
        this.sortType = sortType;
        this.areSavedComments = areSavedComments;
        this.areLocalSavedComments = areLocalSavedComments;
        this.savedSearchCache = savedSearchCache;
        this.redditDataRoomDatabase = redditDataRoomDatabase;
        commentDataSourceLiveData = new MutableLiveData<>();
    }

    @NonNull
    @Override
    public DataSource create() {
        CommentDataSource dataSource = new CommentDataSource(executor, handler, retrofit, accessToken, accountName, username,
                sortType, areSavedComments, areLocalSavedComments, query, savedSearchCache, redditDataRoomDatabase);
        commentDataSource = dataSource;
        commentDataSourceLiveData.postValue(dataSource);
        return dataSource;
    }

    public MutableLiveData<CommentDataSource> getCommentDataSourceLiveData() {
        return commentDataSourceLiveData;
    }

    @Nullable
    CommentDataSource getCommentDataSource() {
        return commentDataSource;
    }

    void changeSortType(SortType sortType) {
        this.sortType = sortType;
    }

    // Sets the Saved-search query and invalidates the current data source so Paging rebuilds it with
    // the new query. The rebuilt CommentDataSource re-fetches from the start, filtering (and walking
    // further pages) against the query. A null/empty query restores the unfiltered listing.
    void changeQuery(String query) {
        this.query = query;
        if (commentDataSource != null) {
            commentDataSource.invalidate();
        }
    }
}
