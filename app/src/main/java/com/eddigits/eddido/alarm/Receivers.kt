package com.eddigits.eddido.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.eddigits.eddido.data.TaskRepository

/** Fired by AlarmManager when a task is due. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(ReminderScheduler.EXTRA_TASK_ID, -1)
        val repo = TaskRepository.get(context)
        val task = repo.get(id) ?: return
        if (task.completed) return
        Notifications.show(context, task)
        // A repeating task rolls to its next occurrence as soon as it rings, so tomorrow's
        // alarm is armed even if this one is never tapped. A snooze is a replay, not a new occurrence.
        val snooze = intent.getBooleanExtra(ReminderScheduler.EXTRA_SNOOZE, false)
        if (task.recurrence != null && !snooze) repo.complete(id)
    }
}

/** Done / Snooze buttons on the notification. */
class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(ReminderScheduler.EXTRA_TASK_ID, -1)
        handle(context, id, intent.action)
    }

    companion object {
        const val ACTION_DONE = "com.eddigits.eddido.DONE"
        const val ACTION_SNOOZE = "com.eddigits.eddido.SNOOZE"

        fun handle(context: Context, id: Long, action: String?) {
            val repo = TaskRepository.get(context)
            Notifications.dismiss(context, id)
            val task = repo.get(id) ?: return
            when (action) {
                // Repeating tasks already moved on when they rang; only one-offs get ticked off.
                ACTION_DONE -> if (task.recurrence == null) repo.complete(id)
                ACTION_SNOOZE -> repo.snooze(id, 10)
            }
        }
    }
}

/** Alarms are wiped on reboot, app update and clock changes; put them back. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        TaskRepository.get(context).rescheduleAll()
        com.eddigits.eddido.data.AppStore.get(context).rearm()
    }
}
