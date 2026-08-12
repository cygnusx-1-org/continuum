package ml.docilealligator.infinityforreddit.viewmodels

import android.os.Handler
import android.os.Looper
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import ml.docilealligator.infinityforreddit.RedditDataRoomDatabase
import ml.docilealligator.infinityforreddit.SingleLiveEvent
import ml.docilealligator.infinityforreddit.postfilter.PostFilter
import ml.docilealligator.infinityforreddit.postfilter.SavePostFilter
import java.util.concurrent.Executor

/**
 * Owns the "save post filter" database write so its result survives a configuration change. The
 * Activity observes [saveResult] and reacts on the live instance — in particular the duplicate-name
 * dialog is shown from the observer, which fixes the crash/silent-no-op the old
 * `isFinishing()/isDestroyed()` guard papered over (CHUNKS deferred item 4).
 */
class CustomizePostFilterViewModel(
    private val executor: Executor,
    private val roomDatabase: RedditDataRoomDatabase
) : ViewModel() {

    private val handler = Handler(Looper.getMainLooper())

    private val _saveResult = SingleLiveEvent<SavePostFilterResult>()
    val saveResult: LiveData<SavePostFilterResult> = _saveResult

    // Guards against a double-tap starting a second concurrent save. Only ever touched on the main
    // thread: savePostFilter() is called from the UI, and the callbacks below are posted via handler.
    // Reset on both outcomes so the duplicate-dialog "Override" re-save can still proceed.
    private var isSaving = false

    fun savePostFilter(postFilter: PostFilter, originalName: String) {
        if (isSaving) {
            return
        }
        isSaving = true
        SavePostFilter.savePostFilter(executor, handler, roomDatabase, postFilter, originalName,
            object : SavePostFilter.SavePostFilterListener {
                override fun success() {
                    isSaving = false
                    _saveResult.value = SavePostFilterResult.Success
                }

                override fun duplicate() {
                    isSaving = false
                    _saveResult.value = SavePostFilterResult.Duplicate
                }

                override fun failed() {
                    isSaving = false
                    _saveResult.value = SavePostFilterResult.Failure
                }
            })
    }

    companion object {
        fun provideFactory(executor: Executor, roomDatabase: RedditDataRoomDatabase): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                    return CustomizePostFilterViewModel(executor, roomDatabase) as T
                }
            }
        }
    }
}

sealed class SavePostFilterResult {
    object Success : SavePostFilterResult()

    object Duplicate : SavePostFilterResult()

    /** The DB write threw and rolled back; shown to the user as a generic failure. */
    object Failure : SavePostFilterResult()
}
