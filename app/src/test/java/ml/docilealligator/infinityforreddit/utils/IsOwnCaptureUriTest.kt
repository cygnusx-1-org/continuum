package ml.docilealligator.infinityforreddit.utils

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import ml.docilealligator.infinityforreddit.TestInfinity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [Utils.isOwnCaptureUri] is the safety guard for deferred item 2's post-submit cleanup: only a temp
 * file this app captured (its own FileProvider authority) may be deleted; a user's picked or shared
 * image must never be. These pin that separation so the cleanup can't be widened into deleting a
 * user's photo.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestInfinity::class)
class IsOwnCaptureUriTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun ownFileProviderUriIsACapture() {
        val uri = Uri.parse("content://${context.packageName}.provider/pictures/temp_img123.jpg")
        assertTrue(Utils.isOwnCaptureUri(context, uri))
    }

    @Test
    fun mediaStoreUriIsNotACapture() {
        assertFalse(Utils.isOwnCaptureUri(context, Uri.parse("content://media/external/images/media/42")))
    }

    @Test
    fun photoPickerUriIsNotACapture() {
        val picked = Uri.parse("content://media/picker/0/com.android.providers.media.photopicker/media/1000014162")
        assertFalse(Utils.isOwnCaptureUri(context, picked))
    }

    @Test
    fun nullUriIsNotACapture() {
        assertFalse(Utils.isOwnCaptureUri(context, null))
    }
}
