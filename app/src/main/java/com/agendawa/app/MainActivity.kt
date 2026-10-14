@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.agendawa.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.BusinessCenter
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : FragmentActivity() {
    companion object {
        const val EXTRA_OPEN_SECTION = "open_section"
        const val ACTION_VOICE = "com.agendawa.app.VOICE"
        /** Si la app estuvo en segundo plano más de esto, vuelve a pedir la huella. */
        private const val RELOCK_AFTER_MS = 60_000L
    }

    private val sharedContent = mutableStateOf<SharedContent?>(null)
    private val openSection = mutableStateOf<String?>(null)
    private val focusTaskId = mutableStateOf<Long?>(null)
    private val locked = mutableStateOf(false)
    private val voiceRequested = mutableStateOf(false)
    private var backgroundSince = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        NotificationHelper.createChannels(this)
        handleIntent(intent)
        locked.value = AppLock.isEnabled(this)
        setContent {
            Box(Modifier.fillMaxSize()) {
                AgendaWaApp(
                    sharedContent = sharedContent,
                    openSection = openSection,
                    focusTaskId = focusTaskId,
                    voiceRequested = voiceRequested,
                    locked = locked,
                )
                if (locked.value) AgendaWaTheme { LockScreen(onUnlock = { locked.value = false }) }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (backgroundSince > 0 && AppLock.isEnabled(this) &&
            System.currentTimeMillis() - backgroundSince > RELOCK_AFTER_MS
        ) locked.value = true
        backgroundSince = 0L
    }

    override fun onStop() {
        super.onStop()
        backgroundSince = System.currentTimeMillis()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        if (intent.action == ACTION_VOICE) voiceRequested.value = true
        SharedContentParser.from(intent)?.let { sharedContent.value = it }
        intent.getStringExtra(EXTRA_OPEN_SECTION)?.let { openSection.value = it }
        val id = intent.getLongExtra(ReminderScheduler.EXTRA_TASK_ID, -1L)
        if (id != -1L) focusTaskId.value = id
    }
}

enum class AppSection { AGENDA, GASTOS, FINANZAS }

private data class NavDestination(val label: String, val icon: ImageVector)

private val agendaTabs = listOf(
    NavDestination("Hoy", Icons.Rounded.Home),
    NavDestination("Próximos", Icons.Rounded.Schedule),
    NavDestination("Notas", Icons.Rounded.Lightbulb),
    NavDestination("Calendario", Icons.Rounded.DateRange),
    NavDestination("WhatsApp", Icons.AutoMirrored.Rounded.Chat),
)

/** Datos para abrir el editor de recordatorios. */
data class TaskEditorRequest(
    val task: AgendaTask? = null,
    val draft: ReminderDraft? = null,
    val whatsApp: Boolean = false,
    val dateMillis: Long? = null,
)

data class NoteEditorRequest(
    val note: SmartNote? = null,
    val prefillTitle: String = "",
    val prefillText: String = "",
    val prefillType: SmartNoteType? = null,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgendaWaApp(
    sharedContent: MutableState<SharedContent?>,
    openSection: MutableState<String?>,
    focusTaskId: MutableState<Long?>,
    voiceRequested: MutableState<Boolean>,
    locked: State<Boolean>,
) {
    val context = LocalContext.current
    val state = remember { AppState(context.applicationContext) }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val showMessage: (String) -> Unit = { msg -> scope.launch { snackbar.currentSnackbarData?.dismiss(); snackbar.showSnackbar(msg) } }

    var section by remember { mutableStateOf(AppSection.AGENDA) }
    var tab by remember { mutableIntStateOf(0) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)

    var taskEditor by remember { mutableStateOf<TaskEditorRequest?>(null) }
    var noteEditor by remember { mutableStateOf<NoteEditorRequest?>(null) }
    var confirmDeleteTask by remember { mutableStateOf<AgendaTask?>(null) }
    var confirmDeleteNote by remember { mutableStateOf<SmartNote?>(null) }
    var showBackup by remember { mutableStateOf(false) }
    var showProfile by remember { mutableStateOf(false) }
    var showAlarmSettings by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var thinking by remember { mutableStateOf<String?>(null) }
    var contactChoice by remember { mutableStateOf<Pair<VoiceIntent, List<SelectedPhoneContact>>?>(null) }
    var waitingContactsPermission by remember { mutableStateOf<VoiceIntent?>(null) }
    var showMenu by remember { mutableStateOf(false) }
    var showWelcome by remember {
        mutableStateOf(context.getSharedPreferences("agenda_wa_profile", Context.MODE_PRIVATE).getBoolean("welcome_pending", true) && state.profileName.isBlank())
    }

    // Permiso de notificaciones (Android 13+)
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) showMessage("Activa las notificaciones para recibir las alertas.")
    }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 && !NotificationHelper.notificationsAllowed(context)) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        ReminderScheduler.rescheduleAll(context)
        ExpenseReminder.apply(context)
    }

    // Intents externos
    LaunchedEffect(openSection.value) {
        when (openSection.value) {
            "gastos" -> section = AppSection.GASTOS
            "finanzas" -> section = AppSection.FINANZAS
        }
        openSection.value = null
    }
    LaunchedEffect(focusTaskId.value) {
        val id = focusTaskId.value ?: return@LaunchedEffect
        state.reloadAll()
        state.tasks.firstOrNull { it.id == id }?.let {
            section = AppSection.AGENDA
            taskEditor = TaskEditorRequest(task = it)
        }
        focusTaskId.value = null
    }

    // ------------------------------------------------- asistente de voz
    // Todo por voz: una frase puede traer varias cosas. Cada una se guarda sola cuando está
    // completa; si falta algo (qué persona, qué mensaje) se pregunta, y al final se confirma en voz alta.

    val speaker = rememberSpeaker()
    var voiceQueue by remember { mutableStateOf<List<VoiceIntent>>(emptyList()) }
    var voiceBusy by remember { mutableStateOf(false) }
    val voiceDone = remember { mutableStateListOf<String>() }
    var editorFromVoice by remember { mutableStateOf(false) }
    var askingMessage by remember { mutableStateOf<Pair<VoiceIntent, SelectedPhoneContact>?>(null) }

    /** Termina la acción actual; [said] es lo que se confirma al final. */
    val finishVoice: (String?) -> Unit = { said ->
        if (!said.isNullOrBlank()) voiceDone += said
        voiceBusy = false
    }

    val saveIdea: (VoiceIntent) -> Unit = { vi ->
        val content = vi.content.ifBlank { vi.title }
        val org = SmartNoteOrganizer.organize(content, vi.title)
        val now = System.currentTimeMillis()
        state.saveNote(
            SmartNote(
                id = 0L,
                title = vi.title.ifBlank { org.title },
                content = content,
                type = if (vi.kind == VoiceIntentKind.IDEA) SmartNoteType.IDEA else org.type,
                tags = org.tags,
                createdAtMillis = now,
                updatedAtMillis = now,
            ),
        )
        section = AppSection.AGENDA; tab = 2
        finishVoice(if (vi.kind == VoiceIntentKind.IDEA) "Idea guardada en notas" else "Nota guardada")
    }

    /** Recordatorios, reuniones y viajes: se guardan de una vez. */
    val saveReminder: (VoiceIntent) -> Unit = { vi ->
        val (_, result) = state.createTask(vi.title, vi.details(), "", vi.dateTimeMillis, false)
        val whenText = spokenWhen(if (vi.eventMillis > 0) vi.eventMillis else vi.dateTimeMillis)
        val what = when (vi.kind) {
            VoiceIntentKind.MEETING -> "${vi.title}, $whenText. Te aviso antes"
            VoiceIntentKind.TRAVEL -> "${vi.title}, $whenText. Te aviso con tiempo"
            else -> "${vi.title}, $whenText"
        }
        finishVoice(what + if (result == ScheduleResult.APPROXIMATE) " (activa las alarmas exactas)" else "")
    }

    val openEditorFromVoice: (ReminderDraft) -> Unit = { draft ->
        editorFromVoice = true
        taskEditor = TaskEditorRequest(draft = draft)
    }

    val askMessageByVoice = rememberSpeechInput(
        "Dime el mensaje que quieres enviar",
        onCancel = {
            askingMessage?.let { (vi, c) ->
                askingMessage = null
                openEditorFromVoice(
                    ReminderDraft("Enviar WhatsApp a ${c.displayName}", "", vi.dateTimeMillis, true, c.phoneNumber, c.displayName),
                )
            }
        },
    ) { said ->
        val (vi, contact) = askingMessage ?: return@rememberSpeechInput
        askingMessage = null
        scope.launch {
            val msg = if (AiSettings.isEnabled(context)) {
                thinking = "Redactando el mensaje..."
                withContext(Dispatchers.IO) {
                    runCatching { AiAssistant.polishWhatsApp(context, said, contact.displayName) }.getOrDefault(said)
                }.also { thinking = null }
            } else said.replaceFirstChar { it.uppercase() }
            val title = "Enviar WhatsApp a ${contact.displayName}"
            state.createTask(title, msg, contact.phoneNumber, vi.dateTimeMillis, true)
            finishVoice("WhatsApp para ${contact.displayName} listo, ${spokenWhen(vi.dateTimeMillis)}")
        }
    }

    val scheduleForContact: (VoiceIntent, SelectedPhoneContact?) -> Unit = { vi, contact ->
        val isWa = vi.kind == VoiceIntentKind.WHATSAPP
        val title = when {
            contact == null -> vi.title
            isWa -> "Enviar WhatsApp a ${contact.displayName}"
            else -> "Llamar a ${contact.displayName}"
        }
        when {
            contact == null -> openEditorFromVoice(ReminderDraft(title, vi.message, vi.dateTimeMillis, isWa))
            // WhatsApp sin mensaje: se pregunta por voz qué escribirle
            isWa && vi.message.isBlank() -> {
                askingMessage = vi to contact
                showMessage("¿Qué le escribo a ${contact.displayName}?")
                askMessageByVoice()
            }
            else -> {
                val (_, result) = state.createTask(title, vi.message, contact.phoneNumber, vi.dateTimeMillis, isWa)
                val whenText = spokenWhen(vi.dateTimeMillis)
                finishVoice(
                    (if (isWa) "WhatsApp para ${contact.displayName} listo, $whenText" else "Llamar a ${contact.displayName}, $whenText") +
                        if (result == ScheduleResult.APPROXIMATE) " (activa las alarmas exactas)" else "",
                )
            }
        }
    }

    val resolveContact: (VoiceIntent) -> Unit = { vi ->
        scope.launch {
            val found = withContext(Dispatchers.IO) { ContactSearch.find(context, vi.personName, state.clients.toList()) }
            when (found.size) {
                0 -> {
                    showMessage("No encontré a \"${vi.personName}\" en tus contactos. Elígelo con el botón de contactos.")
                    scheduleForContact(vi, null)
                }
                1 -> scheduleForContact(vi, found.first())
                else -> {
                    speaker.takeIf { VoicePrefs.speakBack(context) }?.say("Tienes ${found.size} contactos llamados ${vi.personName}. ¿Cuál es?")
                    contactChoice = vi to found
                }
            }
        }
    }

    val contactsPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val vi = waitingContactsPermission ?: return@rememberLauncherForActivityResult
        waitingContactsPermission = null
        if (granted) resolveContact(vi) else {
            showMessage("Sin permiso de contactos: elige la persona a mano.")
            scheduleForContact(vi, null)
        }
    }

    val handleVoiceIntent: (VoiceIntent) -> Unit = { vi ->
        when (vi.kind) {
            VoiceIntentKind.IDEA, VoiceIntentKind.NOTE -> saveIdea(vi)
            VoiceIntentKind.CALL, VoiceIntentKind.WHATSAPP -> when {
                vi.personName.isBlank() -> scheduleForContact(vi, null)
                ContactSearch.hasPermission(context) -> resolveContact(vi)
                else -> {
                    waitingContactsPermission = vi
                    contactsPermission.launch(Manifest.permission.READ_CONTACTS)
                }
            }
            VoiceIntentKind.MEETING, VoiceIntentKind.TRAVEL, VoiceIntentKind.REMINDER -> saveReminder(vi)
        }
    }

    // Procesa la cola de acciones una por una y, al final, confirma todo junto.
    LaunchedEffect(voiceQueue, voiceBusy) {
        if (voiceBusy) return@LaunchedEffect
        if (voiceQueue.isNotEmpty()) {
            val next = voiceQueue.first()
            voiceQueue = voiceQueue.drop(1)
            voiceBusy = true
            handleVoiceIntent(next)
        } else if (voiceDone.isNotEmpty()) {
            val summary = voiceDone.joinToString(". ")
            voiceDone.clear()
            showMessage("✅ $summary")
            if (VoicePrefs.speakBack(context)) speaker.say("Listo. $summary")
        }
    }

    val startVoiceReminder = rememberSpeechInput("Dime qué tienes que hacer: llamar, escribir, una reunión, un viaje o una idea") { text ->
        if (AiSettings.isEnabled(context)) {
            thinking = "Entendiendo lo que dijiste..."
            scope.launch {
                val result = withContext(Dispatchers.IO) { runCatching { AiAssistant.parseVoice(context, text) } }
                thinking = null
                result.onFailure { showMessage("IA no disponible (${it.message}). Lo organicé sin IA.") }
                voiceQueue = voiceQueue + result.getOrElse { listOf(LocalIntentParser.parse(text)) }
            }
        } else {
            voiceQueue = voiceQueue + LocalIntentParser.parse(text)
        }
    }

    // Atajo "Dictar" del ícono de la app: abre el micrófono apenas la app está desbloqueada
    LaunchedEffect(voiceRequested.value, locked.value) {
        if (voiceRequested.value && !locked.value) {
            voiceRequested.value = false
            startVoiceReminder()
        }
    }

    val openWhatsApp: (AgendaTask) -> Unit = { task ->
        if (!WhatsApp.open(context, task.phone, task.message.ifBlank { task.title })) showMessage("No se pudo abrir WhatsApp.")
    }

    val taskActions = TaskActions(
        onEdit = { taskEditor = TaskEditorRequest(task = it) },
        onDelete = { confirmDeleteTask = it },
        onToggle = { task, done -> state.setCompleted(task, done) },
        onWhatsApp = openWhatsApp,
    )

    val openSectionFromDrawer: (AppSection) -> Unit = { s ->
        section = s
        scope.launch { drawerState.close() }
    }

    BackHandler(enabled = drawerState.isOpen) { scope.launch { drawerState.close() } }
    BackHandler(enabled = !drawerState.isOpen && section != AppSection.AGENDA) { section = AppSection.AGENDA }

    AgendaWaTheme {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                AppDrawer(
                    name = state.profileName,
                    current = section,
                    onSection = openSectionFromDrawer,
                    onBackup = { showBackup = true; scope.launch { drawerState.close() } },
                    onAlarms = { showAlarmSettings = true; scope.launch { drawerState.close() } },
                    onProfile = { showProfile = true; scope.launch { drawerState.close() } },
                    onSettings = { showSettings = true; scope.launch { drawerState.close() } },
                )
            },
        ) {
            when (section) {
                AppSection.GASTOS -> GastosModule(
                    state = state,
                    onOpenDrawer = { scope.launch { drawerState.open() } },
                    onOpenBackup = { showBackup = true },
                    onOpenProfile = { showProfile = true },
                    onBackToAgenda = { section = AppSection.AGENDA },
                )
                AppSection.FINANZAS -> FinanceHubScreen(
                    state = state,
                    onBack = { section = AppSection.AGENDA },
                    onOpenDrawer = { scope.launch { drawerState.open() } },
                    showMessage = showMessage,
                    snackbar = snackbar,
                )
                AppSection.AGENDA -> Scaffold(
                    containerColor = MaterialTheme.colorScheme.background,
                    snackbarHost = { SnackbarHost(snackbar) },
                    topBar = {
                        CenterAlignedTopAppBar(
                            title = { Text(agendaTabs[tab].label.takeIf { tab != 0 } ?: "Agenda WA", fontWeight = FontWeight.Bold) },
                            navigationIcon = {
                                IconButton(onClick = { scope.launch { drawerState.open() } }) { Icon(Icons.Rounded.Menu, "Menú") }
                            },
                            actions = {
                                IconButton(onClick = startVoiceReminder) { Icon(Icons.Rounded.Mic, "Crear por voz") }
                                Box {
                                    IconButton(onClick = { showMenu = true }) { Icon(Icons.Rounded.MoreVert, "Más opciones") }
                                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                        DropdownMenuItem(text = { Text("Mis Gastos") }, leadingIcon = { Icon(Icons.Rounded.ReceiptLong, null) },
                                            onClick = { showMenu = false; section = AppSection.GASTOS })
                                        DropdownMenuItem(text = { Text("Clientes y Finanzas") }, leadingIcon = { Icon(Icons.Rounded.BusinessCenter, null) },
                                            onClick = { showMenu = false; section = AppSection.FINANZAS })
                                        DropdownMenuItem(text = { Text("Copia de seguridad") }, leadingIcon = { Icon(Icons.Rounded.Backup, null) },
                                            onClick = { showMenu = false; showBackup = true })
                                        DropdownMenuItem(text = { Text("Configurar alarmas") }, leadingIcon = { Icon(Icons.Rounded.Alarm, null) },
                                            onClick = { showMenu = false; showAlarmSettings = true })
                                        DropdownMenuItem(text = { Text("Perfil") }, leadingIcon = { Icon(Icons.Rounded.Person, null) },
                                            onClick = { showMenu = false; showProfile = true })
                                        DropdownMenuItem(text = { Text("Configuración (IA y huella)") }, leadingIcon = { Icon(Icons.Rounded.Settings, null) },
                                            onClick = { showMenu = false; showSettings = true })
                                    }
                                }
                            },
                            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                        )
                    },
                    bottomBar = {
                        NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                            agendaTabs.forEachIndexed { index, dest ->
                                NavigationBarItem(
                                    selected = tab == index,
                                    onClick = { tab = index },
                                    icon = { Icon(dest.icon, dest.label) },
                                    label = { Text(dest.label, maxLines = 1) },
                                )
                            }
                        }
                    },
                    floatingActionButton = {
                        when (tab) {
                            2 -> ExtendedFloatingActionButton(
                                onClick = { noteEditor = NoteEditorRequest() },
                                icon = { Icon(Icons.Rounded.Add, null) },
                                text = { Text("Nueva nota") },
                            )
                            4 -> ExtendedFloatingActionButton(
                                onClick = { taskEditor = TaskEditorRequest(whatsApp = true) },
                                icon = { Icon(Icons.Rounded.Add, null) },
                                text = { Text("Nuevo WhatsApp") },
                                containerColor = AgendaGreen,
                                contentColor = Color.White,
                            )
                            else -> ExtendedFloatingActionButton(
                                onClick = { taskEditor = TaskEditorRequest() },
                                icon = { Icon(Icons.Rounded.Add, null) },
                                text = { Text("Crear recordatorio") },
                            )
                        }
                    },
                ) { padding ->
                    Box(Modifier.padding(padding).fillMaxSize()) {
                        when (tab) {
                            0 -> TodayScreen(
                                state = state,
                                actions = taskActions,
                                onVoice = startVoiceReminder,
                                onOpenGastos = { section = AppSection.GASTOS },
                                onOpenFinance = { section = AppSection.FINANZAS },
                            )
                            1 -> UpcomingScreen(state = state, actions = taskActions)
                            2 -> NotesScreen(
                                state = state,
                                onEdit = { noteEditor = NoteEditorRequest(note = it) },
                                onDelete = { confirmDeleteNote = it },
                                onTogglePin = { state.toggleNotePin(it) },
                            )
                            3 -> CalendarScreen(
                                state = state,
                                actions = taskActions,
                                onNewForDate = { taskEditor = TaskEditorRequest(dateMillis = it) },
                            )
                            4 -> WhatsAppScreen(state = state, actions = taskActions)
                        }
                    }
                }
            }
        }

        // ------------------------------------------------------------- diálogos

        taskEditor?.let { req ->
            TaskEditorDialog(
                request = req,
                onDismiss = {
                    taskEditor = null
                    if (editorFromVoice) { editorFromVoice = false; finishVoice(null) }
                },
                onSave = { task ->
                    val result = state.saveTask(task)
                    taskEditor = null
                    if (editorFromVoice) { editorFromVoice = false; finishVoice(null) }
                    showMessage(
                        when (result) {
                            ScheduleResult.EXACT -> "Alarma guardada y activada"
                            ScheduleResult.APPROXIMATE -> "Recordatorio guardado. Activa los permisos de alarma."
                            ScheduleResult.SKIPPED -> if (task.completed) "Recordatorio guardado" else "Guardado. La fecha ya pasó, no sonará."
                        },
                    )
                },
            )
        }

        noteEditor?.let { req ->
            SmartNoteEditorDialog(
                request = req,
                onDismiss = { noteEditor = null },
                onSave = { note ->
                    state.saveNote(note)
                    noteEditor = null
                    showMessage("Nota guardada y organizada")
                },
            )
        }

        confirmDeleteTask?.let { task ->
            ConfirmDeleteDialog(
                title = "Eliminar recordatorio",
                name = task.title,
                onConfirm = { state.deleteTask(task); confirmDeleteTask = null; showMessage("Recordatorio eliminado") },
                onDismiss = { confirmDeleteTask = null },
            )
        }

        confirmDeleteNote?.let { note ->
            ConfirmDeleteDialog(
                title = "Eliminar nota",
                name = note.title.ifBlank { "esta nota" },
                onConfirm = { state.deleteNote(note); confirmDeleteNote = null; showMessage("Nota eliminada") },
                onDismiss = { confirmDeleteNote = null },
            )
        }

        if (showBackup) {
            BackupDialog(
                state = state,
                onDismiss = { showBackup = false },
                onMessage = showMessage,
            )
        }

        if (showProfile) {
            ProfileDialog(
                currentName = state.profileName,
                title = "Tu perfil",
                onDismiss = { showProfile = false },
                onSave = { state.updateProfileName(it); showProfile = false; showMessage("Nombre actualizado") },
            )
        }

        if (showWelcome) {
            ProfileDialog(
                currentName = "",
                title = "Bienvenido a Agenda WA",
                welcome = true,
                onDismiss = {
                    context.getSharedPreferences("agenda_wa_profile", Context.MODE_PRIVATE).edit().putBoolean("welcome_pending", false).apply()
                    showWelcome = false
                },
                onSave = {
                    state.updateProfileName(it)
                    context.getSharedPreferences("agenda_wa_profile", Context.MODE_PRIVATE).edit().putBoolean("welcome_pending", false).apply()
                    showWelcome = false
                },
            )
        }

        if (showSettings) {
            SettingsDialog(onDismiss = { showSettings = false }, showMessage = showMessage)
        }

        thinking?.let { ThinkingDialog(it) }

        contactChoice?.let { (vi, list) ->
            ContactChoiceDialog(
                name = vi.personName,
                contacts = list,
                onPick = { contactChoice = null; scheduleForContact(vi, it) },
                onSkip = { contactChoice = null; scheduleForContact(vi, null) },
            )
        }

        if (showAlarmSettings) {
            AlarmSettingsDialog(
                state = state,
                onDismiss = { showAlarmSettings = false },
                onRequestNotifications = {
                    if (Build.VERSION.SDK_INT >= 33) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else NotificationHelper.openAppNotificationSettings(context)
                },
                showMessage = showMessage,
            )
        }

        sharedContent.value?.let { shared ->
            AlertDialog(
                onDismissRequest = { sharedContent.value = null },
                title = { Text(shared.title) },
                text = {
                    Column {
                        Text("¿Qué quieres hacer con este contenido?", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        Text(shared.text.take(280), style = MaterialTheme.typography.bodySmall)
                    }
                },
                confirmButton = {
                    Column(horizontalAlignment = Alignment.End) {
                        TextButton(onClick = {
                            noteEditor = NoteEditorRequest(prefillTitle = shared.title, prefillText = shared.text)
                            sharedContent.value = null
                            section = AppSection.AGENDA; tab = 2
                        }) { Text("Guardar como nota") }
                        TextButton(onClick = {
                            val draft = VoiceParser.parse(shared.text.lineSequence().firstOrNull().orEmpty())
                            taskEditor = TaskEditorRequest(
                                draft = ReminderDraft(
                                    title = if (shared.isWhatsApp) "Enviar WhatsApp" else shared.title,
                                    message = shared.text,
                                    dateTimeMillis = draft.dateTimeMillis,
                                    isWhatsApp = shared.isWhatsApp,
                                ),
                            )
                            sharedContent.value = null
                            section = AppSection.AGENDA
                        }) { Text("Crear recordatorio") }
                        TextButton(onClick = { sharedContent.value = null }) { Text("Cancelar") }
                    }
                },
            )
        }
    }
}

data class TaskActions(
    val onEdit: (AgendaTask) -> Unit,
    val onDelete: (AgendaTask) -> Unit,
    val onToggle: (AgendaTask, Boolean) -> Unit,
    val onWhatsApp: (AgendaTask) -> Unit,
)

@Composable
private fun AppDrawer(
    name: String,
    current: AppSection,
    onSection: (AppSection) -> Unit,
    onBackup: () -> Unit,
    onAlarms: () -> Unit,
    onProfile: () -> Unit,
    onSettings: () -> Unit,
) {
    ModalDrawerSheet {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(48.dp).clip(CircleShape).background(AgendaBlue),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.Event, null, tint = Color.White) }
                Spacer(Modifier.size(12.dp))
                Column {
                    Text("Agenda WA", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(if (name.isBlank()) "Tu agenda inteligente" else "Hola, $name", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))
            NavigationDrawerItem(
                label = { Text("Agenda y recordatorios") }, icon = { Icon(Icons.Rounded.Event, null) },
                selected = current == AppSection.AGENDA, onClick = { onSection(AppSection.AGENDA) },
                modifier = Modifier.padding(horizontal = 12.dp),
            )
            NavigationDrawerItem(
                label = { Text("Mis Gastos") }, icon = { Icon(Icons.Rounded.ReceiptLong, null) },
                selected = current == AppSection.GASTOS, onClick = { onSection(AppSection.GASTOS) },
                modifier = Modifier.padding(horizontal = 12.dp),
            )
            NavigationDrawerItem(
                label = { Text("Clientes y Finanzas") }, icon = { Icon(Icons.Rounded.BusinessCenter, null) },
                selected = current == AppSection.FINANZAS, onClick = { onSection(AppSection.FINANZAS) },
                modifier = Modifier.padding(horizontal = 12.dp),
            )
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(Modifier.padding(horizontal = 24.dp))
            Spacer(Modifier.height(8.dp))
            NavigationDrawerItem(
                label = { Text("Copia de seguridad") }, icon = { Icon(Icons.Rounded.Backup, null) },
                selected = false, onClick = onBackup, modifier = Modifier.padding(horizontal = 12.dp),
            )
            NavigationDrawerItem(
                label = { Text("Configurar alarmas") }, icon = { Icon(Icons.Rounded.Alarm, null) },
                selected = false, onClick = onAlarms, modifier = Modifier.padding(horizontal = 12.dp),
            )
            NavigationDrawerItem(
                label = { Text("Perfil") }, icon = { Icon(Icons.Rounded.Person, null) },
                selected = false, onClick = onProfile, modifier = Modifier.padding(horizontal = 12.dp),
            )
            NavigationDrawerItem(
                label = { Text("Configuración (IA y huella)") }, icon = { Icon(Icons.Rounded.Settings, null) },
                selected = false, onClick = onSettings, modifier = Modifier.padding(horizontal = 12.dp),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "Versión ${BackupManager.APP_VERSION}",
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun ConfirmDeleteDialog(title: String, name: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text("¿Quieres eliminar \"$name\"? Esta acción no se puede deshacer.") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Eliminar", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
fun ProfileDialog(
    currentName: String,
    title: String,
    welcome: Boolean = false,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var name by remember { mutableStateOf(currentName) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                if (welcome) {
                    Text("Tu agenda inteligente para recordatorios, ideas, notas, WhatsApp y gastos.")
                    Spacer(Modifier.height(12.dp))
                }
                Text("Este nombre se usará para personalizar tus recordatorios y alertas.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(40); error = null },
                    label = { Text("Escribe tu nombre o apodo.") },
                    singleLine = true,
                    isError = error != null,
                    supportingText = { Text(error ?: if (welcome) "Podrás cambiar este nombre más adelante." else "") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                if (name.isBlank()) error = "Escribe un nombre válido." else onSave(name.trim())
            }) { Text(if (welcome) "Continuar" else "Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(if (welcome) "Más tarde" else "Cancelar") } },
    )
}

@Composable
private fun AlarmSettingsDialog(
    state: AppState,
    onDismiss: () -> Unit,
    onRequestNotifications: () -> Unit,
    showMessage: (String) -> Unit,
) {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    val notifications = remember(refresh) { NotificationHelper.notificationsAllowed(context) }
    val exact = remember(refresh) { ReminderScheduler.canScheduleExact(context) }
    val fullScreen = remember(refresh) { AlarmCapabilities.canUseFullScreen(context) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Configurar alarmas") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "Agenda WA necesita estos permisos para sonar como una alarma aunque el teléfono esté bloqueado.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                PermissionRow("Notificaciones", notifications) { onRequestNotifications(); refresh++ }
                PermissionRow("Alarmas exactas", exact) { AlarmCapabilities.openExactAlarmSettings(context); refresh++ }
                PermissionRow("Pantalla de alarma", fullScreen) { AlarmCapabilities.openFullScreenSettings(context); refresh++ }
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = { NotificationHelper.openSoundSettings(context) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Cambiar sonido de alarma del teléfono")
                }
                Spacer(Modifier.height(4.dp))
                Button(
                    onClick = {
                        val (_, result) = state.createTask(
                            title = "Prueba de alarma de Agenda WA",
                            message = "Esta es una prueba del sonido, la vibración y la pantalla de alarma.",
                            phone = "",
                            dateTimeMillis = System.currentTimeMillis() + 15_000,
                            isWhatsApp = false,
                        )
                        showMessage(if (result == ScheduleResult.EXACT) "La alarma de prueba sonará en 15 segundos" else "Prueba programada. Activa las alarmas exactas para mayor precisión.")
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Probar alarma en 15 segundos") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
        dismissButton = { TextButton(onClick = { refresh++ }) { Text("Actualizar") } },
    )
}

@Composable
private fun PermissionRow(label: String, enabled: Boolean, onConfigure: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (enabled) Icons.Rounded.CheckCircle else Icons.Rounded.Error,
            null,
            tint = if (enabled) AgendaGreen else AgendaDanger,
        )
        Spacer(Modifier.size(10.dp))
        Column(Modifier.weight(1f)) {
            Text(label, fontWeight = FontWeight.SemiBold)
            Text(if (enabled) "Activada" else "Pendiente", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (!enabled) TextButton(onClick = onConfigure) { Text("Configurar ahora") }
    }
}

