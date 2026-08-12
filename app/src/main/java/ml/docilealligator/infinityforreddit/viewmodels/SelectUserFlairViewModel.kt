package ml.docilealligator.infinityforreddit.viewmodels

import android.os.Handler
import android.os.Looper
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import ml.docilealligator.infinityforreddit.SingleLiveEvent
import ml.docilealligator.infinityforreddit.user.SelectUserFlair
import ml.docilealligator.infinityforreddit.user.UserFlair
import retrofit2.Retrofit
import java.util.concurrent.Executor

/**
 * Owns the one-shot "select/clear user flair" request so its result survives a configuration
 * change. The (possibly recreated) Activity observes [selectResult]; previously the callback fired
 * on the original instance, so after a rotation the flair was applied server-side while the live
 * screen stayed open on the list showing nothing (CHUNKS deferred item 4).
 */
class SelectUserFlairViewModel(
    private val executor: Executor,
    private val oauthRetrofit: Retrofit
) : ViewModel() {

    private val handler = Handler(Looper.getMainLooper())

    private val _selectResult = SingleLiveEvent<SelectFlairResult>()
    val selectResult: LiveData<SelectFlairResult> = _selectResult

    fun selectUserFlair(accessToken: String?, userFlair: UserFlair?, subredditName: String, accountName: String) {
        val cleared = userFlair == null
        SelectUserFlair.selectUserFlair(executor, handler, oauthRetrofit, accessToken, userFlair, subredditName, accountName,
            object : SelectUserFlair.SelectUserFlairListener {
                override fun success() {
                    _selectResult.value = SelectFlairResult.Success(cleared)
                }

                override fun failed(errorMessage: String?) {
                    _selectResult.value = SelectFlairResult.Failure(errorMessage)
                }
            })
    }

    companion object {
        fun provideFactory(executor: Executor, oauthRetrofit: Retrofit): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                    return SelectUserFlairViewModel(executor, oauthRetrofit) as T
                }
            }
        }
    }
}

sealed class SelectFlairResult {
    /** [cleared] is true when the flair was removed rather than set. */
    data class Success(val cleared: Boolean) : SelectFlairResult()

    data class Failure(val message: String?) : SelectFlairResult()
}
