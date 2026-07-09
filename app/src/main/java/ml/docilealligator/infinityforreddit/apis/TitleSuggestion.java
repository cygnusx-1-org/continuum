package ml.docilealligator.infinityforreddit.apis;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Headers;
import retrofit2.http.Streaming;
import retrofit2.http.Url;

public interface TitleSuggestion {
    /**
     * Streams the response so the caller can bound how much of it is read. The URL is whatever the
     * user typed, which may well be a direct link to a large video; converting to a String here
     * would buffer the whole thing into memory.
     */
    @Streaming
    @Headers("Accept: text/html,application/xhtml+xml,application/json;q=0.9,*/*;q=0.8")
    @GET()
    Call<ResponseBody> get(@Url String url);
}
