package com.healthcheck.app.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.healthcheck.app.alarm.AlarmScheduler
import com.healthcheck.app.alarm.Notifications
import com.healthcheck.app.data.CareLink

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    vm: AppViewModel,
    state: UiState,
    onEditMedication: (ownerId: String, medId: String?) -> Unit,
    onOpenPerson: (String) -> Unit,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val snackbar = remember { SnackbarHostState() }
    MessageEffect(vm, state, snackbar)
    var confirmLogout by remember { mutableStateOf(false) }
    val myId = state.me?.id

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("HealthCheck")
                        state.me?.let { Text("Olá, ${it.displayName}", style = MaterialTheme.typography.bodySmall) }
                    }
                },
                actions = {
                    IconButton(onClick = { vm.refresh() }) { Icon(Icons.Filled.Refresh, "Atualizar") }
                    IconButton(onClick = { confirmLogout = true }) { Icon(Icons.AutoMirrored.Filled.Logout, "Sair") }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(tab == 0, { tab = 0 }, { Icon(Icons.Filled.Today, null) }, label = { Text("Hoje") })
                NavigationBarItem(tab == 1, { tab = 1 }, { Icon(Icons.Filled.Medication, null) }, label = { Text("Remédios") })
                NavigationBarItem(tab == 2, { tab = 2 }, { Icon(Icons.Filled.Group, null) }, label = { Text("Pessoas") })
            }
        },
        floatingActionButton = {
            if (tab == 1 && myId != null) {
                ExtendedFloatingActionButton(
                    onClick = { onEditMedication(myId, null) },
                    icon = { Icon(Icons.Filled.Add, null) },
                    text = { Text("Novo remédio") },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            PermissionBanners()
            UpdateBanner()
            when (tab) {
                0 -> TodayTab(vm, state)
                1 -> MedsTab(state) { med -> myId?.let { onEditMedication(it, med) } }
                2 -> PeopleTab(vm, state, onOpenPerson)
            }
        }
    }

    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text("Sair da conta?") },
            text = { Text("Os alarmes deste celular serão desativados até você entrar novamente.") },
            confirmButton = { TextButton(onClick = { confirmLogout = false; vm.signOut() }) { Text("Sair") } },
            dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun PermissionBanners() {
    val context = LocalContext.current
    var notificationsOk by remember { mutableStateOf(Notifications.canPost(context)) }
    var exactOk by remember { mutableStateOf(AlarmScheduler.canScheduleExact(context)) }
    var fullScreenOk by remember { mutableStateOf(Notifications.canUseFullScreen(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        notificationsOk = Notifications.canPost(context)
        exactOk = AlarmScheduler.canScheduleExact(context)
        fullScreenOk = Notifications.canUseFullScreen(context)
        if (exactOk) AlarmScheduler.rescheduleAll(context)
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notificationsOk = it
    }
    if (!notificationsOk && Build.VERSION.SDK_INT >= 33) {
        Banner("Permita as notificações para receber os alarmes dos remédios.", "Permitir") {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    if (!fullScreenOk && Build.VERSION.SDK_INT >= 34) {
        Banner("Permita \"Notificações em tela cheia\" para o alarme aparecer como uma ligação.", "Abrir") {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${context.packageName}"))
            )
        }
    }
    if (!exactOk && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Banner("Permita \"Alarmes e lembretes\" para os avisos tocarem na hora exata.", "Abrir") {
            context.startActivity(
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))
            )
        }
    }
}

@Composable
private fun Banner(text: String, action: String, onClick: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onClick) { Text(action) }
        }
    }
}

@Composable
private fun TodayTab(vm: AppViewModel, state: UiState) {
    LazyColumn(contentPadding = PaddingValues(16.dp)) {
        item {
            val done = state.myDoses.count { it.status == "taken" }
            SectionTitle("Doses de hoje ($done de ${state.myDoses.size} tomadas)", Modifier.padding(top = 0.dp))
        }
        if (state.myDoses.isEmpty()) {
            item { EmptyHint("Nenhuma dose para hoje. Cadastre seus remédios na aba \"Remédios\".") }
        }
        items(state.myDoses, key = { it.medication.id + it.scheduledAt }) { item ->
            DoseCard(item) { status -> vm.markDose(item, status) }
        }
    }
}

@Composable
private fun MedsTab(state: UiState, onEdit: (String?) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp)) {
        if (state.myMeds.isEmpty()) {
            item { EmptyHint("Você ainda não tem remédios cadastrados. Toque em \"Novo remédio\".") }
        }
        items(state.myMeds, key = { it.id }) { med ->
            val author = state.people[med.createdBy]
            Column {
                MedicationCard(med) { onEdit(med.id) }
                if (med.createdBy != state.me?.id && author != null) {
                    Text(
                        "Cadastrado por ${author.displayName}",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(start = 8.dp, bottom = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun PeopleTab(vm: AppViewModel, state: UiState, onOpenPerson: (String) -> Unit) {
    val myId = state.me?.id
    var email by rememberSaveable { mutableStateOf("") }
    var toRemove by remember { mutableStateOf<CareLink?>(null) }

    val received = state.links.filter { it.patientId == myId && !it.accepted }
    val iCareFor = state.links.filter { it.caregiverId == myId }
    val caresForMe = state.links.filter { it.patientId == myId && it.accepted }
    fun nameOf(id: String) = state.people[id]?.displayName ?: "Usuário"

    LazyColumn(contentPadding = PaddingValues(16.dp)) {
        item {
            SectionTitle("Cuidar de alguém", Modifier.padding(top = 0.dp))
            Text(
                "Envie um convite pelo e-mail da pessoa (ela precisa ter conta no app). " +
                    "Depois que ela aceitar, você poderá cadastrar remédios que vão tocar no celular dela.",
                style = MaterialTheme.typography.bodySmall,
            )
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                OutlinedTextField(
                    email, { email = it },
                    label = { Text("E-mail da pessoa") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Button(onClick = { vm.invite(email); email = "" }, enabled = email.contains("@") && !state.busy) {
                    Text("Convidar")
                }
            }
        }

        if (received.isNotEmpty()) {
            item { SectionTitle("Convites recebidos") }
            items(received, key = { it.id }) { link ->
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("${nameOf(link.caregiverId)} quer cadastrar e acompanhar seus remédios.")
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { vm.removeLink(link) }) { Text("Recusar") }
                            Button(onClick = { vm.accept(link) }) { Text("Aceitar") }
                        }
                    }
                }
            }
        }

        item { SectionTitle("Pessoas que eu cuido") }
        if (iCareFor.isEmpty()) item { EmptyHint("Ninguém ainda.") }
        items(iCareFor, key = { it.id }) { link ->
            ListItem(
                headlineContent = { Text(nameOf(link.patientId)) },
                supportingContent = {
                    Text(if (link.accepted) "Toque para ver e cadastrar remédios" else "Aguardando a pessoa aceitar o convite")
                },
                trailingContent = { TextButton(onClick = { toRemove = link }) { Text("Remover") } },
                modifier = Modifier
                    .padding(vertical = 2.dp)
                    .clickable(enabled = link.accepted) { onOpenPerson(link.patientId) },
            )
        }

        item { SectionTitle("Quem cuida de mim") }
        if (caresForMe.isEmpty()) item { EmptyHint("Ninguém ainda.") }
        items(caresForMe, key = { it.id }) { link ->
            ListItem(
                headlineContent = { Text(nameOf(link.caregiverId)) },
                supportingContent = { Text("Pode cadastrar remédios e ver suas doses") },
                trailingContent = { TextButton(onClick = { toRemove = link }) { Text("Remover") } },
            )
        }
    }

    toRemove?.let { link ->
        AlertDialog(
            onDismissRequest = { toRemove = null },
            title = { Text("Remover vínculo?") },
            text = { Text("A outra pessoa não poderá mais ver ou cadastrar remédios. Os remédios já cadastrados continuam.") },
            confirmButton = { TextButton(onClick = { vm.removeLink(link); toRemove = null }) { Text("Remover") } },
            dismissButton = { TextButton(onClick = { toRemove = null }) { Text("Cancelar") } },
        )
    }
}
