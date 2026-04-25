package com.chitniskedar.pesufilter.worker

import android.app.Notification
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
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.chitniskedar.pesufilter.R
import com.chitniskedar.pesufilter.ui.MainActivity
import com.chitniskedar.pesufilter.utils.PreferencesManager

class BackgroundSyncService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var preferencesManager: PreferencesManager
    private val refreshRunnable = object : Runnable {
        override fun run() {
            if (!preferencesManager.hasActiveSession()) {
                stopSelf()
                return
            }

            AnnouncementScheduler.enqueueImmediate(applicationContext)
            handler.postDelayed(this, REFRESH_INTERVAL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        preferencesManager = PreferencesManager(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!preferencesManager.hasActiveSession()) {
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, buildNotification())
        handler.removeCallbacks(refreshRunnable)
        handler.post(refreshRunnable)
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(refreshRunnable)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            2001,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_filter)
            .setContentTitle(getString(R.string.background_sync_title))
            .setContentText(getString(R.string.background_sync_body))
            .setStyle(NotificationCompat.BigTextStyle().bigText(getString(R.string.background_sync_body)))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setContentIntent(contentIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }

        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.background_sync_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.background_sync_channel_description)
        }

        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "pesuflow_background_sync"
        private const val NOTIFICATION_ID = 20_001
        private const val REFRESH_INTERVAL_MS = 3 * 60 * 1000L

        fun start(context: Context) {
            val intent = Intent(context, BackgroundSyncService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, BackgroundSyncService::class.java))
        }
    }
}
