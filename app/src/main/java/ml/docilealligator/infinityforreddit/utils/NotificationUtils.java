package ml.docilealligator.infinityforreddit.utils;

import android.Manifest;
import android.app.Notification;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.core.app.NotificationChannelCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import ml.docilealligator.infinityforreddit.R;

public class NotificationUtils {
    public static final String CHANNEL_SUBMIT_POST = "Submit Post";
    public static final String CHANNEL_ID_NEW_MESSAGES = "new_messages";
    public static final String CHANNEL_NEW_MESSAGES = "New Messages";
    public static final String CHANNEL_ID_DOWNLOAD_REDDIT_VIDEO = "download_reddit_video";
    public static final String CHANNEL_DOWNLOAD_REDDIT_VIDEO = "Download Reddit Video";
    public static final String CHANNEL_ID_DOWNLOAD_VIDEO = "download_video";
    public static final String CHANNEL_DOWNLOAD_VIDEO = "Download Video";
    public static final String CHANNEL_ID_DOWNLOAD_IMAGE = "download_image";
    public static final String CHANNEL_DOWNLOAD_IMAGE = "Download Image";
    public static final String CHANNEL_ID_DOWNLOAD_GIF = "download_gif";
    public static final String CHANNEL_DOWNLOAD_GIF = "Download Gif";
    public static final String CHANNEL_ID_MATERIAL_YOU = "material_you";
    public static final String CHANNEL_MATERIAL_YOU = "Material You";
    public static final int SUBMIT_POST_SERVICE_NOTIFICATION_ID = 10000;
    public static final int DOWNLOAD_REDDIT_VIDEO_NOTIFICATION_ID = 20000;
    public static final int DOWNLOAD_VIDEO_NOTIFICATION_ID = 30000;
    public static final int DOWNLOAD_IMAGE_NOTIFICATION_ID = 40000;
    public static final int DOWNLOAD_GIF_NOTIFICATION_ID = 50000;
    public static final int MATERIAL_YOU_NOTIFICATION_ID = 60000;
    public static final int EDIT_PROFILE_SERVICE_NOTIFICATION_ID = 70000;

    private static final int SUMMARY_BASE_ID_UNREAD_MESSAGE = 0;
    private static final int NOTIFICATION_BASE_ID_UNREAD_MESSAGE = 1;

    private static final String GROUP_USER_BASE = "ml.docilealligator.infinityforreddit.";

    public static NotificationCompat.Builder buildNotification(NotificationManagerCompat notificationManager,
                                                               Context context, String title, String content,
                                                               String summary, String channelId, String channelName,
                                                               String group, int color) {
        NotificationChannelCompat channel =
                new NotificationChannelCompat.Builder(channelId, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                        .setName(channelName)
                        .build();
        notificationManager.createNotificationChannel(channel);

        return new NotificationCompat.Builder(context.getApplicationContext(), channelId)
                .setContentTitle(title)
                .setContentText(content)
                .setSmallIcon(R.drawable.ic_notification)
                .setColor(color)
                .setStyle(new NotificationCompat.BigTextStyle()
                        .setSummaryText(summary)
                        .bigText(content))
                .setGroup(group)
                .setAutoCancel(true);
    }

    public static NotificationCompat.Builder buildSummaryNotification(Context context, NotificationManagerCompat notificationManager,
                                                                      String title, String content, String channelId,
                                                                      String channelName, String group, int color) {
        NotificationChannelCompat channel =
                new NotificationChannelCompat.Builder(channelId, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                        .setName(channelName)
                        .build();
        notificationManager.createNotificationChannel(channel);

        return new NotificationCompat.Builder(context, channelId)
                .setContentTitle(title)
                //set content text to support devices running API level < 24
                .setContentText(content)
                .setSmallIcon(R.drawable.ic_notification)
                .setColor(color)
                .setGroup(group)
                .setGroupSummary(true)
                .setAutoCancel(true);
    }

    public static NotificationManagerCompat getNotificationManager(Context context) {
        return NotificationManagerCompat.from(context);
    }

    public static String getAccountGroupName(String accountName) {
        return GROUP_USER_BASE + accountName;
    }

    public static int getSummaryIdUnreadMessage(int accountIndex) {
        return SUMMARY_BASE_ID_UNREAD_MESSAGE + accountIndex * 1000;
    }

    public static int getNotificationIdUnreadMessage(int accountIndex, int messageIndex) {
        return NOTIFICATION_BASE_ID_UNREAD_MESSAGE + accountIndex * 1000 + messageIndex;
    }

    /**
     * Posts a notification only when the app is actually allowed to. With POST_NOTIFICATIONS denied
     * the system drops the notification anyway; doing the check here makes that explicit and gives
     * lint's MissingPermission a guard in the same method as the notify() call, instead of one
     * suppression per caller.
     *
     * <p>The SDK_INT half of the condition is load-bearing: POST_NOTIFICATIONS does not exist before
     * API 33, where checkSelfPermission reports it denied, so testing the permission unconditionally
     * would silence every service notification on Android 12 and below.
     */
    public static void notifyIfPermitted(Context context, NotificationManagerCompat manager, int id,
                                         Notification notification) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                        == PackageManager.PERMISSION_GRANTED) {
            manager.notify(id, notification);
        }
    }
}
