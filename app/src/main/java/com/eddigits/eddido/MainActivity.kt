package com.eddigits.eddido

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import com.eddigits.eddido.data.TaskRepository
import com.eddigits.eddido.ui.EddiDoApp
import com.eddigits.eddido.ui.dueLabel
import com.eddigits.eddido.ui.theme.EddiDoTheme

class MainActivity : ComponentActivity() {
    private val askNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val repo = TaskRepository.get(this)
        if (Build.VERSION.SDK_INT >= 33) askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        if (savedInstanceState == null) handleShare(intent)
        setContent { EddiDoTheme { EddiDoApp(repo) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShare(intent)
    }

    /** Text shared from another app becomes a task, parsed and sorted like typed text. */
    private fun handleShare(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim().orEmpty()
        if (text.isEmpty()) return
        val t = TaskRepository.get(this).addFromText(text)
        Toast.makeText(this, "Added “${t.title}”" + (t.due?.let { " · " + dueLabel(it, t.hasTime) } ?: ""), Toast.LENGTH_SHORT).show()
    }
}
