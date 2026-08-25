package ml.docilealligator.infinityforreddit.broadcastreceivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import ml.docilealligator.infinityforreddit.utils.SharedPreferencesUtils;
import ml.docilealligator.infinityforreddit.worker.MaterialYouWorker;

public class WallpaperChangeReceiver extends BroadcastReceiver {
    private final SharedPreferences sharedPreferences;

    public WallpaperChangeReceiver(SharedPreferences sharedPreferences) {
        this.sharedPreferences = sharedPreferences;
    }

    /**
     * Shared with Infinity's WallpaperManager colours-changed listener, which stands in for this
     * receiver from API 27 on.
     */
    public static void enqueueMaterialYouWork(Context context, SharedPreferences sharedPreferences) {
        if (sharedPreferences.getBoolean(SharedPreferencesUtils.ENABLE_MATERIAL_YOU, false)) {
            OneTimeWorkRequest materialYouRequest = OneTimeWorkRequest.from(MaterialYouWorker.class);
            WorkManager.getInstance(context).enqueueUniqueWork(MaterialYouWorker.UNIQUE_WORKER_NAME,
                    ExistingWorkPolicy.REPLACE, materialYouRequest);
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        enqueueMaterialYouWork(context, sharedPreferences);
    }
}
