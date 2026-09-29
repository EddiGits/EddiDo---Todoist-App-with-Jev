package com.eddigits.eddido.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.eddigits.eddido.MainActivity
import com.eddigits.eddido.data.AppStore
import com.eddigits.eddido.model.FocusPhase

/** Exact alarms for timers and focus sessions, keyed by "timer:<id>" or "focus". */
object ClockAlarms {
    const val EXTRA_KEY = "clock_key"

    fun schedule(context: Context, key: String, atMillis: Long) {
        val am = context.getSystemService(AlarmManager::class.java)
        val fire = pending(context, key)
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        if (!canExact) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, fire); return
        }
        val show = PendingIntent.getActivity(
            context, key.hashCode(), Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        am.setAlarmClock(AlarmManager.AlarmClockInfo(atMillis, show), fire)
    }

    fun cancel(context: Context, key: String) {
        context.getSystemService(AlarmManager::class.java).cancel(pending(context, key))
    }

    private fun pending(context: Context, key: String): PendingIntent = PendingIntent.getBroadcast(
        context, key.hashCode(),
        Intent(context, ClockReceiver::class.java).setAction("clock:$key").putExtra(EXTRA_KEY, key),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}

/** A timer ran out, or a focus/break stretch ended. */
class ClockReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val key = intent.getStringExtra(ClockAlarms.EXTRA_KEY) ?: return
        val store = AppStore.get(context)
        when {
            key.startsWith("timer:") -> {
                val id = key.removePrefix("timer:").toLongOrNull() ?: return
                store.onTimerDone(id)?.let { Notifications.showTimerDone(context, it) }
            }
            key == AppStore.FOCUS_KEY -> {
                val next = store.onFocusPhaseEnd() ?: return
                val (title, text) = when {
                    next.done -> "Focus session complete" to "${next.rounds} rounds done. Nice work!"
                    next.phase == FocusPhase.BREAK -> "Break time" to "${next.breakMin} min break, then round ${next.round + 1} of ${next.rounds}"
                    else -> "Back to focus" to "Round ${next.round} of ${next.rounds}" + if (next.label.isNotBlank()) " · ${next.label}" else ""
                }
                Notifications.showFocusPhase(context, title, text)
            }
        }
    }
}

/** "+1 min" / "Stop" on a finished timer's notification or ringing screen. */
class TimerActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        handle(context, intent.getLongExtra(EXTRA_ID, -1), intent.action)
    }

    companion object {
        const val EXTRA_ID = "timer_id"
        const val ACTION_ADD_MINUTE = "com.eddigits.eddido.TIMER_ADD_MINUTE"
        const val ACTION_STOP = "com.eddigits.eddido.TIMER_STOP"

        fun handle(context: Context, id: Long, action: String?) {
            val store = AppStore.get(context)
            Notifications.cancelTimer(context, id)
            when (action) {
                ACTION_ADD_MINUTE -> store.addMinute(id)
                ACTION_STOP -> store.resetTimer(id)
            }
        }
    }
}
