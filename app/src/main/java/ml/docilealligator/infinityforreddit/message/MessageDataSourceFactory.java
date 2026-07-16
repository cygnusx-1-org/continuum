package ml.docilealligator.infinityforreddit.message;

import android.os.Handler;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.MutableLiveData;
import androidx.paging.DataSource;
import java.util.Locale;
import java.util.concurrent.Executor;
import retrofit2.Retrofit;

class MessageDataSourceFactory extends DataSource.Factory {
    private final Executor executor;
    private final Handler handler;
    private final Retrofit oauthRetrofit;
    private final Locale locale;
    private final String accessToken;
    private String where;

    @Nullable
    private MessageDataSource messageDataSource;
    private final MutableLiveData<MessageDataSource> messageDataSourceLiveData;

    MessageDataSourceFactory(Executor executor, Handler handler, Retrofit oauthRetrofit, Locale locale, String accessToken, String where) {
        this.executor = executor;
        this.handler = handler;
        this.oauthRetrofit = oauthRetrofit;
        this.locale = locale;
        this.accessToken = accessToken;
        this.where = where;
        messageDataSourceLiveData = new MutableLiveData<>();
    }

    @NonNull
    @Override
    public DataSource create() {
        MessageDataSource dataSource = new MessageDataSource(executor, handler, oauthRetrofit, locale, accessToken, where);
        messageDataSource = dataSource;
        messageDataSourceLiveData.postValue(dataSource);
        return dataSource;
    }

    public MutableLiveData<MessageDataSource> getMessageDataSourceLiveData() {
        return messageDataSourceLiveData;
    }

    @Nullable
    MessageDataSource getMessageDataSource() {
        return messageDataSource;
    }

    void changeWhere(String where) {
        this.where = where;
    }
}
