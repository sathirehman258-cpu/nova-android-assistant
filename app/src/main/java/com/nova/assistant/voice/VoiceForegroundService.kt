package com.nova.assistant.voice

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

/**
 * Foreground service that backs an ACTIVE listening/response session — started only while Nova
 * is actually listening or speaking, not as an always-on background microphone hold. The
 * notification and mic-privacy indicator are always visible while this runs, per platform
 * requirements and Nova's own privacy commitments.
 */
class VoiceForegroundService : Service() {

    companion object {
        const val CHANNEL_ID = "nova_voice_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_STOP = "com.nova.assistant.action.STOP_LISTENING"
    }

    override fun onCreate() {
        super.onCreate()
        createChannelIfNeeded()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(NOTIFICATION_ID, buildNotification())
        return START_NOT_STICKY
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Nova is listening")
            .setContentText("Tap to open Nova")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .build()

    private fun createChannelIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL_ID, "Nova voice activity", NotificationManager.IMPORTANCE_LOW
            )
            manager.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
