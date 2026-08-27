package ml.docilealligator.infinityforreddit.apis;

import androidx.annotation.Nullable;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Streaming;
import retrofit2.http.Url;

public interface DownloadFile {
    @Streaming
    @GET()
    Call<ResponseBody> downloadFile(@Url String fileUrl);

    /**
     * A conditional fetch: pass the ETag from the previous 200 back as {@code If-None-Match} and an
     * unchanged file answers 304 with no body. A null or absent tag omits the header and makes this
     * an ordinary download.
     */
    @Streaming
    @GET()
    Call<ResponseBody> downloadFile(@Url String fileUrl, @Header("If-None-Match") @Nullable String ifNoneMatch);
}
