package com.healthcheck.app.alarm

import android.content.Context
import com.healthcheck.app.data.DoseLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Instant

/** Ações de uma dose (vindas da notificação ou da tela de alarme). */
object DoseActions {
    const val TAKEN = "com.healthcheck.app.TAKEN"
    const val SNOOZE = "com.healthcheck.app.SNOOZE"
    const val SKIP = "com.healthcheck.app.SKIP"
    const val MISSED = "com.healthcheck.app.MISSED" // limite de 30 min sem resposta

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Para o alarme e executa a ação. Retorna o Job do registro (ou null). */
    fun handle(context: Context, dose: DoseExtras, action: String): Job? {
        val app = context.applicationContext
        AlarmService.dismiss(app, dose)
        AlarmScheduler.cancelTimeout(app, dose)
        Notifications.cancel(app, dose.notificationId)
        return when (action) {
            SNOOZE -> {
                AlarmScheduler.snooze(app, dose)
                null
            }
            TAKEN, SKIP, MISSED -> {
                val status = when (action) {
                    TAKEN -> "taken"
                    SKIP -> "skipped"
                    else -> "missed"
                }
                val log = DoseLog(dose.medicationId, dose.ownerId, Instant.ofEpochMilli(dose.scheduledAt).toString(), status)
                scope.launch { DoseLogger.record(app, log) }
            }
            else -> null
        }
    }
}
