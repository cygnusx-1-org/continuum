package ml.docilealligator.infinityforreddit.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import ml.docilealligator.infinityforreddit.utils.TextToSpeechHelper

/**
 * Owns an activity's Read Aloud engine. Held here (not on the activity) so playback survives
 * configuration changes such as rotation. The engine is only created on first use, so screens that
 * never read anything aloud never start one.
 */
class TextToSpeechViewModel : ViewModel() {
    private var textToSpeechHelper: TextToSpeechHelper? = null

    fun getTextToSpeechHelper(context: Context): TextToSpeechHelper {
        return textToSpeechHelper ?: TextToSpeechHelper(context).also { textToSpeechHelper = it }
    }

    fun stopTextToSpeech() {
        textToSpeechHelper?.stop()
    }

    fun shutdownTextToSpeech() {
        textToSpeechHelper?.shutdown()
    }

    override fun onCleared() {
        super.onCleared()
        textToSpeechHelper?.shutdown()
        textToSpeechHelper = null
    }
}
