package ml.docilealligator.infinityforreddit.asynctasks;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Handler;
import android.util.Log;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import ml.docilealligator.infinityforreddit.R;
import ml.docilealligator.infinityforreddit.RedditDataRoomDatabase;
import ml.docilealligator.infinityforreddit.account.Account;
import ml.docilealligator.infinityforreddit.commentfilter.CommentFilter;
import ml.docilealligator.infinityforreddit.commentfilter.CommentFilterUsage;
import ml.docilealligator.infinityforreddit.customtheme.CustomTheme;
import ml.docilealligator.infinityforreddit.customtheme.CustomThemeDao;
import ml.docilealligator.infinityforreddit.customtheme.CustomThemeWrapper;
import ml.docilealligator.infinityforreddit.localsaved.LocalSavedThing;
import ml.docilealligator.infinityforreddit.multireddit.AnonymousMultiredditSubreddit;
import ml.docilealligator.infinityforreddit.multireddit.MultiReddit;
import ml.docilealligator.infinityforreddit.postfilter.PostFilter;
import ml.docilealligator.infinityforreddit.postfilter.PostFilterUsage;
import ml.docilealligator.infinityforreddit.readpost.ReadPost;
import ml.docilealligator.infinityforreddit.subscribedsubreddit.SubscribedSubredditData;
import ml.docilealligator.infinityforreddit.subscribeduser.SubscribedUserData;
import ml.docilealligator.infinityforreddit.utils.AppRestartHelper;
import ml.docilealligator.infinityforreddit.utils.CustomThemeSharedPreferencesUtils;
import ml.docilealligator.infinityforreddit.utils.SharedPreferencesUtils;
import ml.docilealligator.infinityforreddit.utils.Utils;
import net.lingala.zip4j.ZipFile;
import org.apache.commons.io.FileUtils;

public class RestoreSettings {
    public static void restoreSettings(Context context, Executor executor, Handler handler,
                                ContentResolver contentResolver, Uri zipFileUri,
                                String password,
                                RedditDataRoomDatabase redditDataRoomDatabase,
                                SharedPreferences defaultSharedPreferences,
                                SharedPreferences currentAccountSharedPreferences,
                                SharedPreferences lightThemeSharedPreferences,
                                SharedPreferences darkThemeSharedPreferences,
                                SharedPreferences amoledThemeSharedPreferences,
                                SharedPreferences sortTypeSharedPreferences,
                                SharedPreferences postLayoutSharedPreferences,
                                SharedPreferences postDetailsSharedPreferences,
                                SharedPreferences postFeedScrolledPositionSharedPreferences,
                                SharedPreferences mainActivityTabsSharedPreferences,
                                SharedPreferences proxySharedPreferences,
                                SharedPreferences nsfwAndSpoilerSharedPreferencs,
                                SharedPreferences bottomAppBarSharedPreferences,
                                SharedPreferences postHistorySharedPreferences,
                                SharedPreferences navigationDrawerSharedPreferences,
                                RestoreSettingsListener restoreSettingsListener) {
        executor.execute(() -> {
            try {
                InputStream zipFileInputStream = contentResolver.openInputStream(zipFileUri);
                if (zipFileInputStream == null) {
                    handler.post(() -> restoreSettingsListener.failed(context.getString(R.string.restore_settings_failed_cannot_get_file)));
                    return;
                }

                File cacheDir = Utils.getCacheDir(context);
                if (cacheDir == null) {
                    handler.post(() -> restoreSettingsListener.failed(context.getString(R.string.restore_settings_failed_cannot_get_cache_dir)));
                    return;
                }
                String cachePath = cacheDir + "/Restore/";
                if (new File(cachePath).exists()) {
                    FileUtils.deleteDirectory(new File(cachePath));
                }
                new File(cachePath).mkdir();
                FileOutputStream zipCacheOutputStream = new FileOutputStream(new File(cachePath + "restore.zip"));

                byte[] fileReader = new byte[1024];

                while (true) {
                    int read = zipFileInputStream.read(fileReader);

                    if (read == -1) {
                        break;
                    }

                    zipCacheOutputStream.write(fileReader, 0, read);
                }

                new ZipFile(cachePath + "restore.zip", password.toCharArray()).extractAll(cachePath);
                new File(cachePath + "restore.zip").delete();
                File[] files = new File(cachePath).listFiles();
                if (files == null || files.length <= 0) {
                    handler.post(() -> restoreSettingsListener.failedWithWrongPassword(context.getString(R.string.restore_settings_failed_file_corrupted)));
                } else {
                    File restoreFilesDir = files[0];
                    File[] restoreFiles = restoreFilesDir.listFiles();
                    boolean result = true;
                    if (restoreFiles != null) {
                        SharedPreferences defaultPrefsPrivate = context.getSharedPreferences(SharedPreferencesUtils.DEFAULT_PREFERENCES_FILE, Context.MODE_PRIVATE);
                        for (File f : restoreFiles) {
                            if (f.isFile()) {
                                if (f.getName().equals(SharedPreferencesUtils.DEFAULT_PREFERENCES_FILE + "_private.txt")) {
                                    // Not `&&`: the import must run even if an earlier one failed.
                                    boolean imported = importSharedPreferencsFromFile(defaultPrefsPrivate, f.toString());
                                    result = result && imported;
                                } else if (f.getName().equals(SharedPreferencesUtils.DEFAULT_PREFERENCES_FILE + ".txt")) {
                                    // Not `&&`: the import must run even if an earlier one failed.
                                    boolean imported = importSharedPreferencsFromFile(defaultSharedPreferences, f.toString());
                                    result = result && imported;
                                } else if (f.getName().startsWith(CustomThemeSharedPreferencesUtils.LIGHT_THEME_SHARED_PREFERENCES_FILE)) {
                                    // Not `&&`: the import must run even if an earlier one failed.
                                    boolean imported = importSharedPreferencsFromFile(lightThemeSharedPreferences, f.toString());
                                    result = result && imported;
                                } else if (f.getName().startsWith(CustomThemeSharedPreferencesUtils.DARK_THEME_SHARED_PREFERENCES_FILE)) {
                                    // Not `&&`: the import must run even if an earlier one failed.
                                    boolean imported = importSharedPreferencsFromFile(darkThemeSharedPreferences, f.toString());
                                    result = result && imported;
                                } else if (f.getName().startsWith(CustomThemeSharedPreferencesUtils.AMOLED_THEME_SHARED_PREFERENCES_FILE)) {
                                    // Not `&&`: the import must run even if an earlier one failed.
                                    boolean imported = importSharedPreferencsFromFile(amoledThemeSharedPreferences, f.toString());
                                    result = result && imported;
                                } else if (f.getName().startsWith(SharedPreferencesUtils.SORT_TYPE_SHARED_PREFERENCES_FILE)) {
                                    // Not `&&`: the import must run even if an earlier one failed.
                                    boolean imported = importSharedPreferencsFromFile(sortTypeSharedPreferences, f.toString());
                                    result = result && imported;
                                } else if (f.getName().startsWith(SharedPreferencesUtils.POST_LAYOUT_SHARED_PREFERENCES_FILE)) {
                                    // Not `&&`: the import must run even if an earlier one failed.
                                    boolean imported = importSharedPreferencsFromFile(postLayoutSharedPreferences, f.toString());
                                    result = result && imported;
                                } else if (f.getName().startsWith(SharedPreferencesUtils.POST_DETAILS_SHARED_PREFERENCES_FILE)) {
                                    // Not `&&`: the import must run even if an earlier one failed.
                                    boolean imported = importSharedPreferencsFromFile(postDetailsSharedPreferences, f.toString());
                                    result = result && imported;
                                } else if (f.getName().startsWith(SharedPreferencesUtils.FRONT_PAGE_SCROLLED_POSITION_SHARED_PREFERENCES_FILE)) {
                                    // Not `&&`: the import must run even if an earlier one failed.
                                    boolean imported = importSharedPreferencsFromFile(postFeedScrolledPositionSharedPreferences, f.toString());
                                    result = result && imported;
                                } else if (f.getName().startsWith(SharedPreferencesUtils.MAIN_PAGE_TABS_SHARED_PREFERENCES_FILE)) {
                                    // Not `&&`: the import must run even if an earlier one failed.
                                    boolean imported = importSharedPreferencsFromFile(mainActivityTabsSharedPreferences, f.toString());
                                    result = result && imported;
                                } else if (f.getName().startsWith(SharedPreferencesUtils.PROXY_SHARED_PREFERENCES_FILE)) {
                                    // Not `&&`: the import must run even if an earlier one failed.
                                    boolean imported = importSharedPreferencsFromFile(proxySharedPreferences, f.toString());
                                    result = result && imported;
                                } else if (f.getName().startsWith(SharedPreferencesUtils.NSFW_AND_SPOILER_SHARED_PREFERENCES_FILE)) {
                                    // Not `&&`: the import must run even if an earlier one failed.
                                    boolean imported = importSharedPreferencsFromFile(nsfwAndSpoilerSharedPreferencs, f.toString());
                                    result = result && imported;
                                } else if (f.getName().startsWith(SharedPreferencesUtils.BOTTOM_APP_BAR_SHARED_PREFERENCES_FILE)) {
                                    // Not `&&`: the import must run even if an earlier one failed.
                                    boolean imported = importSharedPreferencsFromFile(bottomAppBarSharedPreferences, f.toString());
                                    result = result && imported;
                                } else if (f.getName().startsWith(SharedPreferencesUtils.POST_HISTORY_SHARED_PREFERENCES_FILE)) {
                                    // Not `&&`: the import must run even if an earlier one failed.
                                    boolean imported = importSharedPreferencsFromFile(postHistorySharedPreferences, f.toString());
                                    result = result && imported;
                                } else if (f.getName().startsWith(SharedPreferencesUtils.NAVIGATION_DRAWER_SHARED_PREFERENCES_FILE)) {
                                    // Not `&&`: the import must run even if an earlier one failed.
                                    boolean imported = importSharedPreferencsFromFile(navigationDrawerSharedPreferences, f.toString());
                                    result = result && imported;
                                }
                            } else if (f.isDirectory() && f.getName().equals("database")) {
                                if (!redditDataRoomDatabase.accountDao().isAnonymousAccountInserted()) {
                                    redditDataRoomDatabase.accountDao().insert(Account.getAnonymousAccount());
                                }

                                File anonymousSubscribedSubredditsFile = new File(f.getAbsolutePath() + "/anonymous_subscribed_subreddits.json");
                                File anonymousSubscribedUsersFile = new File(f.getAbsolutePath() + "/anonymous_subscribed_users.json");
                                File anonymousMultiredditsFile = new File(f.getAbsolutePath() + "/anonymous_multireddits.json");
                                File anonymousMultiredditSubredditsFile = new File(f.getAbsolutePath() + "/anonymous_multireddit_subreddits.json");
                                File customThemesFile = new File(f.getAbsolutePath() + "/custom_themes.json");
                                File postFiltersFile = new File(f.getAbsolutePath() + "/post_filters.json");
                                File postFilterUsageFile = new File(f.getAbsolutePath() + "/post_filter_usage.json");
                                File commentFiltersFile = new File(f.getAbsolutePath() + "/comment_filters.json");
                                File commentFilterUsageFile = new File(f.getAbsolutePath() + "/comment_filter_usage.json");
                                File accountsFile = new File(f.getAbsolutePath() + "/accounts.json");
                                File readPostsFile = new File(f.getAbsolutePath() + "/read_posts.json");
                                File localSavedFile = new File(f.getAbsolutePath() + "/local_saved.json");

                                if (anonymousSubscribedSubredditsFile.exists()) {
                                    List<SubscribedSubredditData> anonymousSubscribedSubreddits = getListFromFile(anonymousSubscribedSubredditsFile, new TypeToken<List<SubscribedSubredditData>>() {}.getType());
                                    redditDataRoomDatabase.subscribedSubredditDao().insertAll(anonymousSubscribedSubreddits);
                                }
                                if (anonymousSubscribedUsersFile.exists()) {
                                    List<SubscribedUserData> anonymousSubscribedUsers = getListFromFile(anonymousSubscribedUsersFile, new TypeToken<List<SubscribedUserData>>() {}.getType());
                                    redditDataRoomDatabase.subscribedUserDao().insertAll(anonymousSubscribedUsers);
                                }
                                if (anonymousMultiredditsFile.exists()) {
                                    List<MultiReddit> anonymousMultireddits = getListFromFile(anonymousMultiredditsFile, new TypeToken<List<MultiReddit>>() {}.getType());
                                    redditDataRoomDatabase.multiRedditDao().insertAll(anonymousMultireddits);

                                    if (anonymousMultiredditSubredditsFile.exists()) {
                                        List<AnonymousMultiredditSubreddit> anonymousMultiredditSubreddits = getListFromFile(anonymousMultiredditSubredditsFile, new TypeToken<List<AnonymousMultiredditSubreddit>>() {}.getType());
                                        redditDataRoomDatabase.anonymousMultiredditSubredditDao().insertAll(anonymousMultiredditSubreddits);
                                    }
                                }
                                if (customThemesFile.exists()) {
                                    List<CustomTheme> customThemes = getListFromFile(customThemesFile, new TypeToken<List<CustomTheme>>() {}.getType());
                                    restoreCustomThemes(context, redditDataRoomDatabase, customThemes);
                                }
                                if (postFiltersFile.exists()) {
                                    List<PostFilter> postFilters = getListFromFile(postFiltersFile, new TypeToken<List<PostFilter>>() {}.getType());
                                    redditDataRoomDatabase.postFilterDao().insertAll(postFilters);

                                    if (postFilterUsageFile.exists()) {
                                        List<PostFilterUsage> postFilterUsage = getListFromFile(postFilterUsageFile, new TypeToken<List<PostFilterUsage>>() {}.getType());
                                        redditDataRoomDatabase.postFilterUsageDao().insertAll(postFilterUsage);
                                    }
                                }
                                if (commentFiltersFile.exists()) {
                                    List<CommentFilter> commentFilters = getListFromFile(commentFiltersFile, new TypeToken<List<CommentFilter>>() {}.getType());
                                    redditDataRoomDatabase.commentFilterDao().insertAll(commentFilters);

                                    if (commentFilterUsageFile.exists()) {
                                        List<CommentFilterUsage> commentFilterUsage = getListFromFile(commentFilterUsageFile, new TypeToken<List<CommentFilterUsage>>() {}.getType());
                                        redditDataRoomDatabase.commentFilterUsageDao().insertAll(commentFilterUsage);
                                    }
                                }
                                if (accountsFile.exists()) {
                                    List<Account> accounts = getListFromFile(accountsFile, new TypeToken<List<Account>>() {}.getType());
                                    // Only replace local accounts when the backup actually has some; an empty
                                    // or unreadable accounts.json (now an empty list, not null) must not wipe them.
                                    if (!accounts.isEmpty()) {
                                        // Clear existing accounts before inserting restored ones
                                        redditDataRoomDatabase.accountDao().deleteAllAccounts();
                                        // Inserted rows keep the is_current_user flag from the backup, so more
                                        // than one account can come in marked current. Track which account should
                                        // be the current one, preferring the backed-up current user.
                                        Account currentAccount = null;
                                        for (Account account : accounts) {
                                            redditDataRoomDatabase.accountDao().insert(account);
                                            if (account.isCurrentUser()) {
                                                currentAccount = account;
                                            }
                                        }
                                        if (currentAccount == null && !accounts.isEmpty()) {
                                            currentAccount = accounts.get(0);
                                        }
                                        if (currentAccount != null) {
                                            // Reset every account's flag first so exactly one stays current;
                                            // otherwise non-current accounts are hidden from the account switcher.
                                            redditDataRoomDatabase.accountDao().markAllAccountsNonCurrent();
                                            redditDataRoomDatabase.accountDao().markAccountCurrent(currentAccount.getAccountName());
                                            // Also update the current account shared preferences for immediate effect
                                            currentAccountSharedPreferences.edit()
                                                .putString(SharedPreferencesUtils.ACCOUNT_NAME, currentAccount.getAccountName())
                                                .putString(SharedPreferencesUtils.ACCESS_TOKEN, currentAccount.getAccessToken())
                                                .putString(SharedPreferencesUtils.ACCOUNT_IMAGE_URL, currentAccount.getProfileImageUrl())
                                                .apply();
                                        }
                                    }
                                }
                                // Restore read_posts after accounts so the FK on username is satisfied.
                                if (readPostsFile.exists()) {
                                    List<ReadPost> readPosts = getListFromFile(readPostsFile, new TypeToken<List<ReadPost>>() {}.getType());
                                    if (!readPosts.isEmpty()) {
                                        redditDataRoomDatabase.readPostDao().insertAll(readPosts);
                                    }
                                }

                                // Restore local_saved after accounts so the FK on username is satisfied.
                                if (localSavedFile.exists()) {
                                    List<LocalSavedThing> localSaved = getListFromFile(localSavedFile, new TypeToken<List<LocalSavedThing>>() {}.getType());
                                    if (!localSaved.isEmpty()) {
                                        redditDataRoomDatabase.localSavedThingDao().insertAll(localSaved);
                                    }
                                }
                            }
                        }
                    } else {
                        handler.post(() -> restoreSettingsListener.failed(context.getString(R.string.restore_settings_failed_file_corrupted)));
                    }

                    FileUtils.deleteDirectory(new File(cachePath));

                    if (result) {
                        handler.post(() -> {
                            restoreSettingsListener.success();

                            try {
                                Thread.sleep(2000);
                            } catch (InterruptedException e) {
                                // Restore the interrupted status
                                Thread.currentThread().interrupt();
                                // Optionally log the interruption
                                android.util.Log.w("RestoreSettings", "Sleep interrupted before app restart", e);
                            }

                            // Trigger restart after posting success message
                            AppRestartHelper.triggerAppRestart(context);
                        });
                    } else {
                        handler.post(() -> restoreSettingsListener.failed(context.getString(R.string.restore_settings_partially_failed)));
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();

                if (e instanceof net.lingala.zip4j.exception.ZipException && e.getMessage() != null && e.getMessage().contains("Wrong Password")) {
                    handler.post(() -> restoreSettingsListener.failedWithWrongPassword(context.getString(R.string.restore_settings_failed_wrong_password)));
                } else {
                    handler.post(() -> restoreSettingsListener.failed(context.getString(R.string.restore_settings_partially_failed)));
                }
            }
        });
    }

    /**
     * Merges the backed up themes into whatever is already here. Restored themes carry their own
     * light/dark/amoled flags, so the matching flags have to be cleared first — two rows claiming a
     * slot make the LIMIT 1 lookups for it return whichever SQLite reaches first — but only for the
     * slots the backup actually fills, or a backup holding no theme for a slot would leave it empty
     * while its restored colours stay in the preferences.
     */
    private static void restoreCustomThemes(Context context, RedditDataRoomDatabase redditDataRoomDatabase,
                                            List<CustomTheme> customThemes) {
        CustomThemeDao customThemeDao = redditDataRoomDatabase.customThemeDao();
        String defaultThemeName = context.getString(R.string.theme_name_solarized_amoled);

        CustomTheme existingDefaultTheme = customThemeDao.getCustomTheme(defaultThemeName);

        boolean restoresLightTheme = false;
        boolean restoresDarkTheme = false;
        boolean restoresAmoledTheme = false;
        boolean restoresDefaultTheme = false;
        for (CustomTheme customTheme : customThemes) {
            restoresLightTheme |= customTheme.isLightTheme;
            restoresDarkTheme |= customTheme.isDarkTheme;
            restoresAmoledTheme |= customTheme.isAmoledTheme;
            restoresDefaultTheme |= defaultThemeName.equalsIgnoreCase(customTheme.name);
        }

        if (restoresLightTheme) {
            customThemeDao.unsetLightTheme();
        }
        if (restoresDarkTheme) {
            customThemeDao.unsetDarkTheme();
        }
        if (restoresAmoledTheme) {
            customThemeDao.unsetAmoledTheme();
        }
        customThemeDao.insertAll(customThemes);

        // The seeded theme is left over from this install's first launch, not something the user made
        // or backed up, so drop it once the restored themes have taken every slot it held. The backup
        // has no theme of that name here, so insertAll left the row alone and the slots it still holds
        // are the ones it came in with, minus whatever was just unset. Serializing to tell the seeded
        // row from a theme the user named the same is the expensive part, so it comes last, on the
        // only path that can delete anything.
        if (existingDefaultTheme != null && !restoresDefaultTheme
                && !(existingDefaultTheme.isLightTheme && !restoresLightTheme)
                && !(existingDefaultTheme.isDarkTheme && !restoresDarkTheme)
                && !(existingDefaultTheme.isAmoledTheme && !restoresAmoledTheme)
                && isSeededTheme(context, existingDefaultTheme)) {
            // Its stored name, not the resource string: the lookup above matches case-insensitively.
            customThemeDao.deleteCustomTheme(existingDefaultTheme.name);
        }
    }

    /**
     * Whether the row is the theme the first launch seeded rather than one the user wrote and gave the
     * same name. Its colours are what identify it: the slot flags are the part switching themes is
     * meant to change, so they are matched to the row before comparing rather than tested.
     */
    private static boolean isSeededTheme(Context context, CustomTheme customTheme) {
        CustomTheme seededTheme = CustomThemeWrapper.getSolarizedAmoled(context);
        seededTheme.isLightTheme = customTheme.isLightTheme;
        seededTheme.isDarkTheme = customTheme.isDarkTheme;
        seededTheme.isAmoledTheme = customTheme.isAmoledTheme;
        return customTheme.getJSONModel().equals(seededTheme.getJSONModel());
    }

    private static boolean importSharedPreferencsFromFile(SharedPreferences sharedPreferences, String uriString) {
        boolean result = false;
        ObjectInputStream input = null;

        try {
            input = new ObjectInputStream(new FileInputStream(uriString));
            Object object = input.readObject();
            if (object instanceof Map) {
                Map<String, Object> map = (Map<String, Object>) object;
                Set<Map.Entry<String, Object>> entrySet = map.entrySet();
                SharedPreferences.Editor editor = sharedPreferences.edit();
                for (Map.Entry<String, Object> e : entrySet) {
                    if (e.getValue() instanceof String) {
                        editor.putString(e.getKey(), (String) e.getValue());
                    } else if (e.getValue() instanceof Integer) {
                        editor.putInt(e.getKey(), (Integer) e.getValue());
                    } else if (e.getValue() instanceof Float) {
                        editor.putFloat(e.getKey(), (Float) e.getValue());
                    } else if (e.getValue() instanceof Boolean) {
                        editor.putBoolean(e.getKey(), (Boolean) e.getValue());
                    } else if (e.getValue() instanceof Long) {
                        editor.putLong(e.getKey(), (Long) e.getValue());
                    }
                }

                editor.apply();

                result = true;
            }
        } catch (IOException | ClassNotFoundException e) {
            Log.e("RestoreSettings", "importSharedPreferencsFromFile failed", e);
        } finally {
            try {
                if (input != null) {
                    input.close();
                }
            } catch (IOException ex) {
                Log.e("RestoreSettings", "importSharedPreferencsFromFile failed", ex);
            }
        }
        return result;
    }

    private static <T> List<T> getListFromFile(File file, Type dataType) {
        try (JsonReader reader = new JsonReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            Gson gson = new Gson();
            List<T> result = gson.fromJson(reader, dataType);
            if (result != null) {
                return result;
            }
        } catch (IOException e) {
            Log.e("RestoreSettings", "getListFromFile failed", e);
        }

        return Collections.emptyList();
    }

    public interface RestoreSettingsListener {
        void success();
        void failed(String errorMessage);
        void failedWithWrongPassword(String errorMessage);
    }
}
