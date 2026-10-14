@file:OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)

package com.agendawa.app

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.Celebration
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AddAPhoto
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream

// ---------------------------------------------------------------------------
// Laboratorio de ideas: categoría → preguntas guiadas (con foto opcional) →
// idea estructurada → documento .md con el prompt para que otra IA la desarrolle.
// La IA aquí NO genera código ni imágenes: solo entiende y ordena la idea.
// ---------------------------------------------------------------------------

data class IdeaCategory(
    val key: String,
    val icon: ImageVector,
    val label: String,
    val subcategories: List<String>,
    /** Cómo debe pensar la IA para esta categoría. */
    val expertRole: String,
    /** Secciones del documento final. */
    val outline: String,
    /** Para qué se le puede pasar el .md a otra IA. */
    val targets: List<String>,
    val noteType: SmartNoteType,
)

private const val PHOTO_HINT = "si hay foto, analiza el espacio y di qué colocar y dónde, refiriéndote a lo que se ve"

val ideaCategories = listOf(
    IdeaCategory(
        "negocio", Icons.Outlined.Storefront, "Negocio",
        listOf("Peluquería / barbería", "Ferretería", "Farmacia", "Puesto de comida", "Restaurante", "Bodega / abasto",
            "Tienda de ropa", "Panadería / pastelería", "Taller mecánico", "Servicio técnico", "Delivery", "Negocio desde casa"),
        "consultor de negocios con mucha experiencia montando y haciendo crecer pequeños negocios en Venezuela",
        """
        ## Concepto
        ## Cliente ideal
        ## Qué lo hace diferente
        ## Productos o servicios (con precios sugeridos)
        ## Local y distribución ($PHOTO_HINT)
        ## Inversión inicial estimada (lista con rangos en USD, marcada como estimación)
        ## Proveedores y operación del día a día
        ## Permisos y trámites en Venezuela
        ## Cómo conseguir los primeros clientes
        ## Riesgos y cómo evitarlos
        ## Plan de los primeros 30 días
        """.trimIndent(),
        listOf("Plan de negocio completo", "Logo e identidad visual", "Imagen de cómo quedaría el local", "Página web o catálogo del negocio"),
        SmartNoteType.BUSINESS,
    ),
    IdeaCategory(
        "web", Icons.Outlined.Language, "Página web",
        listOf("Página de ventas (landing)", "Web de un negocio", "Tienda online / catálogo", "Portafolio", "Blog / noticias", "Menú digital"),
        "estratega digital y diseñador web senior que crea páginas que venden",
        """
        ## Objetivo de la página
        ## Para quién es
        ## Secciones de la página (en orden, con lo que va en cada una)
        ## Textos clave (titular, subtítulo, llamados a la acción)
        ## Estilo visual (colores con código hex, tipografías, tipo de fotos)
        ## Funciones (WhatsApp, formularios, catálogo, pagos...)
        ## Dominio, hosting y herramientas sugeridas
        ## Cómo atraer visitas (SEO y redes)
        ## Pasos para lanzarla
        """.trimIndent(),
        listOf("Desarrollar la web con una IA de código (Claude, ChatGPT, Cursor)", "Diseño visual de la página", "Textos completos de la página"),
        SmartNoteType.PROJECT,
    ),
    IdeaCategory(
        "app", Icons.Outlined.PhoneAndroid, "Aplicación",
        listOf("Reservas / citas", "Pedidos / delivery", "Inventario", "Clientes y ventas", "Educativa", "Comunidad", "Herramienta interna"),
        "product manager y arquitecto de software que convierte ideas en aplicaciones claras y realizables",
        """
        ## Problema que resuelve
        ## Usuarios y qué necesita cada uno
        ## Funciones de la primera versión (lo mínimo para lanzar)
        ## Funciones para después
        ## Pantallas (lista, con lo que muestra cada una)
        ## Flujo principal paso a paso
        ## Datos que guarda
        ## Estilo visual
        ## Tecnología sugerida (simple y económica)
        ## Integraciones (WhatsApp, pago móvil, correo...)
        ## Cómo genera dinero
        ## Fases y tiempos
        """.trimIndent(),
        listOf("Desarrollar la app con una IA de código (Claude, ChatGPT, Cursor)", "Diseño de las pantallas"),
        SmartNoteType.PROJECT,
    ),
    IdeaCategory(
        "diseno", Icons.Outlined.Palette, "Diseño",
        listOf("Logo", "Flyer / publicidad", "Post para redes", "Diseño del local / interiores", "Empaque / etiqueta", "Menú impreso", "Tarjeta de presentación"),
        "director de arte y diseñador senior con ojo comercial",
        """
        ## Objetivo del diseño
        ## Público
        ## Concepto creativo
        ## Estilo y referencias
        ## Paleta de colores (con código hex)
        ## Tipografías
        ## Elementos y composición ($PHOTO_HINT)
        ## Textos que lleva
        ## Formatos y medidas
        ## Variantes a probar
        """.trimIndent(),
        listOf("Generar la imagen con IA (Gemini, ChatGPT, Midjourney)", "Brief para un diseñador"),
        SmartNoteType.IDEA,
    ),
    IdeaCategory(
        "imagen", Icons.Outlined.Image, "Imagen / foto",
        listOf("Foto de producto", "Foto de comida", "Ambiente de un local", "Personaje", "Publicidad", "Portada"),
        "director de fotografía y experto en generar imágenes con IA",
        """
        ## Escena
        ## Sujeto principal
        ## Estilo
        ## Iluminación
        ## Cámara y encuadre
        ## Paleta de colores
        ## Texto dentro de la imagen (si lleva)
        ## Variantes
        """.trimIndent(),
        listOf("Prompt para generar la imagen"),
        SmartNoteType.IDEA,
    ),
    IdeaCategory(
        "asesoria", Icons.Outlined.Explore, "Asesoría",
        listOf("Finanzas personales", "Ventas", "Marketing", "Organización y productividad", "Trámites / legal", "Carrera / trabajo"),
        "asesor experto, práctico y honesto",
        """
        ## Situación actual
        ## Objetivo
        ## Diagnóstico
        ## Opciones (con pros y contras)
        ## Recomendación
        ## Plan de acción paso a paso
        ## Cómo saber que está funcionando
        """.trimIndent(),
        listOf("Profundizar la asesoría con otra IA"),
        SmartNoteType.IDEA,
    ),
    IdeaCategory(
        "contenido", Icons.Outlined.Campaign, "Contenido y redes",
        listOf("Campaña", "Reels / TikTok", "Carrusel", "Calendario de contenido", "Guion de video", "Lanzamiento"),
        "estratega de contenido y redes sociales que sabe qué detiene el scroll",
        """
        ## Objetivo
        ## Público
        ## Mensaje central
        ## Ideas de piezas (con gancho de los primeros 3 segundos)
        ## Formato y frecuencia
        ## Textos y llamados a la acción
        ## Cómo medir si funcionó
        """.trimIndent(),
        listOf("Crear el contenido con otra IA", "Generar las imágenes"),
        SmartNoteType.PROJECT,
    ),
    IdeaCategory(
        "evento", Icons.Outlined.Celebration, "Evento",
        listOf("Cumpleaños", "Lanzamiento", "Taller / curso", "Fiesta", "Feria", "Reunión de trabajo"),
        "organizador de eventos detallista y práctico",
        """
        ## Concepto del evento
        ## Invitados y ambiente
        ## Lugar y montaje ($PHOTO_HINT)
        ## Programa (horario)
        ## Comida, música y decoración
        ## Presupuesto estimado
        ## Lista de tareas con fechas
        """.trimIndent(),
        listOf("Organizar el evento con otra IA", "Invitación / diseño"),
        SmartNoteType.PROJECT,
    ),
    IdeaCategory(
        "otro", Icons.Outlined.Lightbulb, "Otra idea",
        emptyList(),
        "consultor creativo y estratégico",
        """
        ## La idea en claro
        ## Para quién y para qué
        ## Cómo funcionaría
        ## Qué se necesita
        ## Riesgos
        ## Pasos para hacerla realidad
        """.trimIndent(),
        listOf("Desarrollar el proyecto con otra IA"),
        SmartNoteType.IDEA,
    ),
)

data class LabQuestion(val text: String, val options: List<String>, val multiple: Boolean)

/** Prompts del laboratorio. */
object IdeaLabAi {

    private fun header(context: Context, cat: IdeaCategory, sub: String, description: String, photo: Boolean, qa: List<Pair<String, String>>): String {
        val user = ProfileStore.name(context).ifBlank { "el usuario" }
        return buildString {
            appendLine("Actúas como ${cat.expertRole}. Ayudas a $user (en Venezuela) a convertir una idea suelta en un proyecto claro.")
            appendLine("Tu trabajo es SOLO entender y estructurar la idea: no escribas código ni generes imágenes.")
            appendLine()
            appendLine("Categoría: ${cat.label}" + if (sub.isNotBlank()) " › $sub" else "")
            appendLine("Lo que contó: \"${description.ifBlank { "(no dio detalles todavía)" }}\"")
            if (photo) appendLine("Adjuntó una foto (del lugar, producto o una referencia). Obsérvala con detalle.")
            if (qa.isNotEmpty()) {
                appendLine()
                appendLine("Respuestas que ya dio:")
                qa.forEach { (q, a) -> appendLine("- $q → $a") }
            }
        }
    }

    fun questions(
        context: Context, cat: IdeaCategory, sub: String, description: String,
        photo: ByteArray?, qa: List<Pair<String, String>>,
    ): Pair<String, List<LabQuestion>> {
        val prompt = header(context, cat, sub, description, photo != null, qa) + """

            Hazle entre 4 y 6 preguntas CLAVE que te falten para estructurar muy bien esta idea (por ejemplo: presupuesto,
            público, ubicación, estilo, plazos, qué tiene ya, qué le preocupa). No repitas lo que ya respondió.
            Cada pregunta trae entre 3 y 6 opciones cortas para tocar (siempre podrá escribir otra respuesta).
            Lenguaje sencillo y cercano.
            ${if (photo != null) "En \"observacion_foto\" describe en 2-4 frases qué ves y qué oportunidades o problemas notas (espacio, luz, paredes libres, dónde iría cada cosa)." else "Deja \"observacion_foto\" vacío."}

            JSON: {"observacion_foto": "...", "preguntas": [{"pregunta": "...", "opciones": ["...", "..."], "multiple": false}]}
        """.trimIndent()
        val o = JSONObject(AiClient.extractJson(AiClient.generate(context, prompt, json = true, image = photo)))
        val arr = o.optJSONArray("preguntas")
        val list = buildList {
            if (arr != null) for (i in 0 until arr.length()) {
                val q = arr.optJSONObject(i) ?: continue
                val opts = q.optJSONArray("opciones")
                add(
                    LabQuestion(
                        text = q.optString("pregunta"),
                        options = if (opts == null) emptyList() else (0 until opts.length()).map { opts.optString(it) }.filter { it.isNotBlank() },
                        multiple = q.optBoolean("multiple"),
                    ),
                )
            }
        }.filter { it.text.isNotBlank() }
        return o.optString("observacion_foto").trim() to list
    }

    fun structure(
        context: Context, cat: IdeaCategory, sub: String, description: String,
        photo: ByteArray?, qa: List<Pair<String, String>>,
    ): String = AiClient.generate(
        context,
        header(context, cat, sub, description, photo != null, qa) + """

            Con esta información, estructura la idea completa en Markdown, en español. Sé concreto, práctico y realista
            para Venezuela (montos en USD marcados como estimación). No inventes datos del usuario: si algo falta, escríbelo
            como "Supuesto:". ${if (photo != null) "Usa la foto para recomendaciones concretas de qué poner y dónde, refiriéndote a lo que se ve (ej. \"en la pared del fondo, a la izquierda de la puerta\")." else ""}

            Usa exactamente este formato:
            # (un título atractivo para la idea)
            > (resumen de 2-3 líneas)

        """.trimIndent() + "\n" + cat.outline + """

            ## Próximo paso de hoy
            (una sola acción concreta que pueda hacer hoy mismo)

            Devuelve solo el Markdown.
        """.trimIndent(),
        json = false,
        image = photo,
    )

    fun promptDocument(context: Context, idea: String, target: String, photo: ByteArray?): String {
        val isImage = Regex("imagen|logo|diseño|visual|invitación", RegexOption.IGNORE_CASE).containsMatchIn(target)
        return AiClient.generate(
            context,
            """
                Eres experto en escribir prompts y briefs para inteligencias artificiales. A partir de la idea estructurada de abajo,
                escribe un documento Markdown listo para pegarlo en otra IA, con este fin: $target.

                El documento debe tener:
                # (título)
                ## Rol — cómo debe actuar la IA (ej. "Actúa como desarrollador web senior...")
                ## Contexto — todo lo necesario de la idea, con los datos concretos
                ## Objetivo
                ## Requisitos — lista detallada y verificable
                ## Estilo y restricciones
                ## Entregables esperados
                ## Criterios de calidad — cómo saber que quedó bien
                ## Antes de empezar — preguntas que debe hacer si le falta información
                ${if (isImage) "## Prompt de imagen — el prompt final EN INGLÉS dentro de un bloque de código, luego \"Negative prompt\" y la relación de aspecto sugerida." else ""}
                ${if (photo != null) "Hay una foto de referencia: descríbela en el Contexto con detalle, porque la otra IA no la verá." else ""}

                Escribe en español${if (isImage) " (salvo el prompt de imagen)" else ""}. Devuelve solo el Markdown.

                IDEA ESTRUCTURADA:
                $idea
            """.trimIndent(),
            json = false,
            image = photo,
        )
    }
}

private enum class LabStep { CATEGORY, SUB, DESCRIBE, QUESTIONS, RESULT, DOCUMENT }

/** Foto reducida a ~1024 px en JPEG, para enviarla rápido a la IA. */
private fun loadPhoto(context: Context, uri: Uri): Pair<ByteArray, ImageBitmap>? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 1024) sample *= 2
    val bmp = context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
    } ?: return@runCatching null
    val scale = minOf(1f, 1024f / maxOf(bmp.width, bmp.height))
    val small = if (scale < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true) else bmp
    val out = ByteArrayOutputStream()
    small.compress(Bitmap.CompressFormat.JPEG, 82, out)
    out.toByteArray() to small.asImageBitmap()
}.getOrNull()

@Composable
fun IdeaLabDialog(
    initialText: String = "",
    onDismiss: () -> Unit,
    onSaveNote: (SmartNote) -> Unit,
    showMessage: (String) -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val aiEnabled = remember { AiSettings.isEnabled(context) }

    var step by remember { mutableStateOf(LabStep.CATEGORY) }
    var category by remember { mutableStateOf<IdeaCategory?>(null) }
    var sub by remember { mutableStateOf("") }
    var customSub by remember { mutableStateOf("") }
    var description by remember { mutableStateOf(initialText) }
    var photo by remember { mutableStateOf<ByteArray?>(null) }
    var photoPreview by remember { mutableStateOf<ImageBitmap?>(null) }
    var busy by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var photoObservation by remember { mutableStateOf("") }
    val questions = remember { mutableStateListOf<LabQuestion>() }
    val selected = remember { mutableStateMapOf<Int, Set<String>>() }
    val otherAnswer = remember { mutableStateMapOf<Int, String>() }
    val history = remember { mutableStateListOf<Pair<String, String>>() }
    var rounds by remember { mutableStateOf(0) }
    var result by remember { mutableStateOf("") }
    var target by remember { mutableStateOf("") }
    var document by remember { mutableStateOf("") }

    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val loaded = loadPhoto(context, uri)
            if (loaded == null) showMessage("No se pudo leer la foto") else { photo = loaded.first; photoPreview = loaded.second }
        }
    }
    val speaker = rememberSpeaker()
    val dictate = rememberSpeechInput("Cuéntame tu idea con todos los detalles") { text ->
        description = if (description.isBlank()) text.replaceFirstChar { it.uppercase() } else "$description $text"
        if (VoicePrefs.speakBack(context)) speaker.say(withName(context, "Copiado"))
    }
    val saveFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")) { uri ->
        if (uri != null) runCatching {
            context.contentResolver.openOutputStream(uri)?.use { it.write(document.toByteArray(Charsets.UTF_8)) }
            showMessage("Archivo .md guardado")
        }.onFailure { showMessage("No se pudo guardar el archivo") }
    }

    fun answersOfThisRound(): List<Pair<String, String>> = questions.mapIndexedNotNull { i, q ->
        val parts = (selected[i].orEmpty().toList() + listOfNotNull(otherAnswer[i]?.trim()?.takeIf { it.isNotBlank() }))
        if (parts.isEmpty()) null else q.text to parts.joinToString(", ")
    }

    fun runAi(label: String, block: suspend () -> Unit) {
        busy = label; error = null
        scope.launch {
            try { block() } catch (e: Exception) { error = e.message ?: "No se pudo usar la IA" }
            busy = null
        }
    }

    fun askQuestions() {
        val cat = category ?: return
        runAi("Pensando qué preguntarte...") {
            val (obs, qs) = withContext(Dispatchers.IO) {
                IdeaLabAi.questions(context, cat, sub, description, photo, history.toList())
            }
            if (obs.isNotBlank()) photoObservation = obs
            questions.clear(); questions.addAll(qs)
            selected.clear(); otherAnswer.clear()
            rounds++
            step = LabStep.QUESTIONS
        }
    }

    fun buildResult() {
        val cat = category ?: return
        history.addAll(answersOfThisRound())
        runAi("Estructurando tu idea...") {
            result = withContext(Dispatchers.IO) {
                IdeaLabAi.structure(context, cat, sub, description, photo, history.toList())
            }
            step = LabStep.RESULT
        }
    }

    fun buildDocument(t: String) {
        target = t
        runAi("Escribiendo el documento para la otra IA...") {
            document = withContext(Dispatchers.IO) { IdeaLabAi.promptDocument(context, result, t, photo) }
            step = LabStep.DOCUMENT
        }
    }

    fun goBack() {
        error = null
        step = when (step) {
            LabStep.CATEGORY -> { onDismiss(); return }
            LabStep.SUB -> LabStep.CATEGORY
            LabStep.DESCRIBE -> if (category?.subcategories?.isNotEmpty() == true) LabStep.SUB else LabStep.CATEGORY
            LabStep.QUESTIONS -> LabStep.DESCRIBE
            LabStep.RESULT -> LabStep.QUESTIONS
            LabStep.DOCUMENT -> LabStep.RESULT
        }
    }

    val ideaTitle = result.lineSequence().firstOrNull { it.startsWith("# ") }?.removePrefix("# ")?.trim()
        ?: description.take(60).ifBlank { "Idea" }

    Dialog(onDismissRequest = { goBack() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BackHandler { goBack() }
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.Science, null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(8.dp))
                                Text("Laboratorio de ideas", fontWeight = FontWeight.Bold)
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = { goBack() }) {
                                Icon(if (step == LabStep.CATEGORY) Icons.Rounded.Close else Icons.AutoMirrored.Rounded.ArrowBack, "Atrás")
                            }
                        },
                    )
                },
            ) { padding ->
                Column(
                    Modifier
                        .padding(padding)
                        .fillMaxSize()
                        .imePadding()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (!aiEnabled) {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                            Text(
                                "El laboratorio necesita una IA. Ve a Configuración (IA y huella) y pega tu clave de Gemini o Groq.",
                                modifier = Modifier.padding(14.dp),
                            )
                        }
                    }

                    when (step) {
                        LabStep.CATEGORY -> {
                            Text("¿De qué es tu idea?", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            if (description.isNotBlank()) {
                                Text("\"$description\"", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                            }
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                ideaCategories.forEach { cat ->
                                    Card(
                                        modifier = Modifier
                                            .width(150.dp)
                                            .clip(RoundedCornerShape(16.dp))
                                            .clickable {
                                                category = cat; sub = ""; customSub = ""
                                                history.clear(); rounds = 0; photoObservation = ""
                                                step = if (cat.subcategories.isEmpty()) LabStep.DESCRIBE else LabStep.SUB
                                            },
                                        shape = RoundedCornerShape(16.dp),
                                    ) {
                                        Column(Modifier.padding(14.dp)) {
                                            Icon(cat.icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                                            Spacer(Modifier.height(8.dp))
                                            Text(cat.label, fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                }
                            }
                        }

                        LabStep.SUB -> {
                            val cat = category!!
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(cat.icon, null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(8.dp))
                                Text("${cat.label}: ¿de qué tipo?", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            }
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                cat.subcategories.forEach { s ->
                                    FilterChip(selected = sub == s, onClick = { sub = s; customSub = ""; step = LabStep.DESCRIBE }, label = { Text(s) })
                                }
                            }
                            OutlinedTextField(
                                value = customSub,
                                onValueChange = { customSub = it.take(60) },
                                label = { Text("Otro (escríbelo)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Button(enabled = customSub.isNotBlank(), onClick = { sub = customSub.trim(); step = LabStep.DESCRIBE }) { Text("Seguir") }
                        }

                        LabStep.DESCRIBE -> {
                            val cat = category!!
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(cat.icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "${cat.label}${if (sub.isNotBlank()) " › $sub" else ""}",
                                    style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            Text("Cuéntame tu idea", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            OutlinedTextField(
                                value = description,
                                onValueChange = { description = it.take(4000) },
                                placeholder = { Text("Escribe o dicta lo que tienes en mente, aunque sea a medias...") },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp),
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = dictate, modifier = Modifier.weight(1f)) {
                                    Icon(Icons.Rounded.Mic, null); Spacer(Modifier.width(6.dp)); Text("Dictar")
                                }
                                OutlinedButton(onClick = { pickPhoto.launch("image/*") }, modifier = Modifier.weight(1f)) {
                                    Icon(Icons.Rounded.AddAPhoto, null); Spacer(Modifier.width(6.dp)); Text(if (photo == null) "Subir foto" else "Cambiar foto")
                                }
                            }
                            Text(
                                "La foto es opcional: el local, el espacio, tu producto o una referencia. La IA la analiza y te dice qué poner y dónde.",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            photoPreview?.let { img ->
                                Box {
                                    Image(
                                        img, null,
                                        modifier = Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(12.dp)),
                                        contentScale = ContentScale.Crop,
                                    )
                                    IconButton(
                                        onClick = { photo = null; photoPreview = null },
                                        modifier = Modifier.align(Alignment.TopEnd).padding(6.dp)
                                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f), RoundedCornerShape(50)),
                                    ) { Icon(Icons.Rounded.Close, "Quitar foto") }
                                }
                            }
                            Button(
                                enabled = aiEnabled && (description.isNotBlank() || photo != null),
                                onClick = { history.clear(); rounds = 0; askQuestions() },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Icon(Icons.Rounded.AutoAwesome, null); Spacer(Modifier.width(6.dp)); Text("Siguiente: que me haga preguntas")
                            }
                        }

                        LabStep.QUESTIONS -> {
                            Text("Unas preguntas para afinar tu idea", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            if (photoObservation.isNotBlank()) {
                                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                                    Column(Modifier.padding(14.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Outlined.PhotoCamera, null, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text("Lo que veo en tu foto", fontWeight = FontWeight.SemiBold)
                                        }
                                        Spacer(Modifier.height(4.dp))
                                        Text(photoObservation)
                                    }
                                }
                            }
                            Text(
                                "Toca las opciones que apliquen o escribe tu respuesta. Puedes dejar alguna en blanco.",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            questions.forEachIndexed { i, q ->
                                Card(Modifier.fillMaxWidth()) {
                                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text("${i + 1}. ${q.text}", fontWeight = FontWeight.SemiBold)
                                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            q.options.forEach { opt ->
                                                val current = selected[i].orEmpty()
                                                FilterChip(
                                                    selected = opt in current,
                                                    onClick = {
                                                        selected[i] = when {
                                                            opt in current -> current - opt
                                                            q.multiple -> current + opt
                                                            else -> setOf(opt)
                                                        }
                                                    },
                                                    label = { Text(opt) },
                                                )
                                            }
                                        }
                                        OutlinedTextField(
                                            value = otherAnswer[i].orEmpty(),
                                            onValueChange = { otherAnswer[i] = it.take(300) },
                                            placeholder = { Text("Otra respuesta...") },
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                    }
                                }
                            }
                            Button(onClick = { buildResult() }, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Rounded.AutoAwesome, null); Spacer(Modifier.width(6.dp)); Text("Estructurar mi idea")
                            }
                            if (rounds < 3) {
                                OutlinedButton(
                                    onClick = { history.addAll(answersOfThisRound()); askQuestions() },
                                    modifier = Modifier.fillMaxWidth(),
                                ) { Text("Hazme más preguntas") }
                            }
                        }

                        LabStep.RESULT -> {
                            MarkdownText(result)
                            Spacer(Modifier.height(4.dp))
                            Button(
                                onClick = {
                                    val cat = category ?: ideaCategories.last()
                                    val now = System.currentTimeMillis()
                                    onSaveNote(
                                        SmartNote(
                                            id = 0L,
                                            title = ideaTitle.take(120),
                                            content = result,
                                            type = cat.noteType,
                                            project = listOf(cat.label, sub).filter { it.isNotBlank() }.joinToString(" › "),
                                            tags = listOf("laboratorio", cat.key, sub.lowercase()).filter { it.isNotBlank() }.joinToString(", "),
                                            createdAtMillis = now,
                                            updatedAtMillis = now,
                                        ),
                                    )
                                    showMessage("Idea guardada en Notas")
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Icon(Icons.Rounded.Save, null); Spacer(Modifier.width(6.dp)); Text("Guardar en Notas") }

                            Text("Crear documento .md para que otra IA lo desarrolle", fontWeight = FontWeight.SemiBold)
                            val targets = (category?.targets.orEmpty() + "Desarrollar el proyecto completo con otra IA").distinct()
                            targets.forEach { t ->
                                OutlinedButton(onClick = { buildDocument(t) }, modifier = Modifier.fillMaxWidth()) {
                                    Icon(Icons.Rounded.Description, null); Spacer(Modifier.width(6.dp)); Text(t)
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = { clipboard.setText(AnnotatedString(result)); showMessage("Idea copiada") }) {
                                    Icon(Icons.Rounded.ContentCopy, null); Spacer(Modifier.width(4.dp)); Text("Copiar")
                                }
                                TextButton(onClick = { shareText(context, ideaTitle, result) }) {
                                    Icon(Icons.Rounded.Share, null); Spacer(Modifier.width(4.dp)); Text("Compartir")
                                }
                            }
                        }

                        LabStep.DOCUMENT -> {
                            Text("Documento para: $target", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                            Text(
                                "Compártelo o guárdalo como .md y pégalo en la IA que va a desarrollarlo (Claude, ChatGPT, Gemini...).",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { shareText(context, ideaTitle, document) }, modifier = Modifier.weight(1f)) {
                                    Icon(Icons.AutoMirrored.Rounded.Send, null); Spacer(Modifier.width(6.dp)); Text("Compartir")
                                }
                                OutlinedButton(
                                    onClick = { saveFile.launch(ideaTitle.replace(Regex("[^\\p{L}0-9 _-]"), "").trim().ifBlank { "idea" } + ".md") },
                                    modifier = Modifier.weight(1f),
                                ) { Icon(Icons.Rounded.Save, null); Spacer(Modifier.width(6.dp)); Text("Guardar .md") }
                            }
                            TextButton(onClick = { clipboard.setText(AnnotatedString(document)); showMessage("Documento copiado") }) {
                                Icon(Icons.Rounded.ContentCopy, null); Spacer(Modifier.width(4.dp)); Text("Copiar todo")
                            }
                            Card(Modifier.fillMaxWidth()) { Box(Modifier.padding(14.dp)) { MarkdownText(document) } }
                        }
                    }

                    busy?.let {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
                            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
                            Spacer(Modifier.width(12.dp))
                            Text(it)
                        }
                    }
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    Spacer(Modifier.height(40.dp))
                }
            }
        }
    }
}

private fun shareText(context: Context, title: String, text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, title)
        putExtra(Intent.EXTRA_TEXT, text)
    }
    runCatching { context.startActivity(Intent.createChooser(intent, "Compartir").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

/** Muestra Markdown sencillo: títulos, viñetas, citas y **negritas**. */
@Composable
fun MarkdownText(markdown: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        var inCode = false
        markdown.lines().forEach { raw ->
            val line = raw.trimEnd()
            if (line.trim().startsWith("```")) { inCode = !inCode; return@forEach }
            when {
                inCode -> Text(
                    line,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                    modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 8.dp),
                )
                line.startsWith("# ") -> Text(inlineBold(line.removePrefix("# ")), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                line.startsWith("## ") -> {
                    Spacer(Modifier.height(6.dp))
                    Text(inlineBold(line.removePrefix("## ")), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
                line.startsWith("### ") -> Text(inlineBold(line.removePrefix("### ")), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                line.startsWith("> ") -> Text(inlineBold(line.removePrefix("> ")), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Regex("^\\s*[-*•] ").containsMatchIn(line) -> Row {
                    Text("•  ")
                    Text(inlineBold(line.replaceFirst(Regex("^\\s*[-*•] "), "")))
                }
                line.isBlank() -> Spacer(Modifier.height(2.dp))
                else -> Text(inlineBold(line))
            }
        }
    }
}

private fun inlineBold(text: String): AnnotatedString = buildAnnotatedString {
    val parts = text.split("**")
    parts.forEachIndexed { i, part ->
        if (i % 2 == 1) withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(part) } else append(part)
    }
}
