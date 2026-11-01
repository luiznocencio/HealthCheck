package com.healthcheck.app.data

import android.content.Context
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.SetSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Cache local dos remédios do usuário (para os alarmes funcionarem offline)
 * e fila de registros de dose que ainda não foram enviados ao servidor.
 */
class LocalStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("healthcheck", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    var myMedications: List<Medication>
        get() = decode(prefs.getString(KEY_MEDS, null), ListSerializer(Medication.serializer())) ?: emptyList()
        set(value) = prefs.edit().putString(KEY_MEDS, json.encodeToString(ListSerializer(Medication.serializer()), value)).apply()

    /** IDs de remédios já vistos — usado para avisar quando alguém cadastra um novo. */
    var knownMedicationIds: Set<String>?
        get() = decode(prefs.getString(KEY_KNOWN, null), SetSerializer(String.serializer()))
        set(value) = prefs.edit().apply {
            if (value == null) remove(KEY_KNOWN)
            else putString(KEY_KNOWN, json.encodeToString(SetSerializer(String.serializer()), value))
        }.apply()

    var scheduledAlarmKeys: Set<String>
        get() = prefs.getStringSet(KEY_ALARMS, emptySet())?.toSet() ?: emptySet()
        set(value) = prefs.edit().putStringSet(KEY_ALARMS, value).apply()

    var pendingLogs: List<DoseLog>
        get() = decode(prefs.getString(KEY_PENDING, null), ListSerializer(DoseLog.serializer())) ?: emptyList()
        set(value) = prefs.edit().putString(KEY_PENDING, json.encodeToString(ListSerializer(DoseLog.serializer()), value)).apply()

    @Synchronized
    fun addPendingLog(log: DoseLog) {
        pendingLogs = pendingLogs.filterNot {
            it.medicationId == log.medicationId && it.scheduledAt == log.scheduledAt
        } + log
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private fun <T> decode(raw: String?, serializer: kotlinx.serialization.KSerializer<T>): T? =
        raw?.let { runCatching { json.decodeFromString(serializer, it) }.getOrNull() }

    companion object {
        private const val KEY_MEDS = "my_meds"
        private const val KEY_KNOWN = "known_ids"
        private const val KEY_ALARMS = "alarm_keys"
        private const val KEY_PENDING = "pending_logs"
    }
}
