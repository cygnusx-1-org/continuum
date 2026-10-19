package ml.docilealligator.infinityforreddit.utils;

import android.content.Context;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.widget.Toast;
import androidx.annotation.Nullable;
import java.util.Locale;
import ml.docilealligator.infinityforreddit.R;

public class TextToSpeechHelper {

    private static final String DBG_TAG = "ReadAloudDbg"; // TTSDBG

    private final Context context;
    @Nullable
    private TextToSpeech tts;
    private boolean initialized;
    @Nullable
    private String pendingText;

    public TextToSpeechHelper(Context context) {
        this.context = context.getApplicationContext();
    }

    private void initIfNeeded() {
        Log.d(DBG_TAG, "TTSDBG initIfNeeded: tts==null " + (tts == null) + ", thread " + Thread.currentThread().getName()); // TTSDBG
        if (tts == null) {
            tts = new TextToSpeech(context, status -> {
                Log.d(DBG_TAG, "TTSDBG onInit: status " + status + ", tts==null " + (tts == null) + ", pendingText==null " + (pendingText == null) + ", thread " + Thread.currentThread().getName()); // TTSDBG
                if (tts == null) {
                    // shut down before init completed
                    Log.d(DBG_TAG, "TTSDBG onInit: dropped because tts==null"); // TTSDBG
                    return;
                }
                if (status == TextToSpeech.SUCCESS) {
                    int result = tts.setLanguage(Locale.getDefault());
                    Log.d(DBG_TAG, "TTSDBG onInit: setLanguage(" + Locale.getDefault() + ") -> " + result); // TTSDBG
                    if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                        Log.d(DBG_TAG, "TTSDBG onInit: setLanguage(US) -> " + tts.setLanguage(Locale.US)); // TTSDBG
                    }
                    initialized = true;
                    if (pendingText != null) {
                        speak(pendingText);
                        pendingText = null;
                    }
                } else {
                    Log.d(DBG_TAG, "TTSDBG onInit: failed, showing tts_not_available toast"); // TTSDBG
                    pendingText = null;
                    Toast.makeText(context, R.string.tts_not_available, Toast.LENGTH_SHORT).show();
                }
            });
            Log.d(DBG_TAG, "TTSDBG initIfNeeded: constructor returned, initialized " + initialized + ", defaultEngine " + tts.getDefaultEngine() + ", engines " + tts.getEngines().size()); // TTSDBG
        }
    }

    public void speak(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        Log.d(DBG_TAG, "TTSDBG speak: length " + text.length() + ", initialized " + initialized); // TTSDBG
        initIfNeeded();
        if (!initialized) {
            Log.d(DBG_TAG, "TTSDBG speak: not initialized, storing pendingText"); // TTSDBG
            pendingText = text;
            return;
        }
        if (tts != null) {
            int speakResult = tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "readAloud"); // TTSDBG
            Log.d(DBG_TAG, "TTSDBG speak: tts.speak -> " + speakResult); // TTSDBG
        }
    }

    public void stop() {
        pendingText = null;
        if (tts != null) {
            tts.stop();
        }
    }

    public boolean isSpeaking() {
        Log.d(DBG_TAG, "TTSDBG isSpeaking: pendingText==null " + (pendingText == null) + ", tts==null " + (tts == null) + ", tts.isSpeaking " + (tts != null && tts.isSpeaking())); // TTSDBG
        return pendingText != null || (tts != null && tts.isSpeaking());
    }

    public void shutdown() {
        Log.d(DBG_TAG, "TTSDBG shutdown: tts==null " + (tts == null)); // TTSDBG
        pendingText = null;
        if (tts != null) {
            tts.stop();
            tts.shutdown();
            tts = null;
            initialized = false;
        }
    }
}
