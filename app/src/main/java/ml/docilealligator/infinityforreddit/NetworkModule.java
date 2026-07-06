package ml.docilealligator.infinityforreddit;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.NonNull;
import dagger.Module;
import dagger.Provides;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.concurrent.TimeUnit;
import javax.inject.Named;
import javax.inject.Singleton;
import ml.docilealligator.infinityforreddit.apimonitor.ApiCallTracker;
import ml.docilealligator.infinityforreddit.apimonitor.ApiMonitorEventListener;
import ml.docilealligator.infinityforreddit.apis.StreamableAPI;
import ml.docilealligator.infinityforreddit.apis.StreamableAPIKt;
import ml.docilealligator.infinityforreddit.network.AccessTokenAuthenticator;
import ml.docilealligator.infinityforreddit.network.AnonymousAccessTokenInterceptor;
import ml.docilealligator.infinityforreddit.network.RedgifsAccessTokenAuthenticator;
import ml.docilealligator.infinityforreddit.network.ServerAccessTokenAuthenticator;
import ml.docilealligator.infinityforreddit.network.SortTypeConverterFactory;
import ml.docilealligator.infinityforreddit.utils.APIUtils;
import ml.docilealligator.infinityforreddit.utils.SharedPreferencesUtils;
import okhttp3.ConnectionPool;
import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import retrofit2.Retrofit;
import retrofit2.adapter.guava.GuavaCallAdapterFactory;
import retrofit2.converter.scalars.ScalarsConverterFactory;

@Module(includes = AppModule.class)
abstract class NetworkModule {

    @Provides
    @Named("base")
    @Singleton
    static OkHttpClient provideBaseOkhttp(@Named("proxy") SharedPreferences mProxySharedPreferences,
                                          ApiCallTracker apiCallTracker) {
        boolean proxyEnabled = mProxySharedPreferences.getBoolean(SharedPreferencesUtils.PROXY_ENABLED, false);

        var builder = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .eventListenerFactory(new ApiMonitorEventListener.Factory(apiCallTracker))
                .addInterceptor(chain -> {
                    if (chain.request().header("User-Agent") == null) {
                        return chain.proceed(
                                chain.request()
                                        .newBuilder()
                                        .header("User-Agent", APIUtils.USER_AGENT)
                                        .build()
                        );
                    } else {
                        return chain.proceed(chain.request());
                    }
                });

        if (proxyEnabled) {
            Proxy.Type proxyType = Proxy.Type.valueOf(mProxySharedPreferences.getString(SharedPreferencesUtils.PROXY_TYPE, "HTTP"));
            if (proxyType != Proxy.Type.DIRECT) {
                String proxyHost = mProxySharedPreferences.getString(SharedPreferencesUtils.PROXY_HOSTNAME, "127.0.0.1");
                int proxyPort = Integer.parseInt(mProxySharedPreferences.getString(SharedPreferencesUtils.PROXY_PORT, "1080"));

                InetSocketAddress proxyAddr = new InetSocketAddress(proxyHost, proxyPort);
                Proxy proxy = new Proxy(proxyType, proxyAddr);
                builder.proxy(proxy);
            }
        }

        return builder.build();
    }

    @Provides
    @Named("base")
    @Singleton
    static Retrofit provideBaseRetrofit(@Named("base") OkHttpClient okHttpClient) {
        return new Retrofit.Builder()
            .baseUrl(APIUtils.API_BASE_URI)
            .client(okHttpClient)
            .addConverterFactory(ScalarsConverterFactory.create())
            .addConverterFactory(SortTypeConverterFactory.create())
            .addCallAdapterFactory(GuavaCallAdapterFactory.create())
            .build();
    }

    @Provides
    static ConnectionPool provideConnectionPool() {
        // OkHttp's default pool: keep up to 5 idle connections alive for 5 minutes so requests
        // reuse warm TCP/TLS connections instead of paying a fresh DNS+TCP+TLS handshake every call.
        // The previous ConnectionPool(0, 1, NANOSECONDS) disabled keep-alive entirely as a 2020-era
        // workaround for stale-connection timeouts in okhttp3, which modern OkHttp (5.x) handles.
        return new ConnectionPool();
    }

    @Provides
    @Named("no_oauth")
    static Retrofit provideRetrofit(@Named("base") Retrofit retrofit, @Named("anonymous") OkHttpClient okHttpClient) {
        return retrofit.newBuilder()
                .client(okHttpClient)
                .build();
    }

    @Provides
    @Named("oauth")
    static Retrofit provideOAuthRetrofit(@Named("base") Retrofit retrofit,
        @Named("default") OkHttpClient okHttpClient) {

        return retrofit.newBuilder().baseUrl(APIUtils.OAUTH_API_BASE_URI).client(okHttpClient).build();
    }

    @Provides
    @Named("anonymous")
    @Singleton
    static OkHttpClient provideCookieOkHttpClient(Context context,
                                            @Named("base") OkHttpClient httpClient,
                                            @Named("base") Retrofit retrofit,
                                            RedditDataRoomDatabase redditDataRoomDatabase,
                                            ConnectionPool connectionPool) {
        AnonymousAccessTokenInterceptor anonymousAccessTokenInterceptor
                = new AnonymousAccessTokenInterceptor(context, retrofit, redditDataRoomDatabase);

        return httpClient.newBuilder()
                .addInterceptor(anonymousAccessTokenInterceptor)
                .connectionPool(connectionPool)
                .build();
    }

    @Provides
    @Named("default")
    @Singleton
    static OkHttpClient provideOkHttpClient(Context context,
        @Named("base") OkHttpClient httpClient,
        @Named("base") Retrofit retrofit,
        RedditDataRoomDatabase redditDataRoomDatabase,
        @Named("current_account") SharedPreferences currentAccountSharedPreferences,
        ConnectionPool connectionPool) {

        return httpClient.newBuilder()
            // Fetch clientId once and pass it to the authenticator
            .authenticator(new AccessTokenAuthenticator(APIUtils.getClientId(context), retrofit, redditDataRoomDatabase, currentAccountSharedPreferences))
            .connectionPool(connectionPool)
            .build();
    }

    @Provides
    @Named("server")
    @Singleton
    static OkHttpClient provideServerOkHttpClient(@Named("base") OkHttpClient httpClient,
        RedditDataRoomDatabase redditDataRoomDatabase,
        @Named("current_account") SharedPreferences currentAccountSharedPreferences,
        ConnectionPool connectionPool) {

        return httpClient.newBuilder()
            .authenticator(new ServerAccessTokenAuthenticator(redditDataRoomDatabase, currentAccountSharedPreferences))
            .connectionPool(connectionPool)
            .build();
    }

    @Provides
    @Named("media3")
    @Singleton
    static OkHttpClient provideMedia3OkHttpClient(@Named("base") OkHttpClient httpClient,
                                            ConnectionPool connectionPool) {
        return httpClient.newBuilder()
                .connectionPool(connectionPool)
                .followRedirects(false)
                .addInterceptor(new Interceptor() {
                    @NonNull
                    @Override
                    public Response intercept(@NonNull Chain chain) throws IOException {
                        Request request = chain.request();
                        Response response = chain.proceed(request);

                        int redirectCount = 0;
                        while (isRedirect(response.code()) && redirectCount < 5) {
                            String location = response.header("Location");
                            if (location == null) break;

                            HttpUrl newUrl = response.request().url().resolve(location);
                            if (newUrl == null) break;

                            request = request.newBuilder()
                                    .url(newUrl)
                                    .build();

                            response.close(); // Close the previous response before continuing
                            response = chain.proceed(request);
                            redirectCount++;
                        }

                        return response;
                    }

                    private boolean isRedirect(int code) {
                        return code == 301 || code == 302 || code == 303 || code == 307 || code == 308;
                    }
                })
                .build();
    }

    @Provides
    @Named("oauth_without_authenticator")
    @Singleton
    static Retrofit provideOauthWithoutAuthenticatorRetrofit(@Named("base") Retrofit retrofit) {
        return retrofit.newBuilder().baseUrl(APIUtils.OAUTH_API_BASE_URI).build();
    }

    @Provides
    @Named("upload_media")
    @Singleton
    static Retrofit provideUploadMediaRetrofit(@Named("base") Retrofit retrofit) {
        return retrofit.newBuilder().baseUrl(APIUtils.API_UPLOAD_MEDIA_URI).build();
    }

    @Provides
    @Named("upload_video")
    @Singleton
    static Retrofit provideUploadVideoRetrofit(@Named("base") Retrofit retrofit) {
        return retrofit.newBuilder().baseUrl(APIUtils.API_UPLOAD_VIDEO_URI).build();
    }

    @Provides
    @Named("download_media")
    @Singleton
    static Retrofit provideDownloadRedditVideoRetrofit(@Named("base") Retrofit retrofit) {
        return retrofit.newBuilder().baseUrl("http://localhost/").build();
    }

    @Provides
    @Named("RedgifsAccessTokenAuthenticator")
    static Interceptor redgifsAccessTokenAuthenticator(@Named("current_account") SharedPreferences currentAccountSharedPreferences) {
        return new RedgifsAccessTokenAuthenticator(currentAccountSharedPreferences);
    }

    @Provides
    @Named("redgifs")
    @Singleton
    static Retrofit provideRedgifsRetrofit(@Named("RedgifsAccessTokenAuthenticator") Interceptor accessTokenAuthenticator,
        @Named("base") OkHttpClient httpClient,
        @Named("base") Retrofit retrofit,
        ConnectionPool connectionPool) {

        OkHttpClient.Builder okHttpClientBuilder = httpClient.newBuilder()
                .addInterceptor(chain -> chain.proceed(
                        chain.request()
                                .newBuilder()
                                .header("User-Agent", APIUtils.USER_AGENT)
                                .build()
                ))
                .addInterceptor(accessTokenAuthenticator)
                .connectionPool(connectionPool);

        return retrofit.newBuilder()
                .baseUrl(APIUtils.REDGIFS_API_BASE_URI)
                .client(okHttpClientBuilder.build())
                .build();
    }

    /*@Provides
    @Named("redgifs")
    @Singleton
    static Retrofit provideRedgifsRetrofit(@Named("base") Retrofit retrofit) {
        return retrofit.newBuilder()
                .baseUrl(APIUtils.OH_MY_DL_BASE_URI)
                .build();
    }*/

    @Provides
    @Named("imgur")
    @Singleton
    static Retrofit provideImgurRetrofit(@Named("base") Retrofit retrofit) {
        return retrofit.newBuilder().baseUrl(APIUtils.IMGUR_API_BASE_URI).build();
    }

    @Provides
    @Named("vReddIt")
    @Singleton
    static Retrofit provideVReddItRetrofit(@Named("base") Retrofit retrofit) {
        return retrofit.newBuilder().baseUrl("http://localhost/").build();
    }

    @Provides
    @Named("streamable")
    @Singleton
    static Retrofit provideStreamableRetrofit(@Named("base") Retrofit retrofit) {
        return retrofit.newBuilder().baseUrl(APIUtils.STREAMABLE_API_BASE_URI).build();
    }

    @Provides
    @Named("arctic_shift")
    @Singleton
    static Retrofit provideArcticShiftRetrofit(@Named("base") Retrofit retrofit) {
        return retrofit.newBuilder().baseUrl(APIUtils.ARCTIC_SHIFT_API_BASE_URI).build();
    }

    @Provides
    @Named("online_custom_themes")
    @Singleton
    static Retrofit provideOnlineCustomThemesRetrofit(@Named("base") Retrofit retrofit, @Named("server") OkHttpClient httpClient) {
        return retrofit.newBuilder().baseUrl(APIUtils.SERVER_API_BASE_URI).client(httpClient).build();
    }

    @Provides
    @Singleton
    static StreamableAPI provideStreamableApi(@Named("streamable") Retrofit streamableRetrofit) {
        return streamableRetrofit.create(StreamableAPI.class);
    }

    @Provides
    @Singleton
    static StreamableAPIKt provideStreamableApiKt(@Named("streamable") Retrofit streamableRetrofit) {
        return streamableRetrofit.create(StreamableAPIKt.class);
    }
}
