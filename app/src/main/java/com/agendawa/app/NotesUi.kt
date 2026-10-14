@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.agendawa.app

import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.CircularProgressIndicator
import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BusinessCenter
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.StickyNote2
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties

fun noteTypeIcon(type: SmartNoteType): ImageVector = when (type) {
    SmartNoteType.PERSON_TOPICS -> Icons.Rounded.People
    SmartNoteType.IDEA -> Icons.Rounded.Lightbulb
    SmartNoteType.BUSINESS -> Icons.Rounded.BusinessCenter
    SmartNoteType.PROJECT -> Icons.Rounded.Folder
    SmartNoteType.REFERENCE -> Icons.Rounded.Link
    SmartNoteType.GENERAL -> Icons.Rounded.StickyNote2
}

fun noteTypeColor(type: SmartNoteType): Color = when (type) {
    SmartNoteType.PERSON_TOPICS -> Color(0xFF7C3AED)
    SmartNoteType.IDEA -> Color(0xFFF59E0B)
    SmartNoteType.BUSINESS -> AgendaGreen
    SmartNoteType.PROJECT -> AgendaBlue
    SmartNoteType.REFERENCE -> Color(0xFF0891B2)
    SmartNoteType.GENERAL -> AgendaMuted
}

@Composable
fun NotesScreen(
    state: AppState,
    onEdit: (SmartNote) -> Unit,
    onDelete: (SmartNote) -> Unit,
    onTogglePin: (SmartNote) -> Unit,
    onDevelop: ((SmartNote) -> Unit)? = null,
) {
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf<SmartNoteType?>(null) }
    val q = SmartNoteOrganizer.normalize(query.trim())
    val filtered = state.notes.filter { n ->
        (filter == null || n.type == filter) &&
            (q.isBlank() || SmartNoteOrganizer.normalize("${n.title} ${n.content} ${n.personName} ${n.project} ${n.tags}").contains(q))
    }

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Column {
                Text("Ideas y notas", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Guarda, clasifica y encuentra lo importante.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Buscar en notas, personas, proyectos o etiquetas") },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = {
                    if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Clear, "Limpiar búsqueda") }
                },
                singleLine = true,
            )
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(selected = filter == null, onClick = { filter = null }, label = { Text("Todo (${state.notes.size})") })
                }
                items(SmartNoteType.entries.sortedBy { it.sortOrder }) { type ->
                    val count = state.notes.count { it.type == type }
                    FilterChip(
                        selected = filter == type,
                        onClick = { filter = if (filter == type) null else type },
                        label = { Text("${type.label} ($count)") },
                        leadingIcon = { Icon(noteTypeIcon(type), null, modifier = Modifier.size(16.dp)) },
                    )
                }
            }
        }
        if (state.notes.isEmpty()) {
            item {
                EmptyState(
                    Icons.Rounded.Lightbulb,
                    "Captura tu primera idea",
                    "Puedes escribir, pegar o dictar una idea, un negocio, un proyecto o puntos para hablar con alguien.",
                )
            }
        } else if (filtered.isEmpty()) {
            item { EmptyState(Icons.Rounded.SearchOff, "No encontramos resultados", "Prueba otro texto o cambia el filtro.") }
        }
        items(filtered, key = { it.id }) { note ->
            SmartNoteCard(
                note,
                onClick = { onEdit(note) },
                onPin = { onTogglePin(note) },
                onDelete = { onDelete(note) },
                onDevelop = onDevelop?.let { dev -> { dev(note) } },
            )
        }
    }
}

@Composable
fun SmartNoteCard(
    note: SmartNote,
    onClick: () -> Unit,
    onPin: () -> Unit,
    onDelete: () -> Unit,
    onDevelop: (() -> Unit)? = null,
) {
    val color = noteTypeColor(note.type)
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, if (note.pinned) color.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline),
    ) {
        Column(Modifier.padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(noteTypeIcon(note.type), null, tint = color, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Pill(note.type.label, color)
                if (note.pinned) {
                    Spacer(Modifier.width(6.dp))
                    Pill("Fijada", AgendaDanger)
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onPin) {
                    Icon(
                        if (note.pinned) Icons.Rounded.PushPin else Icons.Outlined.PushPin,
                        if (note.pinned) "Desfijar" else "Fijar",
                        tint = if (note.pinned) color else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onDelete) { Icon(Icons.Rounded.Delete, "Eliminar", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Text(note.title.ifBlank { "Sin título" }, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            Text(note.content, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 4, overflow = TextOverflow.Ellipsis)
            val meta = buildList {
                if (note.personName.isNotBlank()) add("Persona: ${note.personName}")
                if (note.project.isNotBlank()) add("Proyecto: ${note.project}")
                add("Actualizada ${formatDate(note.updatedAtMillis, "dd MMM yyyy, h:mm a")}")
            }
            Spacer(Modifier.height(6.dp))
            Text(meta.joinToString("  •  "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (note.tags.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(note.tags.split(",").joinToString(" ") { "#" + it.trim().replace(" ", "") }, style = MaterialTheme.typography.labelSmall, color = color)
            }
            // Ideas, negocios y proyectos: se pueden llevar al Laboratorio para desarrollarlos
            if (note.type in setOf(SmartNoteType.IDEA, SmartNoteType.BUSINESS, SmartNoteType.PROJECT)) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(end = 10.dp)) {
                    if (onDevelop != null) {
                        Button(onClick = onDevelop, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Outlined.Science, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Desarrollar")
                        }
                    }
                    OutlinedButton(onClick = onClick, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Outlined.Edit, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Editar")
                    }
                }
            }
        }
    }
}

@Composable
fun SmartNoteEditorDialog(
    request: NoteEditorRequest,
    onDismiss: () -> Unit,
    onSave: (SmartNote) -> Unit,
    onDevelop: ((String) -> Unit)? = null,
) {
    val context = LocalContext.current
    val existing = request.note
    var title by remember { mutableStateOf(existing?.title ?: request.prefillTitle) }
    var content by remember { mutableStateOf(existing?.content ?: request.prefillText) }
    var type by remember { mutableStateOf(existing?.type ?: request.prefillType ?: SmartNoteType.GENERAL) }
    var personName by remember { mutableStateOf(existing?.personName ?: "") }
    var personPhone by remember { mutableStateOf(existing?.personPhone ?: "") }
    var project by remember { mutableStateOf(existing?.project ?: "") }
    var tags by remember { mutableStateOf(existing?.tags ?: "") }
    var pinned by remember { mutableStateOf(existing?.pinned ?: false) }
    var autoType by remember { mutableStateOf(existing == null && request.prefillType == null) }
    val aiEnabled = remember { AiSettings.isEnabled(context) }
    var polishing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf<String?>(null) }

    val contactLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            ContactPicker.read(context, result.data?.data)?.let {
                personName = it.displayName
                personPhone = it.phoneNumber
            } ?: toast(context, "No se pudo leer el contacto.")
        }
    }
    val dictate = rememberSpeechInput("Dicta tu idea o nota. Puedes repetir para seguir agregando.") { text ->
        content = if (content.isBlank()) text.replaceFirstChar { it.uppercase() } else "$content\n$text"
    }

    fun organize() {
        val org = SmartNoteOrganizer.organize(content, title, personName, project)
        if (title.isBlank()) title = org.title
        type = org.type
        tags = org.tags
        autoType = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        title = { Text(if (existing == null) "Capturar una idea" else "Editar nota") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it.take(25000); error = null },
                    label = { Text("Idea, nota o puntos a tratar") },
                    placeholder = { Text("Escribe, pega o dicta toda la información...") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp),
                    supportingText = { Text("${content.length}/25000") },
                    isError = error != null,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = dictate, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.Mic, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Dictar nota")
                    }
                    OutlinedButton(onClick = { organize() }, modifier = Modifier.weight(1f), enabled = content.isNotBlank()) {
                        Icon(Icons.Rounded.AutoAwesome, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Organizar")
                    }
                }
                if (aiEnabled) {
                    Button(
                        onClick = {
                            polishing = true
                            scope.launch {
                                runCatching { withContext(Dispatchers.IO) { AiAssistant.polishNote(context, content) } }
                                    .onSuccess { content = it.take(25000); organize() }
                                    .onFailure { toast(context, it.message ?: "No se pudo redactar") }
                                polishing = false
                            }
                        },
                        enabled = content.isNotBlank() && !polishing,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (polishing) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        else { Icon(Icons.Rounded.AutoAwesome, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Redactar y ordenar con IA") }
                    }
                }
                if (aiEnabled && onDevelop != null) {
                    OutlinedButton(
                        onClick = { onDevelop(listOf(title, content).filter { it.isNotBlank() }.joinToString("\n")) },
                        enabled = content.isNotBlank() || title.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Outlined.Science, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                        Text("Desarrollar en el Laboratorio")
                    }
                }
                Text(
                    if (aiEnabled) "La IA ordena tu idea; \"Organizar\" la clasifica y le pone etiquetas."
                    else "Agenda WA puede clasificarla y sugerir etiquetas. Activa la IA en Configuración para que también la redacte.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = title, onValueChange = { title = it.take(120) },
                    label = { Text("Título") }, placeholder = { Text("Ej. Campaña para el próximo sencillo") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                Text("Tipo de nota", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(SmartNoteType.entries.sortedBy { it.sortOrder }) { t ->
                        FilterChip(
                            selected = type == t,
                            onClick = { type = t; autoType = false },
                            label = { Text(t.label) },
                            leadingIcon = { Icon(noteTypeIcon(t), null, modifier = Modifier.size(16.dp)) },
                        )
                    }
                }
                Text(type.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = personName, onValueChange = { personName = it.take(80) },
                    label = { Text("Persona relacionada (opcional)") }, placeholder = { Text("Ej. Carlos, cliente o integrante") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        IconButton(onClick = {
                            runCatching { contactLauncher.launch(ContactPicker.intent()) }
                                .onFailure { toast(context, "No se pudo abrir la lista de contactos.") }
                        }) { Icon(Icons.Rounded.Contacts, "Buscar contacto") }
                    },
                    supportingText = { if (personPhone.isNotBlank()) Text("Teléfono: $personPhone") },
                )
                OutlinedTextField(
                    value = project, onValueChange = { project = it.take(80) },
                    label = { Text("Proyecto o negocio relacionado (opcional)") }, placeholder = { Text("Ej. Lanzamiento musical, cliente web...") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = tags, onValueChange = { tags = it.take(200) },
                    label = { Text("Etiquetas") }, placeholder = { Text("música, diseño, redes, cliente...") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { pinned = !pinned }) {
                    androidx.compose.material3.Checkbox(checked = pinned, onCheckedChange = { pinned = it })
                    Text("Fijar nota importante")
                }
                if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Button(onClick = {
                if (content.isBlank() && title.isBlank()) {
                    error = "Escribe o dicta una idea antes de guardar."
                } else {
                val org = SmartNoteOrganizer.organize(content, title, personName, project)
                val now = System.currentTimeMillis()
                onSave(
                    SmartNote(
                        id = existing?.id ?: 0L,
                        title = title.trim().ifBlank { org.title },
                        content = content.trim(),
                        type = if (autoType) org.type else type,
                        personName = personName.trim(),
                        personPhone = personPhone.trim(),
                        project = project.trim(),
                        tags = tags.trim().ifBlank { org.tags },
                        pinned = pinned,
                        createdAtMillis = existing?.createdAtMillis ?: now,
                        updatedAtMillis = now,
                    ),
                )
                }
            }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
