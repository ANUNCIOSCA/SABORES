package com.agendawa.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/*
 * Almacenamiento local en SharedPreferences con JSON.
 * Se conservan los mismos nombres de archivo y claves de la versión 1.6.0
 * (agenda_wa_store/tasks, agenda_wa_notes/notes, agenda_wa_clients/clients,
 * agenda_wa_finance/entries, agenda_wa_profile/display_name) para mantener la
 * compatibilidad con las copias de seguridad existentes.
 */

private fun JSONObject.optLongOrNull(key: String): Long? =
    if (has(key) && !isNull(key)) optLong(key) else null

private inline fun <T> readList(context: Context, prefs: String, key: String, parse: (JSONObject) -> T): List<T> =
    runCatching {
        val raw = context.getSharedPreferences(prefs, Context.MODE_PRIVATE).getString(key, "[]") ?: "[]"
        val array = JSONArray(raw)
        buildList {
            for (i in 0 until array.length()) {
                runCatching { parse(array.getJSONObject(i)) }.getOrNull()?.let { add(it) }
            }
        }
    }.getOrDefault(emptyList())

private fun writeList(context: Context, prefs: String, key: String, items: List<JSONObject>) {
    val array = JSONArray()
    items.forEach { array.put(it) }
    context.getSharedPreferences(prefs, Context.MODE_PRIVATE).edit().putString(key, array.toString()).apply()
}

// ---------------------------------------------------------------------------

object TaskStore {
    private const val PREFS = "agenda_wa_store"
    private const val KEY_TASKS = "tasks"

    fun toJson(t: AgendaTask) = JSONObject().apply {
        put("id", t.id)
        put("title", t.title)
        put("message", t.message)
        put("phone", t.phone)
        put("dateTimeMillis", t.dateTimeMillis)
        put("isWhatsApp", t.isWhatsApp)
        put("completed", t.completed)
        t.repeat?.let { put("repeat", Repeat.toJson(it)) }
        if (t.toGroup) {
            put("toGroup", true)
            put("groups", JSONArray().apply { t.groups.forEach { put(GroupStore.toJson(it)) } })
        }
    }

    fun fromJson(o: JSONObject) = AgendaTask(
        id = o.getLong("id"),
        title = o.optString("title", "Recordatorio"),
        message = o.optString("message"),
        phone = o.optString("phone"),
        dateTimeMillis = o.getLong("dateTimeMillis"),
        isWhatsApp = o.optBoolean("isWhatsApp"),
        completed = o.optBoolean("completed"),
        repeat = Repeat.fromJson(o.optJSONObject("repeat")),
        toGroup = o.optBoolean("toGroup"),
        groups = o.optJSONArray("groups")?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it)?.let(GroupStore::fromJson) } }.orEmpty(),
    )

    fun load(context: Context): List<AgendaTask> =
        readList(context, PREFS, KEY_TASKS, ::fromJson).sortedBy { it.dateTimeMillis }

    fun save(context: Context, tasks: List<AgendaTask>) =
        writeList(context, PREFS, KEY_TASKS, tasks.map(::toJson))

    fun find(context: Context, id: Long): AgendaTask? = load(context).firstOrNull { it.id == id }

    fun upsert(context: Context, task: AgendaTask) {
        val list = load(context).filterNot { it.id == task.id } + task
        save(context, list)
    }
}

// ---------------------------------------------------------------------------

/** Grupos de WhatsApp favoritos (nombre + enlace de invitación opcional). */
object GroupStore {
    private const val PREFS = "agenda_wa_groups"
    private const val KEY = "groups"

    fun toJson(g: WaGroup) = JSONObject().put("name", g.name).put("link", g.link)
    fun fromJson(o: JSONObject) = WaGroup(o.optString("name").trim(), o.optString("link").trim()).takeIf { it.name.isNotBlank() }

    fun load(context: Context): List<WaGroup> = readList(context, PREFS, KEY) { fromJson(it) }.filterNotNull()

    fun save(context: Context, groups: List<WaGroup>) = writeList(context, PREFS, KEY, groups.map(::toJson))

    /** Agrega o actualiza (por nombre, sin importar mayúsculas). */
    fun upsert(context: Context, group: WaGroup) {
        val list = load(context).filterNot { it.name.equals(group.name, ignoreCase = true) } + group
        save(context, list.sortedBy { it.name.lowercase() })
    }

    fun remove(context: Context, group: WaGroup) = save(context, load(context).filterNot { it.name.equals(group.name, ignoreCase = true) })

    /** "el grupo de ventas" → el favorito que más se parezca. */
    fun match(context: Context, spoken: String): WaGroup? {
        val q = SmartNoteOrganizer.normalize(spoken).replace(Regex("^(el |la |los |las )?(grupo )?(de |del )?"), "").trim()
        if (q.isBlank()) return null
        val all = load(context)
        return all.firstOrNull { SmartNoteOrganizer.normalize(it.name) == q }
            ?: all.firstOrNull { SmartNoteOrganizer.normalize(it.name).contains(q) || q.contains(SmartNoteOrganizer.normalize(it.name)) }
    }
}

// ---------------------------------------------------------------------------

object SmartNoteStore {
    private const val PREFS = "agenda_wa_notes"
    private const val KEY = "notes"

    fun toJson(n: SmartNote) = JSONObject().apply {
        put("id", n.id)
        put("title", n.title)
        put("content", n.content)
        put("type", n.type.storageKey)
        put("personName", n.personName)
        put("personPhone", n.personPhone)
        put("project", n.project)
        put("tags", n.tags)
        put("pinned", n.pinned)
        put("createdAtMillis", n.createdAtMillis)
        put("updatedAtMillis", n.updatedAtMillis)
    }

    fun fromJson(o: JSONObject) = SmartNote(
        id = o.getLong("id"),
        title = o.optString("title"),
        content = o.optString("content"),
        type = SmartNoteType.fromStorage(o.optString("type")),
        personName = o.optString("personName"),
        personPhone = o.optString("personPhone"),
        project = o.optString("project"),
        tags = o.optString("tags"),
        pinned = o.optBoolean("pinned"),
        createdAtMillis = o.optLong("createdAtMillis", System.currentTimeMillis()),
        updatedAtMillis = o.optLong("updatedAtMillis", System.currentTimeMillis()),
    )

    fun load(context: Context): List<SmartNote> =
        readList(context, PREFS, KEY, ::fromJson)
            .sortedWith(compareByDescending<SmartNote> { it.pinned }.thenByDescending { it.updatedAtMillis })

    fun save(context: Context, notes: List<SmartNote>) =
        writeList(context, PREFS, KEY, notes.map(::toJson))
}

// ---------------------------------------------------------------------------

object ClientStore {
    private const val PREFS = "agenda_wa_clients"
    private const val KEY = "clients"

    fun toJson(c: Client) = JSONObject().apply {
        put("id", c.id)
        put("name", c.name)
        put("phone", c.phone)
        put("email", c.email)
        put("company", c.company)
        put("notes", c.notes)
        put("createdAtMillis", c.createdAtMillis)
        put("updatedAtMillis", c.updatedAtMillis)
    }

    fun fromJson(o: JSONObject) = Client(
        id = o.getLong("id"),
        name = o.optString("name", "Sin nombre"),
        phone = o.optString("phone"),
        email = o.optString("email"),
        company = o.optString("company"),
        notes = o.optString("notes"),
        createdAtMillis = o.optLong("createdAtMillis", System.currentTimeMillis()),
        updatedAtMillis = o.optLong("updatedAtMillis", System.currentTimeMillis()),
    )

    fun load(context: Context): List<Client> =
        readList(context, PREFS, KEY, ::fromJson).sortedBy { it.name.lowercase() }

    fun save(context: Context, clients: List<Client>) =
        writeList(context, PREFS, KEY, clients.map(::toJson))
}

// ---------------------------------------------------------------------------

object FinanceStore {
    private const val PREFS = "agenda_wa_finance"
    private const val KEY = "entries"

    fun toJson(e: FinanceEntry) = JSONObject().apply {
        put("id", e.id)
        put("type", e.type.name)
        put("clientId", e.clientId ?: JSONObject.NULL)
        put("counterparty", e.counterparty)
        put("concept", e.concept)
        put("totalAmount", e.totalAmount)
        put("paidAmount", e.paidAmount)
        put("currency", e.currency)
        put("dueDateMillis", e.dueDateMillis)
        put("notes", e.notes)
        put("reminderTaskId", e.reminderTaskId ?: JSONObject.NULL)
        put("createdAtMillis", e.createdAtMillis)
        put("updatedAtMillis", e.updatedAtMillis)
    }

    fun fromJson(o: JSONObject) = FinanceEntry(
        id = o.getLong("id"),
        type = runCatching { FinanceType.valueOf(o.optString("type")) }.getOrDefault(FinanceType.RECEIVABLE),
        clientId = o.optLongOrNull("clientId"),
        counterparty = o.optString("counterparty"),
        concept = o.optString("concept"),
        totalAmount = o.optDouble("totalAmount", 0.0),
        paidAmount = o.optDouble("paidAmount", 0.0),
        currency = o.optString("currency", "USD").ifBlank { "USD" },
        dueDateMillis = o.optLong("dueDateMillis", System.currentTimeMillis()),
        notes = o.optString("notes"),
        reminderTaskId = o.optLongOrNull("reminderTaskId"),
        createdAtMillis = o.optLong("createdAtMillis", System.currentTimeMillis()),
        updatedAtMillis = o.optLong("updatedAtMillis", System.currentTimeMillis()),
    )

    fun load(context: Context): List<FinanceEntry> =
        readList(context, PREFS, KEY, ::fromJson).sortedBy { it.dueDateMillis }

    fun save(context: Context, entries: List<FinanceEntry>) =
        writeList(context, PREFS, KEY, entries.map(::toJson))
}

// ---------------------------------------------------------------------------

object ProfileStore {
    private const val PREFS = "agenda_wa_profile"
    private const val KEY_NAME = "display_name"

    fun name(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_NAME, "")?.trim().orEmpty()

    fun saveName(context: Context, name: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_NAME, name.trim()).apply()
    }

    /** "Moreno, tienes un recordatorio" o "Tienes un recordatorio" si no hay nombre. */
    fun personalized(context: Context, message: String): String {
        val n = name(context)
        return if (n.isBlank()) message.replaceFirstChar { it.uppercase() } else "$n, $message"
    }
}

// ---------------------------------------------------------------------------
// Mis Gastos
// ---------------------------------------------------------------------------

object ExpenseCategories {
    /** Categorías iniciales. Los ids < 1000 son predeterminados. */
    val defaults: List<ExpenseCategory> = listOf(
        ExpenseCategory(1, "Comida", ExpenseKind.EXPENSE, "food", 0xFFF59E0B, 0),
        ExpenseCategory(2, "Mercado", ExpenseKind.EXPENSE, "cart", 0xFF10B981, 1),
        ExpenseCategory(3, "Transporte", ExpenseKind.EXPENSE, "car", 0xFF3B82F6, 2),
        ExpenseCategory(4, "Casa", ExpenseKind.EXPENSE, "home", 0xFF8B5CF6, 3),
        ExpenseCategory(5, "Servicios", ExpenseKind.EXPENSE, "bolt", 0xFFEAB308, 4),
        ExpenseCategory(6, "Teléfono e internet", ExpenseKind.EXPENSE, "phone", 0xFF06B6D4, 5),
        ExpenseCategory(7, "Salud", ExpenseKind.EXPENSE, "health", 0xFFEF4444, 6),
        ExpenseCategory(8, "Educación", ExpenseKind.EXPENSE, "school", 0xFF6366F1, 7),
        ExpenseCategory(9, "Ropa", ExpenseKind.EXPENSE, "shirt", 0xFFEC4899, 8),
        ExpenseCategory(10, "Entretenimiento", ExpenseKind.EXPENSE, "movie", 0xFFF97316, 9),
        ExpenseCategory(11, "Negocio", ExpenseKind.EXPENSE, "work", 0xFF0EA5E9, 10),
        ExpenseCategory(12, "Pagos a proveedores", ExpenseKind.EXPENSE, "store", 0xFF64748B, 11),
        ExpenseCategory(13, "Regalos", ExpenseKind.EXPENSE, "gift", 0xFFD946EF, 12),
        ExpenseCategory(14, "Mascotas", ExpenseKind.EXPENSE, "pets", 0xFFA16207, 13),
        ExpenseCategory(15, "Otros gastos", ExpenseKind.EXPENSE, "more", 0xFF94A3B8, 14),
        ExpenseCategory(101, "Sueldo", ExpenseKind.INCOME, "salary", 0xFF22C55E, 0),
        ExpenseCategory(102, "Ventas", ExpenseKind.INCOME, "sell", 0xFF14B8A6, 1),
        ExpenseCategory(103, "Cobros a clientes", ExpenseKind.INCOME, "client", 0xFF3B82F6, 2),
        ExpenseCategory(104, "Inversiones", ExpenseKind.INCOME, "trend", 0xFFA855F7, 3),
        ExpenseCategory(105, "Regalos recibidos", ExpenseKind.INCOME, "gift", 0xFFEC4899, 4),
        ExpenseCategory(106, "Otros ingresos", ExpenseKind.INCOME, "more", 0xFF94A3B8, 5),
    )

    const val PAYMENTS_CATEGORY_ID = 12L
    const val COLLECTIONS_CATEGORY_ID = 103L

    val iconKeys = listOf(
        "food", "cart", "car", "home", "bolt", "phone", "health", "school", "shirt", "movie",
        "work", "store", "gift", "pets", "salary", "sell", "client", "trend", "coffee", "fuel",
        "travel", "sport", "beauty", "baby", "bank", "card", "music", "tools", "more",
    )

    val palette = listOf(
        0xFFF59E0B, 0xFF10B981, 0xFF3B82F6, 0xFF8B5CF6, 0xFFEAB308, 0xFF06B6D4, 0xFFEF4444,
        0xFF6366F1, 0xFFEC4899, 0xFFF97316, 0xFF0EA5E9, 0xFF64748B, 0xFF22C55E, 0xFF14B8A6,
        0xFFA855F7, 0xFFA16207,
    )
}

object CategoryStore {
    private const val PREFS = "agenda_wa_expenses"
    private const val KEY = "categories"

    fun toJson(c: ExpenseCategory) = JSONObject().apply {
        put("id", c.id)
        put("name", c.name)
        put("kind", c.kind.name)
        put("icon", c.iconKey)
        put("color", c.colorArgb)
        put("sortOrder", c.sortOrder)
    }

    fun fromJson(o: JSONObject) = ExpenseCategory(
        id = o.getLong("id"),
        name = o.optString("name", "Categoría"),
        kind = runCatching { ExpenseKind.valueOf(o.optString("kind")) }.getOrDefault(ExpenseKind.EXPENSE),
        iconKey = o.optString("icon", "more"),
        colorArgb = o.optLong("color", 0xFF94A3B8),
        sortOrder = o.optInt("sortOrder", 0),
    )

    fun load(context: Context): List<ExpenseCategory> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.contains(KEY)) {
            save(context, ExpenseCategories.defaults)
            return ExpenseCategories.defaults
        }
        return readList(context, PREFS, KEY, ::fromJson).sortedWith(compareBy({ it.kind }, { it.sortOrder }, { it.name }))
    }

    fun save(context: Context, categories: List<ExpenseCategory>) =
        writeList(context, PREFS, KEY, categories.map(::toJson))
}

object ExpenseStore {
    private const val PREFS = "agenda_wa_expenses"
    private const val KEY = "records"

    fun toJson(e: Expense) = JSONObject().apply {
        put("id", e.id)
        put("kind", e.kind.name)
        put("amount", e.amount)
        put("categoryId", e.categoryId)
        put("note", e.note)
        put("dateMillis", e.dateMillis)
        put("createdAtMillis", e.createdAtMillis)
        put("updatedAtMillis", e.updatedAtMillis)
    }

    fun fromJson(o: JSONObject) = Expense(
        id = o.getLong("id"),
        kind = runCatching { ExpenseKind.valueOf(o.optString("kind")) }.getOrDefault(ExpenseKind.EXPENSE),
        amount = o.optDouble("amount", 0.0),
        categoryId = o.optLong("categoryId", 15),
        note = o.optString("note"),
        dateMillis = o.optLong("dateMillis", System.currentTimeMillis()),
        createdAtMillis = o.optLong("createdAtMillis", System.currentTimeMillis()),
        updatedAtMillis = o.optLong("updatedAtMillis", System.currentTimeMillis()),
    )

    fun load(context: Context): List<Expense> =
        readList(context, PREFS, KEY, ::fromJson).sortedByDescending { it.dateMillis }

    fun save(context: Context, records: List<Expense>) =
        writeList(context, PREFS, KEY, records.map(::toJson))
}

object ExpenseSettingsStore {
    private const val PREFS = "agenda_wa_expenses"

    fun load(context: Context): ExpenseSettings {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return ExpenseSettings(
            currencySymbol = p.getString("currency", "$") ?: "$",
            monthlyBudget = p.getFloat("budget", 0f).toDouble(),
            dailyReminderEnabled = p.getBoolean("reminder_enabled", false),
            reminderHour = p.getInt("reminder_hour", 21),
            reminderMinute = p.getInt("reminder_minute", 0),
            openingBalance = p.getFloat("opening_balance", 0f).toDouble(),
        )
    }

    fun save(context: Context, s: ExpenseSettings) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("currency", s.currencySymbol)
            .putFloat("budget", s.monthlyBudget.toFloat())
            .putBoolean("reminder_enabled", s.dailyReminderEnabled)
            .putInt("reminder_hour", s.reminderHour)
            .putInt("reminder_minute", s.reminderMinute)
            .putFloat("opening_balance", s.openingBalance.toFloat())
            .apply()
    }

    fun toJson(s: ExpenseSettings) = JSONObject().apply {
        put("currency", s.currencySymbol)
        put("budget", s.monthlyBudget)
        put("reminderEnabled", s.dailyReminderEnabled)
        put("reminderHour", s.reminderHour)
        put("reminderMinute", s.reminderMinute)
        put("openingBalance", s.openingBalance)
    }

    fun fromJson(o: JSONObject) = ExpenseSettings(
        currencySymbol = o.optString("currency", "$").ifBlank { "$" },
        monthlyBudget = o.optDouble("budget", 0.0),
        dailyReminderEnabled = o.optBoolean("reminderEnabled", false),
        reminderHour = o.optInt("reminderHour", 21),
        reminderMinute = o.optInt("reminderMinute", 0),
        openingBalance = o.optDouble("openingBalance", 0.0),
    )
}
