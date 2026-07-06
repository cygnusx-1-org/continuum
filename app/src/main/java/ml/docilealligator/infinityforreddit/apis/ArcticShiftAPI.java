package ml.docilealligator.infinityforreddit.apis;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface ArcticShiftAPI {
    @GET("api/posts/ids")
    Call<String> getRemovedPost(@Query("ids") String postId);
}
