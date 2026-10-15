package com.agendaveterinaria.app

import android.content.ContentValues
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.json.JSONObject

@Composable
fun SettingsScreen(state: VetState, onRequestNotifications: () -> Unit) {
    val context = LocalContext.current
    val s = state.settings
    var clinic by remember { mutableStateOf(s.clinicName) }
    var vet by remember { mutableStateOf(s.vetName) }
    var license by remember { mutableStateOf(s.license) }
    var phone by remember { mutableStateOf(s.phone) }
    var address by remember { mutableStateOf(s.address) }
    var currency by remember { mutableStateOf(s.currency) }
    var daysBefore by remember { mutableStateOf(s.daysBefore) }
    var hour by remember { mutableStateOf(s.notifyHour) }
    var pendingImport by remember { mutableStateOf<VetData?>(null) }

    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: error("No se pudo leer")
            val o = JSONObject(text)
            if (o.optString("format") != VetStore.FORMAT) error("Ese archivo no es una copia de Agenda Veterinaria")
            VetStore.fromJson(o)
        }.onSuccess { pendingImport = it }.onFailure { toast(context, it.message ?: "No se pudo leer la copia") }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Ajustes", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        SectionTitle("Datos del consultorio (salen en el carnet)")
        OutlinedTextField(clinic, { clinic = it }, label = { Text("Nombre del consultorio") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(vet, { vet = it }, label = { Text("Médico veterinario") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(license, { license = it }, label = { Text("Colegiatura / registro") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(phone, { phone = it }, label = { Text("Teléfono del consultorio") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(address, { address = it }, label = { Text("Dirección") }, modifier = Modifier.fillMaxWidth())
        Text("Moneda", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("$", "Bs", "€").forEach { c -> FilterChip(selected = currency == c, onClick = { currency = c }, label = { Text(c) }) }
        }
        SectionTitle("Recordatorios de vacunas")
        Text("Avisarme antes de la próxima dosis (para escribirle al dueño):", style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(0 to "El mismo día", 1 to "1 día", 2 to "2 días", 3 to "3 días", 7 to "1 semana").take(4).forEach { (d, l) ->
                FilterChip(selected = daysBefore == d, onClick = { daysBefore = d }, label = { Text(l, maxLines = 1) })
            }
        }
        Text("Hora del aviso:", style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(7, 8, 9, 12, 17).forEach { h ->
                FilterChip(selected = hour == h, onClick = { hour = h }, label = { Text(if (h <= 12) "$h am".replace("12 am", "12 m") else "${h - 12} pm") })
            }
        }
        Button(onClick = {
            state.saveSettings(ClinicSettings(clinic.trim(), vet.trim(), license.trim(), phone.trim(), address.trim(), currency, daysBefore, hour))
            toast(context, "Ajustes guardados")
        }, modifier = Modifier.fillMaxWidth()) { Text("Guardar ajustes") }

        SectionTitle("Permisos")
        val notif = ReminderScheduler.notificationsAllowed(context)
        Text(if (notif) "✓ Notificaciones activadas" else "Las notificaciones están apagadas: no verás los recordatorios.", color = if (notif) VetGreen else VetDanger)
        if (!notif) OutlinedButton(onClick = onRequestNotifications, modifier = Modifier.fillMaxWidth()) { Text("Activar notificaciones") }

        SectionTitle("Copia de seguridad")
        Text(
            "Todo se guarda solo en este teléfono. Haz una copia de vez en cuando y guárdala en Drive o envíatela por WhatsApp.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                runCatching { exportBackup(context, state.data) }
                    .onSuccess { toast(context, "Copia guardada en $it") }
                    .onFailure { toast(context, "No se pudo guardar: ${it.message}") }
            }, modifier = Modifier.weight(1f)) { Text("Hacer copia") }
            OutlinedButton(onClick = { runCatching { importer.launch(arrayOf("application/json", "text/plain", "*/*")) } }, modifier = Modifier.weight(1f)) { Text("Restaurar") }
        }
        val d = state.data
        Text(
            "${d.patients.size} pacientes · ${d.owners.size} dueños · ${d.visits.size} consultas · ${d.doses.size} vacunas/desparasitaciones",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.size(80.dp))
    }

    pendingImport?.let { imp ->
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text("¿Restaurar esta copia?") },
            text = { Text("Trae ${imp.patients.size} pacientes y ${imp.owners.size} dueños. Reemplaza todo lo que hay ahora en este teléfono.") },
            confirmButton = { TextButton(onClick = { state.replaceAll(imp); pendingImport = null; toast(context, "Copia restaurada") }) { Text("Restaurar") } },
            dismissButton = { TextButton(onClick = { pendingImport = null }) { Text("Cancelar") } },
        )
    }
}

/** Guarda la copia en Descargas › AgendaVeterinaria. */
fun exportBackup(context: android.content.Context, data: VetData): String {
    val name = "agenda-veterinaria-" + fmt(System.currentTimeMillis(), "yyyyMMdd-HHmm") + ".json"
    val bytes = VetStore.toJson(data).toString(2).toByteArray()
    if (Build.VERSION.SDK_INT >= 29) {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/AgendaVeterinaria")
        }
        val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: error("No se pudo crear el archivo")
        context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
        return "Descargas › AgendaVeterinaria › $name"
    }
    val dir = java.io.File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "copias").apply { mkdirs() }
    val f = java.io.File(dir, name); f.writeBytes(bytes)
    return f.absolutePath
}
