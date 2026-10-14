package com.agendawa.app

import android.content.Context
import android.speech.tts.TextToSpeech
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

/** Preferencias del asistente de voz. */
object VoicePrefs {
    private const val PREFS = "agenda_wa_voice"

    /** Leer en voz alta lo que se guardó ("Listo, WhatsApp para Carlos mañana a las 3"). */
    fun speakBack(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("speak_back", true)

    fun setSpeakBack(context: Context, on: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("speak_back", on).apply()
    }
}

/** Voz del teléfono para confirmar lo que hizo el asistente. */
class Speaker(context: Context) {
    private var ready = false
    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        ready = status == TextToSpeech.SUCCESS
        if (ready) {
            val venezuela = Locale("es", "VE")
            if (tts.setLanguage(venezuela) < TextToSpeech.LANG_AVAILABLE) tts.setLanguage(Locale("es"))
        }
    }

    fun say(text: String) {
        if (ready && text.isNotBlank()) tts.speak(text, TextToSpeech.QUEUE_ADD, null, "agenda-${text.hashCode()}")
    }

    fun shutdown() = tts.shutdown()
}

@Composable
fun rememberSpeaker(): Speaker {
    val context = LocalContext.current
    val speaker = remember { Speaker(context) }
    DisposableEffect(Unit) { onDispose { speaker.shutdown() } }
    return speaker
}

/** Frase corta para decir y mostrar al guardar algo por voz. */
fun spokenWhen(millis: Long): String {
    val today = startOfDay(System.currentTimeMillis())
    val day = when (startOfDay(millis)) {
        today -> "hoy"
        today + 86_400_000L -> "mañana"
        else -> formatDate(millis, "EEEE d")
    }
    return "$day a las ${formatDate(millis, "h:mm a")}"
}
