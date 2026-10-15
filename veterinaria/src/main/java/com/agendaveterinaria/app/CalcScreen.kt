@file:OptIn(ExperimentalLayoutApi::class)

package com.agendaveterinaria.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Resultado grande y claro. */
@Composable
private fun ResultBox(lines: List<Pair<String, String>>, warnings: List<String> = emptyList()) {
    if (lines.isEmpty()) return
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(VetGreenSoft).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        lines.forEach { (label, value) ->
            Row {
                Text(label, modifier = Modifier.weight(1f), color = VetGreenDark)
                Text(value, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = VetGreenDark)
            }
        }
        warnings.forEach { Text("⚠ $it", color = VetDanger, style = MaterialTheme.typography.bodySmall) }
    }
}

/** Dosis por peso: mg totales, mL a aplicar y tabletas. */
@Composable
fun DoseCalculator(initialWeight: Double?, onResult: ((drug: String, text: String) -> Unit)? = null) {
    var drug by remember { mutableStateOf("") }
    var weight by remember { mutableStateOf(initialWeight?.let { num(it) } ?: "") }
    var mgKg by remember { mutableStateOf("") }
    var conc by remember { mutableStateOf("") }
    var tab by remember { mutableStateOf("") }
    val w = parseNum(weight); val d = parseNum(mgKg); val c = parseNum(conc); val t = parseNum(tab)
    val mg = if (w != null && d != null) VetCalc.doseMg(w, d) else null
    val ml = if (mg != null && c != null && c > 0) VetCalc.volumeMl(mg, c) else null
    val tabs = if (mg != null && t != null && t > 0) VetCalc.tablets(mg, t) else null
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(drug, { drug = it }, label = { Text("Medicamento (opcional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField(weight, { weight = it }, "Peso", Modifier.weight(1f), "kg")
            NumberField(mgKg, { mgKg = it }, "Dosis", Modifier.weight(1f), "mg/kg")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField(conc, { conc = it }, "Concentración", Modifier.weight(1f), "mg/mL")
            NumberField(tab, { tab = it }, "o tableta de", Modifier.weight(1f), "mg")
        }
        ResultBox(
            buildList {
                mg?.let { add("Dosis total" to "${num(it)} mg") }
                ml?.let { add("Aplicar" to "${num(it)} mL") }
                tabs?.let { add("Tabletas" to num(it)) }
            },
            if (ml != null && w != null) VetCalc.doseWarnings(ml, w) else emptyList(),
        )
        Text(
            "Fórmula: peso × mg/kg = mg; mg ÷ concentración (mg/mL) = mL. Verifica siempre la dosis y la concentración en la etiqueta o el formulario.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (onResult != null && mg != null) {
            Button(onClick = {
                onResult(drug, "${num(mg)} mg" + (ml?.let { " (${num(it)} mL)" } ?: "") + (tabs?.let { " · ${num(it)} tab." } ?: ""))
            }, modifier = Modifier.fillMaxWidth()) { Text("Usar este resultado") }
        }
    }
}

@Composable
fun DoseCalcDialog(initialWeight: Double?, onDismiss: () -> Unit, onUse: (String, String) -> Unit) {
    FormDialog("Calcular dosis", onDismiss, onSave = onDismiss, saveLabel = "Cerrar") {
        DoseCalculator(initialWeight, onUse)
    }
}

@Composable
fun CalcScreen(state: VetState) {
    val context = LocalContext.current
    var tool by remember { mutableStateOf(0) }
    var patientId by remember { mutableLongStateOf(0L) }
    var q by remember { mutableStateOf("") }
    val patient = state.patient(patientId)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Calculadoras", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("Dosis", "Peso con cinta", "Superficie corporal").forEachIndexed { i, l ->
                FilterChip(selected = tool == i, onClick = { tool = i }, label = { Text(l, maxLines = 1) })
            }
        }
        // Paciente (opcional): toma su peso o guarda el calculado
        if (patient == null) {
            OutlinedTextField(q, { q = it }, label = { Text("Paciente (opcional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            if (q.length >= 2) state.data.patients.filter { it.name.lowercase().contains(q.lowercase()) }.take(5).forEach { p ->
                TextButton(onClick = { patientId = p.id; q = "" }) { Text("${p.name} · ${p.species}" + (state.lastWeight(p.id)?.let { " · ${num(it)} kg" } ?: "")) }
            }
        } else {
            Row {
                Text("${patient.name} · ${patient.species}" + (state.lastWeight(patient.id)?.let { " · ${num(it)} kg" } ?: ""), fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                TextButton(onClick = { patientId = 0L }) { Text("Quitar") }
            }
        }
        VetCard {
            when (tool) {
                0 -> DoseCalculator(patient?.let { state.lastWeight(it.id) })
                1 -> {
                    var girth by remember { mutableStateOf("") }
                    var length by remember { mutableStateOf("") }
                    val g = parseNum(girth); val l = parseNum(length)
                    val kg = if (g != null && l != null && g > 0 && l > 0) VetCalc.cattleWeightKg(g, l) else null
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Peso estimado de bovinos sin báscula", fontWeight = FontWeight.SemiBold)
                        NumberField(girth, { girth = it }, "Perímetro torácico (detrás de las paletas)", Modifier.fillMaxWidth(), "cm")
                        NumberField(length, { length = it }, "Largo: punta del hombro a punta de la nalga", Modifier.fillMaxWidth(), "cm")
                        ResultBox(kg?.let { listOf("Peso estimado" to "${num(it, 0)} kg") } ?: emptyList())
                        Text(
                            "Fórmula de Schaeffer: perímetro² × largo ÷ 10.840. Es una estimación (puede variar varios kg); calíbrala con pesajes reales de tu rebaño.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (kg != null && patient != null) Button(onClick = {
                            state.addWeight(patient.id, Math.round(kg).toDouble(), note = "Estimado con cinta")
                            toast(context, "Peso guardado en ${patient.name}")
                        }, modifier = Modifier.fillMaxWidth()) { Text("Guardar ${num(kg, 0)} kg en ${patient.name}") }
                    }
                }
                else -> {
                    var weight by remember(patientId) { mutableStateOf(patient?.let { state.lastWeight(it.id) }?.let { num(it) } ?: "") }
                    var cat by remember(patientId) { mutableStateOf(patient?.species == "Gato") }
                    val w = parseNum(weight)
                    val m2 = if (w != null && w > 0) VetCalc.bodySurfaceM2(w, cat) else null
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Superficie corporal (perros y gatos)", fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(selected = !cat, onClick = { cat = false }, label = { Text("Perro") })
                            FilterChip(selected = cat, onClick = { cat = true }, label = { Text("Gato") })
                        }
                        NumberField(weight, { weight = it }, "Peso", Modifier.fillMaxWidth(), "kg")
                        ResultBox(m2?.let { listOf("Superficie" to "${num(it, 3)} m²") } ?: emptyList())
                        Text(
                            "m² = K × peso(g)^0,67 ÷ 10.000 (K = 10,1 perro; 10,0 gato). Se usa en dosis por m² (por ejemplo, quimioterapia).",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
