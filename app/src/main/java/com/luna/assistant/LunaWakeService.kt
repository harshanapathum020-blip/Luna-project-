package com.luna.assistant

import ai.picovoice.porcupine.PorcupineManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class LunaWakeService : Service() {

    private var porcupineManager: PorcupineManager? = null

    companion object {
        const val CHANNEL_ID = "LunaWakeChannel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_WAKE_DETECTED = "com.luna.assistant.WAKE_DETECTED"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Luna Assistant")
            .setContentText("Listening for 'wake up baby'...")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        startForeground(NOTIFICATION_ID, notification)
        initWakeEngine()
    }

    private fun initWakeEngine() {
        try {
            porcupineManager = PorcupineManager.Builder()
                .setAccessKey(Config.PICOVOICE_ACCESS_KEY)
                .setKeywordPath(Config.KEYWORD_ASSET_PATH)
                .setSensitivity(0.7f)
                .build(applicationContext) { keywordIndex ->
                    if (keywordIndex == 0) {
                        val broadcastIntent = Intent(ACTION_WAKE_DETECTED).setPackage(packageName)
                        sendBroadcast(broadcastIntent)
                    }
                }
            porcupineManager?.start()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        porcupineManager?.stop()
        porcupineManager?.delete()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Luna Wake Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }
}
