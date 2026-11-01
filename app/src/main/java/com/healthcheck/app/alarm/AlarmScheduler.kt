package com.healthcheck.app.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.healthcheck.app.data.LocalStore
import com.healthcheck.app.data.Medication
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Agenda um alarme por "slot" (remédio + horário). Quando o alarme dispara,
 * o AlarmReceiver mostra a notificação e agenda a próxima ocorrência do mesmo slot.
 */
object AlarmScheduler {

    fun canScheduleExact(context: Context): Boolean {
        val am = context.getSystemService(AlarmManager::class.java)
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
    }

    /** Cancela tudo e agenda novamente a partir do cache local. */
    @Synchronized
    fun rescheduleAll(context: Context) {
        val store = LocalStore(context)
        val meds = store.myMedications
        val previous = store.scheduledAlarmKeys
        val now = LocalDateTime.now()
        val scheduled = mutableSetOf<String>()

        for (med in meds) {
            for (time in med.localTimes()) {
                val key = DoseExtras.slotKey(med.id, time.toString())
                if (scheduleSlot(context, med, time, now)) scheduled += key
            }
        }
        // Cancela alarmes de remédios/horários que não existem mais
        for (key in previous - scheduled) {
            val (medId, time) = key.split("@").let { it[0] to it.getOrElse(1) { "" } }
            cancel(context, DoseExtras(medId, "", "", "", "", time, 0L))
        }
        store.scheduledAlarmKeys = scheduled
    }

    /** Agenda a próxima ocorrência do slot (depois de [after]). Retorna false se não houver. */
    fun scheduleSlot(context: Context, med: Medication, time: LocalTime, after: LocalDateTime): Boolean {
        val next = med.nextOccurrence(time, after) ?: run {
            cancel(context, DoseExtras.from(med, time.toString(), 0L))
            return false
        }
        val dose = DoseExtras.from(med, time.toString(), next.toEpochMilli())
        setAlarm(context, dose.notificationId, next.toEpochMilli(), dose, AlarmReceiver.ACTION_DOSE)
        return true
    }

    fun snooze(context: Context, dose: DoseExtras, minutes: Int = 10) {
        val at = System.currentTimeMillis() + minutes * 60_000L
        setAlarm(context, dose.notificationId + SNOOZE_OFFSET, at, dose, AlarmReceiver.ACTION_SNOOZED)
    }

    /** Arma o limite: se ninguém responder em [RING_LIMIT_MINUTES], o alarme para e o cuidador é avisado. */
    fun armTimeout(context: Context, dose: DoseExtras) {
        val am = context.getSystemService(AlarmManager::class.java)
        val at = System.currentTimeMillis() + RING_LIMIT_MINUTES * 60_000L
        val pi = pendingIntent(context, dose.notificationId + TIMEOUT_OFFSET, dose, AlarmReceiver.ACTION_TIMEOUT)
        if (canScheduleExact(context)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
    }

    fun cancelTimeout(context: Context, dose: DoseExtras) {
        val am = context.getSystemService(AlarmManager::class.java)
        am.cancel(pendingIntent(context, dose.notificationId + TIMEOUT_OFFSET, null, AlarmReceiver.ACTION_TIMEOUT))
    }

    fun cancel(context: Context, dose: DoseExtras) {
        val am = context.getSystemService(AlarmManager::class.java)
        am.cancel(pendingIntent(context, dose.notificationId, null, AlarmReceiver.ACTION_DOSE))
        am.cancel(pendingIntent(context, dose.notificationId + SNOOZE_OFFSET, null, AlarmReceiver.ACTION_SNOOZED))
    }

    private fun setAlarm(context: Context, requestCode: Int, atMillis: Long, dose: DoseExtras, action: String) {
        val am = context.getSystemService(AlarmManager::class.java)
        val pi = pendingIntent(context, requestCode, dose, action)
        if (canScheduleExact(context)) {
            // setAlarmClock é o modo mais confiável: ignora o modo soneca (Doze) do Android.
            val info = AlarmManager.AlarmClockInfo(atMillis, Notifications.openAppIntent(context))
            am.setAlarmClock(info, pi)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
        }
    }

    private fun pendingIntent(context: Context, requestCode: Int, dose: DoseExtras?, action: String): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).setAction(action)
        dose?.writeTo(intent)
        return PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private const val SNOOZE_OFFSET = 0x10000000
    private const val TIMEOUT_OFFSET = 0x20000000
    const val RING_LIMIT_MINUTES = 30
}
