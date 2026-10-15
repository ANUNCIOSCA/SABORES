package com.agendaveterinaria.app

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private sealed interface Route {
    data object Tabs : Route
    data class PatientR(val id: Long) : Route
    data class OwnerR(val id: Long) : Route
}

class MainActivity : ComponentActivity() {
    companion object { const val EXTRA_PATIENT = "patient_id" }

    private val openPatient = mutableStateOf(0L)
    private val sendReminder = mutableStateOf(0L)
    private lateinit var state: VetState

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        state = VetState(applicationContext)
        ReminderScheduler.createChannel(this)
        handle(intent)
        setContent { VetTheme { VetApp(state, openPatient, sendReminder) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handle(intent)
    }

    override fun onResume() {
        super.onResume()
        // Las notificaciones pueden marcar recordatorios como hechos: se relee lo guardado
        state.reload()
    }

    private fun handle(intent: Intent?) {
        intent ?: return
        val pid = intent.getLongExtra(EXTRA_PATIENT, 0L)
        if (pid != 0L) openPatient.value = pid
        if (intent.action == ReminderActionReceiver.ACTION_WHATSAPP) {
            sendReminder.value = intent.getLongExtra(ReminderScheduler.EXTRA_ID, 0L)
        }
    }
}

@Composable
private fun VetApp(state: VetState, openPatient: MutableState<Long>, sendReminder: MutableState<Long>) {
    val context = LocalContext.current
    var tab by remember { mutableIntStateOf(0) }
    var route by remember { mutableStateOf<Route>(Route.Tabs) }
    var newPatient by remember { mutableStateOf(false) }
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val askNotifications = { if (Build.VERSION.SDK_INT >= 33) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS) }

    LaunchedEffect(Unit) {
        if (!ReminderScheduler.notificationsAllowed(context)) askNotifications()
        ReminderScheduler.rescheduleAll(context)
    }
    LaunchedEffect(openPatient.value) {
        val id = openPatient.value
        if (id != 0L) { route = Route.PatientR(id); openPatient.value = 0L }
    }
    // Botón "WhatsApp al dueño" de la notificación
    LaunchedEffect(sendReminder.value) {
        val id = sendReminder.value
        if (id != 0L) {
            sendReminder.value = 0L
            state.reload()
            state.data.reminders.firstOrNull { it.id == id }?.let { r ->
                ReminderScheduler.cancel(context, r.id)
                sendReminderWhatsApp(context, state, r)
            }
        }
    }

    BackHandler(enabled = route != Route.Tabs || tab != 0) {
        if (route != Route.Tabs) route = Route.Tabs else tab = 0
    }

    val openP: (Long) -> Unit = { route = Route.PatientR(it) }
    val openO: (Long) -> Unit = { route = Route.OwnerR(it) }

    when (val r = route) {
        is Route.PatientR -> PatientDetail(state, r.id, onBack = { route = Route.Tabs }, onOpenOwner = openO)
        is Route.OwnerR -> OwnerDetail(state, r.id, onBack = { route = Route.Tabs }, onOpenPatient = openP)
        Route.Tabs -> Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                NavigationBar(containerColor = Color.White) {
                    listOf(
                        Icons.Rounded.Today to "Hoy", Icons.Rounded.Pets to "Pacientes", Icons.Rounded.Calculate to "Calcular",
                        Icons.Rounded.AccountBalanceWallet to "Cuentas", Icons.Rounded.Settings to "Ajustes",
                    ).forEachIndexed { i, (icon, label) ->
                        NavigationBarItem(selected = tab == i, onClick = { tab = i }, icon = { Icon(icon, null) }, label = { Text(label, maxLines = 1) })
                    }
                }
            },
            floatingActionButton = {
                if (tab <= 1) FloatingActionButton(onClick = { newPatient = true }, containerColor = VetGreen, contentColor = Color.White) {
                    Icon(Icons.Rounded.Add, "Nuevo paciente")
                }
            },
        ) { pad ->
            Box(Modifier.fillMaxSize().padding(pad)) {
                when (tab) {
                    0 -> TodayScreen(state, onOpenPatient = openP, onNewPatient = { newPatient = true })
                    1 -> PatientsScreen(state, onOpenPatient = openP, onOpenOwner = openO)
                    2 -> CalcScreen(state)
                    3 -> AccountsScreen(state)
                    else -> SettingsScreen(state, onRequestNotifications = askNotifications)
                }
            }
        }
    }

    if (newPatient) PatientForm(state, null, onDismiss = { newPatient = false }, onSave = { p -> newPatient = false; route = Route.PatientR(p.id) })
}
