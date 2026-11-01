package com.healthcheck.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.healthcheck.app.update.Updater
import kotlinx.coroutines.launch
import java.io.File

/** Faixa "Nova versão disponível" com download e instalação dentro do app. */
@Composable
fun UpdateBanner() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var release by remember { mutableStateOf<Updater.Release?>(null) }
    var dismissed by remember { mutableStateOf(false) }
    var downloading by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var downloaded by remember { mutableStateOf<File?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var waitingPermission by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { release = Updater.checkForUpdate() }

    fun installOrAskPermission(file: File) {
        if (Updater.canInstall(context)) {
            waitingPermission = false
            Updater.install(context, file)
        } else {
            waitingPermission = true
            Updater.openInstallPermissionSettings(context)
        }
    }

    // Volta da tela de permissão: continua a instalação automaticamente.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        val file = downloaded
        if (waitingPermission && file != null && Updater.canInstall(context)) installOrAskPermission(file)
    }

    val r = release ?: return
    if (dismissed) return

    Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.SystemUpdate, null)
                Text(
                    "  Nova versão ${r.versionName} disponível (você tem a ${Updater.currentVersionName})",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
            }
            if (r.notes.isNotBlank()) Text(r.notes, style = MaterialTheme.typography.bodySmall)
            if (waitingPermission) {
                Text(
                    "Ative \"Permitir desta fonte\" para o HealthCheck e volte para o app.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            if (downloading) {
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { dismissed = true }) { Text("Depois") }
                    Button(onClick = {
                        val file = downloaded
                        if (file != null) {
                            installOrAskPermission(file)
                            return@Button
                        }
                        error = null
                        downloading = true
                        scope.launch {
                            try {
                                val apk = Updater.download(context, r) { progress = it }
                                downloaded = apk
                                installOrAskPermission(apk)
                            } catch (e: Exception) {
                                error = "Não foi possível baixar: ${e.message ?: "verifique a internet"}"
                            } finally {
                                downloading = false
                            }
                        }
                    }) { Text(if (downloaded != null) "Instalar" else "Atualizar") }
                }
            }
        }
    }
}
