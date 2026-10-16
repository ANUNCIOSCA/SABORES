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
 * (que el usuario activa una sola vez) la app hace los toques por él:
 *  - Grupo con enlace: abre el grupo y escribe el mensaje en la caja de texto. Solo falta tocar enviar.
 *  - Grupo sin enlace o varios grupos: abre WhatsApp con el mensaje y marca los grupos en la lista
 *    (buscándolos por nombre si no se ven). Solo falta tocar la flecha de enviar.
 * El mensaje también queda copiado por si algo falla: se puede pegar a mano.
 */
object GroupAutomation {
    private const val WINDOW_MS = 30_000L
    private const val FILL_FALLBACK_MS = 9_000L

    enum class Mode { FILL, PICK }

    class Job(val text: String, names: List<String>, var mode: Mode, val startedAt: Long = System.currentTimeMillis()) {
        val pending = names.map { it.trim() }.filter { it.isNotBlank() }.toMutableList()
        val total = pending.size
        var searching = false
        var searchTyped = ""
        var lastAction = 0L
        val expired get() = System.currentTimeMillis() - startedAt > WINDOW_MS
    }

    @Volatile var job: Job? = null

    fun isEnabled(context: Context): Boolean {
        val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        val me = ComponentName(context, GroupSendService::class.java)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
    }

    fun openSettings(context: Context) {
        runCatching {
            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    /** Devuelve false si el permiso no está activo (se usa entonces el envío normal). */
    fun send(context: Context, groups: List<WaGroup>, text: String): Boolean {
        if (groups.isEmpty() || !isEnabled(context)) return false
        copy(context, text)
        val single = groups.singleOrNull()
        return if (single != null && WhatsApp.isGroupLink(single.link)) {
            job = Job(text, listOf(single.name), Mode.FILL)
            openLink(context, single.link) || startPick(context, text, groups.map { it.name })
        } else {
            startPick(context, text, groups.map { it.name })
        }
    }

    fun startPick(context: Context, text: String, names: List<String>): Boolean {
        job = Job(text, names, Mode.PICK)
        val ok = WhatsApp.shareText(context, text)
        if (!ok) job = null
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
}

class GroupSendService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())

    override fun onServiceConnected() {
        super.onServiceConnected()
        toast("Envío directo a grupos activado")
    }

    override fun onInterrupt() {}

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val j = GroupAutomation.job ?: return
        if (j.expired) { GroupAutomation.job = null; return }
        val pkg = event?.packageName?.toString() ?: return
        if (!pkg.startsWith("com.whatsapp")) return
        // Los cambios de pantalla llegan en ráfaga: se espera un momento a que WhatsApp termine de dibujar
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ step() }, 250)
    }

    private fun step() {
        val j = GroupAutomation.job ?: return
        if (j.expired) { GroupAutomation.job = null; return }
        val root = rootInActiveWindow ?: return
        if (!root.packageName.toString().startsWith("com.whatsapp")) return
        when (j.mode) {
            GroupAutomation.Mode.FILL -> fill(j, root)
            GroupAutomation.Mode.PICK -> pick(j, root)
        }
    }

    /** Dentro del grupo: escribe el mensaje en la caja de texto. */
    private fun fill(j: GroupAutomation.Job, root: AccessibilityNodeInfo) {
        val entry = findAll(root) { n ->
            n.isEditable && (n.viewIdResourceName?.endsWith("/entry") == true ||
                Regex("mensaje|message", RegexOption.IGNORE_CASE).containsMatchIn(n.hintText?.toString().orEmpty()))
        }.firstOrNull()
        if (entry != null) {
            setText(entry, j.text)
            GroupAutomation.job = null
            toast("Mensaje listo: toca enviar")
            return
        }
        // Algunas versiones muestran primero la ficha del grupo con un botón para abrirlo
        val open = findAll(root) { n ->
            val t = n.text?.toString()?.trim().orEmpty()
            t.matches(Regex("(?i)(ver|abrir|ir al) grupo|(view|open|go to) group|ver chat|view chat"))
        }.firstOrNull()
        if (open != null && System.currentTimeMillis() - j.lastAction > 1500) {
            click(open); j.lastAction = System.currentTimeMillis(); return
        }
        // Si el enlace no lleva al chat, se usa la lista de WhatsApp
        if (GroupAutomation.fillFallbackDue(j)) {
            GroupAutomation.startPick(this, j.text, j.pending)
        }
    }

    /** En la lista "Enviar a": marca cada grupo por su nombre, buscándolo si no se ve. */
    private fun pick(j: GroupAutomation.Job, root: AccessibilityNodeInfo) {
        while (j.pending.isNotEmpty()) {
            val name = j.pending.first()
            val texts = findAll(root) { n -> !n.isEditable && !n.text.isNullOrBlank() }
            // Nombre exacto; si no, uno solo que lo contenga (ej. "P01" → "P01 Repórtalo 24")
            val partial = texts.filter { name.length >= 3 && it.text.toString().contains(name, ignoreCase = true) && it.text.length <= name.length + 40 }
            val node = texts.firstOrNull { it.text.toString().trim().equals(name, ignoreCase = true) }
                ?: partial.firstOrNull()?.takeIf { partial.size == 1 || j.searchTyped == name }
            if (node != null) {
                if (click(node)) {
                    j.pending.removeAt(0)
                    j.searching = false; j.searchTyped = ""
                    j.lastAction = System.currentTimeMillis()
                    continue
                }
                return
            }
            // No se ve: se busca por el nombre
            val field = findAll(root) { it.isEditable }.firstOrNull()
            if (field != null) {
                if (j.searchTyped != name) {
                    setText(field, name); j.searchTyped = name; j.lastAction = System.currentTimeMillis()
                }
                return
            }
            if (!j.searching) {
                val search = findAll(root) { n ->
                    val d = n.contentDescription?.toString().orEmpty()
                    d.equals("Buscar", true) || d.equals("Search", true) || n.viewIdResourceName?.endsWith("/menuitem_search") == true
                }.firstOrNull()
                if (search != null && click(search)) { j.searching = true; j.lastAction = System.currentTimeMillis() }
            }
            return
        }
        GroupAutomation.job = null
        toast(if (j.total > 1) "Grupos marcados: toca la flecha de enviar" else "Grupo marcado: toca la flecha de enviar")
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
