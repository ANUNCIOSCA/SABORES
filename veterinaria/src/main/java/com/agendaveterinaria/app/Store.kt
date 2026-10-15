package com.agendaveterinaria.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Guarda todo en un solo archivo JSON dentro de la app (y sirve de copia de seguridad). */
object VetStore {
    private const val FILE = "agenda_veterinaria.json"
    const val FORMAT = "agenda-veterinaria"

    private fun file(context: Context) = File(context.filesDir, FILE)

    fun load(context: Context): VetData = runCatching {
        val f = file(context)
        if (!f.exists()) VetData() else fromJson(JSONObject(f.readText()))
    }.getOrDefault(VetData())

    fun save(context: Context, data: VetData) {
        val f = file(context)
        val tmp = File(context.filesDir, "$FILE.tmp")
        tmp.writeText(toJson(data).toString())
        if (!tmp.renameTo(f)) { f.writeText(tmp.readText()); tmp.delete() }
    }

    // ------------------------------------------------------------ JSON

    private fun JSONObject.optDoubleOrNull(k: String): Double? = if (has(k) && !isNull(k)) optDouble(k).takeIf { !it.isNaN() } else null
    private inline fun <T> JSONArray?.mapObjects(f: (JSONObject) -> T?): List<T> {
        if (this == null) return emptyList()
        val out = ArrayList<T>()
        for (i in 0 until length()) optJSONObject(i)?.let { o -> runCatching { f(o) }.getOrNull()?.let { out += it } }
        return out
    }
    private fun <T> arr(list: List<T>, f: (T) -> JSONObject) = JSONArray().apply { list.forEach { put(f(it)) } }

    fun toJson(d: VetData): JSONObject = JSONObject().apply {
        put("format", FORMAT)
        put("version", 1)
        put("exportedAt", System.currentTimeMillis())
        put("owners", arr(d.owners) { o ->
            JSONObject().put("id", o.id).put("name", o.name).put("phone", o.phone).put("email", o.email).put("address", o.address).put("notes", o.notes)
        })
        put("patients", arr(d.patients) { p ->
            JSONObject().put("id", p.id).put("ownerId", p.ownerId).put("name", p.name).put("species", p.species).put("breed", p.breed)
                .put("sex", p.sex).put("birth", p.birthMillis).put("color", p.color).put("chip", p.chip).put("sterilized", p.sterilized)
                .put("allergies", p.allergies).put("notes", p.notes).put("deceased", p.deceased).put("created", p.createdMillis)
        })
        put("weights", arr(d.weights) { w -> JSONObject().put("id", w.id).put("patientId", w.patientId).put("date", w.dateMillis).put("kg", w.kg).put("note", w.note) })
        put("visits", arr(d.visits) { v ->
            JSONObject().put("id", v.id).put("patientId", v.patientId).put("date", v.dateMillis).put("type", v.type.name).put("reason", v.reason)
                .put("weight", v.weightKg ?: JSONObject.NULL).put("temp", v.temperature ?: JSONObject.NULL).put("findings", v.findings)
                .put("diagnosis", v.diagnosis).put("treatment", v.treatment).put("notes", v.notes)
                .put("meds", arr(v.medications) { m -> JSONObject().put("name", m.name).put("dose", m.dose).put("route", m.route).put("freq", m.frequency) })
        })
        put("doses", arr(d.doses) { x ->
            JSONObject().put("id", x.id).put("patientId", x.patientId).put("kind", x.kind.name).put("product", x.product).put("date", x.dateMillis)
                .put("lot", x.lot).put("lab", x.lab).put("next", x.nextMillis).put("notes", x.notes)
        })
        put("reminders", arr(d.reminders) { r ->
            JSONObject().put("id", r.id).put("patientId", r.patientId).put("ownerId", r.ownerId).put("kind", r.kind.name).put("title", r.title)
                .put("message", r.message).put("due", r.dueMillis).put("notify", r.notifyMillis).put("done", r.done).put("sent", r.sentToOwner).put("doseId", r.doseId)
        })
        put("charges", arr(d.charges) { c ->
            JSONObject().put("id", c.id).put("ownerId", c.ownerId).put("patientId", c.patientId).put("date", c.dateMillis).put("concept", c.concept)
                .put("amount", c.amount).put("paid", c.paid).put("visitId", c.visitId)
        })
        put("expenses", arr(d.expenses) { e -> JSONObject().put("id", e.id).put("date", e.dateMillis).put("concept", e.concept).put("category", e.category).put("amount", e.amount) })
        val s = d.settings
        put("settings", JSONObject().put("clinic", s.clinicName).put("vet", s.vetName).put("license", s.license).put("phone", s.phone)
            .put("address", s.address).put("currency", s.currency).put("daysBefore", s.daysBefore).put("notifyHour", s.notifyHour))
    }

    fun fromJson(o: JSONObject): VetData {
        val s = o.optJSONObject("settings")
        return VetData(
            owners = o.optJSONArray("owners").mapObjects { j ->
                Owner(j.getLong("id"), j.optString("name"), j.optString("phone"), j.optString("email"), j.optString("address"), j.optString("notes"))
            },
            patients = o.optJSONArray("patients").mapObjects { j ->
                Patient(
                    id = j.getLong("id"), ownerId = j.optLong("ownerId"), name = j.optString("name"), species = j.optString("species", "Perro"),
                    breed = j.optString("breed"), sex = j.optString("sex"), birthMillis = j.optLong("birth"), color = j.optString("color"),
                    chip = j.optString("chip"), sterilized = j.optBoolean("sterilized"), allergies = j.optString("allergies"),
                    notes = j.optString("notes"), deceased = j.optBoolean("deceased"), createdMillis = j.optLong("created", System.currentTimeMillis()),
                )
            },
            weights = o.optJSONArray("weights").mapObjects { j -> WeightEntry(j.getLong("id"), j.optLong("patientId"), j.optLong("date"), j.optDouble("kg"), j.optString("note")) },
            visits = o.optJSONArray("visits").mapObjects { j ->
                Visit(
                    id = j.getLong("id"), patientId = j.optLong("patientId"), dateMillis = j.optLong("date"), type = VisitType.of(j.optString("type")),
                    reason = j.optString("reason"), weightKg = j.optDoubleOrNull("weight"), temperature = j.optDoubleOrNull("temp"),
                    findings = j.optString("findings"), diagnosis = j.optString("diagnosis"), treatment = j.optString("treatment"), notes = j.optString("notes"),
                    medications = j.optJSONArray("meds").mapObjects { m -> Medication(m.optString("name"), m.optString("dose"), m.optString("route"), m.optString("freq")) },
                )
            },
            doses = o.optJSONArray("doses").mapObjects { j ->
                Dose(j.getLong("id"), j.optLong("patientId"), DoseKind.of(j.optString("kind")), j.optString("product"), j.optLong("date"),
                    j.optString("lot"), j.optString("lab"), j.optLong("next"), j.optString("notes"))
            },
            reminders = o.optJSONArray("reminders").mapObjects { j ->
                Reminder(
                    id = j.getLong("id"), patientId = j.optLong("patientId"), ownerId = j.optLong("ownerId"), kind = ReminderKind.of(j.optString("kind")),
                    title = j.optString("title"), message = j.optString("message"), dueMillis = j.optLong("due"),
                    notifyMillis = j.optLong("notify", j.optLong("due")), done = j.optBoolean("done"), sentToOwner = j.optBoolean("sent"), doseId = j.optLong("doseId"),
                )
            },
            charges = o.optJSONArray("charges").mapObjects { j ->
                Charge(j.getLong("id"), j.optLong("ownerId"), j.optLong("patientId"), j.optLong("date"), j.optString("concept"),
                    j.optDouble("amount", 0.0), j.optDouble("paid", 0.0), j.optLong("visitId"))
            },
            expenses = o.optJSONArray("expenses").mapObjects { j -> Expense(j.getLong("id"), j.optLong("date"), j.optString("concept"), j.optString("category", "Otros"), j.optDouble("amount", 0.0)) },
            settings = if (s == null) ClinicSettings() else ClinicSettings(
                clinicName = s.optString("clinic"), vetName = s.optString("vet"), license = s.optString("license"), phone = s.optString("phone"),
                address = s.optString("address"), currency = s.optString("currency", "$").ifBlank { "$" },
                daysBefore = s.optInt("daysBefore", 2), notifyHour = s.optInt("notifyHour", 8),
            ),
        )
    }
}
