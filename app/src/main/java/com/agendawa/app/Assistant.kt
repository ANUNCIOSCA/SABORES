package com.agendawa.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// ---------------------------------------------------------------------------
// Configuración de la IA (Gemini)
// ---------------------------------------------------------------------------

/**
 * La clave de Gemini se guarda solo en este teléfono (no va en las copias de seguridad).
 */
object AiSettings {
    private const val PREFS = "agenda_wa_ai"
    const val DEFAULT_MODEL = "gemini-2.5-flash"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun apiKey(context: Context): String = prefs(context).getString("api_key", "")?.trim().orEmpty()
    fun model(context: Context): String = prefs(context).getString("model", DEFAULT_MODEL)?.trim().orEmpty().ifBlank { DEFAULT_MODEL }
    fun isEnabled(context: Context): Boolean = apiKey(context).isNotBlank()

    fun save(context: Context, apiKey: String, model: String) {
        prefs(context).edit()
            .putString("api_key", apiKey.trim())
            .putString("model", model.trim().ifBlank { DEFAULT_MODEL })
            .apply()
    }
}

class AiException(message: String) : Exception(message)

/** Cliente mínimo de la API de Gemini (generateContent). Se llama fuera del hilo principal. */
object GeminiClient {

    fun generate(context: Context, prompt: String, json: Boolean): String {
        val key = AiSettings.apiKey(context)
        if (key.isBlank()) throw AiException("Falta la clave de Gemini en Configuración")
        val model = AiSettings.model(context)
        val body = JSONObject().apply {
            put("contents", JSONArray().put(JSONObject().apply {
                put("role", "user")
                put("parts", JSONArray().put(JSONObject().put("text", prompt)))
            }))
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.4)
                if (json) put("responseMimeType", "application/json")
            })
        }

        val conn = URL("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent")
            .openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 15_000
            conn.readTimeout = 45_000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.setRequestProperty("x-goog-api-key", key)
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

            val code = conn.responseCode
            val raw = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val apiMsg = runCatching { JSONObject(raw).getJSONObject("error").getString("message") }.getOrNull()
                throw AiException(
                    when (code) {
                        400, 401, 403 -> "Gemini rechazó la clave o la petición. Revisa la clave en Configuración." + (apiMsg?.let { "\n($it)" } ?: "")
                        404 -> "El modelo \"$model\" no existe. Cámbialo en Configuración."
                        429 -> "Se alcanzó el límite de uso de Gemini. Intenta en un rato."
                        else -> "Error de Gemini ($code)" + (apiMsg?.let { ": $it" } ?: "")
                    },
                )
            }
            val parts = JSONObject(raw).getJSONArray("candidates").getJSONObject(0)
                .getJSONObject("content").getJSONArray("parts")
            val text = buildString {
                for (i in 0 until parts.length()) {
                    val p = parts.getJSONObject(i)
                    if (!p.optBoolean("thought")) append(p.optString("text"))
                }
            }.trim()
            if (text.isBlank()) throw AiException("Gemini no devolvió respuesta")
            return text
        } catch (e: AiException) {
            throw e
        } catch (e: java.io.IOException) {
            throw AiException("Sin conexión a internet o Gemini no respondió.")
        } catch (e: Exception) {
            throw AiException("No se entendió la respuesta de Gemini.")
        } finally {
            conn.disconnect()
        }
    }

    /** Prueba rápida de la clave desde Configuración. */
    fun test(context: Context): String = generate(context, "Responde solo: OK", json = false)
}

// ---------------------------------------------------------------------------
// Qué quiso decir el usuario al hablar
// ---------------------------------------------------------------------------

enum class VoiceIntentKind { IDEA, NOTE, CALL, WHATSAPP, REMINDER }

data class VoiceIntent(
    val kind: VoiceIntentKind,
    val title: String,
    /** Idea o nota redactada (para IDEA / NOTE). */
    val content: String = "",
    /** Nombre de la persona a llamar o escribir, tal como se dijo. */
    val personName: String = "",
    /** Mensaje de WhatsApp listo para enviar. */
    val message: String = "",
    val dateTimeMillis: Long,
    val byAi: Boolean = false,
)

/** Interpretación sin internet, con reglas. Se usa si no hay IA o si la IA falla. */
object LocalIntentParser {
    private val ideaWords = listOf("tengo una idea", "se me ocurrio", "se me ocurre", "idea:", "una idea", "anota esta idea", "idea de")
    private val noteWords = listOf("anota", "apunta", "toma nota", "guarda una nota", "nota:")
    private val callWords = listOf("llamar", "llamarle", "llamale", "llama a", "llamada")
    private val writeWords = listOf("whatsapp", "wasap", "guasap", "escribir", "escribirle", "escribele", "mensaje", "decirle", "avisarle", "mandarle")

    private val personRegex = Regex(
        "(?i:llamar(?:le)?|llama|ll[aá]male|escribir(?:le)?|escr[ií]bele|whatsapp|wasap|guasap|mensaje|decirle|avisarle|mandarle)" +
            "(?i:\\s+(?:un|una|el|al|a|le|por|whatsapp|mensaje))*\\s+(?i:a\\s+|al\\s+)?" +
            "(\\p{Lu}\\p{L}+(?:\\s+\\p{Lu}\\p{L}+)?)",
    )
    private val lowerPersonRegex = Regex(
        "(?i)(?:llamar(?:le)?|escribir(?:le)?|whatsapp|mensaje|decirle|avisarle)(?:\\s+(?:un|una|el|al|a|le))*\\s+(?:a|al)\\s+(\\p{L}+)",
    )
    private val messageRegex = Regex("(?i)\\b(?:que|diciendo que|para decirle que|dile que)\\s+(.+)$")

    fun parse(text: String): VoiceIntent {
        val norm = SmartNoteOrganizer.normalize(text)
        val base = VoiceParser.parse(text)
        val kind = when {
            ideaWords.any { norm.contains(it) } -> VoiceIntentKind.IDEA
            callWords.any { norm.contains(it) } -> VoiceIntentKind.CALL
            writeWords.any { norm.contains(it) } -> VoiceIntentKind.WHATSAPP
            noteWords.any { norm.startsWith(it) } -> VoiceIntentKind.NOTE
            else -> VoiceIntentKind.REMINDER
        }
        val personMatch = personRegex.find(text) ?: lowerPersonRegex.find(text)
        val person = personMatch?.groupValues?.get(1)?.trim()?.replaceFirstChar { it.uppercase() } ?: ""
        // El mensaje es lo que viene después del nombre ("...a Carlos que mañana le pago")
        val afterPerson = personMatch?.let { text.substring(it.range.last + 1) } ?: text
        val stopNames = setOf("Mañana", "Hoy", "Pasado", "Que", "Para")
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
            VoiceIntentKind.REMINDER -> VoiceIntent(kind, title = base.title, message = base.message, dateTimeMillis = base.dateTimeMillis)
        }
    }
}

/** Interpretación con Gemini: entiende frases libres y redacta ideas y mensajes. */
object AiAssistant {
    private val isoFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")

    fun parseVoice(context: Context, text: String): VoiceIntent {
        val now = LocalDateTime.now()
        val today = now.format(DateTimeFormatter.ofPattern("EEEE d 'de' MMMM 'de' yyyy, HH:mm", Locale("es")))
        val user = ProfileStore.name(context).ifBlank { "el usuario" }
        val prompt = """
            Eres el asistente de una agenda en Android. $user acaba de dictar por voz (puede tener errores de dictado):
            "$text"

            Ahora es $today (hora local de Venezuela).
            Decide qué quiso hacer y responde SOLO un JSON con estas claves:
            - "tipo": "idea" (una idea, ocurrencia o proyecto), "nota" (algo para guardar), "llamada" (debe llamar a alguien),
              "whatsapp" (debe escribirle o enviarle un mensaje a alguien) o "recordatorio" (cualquier otra cosa con hora).
            - "titulo": título corto y claro (máx. 60 caracteres).
            - "contenido": solo para idea o nota. Redacta la idea ordenada y bien escrita en español: un resumen de 1-2 frases
              y luego viñetas con los puntos clave y próximos pasos concretos. No inventes datos que no se dijeron.
            - "persona": nombre de la persona a llamar o escribir, tal como lo dijo (ej. "Carlos"). Vacío si no hay.
            - "mensaje": para whatsapp, el mensaje listo para enviar, escrito en primera persona, cálido y natural,
              en español venezolano, sin saludos exagerados. Para llamada, de qué debe hablar. Vacío en otro caso.
            - "fecha_hora": cuándo debe sonar el aviso en formato yyyy-MM-ddTHH:mm. Si no dijo hora para una llamada,
              whatsapp o recordatorio, usa dentro de 1 hora. Para idea o nota, vacío.
        """.trimIndent()

        val o = JSONObject(GeminiClient.generate(context, prompt, json = true).removeSurrounding("```json", "```").trim())
        val kind = when (o.optString("tipo").lowercase(Locale.ROOT)) {
            "idea" -> VoiceIntentKind.IDEA
            "nota" -> VoiceIntentKind.NOTE
            "llamada" -> VoiceIntentKind.CALL
            "whatsapp" -> VoiceIntentKind.WHATSAPP
            else -> VoiceIntentKind.REMINDER
        }
        val millis = runCatching {
            val dt = LocalDateTime.parse(o.optString("fecha_hora").take(16), isoFormat)
            dt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }.getOrNull()?.takeIf { it > System.currentTimeMillis() } ?: VoiceParser.parse(text).dateTimeMillis

        return VoiceIntent(
            kind = kind,
            title = o.optString("titulo").ifBlank { text.take(60) },
            content = o.optString("contenido"),
            personName = o.optString("persona").trim(),
            message = o.optString("mensaje").trim(),
            dateTimeMillis = millis,
            byAi = true,
        )
    }

    /** Redacta y ordena una idea o nota escrita a la carrera. */
    fun polishNote(context: Context, content: String): String = GeminiClient.generate(
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
    fun polishWhatsApp(context: Context, message: String, personName: String): String = GeminiClient.generate(
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

    /** Todos los contactos con teléfono cuyo nombre contiene [name]. Sin duplicados por número. */
    fun find(context: Context, name: String): List<SelectedPhoneContact> {
        if (name.isBlank() || !hasPermission(context)) return emptyList()
        val words = SmartNoteOrganizer.normalize(name).split(" ").filter { it.length >= 2 }
        if (words.isEmpty()) return emptyList()
        val result = linkedMapOf<String, SelectedPhoneContact>()
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
