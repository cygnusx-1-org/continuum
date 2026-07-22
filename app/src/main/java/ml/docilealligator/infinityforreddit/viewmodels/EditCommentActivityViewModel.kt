package ml.docilealligator.infinityforreddit.viewmodels

import android.os.Handler
import android.os.Looper
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import kotlinx.coroutines.launch
import ml.docilealligator.infinityforreddit.SingleLiveEvent
import ml.docilealligator.infinityforreddit.apis.RedditAPI
import ml.docilealligator.infinityforreddit.comment.Comment
import ml.docilealligator.infinityforreddit.comment.CommentDraft
import ml.docilealligator.infinityforreddit.comment.ParseComment
import ml.docilealligator.infinityforreddit.repositories.EditCommentActivityRepository
import ml.docilealligator.infinityforreddit.utils.APIUtils
import ml.docilealligator.infinityforreddit.utils.JSONUtils
import org.json.JSONException
import org.json.JSONObject
import retrofit2.Retrofit
import java.io.IOException
import java.util.concurrent.Executor

class EditCommentActivityViewModel(
    private val editCommentActivityRepository: EditCommentActivityRepository,
    private val executor: Executor,
    private val oauthRetrofit: Retrofit
) : ViewModel() {

    private val handler = Handler(Looper.getMainLooper())

    private val _isSubmitting = MutableLiveData(false)
    val isSubmitting: LiveData<Boolean> = _isSubmitting

    private val _editResult = SingleLiveEvent<EditCommentResult>()
    val editResult: LiveData<EditCommentResult> = _editResult

    fun getCommentDraft(fullname: String): LiveData<CommentDraft> {
        return editCommentActivityRepository.getCommentDraft(fullname)
    }

    fun saveCommentDraft(fullname: String, content: String, onSaved: () -> Unit) {
        viewModelScope.launch {
            editCommentActivityRepository.saveCommentDraft(fullname, content)
            onSaved()
        }
    }

    fun deleteCommentDraft(fullname: String, onDeleted: () -> Unit) {
        viewModelScope.launch {
            editCommentActivityRepository.deleteCommentDraft(fullname)
            onDeleted()
        }
    }

    /**
     * Runs the edit in the ViewModel so its result survives a configuration change (CHUNKS deferred
     * item 4). [params] is the fully built request map (plain text or richtext, api_type=json).
     * [editedContent] is echoed back in [EditCommentResult.Success] so the Activity can fall back to
     * the local text when the returned comment can't be parsed.
     */
    fun editComment(accessToken: String?, params: Map<String, String>, editedContent: String) {
        if (_isSubmitting.value == true) {
            return
        }
        _isSubmitting.value = true
        executor.execute {
            try {
                val response = oauthRetrofit.create(RedditAPI::class.java)
                    .editPostOrComment(APIUtils.getOAuthHeader(accessToken), params).execute()
                val body = response.body()
                if (response.isSuccessful && body != null) {
                    // Reddit reports api_type=json failures inside an otherwise-200 body; check before
                    // treating the response as a successful edit.
                    val apiError = APIUtils.parseApiErrorMessage(body)
                    if (apiError != null) {
                        postResult(EditCommentResult.Failure(apiError))
                        return@execute
                    }
                    var comment: Comment? = null
                    try {
                        var data = JSONObject(body)
                        if (!data.has(JSONUtils.ID_KEY) && data.has(JSONUtils.JSON_KEY)) {
                            data = data.getJSONObject(JSONUtils.JSON_KEY).getJSONObject(JSONUtils.DATA_KEY)
                                .getJSONArray(JSONUtils.THINGS_KEY).getJSONObject(0).getJSONObject(JSONUtils.DATA_KEY)
                        }
                        comment = ParseComment.parseSingleComment(data, 0)
                    } catch (e: JSONException) {
                        e.printStackTrace()
                    }
                    postResult(EditCommentResult.Success(comment, editedContent))
                } else {
                    postResult(EditCommentResult.Failure(null))
                }
            } catch (e: IOException) {
                e.printStackTrace()
                postResult(EditCommentResult.Failure(null))
            }
        }
    }

    private fun postResult(result: EditCommentResult) {
        handler.post {
            _isSubmitting.value = false
            _editResult.value = result
        }
    }

    companion object {
        fun provideFactory(
            editCommentActivityRepository: EditCommentActivityRepository,
            executor: Executor,
            oauthRetrofit: Retrofit
        ): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                    return EditCommentActivityViewModel(editCommentActivityRepository, executor, oauthRetrofit) as T
                }
            }
        }
    }
}

sealed class EditCommentResult {
    /**
     * [comment] is the parsed edited comment, or null when the body couldn't be parsed and the caller
     * should fall back to [editedContent].
     */
    data class Success(val comment: Comment?, val editedContent: String) : EditCommentResult()

    data class Failure(val message: String?) : EditCommentResult()
}
