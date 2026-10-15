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
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.util.Calendar

@Composable
fun ChargeRow(state: VetState, c: Charge, showOwner: Boolean = true) {
    val context = LocalContext.current
    var paying by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    val cur = state.settings.currency
    val owner = state.owner(c.ownerId)
    VetCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(c.concept, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(fmtDate(c.dateMillis), if (showOwner) owner?.name else null, state.patient(c.patientId)?.name).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    money(c.amount, cur) + if (c.isPaid) " · pagado" else if (c.paid > 0) " · abonó ${money(c.paid, cur)}" else "",
                    color = if (c.isPaid) VetGreen else VetAmber, style = MaterialTheme.typography.labelMedium,
                )
            }
            if (!c.isPaid) {
                if (owner?.phone?.isNotBlank() == true) IconButton(onClick = {
                    WhatsApp.open(context, owner.phone, Messages.debt(owner, money(c.pending, cur), c.concept, state.settings))
                }) { Icon(Icons.AutoMirrored.Rounded.Send, "Recordar pago", tint = WaGreen) }
                IconButton(onClick = { paying = true }) { Icon(Icons.Rounded.Payments, "Registrar pago", tint = VetGreen) }
            }
            IconButton(onClick = { confirm = true }) { Icon(Icons.Rounded.Delete, "Borrar", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
    if (paying) {
        var amount by remember { mutableStateOf(num(c.pending)) }
        FormDialog("Registrar pago", { paying = false }, onSave = {
            parseNum(amount)?.let { if (it > 0) state.pay(c, it) }
            paying = false
        }) {
            Text("${c.concept} · pendiente ${money(c.pending, cur)}")
            NumberField(amount, { amount = it }, "Monto recibido", Modifier.fillMaxWidth(), cur)
        }
    }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text("¿Borrar este cobro?") },
        confirmButton = { TextButton(onClick = { confirm = false; state.deleteCharge(c) }) { Text("Borrar", color = VetDanger) } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancelar") } },
    )
}

@Composable
fun AccountsScreen(state: VetState) {
    var tab by remember { mutableIntStateOf(0) }
    var monthOffset by remember { mutableIntStateOf(0) }
    var newCharge by remember { mutableStateOf(false) }
    var newExpense by remember { mutableStateOf(false) }
    val cur = state.settings.currency
    val cal = Calendar.getInstance().apply { add(Calendar.MONTH, monthOffset) }
    val key = fmt(cal.timeInMillis, "yyyyMM")
    val charges = state.data.charges.filter { fmt(it.dateMillis, "yyyyMM") == key }
    val expenses = state.data.expenses.filter { fmt(it.dateMillis, "yyyyMM") == key }.sortedByDescending { it.dateMillis }
    val billed = charges.sumOf { it.amount }
    val collected = charges.sumOf { it.paid }
    val spent = expenses.sumOf { it.amount }
    val pendingAll = state.data.charges.filter { !it.isPaid }.sortedBy { it.dateMillis }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text("Cuentas", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { monthOffset-- }) { Icon(Icons.Rounded.ChevronLeft, "Mes anterior") }
                Text(fmt(cal.timeInMillis, "MMMM yyyy").replaceFirstChar { it.uppercase() }, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                IconButton(onClick = { monthOffset++ }, enabled = monthOffset < 0) { Icon(Icons.Rounded.ChevronRight, "Mes siguiente") }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Metric("Facturado", money(billed, cur), VetBlue, Modifier.weight(1f))
                Metric("Cobrado", money(collected, cur), VetGreen, Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Metric("Gastos", money(spent, cur), VetAmber, Modifier.weight(1f))
                Metric("Ganancia", money(collected - spent, cur), if (collected - spent >= 0) VetGreen else VetDanger, Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { newCharge = true }, modifier = Modifier.weight(1f)) { Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(4.dp)); Text("Cobro") }
                OutlinedButton(onClick = { newExpense = true }, modifier = Modifier.weight(1f)) { Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(4.dp)); Text("Gasto") }
            }
        }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = tab == 0, onClick = { tab = 0 }, label = { Text("Por cobrar (${pendingAll.size})") })
                FilterChip(selected = tab == 1, onClick = { tab = 1 }, label = { Text("Cobros del mes") })
                FilterChip(selected = tab == 2, onClick = { tab = 2 }, label = { Text("Gastos") })
            }
        }
        when (tab) {
            0 -> {
                if (pendingAll.isEmpty()) item { EmptyState(Icons.Rounded.Payments, "Nadie te debe", "Los cobros sin pagar aparecen aquí.") }
                else item { Text("Total por cobrar: ${money(pendingAll.sumOf { it.pending }, cur)}", fontWeight = FontWeight.SemiBold, color = VetAmber) }
                items(pendingAll, key = { it.id }) { c -> ChargeRow(state, c) }
            }
            1 -> {
                if (charges.isEmpty()) item { EmptyState(Icons.AutoMirrored.Rounded.ReceiptLong, "Sin cobros este mes", "Se agregan desde la consulta, la vacuna o el botón Cobro.") }
                items(charges.sortedByDescending { it.dateMillis }, key = { it.id }) { c -> ChargeRow(state, c) }
            }
            else -> {
                if (expenses.isEmpty()) item { EmptyState(Icons.AutoMirrored.Rounded.ReceiptLong, "Sin gastos este mes", "Registra compras de medicamentos, insumos, alquiler...") }
                else item {
                    val byCat = expenses.groupBy { it.category }.mapValues { e -> e.value.sumOf { it.amount } }.entries.sortedByDescending { it.value }
                    VetCard { byCat.forEach { (k, v) -> LabeledRow(k, money(v, cur)) } }
                }
                items(expenses, key = { it.id }) { e ->
                    var confirm by remember { mutableStateOf(false) }
                    VetCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(e.concept, fontWeight = FontWeight.SemiBold)
                                Text("${fmtDate(e.dateMillis)} · ${e.category}", style = MaterialTheme.typography.bodySmall)
                            }
                            Text(money(e.amount, cur), color = VetAmber, fontWeight = FontWeight.SemiBold)
                            IconButton(onClick = { confirm = true }) { Icon(Icons.Rounded.Delete, "Borrar", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                    }
                    if (confirm) AlertDialog(
                        onDismissRequest = { confirm = false }, title = { Text("¿Borrar este gasto?") },
                        confirmButton = { TextButton(onClick = { confirm = false; state.deleteExpense(e) }) { Text("Borrar", color = VetDanger) } },
                        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancelar") } },
                    )
                }
            }
        }
        item { Spacer(Modifier.size(80.dp)) }
    }
    if (newCharge) ChargeForm(state, 0L, onDismiss = { newCharge = false }, onSaved = { newCharge = false })
    if (newExpense) ExpenseForm(state, onDismiss = { newExpense = false }, onSaved = { newExpense = false })
}

// ---------------------------------------------------------------------------
// Ficha del dueño
// ---------------------------------------------------------------------------

@Composable
fun OwnerDetail(state: VetState, ownerId: Long, onBack: () -> Unit, onOpenPatient: (Long) -> Unit) {
    val context = LocalContext.current
    val o = state.owner(ownerId) ?: run { androidx.compose.runtime.LaunchedEffect(Unit) { onBack() }; return }
    var edit by remember { mutableStateOf(false) }
    var newPet by remember { mutableStateOf(false) }
    var newCharge by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val pets = state.patientsOf(o.id)
    val charges = state.chargesOf(o.id)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(o.name, fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Volver") } },
                actions = {
                    IconButton(onClick = { edit = true }) { Icon(Icons.Rounded.Edit, "Editar") }
                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Rounded.Delete, "Eliminar") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                VetCard {
                    LabeledRow("Teléfono", o.phone)
                    LabeledRow("Correo", o.email)
                    LabeledRow("Dirección", o.address)
                    LabeledRow("Notas", o.notes)
                    if (o.phone.isNotBlank()) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { WhatsApp.call(context, o.phone) }, modifier = Modifier.weight(1f)) { Icon(Icons.Rounded.Call, null); Spacer(Modifier.width(4.dp)); Text("Llamar") }
                        Button(onClick = { WhatsApp.open(context, o.phone, Messages.greeting(o) + ", ") }, modifier = Modifier.weight(1f)) { Icon(Icons.AutoMirrored.Rounded.Send, null); Spacer(Modifier.width(4.dp)); Text("WhatsApp") }
                    }
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionTitle("Pacientes (${pets.size})", Modifier.weight(1f))
                    TextButton(onClick = { newPet = true }) { Text("+ Agregar") }
                }
            }
            items(pets, key = { it.id }) { p ->
                VetCard(onClick = { onOpenPatient(p.id) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar(speciesEmoji(p.species), 36)
                        Spacer(Modifier.width(8.dp))
                        Column { Text(p.name, fontWeight = FontWeight.SemiBold); Text(listOf(p.breed, ageText(p.birthMillis)).filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionTitle("Cuenta · debe ${money(state.pendingOf(o.id), state.settings.currency)}", Modifier.weight(1f))
                    TextButton(onClick = { newCharge = true }) { Text("+ Cobro") }
                }
            }
            items(charges, key = { it.id }) { c -> ChargeRow(state, c, showOwner = false) }
            item { Spacer(Modifier.size(40.dp)) }
        }
    }
    if (edit) OwnerForm(o, onDismiss = { edit = false }) { state.saveOwner(it); edit = false }
    if (newPet) PatientForm(state, null, presetOwnerId = o.id, onDismiss = { newPet = false }, onSave = { newPet = false; onOpenPatient(it.id) })
    if (newCharge) ChargeForm(state, o.id, onDismiss = { newCharge = false }, onSaved = { newCharge = false })
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("¿Eliminar a ${o.name}?") },
        text = { Text("Se borran también sus ${pets.size} paciente(s), con su historia, y sus cobros.") },
        confirmButton = { TextButton(onClick = { confirmDelete = false; state.deleteOwner(o); onBack() }) { Text("Eliminar", color = VetDanger) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } },
    )
}
