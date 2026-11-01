package com.healthcheck.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Tela do cuidador: remédios e doses de hoje de uma pessoa que ele cuida. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonScreen(
    vm: AppViewModel,
    personId: String,
    state: UiState,
    onBack: () -> Unit,
    onEditMedication: (String?) -> Unit,
) {
    val person by vm.person.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    MessageEffect(vm, state, snackbar)
    LaunchedEffect(personId) { vm.loadPerson(personId) }
    val name = state.people[personId]?.displayName ?: "Pessoa"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(name) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") } },
                actions = { IconButton(onClick = { vm.loadPerson(personId) }) { Icon(Icons.Filled.Refresh, "Atualizar") } },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onEditMedication(null) },
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text("Remédio para $name") },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (person.loading || state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp)) {
                item {
                    val done = person.doses.count { it.status == "taken" }
                    SectionTitle("Doses de hoje ($done de ${person.doses.size} tomadas)")
                }
                if (person.doses.isEmpty() && !person.loading) item { EmptyHint("Nenhuma dose para hoje.") }
                items(person.doses, key = { "d" + it.medication.id + it.scheduledAt }) { DoseCard(it, onMark = null) }

                item { SectionTitle("Remédios") }
                if (person.meds.isEmpty() && !person.loading) {
                    item { EmptyHint("Nenhum remédio cadastrado. Toque no botão abaixo para adicionar.") }
                }
                items(person.meds, key = { "m" + it.id }) { med -> MedicationCard(med) { onEditMedication(med.id) } }
            }
        }
    }
}
