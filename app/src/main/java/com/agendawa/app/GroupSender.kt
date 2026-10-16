package com.agendawa.app

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast

/**
 * Envío directo a grupos de WhatsApp.
 *
 * WhatsApp no deja abrir un grupo con el texto escrito por enlace. Con el permiso de accesibilidad
 * (que el usuario activa una sola vez) la app hace los toques por él, DESPUÉS de que confirma
 * "Enviar a <grupo>" en Agenda WA:
 *  - Grupo con enlace: abre el grupo, escribe el mensaje y toca enviar.
 *  - Grupo sin enlace o varios grupos: abre la lista "Enviar a" de WhatsApp con el mensaje,
 *    marca los grupos (buscándolos por nombre) y toca enviar.
 * El mensaje también queda copiado por si algo falla. Cada paso queda en un registro corto
 * (Configurar alarmas › Envío directo a grupos) para poder revisar qué pasó.
 */
object GroupAutomation {
    private const val WINDOW_MS = 35_000L
    private const val FILL_FALLBACK_MS = 10_000L
    private const val PREFS = "group_send"

    enum class Mode { FILL, PICK }

    class Job(val text: String, names: List<String>, var mode: Mode, val autoSend: Boolean, val startedAt: Long = System.currentTimeMillis()) {
        val pending = names.map { it.trim() }.filter { it.isNotBlank() }.toMutableList()
        val total = pending.size
        var searching = false
        var searchTyped = ""
        var textSet = false
        var lastAction = 0L
        val expired get() = System.currentTimeMillis() - startedAt > WINDOW_MS
    }

    @Volatile var job: Job? = null
    private val logLines = ArrayDeque<String>()

    fun isEnabled(context: Context): Boolean {
        if (GroupSendService.running) return true
        val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        val me = ComponentName(context, GroupSendService::class.java)
        return enabled.split(':').any { ComponentName.unflattenFromString(it.trim()) == me }
    }

    fun openSettings(context: Context) {
        runCatching {
            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    /** Devuelve false si el permiso no está activo (se usa entonces el envío normal). */
    fun send(context: Context, groups: List<WaGroup>, text: String, autoSend: Boolean = true): Boolean {
        if (groups.isEmpty()) return false
        if (!isEnabled(context)) { log(context, "Permiso de accesibilidad apagado: se usa la lista normal"); return false }
        copy(context, text)
        val single = groups.singleOrNull()
        log(context, "Inicio: ${groups.joinToString { it.name }}" + if (single != null && WhatsApp.isGroupLink(single.link)) " (con enlace)" else " (lista)")
        val ok = if (single != null && WhatsApp.isGroupLink(single.link)) {
            job = Job(text, listOf(single.name), Mode.FILL, autoSend)
            openLink(context, single.link) || startPick(context, text, groups.map { it.name }, autoSend)
        } else {
            startPick(context, text, groups.map { it.name }, autoSend)
        }
        if (ok) GroupSendService.kick()
        return ok
    }

    fun startPick(context: Context, text: String, names: List<String>, autoSend: Boolean): Boolean {
        job = Job(text, names, Mode.PICK, autoSend)
        val ok = WhatsApp.shareText(context, text)
        if (!ok) job = null
        GroupSendService.kick()
        return ok
    }

    private fun openLink(context: Context, link: String): Boolean {
        val uri = Uri.parse(link.trim())
        for (pkg in listOf("com.whatsapp", "com.whatsapp.w4b", null)) {
            val ok = runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, uri).apply { if (pkg != null) setPackage(pkg) }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                true
            }.getOrDefault(false)
            if (ok) return true
        }
        return false
    }

    private fun copy(context: Context, text: String) {
        runCatching {
            context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Mensaje de Agenda WA", text))
        }
    }

    internal fun fillFallbackDue(j: Job) = j.mode == Mode.FILL && System.currentTimeMillis() - j.startedAt > FILL_FALLBACK_MS

    fun log(context: Context, line: String) {
        synchronized(logLines) {
            if (logLines.isEmpty()) {
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("log", "")!!
                    .lines().filter { it.isNotBlank() }.forEach { logLines.addLast(it) }
            }
            logLines.addLast(formatDate(System.currentTimeMillis(), "HH:mm:ss") + " " + line)
            while (logLines.size > 40) logLines.removeFirst()
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("log", logLines.joinToString("\n")).apply()
        }
    }

    fun readLog(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("log", "").orEmpty()
}

class GroupSendService : AccessibilityService() {
    companion object {
        @Volatile var running = false
        @Volatile private var instance: GroupSendService? = null

        /** Empieza a revisar la pantalla de WhatsApp aunque no lleguen eventos. */
        fun kick() { instance?.let { s -> s.handler.post { s.poll() } } }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var polling = false
    private var lastScreen = ""

    override fun onServiceConnected() {
        super.onServiceConnected()
        running = true; instance = this
        GroupAutomation.log(this, "Servicio activo")
        toast("Envío directo a grupos activado")
    }

    override fun onDestroy() {
        running = false; instance = null
        super.onDestroy()
    }

    override fun onInterrupt() {}

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (GroupAutomation.job != null) poll()
    }

    /** Revisa la pantalla cada medio segundo mientras haya un envío pendiente. */
    fun poll() {
        if (polling) return
        polling = true
        handler.postDelayed(object : Runnable {
            override fun run() {
                val j = GroupAutomation.job
                if (j == null) { polling = false; return }
                if (j.expired) {
                    GroupAutomation.log(this@GroupSendService, "Tiempo agotado: faltó ${j.pending.joinToString()} (pantalla: $lastScreen)")
                    toast("No encontré el grupo: el mensaje quedó copiado, pégalo a mano")
                    GroupAutomation.job = null; polling = false; return
                }
                runCatching { step(j) }.onFailure { GroupAutomation.log(this@GroupSendService, "Error: ${it.message}") }
                handler.postDelayed(this, 500)
            }
        }, 300)
    }

    /** La ventana de WhatsApp que está en pantalla (no la de Agenda WA que se está cerrando). */
    private fun whatsappRoot(): AccessibilityNodeInfo? {
        rootInActiveWindow?.let { if (it.packageName?.toString()?.startsWith("com.whatsapp") == true) return it }
        return runCatching { windows }.getOrNull().orEmpty()
            .mapNotNull { it.root }
            .firstOrNull { it.packageName?.toString()?.startsWith("com.whatsapp") == true }
    }

    private fun step(j: GroupAutomation.Job) {
        val root = whatsappRoot() ?: return
        val screen = describe(root)
        if (screen != lastScreen) { lastScreen = screen; GroupAutomation.log(this, "Pantalla: $screen") }
        if (System.currentTimeMillis() - j.lastAction < 700) return
        when (j.mode) {
            GroupAutomation.Mode.FILL -> fill(j, root)
            GroupAutomation.Mode.PICK -> pick(j, root)
        }
    }

    /** Dentro del grupo: escribe el mensaje en la caja de texto y toca enviar. */
    private fun fill(j: GroupAutomation.Job, root: AccessibilityNodeInfo) {
        if (j.textSet) {
            val send = sendButton(root)
            if (send != null && click(send)) finish(j, "Enviado en el grupo") else if (send == null) GroupAutomation.log(this, "No veo el botón enviar")
            return
        }
        val entry = findAll(root) { n ->
            n.isEditable && (n.viewIdResourceName?.endsWith("/entry") == true ||
                Regex("mensaje|message", RegexOption.IGNORE_CASE).containsMatchIn(n.hintText?.toString().orEmpty()))
        }.firstOrNull()
        if (entry != null) {
            setText(entry, j.text)
            j.textSet = true; j.lastAction = System.currentTimeMillis()
            GroupAutomation.log(this, "Mensaje escrito en el grupo")
            if (!j.autoSend) finish(j, "Mensaje listo: toca enviar")
            return
        }
        // Algunas versiones muestran primero la ficha del grupo con un botón para abrirlo
        val open = findAll(root) { n ->
            n.text?.toString()?.trim().orEmpty().matches(Regex("(?i)(ver|abrir|ir al) (grupo|chat)|(view|open|go to) (group|chat)"))
        }.firstOrNull()
        if (open != null) {
            GroupAutomation.log(this, "Toco \"${open.text}\"")
            click(open); j.lastAction = System.currentTimeMillis(); return
        }
        if (GroupAutomation.fillFallbackDue(j)) {
            GroupAutomation.log(this, "El enlace no abrió el chat: uso la lista de WhatsApp")
            GroupAutomation.startPick(this, j.text, j.pending, j.autoSend)
        }
    }

    /** En la lista "Enviar a": marca cada grupo por su nombre, buscándolo si no se ve, y toca enviar. */
    private fun pick(j: GroupAutomation.Job, root: AccessibilityNodeInfo) {
        if (j.pending.isNotEmpty()) {
            val name = j.pending.first()
            val texts = findAll(root) { n -> !n.isEditable && !n.text.isNullOrBlank() }
            // Nombre exacto; si no, uno solo que lo contenga (ej. "P01" → "P01 Repórtalo 24")
            val partial = texts.filter { name.length >= 3 && it.text.toString().contains(name, ignoreCase = true) && it.text.length <= name.length + 40 }
            val node = texts.firstOrNull { it.text.toString().trim().equals(name, ignoreCase = true) }
                ?: partial.firstOrNull()?.takeIf { partial.size == 1 || j.searchTyped == name }
            if (node != null) {
                if (click(node)) {
                    GroupAutomation.log(this, "Marcado \"${node.text}\"")
                    j.pending.removeAt(0)
                    j.searching = false; j.searchTyped = ""
                } else GroupAutomation.log(this, "No pude tocar \"${node.text}\"")
                j.lastAction = System.currentTimeMillis()
                return
            }
            // No se ve: se busca por el nombre
            val field = findAll(root) { it.isEditable }.firstOrNull()
            if (field != null) {
                if (j.searchTyped != name) {
                    setText(field, name); j.searchTyped = name; j.lastAction = System.currentTimeMillis()
                    GroupAutomation.log(this, "Busco \"$name\"")
                }
                return
            }
            if (!j.searching) {
                val search = findAll(root) { n ->
                    val d = n.contentDescription?.toString().orEmpty()
                    d.equals("Buscar", true) || d.equals("Search", true) || n.viewIdResourceName?.endsWith("/menuitem_search") == true
                }.firstOrNull()
                if (search != null && click(search)) {
                    j.searching = true; j.lastAction = System.currentTimeMillis()
                    GroupAutomation.log(this, "Abro la búsqueda")
                }
            }
            return
        }
        if (!j.autoSend) { finish(j, if (j.total > 1) "Grupos marcados: toca enviar" else "Grupo marcado: toca enviar"); return }
        val send = sendButton(root)
        if (send != null && click(send)) finish(j, if (j.total > 1) "Enviado a ${j.total} grupos" else "Enviado al grupo")
        else GroupAutomation.log(this, "Grupo marcado, pero no veo el botón enviar")
    }

    private fun finish(j: GroupAutomation.Job, msg: String) {
        GroupAutomation.job = null
        GroupAutomation.log(this, msg)
        toast(msg)
    }

    private fun sendButton(root: AccessibilityNodeInfo): AccessibilityNodeInfo? =
        findAll(root) { n ->
            val id = n.viewIdResourceName.orEmpty()
            val d = n.contentDescription?.toString().orEmpty()
            id.endsWith("/send") || id.endsWith("/send_btn") || d.equals("Enviar", true) || d.equals("Send", true)
        }.lastOrNull()

    /** Resumen corto de la pantalla para el registro: qué botones y textos se ven. */
    private fun describe(root: AccessibilityNodeInfo): String {
        val ids = findAll(root) { !it.viewIdResourceName.isNullOrBlank() }
            .mapNotNull { it.viewIdResourceName?.substringAfter("/") }.distinct().take(8)
        val texts = findAll(root) { !it.text.isNullOrBlank() && !it.isEditable }.map { it.text.toString().take(20) }.distinct().take(6)
        return (ids + texts).joinToString(", ")
    }

    private fun findAll(root: AccessibilityNodeInfo, match: (AccessibilityNodeInfo) -> Boolean): List<AccessibilityNodeInfo> {
        val out = mutableListOf<AccessibilityNodeInfo>()
        val stack = ArrayDeque<AccessibilityNodeInfo>()
        stack.addLast(root)
        var visited = 0
        while (stack.isNotEmpty() && visited < 3000) {
            val n = stack.removeLast(); visited++
            if (n.isVisibleToUser && match(n)) out += n
            for (i in n.childCount - 1 downTo 0) n.getChild(i)?.let { stack.addLast(it) }
        }
        return out
    }

    private fun click(node: AccessibilityNodeInfo): Boolean {
        var n: AccessibilityNodeInfo? = node
        while (n != null) {
            if (n.isClickable) return n.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            n = n.parent
        }
        return false
    }

    private fun setText(node: AccessibilityNodeInfo, text: String) {
        node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val args = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text) }
        node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    private fun toast(text: String) {
        runCatching { Toast.makeText(applicationContext, text, Toast.LENGTH_SHORT).show() }
    }
}
