package com.agendaveterinaria.app

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Estado de la app: lee y guarda en el teléfono, y programa los recordatorios. */
class VetState(private val context: Context) {
    var data by mutableStateOf(VetStore.load(context))
        private set

    private var lastId = 0L
    fun newId(): Long {
        var id = System.currentTimeMillis()
        if (id <= lastId) id = lastId + 1
        lastId = id
        return id
    }

    fun reload() { data = VetStore.load(context) }

    private fun commit(d: VetData) {
        data = d
        runCatching { VetStore.save(context, d) }
    }

    // ------------------------------------------------------------ consultas
    val settings get() = data.settings
    fun owner(id: Long) = data.owners.firstOrNull { it.id == id }
    fun patient(id: Long) = data.patients.firstOrNull { it.id == id }
    fun ownerOf(p: Patient?) = p?.let { owner(it.ownerId) }
    fun patientsOf(ownerId: Long) = data.patients.filter { it.ownerId == ownerId }
    fun weightsOf(id: Long) = data.weights.filter { it.patientId == id }.sortedByDescending { it.dateMillis }
    fun lastWeight(id: Long): Double? = weightsOf(id).firstOrNull()?.kg
    fun visitsOf(id: Long) = data.visits.filter { it.patientId == id }.sortedByDescending { it.dateMillis }
    fun dosesOf(id: Long) = data.doses.filter { it.patientId == id }.sortedByDescending { it.dateMillis }
    fun remindersOf(id: Long) = data.reminders.filter { it.patientId == id }.sortedBy { it.dueMillis }
    fun chargesOf(ownerId: Long) = data.charges.filter { it.ownerId == ownerId }.sortedByDescending { it.dateMillis }
    fun pendingOf(ownerId: Long) = chargesOf(ownerId).sumOf { it.pending }

    // ------------------------------------------------------------ dueños
    fun saveOwner(o: Owner): Owner {
        val x = if (o.id == 0L) o.copy(id = newId()) else o
        commit(data.copy(owners = data.owners.filterNot { it.id == x.id } + x))
        return x
    }

    fun deleteOwner(o: Owner) {
        patientsOf(o.id).forEach { deletePatient(it) }
        commit(data.copy(owners = data.owners.filterNot { it.id == o.id }, charges = data.charges.filterNot { it.ownerId == o.id }))
    }

    // ------------------------------------------------------------ pacientes
    fun savePatient(p: Patient): Patient {
        val x = if (p.id == 0L) p.copy(id = newId()) else p
        commit(data.copy(patients = data.patients.filterNot { it.id == x.id } + x))
        return x
    }

    fun deletePatient(p: Patient) {
        data.reminders.filter { it.patientId == p.id }.forEach { ReminderScheduler.cancel(context, it.id) }
        commit(
            data.copy(
                patients = data.patients.filterNot { it.id == p.id },
                weights = data.weights.filterNot { it.patientId == p.id },
                visits = data.visits.filterNot { it.patientId == p.id },
                doses = data.doses.filterNot { it.patientId == p.id },
                reminders = data.reminders.filterNot { it.patientId == p.id },
            ),
        )
    }

    fun addWeight(patientId: Long, kg: Double, dateMillis: Long = System.currentTimeMillis(), note: String = "") {
        if (kg <= 0) return
        commit(data.copy(weights = data.weights + WeightEntry(newId(), patientId, dateMillis, kg, note)))
    }

    fun deleteWeight(w: WeightEntry) = commit(data.copy(weights = data.weights.filterNot { it.id == w.id }))

    // ------------------------------------------------------------ historia médica
    /**
     * Guarda la consulta. Si trae peso, lo agrega al historial de peso; si trae cobro, crea la cuenta;
     * si trae próxima cita, crea el recordatorio.
     */
    fun saveVisit(v: Visit, charge: Double? = null, paidNow: Boolean = false, nextMillis: Long = 0L, nextTitle: String = ""): Visit {
        val x = if (v.id == 0L) v.copy(id = newId()) else v
        commit(data.copy(visits = data.visits.filterNot { it.id == x.id } + x))
        if (v.id == 0L && x.weightKg != null) addWeight(x.patientId, x.weightKg, x.dateMillis, "En ${x.type.label.lowercase()}")
        val p = patient(x.patientId)
        if (charge != null && charge > 0 && p != null) {
            saveCharge(Charge(0L, p.ownerId, p.id, x.dateMillis, "${x.type.label}: ${x.reason.ifBlank { p.name }}".take(80), charge, if (paidNow) charge else 0.0, x.id))
        }
        if (nextMillis > 0 && p != null) {
            val title = nextTitle.ifBlank { "Control de ${p.name}" }
            saveReminder(
                Reminder(
                    id = 0L, patientId = p.id, ownerId = p.ownerId, kind = ReminderKind.CONTROL, title = title,
                    message = Messages.appointment(ownerOf(p), p, title, nextMillis, settings),
                    dueMillis = nextMillis, notifyMillis = nextMillis - 60 * 60_000L,
                ),
            )
        }
        return x
    }

    fun deleteVisit(v: Visit) = commit(data.copy(visits = data.visits.filterNot { it.id == v.id }))

    // ------------------------------------------------------------ vacunas y desparasitaciones
    /** Guarda la dosis y deja listo (o actualiza) el recordatorio de la próxima. */
    fun saveDose(d: Dose): Dose {
        val x = if (d.id == 0L) d.copy(id = newId()) else d
        commit(data.copy(doses = data.doses.filterNot { it.id == x.id } + x))
        // El recordatorio de esta dosis se rehace
        data.reminders.filter { it.doseId == x.id }.forEach { ReminderScheduler.cancel(context, it.id) }
        commit(data.copy(reminders = data.reminders.filterNot { it.doseId == x.id && !it.done }))
        val p = patient(x.patientId)
        if (x.nextMillis > 0 && p != null) {
            val due = atHour(x.nextMillis, 9)
            var notify = atHour(plusDays(x.nextMillis, -settings.daysBefore), settings.notifyHour)
            if (notify < System.currentTimeMillis() && due > System.currentTimeMillis()) notify = System.currentTimeMillis() + 60_000L
            val kind = if (x.kind == DoseKind.VACUNA) ReminderKind.VACUNA else ReminderKind.DESPARASITACION
            saveReminder(
                Reminder(
                    id = 0L, patientId = p.id, ownerId = p.ownerId, kind = kind,
                    title = "${x.product} de ${p.name}", message = Messages.dose(ownerOf(p), p, x.product, x.kind, due, settings),
                    dueMillis = due, notifyMillis = notify, doseId = x.id,
                ),
            )
        }
        // Al aplicar una dosis, se da por hecho el recordatorio anterior del mismo producto
        val older = data.reminders.filter {
            it.patientId == x.patientId && !it.done && it.doseId != x.id && it.doseId != 0L &&
                data.doses.firstOrNull { d0 -> d0.id == it.doseId }?.product.equals(x.product, ignoreCase = true)
        }
        older.forEach { setDone(it, true) }
        return x
    }

    fun deleteDose(d: Dose) {
        data.reminders.filter { it.doseId == d.id }.forEach { ReminderScheduler.cancel(context, it.id) }
        commit(data.copy(doses = data.doses.filterNot { it.id == d.id }, reminders = data.reminders.filterNot { it.doseId == d.id }))
    }

    // ------------------------------------------------------------ recordatorios
    fun saveReminder(r: Reminder): Reminder {
        val x = if (r.id == 0L) r.copy(id = newId()) else r
        commit(data.copy(reminders = data.reminders.filterNot { it.id == x.id } + x))
        ReminderScheduler.schedule(context, x)
        return x
    }

    fun setDone(r: Reminder, done: Boolean) {
        val x = r.copy(done = done)
        commit(data.copy(reminders = data.reminders.map { if (it.id == r.id) x else it }))
        if (done) ReminderScheduler.cancel(context, r.id) else ReminderScheduler.schedule(context, x)
    }

    fun markSent(r: Reminder) = commit(data.copy(reminders = data.reminders.map { if (it.id == r.id) it.copy(sentToOwner = true) else it }))

    fun deleteReminder(r: Reminder) {
        ReminderScheduler.cancel(context, r.id)
        commit(data.copy(reminders = data.reminders.filterNot { it.id == r.id }))
    }

    // ------------------------------------------------------------ cuentas
    fun saveCharge(c: Charge): Charge {
        val x = if (c.id == 0L) c.copy(id = newId()) else c
        commit(data.copy(charges = data.charges.filterNot { it.id == x.id } + x))
        return x
    }

    fun pay(c: Charge, amount: Double) = saveCharge(c.copy(paid = (c.paid + amount).coerceAtMost(c.amount)))
    fun deleteCharge(c: Charge) = commit(data.copy(charges = data.charges.filterNot { it.id == c.id }))

    fun saveExpense(e: Expense): Expense {
        val x = if (e.id == 0L) e.copy(id = newId()) else e
        commit(data.copy(expenses = data.expenses.filterNot { it.id == x.id } + x))
        return x
    }

    fun deleteExpense(e: Expense) = commit(data.copy(expenses = data.expenses.filterNot { it.id == e.id }))

    // ------------------------------------------------------------ ajustes y copias
    fun saveSettings(s: ClinicSettings) = commit(data.copy(settings = s))

    fun replaceAll(d: VetData) {
        data.reminders.forEach { ReminderScheduler.cancel(context, it.id) }
        commit(d)
        d.reminders.filter { !it.done }.forEach { ReminderScheduler.schedule(context, it) }
    }
}
