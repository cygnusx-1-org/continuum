package ml.docilealligator.infinityforreddit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import okhttp3.EventListener;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.junit.Test;
import retrofit2.Retrofit;
import retrofit2.converter.scalars.ScalarsConverterFactory;

/**
 * Plain-JVM checks of the {@code title_suggestion} Retrofit wiring. Written in Java because
 * {@link NetworkModule} is package-private.
 */
public class NetworkModuleTest {

    @Test
    public void titleSuggestionClientCarriesNoApiMonitorListener() {
        // Stands in for the ApiMonitorEventListener.Factory installed on the real base client.
        EventListener marker = new EventListener() {
        };
        OkHttpClient baseClient = new OkHttpClient.Builder()
                .eventListenerFactory(call -> marker)
                .build();
        Retrofit baseRetrofit = new Retrofit.Builder()
                .baseUrl("https://www.reddit.com/")
                .client(baseClient)
                .addConverterFactory(ScalarsConverterFactory.create())
                .build();

        Retrofit titleSuggestion = NetworkModule.provideTitleSuggestionRetrofit(baseRetrofit, baseClient);

        // Fetches of arbitrary user-typed URLs must not land in the API monitor's stats.
        OkHttpClient client = (OkHttpClient) titleSuggestion.callFactory();
        EventListener listener = client.eventListenerFactory()
                .create(client.newCall(new Request.Builder().url("https://example.com/").build()));
        assertSame(EventListener.NONE, listener);

        assertEquals("http://localhost/", titleSuggestion.baseUrl().toString());
    }
}
