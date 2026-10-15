package com.agendawa.app

// ---------------------------------------------------------------------------
// Agenda / recordatorios
// ---------------------------------------------------------------------------

data class AgendaTask(
    val id: Long = System.currentTimeMillis(),
    val title: String,
    val message: String = "",
    val phone: String = "",
    val dateTimeMillis: Long,
    val isWhatsApp: Boolean = false,
    val completed: Boolean = false,
    /** Si se repite (lunes, martes y miércoles…). Null = suena una sola vez. */
    val repeat: RepeatRule? = null,
)

data class ReminderDraft(
    val title: String,
    val message: String,
    val dateTimeMillis: Long,
    val isWhatsApp: Boolean,
    /** Teléfono ya elegido (por ejemplo, al dictar "llamar a Carlos"). */
    val phone: String = "",
    val contactName: String? = null,
    val repeat: RepeatRule? = null,
)

enum class ScheduleResult { EXACT, APPROXIMATE, SKIPPED }

data class SharedContent(
    val title: String,
    val text: String,
    val isWhatsApp: Boolean,
)

data class SelectedPhoneContact(
    val displayName: String,
    val phoneNumber: String,
)

// ---------------------------------------------------------------------------
// Clientes y finanzas (cuentas por cobrar / por pagar)
// ---------------------------------------------------------------------------

data class Client(
    val id: Long = System.currentTimeMillis(),
    val name: String,
    val phone: String = "",
    val email: String = "",
    val company: String = "",
    val notes: String = "",
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis(),
)

enum class FinanceType { RECEIVABLE, PAYABLE }

data class FinanceEntry(
    val id: Long = System.currentTimeMillis(),
    val type: FinanceType,
    val clientId: Long? = null,
    val counterparty: String,
    val concept: String,
    val totalAmount: Double,
    val paidAmount: Double = 0.0,
    val currency: String = "USD",
    val dueDateMillis: Long,
    val notes: String = "",
    val reminderTaskId: Long? = null,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis(),
) {
    val balance: Double get() = (totalAmount - paidAmount).coerceAtLeast(0.0)
    val isPaid: Boolean get() = balance <= 0.004
    fun isOverdue(now: Long = System.currentTimeMillis()): Boolean = !isPaid && dueDateMillis < now
}

data class FinanceImportResult(
    val clients: List<Client>,
    val entries: List<FinanceEntry>,
    val fileName: String,
    val sourceRows: Int,
    val skippedRows: Int,
)

// ---------------------------------------------------------------------------
// Notas inteligentes
// ---------------------------------------------------------------------------

enum class SmartNoteType(
    val storageKey: String,
    val label: String,
    val description: String,
    val sortOrder: Int,
) {
    PERSON_TOPICS("person_topics", "Puntos con alguien", "Temas para hablar, preguntar o revisar con una persona", 0),
    IDEA("idea", "Idea", "Conceptos, ocurrencias y oportunidades", 1),
    BUSINESS("business", "Negocio", "Clientes, ventas, presupuestos y oportunidades", 2),
    PROJECT("project", "Proyecto", "Planes, decisiones y próximos pasos", 3),
    REFERENCE("reference", "Referencia", "Enlaces, cuentas, páginas y recursos", 4),
    GENERAL("general", "Nota", "Información general para recordar", 5);

    companion object {
        fun fromStorage(key: String?): SmartNoteType =
            entries.firstOrNull { it.storageKey == key || it.name == key } ?: GENERAL
    }
}

data class SmartNote(
    val id: Long = System.currentTimeMillis(),
    val title: String,
    val content: String,
    val type: SmartNoteType = SmartNoteType.GENERAL,
    val personName: String = "",
    val personPhone: String = "",
    val project: String = "",
    val tags: String = "",
    val pinned: Boolean = false,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis(),
)

data class NoteOrganization(
    val title: String,
    val type: SmartNoteType,
    val tags: String,
)

// ---------------------------------------------------------------------------
// Mis Gastos (nuevo módulo)
// ---------------------------------------------------------------------------

enum class ExpenseKind(val label: String) { EXPENSE("Gasto"), INCOME("Ingreso") }

data class ExpenseCategory(
    val id: Long,
    val name: String,
    val kind: ExpenseKind,
    val iconKey: String,
    val colorArgb: Long,
    val sortOrder: Int = 0,
)

data class Expense(
    val id: Long = System.currentTimeMillis(),
    val kind: ExpenseKind,
    val amount: Double,
    val categoryId: Long,
    val note: String = "",
    val dateMillis: Long,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis(),
)

data class ExpenseSettings(
    val currencySymbol: String = "$",
    val monthlyBudget: Double = 0.0,
    val dailyReminderEnabled: Boolean = false,
    val reminderHour: Int = 21,
    val reminderMinute: Int = 0,
    val openingBalance: Double = 0.0,
)

data class RestoreSummary(
    val imported: Int,
    val importedTasks: Int,
    val importedNotes: Int,
    val importedClients: Int,
    val importedFinanceEntries: Int,
    val importedExpenses: Int,
    val totalAfterRestore: Int,
)
