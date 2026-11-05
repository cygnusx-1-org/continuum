package ml.docilealligator.infinityforreddit.adapters;

import javax.inject.Provider;
import ml.docilealligator.infinityforreddit.apis.StreamableAPI;
import okhttp3.OkHttpClient;
import ml.docilealligator.infinityforreddit.videoautoplay.ExoCreator;
import ml.docilealligator.infinityforreddit.customtheme.CustomThemeWrapper;
import ml.docilealligator.infinityforreddit.fragments.PostFragmentBase;
import android.widget.ImageView;

/**
 * What the video rows need on top of {@link PostRowHost}. Like it, every value is read when asked
 * for: the data saving flags, the default resolutions and "easier to watch in full screen" change
 * while a feed is open.
 */
interface PostVideoHost extends PostRowHost {
    PostFragmentBase fragment();

    CustomThemeWrapper customThemeWrapper();

    boolean easierToWatchInFullScreen();

    boolean dataSavingMode();

    int dataSavingModeDefaultResolution();

    int nonDataSavingModeDefaultResolution();

    Provider<StreamableAPI> streamableApiProvider();

    OkHttpClient shortClipOkHttpClient();

    ExoCreator exoCreator();

    void applyNoPreviewPlaceholder(ImageView previewImageView);

    void scheduleGifAutoplayUpdate();

    void notifyItemChanged(int position);
}
