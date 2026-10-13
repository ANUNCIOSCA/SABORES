@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.agendawa.app

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.BusinessCenter
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import java.util.Calendar

private const val TIME_PATTERN = "h:mm a"

private fun greeting(): String = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
    in 5..11 -> "Buenos días"
    in 12..18 -> "Buenas tardes"
    else -> "Buenas noches"
}

// ---------------------------------------------------------------------------
// Hoy
// ---------------------------------------------------------------------------

@Composable
fun TodayScreen(
    state: AppState,
    actions: TaskActions,
    onVoice: () -> Unit,
    onOpenGastos: () -> Unit,
    onOpenFinance: () -> Unit,
) {
    val now = System.currentTimeMillis()
    val today = state.tasks.filter { isSameDay(it.dateTimeMillis, now) }
    val overdue = state.tasks.filter { !it.completed && it.dateTimeMillis < startOfDay(now) }
    val pendingToday = today.count { !it.completed }
    val upcoming = state.tasks.count { !it.completed && it.dateTimeMillis > endOfDay(now) }

    val cal = Calendar.getInstance()
    val monthExpenses = state.expenses.filter {
        val c = Calendar.getInstance().apply { timeInMillis = it.dateMillis }
        c.get(Calendar.YEAR) == cal.get(Calendar.YEAR) && c.get(Calendar.MONTH) == cal.get(Calendar.MONTH)
    }
    val spent = monthExpenses.filter { it.kind == ExpenseKind.EXPENSE }.sumOf { it.amount }
    val earned = monthExpenses.filter { it.kind == ExpenseKind.INCOME }.sumOf { it.amount }
    val receivable = state.finance.filter { it.type == FinanceType.RECEIVABLE && !it.isPaid }.sumOf { it.balance }

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column {
                Text(
                    if (state.profileName.isBlank()) "${greeting()}" else "${greeting()}, ${state.profileName}",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    formatDate(now, "EEEE d 'de' MMMM").replaceFirstChar { it.uppercase() },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item { VoiceHero(onVoice) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard("Para hoy", pendingToday.toString(), "pendientes", AgendaBlue, Modifier.weight(1f))
                MetricCard("Próximos", upcoming.toString(), "agendados", AgendaGreen, Modifier.weight(1f))
                MetricCard("Vencidos", overdue.size.toString(), "por revisar", AgendaDanger, Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ShortcutCard(
                    icon = Icons.Rounded.ReceiptLong,
                    title = "Mis Gastos",
                    value = money(spent, state.expenseSettings.currencySymbol),
                    subtitle = "gastado este mes · ingresos ${money(earned, state.expenseSettings.currencySymbol)}",
                    color = AgendaAmber,
                    modifier = Modifier.weight(1f),
                    onClick = onOpenGastos,
                )
                ShortcutCard(
                    icon = Icons.Rounded.BusinessCenter,
                    title = "Por cobrar",
                    value = money(receivable),
                    subtitle = "clientes y finanzas",
                    color = AgendaBlue,
                    modifier = Modifier.weight(1f),
                    onClick = onOpenFinance,
                )
            }
        }
        if (overdue.isNotEmpty()) {
            item { SectionTitle("Vencidos", "Revisa estos recordatorios") }
            items(overdue, key = { "overdue_${it.id}" }) { TaskCard(it, actions, showDate = true) }
        }
        item { SectionTitle("Para hoy", if (today.isEmpty()) null else "${today.size} ${if (today.size == 1) "tarea" else "tareas"}") }
        if (today.isEmpty()) {
            item { EmptyState(Icons.Rounded.EventAvailable, "Tu día está libre", "Crea un recordatorio o toca el micrófono para comenzar.") }
        } else {
            items(today, key = { it.id }) { TaskCard(it, actions) }
        }
    }
}

@Composable
fun VoiceHero(onVoice: () -> Unit) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(listOf(AgendaBlue, AgendaBlueDark)))
                .clickable(onClick = onVoice)
                .padding(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Dímelo y lo agendo", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text("Organiza tu día hablando o escribiendo.", color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Ejemplo: mañana a las 9, enviar un WhatsApp a Carlos",
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Box(
                    Modifier.size(64.dp).clip(CircleShape).background(Color.White),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Mic, "Tocar para hablar", tint = AgendaBlue, modifier = Modifier.size(32.dp))
                }
            }
        }
    }
}

@Composable
fun MetricCard(title: String, value: String, subtitle: String, color: Color, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = color)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

@Composable
private fun ShortcutCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    value: String,
    subtitle: String,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        }
    }
}

// ---------------------------------------------------------------------------
// Tarjeta de recordatorio
// ---------------------------------------------------------------------------

@Composable
fun TaskCard(task: AgendaTask, actions: TaskActions, showDate: Boolean = false) {
    val now = System.currentTimeMillis()
    val overdue = !task.completed && task.dateTimeMillis < now
    Card(
        modifier = Modifier.fillMaxWidth().clickable { actions.onEdit(task) },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, if (overdue) AgendaDanger.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outline),
    ) {
        Row(Modifier.padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.Top) {
            Checkbox(checked = task.completed, onCheckedChange = { actions.onToggle(task, it) })
            Column(Modifier.weight(1f).padding(top = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (task.isWhatsApp) {
                        Icon(Icons.AutoMirrored.Rounded.Chat, null, tint = AgendaGreen, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        task.title,
                        fontWeight = FontWeight.SemiBold,
                        color = if (task.completed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (task.message.isNotBlank() && task.message != task.title) {
                    Text(task.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.AccessTime, null, modifier = Modifier.size(14.dp), tint = if (overdue) AgendaDanger else MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        if (showDate || !isSameDay(task.dateTimeMillis, now)) formatDate(task.dateTimeMillis, "EEE d MMM · $TIME_PATTERN") else formatDate(task.dateTimeMillis, TIME_PATTERN),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (overdue) AgendaDanger else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (task.isWhatsApp && task.phone.isNotBlank()) {
                        Spacer(Modifier.width(8.dp))
                        Text(task.phone, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                if (task.isWhatsApp) {
                    IconButton(onClick = { actions.onWhatsApp(task) }) {
                        Icon(Icons.AutoMirrored.Rounded.Send, "Enviar WhatsApp", tint = AgendaGreen)
                    }
                }
                IconButton(onClick = { actions.onDelete(task) }) {
                    Icon(Icons.Rounded.Delete, "Eliminar", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Próximos
// ---------------------------------------------------------------------------

@Composable
fun UpcomingScreen(state: AppState, actions: TaskActions) {
    var showCompleted by remember { mutableStateOf(false) }
    val now = System.currentTimeMillis()
    val list = state.tasks
        .filter { if (showCompleted) true else !it.completed }
        .filter { showCompleted || it.dateTimeMillis >= startOfDay(now) || !it.completed }
        .sortedBy { it.dateTimeMillis }
    val grouped = list.groupBy { startOfDay(it.dateTimeMillis) }

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Próximos", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("Todo lo que viene, ordenado por fecha.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                FilterChip(selected = showCompleted, onClick = { showCompleted = !showCompleted }, label = { Text("Completados") })
            }
        }
        if (list.isEmpty()) {
            item { EmptyState(Icons.Rounded.NotificationsActive, "Sin recordatorios", "Tus próximos recordatorios aparecerán aquí.") }
        }
        grouped.forEach { (day, tasks) ->
            item(key = "h_$day") {
                val label = when {
                    isSameDay(day, now) -> "Hoy"
                    isSameDay(day, now + 86_400_000L) -> "Mañana"
                    else -> formatDate(day, "EEEE d 'de' MMMM").replaceFirstChar { it.uppercase() }
                }
                Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 6.dp))
            }
            items(tasks, key = { it.id }) { TaskCard(it, actions) }
        }
    }
}

// ---------------------------------------------------------------------------
// Calendario
// ---------------------------------------------------------------------------

@Composable
fun CalendarScreen(state: AppState, actions: TaskActions, onNewForDate: (Long) -> Unit) {
    var monthAnchor by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var selectedDay by remember { mutableLongStateOf(startOfDay(System.currentTimeMillis())) }
    val dayTasks = state.tasks.filter { isSameDay(it.dateTimeMillis, selectedDay) }
    val busyDays = state.tasks.filter { !it.completed }.map { startOfDay(it.dateTimeMillis) }.toSet()

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            MonthCalendar(
                monthAnchor = monthAnchor,
                selectedDay = selectedDay,
                onMonthChange = { monthAnchor = it },
                onSelect = { selectedDay = it },
                dayDecoration = { day -> if (day in busyDays) AgendaBlue else null },
            )
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    formatDate(selectedDay, "EEEE d 'de' MMMM").replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = {
                    val c = Calendar.getInstance().apply {
                        timeInMillis = selectedDay
                        set(Calendar.HOUR_OF_DAY, 9)
                    }
                    onNewForDate(c.timeInMillis)
                }) { Text("+ Agregar") }
            }
        }
        if (dayTasks.isEmpty()) {
            item { EmptyState(Icons.Rounded.CalendarMonth, "Día disponible", "No tienes actividades para esta fecha.") }
        } else {
            items(dayTasks, key = { it.id }) { TaskCard(it, actions) }
        }
    }
}

/** Calendario mensual reutilizable (Agenda y Mis Gastos). */
@Composable
fun MonthCalendar(
    monthAnchor: Long,
    selectedDay: Long,
    onMonthChange: (Long) -> Unit,
    onSelect: (Long) -> Unit,
    dayDecoration: (Long) -> Color?,
    dayCaption: ((Long) -> String?)? = null,
) {
    val cal = Calendar.getInstance().apply {
        timeInMillis = monthAnchor
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }
    val firstDay = cal.timeInMillis
    val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    // lunes = 0
    val offset = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7
    val today = startOfDay(System.currentTimeMillis())

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = {
                    onMonthChange(Calendar.getInstance().apply { timeInMillis = firstDay; add(Calendar.MONTH, -1) }.timeInMillis)
                }) { Icon(Icons.Rounded.ChevronLeft, "Mes anterior") }
                Text(
                    formatDate(firstDay, "MMMM yyyy").replaceFirstChar { it.uppercase() },
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold,
                )
                IconButton(onClick = {
                    onMonthChange(Calendar.getInstance().apply { timeInMillis = firstDay; add(Calendar.MONTH, 1) }.timeInMillis)
                }) { Icon(Icons.Rounded.ChevronRight, "Mes siguiente") }
            }
            Row {
                listOf("L", "M", "M", "J", "V", "S", "D").forEach {
                    Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(4.dp))
            val cells = offset + daysInMonth
            val rows = (cells + 6) / 7
            for (r in 0 until rows) {
                Row {
                    for (c in 0 until 7) {
                        val index = r * 7 + c
                        val dayNumber = index - offset + 1
                        Box(Modifier.weight(1f).aspectRatio(if (dayCaption != null) 0.8f else 1f).padding(2.dp), contentAlignment = Alignment.Center) {
                            if (dayNumber in 1..daysInMonth) {
                                val day = Calendar.getInstance().apply { timeInMillis = firstDay; set(Calendar.DAY_OF_MONTH, dayNumber) }.timeInMillis
                                val selected = day == selectedDay
                                val deco = dayDecoration(day)
                                Column(
                                    Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
                                        .then(if (day == today && !selected) Modifier.border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp)) else Modifier)
                                        .clickable { onSelect(day) },
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                ) {
                                    Text(
                                        dayNumber.toString(),
                                        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                        fontWeight = if (day == today) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 14.sp,
                                    )
                                    val caption = dayCaption?.invoke(day)
                                    if (caption != null) {
                                        Text(
                                            caption,
                                            fontSize = 8.sp,
                                            maxLines = 1,
                                            color = if (selected) MaterialTheme.colorScheme.onPrimary else (deco ?: MaterialTheme.colorScheme.onSurfaceVariant),
                                        )
                                    } else if (deco != null) {
                                        Box(Modifier.size(5.dp).clip(CircleShape).background(if (selected) MaterialTheme.colorScheme.onPrimary else deco))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// WhatsApp
// ---------------------------------------------------------------------------

@Composable
fun WhatsAppScreen(state: AppState, actions: TaskActions) {
    val now = System.currentTimeMillis()
    val pending = state.tasks.filter { it.isWhatsApp && !it.completed }.sortedBy { it.dateTimeMillis }
    val done = state.tasks.filter { it.isWhatsApp && it.completed }.sortedByDescending { it.dateTimeMillis }.take(20)
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Column {
                Text("Mensajes agendados", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Agenda un WhatsApp y recíbelo como alerta a la hora indicada.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = AgendaGreenSoft)) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Rounded.Chat, null, tint = AgendaGreen)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "La app te avisará y abrirá WhatsApp con el texto listo. ${pending.count { it.dateTimeMillis < now }} vencidos · ${pending.size} pendientes",
                        style = MaterialTheme.typography.bodySmall,
                        color = AgendaGreenDark,
                    )
                }
            }
        }
        if (pending.isEmpty()) {
            item { EmptyState(Icons.AutoMirrored.Rounded.Chat, "No hay mensajes pendientes", "Mensajes que no quieres olvidar.") }
        } else {
            items(pending, key = { it.id }) { TaskCard(it, actions, showDate = true) }
        }
        if (done.isNotEmpty()) {
            item { SectionTitle("Enviados o completados") }
            items(done, key = { "d_${it.id}" }) { TaskCard(it, actions, showDate = true) }
        }
    }
}

// ---------------------------------------------------------------------------
// Editor de recordatorios
// ---------------------------------------------------------------------------

@Composable
fun TaskEditorDialog(request: TaskEditorRequest, onDismiss: () -> Unit, onSave: (AgendaTask) -> Unit) {
    val context = LocalContext.current
    val existing = request.task
    val draft = request.draft
    val defaultTime = remember {
        request.dateMillis ?: Calendar.getInstance().apply {
            add(Calendar.HOUR_OF_DAY, 1); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    var title by remember { mutableStateOf(existing?.title ?: draft?.title ?: "") }
    var message by remember { mutableStateOf(existing?.message ?: draft?.message ?: "") }
    var phone by remember { mutableStateOf(existing?.phone ?: "") }
    var isWhatsApp by remember { mutableStateOf(existing?.isWhatsApp ?: draft?.isWhatsApp ?: request.whatsApp) }
    var dateTime by remember { mutableLongStateOf(existing?.dateTimeMillis ?: draft?.dateTimeMillis ?: defaultTime) }
    var contactName by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    val contactLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val contact = ContactPicker.read(context, result.data?.data)
            if (contact != null) {
                phone = contact.phoneNumber
                contactName = contact.displayName
                if (title.isBlank() || title.startsWith("Enviar WhatsApp")) title = "Enviar WhatsApp a ${contact.displayName}"
            } else toast(context, "No se pudo leer el número del contacto seleccionado.")
        }
    }
    val dictate = rememberSpeechInput("Dicta el texto. Puedes tocar el micrófono otra vez para continuar.") { text ->
        if (isWhatsApp) message = if (message.isBlank()) text else "$message $text"
        else if (title.isBlank()) title = text.replaceFirstChar { it.uppercase() } else message = if (message.isBlank()) text else "$message $text"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        title = {
            Text(
                when {
                    existing != null -> "Editar recordatorio"
                    draft != null -> "Revisa la información antes de guardar"
                    isWhatsApp -> "Nuevo WhatsApp"
                    else -> "Nuevo recordatorio"
                },
            )
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Rounded.Chat, null, tint = if (isWhatsApp) AgendaGreen else MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Recordatorio de WhatsApp", fontWeight = FontWeight.SemiBold)
                        Text("Te avisaremos y abriremos el mensaje listo", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = isWhatsApp, onCheckedChange = { isWhatsApp = it })
                }
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(200); error = null },
                    label = { Text("¿Qué debes recordar?") },
                    placeholder = { Text("Ej. Llamar al proveedor") },
                    modifier = Modifier.fillMaxWidth(),
                    isError = error != null && title.isBlank(),
                )
                if (isWhatsApp) {
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it.take(25); contactName = null },
                        label = { Text("Número con código de país (opcional)") },
                        placeholder = { Text("Ej. +58 412 123 4567") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            IconButton(onClick = {
                                runCatching { contactLauncher.launch(ContactPicker.intent()) }
                                    .onFailure { toast(context, "No se pudo abrir la aplicación de contactos.") }
                            }) { Icon(Icons.Rounded.Contacts, "Buscar en mis contactos") }
                        },
                        supportingText = { contactName?.let { Text("Contacto seleccionado: $it") } },
                    )
                }
                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it.take(10000) },
                    label = { Text(if (isWhatsApp) "Mensaje de WhatsApp" else "Notas, texto o enlace") },
                    placeholder = { Text(if (isWhatsApp) "Escribe, pega o dicta el mensaje completo" else "Pega una publicación, enlace o dicta una nota larga") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 110.dp),
                    supportingText = { Text("${message.length}/10000") },
                )
                OutlinedButton(onClick = dictate, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Mic, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Dictar y agregar texto")
                }
                Text("Fecha y hora", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { pickDate(context, dateTime) { dateTime = it } }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.CalendarMonth, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(formatDate(dateTime, "EEE d MMM"), maxLines = 1)
                    }
                    OutlinedButton(onClick = { pickTime(context, dateTime) { dateTime = it } }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.AccessTime, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(formatDate(dateTime, TIME_PATTERN), maxLines = 1)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    QuickTimeChip("En 30 min") { dateTime = System.currentTimeMillis() + 30 * 60_000L }
                    QuickTimeChip("En 1 hora") { dateTime = System.currentTimeMillis() + 60 * 60_000L }
                    QuickTimeChip("Mañana 9 AM") {
                        dateTime = Calendar.getInstance().apply {
                            add(Calendar.DAY_OF_YEAR, 1); set(Calendar.HOUR_OF_DAY, 9); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                        }.timeInMillis
                    }
                }
                if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Button(onClick = {
                val finalTitle = title.trim().ifBlank {
                    if (isWhatsApp && message.isNotBlank()) "Enviar WhatsApp" else ""
                }
                when {
                    finalTitle.isBlank() -> error = "Escribe el recordatorio antes de guardarlo."
                    existing == null && dateTime <= System.currentTimeMillis() -> error = "Selecciona una fecha y hora futuras."
                    else -> onSave(
                        AgendaTask(
                            id = existing?.id ?: 0L,
                            title = finalTitle,
                            message = message.trim(),
                            phone = if (isWhatsApp) phone.trim() else "",
                            dateTimeMillis = dateTime,
                            isWhatsApp = isWhatsApp,
                            completed = if (existing != null && dateTime > System.currentTimeMillis()) false else (existing?.completed ?: false),
                        ),
                    )
                }
            }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun QuickTimeChip(label: String, onClick: () -> Unit) {
    Text(
        label,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onPrimaryContainer,
    )
}

