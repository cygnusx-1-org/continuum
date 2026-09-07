package ml.docilealligator.infinityforreddit.asynctasks;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import java.util.concurrent.Executor;
import ml.docilealligator.infinityforreddit.RedditDataRoomDatabase;
import ml.docilealligator.infinityforreddit.account.Account;
import ml.docilealligator.infinityforreddit.account.AccountDao;
import ml.docilealligator.infinityforreddit.resume.ResumeState;
import ml.docilealligator.infinityforreddit.utils.SharedPreferencesUtils;

public class AccountManagement {

    public static void switchAccount(RedditDataRoomDatabase redditDataRoomDatabase,
                                     SharedPreferences currentAccountSharedPreferences, Executor executor,
                                     Handler handler, String newAccountName,
                                     SwitchAccountListener switchAccountListener) {
        executor.execute(() -> {
            redditDataRoomDatabase.accountDao().markAllAccountsNonCurrent();
            redditDataRoomDatabase.accountDao().markAccountCurrent(newAccountName);
            Account account = redditDataRoomDatabase.accountDao().getCurrentAccount();
            currentAccountSharedPreferences.edit()
                    .putString(SharedPreferencesUtils.ACCESS_TOKEN, account.getAccessToken())
                    .putString(SharedPreferencesUtils.ACCOUNT_NAME, account.getAccountName())
                    .putString(SharedPreferencesUtils.ACCOUNT_IMAGE_URL, account.getProfileImageUrl()).apply();
            currentAccountSharedPreferences.edit()
                    .remove(SharedPreferencesUtils.SUBSCRIBED_THINGS_SYNC_TIME)
                    .remove(SharedPreferencesUtils.INBOX_COUNT)
                    .apply();
            handler.post(() -> switchAccountListener.switched(account));
        });

    }

    public static void switchToAnonymousMode(Context context, RedditDataRoomDatabase redditDataRoomDatabase,
                                             SharedPreferences currentAccountSharedPreferences,
                                             Executor executor, Handler handler, boolean removeCurrentAccount,
                                             SwitchToAnonymousAccountAsyncTaskListener switchToAnonymousAccountAsyncTaskListener) {
        Context appContext = context.getApplicationContext();
        executor.execute(() -> {
            AccountDao accountDao = redditDataRoomDatabase.accountDao();
            if (removeCurrentAccount) {
                // Before the account row and the current-account file go: this account is about to
                // stop existing, and its recorded screen stack and cached feeds have to go with it.
                // Switching to anonymous WITHOUT removing the account is deliberately not cleared --
                // that user is coming back, and their place is worth keeping.
                ResumeState.clearAccount(appContext, currentAccountSharedPreferences.getString(
                        SharedPreferencesUtils.ACCOUNT_NAME, Account.ANONYMOUS_ACCOUNT));
                accountDao.deleteCurrentAccount();
            }
            accountDao.markAllAccountsNonCurrent();

            String redgifsAccessToken = currentAccountSharedPreferences.getString(SharedPreferencesUtils.REDGIFS_ACCESS_TOKEN, "");

            currentAccountSharedPreferences.edit().clear().apply();
            // clear() is only reported to preference listeners on API 30+, so write the emptied
            // inbox count out explicitly to bring the badge down on every version.
            currentAccountSharedPreferences.edit()
                    .putString(SharedPreferencesUtils.REDGIFS_ACCESS_TOKEN, redgifsAccessToken)
                    .putInt(SharedPreferencesUtils.INBOX_COUNT, 0)
                    .apply();

            handler.post(switchToAnonymousAccountAsyncTaskListener::logoutSuccess);
        });
    }

    public static void removeAccount(Context context, RedditDataRoomDatabase redditDataRoomDatabase,
                                             Executor executor, String accoutName) {
        Context appContext = context.getApplicationContext();
        executor.execute(() -> {
            redditDataRoomDatabase.accountDao().deleteAccount(accoutName);
            // The screens and cached feeds recorded for an account that no longer exists would
            // otherwise sit on disk until something else happened to clear them.
            ResumeState.clearAccount(appContext, accoutName);
        });
    }

    public interface SwitchToAnonymousAccountAsyncTaskListener {
        void logoutSuccess();
    }

    public interface SwitchAccountListener {
        void switched(Account account);
    }
}
