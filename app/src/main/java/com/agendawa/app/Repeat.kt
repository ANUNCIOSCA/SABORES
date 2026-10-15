package com.agendawa.app

import org.json.JSONObject
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

// ---------------------------------------------------------------------------
// Avisos que se repiten: "lunes, martes y miércoles a las 8", "de lunes a viernes"...
// ---------------------------------------------------------------------------

/**
 * Regla de repetición de un aviso.
 * - [days]: días marcados en bits (lunes = 1, martes = 2, … domingo = 64).
 * - [minutes]: hora del aviso en minutos desde medianoche (8:30 a. m. = 510).
 * - [dayMinutes]: horas distintas por día (índice 0 = lunes … 6 = domingo). Vacío = misma hora todos los días.
 * - [until]: último día en que suena (milisegundos de ese día). 0 = sin fecha final.
 */
data class RepeatRule(
    val days: Int,
    val minutes: Int,
    val dayMinutes: Map<Int, Int> = emptyMap(),
    val until: Long = 0L,
) {
    fun has(day: Int) = (days and (1 shl day)) != 0
    fun minutesFor(day: Int) = dayMinutes[day] ?: minutes
    val selected: List<Int> get() = (0..6).filter { has(it) }
}

object Repeat {
    const val ALL = 0b1111111
    const val WEEKDAYS = 0b0011111
    val shortNames = listOf("Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom")
    val letters = listOf("L", "M", "M", "J", "V", "S", "D")
    val longNames = listOf("lunes", "martes", "miércoles", "jueves", "viernes", "sábado", "domingo")

    private val zone: ZoneId get() = ZoneId.systemDefault()

    fun minutesOf(millis: Long): Int {
        val t = Instant.ofEpochMilli(millis).atZone(zone).toLocalTime()
        return t.hour * 60 + t.minute
    }

    fun timeText(minutes: Int): String {
        val h = minutes / 60; val m = minutes % 60
        val h12 = if (h % 12 == 0) 12 else h % 12
        return "%d:%02d %s".format(h12, m, if (h < 12) "a. m." else "p. m.")
    }

    /** Próxima vez que suena después de [after]. Null si ya pasó la fecha final. */
    fun next(rule: RepeatRule, after: Long): Long? {
        if ((rule.days and ALL) == 0) return null
        val start = Instant.ofEpochMilli(after).atZone(zone).toLocalDate()
        val last = if (rule.until > 0) Instant.ofEpochMilli(rule.until).atZone(zone).toLocalDate() else null
        for (i in 0..7) {
            val date = start.plusDays(i.toLong())
            if (last != null && date.isAfter(last)) return null
            val day = date.dayOfWeek.value - 1
            if (!rule.has(day)) continue
            val m = rule.minutesFor(day).coerceIn(0, 24 * 60 - 1)
            val at = LocalDateTime.of(date, LocalTime.of(m / 60, m % 60)).atZone(zone).toInstant().toEpochMilli()
            if (at > after) return at
        }
        return null
    }

    /**
     * Se cumplió (apagó, envió, llamó o marcó como hecho): un aviso que se repite pasa a la próxima fecha.
     * Si ya no quedan fechas, queda como hecho.
     */
    fun advance(task: AgendaTask, now: Long = System.currentTimeMillis()): AgendaTask {
        val rule = task.repeat ?: return task.copy(completed = true)
        // Si lo hace antes de la hora (o lo había pospuesto), cuenta como la vez actual
        val from = maxOf(now, task.dateTimeMillis)
        val nextAt = next(rule, from) ?: return task.copy(completed = true)
        return task.copy(dateTimeMillis = nextAt, completed = false, eventMillis = eventFor(task, nextAt))
    }

    /** Un aviso que se repite y quedó atrasado (no lo atendió) salta a la próxima fecha sin perder la repetición. */
    fun catchUp(task: AgendaTask, now: Long = System.currentTimeMillis()): AgendaTask {
        val rule = task.repeat ?: return task
        if (task.completed || task.dateTimeMillis > now - MISSED_AFTER_MS) return task
        val nextAt = next(rule, now) ?: return task.copy(completed = true)
        return task.copy(dateTimeMillis = nextAt, eventMillis = eventFor(task, nextAt))
    }

    /** La reunión va [AgendaTask.leadMinutes] después del aviso. */
    private fun eventFor(task: AgendaTask, alarmAt: Long): Long = if (task.eventMillis > 0) alarmAt + task.leadMinutes * 60_000L else 0L

    /** Pasada una hora sin atenderlo, se da por perdido y se programa el siguiente. */
    const val MISSED_AFTER_MS = 60 * 60_000L

    /** "Lun, Mar, Mié · 8:00 a. m." o "Todos los días · 7:00 a. m." */
    fun label(rule: RepeatRule): String {
        val daysText = when (rule.days and ALL) {
            ALL -> "Todos los días"
            WEEKDAYS -> "Lun a Vie"
            0b1100000 -> "Fines de semana"
            else -> rule.selected.joinToString(", ") { shortNames[it] }
        }
        val untilText = if (rule.until > 0) " · hasta el ${formatDate(rule.until, "d MMM")}" else ""
        val times = rule.selected.map { rule.minutesFor(it) }.distinct()
        return if (times.size > 1) {
            rule.selected.joinToString(", ") { "${shortNames[it]} ${timeText(rule.minutesFor(it))}" } + untilText
        } else "$daysText · ${timeText(times.firstOrNull() ?: rule.minutes)}$untilText"
    }

    /** Para decirlo en voz alta: "los lunes, martes y miércoles a las 8:00 a. m." */
    fun spoken(rule: RepeatRule): String {
        val names = rule.selected.map { longNames[it] }
        val days = when (rule.days and ALL) {
            ALL -> "todos los días"
            WEEKDAYS -> "de lunes a viernes"
            else -> "los " + if (names.size > 1) names.dropLast(1).joinToString(", ") + " y " + names.last() else names.joinToString()
        }
        val sameTime = rule.selected.map { rule.minutesFor(it) }.distinct().size <= 1
        return if (sameTime) "$days a las ${timeText(rule.minutesFor(rule.selected.firstOrNull() ?: 0))}"
        else days + ", cada uno a su hora"
    }

    // ------------------------------------------------------------- guardado

    fun toJson(rule: RepeatRule) = JSONObject().apply {
        put("days", rule.days)
        put("minutes", rule.minutes)
        put("dayMinutes", rule.dayMinutes.entries.joinToString(",") { "${it.key}:${it.value}" })
        put("until", rule.until)
    }

    fun fromJson(o: JSONObject?): RepeatRule? {
        if (o == null) return null
        val days = o.optInt("days", 0)
        if ((days and ALL) == 0) return null
        val map = o.optString("dayMinutes").split(",").mapNotNull { part ->
            val (k, v) = part.split(":").takeIf { it.size == 2 } ?: return@mapNotNull null
            val day = k.trim().toIntOrNull() ?: return@mapNotNull null
            val min = v.trim().toIntOrNull() ?: return@mapNotNull null
            day to min
        }.toMap()
        return RepeatRule(days, o.optInt("minutes", 9 * 60), map, o.optLong("until", 0L))
    }
}

/**
 * Entiende la repetición en una frase dictada:
 * "todos los días", "diario", "de lunes a viernes", "entre semana", "los fines de semana",
 * "todos los lunes", "lunes, martes y miércoles", "cada martes", "lunes a las 8 y miércoles a las 10",
 * "hasta el 30 de noviembre".
 * La hora va en [RepeatRule.minutes] = -1 cuando no se dijo (se toma la del aviso).
 */
object RepeatParser {
    private val dayWords = listOf(
        "lunes" to 0, "martes" to 1, "miercoles" to 2, "jueves" to 3, "viernes" to 4, "sabado" to 5, "domingo" to 6,
    )
    private val months = listOf(
        "enero", "febrero", "marzo", "abril", "mayo", "junio", "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre",
    )
    private val dayRegex = Regex("\\b(lunes|martes|miercoles|jueves|viernes|sabado|domingo)s?\\b")
    private val timeRegex = Regex("a\\s+las?\\s+(\\d{1,2})(?::(\\d{2}))?(\\s+y\\s+(media|cuarto))?(\\s+(de|en|por)\\s+la\\s+(manana|tarde|noche))?")

    fun parse(input: String): RepeatRule? {
        val text = SmartNoteOrganizer.normalize(input)
        val days: Int = when {
            Regex("\\b(todos los dias|cada dia|diario|diariamente|a diario|todas las (mananas|tardes|noches))\\b").containsMatchIn(text) -> Repeat.ALL
            Regex("\\b(de lunes a viernes|entre semana|dias de semana|dias habiles|dias laborables)\\b").containsMatchIn(text) -> Repeat.WEEKDAYS
            Regex("\\b(fines? de semana|sabados y domingos)\\b").containsMatchIn(text) -> 0b1100000
            else -> {
                // Varios días nombrados, o uno solo con "todos los / cada / los" (plural): se repite
                val found = dayRegex.findAll(text).map { m -> dayWords.first { it.first == m.groupValues[1] }.second }.distinct().toList()
                val explicit = Regex("\\b(todos los|cada|los)\\s+(lunes|martes|miercoles|jueves|viernes|sabados?|domingos?)\\b").containsMatchIn(text) ||
                    Regex("\\b(semanal|cada semana|todas las semanas)\\b").containsMatchIn(text)
                // "de lunes a jueves"
                val range = Regex("\\bde\\s+(lunes|martes|miercoles|jueves|viernes|sabado|domingo)\\s+a\\s+(lunes|martes|miercoles|jueves|viernes|sabado|domingo)\\b").find(text)
                when {
                    range != null -> {
                        val a = dayWords.first { it.first == range.groupValues[1] }.second
                        val b = dayWords.first { it.first == range.groupValues[2] }.second
                        var bits = 0; var d = a
                        while (true) { bits = bits or (1 shl d); if (d == b) break; d = (d + 1) % 7 }
                        bits
                    }
                    found.size >= 2 || (found.size == 1 && explicit) -> found.fold(0) { acc, d -> acc or (1 shl d) }
                    else -> return null
                }
            }
        }

        // Hora por día: "lunes a las 8 y miércoles a las 10"
        val perDay = mutableMapOf<Int, Int>()
        dayRegex.findAll(text).forEach { m ->
            val day = dayWords.first { it.first == m.groupValues[1] }.second
            val rest = text.substring(m.range.last + 1).take(40)
            val t = timeRegex.find(rest)
            // la hora es de este día solo si viene antes del siguiente día nombrado
            if (t != null && !dayRegex.containsMatchIn(rest.substring(0, t.range.first))) toMinutes(t)?.let { perDay[day] = it }
        }
        val single = timeRegex.find(text)?.let { toMinutes(it) }
        val base = perDay.values.firstOrNull() ?: single ?: -1
        val distinct = perDay.values.distinct().size > 1
        val dayMinutes = if (distinct) perDay.filterKeys { (days and (1 shl it)) != 0 } else emptyMap()
        return RepeatRule(days, base, dayMinutes, parseUntil(text))
    }

    private fun toMinutes(m: MatchResult): Int? {
        var h = m.groupValues[1].toIntOrNull() ?: return null
        val min = when (m.groupValues[4]) { "media" -> 30; "cuarto" -> 15; else -> m.groupValues[2].toIntOrNull() ?: 0 }
        when (m.groupValues[7]) {
            "tarde", "noche" -> if (h in 1..11) h += 12
            "manana" -> if (h == 12) h = 0
            else -> if (h in 1..6) h += 12 // "a las 3" casi siempre es de la tarde
        }
        if (h !in 0..23 || min !in 0..59) return null
        return h * 60 + min
    }

    /** "hasta el 30 de noviembre", "hasta el viernes", "hasta fin de mes", "durante 2 semanas". */
    private fun parseUntil(text: String): Long {
        val today = LocalDate.now()
        val date: LocalDate? = Regex("hasta (el )?(\\d{1,2}) de (${months.joinToString("|")})(?: de (\\d{4}))?").find(text)?.let { m ->
            val day = m.groupValues[2].toInt(); val month = months.indexOf(m.groupValues[3]) + 1
            val year = m.groupValues[4].toIntOrNull()
            runCatching {
                var d = LocalDate.of(year ?: today.year, month, day)
                if (year == null && d.isBefore(today)) d = d.plusYears(1)
                d
            }.getOrNull()
        } ?: Regex("hasta (el )?(lunes|martes|miercoles|jueves|viernes|sabado|domingo)\\b").find(text)?.let { m ->
            val dow = DayOfWeek.of(dayWords.first { it.first == m.groupValues[2] }.second + 1)
            today.with(TemporalAdjusters.next(dow))
        } ?: if (Regex("hasta (el )?fin(al)? de mes").containsMatchIn(text)) today.with(TemporalAdjusters.lastDayOfMonth()) else null
            ?: Regex("(durante|por) (\\d{1,2}) semanas?").find(text)?.let { today.plusWeeks(it.groupValues[2].toLong()) }
            ?: Regex("(durante|por) (\\d{1,2}) mes(es)?").find(text)?.let { today.plusMonths(it.groupValues[2].toLong()) }
        return date?.atStartOfDay(ZoneId.systemDefault())?.toInstant()?.toEpochMilli() ?: 0L
    }

    private const val DAY = "(?:lunes|martes|mi[ée]rcoles|jueves|viernes|s[áa]bados?|domingos?)"
    private val phrasesRegex = Regex(
        "(?i)\\b(?:todos los d[ií]as|cada d[ií]a|a diario|diariamente|diario|de lunes a viernes|entre semana|(?:los )?fines? de semana|" +
            "hasta (?:el )?\\d{1,2} de \\p{L}+(?: de \\d{4})?|hasta (?:el )?fin(?:al)? de mes|hasta (?:el )?$DAY|(?:durante|por) \\d{1,2} (?:semanas?|mes(?:es)?))\\b",
    )
    private val daysRegex = Regex("(?i)\\b(?:todos los |cada |los |el |de )?$DAY(?:\\s*(?:,|y|a)\\s*(?:los |el )?$DAY)*\\b")

    /** "Todos los lunes y martes gimnasio" → "Gimnasio". */
    fun stripFromTitle(title: String): String {
        var t = phrasesRegex.replace(title, " ")
        t = daysRegex.replace(t, " ")
        t = Regex("\\s+").replace(t, " ").trim(' ', ',', '.', '-', ':')
        return t.replaceFirstChar { it.uppercase() }
    }

    /** Completa la hora con la del evento ([fallbackMillis]) si no se dijo, y corre el aviso [leadMinutes] antes (reuniones, viajes). */
    fun resolve(rule: RepeatRule, fallbackMillis: Long, leadMinutes: Int = 0): RepeatRule {
        val base = if (rule.minutes < 0) Repeat.minutesOf(fallbackMillis) else rule.minutes
        fun shift(m: Int) = (m - leadMinutes).coerceIn(0, 24 * 60 - 1)
        return rule.copy(minutes = shift(base), dayMinutes = rule.dayMinutes.mapValues { shift(it.value) })
    }
}
