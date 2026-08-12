package ml.docilealligator.infinityforreddit.viewmodels

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import ml.docilealligator.infinityforreddit.SingleLiveEvent
import ml.docilealligator.infinityforreddit.apis.RedditAPI
import ml.docilealligator.infinityforreddit.utils.APIUtils
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit

/**
 * Owns the one-shot "edit post" network call so its result survives a configuration change. The
 * call and its outcome live in the ViewModel; the (possibly recreated) Activity observes
 * [isSubmitting] and [editResult]. Previously the Retrofit callback fired on the original Activity
 * instance, so a rotation mid-edit dropped the result and the caller refreshed the post back to its
 * unchanged text (CHUNKS deferred item 4).
 */
class EditPostActivityViewModel(
    private val oauthRetrofit: Retrofit
) : ViewModel() {

    private val _isSubmitting = MutableLiveData(false)
    val isSubmitting: LiveData<Boolean> = _isSubmitting

    private val _editResult = SingleLiveEvent<EditPostResult>()
    val editResult: LiveData<EditPostResult> = _editResult

    fun editPost(accessToken: String?, thingFullname: String, text: String) {
        if (_isSubmitting.value == true) {
            return
        }
        _isSubmitting.value = true

        val params = HashMap<String, String>()
        params[APIUtils.API_TYPE_KEY] = APIUtils.API_TYPE_JSON
        params[APIUtils.THING_ID_KEY] = thingFullname
        params[APIUtils.TEXT_KEY] = text

        oauthRetrofit.create(RedditAPI::class.java)
            .editPostOrComment(APIUtils.getOAuthHeader(accessToken), params)
            .enqueue(object : Callback<String> {
                override fun onResponse(call: Call<String>, response: Response<String>) {
                    _isSubmitting.value = false
                    // Retrofit routes every HTTP response here (onFailure covers only network/parse
                    // errors), and Reddit reports api_type=json failures inside an otherwise-200 body,
                    // so a 4xx/5xx or a json.errors body must not be treated as a successful edit.
                    if (!response.isSuccessful) {
                        _editResult.value = EditPostResult.Failure(null)
                        return
                    }
                    val apiError = APIUtils.parseApiErrorMessage(response.body())
                    if (apiError != null) {
                        _editResult.value = EditPostResult.Failure(apiError)
                        return
                    }
                    _editResult.value = EditPostResult.Success
                }

                override fun onFailure(call: Call<String>, t: Throwable) {
                    _isSubmitting.value = false
                    _editResult.value = EditPostResult.Failure(null)
                }
            })
    }

    companion object {
        fun provideFactory(oauthRetrofit: Retrofit): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                    return EditPostActivityViewModel(oauthRetrofit) as T
                }
            }
        }
    }
}

sealed class EditPostResult {
    object Success : EditPostResult()

    /** [message] is the Reddit error to show, or null for a generic post_failed. */
    data class Failure(val message: String?) : EditPostResult()
}
