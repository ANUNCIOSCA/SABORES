package com.agendawa.app

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private const val SETUP_PREFS = "group_setup"

private fun stepDone(context: Context, step: Int) =
    context.getSharedPreferences(SETUP_PREFS, Context.MODE_PRIVATE).getBoolean("step$step", false)

private fun markStep(context: Context, step: Int) =
    context.getSharedPreferences(SETUP_PREFS, Context.MODE_PRIVATE).edit().putBoolean("step$step", true).apply()

/**
 * Asistente de 3 pasos para activar "Envío directo a grupos". Android bloquea el permiso de
 * accesibilidad en apps instaladas desde un archivo: hay que intentar activarlo una vez, permitir
 * la configuración restringida en la ficha de la app y luego activarlo. Cada botón abre la pantalla justa.
 */
@Composable
fun GroupSetupDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    val enabled = remember(refresh) { GroupAutomation.isEnabled(context) }
    var s1 by remember { mutableStateOf(stepDone(context, 1)) }
    var s2 by remember { mutableStateOf(stepDone(context, 2)) }
    // Al volver de los ajustes del teléfono se ve si ya quedó activo
    LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(1200); refresh++ } }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = WideDialogProperties,
        modifier = Modifier.fillMaxWidth().dialogSafePadding().padding(16.dp),
        title = { Text(if (enabled) "¡Envío directo activado!" else "Activar envío directo a grupos") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (enabled) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.CheckCircle, null, tint = AgendaGreen, modifier = Modifier.size(32.dp))
                        Spacer(Modifier.width(10.dp))
                        Text("Listo. A la hora del aviso confirmas \"Enviar a <grupo>\" y la app entra al grupo y lo envía.")
                    }
                    return@Column
                }
                Text(
                    "Se hace una sola vez (menos de un minuto). Android lo pide porque la app no viene de Play Store.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SetupStep(
                    number = 1, done = s1,
                    title = "Toca la opción gris y acepta",
                    detail = "Se abre Accesibilidad. Toca \"Agenda WA: envío directo a grupos\" (sale gris) y en el aviso toca Aceptar. Luego vuelve aquí.",
                    button = "Abrir Accesibilidad",
                ) { markStep(context, 1); s1 = true; GroupAutomation.openSettings(context) }
                SetupStep(
                    number = 2, done = s2,
                    title = "Desbloquear",
                    detail = "Se abre la ficha de Agenda WA. Toca los 3 puntos ⋮ arriba a la derecha › \"Permitir configuración restringida\" y confirma con tu PIN o huella. Luego vuelve aquí.",
                    button = "Abrir ficha de la app",
                ) { markStep(context, 2); s2 = true; AlarmCapabilities.openAppDetails(context) }
                SetupStep(
                    number = 3, done = enabled,
                    title = "Activar",
                    detail = "Se abre Accesibilidad. Toca \"Agenda WA: envío directo a grupos\", activa el interruptor y toca Permitir.",
                    button = "Abrir Accesibilidad",
                ) { GroupAutomation.openSettings(context) }
                Text(
                    "Si en el paso 2 no ves \"Permitir configuración restringida\", repite el paso 1 y vuelve a intentarlo.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(if (enabled) "Listo" else "Cerrar") } },
    )
}

@Composable
private fun SetupStep(number: Int, done: Boolean, title: String, detail: String, button: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, if (done) AgendaGreen else MaterialTheme.colorScheme.outline),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (done) Icon(Icons.Rounded.CheckCircle, null, tint = AgendaGreen, modifier = Modifier.size(20.dp))
                else Text("$number.", fontWeight = FontWeight.Bold, color = AgendaBlue)
                Spacer(Modifier.width(8.dp))
                Text(title, fontWeight = FontWeight.Bold)
            }
            Text(detail, style = MaterialTheme.typography.bodySmall)
            if (done) OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Text("$button otra vez") }
            else Button(
                onClick = onClick, modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = AgendaGreen, contentColor = Color.White),
            ) { Text(button) }
        }
    }
}
