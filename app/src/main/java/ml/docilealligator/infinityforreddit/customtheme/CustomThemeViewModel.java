package ml.docilealligator.infinityforreddit.customtheme;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelKt;
import androidx.lifecycle.ViewModelProvider;
import androidx.paging.PagingData;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;
import ml.docilealligator.infinityforreddit.RedditDataRoomDatabase;
import retrofit2.Retrofit;

public class CustomThemeViewModel extends ViewModel {
    @Nullable
    private LocalCustomThemeRepository localCustomThemeRepository;
    @Nullable
    private OnlineCustomThemeRepository onlineCustomThemeRepository;

    public CustomThemeViewModel(RedditDataRoomDatabase redditDataRoomDatabase) {
        localCustomThemeRepository = new LocalCustomThemeRepository(redditDataRoomDatabase);
    }

    public CustomThemeViewModel(Executor executor, Retrofit retrofit,
                                RedditDataRoomDatabase redditDataRoomDatabase) {
        onlineCustomThemeRepository = new OnlineCustomThemeRepository(executor, retrofit, redditDataRoomDatabase,
                ViewModelKt.getViewModelScope(this));
    }

    @Nullable
    public LiveData<List<CustomTheme>> getAllCustomThemes() {
        return Objects.requireNonNull(localCustomThemeRepository).getAllCustomThemes();
    }

    public LiveData<CustomTheme> getCurrentLightThemeLiveData() {
        return Objects.requireNonNull(localCustomThemeRepository).getCurrentLightCustomTheme();
    }

    public LiveData<CustomTheme> getCurrentDarkThemeLiveData() {
        return Objects.requireNonNull(localCustomThemeRepository).getCurrentDarkCustomTheme();
    }

    @Nullable
    public LiveData<CustomTheme> getCurrentAmoledThemeLiveData() {
        return Objects.requireNonNull(localCustomThemeRepository).getCurrentAmoledCustomTheme();
    }

    public LiveData<PagingData<OnlineCustomThemeMetadata>> getOnlineCustomThemeMetadata() {
        return Objects.requireNonNull(onlineCustomThemeRepository).getOnlineCustomThemeMetadata();
    }

    public static class Factory extends ViewModelProvider.NewInstanceFactory {
        @Nullable
        private Executor executor;
        @Nullable
        private Retrofit retrofit;
        private final RedditDataRoomDatabase mRedditDataRoomDatabase;
        private final boolean isOnline;

        public Factory(RedditDataRoomDatabase redditDataRoomDatabase) {
            mRedditDataRoomDatabase = redditDataRoomDatabase;
            isOnline = false;
        }

        public Factory(Executor executor, Retrofit retrofit, RedditDataRoomDatabase redditDataRoomDatabase) {
            this.executor = executor;
            this.retrofit = retrofit;
            mRedditDataRoomDatabase = redditDataRoomDatabase;
            isOnline = true;
        }

        @NonNull
        @Override
        public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
            if (isOnline) {
                return (T) new CustomThemeViewModel(Objects.requireNonNull(executor), Objects.requireNonNull(retrofit), mRedditDataRoomDatabase);
            } else {
                return (T) new CustomThemeViewModel(mRedditDataRoomDatabase);
            }
        }
    }
}
