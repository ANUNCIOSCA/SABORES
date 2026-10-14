@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.agendawa.app

import android.app.Activity
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Checkroom
import androidx.compose.material.icons.rounded.ChildCare
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.Handyman
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.LocalGasStation
import androidx.compose.material.icons.rounded.LocalHospital
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.Redeem
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Sell
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material.icons.rounded.Store
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Calendar

// ---------------------------------------------------------------------------
// Colores
// ---------------------------------------------------------------------------

val AgendaBlue = Color(0xFF1769E0)
val AgendaBlueDark = Color(0xFF0D47A1)
val AgendaBlueSoft = Color(0xFFEAF2FF)
val AgendaGreen = Color(0xFF16A34A)
val AgendaGreenDark = Color(0xFF0B5E2A)
val AgendaGreenSoft = Color(0xFFEAF8EF)
val AgendaBackground = Color(0xFFF6F8FC)
val AgendaSurface = Color(0xFFFFFFFF)
val AgendaSurfaceAlt = Color(0xFFF0F3F8)
val AgendaText = Color(0xFF172033)
val AgendaMuted = Color(0xFF64748B)
val AgendaBorder = Color(0xFFE3E8F0)
val AgendaDanger = Color(0xFFDC2626)
val AgendaAmber = Color(0xFFF59E0B)

private val AgendaColorScheme: ColorScheme = lightColorScheme(
    primary = AgendaBlue,
    onPrimary = Color.White,
    primaryContainer = AgendaBlueSoft,
    onPrimaryContainer = AgendaBlueDark,
    secondary = AgendaGreen,
    onSecondary = Color.White,
    secondaryContainer = AgendaGreenSoft,
    onSecondaryContainer = AgendaGreenDark,
    background = AgendaBackground,
    onBackground = AgendaText,
    surface = AgendaSurface,
    onSurface = AgendaText,
    surfaceVariant = AgendaSurfaceAlt,
    onSurfaceVariant = AgendaMuted,
    surfaceContainer = AgendaSurface,
    surfaceContainerLow = AgendaSurface,
    surfaceContainerHigh = AgendaSurfaceAlt,
    error = AgendaDanger,
    outline = AgendaBorder,
    outlineVariant = AgendaBorder,
)

// Tema oscuro con acento amarillo para Mis Gastos (como en las capturas)
val GastosYellow = Color(0xFFFFD43B)
val GastosBg = Color(0xFF1B1B1B)
val GastosSurface = Color(0xFF242424)
val GastosSurfaceHigh = Color(0xFF2E2E2E)
val GastosText = Color(0xFFF2F2F2)
val GastosMuted = Color(0xFF9E9E9E)
val GastosExpense = Color(0xFFFF6B6B)
val GastosIncome = Color(0xFF4ADE80)

private val GastosColorScheme: ColorScheme = darkColorScheme(
    primary = GastosYellow,
    onPrimary = Color(0xFF1B1B1B),
    primaryContainer = Color(0xFF4A3F12),
    onPrimaryContainer = GastosYellow,
    secondary = GastosYellow,
    onSecondary = Color(0xFF1B1B1B),
    background = GastosBg,
    onBackground = GastosText,
    surface = GastosSurface,
    onSurface = GastosText,
    surfaceVariant = GastosSurfaceHigh,
    onSurfaceVariant = GastosMuted,
    surfaceContainer = GastosSurface,
    surfaceContainerLow = GastosSurface,
    surfaceContainerHigh = GastosSurfaceHigh,
    surfaceContainerHighest = GastosSurfaceHigh,
    error = GastosExpense,
    outline = Color(0xFF3A3A3A),
    outlineVariant = Color(0xFF333333),
)

private val AgendaShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
)

@Composable
fun AgendaWaTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = AgendaColorScheme, shapes = AgendaShapes, content = content)
}

@Composable
fun GastosTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = GastosColorScheme, shapes = AgendaShapes, content = content)
}

// ---------------------------------------------------------------------------
// Iconos de categorías
// ---------------------------------------------------------------------------

fun categoryIcon(key: String): ImageVector = when (key) {
    "food" -> Icons.Rounded.Restaurant
    "cart" -> Icons.Rounded.ShoppingCart
    "car" -> Icons.Rounded.DirectionsCar
    "home" -> Icons.Rounded.Home
    "bolt" -> Icons.Rounded.Bolt
    "phone" -> Icons.Rounded.Smartphone
    "health" -> Icons.Rounded.LocalHospital
    "school" -> Icons.Rounded.School
    "shirt" -> Icons.Rounded.Checkroom
    "movie" -> Icons.Rounded.Movie
    "work" -> Icons.Rounded.Work
    "store" -> Icons.Rounded.Store
    "gift" -> Icons.Rounded.Redeem
    "pets" -> Icons.Rounded.Pets
    "salary" -> Icons.Rounded.Payments
    "sell" -> Icons.Rounded.Sell
    "client" -> Icons.Rounded.People
    "trend" -> Icons.AutoMirrored.Rounded.TrendingUp
    "coffee" -> Icons.Rounded.LocalCafe
    "fuel" -> Icons.Rounded.LocalGasStation
    "travel" -> Icons.Rounded.Flight
    "sport" -> Icons.Rounded.FitnessCenter
    "beauty" -> Icons.Rounded.Spa
    "baby" -> Icons.Rounded.ChildCare
    "bank" -> Icons.Rounded.AccountBalance
    "card" -> Icons.Rounded.CreditCard
    "music" -> Icons.Rounded.MusicNote
    "tools" -> Icons.Rounded.Handyman
    else -> Icons.Rounded.MoreHoriz
}

@Composable
fun CategoryBadge(category: ExpenseCategory?, size: Dp = 40.dp) {
    val color = Color(category?.colorArgb ?: 0xFF94A3B8)
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.22f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(categoryIcon(category?.iconKey ?: "more"), contentDescription = null, tint = color, modifier = Modifier.size(size * 0.55f))
    }
}

// ---------------------------------------------------------------------------
// Componentes compartidos
// ---------------------------------------------------------------------------

@Composable
fun SectionTitle(text: String, subtitle: String? = null, modifier: Modifier = Modifier) {
    Column(modifier.padding(top = 8.dp, bottom = 4.dp)) {
        Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(vertical = 40.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(52.dp))
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 3.dp),
    ) {
        Text(text, color = color, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

// ---------------------------------------------------------------------------
// Fecha y hora
// ---------------------------------------------------------------------------

fun pickDate(context: Context, initialMillis: Long, onPicked: (Long) -> Unit) {
    val cal = Calendar.getInstance().apply { timeInMillis = initialMillis }
    DatePickerDialog(context, { _, y, m, d ->
        val result = Calendar.getInstance().apply {
            timeInMillis = initialMillis
            set(Calendar.YEAR, y)
            set(Calendar.MONTH, m)
            set(Calendar.DAY_OF_MONTH, d)
        }
        onPicked(result.timeInMillis)
    }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
}

fun pickTime(context: Context, initialMillis: Long, onPicked: (Long) -> Unit) {
    val cal = Calendar.getInstance().apply { timeInMillis = initialMillis }
    TimePickerDialog(context, { _, h, min ->
        val result = Calendar.getInstance().apply {
            timeInMillis = initialMillis
            set(Calendar.HOUR_OF_DAY, h)
            set(Calendar.MINUTE, min)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        onPicked(result.timeInMillis)
    }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), false).show()
}

fun startOfDay(millis: Long): Long = Calendar.getInstance().apply {
    timeInMillis = millis
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

fun endOfDay(millis: Long): Long = startOfDay(millis) + 86_400_000L - 1

fun isSameDay(a: Long, b: Long): Boolean = startOfDay(a) == startOfDay(b)

fun toast(context: Context, text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()

// ---------------------------------------------------------------------------
// Dictado por voz
// ---------------------------------------------------------------------------

@Composable
fun rememberSpeechInput(prompt: String, onCancel: () -> Unit = {}, onText: (String) -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val text = if (result.resultCode == Activity.RESULT_OK)
            result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull() else null
        if (!text.isNullOrBlank()) onText(text) else onCancel()
    }
    return remember(prompt) {
        {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-VE")
                putExtra(RecognizerIntent.EXTRA_PROMPT, prompt)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
            }
            try {
                launcher.launch(intent)
            } catch (e: ActivityNotFoundException) {
                toast(context, "Este teléfono no tiene reconocimiento de voz disponible.")
                onCancel()
            }
        }
    }
}
