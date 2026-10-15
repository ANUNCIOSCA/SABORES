package com.agendawa.app

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import java.util.Calendar

/**
 * Estado central de la app. Cada operación actualiza la lista en memoria (para que la UI
 * se recomponga) y guarda en disco inmediatamente.
 */
class AppState(private val context: Context) {
    val tasks = mutableStateListOf<AgendaTask>()
    val notes = mutableStateListOf<SmartNote>()
    val clients = mutableStateListOf<Client>()
    val finance = mutableStateListOf<FinanceEntry>()
    val expenses = mutableStateListOf<Expense>()
    val categories = mutableStateListOf<ExpenseCategory>()
    var expenseSettings by mutableStateOf(ExpenseSettings())
    var profileName by mutableStateOf("")

    init { reloadAll() }

    fun reloadAll() {
        // Avisos que se repiten y quedaron atrás saltan a su próxima fecha
        val loaded = TaskStore.load(context)
        val caught = loaded.map { Repeat.catchUp(it) }
        if (caught != loaded) {
            TaskStore.save(context, caught)
            caught.filter { it !in loaded }.forEach { ReminderScheduler.schedule(context, it) }
        }
        tasks.clear(); tasks.addAll(caught.sortedBy { it.dateTimeMillis })
        notes.clear(); notes.addAll(SmartNoteStore.load(context))
        clients.clear(); clients.addAll(ClientStore.load(context))
        finance.clear(); finance.addAll(FinanceStore.load(context))
        expenses.clear(); expenses.addAll(ExpenseStore.load(context))
        categories.clear(); categories.addAll(CategoryStore.load(context))
        expenseSettings = ExpenseSettingsStore.load(context)
        profileName = ProfileStore.name(context)
    }

    private fun newId(): Long {
        val base = System.currentTimeMillis()
        val used = (tasks.map { it.id } + notes.map { it.id } + clients.map { it.id } + finance.map { it.id } + expenses.map { it.id }).toHashSet()
        var id = base
        while (id in used) id++
        return id
    }

    // ------------------------------------------------------------------ tareas

    private fun persistTasks() {
        val sorted = tasks.sortedBy { it.dateTimeMillis }
        tasks.clear(); tasks.addAll(sorted)
        TaskStore.save(context, sorted)
    }

    fun saveTask(task: AgendaTask): ScheduleResult {
        val toSave = if (tasks.none { it.id == task.id } && task.id <= 0L) task.copy(id = newId()) else task
        val idx = tasks.indexOfFirst { it.id == toSave.id }
        if (idx >= 0) tasks[idx] = toSave else tasks.add(toSave)
        persistTasks()
        return ReminderScheduler.schedule(context, toSave)
    }

    fun createTask(
        title: String, message: String, phone: String, dateTimeMillis: Long, isWhatsApp: Boolean, repeat: RepeatRule? = null,
        /** No nulo = WhatsApp para grupo(s); lista vacía = se elige el grupo al enviar. */
        groups: List<WaGroup>? = null,
    ): Pair<AgendaTask, ScheduleResult> {
        val at = repeat?.let { Repeat.next(it, System.currentTimeMillis()) } ?: dateTimeMillis
        val t = AgendaTask(
            id = newId(), title = title, message = message, phone = phone, dateTimeMillis = at, isWhatsApp = isWhatsApp, repeat = repeat,
            toGroup = groups != null, groups = groups.orEmpty(),
        )
        return t to saveTask(t)
    }

    fun deleteTask(task: AgendaTask) {
        ReminderScheduler.cancel(context, task.id)
        tasks.removeAll { it.id == task.id }
        persistTasks()
        // desvincula movimientos que usaban este recordatorio
        if (finance.any { it.reminderTaskId == task.id }) {
            val updated = finance.map { if (it.reminderTaskId == task.id) it.copy(reminderTaskId = null) else it }
            finance.clear(); finance.addAll(updated)
            FinanceStore.save(context, updated)
        }
    }

    /** Marcar como hecho. Si se repite, pasa a la próxima fecha y devuelve el aviso actualizado. */
    fun setCompleted(task: AgendaTask, completed: Boolean): AgendaTask {
        val updated = if (completed) Repeat.advance(task) else task.copy(completed = false)
        val idx = tasks.indexOfFirst { it.id == task.id }
        if (idx >= 0) tasks[idx] = updated
        persistTasks()
        if (updated.completed) ReminderScheduler.cancel(context, task.id) else ReminderScheduler.schedule(context, updated)
        return updated
    }

    // ------------------------------------------------------------------ notas

    private fun persistNotes() {
        val sorted = notes.sortedWith(compareByDescending<SmartNote> { it.pinned }.thenByDescending { it.updatedAtMillis })
        notes.clear(); notes.addAll(sorted)
        SmartNoteStore.save(context, sorted)
    }

    fun saveNote(note: SmartNote) {
        val n = if (note.id <= 0L) note.copy(id = newId()) else note
        val idx = notes.indexOfFirst { it.id == n.id }
        if (idx >= 0) notes[idx] = n else notes.add(n)
        persistNotes()
    }

    fun deleteNote(note: SmartNote) {
        notes.removeAll { it.id == note.id }
        persistNotes()
    }

    fun toggleNotePin(note: SmartNote) {
        val idx = notes.indexOfFirst { it.id == note.id }
        if (idx >= 0) notes[idx] = note.copy(pinned = !note.pinned, updatedAtMillis = System.currentTimeMillis())
        persistNotes()
    }

    // ------------------------------------------------------------------ clientes

    private fun persistClients() {
        val sorted = clients.sortedBy { it.name.lowercase() }
        clients.clear(); clients.addAll(sorted)
        ClientStore.save(context, sorted)
    }

    fun saveClient(client: Client): Client {
        val c = if (client.id <= 0L) client.copy(id = newId()) else client
        val idx = clients.indexOfFirst { it.id == c.id }
        if (idx >= 0) clients[idx] = c else clients.add(c)
        persistClients()
        // actualiza el nombre en movimientos relacionados
        if (finance.any { it.clientId == c.id && it.counterparty != c.name }) {
            val updated = finance.map { if (it.clientId == c.id) it.copy(counterparty = c.name) else it }
            finance.clear(); finance.addAll(updated)
            FinanceStore.save(context, updated)
        }
        return c
    }

    fun deleteClient(client: Client) {
        clients.removeAll { it.id == client.id }
        persistClients()
        val updated = finance.map { if (it.clientId == client.id) it.copy(clientId = null) else it }
        finance.clear(); finance.addAll(updated)
        FinanceStore.save(context, updated)
    }

    // ------------------------------------------------------------------ finanzas

    private fun persistFinance() {
        val sorted = finance.sortedBy { it.dueDateMillis }
        finance.clear(); finance.addAll(sorted)
        FinanceStore.save(context, sorted)
    }

    private fun reminderTitle(e: FinanceEntry): String =
        "${if (e.type == FinanceType.RECEIVABLE) "Cobrar a" else "Pagar a"} ${e.counterparty}: ${e.concept}"

    private fun reminderMessage(e: FinanceEntry): String =
        "${e.concept} · Saldo pendiente: ${money(e.balance)} ${e.currency}"

    /** Guarda el movimiento y, si se pide, crea o actualiza su recordatorio. */
    fun saveFinanceEntry(entry: FinanceEntry, withReminder: Boolean): FinanceEntry {
        var e = if (entry.id <= 0L) entry.copy(id = newId()) else entry
        e = e.copy(updatedAtMillis = System.currentTimeMillis())
        if (withReminder && !e.isPaid) {
            val client = clients.firstOrNull { it.id == e.clientId }
            val existing = e.reminderTaskId?.let { id -> tasks.firstOrNull { it.id == id } }
            var at = e.dueDateMillis
            if (at <= System.currentTimeMillis()) {
                at = Calendar.getInstance().apply {
                    add(Calendar.DAY_OF_YEAR, 1); set(Calendar.HOUR_OF_DAY, 9); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
                }.timeInMillis
            }
            val phone = client?.phone.orEmpty()
            val task = (existing ?: AgendaTask(id = newId(), title = "", dateTimeMillis = at)).copy(
                title = reminderTitle(e),
                message = reminderMessage(e),
                phone = phone,
                isWhatsApp = phone.isNotBlank() && e.type == FinanceType.RECEIVABLE,
                dateTimeMillis = at,
                completed = false,
            )
            saveTask(task)
            e = e.copy(reminderTaskId = task.id)
        } else if (e.isPaid) {
            e.reminderTaskId?.let { rid -> tasks.firstOrNull { it.id == rid }?.let { setCompleted(it, true) } }
        }
        val idx = finance.indexOfFirst { it.id == e.id }
        if (idx >= 0) finance[idx] = e else finance.add(e)
        persistFinance()
        return e
    }

    fun markFinancePaid(entry: FinanceEntry): FinanceEntry =
        saveFinanceEntry(entry.copy(paidAmount = entry.totalAmount), withReminder = false)

    fun deleteFinanceEntry(entry: FinanceEntry) {
        entry.reminderTaskId?.let { rid -> tasks.firstOrNull { it.id == rid }?.let { deleteTask(it) } }
        finance.removeAll { it.id == entry.id }
        persistFinance()
    }

    fun importFinance(result: FinanceImportResult) {
        result.clients.forEach { c -> if (clients.none { it.id == c.id }) clients.add(c) }
        persistClients()
        result.entries.forEach { e -> val i = finance.indexOfFirst { it.id == e.id }; if (i >= 0) finance[i] = e else finance.add(e) }
        persistFinance()
    }

    // ------------------------------------------------------------------ Mis Gastos

    private fun persistExpenses() {
        val sorted = expenses.sortedByDescending { it.dateMillis }
        expenses.clear(); expenses.addAll(sorted)
        ExpenseStore.save(context, sorted)
    }

    fun saveExpense(expense: Expense): Expense {
        val x = (if (expense.id <= 0L) expense.copy(id = newId()) else expense).copy(updatedAtMillis = System.currentTimeMillis())
        val idx = expenses.indexOfFirst { it.id == x.id }
        if (idx >= 0) expenses[idx] = x else expenses.add(x)
        persistExpenses()
        return x
    }

    fun deleteExpense(expense: Expense) {
        expenses.removeAll { it.id == expense.id }
        persistExpenses()
    }

    /** Registra en Mis Gastos un movimiento de Finanzas que se marcó como pagado. */
    fun registerFinanceInExpenses(entry: FinanceEntry): Expense {
        val kind = if (entry.type == FinanceType.RECEIVABLE) ExpenseKind.INCOME else ExpenseKind.EXPENSE
        val catId = if (kind == ExpenseKind.INCOME) ExpenseCategories.COLLECTIONS_CATEGORY_ID else ExpenseCategories.PAYMENTS_CATEGORY_ID
        val category = categories.firstOrNull { it.id == catId } ?: categories.first { it.kind == kind }
        return saveExpense(
            Expense(
                id = 0L,
                kind = kind,
                amount = entry.totalAmount,
                categoryId = category.id,
                note = "${entry.counterparty} · ${entry.concept}",
                dateMillis = System.currentTimeMillis(),
            ),
        )
    }

    fun categoryById(id: Long): ExpenseCategory? = categories.firstOrNull { it.id == id }

    fun saveCategory(category: ExpenseCategory): ExpenseCategory {
        val c = if (category.id <= 0L) category.copy(id = newId(), sortOrder = categories.count { it.kind == category.kind } + 100) else category
        val idx = categories.indexOfFirst { it.id == c.id }
        if (idx >= 0) categories[idx] = c else categories.add(c)
        CategoryStore.save(context, categories.toList())
        return c
    }

    /** Elimina la categoría y mueve sus registros a "Otros gastos" / "Otros ingresos". */
    fun deleteCategory(category: ExpenseCategory) {
        val fallback = categories.firstOrNull { it.kind == category.kind && it.id != category.id && it.iconKey == "more" }
            ?: categories.firstOrNull { it.kind == category.kind && it.id != category.id }
            ?: return
        val moved = expenses.map { if (it.categoryId == category.id) it.copy(categoryId = fallback.id) else it }
        expenses.clear(); expenses.addAll(moved)
        persistExpenses()
        categories.removeAll { it.id == category.id }
        CategoryStore.save(context, categories.toList())
    }

    fun moveCategory(category: ExpenseCategory, up: Boolean) {
        val same = categories.filter { it.kind == category.kind }.sortedBy { it.sortOrder }.toMutableList()
        val i = same.indexOfFirst { it.id == category.id }
        val j = if (up) i - 1 else i + 1
        if (i < 0 || j !in same.indices) return
        val tmp = same[i]; same[i] = same[j]; same[j] = tmp
        val reordered = same.mapIndexed { index, c -> c.copy(sortOrder = index) }
        val others = categories.filter { it.kind != category.kind }
        categories.clear(); categories.addAll((others + reordered).sortedWith(compareBy({ it.kind }, { it.sortOrder })))
        CategoryStore.save(context, categories.toList())
    }

    fun updateExpenseSettings(settings: ExpenseSettings) {
        expenseSettings = settings
        ExpenseSettingsStore.save(context, settings)
        ExpenseReminder.apply(context)
    }

    // ------------------------------------------------------------------ perfil

    fun updateProfileName(name: String) {
        ProfileStore.saveName(context, name)
        profileName = name.trim()
    }
}
