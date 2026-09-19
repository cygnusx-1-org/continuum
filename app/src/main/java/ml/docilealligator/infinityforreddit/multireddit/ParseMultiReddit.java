package ml.docilealligator.infinityforreddit.multireddit;

import android.os.Handler;
import android.util.Log;
import androidx.annotation.Nullable;
import java.util.ArrayList;
import java.util.concurrent.Executor;
import ml.docilealligator.infinityforreddit.RedditDataRoomDatabase;
import ml.docilealligator.infinityforreddit.utils.JSONUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class ParseMultiReddit {
    interface ParseMultiRedditsListListener {
        void success(ArrayList<MultiReddit> multiReddits);
        void failed();
    }

    interface ParseMultiRedditListener {
        void success();
        void failed();
    }

    public static void parseMultiRedditsList(Executor executor, Handler handler, @Nullable String response,
                                             ParseMultiRedditsListListener parseMultiRedditsListListener) {
        executor.execute(() -> {
            if (response == null) {
                handler.post(parseMultiRedditsListListener::failed);
                return;
            }
            try {
                JSONArray arrayResponse = new JSONArray(response);
                ArrayList<MultiReddit> multiReddits = new ArrayList<>();
                for (int i = 0; i < arrayResponse.length(); i++) {
                    try {
                        multiReddits.add(parseMultiReddit(arrayResponse.getJSONObject(i).getJSONObject(JSONUtils.DATA_KEY)));
                    } catch (JSONException e) {
                        Log.e("ParseMultiReddit", "parseMultiRedditsList failed", e);
                    }
                }

                handler.post(() -> parseMultiRedditsListListener.success(multiReddits));
            } catch (JSONException e) {
                e.printStackTrace();
                handler.post(parseMultiRedditsListListener::failed);
            }
        });
    }

    public static void parseAndSaveMultiReddit(Executor executor, Handler handler, @Nullable String response, RedditDataRoomDatabase redditDataRoomDatabase,
                                               ParseMultiRedditListener parseMultiRedditListener) {
        executor.execute(() -> {
            if (response == null) {
                handler.post(parseMultiRedditListener::failed);
                return;
            }
            try {
                MultiReddit multiReddit = parseMultiReddit(new JSONObject(response).getJSONObject(JSONUtils.DATA_KEY));
                redditDataRoomDatabase.multiRedditDao().insert(multiReddit);

                handler.post(parseMultiRedditListener::success);
            } catch (JSONException e) {
                e.printStackTrace();
                handler.post(parseMultiRedditListener::failed);
            }
        });
    }

    private static MultiReddit parseMultiReddit(JSONObject singleMultiRedditJSON) throws JSONException {
        String displayName = singleMultiRedditJSON.getString(JSONUtils.DISPLAY_NAME_KEY);
        String name = singleMultiRedditJSON.getString(JSONUtils.NAME_KEY);
        String description = singleMultiRedditJSON.getString(JSONUtils.DESCRIPTION_MD_KEY);
        int nSubscribers = singleMultiRedditJSON.getInt(JSONUtils.NUM_SUBSCRIBERS_KEY);
        String copiedFrom = singleMultiRedditJSON.getString(JSONUtils.COPIED_FROM_KEY);
        String iconUrl = singleMultiRedditJSON.getString(JSONUtils.ICON_URL_KEY);
        long createdUTC = singleMultiRedditJSON.getLong(JSONUtils.CREATED_UTC_KEY);
        String visibility = singleMultiRedditJSON.getString(JSONUtils.VISIBILITY_KEY);
        boolean over18 = singleMultiRedditJSON.getBoolean(JSONUtils.OVER_18_KEY);
        String path = singleMultiRedditJSON.getString(JSONUtils.PATH_KEY);
        String owner = singleMultiRedditJSON.getString(JSONUtils.OWNER_KEY);
        boolean isSubscriber = singleMultiRedditJSON.getBoolean(JSONUtils.IS_SUBSCRIBER_KEY);
        boolean isFavorited = singleMultiRedditJSON.getBoolean(JSONUtils.IS_FAVORITED_KEY);

        ArrayList<ExpandedSubredditInMultiReddit> subreddits =
                parseSubredditsInMultiReddit(singleMultiRedditJSON.getJSONArray(JSONUtils.SUBREDDITS_KEY));

        return new MultiReddit(path, displayName, name, description, copiedFrom,
                iconUrl, visibility, owner, nSubscribers, createdUTC, over18, isSubscriber,
                isFavorited, subreddits);
    }

    /**
     * Reddit nests each subreddit's own fields under "data" only when the request asked for
     * expand_srs=true; otherwise an entry is just {"name": "..."}. The create endpoint ignores
     * that flag entirely, so both shapes have to parse: a missing icon costs the list a picture,
     * it is not a reason to fail the whole multireddit.
     */
    static ArrayList<ExpandedSubredditInMultiReddit> parseSubredditsInMultiReddit(
            JSONArray subredditsArray) {
        ArrayList<ExpandedSubredditInMultiReddit> subreddits = new ArrayList<>();
        for (int i = 0; i < subredditsArray.length(); i++) {
            try {
                JSONObject subredditJSON = subredditsArray.getJSONObject(i);
                JSONObject subredditData = subredditJSON.optJSONObject(JSONUtils.DATA_KEY);
                String iconUrl = subredditData == null || subredditData.isNull(JSONUtils.COMMUNITY_ICON_KEY)
                        ? null
                        : subredditData.getString(JSONUtils.COMMUNITY_ICON_KEY);
                subreddits.add(new ExpandedSubredditInMultiReddit(
                        subredditJSON.getString(JSONUtils.NAME_KEY), iconUrl));
            } catch (JSONException e) {
                Log.e("ParseMultiReddit", "parseSubredditsInMultiReddit failed", e);
            }
        }
        return subreddits;
    }
}
