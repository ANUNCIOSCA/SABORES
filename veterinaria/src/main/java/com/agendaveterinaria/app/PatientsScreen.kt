package com.agendaveterinaria.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

fun speciesEmoji(s: String) = when (s) {
    "Perro" -> "🐶"; "Gato" -> "🐱"; "Bovino" -> "🐄"; "Equino" -> "🐴"; "Porcino" -> "🐷"
    "Caprino / Ovino" -> "🐐"; "Ave" -> "🐔"; else -> "🐾"
}

@Composable
fun Avatar(text: String, size: Int = 42) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(VetGreenSoft), contentAlignment = Alignment.Center) {
        Text(text, style = if (size > 40) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium)
    }
}

@Composable
fun PatientsScreen(state: VetState, onOpenPatient: (Long) -> Unit, onOpenOwner: (Long) -> Unit) {
    var q by rememberSaveable { mutableStateOf("") }
    var showOwners by rememberSaveable { mutableStateOf(false) }
    var species by rememberSaveable { mutableStateOf("") }
    val nq = q.trim().lowercase()
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp)) {
            Text(if (showOwners) "Dueños" else "Pacientes", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                q, { q = it }, placeholder = { Text(if (showOwners) "Buscar por nombre o teléfono" else "Buscar paciente, dueño, raza o chip") },
                leadingIcon = { Icon(Icons.Rounded.Search, null) }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = !showOwners, onClick = { showOwners = false }, label = { Text("Pacientes") }, leadingIcon = { Icon(Icons.Rounded.Pets, null, Modifier.size(16.dp)) })
                FilterChip(selected = showOwners, onClick = { showOwners = true }, label = { Text("Dueños") }, leadingIcon = { Icon(Icons.Rounded.Groups, null, Modifier.size(16.dp)) })
            }
            if (!showOwners) {
                val present = SPECIES.filter { sp -> state.data.patients.any { it.species == sp } }
                if (present.size > 1) Row(Modifier.padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    present.take(5).forEach { sp -> FilterChip(selected = species == sp, onClick = { species = if (species == sp) "" else sp }, label = { Text(speciesEmoji(sp)) }) }
                }
            }
        }
        if (showOwners) {
            val owners = state.data.owners.filter { nq.isBlank() || it.name.lowercase().contains(nq) || it.phone.contains(nq) }.sortedBy { it.name.lowercase() }
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (owners.isEmpty()) item { EmptyState(Icons.Rounded.Groups, "Sin dueños", "Se crean al registrar un paciente.") }
                items(owners, key = { it.id }) { o ->
                    val pets = state.patientsOf(o.id)
                    val debt = state.pendingOf(o.id)
                    VetCard(onClick = { onOpenOwner(o.id) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Avatar(o.name.take(1).uppercase())
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(o.name, fontWeight = FontWeight.SemiBold)
                                Text(listOf(o.phone, pets.joinToString(", ") { it.name }).filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            if (debt > 0) Pill(money(debt, state.settings.currency), VetAmber)
                        }
                    }
                }
                item { Spacer(Modifier.size(80.dp)) }
            }
        } else {
            val list = state.data.patients.filter { p ->
                (species.isBlank() || p.species == species) && (nq.isBlank() || listOf(p.name, p.breed, p.chip, state.ownerOf(p)?.name ?: "", state.ownerOf(p)?.phone ?: "").any { it.lowercase().contains(nq) })
            }.sortedWith(compareBy<Patient> { it.deceased }.thenBy { it.name.lowercase() })
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (list.isEmpty()) item { EmptyState(Icons.Rounded.Pets, if (nq.isBlank()) "Sin pacientes" else "No encontré \"$q\"", "Toca + para registrar un paciente.") }
                items(list, key = { it.id }) { p ->
                    val owner = state.ownerOf(p)
                    val nextDose = state.remindersOf(p.id).firstOrNull { !it.done }
                    VetCard(onClick = { onOpenPatient(p.id) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Avatar(speciesEmoji(p.species))
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(p.name + if (p.deceased) " (fallecido)" else "", fontWeight = FontWeight.SemiBold)
                                Text(
                                    listOf(p.breed.ifBlank { p.species }, ageText(p.birthMillis), state.lastWeight(p.id)?.let { "${num(it)} kg" } ?: "").filter { it.isNotBlank() }.joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                owner?.let { Text("Dueño: ${it.name}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                nextDose?.let {
                                    val late = it.dueMillis < startOfDay(System.currentTimeMillis())
                                    Text("${it.kind.label}: ${fmtNice(it.dueMillis)} (${whenText(it.dueMillis)})", style = MaterialTheme.typography.labelSmall, color = if (late) VetDanger else VetGreen)
                                }
                            }
                        }
                    }
                }
                item { Spacer(Modifier.size(80.dp)) }
            }
        }
    }
}
