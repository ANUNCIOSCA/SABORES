package com.agendaveterinaria.app

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.Period
import java.time.ZoneId
import java.util.Calendar
import java.util.Date
import java.util.Locale

val ES = Locale("es", "VE")

fun fmt(millis: Long, pattern: String): String = SimpleDateFormat(pattern, ES).format(Date(millis))
fun fmtDate(millis: Long) = fmt(millis, "dd/MM/yyyy")
fun fmtDateTime(millis: Long) = fmt(millis, "dd/MM/yyyy h:mm a")
fun fmtNice(millis: Long) = fmt(millis, "EEE d MMM").replaceFirstChar { it.uppercase() }

fun startOfDay(millis: Long): Long = Calendar.getInstance().apply {
    timeInMillis = millis; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

fun atHour(dayMillis: Long, hour: Int, minute: Int = 0): Long = Calendar.getInstance().apply {
    timeInMillis = dayMillis; set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, minute); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

fun plusDays(millis: Long, days: Int): Long = millis + days * 86_400_000L

fun daysUntil(millis: Long): Int = ((startOfDay(millis) - startOfDay(System.currentTimeMillis())) / 86_400_000L).toInt()

/** "hoy", "mañana", "en 3 días", "hace 2 días". */
fun whenText(millis: Long): String = when (val d = daysUntil(millis)) {
    0 -> "hoy"
    1 -> "mañana"
    -1 -> "ayer"
    in 2..60 -> "en $d días"
    in -60..-2 -> "hace ${-d} días"
    else -> fmtDate(millis)
}

/** Edad legible: "3 años 2 meses", "5 meses", "3 semanas". */
fun ageText(birthMillis: Long): String {
    if (birthMillis <= 0) return ""
    val zone = ZoneId.systemDefault()
    val b = Instant.ofEpochMilli(birthMillis).atZone(zone).toLocalDate()
    val p = Period.between(b, LocalDate.now(zone))
    return when {
        p.years > 0 -> "${p.years} año${if (p.years == 1) "" else "s"}" + if (p.months > 0) " ${p.months} mes${if (p.months == 1) "" else "es"}" else ""
        p.months > 0 -> "${p.months} mes${if (p.months == 1) "" else "es"}"
        else -> "${(p.days / 7).coerceAtLeast(0)} semanas"
    }
}

/** Menor de un año (para usar el intervalo de cachorro). */
fun isYoung(birthMillis: Long): Boolean = birthMillis > 0 && System.currentTimeMillis() - birthMillis < 365L * 86_400_000L

fun money(amount: Double, currency: String): String =
    "$currency " + String.format(ES, "%,.2f", amount)

/** Lee números escritos con coma o punto ("4,5" o "4.5"). */
fun parseNum(s: String): Double? = s.trim().replace(" ", "").let {
    if (it.count { c -> c == ',' } == 1 && !it.contains('.')) it.replace(',', '.') else it.replace(",", "")
}.toDoubleOrNull()

fun num(v: Double, decimals: Int = 2): String {
    val s = String.format(ES, "%.${decimals}f", v)
    return if (s.contains(',')) s.trimEnd('0').trimEnd(',') else s
}

fun toast(context: Context, text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()

fun pickDate(context: Context, initial: Long, onPicked: (Long) -> Unit) {
    val c = Calendar.getInstance().apply { timeInMillis = if (initial > 0) initial else System.currentTimeMillis() }
    DatePickerDialog(context, { _, y, m, d ->
        val r = Calendar.getInstance().apply { timeInMillis = c.timeInMillis; set(y, m, d) }
        onPicked(r.timeInMillis)
    }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
}

fun pickTime(context: Context, initial: Long, onPicked: (Long) -> Unit) {
    val c = Calendar.getInstance().apply { timeInMillis = initial }
    TimePickerDialog(context, { _, h, min ->
        onPicked(atHour(c.timeInMillis, h, min))
    }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), false).show()
}

// ---------------------------------------------------------------------------
// WhatsApp
// ---------------------------------------------------------------------------

object WhatsApp {
    /** Número para wa.me: solo dígitos; los números venezolanos que empiezan en 0 pasan a 58. */
    fun normalize(phone: String): String {
        var d = phone.filter { it.isDigit() }
        if (phone.trim().startsWith("+")) return d
        if (d.startsWith("00")) d = d.drop(2)
        if (d.length == 11 && d.startsWith("0")) d = "58" + d.drop(1)   // 0412... → 58412...
        if (d.length == 10 && d.startsWith("4")) d = "58$d"              // 412... → 58412...
        return d
    }

    fun open(context: Context, phone: String, text: String): Boolean {
        val digits = normalize(phone)
        val url = (if (digits.isNotEmpty()) "https://wa.me/$digits" else "https://wa.me/") + "?text=" + URLEncoder.encode(text, "UTF-8").replace("+", "%20")
        // Directo en la app de WhatsApp (chat abierto con el texto escrito); si no está, el enlace normal
        for (pkg in listOf("com.whatsapp", "com.whatsapp.w4b", null)) {
            val ok = runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply { if (pkg != null) setPackage(pkg) }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
            }.getOrDefault(false)
            if (ok) return true
        }
        return false
    }

    fun call(context: Context, phone: String) = runCatching {
        context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + phone.filter { it.isDigit() || it == '+' })).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
