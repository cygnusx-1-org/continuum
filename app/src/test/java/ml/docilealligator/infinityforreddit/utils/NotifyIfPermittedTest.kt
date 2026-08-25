package ml.docilealligator.infinityforreddit.utils

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.test.core.app.ApplicationProvider
import ml.docilealligator.infinityforreddit.R
import ml.docilealligator.infinityforreddit.TestInfinity
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * [NotificationUtils.notifyIfPermitted] guards every service notification behind POST_NOTIFICATIONS.
 * That permission only exists from API 33, and asking about it on an older release answers "denied"
 * — so the SDK half of the condition is what keeps download and message notifications working on
 * Android 12 and below, and dropping it would silence them.
 *
 * The suite otherwise runs at a single SDK level, so this is the one place the API-level dimension
 * is varied. The Android 12 case denies the permission the way `ContextCompat` answers it there:
 * below API 33 it does not consult the permission at all, it reports whether notifications are
 * enabled for the app. Turning those off is therefore what makes the check say "no" on that release,
 * and the notification still has to be posted.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestInfinity::class)
class NotifyIfPermittedTest {

    private lateinit var context: Application
    private lateinit var manager: NotificationManager
    private lateinit var notification: Notification

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel("test_channel", "test", NotificationManager.IMPORTANCE_DEFAULT)
        )
        notification = NotificationCompat.Builder(context, "test_channel")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("a title")
            .build()
    }

    private fun postedCount(): Int {
        NotificationUtils.notifyIfPermitted(
            context, NotificationManagerCompat.from(context), 42, notification
        )
        return shadowOf(manager).size()
    }

    @Test
    @Config(sdk = [32])
    fun `a release older than the runtime permission still gets its notifications`() {
        shadowOf(manager).setNotificationsEnabled(false)

        assertEquals(1, postedCount())
    }

    @Test
    @Config(sdk = [33])
    fun `a release with the runtime permission stays quiet until it is granted`() {
        shadowOf(context).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)

        assertEquals(0, postedCount())
    }

    @Test
    @Config(sdk = [33])
    fun `a granted runtime permission posts the notification`() {
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)

        assertEquals(1, postedCount())
    }
}
