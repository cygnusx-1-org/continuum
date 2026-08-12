package ml.docilealligator.infinityforreddit.utils

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

/**
 * Guards a camera-capture intent (`ACTION_IMAGE_CAPTURE` / `ACTION_VIDEO_CAPTURE`) behind the
 * `CAMERA` runtime permission. The app declares `CAMERA` in its manifest (required by the QR
 * scanner), so launching a capture intent while the permission is revoked throws a
 * [SecurityException] and crashes the app.
 *
 * Construct this from an activity's `onCreate` (before the activity is `STARTED`) so the permission
 * launcher registers in time, then call [launch] from the capture entry point.
 *
 * [onPermissionGranted] is supplied at construction rather than per [launch] call so that a grant is
 * still honored after the activity is recreated (e.g. rotation) while the system permission dialog
 * is showing: the recreated activity registers a fresh launcher bound to its own capture action, and
 * the pending result is redelivered to it.
 */
class CameraCapturePermissionHelper(
    activity: ComponentActivity,
    private val onPermissionGranted: Runnable,
    private val onPermissionDenied: Runnable,
) {
    private val appContext = activity.applicationContext
    private val requestPermissionLauncher = activity.registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            onPermissionGranted.run()
        } else {
            onPermissionDenied.run()
        }
    }

    /**
     * Triggers the capture flow. If `CAMERA` is already granted, [onPermissionGranted] runs
     * synchronously before this returns; otherwise the permission is requested and, once the user
     * responds, [onPermissionGranted] or [onPermissionDenied] runs from the result callback. Either
     * way, callers must not assume the capture has happened by the time this method returns.
     */
    fun launch() {
        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            onPermissionGranted.run()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }
}
