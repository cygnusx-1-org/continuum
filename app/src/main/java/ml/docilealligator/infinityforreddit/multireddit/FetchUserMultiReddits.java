package ml.docilealligator.infinityforreddit.multireddit;

import android.os.Handler;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.ArrayList;
import java.util.concurrent.Executor;
import ml.docilealligator.infinityforreddit.account.Account;
import ml.docilealligator.infinityforreddit.apis.RedditAPI;
import ml.docilealligator.infinityforreddit.utils.APIUtils;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;

public class FetchUserMultiReddits {
    public interface FetchUserMultiRedditsListener {
        void success(ArrayList<MultiReddit> multiReddits);
        void failed();
    }

    /**
     * Fetches the public multireddits (custom feeds) of an arbitrary user.
     *
     * @param oauthRetrofit the OAuth retrofit for a signed-in account, or the no-OAuth retrofit
     *                      (which carries the anonymous app-only token) for the anonymous account.
     * @param accessToken   the account access token, or null for the anonymous account (which takes
     *                      the public endpoint below and never reads the token).
     * @param accountName   the current account name, used to decide which endpoint variant to call.
     */
    public static void fetchUserMultiReddits(Executor executor, Handler handler, Retrofit oauthRetrofit,
                                             @Nullable String accessToken, String accountName, String username,
                                             FetchUserMultiRedditsListener listener) {
        Call<String> call;
        if (Account.ANONYMOUS_ACCOUNT.equals(accountName)) {
            call = oauthRetrofit.create(RedditAPI.class).getPublicUserMultiReddits(username);
        } else {
            call = oauthRetrofit.create(RedditAPI.class).getUserMultiReddits(APIUtils.getOAuthHeader(accessToken), username);
        }
        call.enqueue(new Callback<String>() {
            @Override
            public void onResponse(@NonNull Call<String> call, @NonNull Response<String> response) {
                if (response.isSuccessful()) {
                    ParseMultiReddit.parseMultiRedditsList(executor, handler, response.body(),
                            new ParseMultiReddit.ParseMultiRedditsListListener() {
                                @Override
                                public void success(ArrayList<MultiReddit> multiReddits) {
                                    listener.success(multiReddits);
                                }

                                @Override
                                public void failed() {
                                    listener.failed();
                                }
                            });
                } else {
                    listener.failed();
                }
            }

            @Override
            public void onFailure(@NonNull Call<String> call, @NonNull Throwable t) {
                listener.failed();
            }
        });
    }
}
