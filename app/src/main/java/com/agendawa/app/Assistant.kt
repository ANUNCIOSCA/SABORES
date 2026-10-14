package com.agendawa.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// ---------------------------------------------------------------------------
// Qué quiso decir el usuario al hablar
// ---------------------------------------------------------------------------

enum class VoiceIntentKind { IDEA, NOTE, CALL, WHATSAPP, MEETING, TRAVEL, REMINDER }

data class VoiceIntent(
    val kind: VoiceIntentKind,
    val title: String,
    /** Idea o nota redactada (para IDEA / NOTE). */
    val content: String = "",
    /** Nombre de la persona o empresa a llamar o escribir, tal como se dijo. */
    val personName: String = "",
    /** Mensaje de WhatsApp listo para enviar, o detalles del recordatorio. */
    val message: String = "",
    /** Lugar de la reunión o destino del viaje. */
    val location: String = "",
    /** Cuándo suena el aviso (en reuniones y viajes, un rato antes). */
    val dateTimeMillis: Long,
    /** Hora real del evento (reunión o viaje). 0 si no aplica. */
    val eventMillis: Long = 0L,
    val byAi: Boolean = false,
) {
    /** Texto de detalles que se guarda en el recordatorio. */
    fun details(): String = buildList {
        if (eventMillis > 0) add("Hora: " + formatDate(eventMillis, "EEEE d 'de' MMMM, h:mm a").replaceFirstChar { it.uppercase() })
        if (location.isNotBlank()) add("Lugar: $location")
        if (message.isNotBlank()) add(message)
    }.joinToString("\n")
}

/** Avisos previos por defecto para reuniones y viajes. */
private const val MEETING_LEAD_MIN = 30L
private const val TRAVEL_LEAD_MIN = 120L

/** Interpretación sin internet, con reglas. Se usa si no hay IA o si la IA falla. */
object LocalIntentParser {
    private val ideaWords = listOf("tengo una idea", "se me ocurrio", "se me ocurre", "idea:", "una idea", "anota esta idea", "idea de")
    private val noteWords = listOf("anota", "apunta", "toma nota", "guarda una nota", "nota:")
    private val callWords = listOf("llamar", "llamarle", "llamale", "llama a", "llamada")
    private val writeWords = listOf("whatsapp", "wasap", "guasap", "escribir", "escribirle", "escribele", "mensaje", "decirle", "avisarle", "mandarle")
    private val meetingWords = listOf("reunion", "cita con", "cita en", "junta", "encuentro con", "entrevista", "consulta con", "me reuno", "reunirme")
    private val travelWords = listOf("viajar", "viaje", "vuelo", "salgo para", "salgo a", "me voy a", "irme a", "bus para", "pasaje")

    private val personRegex = Regex(
        "(?i:llamar(?:le)?|llama|ll[aá]male|escribir(?:le)?|escr[ií]bele|whatsapp|wasap|guasap|mensaje|decirle|avisarle|mandarle|reuni[oó]n con|cita con|reunirme con|encuentro con)" +
            "(?i:\\s+(?:un|una|el|al|a|le|la|los|las|por|whatsapp|mensaje|empresa|compañía|señor|señora|sr\\.?|sra\\.?|doctor|doctora|dr\\.?|dra\\.?))*\\s+(?i:a\\s+|al\\s+)?" +
            "(\\p{Lu}[\\p{L}.&]+(?:\\s+(?:de\\s+)?\\p{Lu}[\\p{L}.&]+)?)",
    )
    private val lowerPersonRegex = Regex(
        "(?i)(?:llamar(?:le)?|escribir(?:le)?|whatsapp|mensaje|decirle|avisarle|reuni[oó]n|cita)(?:\\s+(?:un|una|el|al|a|le|la))*\\s+(?:a|al|con)\\s+(\\p{L}+)",
    )
    private val messageRegex = Regex("(?i)\\b(?:que|diciendo que|para decirle que|dile que|para)\\s+(.+)$")
    private val placeRegex = Regex("(?i)\\b(?:en|en el|en la)\\s+((?:el|la|los|las)\\s+)?([\\p{L}0-9][\\p{L}0-9 .,'-]{2,60}?)(?=\\s+(?:a las?|hoy|mañana|pasado|el (?:lunes|martes|miércoles|miercoles|jueves|viernes|sábado|sabado|domingo)|con|para)\\b|[.,]|$)")
    private val destinationRegex = Regex("(?i)\\b(?:a|para|hacia)\\s+(\\p{Lu}[\\p{L}]+(?:\\s+\\p{Lu}[\\p{L}]+)*)")

    fun parse(text: String): VoiceIntent {
        val norm = SmartNoteOrganizer.normalize(text)
        val base = VoiceParser.parse(text)
        val kind = when {
            ideaWords.any { norm.contains(it) } -> VoiceIntentKind.IDEA
            callWords.any { norm.contains(it) } -> VoiceIntentKind.CALL
            writeWords.any { norm.contains(it) } -> VoiceIntentKind.WHATSAPP
            meetingWords.any { norm.contains(it) } -> VoiceIntentKind.MEETING
            travelWords.any { norm.contains(it) } -> VoiceIntentKind.TRAVEL
            noteWords.any { norm.startsWith(it) } -> VoiceIntentKind.NOTE
            else -> VoiceIntentKind.REMINDER
        }
        val personMatch = personRegex.find(text) ?: lowerPersonRegex.find(text)
        val person = personMatch?.groupValues?.get(1)?.trim()?.replaceFirstChar { it.uppercase() } ?: ""
        // El mensaje es lo que viene después del nombre ("...a Carlos que mañana le pago")
        val afterPerson = personMatch?.let { text.substring(it.range.last + 1) } ?: text
        val stopNames = setOf("Mañana", "Hoy", "Pasado", "Que", "Para", "En", "A")
        val cleanPerson = person.split(" ").takeWhile { it !in stopNames }.joinToString(" ")

        return when (kind) {
            VoiceIntentKind.IDEA, VoiceIntentKind.NOTE -> {
                val content = text.trim().replaceFirstChar { it.uppercase() }
                val org = SmartNoteOrganizer.organize(content)
                VoiceIntent(kind, title = org.title, content = content, dateTimeMillis = base.dateTimeMillis)
            }
            VoiceIntentKind.CALL -> VoiceIntent(
                kind,
                title = if (cleanPerson.isNotBlank()) "Llamar a $cleanPerson" else base.title,
                personName = cleanPerson,
                message = messageRegex.find(afterPerson)?.groupValues?.get(1)?.trim().orEmpty(),
                dateTimeMillis = base.dateTimeMillis,
            )
            VoiceIntentKind.WHATSAPP -> {
                val msg = messageRegex.find(afterPerson)?.groupValues?.get(1)?.trim()
                    ?.replaceFirstChar { it.uppercase() }.orEmpty()
                VoiceIntent(
                    kind,
                    title = if (cleanPerson.isNotBlank()) "Enviar WhatsApp a $cleanPerson" else "Enviar WhatsApp",
                    personName = cleanPerson,
                    message = msg,
                    dateTimeMillis = base.dateTimeMillis,
                )
            }
            VoiceIntentKind.MEETING -> {
                val place = placeRegex.find(text)?.let { (it.groupValues[1] + it.groupValues[2]).trim() }.orEmpty()
                val withWho = if (cleanPerson.isNotBlank()) " con $cleanPerson" else ""
                VoiceIntent(
                    kind,
                    title = "Reunión$withWho" + if (place.isNotBlank()) " en $place" else "",
                    personName = cleanPerson,
                    location = place,
                    dateTimeMillis = (base.dateTimeMillis - MEETING_LEAD_MIN * 60_000).coerceAtLeast(System.currentTimeMillis() + 60_000),
                    eventMillis = base.dateTimeMillis,
                )
            }
            VoiceIntentKind.TRAVEL -> {
                val dest = destinationRegex.find(text)?.groupValues?.get(1)?.trim().orEmpty()
                VoiceIntent(
                    kind,
                    title = if (dest.isNotBlank()) "Viaje a $dest" else "Viaje",
                    location = dest,
                    dateTimeMillis = (base.dateTimeMillis - TRAVEL_LEAD_MIN * 60_000).coerceAtLeast(System.currentTimeMillis() + 60_000),
                    eventMillis = base.dateTimeMillis,
                )
            }
            VoiceIntentKind.REMINDER -> VoiceIntent(kind, title = base.title, message = base.message, dateTimeMillis = base.dateTimeMillis)
        }
    }
}

/** Interpretación con IA (el proveedor elegido): entiende frases libres y redacta ideas y mensajes. */
object AiAssistant {
    private val isoFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")

    private fun parseTime(value: String): Long? = runCatching {
        LocalDateTime.parse(value.take(16), isoFormat).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }.getOrNull()

    /** Una frase puede traer varias cosas: "llamar a Carlos y escribirle a María". */
    fun parseVoice(context: Context, text: String): List<VoiceIntent> {
        val now = LocalDateTime.now()
        val today = now.format(DateTimeFormatter.ofPattern("EEEE d 'de' MMMM 'de' yyyy, HH:mm", Locale("es")))
        val user = ProfileStore.name(context).ifBlank { "el usuario" }
        val prompt = """
            Eres el asistente personal de una agenda en Android, en Venezuela. $user dictó por voz (puede tener errores de
            dictado, muletillas o nombres mal escritos; interprétalo con sentido común):
            "$text"

            Ahora es $today.
            Una sola frase puede traer VARIAS cosas por hacer. Responde SOLO un JSON así:
            {"acciones": [ { ...acción... }, ... ]}

            Cada acción tiene:
            - "tipo": uno de
                "idea"         → una idea, ocurrencia, negocio o proyecto ("tengo una idea...", "se me ocurrió...")
                "nota"         → algo para guardar sin hora ("anota que...", datos, listas)
                "llamada"      → debe llamar a una persona o empresa
                "whatsapp"     → debe escribirle, mandarle un mensaje o avisarle algo a una persona o empresa
                "reunion"      → reunión, cita, consulta médica, entrevista, encuentro en un sitio
                "viaje"        → viajar, vuelo, salir hacia otra ciudad
                "recordatorio" → cualquier otra cosa con fecha u hora (pagos, comprar algo, tomar medicina...)
            - "titulo": título corto y claro (máx. 60 caracteres), por ejemplo "Reunión con el contador en Banesco".
            - "contenido": solo idea o nota. Redáctala ordenada: resumen de 1-2 frases y luego viñetas con puntos clave
              y próximos pasos concretos. No inventes datos.
            - "persona": persona o empresa a llamar, escribir o con quien se reúne, tal como la dijo ("Carlos",
              "Carlos Pérez", "Farmatodo"). Sin artículos ni títulos ("la empresa", "el señor"). Vacío si no hay.
            - "mensaje": en whatsapp, el mensaje listo para enviar en primera persona, cálido, breve y natural en español
              venezolano (sin "Estimado"). Si no dijo qué escribir, déjalo vacío. En llamada: de qué hablar.
              En recordatorio, reunión o viaje: detalles útiles que dijo (qué llevar, número de vuelo...).
            - "lugar": sitio de la reunión o destino del viaje. Vacío si no se dijo.
            - "fecha_hora": cuándo ocurre (yyyy-MM-ddTHH:mm). "En la mañana" = 09:00, "al mediodía" = 12:00,
              "en la tarde" = 15:00, "en la noche" = 19:00. Si dijo "a las 3" sin aclarar, es 15:00.
              Si no dijo cuándo: dentro de 1 hora. Para idea o nota: vacío.
            - "aviso_minutos_antes": minutos antes de "fecha_hora" para avisar. 0 para llamada, whatsapp y recordatorio;
              30 para reunión; 120 para viaje (o lo que pida: "avísame una hora antes" = 60).
        """.trimIndent()

        val root = JSONObject(AiClient.extractJson(AiClient.generate(context, prompt, json = true)))
        val arr = root.optJSONArray("acciones") ?: org.json.JSONArray().put(root)
        val fallbackMillis = VoiceParser.parse(text).dateTimeMillis
        val minFuture = System.currentTimeMillis() + 60_000
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val kind = when (SmartNoteOrganizer.normalize(o.optString("tipo"))) {
                "idea" -> VoiceIntentKind.IDEA
                "nota" -> VoiceIntentKind.NOTE
                "llamada" -> VoiceIntentKind.CALL
                "whatsapp" -> VoiceIntentKind.WHATSAPP
                "reunion" -> VoiceIntentKind.MEETING
                "viaje" -> VoiceIntentKind.TRAVEL
                else -> VoiceIntentKind.REMINDER
            }
            val event = parseTime(o.optString("fecha_hora"))?.takeIf { it > System.currentTimeMillis() } ?: fallbackMillis
            val lead = o.optLong("aviso_minutos_antes", 0L).coerceIn(0L, 7 * 24 * 60L)
            val isEvent = kind == VoiceIntentKind.MEETING || kind == VoiceIntentKind.TRAVEL
            VoiceIntent(
                kind = kind,
                title = o.optString("titulo").ifBlank { text.take(60) },
                content = o.optString("contenido"),
                personName = o.optString("persona").trim(),
                message = o.optString("mensaje").trim(),
                location = o.optString("lugar").trim(),
                dateTimeMillis = (event - lead * 60_000).coerceAtLeast(minFuture),
                eventMillis = if (isEvent || lead > 0) event else 0L,
                byAi = true,
            )
        }.ifEmpty { listOf(LocalIntentParser.parse(text)) }
    }

    /** Redacta y ordena una idea o nota escrita a la carrera. */
    fun polishNote(context: Context, content: String): String = AiClient.generate(
        context,
        """
            Ordena y redacta esta idea o nota en español, clara y bien escrita. Empieza con un resumen de 1-2 frases y
            luego viñetas con los puntos clave y próximos pasos concretos. Conserva todos los datos, nombres y cifras.
            No inventes información. Devuelve solo el texto final, sin títulos tipo "Resumen:" ni comentarios.

            Nota original:
            $content
        """.trimIndent(),
        json = false,
    )

    /** Convierte lo que se quiere decir en un mensaje de WhatsApp listo para enviar. */
    fun polishWhatsApp(context: Context, message: String, personName: String): String = AiClient.generate(
        context,
        """
            Redacta un mensaje de WhatsApp listo para enviar${if (personName.isNotBlank()) " a $personName" else ""},
            en primera persona, cálido, breve y natural, en español venezolano. Respeta exactamente la intención y los datos.
            Devuelve solo el mensaje, sin comillas ni explicaciones.

            Lo que quiero decir:
            $message
        """.trimIndent(),
        json = false,
    )
}

// ---------------------------------------------------------------------------
// Búsqueda en los contactos del teléfono
// ---------------------------------------------------------------------------

object ContactSearch {
    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    /**
     * Contactos del teléfono y clientes de la agenda (por nombre o empresa) que coinciden con [name].
     * Sin duplicados por número.
     */
    fun find(context: Context, name: String, clients: List<Client> = emptyList()): List<SelectedPhoneContact> {
        val words = SmartNoteOrganizer.normalize(name).split(" ").filter { it.length >= 2 }
        if (words.isEmpty()) return emptyList()
        val result = linkedMapOf<String, SelectedPhoneContact>()
        clients.filter { it.phone.isNotBlank() }.forEach { cl ->
            val hay = SmartNoteOrganizer.normalize("${cl.name} ${cl.company}")
            if (words.all { hay.contains(it) }) {
                val digits = cl.phone.filter { it.isDigit() }.takeLast(10)
                val label = if (cl.company.isNotBlank()) "${cl.name} (${cl.company})" else cl.name
                if (digits.isNotEmpty() && digits !in result) result[digits] = SelectedPhoneContact(label, cl.phone)
            }
        }
        if (!hasPermission(context)) return result.values.toList()
        runCatching {
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ContactsContract.CommonDataKinds.Phone.NORMALIZED_NUMBER,
                ),
                // Se filtra aquí (y no con LIKE) para que "jose" encuentre a "José"
                null,
                null,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC",
            )?.use { c ->
                while (c.moveToNext()) {
                    val display = c.getString(0).orEmpty()
                    val normDisplay = SmartNoteOrganizer.normalize(display)
                    if (!words.all { normDisplay.contains(it) }) continue
                    val number = (c.getString(2) ?: c.getString(1)).orEmpty()
                    val digits = number.filter { it.isDigit() }.takeLast(10)
                    if (digits.isNotEmpty() && digits !in result) result[digits] = SelectedPhoneContact(display, number)
                }
            }
        }
        return result.values.toList()
    }
}
