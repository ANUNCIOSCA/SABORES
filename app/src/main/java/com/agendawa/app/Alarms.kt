package com.agendawa.app

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.net.URLEncoder
import java.util.Calendar

// ---------------------------------------------------------------------------
// Utilidades de WhatsApp
// ---------------------------------------------------------------------------

object WhatsApp {
    fun cleanPhone(phone: String): String = phone.filter { it.isDigit() }

    fun link(phone: String, text: String): String {
        val encoded = URLEncoder.encode(text, "UTF-8").replace("+", "%20")
        val digits = cleanPhone(phone)
        return if (digits.isNotEmpty()) "https://wa.me/$digits?text=$encoded" else "https://wa.me/?text=$encoded"
    }

    fun intent(phone: String, text: String): Intent =
        Intent(Intent.ACTION_VIEW, Uri.parse(link(phone, text))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun open(context: Context, phone: String, text: String): Boolean =
        runCatching { context.startActivity(intent(phone, text)); true }.getOrDefault(false)
}

/** Llamadas: abre el marcador con el número listo (solo falta tocar llamar). */
object PhoneCall {
    fun isCall(task: AgendaTask): Boolean = !task.isWhatsApp && task.phone.isNotBlank()

    fun intent(phone: String): Intent =
        Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + phone.filter { it.isDigit() || it == '+' }))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun open(context: Context, phone: String): Boolean =
        runCatching { context.startActivity(intent(phone)); true }.getOrDefault(false)
}

// ---------------------------------------------------------------------------
// Programación de alarmas
// ---------------------------------------------------------------------------

object ReminderScheduler {
    const val EXTRA_TASK_ID = "task_id"
    const val EXTRA_PRE_ALERT = "pre_alert"
    const val PRE_ALERT_MINUTES = 5L
    private const val PRE_ALERT_REQUEST_OFFSET = 947000

    private fun pendingIntent(context: Context, taskId: Long, preAlert: Boolean): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra(EXTRA_TASK_ID, taskId)
            putExtra(EXTRA_PRE_ALERT, preAlert)
        }
        val code = if (preAlert) taskId.hashCode() + PRE_ALERT_REQUEST_OFFSET else taskId.hashCode()
        return PendingIntent.getBroadcast(context, code, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    fun canScheduleExact(context: Context): Boolean {
        val am = context.getSystemService(AlarmManager::class.java)
        return Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
    }

    fun schedule(context: Context, task: AgendaTask): ScheduleResult {
        cancel(context, task.id)
        if (task.completed || task.dateTimeMillis <= System.currentTimeMillis()) return ScheduleResult.SKIPPED
        val am = context.getSystemService(AlarmManager::class.java)
        val exact = canScheduleExact(context)

        val preAlertAt = task.dateTimeMillis - PRE_ALERT_MINUTES * 60_000
        if (preAlertAt > System.currentTimeMillis()) {
            runCatching {
                val pi = pendingIntent(context, task.id, true)
                if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, preAlertAt, pi)
                else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, preAlertAt, pi)
            }
        }

        val main = pendingIntent(context, task.id, false)
        if (!exact) return scheduleApproximate(am, task.dateTimeMillis, main)
        return runCatching {
            val show = PendingIntent.getActivity(
                context,
                task.id.hashCode() * 31 + 99,
                Intent(context, MainActivity::class.java).putExtra(EXTRA_TASK_ID, task.id)
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            am.setAlarmClock(AlarmManager.AlarmClockInfo(task.dateTimeMillis, show), main)
            ScheduleResult.EXACT
        }.getOrElse { scheduleApproximate(am, task.dateTimeMillis, main) }
    }

    private fun scheduleApproximate(am: AlarmManager, at: Long, pi: PendingIntent): ScheduleResult =
        runCatching { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi); ScheduleResult.APPROXIMATE }
            .getOrDefault(ScheduleResult.SKIPPED)

    fun cancel(context: Context, taskId: Long) {
        val am = context.getSystemService(AlarmManager::class.java)
        am.cancel(pendingIntent(context, taskId, false))
        am.cancel(pendingIntent(context, taskId, true))
        NotificationHelper.cancelAllForTask(context, taskId)
    }

    fun rescheduleAll(context: Context) {
        TaskStore.load(context)
            .filter { !it.completed && it.dateTimeMillis > System.currentTimeMillis() }
            .forEach { schedule(context, it) }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(ReminderScheduler.EXTRA_TASK_ID, -1L)
        if (id == -1L) return
        val task = TaskStore.find(context, id) ?: return
        if (task.completed) return
        if (intent.getBooleanExtra(ReminderScheduler.EXTRA_PRE_ALERT, false)) {
            NotificationHelper.showPreAlert(context, task)
        } else {
            val started = runCatching { AlarmSoundService.start(context, task.id) }.isSuccess
            if (!started) NotificationHelper.showReminder(context, task)
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> {
                ReminderScheduler.rescheduleAll(context)
                ExpenseReminder.apply(context)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Notificaciones
// ---------------------------------------------------------------------------

object NotificationHelper {
    const val ALARM_CHANNEL_ID = "agenda_wa_alarm_v4"
    const val PRE_ALERT_CHANNEL_ID = "agenda_wa_pre_alert_v1"
    const val EXPENSE_CHANNEL_ID = "agenda_wa_expenses_v1"
    private const val PRE_ALERT_ID_OFFSET = 531000
    const val EXPENSE_NOTIFICATION_ID = 772001

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        val alarm = NotificationChannel(ALARM_CHANNEL_ID, "Alarmas de Agenda WA", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Alarmas sonoras y recordatorios principales de Agenda WA"
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        }
        val pre = NotificationChannel(PRE_ALERT_CHANNEL_ID, "Avisos previos de Agenda WA", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Avisos que suenan cinco minutos antes de cada actividad"
            vibrationPattern = longArrayOf(0, 300, 200, 300)
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
        }
        val expenses = NotificationChannel(EXPENSE_CHANNEL_ID, "Mis Gastos", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Recordatorio diario para registrar tus gastos e ingresos"
        }
        nm.createNotificationChannel(alarm)
        nm.createNotificationChannel(pre)
        nm.createNotificationChannel(expenses)
    }

    fun notificationsAllowed(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    private fun preAlertId(taskId: Long) = taskId.hashCode() + PRE_ALERT_ID_OFFSET

    private fun actionIntent(context: Context, task: AgendaTask, action: String, code: Int): PendingIntent {
        val intent = Intent(context, AlarmActionReceiver::class.java).apply {
            this.action = action
            putExtra(ReminderScheduler.EXTRA_TASK_ID, task.id)
        }
        return PendingIntent.getBroadcast(
            context, task.id.hashCode() * 10 + code, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun contentText(task: AgendaTask): String = buildString {
        append(task.title)
        if (task.message.isNotBlank() && task.message != task.title) append("\n\n").append(task.message)
    }

    private fun whatsappAction(context: Context, task: AgendaTask, builder: NotificationCompat.Builder) {
        if (!task.isWhatsApp) return
        val text = task.message.ifBlank { task.title }
        val pi = PendingIntent.getActivity(
            context, task.id.hashCode() * 10 + 7, WhatsApp.intent(task.phone, text),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        builder.addAction(0, "Abrir WhatsApp", pi)
    }

    private fun callAction(context: Context, task: AgendaTask, builder: NotificationCompat.Builder) {
        if (!PhoneCall.isCall(task)) return
        val pi = PendingIntent.getActivity(
            context, task.id.hashCode() * 10 + 8, PhoneCall.intent(task.phone),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        builder.addAction(0, "Llamar", pi)
    }

    fun buildAlarmNotification(context: Context, task: AgendaTask): android.app.Notification {
        createChannels(context)
        val fullScreen = PendingIntent.getActivity(
            context, task.id.hashCode(),
            Intent(context, AlarmActivity::class.java)
                .putExtra(ReminderScheduler.EXTRA_TASK_ID, task.id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val title = ProfileStore.personalized(context, if (task.isWhatsApp) "tienes un WhatsApp pendiente" else "tienes un recordatorio")
        val builder = NotificationCompat.Builder(context, ALARM_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(task.title)
            .setSubText("Toca o expande para responder")
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText(task)))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(fullScreen)
            .setFullScreenIntent(fullScreen, true)
            .addAction(0, "APAGAR", actionIntent(context, task, AlarmActionReceiver.ACTION_STOP, 1))
            .addAction(0, "Recordarme en 5 min", actionIntent(context, task, AlarmActionReceiver.ACTION_SNOOZE, 2))
        whatsappAction(context, task, builder)
        callAction(context, task, builder)
        if (!task.isWhatsApp && !PhoneCall.isCall(task)) builder.addAction(0, "Eliminar aviso", actionIntent(context, task, AlarmActionReceiver.ACTION_DELETE, 3))
        return builder.build()
    }

    fun showReminder(context: Context, task: AgendaTask) {
        if (!notificationsAllowed(context)) return
        runCatching { NotificationManagerCompat.from(context).notify(task.id.hashCode(), buildAlarmNotification(context, task)) }
    }

    fun showPreAlert(context: Context, task: AgendaTask) {
        createChannels(context)
        if (!notificationsAllowed(context)) return
        val open = PendingIntent.getActivity(
            context, preAlertId(task.id),
            Intent(context, MainActivity::class.java).putExtra(ReminderScheduler.EXTRA_TASK_ID, task.id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(context, PRE_ALERT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(ProfileStore.personalized(context, "tu recordatorio es en 5 minutos"))
            .setContentText(task.title)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText(task)))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(open)
            .addAction(0, "Recordarme en 5 min", actionIntent(context, task, AlarmActionReceiver.ACTION_SNOOZE, 2))
        whatsappAction(context, task, builder)
        callAction(context, task, builder)
        builder.addAction(0, "Eliminar aviso", actionIntent(context, task, AlarmActionReceiver.ACTION_DELETE, 3))
        runCatching { NotificationManagerCompat.from(context).notify(preAlertId(task.id), builder.build()) }
    }

    fun cancelAllForTask(context: Context, taskId: Long) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.cancel(taskId.hashCode())
        nm.cancel(preAlertId(taskId))
    }

    fun openSoundSettings(context: Context) {
        createChannels(context)
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    .putExtra(Settings.EXTRA_CHANNEL_ID, PRE_ALERT_CHANNEL_ID)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.onFailure {
            runCatching { context.startActivity(Intent(Settings.ACTION_SOUND_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
    }

    fun openAppNotificationSettings(context: Context) {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Permisos de alarma
// ---------------------------------------------------------------------------

object AlarmCapabilities {
    fun canUseFullScreen(context: Context): Boolean =
        Build.VERSION.SDK_INT < 34 || context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()

    fun openExactAlarmSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= 31) {
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }.onFailure { openAppDetails(context) }
        } else openAppDetails(context)
    }

    fun openFullScreenSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= 34) {
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${context.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }.onFailure { openAppDetails(context) }
        } else openAppDetails(context)
    }

    fun openAppDetails(context: Context) {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Sonido de alarma (servicio en primer plano)
// ---------------------------------------------------------------------------

class AlarmSoundService : Service() {
    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var activeTaskId: Long = -1L

    companion object {
        private const val ACTION_START = "com.agendawa.app.ALARM_START"
        private const val ACTION_STOP = "com.agendawa.app.ALARM_STOP"

        fun start(context: Context, taskId: Long) {
            val intent = Intent(context, AlarmSoundService::class.java)
                .setAction(ACTION_START)
                .putExtra(ReminderScheduler.EXTRA_TASK_ID, taskId)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            runCatching { context.startService(Intent(context, AlarmSoundService::class.java).setAction(ACTION_STOP)) }
            runCatching { context.stopService(Intent(context, AlarmSoundService::class.java)) }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopAlarm()
            return START_NOT_STICKY
        }
        val id = intent?.getLongExtra(ReminderScheduler.EXTRA_TASK_ID, -1L) ?: -1L
        val task = if (id != -1L) TaskStore.find(this, id) else null
        if (task == null) {
            stopAlarm()
            return START_NOT_STICKY
        }
        activeTaskId = task.id
        val notification = NotificationHelper.buildAlarmNotification(this, task)
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(task.id.hashCode(), notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(task.id.hashCode(), notification)
        }
        startPlayback()
        startVibration()
        openAlarmScreen(task.id)
        return START_NOT_STICKY
    }

    private fun openAlarmScreen(taskId: Long) {
        runCatching {
            startActivity(
                Intent(this, AlarmActivity::class.java)
                    .putExtra(ReminderScheduler.EXTRA_TASK_ID, taskId)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            )
        }
    }

    private fun startPlayback() {
        if (player != null) return
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            ?: return
        player = runCatching {
            MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                setDataSource(this@AlarmSoundService, uri)
                isLooping = true
                prepare()
                start()
            }
        }.getOrNull()
    }

    @Suppress("DEPRECATION")
    private fun startVibration() {
        val v = getSystemService(Vibrator::class.java) ?: return
        vibrator = v
        runCatching { v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 800, 600), 0)) }
    }

    private fun releasePlayback() {
        runCatching { player?.stop() }
        runCatching { player?.release() }
        player = null
        runCatching { vibrator?.cancel() }
        vibrator = null
    }

    private fun stopAlarm() {
        releasePlayback()
        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
        stopSelf()
    }

    override fun onDestroy() {
        releasePlayback()
        super.onDestroy()
    }
}

// ---------------------------------------------------------------------------
// Acciones de la alarma (apagar, posponer, eliminar, copiar)
// ---------------------------------------------------------------------------

object AlarmActions {
    fun cancelNotification(context: Context, taskId: Long) {
        context.getSystemService(NotificationManager::class.java).cancel(taskId.hashCode())
    }

    private fun stopCurrentAlarm(context: Context, taskId: Long) {
        ReminderScheduler.cancel(context, taskId)
        AlarmSoundService.stop(context)
        cancelNotification(context, taskId)
    }

    fun stopAndComplete(context: Context, taskId: Long) {
        val tasks = TaskStore.load(context)
        TaskStore.save(context, tasks.map { if (it.id == taskId) it.copy(completed = true) else it })
        stopCurrentAlarm(context, taskId)
        toast(context, "Alarma apagada")
    }

    fun snooze(context: Context, taskId: Long, minutes: Long = 5) {
        val tasks = TaskStore.load(context)
        val task = tasks.firstOrNull { it.id == taskId }
        AlarmSoundService.stop(context)
        cancelNotification(context, taskId)
        if (task != null) {
            val updated = task.copy(dateTimeMillis = System.currentTimeMillis() + 60_000 * minutes, completed = false)
            TaskStore.save(context, tasks.map { if (it.id == taskId) updated else it })
            ReminderScheduler.schedule(context, updated)
            toast(context, "Te lo recordaré en $minutes minutos")
        }
    }

    fun delete(context: Context, taskId: Long) {
        TaskStore.save(context, TaskStore.load(context).filterNot { it.id == taskId })
        stopCurrentAlarm(context, taskId)
        toast(context, "Aviso eliminado")
    }

    fun copyMessage(context: Context, taskId: Long) {
        val task = TaskStore.find(context, taskId) ?: return
        val cm = context.getSystemService(ClipboardManager::class.java)
        cm.setPrimaryClip(ClipData.newPlainText("Mensaje de Agenda WA", task.message.ifBlank { task.title }))
        toast(context, "Mensaje copiado")
    }

    private fun toast(context: Context, text: String) {
        runCatching { Toast.makeText(context.applicationContext, text, Toast.LENGTH_SHORT).show() }
    }
}

class AlarmActionReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_STOP = "com.agendawa.app.ACTION_STOP"
        const val ACTION_SNOOZE = "com.agendawa.app.ACTION_SNOOZE"
        const val ACTION_DELETE = "com.agendawa.app.ACTION_DELETE"
        const val ACTION_COPY = "com.agendawa.app.ACTION_COPY"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(ReminderScheduler.EXTRA_TASK_ID, -1L)
        if (id == -1L) return
        when (intent.action) {
            ACTION_STOP -> AlarmActions.stopAndComplete(context, id)
            ACTION_SNOOZE -> AlarmActions.snooze(context, id, 5)
            ACTION_DELETE -> AlarmActions.delete(context, id)
            ACTION_COPY -> AlarmActions.copyMessage(context, id)
        }
    }
}

// ---------------------------------------------------------------------------
// Recordatorio diario de Mis Gastos
// ---------------------------------------------------------------------------

object ExpenseReminder {
    private const val REQUEST = 772000

    private fun pending(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context, REQUEST, Intent(context, ExpenseReminderReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    fun apply(context: Context) {
        val settings = ExpenseSettingsStore.load(context)
        val am = context.getSystemService(AlarmManager::class.java)
        am.cancel(pending(context))
        if (!settings.dailyReminderEnabled) return
        val next = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, settings.reminderHour)
            set(Calendar.MINUTE, settings.reminderMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
        }
        runCatching { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.timeInMillis, pending(context)) }
    }
}

class ExpenseReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        NotificationHelper.createChannels(context)
        if (NotificationHelper.notificationsAllowed(context)) {
            val open = PendingIntent.getActivity(
                context, NotificationHelper.EXPENSE_NOTIFICATION_ID,
                Intent(context, MainActivity::class.java)
                    .putExtra(MainActivity.EXTRA_OPEN_SECTION, "gastos")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val n = NotificationCompat.Builder(context, NotificationHelper.EXPENSE_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(ProfileStore.personalized(context, "¿ya registraste tus gastos de hoy?"))
                .setContentText("Toca para anotarlos en Mis Gastos.")
                .setAutoCancel(true)
                .setContentIntent(open)
                .build()
            runCatching { NotificationManagerCompat.from(context).notify(NotificationHelper.EXPENSE_NOTIFICATION_ID, n) }
        }
        ExpenseReminder.apply(context)
    }
}
