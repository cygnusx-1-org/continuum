package ml.docilealligator.infinityforreddit.utils;

import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.widget.Toast;
import ml.docilealligator.infinityforreddit.resume.ResumeState;

public class AppRestartHelper {

    private static final String TAG = "AppRestartHelper";

    /**
     * Restarts the application by clearing the task stack and launching the main activity.
     * Also kills the current process to ensure a clean restart.
     * Shows a toast message as a fallback if restarting via Intent fails.
     *
     * @param context Context used to get application context, package manager, and show toasts.
     */
    public static void triggerAppRestart(Context context) {
        try {
            Context appContext = context.getApplicationContext();
            // Use application context for getPackageManager and getPackageName
            Intent intent = appContext.getPackageManager().getLaunchIntentForPackage(appContext.getPackageName());
            if (intent != null) {
                // Clear the activity stack and start the launch activity as a new task.
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                // A restart the app asked for itself is not the user reopening the app: they were
                // in Settings, and "Resume where I left off" would put them back there instead of
                // in the app they just finished configuring.
                intent.putExtra(ResumeState.EXTRA_SKIP_RESUME, true);
                appContext.startActivity(intent);
                Log.i(TAG, "Triggering app restart via Intent.");

                // Request the current process be killed. System will eventually restart it.
                // This ensures that services and other components are also restarted.
                android.os.Process.killProcess(android.os.Process.myPid());
                // System.exit(0); // Alternative to killProcess, might be slightly cleaner in some cases
            } else {
                Log.e(TAG, "Could not get launch intent for package to trigger restart.");
                // Use application context for Toast as well
                Toast.makeText(appContext, "Please restart the app manually.", Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error triggering app restart", e);
            // Use application context for Toast
            Toast.makeText(context.getApplicationContext(), "Please restart the app manually.", Toast.LENGTH_LONG).show();
        }
    }
}
