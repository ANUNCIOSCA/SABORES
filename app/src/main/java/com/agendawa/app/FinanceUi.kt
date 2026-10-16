@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.agendawa.app

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import java.util.Calendar

private enum class FinanceHubTab(val label: String) { SUMMARY("Resumen"), PAYABLE("Por pagar"), LOANS("Préstamos"), CLIENTS("Clientes"), MOVEMENTS("Movimientos") }

const val FINANCE_TAB_PAYABLE = 1
const val FINANCE_TAB_LOANS = 2

private data class EntryEditorRequest(
    val entry: FinanceEntry? = null,
    val type: FinanceType = FinanceType.RECEIVABLE,
    val clientId: Long? = null,
    val isLoan: Boolean = false,
)

/** Etiqueta corta del tipo de movimiento. */
private fun entryKindLabel(e: FinanceEntry) = when {
    e.isLoan && e.type == FinanceType.RECEIVABLE -> "Presté · me deben"
    e.isLoan -> "Me prestaron · debo"
    e.type == FinanceType.RECEIVABLE -> "Por cobrar"
    else -> "Por pagar"
}

private fun currencyLabel(code: String) = when (code) {
    "VES" -> "Bs"
    "EUR" -> "€"
    "COP" -> "COP $"
    else -> "$"
}

private fun fmoney(amount: Double, currency: String) = money(amount, currencyLabel(currency))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinanceHubScreen(
    state: AppState,
    startTab: Int = 0,
    onBack: () -> Unit,
    onOpenDrawer: () -> Unit,
    showMessage: (String) -> Unit,
    snackbar: SnackbarHostState,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var tab by remember(startTab) { mutableStateOf(FinanceHubTab.entries.getOrElse(startTab) { FinanceHubTab.SUMMARY }) }
    var payEntry by remember { mutableStateOf<FinanceEntry?>(null) }
    var clientEditor by remember { mutableStateOf<Client?>(null) }
    var showClientEditor by remember { mutableStateOf(false) }
    var entryEditor by remember { mutableStateOf<EntryEditorRequest?>(null) }
    var clientDetail by remember { mutableStateOf<Client?>(null) }
    var confirmDeleteClient by remember { mutableStateOf<Client?>(null) }
    var confirmDeleteEntry by remember { mutableStateOf<FinanceEntry?>(null) }
    var importResult by remember { mutableStateOf<FinanceImportResult?>(null) }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { FinanceImportManager.importFile(context, uri, state.clients.toList(), state.finance.toList()) }
                .onSuccess { importResult = it }
                .onFailure { showMessage("No se pudo importar el archivo: ${it.message}") }
        }
    }

    val markPaid: (FinanceEntry) -> Unit = { entry ->
        val saved = state.markFinancePaid(entry)
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val r = snackbar.showSnackbar("Movimiento marcado como pagado", actionLabel = "Pasar a Mis Gastos", withDismissAction = true)
            if (r == SnackbarResult.ActionPerformed) {
                state.registerFinanceInExpenses(saved)
                snackbar.showSnackbar(if (saved.type == FinanceType.RECEIVABLE) "Ingreso registrado en Mis Gastos" else "Gasto registrado en Mis Gastos")
            }
        }
    }

    val remindByWhatsApp: (FinanceEntry) -> Unit = { entry ->
        val client = state.clients.firstOrNull { it.id == entry.clientId }
        val text = if (entry.isLoan && entry.type == FinanceType.RECEIVABLE)
            "Hola ${entry.counterparty}, te escribo para recordarte el préstamo de \"${entry.concept}\". Saldo pendiente: ${fmoney(entry.balance, entry.currency)}. ¡Gracias!"
        else if (entry.isLoan)
            "Hola ${entry.counterparty}, te escribo por el préstamo que me hiciste (\"${entry.concept}\"). Me queda pendiente ${fmoney(entry.balance, entry.currency)}."
        else if (entry.type == FinanceType.RECEIVABLE)
            "Hola ${entry.counterparty}, te escribo para recordarte el pago pendiente de \"${entry.concept}\" por ${fmoney(entry.balance, entry.currency)}. ¡Gracias!"
        else
            "Hola ${entry.counterparty}, te escribo por el pago de \"${entry.concept}\" (${fmoney(entry.balance, entry.currency)})."
        if (!WhatsApp.open(context, client?.phone.orEmpty(), text)) showMessage("No se pudo abrir WhatsApp.")
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Clientes y Finanzas", fontWeight = FontWeight.Bold)
                        Text("CRM, cobros, pagos y recordatorios", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    Row {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Volver") }
                    }
                },
                actions = {
                    IconButton(onClick = onOpenDrawer) { Icon(Icons.Rounded.Menu, "Menú") }
                    IconButton(onClick = {
                        importLauncher.launch(
                            arrayOf(
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                "text/csv", "text/comma-separated-values", "text/tab-separated-values", "text/plain",
                                "application/json", "application/octet-stream",
                            ),
                        )
                    }) { Icon(Icons.Rounded.FileUpload, "Importar de archivo") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        floatingActionButton = {
            if (tab == FinanceHubTab.PAYABLE) {
                ExtendedFloatingActionButton(
                    onClick = { entryEditor = EntryEditorRequest(type = FinanceType.PAYABLE) },
                    icon = { Icon(Icons.Rounded.Add, null) },
                    text = { Text("Cuenta por pagar") },
                    containerColor = AgendaDanger,
                    contentColor = Color.White,
                )
            } else if (tab == FinanceHubTab.LOANS) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ExtendedFloatingActionButton(
                        onClick = { entryEditor = EntryEditorRequest(type = FinanceType.PAYABLE, isLoan = true) },
                        icon = { Icon(Icons.Rounded.Add, null) },
                        text = { Text("Me prestaron") },
                        containerColor = Color(0xFFFFE4E4),
                        contentColor = AgendaDanger,
                    )
                    ExtendedFloatingActionButton(
                        onClick = { entryEditor = EntryEditorRequest(type = FinanceType.RECEIVABLE, isLoan = true) },
                        icon = { Icon(Icons.Rounded.Add, null) },
                        text = { Text("Presté") },
                        containerColor = AgendaGreen,
                        contentColor = Color.White,
                    )
                }
            } else if (tab == FinanceHubTab.CLIENTS) {
                ExtendedFloatingActionButton(
                    onClick = { clientEditor = null; showClientEditor = true },
                    icon = { Icon(Icons.Rounded.Add, null) },
                    text = { Text("Nuevo cliente") },
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ExtendedFloatingActionButton(
                        onClick = { entryEditor = EntryEditorRequest(type = FinanceType.PAYABLE) },
                        icon = { Icon(Icons.Rounded.Add, null) },
                        text = { Text("Pago") },
                        containerColor = Color(0xFFFFE4E4),
                        contentColor = AgendaDanger,
                    )
                    ExtendedFloatingActionButton(
                        onClick = { entryEditor = EntryEditorRequest(type = FinanceType.RECEIVABLE) },
                        icon = { Icon(Icons.Rounded.Add, null) },
                        text = { Text("Cobro") },
                        containerColor = AgendaGreen,
                        contentColor = Color.White,
                    )
                }
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            ScrollableTabRow(selectedTabIndex = tab.ordinal, containerColor = MaterialTheme.colorScheme.background, edgePadding = 8.dp) {
                FinanceHubTab.entries.forEach { t ->
                    Tab(selected = tab == t, onClick = { tab = t }, text = { Text(t.label) })
                }
            }
            when (tab) {
                FinanceHubTab.SUMMARY -> FinanceSummary(state, onOpenEntry = { entryEditor = EntryEditorRequest(entry = it) }, onMarkPaid = markPaid, onImport = {
                    importLauncher.launch(arrayOf("*/*"))
                })
                FinanceHubTab.PAYABLE -> PayablesPanel(
                    state,
                    onOpen = { entryEditor = EntryEditorRequest(entry = it) },
                    onPay = { payEntry = it },
                    onMarkPaid = markPaid,
                    onWhatsApp = remindByWhatsApp,
                )
                FinanceHubTab.LOANS -> LoansPanel(
                    state,
                    onOpen = { entryEditor = EntryEditorRequest(entry = it) },
                    onPay = { payEntry = it },
                    onMarkPaid = markPaid,
                    onWhatsApp = remindByWhatsApp,
                )
                FinanceHubTab.CLIENTS -> ClientsPanel(state, onOpen = { clientDetail = it })
                FinanceHubTab.MOVEMENTS -> MovementsPanel(
                    state,
                    onOpen = { entryEditor = EntryEditorRequest(entry = it) },
                    onMarkPaid = markPaid,
                    onDelete = { confirmDeleteEntry = it },
                    onWhatsApp = remindByWhatsApp,
                )
            }
        }
    }

    if (showClientEditor) {
        ClientEditorDialog(
            initial = clientEditor,
            onDismiss = { showClientEditor = false },
            onSave = { c ->
                val saved = state.saveClient(c)
                showClientEditor = false
                if (clientDetail?.id == saved.id) clientDetail = saved
                showMessage("Cliente guardado")
            },
        )
    }

    clientDetail?.let { client ->
        ClientDetailDialog(
            state = state,
            client = client,
            onDismiss = { clientDetail = null },
            onEdit = { clientEditor = client; showClientEditor = true },
            onDelete = { confirmDeleteClient = client },
            onNewEntry = { type -> entryEditor = EntryEditorRequest(type = type, clientId = client.id) },
            onOpenEntry = { entryEditor = EntryEditorRequest(entry = it) },
            onWhatsApp = {
                if (!WhatsApp.open(context, client.phone, "Hola ${client.name}, ")) showMessage("No se pudo abrir WhatsApp.")
            },
        )
    }

    entryEditor?.let { req ->
        FinanceEntryEditorDialog(
            state = state,
            request = req,
            onDismiss = { entryEditor = null },
            onSave = { entry, withReminder ->
                state.saveFinanceEntry(entry, withReminder)
                entryEditor = null
                showMessage(if (withReminder && !entry.isPaid) "Movimiento y recordatorio guardados" else "Movimiento guardado")
            },
            onDelete = { confirmDeleteEntry = it; entryEditor = null },
        )
    }

    payEntry?.let { e ->
        PartialPaymentDialog(
            entry = e,
            onDismiss = { payEntry = null },
            onSave = { amount ->
                val saved = state.saveFinanceEntry(
                    e.copy(paidAmount = (e.paidAmount + amount).coerceAtMost(e.totalAmount)),
                    withReminder = e.reminderTaskId != null,
                )
                payEntry = null
                showMessage(if (saved.isPaid) "¡Saldado! Ya no queda deuda." else "Abono guardado. Queda ${fmoney(saved.balance, saved.currency)}")
            },
        )
    }

    confirmDeleteClient?.let { c ->
        ConfirmDeleteDialog("Eliminar cliente", c.name, onConfirm = {
            state.deleteClient(c); confirmDeleteClient = null; clientDetail = null; showMessage("Cliente eliminado")
        }, onDismiss = { confirmDeleteClient = null })
    }

    confirmDeleteEntry?.let { e ->
        ConfirmDeleteDialog("Eliminar movimiento", e.concept, onConfirm = {
            state.deleteFinanceEntry(e); confirmDeleteEntry = null; showMessage("Movimiento eliminado")
        }, onDismiss = { confirmDeleteEntry = null })
    }

    importResult?.let { r ->
        AlertDialog(
            onDismissRequest = { importResult = null },
            title = { Text("Importar de archivo") },
            text = {
                Column {
                    Text(r.fileName, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    Text("${r.entries.size} movimientos de ${r.sourceRows} filas · ${r.clients.size} clientes nuevos")
                    if (r.skippedRows > 0) Text("${r.skippedRows} filas omitidas (sin nombre, sin monto o repetidas)", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Text("Total por cobrar: ${money(r.entries.filter { it.type == FinanceType.RECEIVABLE }.sumOf { it.balance })}")
                    Text("Total por pagar: ${money(r.entries.filter { it.type == FinanceType.PAYABLE }.sumOf { it.balance })}")
                }
            },
            confirmButton = {
                Button(onClick = {
                    state.importFinance(r)
                    showMessage("Importados ${r.entries.size} registros. Total: ${money(r.entries.sumOf { it.balance })}")
                    importResult = null
                }) { Text("Importar") }
            },
            dismissButton = { TextButton(onClick = { importResult = null }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun FinanceSummary(
    state: AppState,
    onOpenEntry: (FinanceEntry) -> Unit,
    onMarkPaid: (FinanceEntry) -> Unit,
    onImport: () -> Unit,
) {
    val pending = state.finance.filter { !it.isPaid }
    val currencies = (pending.map { it.currency }.distinct().ifEmpty { listOf("USD") })
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text("Registra lo que te deben pagar y también lo que tú debes cancelar.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
        currencies.forEach { cur ->
            item(key = "sum_$cur") {
                val rec = pending.filter { it.currency == cur && it.type == FinanceType.RECEIVABLE }
                val pay = pending.filter { it.currency == cur && it.type == FinanceType.PAYABLE }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FinanceMetric("Por cobrar ${if (currencies.size > 1) cur else ""}", fmoney(rec.sumOf { it.balance }, cur), "${rec.size} pendientes", AgendaGreen, Modifier.weight(1f))
                    FinanceMetric("Por pagar ${if (currencies.size > 1) cur else ""}", fmoney(pay.sumOf { it.balance }, cur), "${pay.size} pendientes", AgendaDanger, Modifier.weight(1f))
                }
            }
        }
        item {
            val overdue = pending.count { it.isOverdue() }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FinanceMetric("Vencidos", overdue.toString(), "requieren atención", if (overdue > 0) AgendaDanger else AgendaMuted, Modifier.weight(1f))
                FinanceMetric("Clientes", state.clients.size.toString(), "registrados", AgendaBlue, Modifier.weight(1f))
            }
        }
        item {
            OutlinedButton(onClick = onImport, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.FileUpload, null); Spacer(Modifier.width(6.dp)); Text("Importar de archivo")
            }
            Text(
                "Excel, CSV/TSV o JSON. Agenda WA detecta clientes, montos, abonos, saldos y fechas.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        item { SectionTitle("Próximos movimientos") }
        val upcoming = pending.sortedBy { it.dueDateMillis }.take(15)
        if (upcoming.isEmpty()) {
            item { EmptyState(Icons.Rounded.ReceiptLong, "Sin movimientos todavía", "Registra un cobro o un pago con los botones de abajo.") }
        } else {
            items(upcoming, key = { it.id }) { e -> EntryCard(e, onClick = { onOpenEntry(e) }, onMarkPaid = { onMarkPaid(e) }) }
        }
    }
}

@Composable
private fun FinanceMetric(title: String, value: String, subtitle: String, color: Color, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(title.trim(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun EntryCard(
    e: FinanceEntry,
    onClick: () -> Unit,
    onMarkPaid: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onWhatsApp: (() -> Unit)? = null,
    onPay: (() -> Unit)? = null,
) {
    val color = if (e.type == FinanceType.RECEIVABLE) AgendaGreen else AgendaDanger
    val overdue = e.isOverdue()
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, if (overdue) AgendaDanger.copy(alpha = 0.45f) else MaterialTheme.colorScheme.outline),
    ) {
        Column(Modifier.padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Pill(entryKindLabel(e), color)
                Spacer(Modifier.width(6.dp))
                when {
                    e.isPaid -> Pill("Pagado", AgendaBlue)
                    overdue -> Pill("Vencido", AgendaDanger)
                    else -> Pill("Pendiente", AgendaAmber)
                }
                Spacer(Modifier.weight(1f))
                if (onWhatsApp != null && !e.isPaid) IconButton(onClick = onWhatsApp) { Icon(Icons.AutoMirrored.Rounded.Chat, "WhatsApp", tint = AgendaGreen) }
                if (onPay != null && !e.isPaid) IconButton(onClick = onPay) { Icon(Icons.Rounded.Payments, "Abonar", tint = AgendaAmber) }
                if (onMarkPaid != null && !e.isPaid) IconButton(onClick = onMarkPaid) { Icon(Icons.Rounded.CheckCircle, "Marcar pagado", tint = AgendaBlue) }
                if (onDelete != null) IconButton(onClick = onDelete) { Icon(Icons.Rounded.Delete, "Eliminar", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Text(e.concept, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(e.counterparty, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Row {
                Column(Modifier.weight(1f)) {
                    Text("Saldo: ${fmoney(e.balance, e.currency)}", fontWeight = FontWeight.SemiBold, color = color)
                    Text(
                        "Total ${fmoney(e.totalAmount, e.currency)} · Abonado: ${fmoney(e.paidAmount, e.currency)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    "Vence: ${formatDate(e.dueDateMillis, "dd MMM yyyy")}${if (overdue) " · VENCIDO" else ""}",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (overdue) AgendaDanger else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Bottom).padding(end = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun PayablesPanel(
    state: AppState,
    onOpen: (FinanceEntry) -> Unit,
    onPay: (FinanceEntry) -> Unit,
    onMarkPaid: (FinanceEntry) -> Unit,
    onWhatsApp: (FinanceEntry) -> Unit,
) {
    var showPaid by remember { mutableStateOf(false) }
    val all = state.finance.filter { it.type == FinanceType.PAYABLE && !it.isLoan }
    val pending = all.filter { !it.isPaid }.sortedBy { it.dueDateMillis }
    val list = if (showPaid) all.filter { it.isPaid }.sortedByDescending { it.updatedAtMillis } else pending
    val currencies = pending.map { it.currency }.distinct().ifEmpty { listOf("USD") }
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text("Lo que tú debes pagar: servicios, proveedores, alquiler, cuotas... Te aviso en la fecha de pago.",
                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
        item {
            val now = System.currentTimeMillis()
            val week = now + 7L * 24 * 3600 * 1000
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                currencies.take(2).forEach { cur ->
                    val p = pending.filter { it.currency == cur }
                    FinanceMetric("Debo ${if (currencies.size > 1) cur else ""}", fmoney(p.sumOf { it.balance }, cur), "${p.size} pendientes", AgendaDanger, Modifier.weight(1f))
                }
                val soon = pending.count { it.dueDateMillis in now..week }
                val overdue = pending.count { it.isOverdue(now) }
                FinanceMetric(
                    if (overdue > 0) "Vencidas" else "Esta semana",
                    (if (overdue > 0) overdue else soon).toString(),
                    if (overdue > 0) "¡págalas ya!" else "por vencer",
                    if (overdue > 0) AgendaDanger else AgendaAmber,
                    Modifier.weight(1f),
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = !showPaid, onClick = { showPaid = false }, label = { Text("Pendientes") })
                FilterChip(selected = showPaid, onClick = { showPaid = true }, label = { Text("Pagadas") })
            }
        }
        if (list.isEmpty()) item {
            EmptyState(Icons.Rounded.ReceiptLong, if (showPaid) "Nada pagado todavía" else "No tienes cuentas por pagar", "Usa + Cuenta por pagar para registrar una.")
        }
        items(list, key = { it.id }) { e ->
            EntryCard(e, onClick = { onOpen(e) }, onMarkPaid = { onMarkPaid(e) }, onWhatsApp = { onWhatsApp(e) }, onPay = { onPay(e) })
        }
    }
}

@Composable
private fun LoansPanel(
    state: AppState,
    onOpen: (FinanceEntry) -> Unit,
    onPay: (FinanceEntry) -> Unit,
    onMarkPaid: (FinanceEntry) -> Unit,
    onWhatsApp: (FinanceEntry) -> Unit,
) {
    var showPaid by remember { mutableStateOf(false) }
    val loans = state.finance.filter { it.isLoan }
    val pending = loans.filter { !it.isPaid }
    val list = if (showPaid) loans.filter { it.isPaid }.sortedByDescending { it.updatedAtMillis } else pending.sortedBy { it.dueDateMillis }
    val currencies = pending.map { it.currency }.distinct().ifEmpty { listOf("USD") }
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text("Anota el dinero que prestas y el que te prestan. Registra cada abono y te recuerdo la fecha de pago.",
                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
        currencies.forEach { cur ->
            item(key = "loan_$cur") {
                val lent = pending.filter { it.currency == cur && it.type == FinanceType.RECEIVABLE }
                val owed = pending.filter { it.currency == cur && it.type == FinanceType.PAYABLE }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FinanceMetric("Me deben ${if (currencies.size > 1) cur else ""}", fmoney(lent.sumOf { it.balance }, cur), "${lent.size} préstamos", AgendaGreen, Modifier.weight(1f))
                    FinanceMetric("Yo debo ${if (currencies.size > 1) cur else ""}", fmoney(owed.sumOf { it.balance }, cur), "${owed.size} préstamos", AgendaDanger, Modifier.weight(1f))
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = !showPaid, onClick = { showPaid = false }, label = { Text("Activos") })
                FilterChip(selected = showPaid, onClick = { showPaid = true }, label = { Text("Saldados") })
            }
        }
        if (list.isEmpty()) item {
            EmptyState(Icons.Rounded.Payments, if (showPaid) "Ningún préstamo saldado" else "Sin préstamos", "Usa + Presté o + Me prestaron para anotar uno.")
        }
        items(list, key = { it.id }) { e ->
            EntryCard(e, onClick = { onOpen(e) }, onMarkPaid = { onMarkPaid(e) }, onWhatsApp = { onWhatsApp(e) }, onPay = { onPay(e) })
        }
    }
}

/** Registra un abono parcial a una cuenta o préstamo. */
@Composable
private fun PartialPaymentDialog(entry: FinanceEntry, onDismiss: () -> Unit, onSave: (Double) -> Unit) {
    var amount by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Registrar abono") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${entry.concept} · ${entry.counterparty}", fontWeight = FontWeight.SemiBold)
                Text("Saldo actual: ${fmoney(entry.balance, entry.currency)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    amount, { amount = it; error = null }, label = { Text("Monto del abono") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(),
                    isError = error != null, supportingText = { error?.let { Text(it) } },
                )
                TextButton(onClick = { amount = String.format(java.util.Locale.US, "%.2f", entry.balance) }) { Text("Pagar todo el saldo") }
            }
        },
        confirmButton = {
            Button(onClick = {
                val v = ExpenseVoiceParser.parseAmount(amount) ?: 0.0
                if (v <= 0.0) error = "Escribe un monto mayor a cero." else onSave(v)
            }) { Text("Guardar abono") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun ClientsPanel(state: AppState, onOpen: (Client) -> Unit) {
    var query by remember { mutableStateOf("") }
    val q = SmartNoteOrganizer.normalize(query)
    val list = state.clients.filter { q.isBlank() || SmartNoteOrganizer.normalize("${it.name} ${it.company} ${it.phone} ${it.email}").contains(q) }
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            OutlinedTextField(
                value = query, onValueChange = { query = it }, singleLine = true,
                placeholder = { Text("Buscar cliente") }, modifier = Modifier.fillMaxWidth(),
            )
        }
        if (state.clients.isEmpty()) {
            item { EmptyState(Icons.Rounded.People, "Todavía no tienes clientes", "Crea el primero para relacionarlo con cobros y recordatorios.") }
        }
        items(list, key = { it.id }) { c ->
            val pending = state.finance.filter { it.clientId == c.id && !it.isPaid && it.type == FinanceType.RECEIVABLE }
            Card(
                modifier = Modifier.fillMaxWidth().clickable { onOpen(c) },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Person, null, tint = AgendaBlue)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(c.name, fontWeight = FontWeight.SemiBold)
                        val sub = listOf(c.company, c.phone).filter { it.isNotBlank() }.joinToString(" · ")
                        if (sub.isNotBlank()) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (pending.isNotEmpty()) {
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Pendiente por cobrar:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(fmoney(pending.sumOf { it.balance }, pending.first().currency), color = AgendaGreen, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

private enum class MovementFilter(val label: String) { ALL("Todos"), RECEIVABLE("Por cobrar"), PAYABLE("Por pagar"), LOANS("Préstamos"), OVERDUE("Vencidos"), PAID("Pagados") }

@Composable
private fun MovementsPanel(
    state: AppState,
    onOpen: (FinanceEntry) -> Unit,
    onMarkPaid: (FinanceEntry) -> Unit,
    onDelete: (FinanceEntry) -> Unit,
    onWhatsApp: (FinanceEntry) -> Unit,
) {
    var filter by remember { mutableStateOf(MovementFilter.ALL) }
    val list = state.finance.filter {
        when (filter) {
            MovementFilter.ALL -> true
            MovementFilter.RECEIVABLE -> it.type == FinanceType.RECEIVABLE && !it.isPaid
            MovementFilter.PAYABLE -> it.type == FinanceType.PAYABLE && !it.isPaid
            MovementFilter.LOANS -> it.isLoan
            MovementFilter.OVERDUE -> it.isOverdue()
            MovementFilter.PAID -> it.isPaid
        }
    }
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(MovementFilter.entries) { f ->
                    FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(f.label) })
                }
            }
        }
        if (list.isEmpty()) item { EmptyState(Icons.Rounded.ReceiptLong, "Sin movimientos todavía", "Usa + Cobro o + Pago para registrar uno.") }
        items(list, key = { it.id }) { e ->
            EntryCard(e, onClick = { onOpen(e) }, onMarkPaid = { onMarkPaid(e) }, onDelete = { onDelete(e) }, onWhatsApp = { onWhatsApp(e) })
        }
    }
}

@Composable
private fun ClientEditorDialog(initial: Client?, onDismiss: () -> Unit, onSave: (Client) -> Unit) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var phone by remember { mutableStateOf(initial?.phone ?: "") }
    var email by remember { mutableStateOf(initial?.email ?: "") }
    var company by remember { mutableStateOf(initial?.company ?: "") }
    var notes by remember { mutableStateOf(initial?.notes ?: "") }
    var error by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Nuevo cliente" else "Editar cliente") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it; error = false }, label = { Text("Nombre") }, singleLine = true, isError = error,
                    supportingText = { if (error) Text("Escribe el nombre del cliente.") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(phone, { phone = it }, label = { Text("Teléfono") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(email, { email = it }, label = { Text("Correo") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(company, { company = it }, label = { Text("Empresa o proyecto") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(notes, { notes = it }, label = { Text("Notas del cliente") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(onClick = {
                if (name.isBlank()) error = true
                else onSave(
                    (initial ?: Client(id = 0L, name = "")).copy(
                        name = name.trim(), phone = phone.trim(), email = email.trim(), company = company.trim(), notes = notes.trim(),
                        updatedAtMillis = System.currentTimeMillis(),
                    ),
                )
            }) { Text("Guardar cliente") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun ClientDetailDialog(
    state: AppState,
    client: Client,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onNewEntry: (FinanceType) -> Unit,
    onOpenEntry: (FinanceEntry) -> Unit,
    onWhatsApp: () -> Unit,
) {
    val entries = state.finance.filter { it.clientId == client.id }
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = WideDialogProperties,
        modifier = Modifier.fillMaxWidth().dialogSafePadding().padding(16.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(client.name, modifier = Modifier.weight(1f))
                IconButton(onClick = onEdit) { Icon(Icons.Rounded.Edit, "Editar") }
                IconButton(onClick = onDelete) { Icon(Icons.Rounded.Delete, "Eliminar") }
            }
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (client.company.isNotBlank()) Text(client.company, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (client.phone.isNotBlank()) Text("Teléfono: ${client.phone}")
                if (client.email.isNotBlank()) Text("Correo: ${client.email}")
                if (client.notes.isNotBlank()) Text(client.notes, style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (client.phone.isNotBlank()) OutlinedButton(onClick = onWhatsApp, modifier = Modifier.weight(1f)) { Text("WhatsApp") }
                    Button(onClick = { onNewEntry(FinanceType.RECEIVABLE) }, modifier = Modifier.weight(1f)) { Text("Registrar nuevo cobro") }
                }
                val pending = entries.filter { !it.isPaid && it.type == FinanceType.RECEIVABLE }
                if (pending.isNotEmpty()) {
                    Text("Pendiente por cobrar: ${fmoney(pending.sumOf { it.balance }, pending.first().currency)}", fontWeight = FontWeight.Bold, color = AgendaGreen)
                }
                if (entries.isEmpty()) Text("Sin movimientos todavía", color = MaterialTheme.colorScheme.onSurfaceVariant)
                entries.forEach { e -> EntryCard(e, onClick = { onOpenEntry(e) }) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FinanceEntryEditorDialog(
    state: AppState,
    request: EntryEditorRequest,
    onDismiss: () -> Unit,
    onSave: (FinanceEntry, Boolean) -> Unit,
    onDelete: (FinanceEntry) -> Unit,
) {
    val context = LocalContext.current
    val existing = request.entry
    val defaultDue = remember {
        Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 7); set(Calendar.HOUR_OF_DAY, 9); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0) }.timeInMillis
    }
    var type by remember { mutableStateOf(existing?.type ?: request.type) }
    var isLoan by remember { mutableStateOf(existing?.isLoan ?: request.isLoan) }
    var clientId by remember { mutableStateOf(existing?.clientId ?: request.clientId) }
    var counterparty by remember { mutableStateOf(existing?.counterparty ?: state.clients.firstOrNull { it.id == request.clientId }?.name ?: "") }
    var concept by remember { mutableStateOf(existing?.concept ?: "") }
    var total by remember { mutableStateOf(existing?.totalAmount?.let { String.format(java.util.Locale.US, "%.2f", it) } ?: "") }
    var paid by remember { mutableStateOf(existing?.paidAmount?.takeIf { it > 0 }?.let { String.format(java.util.Locale.US, "%.2f", it) } ?: "") }
    var currency by remember { mutableStateOf(existing?.currency ?: "USD") }
    var due by remember { mutableLongStateOf(existing?.dueDateMillis ?: defaultDue) }
    var notes by remember { mutableStateOf(existing?.notes ?: "") }
    var withReminder by remember { mutableStateOf(existing?.reminderTaskId != null || existing == null) }
    var showClientMenu by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = WideDialogProperties,
        modifier = Modifier.fillMaxWidth().dialogSafePadding().padding(16.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    when {
                        existing != null && isLoan -> "Editar préstamo"
                        existing != null -> "Editar movimiento"
                        isLoan && type == FinanceType.RECEIVABLE -> "Préstamo que hice"
                        isLoan -> "Préstamo que me hicieron"
                        type == FinanceType.RECEIVABLE -> "Cuenta por cobrar"
                        else -> "Cuenta por pagar"
                    },
                    modifier = Modifier.weight(1f),
                )
                if (existing != null) IconButton(onClick = { onDelete(existing) }) { Icon(Icons.Rounded.Delete, "Eliminar") }
            }
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = type == FinanceType.RECEIVABLE, onClick = { type = FinanceType.RECEIVABLE },
                        shape = SegmentedButtonDefaults.itemShape(0, 2),
                    ) { Text(if (isLoan) "Yo presté" else "Me pagan") }
                    SegmentedButton(
                        selected = type == FinanceType.PAYABLE, onClick = { type = FinanceType.PAYABLE },
                        shape = SegmentedButtonDefaults.itemShape(1, 2),
                    ) { Text(if (isLoan) "Me prestaron" else "Yo pago") }
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { isLoan = !isLoan }) {
                    Checkbox(checked = isLoan, onCheckedChange = { isLoan = it })
                    Text("Es un préstamo")
                }
                Box {
                    OutlinedTextField(
                        value = counterparty,
                        onValueChange = { counterparty = it; clientId = state.clients.firstOrNull { c -> c.name.equals(it.trim(), true) }?.id },
                        label = {
                            Text(
                                when {
                                    isLoan && type == FinanceType.RECEIVABLE -> "¿A quién le prestaste?"
                                    isLoan -> "¿Quién te prestó?"
                                    type == FinanceType.RECEIVABLE -> "Cliente / persona"
                                    else -> "Proveedor / a quién pagas"
                                },
                            )
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            if (state.clients.isNotEmpty()) IconButton(onClick = { showClientMenu = true }) { Icon(Icons.Rounded.People, "Relacionar cliente") }
                        },
                        supportingText = { Text(if (clientId != null) "Cliente relacionado" else "Sin cliente") },
                    )
                    DropdownMenu(expanded = showClientMenu, onDismissRequest = { showClientMenu = false }) {
                        state.clients.forEach { c ->
                            DropdownMenuItem(text = { Text(c.name) }, onClick = { clientId = c.id; counterparty = c.name; showClientMenu = false })
                        }
                    }
                }
                OutlinedTextField(concept, { concept = it }, label = { Text("Concepto") }, placeholder = { Text(if (isLoan) "Ej. Préstamo para la moto, adelanto..." else "Ej. Diseño, hosting, suscripción...") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(total, { total = it }, label = { Text(if (isLoan) "Monto prestado" else "Monto total") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                    OutlinedTextField(paid, { paid = it }, label = { Text("Pagado / abonado") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(listOf("USD", "VES", "COP", "EUR")) { c ->
                        FilterChip(selected = currency == c, onClick = { currency = c }, label = { Text(if (c == "VES") "Bs (VES)" else c) })
                    }
                }
                OutlinedButton(onClick = { pickDate(context, due) { d -> pickTime(context, d) { due = it } } }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.CalendarMonth, null); Spacer(Modifier.width(6.dp))
                    Text("${if (isLoan) "Devolver el" else "Fecha de pago"}: ${formatDate(due, "dd MMM yyyy, h:mm a")}")
                }
                OutlinedTextField(notes, { notes = it }, label = { Text("Notas") }, modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { withReminder = !withReminder }) {
                    Checkbox(checked = withReminder, onCheckedChange = { withReminder = it })
                    Text("Activar recordatorio en la fecha de pago")
                }
                if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            Button(onClick = {
                val totalValue = ExpenseVoiceParser.parseAmount(total) ?: 0.0
                val paidValue = ExpenseVoiceParser.parseAmount(paid) ?: 0.0
                when {
                    counterparty.isBlank() -> error = if (isLoan) "Escribe el nombre de la persona." else "Escribe el nombre del cliente."
                    concept.isBlank() && !isLoan -> error = "Escribe el concepto."
                    totalValue <= 0.0 -> error = "El monto total debe ser mayor a cero."
                    else -> {
                        var cid = clientId
                        if (cid == null && type == FinanceType.RECEIVABLE && !isLoan) {
                            cid = state.saveClient(Client(id = 0L, name = counterparty.trim())).id
                        }
                        onSave(
                            (existing ?: FinanceEntry(id = 0L, type = type, counterparty = "", concept = "", totalAmount = 0.0, dueDateMillis = due)).copy(
                                type = type,
                                clientId = cid,
                                counterparty = counterparty.trim(),
                                concept = concept.trim().ifBlank { "Préstamo" },
                                isLoan = isLoan,
                                totalAmount = totalValue,
                                paidAmount = paidValue.coerceIn(0.0, totalValue),
                                currency = currency,
                                dueDateMillis = due,
                                notes = notes.trim(),
                            ),
                            withReminder,
                        )
                    }
                }
            }) { Text(if (withReminder) "Guardar y activar recordatorio" else "Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
