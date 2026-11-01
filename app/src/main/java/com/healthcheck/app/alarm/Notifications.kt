package com.healthcheck.app.alarm

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.healthcheck.app.R
import com.healthcheck.app.ui.AlarmActivity
import com.healthcheck.app.ui.MainActivity

object Notifications {
    const val CHANNEL_DOSES = "doses"           // reserva: notificação que repete o som
    const val CHANNEL_ALARM = "dose_alarm"      // alarme em tela cheia (o som vem do AlarmService)
    const val CHANNEL_UPDATES = "updates"

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java)
        val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val doses = NotificationChannel(CHANNEL_DOSES, "Lembretes de remédio", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Alarmes na hora de tomar cada remédio"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 600, 300, 600, 300, 600)
            setSound(
                alarmSound,
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        }
        val updates = NotificationChannel(CHANNEL_UPDATES, "Atualizações", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Quando alguém cadastra ou altera um remédio para você"
        }
        val alarm = NotificationChannel(CHANNEL_ALARM, "Alarme de remédio", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Tela de alarme que toca até você responder"
            setSound(null, null)
            enableVibration(false)
            setBypassDnd(true)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        }
        nm.createNotificationChannel(alarm)
        nm.createNotificationChannel(doses)
        nm.createNotificationChannel(updates)
    }

    fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    fun canUseFullScreen(context: Context): Boolean =
        Build.VERSION.SDK_INT < 34 ||
            context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()

    fun openAppIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun doseAction(context: Context, dose: DoseExtras, action: String, label: String, code: Int): NotificationCompat.Action {
        val intent = Intent(context, DoseActionReceiver::class.java).setAction(action)
        dose.writeTo(intent)
        val pi = PendingIntent.getBroadcast(
            context, dose.notificationId * 10 + code, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Action(0, label, pi)
    }

    fun alarmScreenIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context, 1,
            Intent(context, AlarmActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    /** Notificação do alarme em tela cheia (estilo ligação), usada pelo AlarmService. */
    fun buildAlarm(context: Context, doses: List<DoseExtras>): android.app.Notification {
        val first = doses.first()
        val title = if (doses.size == 1) "Hora do remédio: ${first.name}" else "Hora de ${doses.size} remédios"
        val text = if (doses.size == 1) {
            listOf(first.dosage, "Horário: ${first.time}").filter { it.isNotBlank() }.joinToString(" · ")
        } else doses.joinToString(", ") { it.name }
        val screen = alarmScreenIntent(context)
        return NotificationCompat.Builder(context, CHANNEL_ALARM)
            .setSmallIcon(R.drawable.ic_pill)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(screen)
            .setFullScreenIntent(screen, true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .apply {
                if (doses.size == 1) {
                    addAction(doseAction(context, first, DoseActions.TAKEN, "Tomei ✔", 1))
                    addAction(doseAction(context, first, DoseActions.SNOOZE, "Adiar 10 min", 2))
                    addAction(doseAction(context, first, DoseActions.SKIP, "Pular", 3))
                }
            }
            .build()
    }

    /** Reserva, se o serviço de alarme não puder iniciar: o som se repete até a notificação ser tocada. */
    fun showDose(context: Context, dose: DoseExtras) {
        if (!canPost(context)) return
        val text = buildString {
            if (dose.dosage.isNotBlank()) append(dose.dosage).append(" · ")
            append("Horário: ").append(dose.time)
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_DOSES)
            .setSmallIcon(R.drawable.ic_pill)
            .setContentTitle("Hora do remédio: ${dose.name}")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(if (dose.notes.isBlank()) text else "$text\n${dose.notes}"))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(false)
            .setOngoing(true)
            .setContentIntent(alarmScreenIntent(context))
            .setFullScreenIntent(alarmScreenIntent(context), true)
            .addAction(doseAction(context, dose, DoseActions.TAKEN, "Tomei ✔", 1))
            .addAction(doseAction(context, dose, DoseActions.SNOOZE, "Adiar 10 min", 2))
            .addAction(doseAction(context, dose, DoseActions.SKIP, "Pular", 3))
            .build()
        notification.flags = notification.flags or android.app.Notification.FLAG_INSISTENT
        try {
            NotificationManagerCompat.from(context).notify(dose.notificationId, notification)
        } catch (_: SecurityException) {
        }
    }

    fun showUpdate(context: Context, id: Int, title: String, text: String) {
        if (!canPost(context)) return
        val n = NotificationCompat.Builder(context, CHANNEL_UPDATES)
            .setSmallIcon(R.drawable.ic_pill)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(context))
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id, n)
        } catch (_: SecurityException) {
        }
    }

    fun cancel(context: Context, id: Int) {
        NotificationManagerCompat.from(context).cancel(id)
    }
}
