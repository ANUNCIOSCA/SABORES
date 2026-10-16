package com.agendaveterinaria.app

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

val VetGreen = Color(0xFF0E8A6E)
val VetGreenDark = Color(0xFF075E4B)
val VetGreenSoft = Color(0xFFE3F4EF)
val VetDanger = Color(0xFFD64545)
val VetAmber = Color(0xFFE09A1A)
val VetBlue = Color(0xFF2F6FDE)
val WaGreen = Color(0xFF16A34A)

private val scheme: ColorScheme = lightColorScheme(
    primary = VetGreen,
    onPrimary = Color.White,
    primaryContainer = VetGreenSoft,
    onPrimaryContainer = VetGreenDark,
    secondary = VetBlue,
    background = Color(0xFFF3F8F6),
    surface = Color.White,
    surfaceVariant = Color(0xFFEDF3F1),
    outline = Color(0xFFD5E2DE),
    error = VetDanger,
)

@Composable
fun VetTheme(content: @Composable () -> Unit) = MaterialTheme(colorScheme = scheme, content = content)

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), modifier = modifier.padding(top = 12.dp, bottom = 6.dp), style = MaterialTheme.typography.labelMedium, color = VetGreen, fontWeight = FontWeight.Bold)
}

@Composable
fun EmptyState(icon: ImageVector, title: String, hint: String) {
    Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(64.dp).clip(CircleShape).background(VetGreenSoft), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = VetGreen, modifier = Modifier.size(32.dp))
        }
        Spacer(Modifier.padding(6.dp))
        Text(title, fontWeight = FontWeight.SemiBold)
        Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun VetCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) { Column(Modifier.padding(12.dp)) { content() } }
}

@Composable
fun Pill(text: String, color: Color = VetGreen) {
    Text(
        text, color = color, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.12f)).padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/** Dictado por voz (Google) para escribir rápido el motivo, diagnóstico o tratamiento. */
@Composable
fun rememberDictation(prompt: String, onText: (String) -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) {
            r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let(onText)
        }
    }
    return {
        runCatching {
            launcher.launch(
                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-VE")
                    .putExtra(RecognizerIntent.EXTRA_PROMPT, prompt),
            )
        }.onFailure { toast(context, "Este teléfono no tiene dictado por voz.") }
    }
}

/** Campo de texto con micrófono para dictar. */
@Composable
fun TextFieldVoice(value: String, onChange: (String) -> Unit, label: String, minLines: Int = 1, placeholder: String = "") {
    val dictate = rememberDictation(label) { t -> onChange(if (value.isBlank()) t.replaceFirstChar { it.uppercase() } else "$value $t") }
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) },
        placeholder = { if (placeholder.isNotBlank()) Text(placeholder) },
        modifier = Modifier.fillMaxWidth().then(if (minLines > 1) Modifier.heightIn(min = (minLines * 28 + 28).dp) else Modifier),
        singleLine = minLines == 1,
        trailingIcon = { IconButton(onClick = dictate) { Icon(Icons.Rounded.Mic, "Dictar") } },
    )
}

@Composable
fun NumberField(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier, suffix: String = "") {
    OutlinedTextField(
        value = value, onValueChange = { v -> onChange(v.filter { it.isDigit() || it == ',' || it == '.' }.take(12)) },
        label = { Text(label) }, singleLine = true, modifier = modifier,
        suffix = { if (suffix.isNotBlank()) Text(suffix) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
    )
}

@Composable
fun DateButton(label: String, millis: Long, modifier: Modifier = Modifier, withTime: Boolean = false, onPick: (Long) -> Unit) {
    val context = LocalContext.current
    OutlinedButton(
        onClick = {
            pickDate(context, millis) { d ->
                val base = if (millis > 0) atHour(d, fmt(millis, "H").toInt(), fmt(millis, "m").toInt()) else atHour(d, 9)
                if (withTime) pickTime(context, base, onPick) else onPick(base)
            }
        },
        modifier = modifier, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = VetGreenDark),
    ) {
        Icon(Icons.Rounded.CalendarMonth, null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(if (millis > 0) (if (withTime) fmtDateTime(millis) else fmtDate(millis)) else "Elegir", maxLines = 1)
        }
    }
}

@Composable
fun LabeledRow(label: String, value: String) {
    if (value.isBlank()) return
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(96.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}

/**
 * Diálogos anchos: la ventana ocupa toda la pantalla y el contenido respeta la barra de
 * navegación y el teclado (con safeDrawingPadding), para que Guardar/Cancelar siempre se vean.
 */
val WideDialogProperties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
