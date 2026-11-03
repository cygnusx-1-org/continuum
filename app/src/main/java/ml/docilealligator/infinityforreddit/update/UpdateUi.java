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
import android.text.TextUtils;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;
import androidx.preference.PreferenceManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.atomic.AtomicBoolean;

import io.noties.markwon.Markwon;
import ml.docilealligator.infinityforreddit.BuildConfig;
import ml.docilealligator.infinityforreddit.R;
import ml.docilealligator.infinityforreddit.utils.SharedPreferencesUtils;

/**
 * Checks GitHub for a newer release, asks before downloading it, shows the download's progress,
 * and hands the APK to the system installer.
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
        boolean includePrereleases = PreferenceManager.getDefaultSharedPreferences(activity)
                .getBoolean(SharedPreferencesUtils.INCLUDE_PRERELEASES, false);
        Handler main = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            try {
                UpdateChecker.Release release = UpdateChecker.fetchNewerRelease(BuildConfig.VERSION_NAME, includePrereleases);
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
        // The notes are Markdown, so they are rendered rather than shown with their markup.
        // A long message scrolls inside the dialog.
        CharSequence message = TextUtils.concat(
                activity.getString(R.string.update_available_header, release.version, BuildConfig.VERSION_NAME),
                "\n\n", Markwon.create(activity).toMarkdown(release.notes.trim()));
        new MaterialAlertDialogBuilder(activity, R.style.MaterialAlertDialogTheme)
                .setTitle(R.string.update_available_title)
                .setMessage(message)
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

        LinearProgressIndicator progress = new LinearProgressIndicator(activity);
        progress.setIndeterminate(release.apkSize <= 0);
        progress.setMax(100);
        FrameLayout container = new FrameLayout(activity);
        int padding = Math.round(activity.getResources().getDisplayMetrics().density * 24);
        container.setPadding(padding, padding / 2, padding, 0);
        container.addView(progress);

        AtomicBoolean cancelled = new AtomicBoolean(false);
        AlertDialog dialog = new MaterialAlertDialogBuilder(activity, R.style.MaterialAlertDialogTheme)
                .setTitle(R.string.update_downloading)
                .setView(container)
                .setCancelable(false)
                .setNegativeButton(R.string.cancel, (d, which) -> cancelled.set(true))
                .show();

        Context appContext = activity.getApplicationContext();
        Handler main = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            try {
                File apk = download(appContext, release, cancelled,
                        percent -> main.post(() -> progress.setProgressCompat(percent, true)));
                main.post(() -> {
                    dismiss(dialog);
                    if (apk != null) install(appContext, apk);
                });
            } catch (IOException ex) {
                main.post(() -> {
                    dismiss(dialog);
                    Toast.makeText(appContext, R.string.update_download_failed, Toast.LENGTH_LONG).show();
                });
            }
        }, "update-download").start();
    }

    private static void dismiss(AlertDialog dialog) {
        try {
            dialog.dismiss();
        } catch (IllegalArgumentException ignored) {
            // The window went away with its activity.
        }
    }

    private interface ProgressListener {
        void onProgress(int percent);
    }

    /**
     * @return the downloaded APK, or null if the user cancelled.
     */
    private static File download(Context context, UpdateChecker.Release release, AtomicBoolean cancelled,
                                 ProgressListener listener) throws IOException {
        File dir = new File(context.getCacheDir(), "updates");
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Cannot create " + dir);
        File apk = new File(dir, "update.apk");
        HttpURLConnection connection = (HttpURLConnection) new URL(release.apkUrl).openConnection();
        try {
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(30000);
            connection.setRequestProperty("User-Agent", "continuumjr-updater");
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                throw new IOException("Download returned " + connection.getResponseCode());
            }
            long total = connection.getContentLengthLong() > 0 ? connection.getContentLengthLong() : release.apkSize;
            long done = 0;
            int lastPercent = -1;
            try (InputStream in = connection.getInputStream(); FileOutputStream out = new FileOutputStream(apk)) {
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    if (cancelled.get()) {
                        out.close();
                        //noinspection ResultOfMethodCallIgnored
                        apk.delete();
                        return null;
                    }
                    out.write(buffer, 0, read);
                    done += read;
                    if (total > 0) {
                        int percent = (int) (done * 100 / total);
                        if (percent != lastPercent) {
                            lastPercent = percent;
                            listener.onProgress(percent);
                        }
                    }
                }
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
