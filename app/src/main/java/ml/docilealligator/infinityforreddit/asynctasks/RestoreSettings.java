package ml.docilealligator.infinityforreddit.asynctasks;

import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Handler;
import android.util.Log;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import ml.docilealligator.infinityforreddit.R;
import ml.docilealligator.infinityforreddit.RedditDataRoomDatabase;
import ml.docilealligator.infinityforreddit.account.Account;
import ml.docilealligator.infinityforreddit.account.AccountSettingsMigration;
import ml.docilealligator.infinityforreddit.comment.CommentDraft;
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
import ml.docilealligator.infinityforreddit.recentlyvisited.RecentlyVisited;
import ml.docilealligator.infinityforreddit.recentsearchquery.RecentSearchQuery;
import ml.docilealligator.infinityforreddit.reminder.Reminder;
import ml.docilealligator.infinityforreddit.subscribedsubreddit.SubscribedSubredditData;
import ml.docilealligator.infinityforreddit.subscribeduser.SubscribedUserData;
import ml.docilealligator.infinityforreddit.utils.AppRestartHelper;
import ml.docilealligator.infinityforreddit.utils.CustomThemeSharedPreferencesUtils;
import ml.docilealligator.infinityforreddit.utils.SharedPreferencesUtils;
import ml.docilealligator.infinityforreddit.utils.Utils;
import net.lingala.zip4j.ZipFile;
import org.apache.commons.io.FileUtils;

/**
 * Puts an encrypted backup zip back, preference file by preference file and table by table.
 *
 * Like {@link BackupSettings} this opens the account-scoped files itself instead of taking them
 * injected: writing a backed up key through {@code AccountScopedSharedPreferences} would scope it a
 * second time, onto whichever account happens to be signed in when the restore runs.
 */
public class RestoreSettings {
    public static void restoreSettings(Context context, Executor executor, Handler handler,
                                ContentResolver contentResolver, Uri zipFileUri,
                                String password,
                                RedditDataRoomDatabase redditDataRoomDatabase,
                                SharedPreferences currentAccountSharedPreferences,
                                SharedPreferences lightThemeSharedPreferences,
                                SharedPreferences darkThemeSharedPreferences,
                                SharedPreferences amoledThemeSharedPreferences,
                                SharedPreferences postFeedScrolledPositionSharedPreferences,
                                SharedPreferences mainActivityTabsSharedPreferences,
                                SharedPreferences proxySharedPreferences,
                                SharedPreferences nsfwAndSpoilerSharedPreferencs,
                                SharedPreferences postHistorySharedPreferences,
                                SharedPreferences recentlyVisitedSharedPreferences,
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
                    // What a failure ends on. Null means the generic "partially failed": some file
                    // in an otherwise readable archive did not import.
                    String failureMessage = null;
                    if (restoreFiles != null) {
                        // The account-scoped files, past the façade: a backed up key already
                        // carries its account, and writing it through the injected instance would
                        // scope it again onto whoever is signed in now.
                        SharedPreferences defaultSharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);

                        // Exact names, not prefixes. The backup writes one file per preference file,
                        // named after it; matching on a prefix would pour a file named after an
                        // existing one into the wrong preferences.
                        Map<String, SharedPreferences> targets = new HashMap<>();
                        targets.put(SharedPreferencesUtils.DEFAULT_PREFERENCES_FILE, defaultSharedPreferences);
                        targets.put(SharedPreferencesUtils.DEFAULT_PREFERENCES_FILE + "_private",
                                context.getSharedPreferences(SharedPreferencesUtils.DEFAULT_PREFERENCES_FILE, Context.MODE_PRIVATE));
                        targets.put(CustomThemeSharedPreferencesUtils.LIGHT_THEME_SHARED_PREFERENCES_FILE, lightThemeSharedPreferences);
                        targets.put(CustomThemeSharedPreferencesUtils.DARK_THEME_SHARED_PREFERENCES_FILE, darkThemeSharedPreferences);
                        targets.put(CustomThemeSharedPreferencesUtils.AMOLED_THEME_SHARED_PREFERENCES_FILE, amoledThemeSharedPreferences);
                        targets.put(SharedPreferencesUtils.SORT_TYPE_SHARED_PREFERENCES_FILE,
                                rawFile(context, SharedPreferencesUtils.SORT_TYPE_SHARED_PREFERENCES_FILE));
                        targets.put(SharedPreferencesUtils.POST_LAYOUT_SHARED_PREFERENCES_FILE,
                                rawFile(context, SharedPreferencesUtils.POST_LAYOUT_SHARED_PREFERENCES_FILE));
                        targets.put(SharedPreferencesUtils.POST_DETAILS_SHARED_PREFERENCES_FILE,
                                rawFile(context, SharedPreferencesUtils.POST_DETAILS_SHARED_PREFERENCES_FILE));
                        targets.put(SharedPreferencesUtils.NAVIGATION_DRAWER_SHARED_PREFERENCES_FILE,
                                rawFile(context, SharedPreferencesUtils.NAVIGATION_DRAWER_SHARED_PREFERENCES_FILE));
                        targets.put(SharedPreferencesUtils.FRONT_PAGE_SCROLLED_POSITION_SHARED_PREFERENCES_FILE, postFeedScrolledPositionSharedPreferences);
                        targets.put(SharedPreferencesUtils.MAIN_PAGE_TABS_SHARED_PREFERENCES_FILE, mainActivityTabsSharedPreferences);
                        targets.put(SharedPreferencesUtils.PROXY_SHARED_PREFERENCES_FILE, proxySharedPreferences);
                        targets.put(SharedPreferencesUtils.NSFW_AND_SPOILER_SHARED_PREFERENCES_FILE, nsfwAndSpoilerSharedPreferencs);
                        targets.put(SharedPreferencesUtils.BOTTOM_APP_BAR_SHARED_PREFERENCES_FILE,
                                rawFile(context, SharedPreferencesUtils.BOTTOM_APP_BAR_SHARED_PREFERENCES_FILE));
                        targets.put(SharedPreferencesUtils.POST_HISTORY_SHARED_PREFERENCES_FILE, postHistorySharedPreferences);
                        targets.put(SharedPreferencesUtils.RECENTLY_VISITED_SHARED_PREFERENCES_FILE, recentlyVisitedSharedPreferences);

                        Map<String, Object> restoredDefaultPreferences = null;

                        for (File f : restoreFiles) {
                            if (f.isFile()) {
                                String name = f.getName();
                                if (!name.endsWith(BackupSettings.PREFERENCES_FILE_SUFFIX)) {
                                    continue;
                                }
                                String backedUpFile = name.substring(0,
                                        name.length() - BackupSettings.PREFERENCES_FILE_SUFFIX.length());
                                SharedPreferences target = targets.get(backedUpFile);
                                if (target == null) {
                                    continue;
                                }

                                Map<String, Object> imported = importSharedPreferencesFromFile(target, f.toString());
                                // Read into a local first: every file must be imported even after
                                // one has failed.
                                result = result && imported != null;
                                if (imported != null
                                        && SharedPreferencesUtils.DEFAULT_PREFERENCES_FILE.equals(backedUpFile)) {
                                    restoredDefaultPreferences = imported;
                                }
                            } else if (f.isDirectory() && f.getName().equals("database")) {
                                // A backup taken before the anonymous account was renamed holds its
                                // rows under "-", and every one of them has a foreign key onto an
                                // accounts row of that name. Nothing creates it any more, so the
                                // inserts below would fail the constraint; this gives them a parent
                                // to land on, and renameAnonymousAccount() moves them off it and
                                // drops it once they are all in.
                                redditDataRoomDatabase.getOpenHelper().getWritableDatabase().execSQL(
                                        "INSERT OR IGNORE INTO accounts (username, karma, is_current_user, is_mod)"
                                                + " VALUES ('-', 0, 0, 0)");

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
                                File commentDraftsFile = new File(f.getAbsolutePath() + "/comment_drafts.json");
                                File remindersFile = new File(f.getAbsolutePath() + "/reminders.json");
                                File recentlyVisitedFile = new File(f.getAbsolutePath() + "/recently_visited.json");
                                File recentSearchQueriesFile = new File(f.getAbsolutePath() + "/recent_search_queries.json");

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
                                // Filters after accounts, though nothing here has a foreign key on
                                // one: a backup taken before a filter had an owner has to be shared
                                // out among the accounts, and that list is only right once the block
                                // above has restored them.
                                if (postFiltersFile.exists()) {
                                    List<PostFilter> postFilters = getListFromFile(postFiltersFile, new TypeToken<List<PostFilter>>() {}.getType());
                                    List<PostFilterUsage> postFilterUsage = postFilterUsageFile.exists()
                                            ? getListFromFile(postFilterUsageFile, new TypeToken<List<PostFilterUsage>>() {}.getType())
                                            : Collections.emptyList();
                                    if (namesAccounts(postFiltersFile)) {
                                        redditDataRoomDatabase.postFilterDao().insertAll(postFilters);
                                        redditDataRoomDatabase.postFilterUsageDao().insertAll(postFilterUsage);
                                    } else {
                                        for (String username : accountNames(redditDataRoomDatabase)) {
                                            for (PostFilter postFilter : postFilters) {
                                                postFilter.username = username;
                                            }
                                            redditDataRoomDatabase.postFilterDao().insertAll(postFilters);
                                            for (PostFilterUsage usage : postFilterUsage) {
                                                usage.username = username;
                                            }
                                            redditDataRoomDatabase.postFilterUsageDao().insertAll(postFilterUsage);
                                        }
                                    }
                                }
                                if (commentFiltersFile.exists()) {
                                    List<CommentFilter> commentFilters = getListFromFile(commentFiltersFile, new TypeToken<List<CommentFilter>>() {}.getType());
                                    List<CommentFilterUsage> commentFilterUsage = commentFilterUsageFile.exists()
                                            ? getListFromFile(commentFilterUsageFile, new TypeToken<List<CommentFilterUsage>>() {}.getType())
                                            : Collections.emptyList();
                                    if (namesAccounts(commentFiltersFile)) {
                                        redditDataRoomDatabase.commentFilterDao().insertAll(commentFilters);
                                        redditDataRoomDatabase.commentFilterUsageDao().insertAll(commentFilterUsage);
                                    } else {
                                        for (String username : accountNames(redditDataRoomDatabase)) {
                                            for (CommentFilter commentFilter : commentFilters) {
                                                commentFilter.username = username;
                                            }
                                            redditDataRoomDatabase.commentFilterDao().insertAll(commentFilters);
                                            for (CommentFilterUsage usage : commentFilterUsage) {
                                                usage.username = username;
                                            }
                                            redditDataRoomDatabase.commentFilterUsageDao().insertAll(commentFilterUsage);
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

                                // Restore comment_drafts after accounts so the FK on username is satisfied.
                                if (commentDraftsFile.exists()) {
                                    List<CommentDraft> commentDrafts = getListFromFile(commentDraftsFile, new TypeToken<List<CommentDraft>>() {}.getType());
                                    if (!commentDrafts.isEmpty()) {
                                        redditDataRoomDatabase.commentDraftDao().insertAll(commentDrafts);
                                    }
                                }

                                // Restore reminders after accounts so the FK on username is satisfied.
                                if (remindersFile.exists()) {
                                    List<Reminder> reminders = getListFromFile(remindersFile, new TypeToken<List<Reminder>>() {}.getType());
                                    if (!reminders.isEmpty()) {
                                        redditDataRoomDatabase.reminderDao().insertAll(reminders);
                                    }
                                }

                                // Recently visited and search history hang off accounts by an
                                // ON DELETE CASCADE, so clearing the accounts above wiped whatever
                                // was here. They have to come back from the backup -- nothing on
                                // Reddit can re-sync them -- and, like the tables above, only after
                                // the accounts they reference exist again.
                                if (recentlyVisitedFile.exists()) {
                                    List<RecentlyVisited> recentlyVisited = getListFromFile(recentlyVisitedFile, new TypeToken<List<RecentlyVisited>>() {}.getType());
                                    if (!recentlyVisited.isEmpty()) {
                                        redditDataRoomDatabase.recentlyVisitedDao().insertAll(recentlyVisited);
                                    }
                                }

                                if (recentSearchQueriesFile.exists()) {
                                    List<RecentSearchQuery> recentSearchQueries = getListFromFile(recentSearchQueriesFile, new TypeToken<List<RecentSearchQuery>>() {}.getType());
                                    if (!recentSearchQueries.isEmpty()) {
                                        redditDataRoomDatabase.recentSearchQueryDao().insertAll(recentSearchQueries);
                                    }
                                }

                                // Everything is in. Move whatever landed on the placeholder above
                                // onto the name the app actually reads, and drop the placeholder.
                                // Here rather than at the end of the restore: it is these rows that
                                // can carry the old spelling, and a backup with no database
                                // directory has nothing to rename.
                                RedditDataRoomDatabase.renameAnonymousAccount(
                                        redditDataRoomDatabase.getOpenHelper().getWritableDatabase());
                            }
                        }

                        // A backup taken before the key scheme holds per-account settings under
                        // their old global spellings, which nothing reads any more. Letting the
                        // migration run once more over the restored files is what turns them back
                        // into settings; on a backup that already has them it finds nothing to do.
                        AccountSettingsMigration.rerunIfBackupPredatesAccountScope(
                                rawFile(context, SharedPreferencesUtils.INTERNAL_SHARED_PREFERENCES_FILE),
                                restoredDefaultPreferences);
                    } else {
                        // Nothing was read, so nothing was restored. Without this the success branch
                        // below would fire too, toasting success and restarting the app on a restore
                        // that put nothing back.
                        result = false;
                        failureMessage = context.getString(R.string.restore_settings_failed_file_corrupted);
                    }

                    FileUtils.deleteDirectory(new File(cachePath));

                    if (result) {
                        handler.post(() -> {
                            restoreSettingsListener.success();

                            // Delayed rather than slept: this runs on the main thread, so sleeping
                            // here would freeze input and rendering for the whole two seconds the
                            // success toast is meant to be readable in.
                            handler.postDelayed(() -> AppRestartHelper.triggerAppRestart(context), 2000);
                        });
                    } else {
                        String message = failureMessage != null ? failureMessage
                                : context.getString(R.string.restore_settings_partially_failed);
                        handler.post(() -> restoreSettingsListener.failed(message));
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

    /** The file itself, past any account scoping the injected instance would apply. */
    private static SharedPreferences rawFile(Context context, String fileName) {
        return context.getSharedPreferences(fileName, Context.MODE_PRIVATE);
    }

    /**
     * Reads one backed up preference file into {@code sharedPreferences} and returns what it held,
     * or null if it could not be read.
     *
     * Writes with commit(): a restore ends by killing the process, and apply() only promises that
     * the write reaches disk eventually.
     */
    @SuppressLint("ApplySharedPref")
    @Nullable
    private static Map<String, Object> importSharedPreferencesFromFile(SharedPreferences sharedPreferences,
                                                                       String uriString) {
        try (ObjectInputStream input = new ObjectInputStream(new FileInputStream(uriString))) {
            Object object = input.readObject();
            if (!(object instanceof Map)) {
                return null;
            }

            @SuppressWarnings("unchecked")
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
            editor.commit();

            return map;
        } catch (IOException | ClassNotFoundException | RuntimeException e) {
            // RuntimeException too: the map is cast unchecked, so a backup whose serialized keys
            // are not Strings throws ClassCastException out of putString. Escaping the task would
            // leave the screen with no toast, no restart and a half-written preferences file;
            // counted as a failed import instead, like any other unreadable file.
            Log.e("RestoreSettings", "importSharedPreferencesFromFile failed", e);
            return null;
        }
    }

    /**
     * Whether a backed up filter file says which account each filter belongs to.
     *
     * Backups taken before filters had an owner do not, and Gson fills the field in from its own
     * initialiser rather than leaving it null, so the JSON is the only place the difference shows.
     * An unreadable or empty file is treated as saying so: there is then nothing to share out.
     */
    private static boolean namesAccounts(File file) {
        try (JsonReader reader = new JsonReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            JsonElement parsed = JsonParser.parseReader(reader);
            if (!parsed.isJsonArray() || parsed.getAsJsonArray().size() == 0) {
                return true;
            }
            JsonElement first = parsed.getAsJsonArray().get(0);
            return !first.isJsonObject() || first.getAsJsonObject().has("username");
        } catch (Exception e) {
            Log.e("RestoreSettings", "namesAccounts failed", e);
            return true;
        }
    }

    /** Every account the database holds, anonymous included. */
    private static List<String> accountNames(RedditDataRoomDatabase redditDataRoomDatabase) {
        List<String> names = new ArrayList<>();
        names.add(Account.ANONYMOUS_ACCOUNT);
        for (Account account : redditDataRoomDatabase.accountDao().getAllAccounts()) {
            names.add(account.getAccountName());
        }
        return names;
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
