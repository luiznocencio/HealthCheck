package com.healthcheck.app.alarm

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Doses que estão tocando agora (compartilhado entre o serviço e a tela de alarme). */
object ActiveAlarms {
    private val _doses = MutableStateFlow<List<DoseExtras>>(emptyList())
    val doses: StateFlow<List<DoseExtras>> = _doses.asStateFlow()

    fun add(dose: DoseExtras) = _doses.update { list -> list.filterNot { it.slotKey == dose.slotKey } + dose }
    fun remove(slotKey: String) = _doses.update { list -> list.filterNot { it.slotKey == slotKey } }
    fun contains(slotKey: String) = _doses.value.any { it.slotKey == slotKey }
}

/**
 * Serviço em primeiro plano que toca o som de alarme em loop e vibra, como um despertador
 * ou uma ligação, até a pessoa tocar em "Tomei", "Adiar" ou "Pular".
 */
class AlarmService : Service() {
    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_RING) {
            DoseExtras.read(intent)?.let { ActiveAlarms.add(it) }
        }
        val active = ActiveAlarms.doses.value
        if (active.isEmpty()) {
            stopRinging()
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, Notifications.buildAlarm(this, active), type)
        startRinging()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stopRinging()
        super.onDestroy()
    }

    private fun startRinging() {
        if (player == null) {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            player = try {
                MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    setDataSource(this@AlarmService, uri)
                    setWakeMode(this@AlarmService, PowerManager.PARTIAL_WAKE_LOCK)
                    isLooping = true
                    prepare()
                    start()
                }
            } catch (e: Exception) {
                Log.e("HealthCheck", "Falha ao tocar alarme", e)
                null
            }
        }
        if (vibrator == null) {
            vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                getSystemService(VibratorManager::class.java)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            vibrator?.vibrate(
                VibrationEffect.createWaveform(longArrayOf(0, 1000, 700), 0),
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build()
            )
        }
    }

    private fun stopRinging() {
        player?.run { runCatching { stop() }; release() }
        player = null
        vibrator?.cancel()
        vibrator = null
    }

    companion object {
        const val NOTIFICATION_ID = 0x7FFF0001
        private const val ACTION_RING = "com.healthcheck.app.RING"
        private const val ACTION_REFRESH = "com.healthcheck.app.REFRESH"

        /** Começa a tocar. Retorna false se o Android não permitiu iniciar o serviço. */
        fun ring(context: Context, dose: DoseExtras): Boolean {
            val intent = Intent(context, AlarmService::class.java).setAction(ACTION_RING)
            dose.writeTo(intent)
            return try {
                ContextCompat.startForegroundService(context, intent)
                true
            } catch (e: Exception) {
                Log.e("HealthCheck", "Não foi possível iniciar o alarme em primeiro plano", e)
                false
            }
        }

        /** Para de tocar a dose indicada (e o serviço, se não houver mais nenhuma). */
        fun dismiss(context: Context, dose: DoseExtras) {
            if (!ActiveAlarms.contains(dose.slotKey)) return
            ActiveAlarms.remove(dose.slotKey)
            try {
                context.startService(Intent(context, AlarmService::class.java).setAction(ACTION_REFRESH))
            } catch (e: Exception) {
                Log.e("HealthCheck", "Falha ao atualizar o alarme", e)
            }
        }
    }
}
