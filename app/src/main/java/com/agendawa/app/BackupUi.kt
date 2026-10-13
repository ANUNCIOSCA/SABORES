@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.agendawa.app

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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

@Composable
fun BackupDialog(state: AppState, onDismiss: () -> Unit, onMessage: (String) -> Unit) {
    val context = LocalContext.current
    var confirmRestore by remember { mutableStateOf(false) }
    val stamp = formatDate(System.currentTimeMillis(), "yyyy-MM-dd_HH-mm")

    val saveJson = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { it.write(BackupManager.exportJson(context).toByteArray()) }
                    ?: error("No se pudo abrir el archivo de destino")
            }.onSuccess { onMessage("Copia completa guardada correctamente") }
                .onFailure { onMessage("No se pudo guardar la copia: ${it.message}") }
        }
    }
    val saveMarkdown = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { it.write(BackupManager.readableMarkdown(context).toByteArray()) }
                    ?: error("No se pudo abrir el archivo de destino")
            }.onSuccess { onMessage("Archivo Markdown guardado correctamente") }
                .onFailure { onMessage("No se pudo crear el archivo: ${it.message}") }
        }
    }
    val savePdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { PdfTextWriter.write(BackupManager.readableMarkdown(context), it) }
                    ?: error("No se pudo abrir el archivo de destino")
            }.onSuccess { onMessage("PDF guardado correctamente") }
                .onFailure { onMessage("No se pudo crear el PDF: ${it.message}") }
        }
    }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
                    ?: error("No se pudo leer el archivo")
                BackupManager.restore(context, text)
            }.onSuccess { s ->
                state.reloadAll()
                onMessage("Restaurados ${s.importedTasks} recordatorios · ${s.importedNotes} notas · ${s.importedClients} clientes · ${s.importedFinanceEntries} movimientos · ${s.importedExpenses} gastos")
                onDismiss()
            }.onFailure { onMessage("No se pudo restaurar: ${it.message}") }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Copia de seguridad") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Guarda recordatorios, mensajes, ideas, notas, clientes, finanzas y Mis Gastos fuera del teléfono para recuperarlos si cambias o pierdes el equipo.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        Text("Contenido actual", fontWeight = FontWeight.SemiBold)
                        Text(
                            "${state.tasks.size} recordatorios · ${state.notes.size} notas · ${state.clients.size} clientes · ${state.finance.size} movimientos · ${state.expenses.size} gastos/ingresos",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                Text("Copia completa JSON", fontWeight = FontWeight.Bold)
                Text("Es el archivo restaurable. Guárdalo en Google Drive, OneDrive, memoria SD o computadora.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = { saveJson.launch("AgendaWA-respaldo-$stamp.json") }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Save, null); Spacer(Modifier.width(6.dp)); Text("Guardar copia completa")
                }
                OutlinedButton(onClick = { confirmRestore = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Restore, null); Spacer(Modifier.width(6.dp)); Text("Restaurar desde archivo")
                }
                Spacer(Modifier.height(4.dp))
                Text("Documentos legibles", fontWeight = FontWeight.Bold)
                Text(
                    "PDF y Markdown incluyen recordatorios, notas, clientes, cuentas por cobrar y por pagar y Mis Gastos. Sirven para leer o imprimir, pero no sustituyen la copia JSON.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = { savePdf.launch("AgendaWA-copia-$stamp.pdf") }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.PictureAsPdf, null); Spacer(Modifier.width(6.dp)); Text("Guardar PDF")
                }
                OutlinedButton(onClick = { saveMarkdown.launch("AgendaWA-copia-$stamp.md") }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Description, null); Spacer(Modifier.width(6.dp)); Text("Guardar Markdown")
                }
                Text(
                    "Importante: PDF y Markdown no están cifrados. No guardes contraseñas sensibles en esos formatos.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
    )

    if (confirmRestore) {
        AlertDialog(
            onDismissRequest = { confirmRestore = false },
            title = { Text("Restaurar copia") },
            text = {
                Text("Se combinarán los datos del archivo con los actuales. Los elementos con el mismo identificador se reemplazarán por los de la copia.")
            },
            confirmButton = {
                Button(onClick = { confirmRestore = false; restore.launch(arrayOf("application/json", "text/plain", "application/octet-stream", "*/*")) }) {
                    Text("Elegir archivo")
                }
            },
            dismissButton = { TextButton(onClick = { confirmRestore = false }) { Text("Cancelar") } },
        )
    }
}
