package com.agendawa.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

// ---------------------------------------------------------------------------
// Proveedores de IA: Gemini, Claude, Groq, Grok, ChatGPT, DeepSeek, OpenRouter
// ---------------------------------------------------------------------------

enum class AiProtocol { GEMINI, ANTHROPIC, OPENAI }

enum class AiProvider(
    val key: String,
    val label: String,
    val protocol: AiProtocol,
    val baseUrl: String,
    val defaultModel: String,
    val keyHint: String,
    val keyUrl: String,
    val note: String,
) {
    GEMINI(
        "gemini", "Gemini (Google)", AiProtocol.GEMINI,
        "https://generativelanguage.googleapis.com/v1beta", "gemini-flash-latest",
        "AIza...", "https://aistudio.google.com/apikey",
        "Tiene plan gratis con límite diario.",
    ),
    CLAUDE(
        "claude", "Claude (Anthropic)", AiProtocol.ANTHROPIC,
        "https://api.anthropic.com/v1", "claude-haiku-5-5",
        "sk-ant-...", "https://console.anthropic.com/settings/keys",
        "De pago por uso. Muy bueno redactando en español.",
    ),
    GROQ(
        "groq", "Groq", AiProtocol.OPENAI,
        "https://api.groq.com/openai/v1", "openai/gpt-oss-120b",
        "gsk_...", "https://console.groq.com/keys",
        "Tiene plan gratis y responde muy rápido.",
    ),
    GROK(
        "grok", "Grok (xAI)", AiProtocol.OPENAI,
        "https://api.x.ai/v1", "grok-4.3",
        "xai-...", "https://console.x.ai",
        "De pago por uso.",
    ),
    OPENAI(
        "openai", "ChatGPT (OpenAI)", AiProtocol.OPENAI,
        "https://api.openai.com/v1", "gpt-5-mini",
        "sk-...", "https://platform.openai.com/api-keys",
        "De pago por uso.",
    ),
    DEEPSEEK(
        "deepseek", "DeepSeek", AiProtocol.OPENAI,
        "https://api.deepseek.com/v1", "deepseek-chat",
        "sk-...", "https://platform.deepseek.com/api_keys",
        "De pago por uso, muy económico.",
    ),
    OPENROUTER(
        "openrouter", "OpenRouter (muchos modelos)", AiProtocol.OPENAI,
        "https://openrouter.ai/api/v1", "openrouter/auto",
        "sk-or-...", "https://openrouter.ai/keys",
        "Una sola clave para cientos de modelos, algunos gratis.",
    );

    companion object {
        fun fromKey(key: String?): AiProvider = entries.firstOrNull { it.key == key } ?: GEMINI
    }
}

/**
 * Claves y modelo de cada proveedor. Se guardan solo en este teléfono
 * (no van en las copias de seguridad).
 */
object AiSettings {
    private const val PREFS = "agenda_wa_ai"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun activeProvider(context: Context): AiProvider = AiProvider.fromKey(prefs(context).getString("provider", null))
    fun setActiveProvider(context: Context, provider: AiProvider) {
        prefs(context).edit().putString("provider", provider.key).apply()
    }

    // La 1.8.0 guardaba la clave de Gemini en "api_key"; se sigue leyendo para no perderla.
    fun apiKey(context: Context, p: AiProvider): String =
        (prefs(context).getString("key_${p.key}", null)
            ?: if (p == AiProvider.GEMINI) prefs(context).getString("api_key", "") else "")
            ?.trim().orEmpty()

    fun model(context: Context, p: AiProvider): String {
        val stored = prefs(context).getString("model_${p.key}", null)
            ?: if (p == AiProvider.GEMINI) prefs(context).getString("model", null) else null
        return stored?.trim().orEmpty().ifBlank { p.defaultModel }
    }

    fun save(context: Context, p: AiProvider, apiKey: String, model: String) {
        prefs(context).edit()
            .putString("key_${p.key}", apiKey.trim())
            .putString("model_${p.key}", model.trim().ifBlank { p.defaultModel })
            .apply()
    }

    fun fallbackEnabled(context: Context): Boolean = prefs(context).getBoolean("fallback", true)
    fun setFallback(context: Context, on: Boolean) { prefs(context).edit().putBoolean("fallback", on).apply() }

    /** Proveedores con clave, empezando por el activo. */
    fun configured(context: Context): List<AiProvider> {
        val active = activeProvider(context)
        return (listOf(active) + AiProvider.entries.filter { it != active }).filter { apiKey(context, it).isNotBlank() }
    }

    fun isEnabled(context: Context): Boolean = configured(context).isNotEmpty()
}

open class AiException(message: String) : Exception(message)

/** El modelo configurado ya no existe (los proveedores retiran modelos viejos). */
class AiModelNotFound(message: String) : AiException(message)

/** Cliente único para todos los proveedores. Se llama fuera del hilo principal. */
object AiClient {

    /**
     * Pide una respuesta al proveedor activo. Si falla y hay otros proveedores con clave
     * (y el respaldo está activado), prueba con ellos en orden.
     */
    fun generate(context: Context, prompt: String, json: Boolean, image: ByteArray? = null): String {
        val providers = AiSettings.configured(context)
        if (providers.isEmpty()) throw AiException("Falta la clave de IA en Configuración")
        val toTry = if (AiSettings.fallbackEnabled(context)) providers else providers.take(1)
        var lastError: AiException? = null
        for (p in toTry) {
            try {
                return generateWith(context, p, AiSettings.apiKey(context, p), AiSettings.model(context, p), prompt, json, image)
            } catch (e: AiException) {
                lastError = e
            }
        }
        throw lastError ?: AiException("No se pudo usar la IA")
    }

    fun generateWith(
        context: Context, p: AiProvider, key: String, model: String, prompt: String, json: Boolean, image: ByteArray? = null,
    ): String =
        try {
            generateOnce(p, key, model, prompt, json, image)
        } catch (e: AiModelNotFound) {
            // El modelo fue retirado: se busca uno vigente en la cuenta, se guarda y se reintenta una vez
            val replacement = runCatching { pickModel(p, listModels(p, key)) }.getOrNull()
                ?.takeIf { it != model } ?: throw e
            AiSettings.save(context, p, key, replacement)
            generateOnce(p, key, replacement, prompt, json, image)
        }

    /** Elige el mejor modelo de la lista: el sugerido si está, si no el más nuevo de uso general. */
    fun pickModel(p: AiProvider, models: List<String>): String? {
        if (models.isEmpty()) return null
        if (p.defaultModel in models) return p.defaultModel
        val special = Regex("image|tts|audio|live|embed|vision|guard|whisper|transcribe|search|realtime|aqa|robotics|computer", RegexOption.IGNORE_CASE)
        val general = models.filterNot { special.containsMatchIn(it) }.ifEmpty { models }
        val preferred = when (p) {
            AiProvider.GEMINI -> listOf("flash-latest", "flash")
            AiProvider.CLAUDE -> listOf("haiku", "sonnet")
            AiProvider.OPENAI -> listOf("mini")
            else -> emptyList()
        }
        for (word in preferred) {
            val hits = general.filter { it.contains(word, ignoreCase = true) }
            val stable = hits.filterNot { it.contains("preview") || it.contains("exp") || it.contains("lite") }
            (stable.ifEmpty { hits }).maxOrNull()?.let { return it }
        }
        return general.maxOrNull()
    }

    /** [image]: foto JPEG opcional para que la IA la analice. */
    private fun generateOnce(p: AiProvider, key: String, model: String, prompt: String, json: Boolean, image: ByteArray?): String {
        if (key.isBlank()) throw AiException("Falta la clave de ${p.label}")
        val finalPrompt = if (json) "$prompt\n\nResponde únicamente con el JSON, sin texto adicional." else prompt
        val b64 = image?.let { android.util.Base64.encodeToString(it, android.util.Base64.NO_WRAP) }
        return when (p.protocol) {
            AiProtocol.GEMINI -> {
                val body = JSONObject().apply {
                    put("contents", JSONArray().put(JSONObject().apply {
                        put("role", "user")
                        put("parts", JSONArray().apply {
                            if (b64 != null) put(JSONObject().put("inline_data", JSONObject().put("mime_type", "image/jpeg").put("data", b64)))
                            put(JSONObject().put("text", finalPrompt))
                        })
                    }))
                    put("generationConfig", JSONObject().apply {
                        put("temperature", 0.4)
                        if (json) put("responseMimeType", "application/json")
                    })
                }
                val res = post(p, "${p.baseUrl}/models/$model:generateContent", mapOf("x-goog-api-key" to key), body, model)
                val parts = res.getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts")
                buildString {
                    for (i in 0 until parts.length()) {
                        val part = parts.getJSONObject(i)
                        if (!part.optBoolean("thought")) append(part.optString("text"))
                    }
                }
            }
            AiProtocol.ANTHROPIC -> {
                val body = JSONObject().apply {
                    put("model", model)
                    put("max_tokens", 8000)
                    val content: Any = if (b64 == null) finalPrompt else JSONArray()
                        .put(JSONObject().put("type", "image").put("source", JSONObject()
                            .put("type", "base64").put("media_type", "image/jpeg").put("data", b64)))
                        .put(JSONObject().put("type", "text").put("text", finalPrompt))
                    put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", content)))
                }
                val res = post(
                    p, "${p.baseUrl}/messages",
                    mapOf("x-api-key" to key, "anthropic-version" to "2023-06-01"),
                    body, model,
                )
                val content = res.getJSONArray("content")
                buildString {
                    for (i in 0 until content.length()) {
                        val block = content.getJSONObject(i)
                        if (block.optString("type") == "text") append(block.optString("text"))
                    }
                }
            }
            AiProtocol.OPENAI -> {
                val body = JSONObject().apply {
                    put("model", model)
                    val content: Any = if (b64 == null) finalPrompt else JSONArray()
                        .put(JSONObject().put("type", "text").put("text", finalPrompt))
                        .put(JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", "data:image/jpeg;base64,$b64")))
                    put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", content)))
                }
                val res = post(p, "${p.baseUrl}/chat/completions", mapOf("Authorization" to "Bearer $key"), body, model)
                res.getJSONArray("choices").getJSONObject(0).getJSONObject("message").optString("content")
            }
        }.trim().ifBlank { throw AiException("${p.label} no devolvió respuesta") }
    }

    /** Prueba rápida desde Configuración. */
    fun test(context: Context, p: AiProvider, key: String, model: String): String =
        generateWith(context, p, key, model, "Responde solo: OK", json = false)

    /** Lista de modelos disponibles para esa clave, para elegir sin escribir el nombre. */
    fun listModels(p: AiProvider, key: String): List<String> {
        if (key.isBlank()) throw AiException("Primero pega la clave")
        val (url, headers) = when (p.protocol) {
            AiProtocol.GEMINI -> "${p.baseUrl}/models?pageSize=200" to mapOf("x-goog-api-key" to key)
            AiProtocol.ANTHROPIC -> "${p.baseUrl}/models?limit=100" to mapOf("x-api-key" to key, "anthropic-version" to "2023-06-01")
            AiProtocol.OPENAI -> "${p.baseUrl}/models" to mapOf("Authorization" to "Bearer $key")
        }
        val res = request(p, url, "GET", headers, null, "")
        val out = mutableListOf<String>()
        when (p.protocol) {
            AiProtocol.GEMINI -> {
                val arr = res.optJSONArray("models") ?: JSONArray()
                for (i in 0 until arr.length()) {
                    val m = arr.getJSONObject(i)
                    val methods = m.optJSONArray("supportedGenerationMethods")?.toString().orEmpty()
                    if ("generateContent" in methods) out += m.optString("name").removePrefix("models/")
                }
            }
            else -> {
                val arr = res.optJSONArray("data") ?: JSONArray()
                for (i in 0 until arr.length()) out += arr.getJSONObject(i).optString("id")
            }
        }
        return out.filter { it.isNotBlank() }.distinct().sorted()
    }

    /** Saca el objeto JSON aunque el modelo lo envuelva en ```json ... ``` o texto. */
    fun extractJson(text: String): String {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) throw AiException("La IA no devolvió el formato esperado")
        return text.substring(start, end + 1)
    }

    private fun post(p: AiProvider, url: String, headers: Map<String, String>, body: JSONObject, model: String): JSONObject =
        request(p, url, "POST", headers, body, model)

    private fun request(p: AiProvider, url: String, method: String, headers: Map<String, String>, body: JSONObject?, model: String): JSONObject {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = 15_000
            conn.readTimeout = 60_000
            headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val code = conn.responseCode
            val raw = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val apiMsg = runCatching {
                    val err = JSONObject(raw).opt("error")
                    if (err is JSONObject) err.optString("message") else err?.toString()
                }.getOrNull()?.takeIf { it.isNotBlank() }
                if (code == 404 && model.isNotBlank()) {
                    throw AiModelNotFound("${p.label}: el modelo \"$model\" ya no está disponible. Toca \"Ver modelos\" y elige otro.")
                }
                throw AiException(
                    "${p.label}: " + when (code) {
                        400 -> if (apiMsg?.contains("API key", ignoreCase = true) == true) "la clave no es válida. Cópiala de nuevo completa."
                        else "petición rechazada" + (apiMsg?.let { " ($it)" } ?: "")
                        401, 403 -> "la clave no es válida o no tiene permiso. Cópiala de nuevo completa."
                        402 -> "la cuenta no tiene saldo."
                        404 -> "no se encontró la dirección del servicio."
                        429 -> "se alcanzó el límite de uso. Intenta en un rato."
                        else -> "error $code" + (apiMsg?.let { ": $it" } ?: "")
                    },
                )
            }
            return JSONObject(raw)
        } catch (e: AiException) {
            throw e
        } catch (e: java.io.IOException) {
            throw AiException("${p.label}: sin conexión a internet o no respondió.")
        } catch (e: Exception) {
            throw AiException("${p.label}: no se entendió la respuesta.")
        } finally {
            conn.disconnect()
        }
    }
}
