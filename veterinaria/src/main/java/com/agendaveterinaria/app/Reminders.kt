package com.agendaveterinaria.app

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/** Mensajes de WhatsApp para el dueño. */
object Messages {
    fun greeting(owner: Owner?) = "Hola" + (owner?.name?.split(" ")?.firstOrNull()?.takeIf { it.isNotBlank() }?.let { " $it" } ?: "")

    private fun signature(s: ClinicSettings) = listOf(s.vetName, s.clinicName).filter { it.isNotBlank() }.joinToString(" · ")
        .let { if (it.isBlank()) "" else "\n\n$it" + if (s.phone.isNotBlank()) "\n${s.phone}" else "" }

    fun dose(owner: Owner?, patient: Patient?, product: String, kind: DoseKind, due: Long, s: ClinicSettings): String {
        val what = when (kind) {
            DoseKind.VACUNA -> "la vacuna $product"
            DoseKind.DESPARASITACION_INT -> "la desparasitación ($product)"
            DoseKind.DESPARASITACION_EXT -> "el tratamiento antipulgas/garrapatas ($product)"
        }
        val name = patient?.name ?: "tu mascota"
        return "${greeting(owner)} 🐾 Te recordamos que a $name le toca $what el ${fmtNice(due)} (${whenText(due)}). " +
            "¿Te agendamos una hora? Responde este mensaje y te confirmamos." + signature(s)
    }

    fun appointment(owner: Owner?, patient: Patient?, title: String, due: Long, s: ClinicSettings): String =
        "${greeting(owner)} 🐾 Te recordamos la cita de ${patient?.name ?: "tu mascota"}: $title, el ${fmtNice(due)} a las ${fmt(due, "h:mm a")}. " +
            "Si no puedes asistir, avísanos por aquí." + signature(s)

    fun medication(owner: Owner?, patient: Patient?, text: String, s: ClinicSettings): String =
        "${greeting(owner)} 🐾 Indicaciones para ${patient?.name ?: "tu mascota"}:\n$text" + signature(s)

    fun debt(owner: Owner?, amount: String, detail: String, s: ClinicSettings): String =
        "${greeting(owner)}, te escribimos del consultorio. Queda pendiente un saldo de $amount ($detail). " +
            "Cuando puedas nos avisas. ¡Gracias!" + signature(s)
}

// ---------------------------------------------------------------------------
// Alarmas de los recordatorios
// ---------------------------------------------------------------------------

object ReminderScheduler {
    const val EXTRA_ID = "reminder_id"
    const val CHANNEL = "vet_reminders_v1"

    private fun pi(context: Context, id: Long): PendingIntent = PendingIntent.getBroadcast(
        context, id.hashCode(),
        Intent(context, ReminderReceiver::class.java).putExtra(EXTRA_ID, id),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    fun canExact(context: Context): Boolean =
        Build.VERSION.SDK_INT < 31 || context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    fun schedule(context: Context, r: Reminder) {
        cancel(context, r.id)
        if (r.done || r.notifyMillis <= System.currentTimeMillis()) return
        val am = context.getSystemService(AlarmManager::class.java)
        runCatching {
            if (canExact(context)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, r.notifyMillis, pi(context, r.id))
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, r.notifyMillis, pi(context, r.id))
        }
    }

    fun cancel(context: Context, id: Long) {
        runCatching { context.getSystemService(AlarmManager::class.java).cancel(pi(context, id)) }
        runCatching { NotificationManagerCompat.from(context).cancel(id.hashCode()) }
    }

    fun rescheduleAll(context: Context) {
        VetStore.load(context).reminders.filter { !it.done }.forEach { schedule(context, it) }
    }

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = context.getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL, "Recordatorios del consultorio", NotificationManager.IMPORTANCE_HIGH).apply {
                        description = "Vacunas, citas, controles y cobros"
                        enableVibration(true)
                    },
                )
            }
        }
    }

    fun notificationsAllowed(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun show(context: Context, r: Reminder, data: VetData) {
        createChannel(context)
        if (!notificationsAllowed(context)) return
        val patient = data.patients.firstOrNull { it.id == r.patientId }
        val owner = data.owners.firstOrNull { it.id == (patient?.ownerId ?: r.ownerId) }
        val open = PendingIntent.getActivity(
            context, r.id.hashCode() + 1,
            Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_PATIENT, r.patientId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val who = listOfNotNull(patient?.name, owner?.name).joinToString(" · ")
        val b = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("${r.kind.label}: ${r.title}")
            .setContentText((if (who.isNotBlank()) "$who · " else "") + "${fmtNice(r.dueMillis)} (${whenText(r.dueMillis)})")
            .setStyle(NotificationCompat.BigTextStyle().bigText(
                (if (who.isNotBlank()) "$who\n" else "") + "Toca: ${fmtDateTime(r.dueMillis)} (${whenText(r.dueMillis)})" +
                    if (owner?.phone?.isNotBlank() == true) "\nEl mensaje para el dueño ya está listo." else "",
            ))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(open)
        if (owner?.phone?.isNotBlank() == true) {
            b.addAction(0, "WhatsApp al dueño", action(context, r.id, ReminderActionReceiver.ACTION_WHATSAPP, 2))
        }
        b.addAction(0, "Hecho", action(context, r.id, ReminderActionReceiver.ACTION_DONE, 3))
        b.addAction(0, "Mañana", action(context, r.id, ReminderActionReceiver.ACTION_TOMORROW, 4))
        runCatching { NotificationManagerCompat.from(context).notify(r.id.hashCode(), b.build()) }
    }

    private fun action(context: Context, id: Long, act: String, code: Int): PendingIntent {
        // WhatsApp abre otra app: se hace con una actividad (Android no deja abrir apps desde un receptor)
        return if (act == ReminderActionReceiver.ACTION_WHATSAPP) PendingIntent.getActivity(
            context, id.hashCode() * 10 + code,
            Intent(context, MainActivity::class.java).setAction(act).putExtra(EXTRA_ID, id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        ) else PendingIntent.getBroadcast(
            context, id.hashCode() * 10 + code,
            Intent(context, ReminderActionReceiver::class.java).setAction(act).putExtra(EXTRA_ID, id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(ReminderScheduler.EXTRA_ID, -1L)
        val data = VetStore.load(context)
        val r = data.reminders.firstOrNull { it.id == id } ?: return
        if (!r.done) ReminderScheduler.show(context, r, data)
    }
}

class ReminderActionReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_DONE = "com.agendaveterinaria.app.DONE"
        const val ACTION_TOMORROW = "com.agendaveterinaria.app.TOMORROW"
        const val ACTION_WHATSAPP = "com.agendaveterinaria.app.WHATSAPP"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(ReminderScheduler.EXTRA_ID, -1L)
        val data = VetStore.load(context)
        val r = data.reminders.firstOrNull { it.id == id } ?: return
        val updated = when (intent.action) {
            ACTION_DONE -> r.copy(done = true)
            ACTION_TOMORROW -> r.copy(notifyMillis = System.currentTimeMillis() + 86_400_000L)
            else -> return
        }
        VetStore.save(context, data.copy(reminders = data.reminders.map { if (it.id == id) updated else it }))
        ReminderScheduler.cancel(context, id)
        ReminderScheduler.schedule(context, updated)
        toast(context, if (updated.done) "Marcado como hecho" else "Te lo recuerdo mañana")
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        ReminderScheduler.rescheduleAll(context)
    }
}
