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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.automirrored.rounded.Send
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
import androidx.compose.ui.platform.LocalContext
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

    companion object {
        /** Desde la notificación: enviar el WhatsApp de una vez, sin mostrar pantallas. */
        const val EXTRA_SEND = "send_whatsapp"
    }

    /**
     * Envío a grupos desde la notificación: primero se muestra la pantalla con "ENVIAR A <grupo>"
     * para confirmar; al tocarlo, la app abre el grupo y envía (con el permiso de accesibilidad).
     */
    private fun confirmGroup(intent: Intent): Boolean {
        val task = TaskStore.find(this, intent.getLongExtra(ReminderScheduler.EXTRA_TASK_ID, -1L)) ?: return false
        if (!task.toGroup || task.groups.isEmpty()) return false
        AlarmSoundService.stop(this)
        return true
    }

    /** Apaga la alarma, marca el aviso como hecho y abre WhatsApp con el mensaje escrito. */
    private fun sendNow(id: Long) {
        val task = TaskStore.find(this, id)
        if (task != null) {
            AlarmActions.stopAndComplete(this, task.id, quiet = true)
            NotificationHelper.cancelAllForTask(this, task.id)
            WhatsApp.send(this, task)
        }
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent.getBooleanExtra(EXTRA_SEND, false) && !confirmGroup(intent)) {
            sendNow(intent.getLongExtra(ReminderScheduler.EXTRA_TASK_ID, -1L))
            return
        }
        if (android.os.Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        taskId.longValue = intent.getLongExtra(ReminderScheduler.EXTRA_TASK_ID, -1L)
        // Abierta de nuevo (por ejemplo, desde el aviso perdido): no se cierra sola
        if (AlarmSoundService.lastTimedOut == taskId.longValue) AlarmSoundService.lastTimedOut = -1L
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
                        onWhatsApp = { sendNow(task.id) },
                        onCall = {
                            AlarmActions.stopAndComplete(this, task.id)
                            PhoneCall.open(this, task.phone)
                            finish()
                        },
                        onTimeout = { finish() },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(EXTRA_SEND, false) && !confirmGroup(intent)) {
            sendNow(intent.getLongExtra(ReminderScheduler.EXTRA_TASK_ID, -1L))
            return
        }
        taskId.longValue = intent.getLongExtra(ReminderScheduler.EXTRA_TASK_ID, -1L)
        if (AlarmSoundService.lastTimedOut == taskId.longValue) AlarmSoundService.lastTimedOut = -1L
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
    onTimeout: () -> Unit = {},
) {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000); now = System.currentTimeMillis()
            // La alarma se calló sola (5 min sin atenderla): se cierra la pantalla y queda el aviso perdido
            if (AlarmSoundService.lastTimedOut == task.id) { AlarmSoundService.lastTimedOut = -1L; onTimeout() }
        }
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
                if (task.eventMillis > 0) {
                    Spacer(Modifier.height(6.dp))
                    Text("Es a las ${formatDate(task.eventMillis, "h:mm a")}", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
                if (task.message.isNotBlank() && task.message != task.title) {
                    Spacer(Modifier.height(10.dp))
                    Text(task.message, color = Color.White.copy(alpha = 0.9f), textAlign = TextAlign.Center, maxLines = 8)
                }
                val topics = task.topicList
                if (topics.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text("Temas a tratar", color = Color.White, fontWeight = FontWeight.Bold)
                    Text(topics.take(8).joinToString("\n") { "• $it" }, color = Color.White.copy(alpha = 0.9f), textAlign = TextAlign.Start)
                }
            }
            Column(Modifier.fillMaxWidth().padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (task.isWhatsApp && task.toGroup && task.groups.isNotEmpty()) {
                    Text(
                        WhatsApp.groupHint(task.groups),
                        color = Color.White.copy(alpha = 0.85f), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (task.isWhatsApp) {
                    // El mensaje ya está listo: lo principal es ENVIAR
                    Button(
                        onClick = onWhatsApp,
                        modifier = Modifier.fillMaxWidth().height(60.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AgendaGreen, contentColor = Color.White),
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.Send, null, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (task.toGroup && task.groups.isNotEmpty()) "ENVIAR A " + task.groups.joinToString(", ") { it.name }.uppercase() else "ENVIAR",
                            fontWeight = FontWeight.Bold, fontSize = 18.sp, maxLines = 1,
                        )
                    }
                }
                Button(
                    onClick = onStop,
                    modifier = Modifier.fillMaxWidth().height(if (task.isWhatsApp) 52.dp else 56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = AgendaBlueDark),
                ) { Text("APAGAR", fontWeight = FontWeight.Bold, fontSize = if (task.isWhatsApp) 16.sp else 18.sp) }
                // Reuniones y viajes guardados por voz traen "Lugar: ..." → abrir el mapa
                val place = task.message.lineSequence().firstOrNull { it.startsWith("Lugar: ") }?.removePrefix("Lugar: ")?.trim()
                if (!place.isNullOrBlank()) {
                    val ctx = androidx.compose.ui.platform.LocalContext.current
                    FilledTonalButton(
                        onClick = {
                            runCatching {
                                ctx.startActivity(
                                    android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("geo:0,0?q=" + android.net.Uri.encode(place)))
                                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) { Text("Cómo llegar: $place", maxLines = 1) }
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
