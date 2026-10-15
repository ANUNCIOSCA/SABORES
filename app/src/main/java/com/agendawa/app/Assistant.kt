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
    /** Si se repite ("todos los lunes", "de lunes a viernes"…). Ya trae la hora del aviso. */
    val repeat: RepeatRule? = null,
    /** WhatsApp para un grupo: su nombre como se dijo ("ventas", "familia"). Vacío = a una persona. */
    val groupName: String = "",
    val toGroup: Boolean = false,
    /** Temas a tratar ("los temas a tratar son: presupuesto, entrega..."). */
    val topics: List<String> = emptyList(),
) {
    /** Texto de detalles que se guarda en el recordatorio (la hora va aparte, para poder editarla). */
    fun details(): String = buildList {
        if (location.isNotBlank()) add("Lugar: $location")
        if (message.isNotBlank()) add(message)
    }.joinToString("\n")

    /** Minutos entre el aviso y la reunión (0 si no hay evento aparte). */
    val leadMinutes: Int get() = if (eventMillis > 0) ((eventMillis - dateTimeMillis) / 60_000L).toInt().coerceAtLeast(0) else 0
    val topicsText: String get() = topics.joinToString("\n")
}

/**
 * Temas a tratar dichos por voz: "los temas a tratar son: el presupuesto, la entrega y el pago",
 * "puntos: uno..., dos...", o en reuniones "para hablar de...".
 */
object TopicParser {
    private val start = Regex(
        "(?i)[,.;]?\\s*(?:y\\s+)?(?:(?:los|las|el|la)\\s+)?(?:temas?|puntos?|asuntos?)" +
            "(?:\\s+(?:a|que\\s+(?:vamos\\s+a|hay\\s+que|debo|quiero))\\s+(?:tratar|hablar|ver|revisar|conversar))?" +
            "\\s*(?:son|serían|serian|es|será|sera|:)\\s*:?\\s*",
    )
    private val talk = Regex("(?i)\\s+para\\s+(?:hablar|tratar|conversar|ver|revisar|discutir)\\s+(?:de\\s+|sobre\\s+)?")
    private val separators = Regex(
        "(?i)\\s*(?:[,;\\n•]|\\bpunto\\s+(?:uno|dos|tres|cuatro|cinco|seis|\\d+)\\b|\\b(?:primero|segundo|tercero|cuarto|quinto|luego|después|despues|también|tambien|además|ademas)\\b|\\s+y\\s+)\\s*",
    )

    fun items(s: String): List<String> = s.split(separators)
        .map { it.trim(' ', '.', ':', '-') }
        .filter { it.length > 1 }
        .map { it.replaceFirstChar { c -> c.uppercase() } }
        .take(10)

    /** Separa la frase en (lo demás, temas). */
    fun split(text: String, meeting: Boolean): Pair<String, List<String>> {
        start.find(text)?.let { m ->
            val list = items(text.substring(m.range.last + 1))
            if (list.isNotEmpty()) return text.substring(0, m.range.first).trim() to list
        }
        if (meeting) talk.find(text)?.let { m ->
            // "reunión con Carlos mañana a las 3 para hablar del presupuesto y la entrega"
            val rest = text.substring(m.range.last + 1)
            val cut = Regex("(?i)\\s+(?:a\\s+las?\\s+\\d|mañana|hoy|pasado|el\\s+(?:lunes|martes|miércoles|miercoles|jueves|viernes|sábado|sabado|domingo))").find(rest)
            val topicsPart = if (cut != null) rest.substring(0, cut.range.first) else rest
            val tail = if (cut != null) rest.substring(cut.range.first) else ""
            val list = items(topicsPart.removePrefix("el ").removePrefix("la "))
            if (list.isNotEmpty()) return (text.substring(0, m.range.first) + " " + tail).trim() to list
        }
        return text to emptyList()
    }
}

/** Añade la repetición dicha en [phrase] (si la hay). Ideas y notas no se repiten. */
fun VoiceIntent.withRepeat(phrase: String): VoiceIntent {
    if (kind == VoiceIntentKind.IDEA || kind == VoiceIntentKind.NOTE || phrase.isBlank()) return this
    val rule = RepeatParser.parse(phrase) ?: return this
    val lead = if (eventMillis > 0) ((eventMillis - dateTimeMillis) / 60_000).toInt().coerceIn(0, 12 * 60) else 0
    val resolved = RepeatParser.resolve(rule, if (eventMillis > 0) eventMillis else dateTimeMillis, lead)
    val first = Repeat.next(resolved, System.currentTimeMillis()) ?: return this
    // Sin IA, el título trae "todos los lunes…": se limpia
    val cleanTitle = if (byAi) title else RepeatParser.stripFromTitle(title).ifBlank { title }
    return copy(title = cleanTitle, repeat = resolved, dateTimeMillis = first, eventMillis = if (eventMillis > 0) first + lead * 60_000L else 0L)
}

/** Avisos previos por defecto para reuniones y viajes. */
private const val MEETING_LEAD_MIN = 30L
private const val TRAVEL_LEAD_MIN = 120L

/** Interpretación sin internet, con reglas. Se usa si no hay IA o si la IA falla. */
object LocalIntentParser {
    // "Fuertes": si la frase empieza así es una idea aunque mencione a alguien
    private val ideaStarts = listOf("tengo una idea", "se me ocurrio", "se me ocurre", "idea:", "una idea", "anota esta idea", "mi idea", "idea para", "idea de")
    private val ideaWords = listOf("tengo una idea", "se me ocurrio", "se me ocurre", "anota esta idea")
    private val noteWords = listOf("anota", "apunta", "toma nota", "guarda una nota", "nota:")
    private val callWords = listOf("llamar", "llamarle", "llamale", "llama a", "llamada")
    private val groupWords = listOf("al grupo", "en el grupo", "a los grupos", "por el grupo", "pal grupo", "para el grupo")
    private val groupRegex = Regex(
        "(?i)\\bgrupos?\\s+(?:de\\s+(?:la\\s+|los\\s+|las\\s+)?|del\\s+)?(.+?)(?=\\s+(?:que|para|diciendo|dile|a las?|hoy|mañana|pasado|el (?:lunes|martes|miércoles|miercoles|jueves|viernes|sábado|sabado|domingo)|todos|cada|de lunes|los (?:lunes|martes|miércoles|miercoles|jueves|viernes|sábados|sabados|domingos))\\b|[,.:]|$)",
    )
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

    fun parse(text: String): VoiceIntent = parseOnce(text).withRepeat(text)

    /** Primero se separan los temas a tratar, para que no se confundan con el lugar o el mensaje. */
    private fun parseOnce(text: String): VoiceIntent {
        val first = parseCore(text)
        if (first.kind == VoiceIntentKind.IDEA || first.kind == VoiceIntentKind.NOTE || first.kind == VoiceIntentKind.WHATSAPP) return first
        val (core, topics) = TopicParser.split(text, first.kind == VoiceIntentKind.MEETING)
        if (topics.isEmpty()) return first
        return parseCore(core.ifBlank { text }).copy(topics = topics)
    }

    private fun parseCore(text: String): VoiceIntent {
        val norm = SmartNoteOrganizer.normalize(text)
        val base = VoiceParser.parse(text)
        val kind = when {
            ideaStarts.any { norm.startsWith(it) } -> VoiceIntentKind.IDEA
            // Llamar/escribir pesan más que "idea" en medio de la frase ("escríbele a Carlos sobre la idea de...")
            groupWords.any { norm.contains(it) } -> VoiceIntentKind.WHATSAPP
            callWords.any { norm.contains(it) } -> VoiceIntentKind.CALL
            writeWords.any { norm.contains(it) } -> VoiceIntentKind.WHATSAPP
            ideaWords.any { norm.contains(it) } -> VoiceIntentKind.IDEA
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

        // WhatsApp para un grupo ("mándale al grupo de ventas que…")
        if (kind == VoiceIntentKind.WHATSAPP && groupWords.any { norm.contains(it) }) {
            val g = groupRegex.find(text)
            val name = g?.groupValues?.get(1)?.trim()?.replaceFirstChar { it.uppercase() }.orEmpty()
            val after = g?.let { text.substring(it.range.last + 1) } ?: text
            val msg = messageRegex.find(after)?.groupValues?.get(1)?.trim()?.replaceFirstChar { it.uppercase() }.orEmpty()
            return VoiceIntent(
                kind,
                title = if (name.isNotBlank()) "WhatsApp al grupo $name" else "WhatsApp al grupo",
                groupName = name,
                toGroup = true,
                message = msg,
                dateTimeMillis = base.dateTimeMillis,
            )
        }

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
            - "grupo": SOLO si el whatsapp es para un GRUPO ("mándale al grupo de ventas...", "escribe en el grupo de la
              familia..."): el nombre del grupo sin "grupo de" ("Ventas", "Familia"). Entonces "persona" va vacío y el
              "mensaje" va dirigido a todos ("Buenos días a todos, ..."). Vacío si es para una persona.
            - "temas": en reunión o llamada, la lista de temas o puntos a tratar que dijo ("los temas a tratar son…",
              "para hablar de…"), cada uno corto y claro, por ejemplo ["Presupuesto 2025", "Fecha de entrega"]. Lista vacía si no dijo.
              No repitas los temas dentro de "mensaje".
            - "repetir": SOLO si dijo que se repite. Copia sus palabras de días, horas y hasta cuándo, por ejemplo
              "lunes a las 8, martes a las 10 y miércoles a las 8", "de lunes a viernes a las 7", "todos los días a las 9
              hasta el 30 de noviembre", "todos los sábados a las 10". Usa los nombres de los días. Vacío si es una sola vez.
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
                groupName = o.optString("grupo").trim(),
                topics = o.optJSONArray("temas")?.let { a -> (0 until a.length()).map { a.optString(it).trim() }.filter { it.isNotBlank() } }.orEmpty(),
                toGroup = kind == VoiceIntentKind.WHATSAPP && o.optString("grupo").isNotBlank(),
                dateTimeMillis = (event - lead * 60_000).coerceAtLeast(minFuture),
                eventMillis = if (isEvent || lead > 0) event else 0L,
                byAi = true,
            ).let { vi ->
                // La repetición la dice la IA en "repetir"; si solo hay una acción, también vale la frase original
                val said = o.optString("repetir").trim()
                when {
                    said.isNotBlank() -> vi.withRepeat(said).let { if (it.repeat == null) vi.withRepeat("todos los $said") else it }
                    arr.length() == 1 -> vi.withRepeat(text)
                    else -> vi
                }
            }
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
