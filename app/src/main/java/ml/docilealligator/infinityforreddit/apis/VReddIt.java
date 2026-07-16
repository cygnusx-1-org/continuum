package ml.docilealligator.infinityforreddit.apis;

import androidx.annotation.Nullable;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Url;

public interface VReddIt {
    @GET()
    Call<String> getRedirectUrl(@Url @Nullable String vReddItUrl);
}
