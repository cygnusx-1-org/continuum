package ml.docilealligator.infinityforreddit.viewmodels

import android.content.ContentResolver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import ml.docilealligator.infinityforreddit.SingleLiveEvent
import ml.docilealligator.infinityforreddit.utils.JSONUtils
import ml.docilealligator.infinityforreddit.utils.UploadImageUtils
import org.json.JSONObject
import retrofit2.Retrofit
import java.util.concurrent.Executor

/**
 * Owns the per-image "upload to Reddit's media host" jobs so they survive a configuration change
 * (CHUNKS deferred item 3). Previously each upload reported back through the Activity instance's
 * adapter, so a rotation mid-upload stranded the result and the restore path re-uploaded — leaving
 * Reddit one orphaned media asset per rotation. Here the jobs live in the ViewModel and their
 * outcomes accumulate in [uploadOutcomes]; the Activity applies each (by the image's UUID id,
 * idempotently) to whichever adapter is live, so nothing is re-uploaded on recreation.
 */
class PostGalleryViewModel(
    private val executor: Executor,
    private val oauthRetrofit: Retrofit,
    private val uploadMediaRetrofit: Retrofit,
    private val contentResolver: ContentResolver
) : ViewModel() {

    private val handler = Handler(Looper.getMainLooper())
    private val outcomes = mutableListOf<UploadOutcome>()
    private var inFlightCount = 0

    private val _uploadOutcomes = MutableLiveData<List<UploadOutcome>>(emptyList())
    val uploadOutcomes: LiveData<List<UploadOutcome>> = _uploadOutcomes

    private val _isUploading = MutableLiveData(false)
    val isUploading: LiveData<Boolean> = _isUploading

    private val _uploadFailed = SingleLiveEvent<Void>()
    val uploadFailed: LiveData<Void> = _uploadFailed

    fun uploadImage(uri: Uri, id: String, accessToken: String?) {
        inFlightCount++
        _isUploading.value = true
        executor.execute {
            try {
                val response = UploadImageUtils.uploadImage(
                    oauthRetrofit, uploadMediaRetrofit, contentResolver, accessToken, uri, true, false
                )
                val mediaId = JSONObject(response).getJSONObject(JSONUtils.ASSET_KEY).getString(JSONUtils.ASSET_ID_KEY)
                handler.post {
                    addOutcome(UploadOutcome.Uploaded(id, mediaId))
                    onUploadFinished()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                handler.post {
                    addOutcome(UploadOutcome.Failed(id))
                    _uploadFailed.call()
                    onUploadFinished()
                }
            }
        }
    }

    private fun addOutcome(outcome: UploadOutcome) {
        outcomes.add(outcome)
        _uploadOutcomes.value = ArrayList(outcomes)
    }

    private fun onUploadFinished() {
        inFlightCount--
        if (inFlightCount <= 0) {
            inFlightCount = 0
            _isUploading.value = false
        }
    }

    companion object {
        fun provideFactory(
            executor: Executor,
            oauthRetrofit: Retrofit,
            uploadMediaRetrofit: Retrofit,
            contentResolver: ContentResolver
        ): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                    return PostGalleryViewModel(executor, oauthRetrofit, uploadMediaRetrofit, contentResolver) as T
                }
            }
        }
    }
}

sealed class UploadOutcome {
    abstract val id: String

    data class Uploaded(override val id: String, val mediaId: String) : UploadOutcome()

    data class Failed(override val id: String) : UploadOutcome()
}
