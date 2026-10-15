package com.agendaveterinaria.app

// ---------------------------------------------------------------------------
// Datos de la Agenda Veterinaria. Todo se guarda en el teléfono (funciona sin internet).
// ---------------------------------------------------------------------------

/** Dueño o cliente del consultorio. */
data class Owner(
    val id: Long,
    val name: String,
    val phone: String = "",
    val email: String = "",
    val address: String = "",
    val notes: String = "",
)

val SPECIES = listOf("Perro", "Gato", "Bovino", "Equino", "Porcino", "Caprino / Ovino", "Ave", "Otro")

/** Paciente: una mascota o un animal (o un lote, si se usa así en granjas). */
data class Patient(
    val id: Long,
    val ownerId: Long,
    val name: String,
    val species: String = "Perro",
    val breed: String = "",
    val sex: String = "",           // "Macho" / "Hembra"
    val birthMillis: Long = 0L,     // 0 = no se sabe
    val color: String = "",
    val chip: String = "",          // microchip, crotal o arete
    val sterilized: Boolean = false,
    val allergies: String = "",
    val notes: String = "",
    val deceased: Boolean = false,
    val createdMillis: Long = System.currentTimeMillis(),
)

data class WeightEntry(val id: Long, val patientId: Long, val dateMillis: Long, val kg: Double, val note: String = "")

enum class VisitType(val label: String) {
    CONSULTA("Consulta"), CONTROL("Control"), EMERGENCIA("Emergencia"), CIRUGIA("Cirugía"),
    TRATAMIENTO("Tratamiento"), PELUQUERIA("Baño / peluquería"), OTRO("Otro");

    companion object { fun of(s: String) = entries.firstOrNull { it.name == s } ?: CONSULTA }
}

/** Medicamento aplicado o recetado en una consulta. */
data class Medication(
    val name: String,
    val dose: String = "",        // "450 mg (4,5 mL)"
    val route: String = "",       // IM, SC, IV, oral, tópica
    val frequency: String = "",   // "cada 12 h por 7 días"
)

/** Una entrada de la historia médica. */
data class Visit(
    val id: Long,
    val patientId: Long,
    val dateMillis: Long,
    val type: VisitType = VisitType.CONSULTA,
    val reason: String = "",
    val weightKg: Double? = null,
    val temperature: Double? = null,
    val findings: String = "",     // examen clínico
    val diagnosis: String = "",
    val treatment: String = "",
    val medications: List<Medication> = emptyList(),
    val notes: String = "",
)

enum class DoseKind(val label: String) {
    VACUNA("Vacuna"), DESPARASITACION_INT("Desparasitación interna"), DESPARASITACION_EXT("Desparasitación externa");

    companion object { fun of(s: String) = entries.firstOrNull { it.name == s } ?: VACUNA }
}

/** Vacuna o desparasitación aplicada (lo que va en el carnet). */
data class Dose(
    val id: Long,
    val patientId: Long,
    val kind: DoseKind,
    val product: String,
    val dateMillis: Long,
    val lot: String = "",
    val lab: String = "",
    val nextMillis: Long = 0L,     // próxima dosis; 0 = no lleva
    val notes: String = "",
)

enum class ReminderKind(val label: String) {
    VACUNA("Vacuna"), DESPARASITACION("Desparasitación"), CITA("Cita"), CONTROL("Control"),
    MEDICAMENTO("Medicamento"), COBRO("Cobro"), OTRO("Otro");

    companion object { fun of(s: String) = entries.firstOrNull { it.name == s } ?: OTRO }
}

/**
 * Recordatorio: avisa al veterinario y trae el WhatsApp listo para el dueño.
 * [dueMillis] es cuándo toca (la vacuna, la cita); [notifyMillis] cuándo suena el aviso.
 */
data class Reminder(
    val id: Long,
    val patientId: Long = 0L,
    val ownerId: Long = 0L,
    val kind: ReminderKind = ReminderKind.OTRO,
    val title: String,
    val message: String = "",
    val dueMillis: Long,
    val notifyMillis: Long = dueMillis,
    val done: Boolean = false,
    val sentToOwner: Boolean = false,
    /** Si salió de una vacuna o desparasitación, su id (para actualizarlo si se edita). */
    val doseId: Long = 0L,
)

/** Cobro de una consulta, vacuna, medicamento, etc. */
data class Charge(
    val id: Long,
    val ownerId: Long,
    val patientId: Long = 0L,
    val dateMillis: Long,
    val concept: String,
    val amount: Double,
    val paid: Double = 0.0,
    val visitId: Long = 0L,
) {
    val pending: Double get() = (amount - paid).coerceAtLeast(0.0)
    val isPaid: Boolean get() = pending < 0.005
}

val EXPENSE_CATEGORIES = listOf("Medicamentos", "Vacunas", "Insumos", "Alquiler", "Servicios", "Sueldos", "Transporte", "Equipos", "Otros")

data class Expense(
    val id: Long,
    val dateMillis: Long,
    val concept: String,
    val category: String = "Otros",
    val amount: Double,
)

/** Datos del consultorio (salen en el carnet y en los mensajes). */
data class ClinicSettings(
    val clinicName: String = "",
    val vetName: String = "",
    val license: String = "",       // colegiatura / registro
    val phone: String = "",
    val address: String = "",
    val currency: String = "$",
    /** Días antes de la vacuna para recordarle al dueño. */
    val daysBefore: Int = 2,
    /** Hora del aviso (0-23). */
    val notifyHour: Int = 8,
)

/** Todo lo guardado. */
data class VetData(
    val owners: List<Owner> = emptyList(),
    val patients: List<Patient> = emptyList(),
    val weights: List<WeightEntry> = emptyList(),
    val visits: List<Visit> = emptyList(),
    val doses: List<Dose> = emptyList(),
    val reminders: List<Reminder> = emptyList(),
    val charges: List<Charge> = emptyList(),
    val expenses: List<Expense> = emptyList(),
    val settings: ClinicSettings = ClinicSettings(),
)

/**
 * Productos sugeridos con su intervalo para calcular la próxima dosis.
 * Es una guía: cada veterinario ajusta la fecha según el producto, la edad y la ley local.
 */
data class CatalogItem(val species: String, val kind: DoseKind, val name: String, val puppyDays: Int, val adultDays: Int)

val CATALOG = listOf(
    CatalogItem("Perro", DoseKind.VACUNA, "Polivalente / Séxtuple (moquillo, parvovirus, adenovirus)", 21, 365),
    CatalogItem("Perro", DoseKind.VACUNA, "Antirrábica", 365, 365),
    CatalogItem("Perro", DoseKind.VACUNA, "Leptospirosis", 21, 365),
    CatalogItem("Perro", DoseKind.VACUNA, "Tos de las perreras (Bordetella)", 21, 365),
    CatalogItem("Perro", DoseKind.DESPARASITACION_INT, "Desparasitante interno", 15, 90),
    CatalogItem("Perro", DoseKind.DESPARASITACION_EXT, "Antipulgas / garrapatas", 30, 30),
    CatalogItem("Gato", DoseKind.VACUNA, "Triple felina", 21, 365),
    CatalogItem("Gato", DoseKind.VACUNA, "Antirrábica", 365, 365),
    CatalogItem("Gato", DoseKind.VACUNA, "Leucemia felina", 21, 365),
    CatalogItem("Gato", DoseKind.DESPARASITACION_INT, "Desparasitante interno", 15, 90),
    CatalogItem("Gato", DoseKind.DESPARASITACION_EXT, "Antipulgas", 30, 30),
    CatalogItem("Bovino", DoseKind.VACUNA, "Fiebre aftosa (ciclo)", 180, 180),
    CatalogItem("Bovino", DoseKind.VACUNA, "Rabia bovina", 365, 365),
    CatalogItem("Bovino", DoseKind.VACUNA, "Clostridiales (triple / polivalente)", 30, 365),
    CatalogItem("Bovino", DoseKind.VACUNA, "Brucelosis (hembras, dosis única)", 0, 0),
    CatalogItem("Bovino", DoseKind.DESPARASITACION_INT, "Desparasitante", 90, 90),
    CatalogItem("Equino", DoseKind.VACUNA, "Encefalitis equina", 365, 365),
    CatalogItem("Equino", DoseKind.VACUNA, "Tétanos", 30, 365),
    CatalogItem("Equino", DoseKind.DESPARASITACION_INT, "Desparasitante", 60, 60),
    CatalogItem("Porcino", DoseKind.VACUNA, "Peste porcina clásica", 180, 180),
    CatalogItem("Caprino / Ovino", DoseKind.VACUNA, "Clostridiales", 30, 365),
    CatalogItem("Ave", DoseKind.VACUNA, "Newcastle", 21, 90),
)
