package ml.docilealligator.infinityforreddit.utils;

import android.content.Context;
import android.speech.tts.TextToSpeech;
import android.widget.Toast;

import java.util.Locale;

import ml.docilealligator.infinityforreddit.R;

public class TextToSpeechHelper {

    private final Context context;
    private TextToSpeech tts;
    private boolean initialized;
    private String pendingText;

    public TextToSpeechHelper(Context context) {
        this.context = context.getApplicationContext();
    }

    private void initIfNeeded() {
        if (tts == null) {
            tts = new TextToSpeech(context, status -> {
                if (status == TextToSpeech.SUCCESS) {
                    int result = tts.setLanguage(Locale.getDefault());
                    if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                        tts.setLanguage(Locale.US);
                    }
                    initialized = true;
                    if (pendingText != null) {
                        speak(pendingText);
                        pendingText = null;
                    }
                } else {
                    Toast.makeText(context, R.string.tts_not_available, Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    public void speak(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        initIfNeeded();
        if (!initialized) {
            pendingText = text;
            return;
        }
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "readAloud");
    }

    public void stop() {
        if (tts != null) {
            tts.stop();
        }
    }

    public boolean isSpeaking() {
        return tts != null && tts.isSpeaking();
    }

    public void shutdown() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
            tts = null;
            initialized = false;
        }
    }
}
