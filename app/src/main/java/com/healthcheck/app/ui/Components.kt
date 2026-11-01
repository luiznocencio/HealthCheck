package com.healthcheck.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.healthcheck.app.data.DoseItem
import com.healthcheck.app.data.Medication
import java.time.Instant

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(top = 16.dp, bottom = 8.dp),
    )
}

@Composable
fun EmptyHint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 12.dp),
    )
}

@Composable
fun MedicationCard(med: Medication, onClick: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (med.active) MaterialTheme.colorScheme.surfaceVariant
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Medication, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    med.name + if (med.dosage.isNotBlank()) " · ${med.dosage}" else "",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(med.scheduleSummary(), style = MaterialTheme.typography.bodySmall)
                if (!med.active) Text("Pausado", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                med.endDate?.let { Text("Até $it", style = MaterialTheme.typography.labelSmall) }
            }
        }
    }
}

@Composable
fun DoseCard(item: DoseItem, onMark: ((String) -> Unit)?) {
    val now = Instant.now()
    val late = item.status == null && item.scheduledAt.isBefore(now)
    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(item.time.toString(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(item.medication.name, style = MaterialTheme.typography.titleMedium)
                    if (item.medication.dosage.isNotBlank()) Text(item.medication.dosage, style = MaterialTheme.typography.bodySmall)
                }
                StatusChip(item.status, late)
            }
            if (onMark != null && item.status == null) {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                    OutlinedButton(onClick = { onMark("skipped") }) { Text("Pular") }
                    Spacer(Modifier.width(8.dp))
                    FilledTonalButton(onClick = { onMark("taken") }) { Text("Tomei") }
                }
            }
        }
    }
}

@Composable
fun StatusChip(status: String?, late: Boolean) {
    val (label, icon, color) = when {
        status == "taken" -> Triple("Tomado", Icons.Filled.CheckCircle, Color(0xFF2E7D32))
        status == "skipped" -> Triple("Pulado", Icons.Filled.RemoveCircle, Color(0xFF9E9E9E))
        late -> Triple("Atrasado", Icons.Filled.Schedule, Color(0xFFC62828))
        else -> Triple("Pendente", Icons.Filled.Schedule, MaterialTheme.colorScheme.primary)
    }
    AssistChip(
        onClick = {},
        label = { Text(label, color = color) },
        leadingIcon = { Icon(icon, null, tint = color, modifier = Modifier.size(18.dp)) },
    )
}
