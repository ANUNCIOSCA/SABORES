@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.agendawa.app

import androidx.compose.foundation.layout.safeDrawingPadding
import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BusinessCenter
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.GroupAdd
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    val context = LocalContext.current
    var speak by remember { mutableStateOf(VoicePrefs.speakBack(context)) }
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
                        "Ej.: \"Escríbele a Carlos que mañana le pago y tengo reunión el viernes a las 10 en la oficina\"",
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.size(64.dp).clip(CircleShape).background(Color.White),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.Mic, "Tocar para hablar", tint = AgendaBlue, modifier = Modifier.size(32.dp))
                    }
                    Spacer(Modifier.height(6.dp))
                    // Escuchar o no la voz de confirmación ("Copiado, Alberto")
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable { speak = !speak; VoicePrefs.setSpeakBack(context, speak) }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            if (speak) Icons.AutoMirrored.Outlined.VolumeUp else Icons.AutoMirrored.Outlined.VolumeOff,
                            if (speak) "Voz activada" else "Voz desactivada",
                            tint = Color.White, modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(if (speak) "Voz sí" else "Voz no", color = Color.White, style = MaterialTheme.typography.labelSmall)
                    }
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
                task.repeat?.let { rule ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Repeat, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(Repeat.label(rule), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, maxLines = 2)
                    }
                }
                if (task.message.isNotBlank() && task.message != task.title) {
                    Text(task.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                } else if (task.isWhatsApp && !task.completed) {
                    Text("Sin mensaje todavía", style = MaterialTheme.typography.bodySmall, color = AgendaDanger)
                }
                // Temas a tratar de la reunión o llamada
                task.topicList.takeIf { it.isNotEmpty() }?.let { list ->
                    list.take(4).forEach { t -> Text("• $t", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    if (list.size > 4) Text("y ${list.size - 4} más…", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (task.isWhatsApp && !task.completed && actions.onDraft != null) {
                    TextButton(onClick = { actions.onDraft?.invoke(task) }, contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp)) {
                        Icon(Icons.Rounded.AutoAwesome, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (task.message.isBlank() || task.message == task.title) "Redactar mensaje" else "Redactar de nuevo")
                    }
                }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.AccessTime, null, modifier = Modifier.size(14.dp), tint = if (overdue) AgendaDanger else MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(4.dp))
                    // La hora de la reunión; si el aviso suena antes, también se ve a qué hora
                    val shown = task.whenMillis
                    Text(
                        (if (showDate || !isSameDay(shown, now)) formatDate(shown, "EEE d MMM · $TIME_PATTERN") else formatDate(shown, TIME_PATTERN)) +
                            if (task.eventMillis > 0 && task.dateTimeMillis != task.eventMillis) " · aviso ${formatDate(task.dateTimeMillis, TIME_PATTERN)}" else "",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (overdue) AgendaDanger else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (task.isWhatsApp && task.toGroup) {
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.Rounded.Groups, null, tint = AgendaGreen, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(3.dp))
                        Text(
                            task.groups.joinToString(", ") { it.name }.ifBlank { "Grupo (eliges al enviar)" },
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    } else if (task.isWhatsApp && task.phone.isNotBlank()) {
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
    var phone by remember { mutableStateOf(existing?.phone ?: draft?.phone ?: "") }
    var isWhatsApp by remember { mutableStateOf(existing?.isWhatsApp ?: draft?.isWhatsApp ?: request.whatsApp) }
    // dateTime = cuándo es (la reunión); el aviso suena [lead] minutos antes
    var dateTime by remember {
        mutableLongStateOf(
            existing?.whenMillis ?: draft?.let { if (it.eventMillis > 0) it.eventMillis else it.dateTimeMillis } ?: defaultTime,
        )
    }
    val initialLead = existing?.let { if (it.eventMillis > 0) it.leadMinutes else 0 } ?: draft?.let { if (it.eventMillis > 0) it.leadMinutes else 0 } ?: 0
    var lead by remember { mutableIntStateOf(initialLead) }
    var topics by remember { mutableStateOf(existing?.topics ?: draft?.topics ?: "") }
    var showTopics by remember { mutableStateOf(topics.isNotBlank()) }
    val dictateTopics = rememberSpeechInput("Dime los temas a tratar") { text ->
        val items = TopicParser.items(text)
        topics = (topics.lines().filter { it.isNotBlank() } + items.map { "• $it" }).joinToString("\n")
    }
    var contactName by remember { mutableStateOf(draft?.contactName) }
    var error by remember { mutableStateOf<String?>(null) }
    // WhatsApp a un grupo (o varios) en vez de a un contacto
    var toGroup by remember { mutableStateOf(existing?.toGroup ?: draft?.toGroup ?: false) }
    var selectedGroups by remember { mutableStateOf(existing?.groups ?: draft?.groups ?: emptyList()) }
    var favoriteGroups by remember { mutableStateOf(GroupStore.load(context)) }
    val retitle: (List<WaGroup>) -> Unit = { list ->
        if (title.isBlank() || title.startsWith("Enviar WhatsApp") || title.startsWith("WhatsApp al grupo") || title.startsWith("WhatsApp a los grupos")) {
            title = when (list.size) {
                0 -> "WhatsApp al grupo"
                1 -> "WhatsApp al grupo ${list.first().name}"
                else -> "WhatsApp a los grupos " + list.joinToString(", ") { it.name }
            }
        }
    }
    // Repetir: 0 = una sola vez, 1 = todos los días, 2 = lunes a viernes, 3 = días que elija
    val initialRule = existing?.repeat ?: draft?.repeat
    var repeatMode by remember {
        mutableStateOf(
            when (initialRule?.days) {
                null -> 0
                Repeat.ALL -> 1
                Repeat.WEEKDAYS -> 2
                else -> 3
            },
        )
    }
    var customDays by remember {
        mutableStateOf(initialRule?.days ?: (1 shl (Calendar.getInstance().apply { timeInMillis = dateTime }.get(Calendar.DAY_OF_WEEK) + 5) % 7))
    }
    var perDayTime by remember { mutableStateOf(initialRule?.dayMinutes?.isNotEmpty() == true) }
    // Las horas por día guardadas son las del aviso: se muestran como las del evento
    var dayMinutes by remember { mutableStateOf<Map<Int, Int>>(initialRule?.dayMinutes?.mapValues { it.value + initialLead } ?: emptyMap()) }
    var repeatUntil by remember { mutableLongStateOf(initialRule?.until ?: 0L) }
    val repeatDays = when (repeatMode) { 1 -> Repeat.ALL; 2 -> Repeat.WEEKDAYS; 3 -> customDays; else -> 0 }
    val aiEnabled = remember { AiSettings.isEnabled(context) }
    var polishing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Lista propia con todas las cuentas del teléfono (Google, teléfono, SIM, WhatsApp...)
    val pickContact = rememberContactPicker { contact ->
        phone = contact.phoneNumber
        contactName = contact.displayName
        if (title.isBlank() || title.startsWith("Enviar WhatsApp")) title = "Enviar WhatsApp a ${contact.displayName}"
        else if (!isWhatsApp && (title.startsWith("Llamar"))) title = "Llamar a ${contact.displayName}"
    }
    val dictate = rememberSpeechInput("Dicta el texto. Puedes tocar el micrófono otra vez para continuar.") { text ->
        if (isWhatsApp) message = if (message.isBlank()) text else "$message $text"
        else if (title.isBlank()) title = text.replaceFirstChar { it.uppercase() } else message = if (message.isBlank()) text else "$message $text"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = WideDialogProperties,
        modifier = Modifier.fillMaxWidth().safeDrawingPadding().padding(16.dp),
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
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(selected = !toGroup, onClick = { toGroup = false }, label = { Text("A un contacto") }, modifier = Modifier.weight(1f),
                            leadingIcon = { Icon(Icons.Rounded.Person, null, modifier = Modifier.size(16.dp)) })
                        FilterChip(selected = toGroup, onClick = { toGroup = true; retitle(selectedGroups) }, label = { Text("A un grupo") }, modifier = Modifier.weight(1f),
                            leadingIcon = { Icon(Icons.Rounded.Groups, null, modifier = Modifier.size(16.dp)) })
                    }
                }
                if (isWhatsApp && toGroup) {
                    GroupPicker(
                        favorites = favoriteGroups,
                        selected = selectedGroups,
                        onToggle = { g ->
                            selectedGroups = if (selectedGroups.any { it.name == g.name }) selectedGroups.filterNot { it.name == g.name } else selectedGroups + g
                            retitle(selectedGroups)
                        },
                        onAdd = { g ->
                            GroupStore.upsert(context, g)
                            favoriteGroups = GroupStore.load(context)
                            selectedGroups = selectedGroups.filterNot { it.name.equals(g.name, true) } + g
                            retitle(selectedGroups)
                        },
                        onRemove = { g ->
                            GroupStore.remove(context, g)
                            favoriteGroups = GroupStore.load(context)
                        },
                    )
                } else run {
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it.take(25); contactName = null },
                        label = { Text(if (isWhatsApp) "Número con código de país (opcional)" else "Teléfono para llamar (opcional)") },
                        placeholder = { Text("Ej. +58 412 123 4567") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            IconButton(onClick = pickContact) { Icon(Icons.Rounded.Contacts, "Buscar en mis contactos") }
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
                if (isWhatsApp && aiEnabled) {
                    OutlinedButton(
                        enabled = message.isNotBlank() && !polishing,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            polishing = true
                            scope.launch {
                                val person = contactName ?: title.substringAfter("Enviar WhatsApp a ", "")
                                runCatching { withContext(Dispatchers.IO) { AiAssistant.polishWhatsApp(context, message, person) } }
                                    .onSuccess { message = it }
                                    .onFailure { toast(context, it.message ?: "No se pudo redactar") }
                                polishing = false
                            }
                        },
                    ) {
                        if (polishing) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        else { Icon(Icons.Rounded.AutoAwesome, null); Spacer(Modifier.width(6.dp)); Text("Redactar mensaje con IA") }
                    }
                }
                Text(if (repeatMode != 0) "Desde qué día y a qué hora" else "Fecha y hora", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
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
                if (!isWhatsApp) {
                    Text("Avisarme", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(0 to "A la hora", 15 to "15 min antes", 30 to "30 min antes", 60 to "1 h antes", 120 to "2 h antes", 1440 to "1 día antes").forEach { (m, l) ->
                            FilterChip(selected = lead == m, onClick = { lead = m }, label = { Text(l, maxLines = 1) })
                        }
                    }
                    if (lead > 0) Text(
                        "Sonará ${formatDate(dateTime - lead * 60_000L, "EEE d MMM · $TIME_PATTERN")}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary,
                    )
                    if (showTopics) {
                        OutlinedTextField(
                            value = topics,
                            onValueChange = { topics = it.take(3000) },
                            label = { Text("Temas a tratar (uno por línea)") },
                            placeholder = { Text("• Presupuesto\n• Fecha de entrega") },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
                            trailingIcon = { IconButton(onClick = dictateTopics) { Icon(Icons.Rounded.Mic, "Dictar temas") } },
                        )
                    } else {
                        TextButton(onClick = { showTopics = true }) { Text("+ Temas a tratar") }
                    }
                }
                RepeatSection(
                    mode = repeatMode,
                    onMode = { repeatMode = it; error = null },
                    days = repeatDays,
                    onToggleDay = { d -> customDays = customDays xor (1 shl d); error = null },
                    baseMinutes = Repeat.minutesOf(dateTime),
                    perDayTime = perDayTime,
                    onPerDayTime = { perDayTime = it },
                    dayMinutes = dayMinutes,
                    onDayTime = { d, m -> dayMinutes = dayMinutes + (d to m) },
                    until = repeatUntil,
                    onUntil = { repeatUntil = it },
                )
                if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Button(onClick = {
                val leadUsed = if (isWhatsApp) 0 else lead
                val base = Repeat.minutesOf(dateTime)
                // La regla guarda la hora del aviso (la del evento menos el aviso previo)
                val rule = if (repeatMode == 0 || repeatDays == 0) null else RepeatParser.resolve(
                    RepeatRule(
                        days = repeatDays,
                        minutes = base,
                        dayMinutes = if (perDayTime) (0..6).filter { (repeatDays and (1 shl it)) != 0 }.associateWith { dayMinutes[it] ?: base } else emptyMap(),
                        until = repeatUntil,
                    ),
                    dateTime, leadUsed,
                )
                // Si se repite, la primera vez es la próxima fecha válida desde el día elegido
                val firstAt = rule?.let { Repeat.next(it, maxOf(System.currentTimeMillis(), startOfDay(dateTime - leadUsed * 60_000L) - 1)) }
                // Sin repetir: el aviso suena [leadUsed] minutos antes (si eso ya pasó, en un minuto)
                val alarmAt = firstAt ?: (dateTime - leadUsed * 60_000L).let { a ->
                    if (a <= System.currentTimeMillis() && dateTime > System.currentTimeMillis()) System.currentTimeMillis() + 60_000L else a
                }
                val eventAt = when {
                    leadUsed == 0 -> 0L
                    firstAt != null -> firstAt + leadUsed * 60_000L
                    else -> dateTime
                }
                val finalTitle = title.trim().ifBlank {
                    if (isWhatsApp && message.isNotBlank()) "Enviar WhatsApp" else ""
                }
                when {
                    finalTitle.isBlank() -> error = "Escribe el recordatorio antes de guardarlo."
                    repeatMode == 3 && repeatDays == 0 -> error = "Marca al menos un día para repetir."
                    rule != null && firstAt == null -> error = "La fecha final ya pasó. Cámbiala o quítala."
                    rule == null && existing == null && dateTime <= System.currentTimeMillis() -> error = "Selecciona una fecha y hora futuras."
                    else -> onSave(
                        AgendaTask(
                            id = existing?.id ?: 0L,
                            title = finalTitle,
                            message = message.trim(),
                            dateTimeMillis = alarmAt,
                            eventMillis = eventAt,
                            leadMinutes = if (eventAt > 0) leadUsed else 0,
                            topics = if (isWhatsApp) "" else topics.lines().map { it.trim() }.filter { it.isNotBlank() }.joinToString("\n"),
                            isWhatsApp = isWhatsApp,
                            completed = if (firstAt != null || (existing != null && dateTime > System.currentTimeMillis())) false else (existing?.completed ?: false),
                            repeat = rule,
                            toGroup = isWhatsApp && toGroup,
                            groups = if (isWhatsApp && toGroup) selectedGroups else emptyList(),
                            phone = if (isWhatsApp && toGroup) "" else phone.trim(),
                        ),
                    )
                }
            }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

/** Repetir: una vez, todos los días, lunes a viernes o días elegidos; misma hora o una por día; fecha final. */
@Composable
private fun RepeatSection(
    mode: Int,
    onMode: (Int) -> Unit,
    days: Int,
    onToggleDay: (Int) -> Unit,
    baseMinutes: Int,
    perDayTime: Boolean,
    onPerDayTime: (Boolean) -> Unit,
    dayMinutes: Map<Int, Int>,
    onDayTime: (Int, Int) -> Unit,
    until: Long,
    onUntil: (Long) -> Unit,
) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Repeat, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(6.dp))
            Text("Repetir", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(selected = mode == 0, onClick = { onMode(0) }, label = { Text("Una vez") }, modifier = Modifier.weight(1f))
            FilterChip(selected = mode == 1, onClick = { onMode(1) }, label = { Text("Todos los días", maxLines = 1) }, modifier = Modifier.weight(1.4f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(selected = mode == 2, onClick = { onMode(2) }, label = { Text("Lun a Vie") }, modifier = Modifier.weight(1f))
            FilterChip(selected = mode == 3, onClick = { onMode(3) }, label = { Text("Elegir días", maxLines = 1) }, modifier = Modifier.weight(1.4f))
        }
        if (mode == 3) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                (0..6).forEach { d ->
                    val on = (days and (1 shl d)) != 0
                    Box(
                        Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(CircleShape)
                            .background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { onToggleDay(d) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            Repeat.letters[d],
                            fontWeight = FontWeight.Bold,
                            color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        if (mode != 0 && days != 0) {
            val selected = (0..6).filter { (days and (1 shl it)) != 0 }
            if (selected.size > 1) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Hora distinta para cada día")
                        Text(
                            if (perDayTime) "Toca la hora de cada día para cambiarla" else "Todos a las ${Repeat.timeText(baseMinutes)}",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = perDayTime, onCheckedChange = onPerDayTime)
                }
            }
            if (perDayTime && selected.size > 1) {
                selected.forEach { d ->
                    val m = dayMinutes[d] ?: baseMinutes
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(Repeat.longNames[d].replaceFirstChar { it.uppercase() }, modifier = Modifier.weight(1f))
                        OutlinedButton(onClick = {
                            pickTime(context, startOfDay(System.currentTimeMillis()) + m * 60_000L) { onDayTime(d, Repeat.minutesOf(it)) }
                        }) {
                            Icon(Icons.Rounded.AccessTime, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(Repeat.timeText(m))
                        }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(
                    onClick = { pickDate(context, if (until > 0) until else System.currentTimeMillis() + 30 * 86_400_000L) { onUntil(startOfDay(it)) } },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Rounded.EventAvailable, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (until > 0) "Hasta el ${formatDate(until, "d MMM yyyy")}" else "Sin fecha final", maxLines = 1)
                }
                if (until > 0) TextButton(onClick = { onUntil(0L) }) { Text("Quitar") }
            }
            Text(
                "Se repite: " + Repeat.label(
                    RepeatRule(days, baseMinutes, if (perDayTime) selected.associateWith { dayMinutes[it] ?: baseMinutes } else emptyMap(), until),
                ),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** Grupos favoritos: marcar uno o varios, agregar nuevos (con su enlace de invitación) o quitarlos. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GroupPicker(
    favorites: List<WaGroup>,
    selected: List<WaGroup>,
    onToggle: (WaGroup) -> Unit,
    onAdd: (WaGroup) -> Unit,
    onRemove: (WaGroup) -> Unit,
) {
    var adding by remember { mutableStateOf(false) }
    var managing by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("¿A qué grupo(s)?", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        if (favorites.isEmpty()) {
            Text(
                "Guarda tus grupos para elegirlos rápido. Si no marcas ninguno, a la hora eliges el grupo en WhatsApp.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            favorites.forEach { g ->
                val on = selected.any { it.name == g.name }
                FilterChip(
                    selected = on,
                    onClick = { if (managing) onRemove(g) else onToggle(g) },
                    label = { Text(g.name) },
                    leadingIcon = {
                        Icon(
                            when {
                                managing -> Icons.Rounded.Delete
                                on -> Icons.Rounded.CheckCircle
                                else -> Icons.Rounded.Groups
                            },
                            null, modifier = Modifier.size(16.dp),
                        )
                    },
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { adding = true }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Rounded.GroupAdd, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Agregar grupo", maxLines = 1)
            }
            if (favorites.isNotEmpty()) {
                TextButton(onClick = { managing = !managing }) { Text(if (managing) "Listo" else "Quitar grupos") }
            }
        }
        if (managing) Text("Toca un grupo para quitarlo de tus favoritos.", style = MaterialTheme.typography.bodySmall, color = AgendaDanger)
        Text(
            when {
                selected.isEmpty() -> "A la hora se abre WhatsApp con el mensaje listo y eliges el grupo."
                selected.size == 1 -> "A la hora se abre WhatsApp con el mensaje ya escrito: tocas \"${selected.first().name}\" y enviar."
                else -> "A la hora puedes enviarlo a todos de una vez (los marcas en WhatsApp) o abrirlos uno por uno."
            },
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary,
        )
    }
    if (adding) AddGroupDialog(onDismiss = { adding = false }, onSave = { onAdd(it); adding = false })
}

@Composable
private fun AddGroupDialog(onDismiss: () -> Unit, onSave: (WaGroup) -> Unit) {
    var name by remember { mutableStateOf("") }
    var link by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.GroupAdd, null, tint = AgendaGreen) },
        title = { Text("Nuevo grupo") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it.take(60); error = null },
                    label = { Text("Nombre del grupo") }, placeholder = { Text("Ej. Equipo R24") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = link, onValueChange = { link = it.trim().take(200); error = null },
                    label = { Text("Enlace de invitación (opcional)") }, placeholder = { Text("https://chat.whatsapp.com/...") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "Con el enlace, la app abre ese grupo directo. Para copiarlo: en WhatsApp entra al grupo → toca el nombre → " +
                        "\"Invitar al grupo mediante enlace\" → Copiar enlace. (Solo lo ven los administradores, o todos si el grupo lo permite.)\n" +
                        "Sin enlace también funciona: eliges el grupo en la lista de WhatsApp.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            Button(onClick = {
                when {
                    name.isBlank() -> error = "Ponle un nombre al grupo."
                    link.isNotBlank() && !WhatsApp.isGroupLink(link) -> error = "Ese enlace no es de un grupo (debe ser chat.whatsapp.com/...)."
                    else -> onSave(WaGroup(name.trim(), link.trim()))
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

// ---------------------------------------------------------------------------
// Redactar el mensaje de un WhatsApp programado
// ---------------------------------------------------------------------------

@Composable
fun DraftMessageDialog(task: AgendaTask, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val aiEnabled = remember { AiSettings.isEnabled(context) }
    val person = if (task.toGroup) "el grupo " + task.groups.joinToString(" y ") { it.name }.ifBlank { "de WhatsApp" }
    else task.title.substringAfter("Enviar WhatsApp a ", "").trim()
    var intent by remember { mutableStateOf(if (task.message == task.title) "" else task.message) }
    var draft by remember { mutableStateOf("") }
    var working by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val dictate = rememberSpeechInput("¿Qué le quieres decir${if (person.isNotBlank()) " a $person" else ""}?") { text ->
        intent = if (intent.isBlank()) text else "$intent $text"
    }
    val write = {
        working = true; error = null
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { AiAssistant.polishWhatsApp(context, intent, person) } }
                .onSuccess { draft = it }
                .onFailure { error = it.message }
            working = false
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = WideDialogProperties,
        modifier = Modifier.fillMaxWidth().safeDrawingPadding().padding(16.dp),
        title = { Text(if (person.isNotBlank()) "Mensaje para $person" else "Redactar mensaje") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = intent,
                    onValueChange = { intent = it.take(2000); error = null },
                    label = { Text("¿Qué le quieres decir?") },
                    placeholder = { Text("Ej.: que mañana le paso el presupuesto y que me confirme la hora") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 90.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = dictate, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.Mic, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Dictar")
                    }
                    Button(onClick = { write() }, enabled = aiEnabled && intent.isNotBlank() && !working, modifier = Modifier.weight(1f)) {
                        if (working) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        else { Icon(Icons.Rounded.AutoAwesome, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(if (draft.isBlank()) "Redactar" else "Otra versión") }
                    }
                }
                if (!aiEnabled) {
                    Text(
                        "Activa la IA en Configuración para que lo redacte. Mientras tanto se guarda tal cual lo escribas.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (draft.isNotBlank()) {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it.take(4000) },
                        label = { Text("Mensaje listo (puedes ajustarlo)") },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                    )
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(draft.ifBlank { intent }.trim()) },
                enabled = draft.isNotBlank() || intent.isNotBlank(),
            ) { Text("Guardar mensaje") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

/**
 * Al tocar un WhatsApp pendiente (o su notificación) lo primero es ENVIAR:
 * se muestra el mensaje listo y un botón grande "Enviar". Editar queda como opción secundaria.
 */
@Composable
fun SendWhatsAppDialog(
    task: AgendaTask,
    onDismiss: () -> Unit,
    onSend: () -> Unit,
    onDraft: () -> Unit,
    onEdit: () -> Unit,
    onOpenGroup: (WaGroup) -> Unit = {},
    onFinish: () -> Unit = {},
) {
    val person = when {
        task.toGroup && task.groups.isNotEmpty() -> task.groups.joinToString(", ") { it.name }
        task.toGroup -> "un grupo"
        else -> task.title.substringAfter("Enviar WhatsApp a ", "").trim().ifBlank { task.title }
    }
    val message = task.message.takeIf { it.isNotBlank() && it != task.title }
    // Varios grupos: se abren uno por uno (o todos a la vez desde la lista de WhatsApp)
    val several = task.toGroup && task.groups.size > 1
    var opened by remember { mutableStateOf(setOf<String>()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = WideDialogProperties,
        modifier = Modifier.fillMaxWidth().safeDrawingPadding().padding(16.dp),
        icon = { Icon(Icons.AutoMirrored.Rounded.Chat, null, tint = AgendaGreen) },
        title = { Text("WhatsApp para $person", textAlign = TextAlign.Center) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (task.phone.isNotBlank()) {
                    Text(task.phone, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (message != null) {
                    Box(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(AgendaGreenSoft).padding(12.dp),
                    ) { Text(message, color = Color(0xFF14321F)) }
                } else {
                    Text(
                        "Todavía no tiene mensaje. Puedes redactarlo ahora o enviar y escribirlo en WhatsApp.",
                        style = MaterialTheme.typography.bodySmall, color = AgendaDanger,
                    )
                }
                if (several) {
                    Text("Envíalo a todos de una vez (los marcas en WhatsApp) o grupo por grupo; el mensaje va ya escrito:", style = MaterialTheme.typography.bodySmall)
                    task.groups.forEach { g ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (g.name in opened) Icons.Rounded.CheckCircle else Icons.Rounded.Groups, null,
                                tint = if (g.name in opened) AgendaGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(g.name, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            OutlinedButton(onClick = { opened = opened + g.name; onOpenGroup(g) }) { Text(if (g.name in opened) "Otra vez" else "Enviar") }
                        }
                    }
                }
                Button(
                    onClick = onSend,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AgendaGreen, contentColor = Color.White),
                ) {
                    Icon(Icons.AutoMirrored.Rounded.Send, null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        when {
                            several -> "Enviar a todos (los marcas en WhatsApp)"
                            task.toGroup && task.groups.isEmpty() -> "Enviar (eliges el grupo)"
                            else -> "Enviar"
                        },
                        fontWeight = FontWeight.Bold, maxLines = 1,
                    )
                }
                if (several && opened.isNotEmpty()) {
                    TextButton(onClick = onFinish, modifier = Modifier.fillMaxWidth()) { Text("Ya los envié todos") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onDraft, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.AutoAwesome, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp))
                        Text(if (message == null) "Redactar" else "Redactar otro", maxLines = 1)
                    }
                    OutlinedButton(onClick = onEdit, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.Edit, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp))
                        Text("Editar", maxLines = 1)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
    )
}
