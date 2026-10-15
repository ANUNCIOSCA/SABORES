@file:OptIn(ExperimentalLayoutApi::class)

package com.agendaveterinaria.app

import android.app.Activity
import android.content.Intent
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties

@Composable
fun FormDialog(title: String, onDismiss: () -> Unit, onSave: () -> Unit, saveLabel: String = "Guardar", content: @Composable () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.fillMaxWidth().padding(12.dp),
        title = { Text(title) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) { content() } },
        confirmButton = { Button(onClick = onSave) { Text(saveLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun FormError(msg: String?) { msg?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) } }

// ---------------------------------------------------------------------------
// Dueño
// ---------------------------------------------------------------------------

@Composable
fun rememberPhonePicker(onPicked: (name: String, phone: String) -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val uri = r.data?.data ?: return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.query(
                uri, arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER), null, null, null,
            )?.use { c -> if (c.moveToFirst()) onPicked(c.getString(0).orEmpty(), c.getString(1).orEmpty()) }
        }.onFailure { toast(context, "No se pudo leer el contacto.") }
    }
    return {
        runCatching { launcher.launch(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)) }
            .onFailure { toast(context, "No se pudo abrir Contactos.") }
    }
}

@Composable
fun OwnerForm(initial: Owner?, onDismiss: () -> Unit, onSave: (Owner) -> Unit) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var phone by remember { mutableStateOf(initial?.phone ?: "") }
    var email by remember { mutableStateOf(initial?.email ?: "") }
    var address by remember { mutableStateOf(initial?.address ?: "") }
    var notes by remember { mutableStateOf(initial?.notes ?: "") }
    var error by remember { mutableStateOf<String?>(null) }
    val pick = rememberPhonePicker { n, p -> if (name.isBlank()) name = n; phone = p }
    FormDialog(if (initial == null) "Nuevo dueño" else "Editar dueño", onDismiss, onSave = {
        if (name.isBlank()) error = "Escribe el nombre del dueño."
        else onSave(Owner(initial?.id ?: 0L, name.trim(), phone.trim(), email.trim(), address.trim(), notes.trim()))
    }) {
        OutlinedTextField(name, { name = it; error = null }, label = { Text("Nombre y apellido") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            phone, { phone = it }, label = { Text("Teléfono / WhatsApp") }, placeholder = { Text("0412 123 4567") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth(),
            trailingIcon = { IconButton(onClick = pick) { Icon(Icons.Rounded.Contacts, "Elegir de contactos") } },
        )
        OutlinedTextField(email, { email = it }, label = { Text("Correo (opcional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(address, { address = it }, label = { Text("Dirección (opcional)") }, modifier = Modifier.fillMaxWidth())
        TextFieldVoice(notes, { notes = it }, "Notas")
        FormError(error)
    }
}

// ---------------------------------------------------------------------------
// Paciente
// ---------------------------------------------------------------------------

/** Guarda el paciente (y el dueño nuevo si hace falta) y devuelve el guardado en [onSave]. */
@Composable
fun PatientForm(state: VetState, initial: Patient?, presetOwnerId: Long = 0L, onDismiss: () -> Unit, onSave: (Patient) -> Unit) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var species by remember { mutableStateOf(initial?.species ?: "Perro") }
    var breed by remember { mutableStateOf(initial?.breed ?: "") }
    var sex by remember { mutableStateOf(initial?.sex ?: "") }
    var birth by remember { mutableLongStateOf(initial?.birthMillis ?: 0L) }
    var color by remember { mutableStateOf(initial?.color ?: "") }
    var chip by remember { mutableStateOf(initial?.chip ?: "") }
    var sterilized by remember { mutableStateOf(initial?.sterilized ?: false) }
    var allergies by remember { mutableStateOf(initial?.allergies ?: "") }
    var notes by remember { mutableStateOf(initial?.notes ?: "") }
    var weight by remember { mutableStateOf("") }
    var ageYears by remember { mutableStateOf("") }
    // Dueño: uno existente o uno nuevo aquí mismo
    var ownerId by remember { mutableLongStateOf(initial?.ownerId ?: presetOwnerId) }
    var ownerQuery by remember { mutableStateOf("") }
    var newOwnerName by remember { mutableStateOf("") }
    var newOwnerPhone by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val pick = rememberPhonePicker { n, p -> newOwnerName = n; newOwnerPhone = p; ownerId = 0L }

    FormDialog(if (initial == null) "Nuevo paciente" else "Editar paciente", onDismiss, onSave = {
        when {
            name.isBlank() -> error = "Escribe el nombre del paciente."
            ownerId == 0L && newOwnerName.isBlank() -> error = "Elige o escribe el dueño."
            else -> {
                val oid = if (ownerId != 0L) ownerId else state.saveOwner(Owner(0L, newOwnerName.trim(), newOwnerPhone.trim())).id
                val years = parseNum(ageYears)
                val b = if (birth == 0L && years != null && years > 0) System.currentTimeMillis() - (years * 365.25 * 86_400_000L).toLong() else birth
                val p = Patient(
                    id = initial?.id ?: 0L, ownerId = oid, name = name.trim(), species = species, breed = breed.trim(), sex = sex,
                    birthMillis = b, color = color.trim(), chip = chip.trim(), sterilized = sterilized, allergies = allergies.trim(),
                    notes = notes.trim(), deceased = initial?.deceased ?: false, createdMillis = initial?.createdMillis ?: System.currentTimeMillis(),
                )
                val saved = state.savePatient(p)
                parseNum(weight)?.let { kg -> if (kg > 0 && initial == null) state.addWeight(saved.id, kg) }
                onSave(saved)
            }
        }
    }) {
        OutlinedTextField(name, { name = it; error = null }, label = { Text("Nombre del paciente") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Text("Especie", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SPECIES.forEach { sp -> FilterChip(selected = species == sp, onClick = { species = sp }, label = { Text(sp) }) }
        }
        OutlinedTextField(breed, { breed = it }, label = { Text("Raza") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf("Macho", "Hembra").forEach { sx -> FilterChip(selected = sex == sx, onClick = { sex = if (sex == sx) "" else sx }, label = { Text(sx) }) }
            Spacer(Modifier.width(6.dp))
            Checkbox(sterilized, { sterilized = it }); Text("Esterilizado/a", style = MaterialTheme.typography.bodySmall)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            DateButton("Nacimiento", birth, Modifier.weight(1f)) { birth = it; ageYears = "" }
            NumberField(ageYears, { ageYears = it; if (it.isNotBlank()) birth = 0L }, "o edad", Modifier.weight(0.8f), "años")
        }
        if (initial == null) NumberField(weight, { weight = it }, "Peso actual", Modifier.fillMaxWidth(), "kg")
        OutlinedTextField(color, { color = it }, label = { Text("Color / señas") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(chip, { chip = it }, label = { Text("Microchip / arete / crotal") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        TextFieldVoice(allergies, { allergies = it }, "Alergias o alertas")
        TextFieldVoice(notes, { notes = it }, "Notas")

        Text("Dueño", style = MaterialTheme.typography.labelLarge)
        val current = state.owner(ownerId)
        if (current != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${current.name} ${if (current.phone.isNotBlank()) "· ${current.phone}" else ""}", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                TextButton(onClick = { ownerId = 0L }) { Text("Cambiar") }
            }
        } else {
            OutlinedTextField(ownerQuery, { ownerQuery = it }, label = { Text("Buscar dueño registrado") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            val q = ownerQuery.trim().lowercase()
            if (q.length >= 2) {
                state.data.owners.filter { it.name.lowercase().contains(q) || it.phone.contains(q) }.take(5).forEach { o ->
                    TextButton(onClick = { ownerId = o.id; ownerQuery = "" }) { Text("${o.name} ${o.phone}") }
                }
            }
            Text("…o dueño nuevo:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(newOwnerName, { newOwnerName = it; error = null }, label = { Text("Nombre del dueño") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                newOwnerPhone, { newOwnerPhone = it }, label = { Text("Teléfono / WhatsApp") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth(),
                trailingIcon = { IconButton(onClick = pick) { Icon(Icons.Rounded.Contacts, "Elegir de contactos") } },
            )
        }
        FormError(error)
    }
}

// ---------------------------------------------------------------------------
// Consulta (historia médica)
// ---------------------------------------------------------------------------

@Composable
fun VisitForm(state: VetState, patient: Patient, initial: Visit?, onDismiss: () -> Unit, onSaved: () -> Unit) {
    var date by remember { mutableLongStateOf(initial?.dateMillis ?: System.currentTimeMillis()) }
    var type by remember { mutableStateOf(initial?.type ?: VisitType.CONSULTA) }
    var reason by remember { mutableStateOf(initial?.reason ?: "") }
    var weight by remember { mutableStateOf(initial?.weightKg?.let { num(it) } ?: state.lastWeight(patient.id)?.let { num(it) } ?: "") }
    var temp by remember { mutableStateOf(initial?.temperature?.let { num(it, 1) } ?: "") }
    var findings by remember { mutableStateOf(initial?.findings ?: "") }
    var diagnosis by remember { mutableStateOf(initial?.diagnosis ?: "") }
    var treatment by remember { mutableStateOf(initial?.treatment ?: "") }
    var meds by remember { mutableStateOf(initial?.medications ?: emptyList()) }
    var notes by remember { mutableStateOf(initial?.notes ?: "") }
    var charge by remember { mutableStateOf("") }
    var paidNow by remember { mutableStateOf(true) }
    var next by remember { mutableLongStateOf(0L) }
    var nextTitle by remember { mutableStateOf("") }
    var calcOpen by remember { mutableStateOf(false) }
    var medName by remember { mutableStateOf("") }
    var medDose by remember { mutableStateOf("") }
    var medRoute by remember { mutableStateOf("") }
    var medFreq by remember { mutableStateOf("") }

    val addMed = {
        if (medName.isNotBlank()) {
            meds = meds + Medication(medName.trim(), medDose.trim(), medRoute.trim(), medFreq.trim())
            medName = ""; medDose = ""; medRoute = ""; medFreq = ""
        }
    }

    FormDialog(if (initial == null) "Consulta de ${patient.name}" else "Editar consulta", onDismiss, onSave = {
        addMed()
        state.saveVisit(
            Visit(
                id = initial?.id ?: 0L, patientId = patient.id, dateMillis = date, type = type, reason = reason.trim(),
                weightKg = parseNum(weight), temperature = parseNum(temp), findings = findings.trim(), diagnosis = diagnosis.trim(),
                treatment = treatment.trim(), medications = meds, notes = notes.trim(),
            ),
            charge = if (initial == null) parseNum(charge) else null, paidNow = paidNow, nextMillis = next, nextTitle = nextTitle.trim(),
        )
        onSaved()
    }) {
        DateButton("Fecha y hora", date, Modifier.fillMaxWidth(), withTime = true) { date = it }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            VisitType.entries.forEach { t -> FilterChip(selected = type == t, onClick = { type = t }, label = { Text(t.label, maxLines = 1) }) }
        }
        TextFieldVoice(reason, { reason = it }, "Motivo de la consulta")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField(weight, { weight = it }, "Peso", Modifier.weight(1f), "kg")
            NumberField(temp, { temp = it }, "Temperatura", Modifier.weight(1f), "°C")
        }
        TextFieldVoice(findings, { findings = it }, "Examen clínico / hallazgos", minLines = 2)
        TextFieldVoice(diagnosis, { diagnosis = it }, "Diagnóstico", minLines = 2)
        TextFieldVoice(treatment, { treatment = it }, "Tratamiento / indicaciones", minLines = 2)

        Text("Medicamentos aplicados o recetados", style = MaterialTheme.typography.labelLarge)
        meds.forEachIndexed { i, m ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(listOf(m.name, m.dose, m.route, m.frequency).filter { it.isNotBlank() }.joinToString(" · "), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                IconButton(onClick = { meds = meds.filterIndexed { j, _ -> j != i } }) { Icon(Icons.Rounded.Close, "Quitar") }
            }
        }
        OutlinedTextField(medName, { medName = it }, label = { Text("Medicamento") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(medDose, { medDose = it }, label = { Text("Dosis") }, singleLine = true, modifier = Modifier.weight(1f))
            OutlinedTextField(medRoute, { medRoute = it }, label = { Text("Vía") }, placeholder = { Text("IM, SC, oral") }, singleLine = true, modifier = Modifier.weight(1f))
        }
        OutlinedTextField(medFreq, { medFreq = it }, label = { Text("Frecuencia y duración") }, placeholder = { Text("cada 12 h por 7 días") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { calcOpen = true }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Rounded.Calculate, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Calcular dosis")
            }
            OutlinedButton(onClick = addMed, enabled = medName.isNotBlank(), modifier = Modifier.weight(1f)) {
                Icon(Icons.Rounded.Add, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Agregar")
            }
        }
        TextFieldVoice(notes, { notes = it }, "Notas")

        if (initial == null) {
            Text("Cobro (opcional)", style = MaterialTheme.typography.labelLarge)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(charge, { charge = it }, "Monto", Modifier.weight(1f), state.settings.currency)
                Switch(paidNow, { paidNow = it }); Text(if (paidNow) "Pagado" else "Por cobrar", style = MaterialTheme.typography.bodySmall)
            }
            Text("Próximo control (opcional)", style = MaterialTheme.typography.labelLarge)
            DateButton("Fecha y hora del control", next, Modifier.fillMaxWidth(), withTime = true) { next = it }
            if (next > 0) OutlinedTextField(nextTitle, { nextTitle = it }, label = { Text("Motivo del control") }, placeholder = { Text("Control de ${patient.name}") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        }
    }

    if (calcOpen) {
        DoseCalcDialog(
            initialWeight = parseNum(weight),
            onDismiss = { calcOpen = false },
            onUse = { drug, doseText ->
                calcOpen = false
                if (drug.isNotBlank()) medName = drug
                medDose = doseText
            },
        )
    }
}

// ---------------------------------------------------------------------------
// Vacuna / desparasitación
// ---------------------------------------------------------------------------

@Composable
fun DoseForm(state: VetState, patient: Patient, initial: Dose?, presetKind: DoseKind = DoseKind.VACUNA, onDismiss: () -> Unit, onSaved: () -> Unit) {
    var kind by remember { mutableStateOf(initial?.kind ?: presetKind) }
    var product by remember { mutableStateOf(initial?.product ?: "") }
    var date by remember { mutableLongStateOf(initial?.dateMillis ?: System.currentTimeMillis()) }
    var lot by remember { mutableStateOf(initial?.lot ?: "") }
    var lab by remember { mutableStateOf(initial?.lab ?: "") }
    var next by remember { mutableLongStateOf(initial?.nextMillis ?: 0L) }
    var notes by remember { mutableStateOf(initial?.notes ?: "") }
    var charge by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val young = isYoung(patient.birthMillis)
    val suggestions = CATALOG.filter { it.species == patient.species && it.kind == kind }

    FormDialog(if (initial == null) "${kind.label} de ${patient.name}" else "Editar ${kind.label.lowercase()}", onDismiss, onSave = {
        if (product.isBlank()) { error = "Escribe la vacuna o el producto."; return@FormDialog }
        state.saveDose(Dose(initial?.id ?: 0L, patient.id, kind, product.trim(), date, lot.trim(), lab.trim(), next, notes.trim()))
        parseNum(charge)?.takeIf { it > 0 && initial == null }?.let {
            state.saveCharge(Charge(0L, patient.ownerId, patient.id, date, "${kind.label}: ${product.trim()}".take(80), it, it))
        }
        onSaved()
    }) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            DoseKind.entries.forEach { k -> FilterChip(selected = kind == k, onClick = { kind = k }, label = { Text(k.label.replace("Desparasitación", "Desp."), maxLines = 1) }) }
        }
        if (suggestions.isNotEmpty()) {
            Text("Sugeridos para ${patient.species.lowercase()}${if (young) " (menor de 1 año)" else ""}:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                suggestions.forEach { c ->
                    FilterChip(selected = product == c.name, onClick = {
                        product = c.name
                        val days = if (young) c.puppyDays else c.adultDays
                        next = if (days > 0) atHour(plusDays(date, days), 9) else 0L
                    }, label = { Text(c.name, maxLines = 2) })
                }
            }
        }
        OutlinedTextField(product, { product = it; error = null }, label = { Text("Vacuna / producto") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(lot, { lot = it }, label = { Text("Lote") }, singleLine = true, modifier = Modifier.weight(1f))
            OutlinedTextField(lab, { lab = it }, label = { Text("Laboratorio") }, singleLine = true, modifier = Modifier.weight(1f))
        }
        DateButton("Fecha de aplicación", date, Modifier.fillMaxWidth()) { date = it }
        Text("Próxima dosis", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(21 to "21 días", 30 to "1 mes", 90 to "3 meses", 180 to "6 meses", 365 to "1 año").forEach { (d, l) ->
                FilterChip(selected = next > 0 && startOfDay(next) == startOfDay(plusDays(date, d)), onClick = { next = atHour(plusDays(date, d), 9) }, label = { Text(l, maxLines = 1) })
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            DateButton("Fecha de la próxima", next, Modifier.weight(1f)) { next = it }
            if (next > 0) TextButton(onClick = { next = 0L }) { Text("No lleva") }
        }
        if (next > 0) Text(
            "Te aviso ${state.settings.daysBefore} día(s) antes con el WhatsApp listo para el dueño.",
            style = MaterialTheme.typography.bodySmall, color = VetGreen,
        )
        Text("La fecha sugerida es una guía: ajústala según el producto, la edad y la normativa.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextFieldVoice(notes, { notes = it }, "Notas")
        if (initial == null) NumberField(charge, { charge = it }, "Cobro (opcional, pagado)", Modifier.fillMaxWidth(), state.settings.currency)
        FormError(error)
    }
}

// ---------------------------------------------------------------------------
// Recordatorio / cita
// ---------------------------------------------------------------------------

@Composable
fun ReminderForm(state: VetState, patient: Patient?, initial: Reminder?, onDismiss: () -> Unit, onSaved: () -> Unit) {
    var kind by remember { mutableStateOf(initial?.kind ?: ReminderKind.CITA) }
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var due by remember { mutableLongStateOf(initial?.dueMillis ?: atHour(plusDays(System.currentTimeMillis(), 1), 9)) }
    var lead by remember { mutableStateOf(if (initial == null) 60 else ((initial.dueMillis - initial.notifyMillis) / 60_000L).toInt()) }
    var message by remember { mutableStateOf(initial?.message ?: "") }
    var error by remember { mutableStateOf<String?>(null) }
    val owner = state.ownerOf(patient)
    fun autoMessage() = when (kind) {
        ReminderKind.MEDICAMENTO -> Messages.medication(owner, patient, title, state.settings)
        else -> Messages.appointment(owner, patient, title.ifBlank { kind.label }, due, state.settings)
    }

    FormDialog(if (initial == null) "Nuevo recordatorio" else "Editar recordatorio", onDismiss, onSave = {
        if (title.isBlank()) { error = "Escribe de qué es el recordatorio."; return@FormDialog }
        state.saveReminder(
            Reminder(
                id = initial?.id ?: 0L, patientId = patient?.id ?: 0L, ownerId = owner?.id ?: 0L, kind = kind, title = title.trim(),
                message = message.ifBlank { autoMessage() }, dueMillis = due, notifyMillis = due - lead * 60_000L, doseId = initial?.doseId ?: 0L,
            ),
        )
        onSaved()
    }) {
        if (patient != null) Text("Paciente: ${patient.name}" + (owner?.let { " · ${it.name}" } ?: ""), fontWeight = FontWeight.SemiBold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(ReminderKind.CITA, ReminderKind.CONTROL, ReminderKind.MEDICAMENTO, ReminderKind.OTRO).forEach { k ->
                FilterChip(selected = kind == k, onClick = { kind = k }, label = { Text(k.label, maxLines = 1) })
            }
        }
        TextFieldVoice(title, { title = it; error = null }, if (kind == ReminderKind.MEDICAMENTO) "Medicamento e indicación" else "Motivo", placeholder = if (kind == ReminderKind.MEDICAMENTO) "Amoxicilina 250 mg cada 12 h por 7 días" else "Control post operatorio")
        DateButton("Fecha y hora", due, Modifier.fillMaxWidth(), withTime = true) { due = it }
        Text("Avisarme", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(0 to "A la hora", 60 to "1 h antes", 1440 to "1 día antes", 2880 to "2 días").forEach { (m, l) ->
                FilterChip(selected = lead == m, onClick = { lead = m }, label = { Text(l, maxLines = 1) })
            }
        }
        if (owner?.phone?.isNotBlank() == true) {
            Text("Mensaje para el dueño (WhatsApp)", style = MaterialTheme.typography.labelLarge)
            OutlinedTextField(
                message, { message = it }, placeholder = { Text("Se arma solo si lo dejas vacío") },
                modifier = Modifier.fillMaxWidth().height(120.dp),
            )
            TextButton(onClick = { message = autoMessage() }) { Text("Armar mensaje automático") }
        }
        FormError(error)
    }
}

// ---------------------------------------------------------------------------
// Peso, cobros y gastos
// ---------------------------------------------------------------------------

@Composable
fun WeightForm(onDismiss: () -> Unit, onSave: (Double, Long) -> Unit) {
    var kg by remember { mutableStateOf("") }
    var date by remember { mutableLongStateOf(System.currentTimeMillis()) }
    FormDialog("Registrar peso", onDismiss, onSave = { parseNum(kg)?.let { if (it > 0) onSave(it, date) } }) {
        NumberField(kg, { kg = it }, "Peso", Modifier.fillMaxWidth(), "kg")
        DateButton("Fecha", date, Modifier.fillMaxWidth()) { date = it }
    }
}

@Composable
fun ChargeForm(state: VetState, ownerId: Long, patientId: Long = 0L, onDismiss: () -> Unit, onSaved: () -> Unit) {
    var concept by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var paid by remember { mutableStateOf(false) }
    var date by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var oid by remember { mutableLongStateOf(ownerId) }
    var error by remember { mutableStateOf<String?>(null) }
    FormDialog("Nuevo cobro", onDismiss, onSave = {
        val a = parseNum(amount)
        when {
            oid == 0L -> error = "Elige el cliente."
            concept.isBlank() -> error = "Escribe el concepto."
            a == null || a <= 0 -> error = "Escribe el monto."
            else -> { state.saveCharge(Charge(0L, oid, patientId, date, concept.trim(), a, if (paid) a else 0.0)); onSaved() }
        }
    }) {
        if (ownerId == 0L) {
            Text("Cliente", style = MaterialTheme.typography.labelLarge)
            val sel = state.owner(oid)
            if (sel != null) Row(verticalAlignment = Alignment.CenterVertically) {
                Text(sel.name, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f)); TextButton(onClick = { oid = 0L }) { Text("Cambiar") }
            } else {
                var q by remember { mutableStateOf("") }
                OutlinedTextField(q, { q = it }, label = { Text("Buscar cliente") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                state.data.owners.filter { q.length >= 1 && it.name.lowercase().contains(q.lowercase()) }.take(5).forEach { o ->
                    TextButton(onClick = { oid = o.id }) { Text(o.name) }
                }
            }
        }
        TextFieldVoice(concept, { concept = it; error = null }, "Concepto", placeholder = "Consulta, vacuna, medicamento...")
        NumberField(amount, { amount = it; error = null }, "Monto", Modifier.fillMaxWidth(), state.settings.currency)
        DateButton("Fecha", date, Modifier.fillMaxWidth()) { date = it }
        Row(verticalAlignment = Alignment.CenterVertically) { Switch(paid, { paid = it }); Spacer(Modifier.width(8.dp)); Text(if (paid) "Pagado" else "Queda por cobrar") }
        FormError(error)
    }
}

@Composable
fun ExpenseForm(state: VetState, onDismiss: () -> Unit, onSaved: () -> Unit) {
    var concept by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Medicamentos") }
    var date by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var error by remember { mutableStateOf<String?>(null) }
    FormDialog("Nuevo gasto", onDismiss, onSave = {
        val a = parseNum(amount)
        if (concept.isBlank() || a == null || a <= 0) error = "Escribe el concepto y el monto."
        else { state.saveExpense(Expense(0L, date, concept.trim(), category, a)); onSaved() }
    }) {
        TextFieldVoice(concept, { concept = it; error = null }, "Concepto")
        NumberField(amount, { amount = it; error = null }, "Monto", Modifier.fillMaxWidth(), state.settings.currency)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            EXPENSE_CATEGORIES.forEach { c -> FilterChip(selected = category == c, onClick = { category = c }, label = { Text(c) }) }
        }
        DateButton("Fecha", date, Modifier.fillMaxWidth()) { date = it }
        FormError(error)
    }
}
