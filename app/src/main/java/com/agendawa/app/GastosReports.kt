@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.agendawa.app

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Paid
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Calendar

// ---------------------------------------------------------------------------
// Componentes visuales
// ---------------------------------------------------------------------------

@Composable
private fun KindToggle(kind: ExpenseKind, onKind: (ExpenseKind) -> Unit) {
    Row(Modifier.clip(RoundedCornerShape(50)).border(1.dp, GastosYellow, RoundedCornerShape(50))) {
        ExpenseKind.entries.forEach { k ->
            Text(
                if (k == ExpenseKind.EXPENSE) "Gastos" else "Ingresos",
                modifier = Modifier
                    .background(if (kind == k) GastosYellow else Color.Transparent)
                    .clickable { onKind(k) }
                    .padding(horizontal = 18.dp, vertical = 6.dp),
                color = if (kind == k) Color(0xFF1B1B1B) else GastosYellow,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
            )
        }
    }
}

@Composable
private fun DonutChart(slices: List<Pair<Color, Double>>, centerTop: String, centerBottom: String, modifier: Modifier = Modifier) {
    val total = slices.sumOf { it.second }
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = size.minDimension * 0.16f
            val diameter = size.minDimension - stroke
            val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
            if (total <= 0.0) {
                drawArc(GastosSurfaceHigh, 0f, 360f, false, topLeft, Size(diameter, diameter), style = Stroke(stroke))
            } else {
                var start = -90f
                slices.forEach { (color, value) ->
                    val sweep = (value / total * 360.0).toFloat()
                    drawArc(color, start, (sweep - 0.8f).coerceAtLeast(0.5f), false, topLeft, Size(diameter, diameter), style = Stroke(stroke, cap = StrokeCap.Butt))
                    start += sweep
                }
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(centerTop, color = GastosMuted, fontSize = 13.sp)
            Text(centerBottom, color = GastosText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun BarsChart(data: List<Triple<String, Double, Double>>, modifier: Modifier = Modifier) {
    val max = data.maxOfOrNull { maxOf(it.second, it.third) }?.takeIf { it > 0 } ?: 1.0
    Column(modifier) {
        Row(Modifier.fillMaxWidth().height(140.dp), verticalAlignment = Alignment.Bottom) {
            data.forEach { (_, exp, inc) ->
                Row(Modifier.weight(1f).fillMaxHeight(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.Bottom) {
                    Box(Modifier.width(10.dp).fillMaxHeight((exp / max).toFloat().coerceIn(0.01f, 1f)).clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)).background(GastosExpense))
                    Spacer(Modifier.width(3.dp))
                    Box(Modifier.width(10.dp).fillMaxHeight((inc / max).toFloat().coerceIn(0.01f, 1f)).clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)).background(GastosIncome))
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth()) {
            data.forEach { (label, _, _) -> Text(label, Modifier.weight(1f), textAlign = TextAlign.Center, color = GastosMuted, fontSize = 11.sp) }
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(GastosExpense)); Spacer(Modifier.width(4.dp))
            Text("Gastos", color = GastosMuted, fontSize = 12.sp); Spacer(Modifier.width(14.dp))
            Box(Modifier.size(10.dp).clip(CircleShape).background(GastosIncome)); Spacer(Modifier.width(4.dp))
            Text("Ingresos", color = GastosMuted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun GastosCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(GastosSurface).padding(16.dp)) { content() }
}

// ---------------------------------------------------------------------------
// Gráficos
// ---------------------------------------------------------------------------

@Composable
fun GastosCharts(
    state: AppState,
    period: YearMonth,
    onPeriod: (YearMonth) -> Unit,
    onOpenDrawer: () -> Unit,
    onCategory: (Long, Long, Long, String) -> Unit,
) {
    var kind by remember { mutableStateOf(ExpenseKind.EXPENSE) }
    var yearly by remember { mutableStateOf(false) }
    var year by remember { mutableIntStateOf(period.year) }

    val start = if (yearly) YearMonth(year, 0).startMillis else period.startMillis
    val end = if (yearly) YearMonth(year, 11).endMillis else period.endMillis
    val title = if (yearly) year.toString() else "${longMonths[period.month]} ${period.year}"
    val records = state.expenses.inRange(start, end).filter { it.kind == kind }
    val total = records.sumOf { it.amount }
    val byCategory = records.groupBy { it.categoryId }.mapValues { (_, v) -> v.sumOf { it.amount } }.entries.sortedByDescending { it.value }
    val categories = state.categories.associateBy { it.id }
    val symbol = state.expenseSettings.currencySymbol

    Column(Modifier.fillMaxSize()) {
        GastosTopBar(title = "Gráficos", onMenu = onOpenDrawer)
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    KindToggle(kind) { kind = it }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(false to "Mes", true to "Año").forEach { (isYear, label) ->
                            Text(
                                label,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(if (yearly == isYear) GastosSurfaceHigh else Color.Transparent)
                                    .clickable { yearly = isYear; year = period.year }
                                    .padding(horizontal = 14.dp, vertical = 5.dp),
                                color = if (yearly == isYear) GastosText else GastosMuted,
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { if (yearly) year-- else onPeriod(period.plus(-1)) }) { Icon(Icons.Rounded.ChevronLeft, "Anterior") }
                        Text(title, fontWeight = FontWeight.Bold, color = GastosText, modifier = Modifier.width(170.dp), textAlign = TextAlign.Center)
                        IconButton(onClick = { if (yearly) year++ else onPeriod(period.plus(1)) }) { Icon(Icons.Rounded.ChevronRight, "Siguiente") }
                    }
                }
            }
            item {
                GastosCard {
                    DonutChart(
                        slices = byCategory.map { Color(categories[it.key]?.colorArgb ?: 0xFF94A3B8) to it.value },
                        centerTop = if (kind == ExpenseKind.EXPENSE) "Gastos" else "Ingresos",
                        centerBottom = num(total),
                        modifier = Modifier.fillMaxWidth().height(220.dp),
                    )
                }
            }
            if (byCategory.isEmpty()) {
                item { Text("No hay registros en este periodo", color = GastosMuted, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) }
            }
            items(byCategory, key = { it.key }) { entry ->
                val cat = categories[entry.key]
                val pct = if (total > 0) entry.value / total else 0.0
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onCategory(entry.key, start, end, "${cat?.name ?: "Sin categoría"} · $title") }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CategoryBadge(cat, 38.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Row {
                            Text(cat?.name ?: "Sin categoría", color = GastosText, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(String.format(esLocale, "%.1f%%", pct * 100), color = GastosMuted, fontSize = 13.sp)
                        }
                        Spacer(Modifier.height(4.dp))
                        Box(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)).background(GastosSurfaceHigh)) {
                            Box(Modifier.fillMaxWidth(pct.toFloat().coerceIn(0.01f, 1f)).fillMaxHeight().background(Color(cat?.colorArgb ?: 0xFF94A3B8)))
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(money(entry.value, symbol), color = GastosText, fontWeight = FontWeight.SemiBold)
                }
            }
            item {
                val months = (5 downTo 0).map { period.plus(-it) }
                GastosCard {
                    Column {
                        Text("Últimos 6 meses", color = GastosText, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(10.dp))
                        BarsChart(
                            months.map { ym ->
                                val r = state.expenses.inRange(ym.startMillis, ym.endMillis)
                                Triple(shortMonths[ym.month], r.totalOf(ExpenseKind.EXPENSE), r.totalOf(ExpenseKind.INCOME))
                            },
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Informes
// ---------------------------------------------------------------------------

@Composable
fun GastosReports(
    state: AppState,
    period: YearMonth,
    onOpenDrawer: () -> Unit,
    onBalance: (Int?) -> Unit,
    showMessage: (String) -> Unit,
) {
    val context = LocalContext.current
    val settings = state.expenseSettings
    val symbol = settings.currencySymbol
    var showBudget by remember { mutableStateOf(false) }
    var exportScope by remember { mutableStateOf(0) } // 0 = mes, 1 = año, 2 = todo

    val now = YearMonth.now()
    val month = state.expenses.inRange(now.startMillis, now.endMillis)
    val spent = month.totalOf(ExpenseKind.EXPENSE)
    val yearRecords = state.expenses.inRange(YearMonth(now.year, 0).startMillis, YearMonth(now.year, 11).endMillis)
    val allExp = state.expenses.totalOf(ExpenseKind.EXPENSE)
    val allInc = state.expenses.totalOf(ExpenseKind.INCOME)

    fun scopeRecords(): Pair<String, List<Expense>> = when (exportScope) {
        0 -> "${longMonths[period.month]} ${period.year}" to state.expenses.inRange(period.startMillis, period.endMillis)
        1 -> "Año ${period.year}" to state.expenses.inRange(YearMonth(period.year, 0).startMillis, YearMonth(period.year, 11).endMillis)
        else -> "Todo el historial" to state.expenses.toList()
    }

    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) {
            val (_, list) = scopeRecords()
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use {
                    it.write(ExpenseExport.csv(list, state.categories.associateBy { c -> c.id }).toByteArray())
                }
            }.onSuccess { showMessage("CSV guardado (${list.size} registros)") }.onFailure { showMessage("No se pudo guardar: ${it.message}") }
        }
    }
    val pdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) {
            val (label, list) = scopeRecords()
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use {
                    PdfTextWriter.write(ExpenseExport.report("Mis Gastos · $label", list, state.categories.associateBy { c -> c.id }, symbol), it, "Mis Gastos")
                }
            }.onSuccess { showMessage("Informe PDF guardado") }.onFailure { showMessage("No se pudo guardar: ${it.message}") }
        }
    }

    Column(Modifier.fillMaxSize()) {
        GastosTopBar(title = "Informes", onMenu = onOpenDrawer)
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Presupuesto
            item {
                GastosCard {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Savings, null, tint = GastosYellow)
                            Spacer(Modifier.width(8.dp))
                            Text("Presupuesto mensual", color = GastosText, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            IconButton(onClick = { showBudget = true }) { Icon(Icons.Rounded.Settings, "Configurar presupuesto", tint = GastosMuted) }
                        }
                        if (settings.monthlyBudget <= 0) {
                            Text("Aún no tienes presupuesto. Toca el engranaje para definir cuánto quieres gastar al mes.", color = GastosMuted, fontSize = 13.sp)
                        } else {
                            val budget = settings.monthlyBudget
                            val ratio = (spent / budget).coerceIn(0.0, 1.0).toFloat()
                            val over = spent > budget
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                DonutChart(
                                    slices = listOf((if (over) GastosExpense else GastosYellow) to spent.coerceAtMost(budget), GastosSurfaceHigh to (budget - spent).coerceAtLeast(0.0)),
                                    centerTop = "Usado",
                                    centerBottom = "${(ratio * 100).toInt()}%",
                                    modifier = Modifier.size(110.dp),
                                )
                                Spacer(Modifier.width(16.dp))
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    LabeledValue("Presupuesto", money(budget, symbol))
                                    LabeledValue("Gastado (${shortMonths[now.month]})", money(spent, symbol))
                                    LabeledValue(if (over) "Excedido" else "Restante", money(kotlin.math.abs(budget - spent), symbol), if (over) GastosExpense else GastosIncome)
                                    val daysLeft = Calendar.getInstance().let { it.getActualMaximum(Calendar.DAY_OF_MONTH) - it.get(Calendar.DAY_OF_MONTH) + 1 }
                                    if (!over) LabeledValue("Puedes gastar por día", money((budget - spent) / daysLeft, symbol))
                                }
                            }
                        }
                    }
                }
            }
            // Balance (captura 2)
            item {
                GastosCard(Modifier.clickable { onBalance(null) }) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.AccountBalanceWallet, null, tint = GastosYellow)
                            Spacer(Modifier.width(8.dp))
                            Text("Balance mensual y anual", color = GastosText, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = GastosMuted)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("Saldo total", color = GastosMuted, fontSize = 13.sp)
                        Text(money(settings.openingBalance + allInc - allExp, symbol), color = GastosText, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        Row {
                            LabeledValue("Gastos ${now.year}", money(yearRecords.totalOf(ExpenseKind.EXPENSE), symbol), modifier = Modifier.weight(1f))
                            LabeledValue("Ingresos ${now.year}", money(yearRecords.totalOf(ExpenseKind.INCOME), symbol), modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
            // Resumen del mes
            item {
                val prev = YearMonth.now().plus(-1)
                val prevSpent = state.expenses.inRange(prev.startMillis, prev.endMillis).totalOf(ExpenseKind.EXPENSE)
                GastosCard {
                    Column {
                        Text("Comparación con el mes anterior", color = GastosText, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        val diff = spent - prevSpent
                        Text(
                            when {
                                prevSpent <= 0 && spent <= 0 -> "Sin gastos registrados en estos dos meses."
                                prevSpent <= 0 -> "Este mes llevas ${money(spent, symbol)} en gastos."
                                diff > 0 -> "Llevas ${money(diff, symbol)} más que en ${longMonths[prev.month].lowercase()} (${String.format(esLocale, "%.0f", diff / prevSpent * 100)}% más)."
                                diff < 0 -> "Llevas ${money(-diff, symbol)} menos que en ${longMonths[prev.month].lowercase()}. ¡Bien!"
                                else -> "Vas igual que el mes anterior."
                            },
                            color = GastosMuted,
                            fontSize = 14.sp,
                        )
                        val top = month.filter { it.kind == ExpenseKind.EXPENSE }.groupBy { it.categoryId }
                            .mapValues { (_, v) -> v.sumOf { it.amount } }.maxByOrNull { it.value }
                        if (top != null) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Donde más gastas este mes: ${state.categoryById(top.key)?.name ?: "Sin categoría"} (${money(top.value, symbol)})",
                                color = GastosText,
                                fontSize = 14.sp,
                            )
                        }
                    }
                }
            }
            // Exportar
            item {
                GastosCard {
                    Column {
                        Text("Exportar", color = GastosText, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("${shortMonths[period.month]} ${period.year}", "Año ${period.year}", "Todo").forEachIndexed { i, label ->
                                Text(
                                    label,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(if (exportScope == i) GastosYellow else GastosSurfaceHigh)
                                        .clickable { exportScope = i }
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                    color = if (exportScope == i) Color(0xFF1B1B1B) else GastosText,
                                    fontSize = 13.sp,
                                )
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        ReportAction(Icons.Rounded.TableChart, "Exportar a Excel (CSV)", "Ábrelo en Excel o Google Sheets") {
                            csvLauncher.launch("MisGastos-${formatDate(System.currentTimeMillis(), "yyyy-MM-dd")}.csv")
                        }
                        ReportAction(Icons.Rounded.PictureAsPdf, "Informe en PDF", "Resumen por categorías y detalle") {
                            pdfLauncher.launch("MisGastos-${formatDate(System.currentTimeMillis(), "yyyy-MM-dd")}.pdf")
                        }
                    }
                }
            }
        }
    }

    if (showBudget) {
        AmountDialog(
            title = "Presupuesto mensual",
            description = "¿Cuánto quieres gastar como máximo cada mes? Escribe 0 para desactivarlo.",
            initial = settings.monthlyBudget,
            onDismiss = { showBudget = false },
            onSave = { state.updateExpenseSettings(settings.copy(monthlyBudget = it)); showBudget = false; showMessage("Presupuesto actualizado") },
        )
    }
}

@Composable
private fun LabeledValue(label: String, value: String, color: Color = GastosText, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, color = GastosMuted, fontSize = 12.sp)
        Text(value, color = color, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    }
}

@Composable
private fun ReportAction(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = GastosYellow)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = GastosText)
            Text(subtitle, color = GastosMuted, fontSize = 12.sp)
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = GastosMuted)
    }
}

@Composable
fun AmountDialog(title: String, description: String, initial: Double, allowNegative: Boolean = false, onDismiss: () -> Unit, onSave: (Double) -> Unit) {
    var text by remember { mutableStateOf(if (initial == 0.0) "" else num(initial)) }
    var error by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(description, fontSize = 14.sp, color = GastosMuted)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it; error = false },
                    singleLine = true,
                    isError = error,
                    keyboardOptions = KeyboardOptions(keyboardType = if (allowNegative) KeyboardType.Text else KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val negative = allowNegative && text.trim().startsWith("-")
                val value = if (text.isBlank()) 0.0 else ExpenseVoiceParser.parseAmount(text.trim().removePrefix("-"))
                if (value == null) error = true else onSave(if (negative) -value else value)
            }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

// ---------------------------------------------------------------------------
// Balance (capturas 2 y 3): Todo / años / Otro
// ---------------------------------------------------------------------------

private data class RowData(val label: String, val exp: Double, val inc: Double, val drillYear: Int?)

@Composable
fun BalanceReportScreen(state: AppState, initialYear: Int?, onBack: () -> Unit) {
    var year by remember { mutableStateOf(initialYear) } // null = Todo
    var showMenu by remember { mutableStateOf(false) }
    var showOtherYear by remember { mutableStateOf(false) }
    var showOpening by remember { mutableStateOf(false) }
    val settings = state.expenseSettings
    val current = Calendar.getInstance().get(Calendar.YEAR)

    val records = if (year == null) state.expenses.toList()
    else state.expenses.inRange(YearMonth(year!!, 0).startMillis, YearMonth(year!!, 11).endMillis)
    val exp = records.totalOf(ExpenseKind.EXPENSE)
    val inc = records.totalOf(ExpenseKind.INCOME)
    val saldoTotal = if (year == null) settings.openingBalance + inc - exp else inc - exp

    val rows: List<RowData> = if (year == null) {
        val years = state.expenses.map { yearOf(it.dateMillis) }.distinct().sortedDescending()
        years.map { y ->
            val r = state.expenses.filter { yearOf(it.dateMillis) == y }
            RowData(y.toString(), r.totalOf(ExpenseKind.EXPENSE), r.totalOf(ExpenseKind.INCOME), y)
        }
    } else {
        val y = year!!
        val lastMonth = if (y == current) Calendar.getInstance().get(Calendar.MONTH) else 11
        (lastMonth downTo 0).map { m ->
            val ym = YearMonth(y, m)
            val r = state.expenses.inRange(ym.startMillis, ym.endMillis)
            RowData(shortMonths[m], r.totalOf(ExpenseKind.EXPENSE), r.totalOf(ExpenseKind.INCOME), null)
        }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        GastosTopBar(
            title = "",
            onBack = onBack,
            actions = {
                Box {
                    Row(
                        Modifier.clip(RoundedCornerShape(8.dp)).clickable { showMenu = true }.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(year?.toString() ?: "Todo", color = GastosText, fontSize = 18.sp)
                        Icon(Icons.Rounded.ArrowDropDown, null, tint = GastosText)
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(text = { Text("Todo") }, onClick = { year = null; showMenu = false })
                        (current downTo current - 4).forEach { y ->
                            DropdownMenuItem(text = { Text(y.toString()) }, onClick = { year = y; showMenu = false })
                        }
                        DropdownMenuItem(text = { Text("Otro") }, onClick = { showMenu = false; showOtherYear = true })
                    }
                }
            },
        )
        Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(bottom = 12.dp)) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Saldo total", color = GastosText, fontSize = 16.sp)
                Text(num(saldoTotal), color = GastosText, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                if (year == null && settings.openingBalance != 0.0) {
                    Text("Incluye saldo inicial de ${num(settings.openingBalance)}", color = GastosMuted, fontSize = 12.sp)
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Gastos", color = GastosText); Text(num(exp), color = GastosText, fontWeight = FontWeight.Bold)
                    }
                    Box(Modifier.width(1.dp).height(36.dp).background(Color(0xFF3A3A3A)))
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Ingresos", color = GastosText); Text(num(inc), color = GastosText, fontWeight = FontWeight.Bold)
                    }
                }
            }
            IconButton(onClick = { showOpening = true }, modifier = Modifier.align(Alignment.TopEnd).padding(end = 6.dp)) {
                Icon(Icons.Rounded.Settings, "Saldo inicial", tint = GastosText)
            }
        }
        Row(Modifier.fillMaxWidth().background(Color(0xFF202020)).padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(if (year == null) "Año" else "Mes", color = GastosMuted, modifier = Modifier.weight(0.7f))
            Text("Gastos", color = GastosMuted, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
            Text("Ingresos", color = GastosMuted, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
            Text("Saldo", color = GastosMuted, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
        }
        HorizontalDivider(color = Color(0xFF333333))
        if (rows.isEmpty() || rows.all { it.exp == 0.0 && it.inc == 0.0 }) {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Icon(Icons.Rounded.ReceiptLong, null, tint = GastosMuted, modifier = Modifier.size(56.dp))
                Spacer(Modifier.height(8.dp))
                Text("No hay registros", color = GastosMuted)
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                items(rows, key = { it.label }) { r ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable(enabled = r.drillYear != null) { year = r.drillYear }
                            .padding(horizontal = 14.dp, vertical = 14.dp),
                    ) {
                        Text(r.label, color = GastosText, modifier = Modifier.weight(0.7f))
                        Text(num(r.exp), color = GastosText, modifier = Modifier.weight(1f), textAlign = TextAlign.End, maxLines = 1)
                        Text(num(r.inc), color = GastosText, modifier = Modifier.weight(1f), textAlign = TextAlign.End, maxLines = 1)
                        Text(
                            num(r.inc - r.exp),
                            color = if (r.inc - r.exp < 0) GastosExpense else GastosText,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.End,
                            maxLines = 1,
                        )
                    }
                    HorizontalDivider(color = Color(0xFF2A2A2A))
                }
            }
        }
    }

    if (showOtherYear) {
        val years = (state.expenses.map { yearOf(it.dateMillis) } + (current - 15..current + 1)).distinct().sortedDescending()
        AlertDialog(
            onDismissRequest = { showOtherYear = false },
            title = { Text("Elige un año") },
            text = {
                LazyColumn(Modifier.height(320.dp)) {
                    items(years) { y ->
                        Text(
                            y.toString(),
                            modifier = Modifier.fillMaxWidth().clickable { year = y; showOtherYear = false }.padding(vertical = 12.dp),
                            fontSize = 17.sp,
                            fontWeight = if (y == year) FontWeight.Bold else FontWeight.Normal,
                            color = if (y == year) GastosYellow else GastosText,
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showOtherYear = false }) { Text("Cancelar") } },
        )
    }

    if (showOpening) {
        AmountDialog(
            title = "Saldo inicial",
            description = "Dinero que ya tenías antes de empezar a registrar. Se suma al saldo total (puede ser negativo, ej. -50).",
            initial = settings.openingBalance,
            allowNegative = true,
            onDismiss = { showOpening = false },
            onSave = { state.updateExpenseSettings(settings.copy(openingBalance = it)); showOpening = false },
        )
    }
}

// ---------------------------------------------------------------------------
// Yo
// ---------------------------------------------------------------------------

@Composable
fun GastosMe(
    state: AppState,
    onOpenDrawer: () -> Unit,
    onCategories: () -> Unit,
    onBackup: () -> Unit,
    onProfile: () -> Unit,
    onBackToAgenda: () -> Unit,
    showMessage: (String) -> Unit,
) {
    val context = LocalContext.current
    val settings = state.expenseSettings
    var showCurrency by remember { mutableStateOf(false) }
    var showBudget by remember { mutableStateOf(false) }
    var showOpening by remember { mutableStateOf(false) }
    var confirmWipe by remember { mutableStateOf(false) }

    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    Column(Modifier.fillMaxSize()) {
        GastosTopBar(title = "Yo", onMenu = onOpenDrawer)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            GastosCard(Modifier.clickable(onClick = onProfile)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(54.dp).clip(CircleShape).background(GastosYellow), contentAlignment = Alignment.Center) {
                        Text(state.profileName.take(1).uppercase().ifBlank { "?" }, color = Color(0xFF1B1B1B), fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(state.profileName.ifBlank { "Tu perfil" }, color = GastosText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text("${state.expenses.size} registros · ${state.categories.size} categorías", color = GastosMuted, fontSize = 13.sp)
                    }
                    Icon(Icons.Rounded.Edit, "Editar nombre", tint = GastosMuted)
                }
            }
            GastosCard {
                Column {
                    SettingRow(Icons.Rounded.Paid, "Moneda", settings.currencySymbol) { showCurrency = true }
                    SettingRow(Icons.Rounded.Category, "Categorías", "Agregar, editar u ordenar", onClick = onCategories)
                    SettingRow(Icons.Rounded.Savings, "Presupuesto mensual", if (settings.monthlyBudget > 0) money(settings.monthlyBudget, settings.currencySymbol) else "Sin definir") { showBudget = true }
                    SettingRow(Icons.Rounded.AccountBalanceWallet, "Saldo inicial", money(settings.openingBalance, settings.currencySymbol)) { showOpening = true }
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.NotificationsActive, null, tint = GastosYellow)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f).clickable(enabled = settings.dailyReminderEnabled) {
                            pickTime(context, Calendar.getInstance().apply {
                                set(Calendar.HOUR_OF_DAY, settings.reminderHour); set(Calendar.MINUTE, settings.reminderMinute)
                            }.timeInMillis) { t ->
                                val c = Calendar.getInstance().apply { timeInMillis = t }
                                state.updateExpenseSettings(settings.copy(reminderHour = c.get(Calendar.HOUR_OF_DAY), reminderMinute = c.get(Calendar.MINUTE)))
                            }
                        }) {
                            Text("Recordatorio diario", color = GastosText)
                            Text(
                                if (settings.dailyReminderEnabled) "Todos los días a las ${String.format(esLocale, "%d:%02d", if (settings.reminderHour % 12 == 0) 12 else settings.reminderHour % 12, settings.reminderMinute)} ${if (settings.reminderHour < 12) "a. m." else "p. m."} · toca para cambiar"
                                else "Te aviso para anotar tus gastos del día",
                                color = GastosMuted, fontSize = 12.sp,
                            )
                        }
                        Switch(
                            checked = settings.dailyReminderEnabled,
                            onCheckedChange = { on ->
                                if (on && android.os.Build.VERSION.SDK_INT >= 33 && !NotificationHelper.notificationsAllowed(context)) {
                                    notifPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                }
                                state.updateExpenseSettings(settings.copy(dailyReminderEnabled = on))
                                showMessage(if (on) "Recordatorio diario activado" else "Recordatorio diario desactivado")
                            },
                            colors = SwitchDefaults.colors(checkedTrackColor = GastosYellow, checkedThumbColor = Color(0xFF1B1B1B)),
                        )
                    }
                }
            }
            GastosCard {
                Column {
                    SettingRow(Icons.Rounded.Backup, "Copia de seguridad", "Incluye Mis Gastos, agenda, notas y finanzas", onClick = onBackup)
                    SettingRow(Icons.Rounded.Event, "Volver a la Agenda", "Recordatorios, notas y WhatsApp", onClick = onBackToAgenda)
                    SettingRow(Icons.Rounded.DeleteForever, "Borrar registros de Mis Gastos", "Elimina todos los gastos e ingresos", tint = GastosExpense) { confirmWipe = true }
                }
            }
            Text("Agenda WA ${BackupManager.APP_VERSION} · Mis Gastos", color = GastosMuted, fontSize = 12.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        }
    }

    if (showCurrency) {
        val options = listOf("$", "Bs", "€", "COP $", "S/", "MXN $", "£")
        var custom by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCurrency = false },
            title = { Text("Moneda") },
            text = {
                Column {
                    options.forEach { o ->
                        Text(
                            o,
                            modifier = Modifier.fillMaxWidth().clickable {
                                state.updateExpenseSettings(settings.copy(currencySymbol = o)); showCurrency = false
                            }.padding(vertical = 10.dp),
                            color = if (o == settings.currencySymbol) GastosYellow else GastosText,
                            fontWeight = if (o == settings.currencySymbol) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 17.sp,
                        )
                    }
                    OutlinedTextField(custom, { custom = it.take(6) }, label = { Text("Otra (símbolo)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (custom.isNotBlank()) state.updateExpenseSettings(settings.copy(currencySymbol = custom.trim()))
                    showCurrency = false
                }) { Text("Guardar") }
            },
            dismissButton = { TextButton(onClick = { showCurrency = false }) { Text("Cancelar") } },
        )
    }
    if (showBudget) {
        AmountDialog("Presupuesto mensual", "¿Cuánto quieres gastar como máximo cada mes? Escribe 0 para desactivarlo.", settings.monthlyBudget,
            onDismiss = { showBudget = false },
            onSave = { state.updateExpenseSettings(settings.copy(monthlyBudget = it)); showBudget = false })
    }
    if (showOpening) {
        AmountDialog("Saldo inicial", "Dinero que ya tenías antes de empezar a registrar (puede ser negativo, ej. -50).", settings.openingBalance, allowNegative = true,
            onDismiss = { showOpening = false },
            onSave = { state.updateExpenseSettings(settings.copy(openingBalance = it)); showOpening = false })
    }
    if (confirmWipe) {
        AlertDialog(
            onDismissRequest = { confirmWipe = false },
            title = { Text("Borrar registros") },
            text = { Text("Se eliminarán ${state.expenses.size} gastos e ingresos. Te recomendamos hacer una copia de seguridad antes. Esta acción no se puede deshacer.") },
            confirmButton = {
                TextButton(onClick = {
                    state.expenses.toList().forEach { state.deleteExpense(it) }
                    confirmWipe = false
                    showMessage("Registros eliminados")
                }) { Text("Borrar todo", color = GastosExpense) }
            },
            dismissButton = { TextButton(onClick = { confirmWipe = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun SettingRow(icon: ImageVector, title: String, value: String, tint: Color = GastosYellow, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = tint)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = GastosText)
            Text(value, color = GastosMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = GastosMuted)
    }
}

// ---------------------------------------------------------------------------
// Administrador de categorías
// ---------------------------------------------------------------------------

@Composable
fun CategoryManagerScreen(state: AppState, onBack: () -> Unit, showMessage: (String) -> Unit) {
    var kind by remember { mutableStateOf(ExpenseKind.EXPENSE) }
    var editing by remember { mutableStateOf<ExpenseCategory?>(null) }
    var creating by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<ExpenseCategory?>(null) }
    val list = state.categories.filter { it.kind == kind }.sortedBy { it.sortOrder }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize()) {
            GastosTopBar(title = "Categorías", onBack = onBack)
            Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(bottom = 10.dp), contentAlignment = Alignment.Center) {
                KindToggle(kind) { kind = it }
            }
            LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
                items(list, key = { it.id }) { c ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        CategoryBadge(c, 38.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(c.name, color = GastosText)
                            Text("${state.expenses.count { it.categoryId == c.id }} registros", color = GastosMuted, fontSize = 12.sp)
                        }
                        IconButton(onClick = { state.moveCategory(c, up = true) }) { Icon(Icons.Rounded.KeyboardArrowUp, "Subir", tint = GastosMuted) }
                        IconButton(onClick = { state.moveCategory(c, up = false) }) { Icon(Icons.Rounded.KeyboardArrowDown, "Bajar", tint = GastosMuted) }
                        IconButton(onClick = { editing = c }) { Icon(Icons.Rounded.Edit, "Editar", tint = GastosMuted) }
                        IconButton(onClick = { confirmDelete = c }, enabled = list.size > 1) { Icon(Icons.Rounded.Delete, "Eliminar", tint = GastosMuted) }
                    }
                }
            }
        }
        FloatingActionButton(
            onClick = { creating = true },
            containerColor = GastosYellow,
            contentColor = Color(0xFF1B1B1B),
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp).padding(bottom = 24.dp),
        ) { Icon(Icons.Rounded.Add, "Nueva categoría") }
    }

    if (creating || editing != null) {
        CategoryEditorDialog(
            initial = editing,
            kind = editing?.kind ?: kind,
            onDismiss = { creating = false; editing = null },
            onSave = { c ->
                state.saveCategory(c)
                showMessage(if (editing == null) "Categoría creada" else "Categoría actualizada")
                creating = false; editing = null
            },
        )
    }
    confirmDelete?.let { c ->
        val count = state.expenses.count { it.categoryId == c.id }
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Eliminar categoría") },
            text = { Text(if (count > 0) "\"${c.name}\" tiene $count registros. Se moverán a \"Otros\"." else "¿Eliminar \"${c.name}\"?") },
            confirmButton = { TextButton(onClick = { state.deleteCategory(c); confirmDelete = null; showMessage("Categoría eliminada") }) { Text("Eliminar", color = GastosExpense) } },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun CategoryEditorDialog(initial: ExpenseCategory?, kind: ExpenseKind, onDismiss: () -> Unit, onSave: (ExpenseCategory) -> Unit) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var icon by remember { mutableStateOf(initial?.iconKey ?: "more") }
    var color by remember { mutableLongStateOf(initial?.colorArgb ?: ExpenseCategories.palette.first()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Nueva categoría de ${kind.label.lowercase()}" else "Editar categoría") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(name, { name = it.take(30) }, label = { Text("Nombre") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                Text("Ícono", color = GastosMuted)
                ExpenseCategories.iconKeys.chunked(6).forEach { row ->
                    Row {
                        row.forEach { key ->
                            Box(
                                Modifier
                                    .weight(1f)
                                    .padding(3.dp)
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(if (icon == key) Color(color) else GastosSurfaceHigh)
                                    .clickable { icon = key },
                                contentAlignment = Alignment.Center,
                            ) { Icon(categoryIcon(key), null, tint = if (icon == key) Color.White else GastosText, modifier = Modifier.size(20.dp)) }
                        }
                        repeat(6 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text("Color", color = GastosMuted)
                ExpenseCategories.palette.chunked(8).forEach { row ->
                    Row {
                        row.forEach { c ->
                            Box(
                                Modifier
                                    .weight(1f)
                                    .padding(3.dp)
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(Color(c))
                                    .border(if (color == c) 3.dp else 0.dp, Color.White, CircleShape)
                                    .clickable { color = c },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isNotBlank()) onSave(
                    (initial ?: ExpenseCategory(id = 0L, name = "", kind = kind, iconKey = icon, colorArgb = color))
                        .copy(name = name.trim(), iconKey = icon, colorArgb = color),
                )
            }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

// ---------------------------------------------------------------------------
// Detalle de categoría, búsqueda y calendario
// ---------------------------------------------------------------------------

@Composable
fun CategoryDetailScreen(state: AppState, categoryId: Long, start: Long, end: Long, title: String, onBack: () -> Unit, onOpen: (Expense) -> Unit) {
    val records = state.expenses.inRange(start, end).filter { it.categoryId == categoryId }.sortedByDescending { it.dateMillis }
    val cat = state.categoryById(categoryId)
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        GastosTopBar(title = cat?.name ?: "Categoría", onBack = onBack)
        Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            CategoryBadge(cat, 52.dp)
            Spacer(Modifier.height(6.dp))
            Text(title, color = GastosMuted, fontSize = 13.sp)
            Text(money(records.sumOf { it.amount }, state.expenseSettings.currencySymbol), color = GastosText, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text("${records.size} registros", color = GastosMuted, fontSize = 12.sp)
        }
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            items(records, key = { it.id }) { r -> ExpenseRow(r, cat, showDate = true, onClick = { onOpen(r) }) }
        }
    }
}

@Composable
fun ExpenseSearchScreen(state: AppState, onBack: () -> Unit, onOpen: (Expense) -> Unit) {
    var query by remember { mutableStateOf("") }
    val categories = state.categories.associateBy { it.id }
    val q = SmartNoteOrganizer.normalize(query.trim())
    val results = if (q.isBlank()) emptyList() else state.expenses.filter { e ->
        val catName = categories[e.categoryId]?.name.orEmpty()
        SmartNoteOrganizer.normalize("${e.note} $catName ${num(e.amount)} ${formatDate(e.dateMillis, "dd/MM/yyyy")}").contains(q)
    }.sortedByDescending { it.dateMillis }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        GastosTopBar(title = "Buscar", onBack = onBack)
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Nota, categoría, monto o fecha") },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Clear, "Limpiar") } },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(12.dp),
        )
        if (results.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                Text("${results.size} resultados", color = GastosMuted, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Text("Gastos ${num(results.totalOf(ExpenseKind.EXPENSE))} · Ingresos ${num(results.totalOf(ExpenseKind.INCOME))}", color = GastosMuted, fontSize = 13.sp)
            }
        } else if (q.isNotBlank()) {
            Text("No encontramos resultados", color = GastosMuted, modifier = Modifier.fillMaxWidth().padding(24.dp), textAlign = TextAlign.Center)
        }
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            items(results, key = { it.id }) { r -> ExpenseRow(r, categories[r.categoryId], showDate = true, onClick = { onOpen(r) }) }
        }
    }
}

@Composable
fun ExpenseCalendarScreen(state: AppState, onBack: () -> Unit, onOpen: (Expense) -> Unit) {
    var anchor by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var selected by remember { mutableLongStateOf(startOfDay(System.currentTimeMillis())) }
    val categories = state.categories.associateBy { it.id }
    val dayTotals = remember(state.expenses.toList()) {
        state.expenses.groupBy { startOfDay(it.dateMillis) }.mapValues { (_, v) -> v.totalOf(ExpenseKind.INCOME) - v.totalOf(ExpenseKind.EXPENSE) }
    }
    val dayRecords = state.expenses.filter { isSameDay(it.dateMillis, selected) }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        GastosTopBar(title = "Calendario", onBack = onBack)
        LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                MonthCalendar(
                    monthAnchor = anchor,
                    selectedDay = selected,
                    onMonthChange = { anchor = it },
                    onSelect = { selected = it },
                    dayDecoration = { d -> dayTotals[d]?.let { if (it < 0) GastosExpense else GastosIncome } },
                    dayCaption = { d -> dayTotals[d]?.let { num(it) } },
                )
            }
            item {
                val ym = YearMonth.of(anchor)
                val month = state.expenses.inRange(ym.startMillis, ym.endMillis)
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                    LabeledValue("Gastos del mes", num(month.totalOf(ExpenseKind.EXPENSE)), modifier = Modifier.weight(1f))
                    LabeledValue("Ingresos del mes", num(month.totalOf(ExpenseKind.INCOME)), modifier = Modifier.weight(1f))
                }
            }
            item { DayHeader(selected, dayRecords) }
            if (dayRecords.isEmpty()) item { Text("Sin registros este día", color = GastosMuted, modifier = Modifier.padding(16.dp)) }
            items(dayRecords, key = { it.id }) { r -> ExpenseRow(r, categories[r.categoryId], onClick = { onOpen(r) }) }
        }
    }
}

