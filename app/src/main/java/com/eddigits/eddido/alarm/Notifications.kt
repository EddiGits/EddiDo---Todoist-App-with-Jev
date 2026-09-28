package com.eddigits.eddido.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import androidx.core.app.NotificationCompat
import com.eddigits.eddido.MainActivity
import com.eddigits.eddido.R
import com.eddigits.eddido.model.ReminderKind
import com.eddigits.eddido.model.Task
import java.time.format.DateTimeFormatter

object Notifications {
    private const val CH_REMINDERS = "reminders"
    private const val CH_ALARMS = "alarms_v1"

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_REMINDERS, "Reminders", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Task reminders"
                enableVibration(true)
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_ALARMS, "Alarms", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Ringing alarms and timers"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 800, 600, 800, 600)
                setBypassDnd(true)
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                        ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
            },
        )
    }

    fun show(context: Context, task: Task) {
        val nm = context.getSystemService(NotificationManager::class.java)
        val code = ReminderScheduler.requestCode(task.id)
        val open = PendingIntent.getActivity(
            context, code, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val done = action(context, task.id, ActionReceiver.ACTION_DONE)
        val snooze = action(context, task.id, ActionReceiver.ACTION_SNOOZE)
        val time = task.due?.format(DateTimeFormatter.ofPattern("h:mm a")) ?: ""
        val text = listOfNotNull(time.ifEmpty { null }, task.project.takeIf { it != Task.INBOX }, task.description.ifBlank { null })
            .joinToString(" · ")

        val builder = if (task.reminder == ReminderKind.ALARM) {
            val full = PendingIntent.getActivity(
                context, code,
                Intent(context, AlarmActivity::class.java)
                    .putExtra(ReminderScheduler.EXTRA_TASK_ID, task.id)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            NotificationCompat.Builder(context, CH_ALARMS)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setFullScreenIntent(full, true)
                .setContentIntent(full)
                .setOngoing(true)
                .setTimeoutAfter(10 * 60_000L)
                .addAction(0, "Snooze 10 min", snooze)
                .addAction(0, if (task.recurrence != null) "Dismiss" else "Dismiss & done", done)
        } else {
            NotificationCompat.Builder(context, CH_REMINDERS)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setContentIntent(open)
                .setAutoCancel(true)
                .addAction(0, "Done", done)
                .addAction(0, "Snooze 10 min", snooze)
        }
        val n = builder
            .setSmallIcon(R.drawable.ic_stat_task)
            .setContentTitle(task.title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
        if (task.reminder == ReminderKind.ALARM) n.flags = n.flags or Notification.FLAG_INSISTENT
        runCatching { nm.notify(code, n) }
    }

    fun dismiss(context: Context, id: Long) {
        context.getSystemService(NotificationManager::class.java).cancel(ReminderScheduler.requestCode(id))
    }

    private fun action(context: Context, id: Long, action: String): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            ReminderScheduler.requestCode(id) + action.hashCode(),
            Intent(context, ActionReceiver::class.java).setAction(action).putExtra(ReminderScheduler.EXTRA_TASK_ID, id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}
