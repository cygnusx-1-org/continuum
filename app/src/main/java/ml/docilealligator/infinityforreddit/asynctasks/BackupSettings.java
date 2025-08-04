package ml.docilealligator.infinityforreddit.asynctasks;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Handler;

import androidx.documentfile.provider.DocumentFile;

import com.google.gson.Gson;

import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.ZipParameters;
import net.lingala.zip4j.model.enums.EncryptionMethod;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.Executor;

import ml.docilealligator.infinityforreddit.BuildConfig;
import ml.docilealligator.infinityforreddit.R;
import ml.docilealligator.infinityforreddit.RedditDataRoomDatabase;
import ml.docilealligator.infinityforreddit.account.Account;
import ml.docilealligator.infinityforreddit.commentfilter.CommentFilter;
import ml.docilealligator.infinityforreddit.commentfilter.CommentFilterUsage;
import ml.docilealligator.infinityforreddit.customtheme.CustomTheme;
import ml.docilealligator.infinityforreddit.multireddit.AnonymousMultiredditSubreddit;
import ml.docilealligator.infinityforreddit.multireddit.MultiReddit;
import ml.docilealligator.infinityforreddit.postfilter.PostFilter;
import ml.docilealligator.infinityforreddit.postfilter.PostFilterUsage;
import ml.docilealligator.infinityforreddit.subscribedsubreddit.SubscribedSubredditData;
import ml.docilealligator.infinityforreddit.subscribeduser.SubscribedUserData;
import ml.docilealligator.infinityforreddit.utils.CustomThemeSharedPreferencesUtils;
import ml.docilealligator.infinityforreddit.utils.SharedPreferencesUtils;

public class BackupSettings {
    public static void backupSettings(Context context, Executor executor, Handler handler,
                                    ContentResolver contentResolver, Uri destinationDirUri,
                                    String password,
                                    RedditDataRoomDatabase redditDataRoomDatabase,
                                    SharedPreferences defaultSharedPreferences,
                                    SharedPreferences lightThemeSharedPreferences,
                                    SharedPreferences darkThemeSharedPreferences,
                                    SharedPreferences amoledThemeSharedPreferences,
                                    SharedPreferences sortTypeSharedPreferences,
                                    SharedPreferences postLayoutSharedPreferences,
                                    SharedPreferences postDetailsSharedPreferences,
                                    SharedPreferences postFeedScrolledPositionSharedPreferences,
                                    SharedPreferences mainActivityTabsSharedPreferences,
                                    SharedPreferences proxySharedPreferences,
                                    SharedPreferences nsfwAndSpoilerSharedPreferences,
                                    SharedPreferences bottomAppBarSharedPreferences,
                                    SharedPreferences postHistorySharedPreferences,
                                    SharedPreferences navigationDrawerSharedPreferences,
                                    BackupSettingsListener backupSettingsListener) {
        executor.execute(() -> {
            // Defensive null check for external cache directory
            File externalCacheDir = context.getExternalCacheDir();
            String backupDir;
            if (externalCacheDir != null) {
                backupDir = externalCacheDir.getAbsolutePath() + "/Backup/" + BuildConfig.VERSION_NAME;
            } else {
                // Fallback to internal cache if external cache directory creation failed
                File internalCacheDir = context.getCacheDir();
                if (internalCacheDir != null) {
                    backupDir = internalCacheDir.getAbsolutePath() + "/Backup/" + BuildConfig.VERSION_NAME;
                } else {
                    // Last resort - use app files directory
                    backupDir = context.getFilesDir().getAbsolutePath() + "/cache/Backup/" + BuildConfig.VERSION_NAME;
                }
            }
            File backupDirFile = new File(backupDir);
            if (new File(backupDir).exists()) {
                try {
                    FileUtils.deleteDirectory(backupDirFile);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            backupDirFile.mkdirs();

            File databaseDirFile = new File(backupDir + "/database");
            databaseDirFile.mkdirs();

            // Handle default shared preferences (potentially excluding client ID)
            Map<String, ?> defaultPrefsMap = defaultSharedPreferences.getAll();
            Map<String, Object> filteredDefaultPrefsMap = new HashMap<>(defaultPrefsMap); // Create a mutable copy

            boolean res = saveMapToFile(filteredDefaultPrefsMap, backupDir, SharedPreferencesUtils.DEFAULT_PREFERENCES_FILE);

            SharedPreferences defaultPrefsPrivate = context.getSharedPreferences(SharedPreferencesUtils.DEFAULT_PREFERENCES_FILE, Context.MODE_PRIVATE);
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

            List<SubscribedSubredditData> anonymousSubscribedSubredditsData = redditDataRoomDatabase.subscribedSubredditDao().getAllSubscribedSubredditsList(Account.ANONYMOUS_ACCOUNT);
            String anonymousSubscribedSubredditsDataJson = new Gson().toJson(anonymousSubscribedSubredditsData);
            boolean res14 = saveDatabaseTableToFile(anonymousSubscribedSubredditsDataJson, databaseDirFile.getAbsolutePath(), "/anonymous_subscribed_subreddits.json");

            List<SubscribedUserData> anonymousSubscribedUsersData = redditDataRoomDatabase.subscribedUserDao().getAllSubscribedUsersList(Account.ANONYMOUS_ACCOUNT);
            String anonymousSubscribedUsersDataJson = new Gson().toJson(anonymousSubscribedUsersData);
            boolean res15 = saveDatabaseTableToFile(anonymousSubscribedUsersDataJson, databaseDirFile.getAbsolutePath(), "/anonymous_subscribed_users.json");

            List<MultiReddit> anonymousMultireddits = redditDataRoomDatabase.multiRedditDao().getAllMultiRedditsList(Account.ANONYMOUS_ACCOUNT);
            String anonymousMultiredditsJson = new Gson().toJson(anonymousMultireddits);
            boolean res16 = saveDatabaseTableToFile(anonymousMultiredditsJson, databaseDirFile.getAbsolutePath(), "/anonymous_multireddits.json");

            List<AnonymousMultiredditSubreddit> anonymousMultiredditSubreddits = redditDataRoomDatabase.anonymousMultiredditSubredditDao().getAllSubreddits();
            String anonymousMultiredditSubredditsJson = new Gson().toJson(anonymousMultiredditSubreddits);
            boolean res17 = saveDatabaseTableToFile(anonymousMultiredditSubredditsJson, databaseDirFile.getAbsolutePath(), "/anonymous_multireddit_subreddits.json");

            List<CustomTheme> customThemes = redditDataRoomDatabase.customThemeDao().getAllCustomThemesList();
            String customThemesJson = new Gson().toJson(customThemes);
            boolean res18 = saveDatabaseTableToFile(customThemesJson, databaseDirFile.getAbsolutePath(), "/custom_themes.json");

            List<PostFilter> postFilters = redditDataRoomDatabase.postFilterDao().getAllPostFilters();
            String postFiltersJson = new Gson().toJson(postFilters);
            boolean res19 = saveDatabaseTableToFile(postFiltersJson, databaseDirFile.getAbsolutePath(), "/post_filters.json");

            List<PostFilterUsage> postFilterUsage = redditDataRoomDatabase.postFilterUsageDao().getAllPostFilterUsageForBackup();
            String postFilterUsageJson = new Gson().toJson(postFilterUsage);
            boolean res20 = saveDatabaseTableToFile(postFilterUsageJson, databaseDirFile.getAbsolutePath(), "/post_filter_usage.json");

            List<CommentFilter> commentFilters = redditDataRoomDatabase.commentFilterDao().getAllCommentFilters();
            String commentFiltersJson = new Gson().toJson(commentFilters);
            boolean res21 = saveDatabaseTableToFile(commentFiltersJson, databaseDirFile.getAbsolutePath(), "/comment_filters.json");

            List<CommentFilterUsage> commentFilterUsage = redditDataRoomDatabase.commentFilterUsageDao().getAllCommentFilterUsageForBackup();
            String commentFilterUsageJson = new Gson().toJson(commentFilterUsage);
            boolean res22 = saveDatabaseTableToFile(commentFilterUsageJson, databaseDirFile.getAbsolutePath(), "/comment_filter_usage.json");

            List<Account> accounts = redditDataRoomDatabase.accountDao().getAllAccounts();
            String accountsJson = new Gson().toJson(accounts);
            boolean res23 = saveDatabaseTableToFile(accountsJson, databaseDirFile.getAbsolutePath(), "/accounts.json");


            boolean zipRes = zipAndMoveToDestinationDir(context, contentResolver, destinationDirUri, password);

            try {
                // Use the same cache directory logic for cleanup
                File cacheDir = context.getExternalCacheDir();
                if (cacheDir == null) {
                    cacheDir = context.getCacheDir();
                    if (cacheDir == null) {
                        cacheDir = new File(context.getFilesDir(), "cache");
                    }
                }
                FileUtils.deleteDirectory(new File(cacheDir, "Backup"));
            } catch (IOException e) {
                e.printStackTrace();
            }

            handler.post(() -> {
                boolean finalResult = res && res1 && res2 && res3 && res4 && res5 && res6 && res7 && res8
                        && res9 && res10 && res11 && res12 && res13 && res14 && res15 && res16 && res17
                        && res18 && res19 && res20 && res21 && res22 && res23 && zipRes && resPrivate;
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

    private static boolean saveMapToFile(Map<String, ?> mapToSave, String backupDir, String fileName) {
        boolean result = false;

        ObjectOutputStream output = null;
        try {
            output = new ObjectOutputStream(new FileOutputStream(backupDir + "/" + fileName + ".txt"));
            output.writeObject(mapToSave);

            result = true;
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (output != null) {
                    output.flush();
                }
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
        return result;
    }

    private static boolean saveDatabaseTableToFile(String dataJson, String backupDir, String fileName) {
        File anonymousSubscribedSubredditsFile = new File(backupDir + fileName);
        try {
            anonymousSubscribedSubredditsFile.createNewFile();
            try (PrintWriter out = new PrintWriter(anonymousSubscribedSubredditsFile.getAbsolutePath())) {
                out.println(dataJson);
            } catch (IOException e) {
                e.printStackTrace();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return true;
    }

    private static boolean zipAndMoveToDestinationDir(Context context, ContentResolver contentResolver, Uri destinationDirUri, String password) {
        OutputStream outputStream = null;
        boolean result = false;
        try {
            String time = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date(System.currentTimeMillis()));
            String fileName = "Continuum_Settings_Backup_v" + BuildConfig.VERSION_NAME + "-" + BuildConfig.VERSION_CODE + "-" + time + ".zip";
            // Use the same cache directory logic for zip creation
            File cacheDir = context.getExternalCacheDir();
            if (cacheDir == null) {
                cacheDir = context.getCacheDir();
                if (cacheDir == null) {
                    cacheDir = new File(context.getFilesDir(), "cache");
                }
            }
            String filePath = cacheDir.getAbsolutePath() + "/Backup/" + fileName;
            ZipFile zip = new ZipFile(filePath, password.toCharArray());
            ZipParameters zipParameters = new ZipParameters();
            zipParameters.setEncryptFiles(true);
            zipParameters.setEncryptionMethod(EncryptionMethod.AES);
            zip.addFolder(new File(cacheDir.getAbsolutePath() + "/Backup/" + BuildConfig.VERSION_NAME + "/"), zipParameters);

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

            FileInputStream inputStream = new FileInputStream(filePath);
            while (true) {
                int read = inputStream.read(fileReader);

                if (read == -1) {
                    break;
                }

                outputStream.write(fileReader, 0, read);
            }
            result = true;
        } catch (IOException e) {
            e.printStackTrace();

        } finally {
            if (outputStream != null) {
                try {
                    outputStream.flush();
                } catch (IOException e) {
                    e.printStackTrace();
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
