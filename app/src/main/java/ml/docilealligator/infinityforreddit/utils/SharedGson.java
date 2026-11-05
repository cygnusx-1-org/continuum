package ml.docilealligator.infinityforreddit.utils;

import com.google.gson.Gson;

/**
 * The plain Gson the app uses wherever it has no special configuration. A Gson caches what it learns
 * about each class it handles, so building a new one for every call threw that away each time; one
 * is safe to share between threads.
 */
public final class SharedGson {
    public static final Gson INSTANCE = new Gson();

    private SharedGson() {
        // Prevent instantiation
    }
}
