package ml.docilealligator.infinityforreddit.viewmodels

import android.content.Context
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
import ml.docilealligator.infinityforreddit.account.Account
import ml.docilealligator.infinityforreddit.comment.Comment
import ml.docilealligator.infinityforreddit.comment.CommentDraft
import ml.docilealligator.infinityforreddit.comment.SendComment
import ml.docilealligator.infinityforreddit.repositories.CommentActivityRepository
import ml.docilealligator.infinityforreddit.thing.GiphyGif
import ml.docilealligator.infinityforreddit.thing.UploadedImage
import retrofit2.Retrofit
import java.util.concurrent.Executor

class CommentActivityViewModel(
    private val commentActivityRepository: CommentActivityRepository,
    private val executor: Executor,
    private val applicationContext: Context
) : ViewModel() {

    private val handler = Handler(Looper.getMainLooper())

    private val _isSubmitting = MutableLiveData(false)
    val isSubmitting: LiveData<Boolean> = _isSubmitting

    private val _sendResult = SingleLiveEvent<SendCommentResult>()
    val sendResult: LiveData<SendCommentResult> = _sendResult

    fun getCommentDraft(fullname: String): LiveData<CommentDraft> {
        return commentActivityRepository.getCommentDraft(fullname)
    }

    fun saveCommentDraft(fullname: String, content: String, onSaved: () -> Unit) {
        viewModelScope.launch {
            commentActivityRepository.saveCommentDraft(fullname, content)
            onSaved()
        }
    }

    fun deleteCommentDraft(fullname: String, onDeleted: () -> Unit) {
        viewModelScope.launch {
            commentActivityRepository.deleteCommentDraft(fullname)
            onDeleted()
        }
    }

    /**
     * Runs the comment send in the ViewModel so its result survives a configuration change (CHUNKS
     * deferred item 4). The caller builds [authenticatorRetrofit] (the per-account authenticator
     * variant) since that is synchronous and cheap; the network call and its outcome live here.
     */
    fun sendComment(
        authenticatorRetrofit: Retrofit,
        account: Account,
        commentMarkdown: String,
        parentFullname: String,
        parentDepth: Int,
        uploadedImages: List<UploadedImage>,
        giphyGif: GiphyGif?
    ) {
        if (_isSubmitting.value == true) {
            return
        }
        _isSubmitting.value = true
        SendComment.sendComment(applicationContext, executor, handler, commentMarkdown, parentFullname, parentDepth,
            uploadedImages, giphyGif, authenticatorRetrofit, account,
            object : SendComment.SendCommentListener {
                override fun sendCommentSuccess(comment: Comment) {
                    _isSubmitting.value = false
                    _sendResult.value = SendCommentResult.Success(comment)
                }

                override fun sendCommentFailed(errorMessage: String?) {
                    _isSubmitting.value = false
                    _sendResult.value = SendCommentResult.Failure(errorMessage)
                }
            })
    }

    companion object {
        fun provideFactory(
            commentActivityRepository: CommentActivityRepository,
            executor: Executor,
            applicationContext: Context
        ): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                    return CommentActivityViewModel(commentActivityRepository, executor, applicationContext) as T
                }
            }
        }
    }
}

sealed class SendCommentResult {
    data class Success(val comment: Comment) : SendCommentResult()

    data class Failure(val message: String?) : SendCommentResult()
}
