package com.healthcheck.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.healthcheck.app.data.Medication
import com.healthcheck.app.data.dayShort
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MedicationEditScreen(
    vm: AppViewModel,
    state: UiState,
    ownerId: String,
    medicationId: String?,
    onDone: () -> Unit,
) {
    val existing = remember(medicationId) { medicationId?.let { id -> vm.medicationsOf(ownerId).firstOrNull { it.id == id } } }
    val isNew = existing == null
    val snackbar = remember { SnackbarHostState() }
    MessageEffect(vm, state, snackbar)

    var name by remember { mutableStateOf(existing?.name ?: "") }
    var dosage by remember { mutableStateOf(existing?.dosage ?: "") }
    var notes by remember { mutableStateOf(existing?.notes ?: "") }
    var active by remember { mutableStateOf(existing?.active ?: true) }
    val times = remember { mutableStateListOf<LocalTime>().apply { addAll(existing?.localTimes() ?: listOf(LocalTime.of(8, 0))) } }
    val days = remember { mutableStateListOf<Int>().apply { addAll(existing?.daysOfWeek ?: (1..7).toList()) } }
    val startDate = remember { existing?.startDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: LocalDate.now() }
    var duration by remember {
        mutableStateOf(
            existing?.endDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                ?.let { (ChronoUnit.DAYS.between(startDate, it) + 1).toString() } ?: ""
        )
    }
    var timesPerDay by remember { mutableStateOf(times.size.coerceIn(1, 6)) }
    var editingTime by remember { mutableStateOf<Int?>(null) } // índice; -1 = novo horário
    var confirmDelete by remember { mutableStateOf(false) }

    val ownerName = if (ownerId == state.me?.id) null else state.people[ownerId]?.displayName

    fun spreadTimes(n: Int) {
        val first = times.minOrNull() ?: LocalTime.of(8, 0)
        val step = 24 * 60 / n
        val generated = (0 until n).map { first.plusMinutes((it * step).toLong()) }.distinct().sorted()
        times.clear(); times.addAll(generated)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(if (isNew) "Novo remédio" else "Editar remédio")
                        ownerName?.let { Text("Para: $it", style = MaterialTheme.typography.bodySmall) }
                    }
                },
                navigationIcon = { IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") } },
                actions = {
                    if (!isNew) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, "Excluir") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            OutlinedTextField(name, { name = it }, label = { Text("Nome do remédio *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(dosage, { dosage = it }, label = { Text("Dose (ex: 1 comprimido, 10 ml)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(notes, { notes = it }, label = { Text("Observações (ex: tomar após comer)") }, modifier = Modifier.fillMaxWidth())

            SectionTitle("Quantas vezes por dia?")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (1..6).forEach { n ->
                    FilterChip(
                        selected = timesPerDay == n && times.size == n,
                        onClick = { timesPerDay = n; spreadTimes(n) },
                        label = { Text(if (n == 1) "1x" else "${n}x") },
                    )
                }
            }
            Text(
                "Os horários são distribuídos a cada ${24 / timesPerDay}h a partir do primeiro. " +
                    "Toque em um horário para alterar ou remover.",
                style = MaterialTheme.typography.bodySmall,
            )

            SectionTitle("Horários")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                times.sorted().forEach { t ->
                    InputChip(
                        selected = false,
                        onClick = { editingTime = times.indexOf(t) },
                        label = { Text(t.toString()) },
                    )
                }
                OutlinedButton(onClick = { editingTime = -1 }) {
                    Icon(Icons.Filled.Add, null); Spacer(Modifier.width(4.dp)); Text("Horário")
                }
            }
            SectionTitle("Dias da semana")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Todos os dias", Modifier.weight(1f))
                Switch(checked = days.size == 7, onCheckedChange = { all ->
                    days.clear(); if (all) days.addAll(1..7) else days.addAll(1..5)
                })
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (1..7).forEach { d ->
                    FilterChip(
                        selected = d in days,
                        onClick = {
                            when {
                                d !in days -> days.add(d)
                                days.size > 1 -> days.remove(d)
                            }
                        },
                        label = { Text(dayShort(d)) },
                    )
                }
            }

            SectionTitle("Duração do tratamento")
            OutlinedTextField(
                duration,
                { duration = it.filter(Char::isDigit).take(4) },
                label = { Text("Quantidade de dias (vazio = uso contínuo)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            val endDate = duration.toLongOrNull()?.takeIf { it > 0 }?.let { startDate.plusDays(it - 1) }
            Text(
                "Início: $startDate" + (endDate?.let { " · Término: $it" } ?: " · Uso contínuo"),
                style = MaterialTheme.typography.bodySmall,
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Lembretes ativos", Modifier.weight(1f))
                Switch(checked = active, onCheckedChange = { active = it })
            }

            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    val myId = state.me?.id ?: ""
                    val med = Medication(
                        id = existing?.id ?: UUID.randomUUID().toString(),
                        ownerId = ownerId,
                        createdBy = existing?.createdBy ?: myId,
                        name = name.trim(),
                        dosage = dosage.trim(),
                        notes = notes.trim(),
                        times = times.distinct().sorted().map { it.toString() },
                        daysOfWeek = days.distinct().sorted(),
                        startDate = startDate.toString(),
                        endDate = endDate?.toString(),
                        active = active,
                    )
                    vm.saveMedication(med, isNew, onDone)
                },
                enabled = name.isNotBlank() && times.isNotEmpty() && days.isNotEmpty() && !state.busy,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text("Salvar") }
        }
    }

    editingTime?.let { index ->
        val initial = times.getOrNull(index) ?: LocalTime.of(12, 0)
        val pickerState = rememberTimePickerState(initial.hour, initial.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { editingTime = null },
            title = { Text(if (index == -1) "Novo horário" else "Alterar horário") },
            text = { TimePicker(state = pickerState) },
            confirmButton = {
                TextButton(onClick = {
                    val t = LocalTime.of(pickerState.hour, pickerState.minute)
                    when {
                        index == -1 -> if (t !in times) times.add(t)
                        else -> times[index] = t
                    }
                    timesPerDay = times.size.coerceIn(1, 6)
                    editingTime = null
                }) { Text("OK") }
            },
            dismissButton = {
                Row {
                    if (index != -1 && times.size > 1) {
                        TextButton(onClick = { times.removeAt(index); timesPerDay = times.size; editingTime = null }) { Text("Remover") }
                    }
                    TextButton(onClick = { editingTime = null }) { Text("Cancelar") }
                }
            },
        )
    }

    if (confirmDelete && existing != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Excluir ${existing.name}?") },
            text = { Text("Os alarmes deste remédio serão cancelados.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; vm.deleteMedication(existing, onDone) }) { Text("Excluir") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } },
        )
    }
}
