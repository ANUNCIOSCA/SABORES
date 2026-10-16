package com.agendawa.app

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.provider.ContactsContract
import android.provider.OpenableColumns
import android.util.Xml
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import java.util.Locale
import java.util.zip.ZipInputStream

val esLocale: Locale = Locale.forLanguageTag("es-VE")

fun formatDate(millis: Long, pattern: String): String =
    SimpleDateFormat(pattern, esLocale).format(Date(millis))

fun money(amount: Double, symbol: String = "$"): String {
    val formatted = String.format(esLocale, "%,.2f", amount)
    return if (symbol.length <= 1) "$symbol$formatted" else "$symbol $formatted"
}

// ---------------------------------------------------------------------------
// Copias de seguridad
// ---------------------------------------------------------------------------

object BackupManager {
    private const val FORMAT = "agenda-wa-backup"
    private const val SCHEMA_VERSION = 4
    const val APP_VERSION = "2.6.2"

    fun exportJson(context: Context): String {
        val root = JSONObject()
        root.put("format", FORMAT)
        root.put("schemaVersion", SCHEMA_VERSION)
        root.put("appVersion", APP_VERSION)
        root.put("exportedAt", System.currentTimeMillis())
        root.put("profileName", ProfileStore.name(context))
        root.put("tasks", JSONArray().apply { TaskStore.load(context).forEach { put(TaskStore.toJson(it)) } })
        root.put("notes", JSONArray().apply { SmartNoteStore.load(context).forEach { put(SmartNoteStore.toJson(it)) } })
        root.put("clients", JSONArray().apply { ClientStore.load(context).forEach { put(ClientStore.toJson(it)) } })
        root.put("financeEntries", JSONArray().apply { FinanceStore.load(context).forEach { put(FinanceStore.toJson(it)) } })
        root.put("expenses", JSONArray().apply { ExpenseStore.load(context).forEach { put(ExpenseStore.toJson(it)) } })
        root.put("expenseCategories", JSONArray().apply { CategoryStore.load(context).forEach { put(CategoryStore.toJson(it)) } })
        root.put("expenseSettings", ExpenseSettingsStore.toJson(ExpenseSettingsStore.load(context)))
        return root.toString(2)
    }

    private inline fun <T> parseArray(root: JSONObject, key: String, parse: (JSONObject) -> T): List<T> {
        val array = root.optJSONArray(key) ?: return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                runCatching { parse(array.getJSONObject(i)) }.getOrNull()?.let { add(it) }
            }
        }
    }

    private fun <T> merge(existing: List<T>, incoming: List<T>, id: (T) -> Long): List<T> {
        val ids = incoming.map(id).toSet()
        return existing.filterNot { id(it) in ids } + incoming
    }

    /** Restaura una copia JSON. Combina por id: lo que viene en la copia reemplaza lo que tenga el mismo id. */
    fun restore(context: Context, text: String): RestoreSummary {
        val trimmed = text.trim()
        val root: JSONObject = if (trimmed.startsWith("[")) {
            JSONObject().put("tasks", JSONArray(trimmed)) // copias muy antiguas: solo lista de tareas
        } else {
            JSONObject(trimmed)
        }
        val format = root.optString("format")
        if (format.isNotEmpty() && format != FORMAT) throw IllegalArgumentException("El archivo no es una copia válida de Agenda WA")
        if (format.isEmpty() && !root.has("tasks") && !root.has("notes")) throw IllegalArgumentException("El archivo no es una copia válida de Agenda WA")
        if (root.optInt("schemaVersion", 1) > SCHEMA_VERSION) throw IllegalArgumentException("La versión de la copia no es compatible")

        val tasks = parseArray(root, "tasks", TaskStore::fromJson)
        val notes = parseArray(root, "notes", SmartNoteStore::fromJson)
        val clients = parseArray(root, "clients", ClientStore::fromJson)
        val finance = parseArray(root, "financeEntries", FinanceStore::fromJson)
        val expenses = parseArray(root, "expenses", ExpenseStore::fromJson)
        val categories = parseArray(root, "expenseCategories", CategoryStore::fromJson)

        val allTasks = merge(TaskStore.load(context), tasks) { it.id }
        TaskStore.save(context, allTasks)
        SmartNoteStore.save(context, merge(SmartNoteStore.load(context), notes) { it.id })
        ClientStore.save(context, merge(ClientStore.load(context), clients) { it.id })
        FinanceStore.save(context, merge(FinanceStore.load(context), finance) { it.id })
        ExpenseStore.save(context, merge(ExpenseStore.load(context), expenses) { it.id })
        if (categories.isNotEmpty()) CategoryStore.save(context, merge(CategoryStore.load(context), categories) { it.id })
        root.optJSONObject("expenseSettings")?.let { ExpenseSettingsStore.save(context, ExpenseSettingsStore.fromJson(it)) }
        val name = root.optString("profileName")
        if (name.isNotBlank() && ProfileStore.name(context).isBlank()) ProfileStore.saveName(context, name)

        ReminderScheduler.rescheduleAll(context)
        ExpenseReminder.apply(context)

        val imported = tasks.size + notes.size + clients.size + finance.size + expenses.size
        return RestoreSummary(
            imported = imported,
            importedTasks = tasks.size,
            importedNotes = notes.size,
            importedClients = clients.size,
            importedFinanceEntries = finance.size,
            importedExpenses = expenses.size,
            totalAfterRestore = allTasks.size,
        )
    }

    fun readableMarkdown(context: Context): String {
        val tasks = TaskStore.load(context)
        val notes = SmartNoteStore.load(context)
        val clients = ClientStore.load(context)
        val finance = FinanceStore.load(context)
        val expenses = ExpenseStore.load(context)
        val categories = CategoryStore.load(context).associateBy { it.id }
        val settings = ExpenseSettingsStore.load(context)
        val fmt = "dd/MM/yyyy h:mm a"
        return buildString {
            appendLine("# Agenda WA - Copia legible")
            appendLine()
            appendLine("- Exportado: ${formatDate(System.currentTimeMillis(), fmt)}")
            appendLine("- Recordatorios: ${tasks.size}")
            appendLine("- Ideas y notas: ${notes.size}")
            appendLine("- Clientes: ${clients.size}")
            appendLine("- Movimientos financieros: ${finance.size}")
            appendLine("- Registros de Mis Gastos: ${expenses.size}")
            appendLine()
            appendLine("---")
            appendLine()
            appendLine("# Recordatorios y agenda")
            appendLine()
            if (tasks.isEmpty()) appendLine("No hay recordatorios guardados.")
            tasks.forEach { t ->
                appendLine("## ${t.title.ifBlank { "Sin título" }}")
                appendLine("- **Fecha:** ${formatDate(t.dateTimeMillis, fmt)}")
                appendLine("- **Tipo:** ${if (t.isWhatsApp) "WhatsApp" else "Recordatorio"}")
                appendLine("- **Estado:** ${if (t.completed) "Completado" else "Pendiente"}")
                if (t.phone.isNotBlank()) appendLine("- **Teléfono:** ${t.phone}")
                if (t.message.isNotBlank()) { appendLine(); appendLine(t.message) }
                appendLine()
            }
            appendLine("# Ideas, negocios y notas")
            appendLine()
            if (notes.isEmpty()) appendLine("No hay notas guardadas.")
            notes.forEach { n ->
                appendLine("## ${n.title.ifBlank { "Sin título" }}")
                appendLine("- **Tipo:** ${n.type.label}")
                if (n.personName.isNotBlank()) appendLine("- **Persona:** ${n.personName}")
                if (n.project.isNotBlank()) appendLine("- **Proyecto:** ${n.project}")
                if (n.tags.isNotBlank()) appendLine("- **Etiquetas:** ${n.tags}")
                appendLine("- **Fijada:** ${if (n.pinned) "Sí" else "No"}")
                appendLine("- **Actualizada:** ${formatDate(n.updatedAtMillis, fmt)}")
                appendLine()
                appendLine("### Contenido")
                appendLine(n.content)
                appendLine()
            }
            appendLine("# Clientes")
            appendLine()
            if (clients.isEmpty()) appendLine("No hay clientes guardados.")
            clients.forEach { c ->
                appendLine("## ${c.name.ifBlank { "Sin nombre" }}")
                if (c.phone.isNotBlank()) appendLine("- **Teléfono:** ${c.phone}")
                if (c.email.isNotBlank()) appendLine("- **Correo:** ${c.email}")
                if (c.company.isNotBlank()) appendLine("- **Empresa/proyecto:** ${c.company}")
                if (c.notes.isNotBlank()) appendLine("- **Notas:** ${c.notes}")
                appendLine()
            }
            appendLine("# Finanzas")
            appendLine()
            if (finance.isEmpty()) appendLine("No hay movimientos financieros guardados.")
            finance.forEach { e ->
                appendLine("## ${e.concept.ifBlank { "Sin concepto" }}")
                appendLine("- **Tipo:** ${if (e.type == FinanceType.RECEIVABLE) "Por cobrar" else "Por pagar"}")
                appendLine("- **Persona/empresa:** ${e.counterparty}")
                appendLine("- **Total:** ${money(e.totalAmount)} ${e.currency}")
                appendLine("- **Pagado/abonado:** ${money(e.paidAmount)} ${e.currency}")
                appendLine("- **Saldo:** ${money(e.balance)} ${e.currency}")
                appendLine("- **Fecha:** ${formatDate(e.dueDateMillis, "dd/MM/yyyy")}")
                appendLine("- **Estado:** ${if (e.isPaid) "Pagado" else if (e.isOverdue()) "Vencido" else "Pendiente"}")
                if (e.notes.isNotBlank()) appendLine("- **Notas:** ${e.notes}")
                appendLine()
            }
            appendLine("# Mis Gastos")
            appendLine()
            if (expenses.isEmpty()) appendLine("No hay registros de gastos o ingresos.")
            else {
                val totalExp = expenses.filter { it.kind == ExpenseKind.EXPENSE }.sumOf { it.amount }
                val totalInc = expenses.filter { it.kind == ExpenseKind.INCOME }.sumOf { it.amount }
                appendLine("- **Gastos totales:** ${money(totalExp, settings.currencySymbol)}")
                appendLine("- **Ingresos totales:** ${money(totalInc, settings.currencySymbol)}")
                appendLine("- **Saldo:** ${money(totalInc - totalExp, settings.currencySymbol)}")
                appendLine()
                expenses.forEach { x ->
                    val cat = categories[x.categoryId]?.name ?: "Sin categoría"
                    val sign = if (x.kind == ExpenseKind.EXPENSE) "-" else "+"
                    appendLine("- ${formatDate(x.dateMillis, "dd/MM/yyyy")} · $cat · $sign${money(x.amount, settings.currencySymbol)}${if (x.note.isNotBlank()) " · ${x.note}" else ""}")
                }
            }
        }
    }
}

/** Escribe texto plano (con encabezados "#") en un PDF tamaño carta/A4. */
object PdfTextWriter {
    fun write(text: String, out: OutputStream, title: String = "Agenda WA") {
        val doc = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        val margin = 40f
        val body = Paint().apply { textSize = 10.5f; isAntiAlias = true; color = 0xFF172033.toInt() }
        val h1 = Paint(body).apply { textSize = 16f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); color = 0xFF0D47A1.toInt() }
        val h2 = Paint(body).apply { textSize = 12.5f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
        val footer = Paint(body).apply { textSize = 8.5f; color = 0xFF64748B.toInt() }
        val maxWidth = pageWidth - margin * 2

        var pageNumber = 0
        var page: PdfDocument.Page? = null
        var y = 0f

        fun finishPage() {
            page?.let {
                it.canvas.drawText("$title - página $pageNumber", margin, pageHeight - 20f, footer)
                doc.finishPage(it)
            }
        }

        fun newPage() {
            finishPage()
            pageNumber++
            page = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
            y = margin + 10f
        }

        fun wrap(line: String, paint: Paint): List<String> {
            if (line.isEmpty()) return listOf("")
            val words = line.split(Regex("\\s+"))
            val result = mutableListOf<String>()
            var current = ""
            for (word in words) {
                val candidate = if (current.isEmpty()) word else "$current $word"
                if (paint.measureText(candidate) <= maxWidth) current = candidate
                else {
                    if (current.isNotEmpty()) result += current
                    var w = word
                    while (paint.measureText(w) > maxWidth && w.length > 1) {
                        var cut = w.length
                        while (cut > 1 && paint.measureText(w.substring(0, cut)) > maxWidth) cut--
                        result += w.substring(0, cut)
                        w = w.substring(cut)
                    }
                    current = w
                }
            }
            if (current.isNotEmpty()) result += current
            return result
        }

        newPage()
        text.lines().forEach { raw ->
            val (paint, content, extra) = when {
                raw.startsWith("# ") -> Triple(h1, raw.removePrefix("# "), 8f)
                raw.startsWith("## ") -> Triple(h2, raw.removePrefix("## "), 5f)
                raw.startsWith("### ") -> Triple(h2, raw.removePrefix("### "), 3f)
                raw.trim() == "---" -> Triple(body, "", 0f)
                else -> Triple(body, raw.replace("**", ""), 0f)
            }
            y += extra
            wrap(content, paint).forEach { line ->
                val lineHeight = paint.textSize * 1.45f
                if (y + lineHeight > pageHeight - margin) newPage()
                y += lineHeight
                page!!.canvas.drawText(line, margin, y, paint)
            }
        }
        finishPage()
        doc.writeTo(out)
        doc.close()
    }
}

// ---------------------------------------------------------------------------
// Importación de clientes y deudas (Excel, CSV/TSV, JSON)
// ---------------------------------------------------------------------------

object FinanceImportManager {
    private const val MAX_BYTES = 20L * 1024 * 1024

    fun fileName(context: Context, uri: Uri): String =
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull() ?: uri.lastPathSegment ?: "archivo"

    fun importFile(context: Context, uri: Uri, existingClients: List<Client>, existingEntries: List<FinanceEntry>): FinanceImportResult {
        val name = fileName(context, uri)
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalStateException("No se pudo leer el archivo.")
        if (bytes.size > MAX_BYTES) throw IllegalStateException("El archivo supera el límite de 20 MB.")
        val lower = name.lowercase(Locale.ROOT)
        val isZip = bytes.size > 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()

        if (lower.endsWith(".json") || bytes.decodeToString(0, minOf(bytes.size, 64)).trim().let { it.startsWith("{") || it.startsWith("[") }) {
            return importJson(bytes.decodeToString(), name, existingClients)
        }
        val rows: List<List<String>> = if (lower.endsWith(".xlsx") || isZip) readXlsx(bytes) else readDelimited(bytes.decodeToString())
        return importRows(rows, name, existingClients, existingEntries)
    }

    private fun importJson(text: String, name: String, existingClients: List<Client>): FinanceImportResult {
        val trimmed = text.trim()
        if (trimmed.startsWith("{")) {
            val root = JSONObject(trimmed)
            val clientsArr = root.optJSONArray("clients")
            val entriesArr = root.optJSONArray("financeEntries") ?: root.optJSONArray("entries") ?: root.optJSONArray("debts")
            if (entriesArr != null && entriesArr.length() > 0 && entriesArr.getJSONObject(0).has("totalAmount")) {
                val clients = buildList { if (clientsArr != null) for (i in 0 until clientsArr.length()) runCatching { add(ClientStore.fromJson(clientsArr.getJSONObject(i))) } }
                val entries = buildList { for (i in 0 until entriesArr.length()) runCatching { add(FinanceStore.fromJson(entriesArr.getJSONObject(i))) } }
                return FinanceImportResult(clients, entries, name, entriesArr.length(), entriesArr.length() - entries.size)
            }
            val arr = entriesArr ?: throw IllegalStateException("No se encontraron filas para importar.")
            return importRows(jsonArrayToRows(arr), name, existingClients, emptyList())
        }
        return importRows(jsonArrayToRows(JSONArray(trimmed)), name, existingClients, emptyList())
    }

    private fun jsonArrayToRows(arr: JSONArray): List<List<String>> {
        val keys = linkedSetOf<String>()
        for (i in 0 until arr.length()) arr.optJSONObject(i)?.keys()?.forEach { keys += it }
        val header = keys.toList()
        val rows = mutableListOf(header)
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            rows += header.map { if (o.isNull(it)) "" else o.opt(it)?.toString().orEmpty() }
        }
        return rows
    }

    private fun readDelimited(text: String): List<List<String>> {
        val lines = text.replace("\r\n", "\n").replace('\r', '\n').lines().filter { it.isNotBlank() }
        if (lines.isEmpty()) return emptyList()
        val first = lines.first()
        val delimiter = listOf('\t', ';', ',').maxBy { d -> first.count { it == d } }
        return lines.map { parseCsvLine(it, delimiter) }
    }

    private fun parseCsvLine(line: String, d: Char): List<String> {
        val out = mutableListOf<String>()
        val sb = StringBuilder()
        var quoted = false
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            when {
                ch == '"' && quoted && i + 1 < line.length && line[i + 1] == '"' -> { sb.append('"'); i++ }
                ch == '"' -> quoted = !quoted
                ch == d && !quoted -> { out += sb.toString().trim(); sb.clear() }
                else -> sb.append(ch)
            }
            i++
        }
        out += sb.toString().trim()
        return out
    }

    private fun readXlsx(bytes: ByteArray): List<List<String>> {
        val files = mutableMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val n = entry.name
                if (n == "xl/sharedStrings.xml" || (n.startsWith("xl/worksheets/sheet") && n.endsWith(".xml"))) {
                    files[n] = zip.readBytes()
                }
                entry = zip.nextEntry
            }
        }
        val sheetName = files.keys.filter { it.startsWith("xl/worksheets/sheet") }
            .minByOrNull { it.filter(Char::isDigit).toIntOrNull() ?: 999 }
            ?: throw IllegalStateException("El Excel no contiene una hoja reconocible.")
        val shared = files["xl/sharedStrings.xml"]?.let { parseSharedStrings(it) } ?: emptyList()
        return parseSheet(files.getValue(sheetName), shared)
    }

    private fun parseSharedStrings(data: ByteArray): List<String> {
        val parser = Xml.newPullParser()
        parser.setInput(ByteArrayInputStream(data), "UTF-8")
        val result = mutableListOf<String>()
        var current: StringBuilder? = null
        var inT = false
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "si" -> current = StringBuilder()
                    "t" -> inT = true
                }
                XmlPullParser.TEXT -> if (inT) current?.append(parser.text)
                XmlPullParser.END_TAG -> when (parser.name) {
                    "t" -> inT = false
                    "si" -> { result += current?.toString().orEmpty(); current = null }
                }
            }
            event = parser.next()
        }
        return result
    }

    private fun columnIndex(ref: String): Int {
        var idx = 0
        for (ch in ref) {
            if (!ch.isLetter()) break
            idx = idx * 26 + (ch.uppercaseChar() - 'A' + 1)
        }
        return idx - 1
    }

    private fun parseSheet(data: ByteArray, shared: List<String>): List<List<String>> {
        val parser = Xml.newPullParser()
        parser.setInput(ByteArrayInputStream(data), "UTF-8")
        val rows = mutableListOf<List<String>>()
        var row: MutableMap<Int, String>? = null
        var cellRef = ""
        var cellType = ""
        var value = StringBuilder()
        var inValue = false
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "row" -> row = mutableMapOf()
                    "c" -> {
                        cellRef = parser.getAttributeValue(null, "r") ?: ""
                        cellType = parser.getAttributeValue(null, "t") ?: ""
                        value = StringBuilder()
                    }
                    "v", "t" -> inValue = true
                }
                XmlPullParser.TEXT -> if (inValue) value.append(parser.text)
                XmlPullParser.END_TAG -> when (parser.name) {
                    "v", "t" -> inValue = false
                    "c" -> {
                        val raw = value.toString()
                        val text = if (cellType == "s") shared.getOrNull(raw.trim().toIntOrNull() ?: -1).orEmpty() else raw
                        val col = if (cellRef.isNotEmpty()) columnIndex(cellRef) else (row?.size ?: 0)
                        row?.put(col, text.trim())
                    }
                    "row" -> {
                        val r = row
                        if (r != null && r.isNotEmpty()) {
                            val max = r.keys.maxOrNull() ?: 0
                            rows += (0..max).map { r[it].orEmpty() }
                        }
                        row = null
                    }
                }
            }
            event = parser.next()
        }
        return rows
    }

    private fun norm(s: String) = Regex("[^a-z0-9]+").replace(SmartNoteOrganizer.normalize(s), "")

    private fun findColumn(header: List<String>, vararg aliases: String): Int =
        header.indexOfFirst { h -> aliases.any { a -> h == a } }.takeIf { it >= 0 }
            ?: header.indexOfFirst { h -> aliases.any { a -> h.contains(a) } }

    private fun parseMoney(raw: String): Double? {
        val cleaned = raw.replace(Regex("[^0-9,.\\-]"), "")
        if (cleaned.isBlank() || cleaned == "-") return null
        val negative = cleaned.startsWith("-")
        return ExpenseVoiceParser.parseAmount(cleaned.removePrefix("-"))?.let { if (negative) -it else it }
    }

    private val dateFormats = listOf(
        "dd/MM/yyyy", "d/M/yyyy", "dd-MM-yyyy", "d-M-yyyy", "dd.MM.yyyy", "d.M.yyyy",
        "yyyy-MM-dd", "yyyy/M/d", "dd/MM/yy", "d/M/yy",
    )

    private fun parseDate(raw: String): Long? {
        val s = raw.trim().substringBefore(" ").substringBefore("T")
        if (s.isEmpty()) return null
        s.toDoubleOrNull()?.let { serial ->
            if (serial in 20000.0..80000.0) {
                return LocalDate.of(1899, 12, 30).plusDays(serial.toLong())
                    .atTime(9, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            }
        }
        for (f in dateFormats) {
            val parsed = runCatching { SimpleDateFormat(f, Locale.US).apply { isLenient = false }.parse(s) }.getOrNull()
            if (parsed != null) return parsed.time + 9 * 3_600_000L
        }
        return null
    }

    fun importRows(
        rows: List<List<String>>,
        fileName: String,
        existingClients: List<Client>,
        existingEntries: List<FinanceEntry>,
    ): FinanceImportResult {
        val headerIndex = rows.indexOfFirst { r -> r.count { it.isNotBlank() } >= 2 }
        if (headerIndex < 0 || rows.size <= headerIndex + 1) throw IllegalStateException("No se encontraron filas para importar.")
        val header = rows[headerIndex].map(::norm)
        val cName = findColumn(header, "cliente", "nombre", "persona", "client", "counterparty", "proveedor", "supplier", "acreedor", "deudor", "name")
        val cConcept = findColumn(header, "concepto", "descripcion", "detalle", "servicio", "description", "movimiento", "concept")
        val cTotal = findColumn(header, "total", "monto", "valor", "importe", "amount", "deuda")
        val cPaid = findColumn(header, "pagado", "abonado", "abono", "paid", "payment")
        val cBalance = findColumn(header, "saldo", "restante", "balance", "deudapendiente", "pendiente")
        val cDate = findColumn(header, "vencimiento", "vence", "fechadecobro", "fechapago", "duedate", "fecha", "date")
        val cType = findColumn(header, "tipo", "type", "clase")
        val cPhone = findColumn(header, "telefono", "celular", "movil", "phone", "whatsapp")
        val cEmail = findColumn(header, "correo", "email", "mail")
        val cCompany = findColumn(header, "empresa", "company", "proyecto", "project")
        val cNotes = findColumn(header, "observaciones", "observacion", "notas", "nota", "notes")
        val cCurrency = findColumn(header, "moneda", "currency", "divisa")
        val headerSaysPayable = header.any { it.contains("proveedor") || it.contains("acreedor") || it.contains("supplier") }

        val clientsByName = existingClients.associateBy { norm(it.name) }.toMutableMap()
        val newClients = mutableListOf<Client>()
        val entries = mutableListOf<FinanceEntry>()
        var skipped = 0
        var idSeed = System.currentTimeMillis()
        val existingKeys = existingEntries.map { "${norm(it.counterparty)}|${norm(it.concept)}|${"%.2f".format(Locale.US, it.totalAmount)}" }.toSet()

        fun cell(r: List<String>, i: Int) = if (i >= 0) r.getOrNull(i).orEmpty().trim() else ""

        for (r in rows.drop(headerIndex + 1)) {
            if (r.all { it.isBlank() }) continue
            val name = cell(r, cName)
            val paid = parseMoney(cell(r, cPaid)) ?: 0.0
            val balance = parseMoney(cell(r, cBalance))
            var total = parseMoney(cell(r, cTotal))
            if (total == null && balance != null) total = balance + paid
            if (name.isBlank() || total == null || total == 0.0) { skipped++; continue }
            val typeText = norm(cell(r, cType))
            val payable = typeText.contains("pagar") || typeText.contains("payable") || typeText.contains("proveedor") ||
                typeText.contains("egreso") || typeText.contains("gasto") || typeText.contains("acreedor") ||
                (typeText.isBlank() && headerSaysPayable)
            val concept = cell(r, cConcept).ifBlank { "Deuda importada ${formatDate(System.currentTimeMillis(), "dd/MM/yyyy")}" }
            val key = "${norm(name)}|${norm(concept)}|${"%.2f".format(Locale.US, kotlin.math.abs(total))}"
            if (key in existingKeys) { skipped++; continue }

            val client = clientsByName[norm(name)] ?: Client(
                id = idSeed++,
                name = name,
                phone = cell(r, cPhone),
                email = cell(r, cEmail),
                company = cell(r, cCompany),
            ).also { clientsByName[norm(name)] = it; newClients += it }

            val currencyRaw = cell(r, cCurrency).uppercase(Locale.ROOT)
            val currency = when {
                currencyRaw.contains("BS") || currencyRaw.contains("VES") || currencyRaw.contains("BOL") -> "VES"
                currencyRaw.contains("COP") -> "COP"
                currencyRaw.contains("EUR") || currencyRaw.contains("€") -> "EUR"
                else -> "USD"
            }
            entries += FinanceEntry(
                id = idSeed++,
                type = if (payable) FinanceType.PAYABLE else FinanceType.RECEIVABLE,
                clientId = client.id,
                counterparty = client.name,
                concept = concept,
                totalAmount = kotlin.math.abs(total),
                paidAmount = paid.coerceIn(0.0, kotlin.math.abs(total)),
                currency = currency,
                dueDateMillis = parseDate(cell(r, cDate)) ?: System.currentTimeMillis(),
                notes = cell(r, cNotes),
            )
        }
        if (entries.isEmpty() && newClients.isEmpty()) throw IllegalStateException("No se encontraron filas para importar.")
        return FinanceImportResult(newClients, entries, fileName, rows.size - headerIndex - 1, skipped)
    }
}

// ---------------------------------------------------------------------------
// Exportación de Mis Gastos
// ---------------------------------------------------------------------------

object ExpenseExport {
    fun csv(expenses: List<Expense>, categories: Map<Long, ExpenseCategory>): String = buildString {
        append('﻿')
        appendLine("Fecha;Tipo;Categoría;Monto;Nota")
        expenses.sortedBy { it.dateMillis }.forEach { e ->
            val cat = categories[e.categoryId]?.name ?: "Sin categoría"
            val note = e.note.replace("\"", "\"\"")
            appendLine("${formatDate(e.dateMillis, "dd/MM/yyyy")};${e.kind.label};$cat;${String.format(Locale.US, "%.2f", e.amount)};\"$note\"")
        }
    }

    fun report(title: String, expenses: List<Expense>, categories: Map<Long, ExpenseCategory>, symbol: String): String = buildString {
        val exp = expenses.filter { it.kind == ExpenseKind.EXPENSE }
        val inc = expenses.filter { it.kind == ExpenseKind.INCOME }
        appendLine("# $title")
        appendLine()
        appendLine("- Generado: ${formatDate(System.currentTimeMillis(), "dd/MM/yyyy h:mm a")}")
        appendLine("- Ingresos: ${money(inc.sumOf { it.amount }, symbol)}")
        appendLine("- Gastos: ${money(exp.sumOf { it.amount }, symbol)}")
        appendLine("- Saldo: ${money(inc.sumOf { it.amount } - exp.sumOf { it.amount }, symbol)}")
        appendLine()
        fun byCategory(list: List<Expense>, label: String) {
            if (list.isEmpty()) return
            appendLine("## $label por categoría")
            val total = list.sumOf { it.amount }
            list.groupBy { it.categoryId }.mapValues { (_, v) -> v.sumOf { it.amount } }
                .entries.sortedByDescending { it.value }
                .forEach { (id, amount) ->
                    val pct = if (total > 0) amount * 100 / total else 0.0
                    appendLine("- ${categories[id]?.name ?: "Sin categoría"}: ${money(amount, symbol)} (${String.format(esLocale, "%.1f", pct)}%)")
                }
            appendLine()
        }
        byCategory(exp, "Gastos")
        byCategory(inc, "Ingresos")
        appendLine("## Detalle")
        expenses.sortedBy { it.dateMillis }.forEach { e ->
            val sign = if (e.kind == ExpenseKind.EXPENSE) "-" else "+"
            appendLine("- ${formatDate(e.dateMillis, "dd/MM/yyyy")} · ${categories[e.categoryId]?.name ?: "Sin categoría"} · $sign${money(e.amount, symbol)}${if (e.note.isNotBlank()) " · ${e.note}" else ""}")
        }
    }
}

// ---------------------------------------------------------------------------
// Contenido compartido desde otras apps y selector de contactos
// ---------------------------------------------------------------------------

object SharedContentParser {
    fun from(intent: Intent?): SharedContent? {
        if (intent == null) return null
        if (intent.action != Intent.ACTION_SEND && intent.action != Intent.ACTION_SEND_MULTIPLE) return null
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim().orEmpty()
        val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT)?.trim().orEmpty()
        if (text.isBlank() && subject.isBlank()) return null
        val lower = text.lowercase(Locale.ROOT)
        val isWhatsApp = lower.contains("wa.me") || lower.contains("whatsapp.com")
        val title = when {
            subject.isNotBlank() -> subject
            lower.contains("instagram.com") -> "Publicación de Instagram"
            lower.contains("tiktok.com") || lower.contains("vm.tiktok.com") -> "Publicación de TikTok"
            isWhatsApp -> "Mensaje de WhatsApp"
            else -> "Contenido compartido"
        }
        val body = if (subject.isNotBlank() && text.isNotBlank() && !text.contains(subject)) "$subject\n\n$text" else text.ifBlank { subject }
        return SharedContent(title, body, isWhatsApp)
    }
}

object ContactPicker {
    fun intent(): Intent = Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)

    fun read(context: Context, uri: Uri?): SelectedPhoneContact? {
        if (uri == null) return null
        return runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ContactsContract.CommonDataKinds.Phone.NORMALIZED_NUMBER,
                ),
                null, null, null,
            )?.use { c ->
                if (!c.moveToFirst()) return@use null
                val name = c.getString(0).orEmpty()
                val normalized = c.getString(2)
                val number = (normalized ?: c.getString(1)).orEmpty()
                if (number.isBlank()) null else SelectedPhoneContact(name, number)
            }
        }.getOrNull()
    }
}
