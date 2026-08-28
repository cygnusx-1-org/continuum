package ml.docilealligator.infinityforreddit.apis;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface ArcticShiftAPI {
    @GET("api/posts/ids")
    Call<String> getRemovedPost(@Query("ids") String postId);

    /**
     * {@code ids} takes a comma-separated batch and the archive answers with one {@code data} entry
     * per id it holds, so recovering a whole thread's removed comments is one request rather than
     * one per comment. The ceiling is URL length, not an item count: 500 ids answer normally and
     * 1000 come back {@code 414 URI Too Long}.
     */
    @GET("api/comments/ids")
    Call<String> getRemovedComments(@Query("ids") String ids);
}
