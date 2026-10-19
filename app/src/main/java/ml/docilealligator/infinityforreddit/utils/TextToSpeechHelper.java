package ml.docilealligator.infinityforreddit.utils;

import android.content.Context;
import android.speech.tts.TextToSpeech;
import android.widget.Toast;
import androidx.annotation.Nullable;
import java.util.Locale;
import ml.docilealligator.infinityforreddit.R;

public class TextToSpeechHelper {

    private final Context context;
    @Nullable
    private TextToSpeech tts;
    private boolean initialized;
    @Nullable
    private String pendingText;
    // Bumped by shutdown(), so a late onInit from an engine that was already released is ignored.
    private int generation;

    public TextToSpeechHelper(Context context) {
        this.context = context.getApplicationContext();
    }

    private void initIfNeeded() {
        if (tts != null) {
            return;
        }
        int initGeneration = generation;
        // When no engine is usable, TextToSpeech reports ERROR from inside its constructor, before
        // tts is assigned. onInit then shuts down this generation, and the engine is dropped here.
        TextToSpeech engine = new TextToSpeech(context, status -> onInit(initGeneration, status));
        if (initGeneration == generation) {
            tts = engine;
        } else {
            engine.shutdown();
        }
    }

    private void onInit(int initGeneration, int status) {
        if (initGeneration != generation) {
            // shut down before init completed
            return;
        }
        TextToSpeech engine = tts;
        if (status != TextToSpeech.SUCCESS || engine == null || !setUsableLanguage(engine)) {
            // No usable engine or voice. Release it so the next tap tries again, e.g. after one is
            // installed or its voice data is downloaded.
            shutdown();
            Toast.makeText(context, R.string.tts_not_available, Toast.LENGTH_SHORT).show();
            return;
        }
        initialized = true;
        String text = pendingText;
        pendingText = null;
        if (text != null) {
            speak(text);
        }
    }

    // False when the engine has no voice for the default locale or for US English, e.g. before
    // its voice data has been downloaded. Speaking then would be silent.
    private static boolean setUsableLanguage(TextToSpeech engine) {
        int result = engine.setLanguage(Locale.getDefault());
        if (isLanguageUsable(result)) {
            return true;
        }
        result = engine.setLanguage(Locale.US);
        return isLanguageUsable(result);
    }

    private static boolean isLanguageUsable(int setLanguageResult) {
        return setLanguageResult != TextToSpeech.LANG_MISSING_DATA
                && setLanguageResult != TextToSpeech.LANG_NOT_SUPPORTED;
    }

    public void speak(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        if (initialized && tts != null) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "readAloud");
            return;
        }
        // Queued before initIfNeeded(), which clears it again if init fails synchronously.
        pendingText = text;
        initIfNeeded();
    }

    public void stop() {
        pendingText = null;
        if (tts != null) {
            tts.stop();
        }
    }

    public boolean isSpeaking() {
        return pendingText != null || (tts != null && tts.isSpeaking());
    }

    public void shutdown() {
        pendingText = null;
        generation++;
        initialized = false;
        if (tts != null) {
            tts.stop();
            tts.shutdown();
            tts = null;
        }
    }
}
