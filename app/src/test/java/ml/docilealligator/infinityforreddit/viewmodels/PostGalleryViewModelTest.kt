package ml.docilealligator.infinityforreddit.viewmodels

import android.content.ContentResolver
import android.net.Uri
import android.os.Looper
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import ml.docilealligator.infinityforreddit.TestInfinity
import ml.docilealligator.infinityforreddit.utils.UploadImageUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import java.io.IOException
import java.util.concurrent.Executor

/**
 * The gallery upload (CHUNKS deferred item 3) now lives in [PostGalleryViewModel] so it survives a
 * configuration change. These tests pin the three properties the fix relies on: a completed upload
 * surfaces exactly one [UploadOutcome], the in-flight flag toggles back off, and — the regression
 * the old restore-path re-upload caused — re-observing the outcomes never triggers a second upload
 * (so Reddit never gets a duplicate media asset per rotation).
 *
 * [UploadImageUtils.uploadImage] is a static that does real network I/O, so it is mocked; that also
 * exercises Mockito's inline maker on this JDK-25 toolchain.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestInfinity::class)
class PostGalleryViewModelTest {

    @get:Rule
    val instantRule = InstantTaskExecutorRule()

    private val inlineExecutor = Executor { it.run() }
    private val uri: Uri = Uri.parse("content://media/pick/1")

    private fun newViewModel() = PostGalleryViewModel(
        inlineExecutor, mock<Retrofit>(), mock<Retrofit>(), mock<ContentResolver>()
    )

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    @Test
    fun successProducesOneUploadedOutcomeAndClearsInFlight() {
        Mockito.mockStatic(UploadImageUtils::class.java).use { mocked ->
            whenever(
                UploadImageUtils.uploadImage(any(), any(), any(), anyOrNull(), any(), anyBoolean(), anyBoolean())
            ).thenReturn("""{"asset":{"asset_id":"MEDIA123"}}""")

            val vm = newViewModel()
            vm.uploadImage(uri, "img-1", "token")
            assertTrue("isUploading must be true while the upload is in flight", vm.isUploading.value == true)
            idle()

            val outcomes = vm.uploadOutcomes.value.orEmpty()
            assertEquals(1, outcomes.size)
            val uploaded = outcomes.single() as UploadOutcome.Uploaded
            assertEquals("img-1", uploaded.id)
            assertEquals("MEDIA123", uploaded.mediaId)
            assertFalse("isUploading must clear once the only upload finished", vm.isUploading.value == true)

            // The crux of item 3: re-observing the accumulated outcomes must not kick off another
            // upload — otherwise a rotation (which re-subscribes) would duplicate the media asset.
            vm.uploadOutcomes.observeForever { }
            idle()
            mocked.verify(
                { UploadImageUtils.uploadImage(any(), any(), any(), anyOrNull(), any(), anyBoolean(), anyBoolean()) },
                times(1)
            )
            assertEquals(1, vm.uploadOutcomes.value.orEmpty().size)
        }
    }

    @Test
    fun failureProducesFailedOutcomeAndSignalsUploadFailed() {
        Mockito.mockStatic(UploadImageUtils::class.java).use { mocked ->
            whenever(
                UploadImageUtils.uploadImage(any(), any(), any(), anyOrNull(), any(), anyBoolean(), anyBoolean())
            ).thenThrow(IOException("boom"))

            val vm = newViewModel()
            var uploadFailedCount = 0
            vm.uploadFailed.observeForever { uploadFailedCount++ }

            vm.uploadImage(uri, "img-2", "token")
            idle()

            val outcomes = vm.uploadOutcomes.value.orEmpty()
            assertEquals(1, outcomes.size)
            assertEquals("img-2", (outcomes.single() as UploadOutcome.Failed).id)
            assertEquals("uploadFailed must fire exactly once", 1, uploadFailedCount)
            assertFalse(vm.isUploading.value == true)
        }
    }
}
