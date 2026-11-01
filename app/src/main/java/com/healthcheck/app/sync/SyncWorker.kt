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
import java.util.concurrent.TimeUnit

/**
 * Baixa os remédios do usuário logado, atualiza o cache local e reagenda os alarmes.
 * Roda ao abrir o app e periodicamente (a cada ~15 min) em segundo plano, então os
 * remédios cadastrados por um cuidador chegam ao celular do paciente automaticamente.
 */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
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
            AlarmScheduler.rescheduleAll(context)
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
