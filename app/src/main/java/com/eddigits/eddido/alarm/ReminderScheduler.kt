package com.eddigits.eddido.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.eddigits.eddido.MainActivity
import com.eddigits.eddido.model.ReminderKind
import com.eddigits.eddido.model.Task
import com.eddigits.eddido.model.toEpochMillis
import java.time.LocalDateTime

/** Schedules one exact system alarm per task. */
object ReminderScheduler {
    const val EXTRA_TASK_ID = "task_id"
    const val EXTRA_SNOOZE = "snooze"

    fun requestCode(id: Long): Int = (id xor (id ushr 32)).toInt()

    fun schedule(context: Context, task: Task) {
        cancel(context, task.id)
        val at = task.fireAt() ?: return
        if (!at.isAfter(LocalDateTime.now())) return
        scheduleAt(context, task, at)
    }

    /**
     * A snooze uses its own alarm slot so it never replaces the next occurrence
     * of a repeating task.
     */
    fun scheduleAt(context: Context, task: Task, at: LocalDateTime, snooze: Boolean = false) {
        val am = context.getSystemService(AlarmManager::class.java)
        val fire = firePendingIntent(context, task.id, snooze)
        val millis = at.toEpochMillis()
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        when {
            !canExact -> am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, fire)
            task.reminder == ReminderKind.ALARM -> {
                // Alarm-clock alarms are the most reliable and show the alarm icon in the status bar.
                val show = PendingIntent.getActivity(
                    context, requestCode(task.id), Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                am.setAlarmClock(AlarmManager.AlarmClockInfo(millis, show), fire)
            }
            else -> am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, fire)
        }
    }

    fun cancel(context: Context, id: Long, includeSnooze: Boolean = false) {
        val am = context.getSystemService(AlarmManager::class.java)
        am.cancel(firePendingIntent(context, id, snooze = false))
        if (includeSnooze) am.cancel(firePendingIntent(context, id, snooze = true))
    }

    private fun firePendingIntent(context: Context, id: Long, snooze: Boolean): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            requestCode(id) + if (snooze) 1 else 0,
            Intent(context, ReminderReceiver::class.java)
                .setAction(if (snooze) "snooze" else "fire")
                .putExtra(EXTRA_TASK_ID, id)
                .putExtra(EXTRA_SNOOZE, snooze),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}
