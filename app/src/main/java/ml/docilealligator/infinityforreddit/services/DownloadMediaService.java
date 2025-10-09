package ml.docilealligator.infinityforreddit.services;


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
import android.media.MediaScannerConnection;
import android.net.NetworkRequest;
import android.net.Uri;
import android.os.Build;
import android.os.PersistableBundle;
import android.provider.MediaStore;
import android.util.Log;

import androidx.core.app.NotificationChannelCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.documentfile.provider.DocumentFile;

import org.apache.commons.io.FilenameUtils;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Executor;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;

import ml.docilealligator.infinityforreddit.DownloadProgressResponseBody;
import ml.docilealligator.infinityforreddit.Infinity;
import ml.docilealligator.infinityforreddit.R;
import ml.docilealligator.infinityforreddit.VideoLinkFetcher;
import ml.docilealligator.infinityforreddit.activities.ViewVideoActivity;
import ml.docilealligator.infinityforreddit.apis.DownloadFile;
import ml.docilealligator.infinityforreddit.apis.StreamableAPI;
import ml.docilealligator.infinityforreddit.broadcastreceivers.DownloadedMediaDeleteActionBroadcastReceiver;
import ml.docilealligator.infinityforreddit.customtheme.CustomThemeWrapper;
import ml.docilealligator.infinityforreddit.post.ImgurMedia;
import ml.docilealligator.infinityforreddit.post.Post;
import ml.docilealligator.infinityforreddit.utils.APIUtils;
import ml.docilealligator.infinityforreddit.utils.NotificationUtils;
import ml.docilealligator.infinityforreddit.utils.SharedPreferencesUtils;
import okhttp3.OkHttpClient;
import okhttp3.ResponseBody;
import retrofit2.Response;
import retrofit2.Retrofit;

public class DownloadMediaService extends JobService {
    public static final String EXTRA_URL = "EU";
    public static final String EXTRA_FILE_NAME = "EFN";
    public static final String EXTRA_SUBREDDIT_NAME = "ESN";
    public static final String EXTRA_TITLE = "ET";
    public static final String EXTRA_MEDIA_TYPE = "EIG";
    public static final String EXTRA_IS_NSFW = "EIN";
    public static final String EXTRA_REDGIFS_ID = "EGI";
    public static final String EXTRA_STREAMABLE_SHORT_CODE = "ESSC";
    public static final String EXTRA_IS_ALL_GALLERY_MEDIA = "EIAGM";
    public static final int EXTRA_MEDIA_TYPE_IMAGE = 0;
    public static final int EXTRA_MEDIA_TYPE_GIF = 1;
    public static final int EXTRA_MEDIA_TYPE_VIDEO = 2;

    public static final String EXTRA_ALL_GALLERY_IMAGE_URLS = "EAGIU";
    public static final String EXTRA_ALL_GALLERY_IMAGE_MEDIA_TYPES = "EAGIMT";
    public static final String EXTRA_ALL_GALLERY_IMAGE_FILE_NAMES = "EAGIFN";

    private static final int NO_ERROR = -1;
    private static final int ERROR_CANNOT_GET_DESTINATION_DIRECTORY = 0;
    private static final int ERROR_FILE_CANNOT_DOWNLOAD = 1;
    private static final int ERROR_FILE_CANNOT_SAVE = 2;
    private static final int ERROR_FILE_CANNOT_FETCH_REDGIFS_VIDEO_LINK = 3;
    private static final int ERROR_CANNOT_FETCH_STREAMABLE_VIDEO_LINK = 4;
    private static final int ERROR_INVALID_ARGUMENT = 5;

    private static int JOB_ID = 20000;

    @Inject
    @Named("download_media")
    Retrofit retrofit;
    @Inject
    @Named("redgifs")
    Retrofit mRedgifsRetrofit;
    @Inject
    Provider<StreamableAPI> mStreamableApiProvider;
    @Inject
    @Named("default")
    SharedPreferences mSharedPreferences;
    @Inject
    @Named("current_account")
    SharedPreferences mCurrentAccountSharedPreferences;
    @Inject
    CustomThemeWrapper mCustomThemeWrapper;
    @Inject
    Executor mExecutor;
    private NotificationManagerCompat notificationManager;

    private static String sanitizeFilename(String inputName) {
        if (inputName == null || inputName.isEmpty()) {
            return "reddit_media"; // Default name if title is missing
        }

        // Remove characters that are invalid in filenames on most systems
        String sanitized = inputName.replaceAll("[\\\\/:*?\"<>|]", "_");
        // Replace multiple spaces/underscores with a single underscore
        sanitized = sanitized.replaceAll("[\\s_]+", "_");
        // Trim leading/trailing underscores
        sanitized = sanitized.replaceAll("^_+|_+$", "");
        // Limit length to avoid issues with max path length
        int maxLength = 100; // Adjust max length as needed

        if (sanitized.length() > maxLength) {
            sanitized = sanitized.substring(0, maxLength);
            // Ensure we don't cut in the middle of a multi-byte character if needed,
            // but for simplicity, basic substring is often sufficient.
            // Re-trim in case the cut resulted in a trailing underscore
            sanitized = sanitized.replaceAll("_+$", "");
        }

        // Handle case where sanitization results in an empty string
        if (sanitized.isEmpty()) {
            return "reddit_media_" + System.currentTimeMillis();
        }
        return sanitized;
    }

    // Helper function to get file extension (overload for Post)
    private static String getExtension(String url, int mediaType, String defaultFileName) {
        return getExtensionInternal(url, mediaType, defaultFileName);
    }

    // Helper function to get file extension (overload for ImgurMedia)
    private static String getExtension(ImgurMedia imgurMedia) {
        // ImgurMedia already has a reasonable filename with extension
        String fileName = imgurMedia.getFileName();
        String extension = FilenameUtils.getExtension(fileName);
        if (extension != null && !extension.isEmpty()) {
            // Limit extension length
            return "." + extension.toLowerCase().substring(0, Math.min(extension.length(), 5));
        }
        // Fallback based on type if filename lacks extension
        return getExtensionInternal(imgurMedia.getLink(),
                imgurMedia.getType() == ImgurMedia.TYPE_VIDEO ? EXTRA_MEDIA_TYPE_VIDEO : EXTRA_MEDIA_TYPE_IMAGE,
                null);
    }

    // Internal helper for extension logic
    private static String getExtensionInternal(String url, int mediaType, String defaultFileName) {
        String extension = FilenameUtils.getExtension(url);
        if (extension != null && !extension.isEmpty()) {
            // Basic validation for common image/video extensions
            if (extension.matches("(?i)(jpg|jpeg|png|gif|mp4|webm|mov|avi)")) {
                 // Limit extension length to prevent abuse
                return "." + extension.toLowerCase().substring(0, Math.min(extension.length(), 5));
            }
        }
        // Fallback based on media type or default filename
        switch (mediaType) {
            case EXTRA_MEDIA_TYPE_IMAGE:
                return ".jpg";
            case EXTRA_MEDIA_TYPE_GIF:
                return ".gif";
            case EXTRA_MEDIA_TYPE_VIDEO:
                return ".mp4";
            default:
                // Try extracting from defaultFileName if provided
                if (defaultFileName != null && defaultFileName.contains(".")) {
                    String defaultExt = FilenameUtils.getExtension(defaultFileName);
                    if (defaultExt != null && !defaultExt.isEmpty()) {
                        return "." + defaultExt.toLowerCase().substring(0, Math.min(defaultExt.length(), 5));
                    }
                }
                return ".unknown"; // Default if no extension found
        }
    }


    public DownloadMediaService() {
    }

    /**
     *
     * @param context
     * @param contentEstimatedBytes
     * @param post
     * @param galleryIndex if post is not a gallery post, then galleryIndex should be 0
     * @return JobInfo for DownloadMediaService
     */
    public static JobInfo constructJobInfo(Context context, long contentEstimatedBytes, Post post, int galleryIndex) {
        PersistableBundle extras = new PersistableBundle();
        String sanitizedTitle = sanitizeFilename(post.getTitle());
        String url = "";
        String extension = "";
        int currentMediaType = -1;

        if (post.getPostType() == Post.IMAGE_TYPE) {
            url = post.getUrl();
            currentMediaType = EXTRA_MEDIA_TYPE_IMAGE;
            extras.putString(EXTRA_URL, url);
            extras.putInt(EXTRA_MEDIA_TYPE, currentMediaType);
            extras.putString(EXTRA_SUBREDDIT_NAME, post.getSubredditName());
            extras.putInt(EXTRA_IS_NSFW, post.isNSFW() ? 1 : 0);
        } else if (post.getPostType() == Post.GIF_TYPE) {
            url = post.getVideoUrl(); // GIFs often served as videos (mp4)
            currentMediaType = EXTRA_MEDIA_TYPE_GIF; // Keep original type for logic, but extension might be mp4
            extras.putString(EXTRA_URL, url);
            extras.putInt(EXTRA_MEDIA_TYPE, currentMediaType);
            extras.putString(EXTRA_SUBREDDIT_NAME, post.getSubredditName());
            extras.putInt(EXTRA_IS_NSFW, post.isNSFW() ? 1 : 0);
        } else if (post.getPostType() == Post.VIDEO_TYPE) {
            currentMediaType = EXTRA_MEDIA_TYPE_VIDEO;
            if (post.isStreamable()) {
                if (post.isLoadedStreamableVideoAlready()) {
                    extras.putString(EXTRA_URL, post.getVideoUrl());
                } else {
                    extras.putString(EXTRA_STREAMABLE_SHORT_CODE, post.getStreamableShortCode());
                }

                extras.putString(EXTRA_FILE_NAME, "Streamable-" + post.getStreamableShortCode() + ".mp4");
            } else if (post.isRedgifs()) {
                extras.putString(EXTRA_URL, post.getVideoUrl());
                extras.putString(EXTRA_REDGIFS_ID, post.getRedgifsId());

                String redgifsId = post.getRedgifsId();
                if (redgifsId != null && redgifsId.contains("-")) {
                    redgifsId = redgifsId.substring(0, redgifsId.indexOf('-'));
                }
            } else if (post.isImgur()) {
                url = post.getVideoUrl();
                extras.putString(EXTRA_URL, url);
            } else { // Standard Reddit video
                url = post.getVideoUrl();
                extras.putString(EXTRA_URL, url);
            }
            extras.putInt(EXTRA_MEDIA_TYPE, currentMediaType);
            extras.putString(EXTRA_SUBREDDIT_NAME, post.getSubredditName());
            extras.putInt(EXTRA_IS_NSFW, post.isNSFW() ? 1 : 0);
        } else if (post.getPostType() == Post.GALLERY_TYPE) {
            Post.Gallery media = post.getGallery().get(galleryIndex);
            Log.d("GalleryDownload", "DownloadMediaService.constructJobInfo(Gallery): media.mediaType=" + media.mediaType + ", post.isNSFW()=" + post.isNSFW());

            if (media.mediaType == Post.Gallery.TYPE_VIDEO) {
                url = media.url;
                currentMediaType = EXTRA_MEDIA_TYPE_VIDEO;
                extras.putString(EXTRA_URL, url);
                extras.putInt(EXTRA_MEDIA_TYPE, currentMediaType);
            } else {
                url = media.hasFallback() ? media.fallbackUrl : media.url; // Retrieve original instead of the one additionally compressed by reddit
                currentMediaType = media.mediaType == Post.Gallery.TYPE_GIF ? EXTRA_MEDIA_TYPE_GIF : EXTRA_MEDIA_TYPE_IMAGE;
                extras.putString(EXTRA_URL, url);
                extras.putInt(EXTRA_MEDIA_TYPE, currentMediaType);
            }

            extras.putString(EXTRA_SUBREDDIT_NAME, post.getSubredditName());
            extras.putInt(EXTRA_IS_NSFW, post.isNSFW() ? 1 : 0);
        }

        // Determine extension based on URL and media type
        extension = getExtension(url, currentMediaType, null); // Pass null for defaultFileName initially

        // Construct filename: title + (optional index for gallery) + extension
        String finalFileName = sanitizedTitle +
                (post.getPostType() == Post.GALLERY_TYPE && galleryIndex >= 0 ? "_" + (galleryIndex + 1) : "") + // Use 1-based index for galleries
                extension;

        // Set the final filename in extras
        extras.putString(EXTRA_FILE_NAME, finalFileName);

        // Re-fetch extension if it was based on a potentially incorrect default, now using the final name
        if (url == null || url.isEmpty()) { // Especially for Redgifs/Streamable where URL might be fetched later
            extension = getExtension(url, currentMediaType, finalFileName);
            finalFileName = sanitizedTitle +
                (post.getPostType() == Post.GALLERY_TYPE && galleryIndex >= 0 ? "_" + (galleryIndex + 1) : "") +
                extension;
             extras.putString(EXTRA_FILE_NAME, finalFileName); // Update again if extension changed
        }


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return new JobInfo.Builder(JOB_ID++, new ComponentName(context, DownloadMediaService.class))
                    .setUserInitiated(true)
                    .setRequiredNetwork(new NetworkRequest.Builder().clearCapabilities().build())
                    .setEstimatedNetworkBytes(0, contentEstimatedBytes + 500)
                    .setExtras(extras)
                    .build();
        } else {
            return new JobInfo.Builder(JOB_ID++, new ComponentName(context, DownloadMediaService.class))
                    .setOverrideDeadline(0)
                    .setExtras(extras)
                    .build();
        }
    }

    public static JobInfo constructGalleryDownloadAllMediaJobInfo(Context context, long contentEstimatedBytes, Post post) {
        PersistableBundle extras = new PersistableBundle();
        if (post.getPostType() == Post.GALLERY_TYPE) {
            extras.putString(EXTRA_SUBREDDIT_NAME, post.getSubredditName());
            extras.putInt(EXTRA_IS_NSFW, post.isNSFW() ? 1 : 0);

            ArrayList<Post.Gallery> gallery = post.getGallery();

            StringBuilder concatUrlsBuilder = new StringBuilder();
            StringBuilder concatMediaTypesBuilder = new StringBuilder();
            StringBuilder concatFileNamesBuilder = new StringBuilder();

            for (int i = 0; i < gallery.size(); i++) {
                Post.Gallery media = gallery.get(i);
                String url = "";
                int currentMediaType = -1;
                String sanitizedTitle = sanitizeFilename(post.getTitle()); // Sanitize title once

                if (media.mediaType == Post.Gallery.TYPE_VIDEO) {
                    url = media.url;
                    currentMediaType = EXTRA_MEDIA_TYPE_VIDEO;
                    concatUrlsBuilder.append(url).append(" ");
                    concatMediaTypesBuilder.append(currentMediaType).append(" ");
                } else {
                    url = media.hasFallback() ? media.fallbackUrl : media.url; // Retrieve original
                    currentMediaType = media.mediaType == Post.Gallery.TYPE_GIF ? EXTRA_MEDIA_TYPE_GIF : EXTRA_MEDIA_TYPE_IMAGE;
                    concatUrlsBuilder.append(url).append(" ");
                    concatMediaTypesBuilder.append(currentMediaType).append(" ");
                }

                // Construct filename for this gallery item
                String extension = getExtension(url, currentMediaType, media.fileName); // Use original media.fileName as fallback hint
                String finalFileName = sanitizedTitle + "_" + (i + 1) + extension; // Use 1-based index
                concatFileNamesBuilder.append(finalFileName).append(" ");
            }

            if (concatUrlsBuilder.length() > 0) {
                concatUrlsBuilder.deleteCharAt(concatUrlsBuilder.length() - 1);
            }

            if (concatMediaTypesBuilder.length() > 0) {
                concatMediaTypesBuilder.deleteCharAt(concatMediaTypesBuilder.length() - 1);
            }

            if (concatFileNamesBuilder.length() > 0) {
                concatFileNamesBuilder.deleteCharAt(concatFileNamesBuilder.length() - 1);
            }

            extras.putString(EXTRA_ALL_GALLERY_IMAGE_URLS, concatUrlsBuilder.toString());
            extras.putString(EXTRA_ALL_GALLERY_IMAGE_MEDIA_TYPES, concatMediaTypesBuilder.toString());
            extras.putString(EXTRA_ALL_GALLERY_IMAGE_FILE_NAMES, concatFileNamesBuilder.toString());
            extras.putInt(EXTRA_IS_ALL_GALLERY_MEDIA, 1);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return new JobInfo.Builder(JOB_ID++, new ComponentName(context, DownloadMediaService.class))
                    .setUserInitiated(true)
                    .setRequiredNetwork(new NetworkRequest.Builder().clearCapabilities().build())
                    .setEstimatedNetworkBytes(0, contentEstimatedBytes + 500)
                    .setExtras(extras)
                    .build();
        } else {
            return new JobInfo.Builder(JOB_ID++, new ComponentName(context, DownloadMediaService.class))
                    .setOverrideDeadline(0)
                    .setExtras(extras)
                    .build();
        }
    }

    public static JobInfo constructJobInfo(Context context, long contentEstimatedBytes, ImgurMedia imgurMedia, String subredditName, boolean isNsfw, String title) {
        PersistableBundle extras = new PersistableBundle();
        extras.putString(EXTRA_URL, imgurMedia.getLink());

        if (title == null || title.trim().isEmpty()) {
            title = imgurMedia.getId(); // Fallback to ID if title is missing
        }

        String sanitizedTitle = sanitizeFilename(title); // Use static sanitize helper
        String extension = getExtension(imgurMedia); // Use static ImgurMedia extension helper
        String finalFileName = sanitizedTitle + extension;

        extras.putString(EXTRA_FILE_NAME, finalFileName); // Set the constructed filename

        if (imgurMedia.getType() == ImgurMedia.TYPE_VIDEO) {
            extras.putInt(EXTRA_MEDIA_TYPE, EXTRA_MEDIA_TYPE_VIDEO);
        } else {
            extras.putInt(EXTRA_MEDIA_TYPE, EXTRA_MEDIA_TYPE_IMAGE);
        }

        // Pass the received subreddit, NSFW status, and title to the extras
        extras.putString(EXTRA_SUBREDDIT_NAME, subredditName);
        extras.putBoolean(EXTRA_IS_NSFW, isNsfw);
        extras.putString(EXTRA_TITLE, title); // Add title as well for consistency

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return new JobInfo.Builder(JOB_ID++, new ComponentName(context, DownloadMediaService.class))
                    .setUserInitiated(true)
                    .setRequiredNetwork(new NetworkRequest.Builder().clearCapabilities().build())
                    .setEstimatedNetworkBytes(0, contentEstimatedBytes + 500)
                    .setExtras(extras)
                    .build();
        } else {
            return new JobInfo.Builder(JOB_ID++, new ComponentName(context, DownloadMediaService.class))
                    .setOverrideDeadline(0)
                    .setExtras(extras)
                    .build();
        }
    }

    public static JobInfo constructImgurAlbumDownloadAllMediaJobInfo(Context context, long contentEstimatedBytes, List<ImgurMedia> imgurMedia, String subredditName, boolean isNsfw, String title) {
        PersistableBundle extras = new PersistableBundle();

        Log.d("ImgurDownload", "Creating job for Imgur album with " + imgurMedia.size() + " items, isNsfw=" + isNsfw);

        StringBuilder concatUrlsBuilder = new StringBuilder();
        StringBuilder concatMediaTypesBuilder = new StringBuilder();
        StringBuilder concatFileNamesBuilder = new StringBuilder();

        for (int i = 0; i < imgurMedia.size(); i++) {
            ImgurMedia media = imgurMedia.get(i);
            String url = media.getLink();
            int currentMediaType;

            if (media.getType() == ImgurMedia.TYPE_VIDEO) {
                currentMediaType = EXTRA_MEDIA_TYPE_VIDEO;
                concatUrlsBuilder.append(url).append(" ");
                concatMediaTypesBuilder.append(currentMediaType).append(" ");
                Log.d("ImgurDownload", "Item " + i + ": Video - " + url);
            } else {
                currentMediaType = EXTRA_MEDIA_TYPE_IMAGE;
                concatUrlsBuilder.append(url).append(" ");
                concatMediaTypesBuilder.append(currentMediaType).append(" ");
                Log.d("ImgurDownload", "Item " + i + ": Image - " + url);
            }

            if (title == null || title.trim().isEmpty()) {
                title = media.getId(); // Fallback to ID
            }

            String sanitizedTitle = sanitizeFilename(title);
            String extension = getExtension(media);
            String finalFileName = sanitizedTitle + "_" + (i + 1) + extension; // Add 1-based index
            concatFileNamesBuilder.append(finalFileName).append(" ");
        }

        if (concatUrlsBuilder.length() > 0) {
            concatUrlsBuilder.deleteCharAt(concatUrlsBuilder.length() - 1);
        }

        if (concatMediaTypesBuilder.length() > 0) {
            concatMediaTypesBuilder.deleteCharAt(concatMediaTypesBuilder.length() - 1);
        }

        if (concatFileNamesBuilder.length() > 0) {
            concatFileNamesBuilder.deleteCharAt(concatFileNamesBuilder.length() - 1);
        }

        extras.putString(EXTRA_ALL_GALLERY_IMAGE_URLS, concatUrlsBuilder.toString());
        extras.putString(EXTRA_ALL_GALLERY_IMAGE_MEDIA_TYPES, concatMediaTypesBuilder.toString());
        extras.putString(EXTRA_ALL_GALLERY_IMAGE_FILE_NAMES, concatFileNamesBuilder.toString());
        extras.putString(EXTRA_SUBREDDIT_NAME, subredditName);
        extras.putBoolean(EXTRA_IS_NSFW, isNsfw);
        extras.putString(EXTRA_TITLE, title);
        extras.putInt(EXTRA_MEDIA_TYPE, EXTRA_MEDIA_TYPE_IMAGE);

        Log.d("ImgurDownload", "Bundle created with media types: " + concatMediaTypesBuilder.toString());
        Log.d("ImgurDownload", "Overall media type set to: " + EXTRA_MEDIA_TYPE_IMAGE + " (IMAGE)");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return new JobInfo.Builder(JOB_ID++, new ComponentName(context, DownloadMediaService.class))
                    .setUserInitiated(true)
                    .setRequiredNetwork(new NetworkRequest.Builder().clearCapabilities().build())
                    .setEstimatedNetworkBytes(0, contentEstimatedBytes + 500)
                    .setExtras(extras)
                    .build();
        } else {
            return new JobInfo.Builder(JOB_ID++, new ComponentName(context, DownloadMediaService.class))
                    .setOverrideDeadline(0)
                    .setExtras(extras)
                    .build();
        }
    }

    public static JobInfo constructJobInfo(Context context, long contentEstimatedBytes, PersistableBundle extras) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return new JobInfo.Builder(JOB_ID++, new ComponentName(context, DownloadMediaService.class))
                    .setUserInitiated(true)
                    .setRequiredNetwork(new NetworkRequest.Builder().clearCapabilities().build())
                    .setEstimatedNetworkBytes(0, contentEstimatedBytes + 500)
                    .setExtras(extras)
                    .build();
        } else {
            return new JobInfo.Builder(JOB_ID++, new ComponentName(context, DownloadMediaService.class))
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
        PersistableBundle extras = params.getExtras();
        int mediaType = extras.getInt(EXTRA_MEDIA_TYPE, EXTRA_MEDIA_TYPE_IMAGE);

        Log.d("ImgurDownload", "onStartJob - overall mediaType: " + mediaType);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, getNotificationChannelId(mediaType));

        NotificationChannelCompat serviceChannel =
                new NotificationChannelCompat.Builder(
                        getNotificationChannelId(mediaType),
                        NotificationManagerCompat.IMPORTANCE_LOW)
                        .setName(getNotificationChannel(mediaType))
                        .build();
        notificationManager.createNotificationChannel(serviceChannel);

        int randomNotificationIdOffset = new Random().nextInt(10000);
        String notificationTitle = extras.containsKey(EXTRA_FILE_NAME) ?
                extras.getString(EXTRA_FILE_NAME) :
                (extras.getInt(EXTRA_IS_ALL_GALLERY_MEDIA, 0) == 1 ?
                        getString(R.string.download_all_gallery_media_notification_title) : getString(R.string.download_all_imgur_album_media_notification_title));
        switch (extras.getInt(EXTRA_MEDIA_TYPE, EXTRA_MEDIA_TYPE_IMAGE)) {
            case EXTRA_MEDIA_TYPE_GIF:
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    setNotification(params,
                            NotificationUtils.DOWNLOAD_GIF_NOTIFICATION_ID + randomNotificationIdOffset,
                            createNotification(builder, notificationTitle),
                            JobService.JOB_END_NOTIFICATION_POLICY_DETACH);
                } else {
                    notificationManager.notify(NotificationUtils.DOWNLOAD_GIF_NOTIFICATION_ID + randomNotificationIdOffset,
                            createNotification(builder, notificationTitle));
                }
                break;
            case EXTRA_MEDIA_TYPE_VIDEO:
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    setNotification(params,
                            NotificationUtils.DOWNLOAD_VIDEO_NOTIFICATION_ID + randomNotificationIdOffset,
                            createNotification(builder, notificationTitle),
                            JobService.JOB_END_NOTIFICATION_POLICY_DETACH);
                } else {
                    notificationManager.notify(NotificationUtils.DOWNLOAD_VIDEO_NOTIFICATION_ID + randomNotificationIdOffset,
                            createNotification(builder, notificationTitle));
                }
                break;
            default:
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    setNotification(params,
                            NotificationUtils.DOWNLOAD_IMAGE_NOTIFICATION_ID + randomNotificationIdOffset,
                            createNotification(builder, notificationTitle),
                            JobService.JOB_END_NOTIFICATION_POLICY_DETACH);
                } else {
                    notificationManager.notify(NotificationUtils.DOWNLOAD_IMAGE_NOTIFICATION_ID + randomNotificationIdOffset,
                            createNotification(builder, notificationTitle));
                }
        }

        mExecutor.execute(() -> {
            Log.d("GalleryDownload", "DownloadMediaService.onStartJob(): Job started. Extras: " + extras.toString());
            String subredditName = extras.getString(EXTRA_SUBREDDIT_NAME);
            // Remove the direct getBoolean call:
            // boolean isNsfw = extras.getBoolean(EXTRA_IS_NSFW, false);

            // Explicitly get the object and check its type:
            Object nsfwValue = extras.get(EXTRA_IS_NSFW);
            boolean isNsfw = false; // Default value
            if (nsfwValue instanceof Boolean) {
                isNsfw = (Boolean) nsfwValue;
            } else if (nsfwValue instanceof Integer) {
                // Correctly handle the Integer case based on the value (1 for true, 0 for false)
                isNsfw = ((Integer) nsfwValue) != 0;
                // Optional: Log a warning if you still want to know this happened, but it's handled now.
                Log.d("ImgurDownload", "EXTRA_IS_NSFW was Integer: " + nsfwValue);
            } else if (nsfwValue != null) {
                // Optional: Handle unexpected types if necessary
                Log.d("ImgurDownload", "Unexpected type for EXTRA_IS_NSFW: " + nsfwValue.getClass().getName());
            }

            Log.d("ImgurDownload", "Processing download with isNsfw=" + isNsfw);

            if (extras.containsKey(EXTRA_ALL_GALLERY_IMAGE_URLS)) {
                // Download all images in a gallery post
                String concatUrls = extras.getString(EXTRA_ALL_GALLERY_IMAGE_URLS);
                String concatMediaTypes = extras.getString(EXTRA_ALL_GALLERY_IMAGE_MEDIA_TYPES);
                String concatFileNames = extras.getString(EXTRA_ALL_GALLERY_IMAGE_FILE_NAMES);

                Log.d("ImgurDownload", "Processing album/gallery with media types: " + concatMediaTypes);

                String[] urls = concatUrls.split(" ");
                String[] mediaTypes = concatMediaTypes.split(" ");
                String[] fileNames = concatFileNames.split(" ");

                Log.d("ImgurDownload", "Split into " + urls.length + " items to download");

                boolean allImagesDownloadedSuccessfully = true;
                for (int i = 0; i < urls.length; i++) {
                    String mimeType = Integer.parseInt(mediaTypes[i]) == EXTRA_MEDIA_TYPE_VIDEO ? "video/*" : "image/*";
                    int finalI = i;
                    int individualMediaType = Integer.parseInt(mediaTypes[i]);

                    Log.d("ImgurDownload", "Processing item " + i + ": mediaType=" + individualMediaType +
                        " (" + (individualMediaType == EXTRA_MEDIA_TYPE_VIDEO ? "VIDEO" :
                        individualMediaType == EXTRA_MEDIA_TYPE_GIF ? "GIF" : "IMAGE") + ")");

                    // Create a new PersistableBundle for each individual download to ensure correct mediaType
                    PersistableBundle itemExtras = new PersistableBundle(extras);
                    // Set the media type for this specific item
                    itemExtras.putInt(EXTRA_MEDIA_TYPE, individualMediaType);

                    allImagesDownloadedSuccessfully &= downloadMedia(params, urls[i], itemExtras, builder, individualMediaType, randomNotificationIdOffset, fileNames[i],
                            mimeType, subredditName, isNsfw, true,
                            new DownloadProgressResponseBody.ProgressListener() {
                                long time = 0;
                                @Override
                                public void update(long bytesRead, long contentLength, boolean done) {
                                    if (!done) {
                                        if (contentLength != -1) {
                                            long currentTime = System.currentTimeMillis();

                                            if (currentTime - time > 1000) {
                                                time = currentTime;
                                                int currentMediaProgress = (int) (((float) bytesRead / contentLength + (float) finalI / urls.length) * 100);
                                                updateNotification(builder, individualMediaType, 0, currentMediaProgress, randomNotificationIdOffset, null, null);
                                            }
                                        }
                                    }
                                }
                            });
                }

                updateNotification(builder, mediaType,
                        allImagesDownloadedSuccessfully ? R.string.downloading_media_finished : R.string.download_gallery_failed_some_images,
                        -1, randomNotificationIdOffset,
                        null, null);
                jobFinished(params, false);
            } else {
                String fileUrl = extras.getString(EXTRA_URL);
                String fileName = extras.getString(EXTRA_FILE_NAME);
                String mimeType = mediaType == EXTRA_MEDIA_TYPE_VIDEO ? "video/*" : "image/*";

                Log.d("ImgurDownload", "Processing single download: mediaType=" + mediaType +
                    " (" + (mediaType == EXTRA_MEDIA_TYPE_VIDEO ? "VIDEO" :
                    mediaType == EXTRA_MEDIA_TYPE_GIF ? "GIF" : "IMAGE") + ")");

                downloadMedia(params, fileUrl, extras, builder, mediaType, randomNotificationIdOffset, fileName,
                    mimeType, subredditName, isNsfw, false, new DownloadProgressResponseBody.ProgressListener() {
                        long time = 0;
                        @Override
                        public void update(long bytesRead, long contentLength, boolean done) {
                            if (!done) {
                                if (contentLength != -1) {
                                    long currentTime = System.currentTimeMillis();
                                    if (currentTime - time > 1000) {
                                        time = currentTime;
                                        updateNotification(builder, mediaType, 0,
                                                (int) ((100 * bytesRead) / contentLength), randomNotificationIdOffset, null, null);
                                    }
                                }
                            }
                        }
                    });
            }
        });

        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return false;
    }

    /**
     *
     * @param params
     * @param fileUrl
     * @param intent
     * @param builder
     * @param mediaType
     * @param randomNotificationIdOffset
     * @param fileName
     * @param mimeType
     * @param subredditName
     * @param isNsfw
     * @param multipleDownloads
     * @param progressListener
     * @return true if download succeeded or false otherwise.
     */
    private boolean downloadMedia(JobParameters params, String fileUrl, PersistableBundle intent,
                            NotificationCompat.Builder builder, int mediaType, int randomNotificationIdOffset,
                            String fileName, String mimeType, String subredditName, boolean isNsfw,
                            boolean multipleDownloads, DownloadProgressResponseBody.ProgressListener progressListener) {
    Log.d("GalleryDownload", "DownloadMediaService.downloadMedia(): Starting download. " +
        "mediaType=" + mediaType +
        ", isNsfw=" + isNsfw +
        ", fileName=" + fileName +
        ", fileUrl=" + (fileUrl == null ? "NULL (will fetch)" : fileUrl) +
        ", multipleDownloads=" + multipleDownloads);
    Log.d("ImgurDownload", "downloadMedia - mediaType=" + mediaType +
        " (" + (mediaType == EXTRA_MEDIA_TYPE_VIDEO ? "VIDEO" :
        mediaType == EXTRA_MEDIA_TYPE_GIF ? "GIF" : "IMAGE") + ")" +
        ", fileName=" + fileName + ", isNsfw=" + isNsfw);

        if (fileUrl == null) {
            // Only Redgifs and Streamble video can go inside this if clause.
            String redgifsId = intent.getString(EXTRA_REDGIFS_ID, null);
            String streamableShortCode = intent.getString(EXTRA_STREAMABLE_SHORT_CODE, null);

            if (redgifsId == null && streamableShortCode == null) {
                downloadFinished(params, builder, mediaType, randomNotificationIdOffset, mimeType,
                        null,
                        ERROR_INVALID_ARGUMENT,
                        multipleDownloads);
                return false;
            }

            fileUrl = VideoLinkFetcher.fetchVideoLinkSync(mRedgifsRetrofit, mStreamableApiProvider, mCurrentAccountSharedPreferences,
                    redgifsId == null ? ViewVideoActivity.VIDEO_TYPE_STREAMABLE : ViewVideoActivity.VIDEO_TYPE_REDGIFS,
                    redgifsId, streamableShortCode);

            if (fileUrl == null) {
                downloadFinished(params, builder, mediaType, randomNotificationIdOffset, mimeType,
                        null,
                        redgifsId == null ? ERROR_CANNOT_FETCH_STREAMABLE_VIDEO_LINK : ERROR_FILE_CANNOT_FETCH_REDGIFS_VIDEO_LINK,
                        multipleDownloads);
                return false;
            }
        }

        OkHttpClient client = new OkHttpClient.Builder()
                .addNetworkInterceptor(chain -> {
                    okhttp3.Response originalResponse = chain.proceed(chain.request());
                    return originalResponse.newBuilder()
                            .body(new DownloadProgressResponseBody(originalResponse.body(), progressListener))
                            .build();
                })
                .addInterceptor(chain -> chain.proceed(
                        chain.request()
                                .newBuilder()
                                .header("User-Agent", APIUtils.USER_AGENT)
                                .build()
                ))
                .build();

        retrofit = retrofit.newBuilder().client(client).build();

        boolean separateDownloadFolder = mSharedPreferences.getBoolean(SharedPreferencesUtils.SEPARATE_FOLDER_FOR_EACH_SUBREDDIT, false);

        Response<ResponseBody> response;
        String destinationFileUriString = null;
        boolean isDefaultDestination = true;
        try {
            response = retrofit.create(DownloadFile.class).downloadFile(fileUrl).execute();
            if (response.isSuccessful() && response.body() != null) {
                String destinationFileDirectory = getDownloadLocation(mediaType, isNsfw);
                Log.d("ImgurDownload", "Got download location: " + destinationFileDirectory + " for mediaType=" + mediaType + ", isNsfw=" + isNsfw);

                if (destinationFileDirectory == null || destinationFileDirectory.isEmpty()) {
                    Log.e("ImgurDownload", "Download location is empty! mediaType=" + mediaType + ", isNsfw=" + isNsfw);
                    downloadFinished(params, builder, mediaType, randomNotificationIdOffset, mimeType,
                            null, ERROR_CANNOT_GET_DESTINATION_DIRECTORY, multipleDownloads);
                    return false;
                }
                isDefaultDestination = false;
                DocumentFile picFile;
                DocumentFile dir;

                if (separateDownloadFolder && subredditName != null && !subredditName.equals("")) {
                    dir = DocumentFile.fromTreeUri(DownloadMediaService.this, Uri.parse(destinationFileDirectory));
                    if (dir == null) {
                        Log.e("ImgurDownload", "Could not get tree URI from destination directory");
                        downloadFinished(params, builder, mediaType, randomNotificationIdOffset, mimeType,
                                null, ERROR_CANNOT_GET_DESTINATION_DIRECTORY, multipleDownloads);

                        return false;
                    }
                    dir = dir.findFile(subredditName);
                    if (dir == null) {
                        dir = DocumentFile.fromTreeUri(DownloadMediaService.this, Uri.parse(destinationFileDirectory)).createDirectory(subredditName);
                        if (dir == null) {
                            Log.e("ImgurDownload", "Could not create subreddit directory");
                            downloadFinished(params, builder, mediaType, randomNotificationIdOffset, mimeType,
                                    null, ERROR_CANNOT_GET_DESTINATION_DIRECTORY, multipleDownloads);

                            return false;
                        }
                    }
                } else {
                    dir = DocumentFile.fromTreeUri(DownloadMediaService.this, Uri.parse(destinationFileDirectory));
                    if (dir == null) {
                        Log.e("ImgurDownload", "Could not get tree URI from destination directory (no subreddit)");
                        downloadFinished(params, builder, mediaType, randomNotificationIdOffset, mimeType,
                                null, ERROR_CANNOT_GET_DESTINATION_DIRECTORY, multipleDownloads);

                        return false;
                    }
                }

                int dotIndex = fileName.lastIndexOf('.');
                final String baseName = (dotIndex == -1) ? fileName : fileName.substring(0, dotIndex);
                final String extension = (dotIndex == -1) ? "" : fileName.substring(dotIndex);

                DocumentFile[] files = dir.listFiles();
                HashSet<String> existingFileNames = new HashSet<>();
                if (files != null) {
                    for (DocumentFile file : files) {
                        if (file.getName() != null) {
                            existingFileNames.add(file.getName().toLowerCase());
                        }
                    }
                }

                if (existingFileNames.contains(fileName.toLowerCase())) {
                    int num = 1;
                    String newFileName;
                    do {
                        newFileName = baseName + " (" + num + ")" + extension;
                        num++;
                    } while (existingFileNames.contains(newFileName.toLowerCase()));
                    fileName = newFileName;
                }

                picFile = dir.createFile(mimeType, fileName);

                if (picFile == null) {
                    Log.e("ImgurDownload", "Could not create file: " + fileName);
                    downloadFinished(params, builder, mediaType, randomNotificationIdOffset, mimeType,
                            null, ERROR_CANNOT_GET_DESTINATION_DIRECTORY, multipleDownloads);
                    return false;
                }

                destinationFileUriString = picFile.getUri().toString();
                Log.d("ImgurDownload", "File created successfully at: " + destinationFileUriString);
            } else {
                Log.e("ImgurDownload", "Download response not successful: " + response.code());
                downloadFinished(params, builder, mediaType, randomNotificationIdOffset, mimeType, null,
                        ERROR_FILE_CANNOT_DOWNLOAD, multipleDownloads);
                return false;
            }
        } catch (IOException e) {
            e.printStackTrace();
            Log.e("ImgurDownload", "IOException during download: " + e.getMessage());
            downloadFinished(params, builder, mediaType, randomNotificationIdOffset, mimeType, null,
                    ERROR_FILE_CANNOT_DOWNLOAD, multipleDownloads);
            return false;
        }

        try {
            Uri destinationFileUri = writeResponseBodyToDisk(response.body(), isDefaultDestination, destinationFileUriString, fileName, mediaType);
            Log.d("ImgurDownload", "File written successfully");
            downloadFinished(params, builder, mediaType, randomNotificationIdOffset,
                    mimeType, destinationFileUri, NO_ERROR, multipleDownloads);
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            Log.e("ImgurDownload", "IOException writing to disk: " + e.getMessage());
            downloadFinished(params, builder, mediaType, randomNotificationIdOffset,
                    mimeType, null, ERROR_FILE_CANNOT_SAVE, multipleDownloads);

            return false;
        }
    }

    private Notification createNotification(NotificationCompat.Builder builder, String fileName) {
        builder.setContentTitle(fileName).setContentText(getString(R.string.downloading)).setProgress(100, 0, false);
        return builder.setSmallIcon(R.drawable.ic_notification)
                .setColor(mCustomThemeWrapper.getColorPrimaryLightTheme())
                .build();
    }

    private void updateNotification(NotificationCompat.Builder builder, int mediaType, int contentStringResId, int progress, int randomNotificationIdOffset, Uri mediaUri, String mimeType) {
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
                intent.setDataAndType(mediaUri, mimeType);
                intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                PendingIntent pendingIntent = PendingIntent.getActivity(DownloadMediaService.this, 0, intent, pendingIntentFlags);
                builder.setContentIntent(pendingIntent);

                Intent shareIntent = new Intent();
                shareIntent.setAction(Intent.ACTION_SEND);
                shareIntent.putExtra(Intent.EXTRA_STREAM, mediaUri);
                shareIntent.setType(mimeType);
                shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                Intent intentAction = Intent.createChooser(shareIntent, getString(R.string.share));
                PendingIntent shareActionPendingIntent = PendingIntent.getActivity(this, 1, intentAction, pendingIntentFlags);

                builder.addAction(new NotificationCompat.Action(R.drawable.ic_notification, getString(R.string.share), shareActionPendingIntent));

                Intent deleteIntent = new Intent(this, DownloadedMediaDeleteActionBroadcastReceiver.class);
                deleteIntent.setData(mediaUri);
                deleteIntent.putExtra(DownloadedMediaDeleteActionBroadcastReceiver.EXTRA_NOTIFICATION_ID, getNotificationId(mediaType, randomNotificationIdOffset));
                PendingIntent deleteActionPendingIntent = PendingIntent.getBroadcast(this, 2, deleteIntent, pendingIntentFlags);
                builder.addAction(new NotificationCompat.Action(R.drawable.ic_notification, getString(R.string.delete), deleteActionPendingIntent));
            }

            notificationManager.notify(getNotificationId(mediaType, randomNotificationIdOffset), builder.build());
        }
    }

    private String getNotificationChannelId(int mediaType) {
        switch (mediaType) {
            case EXTRA_MEDIA_TYPE_GIF:
                return NotificationUtils.CHANNEL_ID_DOWNLOAD_GIF;
            case EXTRA_MEDIA_TYPE_VIDEO:
                return NotificationUtils.CHANNEL_ID_DOWNLOAD_VIDEO;
            default:
                return NotificationUtils.CHANNEL_ID_DOWNLOAD_IMAGE;
        }
    }

    private String getNotificationChannel(int mediaType) {
        switch (mediaType) {
            case EXTRA_MEDIA_TYPE_GIF:
                return NotificationUtils.CHANNEL_DOWNLOAD_GIF;
            case EXTRA_MEDIA_TYPE_VIDEO:
                return NotificationUtils.CHANNEL_DOWNLOAD_VIDEO;
            default:
                return NotificationUtils.CHANNEL_DOWNLOAD_IMAGE;
        }
    }

    private int getNotificationId(int mediaType, int randomNotificationIdOffset) {
        switch (mediaType) {
            case EXTRA_MEDIA_TYPE_GIF:
                return NotificationUtils.DOWNLOAD_GIF_NOTIFICATION_ID + randomNotificationIdOffset;
            case EXTRA_MEDIA_TYPE_VIDEO:
                return NotificationUtils.DOWNLOAD_VIDEO_NOTIFICATION_ID + randomNotificationIdOffset;
            default:
                return NotificationUtils.DOWNLOAD_IMAGE_NOTIFICATION_ID + randomNotificationIdOffset;
        }
    }

    private String getDownloadLocation(int mediaType, boolean isNsfw) {
        String defaultSharedPrefsFile = "ml.docilealligator.infinityforreddit_preferences";
        // Additional diagnostics
        String nsfwLoc = mSharedPreferences.getString(SharedPreferencesUtils.NSFW_DOWNLOAD_LOCATION, "");
        String imgLoc = mSharedPreferences.getString(SharedPreferencesUtils.IMAGE_DOWNLOAD_LOCATION, "");
        String gifLoc = mSharedPreferences.getString(SharedPreferencesUtils.GIF_DOWNLOAD_LOCATION, "");
        String vidLoc = mSharedPreferences.getString(SharedPreferencesUtils.VIDEO_DOWNLOAD_LOCATION, "");

        Log.d("GalleryDownload", "DownloadMediaService.getDownloadLocation(): Checking locations for mediaType=" + mediaType + ", isNsfw=" + isNsfw);
        Log.d("ImgurDownload", "DownloadMediaService getDownloadLocation - mediaType=" + mediaType +
            ", isNsfw=" + isNsfw +
            ", prefs contain - IMAGE: " + (imgLoc.isEmpty() ? "EMPTY" : "SET") +
            ", GIF: " + (gifLoc.isEmpty() ? "EMPTY" : "SET") +
            ", VIDEO: " + (vidLoc.isEmpty() ? "EMPTY" : "SET") +
            ", NSFW: " + (nsfwLoc.isEmpty() ? "EMPTY" : "SET"));

        // Try alternate SharedPreferences file if image location is empty
        if (imgLoc.isEmpty()) {
            imgLoc = getApplicationContext().getSharedPreferences(defaultSharedPrefsFile, MODE_PRIVATE)
                    .getString(SharedPreferencesUtils.IMAGE_DOWNLOAD_LOCATION, "");
            Log.d("ImgurDownload", "Tried alternate SharedPreferences, IMAGE: " +
                (imgLoc.isEmpty() ? "EMPTY" : "SET"));
        }

        if (isNsfw && mSharedPreferences.getBoolean(SharedPreferencesUtils.SAVE_NSFW_MEDIA_IN_DIFFERENT_FOLDER, false)) {
            // If NSFW location is set, return it. Otherwise, return empty string to indicate not set.
            if (!nsfwLoc.isEmpty()) {
                Log.d("ImgurDownload", "Using NSFW location: " + nsfwLoc);
                return nsfwLoc;
            } else {
                Log.d("GalleryDownload", "NSFW location requested but not set, returning empty.");
                return ""; // Explicitly return empty if NSFW location is not set
            }
        }

        // If not using separate NSFW folder, proceed with type-specific locations
        String finalLocation;
        switch (mediaType) {
            case EXTRA_MEDIA_TYPE_GIF:
                finalLocation = gifLoc.isEmpty() ? imgLoc : gifLoc; // Fallback to image location if GIF is not set
                break;
            case EXTRA_MEDIA_TYPE_VIDEO:
                finalLocation = vidLoc.isEmpty() ? imgLoc : vidLoc; // Fallback to image location if VIDEO is not set
                break;
            default: // EXTRA_MEDIA_TYPE_IMAGE
                finalLocation = imgLoc;
                break;
        }
        Log.d("GalleryDownload", "DownloadMediaService.getDownloadLocation(): Returning final location: " + (finalLocation == null || finalLocation.isEmpty() ? "EMPTY" : finalLocation));
        return finalLocation;
    }

    private Uri writeResponseBodyToDisk(ResponseBody body, boolean isDefaultDestination, String destinationFileUriString, String destinationFileName, int mediaType) throws IOException {
        ContentResolver contentResolver = getContentResolver();
        if (isDefaultDestination) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                InputStream inputStream = body.byteStream();
                OutputStream outputStream = new FileOutputStream(destinationFileUriString);
                byte[] fileReader = new byte[4096];

                long fileSize = body.contentLength();
                long fileSizeDownloaded = 0;

                while (true) {
                    int read = inputStream.read(fileReader);

                    if (read == -1) {
                        break;
                    }

                    outputStream.write(fileReader, 0, read);

                    fileSizeDownloaded += read;
                }

                outputStream.flush();
            } else {
                ContentValues contentValues = new ContentValues();
                contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, destinationFileName);
                String mimeType;

                switch (mediaType) {
                    case EXTRA_MEDIA_TYPE_VIDEO:
                        mimeType = "video/mpeg";
                        break;
                    case EXTRA_MEDIA_TYPE_GIF:
                        mimeType = "image/gif";
                        break;
                    default:
                        mimeType = "image/jpeg";
                }

                contentValues.put(MediaStore.MediaColumns.MIME_TYPE, mimeType);
                contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, destinationFileUriString);
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 1);

                final Uri contentUri = mediaType == EXTRA_MEDIA_TYPE_VIDEO ? MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) : MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
                Uri uri = contentResolver.insert(contentUri, contentValues);

                if (uri == null) {
                    throw new IOException("Failed to create new MediaStore record.");
                }

                OutputStream stream = contentResolver.openOutputStream(uri);

                if (stream == null) {
                    throw new IOException("Failed to get output stream.");
                }

                InputStream in = body.byteStream();
                byte[] buf = new byte[1024];
                int len;

                while ((len = in.read(buf)) > 0) {
                    stream.write(buf, 0, len);
                }

                contentValues.clear();
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0);
                contentResolver.update(uri, contentValues, null, null);
                destinationFileUriString = uri.toString();
            }
        } else {
            try (OutputStream stream = contentResolver.openOutputStream(Uri.parse(destinationFileUriString))) {
                if (stream == null) {
                    throw new IOException("Failed to get output stream.");
                }

                InputStream in = body.byteStream();

                byte[] buf = new byte[1024];
                int len;

                while ((len = in.read(buf)) > 0) {
                    stream.write(buf, 0, len);
                }
            }
        }
        return Uri.parse(destinationFileUriString);
    }

    private void downloadFinished(JobParameters parameters, NotificationCompat.Builder builder, int mediaType, int randomNotificationIdOffset, String mimeType, Uri destinationFileUri, int errorCode, boolean multipleDownloads) {
        if (errorCode != NO_ERROR) {
            if (!multipleDownloads) {
                switch (errorCode) {
                    case ERROR_CANNOT_GET_DESTINATION_DIRECTORY:
                        updateNotification(builder, mediaType, R.string.downloading_image_or_gif_failed_cannot_get_destination_directory,
                                -1, randomNotificationIdOffset, null, null);
                        break;
                    case ERROR_FILE_CANNOT_DOWNLOAD:
                        updateNotification(builder, mediaType, R.string.downloading_media_failed_cannot_download_media,
                                -1, randomNotificationIdOffset, null, null);
                        break;
                    case ERROR_FILE_CANNOT_SAVE:
                        updateNotification(builder, mediaType, R.string.downloading_media_failed_cannot_save_to_destination_directory,
                                -1, randomNotificationIdOffset, null, null);
                        break;
                    case ERROR_FILE_CANNOT_FETCH_REDGIFS_VIDEO_LINK:
                        updateNotification(builder, mediaType, R.string.download_media_failed_cannot_fetch_redgifs_url,
                                -1, randomNotificationIdOffset, null, null);
                        break;
                    case ERROR_CANNOT_FETCH_STREAMABLE_VIDEO_LINK:
                        updateNotification(builder, mediaType, R.string.download_media_failed_cannot_fetch_streamable_url,
                                -1, randomNotificationIdOffset, null, null);
                        break;
                    case ERROR_INVALID_ARGUMENT:
                        updateNotification(builder, mediaType, R.string.download_media_failed_invalid_argument,
                                -1, randomNotificationIdOffset, null, null);
                        break;
                }
            }
        } else {
            MediaScannerConnection.scanFile(
                    DownloadMediaService.this, new String[]{destinationFileUri.toString()}, null,
                    (path, uri) -> {
                        if (!multipleDownloads) {
                            updateNotification(builder, mediaType, R.string.downloading_media_finished, -1,
                                    randomNotificationIdOffset, destinationFileUri, mimeType);
                        }
                    }
            );
        }

        if (!multipleDownloads) {
            jobFinished(parameters, false);
        }
    }
}
