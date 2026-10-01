package ml.docilealligator.infinityforreddit.reminder

import android.app.AlarmManager
import android.app.Application
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.room.withTransaction
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import ml.docilealligator.infinityforreddit.R
import ml.docilealligator.infinityforreddit.RedditDataRoomDatabase
import ml.docilealligator.infinityforreddit.activities.ViewPostDetailActivity
import ml.docilealligator.infinityforreddit.broadcastreceivers.ReminderAlarmReceiver
import ml.docilealligator.infinityforreddit.customtheme.CustomThemeWrapper
import ml.docilealligator.infinityforreddit.utils.NotificationUtils
import java.util.Random

class ReminderManager(
    private val applicationContext: Application,
    private val redditRoomDatabase: RedditDataRoomDatabase,
    private val alarmManager: AlarmManager,
    private val customThemeWrapper: CustomThemeWrapper
) {
    /**
     * Saves [reminder] and sets its alarm, unless another reminder for the same post or comment is
     * already set for the same time. That one is returned instead, and nothing is saved.
     *
     * post_id, comment_id and reminder_time are the table's primary key and the insert replaces on
     * conflict, so the duplicate used to overwrite the existing reminder while leaving its alarm
     * armed under its own request code, which then fired for a reminder no longer listed.
     */
    suspend fun setReminder(reminder: Reminder): Reminder? {
        val existingReminder = redditRoomDatabase.withTransaction {
            findReminderAtSameTime(reminder) ?: run {
                redditRoomDatabase.reminderDao().insert(reminder)
                null
            }
        }
        if (existingReminder == null) {
            setAlarm(reminder)
        }
        return existingReminder
    }

    fun setAlarm(reminder: Reminder) {
        val alarmIntent = Intent(
            applicationContext,
            ReminderAlarmReceiver::class.java
        ).let { intent ->
            intent.putExtra(ReminderAlarmReceiver.EXTRA_REMINDER, reminder)
            PendingIntent.getBroadcast(
                applicationContext, reminder.createdAt.toInt(), intent,
                PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        alarmManager.set(
            AlarmManager.RTC_WAKEUP,
            reminder.reminderTime,
            alarmIntent
        )
    }

    fun checkAndSetAllAlarms() {
        MainScope().launch {
            checkAndSetAllAlarmsSync()
        }
    }

    suspend fun checkAndSetAllAlarmsSync() {
        for (reminder in redditRoomDatabase.reminderDao().getAllReminders()) {
            if (PendingIntent.getBroadcast(applicationContext, reminder.createdAt.toInt(), Intent(
                    applicationContext,
                    ReminderAlarmReceiver::class.java
                ), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE) == null) {
                if (System.currentTimeMillis() >= reminder.reminderTime) {
                    sendNotification(applicationContext, customThemeWrapper, reminder)
                    redditRoomDatabase.reminderDao().deleteReminder(reminder)
                } else {
                    setAlarm(reminder)
                }
            }
        }
    }

    fun getAllRemindersFlow(): Flow<List<Reminder>> {
        return redditRoomDatabase.reminderDao().getAllRemindersFlow()
    }

    /**
     * Moves [reminder] to [newReminderTime], unless another reminder for the same post or comment
     * is already set for that time. That one is returned instead and [reminder] is left as it was,
     * for the reason given on [setReminder].
     */
    suspend fun updateReminder(reminder: Reminder, newReminderTime: Long): Reminder? {
        if (newReminderTime == reminder.reminderTime) {
            return null
        }
        val newReminder = reminder.copy(
            reminderTime = newReminderTime
        )
        val existingReminder = redditRoomDatabase.withTransaction {
            findReminderAtSameTime(newReminder) ?: run {
                redditRoomDatabase.reminderDao().deleteReminder(reminder)
                redditRoomDatabase.reminderDao().insert(newReminder)
                null
            }
        }
        if (existingReminder == null) {
            // Same request code as the old alarm, which this replaces.
            setAlarm(newReminder)
        }
        return existingReminder
    }

    private suspend fun findReminderAtSameTime(reminder: Reminder): Reminder? =
        redditRoomDatabase.reminderDao().getReminder(reminder.postId, reminder.commentId, reminder.reminderTime)

    suspend fun deleteReminder(reminder: Reminder) {
        redditRoomDatabase.reminderDao().deleteReminder(reminder)
        PendingIntent.getBroadcast(applicationContext, reminder.createdAt.toInt(), Intent(
            applicationContext,
            ReminderAlarmReceiver::class.java
        ), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT).cancel();
    }

    companion object {
        fun sendNotification(
            context: Context,
            customThemeWrapper: CustomThemeWrapper,
            reminder: Reminder
        ) {
            val notificationManager = NotificationUtils.getNotificationManager(context)
            val builder = NotificationUtils.buildNotification(
                notificationManager,
                context,
                context.getString(R.string.reminder),
                reminder.content,
                context.getString(if (reminder.commentId.isNotEmpty()) R.string.comment else R.string.post),
                NotificationUtils.CHANNEL_ID_NEW_MESSAGES,
                NotificationUtils.CHANNEL_NEW_MESSAGES,
                NotificationUtils.GROUP_REMINDER, customThemeWrapper.colorPrimaryLightTheme
            )

            val intent = Intent(context, ViewPostDetailActivity::class.java)
            intent.putExtra(ViewPostDetailActivity.EXTRA_POST_ID, reminder.postId)
            intent.putExtra(ViewPostDetailActivity.EXTRA_SINGLE_COMMENT_ID, reminder.commentId)
            val pendingIntent =
                PendingIntent.getActivity(context, reminder.createdAt.toInt(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            builder.setContentIntent(pendingIntent)

            try {
                notificationManager.notify(
                    NotificationUtils.REMINDER_NOTIFICATION_ID + Random().nextInt(10000), builder.build()
                )
            } catch (e: SecurityException) {
                e.printStackTrace()
            }
        }
    }
}