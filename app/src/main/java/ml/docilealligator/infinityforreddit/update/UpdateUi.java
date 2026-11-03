package ml.docilealligator.infinityforreddit.update;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.Toast;

import androidx.core.content.FileProvider;
import androidx.preference.PreferenceManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import ml.docilealligator.infinityforreddit.BuildConfig;
import ml.docilealligator.infinityforreddit.R;
import ml.docilealligator.infinityforreddit.utils.SharedPreferencesUtils;

/**
 * Checks GitHub for a newer release, asks before downloading it, and hands the APK to the
 * system installer.
 */
public final class UpdateUi {
    private static final long CHECK_INTERVAL_MS = 24L * 60 * 60 * 1000;

    private UpdateUi() {
        // Prevent instantiation
    }

    /**
     * Checks at most once a day, and only if the user has not turned it off.
     */
    public static void checkAutomatically(Activity activity) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity);
        if (!prefs.getBoolean(SharedPreferencesUtils.AUTO_CHECK_FOR_UPDATES, true)) return;
        long now = System.currentTimeMillis();
        if (now - prefs.getLong(SharedPreferencesUtils.LAST_UPDATE_CHECK_TIME, 0) < CHECK_INTERVAL_MS) return;
        prefs.edit().putLong(SharedPreferencesUtils.LAST_UPDATE_CHECK_TIME, now).apply();
        check(activity, false);
    }

    /**
     * @param manual whether the user asked, so "up to date" and failures are reported.
     */
    public static void check(Activity activity, boolean manual) {
        Handler main = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            try {
                UpdateChecker.Release release = UpdateChecker.fetchNewerRelease(BuildConfig.VERSION_NAME);
                main.post(() -> {
                    if (activity.isFinishing() || activity.isDestroyed()) return;
                    if (release != null) {
                        showUpdateDialog(activity, release);
                    } else if (manual) {
                        Toast.makeText(activity, R.string.update_up_to_date, Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (IOException ex) {
                if (manual) {
                    main.post(() -> Toast.makeText(activity, R.string.update_check_failed, Toast.LENGTH_SHORT).show());
                }
            }
        }, "update-check").start();
    }

    private static void showUpdateDialog(Activity activity, UpdateChecker.Release release) {
        new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.update_available_title)
                .setMessage(activity.getString(R.string.update_available_message,
                        release.version, BuildConfig.VERSION_NAME, release.notes))
                .setPositiveButton(R.string.update_download_and_install, (dialog, which) -> downloadAndInstall(activity, release))
                .setNegativeButton(R.string.update_later, null)
                .show();
    }

    private static void downloadAndInstall(Activity activity, UpdateChecker.Release release) {
        if (!activity.getPackageManager().canRequestPackageInstalls()) {
            Toast.makeText(activity, R.string.update_allow_installs, Toast.LENGTH_LONG).show();
            activity.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + activity.getPackageName())));
            return;
        }
        Toast.makeText(activity, R.string.update_downloading, Toast.LENGTH_SHORT).show();
        Context appContext = activity.getApplicationContext();
        Handler main = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            try {
                File apk = download(appContext, release.apkUrl);
                main.post(() -> install(appContext, apk));
            } catch (IOException ex) {
                main.post(() -> Toast.makeText(appContext, R.string.update_download_failed, Toast.LENGTH_LONG).show());
            }
        }, "update-download").start();
    }

    private static File download(Context context, String url) throws IOException {
        File dir = new File(context.getCacheDir(), "updates");
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Cannot create " + dir);
        File apk = new File(dir, "update.apk");
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        try {
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(30000);
            connection.setRequestProperty("User-Agent", "continuumjr-updater");
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                throw new IOException("Download returned " + connection.getResponseCode());
            }
            try (InputStream in = connection.getInputStream(); FileOutputStream out = new FileOutputStream(apk)) {
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
            }
        } finally {
            connection.disconnect();
        }
        return apk;
    }

    private static void install(Context context, File apk) {
        Uri uri = FileProvider.getUriForFile(context, context.getPackageName() + ".provider", apk);
        Intent intent = new Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.startActivity(intent);
        } catch (ActivityNotFoundException ex) {
            Toast.makeText(context, R.string.update_download_failed, Toast.LENGTH_LONG).show();
        }
    }
}
