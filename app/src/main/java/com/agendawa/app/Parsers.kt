package com.agendawa.app

import java.text.Normalizer
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/** Interpreta frases dictadas como "mañana a las 9 enviar un WhatsApp a Carlos". */
object VoiceParser {
    private val weekdays = linkedMapOf(
        "lunes" to DayOfWeek.MONDAY,
        "martes" to DayOfWeek.TUESDAY,
        "miércoles" to DayOfWeek.WEDNESDAY,
        "miercoles" to DayOfWeek.WEDNESDAY,
        "jueves" to DayOfWeek.THURSDAY,
        "viernes" to DayOfWeek.FRIDAY,
        "sábado" to DayOfWeek.SATURDAY,
        "sabado" to DayOfWeek.SATURDAY,
        "domingo" to DayOfWeek.SUNDAY,
    )

    private fun defaultTime(now: LocalDateTime): LocalTime =
        now.plusHours(1).withMinute(0).withSecond(0).withNano(0).toLocalTime()

    private fun parseTime(text: String): LocalTime? {
        val match = Regex("(?:a\\s+las?|a\\s+la)\\s+(\\d{1,2})(?::(\\d{2}))?").find(text)
            ?: Regex("\\b(\\d{1,2}):(\\d{2})\\b").find(text)
            ?: return null
        var hour = match.groupValues.getOrNull(1)?.toIntOrNull() ?: return null
        val minute = match.groupValues.getOrNull(2)?.toIntOrNull() ?: 0
        val afternoon = text.contains("de la tarde") || text.contains("por la tarde")
        val night = text.contains("de la noche") || text.contains("por la noche")
        val morning = text.contains("de la mañana") || text.contains("por la mañana")
        if ((afternoon || night) && hour in 1..11) hour += 12
        // "a las 3" sin decir de la mañana: en una agenda casi siempre es de la tarde
        else if (!morning && hour in 1..6) hour += 12
        if (morning && hour == 12) hour = 0
        if (hour !in 0..23 || minute !in 0..59) return null
        return LocalTime.of(hour, minute)
    }

    private fun resolveWeekday(text: String, today: LocalDate): LocalDate? {
        val entry = weekdays.entries.firstOrNull { text.contains(it.key) } ?: return null
        var date = today.with(TemporalAdjusters.nextOrSame(entry.value))
        if (date == today && !text.contains("hoy")) date = date.plusWeeks(1)
        return date
    }

    fun parse(input: String): ReminderDraft {
        val text = input.lowercase(Locale.getDefault()).trim()
        val now = LocalDateTime.now()
        val date: LocalDate = when {
            text.contains("pasado mañana") -> now.toLocalDate().plusDays(2)
            text.contains("mañana") && !text.contains("de la mañana") && !text.contains("por la mañana") ->
                now.toLocalDate().plusDays(1)
            text.contains("hoy") -> now.toLocalDate()
            else -> resolveWeekday(text, now.toLocalDate()) ?: now.toLocalDate()
        }
        val minutes = Regex("en\\s+(\\d+)\\s+minutos?").find(text)?.groupValues?.getOrNull(1)?.toLongOrNull()
        val hours = Regex("en\\s+(\\d+)\\s+horas?").find(text)?.groupValues?.getOrNull(1)?.toLongOrNull()

        val dateTime: LocalDateTime = when {
            minutes != null -> now.plusMinutes(minutes)
            hours != null -> now.plusHours(hours)
            else -> {
                val time = parseTime(text) ?: defaultTime(now)
                val candidate = LocalDateTime.of(date, time)
                if (candidate.isAfter(now) || date != now.toLocalDate()) candidate else candidate.plusDays(1)
            }
        }

        val isWhatsApp = text.contains("whatsapp") || text.contains("wasap") || text.contains("guasap")

        var cleaned = input
        cleaned = Regex("(?i)recuérdame|recuerdame|recordarme|recordatorio de|recordatorio").replace(cleaned, "")
        cleaned = Regex("(?i)pasado mañana|hoy|mañana").replace(cleaned, "")
        cleaned = Regex("(?i)a las?\\s+\\d{1,2}(:\\d{2})?").replace(cleaned, "")
        cleaned = Regex("(?i)en\\s+\\d+\\s+(minutos?|horas?)").replace(cleaned, "")
        cleaned = Regex("(?i)(de|por) la (tarde|noche|mañana)").replace(cleaned, "")
        cleaned = Regex("\\s+").replace(cleaned, " ").trim(' ', ',', '.', '-')
        if (cleaned.isBlank()) cleaned = input.trim()

        val title = cleaned.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        val millis = dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        return ReminderDraft(title = title, message = if (isWhatsApp) cleaned else "", dateTimeMillis = millis, isWhatsApp = isWhatsApp)
    }
}

/** Clasifica y titula notas automáticamente. */
object SmartNoteOrganizer {

    fun normalize(text: String): String {
        val lower = text.lowercase(Locale.getDefault())
        return Regex("\\p{Mn}+").replace(Normalizer.normalize(lower, Normalizer.Form.NFD), "")
    }

    private fun containsAny(text: String, vararg words: String) = words.any { text.contains(normalize(it)) }

    fun organize(content: String, currentTitle: String = "", personName: String = "", project: String = ""): NoteOrganization {
        val all = normalize(listOf(content, currentTitle, personName, project).filter { it.isNotBlank() }.joinToString(" "))
        val type = when {
            personName.isNotBlank() -> SmartNoteType.PERSON_TOPICS
            containsAny(all, "hablar con", "preguntar a", "reunion con", "puntos con", "conversar con", "llamar a", "consultar a", "decirle a") ->
                SmartNoteType.PERSON_TOPICS
            containsAny(all, "cliente", "presupuesto", "cotizacion", "venta", "negocio", "factura", "cobrar", "pago", "proveedor", "inversion", "propuesta comercial", "ganancia", "precio", "contrato") ->
                SmartNoteType.BUSINESS
            containsAny(all, "proyecto", "entrega", "cronograma", "pendiente", "campana", "lanzamiento", "sitio web", "pagina web", "diseno", "brief", "tarea", "objetivo", "plan de trabajo", "fecha limite") ->
                SmartNoteType.PROJECT
            containsAny(all, "http://", "https://", "www.", "instagram.com", "tiktok.com", "youtube.com", "pagina", "enlace", "link", "herramienta", "referencia", "cuenta interesante", "perfil") ->
                SmartNoteType.REFERENCE
            containsAny(all, "idea", "se me ocurre", "podriamos", "podria", "concepto", "inspiracion", "ocurrencia", "propuesta creativa", "imagino", "seria bueno", "pensando en") ->
                SmartNoteType.IDEA
            else -> SmartNoteType.GENERAL
        }
        val title = currentTitle.trim().ifBlank { buildTitle(content, type, personName, project) }
        return NoteOrganization(title, type, buildTags(all, type, personName, project))
    }

    private fun buildTitle(content: String, type: SmartNoteType, personName: String, project: String): String {
        if (type == SmartNoteType.PERSON_TOPICS && personName.isNotBlank()) return "Puntos con ${personName.trim()}"
        if (type == SmartNoteType.PROJECT && project.isNotBlank()) return "Notas de ${project.trim()}"
        val fallback = when (type) {
            SmartNoteType.IDEA -> "Nueva idea"
            SmartNoteType.BUSINESS -> "Nota de negocio"
            SmartNoteType.PERSON_TOPICS -> "Puntos a tratar"
            SmartNoteType.PROJECT -> "Nota de proyecto"
            SmartNoteType.REFERENCE -> "Referencia guardada"
            SmartNoteType.GENERAL -> "Nueva nota"
        }
        val firstLine = content.lineSequence().map { it.trim() }.firstOrNull { it.isNotBlank() } ?: return fallback
        val cleaned = Regex("(?i)^(idea|nota|recordar|anotar|pendiente)[:\\-]?\\s*").replace(firstLine, "").trim()
        if (cleaned.isBlank()) return fallback
        val sentence = Regex("(?<=[.!?])\\s+").split(cleaned).firstOrNull().orEmpty()
        return sentence.take(80).trim().ifBlank { fallback }
    }

    private fun buildTags(text: String, type: SmartNoteType, personName: String, project: String): String {
        val tags = linkedSetOf(type.label.lowercase(Locale.getDefault()))
        fun tag(name: String, vararg words: String) { if (words.any { text.contains(it) }) tags += name }
        tag("musica", "musica", "artista", "agrupacion", "cancion", "sencillo", "concierto", "ensayo")
        tag("diseno", "diseno", "logo", "flyer", "pieza grafica", "identidad visual", "portada")
        tag("web", "sitio web", "pagina web", "dominio", "hosting", "wordpress", "webmaster")
        tag("redes", "instagram", "tiktok", "facebook", "redes sociales", "contenido", "reel", "post")
        tag("video", "video", "grabacion", "editar", "guion", "ugc")
        tag("cliente", "cliente", "brief", "revision", "aprobacion")
        tag("finanzas", "pago", "cobro", "factura", "presupuesto", "precio", "inversion")
        tag("reunion", "reunion", "cita", "puntos a tratar", "agenda")
        tag("ia", "inteligencia artificial", "chatgpt", "claude", "gemini", "prompt")
        tag("urgente", "urgente", "prioridad", "hoy", "inmediato")
        if (personName.isNotBlank()) tags += "persona"
        if (project.isNotBlank()) tags += "proyecto"
        return tags.joinToString(", ")
    }
}

/**
 * Interpreta frases para Mis Gastos, por ejemplo:
 *  "gasté 25 en comida", "pagué 12,50 de taxi ayer", "me pagaron 300 de sueldo".
 */
object ExpenseVoiceParser {
    data class Result(val kind: ExpenseKind, val amount: Double?, val categoryId: Long?, val note: String, val dateMillis: Long)

    private val incomeWords = listOf("ingreso", "ingresé", "ingrese", "cobré", "cobre", "me pagaron", "recibí", "recibi", "gané", "gane", "vendí", "vendi", "sueldo", "salario", "venta")

    fun parse(input: String, categories: List<ExpenseCategory>): Result {
        val text = input.lowercase(Locale.getDefault()).trim()
        val norm = SmartNoteOrganizer.normalize(text)
        val kind = if (incomeWords.any { norm.contains(SmartNoteOrganizer.normalize(it)) }) ExpenseKind.INCOME else ExpenseKind.EXPENSE

        val amount = Regex("(\\d{1,3}(?:[.\\s]\\d{3})+(?:,\\d{1,2})?|\\d+(?:[.,]\\d{1,2})?)").find(text)?.value?.let { parseAmount(it) }

        var date = LocalDate.now()
        if (text.contains("antier") || text.contains("anteayer")) date = date.minusDays(2)
        else if (text.contains("ayer")) date = date.minusDays(1)

        val candidates = categories.filter { it.kind == kind }
        val category = candidates.firstOrNull { norm.contains(SmartNoteOrganizer.normalize(it.name)) }
            ?: guessCategory(norm, kind, candidates)

        val note = input
            .replace(Regex("(?i)gast[eé]|pagu[eé]|compr[eé]|me pagaron|cobr[eé]|recib[ií]|ingres[eé]|gan[eé]|vend[ií]"), "")
            .replace(Regex("(\\d{1,3}(?:[.\\s]\\d{3})+(?:,\\d{1,2})?|\\d+(?:[.,]\\d{1,2})?)"), "")
            .replace(Regex("(?i)\\b(d[oó]lares|bol[ií]vares|bs|usd|pesos|euros)\\b"), "")
            .replace(Regex("(?i)\\b(antier|anteayer|ayer|hoy)\\b"), "")
            .replace(Regex("\\s+"), " ")
            .trim(' ', ',', '.', '-')
            .replace(Regex("(?i)^(en|de|por|para)\\s+"), "")
            .replaceFirstChar { it.uppercase() }

        val millis = date.atTime(LocalTime.now()).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        return Result(kind, amount, category?.id, note, millis)
    }

    fun parseAmount(raw: String): Double? {
        var s = raw.trim().replace(" ", "")
        if (s.isEmpty()) return null
        val lastComma = s.lastIndexOf(',')
        val lastDot = s.lastIndexOf('.')
        s = when {
            lastComma >= 0 && lastDot >= 0 ->
                if (lastComma > lastDot) s.replace(".", "").replace(',', '.') else s.replace(",", "")
            lastComma >= 0 -> if (s.length - lastComma - 1 in 1..2) s.replace(',', '.') else s.replace(",", "")
            lastDot >= 0 -> if (s.count { it == '.' } > 1 || s.length - lastDot - 1 == 3) s.replace(".", "") else s
            else -> s
        }
        return s.toDoubleOrNull()
    }

    private fun guessCategory(norm: String, kind: ExpenseKind, candidates: List<ExpenseCategory>): ExpenseCategory? {
        val hints: Map<String, List<String>> = if (kind == ExpenseKind.EXPENSE) mapOf(
            "food" to listOf("almuerzo", "desayuno", "cena", "comida", "restaurante", "pizza", "hamburguesa", "arepa", "empanada", "cafe"),
            "cart" to listOf("mercado", "supermercado", "viveres", "abasto", "compras"),
            "car" to listOf("taxi", "uber", "yummy", "ridery", "pasaje", "gasolina", "bus", "transporte", "estacionamiento"),
            "home" to listOf("alquiler", "renta", "condominio", "casa", "hogar"),
            "bolt" to listOf("luz", "agua", "gas", "electricidad", "aseo"),
            "phone" to listOf("internet", "telefono", "saldo", "recarga", "movistar", "digitel", "movilnet", "cantv"),
            "health" to listOf("farmacia", "medicina", "doctor", "consulta", "clinica"),
            "school" to listOf("colegio", "curso", "universidad", "libro"),
            "shirt" to listOf("ropa", "zapatos", "camisa"),
            "movie" to listOf("cine", "netflix", "spotify", "fiesta", "salida"),
        ) else mapOf(
            "salary" to listOf("sueldo", "salario", "quincena", "nomina"),
            "sell" to listOf("venta", "vendi"),
            "client" to listOf("cliente", "cobro", "cobre"),
        )
        val key = hints.entries.firstOrNull { (_, words) -> words.any { norm.contains(it) } }?.key ?: return null
        return candidates.firstOrNull { it.iconKey == key }
    }
}
