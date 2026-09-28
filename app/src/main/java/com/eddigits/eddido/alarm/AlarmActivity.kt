package com.eddigits.eddido.alarm

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eddigits.eddido.data.TaskRepository
import com.eddigits.eddido.ui.theme.EddiDoTheme
import com.eddigits.eddido.ui.theme.Brand
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** Full-screen ringing screen, shown over the lock screen. */
class AlarmActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val id = intent.getLongExtra(ReminderScheduler.EXTRA_TASK_ID, -1)
        val task = TaskRepository.get(this).get(id)
        val title = task?.title ?: "Alarm"
        val subtitle = task?.project?.takeIf { it != "Inbox" }.orEmpty()

        setContent {
            EddiDoTheme {
                Column(
                    Modifier.fillMaxSize().background(Color(0xFF141414)).padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(Icons.Filled.Alarm, null, tint = Brand, modifier = Modifier.size(72.dp))
                    Spacer(Modifier.height(24.dp))
                    Text(
                        LocalTime.now().format(DateTimeFormatter.ofPattern("h:mm a")),
                        color = Color.White, fontSize = 56.sp, fontWeight = FontWeight.Light,
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(title, color = Color.White, fontSize = 26.sp, textAlign = TextAlign.Center, fontWeight = FontWeight.Medium)
                    if (subtitle.isNotEmpty()) Text(subtitle, color = Color(0xFF9E9E9E), fontSize = 16.sp)
                    Spacer(Modifier.height(64.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        OutlinedButton(
                            onClick = { ActionReceiver.handle(this@AlarmActivity, id, ActionReceiver.ACTION_SNOOZE); finish() },
                            modifier = Modifier.weight(1f).height(64.dp),
                        ) { Text("Snooze 10 min", fontSize = 16.sp) }
                        Button(
                            onClick = { ActionReceiver.handle(this@AlarmActivity, id, ActionReceiver.ACTION_DONE); finish() },
                            modifier = Modifier.weight(1f).height(64.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Brand),
                        ) { Text("Dismiss", fontSize = 16.sp, color = Color.White) }
                    }
                }
            }
        }
    }
}
