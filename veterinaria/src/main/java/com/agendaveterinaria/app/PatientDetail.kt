@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.agendaveterinaria.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.MedicalServices
import androidx.compose.material.icons.rounded.MonitorWeight
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Vaccines
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.io.File

/** Qué formulario está abierto en la ficha del paciente. */
private sealed interface PForm {
    data object EditPatient : PForm
    data class VisitF(val v: Visit?) : PForm
    data class DoseF(val d: Dose?, val kind: DoseKind) : PForm
    data class ReminderF(val r: Reminder?) : PForm
    data object WeightF : PForm
    data object ChargeF : PForm
    data object Carnet : PForm
    data object AddMenu : PForm
}

@Composable
fun PatientDetail(state: VetState, patientId: Long, onBack: () -> Unit, onOpenOwner: (Long) -> Unit) {
    val context = LocalContext.current
    val p = state.patient(patientId) ?: run { LaunchedEffect(Unit) { onBack() }; return }
    val owner = state.ownerOf(p)
    var tab by remember { mutableIntStateOf(0) }
    var form by remember { mutableStateOf<PForm?>(null) }
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val close = { form = null }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(p.name, fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Volver") } },
                actions = {
                    IconButton(onClick = { form = PForm.Carnet }) { Icon(Icons.Rounded.PictureAsPdf, "Carnet") }
                    IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "Más") }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem(text = { Text("Editar datos") }, onClick = { menu = false; form = PForm.EditPatient })
                        DropdownMenuItem(text = { Text("Registrar peso") }, onClick = { menu = false; form = PForm.WeightF })
                        DropdownMenuItem(text = { Text("Nuevo cobro") }, onClick = { menu = false; form = PForm.ChargeF })
                        DropdownMenuItem(text = { Text(if (p.deceased) "Marcar como activo" else "Marcar fallecido") }, onClick = { menu = false; state.savePatient(p.copy(deceased = !p.deceased)) })
                        DropdownMenuItem(text = { Text("Eliminar paciente", color = VetDanger) }, onClick = { menu = false; confirmDelete = true })
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { form = PForm.AddMenu }, containerColor = VetGreen, contentColor = androidx.compose.ui.graphics.Color.White) {
                Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(6.dp)); Text("Registrar")
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                VetCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar(speciesEmoji(p.species), 52)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(listOf(p.species, p.breed).filter { it.isNotBlank() }.joinToString(" · "), fontWeight = FontWeight.SemiBold)
                            Text(
                                listOf(p.sex + if (p.sterilized) " esterilizado/a" else "", ageText(p.birthMillis), state.lastWeight(p.id)?.let { "${num(it)} kg" } ?: "")
                                    .filter { it.isNotBlank() }.joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (p.deceased) Pill("Fallecido", VetDanger)
                        }
                    }
                    LabeledRow("Chip / arete", p.chip)
                    LabeledRow("Color / señas", p.color)
                    if (p.allergies.isNotBlank()) Text("⚠ ${p.allergies}", color = VetDanger, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                    LabeledRow("Notas", p.notes)
                }
            }
            if (owner != null) item {
                VetCard(onClick = { onOpenOwner(owner.id) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Dueño", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(owner.name, fontWeight = FontWeight.SemiBold)
                            if (owner.phone.isNotBlank()) Text(owner.phone, style = MaterialTheme.typography.bodySmall)
                            val debt = state.pendingOf(owner.id)
                            if (debt > 0) Text("Debe ${money(debt, state.settings.currency)}", color = VetAmber, style = MaterialTheme.typography.labelSmall)
                        }
                        if (owner.phone.isNotBlank()) {
                            IconButton(onClick = { WhatsApp.call(context, owner.phone) }) { Icon(Icons.Rounded.Call, "Llamar", tint = VetGreen) }
                            IconButton(onClick = { WhatsApp.open(context, owner.phone, Messages.greeting(owner) + ", ") }) { Icon(Icons.AutoMirrored.Rounded.Send, "WhatsApp", tint = WaGreen) }
                        }
                    }
                }
            }
            item {
                TabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.background) {
                    listOf("Historia", "Vacunas", "Avisos", "Peso").forEachIndexed { i, t -> Tab(tab == i, { tab = i }, text = { Text(t, maxLines = 1) }) }
                }
            }
            when (tab) {
                0 -> {
                    val visits = state.visitsOf(p.id)
                    if (visits.isEmpty()) item { EmptyState(Icons.Rounded.MedicalServices, "Sin consultas", "Toca Registrar › Consulta.") }
                    items(visits, key = { it.id }) { v -> VisitCard(state, v) { form = PForm.VisitF(v) } }
                }
                1 -> {
                    val doses = state.dosesOf(p.id)
                    if (doses.isEmpty()) item { EmptyState(Icons.Rounded.Vaccines, "Sin vacunas", "Toca Registrar › Vacuna o Desparasitación.") }
                    items(doses, key = { it.id }) { d ->
                        VetCard(onClick = { form = PForm.DoseF(d, d.kind) }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Pill(if (d.kind == DoseKind.VACUNA) "Vacuna" else "Desparasitación", if (d.kind == DoseKind.VACUNA) VetGreen else VetBlue)
                                        Text(fmtDate(d.dateMillis), style = MaterialTheme.typography.labelSmall)
                                    }
                                    Text(d.product, fontWeight = FontWeight.SemiBold)
                                    Text(listOf(d.lot.takeIf { it.isNotBlank() }?.let { "Lote $it" }, d.lab.takeIf { it.isNotBlank() }).filterNotNull().joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                                    if (d.nextMillis > 0) {
                                        val late = d.nextMillis < startOfDay(System.currentTimeMillis())
                                        Text("Próxima: ${fmtNice(d.nextMillis)} (${whenText(d.nextMillis)})", color = if (late) VetDanger else VetGreen, style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                                Icon(Icons.Rounded.Edit, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                2 -> {
                    val rs = state.remindersOf(p.id).sortedWith(compareBy<Reminder> { it.done }.thenBy { it.dueMillis })
                    if (rs.isEmpty()) item { EmptyState(Icons.Rounded.EventAvailable, "Sin recordatorios", "Se crean solos con la próxima vacuna, o toca Registrar › Cita.") }
                    items(rs, key = { it.id }) { r -> ReminderRow(state, r, showPatient = false) }
                }
                else -> {
                    val ws = state.weightsOf(p.id)
                    item {
                        OutlinedButton(onClick = { form = PForm.WeightF }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Rounded.MonitorWeight, null); Spacer(Modifier.width(6.dp)); Text("Registrar peso")
                        }
                    }
                    if (ws.isEmpty()) item { EmptyState(Icons.Rounded.MonitorWeight, "Sin pesos", "El peso de cada consulta se guarda aquí.") }
                    items(ws, key = { it.id }) { w ->
                        val prev = ws.getOrNull(ws.indexOf(w) + 1)
                        VetCard {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("${num(w.kg)} kg", fontWeight = FontWeight.Bold)
                                    Text(fmtDate(w.dateMillis) + if (w.note.isNotBlank()) " · ${w.note}" else "", style = MaterialTheme.typography.bodySmall)
                                }
                                prev?.let {
                                    val diff = w.kg - it.kg
                                    Text((if (diff >= 0) "+" else "") + num(diff) + " kg", color = if (diff >= 0) VetGreen else VetAmber, style = MaterialTheme.typography.labelMedium)
                                }
                                IconButton(onClick = { state.deleteWeight(w) }) { Icon(Icons.Rounded.Delete, "Borrar", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.size(90.dp)) }
        }
    }

    when (val f = form) {
        null -> Unit
        PForm.AddMenu -> AlertDialog(
            onDismissRequest = close,
            title = { Text("Registrar para ${p.name}") },
            text = {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { form = PForm.VisitF(null) }) { Text("Consulta") }
                    Button(onClick = { form = PForm.DoseF(null, DoseKind.VACUNA) }) { Text("Vacuna") }
                    Button(onClick = { form = PForm.DoseF(null, DoseKind.DESPARASITACION_INT) }) { Text("Desparasitación") }
                    OutlinedButton(onClick = { form = PForm.ReminderF(null) }) { Text("Cita / recordatorio") }
                    OutlinedButton(onClick = { form = PForm.WeightF }) { Text("Peso") }
                    OutlinedButton(onClick = { form = PForm.ChargeF }) { Text("Cobro") }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = close) { Text("Cerrar") } },
        )
        PForm.EditPatient -> PatientForm(state, p, onDismiss = close, onSave = { close() })
        is PForm.VisitF -> VisitForm(state, p, f.v, onDismiss = close, onSaved = { close(); tab = 0; toast(context, "Consulta guardada") })
        is PForm.DoseF -> DoseForm(state, p, f.d, f.kind, onDismiss = close, onSaved = { close(); tab = 1; toast(context, "Guardado en el carnet") })
        is PForm.ReminderF -> ReminderForm(state, p, f.r, onDismiss = close, onSaved = { close(); tab = 2 })
        PForm.WeightF -> WeightForm(onDismiss = close) { kg, d -> state.addWeight(p.id, kg, d); close(); tab = 3 }
        PForm.ChargeF -> ChargeForm(state, p.ownerId, p.id, onDismiss = close, onSaved = { close(); toast(context, "Cobro registrado") })
        PForm.Carnet -> CarnetDialog(state, p, onDismiss = close)
    }

    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("¿Eliminar a ${p.name}?") },
        text = { Text("Se borran su historia, vacunas, pesos y recordatorios. Los cobros del dueño se conservan.") },
        confirmButton = { TextButton(onClick = { confirmDelete = false; state.deletePatient(p); onBack() }) { Text("Eliminar", color = VetDanger) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } },
    )
}

@Composable
private fun VisitCard(state: VetState, v: Visit, onEdit: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    VetCard(onClick = onEdit) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Pill(v.type.label, if (v.type == VisitType.EMERGENCIA) VetDanger else VetGreen)
            Text(fmtDateTime(v.dateMillis), style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
            IconButton(onClick = { confirm = true }, modifier = Modifier.size(28.dp)) { Icon(Icons.Rounded.Delete, "Borrar", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp)) }
        }
        if (v.reason.isNotBlank()) Text(v.reason, fontWeight = FontWeight.SemiBold)
        val vitals = listOfNotNull(v.weightKg?.let { "${num(it)} kg" }, v.temperature?.let { "${num(it, 1)} °C" }).joinToString(" · ")
        LabeledRow("Signos", vitals)
        LabeledRow("Examen", v.findings)
        LabeledRow("Diagnóstico", v.diagnosis)
        LabeledRow("Tratamiento", v.treatment)
        if (v.medications.isNotEmpty()) LabeledRow("Medicamentos", v.medications.joinToString("\n") { m -> listOf(m.name, m.dose, m.route, m.frequency).filter { it.isNotBlank() }.joinToString(" · ") })
        LabeledRow("Notas", v.notes)
    }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text("¿Borrar esta consulta?") },
        confirmButton = { TextButton(onClick = { confirm = false; state.deleteVisit(v) }) { Text("Borrar", color = VetDanger) } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancelar") } },
    )
}

/** Exportar el carnet / boleta de control en PDF. */
@Composable
fun CarnetDialog(state: VetState, p: Patient, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var file by remember { mutableStateOf<File?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(p.id) {
        runCatching { Carnet.build(context, state, p) }.onSuccess { file = it }.onFailure { error = it.message ?: "No se pudo crear el PDF" }
    }
    val owner = state.ownerOf(p)
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.PictureAsPdf, null, tint = VetGreen) },
        title = { Text("Carnet de ${p.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (error != null) Text(error!!, color = VetDanger)
                else if (file == null) Text("Preparando el PDF...")
                else Text(
                    "Carnet de vacunación y boleta de control listo: datos del paciente y del dueño, vacunas, desparasitaciones, últimos controles y próximas citas." +
                        if (state.settings.clinicName.isBlank()) "\n\nConsejo: pon el nombre del consultorio en Ajustes para que salga en el encabezado." else "",
                    style = MaterialTheme.typography.bodySmall,
                )
                val f = file
                if (f != null) {
                    Button(onClick = {
                        Carnet.share(context, f, "${Messages.greeting(owner)} 🐾 Te envío el carnet de ${p.name}." + if (state.settings.clinicName.isNotBlank()) "\n${state.settings.clinicName}" else "")
                    }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.AutoMirrored.Rounded.Send, null); Spacer(Modifier.width(6.dp)); Text("Enviar (WhatsApp, correo…)") }
                    OutlinedButton(onClick = { Carnet.open(context, f) }, modifier = Modifier.fillMaxWidth()) { Text("Ver / imprimir") }
                    OutlinedButton(onClick = {
                        runCatching { Carnet.saveToDownloads(context, f) }
                            .onSuccess { toast(context, "Guardado en $it") }
                            .onFailure { toast(context, "No se pudo guardar: ${it.message}") }
                    }, modifier = Modifier.fillMaxWidth()) { Text("Guardar en Descargas") }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
    )
}
