package com.agendawa.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ---------------------------------------------------------------------------
// Candado con huella / PIN del teléfono
// ---------------------------------------------------------------------------

object AppLock {
    private const val PREFS = "agenda_wa_security"
    private const val AUTH = BIOMETRIC_WEAK or DEVICE_CREDENTIAL

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("lock_enabled", false)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("lock_enabled", enabled).apply()
    }

    /** null si se puede usar; si no, el motivo para mostrarlo. */
    fun unavailableReason(context: Context): String? =
        when (BiometricManager.from(context).canAuthenticate(AUTH)) {
            BiometricManager.BIOMETRIC_SUCCESS -> null
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED ->
                "Primero registra una huella o un PIN/patrón de bloqueo en los ajustes del teléfono."
            else -> "Este teléfono no tiene huella ni bloqueo de pantalla disponible."
        }

    fun authenticate(activity: FragmentActivity, subtitle: String, onSuccess: () -> Unit, onFail: (String) -> Unit = {}) {
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onFail(errString.toString())
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Agenda WA")
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(AUTH)
            .build()
        prompt.authenticate(info)
    }
}

/** Pantalla que tapa la app hasta que se desbloquea. */
@Composable
fun LockScreen(onUnlock: () -> Unit) {
    val activity = LocalContext.current as FragmentActivity
    var error by remember { mutableStateOf<String?>(null) }
    val ask = {
        error = null
        AppLock.authenticate(activity, "Usa tu huella para abrir la agenda", onSuccess = onUnlock, onFail = { error = it })
    }
    LaunchedEffect(Unit) { ask() }
    BackHandler { activity.finish() }
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(AgendaBlueDark, AgendaBlue)))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Box(Modifier.size(96.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Lock, null, tint = Color.White, modifier = Modifier.size(48.dp))
            }
            Spacer(Modifier.height(20.dp))
            Text("Agenda WA está protegida", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(error ?: "Desbloquea con tu huella o tu PIN", color = Color.White.copy(alpha = 0.85f), textAlign = TextAlign.Center)
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = ask,
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = AgendaBlueDark),
            ) {
                Icon(Icons.Rounded.Fingerprint, null)
                Spacer(Modifier.width(8.dp))
                Text("Desbloquear", fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Configuración: IA (Gemini) y seguridad
// ---------------------------------------------------------------------------

@Composable
fun SettingsDialog(onDismiss: () -> Unit, showMessage: (String) -> Unit) {
    val context = LocalContext.current
    val activity = context as FragmentActivity
    val scope = rememberCoroutineScope()

    var apiKey by remember { mutableStateOf(AiSettings.apiKey(context)) }
    var model by remember { mutableStateOf(AiSettings.model(context)) }
    var showKey by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var lockEnabled by remember { mutableStateOf(AppLock.isEnabled(context)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Configuración") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.AutoAwesome, null, tint = AgendaBlue)
                    Spacer(Modifier.width(8.dp))
                    Text("Inteligencia artificial (Gemini)", fontWeight = FontWeight.SemiBold)
                }
                Text(
                    "Con tu clave de Gemini, la agenda entiende lo que dictas, redacta tus ideas y deja listos los mensajes de WhatsApp. " +
                        "La clave se guarda solo en este teléfono.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it.trim(); testResult = null },
                    label = { Text("Clave de API de Gemini") },
                    placeholder = { Text("AIza...") },
                    singleLine = true,
                    visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showKey = !showKey }) {
                            Icon(if (showKey) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, "Mostrar clave")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it.trim(); testResult = null },
                    label = { Text("Modelo") },
                    supportingText = { Text("Recomendado: ${AiSettings.DEFAULT_MODEL}") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(onClick = {
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/apikey")))
                    }
                }) { Text("¿No tienes clave? Sácala gratis en Google AI Studio") }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        enabled = apiKey.isNotBlank() && !testing,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            AiSettings.save(context, apiKey, model)
                            testing = true
                            testResult = null
                            scope.launch {
                                testResult = withContext(Dispatchers.IO) {
                                    runCatching { GeminiClient.test(context); "✅ Gemini conectado y funcionando" }
                                        .getOrElse { "❌ ${it.message}" }
                                }
                                testing = false
                            }
                        },
                    ) {
                        if (testing) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Probar conexión")
                    }
                    if (AiSettings.isEnabled(context)) {
                        OutlinedButton(modifier = Modifier.weight(1f), onClick = {
                            apiKey = ""
                            AiSettings.save(context, "", model)
                            testResult = "IA desactivada. La agenda sigue funcionando sin internet."
                        }) { Text("Quitar clave") }
                    }
                }
                testResult?.let { Text(it, style = MaterialTheme.typography.bodySmall) }

                HorizontalDivider(Modifier.padding(vertical = 6.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Fingerprint, null, tint = AgendaBlue)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Bloquear con huella", fontWeight = FontWeight.SemiBold)
                        Text(
                            "Pide tu huella o PIN al abrir la agenda. Las alarmas siguen sonando igual.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = lockEnabled,
                        onCheckedChange = { wanted ->
                            val reason = AppLock.unavailableReason(context)
                            if (reason != null) {
                                showMessage(reason)
                            } else {
                                // Se confirma con la huella antes de activar o desactivar
                                AppLock.authenticate(
                                    activity,
                                    if (wanted) "Confirma para activar el candado" else "Confirma para quitar el candado",
                                    onSuccess = {
                                        AppLock.setEnabled(context, wanted)
                                        lockEnabled = wanted
                                        showMessage(if (wanted) "Candado activado" else "Candado desactivado")
                                    },
                                )
                            }
                        },
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                AiSettings.save(context, apiKey, model)
                showMessage(if (apiKey.isNotBlank()) "Configuración guardada. IA activa." else "Configuración guardada")
                onDismiss()
            }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
    )
}

// ---------------------------------------------------------------------------
// Diálogos del asistente de voz
// ---------------------------------------------------------------------------

@Composable
fun ThinkingDialog(text: String) {
    AlertDialog(
        onDismissRequest = {},
        confirmButton = {},
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(28.dp))
                Spacer(Modifier.width(16.dp))
                Text(text)
            }
        },
    )
}

/** Cuando hay varios contactos con el mismo nombre, se elige uno. */
@Composable
fun ContactChoiceDialog(
    name: String,
    contacts: List<SelectedPhoneContact>,
    onPick: (SelectedPhoneContact) -> Unit,
    onSkip: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onSkip,
        title = { Text("¿Cuál $name?") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Encontré ${contacts.size} contactos. Toca el correcto:", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                contacts.forEach { c ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.medium)
                            .clickable { onPick(c) }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(36.dp).clip(CircleShape).background(AgendaBlue.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Person, null, tint = AgendaBlue)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(c.displayName, fontWeight = FontWeight.SemiBold)
                            Text(c.phoneNumber, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onSkip) { Text("Ninguno, lo elijo luego") } },
    )
}
