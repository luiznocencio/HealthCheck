package com.healthcheck.app.ui

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.healthcheck.app.alarm.ActiveAlarms
import com.healthcheck.app.alarm.DoseActions
import com.healthcheck.app.alarm.DoseExtras

/** Tela cheia que aparece por cima da tela de bloqueio enquanto o alarme toca. */
class AlarmActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()
        setContent {
            HealthTheme {
                val doses by ActiveAlarms.doses.collectAsStateWithLifecycle()
                LaunchedEffect(doses.isEmpty()) { if (doses.isEmpty()) finish() }
                AlarmScreen(doses) { dose, action -> DoseActions.handle(this, dose, action) }
            }
        }
    }

    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}

@Composable
private fun AlarmScreen(doses: List<DoseExtras>, onAction: (DoseExtras, String) -> Unit) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.primaryContainer) {
        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.height(24.dp))
            Icon(Icons.Filled.Medication, null, Modifier.size(96.dp), tint = MaterialTheme.colorScheme.primary)
            Text(
                "Hora do remédio",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            doses.forEach { dose -> DoseAlarmCard(dose, onAction) }
        }
    }
}

@Composable
private fun DoseAlarmCard(dose: DoseExtras, onAction: (DoseExtras, String) -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(dose.time, fontSize = 48.sp, fontWeight = FontWeight.Bold)
            Text(dose.name, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            if (dose.dosage.isNotBlank()) Text(dose.dosage, style = MaterialTheme.typography.titleMedium)
            if (dose.notes.isNotBlank()) {
                Text(dose.notes, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { onAction(dose, DoseActions.TAKEN) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                modifier = Modifier.fillMaxWidth().height(64.dp),
            ) { Text("Tomei ✔", fontSize = 22.sp) }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { onAction(dose, DoseActions.SNOOZE) },
                    modifier = Modifier.weight(1f).height(52.dp),
                ) { Text("Adiar 10 min") }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(
                    onClick = { onAction(dose, DoseActions.SKIP) },
                    modifier = Modifier.weight(1f).height(52.dp),
                ) { Text("Pular") }
            }
        }
    }
}
