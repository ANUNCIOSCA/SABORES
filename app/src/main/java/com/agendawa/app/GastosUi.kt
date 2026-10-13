@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.agendawa.app

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale

// ---------------------------------------------------------------------------
// Utilidades de periodo
// ---------------------------------------------------------------------------

data class YearMonth(val year: Int, val month: Int) {
    fun plus(months: Int): YearMonth {
        val c = Calendar.getInstance().apply { clear(); set(year, month, 1); add(Calendar.MONTH, months) }
        return YearMonth(c.get(Calendar.YEAR), c.get(Calendar.MONTH))
    }

    val startMillis: Long get() = Calendar.getInstance().apply { clear(); set(year, month, 1) }.timeInMillis
    val endMillis: Long get() = Calendar.getInstance().apply { clear(); set(year, month, 1); add(Calendar.MONTH, 1) }.timeInMillis - 1

    companion object {
        fun now(): YearMonth = Calendar.getInstance().let { YearMonth(it.get(Calendar.YEAR), it.get(Calendar.MONTH)) }
        fun of(millis: Long): YearMonth = Calendar.getInstance().apply { timeInMillis = millis }.let { YearMonth(it.get(Calendar.YEAR), it.get(Calendar.MONTH)) }
    }
}

val shortMonths = listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sept", "oct", "nov", "dic")
val longMonths = listOf("Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio", "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre")

fun yearOf(millis: Long): Int = Calendar.getInstance().apply { timeInMillis = millis }.get(Calendar.YEAR)

/** Formato compacto como en las capturas: "0", "1.250", "12,50". */
fun num(amount: Double): String {
    val whole = kotlin.math.abs(amount - Math.round(amount)) < 0.005
    return if (whole) String.format(esLocale, "%,.0f", amount) else String.format(esLocale, "%,.2f", amount)
}

fun List<Expense>.inRange(start: Long, end: Long) = filter { it.dateMillis in start..end }
fun List<Expense>.totalOf(kind: ExpenseKind) = filter { it.kind == kind }.sumOf { it.amount }

private enum class GastosTab { INICIO, GRAFICOS, INFORMES, YO }

private sealed interface GastosScreen {
    data object Main : GastosScreen
    data class Editor(val expense: Expense?, val kind: ExpenseKind = ExpenseKind.EXPENSE) : GastosScreen
    data object Search : GastosScreen
    data object Calendar : GastosScreen
    data class Balance(val year: Int?) : GastosScreen
    data object Categories : GastosScreen
    data class CategoryDetail(val categoryId: Long, val start: Long, val end: Long, val title: String) : GastosScreen
}

// ---------------------------------------------------------------------------
// Módulo principal
// ---------------------------------------------------------------------------

@Composable
fun GastosModule(
    state: AppState,
    onOpenDrawer: () -> Unit,
    onOpenBackup: () -> Unit,
    onOpenProfile: () -> Unit,
    onBackToAgenda: () -> Unit,
) {
    // Barra de estado con íconos claros mientras se muestra el tema oscuro
    val view = LocalView.current
    DisposableEffect(Unit) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val previous = controller?.isAppearanceLightStatusBars ?: true
        controller?.isAppearanceLightStatusBars = false
        controller?.isAppearanceLightNavigationBars = false
        onDispose {
            controller?.isAppearanceLightStatusBars = previous
            controller?.isAppearanceLightNavigationBars = previous
        }
    }

    var tab by remember { mutableStateOf(GastosTab.INICIO) }
    var period by remember { mutableStateOf(YearMonth.now()) }
    val stack = remember { mutableStateOf(listOf<GastosScreen>(GastosScreen.Main)) }
    val current = stack.value.last()
    val push: (GastosScreen) -> Unit = { stack.value = stack.value + it }
    val pop: () -> Unit = { if (stack.value.size > 1) stack.value = stack.value.dropLast(1) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val showMessage: (String) -> Unit = { m -> scope.launch { snackbar.currentSnackbarData?.dismiss(); snackbar.showSnackbar(m) } }

    BackHandler(enabled = stack.value.size > 1) { pop() }
    BackHandler(enabled = stack.value.size == 1 && tab != GastosTab.INICIO) { tab = GastosTab.INICIO }

    GastosTheme {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            when (val screen = current) {
                is GastosScreen.Main -> Scaffold(
                    containerColor = MaterialTheme.colorScheme.background,
                    snackbarHost = { SnackbarHost(snackbar) },
                    bottomBar = {
                        GastosBottomBar(
                            selected = tab,
                            onSelect = { tab = it },
                            onAdd = { push(GastosScreen.Editor(null)) },
                        )
                    },
                ) { padding ->
                    Box(Modifier.padding(padding).fillMaxSize()) {
                        when (tab) {
                            GastosTab.INICIO -> GastosHome(
                                state = state,
                                period = period,
                                onPeriod = { period = it },
                                onOpenDrawer = onOpenDrawer,
                                onSearch = { push(GastosScreen.Search) },
                                onCalendar = { push(GastosScreen.Calendar) },
                                onOpen = { push(GastosScreen.Editor(it)) },
                                onVoice = { push(GastosScreen.Editor(null)) },
                            )
                            GastosTab.GRAFICOS -> GastosCharts(
                                state = state,
                                period = period,
                                onPeriod = { period = it },
                                onOpenDrawer = onOpenDrawer,
                                onCategory = { id, start, end, title -> push(GastosScreen.CategoryDetail(id, start, end, title)) },
                            )
                            GastosTab.INFORMES -> GastosReports(
                                state = state,
                                period = period,
                                onOpenDrawer = onOpenDrawer,
                                onBalance = { push(GastosScreen.Balance(it)) },
                                showMessage = showMessage,
                            )
                            GastosTab.YO -> GastosMe(
                                state = state,
                                onOpenDrawer = onOpenDrawer,
                                onCategories = { push(GastosScreen.Categories) },
                                onBackup = onOpenBackup,
                                onProfile = onOpenProfile,
                                onBackToAgenda = onBackToAgenda,
                                showMessage = showMessage,
                            )
                        }
                    }
                }
                is GastosScreen.Editor -> ExpenseEditorScreen(
                    state = state,
                    initial = screen.expense,
                    initialKind = screen.kind,
                    defaultDate = if (period == YearMonth.now()) System.currentTimeMillis() else period.startMillis + 12 * 3_600_000L,
                    onClose = pop,
                    onManageCategories = { push(GastosScreen.Categories) },
                    onSaved = { msg -> pop(); showMessage(msg) },
                )
                is GastosScreen.Search -> ExpenseSearchScreen(state, onBack = pop, onOpen = { push(GastosScreen.Editor(it)) })
                is GastosScreen.Calendar -> ExpenseCalendarScreen(state, onBack = pop, onOpen = { push(GastosScreen.Editor(it)) })
                is GastosScreen.Balance -> BalanceReportScreen(state, initialYear = screen.year, onBack = pop)
                is GastosScreen.Categories -> CategoryManagerScreen(state, onBack = pop, showMessage = showMessage)
                is GastosScreen.CategoryDetail -> CategoryDetailScreen(
                    state, screen.categoryId, screen.start, screen.end, screen.title,
                    onBack = pop, onOpen = { push(GastosScreen.Editor(it)) },
                )
            }
        }
    }
}

@Composable
private fun GastosBottomBar(selected: GastosTab, onSelect: (GastosTab) -> Unit, onAdd: () -> Unit) {
    Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically) {
            BottomItem("Inicio", Icons.Rounded.ReceiptLong, Icons.Outlined.ReceiptLong, selected == GastosTab.INICIO, Modifier.weight(1f)) { onSelect(GastosTab.INICIO) }
            BottomItem("Gráficos", Icons.Rounded.PieChart, Icons.Outlined.PieChart, selected == GastosTab.GRAFICOS, Modifier.weight(1f)) { onSelect(GastosTab.GRAFICOS) }
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .offset(y = (-14).dp)
                        .size(62.dp)
                        .clip(CircleShape)
                        .background(GastosYellow)
                        .clickable(onClick = onAdd),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.Add, "Agregar", tint = Color(0xFF1B1B1B), modifier = Modifier.size(34.dp)) }
            }
            BottomItem("Informes", Icons.Rounded.Description, Icons.Outlined.Description, selected == GastosTab.INFORMES, Modifier.weight(1f)) { onSelect(GastosTab.INFORMES) }
            BottomItem("Yo", Icons.Rounded.Person, Icons.Outlined.Person, selected == GastosTab.YO, Modifier.weight(1f)) { onSelect(GastosTab.YO) }
        }
    }
}

@Composable
private fun BottomItem(label: String, selectedIcon: ImageVector, icon: ImageVector, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.fillMaxHeight().clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(if (selected) selectedIcon else icon, label, tint = if (selected) GastosYellow else GastosMuted)
        Text(label, fontSize = 12.sp, color = if (selected) GastosYellow else GastosMuted, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}

/** Barra superior estilo "Mis Gastos": menú, título centrado y acciones. */
@Composable
fun GastosTopBar(
    title: String,
    onMenu: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {},
) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding()
            .height(56.dp),
    ) {
        Row(Modifier.align(Alignment.CenterStart)) {
            if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Volver") }
            else if (onMenu != null) IconButton(onClick = onMenu) { Icon(Icons.Rounded.Menu, "Menú") }
        }
        Text(title, modifier = Modifier.align(Alignment.Center), fontWeight = FontWeight.Bold, fontSize = 19.sp)
        Row(Modifier.align(Alignment.CenterEnd)) { actions() }
    }
}

// ---------------------------------------------------------------------------
// Inicio (captura 1)
// ---------------------------------------------------------------------------

@Composable
private fun GastosHome(
    state: AppState,
    period: YearMonth,
    onPeriod: (YearMonth) -> Unit,
    onOpenDrawer: () -> Unit,
    onSearch: () -> Unit,
    onCalendar: () -> Unit,
    onOpen: (Expense) -> Unit,
    onVoice: () -> Unit,
) {
    var showPicker by remember { mutableStateOf(false) }
    val monthRecords = state.expenses.inRange(period.startMillis, period.endMillis)
    val spent = monthRecords.totalOf(ExpenseKind.EXPENSE)
    val earned = monthRecords.totalOf(ExpenseKind.INCOME)
    val budget = state.expenseSettings.monthlyBudget
    val categories = state.categories.associateBy { it.id }

    Column(Modifier.fillMaxSize()) {
        GastosTopBar(
            title = "Mis Gastos",
            onMenu = onOpenDrawer,
            actions = {
                IconButton(onClick = onSearch) { Icon(Icons.Rounded.Search, "Buscar") }
                IconButton(onClick = onCalendar) { Icon(Icons.Rounded.CalendarMonth, "Calendario") }
            },
        )
        // Cabecera con mes y totales
        Row(
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(start = 16.dp, end = 12.dp, bottom = 14.dp, top = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(GastosSurfaceHigh)
                    .border(1.dp, Color(0xFF444444), RoundedCornerShape(10.dp))
                    .clickable { showPicker = true }
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(period.year.toString(), fontSize = 13.sp, color = GastosText)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(shortMonths[period.month], fontSize = 20.sp, color = GastosText)
                    Icon(Icons.Rounded.ArrowDropDown, null, modifier = Modifier.size(16.dp), tint = GastosMuted)
                }
            }
            Spacer(Modifier.width(18.dp))
            HeaderValue("Gastos", num(spent), Modifier.weight(1f))
            HeaderValue("Ingresos", num(earned), Modifier.weight(1f))
            HeaderValue("Saldo", num(earned - spent), Modifier.weight(1f))
        }
        if (budget > 0) {
            val ratio = (spent / budget).coerceIn(0.0, 1.0).toFloat()
            val over = spent > budget
            Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 16.dp, vertical = 6.dp)) {
                Row {
                    Text("Presupuesto ${num(budget)}", fontSize = 12.sp, color = GastosMuted, modifier = Modifier.weight(1f))
                    Text(
                        if (over) "Excedido por ${num(spent - budget)}" else "Restante ${num(budget - spent)}",
                        fontSize = 12.sp,
                        color = if (over) GastosExpense else GastosIncome,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(GastosSurfaceHigh)) {
                    Box(Modifier.fillMaxWidth(ratio).fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(if (over) GastosExpense else GastosYellow))
                }
            }
        }

        if (monthRecords.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(Icons.Rounded.ReceiptLong, null, tint = GastosMuted, modifier = Modifier.size(64.dp))
                Spacer(Modifier.height(10.dp))
                Text("No hay registros", color = GastosMuted, fontSize = 17.sp)
                Spacer(Modifier.height(16.dp))
                TextButton(onClick = onVoice) { Text("Toca + para anotar tu primer gasto o ingreso", color = GastosYellow) }
            }
        } else {
            val byDay = monthRecords.sortedByDescending { it.dateMillis }.groupBy { startOfDay(it.dateMillis) }
            LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                byDay.forEach { (day, records) ->
                    item(key = "d_$day") {
                        DayHeader(day, records)
                    }
                    items(records, key = { it.id }) { r -> ExpenseRow(r, categories[r.categoryId], onClick = { onOpen(r) }) }
                }
            }
        }
    }

    if (showPicker) {
        MonthPickerDialog(initial = period, onDismiss = { showPicker = false }, onPick = { onPeriod(it); showPicker = false })
    }
}

@Composable
private fun HeaderValue(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(label, fontSize = 14.sp, color = GastosMuted)
        Spacer(Modifier.height(6.dp))
        Text(value, fontSize = 20.sp, color = GastosText, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun DayHeader(day: Long, records: List<Expense>) {
    val exp = records.totalOf(ExpenseKind.EXPENSE)
    val inc = records.totalOf(ExpenseKind.INCOME)
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(formatDate(day, "d MMM"), fontWeight = FontWeight.Bold, color = GastosText)
        Spacer(Modifier.width(6.dp))
        Text(formatDate(day, "EEEE"), color = GastosMuted, fontSize = 13.sp)
        Spacer(Modifier.weight(1f))
        if (inc > 0) Text("Ingresos: ${num(inc)}", color = GastosMuted, fontSize = 12.sp)
        if (inc > 0 && exp > 0) Spacer(Modifier.width(10.dp))
        if (exp > 0) Text("Gastos: ${num(exp)}", color = GastosMuted, fontSize = 12.sp)
    }
}

@Composable
fun ExpenseRow(record: Expense, category: ExpenseCategory?, showDate: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryBadge(category, 40.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(category?.name ?: "Sin categoría", color = GastosText, maxLines = 1)
            val sub = listOfNotNull(
                if (showDate) formatDate(record.dateMillis, "dd/MM/yyyy") else null,
                record.note.takeIf { it.isNotBlank() },
            ).joinToString(" · ")
            if (sub.isNotBlank()) Text(sub, color = GastosMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(
            (if (record.kind == ExpenseKind.EXPENSE) "-" else "+") + num(record.amount),
            color = if (record.kind == ExpenseKind.EXPENSE) GastosText else GastosIncome,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
fun MonthPickerDialog(initial: YearMonth, onDismiss: () -> Unit, onPick: (YearMonth) -> Unit) {
    var year by remember { mutableIntStateOf(initial.year) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { year-- }) { Icon(Icons.Rounded.ChevronLeft, "Año anterior") }
                Text(year.toString(), modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
                IconButton(onClick = { year++ }) { Icon(Icons.Rounded.ChevronRight, "Año siguiente") }
            }
        },
        text = {
            Column {
                for (row in 0 until 3) {
                    Row {
                        for (col in 0 until 4) {
                            val m = row * 4 + col
                            val selected = year == initial.year && m == initial.month
                            Box(
                                Modifier
                                    .weight(1f)
                                    .padding(4.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (selected) GastosYellow else GastosSurfaceHigh)
                                    .clickable { onPick(YearMonth(year, m)) }
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(shortMonths[m], color = if (selected) Color(0xFF1B1B1B) else GastosText, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onPick(YearMonth.now()) }) { Text("Este mes") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

// ---------------------------------------------------------------------------
// Editor de gastos / ingresos con teclado numérico
// ---------------------------------------------------------------------------

private fun evaluate(expr: String): Double? {
    if (expr.isBlank()) return null
    val tokens = Regex("[+-]?[0-9]*\\.?[0-9]+").findAll(expr.replace(" ", "")).map { it.value }.toList()
    if (tokens.isEmpty()) return null
    return tokens.sumOf { it.toDoubleOrNull() ?: 0.0 }
}

@Composable
private fun ExpenseEditorScreen(
    state: AppState,
    initial: Expense?,
    initialKind: ExpenseKind,
    defaultDate: Long,
    onClose: () -> Unit,
    onManageCategories: () -> Unit,
    onSaved: (String) -> Unit,
) {
    val context = LocalContext.current
    var kind by remember { mutableStateOf(initial?.kind ?: initialKind) }
    var categoryId by remember { mutableStateOf(initial?.categoryId) }
    var expr by remember { mutableStateOf(initial?.amount?.let { if (it % 1.0 == 0.0) it.toLong().toString() else String.format(Locale.US, "%.2f", it) } ?: "") }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    var date by remember { mutableLongStateOf(initial?.dateMillis ?: defaultDate) }
    var confirmDelete by remember { mutableStateOf(false) }

    val categories = state.categories.filter { it.kind == kind }.sortedBy { it.sortOrder }

    val dictate = rememberSpeechInput("Ej.: gasté 25 en comida · me pagaron 300 de sueldo") { text ->
        val r = ExpenseVoiceParser.parse(text, state.categories)
        kind = r.kind
        r.categoryId?.let { categoryId = it }
        if (categoryId == null || state.categoryById(categoryId!!)?.kind != r.kind) {
            categoryId = r.categoryId ?: state.categories.firstOrNull { it.kind == r.kind && it.iconKey == "more" }?.id
        }
        r.amount?.let { expr = if (it % 1.0 == 0.0) it.toLong().toString() else String.format(Locale.US, "%.2f", it) }
        if (r.note.isNotBlank()) note = r.note
        date = r.dateMillis
    }

    fun save() {
        val amount = evaluate(expr)
        val cat = categoryId?.let { state.categoryById(it) }
        when {
            cat == null || cat.kind != kind -> toast(context, "Elige una categoría")
            amount == null || amount <= 0.0 -> toast(context, "Escribe un monto mayor a cero")
            else -> {
                state.saveExpense(
                    (initial ?: Expense(id = 0L, kind = kind, amount = 0.0, categoryId = cat.id, dateMillis = date)).copy(
                        kind = kind, amount = Math.round(amount * 100) / 100.0, categoryId = cat.id, note = note.trim(), dateMillis = date,
                    ),
                )
                onSaved(if (initial == null) "${kind.label} guardado: ${num(amount)}" else "Registro actualizado")
            }
        }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // Encabezado: cancelar · Gastos/Ingresos · eliminar
        Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).statusBarsPadding().height(56.dp)) {
            TextButton(onClick = onClose, modifier = Modifier.align(Alignment.CenterStart)) { Text("Cancelar", color = GastosText) }
            Row(
                Modifier.align(Alignment.Center).clip(RoundedCornerShape(50)).border(1.dp, GastosYellow, RoundedCornerShape(50)),
            ) {
                ExpenseKind.entries.forEach { k ->
                    Text(
                        if (k == ExpenseKind.EXPENSE) "Gastos" else "Ingresos",
                        modifier = Modifier
                            .background(if (kind == k) GastosYellow else Color.Transparent)
                            .clickable {
                                if (kind != k) { kind = k; categoryId = null }
                            }
                            .padding(horizontal = 18.dp, vertical = 7.dp),
                        color = if (kind == k) Color(0xFF1B1B1B) else GastosYellow,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            Row(Modifier.align(Alignment.CenterEnd)) {
                IconButton(onClick = dictate) { Icon(Icons.Rounded.Mic, "Dictar") }
                if (initial != null) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Rounded.Delete, "Eliminar") }
            }
        }

        // Cuadrícula de categorías
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(12.dp),
        ) {
            items(categories, key = { it.id }) { c ->
                val selected = c.id == categoryId
                Column(
                    Modifier
                        .padding(4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (selected) GastosSurfaceHigh else Color.Transparent)
                        .clickable { categoryId = c.id }
                        .padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier.size(50.dp).clip(CircleShape).background(if (selected) GastosYellow else GastosSurfaceHigh),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(categoryIcon(c.iconKey), null, tint = if (selected) Color(0xFF1B1B1B) else Color(c.colorArgb), modifier = Modifier.size(26.dp))
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(c.name, fontSize = 11.sp, color = GastosText, maxLines = 2, textAlign = TextAlign.Center, lineHeight = 13.sp)
                }
            }
            item {
                Column(
                    Modifier.padding(4.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onManageCategories).padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(Modifier.size(50.dp).clip(CircleShape).background(GastosSurfaceHigh), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Settings, null, tint = GastosMuted)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("Ajustes", fontSize = 11.sp, color = GastosMuted)
                }
            }
        }

        // Panel de monto + nota + teclado
        Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                val cat = categoryId?.let { state.categoryById(it) }
                CategoryBadge(cat, 34.dp)
                Spacer(Modifier.width(10.dp))
                Text(cat?.name ?: "Elige una categoría", color = if (cat == null) GastosMuted else GastosText, modifier = Modifier.weight(1f), maxLines = 1)
                Text(
                    (if (expr.isBlank()) "0" else expr) + "  " + state.expenseSettings.currencySymbol,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (kind == ExpenseKind.EXPENSE) GastosText else GastosIncome,
                    maxLines = 1,
                )
            }
            OutlinedTextField(
                value = note,
                onValueChange = { note = it.take(200) },
                placeholder = { Text("Nota (opcional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = GastosSurfaceHigh,
                    unfocusedContainerColor = GastosSurfaceHigh,
                ),
            )
            Spacer(Modifier.height(6.dp))
            val dateLabel = when {
                isSameDay(date, System.currentTimeMillis()) -> "Hoy"
                isSameDay(date, System.currentTimeMillis() - 86_400_000L) -> "Ayer"
                else -> formatDate(date, "d/MM/yy")
            }
            val keys = listOf(
                listOf("7", "8", "9", "DATE"),
                listOf("4", "5", "6", "+"),
                listOf("1", "2", "3", "-"),
                listOf(".", "0", "DEL", "OK"),
            )
            keys.forEach { row ->
                Row(Modifier.fillMaxWidth()) {
                    row.forEach { key ->
                        val isOk = key == "OK"
                        Box(
                            Modifier
                                .weight(1f)
                                .height(56.dp)
                                .padding(1.dp)
                                .background(if (isOk) GastosYellow else GastosSurfaceHigh)
                                .clickable {
                                    when (key) {
                                        "DEL" -> expr = expr.dropLast(1)
                                        "OK" -> save()
                                        "DATE" -> pickDate(context, date) { date = it }
                                        "+", "-" -> if (expr.isNotEmpty() && expr.last().isDigit()) expr += key
                                        "." -> {
                                            val lastNumber = expr.split('+', '-').last()
                                            if (!lastNumber.contains('.')) expr += if (lastNumber.isEmpty()) "0." else "."
                                        }
                                        else -> {
                                            val lastNumber = expr.split('+', '-').last()
                                            val decimals = lastNumber.substringAfter('.', "")
                                            if (expr.length < 18 && !(lastNumber.contains('.') && decimals.length >= 2)) expr += key
                                        }
                                    }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            when (key) {
                                "DEL" -> Icon(Icons.AutoMirrored.Rounded.Backspace, "Borrar", tint = GastosText)
                                "OK" -> Icon(Icons.Rounded.Check, "Guardar", tint = Color(0xFF1B1B1B), modifier = Modifier.size(30.dp))
                                "DATE" -> Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Rounded.CalendarMonth, null, tint = GastosYellow, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(dateLabel, color = GastosYellow, fontSize = 14.sp)
                                }
                                else -> Text(key, fontSize = 22.sp, color = GastosText)
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmDelete && initial != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Eliminar registro") },
            text = { Text("¿Quieres eliminar este registro de ${num(initial.amount)}? Esta acción no se puede deshacer.") },
            confirmButton = {
                TextButton(onClick = {
                    state.deleteExpense(initial)
                    confirmDelete = false
                    onSaved("Registro eliminado")
                }) { Text("Eliminar", color = GastosExpense) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } },
        )
    }
}

