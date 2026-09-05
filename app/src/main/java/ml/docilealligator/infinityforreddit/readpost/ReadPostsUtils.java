package ml.docilealligator.infinityforreddit.readpost;

import android.content.SharedPreferences;
import androidx.annotation.Nullable;
import ml.docilealligator.infinityforreddit.account.AccountScope;
import ml.docilealligator.infinityforreddit.utils.SharedPreferencesUtils;

public class ReadPostsUtils {
    public static int GetReadPostsLimit(String accountName, @Nullable SharedPreferences mPostHistorySharedPreferences) {
        if (mPostHistorySharedPreferences == null) {
            return -1;
        }

        // No anonymous exception. The Post History screen is reachable while logged out and offers
        // this switch there like anywhere else, so ignoring it for anonymous made a control that
        // did nothing. The default is on, so a history that was capped stays capped.
        if (mPostHistorySharedPreferences.getBoolean(AccountScope.key(accountName, SharedPreferencesUtils.READ_POSTS_LIMIT_ENABLED), true)) {
            return mPostHistorySharedPreferences.getInt(AccountScope.key(accountName, SharedPreferencesUtils.READ_POSTS_LIMIT), 500);
        } else {
            return -1;
        }
    }
}
