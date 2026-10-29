package com.kec.tnstc.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.Ringtone
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import com.kec.tnstc.MainActivity
import kotlinx.coroutines.*

/**
 * High-Priority Destination Alarm Controller.
 * Manages continuous audio ringtone, haptic vibration, and system heads-up notifications
 * when the bus reaches within the target proximity (e.g. 200m) of the destination.
 */
object DestinationAlarmController {
    private const val CHANNEL_ID = "tnstc_destination_alarm_channel"
    private const val CHANNEL_NAME = "TNSTC Destination Proximity Alarm"
    private const val NOTIFICATION_ID = 2026

    private var activeRingtone: Ringtone? = null
    private var activeMediaPlayer: MediaPlayer? = null
    private var activeVibrator: Vibrator? = null
    private var activeToneGenerator: ToneGenerator? = null
    private var toneJob: Job? = null
    private var isRinging = false

    private var previewMediaPlayer: MediaPlayer? = null
    private var previewToneGenerator: ToneGenerator? = null

    @Synchronized
    fun startAlarm(
        context: Context,
        toneCode: Int = ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK,
        customUriString: String? = null
    ) {
        stopAlarm()
        stopPreview()
        isRinging = true

        val appContext = context.applicationContext

        // Ensure alarm stream volume is sufficiently loud
        try {
            val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            val maxVol = audioManager?.getStreamMaxVolume(AudioManager.STREAM_ALARM) ?: 10
            val curVol = audioManager?.getStreamVolume(AudioManager.STREAM_ALARM) ?: 0
            if (curVol < (maxVol * 0.7).toInt()) {
                audioManager?.setStreamVolume(AudioManager.STREAM_ALARM, (maxVol * 0.85).toInt(), 0)
            }
        } catch (_: Exception) {}

        // 1. Play Custom Local Audio if configured by passenger
        var customAudioPlayed = false
        if (!customUriString.isNullOrBlank()) {
            try {
                val uri = Uri.parse(customUriString)
                activeMediaPlayer = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    setDataSource(appContext, uri)
                    isLooping = true
                    prepare()
                    start()
                }
                customAudioPlayed = true
            } catch (_: Exception) {
                activeMediaPlayer?.release()
                activeMediaPlayer = null
                customAudioPlayed = false
            }
        }

        // 2. Play System Alarm Ringtone & Tone Generator
        if (!customAudioPlayed) {
            try {
                val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

                activeRingtone = RingtoneManager.getRingtone(appContext, alarmUri)?.apply {
                    audioAttributes = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        isLooping = true
                    }
                    play()
                }
            } catch (_: Exception) {}

            // Always run Audio Tone Generator Loop (reliable across all devices, silent modes & emulators)
            try {
                activeToneGenerator = ToneGenerator(AudioManager.STREAM_ALARM, 100)
                toneJob = CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
                    while (isRinging && isActive) {
                        try {
                            activeToneGenerator?.startTone(toneCode, 900)
                        } catch (_: Exception) {}
                        delay(1100)
                    }
                }
            } catch (_: Exception) {}
        }

        // 3. Trigger Repeating Haptic Vibration
        try {
            activeVibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val mgr = appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                mgr?.defaultVibrator ?: (appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)
            } else {
                @Suppress("DEPRECATION")
                appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }

            val pattern = longArrayOf(0, 600, 250, 600, 250, 600)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                activeVibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0)) // 0 = loop forever
            } else {
                @Suppress("DEPRECATION")
                activeVibrator?.vibrate(pattern, 0)
            }
        } catch (_: Exception) {}
    }

    @Synchronized
    fun stopAlarm(context: Context? = null) {
        isRinging = false
        toneJob?.cancel()
        toneJob = null

        try {
            activeMediaPlayer?.stop()
            activeMediaPlayer?.release()
            activeMediaPlayer = null
        } catch (_: Exception) {}

        try {
            activeRingtone?.stop()
            activeRingtone = null
        } catch (_: Exception) {}

        try {
            activeToneGenerator?.release()
            activeToneGenerator = null
        } catch (_: Exception) {}

        try {
            activeVibrator?.cancel()
            activeVibrator = null
        } catch (_: Exception) {}

        context?.let { ctx ->
            try {
                val notificationManager = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                notificationManager?.cancel(NOTIFICATION_ID)
            } catch (_: Exception) {}
        }
    }

    @Synchronized
    fun playPreview(
        context: Context,
        customUriString: String? = null,
        toneCode: Int = ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK
    ) {
        stopPreview()
        val appContext = context.applicationContext

        if (!customUriString.isNullOrBlank()) {
            try {
                previewMediaPlayer = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    setDataSource(appContext, Uri.parse(customUriString))
                    prepare()
                    start()
                }
                return
            } catch (_: Exception) {
                previewMediaPlayer?.release()
                previewMediaPlayer = null
            }
        }

        try {
            previewToneGenerator = ToneGenerator(AudioManager.STREAM_ALARM, 90)
            previewToneGenerator?.startTone(toneCode, 1200)
        } catch (_: Exception) {}
    }

    @Synchronized
    fun stopPreview() {
        try {
            previewMediaPlayer?.stop()
            previewMediaPlayer?.release()
            previewMediaPlayer = null
        } catch (_: Exception) {}

        try {
            previewToneGenerator?.release()
            previewToneGenerator = null
        } catch (_: Exception) {}
    }

    fun showArrivalNotification(context: Context, destStopName: String, distanceMeters: Int) {
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Alerts passenger when approaching destination stop"
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 600, 200, 600)
                    lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                }
                notificationManager.createNotificationChannel(channel)
            }

            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
            )

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("🚨 Reaching Destination ($distanceMeters m)!")
                .setContentText("The bus is within $distanceMeters meters of $destStopName. Please wake up and prepare to deboard.")
                .setStyle(NotificationCompat.BigTextStyle().bigText("🚨 Reaching Destination ($distanceMeters m)!\n\nThe bus is now within $distanceMeters meters of $destStopName. Please wake up, check your belongings, and prepare to deboard."))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setFullScreenIntent(pendingIntent, true)
                .build()

            notificationManager.notify(NOTIFICATION_ID, notification)
        } catch (_: Exception) {}
    }
}
