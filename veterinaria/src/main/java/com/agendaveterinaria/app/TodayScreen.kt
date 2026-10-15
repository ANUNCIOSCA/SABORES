package com.agendaveterinaria.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.Vaccines
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Enviar por WhatsApp al dueño el mensaje del recordatorio. */
fun sendReminderWhatsApp(context: android.content.Context, state: VetState, r: Reminder): Boolean {
    val p = state.patient(r.patientId)
    val owner = state.ownerOf(p) ?: state.owner(r.ownerId)
    if (owner == null || owner.phone.isBlank()) { toast(context, "El dueño no tiene teléfono registrado."); return false }
    val msg = r.message.ifBlank { Messages.appointment(owner, p, r.title, r.dueMillis, state.settings) }
    val ok = WhatsApp.open(context, owner.phone, msg)
    if (ok) state.markSent(r) else toast(context, "No se pudo abrir WhatsApp.")
    return ok
}

@Composable
fun ReminderRow(state: VetState, r: Reminder, showPatient: Boolean = true, onOpenPatient: (Long) -> Unit = {}) {
    val context = LocalContext.current
    val p = state.patient(r.patientId)
    val owner = state.ownerOf(p) ?: state.owner(r.ownerId)
    val overdue = !r.done && r.dueMillis < startOfDay(System.currentTimeMillis())
    VetCard(onClick = if (showPatient && p != null) ({ onOpenPatient(p.id) }) else null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                when (r.kind) { ReminderKind.VACUNA, ReminderKind.DESPARASITACION -> Icons.Rounded.Vaccines; else -> Icons.Rounded.EventAvailable },
                null, tint = if (r.done) MaterialTheme.colorScheme.onSurfaceVariant else if (overdue) VetDanger else VetGreen,
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Pill(r.kind.label, if (overdue) VetDanger else VetGreen)
                    Text(
                        "${fmtNice(r.dueMillis)} · ${whenText(r.dueMillis)}", style = MaterialTheme.typography.labelSmall,
                        color = if (overdue) VetDanger else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(r.title, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (showPatient) {
                    val who = listOfNotNull(p?.let { "${it.name} (${it.species.lowercase()})" }, owner?.name).joinToString(" · ")
                    if (who.isNotBlank()) Text(who, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (r.sentToOwner) Text("✓ Mensaje enviado al dueño", style = MaterialTheme.typography.labelSmall, color = WaGreen)
            }
            if (!r.done && owner?.phone?.isNotBlank() == true) {
                IconButton(onClick = { sendReminderWhatsApp(context, state, r) }) {
                    Icon(Icons.AutoMirrored.Rounded.Send, "WhatsApp al dueño", tint = WaGreen)
                }
            }
            IconButton(onClick = { state.setDone(r, !r.done) }) {
                Icon(Icons.Rounded.Check, if (r.done) "Desmarcar" else "Hecho", tint = if (r.done) VetGreen else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun TodayScreen(state: VetState, onOpenPatient: (Long) -> Unit, onNewPatient: () -> Unit) {
    val now = System.currentTimeMillis()
    val today = startOfDay(now)
    val pending = state.data.reminders.filter { !it.done }.sortedBy { it.dueMillis }
    val overdue = pending.filter { it.dueMillis < today }
    val todayList = pending.filter { it.dueMillis in today until plusDays(today, 1) }
    val week = pending.filter { it.dueMillis in plusDays(today, 1) until plusDays(today, 8) }
    val later = pending.filter { it.dueMillis >= plusDays(today, 8) }.take(15)
    val month = fmt(now, "yyyyMM")
    val income = state.data.charges.filter { fmt(it.dateMillis, "yyyyMM") == month }.sumOf { it.paid }
    val debt = state.data.charges.sumOf { it.pending }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            val clinic = state.settings.clinicName.ifBlank { "Agenda Veterinaria" }
            Text(clinic, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(fmt(now, "EEEE d 'de' MMMM").replaceFirstChar { it.uppercase() }, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Metric("Hoy", todayList.size.toString(), VetGreen, Modifier.weight(1f))
                Metric("Atrasados", overdue.size.toString(), if (overdue.isEmpty()) VetGreen else VetDanger, Modifier.weight(1f))
                Metric("Pacientes", state.data.patients.count { !it.deceased }.toString(), VetBlue, Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Metric("Cobrado este mes", money(income, state.settings.currency), VetGreen, Modifier.weight(1f))
                Metric("Por cobrar", money(debt, state.settings.currency), if (debt > 0) VetAmber else VetGreen, Modifier.weight(1f))
            }
        }
        if (pending.isEmpty() && state.data.patients.isEmpty()) {
            item {
                EmptyState(Icons.Rounded.Pets, "Bienvenido", "Registra tu primer paciente con el botón +. Al poner sus vacunas, aquí verás a quién le toca y el WhatsApp listo para el dueño.")
                androidx.compose.material3.Button(onClick = onNewPatient, modifier = Modifier.fillMaxWidth()) { Text("Registrar paciente") }
            }
        }
        fun section(title: String, list: List<Reminder>) {
            if (list.isEmpty()) return
            item { SectionTitle("$title (${list.size})") }
            items(list, key = { it.id }) { r -> ReminderRow(state, r, onOpenPatient = onOpenPatient) }
        }
        section("Atrasados", overdue)
        section("Hoy", todayList)
        section("Próximos 7 días", week)
        section("Más adelante", later)
        item { Spacer(Modifier.size(80.dp)) }
    }
}

@Composable
fun Metric(label: String, value: String, color: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    androidx.compose.material3.Card(
        modifier = modifier,
        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = color.copy(alpha = 0.10f)),
    ) {
        Column(Modifier.padding(10.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = color)
            Text(value, fontWeight = FontWeight.Bold, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
