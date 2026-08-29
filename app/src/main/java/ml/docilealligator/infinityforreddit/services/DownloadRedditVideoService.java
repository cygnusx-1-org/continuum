package ml.docilealligator.infinityforreddit.services;


import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.UriPermission;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.media.MediaScannerConnection;
import android.net.NetworkRequest;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.PersistableBundle;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationChannelCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.Executor;
import javax.inject.Inject;
import javax.inject.Named;
import ml.docilealligator.infinityforreddit.DownloadProgressResponseBody;
import ml.docilealligator.infinityforreddit.Infinity;
import ml.docilealligator.infinityforreddit.R;
import ml.docilealligator.infinityforreddit.apis.DownloadFile;
import ml.docilealligator.infinityforreddit.broadcastreceivers.DownloadedMediaDeleteActionBroadcastReceiver;
import ml.docilealligator.infinityforreddit.customtheme.CustomThemeWrapper;
import ml.docilealligator.infinityforreddit.events.ShareMediaEvent;
import ml.docilealligator.infinityforreddit.utils.DocumentTreeUtils;
import ml.docilealligator.infinityforreddit.utils.NotificationUtils;
import ml.docilealligator.infinityforreddit.utils.SharedPreferencesUtils;
import ml.docilealligator.infinityforreddit.utils.Utils;
import okhttp3.OkHttpClient;
import okhttp3.ResponseBody;
import org.greenrobot.eventbus.EventBus;
import retrofit2.Response;
import retrofit2.Retrofit;

public class DownloadRedditVideoService extends JobService {

    public static final String EXTRA_VIDEO_URL = "EVU";
    public static final String EXTRA_SUBREDDIT = "ES";
    public static final String EXTRA_POST_ID = "EPI";
    public static final String EXTRA_FILE_NAME = "EFN";
    public static final String EXTRA_IS_NSFW = "EIN";
    // When set, the muxed video is written to the cache and shared instead of being saved to the
    // user's download folder.
    public static final String EXTRA_IS_SHARE = "EIS";

    private static final int NO_ERROR = -1;
    private static final int ERROR_INVALID_VIDEO_URL = 0;
    private static final int ERROR_CANNOT_GET_CACHE_DIRECTORY = 1;
    private static final int ERROR_VIDEO_FILE_CANNOT_DOWNLOAD = 2;
    private static final int ERROR_VIDEO_FILE_CANNOT_SAVE = 3;
    private static final int ERROR_AUDIO_FILE_CANNOT_SAVE = 4;
    private static final int ERROR_MUX_FAILED = 5;
    private static final int ERROR_MUXED_VIDEO_FILE_CANNOT_SAVE = 6;

    private static int JOB_ID = 30000;

    @Inject
    @Named("download_media")
    Retrofit retrofit;
    @Inject
    @Named("default")
    SharedPreferences sharedPreferences;
    @Inject
    CustomThemeWrapper customThemeWrapper;
    @Inject
    Executor executor;
    private NotificationManagerCompat notificationManager;
    private final String[] possibleVideoUrlSuffices = new String[]{"/CMAF_720.mp4", "/CMAF_480.mp4", "/CMAF_360.mp4"};
    private final String[] possibleAudioUrlSuffices = new String[]{"/CMAF_AUDIO_128.mp4", "/CMAF_AUDIO_64.mp4", "/DASH_AUDIO_128.mp4", "/DASH_audio.mp4", "/DASH_audio", "/audio.mp4", "/audio"};

    public DownloadRedditVideoService() {
    }

    public static JobInfo constructJobInfo(Context context, long contentEstimatedBytes, PersistableBundle extras) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return new JobInfo.Builder(JOB_ID++, new ComponentName(context, DownloadRedditVideoService.class))
                    .setUserInitiated(true)
                    .setRequiredNetwork(new NetworkRequest.Builder().clearCapabilities().build())
                    .setEstimatedNetworkBytes(0, contentEstimatedBytes + 500)
                    .setExtras(extras)
                    .build();
        } else {
            return new JobInfo.Builder(JOB_ID++, new ComponentName(context, DownloadRedditVideoService.class))
                    .setOverrideDeadline(0)
                    .setExtras(extras)
                    .build();
        }
    }

    @Override
    public void onCreate() {
        ((Infinity) getApplication()).getAppComponent().inject(this);
        notificationManager = NotificationManagerCompat.from(this);
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(DownloadRedditVideoService.this, NotificationUtils.CHANNEL_ID_DOWNLOAD_REDDIT_VIDEO);

        PersistableBundle intent = params.getExtras();
        if (intent == null) {
            return false;
        }

        String videoUrl = intent.getString(EXTRA_VIDEO_URL);
        String subredditName = intent.getString(EXTRA_SUBREDDIT);
        String postId = intent.getString(EXTRA_POST_ID);
        String finalFileName = intent.getString(EXTRA_FILE_NAME);
        boolean isShare = intent.getInt(EXTRA_IS_SHARE, 0) == 1;

        // Use the passed filename for notifications, fallback if missing
        String notificationTitle = (finalFileName != null && !finalFileName.isEmpty()) ? finalFileName : "reddit_video.mp4";
        // Extract base name for cache files if needed, fallback to postId
        String cacheBaseName = (finalFileName != null && finalFileName.contains(".")) ?
                                finalFileName.substring(0, finalFileName.lastIndexOf('.')) :
                                (subredditName + "-" + postId);


        NotificationChannelCompat serviceChannel =
                new NotificationChannelCompat.Builder(
                        NotificationUtils.CHANNEL_ID_DOWNLOAD_REDDIT_VIDEO,
                        NotificationManagerCompat.IMPORTANCE_LOW)
                        .setName(NotificationUtils.CHANNEL_DOWNLOAD_REDDIT_VIDEO)
                        .build();
        notificationManager.createNotificationChannel(serviceChannel);

        int randomNotificationIdOffset = new Random().nextInt(10000);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            setNotification(params,
                    NotificationUtils.DOWNLOAD_REDDIT_VIDEO_NOTIFICATION_ID + randomNotificationIdOffset,
                    createNotification(builder, notificationTitle), // Use notificationTitle
                    JobService.JOB_END_NOTIFICATION_POLICY_DETACH);
        } else {
            NotificationUtils.notifyIfPermitted(this, notificationManager, NotificationUtils.DOWNLOAD_REDDIT_VIDEO_NOTIFICATION_ID + randomNotificationIdOffset,
                    createNotification(builder, notificationTitle)); // Use notificationTitle
        }

        if (videoUrl == null) {
            downloadFinished(params, builder, null, ERROR_INVALID_VIDEO_URL, randomNotificationIdOffset);
            return true;
        }

        int audioLastSlashIndex = videoUrl.lastIndexOf('/');
        String audioUrlPrefix = Build.VERSION.SDK_INT > Build.VERSION_CODES.N && audioLastSlashIndex >= 0 ? videoUrl.substring(0, audioLastSlashIndex) : null;

        boolean isNsfw = intent.getInt(EXTRA_IS_NSFW, 0) == 1;

        executor.execute(() -> {
            final DownloadProgressResponseBody.ProgressListener progressListener = new DownloadProgressResponseBody.ProgressListener() {
                long time = 0;

                @Override public void update(long bytesRead, long contentLength, boolean done) {
                    if (!done) {
                        if (contentLength != -1) {
                            long currentTime = System.currentTimeMillis();
                            if (currentTime - time > 1000) {
                                time = currentTime;
                                updateNotification(builder, 0, (int) ((100 * bytesRead) / contentLength), randomNotificationIdOffset, null);
                            }
                        }
                    }
                }
            };

            OkHttpClient client = new OkHttpClient.Builder()
                    .addNetworkInterceptor(chain -> {
                        okhttp3.Response originalResponse = chain.proceed(chain.request());
                        return originalResponse.newBuilder()
                                .body(new DownloadProgressResponseBody(originalResponse.body(), progressListener))
                                .build();
                    })
                    .build();

            // Keep this call's Retrofit local. A JobService instance is shared by every job routed
            // to it, so assigning the injected field here let a second concurrent download swap the
            // client out from under the first: both downloads then streamed through whichever
            // client won, and that client carries the other download's progress listener, so one
            // notification counted twice while the other never left 0%.
            Retrofit downloadRetrofit = retrofit.newBuilder().client(client).build();

            DownloadFile downloadFileRetrofit = downloadRetrofit.create(DownloadFile.class);

            boolean separateDownloadFolder = sharedPreferences.getBoolean(SharedPreferencesUtils.SEPARATE_FOLDER_FOR_EACH_SUBREDDIT, false);

            File externalCacheDirectory = Utils.getCacheDir(this);
            if (externalCacheDirectory != null) {
                // Use the filename passed via extras
                String destinationFileName = (finalFileName != null && !finalFileName.isEmpty()) ? finalFileName : (cacheBaseName + ".mp4");
                // Use cacheBaseName for temporary files to avoid conflicts if sanitization differs slightly
                String tempFileBaseName = cacheBaseName;

                // Settle the destination folder and the filename before opening the response.
                // Resolving a SAF directory costs Binder round trips, and doing it with the response
                // already open left the socket idle the whole time against OkHttp's read timeout.
                // Nothing here needs the response.
                //
                // Only the lookups happen now; the directory and the file are still created after
                // the download succeeds, so a failed request leaves nothing behind, exactly as
                // before. A share-only request keeps the muxed file in the cache and needs no
                // destination at all, so it skips this.
                Uri destinationDirUri = null;
                Uri subredditDirParentUri = null;
                String subredditDirName = null;
                if (!isShare) {
                    String destinationFileDirectory;
                    if (isNsfw && sharedPreferences.getBoolean(SharedPreferencesUtils.SAVE_NSFW_MEDIA_IN_DIFFERENT_FOLDER, false)) {
                        destinationFileDirectory = sharedPreferences.getString(SharedPreferencesUtils.NSFW_DOWNLOAD_LOCATION, "");
                    } else {
                        destinationFileDirectory = sharedPreferences.getString(SharedPreferencesUtils.VIDEO_DOWNLOAD_LOCATION, "");
                    }

                    // Try to write into the user-chosen folder. This can fail if the persisted URI
                    // permission was lost (revoked, or granted to a different app build). We check the
                    // grant up front so we skip straight to the fallback instead of failing the download.
                    if (destinationFileDirectory != null && !destinationFileDirectory.isEmpty()
                            && hasPersistedWritePermission(Uri.parse(destinationFileDirectory))) {
                        try {
                            Uri treeDirUri = DocumentTreeUtils.treeRootDocumentUri(Uri.parse(destinationFileDirectory));
                            if (separateDownloadFolder && subredditName != null && !subredditName.isEmpty()) {
                                destinationDirUri = DocumentTreeUtils.findChildDocumentUri(this, treeDirUri, subredditName);
                                if (destinationDirUri == null) {
                                    // The per-subreddit folder does not exist yet, so there is
                                    // nothing to deduplicate against. Create it once the download
                                    // succeeds.
                                    subredditDirParentUri = treeDirUri;
                                    subredditDirName = subredditName;
                                }
                            } else {
                                destinationDirUri = treeDirUri;
                            }

                            if (destinationDirUri != null) {
                                // Filenames already carry the post id, so the only way to collide is
                                // downloading the same video twice, and the provider produces
                                // exactly the " (n)" form this app uses — but only when the MIME
                                // type matches the filename's extension. The muxed output is always
                                // mp4, so the type stays "video/mp4" rather than following the name;
                                // when the name does not agree with that, deduplicate here instead.
                                if (!"video/mp4".equals(DocumentTreeUtils.mimeTypeMatchingExtension(destinationFileName))
                                        || !DocumentTreeUtils.providerDeduplicatesOnCreate(destinationDirUri)) {
                                    destinationFileName = DocumentTreeUtils.deduplicateFileName(destinationFileName,
                                            DocumentTreeUtils.listChildDisplayNamesLowercase(this, destinationDirUri));
                                }
                            }
                        } catch (SecurityException | IllegalArgumentException e) {
                            Log.e("DownloadRedditVideo", "Lost permission for chosen download folder: " + e.getMessage());
                            destinationDirUri = null;
                            subredditDirParentUri = null;
                            subredditDirName = null;
                        }
                    }
                }

                try {
                    ResponseBody videoResponse = getVideoResponse(downloadFileRetrofit, videoUrl, -1);
                    if (videoResponse != null) {
                        String externalCacheDirectoryPath = externalCacheDirectory.getAbsolutePath() + "/";

                        // For share-only there is no destination folder; the muxed file stays in the
                        // cache and is handed to the activity via ShareMediaEvent.
                        String destinationFileUriString = null;
                        boolean isDefaultDestination = false;

                        if (!isShare) {
                            if (destinationDirUri == null && subredditDirParentUri != null && subredditDirName != null) {
                                destinationDirUri = DocumentTreeUtils.createDirectory(this, subredditDirParentUri, subredditDirName);
                            }

                            Uri picFileUri = destinationDirUri == null ? null
                                    : DocumentTreeUtils.createDocument(this, destinationDirUri, "video/mp4", destinationFileName);

                            if (picFileUri != null) {
                                isDefaultDestination = false;
                                destinationFileUriString = picFileUri.toString();
                            } else {
                                // The chosen folder is unusable. Save to the default media location so the
                                // download still succeeds, and prompt the user to re-select their folder.
                                Log.w("DownloadRedditVideo", "Falling back to the default download location.");
                                showReselectDownloadFolderToast();
                                isDefaultDestination = true;
                                destinationFileUriString = getDefaultDownloadPath(
                                        separateDownloadFolder ? subredditName : null, destinationFileName);
                            }
                        }

                        updateNotification(builder, R.string.downloading_reddit_video_audio_track, 0,
                                randomNotificationIdOffset, null);

                        // Use tempFileBaseName for cache file path
                        String videoFilePath = externalCacheDirectoryPath + tempFileBaseName + "-cache.mp4";
                        String savedVideoFilePath = writeResponseBodyToDisk(videoResponse, videoFilePath);

                        if (savedVideoFilePath == null) {
                            downloadFinished(params, builder, null, ERROR_VIDEO_FILE_CANNOT_SAVE, randomNotificationIdOffset);
                            return;
                        }

                        if (audioUrlPrefix != null) {
                            ResponseBody audioResponse = getAudioResponse(downloadFileRetrofit, audioUrlPrefix, 0);
                            // Use tempFileBaseName for cache file path
                            String outputFilePath = externalCacheDirectoryPath + tempFileBaseName + ".mp4";

                            if (audioResponse != null) {
                                // Use tempFileBaseName for cache file path
                                String audioFilePath = externalCacheDirectoryPath + tempFileBaseName + "-cache.mp3";

                                String savedAudioFilePath = writeResponseBodyToDisk(audioResponse, audioFilePath);

                                if (savedAudioFilePath == null) {
                                    downloadFinished(params, builder, null, ERROR_AUDIO_FILE_CANNOT_SAVE, randomNotificationIdOffset);
                                    return;
                                }

                                updateNotification(builder, R.string.downloading_reddit_video_muxing, -1, randomNotificationIdOffset, null);

                                if (!muxVideoAndAudio(videoFilePath, audioFilePath, outputFilePath)) {
                                    downloadFinished(params, builder, null, ERROR_MUX_FAILED, randomNotificationIdOffset);

                                    return;
                                }

                                updateNotification(builder, R.string.downloading_reddit_video_save_file_to_public_dir, -1, randomNotificationIdOffset, null);

                                try {
                                    if (isShare) {
                                        new File(videoFilePath).delete();
                                        new File(audioFilePath).delete();
                                        shareCachedVideo(params, builder, randomNotificationIdOffset, outputFilePath, destinationFileName);
                                    } else {
                                        Uri destinationFileUri = copyToDestination(outputFilePath, Objects.requireNonNull(destinationFileUriString), destinationFileName, isDefaultDestination);

                                        new File(videoFilePath).delete();
                                        new File(audioFilePath).delete();
                                        new File(outputFilePath).delete();

                                        downloadFinished(params, builder, destinationFileUri, NO_ERROR, randomNotificationIdOffset);
                                    }
                                } catch (IOException e) {
                                    e.printStackTrace();
                                    downloadFinished(params, builder, null, ERROR_MUXED_VIDEO_FILE_CANNOT_SAVE, randomNotificationIdOffset);
                                }
                            } else {
                                updateNotification(builder, R.string.downloading_reddit_video_muxing, -1, randomNotificationIdOffset, null);

                                if (!muxVideoAndAudio(videoFilePath, null, outputFilePath)) {
                                    downloadFinished(params, builder, null, ERROR_MUX_FAILED, randomNotificationIdOffset);

                                    return;
                                }

                                updateNotification(builder, R.string.downloading_reddit_video_save_file_to_public_dir, -1, randomNotificationIdOffset, null);

                                try {
                                    if (isShare) {
                                        new File(videoFilePath).delete();
                                        shareCachedVideo(params, builder, randomNotificationIdOffset, outputFilePath, destinationFileName);
                                    } else {
                                        Uri destinationFileUri = copyToDestination(outputFilePath, Objects.requireNonNull(destinationFileUriString), destinationFileName, isDefaultDestination);

                                        new File(videoFilePath).delete();
                                        new File(outputFilePath).delete();

                                        downloadFinished(params, builder, destinationFileUri, NO_ERROR, randomNotificationIdOffset);
                                    }
                                } catch (IOException e) {
                                    e.printStackTrace();
                                    downloadFinished(params, builder, null, ERROR_MUXED_VIDEO_FILE_CANNOT_SAVE, randomNotificationIdOffset);
                                }
                            }
                        } else {
                            // do not remux video on <= Android N, just save video
                            updateNotification(builder, R.string.downloading_reddit_video_save_file_to_public_dir, -1, randomNotificationIdOffset, null);

                            try {
                                if (isShare) {
                                    shareCachedVideo(params, builder, randomNotificationIdOffset, videoFilePath, destinationFileName);
                                } else {
                                    Uri destinationFileUri = copyToDestination(videoFilePath, Objects.requireNonNull(destinationFileUriString), destinationFileName, isDefaultDestination);
                                    new File(videoFilePath).delete();
                                    downloadFinished(params, builder, destinationFileUri, NO_ERROR, randomNotificationIdOffset);
                                }
                            } catch (IOException e) {
                                e.printStackTrace();
                                downloadFinished(params, builder, null, ERROR_MUXED_VIDEO_FILE_CANNOT_SAVE, randomNotificationIdOffset);
                            }
                        }
                    } else {
                        downloadFinished(params, builder, null, ERROR_VIDEO_FILE_CANNOT_DOWNLOAD, randomNotificationIdOffset);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    downloadFinished(params, builder, null, ERROR_VIDEO_FILE_CANNOT_DOWNLOAD, randomNotificationIdOffset);
                }
            } else {
                downloadFinished(params, builder, null, ERROR_CANNOT_GET_CACHE_DIRECTORY, randomNotificationIdOffset);
            }
        });

        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return false;
    }

    /**
     * Moves the prepared video into a cache folder and posts a {@link ShareMediaEvent} so the
     * foreground activity can launch the share sheet. Used for "share only" (no save to gallery).
     */
    private void shareCachedVideo(JobParameters params, NotificationCompat.Builder builder,
                                  int randomNotificationIdOffset, String sourceFilePath, String fileName) {
        File cacheDir = Utils.getCacheDir(this);
        if (cacheDir == null) {
            downloadFinished(params, builder, null, ERROR_CANNOT_GET_CACHE_DIRECTORY, randomNotificationIdOffset);
            return;
        }

        File shareDir = new File(cacheDir, "shared_media");
        if (!shareDir.exists()) {
            shareDir.mkdirs();
        }

        File source = new File(sourceFilePath);
        File outFile = new File(shareDir, fileName);
        if (outFile.exists()) {
            outFile.delete();
        }

        if (!source.renameTo(outFile)) {
            // Different filesystem or rename unsupported: fall back to a copy.
            try (InputStream in = new FileInputStream(source); OutputStream out = new FileOutputStream(outFile)) {
                byte[] buf = new byte[4096];
                int len;
                while ((len = in.read(buf)) > 0) {
                    out.write(buf, 0, len);
                }
            } catch (IOException e) {
                e.printStackTrace();
                downloadFinished(params, builder, null, ERROR_MUXED_VIDEO_FILE_CANNOT_SAVE, randomNotificationIdOffset);
                return;
            }
            source.delete();
        }

        EventBus.getDefault().post(new ShareMediaEvent(outFile.getAbsolutePath(), "video/*"));
        notificationManager.cancel(NotificationUtils.DOWNLOAD_REDDIT_VIDEO_NOTIFICATION_ID + randomNotificationIdOffset);
        jobFinished(params, false);
    }

    @Nullable
    private ResponseBody getVideoResponse(DownloadFile downloadFileRetrofit, @NonNull String videoUrl, int videoSuffixIndex) throws IOException {
        if (videoSuffixIndex >= possibleVideoUrlSuffices.length) {
            return null;
        }

        if (videoSuffixIndex >= 0) {
            int videoLastSlashIndex = videoUrl.lastIndexOf('/');
            String videoUrlPrefix = videoLastSlashIndex >= 0 ? videoUrl.substring(0, videoLastSlashIndex) : null;
            if (videoUrlPrefix == null) {
                return null;
            }

            videoUrl = videoUrlPrefix + possibleVideoUrlSuffices[videoSuffixIndex];
        }

        Response<ResponseBody> videoResponse = downloadFileRetrofit.downloadFile(videoUrl).execute();
        ResponseBody responseBody = videoResponse.body();
        if (videoResponse.isSuccessful() && responseBody != null) {
            return responseBody;
        }

        return getVideoResponse(downloadFileRetrofit, videoUrl, videoSuffixIndex < 0 ? 0 : videoSuffixIndex + 1);
    }

    @Nullable
    private ResponseBody getAudioResponse(DownloadFile downloadFileRetrofit, @NonNull String audioUrlPrefix, int audioSuffixIndex) throws IOException {
        if (audioSuffixIndex >= possibleAudioUrlSuffices.length) {
            return null;
        }

        String audioUrl = audioUrlPrefix + possibleAudioUrlSuffices[audioSuffixIndex];
        Response<ResponseBody> audioResponse = downloadFileRetrofit.downloadFile(audioUrl).execute();
        ResponseBody responseBody = audioResponse.body();

        if (audioResponse.isSuccessful() && responseBody != null) {
            return responseBody;
        }

        return getAudioResponse(downloadFileRetrofit, audioUrlPrefix, audioSuffixIndex + 1);
    }

    @Nullable
    private String writeResponseBodyToDisk(ResponseBody body, String filePath) {
        try {
            File file = new File(filePath);

            InputStream inputStream = null;
            OutputStream outputStream = null;

            try {
                byte[] fileReader = new byte[4096];


                inputStream = body.byteStream();
                outputStream = new FileOutputStream(file);

                while (true) {
                    int read = inputStream.read(fileReader);

                    if (read == -1) {
                        break;
                    }

                    outputStream.write(fileReader, 0, read);

                }

                outputStream.flush();

                return file.getPath();
            } catch (IOException e) {
                return null;
            } finally {
                if (inputStream != null) {
                    inputStream.close();
                }

                if (outputStream != null) {
                    outputStream.close();
                }
            }
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Remuxes the downloaded video and audio tracks into one file.
     *
     * <p>{@code @SuppressLint("WrongConstant")}: the buffer flags are copied straight from
     * MediaExtractor to MediaMuxer, which is the documented remux idiom, but
     * MediaExtractor.SAMPLE_FLAG_* and MediaCodec.BUFFER_FLAG_* are separate @IntDefs so lint
     * objects. They agree on the values a remux cares about (SAMPLE_FLAG_SYNC ==
     * BUFFER_FLAG_KEY_FRAME == 1); remapping them would change muxing behaviour with no test
     * corpus to show the new mapping is better.
     */
    @SuppressLint("WrongConstant")
    private boolean muxVideoAndAudio(String videoFilePath, @Nullable String audioFilePath, String outputFilePath) {
        try {
            File file = new File(outputFilePath);
            file.createNewFile();
            MediaExtractor videoExtractor = new MediaExtractor();
            videoExtractor.setDataSource(videoFilePath);
            MediaMuxer muxer = new MediaMuxer(outputFilePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);

            videoExtractor.selectTrack(0);
            MediaFormat videoFormat = videoExtractor.getTrackFormat(0);
            int videoTrack = muxer.addTrack(videoFormat);

            boolean sawEOS = false;
            int offset = 100;
            int sampleSize = 4096 * 1024;
            ByteBuffer videoBuf = ByteBuffer.allocate(sampleSize);
            ByteBuffer audioBuf = ByteBuffer.allocate(sampleSize);
            MediaCodec.BufferInfo videoBufferInfo = new MediaCodec.BufferInfo();

            videoExtractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC);

            // audio not present for all videos
            MediaExtractor audioExtractor = new MediaExtractor();
            MediaCodec.BufferInfo audioBufferInfo = new MediaCodec.BufferInfo();
            int audioTrack = -1;

            if (audioFilePath != null) {
                audioExtractor.setDataSource(audioFilePath);
                audioExtractor.selectTrack(0);
                MediaFormat audioFormat = audioExtractor.getTrackFormat(0);
                audioExtractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
                audioTrack = muxer.addTrack(audioFormat);
            }

            muxer.start();

            while (!sawEOS) {
                videoBufferInfo.offset = offset;
                videoBufferInfo.size = videoExtractor.readSampleData(videoBuf, offset);

                if (videoBufferInfo.size < 0) {
                    sawEOS = true;
                    videoBufferInfo.size = 0;
                } else {
                    videoBufferInfo.presentationTimeUs = videoExtractor.getSampleTime();
                    videoBufferInfo.flags = videoExtractor.getSampleFlags();
                    muxer.writeSampleData(videoTrack, videoBuf, videoBufferInfo);
                    videoExtractor.advance();
                }
            }

            if (audioFilePath != null) {
                boolean sawEOS2 = false;

                while (!sawEOS2) {
                    audioBufferInfo.offset = offset;
                    audioBufferInfo.size = audioExtractor.readSampleData(audioBuf, offset);

                    if (audioBufferInfo.size < 0) {
                        sawEOS2 = true;
                        audioBufferInfo.size = 0;
                    } else {
                        audioBufferInfo.presentationTimeUs = audioExtractor.getSampleTime();
                        audioBufferInfo.flags = audioExtractor.getSampleFlags();
                        muxer.writeSampleData(audioTrack, audioBuf, audioBufferInfo);
                        audioExtractor.advance();
                    }
                }
            }

            muxer.stop();
            muxer.release();
        } catch (IllegalArgumentException | IllegalStateException e) {
            Log.e("DownloadRedditVideoService", "muxVideoAndAudio failed", e);
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }

        return true;
    }

    private Uri copyToDestination(String srcPath, String destinationFileUriString, String destinationFileName, boolean isDefaultDestination) throws IOException {
        ContentResolver contentResolver = getContentResolver();
        if (isDefaultDestination) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                InputStream in = new FileInputStream(srcPath);
                OutputStream out = new FileOutputStream(destinationFileUriString);
                byte[] buf = new byte[1024];
                int len;

                while ((len = in.read(buf)) > 0) {
                    out.write(buf, 0, len);
                }

                new File(srcPath).delete();
            } else {
                ContentValues contentValues = new ContentValues();
                contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, destinationFileName);
                contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4");
                contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, destinationFileUriString);
                contentValues.put(MediaStore.Video.Media.IS_PENDING, 1);

                OutputStream stream = null;
                Uri uri = null;

                try {
                    final Uri contentUri = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
                    uri = contentResolver.insert(contentUri, contentValues);

                    if (uri == null) {
                        throw new IOException("Failed to create new MediaStore record.");
                    }

                    stream = contentResolver.openOutputStream(uri);

                    if (stream == null) {
                        throw new IOException("Failed to get output stream.");
                    }

                    InputStream in = new FileInputStream(srcPath);

                    byte[] buf = new byte[1024];
                    int len;

                    while ((len = in.read(buf)) > 0) {
                        stream.write(buf, 0, len);
                    }

                    contentValues.clear();
                    contentValues.put(MediaStore.Video.Media.IS_PENDING, 0);
                    contentResolver.update(uri, contentValues, null, null);
                    return uri;
                } catch (IOException e) {
                    if (uri != null) {
                        // Don't leave an orphan entry in the MediaStore
                        contentResolver.delete(uri, null, null);
                    }

                    throw e;
                } finally {
                    if (stream != null) {
                        stream.close();
                    }
                }
            }
        } else {
            OutputStream stream = contentResolver.openOutputStream(Uri.parse(destinationFileUriString));
            if (stream == null) {
                throw new IOException("Failed to get output stream.");
            }

            InputStream in = new FileInputStream(srcPath);

            byte[] buf = new byte[1024];
            int len;

            while ((len = in.read(buf)) > 0) {
                stream.write(buf, 0, len);
            }
        }

        return Uri.parse(destinationFileUriString);
    }

    /**
     * Returns true if the app still holds a persisted write grant for the given tree URI. SAF
     * permissions can be lost (revoked by the user, or originally granted to a different app build),
     * so checking before use lets us fall back cleanly instead of hitting a SecurityException.
     */
    private boolean hasPersistedWritePermission(Uri treeUri) {
        try {
            for (UriPermission permission : getContentResolver().getPersistedUriPermissions()) {
                if (permission.isWritePermission() && permission.getUri().equals(treeUri)) {
                    return true;
                }
            }
        } catch (Exception e) {
            Log.e("DownloadRedditVideo", "Failed to read persisted URI permissions: " + e.getMessage());
        }
        return false;
    }

    private void showReselectDownloadFolderToast() {
        new Handler(Looper.getMainLooper()).post(() ->
                Toast.makeText(getApplicationContext(),
                        R.string.download_folder_permission_lost, Toast.LENGTH_LONG).show());
    }

    /**
     * Returns a destination for the default video location, used when the user's chosen folder is
     * unavailable. On Android Q+ this is a MediaStore {@code RELATIVE_PATH}; on older versions it is
     * an absolute file path (with parent directories created and name collisions resolved).
     */
    private String getDefaultDownloadPath(@Nullable String subredditName, String fileName) {
        String topDir = Environment.DIRECTORY_MOVIES;
        String subFolder = (subredditName != null && !subredditName.isEmpty()) ? subredditName : null;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // MediaStore resolves name collisions on its own for RELATIVE_PATH destinations.
            return subFolder == null ? topDir : topDir + File.separator + subFolder;
        }

        File baseDir = Environment.getExternalStoragePublicDirectory(topDir);
        if (subFolder != null) {
            baseDir = new File(baseDir, subFolder);
        }
        if (!baseDir.exists()) {
            baseDir.mkdirs();
        }

        File outFile = new File(baseDir, fileName);
        int dotIndex = fileName.lastIndexOf('.');
        String baseName = (dotIndex == -1) ? fileName : fileName.substring(0, dotIndex);
        String extension = (dotIndex == -1) ? "" : fileName.substring(dotIndex);
        int num = 1;
        while (outFile.exists()) {
            outFile = new File(baseDir, baseName + " (" + num + ")" + extension);
            num++;
        }
        return outFile.getAbsolutePath();
    }

    private void downloadFinished(JobParameters parameters, NotificationCompat.Builder builder, @Nullable Uri destinationFileUri, int errorCode, int randomNotificationIdOffset) {
        if (errorCode != NO_ERROR) {
            switch (errorCode) {
                case ERROR_INVALID_VIDEO_URL:
                    updateNotification(builder, R.string.downloading_reddit_video_failed_invalid_video_url, -1,
                            randomNotificationIdOffset, null);
                    break;
                case ERROR_CANNOT_GET_CACHE_DIRECTORY:
                    updateNotification(builder, R.string.downloading_reddit_video_failed_cannot_get_cache_directory, -1,
                            randomNotificationIdOffset, null);
                    break;
                case ERROR_VIDEO_FILE_CANNOT_DOWNLOAD:
                    updateNotification(builder, R.string.downloading_reddit_video_failed_cannot_download_video, -1,
                            randomNotificationIdOffset, null);
                    break;
                case ERROR_VIDEO_FILE_CANNOT_SAVE:
                    updateNotification(builder, R.string.downloading_reddit_video_failed_cannot_save_video, -1,
                            randomNotificationIdOffset, null);
                    break;
                case ERROR_AUDIO_FILE_CANNOT_SAVE:
                    updateNotification(builder, R.string.downloading_reddit_video_failed_cannot_save_audio, -1,
                            randomNotificationIdOffset, null);
                    break;
                case ERROR_MUX_FAILED:
                    updateNotification(builder, R.string.downloading_reddit_video_failed_cannot_mux, -1,
                            randomNotificationIdOffset, null);
                    break;
                case ERROR_MUXED_VIDEO_FILE_CANNOT_SAVE:
                    updateNotification(builder, R.string.downloading_reddit_video_failed_cannot_save_mux_video, -1,
                            randomNotificationIdOffset, null);
                    break;
            }
        } else {
            Uri finishedUri = Objects.requireNonNull(destinationFileUri);
            MediaScannerConnection.scanFile(
                    this, new String[]{finishedUri.toString()}, null,
                    (path, uri) -> {
                        updateNotification(builder, R.string.downloading_reddit_video_finished, -1, randomNotificationIdOffset, finishedUri);
                    }
            );
        }

        jobFinished(parameters, false);
    }

    private Notification createNotification(NotificationCompat.Builder builder, String fileName) {
        builder.setContentTitle(fileName).setContentText(getString(R.string.downloading_reddit_video)).setProgress(100, 0, false);
        return builder.setSmallIcon(R.drawable.ic_notification).setColor(customThemeWrapper.getColorPrimaryLightTheme()).build();
    }

    private void updateNotification(NotificationCompat.Builder builder, int contentStringResId, int progress, int randomNotificationIdOffset, @Nullable Uri mediaUri) {
        if (notificationManager != null) {
            if (progress < 0) {
                builder.setProgress(0, 0, false);
            } else {
                builder.setProgress(100, progress, false);
            }

            if (contentStringResId != 0) {
                builder.setContentText(getString(contentStringResId));
                builder.setStyle(new NotificationCompat.BigTextStyle().bigText(getString(contentStringResId)));
            }

            if (mediaUri != null) {
                int pendingIntentFlags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE : PendingIntent.FLAG_CANCEL_CURRENT;

                Intent intent = new Intent();
                intent.setAction(android.content.Intent.ACTION_VIEW);
                intent.setDataAndType(mediaUri, "video/mp4");
                intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, pendingIntentFlags);

                builder.setContentIntent(pendingIntent);

                Intent shareIntent = new Intent();
                shareIntent.setAction(Intent.ACTION_SEND);
                shareIntent.putExtra(Intent.EXTRA_STREAM, mediaUri);
                shareIntent.setType("video/mp4");
                shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                Intent intentAction = Intent.createChooser(shareIntent, getString(R.string.share));
                PendingIntent shareActionPendingIntent = PendingIntent.getActivity(this, 1, intentAction, pendingIntentFlags);
                builder.addAction(new NotificationCompat.Action(R.drawable.ic_notification, getString(R.string.share), shareActionPendingIntent));

                Intent deleteIntent = new Intent(this, DownloadedMediaDeleteActionBroadcastReceiver.class);
                deleteIntent.setData(mediaUri);
                deleteIntent.putExtra(DownloadedMediaDeleteActionBroadcastReceiver.EXTRA_NOTIFICATION_ID, NotificationUtils.DOWNLOAD_REDDIT_VIDEO_NOTIFICATION_ID + randomNotificationIdOffset);
                PendingIntent deleteActionPendingIntent = PendingIntent.getBroadcast(this, 2, deleteIntent, pendingIntentFlags);
                builder.addAction(new NotificationCompat.Action(R.drawable.ic_notification, getString(R.string.delete), deleteActionPendingIntent));
            } else {
                builder.setContentIntent(null);
                builder.clearActions();
            }
            NotificationUtils.notifyIfPermitted(this, notificationManager, NotificationUtils.DOWNLOAD_REDDIT_VIDEO_NOTIFICATION_ID + randomNotificationIdOffset, builder.build());
        }
    }
}