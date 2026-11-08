package ml.docilealligator.infinityforreddit.asynctasks;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Handler;
import android.util.Log;
import androidx.documentfile.provider.DocumentFile;
import androidx.preference.PreferenceManager;
import ml.docilealligator.infinityforreddit.utils.SharedGson;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import ml.docilealligator.infinityforreddit.BuildConfig;
import ml.docilealligator.infinityforreddit.R;
import ml.docilealligator.infinityforreddit.RedditDataRoomDatabase;
import ml.docilealligator.infinityforreddit.account.Account;
import ml.docilealligator.infinityforreddit.comment.AnonymousSavedComment;
import ml.docilealligator.infinityforreddit.comment.CommentDraft;
import ml.docilealligator.infinityforreddit.commentfilter.CommentFilter;
import ml.docilealligator.infinityforreddit.commentfilter.CommentFilterUsage;
import ml.docilealligator.infinityforreddit.customtheme.CustomTheme;
import ml.docilealligator.infinityforreddit.localsaved.LocalSavedThing;
import ml.docilealligator.infinityforreddit.multireddit.AnonymousMultiredditSubreddit;
import ml.docilealligator.infinityforreddit.multireddit.MultiReddit;
import ml.docilealligator.infinityforreddit.postfilter.PostFilter;
import ml.docilealligator.infinityforreddit.postfilter.PostFilterUsage;
import ml.docilealligator.infinityforreddit.readpost.ReadPost;
import ml.docilealligator.infinityforreddit.recentsearchquery.RecentSearchQuery;
import ml.docilealligator.infinityforreddit.reminder.Reminder;
import ml.docilealligator.infinityforreddit.subscribedsubreddit.SubscribedSubredditData;
import ml.docilealligator.infinityforreddit.subscribeduser.SubscribedUserData;
import ml.docilealligator.infinityforreddit.utils.CustomThemeSharedPreferencesUtils;
import ml.docilealligator.infinityforreddit.utils.SharedPreferencesUtils;
import ml.docilealligator.infinityforreddit.utils.Utils;
import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.ZipParameters;
import org.apache.commons.io.FileUtils;

/**
 * Writes every preference file, database table and custom font to a zip.
 *
 * What it leaves out is on purpose: the files that only bookkeep (the internal migration flags, the
 * cookies, the random-subreddit cache, the signed-in account, which {@code accounts.json} carries),
 * and the keys in {@link #DEVICE_BOUND_KEYS}, which mean nothing on any other device.
 *
 * The account-scoped files are opened here rather than taken injected. The injected ones are
 * {@code AccountScopedSharedPreferences}, whose {@code getAll()} answers for the current account
 * only: it renames that account's keys back to their bases and drops every other account's
 * entirely. A backup taken through it would quietly hold one account's settings under names that
 * belong to nobody.
 */
public class BackupSettings {

    /** Suffix every preference file in a backup carries; the name before it is the file's own. */
    static final String PREFERENCES_FILE_SUFFIX = ".txt";

    /** The directory of a backup that holds the custom font files. */
    static final String FONTS_DIRECTORY = "fonts";

    /**
     * Settings that only mean something on the device that wrote them, so a backup does not carry
     * them and a restore leaves this device's own alone: a download folder is a permission this
     * device was granted on one folder of its own storage, and the two times are bookkeeping about
     * this device's last run. Restored onto another device the folders would point where nothing may
     * be written, and downloads would fail without saying why.
     */
    static final Set<String> DEVICE_BOUND_KEYS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            SharedPreferencesUtils.IMAGE_DOWNLOAD_LOCATION,
            SharedPreferencesUtils.GIF_DOWNLOAD_LOCATION,
            SharedPreferencesUtils.VIDEO_DOWNLOAD_LOCATION,
            SharedPreferencesUtils.NSFW_DOWNLOAD_LOCATION,
            SharedPreferencesUtils.LAST_UPDATE_CHECK_TIME,
            SharedPreferencesUtils.LAST_FOREGROUND_TIME)));

    public static void backupSettings(Context context, Executor executor, Handler handler,
                                    ContentResolver contentResolver, Uri destinationDirUri,
                                    RedditDataRoomDatabase redditDataRoomDatabase,
                                    SharedPreferences lightThemeSharedPreferences,
                                    SharedPreferences darkThemeSharedPreferences,
                                    SharedPreferences amoledThemeSharedPreferences,
                                    SharedPreferences postFeedScrolledPositionSharedPreferences,
                                    SharedPreferences mainActivityTabsSharedPreferences,
                                    SharedPreferences proxySharedPreferences,
                                    SharedPreferences nsfwAndSpoilerSharedPreferences,
                                    SharedPreferences postHistorySharedPreferences,
                                    BackupSettingsListener backupSettingsListener) {
        executor.execute(() -> {
            SharedPreferences defaultSharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
            SharedPreferences sortTypeSharedPreferences = rawFile(context, SharedPreferencesUtils.SORT_TYPE_SHARED_PREFERENCES_FILE);
            SharedPreferences postLayoutSharedPreferences = rawFile(context, SharedPreferencesUtils.POST_LAYOUT_SHARED_PREFERENCES_FILE);
            SharedPreferences postDetailsSharedPreferences = rawFile(context, SharedPreferencesUtils.POST_DETAILS_SHARED_PREFERENCES_FILE);
            SharedPreferences navigationDrawerSharedPreferences = rawFile(context, SharedPreferencesUtils.NAVIGATION_DRAWER_SHARED_PREFERENCES_FILE);
            SharedPreferences bottomAppBarSharedPreferences = rawFile(context, SharedPreferencesUtils.BOTTOM_APP_BAR_SHARED_PREFERENCES_FILE);
            SharedPreferences userTagsSharedPreferences = rawFile(context, SharedPreferencesUtils.USER_TAGS_SHARED_PREFERENCES_FILE);
            SharedPreferences securitySharedPreferences = rawFile(context, SharedPreferencesUtils.SECURITY_SHARED_PREFERENCES_FILE);

            File cacheDir = Utils.getCacheDir(context);
            if (cacheDir == null) {
                handler.post(() -> backupSettingsListener.failed(context.getText(R.string.restore_settings_failed_cannot_get_cache_dir).toString()));
                return;
            }

            String backupDir = cacheDir + "/Backup/" + BuildConfig.VERSION_NAME;
            File backupDirFile = new File(backupDir);
            if (new File(backupDir).exists()) {
                try {
                    FileUtils.deleteDirectory(backupDirFile);
                } catch (IOException e) {
                    Log.e("BackupSettings", "backupSettings failed", e);
                }
            }
            backupDirFile.mkdirs();

            File databaseDirFile = new File(backupDir + "/database");
            databaseDirFile.mkdirs();

            boolean res = saveMapToFile(defaultSharedPreferences.getAll(), backupDir, SharedPreferencesUtils.DEFAULT_PREFERENCES_FILE);

            SharedPreferences defaultPrefsPrivate = rawFile(context, SharedPreferencesUtils.DEFAULT_PREFERENCES_FILE);
            boolean resPrivate = saveMapToFile(defaultPrefsPrivate.getAll(), backupDir, SharedPreferencesUtils.DEFAULT_PREFERENCES_FILE + "_private");


            boolean res1 = saveMapToFile(lightThemeSharedPreferences.getAll(), backupDir, CustomThemeSharedPreferencesUtils.LIGHT_THEME_SHARED_PREFERENCES_FILE);
            boolean res2 = saveMapToFile(darkThemeSharedPreferences.getAll(), backupDir, CustomThemeSharedPreferencesUtils.DARK_THEME_SHARED_PREFERENCES_FILE);
            boolean res3 = saveMapToFile(amoledThemeSharedPreferences.getAll(), backupDir, CustomThemeSharedPreferencesUtils.AMOLED_THEME_SHARED_PREFERENCES_FILE);
            boolean res4 = saveMapToFile(sortTypeSharedPreferences.getAll(), backupDir, SharedPreferencesUtils.SORT_TYPE_SHARED_PREFERENCES_FILE);
            boolean res5 = saveMapToFile(postLayoutSharedPreferences.getAll(), backupDir, SharedPreferencesUtils.POST_LAYOUT_SHARED_PREFERENCES_FILE);
            boolean res6 = saveMapToFile(postDetailsSharedPreferences.getAll(), backupDir, SharedPreferencesUtils.POST_DETAILS_SHARED_PREFERENCES_FILE);
            boolean res7 = saveMapToFile(postFeedScrolledPositionSharedPreferences.getAll(), backupDir, SharedPreferencesUtils.FRONT_PAGE_SCROLLED_POSITION_SHARED_PREFERENCES_FILE);
            boolean res8 = saveMapToFile(mainActivityTabsSharedPreferences.getAll(), backupDir, SharedPreferencesUtils.MAIN_PAGE_TABS_SHARED_PREFERENCES_FILE);
            boolean res9 = saveMapToFile(proxySharedPreferences.getAll(), backupDir, SharedPreferencesUtils.PROXY_SHARED_PREFERENCES_FILE);
            boolean res10 = saveMapToFile(nsfwAndSpoilerSharedPreferences.getAll(), backupDir, SharedPreferencesUtils.NSFW_AND_SPOILER_SHARED_PREFERENCES_FILE);
            boolean res11 = saveMapToFile(bottomAppBarSharedPreferences.getAll(), backupDir, SharedPreferencesUtils.BOTTOM_APP_BAR_SHARED_PREFERENCES_FILE);
            boolean res12 = saveMapToFile(postHistorySharedPreferences.getAll(), backupDir, SharedPreferencesUtils.POST_HISTORY_SHARED_PREFERENCES_FILE);
            boolean res13 = saveMapToFile(navigationDrawerSharedPreferences.getAll(), backupDir, SharedPreferencesUtils.NAVIGATION_DRAWER_SHARED_PREFERENCES_FILE);
            boolean res31 = saveMapToFile(userTagsSharedPreferences.getAll(), backupDir, SharedPreferencesUtils.USER_TAGS_SHARED_PREFERENCES_FILE);
            boolean res33 = saveMapToFile(securitySharedPreferences.getAll(), backupDir, SharedPreferencesUtils.SECURITY_SHARED_PREFERENCES_FILE);
            boolean res34 = saveFonts(context, backupDir);

            boolean res14 = saveTable(redditDataRoomDatabase.subscribedSubredditDao().getAllSubscribedSubredditsList(Account.ANONYMOUS_ACCOUNT), databaseDirFile.getAbsolutePath(), "/anonymous_subscribed_subreddits.json");

            boolean res15 = saveTable(redditDataRoomDatabase.subscribedUserDao().getAllSubscribedUsersList(Account.ANONYMOUS_ACCOUNT), databaseDirFile.getAbsolutePath(), "/anonymous_subscribed_users.json");

            boolean res16 = saveTable(redditDataRoomDatabase.multiRedditDao().getAllMultiRedditsList(Account.ANONYMOUS_ACCOUNT), databaseDirFile.getAbsolutePath(), "/anonymous_multireddits.json");

            boolean res17 = saveTable(redditDataRoomDatabase.anonymousMultiredditSubredditDao().getAllSubreddits(), databaseDirFile.getAbsolutePath(), "/anonymous_multireddit_subreddits.json");

            boolean res18 = saveTable(redditDataRoomDatabase.customThemeDao().getAllCustomThemesList(), databaseDirFile.getAbsolutePath(), "/custom_themes.json");

            boolean res19 = saveTable(redditDataRoomDatabase.postFilterDao().getAllPostFiltersForBackup(), databaseDirFile.getAbsolutePath(), "/post_filters.json");

            boolean res20 = saveTable(redditDataRoomDatabase.postFilterUsageDao().getAllPostFilterUsageForBackup(), databaseDirFile.getAbsolutePath(), "/post_filter_usage.json");

            boolean res21 = saveTable(redditDataRoomDatabase.commentFilterDao().getAllCommentFiltersForBackup(), databaseDirFile.getAbsolutePath(), "/comment_filters.json");

            boolean res22 = saveTable(redditDataRoomDatabase.commentFilterUsageDao().getAllCommentFilterUsageForBackup(), databaseDirFile.getAbsolutePath(), "/comment_filter_usage.json");

            boolean res23 = saveTable(redditDataRoomDatabase.accountDao().getAllAccounts(), databaseDirFile.getAbsolutePath(), "/accounts.json");

            boolean res24 = saveTable(redditDataRoomDatabase.readPostDao().getAllReadPostsForBackup(), databaseDirFile.getAbsolutePath(), "/read_posts.json");

            boolean res25 = saveTable(redditDataRoomDatabase.localSavedThingDao().getAllForBackup(), databaseDirFile.getAbsolutePath(), "/local_saved.json");

            boolean res32 = saveTable(redditDataRoomDatabase.anonymousSavedCommentDao().getAllForBackup(), databaseDirFile.getAbsolutePath(), "/anonymous_saved_comments.json");

            boolean res27 = saveTable(redditDataRoomDatabase.commentDraftDao().getCommentDraftsForBackup(), databaseDirFile.getAbsolutePath(), "/comment_drafts.json");

            boolean res28 = saveTable(redditDataRoomDatabase.reminderDao().getAllRemindersForBackup(), databaseDirFile.getAbsolutePath(), "/reminders.json");

            // Search history is local-only: nothing on Reddit can re-sync it, and restoring clears
            // the accounts it hangs off, so without this it does not survive a restore at all. The
            // search *settings* travel in the shared-preferences files above; this is the data
            // they govern.
            boolean res30 = saveTable(redditDataRoomDatabase.recentSearchQueryDao().getAllForBackup(), databaseDirFile.getAbsolutePath(), "/recent_search_queries.json");

            boolean zipRes = zipAndMoveToDestinationDir(context, cacheDir, contentResolver, destinationDirUri);

            try {
                FileUtils.deleteDirectory(new File(cacheDir + "/Backup/"));
            } catch (IOException e) {
                Log.e("BackupSettings", "backupSettings failed", e);
            }

            handler.post(() -> {
                boolean finalResult = res && res1 && res2 && res3 && res4 && res5 && res6 && res7 && res8
                        && res9 && res10 && res11 && res12 && res13 && res14 && res15 && res16 && res17
                        && res18 && res19 && res20 && res21 && res22 && res23 && res24 && res25
                        && res27 && res28 && res30 && res31 && res32 && res33 && res34 && zipRes && resPrivate;
                if (finalResult) {
                    backupSettingsListener.success();
                } else {
                    if (!zipRes) {
                        backupSettingsListener.failed(context.getText(R.string.create_zip_in_destination_directory_failed).toString());
                    } else {
                        backupSettingsListener.failed(context.getText(R.string.backup_some_settings_failed).toString());
                    }
                }
            });
        });
    }

    /** The file itself, past any account scoping the injected instance would apply. */
    private static SharedPreferences rawFile(Context context, String fileName) {
        return context.getSharedPreferences(fileName, Context.MODE_PRIVATE);
    }

    private static boolean saveMapToFile(Map<String, ?> mapToSave, String backupDir, String fileName) {
        Map<String, Object> portable = new HashMap<>(mapToSave);
        portable.keySet().removeAll(DEVICE_BOUND_KEYS);
        try (ObjectOutputStream output = new ObjectOutputStream(
                new FileOutputStream(backupDir + "/" + fileName + PREFERENCES_FILE_SUFFIX))) {
            output.writeObject(portable);
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * The custom font files into the backup. A font setting set to Custom names a file in this
     * app's own storage, so without the file it falls back to the default font on any device that
     * does not have it.
     */
    private static boolean saveFonts(Context context, String backupDir) {
        File fontsDirectory = context.getExternalFilesDir(FONTS_DIRECTORY);
        File[] fonts = fontsDirectory == null ? null : fontsDirectory.listFiles(File::isFile);
        if (fonts == null || fonts.length == 0) {
            return true;
        }
        try {
            for (File font : fonts) {
                FileUtils.copyFile(font, new File(backupDir + "/" + FONTS_DIRECTORY, font.getName()));
            }
            return true;
        } catch (IOException e) {
            Log.e("BackupSettings", "saveFonts failed", e);
            return false;
        }
    }

    /** One database table, as JSON, into the backup. */
    private static boolean saveTable(List<?> rows, String backupDir, String fileName) {
        return saveDatabaseTableToFile(SharedGson.INSTANCE.toJson(rows), backupDir, fileName);
    }

    private static boolean saveDatabaseTableToFile(String dataJson, String backupDir, String fileName) {
        File databaseTableFile = new File(backupDir + fileName);
        try (PrintWriter out = new PrintWriter(new OutputStreamWriter(new FileOutputStream(databaseTableFile), StandardCharsets.UTF_8))) {
            out.println(dataJson);
            // PrintWriter swallows IO errors; surface them so a failed dump fails the backup.
            if (out.checkError()) {
                return false;
            }
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    private static boolean zipAndMoveToDestinationDir(Context context, File cacheDir, ContentResolver contentResolver, Uri destinationDirUri) {
        OutputStream outputStream = null;
        boolean result = false;
        try {
            String time = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.US).format(LocalDateTime.now(ZoneId.systemDefault()));
            String fileName = "RedditJr_Settings_Backup_v" + BuildConfig.VERSION_NAME + "-" + BuildConfig.VERSION_CODE + "-" + time + ".zip";
            String filePath = cacheDir + "/Backup/" + fileName;
            try (ZipFile zip = new ZipFile(filePath)) {
                ZipParameters zipParameters = new ZipParameters();
                zip.addFolder(new File(cacheDir + "/Backup/" + BuildConfig.VERSION_NAME + "/"), zipParameters);
            }

            DocumentFile dir = DocumentFile.fromTreeUri(context, destinationDirUri);
            if (dir == null) {
                return false;
            }
            DocumentFile checkForDuplicate = dir.findFile(fileName);
            if (checkForDuplicate != null) {
                checkForDuplicate.delete();
            }
            DocumentFile destinationFile = dir.createFile("application/zip", fileName);
            if (destinationFile == null) {
                return false;
            }

            outputStream = contentResolver.openOutputStream(destinationFile.getUri());
            if (outputStream == null) {
                return false;
            }

            byte[] fileReader = new byte[1024];

            try (FileInputStream inputStream = new FileInputStream(filePath)) {
                while (true) {
                    int read = inputStream.read(fileReader);

                    if (read == -1) {
                        break;
                    }

                    outputStream.write(fileReader, 0, read);
                }
            }
            result = true;
        } catch (IOException e) {
            Log.e("BackupSettings", "zipAndMoveToDestinationDir failed", e);

        } finally {
            if (outputStream != null) {
                try {
                    outputStream.flush();
                    // close() is required for SAF/cloud providers (e.g. Google Drive)
                    // to actually commit the bytes; flush() alone leaves a 0-byte file.
                    outputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                    // The bytes never committed, so the backup is not valid.
                    result = false;
                }
            }
        }

        return result;
    }

    public interface BackupSettingsListener {
        void success();
        void failed(String errorMessage);
    }
}
