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
        if (intent.action == ACTION_TIMEOUT) {
            onTimeout(context, dose)
            return
        }
        val store = LocalStore(context)
        val med = store.myMedications.firstOrNull { it.id == dose.medicationId }

        // Remédio removido/desativado desde o agendamento: não avisa.
        if (med == null || !med.active) return

        // Tratamento já terminou (data final ou total de doses): não toca e não agenda mais.
        val at = LocalDateTime.ofInstant(Instant.ofEpochMilli(dose.scheduledAt), ZoneId.systemDefault())
        if (!med.isDoseIncluded(at)) {
            AlarmScheduler.cancel(context, dose)
            return
        }

        // Já marcada como tomada antes do horário: não toca, só agenda a próxima.
        if (store.isTaken(dose.medicationId, dose.scheduledAt)) {
            scheduleNext(context, intent, dose, med)
            return
        }

        val fresh = dose.copy(name = med.name, dosage = med.dosage, notes = med.notes)
        // Toca como despertador até a pessoa responder; se o Android bloquear o serviço,
        // usa uma notificação que repete o som até ser tocada.
        if (!AlarmService.ring(context, fresh)) {
            ActiveAlarms.add(fresh)
            Notifications.showDose(context, fresh)
        }
        AlarmScheduler.armTimeout(context, fresh)
        scheduleNext(context, intent, dose, med)
    }

    private fun scheduleNext(context: Context, intent: Intent, dose: DoseExtras, med: com.healthcheck.app.data.Medication) {
        if (intent.action != ACTION_DOSE) return
        val time = runCatching { LocalTime.parse(dose.time) }.getOrNull() ?: return
        val after = LocalDateTime.ofInstant(Instant.ofEpochMilli(dose.scheduledAt), ZoneId.systemDefault())
        AlarmScheduler.scheduleSlot(context, med, time, maxOf(after, LocalDateTime.now()))
    }

    /** 30 minutos tocando sem resposta: para o alarme, registra "não respondeu" e avisa. */
    private fun onTimeout(context: Context, dose: DoseExtras) {
        val job = DoseActions.handle(context, dose, DoseActions.MISSED)
        Notifications.showUpdate(
            context, dose.notificationId,
            "Alarme encerrado: ${dose.name}",
            "Ninguém confirmou a dose das ${dose.time} em ${AlarmScheduler.RING_LIMIT_MINUTES} minutos. " +
                "Quem cuida de você será avisado. Se já tomou, marque na aba Hoje."
        )
        if (job != null) {
            val pending = goAsync()
            job.invokeOnCompletion { pending.finish() }
        }
    }

    companion object {
        const val ACTION_DOSE = "com.healthcheck.app.DOSE"
        const val ACTION_SNOOZED = "com.healthcheck.app.DOSE_SNOOZED"
        const val ACTION_TIMEOUT = "com.healthcheck.app.DOSE_TIMEOUT"
    }
}

/** Botões da notificação: Tomei / Adiar / Pular. */
class DoseActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val dose = DoseExtras.read(intent) ?: return
        val action = intent.action ?: return
        val job = DoseActions.handle(context, dose, action) ?: return
        val pending = goAsync()
        job.invokeOnCompletion { pending.finish() }
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
        store.addRecentLog(log)
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
