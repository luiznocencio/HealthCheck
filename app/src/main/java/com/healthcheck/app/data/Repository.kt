package com.healthcheck.app.data

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Acesso ao backend (Supabase). Todas as funções são suspensas e podem lançar exceção. */
object Repository {
    private val client get() = Supabase.client

    suspend fun awaitAuth() {
        client.auth.awaitInitialization()
    }

    fun currentUserId(): String? = client.auth.currentUserOrNull()?.id

    suspend fun signUp(name: String, email: String, password: String) {
        client.auth.signUpWith(Email) {
            this.email = email.trim()
            this.password = password
            data = buildJsonObject { put("name", name.trim()) }
        }
    }

    suspend fun signIn(email: String, password: String) {
        client.auth.signInWith(Email) {
            this.email = email.trim()
            this.password = password
        }
    }

    suspend fun signOut() {
        client.auth.signOut()
    }

    // ---------------- Perfis ----------------

    suspend fun profiles(ids: Collection<String>): List<Profile> {
        if (ids.isEmpty()) return emptyList()
        return client.from("profiles").select {
            filter { isIn("id", ids.toList()) }
        }.decodeList<Profile>()
    }

    suspend fun myProfile(): Profile? {
        val id = currentUserId() ?: return null
        return profiles(listOf(id)).firstOrNull()
    }

    // ---------------- Vínculos ----------------

    suspend fun links(): List<CareLink> =
        client.from("care_links").select().decodeList<CareLink>()

    suspend fun invite(email: String) {
        client.postgrest.rpc("request_care_link", buildJsonObject { put("patient_email", email.trim()) })
    }

    suspend fun acceptLink(linkId: String) {
        client.from("care_links").update({ set("status", "accepted") }) {
            filter { eq("id", linkId) }
        }
    }

    suspend fun removeLink(linkId: String) {
        client.from("care_links").delete { filter { eq("id", linkId) } }
    }

    // ---------------- Remédios ----------------

    suspend fun medications(ownerId: String): List<Medication> =
        client.from("medications").select {
            filter { eq("owner_id", ownerId) }
            order("name", io.github.jan.supabase.postgrest.query.Order.ASCENDING)
        }.decodeList<Medication>()

    suspend fun saveMedication(med: Medication, isNew: Boolean) {
        if (isNew) {
            client.from("medications").insert(med)
        } else {
            client.from("medications").update({
                set("name", med.name)
                set("dosage", med.dosage)
                set("notes", med.notes)
                set("times", med.times)
                set("days_of_week", med.daysOfWeek)
                set("start_date", med.startDate)
                set("end_date", med.endDate)
                set("active", med.active)
            }) {
                filter { eq("id", med.id) }
            }
        }
    }

    suspend fun deleteMedication(id: String) {
        client.from("medications").delete { filter { eq("id", id) } }
    }

    // ---------------- Doses ----------------

    suspend fun logsForDay(ownerId: String, date: LocalDate = LocalDate.now()): List<DoseLog> {
        val zone = ZoneId.systemDefault()
        val start = date.atStartOfDay(zone).toInstant()
        val end = date.plusDays(1).atStartOfDay(zone).toInstant()
        return client.from("dose_logs").select {
            filter {
                eq("owner_id", ownerId)
                gte("scheduled_at", start.toString())
                lt("scheduled_at", end.toString())
            }
        }.decodeList<DoseLog>()
    }

    suspend fun upsertLogs(logs: List<DoseLog>) {
        if (logs.isEmpty()) return
        client.from("dose_logs").upsert(logs) {
            onConflict = "medication_id,scheduled_at"
        }
    }

    fun newLog(med: Medication, scheduledAt: Instant, status: String) =
        DoseLog(med.id, med.ownerId, scheduledAt.toString(), status)
}
