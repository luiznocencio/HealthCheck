package com.healthcheck.app.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.healthcheck.app.data.DoseLog
import com.healthcheck.app.data.LocalStore
import com.healthcheck.app.data.Repository
import com.healthcheck.app.data.Supabase
import com.healthcheck.app.sync.SyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

/** Dispara na hora da dose: mostra a notificação e agenda a próxima ocorrência. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val dose = DoseExtras.read(intent) ?: return
        val store = LocalStore(context)
        val med = store.myMedications.firstOrNull { it.id == dose.medicationId }

        // Remédio removido/desativado desde o agendamento: não avisa.
        if (med == null || !med.active) return

        Notifications.showDose(context, dose.copy(name = med.name, dosage = med.dosage, notes = med.notes))

        if (intent.action == ACTION_DOSE) {
            val time = runCatching { LocalTime.parse(dose.time) }.getOrNull() ?: return
            val after = LocalDateTime.ofInstant(Instant.ofEpochMilli(dose.scheduledAt), ZoneId.systemDefault())
            AlarmScheduler.scheduleSlot(context, med, time, maxOf(after, LocalDateTime.now()))
        }
    }

    companion object {
        const val ACTION_DOSE = "com.healthcheck.app.DOSE"
        const val ACTION_SNOOZED = "com.healthcheck.app.DOSE_SNOOZED"
    }
}

/** Botões da notificação: Tomei / Adiar / Pular. */
class DoseActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val dose = DoseExtras.read(intent) ?: return
        Notifications.cancel(context, dose.notificationId)
        when (intent.action) {
            ACTION_SNOOZE -> AlarmScheduler.snooze(context, dose)
            ACTION_TAKEN, ACTION_SKIP -> {
                val status = if (intent.action == ACTION_TAKEN) "taken" else "skipped"
                val log = DoseLog(dose.medicationId, dose.ownerId, Instant.ofEpochMilli(dose.scheduledAt).toString(), status)
                val pending = goAsync()
                receiverScope.launch {
                    try {
                        DoseLogger.record(context, log)
                    } finally {
                        pending.finish()
                    }
                }
            }
        }
    }

    companion object {
        const val ACTION_TAKEN = "com.healthcheck.app.TAKEN"
        const val ACTION_SNOOZE = "com.healthcheck.app.SNOOZE"
        const val ACTION_SKIP = "com.healthcheck.app.SKIP"
    }
}

/** Depois de reiniciar o celular (ou mudar horário/fuso), os alarmes precisam ser recriados. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        AlarmScheduler.rescheduleAll(context)
        SyncWorker.runNow(context)
    }
}

object DoseLogger {
    /** Tenta enviar o registro; se estiver offline, guarda para enviar na próxima sincronização. */
    suspend fun record(context: Context, log: DoseLog) {
        val store = LocalStore(context)
        store.addPendingLog(log)
        if (!Supabase.isConfigured) return
        val sent = withTimeoutOrNull(8_000) {
            runCatching {
                Repository.awaitAuth()
                flush(store)
            }.isSuccess
        } ?: false
        if (!sent) SyncWorker.runNow(context)
    }

    suspend fun flush(store: LocalStore) {
        val pending = store.pendingLogs
        if (pending.isEmpty()) return
        Repository.upsertLogs(pending)
        // Remove só o que foi enviado (pode ter chegado algo novo nesse meio tempo)
        store.pendingLogs = store.pendingLogs - pending.toSet()
    }
}
