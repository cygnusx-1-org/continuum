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
import ml.docilealligator.infinityforreddit.utils.SharedGson;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import ml.docilealligator.infinityforreddit.R;
import ml.docilealligator.infinityforreddit.RedditDataRoomDatabase;
import ml.docilealligator.infinityforreddit.account.Account;
import ml.docilealligator.infinityforreddit.account.AccountSettingsMigration;
import ml.docilealligator.infinityforreddit.account.AnonymousAccountRename;
import ml.docilealligator.infinityforreddit.comment.AnonymousSavedComment;
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
import ml.docilealligator.infinityforreddit.recentsearchquery.RecentSearchQuery;
import ml.docilealligator.infinityforreddit.reminder.Reminder;
import ml.docilealligator.infinityforreddit.resume.ResumeState;
import ml.docilealligator.infinityforreddit.subscribedsubreddit.SubscribedSubredditData;
import ml.docilealligator.infinityforreddit.subscribeduser.SubscribedUserData;
import ml.docilealligator.infinityforreddit.utils.AppRestartHelper;
import ml.docilealligator.infinityforreddit.utils.CustomThemeSharedPreferencesUtils;
import ml.docilealligator.infinityforreddit.utils.SharedPreferencesUtils;
import ml.docilealligator.infinityforreddit.utils.SwipeActionSideMigration;
import ml.docilealligator.infinityforreddit.utils.Utils;
import net.lingala.zip4j.ZipFile;
import org.apache.commons.io.FileUtils;

/**
 * Puts a backup zip back, preference file by preference file, table by table and font by font.
 *
 * A preference file in the backup replaces the file on this device, key for key: a setting the
 * other device left at its default is absent from the backup, and merging would keep this device's
 * own value for it, so the two devices would never match. The exceptions are the keys in
 * {@link BackupSettings#DEVICE_BOUND_KEYS}, which stay as this device has them.
 *
 * Like {@link BackupSettings} this opens the account-scoped files itself instead of taking them
 * injected: writing a backed up key through {@code AccountScopedSharedPreferences} would scope it a
 * second time, onto whichever account happens to be signed in when the restore runs.
 */
public class RestoreSettings {
    public static void restoreSettings(Context context, Executor executor, Handler handler,
                                ContentResolver contentResolver, Uri zipFileUri,
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
                                RestoreSettingsListener restoreSettingsListener) {
        executor.execute(() -> {
            try (InputStream zipFileInputStream = contentResolver.openInputStream(zipFileUri)) {
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

                // Both the copy target and the archive are closed as soon as they are done with:
                // the restore below is long, and a half-written cache file would be extracted.
                try (FileOutputStream zipCacheOutputStream = new FileOutputStream(cachePath + "restore.zip")) {
                    byte[] fileReader = new byte[1024];

                    while (true) {
                        int read = zipFileInputStream.read(fileReader);

                        if (read == -1) {
                            break;
                        }

                        zipCacheOutputStream.write(fileReader, 0, read);
                    }
                }

                try (ZipFile zipFile = new ZipFile(cachePath + "restore.zip")) {
                    zipFile.extractAll(cachePath);
                }
                new File(cachePath + "restore.zip").delete();
                File[] files = new File(cachePath).listFiles();
                if (files == null || files.length <= 0) {
                    handler.post(() -> restoreSettingsListener.failed(context.getString(R.string.restore_settings_failed_file_corrupted)));
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
                        // The files nothing injects: opened raw, each under its own name.
                        for (String rawName : new String[]{
                                SharedPreferencesUtils.SORT_TYPE_SHARED_PREFERENCES_FILE,
                                SharedPreferencesUtils.POST_LAYOUT_SHARED_PREFERENCES_FILE,
                                SharedPreferencesUtils.POST_DETAILS_SHARED_PREFERENCES_FILE,
                                SharedPreferencesUtils.NAVIGATION_DRAWER_SHARED_PREFERENCES_FILE,
                                SharedPreferencesUtils.BOTTOM_APP_BAR_SHARED_PREFERENCES_FILE,
                                SharedPreferencesUtils.USER_TAGS_SHARED_PREFERENCES_FILE,
                                SharedPreferencesUtils.SECURITY_SHARED_PREFERENCES_FILE}) {
                            targets.put(rawName, rawFile(context, rawName));
                        }
                        targets.put(SharedPreferencesUtils.FRONT_PAGE_SCROLLED_POSITION_SHARED_PREFERENCES_FILE, postFeedScrolledPositionSharedPreferences);
                        targets.put(SharedPreferencesUtils.MAIN_PAGE_TABS_SHARED_PREFERENCES_FILE, mainActivityTabsSharedPreferences);
                        targets.put(SharedPreferencesUtils.PROXY_SHARED_PREFERENCES_FILE, proxySharedPreferences);
                        targets.put(SharedPreferencesUtils.NSFW_AND_SPOILER_SHARED_PREFERENCES_FILE, nsfwAndSpoilerSharedPreferencs);
                        targets.put(SharedPreferencesUtils.POST_HISTORY_SHARED_PREFERENCES_FILE, postHistorySharedPreferences);

                        // What each file held, by the name the backup gave it.
                        Map<String, Map<String, Object>> restoredFiles = new HashMap<>();
                        // Whether the backup has an accounts.json at all, and whether accounts from
                        // it are now the ones on this device. Infinity's has neither.
                        boolean backupHasAccountsFile = false;
                        boolean restoredAccounts = false;

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
                                if (imported != null) {
                                    restoredFiles.put(backedUpFile, imported);
                                }
                            } else if (f.isDirectory() && f.getName().equals(BackupSettings.FONTS_DIRECTORY)) {
                                result = restoreFonts(context, f) && result;
                            } else if (f.isDirectory() && f.getName().equals("database")) {
                                backupHasAccountsFile = new File(f, "accounts.json").exists();
                                // All of it or none of it. Written table by table with nothing
                                // around it, a restore that failed part of the way through kept
                                // whatever it had reached -- the local accounts already deleted,
                                // rows parked on the placeholder account -- and a backup taken
                                // afterwards carried that along. Caught for the same reason: a
                                // constraint or a malformed file escaping the executor was a crash.
                                try {
                                    Account currentAccount = redditDataRoomDatabase.runInTransaction(
                                            () -> restoreDatabase(context, redditDataRoomDatabase, f));
                                    if (currentAccount != null) {
                                        restoredAccounts = true;
                                        // Also update the current account shared preferences for
                                        // immediate effect. Only once committed: written inside the
                                        // transaction, a rollback would leave them naming an
                                        // account that is not there.
                                        currentAccountSharedPreferences.edit()
                                            .putString(SharedPreferencesUtils.ACCOUNT_NAME, currentAccount.getAccountName())
                                            .putString(SharedPreferencesUtils.ACCESS_TOKEN, currentAccount.getAccessToken())
                                            .putString(SharedPreferencesUtils.ACCOUNT_IMAGE_URL, currentAccount.getProfileImageUrl())
                                            .apply();
                                    }
                                } catch (RuntimeException e) {
                                    Log.e("RestoreSettings", "restoring the database failed", e);
                                    result = false;
                                }
                            }
                        }

                        // A backup from before the swipe keys named the side of the row holds them
                        // in the old sense; only the keys it brought in are turned over. Before the
                        // step below, which copies them on.
                        SwipeActionSideMigration.flipRestoredKeys(defaultSharedPreferences,
                                restoredFiles.get(SharedPreferencesUtils.DEFAULT_PREFERENCES_FILE));

                        // A backup taken before the key scheme -- Infinity's among them -- holds
                        // per-account settings under their old global spellings, which nothing reads
                        // any more. This turns them back into settings; on a backup that already has
                        // them it finds nothing to do. Infinity's bottom app bar options also predate
                        // Random being dropped, as does any backup too old to hold accounts.
                        AccountSettingsMigration.adoptRestoredBackup(
                                rawFile(context, SharedPreferencesUtils.INTERNAL_SHARED_PREFERENCES_FILE),
                                restoredFiles, targets, restoredAccounts, !backupHasAccountsFile);

                        // A feed cached before the restore was built from the subscriptions and
                        // settings it has just replaced, and reopening it shows the old ones' posts
                        // until a refresh. A backup that brought accounts replaced them all; one that
                        // brought none -- every Infinity backup -- replaced only anonymous's.
                        if (result) {
                            if (restoredAccounts) {
                                ResumeState.clearAll(context);
                            } else {
                                ResumeState.clearAccount(context, Account.ANONYMOUS_ACCOUNT);
                            }
                        }
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

                handler.post(() -> restoreSettingsListener.failed(context.getString(R.string.restore_settings_partially_failed)));
            }
        });
    }

    /**
     * Puts the backup's {@code database} directory back, table by table.
     *
     * Runs inside the caller's transaction, so it writes nothing the transaction cannot take back:
     * the account the backup makes current is returned rather than stored, for the caller to store
     * once the rows are committed. Null when the backup names no accounts, which leaves the current
     * one as it is.
     */
    @Nullable
    private static Account restoreDatabase(Context context, RedditDataRoomDatabase redditDataRoomDatabase,
                                           File databaseDir) {
        // A backup taken before the anonymous account was renamed holds its rows under "-", and
        // every one of them has a foreign key onto an accounts row of that name. Nothing creates it
        // any more, so the inserts below would fail the constraint; this gives them a parent to land
        // on, and renameAnonymousAccount() moves them off it and drops it once they are all in.
        redditDataRoomDatabase.getOpenHelper().getWritableDatabase().execSQL(
                "INSERT OR IGNORE INTO accounts (username, karma, is_current_user, is_mod)"
                        + " VALUES ('-', 0, 0, 0)");

        File anonymousSubscribedSubredditsFile = new File(databaseDir, "anonymous_subscribed_subreddits.json");
        File anonymousSubscribedUsersFile = new File(databaseDir, "anonymous_subscribed_users.json");
        File anonymousMultiredditsFile = new File(databaseDir, "anonymous_multireddits.json");
        File anonymousMultiredditSubredditsFile = new File(databaseDir, "anonymous_multireddit_subreddits.json");
        File customThemesFile = new File(databaseDir, "custom_themes.json");
        File postFiltersFile = new File(databaseDir, "post_filters.json");
        File postFilterUsageFile = new File(databaseDir, "post_filter_usage.json");
        File commentFiltersFile = new File(databaseDir, "comment_filters.json");
        File commentFilterUsageFile = new File(databaseDir, "comment_filter_usage.json");
        File accountsFile = new File(databaseDir, "accounts.json");
        File readPostsFile = new File(databaseDir, "read_posts.json");
        File localSavedFile = new File(databaseDir, "local_saved.json");
        File anonymousSavedCommentsFile = new File(databaseDir, "anonymous_saved_comments.json");
        File commentDraftsFile = new File(databaseDir, "comment_drafts.json");
        File remindersFile = new File(databaseDir, "reminders.json");
        File recentSearchQueriesFile = new File(databaseDir, "recent_search_queries.json");

        if (anonymousSubscribedSubredditsFile.exists()) {
            List<SubscribedSubredditData> anonymousSubscribedSubreddits = getListFromFile(anonymousSubscribedSubredditsFile, new TypeToken<List<SubscribedSubredditData>>() {}.getType());
            redditDataRoomDatabase.subscribedSubredditDao().insertAll(anonymousSubscribedSubreddits);
        }
        if (anonymousSubscribedUsersFile.exists()) {
            List<SubscribedUserData> anonymousSubscribedUsers = getListFromFile(anonymousSubscribedUsersFile, new TypeToken<List<SubscribedUserData>>() {}.getType());
            // Taken before a user could be saved without being followed -- every Infinity backup --
            // a row was a follow and says nothing more. Gson leaves the missing flag false, and a row
            // neither followed nor saved is listed while every screen offers to follow them.
            if (!rowsHave(anonymousSubscribedUsersFile, "followed")) {
                for (SubscribedUserData user : anonymousSubscribedUsers) {
                    user.setFollowed(true);
                }
            }
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
            // Taken before each type of post had a colour of its own -- every Infinity backup -- a
            // theme has none, and Gson leaves an int it does not find at 0, which is transparent.
            // The six came in together, so one stands for them all.
            if (!rowsHave(customThemesFile, "imageTypeBackgroundColor")) {
                fillPostTypeColors(context, customThemes);
            }
            restoreCustomThemes(context, redditDataRoomDatabase, customThemes);
        }
        Account currentAccount = null;
        if (accountsFile.exists()) {
            List<Account> backedUpAccounts = getListFromFile(accountsFile, new TypeToken<List<Account>>() {}.getType());
            // A backup taken after a restore had crashed carries that restore's placeholder as if
            // it were an account. It is not one -- this restore has made its own -- and let through
            // it could be the one made current.
            List<Account> accounts = new ArrayList<>();
            for (Account account : backedUpAccounts) {
                if (!AnonymousAccountRename.LEGACY_ANONYMOUS_ACCOUNT.equals(account.getAccountName())) {
                    accounts.add(account);
                }
            }
            // Only replace local accounts when the backup actually has some; an empty
            // or unreadable accounts.json (now an empty list, not null) must not wipe them.
            if (!accounts.isEmpty()) {
                // Clear existing accounts before inserting restored ones
                redditDataRoomDatabase.accountDao().deleteAllAccounts();
                // Inserted rows keep the is_current_user flag from the backup, so more
                // than one account can come in marked current. Track which account should
                // be the current one, preferring the backed-up current user.
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
                }
            }
        }
        // Filters after accounts, though nothing here has a foreign key on
        // one: a backup taken before a filter had an owner has to be shared
        // out among the accounts, and that list is only right once the block
        // above has restored them. Only the backup's own accounts, though: one
        // that brought none -- every Infinity backup -- gives its filters to
        // anonymous alone, like its settings, and the accounts already on this
        // device keep theirs rather than have a filter of the same name replaced.
        List<String> filterOwners = currentAccount != null
                ? accountNames(redditDataRoomDatabase)
                : Collections.singletonList(Account.ANONYMOUS_ACCOUNT);
        restoreFilters(postFiltersFile, postFilterUsageFile,
                new TypeToken<List<PostFilter>>() {}, new TypeToken<List<PostFilterUsage>>() {},
                filterOwners, (filter, owner) -> filter.username = owner, (usage, owner) -> usage.username = owner,
                redditDataRoomDatabase.postFilterDao()::insertAll, redditDataRoomDatabase.postFilterUsageDao()::insertAll);
        restoreFilters(commentFiltersFile, commentFilterUsageFile,
                new TypeToken<List<CommentFilter>>() {}, new TypeToken<List<CommentFilterUsage>>() {},
                filterOwners, (filter, owner) -> filter.username = owner, (usage, owner) -> usage.username = owner,
                redditDataRoomDatabase.commentFilterDao()::insertAll, redditDataRoomDatabase.commentFilterUsageDao()::insertAll);
        // Everything below has a foreign key onto an account, and a backup can name accounts it
        // does not bring back: Infinity's holds read posts and reminders for every account it had
        // and no accounts at all. One such row failed its key, and took the whole database restore
        // back with it -- anonymous subscriptions, multireddits, themes and filters included.
        Set<String> accountsInDatabase = namesInAccountsTable(redditDataRoomDatabase);

        // Each of these has a foreign key on username, so they go in after the accounts.
        restoreRows(readPostsFile, new TypeToken<List<ReadPost>>() {},
                rows -> onAccountsInDatabase(rows, ReadPost::getUsername, accountsInDatabase, "read_posts"),
                redditDataRoomDatabase.readPostDao()::insertAll);
        restoreRows(localSavedFile, new TypeToken<List<LocalSavedThing>>() {},
                rows -> onAccountsInDatabase(rows, LocalSavedThing::getUsername, accountsInDatabase, "local_saved"),
                redditDataRoomDatabase.localSavedThingDao()::insertAll);
        restoreRows(anonymousSavedCommentsFile, new TypeToken<List<AnonymousSavedComment>>() {},
                rows -> onAccountsInDatabase(rows, AnonymousSavedComment::getUsername, accountsInDatabase, "anonymous_saved_comments"),
                redditDataRoomDatabase.anonymousSavedCommentDao()::insertAll);
        restoreRows(commentDraftsFile, new TypeToken<List<CommentDraft>>() {},
                rows -> rows,
                redditDataRoomDatabase.commentDraftDao()::insertAll);
        restoreRows(remindersFile, new TypeToken<List<Reminder>>() {},
                rows -> withoutAbsentAccounts(rows, accountsInDatabase),
                redditDataRoomDatabase.reminderDao()::insertAll);

        // Recently visited and search history hang off accounts by an
        // ON DELETE CASCADE, so clearing the accounts above wiped whatever
        // was here. They have to come back from the backup -- nothing on
        // Reddit can re-sync them -- and, like the tables above, only after
        // the accounts they reference exist again.
        restoreRows(recentSearchQueriesFile, new TypeToken<List<RecentSearchQuery>>() {},
                rows -> onAccountsInDatabase(rows, RecentSearchQuery::getUsername, accountsInDatabase, "recent_search_queries"),
                redditDataRoomDatabase.recentSearchQueryDao()::insertAll);

        // Everything is in. Move whatever landed on the placeholder above
        // onto the name the app actually reads, and drop the placeholder.
        // Here rather than at the end of the restore: it is these rows that
        // can carry the old spelling, and a backup with no database
        // directory has nothing to rename.
        RedditDataRoomDatabase.renameAnonymousAccount(
                redditDataRoomDatabase.getOpenHelper().getWritableDatabase());

        return currentAccount;
    }

    /**
     * Restores one table from its file: nothing when the backup has no file for it, otherwise the
     * rows {@code keep} lets through, and nothing at all when that leaves none.
     */
    private static <T> void restoreRows(File file, TypeToken<List<T>> type,
                                        UnaryOperator<List<T>> keep, Consumer<List<T>> insert) {
        if (!file.exists()) {
            return;
        }
        List<T> rows = keep.apply(getListFromFile(file, type.getType()));
        if (!rows.isEmpty()) {
            insert.accept(rows);
        }
    }

    /**
     * Restores the filters of one kind, and their usage rows. A backup that says whose they are
     * brings them in as written; one from before a filter had an owner gives a copy to each of
     * {@code owners}.
     */
    private static <F, U> void restoreFilters(File filtersFile, File usageFile,
                                              TypeToken<List<F>> filterType, TypeToken<List<U>> usageType,
                                              List<String> owners,
                                              BiConsumer<F, String> setFilterOwner, BiConsumer<U, String> setUsageOwner,
                                              Consumer<List<F>> insertFilters, Consumer<List<U>> insertUsage) {
        if (!filtersFile.exists()) {
            return;
        }
        List<F> filters = getListFromFile(filtersFile, filterType.getType());
        List<U> usage = usageFile.exists()
                ? getListFromFile(usageFile, usageType.getType())
                : Collections.emptyList();
        if (rowsHave(filtersFile, "username")) {
            insertFilters.accept(filters);
            insertUsage.accept(usage);
            return;
        }
        for (String owner : owners) {
            for (F filter : filters) {
                setFilterOwner.accept(filter, owner);
            }
            insertFilters.accept(filters);
            for (U row : usage) {
                setUsageOwner.accept(row, owner);
            }
            insertUsage.accept(usage);
        }
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
     * Puts the backup's custom font files where the app looks for them. Files this device has that
     * the backup does not are left: a backup from before fonts were carried holds none.
     */
    private static boolean restoreFonts(Context context, File backedUpFonts) {
        File fontsDirectory = context.getExternalFilesDir(BackupSettings.FONTS_DIRECTORY);
        File[] fonts = backedUpFonts.listFiles(File::isFile);
        if (fontsDirectory == null || fonts == null) {
            return false;
        }
        try {
            for (File font : fonts) {
                FileUtils.copyFile(font, new File(fontsDirectory, font.getName()));
            }
            return true;
        } catch (IOException e) {
            Log.e("RestoreSettings", "restoreFonts failed", e);
            return false;
        }
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
            Map<String, ?> onThisDevice = sharedPreferences.getAll();
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.clear();
            for (Map.Entry<String, Object> e : map.entrySet()) {
                // An older backup may still carry them; they are not this device's to take.
                if (!BackupSettings.DEVICE_BOUND_KEYS.contains(e.getKey())) {
                    putValue(editor, e.getKey(), e.getValue());
                }
            }
            for (String deviceBoundKey : BackupSettings.DEVICE_BOUND_KEYS) {
                putValue(editor, deviceBoundKey, onThisDevice.get(deviceBoundKey));
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

    /** Writes {@code value} under {@code key} as the type it is; a value of no preference type is skipped. */
    private static void putValue(SharedPreferences.Editor editor, String key, @Nullable Object value) {
        if (value instanceof String) {
            editor.putString(key, (String) value);
        } else if (value instanceof Integer) {
            editor.putInt(key, (Integer) value);
        } else if (value instanceof Float) {
            editor.putFloat(key, (Float) value);
        } else if (value instanceof Boolean) {
            editor.putBoolean(key, (Boolean) value);
        } else if (value instanceof Long) {
            editor.putLong(key, (Long) value);
        } else if (value instanceof Set) {
            Set<String> strings = new HashSet<>();
            for (Object element : (Set<?>) value) {
                if (element instanceof String) {
                    strings.add((String) element);
                }
            }
            editor.putStringSet(key, strings);
        }
    }

    /**
     * Whether the rows of a backed up table carry {@code field}: a backup taken before the column
     * existed does not.
     *
     * Gson fills a field it does not find from the class's own initialiser, or leaves it at zero,
     * rather than marking it absent, so the JSON is the only place the difference shows. Only the
     * first row is read, a backup's rows all being written by one version. An unreadable or empty
     * file is treated as carrying it: there is then nothing to fill in.
     */
    private static boolean rowsHave(File file, String field) {
        try (JsonReader reader = new JsonReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            JsonElement parsed = JsonParser.parseReader(reader);
            if (!parsed.isJsonArray() || parsed.getAsJsonArray().size() == 0) {
                return true;
            }
            JsonElement first = parsed.getAsJsonArray().get(0);
            return !first.isJsonObject() || first.getAsJsonObject().has(field);
        } catch (Exception e) {
            Log.e("RestoreSettings", "rowsHave failed", e);
            return true;
        }
    }

    /** Gives each theme the post type colours every built-in theme has. */
    private static void fillPostTypeColors(Context context, List<CustomTheme> customThemes) {
        CustomTheme defaults = CustomThemeWrapper.getSolarizedAmoled(context);
        for (CustomTheme customTheme : customThemes) {
            customTheme.textTypeBackgroundColor = defaults.textTypeBackgroundColor;
            customTheme.imageTypeBackgroundColor = defaults.imageTypeBackgroundColor;
            customTheme.linkTypeBackgroundColor = defaults.linkTypeBackgroundColor;
            customTheme.videoTypeBackgroundColor = defaults.videoTypeBackgroundColor;
            customTheme.gifTypeBackgroundColor = defaults.gifTypeBackgroundColor;
            customTheme.galleryTypeBackgroundColor = defaults.galleryTypeBackgroundColor;
        }
    }

    /**
     * Every name in the accounts table, which is what a restored row's foreign key can point at:
     * anonymous, and the placeholder old-spelling rows are parked on until the rename moves them.
     */
    private static Set<String> namesInAccountsTable(RedditDataRoomDatabase redditDataRoomDatabase) {
        Set<String> names = new HashSet<>();
        names.add(Account.ANONYMOUS_ACCOUNT);
        for (Account account : redditDataRoomDatabase.accountDao().getAllAccounts()) {
            names.add(account.getAccountName());
        }
        return names;
    }

    /** The rows whose account is in the database; the rest belong to nobody here and are dropped. */
    private static <T> List<T> onAccountsInDatabase(List<T> rows, Function<T, String> accountOf,
                                                    Set<String> accountsInDatabase, String table) {
        List<T> kept = new ArrayList<>(rows.size());
        for (T row : rows) {
            if (accountsInDatabase.contains(accountOf.apply(row))) {
                kept.add(row);
            }
        }
        if (kept.size() < rows.size()) {
            Log.w("RestoreSettings", "dropped " + (rows.size() - kept.size()) + " " + table
                    + " rows for accounts not on this device");
        }
        return kept;
    }

    /**
     * The reminders, with an account that is not in the database taken off them rather than the
     * reminder dropped. The list is not per account, and a reminder whose account goes keeps going
     * the same way: the foreign key sets it to null on delete.
     */
    private static List<Reminder> withoutAbsentAccounts(List<Reminder> reminders,
                                                        Set<String> accountsInDatabase) {
        List<Reminder> kept = new ArrayList<>(reminders.size());
        for (Reminder reminder : reminders) {
            String accountName = reminder.getAccountName();
            if (accountName == null || accountsInDatabase.contains(accountName)) {
                kept.add(reminder);
            } else {
                kept.add(new Reminder(null, reminder.getPostId(), reminder.getCommentId(),
                        reminder.getContent(), reminder.getCreatedAt(), reminder.getReminderTime()));
            }
        }
        return kept;
    }

    /**
     * Every account the database holds, anonymous included.
     *
     * Not the placeholder a restore parks old-spelling rows on, which sits in the accounts table
     * like one: a copy of every filter would go to it as well, only for the rename to find each
     * copy already under the new name and drop it.
     */
    private static List<String> accountNames(RedditDataRoomDatabase redditDataRoomDatabase) {
        List<String> names = new ArrayList<>();
        names.add(Account.ANONYMOUS_ACCOUNT);
        for (Account account : redditDataRoomDatabase.accountDao().getAllAccounts()) {
            if (!AnonymousAccountRename.LEGACY_ANONYMOUS_ACCOUNT.equals(account.getAccountName())) {
                names.add(account.getAccountName());
            }
        }
        return names;
    }

    private static <T> List<T> getListFromFile(File file, Type dataType) {
        try (JsonReader reader = new JsonReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            Gson gson = SharedGson.INSTANCE;
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
    }
}
