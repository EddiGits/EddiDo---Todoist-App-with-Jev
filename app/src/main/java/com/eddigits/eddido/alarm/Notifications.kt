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
    private const val CH_RUNNING = "running"
    private const val FOCUS_ID = 7_000_001
    private const val FOCUS_PHASE_ID = 7_000_002
    private fun timerNotifId(id: Long) = 8_000_000 + (id % 1_000_000).toInt()

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
        nm.createNotificationChannel(
            NotificationChannel(CH_RUNNING, "Running timers", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Live countdown for running timers and focus sessions"
                setShowBadge(false)
            },
        )
    }

    private fun openApp(context: Context, code: Int) = PendingIntent.getActivity(
        context, code, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** A quiet ongoing notification whose clock counts down to [endsAt] by itself. */
    private fun countdown(context: Context, id: Int, title: String, text: String, endsAt: Long?, paused: Boolean) {
        val b = NotificationCompat.Builder(context, CH_RUNNING)
            .setSmallIcon(R.drawable.ic_stat_task)
            .setContentTitle(title)
            .setContentText(if (paused) "Paused · $text" else text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setContentIntent(openApp(context, id))
        if (endsAt != null && !paused) b.setUsesChronometer(true).setChronometerCountDown(true).setWhen(endsAt).setShowWhen(true)
        runCatching { context.getSystemService(NotificationManager::class.java).notify(id, b.build()) }
    }

    fun showTimerRunning(context: Context, t: com.eddigits.eddido.model.TimerItem) =
        countdown(context, timerNotifId(t.id), t.label.ifBlank { "Timer" }, "Timer running", t.endsAt, paused = !t.running)

    fun cancelTimer(context: Context, id: Long) =
        context.getSystemService(NotificationManager::class.java).cancel(timerNotifId(id))

    /** The timer ran out: ring like an alarm, full screen over the lock screen. */
    fun showTimerDone(context: Context, t: com.eddigits.eddido.model.TimerItem) {
        val code = timerNotifId(t.id)
        fun act(action: String) = PendingIntent.getBroadcast(
            context, code + action.hashCode(),
            Intent(context, TimerActionReceiver::class.java).setAction(action).putExtra(TimerActionReceiver.EXTRA_ID, t.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val full = PendingIntent.getActivity(
            context, code,
            Intent(context, AlarmActivity::class.java)
                .putExtra(AlarmActivity.EXTRA_KIND, AlarmActivity.KIND_TIMER)
                .putExtra(AlarmActivity.EXTRA_ID, t.id)
                .putExtra(AlarmActivity.EXTRA_TITLE, t.label.ifBlank { "Time's up" })
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, CH_ALARMS)
            .setSmallIcon(R.drawable.ic_stat_task)
            .setContentTitle(t.label.ifBlank { "Time's up" })
            .setContentText("Timer finished")
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setFullScreenIntent(full, true)
            .setContentIntent(full)
            .setOngoing(true)
            .setTimeoutAfter(10 * 60_000L)
            .addAction(0, "+1 min", act(TimerActionReceiver.ACTION_ADD_MINUTE))
            .addAction(0, "Stop", act(TimerActionReceiver.ACTION_STOP))
            .build()
        n.flags = n.flags or Notification.FLAG_INSISTENT
        runCatching { context.getSystemService(NotificationManager::class.java).notify(code, n) }
    }

    fun showFocusRunning(context: Context, s: com.eddigits.eddido.model.FocusSession) {
        val phase = if (s.phase == com.eddigits.eddido.model.FocusPhase.FOCUS) "Focus" else "Break"
        countdown(
            context, FOCUS_ID, s.label.ifBlank { "Focus" }, "$phase · round ${s.round} of ${s.rounds}",
            s.phaseEndsAt, paused = !s.running,
        )
    }

    fun cancelFocus(context: Context) =
        context.getSystemService(NotificationManager::class.java).cancel(FOCUS_ID)

    /** A focus or break stretch ended: a normal (not insistent) alert. */
    fun showFocusPhase(context: Context, title: String, text: String) {
        val n = NotificationCompat.Builder(context, CH_REMINDERS)
            .setSmallIcon(R.drawable.ic_stat_task)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openApp(context, FOCUS_PHASE_ID))
            .build()
        runCatching { context.getSystemService(NotificationManager::class.java).notify(FOCUS_PHASE_ID, n) }
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
