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
import com.healthcheck.app.ui.MainActivity

object Notifications {
    const val CHANNEL_DOSES = "doses"
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
        nm.createNotificationChannel(doses)
        nm.createNotificationChannel(updates)
    }

    fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    fun openAppIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    fun showDose(context: Context, dose: DoseExtras) {
        if (!canPost(context)) return
        fun action(action: String, label: String, code: Int): NotificationCompat.Action {
            val intent = Intent(context, DoseActionReceiver::class.java).setAction(action)
            dose.writeTo(intent)
            val pi = PendingIntent.getBroadcast(
                context, dose.notificationId * 10 + code, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            return NotificationCompat.Action(0, label, pi)
        }
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
            .setContentIntent(openAppIntent(context))
            .addAction(action(DoseActionReceiver.ACTION_TAKEN, "Tomei ✔", 1))
            .addAction(action(DoseActionReceiver.ACTION_SNOOZE, "Adiar 10 min", 2))
            .addAction(action(DoseActionReceiver.ACTION_SKIP, "Pular", 3))
            .build()
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
