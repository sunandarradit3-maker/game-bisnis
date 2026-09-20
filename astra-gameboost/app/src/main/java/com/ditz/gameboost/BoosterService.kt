package com.ditz.gameboost

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.NotificationCompat

class BoosterService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var controller: PerformanceController
    private var thermalGuard = true
    private var tripped = false

    private val watchdog = object : Runnable {
        override fun run() {
            if (thermalGuard) {
                val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
                val status = if (Build.VERSION.SDK_INT >= 29) pm.currentThermalStatus else PowerManager.THERMAL_STATUS_NONE
                if (status >= PowerManager.THERMAL_STATUS_SEVERE && !tripped) {
                    tripped = true
                    Thread { controller.thermalSafetyFallback() }.start()
                    updateNotification("Thermal tinggi: performa agresif dimatikan otomatis")
                }
            }
            handler.postDelayed(this, 2500)
        }
    }

    override fun onCreate() {
        super.onCreate()
        controller = PerformanceController(this)
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                handler.removeCallbacks(watchdog)
                Thread { controller.restore() }.start()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                thermalGuard = intent?.getBooleanExtra(EXTRA_THERMAL_GUARD, true) ?: true
                startForeground(NOTIFICATION_ID, buildNotification("Gaming session aktif • Thermal Guard ${if (thermalGuard) "ON" else "OFF"}"))
                handler.removeCallbacks(watchdog)
                handler.post(watchdog)
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(watchdog)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Gaming booster session", NotificationManager.IMPORTANCE_LOW))
    }

    private fun buildNotification(text: String): android.app.Notification {
        val openIntent = PendingIntent.getActivity(
            this, 1, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this, 2, Intent(this, BoosterService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("Astra GameBoost Premium")
            .setContentText(text)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_media_pause, "STOP & RESTORE", stopIntent)
            .build()
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification(text))
    }

    companion object {
        const val ACTION_STOP = "com.ditz.gameboost.STOP"
        const val EXTRA_THERMAL_GUARD = "thermal_guard"
        private const val CHANNEL_ID = "gameboost_session"
        private const val NOTIFICATION_ID = 4401
    }
}
