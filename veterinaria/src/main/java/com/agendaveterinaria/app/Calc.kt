package com.agendaveterinaria.app

import kotlin.math.pow

/**
 * Cálculos clínicos. Son ayudas: la dosis final la decide el veterinario según el prospecto o el formulario.
 */
object VetCalc {
    /** Dosis total: peso (kg) × dosis (mg/kg). */
    fun doseMg(weightKg: Double, mgPerKg: Double): Double = weightKg * mgPerKg

    /** Volumen a aplicar: dosis (mg) ÷ concentración (mg/mL). */
    fun volumeMl(doseMg: Double, mgPerMl: Double): Double = if (mgPerMl > 0) doseMg / mgPerMl else 0.0

    /** Tabletas: dosis (mg) ÷ mg por tableta, redondeado al cuarto más cercano. */
    fun tablets(doseMg: Double, mgPerTablet: Double): Double =
        if (mgPerTablet > 0) Math.round(doseMg / mgPerTablet * 4) / 4.0 else 0.0

    /**
     * Peso estimado de bovinos con cinta (fórmula de Schaeffer en cm/kg):
     * perímetro torácico² × largo del cuerpo ÷ 10.840.
     */
    fun cattleWeightKg(girthCm: Double, lengthCm: Double): Double = girthCm * girthCm * lengthCm / 10840.0

    /** Superficie corporal (m²) = K × peso(g)^0,67 ÷ 10.000; K = 10,1 perro, 10,0 gato. */
    fun bodySurfaceM2(weightKg: Double, isCat: Boolean): Double {
        val k = if (isCat) 10.0 else 10.1
        return k * (weightKg * 1000).pow(0.67) / 10000.0
    }

    /** Avisos de seguridad para la dosis calculada. */
    fun doseWarnings(volumeMl: Double, weightKg: Double): List<String> = buildList {
        if (volumeMl > 0 && volumeMl < 0.05) add("El volumen es muy pequeño (menos de 0,05 mL): revisa la concentración o diluye.")
        if (weightKg > 0 && weightKg < 80 && volumeMl > 30) add("El volumen es alto para el peso: revisa la concentración del frasco.")
        if (weightKg > 1500) add("¿El peso está en kg? Revisa el dato.")
    }
}
