@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.agendawa.app

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

class AlarmActivity : ComponentActivity() {
    private val taskId = mutableLongStateOf(-1L)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (android.os.Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        taskId.longValue = intent.getLongExtra(ReminderScheduler.EXTRA_TASK_ID, -1L)
        setContent {
            AgendaWaTheme {
                val task = remember(taskId.longValue) { TaskStore.find(this, taskId.longValue) }
                if (task == null) {
                    LaunchedEffect(Unit) { finish() }
                } else {
                    AlarmScreen(
                        task = task,
                        name = ProfileStore.name(this),
                        onStop = { AlarmActions.stopAndComplete(this, task.id); finish() },
                        onSnooze = { AlarmActions.snooze(this, task.id, 5); finish() },
                        onDelete = { AlarmActions.delete(this, task.id); finish() },
                        onWhatsApp = {
                            AlarmActions.stopAndComplete(this, task.id)
                            WhatsApp.open(this, task.phone, task.message.ifBlank { task.title })
                            finish()
                        },
                        onCall = {
                            AlarmActions.stopAndComplete(this, task.id)
                            PhoneCall.open(this, task.phone)
                            finish()
                        },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        taskId.longValue = intent.getLongExtra(ReminderScheduler.EXTRA_TASK_ID, -1L)
    }
}

@Composable
private fun AlarmScreen(
    task: AgendaTask,
    name: String,
    onStop: () -> Unit,
    onSnooze: () -> Unit,
    onDelete: () -> Unit,
    onWhatsApp: () -> Unit,
    onCall: () -> Unit,
) {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) { delay(1000); now = System.currentTimeMillis() }
    }
    val headline = (if (name.isBlank()) "" else "$name, ") + (if (task.isWhatsApp) "tienes un WhatsApp pendiente" else "tienes un recordatorio")
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(AgendaBlueDark, AgendaBlue)))
            .systemBarsPadding()
            .padding(24.dp),
    ) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(24.dp))
                Text(formatDate(now, "h:mm a"), color = Color.White, fontSize = 56.sp, fontWeight = FontWeight.Bold)
                Text(formatDate(now, "EEEE d 'de' MMMM").replaceFirstChar { it.uppercase() }, color = Color.White.copy(alpha = 0.8f))
                Spacer(Modifier.height(32.dp))
                Box(Modifier.size(88.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                    Icon(if (task.isWhatsApp) Icons.AutoMirrored.Rounded.Chat else Icons.Rounded.Alarm, null, tint = Color.White, modifier = Modifier.size(48.dp))
                }
                Spacer(Modifier.height(20.dp))
                Text(headline.replaceFirstChar { it.uppercase() }, color = Color.White.copy(alpha = 0.85f), textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                Text(task.title, color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                if (task.message.isNotBlank() && task.message != task.title) {
                    Spacer(Modifier.height(10.dp))
                    Text(task.message, color = Color.White.copy(alpha = 0.9f), textAlign = TextAlign.Center, maxLines = 8)
                }
            }
            Column(Modifier.fillMaxWidth().padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onStop,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = AgendaBlueDark),
                ) { Text("APAGAR", fontWeight = FontWeight.Bold, fontSize = 18.sp) }
                if (task.isWhatsApp) {
                    Button(
                        onClick = onWhatsApp,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AgendaGreen, contentColor = Color.White),
                    ) { Text("Abrir WhatsApp") }
                }
                if (PhoneCall.isCall(task)) {
                    Button(
                        onClick = onCall,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AgendaGreen, contentColor = Color.White),
                    ) { Text("Llamar ahora") }
                }
                FilledTonalButton(onClick = onSnooze, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Recordarme en 5 min") }
                OutlinedButton(
                    onClick = onDelete,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                ) { Text("Eliminar aviso") }
            }
        }
    }
}
