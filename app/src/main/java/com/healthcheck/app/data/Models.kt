package com.healthcheck.app.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId

@Serializable
data class Profile(
    val id: String,
    val name: String = "",
    val email: String = "",
) {
    val displayName: String get() = name.ifBlank { email }
}

@Serializable
data class CareLink(
    val id: String,
    @SerialName("caregiver_id") val caregiverId: String,
    @SerialName("patient_id") val patientId: String,
    val status: String = "pending",
) {
    val accepted: Boolean get() = status == "accepted"
}

/**
 * Um remédio com seu padrão de horários.
 * times: "HH:mm" ; daysOfWeek: 1 = segunda ... 7 = domingo (ISO-8601).
 */
@Serializable
data class Medication(
    val id: String,
    @SerialName("owner_id") val ownerId: String,
    @SerialName("created_by") val createdBy: String,
    val name: String,
    val dosage: String = "",
    val notes: String = "",
    val times: List<String> = emptyList(),
    @SerialName("days_of_week") val daysOfWeek: List<Int> = listOf(1, 2, 3, 4, 5, 6, 7),
    @SerialName("start_date") val startDate: String = LocalDate.now().toString(),
    @SerialName("end_date") val endDate: String? = null,
    val active: Boolean = true,
) {
    fun localTimes(): List<LocalTime> =
        times.mapNotNull { runCatching { LocalTime.parse(it) }.getOrNull() }.sorted()

    fun isScheduledOn(date: LocalDate): Boolean {
        if (!active) return false
        val start = runCatching { LocalDate.parse(startDate) }.getOrDefault(LocalDate.MIN)
        val end = endDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        if (date.isBefore(start)) return false
        if (end != null && date.isAfter(end)) return false
        return date.dayOfWeek.value in daysOfWeek
    }

    /** Próxima ocorrência (estritamente depois de [after]) de um horário específico. */
    fun nextOccurrence(time: LocalTime, after: LocalDateTime, zone: ZoneId = ZoneId.systemDefault()): Instant? {
        var date = after.toLocalDate()
        repeat(400) {
            if (isScheduledOn(date)) {
                val candidate = LocalDateTime.of(date, time)
                if (candidate.isAfter(after)) return candidate.atZone(zone).toInstant()
            }
            date = date.plusDays(1)
            val end = endDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            if (end != null && date.isAfter(end)) return null
        }
        return null
    }

    fun scheduleSummary(): String {
        val days = when {
            daysOfWeek.size == 7 -> "Todos os dias"
            daysOfWeek.toSet() == setOf(1, 2, 3, 4, 5) -> "Segunda a sexta"
            daysOfWeek.toSet() == setOf(6, 7) -> "Fins de semana"
            else -> daysOfWeek.sorted().joinToString(", ") { dayShort(it) }
        }
        val count = times.size
        val vezes = if (count == 1) "1 vez ao dia" else "$count vezes ao dia"
        return "$days · $vezes · ${localTimes().joinToString(" · ") { it.toString() }}"
    }
}

@Serializable
data class DoseLog(
    @SerialName("medication_id") val medicationId: String,
    @SerialName("owner_id") val ownerId: String,
    @SerialName("scheduled_at") val scheduledAt: String,
    val status: String,
) {
    fun scheduledInstant(): Instant? = parseInstant(scheduledAt)
}

/** Registro "não respondeu" lido pelo cuidador (inclui quando foi registrado). */
@Serializable
data class MissedLog(
    @SerialName("medication_id") val medicationId: String,
    @SerialName("owner_id") val ownerId: String,
    @SerialName("scheduled_at") val scheduledAt: String,
    @SerialName("logged_at") val loggedAt: String,
)

/** Uma dose calculada para um dia, juntando remédio + registro (se houver). */
data class DoseItem(
    val medication: Medication,
    val time: LocalTime,
    val scheduledAt: Instant,
    val status: String?, // "taken", "skipped", "missed" ou null (pendente)
)

fun parseInstant(value: String): Instant? =
    runCatching { OffsetDateTime.parse(value).toInstant() }.getOrNull()
        ?: runCatching { Instant.parse(value) }.getOrNull()
        ?: runCatching { OffsetDateTime.parse(value.replace(" ", "T")).toInstant() }.getOrNull()

fun dayShort(iso: Int): String = when (DayOfWeek.of(iso)) {
    DayOfWeek.MONDAY -> "Seg"
    DayOfWeek.TUESDAY -> "Ter"
    DayOfWeek.WEDNESDAY -> "Qua"
    DayOfWeek.THURSDAY -> "Qui"
    DayOfWeek.FRIDAY -> "Sex"
    DayOfWeek.SATURDAY -> "Sáb"
    DayOfWeek.SUNDAY -> "Dom"
}

/** Monta a lista de doses de um dia a partir dos remédios e dos registros. */
fun buildDoses(
    meds: List<Medication>,
    logs: List<DoseLog>,
    date: LocalDate = LocalDate.now(),
    zone: ZoneId = ZoneId.systemDefault(),
): List<DoseItem> {
    val logIndex = logs.associateBy { it.medicationId to it.scheduledInstant()?.toEpochMilli() }
    return meds.filter { it.isScheduledOn(date) }.flatMap { med ->
        med.localTimes().map { t ->
            val at = LocalDateTime.of(date, t).atZone(zone).toInstant()
            DoseItem(med, t, at, logIndex[med.id to at.toEpochMilli()]?.status)
        }
    }.sortedBy { it.scheduledAt }
}
