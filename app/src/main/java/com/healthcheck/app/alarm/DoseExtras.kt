package com.healthcheck.app.alarm

import android.content.Intent
import com.healthcheck.app.data.Medication

/** Dados de uma dose transportados nos Intents dos alarmes e das notificações. */
data class DoseExtras(
    val medicationId: String,
    val ownerId: String,
    val name: String,
    val dosage: String,
    val notes: String,
    val time: String,
    val scheduledAt: Long,
) {
    /** Chave estável do "slot" (remédio + horário) — usada como id do alarme. */
    val slotKey: String get() = slotKey(medicationId, time)
    val notificationId: Int get() = slotKey.hashCode() and 0x0FFFFFFF

    fun writeTo(intent: Intent) {
        intent.putExtra(K_MED, medicationId)
        intent.putExtra(K_OWNER, ownerId)
        intent.putExtra(K_NAME, name)
        intent.putExtra(K_DOSAGE, dosage)
        intent.putExtra(K_NOTES, notes)
        intent.putExtra(K_TIME, time)
        intent.putExtra(K_AT, scheduledAt)
    }

    companion object {
        private const val K_MED = "med_id"
        private const val K_OWNER = "owner_id"
        private const val K_NAME = "name"
        private const val K_DOSAGE = "dosage"
        private const val K_NOTES = "notes"
        private const val K_TIME = "time"
        private const val K_AT = "scheduled_at"

        fun slotKey(medicationId: String, time: String) = "$medicationId@$time"

        fun from(med: Medication, time: String, scheduledAt: Long) =
            DoseExtras(med.id, med.ownerId, med.name, med.dosage, med.notes, time, scheduledAt)

        fun read(intent: Intent): DoseExtras? {
            val id = intent.getStringExtra(K_MED) ?: return null
            return DoseExtras(
                medicationId = id,
                ownerId = intent.getStringExtra(K_OWNER) ?: "",
                name = intent.getStringExtra(K_NAME) ?: "",
                dosage = intent.getStringExtra(K_DOSAGE) ?: "",
                notes = intent.getStringExtra(K_NOTES) ?: "",
                time = intent.getStringExtra(K_TIME) ?: "",
                scheduledAt = intent.getLongExtra(K_AT, 0L),
            )
        }
    }
}
