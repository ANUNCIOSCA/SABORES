package com.agendawa.app

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.provider.ContactsContract
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// ---------------------------------------------------------------------------
// Libreta de contactos propia: lee TODAS las cuentas del teléfono (Google/correo,
// teléfono, SIM, WhatsApp, Samsung…), sin depender del filtro "contactos a mostrar"
// de la app de Contactos, que a veces esconde cuentas enteras.
// ---------------------------------------------------------------------------

data class BookEntry(val name: String, val number: String, val account: String)

data class ContactBook(
    val entries: List<BookEntry>,
    /** Cuántos números hay por tipo de cuenta ("Google (correo)" → 812). */
    val byAccount: Map<String, Int>,
    /** Contactos sin ningún número (solo correo): no sirven para WhatsApp ni llamar. */
    val withoutPhone: Int,
)

object ContactBookReader {
    private fun accountLabel(type: String?): String {
        val t = (type ?: "").lowercase()
        return when {
            t.isBlank() || "phone" in t || "local" in t || "device" in t -> "Teléfono"
            t == "com.google" -> "Google (correo)"
            "whatsapp" in t -> "WhatsApp"
            "sim" in t || "icc" in t || "usim" in t -> "SIM"
            "exchange" in t || "outlook" in t || "office" in t -> "Outlook / Exchange"
            "samsung" in t || "osp" in t -> "Cuenta Samsung"
            "xiaomi" in t || "mi" == t -> "Cuenta Xiaomi"
            "huawei" in t -> "Cuenta Huawei"
            "telegram" in t -> "Telegram"
            else -> type!!.substringAfterLast('.').replaceFirstChar { it.uppercase() }
        }
    }

    fun load(context: Context): ContactBook {
        if (!ContactSearch.hasPermission(context)) return ContactBook(emptyList(), emptyMap(), 0)
        val seen = HashMap<String, Int>() // nombre+número → posición
        val list = ArrayList<BookEntry>()
        val counts = linkedMapOf<String, Int>()
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.NORMALIZED_NUMBER,
            ContactsContract.RawContacts.ACCOUNT_TYPE,
        )
        val cursor = runCatching {
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI, projection, null, null,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} COLLATE LOCALIZED ASC",
            )
        }.getOrNull() ?: runCatching {
            // Algunos teléfonos no dejan pedir la cuenta: se lee sin ella
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI, projection.take(3).toTypedArray(), null, null,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC",
            )
        }.getOrNull()
        cursor?.use { c ->
            val hasAccount = c.columnCount > 3
            while (c.moveToNext()) {
                val name = c.getString(0).orEmpty()
                if (name.isBlank()) continue
                val number = (c.getString(2) ?: c.getString(1)).orEmpty()
                val digits = number.filter { it.isDigit() }.takeLast(10)
                if (digits.isEmpty()) continue
                val account = accountLabel(if (hasAccount) c.getString(3) else null)
                counts[account] = (counts[account] ?: 0) + 1
                val key = SmartNoteOrganizer.normalize(name) + "|" + digits
                val at = seen[key]
                if (at == null) {
                    seen[key] = list.size
                    list += BookEntry(name, number, account)
                } else if (list[at].account == "WhatsApp" || list[at].account == "Teléfono") {
                    // Si el mismo número está en Google y en el teléfono, se muestra como Google (es el respaldo)
                    if (account == "Google (correo)") list[at] = list[at].copy(account = account)
                }
            }
        }
        val withoutPhone = runCatching {
            context.contentResolver.query(
                ContactsContract.Contacts.CONTENT_URI, arrayOf(ContactsContract.Contacts._ID),
                "${ContactsContract.Contacts.HAS_PHONE_NUMBER} = 0", null, null,
            )?.use { it.count } ?: 0
        }.getOrDefault(0)
        return ContactBook(list, counts, withoutPhone)
    }
}

/**
 * Botón de contactos: abre la libreta propia (todas las cuentas). Si no hay permiso lo pide;
 * si lo niegan, usa el selector del teléfono como antes.
 */
@Composable
fun rememberContactPicker(onPicked: (SelectedPhoneContact) -> Unit): () -> Unit {
    val context = LocalContext.current
    var show by remember { mutableStateOf(false) }
    val systemPicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            ContactPicker.read(context, result.data?.data)?.let(onPicked)
                ?: toast(context, "No se pudo leer el número del contacto seleccionado.")
        }
    }
    val openSystem = {
        runCatching { systemPicker.launch(ContactPicker.intent()) }
            .onFailure { toast(context, "No se pudo abrir la aplicación de contactos.") }
        Unit
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) show = true else openSystem()
    }
    if (show) {
        ContactBookDialog(
            onDismiss = { show = false },
            onPick = { show = false; onPicked(it) },
            onSystemPicker = { show = false; openSystem() },
        )
    }
    return {
        if (ContactSearch.hasPermission(context)) show = true
        else permission.launch(Manifest.permission.READ_CONTACTS)
    }
}

@Composable
private fun ContactBookDialog(onDismiss: () -> Unit, onPick: (SelectedPhoneContact) -> Unit, onSystemPicker: () -> Unit) {
    val context = LocalContext.current
    var book by remember { mutableStateOf<ContactBook?>(null) }
    var query by remember { mutableStateOf("") }
    var help by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { book = withContext(Dispatchers.IO) { ContactBookReader.load(context) } }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = WideDialogProperties,
        modifier = Modifier.fillMaxWidth().dialogSafePadding().padding(12.dp),
        title = { Text("Elegir contacto") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query, onValueChange = { query = it.take(40) },
                    placeholder = { Text("Buscar por nombre o número") },
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                val b = book
                if (b == null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp)); Text("Leyendo todos tus contactos...")
                    }
                } else {
                    Text(
                        "${b.entries.size} contactos con número · " + b.byAccount.entries.joinToString(" · ") { "${it.key}: ${it.value}" },
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    val q = SmartNoteOrganizer.normalize(query).trim()
                    val qDigits = query.filter { it.isDigit() }
                    val shown = if (q.isBlank()) b.entries else b.entries.filter { e ->
                        val n = SmartNoteOrganizer.normalize(e.name)
                        q.split(" ").filter { it.isNotBlank() }.all { n.contains(it) } ||
                            (qDigits.length >= 3 && e.number.filter { it.isDigit() }.contains(qDigits))
                    }
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 380.dp)) {
                        items(shown.take(300)) { e ->
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                    .clickable { onPick(SelectedPhoneContact(e.name, e.number)) }
                                    .padding(vertical = 8.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(Modifier.size(34.dp).clip(CircleShape).background(AgendaBlue.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                                    Text(e.name.take(1).uppercase(), color = AgendaBlue, fontWeight = FontWeight.Bold)
                                }
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(e.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(e.number, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text(
                                    e.account, style = MaterialTheme.typography.labelSmall, color = AgendaBlue,
                                    modifier = Modifier.clip(RoundedCornerShape(50)).background(AgendaBlue.copy(alpha = 0.08f)).padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                        }
                        if (shown.isEmpty()) item {
                            Text(
                                "No encontré \"$query\". Mira abajo \"¿Falta alguien?\".",
                                modifier = Modifier.padding(8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    TextButton(onClick = { help = !help }) {
                        Icon(Icons.Rounded.Person, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp))
                        Text(if (help) "Ocultar ayuda" else "¿Falta alguien?")
                    }
                    if (help) {
                        Text(
                            buildString {
                                append("• La app ve los contactos guardados en el teléfono, aunque sean de Google, la SIM o WhatsApp.\n")
                                if (b.byAccount.keys.none { it == "Google (correo)" }) append("• No veo contactos de tu cuenta Google: activa su sincronización.\n")
                                append("• Los que están solo en Gmail (\"Otros contactos\", personas a las que escribiste) no bajan al teléfono: ábrelos en contacts.google.com y guárdalos como contacto.\n")
                                if (b.withoutPhone > 0) append("• Hay ${b.withoutPhone} contactos sin número (solo correo): no sirven para WhatsApp ni llamar.\n")
                                append("• Si acabas de agregar a alguien, espera a que sincronice y vuelve a abrir esta lista.")
                            },
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(onClick = {
                                runCatching {
                                    context.startActivity(
                                        Intent(Settings.ACTION_SYNC_SETTINGS).putExtra(Settings.EXTRA_AUTHORITIES, arrayOf(ContactsContract.AUTHORITY))
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                    )
                                }.onFailure { toast(context, "Abre Ajustes › Cuentas › Google › Contactos") }
                            }) { Text("Sincronizar cuentas") }
                            TextButton(onClick = onSystemPicker) { Text("Selector del teléfono") }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
    )
}
