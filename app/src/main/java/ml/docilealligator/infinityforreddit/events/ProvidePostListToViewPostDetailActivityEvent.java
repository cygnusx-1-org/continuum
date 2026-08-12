package ml.docilealligator.infinityforreddit.events;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import ml.docilealligator.infinityforreddit.post.Post;
import ml.docilealligator.infinityforreddit.post.PostType;
import ml.docilealligator.infinityforreddit.postfilter.PostFilter;
import ml.docilealligator.infinityforreddit.readpost.ReadPostType;
import ml.docilealligator.infinityforreddit.readpost.ReadPostsListInterface;
import ml.docilealligator.infinityforreddit.thing.SortType;

public class ProvidePostListToViewPostDetailActivityEvent {
    public long postFragmentId;
    public ArrayList<Post> posts;
    @PostType
    public int postType;
    @Nullable
    public String subredditName;
    @Nullable
    public String concatenatedSubredditNames;
    @Nullable
    public String username;
    @Nullable
    public String userWhere;
    @Nullable
    public String multiPath;
    @Nullable
    public String query;
    @Nullable
    public String trendingSource;
    @ReadPostType
    public int readPostType;
    @Nullable
    public PostFilter postFilter;
    @Nullable
    public SortType sortType;
    @Nullable
    public ReadPostsListInterface readPostsList;

    public ProvidePostListToViewPostDetailActivityEvent(long postFragmentId, ArrayList<Post> posts, @PostType int postType,
                                                        @Nullable String subredditName, @Nullable String concatenatedSubredditNames,
                                                        @Nullable String username, @Nullable String userWhere,
                                                        @Nullable String multiPath, @Nullable String query, @Nullable String trendingSource,
                                                        @ReadPostType int readPostType, @Nullable PostFilter postFilter,
                                                        @Nullable SortType sortType, @Nullable ReadPostsListInterface readPostsList) {
        this.postFragmentId = postFragmentId;
        this.posts = posts;
        this.postType = postType;
        this.subredditName = subredditName;
        this.concatenatedSubredditNames = concatenatedSubredditNames;
        this.username = username;
        this.userWhere = userWhere;
        this.multiPath = multiPath;
        this.query = query;
        this.trendingSource = trendingSource;
        this.readPostType = readPostType;
        this.postFilter = postFilter;
        this.sortType = sortType;
        this.readPostsList = readPostsList;
    }
}
