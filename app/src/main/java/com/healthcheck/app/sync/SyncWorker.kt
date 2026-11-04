package com.healthcheck.app.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.healthcheck.app.alarm.AlarmScheduler
import com.healthcheck.app.alarm.DoseLogger
import com.healthcheck.app.alarm.Notifications
import com.healthcheck.app.data.LocalStore
import com.healthcheck.app.data.Repository
import com.healthcheck.app.data.Supabase
import com.healthcheck.app.data.parseInstant
import com.healthcheck.app.update.Updater
import java.util.concurrent.TimeUnit

/**
 * Baixa os remédios do usuário logado, atualiza o cache local e reagenda os alarmes.
 * Roda ao abrir o app e periodicamente (a cada ~15 min) em segundo plano, então os
 * remédios cadastrados por um cuidador chegam ao celular do paciente automaticamente.
 */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        notifyUpdate(applicationContext)
        if (!Supabase.isConfigured) return Result.success()
        return try {
            sync(applicationContext)
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val PERIODIC = "sync-periodic"
        private const val ONE_SHOT = "sync-now"

        suspend fun sync(context: Context) {
            Repository.awaitAuth()
            val userId = Repository.currentUserId() ?: return
            val store = LocalStore(context)

            DoseLogger.flush(store)

            val meds = Repository.medications(userId)
            val known = store.knownMedicationIds
            if (known != null) {
                val added = meds.filter { it.id !in known && it.createdBy != userId }
                if (added.isNotEmpty()) {
                    val authors = Repository.profiles(added.map { it.createdBy }.toSet()).associateBy { it.id }
                    added.forEach { med ->
                        val who = authors[med.createdBy]?.displayName ?: "Alguém"
                        Notifications.showUpdate(
                            context, med.id.hashCode(),
                            "Novo remédio para você",
                            "$who adicionou ${med.name}: ${med.scheduleSummary()}"
                        )
                    }
                }
            }
            store.knownMedicationIds = meds.map { it.id }.toSet()
            store.myMedications = meds
            // Registros de hoje (inclusive de outro aparelho), para não tocar dose já tomada.
            store.recentLogs = store.recentLogs + Repository.logsForDay(userId)
            AlarmScheduler.rescheduleAll(context)

            notifyMissedDoses(context, store, userId)
        }

        /** Cuidador: avisa quando uma pessoa que ele cuida não respondeu ao alarme em 30 min. */
        private suspend fun notifyMissedDoses(context: Context, store: LocalStore, userId: String) {
            val patients = Repository.links().filter { it.caregiverId == userId && it.accepted }.map { it.patientId }.toSet()
            if (patients.isEmpty()) return
            val since = store.missedCheckedAt
            if (since == null) {
                // Primeira verificação: começa a contar a partir de agora (sem avisos antigos).
                store.missedCheckedAt = java.time.Instant.now().toString()
                return
            }
            val missed = Repository.missedSince(patients, since)
            if (missed.isEmpty()) return
            val names = Repository.profiles(patients).associateBy { it.id }
            val meds = Repository.medicationsByIds(missed.map { it.medicationId }.toSet()).associateBy { it.id }
            val zone = java.time.ZoneId.systemDefault()
            missed.forEach { log ->
                val who = names[log.ownerId]?.displayName ?: "Uma pessoa que você cuida"
                val med = meds[log.medicationId]?.name ?: "um remédio"
                val time = parseInstant(log.scheduledAt)?.atZone(zone)?.toLocalTime()?.toString()?.take(5) ?: ""
                Notifications.showUpdate(
                    context, (log.medicationId + log.scheduledAt).hashCode(),
                    "$who não respondeu ao alarme",
                    "O alarme de $med${if (time.isNotEmpty()) " das $time" else ""} tocou por " +
                        "${AlarmScheduler.RING_LIMIT_MINUTES} minutos sem resposta.",
                    Notifications.CHANNEL_CARE,
                )
            }
            store.missedCheckedAt = missed.maxByOrNull { parseInstant(it.loggedAt) ?: java.time.Instant.EPOCH }!!.loggedAt
        }

        /** Avisa (uma vez por versão) que há atualização; tocar abre o app com o botão "Atualizar". */
        private suspend fun notifyUpdate(context: Context) {
            val release = Updater.checkForUpdate() ?: return
            val store = LocalStore(context)
            if (store.notifiedUpdateCode >= release.versionCode) return
            store.notifiedUpdateCode = release.versionCode
            Notifications.showUpdate(
                context, 0x7FFF0002,
                "Atualização do HealthCheck disponível",
                "Versão ${release.versionName}. Toque para abrir o app e atualizar.",
            )
        }

        fun schedulePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun runNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(ONE_SHOT, ExistingWorkPolicy.REPLACE, request)
        }

        fun cancelAll(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(PERIODIC)
        }
    }
}
