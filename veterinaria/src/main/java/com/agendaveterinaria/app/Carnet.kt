package com.agendaveterinaria.app

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File

/**
 * Carnet de vacunación y boleta de control en PDF (tamaño media carta),
 * listo para enviar por WhatsApp, imprimir o guardar.
 */
object Carnet {
    private const val W = 396   // 5,5 pulgadas
    private const val H = 612   // 8,5 pulgadas
    private const val M = 24f
    private val GREEN = Color.rgb(14, 138, 110)
    private val GREY = Color.rgb(90, 90, 90)

    fun fileName(p: Patient) = "carnet-" + p.name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifBlank { "paciente" } +
        "-" + fmt(System.currentTimeMillis(), "yyyyMMdd") + ".pdf"

    /** Crea el PDF en la memoria temporal de la app y lo devuelve. */
    fun build(context: Context, state: VetState, p: Patient): File {
        val s = state.settings
        val owner = state.ownerOf(p)
        val doses = state.dosesOf(p.id).sortedBy { it.dateMillis }
        val vaccines = doses.filter { it.kind == DoseKind.VACUNA }
        val deworm = doses.filter { it.kind != DoseKind.VACUNA }
        val visits = state.visitsOf(p.id).take(5)
        val next = state.remindersOf(p.id).filter { !it.done && it.dueMillis >= startOfDay(System.currentTimeMillis()) }.take(4)
        val weight = state.lastWeight(p.id)

        val doc = PdfDocument()
        var pageNo = 0
        lateinit var page: PdfDocument.Page
        lateinit var c: Canvas
        var y = 0f

        val title = Paint().apply { color = GREEN; textSize = 15f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); isAntiAlias = true }
        val h2 = Paint().apply { color = Color.WHITE; textSize = 9.5f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); isAntiAlias = true }
        val bold = Paint().apply { color = Color.BLACK; textSize = 8.5f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); isAntiAlias = true }
        val text = Paint().apply { color = Color.BLACK; textSize = 8.5f; isAntiAlias = true }
        val small = Paint().apply { color = GREY; textSize = 7.5f; isAntiAlias = true }
        val line = Paint().apply { color = Color.rgb(200, 220, 214); strokeWidth = 0.7f }
        val band = Paint().apply { color = GREEN }

        fun newPage() {
            if (pageNo > 0) doc.finishPage(page)
            pageNo++
            page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, pageNo).create())
            c = page.canvas
            y = M
            if (pageNo > 1) { c.drawText("${p.name} · continuación", M, y + 8, small); y += 18 }
        }
        fun ensure(space: Float) { if (y + space > H - M - 30) newPage() }
        fun clip(s: String, paint: Paint, maxW: Float): String {
            if (paint.measureText(s) <= maxW) return s
            var t = s
            while (t.isNotEmpty() && paint.measureText("$t…") > maxW) t = t.dropLast(1)
            return "$t…"
        }
        /** Parte un texto largo en renglones que caben en el ancho. */
        fun wrap(s: String, paint: Paint, maxW: Float): List<String> {
            val out = ArrayList<String>()
            s.split("\n").forEach { para ->
                var cur = ""
                para.split(" ").forEach { w ->
                    val t = if (cur.isEmpty()) w else "$cur $w"
                    if (paint.measureText(t) <= maxW) cur = t else { if (cur.isNotEmpty()) out += cur; cur = w }
                }
                out += cur
            }
            return out
        }
        fun section(name: String) {
            ensure(30f)
            y += 6
            c.drawRect(M, y, W - M, y + 14, band)
            c.drawText(name.uppercase(), M + 5, y + 10, h2)
            y += 20
        }
        fun field(label: String, value: String, x: Float, width: Float) {
            c.drawText(label, x, y, small)
            c.drawText(clip(value.ifBlank { "—" }, text, width), x, y + 10, text)
        }
        fun table(headers: List<String>, widths: List<Float>, rows: List<List<String>>, empty: String) {
            ensure(16f)
            var x = M
            headers.forEachIndexed { i, h -> c.drawText(h, x + 2, y, bold); x += widths[i] }
            y += 4; c.drawLine(M, y, W - M, y, line); y += 10
            if (rows.isEmpty()) { c.drawText(empty, M + 2, y, small); y += 12; return }
            rows.forEach { r ->
                ensure(13f)
                x = M
                r.forEachIndexed { i, v -> c.drawText(clip(v, text, widths[i] - 4), x + 2, y, text); x += widths[i] }
                y += 3; c.drawLine(M, y, W - M, y, line); y += 10
            }
        }

        newPage()
        // Encabezado del consultorio
        c.drawText(clip(s.clinicName.ifBlank { "Consultorio veterinario" }, title, W - 2 * M), M, y + 12, title)
        y += 24
        listOf(
            listOf(s.vetName, s.license.takeIf { it.isNotBlank() }?.let { "Reg. $it" }).filterNotNull().filter { it.isNotBlank() }.joinToString(" · "),
            listOf(s.phone, s.address).filter { it.isNotBlank() }.joinToString(" · "),
        ).filter { it.isNotBlank() }.forEach { c.drawText(clip(it, small, W - 2 * M), M, y, small); y += 10 }
        y += 4
        c.drawRect(M, y, W - M, y + 2, band)
        y += 16
        val docTitle = Paint(bold).apply { textSize = 11f; color = GREEN }
        c.drawText("CARNET DE VACUNACIÓN Y CONTROL", M, y, docTitle)
        c.drawText("Emitido: ${fmtDate(System.currentTimeMillis())}", W - M - small.measureText("Emitido: 00/00/0000"), y, small)
        y += 10

        section("Paciente")
        val col = (W - 2 * M) / 3
        field("Nombre", p.name, M, col - 6); field("Especie", p.species, M + col, col - 6); field("Raza", p.breed, M + 2 * col, col - 6); y += 24
        field("Sexo", p.sex + if (p.sterilized) " (esterilizado/a)" else "", M, col - 6)
        field("Nacimiento", if (p.birthMillis > 0) "${fmtDate(p.birthMillis)} (${ageText(p.birthMillis)})" else "", M + col, col * 2 - 6); y += 24
        field("Color / señas", p.color, M, col - 6); field("Chip / arete", p.chip, M + col, col - 6)
        field("Peso actual", weight?.let { "${num(it)} kg" } ?: "", M + 2 * col, col - 6); y += 24
        if (p.allergies.isNotBlank()) { field("Alergias / alertas", p.allergies, M, W - 2 * M); y += 24 }

        section("Propietario")
        field("Nombre", owner?.name ?: "", M, col * 2 - 6); field("Teléfono", owner?.phone ?: "", M + 2 * col, col - 6); y += 24
        if (owner?.address?.isNotBlank() == true) { field("Dirección", owner.address, M, W - 2 * M); y += 24 }

        val wTable = listOf(58f, 150f, 62f, 78f)
        section("Vacunas")
        table(
            listOf("Fecha", "Vacuna", "Lote", "Próxima"), wTable,
            vaccines.map { listOf(fmtDate(it.dateMillis), it.product, it.lot, if (it.nextMillis > 0) fmtDate(it.nextMillis) else "—") },
            "Sin vacunas registradas.",
        )
        section("Desparasitaciones")
        table(
            listOf("Fecha", "Producto", "Tipo", "Próxima"), wTable,
            deworm.map { listOf(fmtDate(it.dateMillis), it.product, if (it.kind == DoseKind.DESPARASITACION_INT) "Interna" else "Externa", if (it.nextMillis > 0) fmtDate(it.nextMillis) else "—") },
            "Sin desparasitaciones registradas.",
        )
        section("Últimos controles")
        if (visits.isEmpty()) { c.drawText("Sin consultas registradas.", M + 2, y, small); y += 12 }
        visits.forEach { v ->
            val head = "${fmtDate(v.dateMillis)} · ${v.type.label}" + (v.weightKg?.let { " · ${num(it)} kg" } ?: "") + (v.temperature?.let { " · ${num(it, 1)} °C" } ?: "")
            ensure(24f)
            c.drawText(head, M + 2, y, bold); y += 10
            val body = listOf(
                v.reason.takeIf { it.isNotBlank() }?.let { "Motivo: $it" },
                v.diagnosis.takeIf { it.isNotBlank() }?.let { "Dx: $it" },
                v.treatment.takeIf { it.isNotBlank() }?.let { "Tto: $it" },
                v.medications.takeIf { it.isNotEmpty() }?.joinToString("; ") { m -> listOf(m.name, m.dose, m.route, m.frequency).filter { it.isNotBlank() }.joinToString(" ") }?.let { "Medicamentos: $it" },
            ).filterNotNull().joinToString(" · ")
            wrap(body, text, W - 2 * M - 4).take(4).forEach { ensure(11f); c.drawText(it, M + 2, y, text); y += 10 }
            y += 4
        }
        if (next.isNotEmpty()) {
            section("Próximas citas y dosis")
            next.forEach { r -> ensure(12f); c.drawText(clip("${fmtNice(r.dueMillis)} — ${r.title}", text, W - 2 * M), M + 2, y, text); y += 12 }
        }
        // Firma
        ensure(50f)
        y = maxOf(y + 30, H - M - 40)
        c.drawLine(W - M - 150, y, W - M, y, Paint().apply { color = Color.DKGRAY; strokeWidth = 0.8f })
        c.drawText("Firma y sello del médico veterinario", W - M - 150, y + 10, small)
        doc.finishPage(page)

        val dir = File(context.cacheDir, "carnets").apply { mkdirs() }
        val f = File(dir, fileName(p))
        f.outputStream().use { doc.writeTo(it) }
        doc.close()
        return f
    }

    fun uri(context: Context, f: File): Uri = FileProvider.getUriForFile(context, context.packageName + ".files", f)

    /** Abre la lista para compartir (WhatsApp, correo, Drive, imprimir...). */
    fun share(context: Context, f: File, message: String) {
        val u = uri(context, f)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, u)
            putExtra(Intent.EXTRA_TEXT, message)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Enviar carnet").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun open(context: Context, f: File) {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW).setDataAndType(uri(context, f), "application/pdf")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.onFailure { toast(context, "No hay una app para ver PDF. Usa Enviar o Guardar.") }
    }

    /** Guarda una copia en Descargas › AgendaVeterinaria. Devuelve dónde quedó. */
    fun saveToDownloads(context: Context, f: File): String {
        if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, f.name)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/AgendaVeterinaria")
            }
            val u = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: error("No se pudo crear el archivo")
            context.contentResolver.openOutputStream(u)?.use { out -> f.inputStream().use { it.copyTo(out) } }
            return "Descargas › AgendaVeterinaria › ${f.name}"
        }
        val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "carnets").apply { mkdirs() }
        val out = File(dir, f.name)
        f.copyTo(out, overwrite = true)
        return out.absolutePath
    }
}
