package ml.docilealligator.infinityforreddit.message;

import android.os.Handler;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executor;
import ml.docilealligator.infinityforreddit.apis.RedditAPI;
import ml.docilealligator.infinityforreddit.utils.APIUtils;
import ml.docilealligator.infinityforreddit.utils.JSONUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;

public class FetchMessage {

    public static final String WHERE_INBOX = "inbox";
    public static final String WHERE_UNREAD = "unread";
    public static final String WHERE_SENT = "sent";
    public static final String WHERE_COMMENTS = "comments";
    public static final String WHERE_MESSAGES = "messages";
    public static final String WHERE_MESSAGES_DETAIL = "messages_detail";
    public static final int MESSAGE_TYPE_INBOX = 0;
    public static final int MESSAGE_TYPE_PRIVATE_MESSAGE = 1;
    public static final int MESSAGE_TYPE_NOTIFICATION = 2;

    static void fetchInbox(Executor executor, Handler handler,  Retrofit oauthRetrofit, Locale locale, @Nullable String accessToken, String where,
                           @Nullable String after, int messageType, FetchMessagesListener fetchMessagesListener) {
        oauthRetrofit.create(RedditAPI.class).getMessages(APIUtils.getOAuthHeader(accessToken), where, after).enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<String> call, @NonNull Response<String> response) {
                if (response.isSuccessful() && response.body() != null) {
                    executor.execute(() -> {
                        try {
                            JSONObject jsonResponse = new JSONObject(response.body());
                            JSONArray messageArray = jsonResponse.getJSONObject(JSONUtils.DATA_KEY).getJSONArray(JSONUtils.CHILDREN_KEY);
                            List<Message> messages = ParseMessage.parseMessages(messageArray, locale, messageType);
                            String newAfter = jsonResponse.getJSONObject(JSONUtils.DATA_KEY).getString(JSONUtils.AFTER_KEY);
                            handler.post(() -> fetchMessagesListener.fetchSuccess(messages, newAfter));
                        } catch (JSONException e) {
                            e.printStackTrace();
                            handler.post(fetchMessagesListener::fetchFailed);
                        }
                    });
                } else {
                    fetchMessagesListener.fetchFailed();
                }
            }

            @Override
            public void onFailure(@NonNull Call<String> call, @NonNull Throwable throwable) {
                fetchMessagesListener.fetchFailed();
            }
        });
    }

    /**
     * Fetches the actual unread inbox listing and reports how many items it contains. Reddit's
     * {@code inbox_count} field can get stuck on items that cannot be cleared from within the app
     * (archived private messages, chat, ...), so this is used to reconcile the badge against what
     * the user can really act on. See issue #334.
     */
    public static void fetchUnreadMessagesCount(Executor executor, Handler handler, Retrofit oauthRetrofit,
                                                String accessToken, FetchUnreadMessagesCountListener listener) {
        oauthRetrofit.create(RedditAPI.class).getMessages(APIUtils.getOAuthHeader(accessToken), WHERE_UNREAD, null).enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<String> call, @NonNull Response<String> response) {
                if (response.isSuccessful() && response.body() != null) {
                    executor.execute(() -> {
                        try {
                            JSONObject data = new JSONObject(response.body()).getJSONObject(JSONUtils.DATA_KEY);
                            int count = data.getJSONArray(JSONUtils.CHILDREN_KEY).length();
                            // A non-empty "after" means there are more than one page of unread items.
                            boolean hasMore = !data.isNull(JSONUtils.AFTER_KEY)
                                    && !data.getString(JSONUtils.AFTER_KEY).isEmpty();
                            handler.post(() -> listener.fetchSuccess(count, hasMore));
                        } catch (JSONException e) {
                            e.printStackTrace();
                            handler.post(listener::fetchFailed);
                        }
                    });
                } else {
                    listener.fetchFailed();
                }
            }

            @Override
            public void onFailure(@NonNull Call<String> call, @NonNull Throwable throwable) {
                listener.fetchFailed();
            }
        });
    }

    interface FetchMessagesListener {
        void fetchSuccess(List<Message> messages, @Nullable String after);

        void fetchFailed();
    }

    public interface FetchUnreadMessagesCountListener {
        void fetchSuccess(int unreadCount, boolean hasMore);

        void fetchFailed();
    }
}
